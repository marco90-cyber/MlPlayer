package com.marco.mlplayer.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marco.mlplayer.audio.direct.Route
import com.marco.mlplayer.data.Track
import com.marco.mlplayer.data.rememberCover
import com.marco.mlplayer.util.khz
import com.marco.mlplayer.util.mmss
import kotlinx.coroutines.delay
import kotlin.math.log10
import kotlin.math.pow

/** Volume: cursore 0..1 con scala in dB (60 dB di escursione). */
private fun gainToPos(g: Float): Float = if (g <= 0.001f) 0f else (1f + 20f * log10(g) / 60f).coerceIn(0f, 1f)
private fun posToGain(p: Float): Float = if (p <= 0.005f) 0f else 10f.pow((p - 1f) * 60f / 20f)

/** Qualità della sorgente, per il chip sotto il lettore. */
private fun sourceLabel(t: Track): String = buildList {
    add(t.formatLabel)
    val q = buildList {
        if (t.bits > 0) add("${t.bits} bit")
        if (t.sampleRate > 0) add(khz(t.sampleRate))
    }.joinToString(" / ")
    if (q.isNotBlank()) add(q) else if (t.bitrateKbps > 0) add("${t.bitrateKbps} kbps")
}.joinToString(" · ")

/** Dove esce il suono, per il chip sotto il lettore. */
private fun outputLabel(env: AppEnv): String {
    val e = env.engine
    val path = e.outputs.path
    val om = e.outputMonitor
    val dev = path.steps.firstOrNull { it.title == "Dispositivo" }?.main
        ?: om.name.takeIf { it.isNotBlank() && it != om.kind.label } ?: om.kind.label
    val mode = when {
        path.bitPerfect -> "bit-perfect"
        path.route != Route.Android -> "diretta"
        env.settings.dsp.master -> "DSP"
        else -> null
    }
    return listOfNotNull(dev, mode).joinToString(" · ")
}

/** Sezione Lettore: copertina, brano, avanzamento, comandi, VU meter e qualità dell'uscita. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingPage(env: AppEnv) {
    val e = env.engine
    val t = e.currentTrack
    val cs = MaterialTheme.colorScheme
    val fb = env.feedback

    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var drag by remember { mutableFloatStateOf(-1f) }
    LaunchedEffect(t?.id, e.isPlaying) {
        while (true) {
            pos = e.player.currentPosition
            val d = e.player.duration
            dur = if (d > 0) d else (t?.durationMs ?: 0L)
            delay(250)
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // barra in alto
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(start = 24.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (t != null && e.queueSize > 0) "Brano ${e.queueIndex + 1} di ${e.queueSize}" else "In riproduzione",
                style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { fb.tapScreen(); env.overlay = Overlay.Analyzer }) {
                Icon(Icons.Filled.Timeline, contentDescription = "Analizzatore")
            }
            IconButton(onClick = { fb.tapScreen(); env.overlay = Overlay.Output }) {
                Icon(Icons.Filled.Headphones, contentDescription = "Uscita audio")
            }
            IconButton(onClick = { fb.tapScreen(); env.showQueue = true }, enabled = t != null) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Coda")
            }
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // copertina: il quadrato più grande che entra nello spazio rimasto
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val side = if (maxWidth < maxHeight) maxWidth else maxHeight
                val file = remember(t?.id, env.library.revision) { t?.let { env.library.trackCoverFile(it) } }
                val cover = rememberCover(file, 720)
                Box(
                    Modifier.size(side).clip(RoundedCornerShape(28.dp)).background(cs.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (cover != null) {
                        Image(cover, contentDescription = "Copertina", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(Icons.Filled.MusicNote, null, tint = cs.onPrimaryContainer, modifier = Modifier.size(side / 3))
                    }
                    // Testo e menu del brano, in basso a destra sulla copertina
                    if (t != null) Row(
                        Modifier.align(Alignment.BottomEnd).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val chip = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = cs.surfaceContainerHighest.copy(alpha = 0.85f), contentColor = cs.onSurface
                        )
                        FilledTonalIconButton(onClick = { fb.tapScreen(); env.openPage(Overlay.Lyrics) }, colors = chip) {
                            Icon(Icons.Filled.Lyrics, contentDescription = "Testo")
                        }
                        FilledTonalIconButton(onClick = { fb.tapScreen(); env.showTrackMenu = true }, colors = chip) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Altre azioni del brano")
                        }
                    }
                }
            }

            // titolo e artista
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        t?.title ?: "Nessun brano",
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2500)
                    )
                    Text(
                        if (t != null) listOf(t.artist, t.album).filter { it.isNotBlank() }.joinToString(" — ")
                        else "Scegli la musica dalla Libreria",
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (t != null && e.hiResNow) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .border(1.dp, cs.primary, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) { Text("Hi-Res", style = MaterialTheme.typography.labelMedium, color = cs.primary) }
                }
            }

            // avanzamento
            Column {
                val frac = if (drag >= 0f) drag else if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
                Slider(
                    value = frac,
                    onValueChange = { drag = it },
                    onValueChangeFinished = {
                        if (drag >= 0f) {
                            e.seekToFraction(drag)
                            pos = (drag * dur).toLong()
                        }
                        drag = -1f
                    },
                    enabled = t != null,
                    modifier = Modifier.fillMaxWidth().height(28.dp),
                )
                Row(Modifier.fillMaxWidth()) {
                    val shown = if (drag >= 0f) (drag * dur).toLong() else pos
                    Text(mmss(shown), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(if (dur > 0) mmss(dur) else "--:--", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
            }

            // comandi
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconToggleButton(checked = e.shuffleOn, onCheckedChange = { fb.tapScreen(); e.toggleShuffle() }) {
                    Icon(Icons.Filled.Shuffle, contentDescription = "Casuale", tint = if (e.shuffleOn) cs.primary else cs.onSurfaceVariant)
                }
                FilledTonalIconButton(
                    onClick = { fb.tapScreen(); e.previous() },
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = cs.surfaceContainerHighest, contentColor = cs.onSurface),
                ) { Icon(Icons.Filled.SkipPrevious, contentDescription = "Brano precedente", modifier = Modifier.size(28.dp)) }
                FilledIconButton(
                    onClick = { fb.tapScreen(); env.playPause() },
                    modifier = Modifier.size(76.dp),
                ) {
                    Icon(
                        if (e.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (e.isPlaying) "Pausa" else "Riproduci",
                        modifier = Modifier.size(36.dp)
                    )
                }
                FilledTonalIconButton(
                    onClick = { fb.tapScreen(); e.next() },
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = cs.surfaceContainerHighest, contentColor = cs.onSurface),
                ) { Icon(Icons.Filled.SkipNext, contentDescription = "Brano successivo", modifier = Modifier.size(28.dp)) }
                IconToggleButton(checked = e.repeatMode != 0, onCheckedChange = { fb.tapScreen(); e.cycleRepeat() }) {
                    Icon(
                        if (e.repeatMode == 2) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = when (e.repeatMode) { 1 -> "Ripeti tutto"; 2 -> "Ripeti brano"; else -> "Ripeti" },
                        tint = if (e.repeatMode != 0) cs.primary else cs.onSurfaceVariant
                    )
                }
            }

            // volume dell'app
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.VolumeDown, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Slider(
                    value = gainToPos(e.volume),
                    onValueChange = { p -> e.changeVolume(posToGain(p) - e.volume) },
                    modifier = Modifier.weight(1f).height(28.dp).padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = cs.onSurfaceVariant, activeTrackColor = cs.onSurfaceVariant,
                        inactiveTrackColor = cs.surfaceContainerHighest,
                    ),
                )
                Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }

            if (env.settings.showVu) VuCard(env)

            // qualità e uscita: toccando si apre il percorso del segnale
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (t != null) {
                    AssistChip(
                        onClick = { fb.tapScreen(); env.showSignalPath = true },
                        label = { Text(sourceLabel(t), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Filled.GraphicEq, null, Modifier.size(AssistChipDefaults.IconSize)) },
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                AssistChip(
                    onClick = { fb.tapScreen(); env.showSignalPath = true },
                    label = { Text(outputLabel(env), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Filled.Headphones, null, Modifier.size(AssistChipDefaults.IconSize)) },
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}

/** Coda di riproduzione: tocca un brano per saltarci. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(env: AppEnv) {
    val e = env.engine
    val cs = MaterialTheme.colorScheme
    val tracks = e.queueTracks
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (e.queueIndex - 2).coerceAtLeast(0))
    ModalBottomSheet(onDismissRequest = { env.showQueue = false }, sheetState = state, containerColor = cs.surfaceContainerLow) {
        Text(
            "Coda · ${tracks.size} brani",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 24.dp, bottom = 8.dp)
        )
        LazyColumn(state = list, modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(tracks) { i, tr ->
                val now = i == e.queueIndex
                ListItem(
                    headlineContent = {
                        Text(
                            tr.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (now) cs.primary else cs.onSurface,
                            fontWeight = if (now) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    supportingContent = if (tr.artist.isNotBlank()) ({ Text(tr.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) }) else null,
                    leadingContent = {
                        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                            if (now) Icon(Icons.Filled.GraphicEq, null, tint = cs.primary)
                            else Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = cs.surfaceContainerLow),
                    modifier = Modifier.clickable { env.feedback.tapScreen(); e.playAt(i) }
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
