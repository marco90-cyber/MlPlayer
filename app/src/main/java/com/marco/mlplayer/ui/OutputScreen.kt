package com.marco.mlplayer.ui

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Locale


private data class OutDevice(val title: String, val details: List<String>)

private fun typeName(t: Int): String = when (t) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Altoparlante del telefono"
    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Auricolare di sistema"
    AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Cuffie con microfono (jack)"
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Cuffie (jack)"
    AudioDeviceInfo.TYPE_LINE_ANALOG -> "Uscita analogica"
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth (A2DP)"
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth (chiamate)"
    AudioDeviceInfo.TYPE_HDMI -> "HDMI"
    AudioDeviceInfo.TYPE_USB_DEVICE -> "USB (dispositivo audio)"
    AudioDeviceInfo.TYPE_USB_HEADSET -> "USB (cuffie)"
    AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB (accessorio)"
    AudioDeviceInfo.TYPE_HEARING_AID -> "Apparecchio acustico"
    AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE (cuffie)"
    AudioDeviceInfo.TYPE_BLE_SPEAKER -> "Bluetooth LE (altoparlante)"
    else -> "Tipo $t"
}

private fun encName(e: Int): String? = when (e) {
    AudioFormat.ENCODING_PCM_16BIT -> "16 bit"
    AudioFormat.ENCODING_PCM_24BIT_PACKED -> "24 bit"
    AudioFormat.ENCODING_PCM_32BIT -> "32 bit"
    AudioFormat.ENCODING_PCM_FLOAT -> "float"
    else -> null
}

private fun readDevices(ctx: Context): List<OutDevice> {
    val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return emptyList()
    return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { d ->
        val det = ArrayList<String>()
        if (d.sampleRates.isNotEmpty()) {
            det.add("Frequenze dichiarate: " + d.sampleRates.sorted().joinToString(", ") { "${it / 1000.0} kHz".replace(".0 ", " ") })
        }
        val enc = d.encodings.toList().mapNotNull { encName(it) }.distinct()
        if (enc.isNotEmpty()) det.add("Formati dichiarati: " + enc.joinToString(", "))
        if (d.channelCounts.isNotEmpty()) det.add("Canali dichiarati: " + d.channelCounts.sorted().joinToString(", "))
        val name = d.productName?.toString().orEmpty()
        OutDevice(typeName(d.type) + if (name.isNotBlank()) " · $name" else "", det)
    }
}

/** Pagina "Uscita audio" con la barra in alto. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutputPage(env: AppEnv) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Uscita audio") },
            navigationIcon = {
                IconButton(onClick = { env.feedback.tapScreen(); env.overlay = Overlay.None }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        Column(Modifier.weight(1f).navigationBarsPadding()) { OutputScreen(env) }
    }
}

@Composable
fun OutputScreen(env: AppEnv) {
    val ctx = LocalContext.current
    val devices by produceState(emptyList<OutDevice>()) {
        while (true) {
            value = readDevices(ctx)
            delay(1500)
        }
    }
    val limiterDb by produceState(0f) {
        while (true) {
            value = env.engine.eqProcessor.meters().limiterDb
            delay(600)
        }
    }
    val info = env.engine.eqProcessor.info
    val track = env.engine.currentTrack
    val engine = env.engine

    Column(
        Modifier
            .fillMaxSize()
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Section("Percorso del segnale") { SignalPathView(env, showOptions = false) }
            Section("Uscita attiva") {
                val om = engine.outputMonitor
                KV(
                    if (om.exact) "Dispositivo (indicato da Android)" else "Dispositivo (stimato dall'elenco uscite)",
                    om.kind.label + if (om.name.isNotBlank()) " · ${om.name}" else ""
                )
                KV(
                    "Uscita adattiva",
                    if (env.settings.adaptiveOutput) engine.outputMonitor.lastPlan else "spenta"
                )
            }

            Section("Flusso audio in uso") {
                if (track == null) {
                    Line("Nessun brano in riproduzione")
                } else {
                    val fileRate = if (track.sampleRate > 0) String.format(Locale.ITALY, "%.1f kHz", track.sampleRate / 1000.0) else "?"
                    val fileBits = if (track.bits > 0) "${track.bits} bit" else "profondità non rilevata"
                    KV("1. File", "${track.formatLabel} · $fileRate · $fileBits")
                    if (info.sampleRate > 0) {
                        KV("2. Uscita del decoder", "${info.inEncoding} · ${rate(info.sampleRate)} · ${info.channels} can.")
                        KV(
                            "3. DSP",
                            when {
                                info.bypass -> "bypass (nessuna elaborazione)"
                                else -> "attivo · ${info.stages} filtri" + if (info.limiter) " · limiter" else ""
                            }
                        )
                        KV("4. Uscita del DSP", "${info.outEncoding} · ${rate(info.sampleRate)}")
                    } else {
                        KV("2. Decoder / DSP", "dati non disponibili")
                    }
                    if (engine.trackSampleRate > 0) {
                        KV("5. Verso Android (AudioTrack)", "${engine.trackEncoding} · ${rate(engine.trackSampleRate)}" +
                            if (engine.trackOffload) " · offload" else "")
                    } else {
                        KV("5. Verso Android (AudioTrack)", "dati non disponibili")
                    }
                }
            }

            Section("Elaborazione DSP") {
                KV("Guadagno in ingresso", String.format(Locale.ITALY, "%+.1f dB", info.inputGainDb))
                KV("ReplayGain", info.replayGainDb?.let { String.format(Locale.ITALY, "%+.1f dB", it) } ?: "non applicato")
                KV("Latenza del limiter", if (info.limiter) String.format(Locale.ITALY, "%.1f ms", info.latencyMs) else "limiter spento")
                if (info.limiter) {
                    KV(
                        "Riduzione limiter (ultimo 0,6 s)",
                        if (limiterDb > -0.05f) "nessuna" else String.format(Locale.ITALY, "%.1f dB", limiterDb)
                    )
                }
                KV("Calcolo", "filtri in doppia precisione, campioni a 32 bit float")
            }

            Section("Uscite audio presenti (dichiarate da Android)") {
                if (devices.isEmpty()) Line("Nessuna uscita rilevata")
                for (d in devices) {
                    Text(d.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    for (x in d.details) Text(x, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                }
            }

            Section("Cosa non è verificabile") {
                Line(
                    "L'audio passa dal mixer di Android: il sistema può ricampionare o convertire il formato " +
                        "prima dell'uscita, quindi non posso dichiarare l'uscita bit-perfect. " +
                        "Per saltarlo usa USB esclusiva o Uscita diretta nel Percorso del segnale."
                )
                Line(
                    "I valori sopra (punti 2-5) sono quelli realmente comunicati da Media3 e dal motore DSP; " +
                        "le frequenze e i formati delle uscite sono solo quelli che Android dichiara per il dispositivo."
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun rate(hz: Int): String =
    if (hz % 1000 == 0) "${hz / 1000} kHz" else String.format(Locale.ITALY, "%.1f kHz", hz / 1000.0)

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun KV(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Line(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}
