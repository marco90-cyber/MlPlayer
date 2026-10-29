package com.marco.mlplayer.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marco.mlplayer.audio.EqMath
import com.marco.mlplayer.audio.OutKind
import com.marco.mlplayer.data.Track
import com.marco.mlplayer.data.rememberCover
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// ---------------------------------------------------------------- schermata principale

@Composable
fun NowPlayingPanel(env: AppEnv) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HeaderPlate(env)
        MetersPlate(env, Modifier.weight(1f).heightIn(min = 110.dp))
        DisplayPlate(env)
        ControlsPlate(env)
    }
}

@Composable
private fun HeaderPlate(env: AppEnv) {
    val e = env.engine
    val eq = env.settings.eq
    val dsp = eq.enabled || eq.bassPercent != 0 || eq.treblePercent != 0
    val usb = e.outputMonitor.kind == OutKind.Usb
    Row(
        Modifier.fillMaxWidth().plate().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Engraved("MLPLAYER", 19.sp, spacing = 5.sp)
            Engraved("HIGH RESOLUTION AUDIO PLAYER", 9.sp, dim = true, bold = false, spacing = 2.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Lamp("HI-RES", e.hiResNow)
                Lamp("DSP", dsp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Lamp("DAC", usb, Hifi.finish.led2)
                Lamp("BYPASS", !dsp, Hifi.finish.led2)
            }
        }
    }
}

// ---------------------------------------------------------------- VU meter

/** Stato delle lancette: livello RMS con balistica VU (0 VU = -18 dBFS). */
private class VuState {
    var frame by mutableIntStateOf(0)
    val needle = FloatArray(2)
    val peakUntil = LongArray(2)
}

private const val VU_REF_DBFS = -18f
private const val VU_MAX_DB = 3f
private const val VU_N = 2048

/** Posizione della lancetta 0..1 per un valore in dB VU (scala in tensione, come gli strumenti veri). */
private fun vuFrac(db: Float): Float = (10f.pow(db / 20f) / 10f.pow(VU_MAX_DB / 20f)).coerceIn(0f, 1.06f)

@Composable
private fun MetersPlate(env: AppEnv, modifier: Modifier) {
    val an = env.engine.analyzer
    val st = remember { VuState() }
    DisposableEffect(Unit) {
        an.acquire()
        onDispose { an.release() }
    }
    LaunchedEffect(Unit) {
        val l = FloatArray(VU_N)
        val r = FloatArray(VU_N)
        val pk = FloatArray(2)
        var lastPub = 0L
        var last = 0L
        while (isActive) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0.001f, 0.1f)
            last = now
            val pub = an.published
            val playing = pub != lastPub && pub > VU_N
            lastPub = pub
            an.takePeaks(pk)
            val tau = if (env.settings.vuSpeed == 1) 0.12f else 0.30f
            val k = 1f - exp(-dt / tau)
            if (playing) an.copyLatest(l, r, VU_N)
            for (c in 0..1) {
                var target = 0f
                if (playing) {
                    val buf = if (c == 0) l else r
                    var s = 0.0
                    for (i in 0 until VU_N) s += buf[i] * buf[i]
                    val rms = sqrt(s / VU_N).toFloat()
                    if (rms > 1e-6f) target = vuFrac(20f * log10(rms) - VU_REF_DBFS)
                    if (pk[c] >= 0.89f) st.peakUntil[c] = System.currentTimeMillis() + 600
                }
                st.needle[c] += (target - st.needle[c]) * k
            }
            st.frame++
            // a riposo (nessun suono e lancette ferme) si aggiorna di rado
            if (!playing && st.needle[0] < 0.002f && st.needle[1] < 0.002f) delay(120)
        }
    }

    Column(modifier.fillMaxWidth().plate().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Screw()
            Engraved("LEVEL", 9.sp, dim = true, spacing = 3.sp, modifier = Modifier.weight(1f), align = androidx.compose.ui.text.style.TextAlign.Center)
            Screw()
        }
        VSpace(6.dp)
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VuMeter(st, 0, "LEFT", Modifier.weight(1f).fillMaxHeight())
            VuMeter(st, 1, "RIGHT", Modifier.weight(1f).fillMaxHeight())
        }
    }
}

private val vuMarks = listOf(-20f, -10f, -7f, -5f, -3f, -2f, -1f, 0f, 1f, 2f, 3f)
private val vuMinor = listOf(-15f, -8f, -6f, -4f, -2.5f, -1.5f, -0.5f, 0.5f, 1.5f, 2.5f)
private val vuLabels = mapOf(-20f to "20", -10f to "10", -5f to "5", -3f to "3", -1f to "1", 0f to "0", 1f to "1", 3f to "3")

@Composable
private fun VuMeter(st: VuState, ch: Int, label: String, modifier: Modifier) {
    val f = Hifi.finish
    val light = Hifi.light
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER } }
    paint.typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
    Canvas(
        modifier
            .graphicsLayer()
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, Color(0x88000000), RoundedCornerShape(6.dp))
    ) {
        st.frame
        val w = size.width
        val h = size.height
        drawRect(f.meterFace)
        drawCircle(
            Brush.radialGradient(
                listOf(f.meterGlow.copy(alpha = f.meterGlow.alpha * light), Color.Transparent),
                Offset(w / 2f, h * 0.6f), w * 0.75f
            ),
            radius = w, center = Offset(w / 2f, h * 0.6f)
        )
        val px = w / 2f
        val py = h * 1.28f
        val rs = minOf(h * 0.98f, w * 0.78f)
        val span = 42f
        fun ang(frac: Float) = ((-span + 2f * span * frac) * PI / 180f).toFloat()
        fun pt(a: Float, r: Float) = Offset(px + r * sin(a), py - r * cos(a))

        // archi della scala
        val a0 = ang(vuFrac(-20f)) ; val aZ = ang(vuFrac(0f)); val a1 = ang(1f)
        val steps = 24
        for (i in 0 until steps) {
            val aa = a0 + (aZ - a0) * i / steps
            val ab = a0 + (aZ - a0) * (i + 1) / steps
            drawLine(f.scale, pt(aa, rs), pt(ab, rs), 1.4.dp.toPx())
        }
        for (i in 0 until 8) {
            val aa = aZ + (a1 - aZ) * i / 8
            val ab = aZ + (a1 - aZ) * (i + 1) / 8
            drawLine(f.scaleRed, pt(aa, rs + 1.5.dp.toPx()), pt(ab, rs + 1.5.dp.toPx()), 4.dp.toPx())
        }
        for (m in vuMarks) {
            val a = ang(vuFrac(m))
            val c = if (m > 0f) f.scaleRed else f.scale
            drawLine(c, pt(a, rs), pt(a, rs + h * 0.08f), 1.4.dp.toPx())
        }
        for (m in vuMinor) {
            val a = ang(vuFrac(m))
            val c = if (m > 0f) f.scaleRed else f.scale
            drawLine(c, pt(a, rs), pt(a, rs + h * 0.045f), 1.dp.toPx())
        }
        drawIntoCanvas { cv ->
            val nc = cv.nativeCanvas
            paint.textSize = (h * 0.095f).coerceIn(8.dp.toPx(), 12.dp.toPx())
            for ((m, t) in vuLabels) {
                val p = pt(ang(vuFrac(m)), rs + h * 0.16f)
                paint.color = (if (m > 0f) f.scaleRed else f.scale).toArgb()
                nc.drawText(t, p.x, p.y + paint.textSize * 0.35f, paint)
            }
            paint.color = f.meterText.toArgb()
            paint.textSize = (h * 0.13f).coerceIn(10.dp.toPx(), 15.dp.toPx())
            nc.drawText("VU", px, h * 0.74f, paint)
            paint.textSize = (h * 0.075f).coerceIn(7.dp.toPx(), 10.dp.toPx())
            paint.textAlign = Paint.Align.RIGHT
            nc.drawText(label, w - 8.dp.toPx(), h - 7.dp.toPx(), paint)
            paint.textAlign = Paint.Align.LEFT
            nc.drawText("PEAK", 18.dp.toPx(), h - 7.dp.toPx(), paint)
            paint.textAlign = Paint.Align.CENTER
        }
        // spia di picco
        val peakOn = System.currentTimeMillis() < st.peakUntil[ch]
        val lc = Offset(11.dp.toPx(), h - 10.dp.toPx())
        if (peakOn) drawCircle(f.scaleRed.copy(alpha = 0.4f), 6.dp.toPx(), lc)
        drawCircle(if (peakOn) f.scaleRed else Color(0x55000000), 3.dp.toPx(), lc)

        // lancetta
        val na = ang(st.needle[ch])
        clipRect {
            val tip = pt(na, rs + h * 0.1f)
            val base = pt(na, rs * 0.38f)
            drawLine(Color(0x33000000), base + Offset(2f, 3f), tip + Offset(2f, 3f), 2.dp.toPx(), StrokeCap.Round)
            drawLine(f.needle, base, tip, 1.6.dp.toPx(), StrokeCap.Round)
        }
        // copertura del perno
        drawRect(Color(0x40000000), Offset(0f, h - 3.dp.toPx()), androidx.compose.ui.geometry.Size(w, 3.dp.toPx()))
        drawRoundRect(Color(0x22FFFFFF), style = Stroke(1f))
    }
}

// ---------------------------------------------------------------- display

private fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.ITALY, "%02d:%02d", s / 60, s % 60)
}

private fun formatLine(t: Track, runtimeRate: Int): String {
    val sr = if (t.sampleRate > 0) t.sampleRate else runtimeRate
    val parts = ArrayList<String>()
    parts.add(t.formatLabel)
    val q = ArrayList<String>()
    if (t.bits > 0) q.add("${t.bits}bit")
    if (sr > 0) q.add(if (sr % 1000 == 0) "${sr / 1000}kHz" else String.format(Locale.ITALY, "%.1fkHz", sr / 1000.0))
    if (q.isNotEmpty()) parts.add(q.joinToString(" / "))
    return parts.joinToString("  ")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DisplayPlate(env: AppEnv) {
    val f = Hifi.finish
    val e = env.engine
    val t = e.currentTrack
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

    Box(Modifier.fillMaxWidth().plate().padding(8.dp)) {
        Column(
            Modifier.fillMaxWidth().displayWindow().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(Modifier.fillMaxWidth().height(84.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val coverFile = remember(t?.albumKey, env.library.revision) { t?.let { env.library.coverFile(it.albumKey) } }
                val cover = rememberCover(coverFile, 360)
                Box(
                    Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .border(1.dp, f.vfdDim, RoundedCornerShape(3.dp))
                        .background(Color(0xFF000000)),
                    contentAlignment = Alignment.Center
                ) {
                    if (cover != null) {
                        Image(cover, contentDescription = "Copertina", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(Icons.Filled.MusicNote, null, tint = f.vfdDim, modifier = Modifier.size(36.dp))
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Vfd(
                            if (t != null) String.format(Locale.ITALY, "TRACK %02d/%02d", e.queueIndex + 1, e.queueSize) else "STOP",
                            11.sp, Modifier.weight(1f)
                        )
                        if (t != null && e.hiResNow) {
                            Box(
                                Modifier
                                    .border(1.dp, f.vfd, RoundedCornerShape(2.dp))
                                    .padding(horizontal = 4.dp)
                            ) { Vfd("HI-RES", 10.sp) }
                        }
                    }
                    Vfd(
                        t?.title?.uppercase() ?: "NESSUN BRANO",
                        19.sp,
                        Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2500)
                    )
                    Vfd(
                        if (t != null) listOf(t.artist, t.album).filter { it.isNotBlank() }.joinToString(" · ")
                        else "Scegli la musica da LIBRERIA",
                        13.sp, alpha = 0.85f
                    )
                }
            }
            val frac = if (drag >= 0f) drag else if (dur > 0) pos.toFloat() / dur else 0f
            SegmentBar(
                frac, Modifier.fillMaxWidth().height(14.dp).padding(vertical = 4.dp),
                onSeek = if (t != null) ({ v: Float -> drag = -1f; e.seekToFraction(v); pos = (v * dur).toLong() }) else null
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Vfd(fmtTime(pos), 22.sp)
                Vfd(
                    if (t != null) formatLine(t, e.trackSampleRate) else "",
                    12.sp, Modifier.weight(1f).padding(horizontal = 8.dp).padding(bottom = 3.dp)
                        .let { it }, alpha = 0.95f
                )
                Vfd(if (dur > 0) "-" + fmtTime(dur - pos) else "--:--", 13.sp, Modifier.padding(bottom = 3.dp), alpha = 0.8f)
            }
        }
    }
}

// ---------------------------------------------------------------- comandi

/** Volume: manopola 0..1 con scala in dB (60 dB di escursione). */
private fun gainToKnob(g: Float): Float = if (g <= 0.001f) 0f else (1f + 20f * log10(g) / 60f).coerceIn(0f, 1f)
private fun knobToGain(p: Float): Float = if (p <= 0.005f) 0f else 10f.pow((p - 1f) * 60f / 20f)

@Composable
private fun ControlsPlate(env: AppEnv) {
    val f = Hifi.finish
    val e = env.engine
    val s = env.settings
    val eq = s.eq
    val fb = env.feedback
    Column(
        Modifier.fillMaxWidth().plate().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            MetalButton("Casuale", { fb.tapScreen(); e.toggleShuffle() }, width = 48.dp, active = e.shuffleOn) {
                Icon(Icons.Filled.Shuffle, null, tint = f.btnIcon)
            }
            MetalButton(
                "Precedente", { fb.tapScreen(); e.previous() },
                onLongClick = { fb.tapScreen(); e.seekBy(-10_000L) }
            ) { Icon(Icons.Filled.SkipPrevious, null, tint = f.btnIcon) }
            MetalButton(
                if (e.isPlaying) "Pausa" else "Riproduci", { fb.tapScreen(); env.playPause() },
                width = 76.dp, active = e.isPlaying
            ) {
                Icon(if (e.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = f.btnIcon, modifier = Modifier.size(28.dp))
            }
            MetalButton(
                "Successivo", { fb.tapScreen(); e.next() },
                onLongClick = { fb.tapScreen(); e.seekBy(10_000L) }
            ) { Icon(Icons.Filled.SkipNext, null, tint = f.btnIcon) }
            MetalButton("Ripeti", { fb.tapScreen(); e.cycleRepeat() }, width = 48.dp, active = e.repeatMode != 0) {
                Icon(if (e.repeatMode == 2) Icons.Filled.RepeatOne else Icons.Filled.Repeat, null, tint = f.btnIcon)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.Bottom) {
            MetalKnob(
                eq.bassPercent / 100f, 54.dp, "BASSI",
                String.format(Locale.ITALY, "+%.1f dB · %s", EqMath.toneDb(eq.bassPercent), hz(eq.bassFreq)),
                onStep = { fb.wheelStep() },
            ) { v -> s.updateEq { it.copy(bassPercent = (v * 100f).roundToInt()) } }
            val vol = e.volume
            MetalKnob(
                gainToKnob(vol), 92.dp, "VOLUME",
                if (vol <= 0.001f) "MUTO" else if (vol >= 0.999f) "0 dB" else String.format(Locale.ITALY, "%.0f dB", 20f * log10(vol)),
                steps = 30,
                onStep = { fb.wheelStep() },
            ) { v -> e.changeVolume(knobToGain(v) - e.volume) }
            MetalKnob(
                eq.treblePercent / 100f, 54.dp, "ALTI",
                String.format(Locale.ITALY, "+%.1f dB · %s", EqMath.toneDb(eq.treblePercent), hz(eq.trebleFreq)),
                onStep = { fb.wheelStep() },
            ) { v -> s.updateEq { it.copy(treblePercent = (v * 100f).roundToInt()) } }
        }
    }
}

private fun hz(f: Float): String =
    if (f >= 1000f) String.format(Locale.ITALY, "%.0f kHz", f / 1000f) else "${f.roundToInt()} Hz"
