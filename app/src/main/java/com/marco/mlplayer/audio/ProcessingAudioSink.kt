package com.marco.mlplayer.audio

import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import com.marco.mlplayer.audio.direct.DirectOutputs
import com.marco.mlplayer.audio.direct.DirectTrackStream
import com.marco.mlplayer.audio.direct.InternalDirectStream
import com.marco.mlplayer.audio.direct.PathStep
import com.marco.mlplayer.audio.direct.Route
import com.marco.mlplayer.audio.direct.SignalPath
import com.marco.mlplayer.util.AppLog
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

/**
 * Applica il DSP PRIMA del sink di Media3 e sceglie dove mandare il suono.
 *
 * Motivo del DSP qui: DefaultAudioSink, con l'uscita float attiva e un ingresso PCM a 24/32 bit, usa
 * solo il suo convertitore float e salta i processori personalizzati. Qui il processore vede sempre il
 * PCM uscito dal decoder.
 *
 * Uscite (idee e codice da LastWave, GPL-3.0):
 *  - USB esclusiva: driver usbdevfs (decent-player, MIT), il DAC riceve il PCM senza il mixer di Android;
 *  - uscita diretta: AudioTrack 24 bit alla frequenza del brano che la politica audio mette su un'uscita
 *    DIRECT (verificato, non supposto) oppure AAudio esclusiva (MMAP);
 *  - Android: AudioTrack normale tramite il sink di Media3 (con il bit-perfect del mixer su Android 14+).
 */
@OptIn(UnstableApi::class)
class ProcessingAudioSink(
    sink: AudioSink,
    private val processor: EqAudioProcessor,
    private val monitor: OutputMonitor,
    private val outputs: DirectOutputs,
    private val adaptive: () -> Boolean,
) : ForwardingAudioSink(sink) {

    private var active = false
    private var pending: ByteBuffer? = null
    private var pendingPts = 0L
    private var pendingCount = 1
    private var pendingScaled = false
    private var eosQueued = false

    // uscita diretta
    @Volatile private var route = Route.Android
    private var androidConfigured = false
    private var directTrack: DirectTrackStream? = null
    private var aaudio: InternalDirectStream? = null
    private var outRate = 0
    private var outChannels = 2
    private var outFloat = true
    private var startPtsUs = 0L
    private var needStart = true
    private var baseFrames = 0L
    private var playing = false
    private var volume = 1f

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        active = false
        pending = null
        eosQueued = false
        needStart = true
        closeLocalDirect()
        var fmt = inputFormat
        val raw = MimeTypes.AUDIO_RAW == inputFormat.sampleMimeType && inputFormat.pcmEncoding != Format.NO_VALUE
        val bitPerfect = outputs.bitPerfect
        var target = Route.Android
        if (raw && inputFormat.channelCount in 1..2) {
            target = when {
                outputs.usbWanted && outputs.usbDacReady() -> Route.UsbExclusive
                outputs.directWanted && outputs.usbAudioDevice() == null ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Route.DirectTrack else Route.AAudio
                else -> Route.Android
            }
        }
        if (outputs.usbWanted && outputs.usbAudioDevice() != null && !outputs.usbDacReady()) outputs.ensureUsbPermission()

        if (raw) {
            processor.forceBypass = bitPerfect
            var plan: OutputMonitor.Plan? = null
            when {
                bitPerfect -> {
                    processor.targetRate = 0
                    processor.outputBits = if (inputFormat.pcmEncoding == C.ENCODING_PCM_16BIT) 16 else 32
                }
                target == Route.Android -> {
                    // uscita adattiva: frequenza e profondità richieste dal dispositivo collegato
                    plan = if (adaptive()) monitor.plan(inputFormat.sampleRate) else null
                    processor.targetRate = if (plan != null && plan.rate != inputFormat.sampleRate) plan.rate else 0
                    processor.outputBits = plan?.bits ?: 32
                }
                else -> {
                    processor.targetRate = 0
                    processor.outputBits = 32
                }
            }
            monitor.lastPlan = plan?.describe(inputFormat.sampleRate) ?: if (bitPerfect) "Bit-perfect (formato del brano)" else "Standard (uscita non adattata)"
            processor.replayGain = ReplayGain.parse(inputFormat.metadata)
            fmt = configureProcessor(inputFormat) ?: inputFormat
        } else {
            processor.forceBypass = false
        }

        // apertura dell'uscita scelta (se non riesce si torna ad Android)
        var r = Route.Android
        var note = ""
        if (active && target != Route.Android) {
            when (target) {
                Route.UsbExclusive -> {
                    if (runCatching { outputs.usb.configure(fmt) }.getOrDefault(false)) r = Route.UsbExclusive
                    else note = "USB esclusiva non avviata: " + (outputs.usb.lastFailureReason ?: "motivo sconosciuto")
                }
                Route.DirectTrack, Route.AAudio -> {
                    if (target == Route.DirectTrack && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val t = DirectTrackStream(mediaAttributes())
                        val wire = InternalDirectStream.ENC_I24
                        if (t.open(fmt.sampleRate, fmt.channelCount, wire)) {
                            val direct = t.probeDirect()
                            if (direct == true) {
                                directTrack = t
                                r = Route.DirectTrack
                            } else {
                                t.release()
                                note = "Android ha messo l'AudioTrack 24 bit nel mixer (nessuna uscita DIRECT)"
                            }
                        } else note = "AudioTrack 24 bit a ${fmt.sampleRate} Hz rifiutato"
                    }
                    if (r == Route.Android) r = openAAudio(inputFormat, fmt, bitPerfect)?.also { fmt = it.second }?.first ?: r
                    if (r == Route.Android && note.isEmpty()) note = "Uscita esclusiva AAudio non disponibile su questa uscita"
                }
                else -> {}
            }
        }
        route = r
        if (r != Route.UsbExclusive && outputs.usb.isActive()) runCatching { outputs.usb.reset() }

        outRate = fmt.sampleRate
        outChannels = fmt.channelCount
        outFloat = fmt.pcmEncoding == C.ENCODING_PCM_FLOAT
        if (r == Route.Android) {
            if (raw) {
                outputs.usbBitPerfect.setDevice(outputs.androidUsbOutput())
                outputs.usbBitPerfect.setFormat(if (bitPerfect) androidFormat(fmt) else null)
            }
            super.configure(fmt, specifiedBufferSize, outputChannels)
            androidConfigured = true
        } else {
            if (androidConfigured) runCatching { super.flush() }
            directTrack?.setVolume(volume)
            outputs.usb.setVolume(volume)
            if (playing) startDirect()
        }
        if (note.isNotEmpty()) AppLog.w("Uscita", note)
        AppLog.i("Uscita", "Percorso: ${r.label}, ${fmt.sampleRate} Hz, ${encLabel(fmt.pcmEncoding)}, bit-perfect=$bitPerfect")
        publishPath(inputFormat, fmt, raw, bitPerfect, note)
    }

    private fun configureProcessor(inputFormat: Format): Format? = try {
        val out = processor.configure(AudioProcessor.AudioFormat(inputFormat))
        if (processor.isActive) {
            processor.flush()
            active = true
            inputFormat.buildUpon().setPcmEncoding(out.encoding).setSampleRate(out.sampleRate).build()
        } else {
            AppLog.w("Audio", "DSP non applicabile a questo formato (codifica ${inputFormat.pcmEncoding})")
            null
        }
    } catch (e: AudioProcessor.UnhandledAudioFormatException) {
        AppLog.w("Audio", "DSP: formato non gestito: ${e.message}")
        null
    }

    /** AAudio esclusiva: il dispositivo impone frequenza e formato; il ricampionamento lo fa soxr nel DSP. */
    private fun openAAudio(input: Format, current: Format, bitPerfect: Boolean): Pair<Route, Format>? {
        if (!InternalDirectStream.nativeLoaded) return null
        val probe = InternalDirectStream.probe(current.channelCount) ?: return null
        if (!probe.usable) return null
        var fmt = current
        if (probe.sampleRateHz != current.sampleRate) {
            if (bitPerfect) return null
            processor.targetRate = probe.sampleRateHz
            processor.outputBits = 32
            fmt = configureProcessor(input) ?: return null
        }
        val s = InternalDirectStream()
        if (!s.isAvailable) return null
        val res = s.open(probe.sampleRateHz, fmt.channelCount, probe.encoding)
        if (res != InternalDirectStream.OPEN_OK) {
            AppLog.w("Uscita", "AAudio esclusiva rifiutata (codice $res)")
            s.close()
            return null
        }
        aaudio = s
        return Route.AAudio to fmt
    }

    private fun mediaAttributes(): android.media.AudioAttributes = android.media.AudioAttributes.Builder()
        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private fun androidFormat(f: Format): android.media.AudioFormat? {
        val enc = when (f.pcmEncoding) {
            C.ENCODING_PCM_16BIT -> android.media.AudioFormat.ENCODING_PCM_16BIT
            C.ENCODING_PCM_FLOAT -> android.media.AudioFormat.ENCODING_PCM_FLOAT
            else -> return null
        }
        val mask = if (f.channelCount == 1) android.media.AudioFormat.CHANNEL_OUT_MONO else android.media.AudioFormat.CHANNEL_OUT_STEREO
        return runCatching {
            android.media.AudioFormat.Builder().setEncoding(enc).setSampleRate(f.sampleRate).setChannelMask(mask).build()
        }.getOrNull()
    }

    // ------------------------------------------------------------------ dati

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        if (!active) {
            return if (route == Route.Android) super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount) else {
                buffer.position(buffer.limit()); true
            }
        }
        if (pending == null) {
            if (!buffer.hasRemaining()) return true
            processor.queueInput(buffer)
            // eventuale resto non allineato a un frame intero: scartato
            if (buffer.hasRemaining()) buffer.position(buffer.limit())
            pending = processor.output
            pendingPts = presentationTimeUs
            pendingCount = encodedAccessUnitCount
            pendingScaled = false
        }
        val out = pending!!
        if (out.hasRemaining()) {
            if (!writeOut(out, pendingPts, pendingCount)) return false
        }
        pending = null
        return true
    }

    /** Scrive [out] nell'uscita attiva; true quando è stato preso tutto. */
    private fun writeOut(out: ByteBuffer, pts: Long, count: Int): Boolean {
        if (route == Route.Android) return super.handleBuffer(out, pts, count)
        if (needStart) {
            startPtsUs = pts.coerceAtLeast(0L)
            baseFrames = playedFrames()
            needStart = false
        }
        return when (route) {
            Route.UsbExclusive -> outputs.usb.write(out, pts, outFloat)
            Route.DirectTrack -> {
                val t = directTrack
                if (t == null) {
                    out.position(out.limit())
                    true
                } else {
                    val enc = if (outFloat) InternalDirectStream.ENC_FLOAT else InternalDirectStream.ENC_I16
                    t.write(out, enc, outChannels) && !out.hasRemaining()
                }
            }
            Route.AAudio -> {
                val s = aaudio
                if (s == null) {
                    out.position(out.limit())
                    true
                } else {
                    if (!pendingScaled) {
                        // AAudio non ha un volume: guadagno software (al 100% il PCM resta intatto)
                        if (volume < 0.9999f && outFloat) scaleFloat(out, volume)
                        pendingScaled = true
                    }
                    val enc = if (outFloat) InternalDirectStream.ENC_FLOAT else InternalDirectStream.ENC_I16
                    s.write(out, enc, outChannels)
                    !out.hasRemaining()
                }
            }
            Route.Android -> true
        }
    }

    private fun scaleFloat(b: ByteBuffer, g: Float) {
        val fb = b.duplicate().order(ByteOrder.nativeOrder())
        var i = fb.position()
        while (i + 4 <= fb.limit()) {
            fb.putFloat(i, fb.getFloat(i) * g)
            i += 4
        }
    }

    override fun playToEndOfStream() {
        if (active) {
            if (!eosQueued) {
                processor.queueEndOfStream()
                pending = processor.output
                eosQueued = true
            }
            val out = pending
            if (out != null && out.hasRemaining()) {
                if (!writeOut(out, pendingPts, pendingCount)) return
            }
            pending = null
        } else eosQueued = true
        if (route == Route.Android) super.playToEndOfStream()
    }

    override fun isEnded(): Boolean =
        if (route == Route.Android) super.isEnded()
        else eosQueued && pending?.hasRemaining() != true && !directPending()

    override fun hasPendingData(): Boolean =
        if (route == Route.Android) super.hasPendingData() || (pending?.hasRemaining() == true)
        else pending?.hasRemaining() == true || directPending()

    private fun directPending(): Boolean = when (route) {
        Route.UsbExclusive -> outputs.usb.hasPendingData()
        Route.DirectTrack -> directTrack?.hasPendingData() == true
        Route.AAudio -> (aaudio?.counters()?.getOrNull(1) ?: 0L) > 0L
        else -> false
    }

    // ------------------------------------------------------------------ orologio

    private fun playedFrames(): Long = when (route) {
        Route.DirectTrack -> directTrack?.playedFrames() ?: 0L
        Route.AAudio -> aaudio?.counters()?.getOrNull(0) ?: 0L
        else -> 0L
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long = when (route) {
        Route.Android -> super.getCurrentPositionUs(sourceEnded)
        Route.UsbExclusive -> outputs.usb.getCurrentPositionUs()
        else -> {
            if (needStart || outRate <= 0) AudioSink.CURRENT_POSITION_NOT_SET
            else startPtsUs + (playedFrames() - baseFrames).coerceAtLeast(0L) * C.MICROS_PER_SECOND / outRate
        }
    }

    // ------------------------------------------------------------------ trasporto

    private fun startDirect() {
        when (route) {
            Route.UsbExclusive -> outputs.usb.setPaused(false)
            Route.DirectTrack -> directTrack?.play()
            Route.AAudio -> aaudio?.start()
            else -> {}
        }
    }

    override fun play() {
        playing = true
        if (route == Route.Android) super.play() else startDirect()
    }

    override fun pause() {
        playing = false
        when (route) {
            Route.Android -> super.pause()
            Route.UsbExclusive -> outputs.usb.setPaused(true)
            Route.DirectTrack -> directTrack?.pause()
            Route.AAudio -> aaudio?.pause()
        }
    }

    override fun setVolume(volume: Float) {
        this.volume = volume
        super.setVolume(volume)
        directTrack?.setVolume(volume)
        if (route == Route.UsbExclusive) outputs.usb.setVolume(volume)
    }

    override fun flush() {
        pending = null
        eosQueued = false
        needStart = true
        if (active) processor.flush()
        when (route) {
            Route.UsbExclusive -> outputs.usb.flush()
            Route.DirectTrack -> directTrack?.flush()
            Route.AAudio -> aaudio?.flush()
            else -> {}
        }
        if (androidConfigured) super.flush()
    }

    override fun reset() {
        pending = null
        eosQueued = false
        active = false
        needStart = true
        processor.reset()
        closeLocalDirect()
        // la sessione USB resta aperta: il brano successivo la riusa senza rinegoziare il DAC
        if (route == Route.UsbExclusive) runCatching { outputs.usb.prepareForNextItem() }
        route = Route.Android
        androidConfigured = false
        super.reset()
    }

    override fun release() {
        closeLocalDirect()
        super.release()
    }

    private fun closeLocalDirect() {
        directTrack?.release()
        directTrack = null
        aaudio?.let { runCatching { it.pause() }; runCatching { it.closeStream() }; runCatching { it.close() } }
        aaudio = null
    }

    // ------------------------------------------------------------------ percorso del segnale

    private fun encLabel(e: Int) = when (e) {
        C.ENCODING_PCM_16BIT -> "16 bit"
        C.ENCODING_PCM_24BIT -> "24 bit"
        C.ENCODING_PCM_32BIT -> "32 bit"
        C.ENCODING_PCM_FLOAT -> "float 32 bit"
        else -> "codifica $e"
    }

    private fun khz(r: Int) = if (r % 1000 == 0) "${r / 1000} kHz" else String.format(Locale.ITALY, "%.1f kHz", r / 1000.0)

    private fun publishPath(input: Format, out: Format, raw: Boolean, bitPerfect: Boolean, note: String) {
        try {
            val steps = ArrayList<PathStep>()
            steps.add(
                PathStep(
                    "Sorgente", if (raw) "${khz(input.sampleRate)} · ${encLabel(input.pcmEncoding)}" else (input.sampleMimeType ?: "?"),
                    listOf("Canali" to "${input.channelCount}", "Decoder" to (input.codecs ?: input.sampleMimeType ?: "—")),
                )
            )
            val info = processor.info
            val resampled = out.sampleRate != input.sampleRate
            val dspMain = when {
                !active -> "Non applicato"
                bitPerfect -> "Bit-perfect: bypass totale"
                info.active -> "Attivo · ${info.stages} stadi"
                else -> "Bypass (DSP spento)"
            }
            val dspDetails = ArrayList<Pair<String, String>>()
            if (active) {
                dspDetails.add("Calcolo" to "doppia precisione, ${encLabel(out.pcmEncoding)} in uscita")
                dspDetails.add("Ricampionamento" to if (resampled) "soxr ${khz(input.sampleRate)} → ${khz(out.sampleRate)}" else "no (frequenza originale)")
                if (info.modules.isNotEmpty()) dspDetails.add("Moduli" to info.modules.joinToString(", "))
                if (info.active) dspDetails.add("Ingresso" to String.format(Locale.ITALY, "%+.1f dB (headroom %.1f dB)", info.inputGainDb, info.headroomDb))
                if (info.latencyMs > 0f) dspDetails.add("Latenza" to String.format(Locale.ITALY, "%.1f ms", info.latencyMs))
            }
            steps.add(PathStep("DSP", dspMain, dspDetails, ok = if (bitPerfect) (active && !resampled) else null))
            val volMain = when (route) {
                Route.UsbExclusive -> if (outputs.usb.usesHardwareVolume()) "Volume hardware del DAC" else "Volume software (PCM scalato)"
                Route.AAudio -> "Volume software (al 100% PCM intatto)"
                else -> "Volume di sistema"
            }
            steps.add(PathStep("Volume", volMain, listOf("Livello app" to String.format(Locale.ITALY, "%.0f %%", volume * 100))))
            val outDetails = ArrayList<Pair<String, String>>()
            val outMain: String
            var outOk: Boolean? = null
            when (route) {
                Route.UsbExclusive -> {
                    val u = outputs.usb
                    outMain = "USB esclusiva · ${khz(u.currentRateHz())} · ${u.activeWireBits()} bit sul cavo"
                    outDetails.add("Mixer di Android" to "saltato (il DAC è gestito dall'app)")
                    outDetails.add("Clock del DAC" to if (u.lastHardwareRateHz() > 0) khz(u.lastHardwareRateHz()) + if (u.isClockMatched()) " (coincide)" else " (diverso)" else "non letto")
                    outOk = u.isClockMatched()
                }
                Route.DirectTrack -> {
                    outMain = "AudioTrack DIRECT · ${khz(out.sampleRate)} · 24 bit"
                    outDetails.add("Verifica" to "Android rifiuta il cambio di velocità: traccia non mixata")
                    outOk = true
                }
                Route.AAudio -> {
                    val i = aaudio?.info()
                    outMain = "AAudio esclusiva · ${khz(i?.sampleRateHz ?: out.sampleRate)} · ${InternalDirectStream.labelOf(i?.encoding ?: -1)}"
                    outDetails.add("MMAP" to if (i?.mmap == true) "sì (scrive nel buffer dell'HAL)" else "no")
                    i?.let { outDetails.add("Buffer del dispositivo" to "${it.deviceBufferFrames} frame") }
                    outOk = i?.mmap
                }
                Route.Android -> {
                    outMain = "Android AudioTrack · ${khz(out.sampleRate)} · ${encLabel(out.pcmEncoding)}"
                    outDetails.add("Mixer di Android" to "sì (il sistema può ricampionare)")
                    if (bitPerfect) {
                        val granted = outputs.usbBitPerfect.isConfigured()
                        outDetails.add("Bit-perfect del mixer" to if (granted) "concesso (Android 14+)" else "non disponibile")
                        outOk = granted
                    }
                }
            }
            if (note.isNotEmpty()) outDetails.add("Nota" to note)
            steps.add(PathStep("Uscita", outMain, outDetails, outOk))
            val devName = when (route) {
                Route.UsbExclusive -> outputs.usbAudioDevice()?.productName ?: "DAC USB"
                Route.DirectTrack -> directTrack?.routedDeviceName() ?: monitor.name
                else -> monitor.name.ifBlank { monitor.kind.label }
            }
            steps.add(PathStep("Dispositivo", devName.ifBlank { monitor.kind.label }, listOf("Tipo" to monitor.kind.label)))
            val bpReal = bitPerfect && active && !resampled && when (route) {
                Route.UsbExclusive -> outputs.usb.isClockMatched() && (outputs.usb.usesHardwareVolume() || volume >= 0.9999f)
                Route.DirectTrack -> true
                Route.AAudio -> volume >= 0.9999f
                Route.Android -> outputs.usbBitPerfect.isConfigured()
            }
            val verdict = when {
                bpReal -> "Bit-perfect: il PCM arriva intatto all'uscita"
                route != Route.Android -> "Mixer di Android saltato" + if (bitPerfect) " (bit-perfect non pieno: vedi i dettagli)" else ""
                bitPerfect -> "Bit-perfect richiesto ma il suono passa dal mixer di Android"
                else -> "Il suono passa dal mixer di Android"
            }
            outputs.path = SignalPath(route, bitPerfect, steps, verdict, bpReal)
        } catch (e: Exception) {
            AppLog.w("Uscita", "Percorso del segnale non aggiornato", e)
        }
    }
}
