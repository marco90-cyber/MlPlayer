// SPDX-License-Identifier: GPL-3.0-or-later
package com.marco.mlplayer.audio.direct

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.decent.usbaudio.UsbAudioDevice
import com.marco.mlplayer.data.Settings
import com.marco.mlplayer.util.AppLog

/** Dove finisce il suono. */
enum class Route(val label: String) {
    Android("Android AudioTrack (mixer di sistema)"),
    UsbExclusive("USB esclusiva (driver diretto al DAC)"),
    DirectTrack("Uscita diretta (AudioTrack DIRECT)"),
    AAudio("AAudio esclusiva (MMAP)"),
}

/**
 * Una tappa del percorso del segnale, per la pagina "Percorso del segnale".
 * [ok] = verificato (true), problema (false), non verificabile (null).
 */
data class PathStep(val title: String, val main: String, val details: List<Pair<String, String>> = emptyList(), val ok: Boolean? = null)

/** Fotografia del percorso del segnale scattata a ogni configurazione dell'uscita. */
data class SignalPath(
    val route: Route = Route.Android,
    val bitPerfect: Boolean = false,
    val steps: List<PathStep> = emptyList(),
    val verdict: String = "In attesa di un brano",
    val bitPerfectReal: Boolean = false,
    val report: PathReport = PathReport(),
)

/** Dati per il pannello "Percorso del segnale" in stile LastWave (stessi nodi e righe). */
data class PathReport(
    val valid: Boolean = false,
    val sourceRateHz: Int = 0,
    val sourceBits: Int = 0,
    val dspRateHz: Int = 0,
    val dspSummary: String = "Float32 / PCM",
    val dspOn: Boolean = false,
    val eqLine: String = "Equ Off • Balance (0%)",
    val toneLine: String = "Tone Off",
    val crossfadeLine: String = "No fading / Short crossfade",
    val buffersLine: String = "",
    val dspLatencyMs: Int = 0,
    val volumeMode: String = "System Volume",
    val volumeDetail: String = "0.0 dB (Unity Gain)",
    val preampDb: Float = 0f,
    val engineName: String = "Android AudioTrack",
    val outRateHz: Int = 0,
    val outBits: Int = 32,
    val flags: List<String> = emptyList(),
    val outLatencyMs: Int = 0,
    val deviceName: String = "Android Default Output",
    val fidelity: Int = 65,
    val dspScore: String = "—",
    /** "BIT-PERFECT", "DIRECT → HAL", "USB EXCLUSIVE" oppure "CONTROLLA PERCORSO". */
    val pill: String = "CONTROLLA PERCORSO",
    val pillLevel: Int = 0,
    val verdictText: String = "",
    val usbExclusive: Boolean = false,
    val direct: Boolean = false,
    val routeName: String = "",
    val directStatus: String = "",
    val endpoint: String = "—",
)

/**
 * Uscite che saltano il mixer di Android (idee e codice da LastWave, GPL-3.0; driver USB di
 * decent-player, MIT): USB esclusiva al DAC, AudioTrack DIRECT e AAudio esclusiva (MMAP).
 * Qui stanno le istanze condivise, le preferenze e lo stato mostrato nell'interfaccia.
 */
class DirectOutputs(context: Context, private val settings: Settings) {
    private val app = context.applicationContext
    val audioManager: AudioManager? = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val usbManager: UsbManager? = app.getSystemService(Context.USB_SERVICE) as? UsbManager

    val usb = ExclusiveUsbOutput(app)
    val usbBitPerfect = UsbBitPerfectOutput(audioManager)

    /** Ultimo percorso del segnale (osservabile da Compose). */
    var path by mutableStateOf(SignalPath())

    /** Incrementato da "Controlla percorso" per forzare una nuova lettura. */
    var checkToken by mutableIntStateOf(0)

    init {
        usb.setWanted(settings.usbExclusive)
        usbBitPerfect.setEnabled(settings.bitPerfect)
    }

    /** Impostazioni del DSP attuali (per il percorso del segnale). */
    val dspSettings get() = settings.dsp

    /** Disturbi (underrun) dell'uscita diretta in uso, se misurabili. */
    @Volatile var underrunsProvider: (() -> Long)? = null

    val usbWanted: Boolean get() = settings.usbExclusive
    val directWanted: Boolean get() = settings.directOutput
    val bitPerfect: Boolean get() = settings.bitPerfect

    fun setUsbExclusive(on: Boolean) {
        settings.usbExclusive = on
        usb.setWanted(on)
        if (on) ensureUsbPermission()
        AppLog.i("Uscita", "USB esclusiva ${if (on) "attivata" else "disattivata"} (vale dal prossimo brano)")
    }

    fun setDirectOutput(on: Boolean) {
        settings.directOutput = on
        AppLog.i("Uscita", "Uscita diretta ${if (on) "attivata" else "disattivata"} (vale dal prossimo brano)")
    }

    fun setBitPerfect(on: Boolean) {
        settings.bitPerfect = on
        usbBitPerfect.setEnabled(on)
        AppLog.i("Uscita", "Bit-perfect ${if (on) "attivato" else "disattivato"} (vale dal prossimo brano)")
    }

    /** Primo DAC USB Audio Class collegato (anche senza permesso). */
    fun usbAudioDevice(): UsbDevice? = runCatching {
        usbManager?.deviceList?.values?.firstOrNull { dev ->
            (0 until dev.interfaceCount).any { dev.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_AUDIO }
        }
    }.getOrNull()

    fun usbDacReady(): Boolean {
        val d = usbAudioDevice() ?: return false
        return runCatching { usbManager?.hasPermission(d) == true }.getOrDefault(false)
    }

    /** Chiede il permesso USB per il DAC collegato (serve per l'uscita esclusiva). */
    fun ensureUsbPermission() {
        val d = usbAudioDevice() ?: return
        if (runCatching { usbManager?.hasPermission(d) == true }.getOrDefault(false)) return
        runCatching {
            UsbAudioDevice.getInstance(app).requestPermission(d) { granted ->
                AppLog.i("Uscita", "Permesso USB per ${d.productName}: ${if (granted) "concesso" else "negato"}")
            }
        }.onFailure { AppLog.w("Uscita", "Richiesta permesso USB non riuscita", it) }
    }

    /** Dispositivo di uscita USB visto da Android (per il bit-perfect del mixer, Android 14+). */
    fun androidUsbOutput(): AudioDeviceInfo? = runCatching {
        audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
    }.getOrNull()

    /** "Controlla percorso": rilegge quello che si può verificare adesso. */
    fun recheck(): String = runCatching {
        when (path.route) {
            Route.UsbExclusive -> {
                val want = usb.currentRateHz()
                val r = UsbAudioDevice.getInstance(app).readSampleRate()
                when {
                    r <= 0 -> "DAC: la frequenza non si può leggere"
                    r == want -> "DAC a ${r} Hz: coincide con il flusso"
                    else -> "DAC a ${r} Hz invece di ${want} Hz"
                }
            }
            Route.Android -> {
                val mixer = audioManager?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE) ?: "?"
                val bp = if (bitPerfect) (if (usbBitPerfect.isConfigured()) " · bit-perfect concesso" else " · bit-perfect non concesso") else ""
                "Mixer di Android a ${mixer} Hz$bp"
            }
            Route.DirectTrack -> "Uscita DIRECT verificata all'avvio del brano"
            Route.AAudio -> "AAudio esclusiva attiva"
        }
    }.getOrElse { "Controllo non riuscito: ${it.message}" }

    fun release() {
        runCatching { usb.release() }
    }
}
