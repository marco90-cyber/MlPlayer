package com.marco.mlplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.marco.mltheme.MLGroup
import com.marco.mltheme.MLSwitchRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Percorso del segnale (idea da LastWave): stato attuale, pulsante per aprire il pannello completo
 * e le tre opzioni d'uscita. Si usa nelle impostazioni e nella pagina Uscita audio.
 */
@Composable
fun SignalPathView(env: AppEnv, showOptions: Boolean = true) {
    val cs = MaterialTheme.colorScheme
    val outs = env.engine.outputs
    val path = outs.path
    var checked by remember { mutableStateOf("") }
    val inGroup = showOptions
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.padding(horizontal = if (inGroup) 32.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                path.verdict, style = MaterialTheme.typography.bodyLarge,
                color = if (path.bitPerfectReal) cs.primary else cs.onSurface
            )
            if (checked.isNotEmpty()) Text(checked, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { env.feedback.tapScreen(); env.showSignalPath = true }) {
                    Icon(Icons.Filled.Route, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Apri il percorso")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    env.feedback.tapScreen()
                    checked = outs.recheck() + " · " + SimpleDateFormat("HH:mm:ss", Locale.ITALY).format(Date())
                }) {
                    Icon(Icons.Filled.Refresh, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Controlla")
                }
            }
        }

        if (showOptions) {
            MLGroup("Uscita") {
                MLSwitchRow(
                    "USB esclusiva",
                    "Il PCM va dritto al DAC con il driver USB dell'app: il mixer di Android non lo tocca. " +
                        "Volume hardware del DAC se c'è, altrimenti software. Serve il permesso USB.",
                    env.settings.usbExclusive
                ) { outs.setUsbExclusive(it) }
                MLSwitchRow(
                    "Uscita diretta",
                    "Senza DAC USB: AudioTrack a 24 bit alla frequenza del brano su un'uscita DIRECT (verificata) " +
                        "oppure AAudio esclusiva (MMAP), anche per altoparlante, cuffie e Bluetooth se il telefono lo permette.",
                    env.settings.directOutput
                ) { outs.setDirectOutput(it) }
                MLSwitchRow(
                    "Bit-perfect",
                    "Niente DSP, niente ricampionamento: il brano arriva all'uscita così com'è (al 100% del volume). " +
                        "Con il mixer di Android serve Android 14 e un DAC USB che lo supporti.",
                    env.settings.bitPerfect
                ) { outs.setBitPerfect(it) }
            }
            Text(
                "Le modifiche valgono dal prossimo brano (o dopo pausa e ripresa del brano).",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}
