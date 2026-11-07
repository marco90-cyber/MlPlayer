package com.marco.mlplayer.ui.themes

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.PlaybackParameters
import com.marco.mlplayer.audio.direct.Route
import com.marco.mlplayer.data.rememberCover
import com.marco.mlplayer.ui.AppEnv
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private class DeckColors(
    val name: String,
    val bg: Color, val p1: Color, val p2: Color, val text: Color, val faint: Color, val line: Color, val track: Color,
    val metal: Color, val arm: Color, val btn1: Color, val btn2: Color, val btnInk: Color, val k1: Color, val k2: Color,
)

private val DeckBlack = DeckColors(
    "NERO", Color(0xFF141516), Color(0xFF26272A), Color(0xFF1C1D1F), Color(0xFFECEDEE), Color(0xFFA3A6AA), Color(0x1AFFFFFF), Color(0x2EFFFFFF),
    Color(0xFFD4D6D9), Color(0xFFC9CBCE), Color(0xFFD8DADC), Color(0xFFAFB2B5), Color(0xFF232427), Color(0xFF3A3C40), Color(0xFF26272A),
)
private val DeckWhite = DeckColors(
    "BIANCO", Color(0xFFE9E8E4), Color(0xFFF6F5F2), Color(0xFFE2E1DD), Color(0xFF1F2022), Color(0xFF56585C), Color(0x1F000000), Color(0x26000000),
    Color(0xFFD9C08A), Color(0xFFC9A55A), Color(0xFFFFFFFF), Color(0xFFDADAD7), Color(0xFF232427), Color(0xFFD9DAD7), Color(0xFFBFC0BD),
)

// misure del giradischi (unità di disegno)
private const val DW = 374f
private const val DH = 430f
private const val PX = 158f
private const val PY = 196f
private const val PR = 146f
private const val VR = 126f
private const val AX = 338f
private const val AY = 70f
private const val SEC_PER_TURN = 1.8f   // 33⅓ giri

/**
 * Layout "Giradischi": piatto a trazione diretta con i puntini stroboscopici, disco con la copertina
 * al centro, braccio che avanza col brano, START·STOP con frenata e partenza, cursore della velocità
 * (±8 %, cambia anche il tono come sul vinile) e 33/45. Trascinando il disco col dito lo si "gratta":
 * il disco segue il dito e al rilascio il brano riprende dal punto raggiunto.
 */
@Composable
fun TurntablePlayerPanel(env: AppEnv) {
    val c = if (env.settings.turntableWhite) DeckWhite else DeckBlack
    val e = env.engine
    val t = e.currentTrack
    val fb = env.feedback
    val pp = rememberPlayPos(env)
    val scope = rememberCoroutineScope()
    val coverFile = remember(t?.albumKey, env.library.revision) { t?.let { env.library.coverFile(it.albumKey) } }
    val cover = rememberCover(coverFile, 500)

    var pitch by remember { mutableFloatStateOf(0f) }          // −8..+8 %
    var rpm45 by remember { mutableStateOf(false) }
    var scratching by remember { mutableStateOf(false) }
    var braking by remember { mutableStateOf(false) }
    var rampJob by remember { mutableStateOf<Job?>(null) }
    val androidRoute = e.outputs.path.route == Route.Android && !e.outputs.path.bitPerfectReal
    val speed = (1f + pitch / 100f) * (if (rpm45) 45f / (100f / 3f) else 1f)

    fun applySpeed(v: Float) {
        if (androidRoute) runCatching { e.player.playbackParameters = PlaybackParameters(v, v) }
    }
    // velocità e tono tornano normali uscendo dal layout
    DisposableEffect(Unit) { onDispose { runCatching { e.player.playbackParameters = PlaybackParameters.DEFAULT } } }
    androidx.compose.runtime.LaunchedEffect(speed, androidRoute) {
        if (braking) return@LaunchedEffect
        if (androidRoute) applySpeed(speed) else runCatching { e.player.playbackParameters = PlaybackParameters.DEFAULT }
    }

    val turning = (e.isPlaying || braking) && !scratching
    val spin = rememberSpinner(turning && !braking, 360f / SEC_PER_TURN * speed, rampSec = 0.7f)
    val arm by animateFloatAsState(if (e.isPlaying || scratching) 20f + 12f * pp.frac else 6f, tween(1100), label = "arm")
    var scratchDeg by remember { mutableFloatStateOf(0f) }

    fun startStop() {
        fb.tapScreen()
        rampJob?.cancel()
        if (e.isPlaying) {
            // frenata: il disco rallenta e il tono scende, poi pausa
            braking = true
            rampJob = scope.launch {
                if (androidRoute) {
                    for (i in 1..14) { applySpeed(speed * (1f - i / 15f).coerceAtLeast(0.08f)); delay(45) }
                }
                if (e.isPlaying) env.playPause()
                delay(80)
                applySpeed(speed)
                braking = false
            }
        } else {
            // partenza: un attimo di "decollo"
            rampJob = scope.launch {
                if (androidRoute) applySpeed(speed * 0.3f)
                env.playPause()
                if (androidRoute) for (i in 1..10) { delay(40); applySpeed(speed * (0.3f + 0.7f * i / 10f)) }
                applySpeed(speed)
            }
        }
    }

    Column(
        Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(c.bg).padding(horizontal = 10.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("MlPlayer", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("GIRADISCHI A TRAZIONE DIRETTA", color = c.faint, fontSize = 9.sp, letterSpacing = 1.8.sp)
            }
            Text(
                qualityShort(env, t).uppercase(Locale.ITALY), color = c.faint, fontSize = 11.sp, letterSpacing = 1.sp,
                modifier = Modifier.clickable { fb.tapScreen(); env.showSignalPath = true }
            )
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val s = min(maxWidth.value / DW, maxHeight.value / DH)
            val w = (DW * s).dp
            val h = (DH * s).dp
            fun u(v: Float) = (v * s).dp
            Box(
                Modifier.size(w, h).shadow(10.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp))
                    .background(Brush.verticalGradient(listOf(c.p1, c.p2)))
            ) {
                val angle = (spin.angle + scratchDeg) % 360f
                Canvas(Modifier.fillMaxSize()) {
                    val k = s * density
                    scale(k, k, Offset.Zero) { drawDeck(c, angle, e.isPlaying && !braking) }
                }
                // copertina al centro del disco
                val cd = 108f
                Box(
                    Modifier.offset(u(PX - cd / 2), u(PY - cd / 2)).size(u(cd)).rotate(angle)
                        .clip(CircleShape).background(Color(0xFF2C3E8F)).border(1.dp, Color(0x80000000), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (cover != null) Image(cover, "Copertina", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(Modifier.size(u(9f)).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFFF1F2F3), Color(0xFF8D9095)))))
                }
                // zona del disco per lo scratch
                val wasPlaying = remember { mutableStateOf(false) }
                val accum = remember { mutableFloatStateOf(0f) }
                val last = remember { mutableFloatStateOf(0f) }
                Box(
                    Modifier.offset(u(PX - VR), u(PY - VR)).size(u(2 * VR)).clip(CircleShape)
                        .semantics { contentDescription = "Disco: trascinalo per spostarti nel brano" }
                        .pointerInput(Unit) {
                            fun ang(o: Offset) = atan2(o.y - size.height / 2f, o.x - size.width / 2f) * 180f / PI.toFloat()
                            detectDragGestures(
                                onDragStart = { o ->
                                    last.floatValue = ang(o); accum.floatValue = 0f
                                    wasPlaying.value = e.isPlaying
                                    scratching = true
                                    if (e.isPlaying) env.playPause()
                                },
                                onDragEnd = {
                                    scratching = false
                                    val deltaMs = (accum.floatValue / 360f * SEC_PER_TURN * 1000f).toLong()
                                    if (deltaMs != 0L) e.seekBy(deltaMs)
                                    if (wasPlaying.value && !e.isPlaying) env.playPause()
                                },
                                onDragCancel = { scratching = false; if (wasPlaying.value && !e.isPlaying) env.playPause() },
                            ) { ch, _ ->
                                ch.consume()
                                val a = ang(ch.position)
                                var d = a - last.floatValue
                                if (d > 180f) d -= 360f
                                if (d < -180f) d += 360f
                                last.floatValue = a
                                accum.floatValue += d
                                scratchDeg += d
                            }
                        }
                )
                // braccio
                Canvas(Modifier.fillMaxSize()) {
                    val k = s * density
                    scale(k, k, Offset.Zero) { drawArm(c, arm) }
                }
                // cursore della velocità
                PitchSlider(env, c, s, pitch, androidRoute) { pitch = it }
                // START · STOP e 33/45
                Box(
                    Modifier.offset(u(12f), u(DH - 60f)).size(u(70f), u(48f))
                        .shadow(3.dp, RoundedCornerShape(4.dp)).clip(RoundedCornerShape(4.dp))
                        .background(Brush.verticalGradient(listOf(c.btn1, c.btn2)))
                        .semantics { contentDescription = if (e.isPlaying) "Ferma" else "Avvia" }
                        .clickable(role = Role.Button) { startStop() },
                    contentAlignment = Alignment.Center
                ) { Text("START · STOP", color = c.btnInk, fontSize = (9f * s * 1.2f).coerceAtLeast(8f).sp, letterSpacing = 1.sp) }
                Row(Modifier.offset(u(94f), u(DH - 48f)), horizontalArrangement = Arrangement.spacedBy(u(6f))) {
                    for ((label, is45) in listOf("33" to false, "45" to true)) {
                        Row(
                            Modifier.clip(RoundedCornerShape(3.dp)).background(Brush.verticalGradient(listOf(c.btn1, c.btn2)))
                                .semantics { contentDescription = "$label giri" }
                                .clickable(role = Role.RadioButton) { fb.tapScreen(); rpm45 = is45 }
                                .padding(horizontal = u(8f), vertical = u(4f)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, color = c.btnInk, fontSize = 10.sp)
                            if (rpm45 == is45) { Spacer(Modifier.width(4.dp)); Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFFF4A3D))) }
                        }
                    }
                }
            }
        }

        Column(Modifier.padding(horizontal = 10.dp)) {
            Text(t?.title ?: "Nessun brano", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                t?.let { listOf(it.artist, it.album).filter { x -> x.isNotBlank() }.joinToString(" · ") } ?: "Scegli la musica da LIBRERIA",
                color = c.faint, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(mmss(pp.pos), color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(10.dp))
            ProgressLine(env, pp, c, Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            Text(if (pp.dur > 0) "-" + mmss(pp.dur - pp.pos) else "--:--", color = c.faint, fontSize = 13.sp)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SquareKey(c, "Precedente") { fb.tapScreen(); e.previous() }
            val ribbed = Brush.sweepGradient(List(36) { if (it % 2 == 0) c.k1 else c.k2 })
            ThemeKnob(env, "BASSI", bassDb(env), TONE_MAX, 46.dp, c.faint, c.text, Color(0xFFFF4A3D), ribbed, Color(0x4D000000)) { setBass(env, it) }
            ThemeKnob(env, "ALTI", trebleDb(env), TONE_MAX, 46.dp, c.faint, c.text, Color(0xFFFF4A3D), ribbed, Color(0x4D000000)) { setTreble(env, it) }
            SquareKey(c, "Successivo", next = true) { fb.tapScreen(); e.next() }
        }
    }
}

private fun DrawScope.drawDeck(@Suppress("UNUSED_PARAMETER") c: DeckColors, angle: Float, lampOn: Boolean) {
    val pc = Offset(PX, PY)
    drawCircle(Color(0xFF0B0B0C), PR + 3f, pc)
    drawCircle(
        Brush.radialGradient(0.86f to Color(0xFF2A2B2E), 0.9f to Color(0xFFC9CBCE), 0.97f to Color(0xFF8D9095), 1f to Color(0xFF5C5F63), center = pc, radius = PR),
        PR, pc
    )
    rotate(angle, pc) {
        for ((r, col, rad) in listOf(Triple(PR - 5f, Color(0xFFE9EAEC), 1.3f), Triple(PR - 10f, Color(0xFFB9BBBF), 1.2f), Triple(PR - 15f, Color(0xFFE9EAEC), 1.1f))) {
            for (i in 0 until 120) {
                val a = 2f * PI.toFloat() * i / 120f
                drawCircle(col, rad, Offset(PX + r * cos(a), PY + r * sin(a)))
            }
        }
        // disco
        drawCircle(Color(0xFF121214), VR, pc)
        var r = VR - 6f
        while (r > 56f) { drawCircle(Color(0xFF1C1D20), r, pc, style = Stroke(1f)); r -= 3f }
        drawCircle(Color(0xFF0D0D0F), VR, pc, style = Stroke(6f))
    }
    // riflessi fissi del vinile
    drawArc(Color(0x17FFFFFF), 30f, 30f, true, Offset(PX - VR, PY - VR), Size(2 * VR, 2 * VR))
    drawArc(Color(0x12FFFFFF), 205f, 30f, true, Offset(PX - VR, PY - VR), Size(2 * VR, 2 * VR))
    // luce stroboscopica
    if (lampOn) drawCircle(Color(0x66FF3B30), 14f, Offset(30f, 306f))
    drawCircle(if (lampOn) Color(0xFFFF3B30) else Color(0xFF5A1A17), 6f, Offset(30f, 306f))
    // base del braccio
    drawCircle(Color(0xFF2E3033), 38f, Offset(AX, AY))
    drawCircle(Color(0xFF1A1B1D), 19f, Offset(AX, AY))
    drawCircle(Color(0xFF0B0B0C), 38f, Offset(AX, AY), style = Stroke(2f))
}

private fun DrawScope.drawArm(c: DeckColors, angle: Float) {
    val pivot = Offset(AX, AY)
    rotate(angle, pivot) {
        val ox = AX - 60f
        val oy = AY - 60f
        drawRoundRect(c.metal, Offset(ox + 49f, oy), Size(22f, 34f), CornerRadius(4f))
        drawRoundRect(Color(0xFF44474B), Offset(ox + 49f, oy), Size(22f, 34f), CornerRadius(4f), style = Stroke(1f))
        drawCircle(c.metal, 16f, pivot)
        drawCircle(Color(0xFF44474B), 16f, pivot, style = Stroke(1.5f))
        drawCircle(Color(0xFF2A2C30), 6f, pivot)
        val tube = Path().apply {
            moveTo(ox + 60f, oy + 60f); lineTo(ox + 60f, oy + 205f); quadraticTo(ox + 60f, oy + 240f, ox + 34f, oy + 268f)
        }
        drawPath(tube, Color(0x73000000), style = Stroke(7f, cap = StrokeCap.Round))
        drawPath(tube, c.arm, style = Stroke(5f, cap = StrokeCap.Round))
        rotate(28f, Offset(ox + 31f, oy + 282f)) {
            drawRoundRect(Color(0xFF1B1C1F), Offset(ox + 18f, oy + 262f), Size(26f, 40f), CornerRadius(3f))
            drawRoundRect(Color(0xFF55585D), Offset(ox + 18f, oy + 262f), Size(26f, 40f), CornerRadius(3f), style = Stroke(1f))
            drawRoundRect(c.metal, Offset(ox + 30f, oy + 258f), Size(10f, 8f), CornerRadius(1f))
        }
    }
}

@Composable
private fun PitchSlider(env: AppEnv, c: DeckColors, s: Float, pitch: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    val change = rememberUpdatedState(onChange)
    fun u(v: Float) = (v * s).dp
    var lastStep by remember { mutableIntStateOf(0) }
    Column(Modifier.offset(u(DW - 64f), u(278f)), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier.size(u(52f), u(132f))
                .semantics { contentDescription = "Velocità ${String.format(Locale.ITALY, "%+.1f", pitch)} %" }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(onDoubleTap = { change.value(0f) })
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectDragGestures { ch, _ ->
                        ch.consume()
                        val top = 10f * s * density
                        val bot = size.height - 10f * s * density
                        val f = ((ch.position.y - top) / (bot - top)).coerceIn(0f, 1f)
                        var v = -8f + 16f * f
                        if (kotlin.math.abs(v) < 0.4f) v = 0f
                        val st = (v * 2).toInt()
                        if (st != lastStep) { lastStep = st; env.feedback.wheelStep() }
                        change.value((v * 10f).toInt() / 10f)
                    }
                }
        ) {
            val k = s * density
            scale(k, k, Offset.Zero) {
                val tx = 34f
                drawRoundRect(Color(0xFF0B0B0C), Offset(tx - 8f, 0f), Size(16f, 132f), CornerRadius(3f))
                drawLine(Color(0xFF3A3C40), Offset(tx, 8f), Offset(tx, 124f), 2f)
                for (i in 0..8) {
                    val y = 10f + 112f * i / 8f
                    drawLine(c.faint, Offset(14f, y), Offset(20f, y), 1f)
                }
                drawCircle(Color(0xFFC6F04A), 2f, Offset(tx - 12f, 66f))
                val y = 10f + 112f * ((pitch + 8f) / 16f)
                drawRoundRect(Color(0x80000000), Offset(tx - 14f, y - 8f), Size(28f, 18f), CornerRadius(3f))
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color(0xFFF1F2F3), Color(0xFFA9ACB0)), startY = y - 9f, endY = y + 9f),
                    Offset(tx - 14f, y - 9f), Size(28f, 18f), CornerRadius(3f)
                )
                drawLine(Color(0xFF3A3C40), Offset(tx - 11f, y), Offset(tx + 11f, y), 2f)
            }
        }
        Text(
            if (enabled) String.format(Locale.ITALY, "%+.1f %%", pitch).replace("+0,0", "0,0") else "SOLO USCITA ANDROID",
            color = c.faint, fontSize = 8.sp, letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun ProgressLine(env: AppEnv, pp: PlayPos, c: DeckColors, modifier: Modifier) {
    var drag by remember { mutableFloatStateOf(-1f) }
    val seek = rememberUpdatedState { f: Float -> env.engine.seekToFraction(f) }
    val frac = if (drag >= 0f) drag else pp.frac
    Canvas(
        modifier.height(24.dp)
            .semantics { contentDescription = "Posizione nel brano" }
            .pointerInput(Unit) { detectTapGestures { o -> seek.value((o.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { if (drag >= 0f) seek.value(drag); drag = -1f },
                    onDragCancel = { drag = -1f },
                ) { ch, _ -> ch.consume(); drag = (ch.position.x / size.width).coerceIn(0f, 1f) }
            }
    ) {
        val y = size.height / 2f
        val h = 3.dp.toPx()
        drawRoundRect(c.track, Offset(0f, y - h / 2), Size(size.width, h), CornerRadius(h / 2))
        drawRoundRect(c.text, Offset(0f, y - h / 2), Size(size.width * frac, h), CornerRadius(h / 2))
        drawCircle(c.text, 6.dp.toPx(), Offset(size.width * frac, y))
    }
}

@Composable
private fun SquareKey(c: DeckColors, desc: String, next: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp, 44.dp).shadow(3.dp, RoundedCornerShape(4.dp)).clip(RoundedCornerShape(4.dp))
            .background(Brush.verticalGradient(listOf(c.btn1, c.btn2)))
            .semantics { contentDescription = desc }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(if (next) Icons.Filled.SkipNext else Icons.Filled.SkipPrevious, null, tint = c.btnInk) }
}
