package com.marco.mlplayer.ui

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marco.mlplayer.data.Track
import com.marco.mlplayer.data.rememberCover
import kotlinx.coroutines.delay
import java.util.Locale

/** Colore medio della copertina (per lo sfondo), scurito perché i testi bianchi restino leggibili. */
private fun coverTint(img: ImageBitmap?): Color {
    if (img == null) return Color(0xFF1A1A1E)
    return runCatching {
        val b = Bitmap.createScaledBitmap(img.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false), 1, 1, true)
        val c = b.getPixel(0, 0)
        val r = (c shr 16 and 0xFF) / 255f
        val g = (c shr 8 and 0xFF) / 255f
        val bl = (c and 0xFF) / 255f
        Color(r * 0.35f, g * 0.35f, bl * 0.35f)
    }.getOrDefault(Color(0xFF1A1A1E))
}

private fun mmss(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.ITALY, "%d:%02d", s / 60, s % 60)
}

private fun qualityLine(t: Track): String {
    val parts = ArrayList<String>()
    parts.add(t.formatLabel)
    val q = ArrayList<String>()
    if (t.bits > 0) q.add("${t.bits} bit")
    if (t.sampleRate > 0) q.add(if (t.sampleRate % 1000 == 0) "${t.sampleRate / 1000} kHz" else String.format(Locale.ITALY, "%.1f kHz", t.sampleRate / 1000.0))
    if (q.isNotEmpty()) parts.add(q.joinToString(" / "))
    if (t.bitrateKbps > 0) parts.add("${t.bitrateKbps} kbps")
    return parts.joinToString(" • ")
}

/**
 * Layout "Copertina" del lettore (ispirato a LastWave): copertina a tutto schermo che sfuma nel
 * colore dell'album, titolo grande, riga della qualità che apre il percorso del segnale, barra di
 * avanzamento e comandi a pillola.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CoverPlayerPanel(env: AppEnv) {
    val e = env.engine
    val t = e.currentTrack
    val fb = env.feedback
    val coverFile = remember(t?.albumKey, env.library.revision) { t?.let { env.library.coverFile(it.albumKey) } }
    val cover = rememberCover(coverFile, 1080)
    val tint = remember(cover) { coverTint(cover) }
    val accent = Hifi.finish.vfd

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

    BoxWithConstraints(
        Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(Color.Black)
    ) {
        val artH = maxHeight * 0.62f
        // copertina in alto, a tutta larghezza
        Box(Modifier.fillMaxWidth().height(artH)) {
            if (cover != null) {
                Image(cover, contentDescription = "Copertina", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(tint), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.MusicNote, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(96.dp))
                }
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0f to Color.Transparent, 0.55f to Color.Transparent, 1f to Color.Black)
                )
            )
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                t?.title ?: "Nessun brano", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2500)
            )
            Text(
                t?.let { listOf(it.artist, it.album).filter { s -> s.isNotBlank() }.joinToString(" · ") } ?: "Scegli la musica da LIBRERIA",
                color = Color.White.copy(alpha = 0.72f), fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(18.dp))

            // barra di avanzamento
            val frac = if (drag >= 0f) drag else if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
            val seek = rememberUpdatedState { v: Float -> if (t != null) { e.seekToFraction(v); pos = (v * dur).toLong() } }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .semantics { contentDescription = "Posizione nel brano" }
                    .pointerInput(Unit) {
                        detectTapGestures { o -> seek.value((o.x / size.width).coerceIn(0f, 1f)) }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = { if (drag >= 0f) seek.value(drag); drag = -1f },
                            onDragCancel = { drag = -1f },
                        ) { c, _ -> c.consume(); drag = (c.position.x / size.width).coerceIn(0f, 1f) }
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.25f)))
                Box(Modifier.fillMaxWidth(frac).height(4.dp).clip(RoundedCornerShape(2.dp)).background(accent))
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .padding(start = (maxWidth - 18.dp) * frac)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Text(mmss(if (drag >= 0f) (drag * dur).toLong() else pos), color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text(if (dur > 0) "-" + mmss(dur - pos) else "--:--", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            }
            // qualità: apre il percorso del segnale
            if (t != null) {
                Row(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button) { fb.tapScreen(); env.showSignalPath = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.GraphicEq, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    val bp = if (e.outputs.path.bitPerfectReal) " • Bit-perfect" else ""
                    Text(qualityLine(t) + bp, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            // comandi principali
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill("Precedente", Modifier.size(64.dp), round = true, onClick = { fb.tapScreen(); e.previous() }) {
                    Icon(Icons.Filled.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Pill(if (e.isPlaying) "Pausa" else "Riproduci", Modifier.weight(1f).height(64.dp), onClick = { fb.tapScreen(); env.playPause() }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (e.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (e.isPlaying) "Pausa" else "Play", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Pill("Successivo", Modifier.size(64.dp), round = true, onClick = { fb.tapScreen(); e.next() }) {
                    Icon(Icons.Filled.SkipNext, null, tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Pill("Casuale", Modifier.weight(1f).height(52.dp), active = e.shuffleOn, accent = accent, onClick = { fb.tapScreen(); e.toggleShuffle() }) {
                    Icon(Icons.Filled.Shuffle, null, tint = if (e.shuffleOn) accent else Color.White)
                }
                Pill("Equalizzatore", Modifier.weight(1f).height(52.dp), onClick = { fb.tapScreen(); env.tab = Tab.Eq }) {
                    Icon(Icons.Filled.Tune, null, tint = Color.White)
                }
                Pill("Ripeti", Modifier.weight(1f).height(52.dp), active = e.repeatMode != 0, accent = accent, onClick = { fb.tapScreen(); e.cycleRepeat() }) {
                    Icon(if (e.repeatMode == 2) Icons.Filled.RepeatOne else Icons.Filled.Repeat, null, tint = if (e.repeatMode != 0) accent else Color.White)
                }
            }
        }
    }
}

@Composable
private fun Pill(
    description: String,
    modifier: Modifier,
    round: Boolean = false,
    active: Boolean = false,
    accent: Color = Color.White,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .clip(if (round) CircleShape else RoundedCornerShape(32.dp))
            .background(if (active) accent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.12f))
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}
