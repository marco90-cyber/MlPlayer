package com.marco.mlplayer.ui.themes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marco.mlplayer.R
import com.marco.mlplayer.audio.DspSettings
import com.marco.mlplayer.audio.direct.Route
import com.marco.mlplayer.data.Track
import com.marco.mlplayer.ui.AppEnv
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.roundToInt

/** Caratteri dei temi: scrittura a penna (Caveat) e display a pixel (DotGothic16), entrambi OFL. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
object ThemeFonts {
    val Hand = FontFamily(
        Font(R.font.caveat, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
        Font(R.font.caveat, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    )
    val Pixel = FontFamily(Font(R.font.dotgothic16))
}

fun mmss(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.ITALY, "%d:%02d", s / 60, s % 60)
}

/** "FLAC · 96/24 · DIRETTO": formato, qualità e uscita in una riga corta. */
fun qualityShort(env: AppEnv, t: Track?): String {
    if (t == null) return "—"
    val parts = ArrayList<String>()
    parts.add(t.formatLabel)
    if (t.sampleRate > 0) {
        val k = if (t.sampleRate % 1000 == 0) "${t.sampleRate / 1000}" else String.format(Locale.ITALY, "%.1f", t.sampleRate / 1000.0)
        parts.add(if (t.bits > 0) "$k/${t.bits}" else "$k kHz")
    }
    parts.add(routeShort(env))
    return parts.joinToString(" · ")
}

fun routeShort(env: AppEnv): String {
    val p = env.engine.outputs.path
    return when {
        p.bitPerfectReal -> "BIT-PERFECT"
        p.route == Route.UsbExclusive -> "USB"
        p.route == Route.DirectTrack || p.route == Route.AAudio -> "DIRETTO"
        else -> "ANDROID"
    }
}

fun isDirectRoute(env: AppEnv): Boolean = env.engine.outputs.path.route != Route.Android || env.engine.outputs.path.bitPerfectReal

/** Posizione e durata del brano, aggiornate più volte al secondo. */
@Stable
class PlayPos {
    var pos by mutableLongStateOf(0L)
    var dur by mutableLongStateOf(0L)
    val frac: Float get() = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
}

@Composable
fun rememberPlayPos(env: AppEnv, periodMs: Long = 200L): PlayPos {
    val e = env.engine
    val t = e.currentTrack
    val p = remember { PlayPos() }
    LaunchedEffect(t?.id, e.isPlaying) {
        while (isActive) {
            p.pos = e.player.currentPosition
            val d = e.player.duration
            p.dur = if (d > 0) d else (t?.durationMs ?: 0L)
            delay(periodMs)
        }
    }
    return p
}

/**
 * Angolo di rotazione continuo (gradi) che avanza a [degPerSec] mentre [running] e si ferma
 * senza salti quando non gira. [speed] scala la velocità (per frenate e partenze).
 */
@Stable
class Spinner {
    var angle by mutableFloatStateOf(0f)
    var speed by mutableFloatStateOf(1f)
}

@Composable
fun rememberSpinner(running: Boolean, degPerSec: Float, rampSec: Float = 0f): Spinner {
    val sp = remember { Spinner() }
    val run = rememberUpdatedState(running)
    val rate = rememberUpdatedState(degPerSec)
    LaunchedEffect(Unit) {
        var last = 0L
        while (isActive) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceIn(0f, 0.1f)
            last = now
            val target = if (run.value) 1f else 0f
            sp.speed = if (rampSec <= 0f) target else {
                val step = dt / rampSec
                if (sp.speed < target) (sp.speed + step).coerceAtMost(target) else (sp.speed - step).coerceAtLeast(target)
            }
            if (sp.speed > 0f) sp.angle = (sp.angle + rate.value * sp.speed * dt) % 360f
            else delay(60)
        }
    }
    return sp
}

/** Livelli L/R (0..1, scala −48..0 dBFS di picco) con picco trattenuto, per le barre dei temi. */
@Stable
class Levels {
    var l by mutableFloatStateOf(0f)
    var r by mutableFloatStateOf(0f)
    var pl by mutableFloatStateOf(0f)
    var pr by mutableFloatStateOf(0f)
}

@Composable
fun rememberLevels(env: AppEnv): Levels {
    val an = env.engine.analyzer
    val lv = remember { Levels() }
    DisposableEffect(Unit) {
        an.acquire()
        onDispose { an.release() }
    }
    LaunchedEffect(Unit) {
        val pk = FloatArray(2)
        var lastPub = 0L
        var lastFresh = 0L
        var holdL = 0L
        var holdR = 0L
        while (isActive) {
            withFrameNanos { }
            val now = System.currentTimeMillis()
            val pub = an.published
            var tl = 0f
            var tr = 0f
            if (pub != lastPub) {
                lastPub = pub
                lastFresh = now
                an.takePeaks(pk)
                tl = toBar(pk[0]); tr = toBar(pk[1])
                lv.l = max(lv.l * 0.82f, tl); lv.r = max(lv.r * 0.82f, tr)
            } else {
                lv.l *= 0.9f; lv.r *= 0.9f
            }
            if (!env.engine.isPlaying || now - lastFresh > 300) { lv.l *= 0.8f; lv.r *= 0.8f }
            if (lv.l >= lv.pl) { lv.pl = lv.l; holdL = now + 900 } else if (now > holdL) lv.pl = (lv.pl - 0.01f).coerceAtLeast(0f)
            if (lv.r >= lv.pr) { lv.pr = lv.r; holdR = now + 900 } else if (now > holdR) lv.pr = (lv.pr - 0.01f).coerceAtLeast(0f)
            if (!env.engine.isPlaying && lv.pl < 0.002f && lv.pr < 0.002f) delay(150)
        }
    }
    return lv
}

private fun toBar(p: Float): Float = if (p < 1e-5f) 0f else ((20f * log10(p) + 48f) / 48f).coerceIn(0f, 1f)

/** Bassi e Alti dei toni (0..TONE_MAX_DB): stesso valore della manopola del layout Hi-Fi. */
fun bassDb(env: AppEnv): Float = env.settings.dsp.let { if (it.tone) it.bassDb else 0f }
fun trebleDb(env: AppEnv): Float = env.settings.dsp.let { if (it.tone) it.trebleDb else 0f }
fun setBass(env: AppEnv, db: Float) = env.settings.updateDsp { it.copy(tone = true, bassDb = (db * 10f).roundToInt() / 10f) }
fun setTreble(env: AppEnv, db: Float) = env.settings.updateDsp { it.copy(tone = true, trebleDb = (db * 10f).roundToInt() / 10f) }
fun dbLabel(db: Float): String = String.format(Locale.ITALY, "+%.1f dB", db)

/**
 * Manopola dei temi: si gira trascinando (su/giù o destra/sinistra), 0..[max].
 * [draw] disegna il corpo; l'indicatore va da −135° a +135°.
 */
@Composable
fun ThemeKnob(
    env: AppEnv,
    label: String,
    value: Float,
    max: Float,
    size: Dp,
    labelColor: Color,
    valueColor: Color,
    pointer: Color,
    body: Brush,
    rim: Color,
    onChange: (Float) -> Unit,
) {
    val cur = rememberUpdatedState(value)
    val change = rememberUpdatedState(onChange)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Canvas(
            Modifier
                .size(size)
                .semantics { contentDescription = "$label ${dbLabel(value)}" }
                .pointerInput(Unit) {
                    var acc = 0f
                    detectDragGestures(onDragStart = { acc = 0f }) { c, d ->
                        c.consume()
                        acc += (d.x - d.y) / 300f * max
                        if (abs(acc) >= 0.1f) {
                            val nv = (cur.value + acc).coerceIn(0f, max)
                            acc = 0f
                            if ((nv * 2f).roundToInt() != (cur.value * 2f).roundToInt()) env.feedback.wheelStep()
                            change.value(nv)
                        }
                    }
                }
        ) {
            val r = this.size.minDimension / 2f
            drawCircle(Color.Black.copy(alpha = 0.35f), r, center + Offset(0f, 3.dp.toPx()))
            drawCircle(rim, r)
            drawCircle(body, r * 0.84f)
            val a = -135f + 270f * (value / max).coerceIn(0f, 1f)
            rotate(a) {
                drawLine(pointer, Offset(center.x, center.y - r * 0.78f), Offset(center.x, center.y - r * 0.42f), 3.dp.toPx(), StrokeCap.Round)
            }
        }
        Column {
            Text(label, color = labelColor, fontSize = 10.sp, letterSpacing = 1.5.sp)
            Text(dbLabel(value), color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

const val TONE_MAX: Float = DspSettings.TONE_MAX_DB
