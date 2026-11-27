@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.marco.mlplayer.ui

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.marco.mlplayer.lyrics.LLine
import com.marco.mlplayer.lyrics.LWord
import com.marco.mlplayer.util.mmss
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/*
 * Testo e lettore nello stile di LastWave (GPL-3.0, github.com/Clash-Projects/LastWave-native):
 * parole che si alzano mentre vengono cantate, righe lontane sfumate e sfocate, barra ondulata.
 */

/** Opacità delle righe in base alla distanza da quella attuale. */
private val FALLOFF = floatArrayOf(1f, 0.55f, 0.40f, 0.30f, 0.22f)

private fun falloff(dist: Int): Float = FALLOFF[dist.coerceIn(0, FALLOFF.lastIndex)]

/**
 * Quanto è alzata una parola (0..1): sale quando inizia a essere cantata, resta su mentre dura,
 * poi si riabbassa: un'onda morbida che attraversa la riga.
 */
private fun wordLift(w: LWord, pos: Long): Float {
    if (pos < w.start) return 0f
    val dur = (w.end - w.start).coerceAtLeast(1L)
    val rise = ((pos - w.start).toFloat() / dur.coerceIn(140L, 420L)).coerceIn(0f, 1f)
    val inv = 1f - rise
    val riseE = 1f - inv * inv * inv
    val after = pos - (w.start + dur)
    val settle = if (after <= 0L) 1f else (1f - after / 520f).coerceIn(0f, 1f)
    val settleE = settle * settle * (3f - 2f * settle)
    return riseE * settleE
}

/** Una riga del testo sincronizzato: [dist] = distanza dalla riga attuale, [now] conta solo per quella attuale. */
@Composable
internal fun LyricRow(line: LLine, active: Boolean, dist: Int, now: Long, size: TextUnit) {
    val a by animateFloatAsState(falloff(dist), tween(300), label = "rigaAlpha")
    val blurDp by animateFloatAsState(if (dist <= 1) 0f else ((dist - 1) * 1.6f).coerceAtMost(4.8f), tween(300), label = "rigaBlur")
    val glow = Shadow(Color.White.copy(alpha = 0.55f), Offset.Zero, 26f)
    val base = TextStyle(fontSize = size, lineHeight = size * 1.2f, fontWeight = FontWeight.Bold)
    val mod = Modifier
        .fillMaxWidth()
        .alpha(a)
        .then(if (blurDp > 0.1f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(blurDp.dp) else Modifier)

    when {
        line.text.isBlank() -> Text("♪", style = base, color = Color.White, modifier = mod)
        active && line.words.isNotEmpty() -> {
            val density = LocalDensity.current
            val liftPx = with(density) { 5.dp.toPx() }
            val gap = with(density) { (size * 0.27f).toDp() }
            FlowRow(mod) {
                for (w in line.words) {
                    val lift = wordLift(w, now)
                    val dur = (w.end - w.start).coerceAtLeast(1L)
                    val f = ((now - w.start).toFloat() / dur).coerceIn(0f, 1f)
                    val dim = Color.White.copy(alpha = 0.40f)
                    // la parte già cantata è piena, il resto tenue: passa sulla parola mentre la canti
                    val brush = Brush.horizontalGradient(
                        0f to Color.White, f to Color.White,
                        (f + 0.001f).coerceAtMost(1f) to dim, 1f to dim,
                    )
                    val txt = w.text.trimEnd()
                    Text(
                        txt,
                        style = base.copy(brush = brush, shadow = if (lift > 0.02f) glow else null),
                        modifier = Modifier
                            .graphicsLayer { translationY = -lift * liftPx }
                            .padding(end = if (w.text.length > txt.length) gap else 0.dp),
                    )
                }
            }
        }
        else -> Text(
            line.text,
            style = base.copy(shadow = if (active) glow else null),
            color = Color.White,
            modifier = mod,
        )
    }
}

/** Barra di avanzamento: linea che ondeggia mentre suona, con un indicatore verticale. */
@Composable
internal fun WavySeekBar(
    fraction: Float,
    playing: Boolean,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val amp by animateFloatAsState(if (playing) 3f else 0f, tween(300), label = "onda")
    val phase by rememberInfiniteTransition(label = "faseOnda").animateFloat(
        0f, (2 * PI).toFloat(), infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "fase"
    )
    Canvas(
        modifier
            .pointerInput(Unit) { detectTapGestures { o -> onChange((o.x / size.width).coerceIn(0f, 1f)); onFinished() } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> onChange((o.x / size.width).coerceIn(0f, 1f)) },
                    onDragEnd = { onFinished() },
                    onDragCancel = { onFinished() },
                ) { c, _ -> onChange((c.position.x / size.width).coerceIn(0f, 1f)) }
            }
    ) {
        val cy = size.height / 2f
        val stroke = 5.dp.toPx()
        val gapPx = 8.dp.toPx()
        val x = (fraction.coerceIn(0f, 1f) * size.width)
        val end = (x - gapPx).coerceAtLeast(0f)
        // parte ancora da ascoltare
        if (x + gapPx < size.width) {
            drawLine(Color.White.copy(alpha = 0.28f), Offset(x + gapPx, cy), Offset(size.width, cy), stroke, StrokeCap.Round)
        }
        // parte già ascoltata, con l'onda
        if (end > 0f) {
            val path = Path()
            val k = (2 * PI / 30.dp.toPx()).toFloat()
            val a = amp.dp.toPx()
            var px = 0f
            path.moveTo(0f, cy)
            while (px <= end) {
                val env = (px / 24.dp.toPx()).coerceAtMost(1f) * ((end - px) / 24.dp.toPx()).coerceIn(0f, 1f)
                path.lineTo(px, cy + sin(px * k - phase) * a * env)
                px += 2.dp.toPx()
            }
            drawPath(path, Color.White, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        // indicatore verticale
        val w = 4.dp.toPx()
        val h = 26.dp.toPx()
        drawRoundRect(Color.White, Offset(x - w / 2f, cy - h / 2f), Size(w, h), CornerRadius(w / 2f))
    }
}

/** Avanzamento e comandi in basso nella pagina Testo: barra ondulata e pulsanti rotondi scuri. */
@Composable
internal fun LyricsTransportBar(env: AppEnv) {
    val e = env.engine
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var drag by remember { mutableFloatStateOf(-1f) }
    LaunchedEffect(e.currentTrack?.id, e.isPlaying) {
        while (true) {
            pos = e.player.currentPosition
            dur = e.player.duration.coerceAtLeast(0)
            delay(300)
        }
    }
    val frac = if (drag >= 0f) drag else if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    val shownPos = if (drag >= 0f) (drag * dur).toLong() else pos
    val soft = Color.White.copy(alpha = 0.16f)
    Column(
        Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.18f)).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        WavySeekBar(
            fraction = frac, playing = e.isPlaying,
            onChange = { drag = it },
            onFinished = { if (drag >= 0f) e.seekToFraction(drag); drag = -1f },
            modifier = Modifier.fillMaxWidth().height(36.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(mmss(shownPos), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.width(56.dp))
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(58.dp).clip(CircleShape).background(soft).clickable { env.feedback.tapScreen(); e.previous() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.SkipPrevious, "Brano precedente", tint = Color.White, modifier = Modifier.size(28.dp)) }
            Spacer(Modifier.width(14.dp))
            Box(
                Modifier.size(76.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.24f)).clickable { env.feedback.tapScreen(); env.playPause() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (e.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (e.isPlaying) "Pausa" else "Riproduci", tint = Color.White, modifier = Modifier.size(36.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Box(
                Modifier.size(58.dp).clip(CircleShape).background(soft).clickable { env.feedback.tapScreen(); e.next() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.SkipNext, "Brano successivo", tint = Color.White, modifier = Modifier.size(28.dp)) }
            Spacer(Modifier.weight(1f))
            Text(
                if (dur > 0) "−" + mmss((dur - shownPos).coerceAtLeast(0)) else "--:--",
                style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.width(56.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
    }
}
