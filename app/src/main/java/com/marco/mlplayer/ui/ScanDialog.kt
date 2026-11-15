package com.marco.mlplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

/** Avanzamento della scansione dopo aver scelto la cartella della musica. */
@Composable
fun ScanDialog(env: AppEnv) {
    val lib = env.library
    val scan = lib.scan
    val cs = MaterialTheme.colorScheme
    // la scansione parte un attimo dopo la scelta: aspetto che inizi prima di dire "finita"
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(scan.running) { if (scan.running) started = true }
    LaunchedEffect(Unit) { delay(1500); started = true }
    val finished = started && !scan.running
    fun close() { env.showScanDialog = false }

    AlertDialog(
        onDismissRequest = { if (finished) close() },
        properties = DialogProperties(dismissOnClickOutside = finished),
        icon = { Icon(if (finished) Icons.Filled.CheckCircle else Icons.Filled.LibraryMusic, null, tint = cs.primary) },
        title = { Text(if (finished) "Scansione completata" else "Scansione della musica") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                if (!finished) {
                    Text(scan.message.ifBlank { "Ricerca dei file..." }, style = MaterialTheme.typography.bodyMedium)
                    if (scan.total > 0) {
                        LinearProgressIndicator(
                            progress = { (scan.done.toFloat() / scan.total).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("${scan.done} di ${scan.total} brani", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    Text(
                        "Puoi chiudere questa finestra: la scansione continua da sola.",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                    )
                } else if (lib.tracks.isEmpty()) {
                    Text("Nessun brano trovato in questa cartella. Prova a sceglierne un'altra.")
                } else {
                    Text(
                        "${lib.tracks.size} brani · ${lib.albums.size} album · ${lib.artists.size} artisti",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        },
        confirmButton = {
            if (finished && lib.tracks.isNotEmpty()) Button(onClick = {
                close()
                env.libraryNav.popToRoot()
                env.select(Tab.Library)
            }) { Text("Vai alla libreria") }
            else if (finished) Button(onClick = { close(); env.actions?.pickFolder() }) { Text("Scegli un'altra cartella") }
            else TextButton(onClick = { close() }) { Text("Continua in background") }
        },
        dismissButton = { if (finished) TextButton(onClick = { close() }) { Text("Chiudi") } },
    )
}
