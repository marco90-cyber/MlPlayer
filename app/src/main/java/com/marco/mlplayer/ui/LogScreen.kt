package com.marco.mlplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marco.mlplayer.util.AppLog

@Composable
fun LogScreen(env: AppEnv) {
    val rev = AppLog.revision
    val lines = AppLog.snapshot()
    val listState = rememberLazyListState()

    LaunchedEffect(rev) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { env.overlay = Overlay.None }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
            }
            Text(
                "Log (${lines.size})", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { env.actions?.shareLog() }) {
                Icon(Icons.Filled.Share, contentDescription = "Condividi")
            }
            IconButton(onClick = { AppLog.clear() }) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = "Cancella")
            }
        }
        val cs = MaterialTheme.colorScheme
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(lines) { _, line ->
                val color = when {
                    line.contains(" E/") -> cs.error
                    line.contains(" W/") -> cs.tertiary
                    line.contains(" D/") -> cs.onSurfaceVariant
                    else -> cs.onSurface
                }
                Text(line, color = color, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
