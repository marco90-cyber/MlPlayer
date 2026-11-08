package com.marco.mlplayer.ui.themes

import androidx.compose.animation.core.animateFloatAsState
import kotlin.math.log10
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
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
    val accent: Color, val hl: Color, val joint: Color, val armHi: Color, val sArm: Boolean,
    val lampOn: Color, val lampOff: Color, val knobGlow: Boolean, val meterBg: Color, val meterFill: Color,
)

private val DeckBlack = DeckColors(
    "NERO", Color(0xFF141516), Color(0xFF26272A), Color(0xFF1C1D1F), Color(0xFFECEDEE), Color(0xFFA3A6AA), Color(0x1AFFFFFF), Color(0x2EFFFFFF),
    Color(0xFFD4D6D9), Color(0xFF1E1F22), Color(0xFF303135), Color(0xFF1B1C1F), Color(0xFFECEDEE), Color(0xFF2A2B2E), Color(0xFF141517),
    Color(0xFF3D82FF), Color(0x1AFFFFFF), Color(0xFF2A2C30), Color(0x47FFFFFF), true,
    Color(0xFF4A8CFF), Color(0xFF14254A), true, Color(0xFF0B0B0C), Color(0xFFD4D6D9),
)
private val DeckWhite = DeckColors(
    "BIANCO", Color(0xFFE9E8E4), Color(0xFFF6F5F2), Color(0xFFE2E1DD), Color(0xFF1F2022), Color(0xFF56585C), Color(0x1F000000), Color(0x26000000),
    Color(0xFFD9C08A), Color(0xFFC9A55A), Color(0xFFFFFFFF), Color(0xFFDADAD7), Color(0xFF232427), Color(0xFFD9DAD7), Color(0xFFBFC0BD),
    Color(0xFFE5372C), Color(0x99FFFFFF), Color(0xFFD9C08A), Color(0x73FFFFFF), false,
    Color(0xFFFF3B30), Color(0xFF5A1A17), false, Color(0xFFCFCFCB), Color(0xFF2A2B2E),
)

// misure del giradischi (unità di disegno)
private const val DW = 374f
private const val DH = 372f
private const val PX = 158f
private const val PY = 196f
private const val PR = 146f
private const val VR = 126f
private const val AX = 338f
private const val AY = 70f
private const val SEC_PER_TURN = 1.8f   // 33⅓ giri

/**
 * Layout "Giradischi": in alto i VU a lancetta come nel layout Hi-Fi; in basso il piatto a trazione
 * diretta con i puntini stroboscopici e il disco con la copertina al centro, il braccio che avanza
 * col brano, poi titolo, avanzamento e comandi (START · STOP con frenata e partenza). Trascinando
 * il disco col dito lo si "gratta": segue il dito e al rilascio il brano riprende dal punto raggiunto.
 */
@Composable
fun TurntablePlayerPanel(env: AppEnv) {
    val c = if (env.settings.turntableWhite) DeckWhite else DeckBlack
    val e = env.engine
    val t = e.currentTrack
    val fb = env.feedback
    val pp = rememberPlayPos(env)
    val lv = rememberLevels(env)
    val scope = rememberCoroutineScope()
    val coverFile = remember(t?.albumKey, env.library.revision) { t?.let { env.library.coverFile(it.albumKey) } }
    val cover = rememberCover(coverFile, 500)

    var scratching by remember { mutableStateOf(false) }
    var braking by remember { mutableStateOf(false) }
    var rampJob by remember { mutableStateOf<Job?>(null) }

    val spin = rememberSpinner(e.isPlaying && !braking && !scratching, 360f / SEC_PER_TURN, rampSec = 0.7f)
    val arm by animateFloatAsState(if (e.isPlaying || scratching) 20f + 12f * pp.frac else 6f, tween(1100), label = "arm")
    var scratchDeg by remember { mutableFloatStateOf(0f) }

    fun startStop() {
        // solo effetto visivo: il piatto frena e riparte, il suono no
        fb.tapScreen()
        rampJob?.cancel()
        if (e.isPlaying) {
            braking = true
            env.playPause()
            rampJob = scope.launch { delay(700); braking = false }
        } else env.playPause()
    }

    Column(
        Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(c.bg).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // VU a lancetta, come nel layout Hi-Fi
        Box(Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)).background(Brush.verticalGradient(listOf(c.p1, c.p2)))) {
            com.marco.mlplayer.ui.MetersPlate(env, Modifier, plated = false)
        }

        // giradischi, attaccato ai comandi in basso
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            val s = min(maxWidth.value / DW, maxHeight.value / DH)
            val w = (DW * s).dp
            val h = (DH * s).dp
            fun u(v: Float) = (v * s).dp
            Box(
                Modifier.size(w, h).shadow(10.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp))
                    .background(Brush.verticalGradient(listOf(c.p1, c.p2)))
            ) {
                val angle = (spin.angle + scratchDeg) % 360f
                val lamp = e.isPlaying && !braking
                Canvas(Modifier.fillMaxSize()) {
                    val k = s * density
                    scale(k, k, Offset.Zero) { drawDeck(c, angle, lamp) }
                }
                val cd = 108f
                Box(
                    Modifier.offset(u(PX - cd / 2), u(PY - cd / 2)).size(u(cd)).rotate(angle)
                        .clip(CircleShape).background(Color(0xFF2C3E8F)).border(1.dp, Color(0x80000000), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (cover != null) Image(cover, "Copertina", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(Modifier.size(u(9f)).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFFF1F2F3), Color(0xFF8D9095)))))
                }
                // il disco si "gratta" col dito
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
                Canvas(Modifier.fillMaxSize()) {
                    val k = s * density
                    scale(k, k, Offset.Zero) { drawArm(c, arm) }
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
        Column(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                qualityShort(env, t).uppercase(Locale.ITALY), color = c.faint, fontSize = 11.sp, letterSpacing = 1.5.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).clickable { fb.tapScreen(); env.showSignalPath = true }
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DeckRound(c, "Casuale", active = e.shuffleOn, onClick = { fb.tapScreen(); e.toggleShuffle() }) {
                    Icon(Icons.Filled.Shuffle, null, tint = if (e.shuffleOn) c.accent else c.btnInk, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                DeckRound(c, "Ripeti", active = e.repeatMode != 0, onClick = { fb.tapScreen(); e.cycleRepeat() }) {
                    Icon(if (e.repeatMode == 2) Icons.Filled.RepeatOne else Icons.Filled.Repeat, null, tint = if (e.repeatMode != 0) c.accent else c.btnInk, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.weight(1f))
                val vol = e.volume
                Text(
                    if (vol <= 0.001f) "MUTO" else if (vol >= 0.999f) "VOL 0 dB" else String.format(Locale.ITALY, "VOL %.0f dB", 20f * log10(vol)),
                    color = c.faint, fontSize = 10.sp, letterSpacing = 1.sp
                )
                Spacer(Modifier.width(10.dp))
                DeckRound(c, "Volume giù", onClick = { fb.tapScreen(); stepVolume(env, -2f) }) { Text("−", color = c.btnInk, fontSize = 22.sp, fontWeight = FontWeight.Light) }
                Spacer(Modifier.width(10.dp))
                DeckRound(c, "Volume su", onClick = { fb.tapScreen(); stepVolume(env, 2f) }) { Text("+", color = c.btnInk, fontSize = 22.sp, fontWeight = FontWeight.Light) }
            }
            Row(Modifier.fillMaxWidth().height(72.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DeckKey(c, "Precedente", Modifier.weight(1f), { fb.tapScreen(); e.previous() }) {
                    Icon(Icons.Filled.SkipPrevious, null, tint = c.btnInk, modifier = Modifier.size(28.dp))
                }
                DeckKey(c, if (e.isPlaying) "Ferma" else "Avvia", Modifier.weight(1f), { startStop() }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.PlayArrow, null, tint = c.accent, modifier = Modifier.size(30.dp))
                        Text("START · STOP", color = c.btnInk, fontSize = 9.sp, letterSpacing = 1.5.sp)
                    }
                }
                DeckKey(c, "Successivo", Modifier.weight(1f), { fb.tapScreen(); e.next() }) {
                    Icon(Icons.Filled.SkipNext, null, tint = c.btnInk, modifier = Modifier.size(28.dp))
                }
                Row(Modifier.width(46.dp).fillMaxHeight(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    DeckMeter(c, "L", lv.l, lv.pl)
                    DeckMeter(c, "R", lv.r, lv.pr)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                val ribbed = Brush.sweepGradient(List(36) { if (it % 2 == 0) c.k1 else c.k2 })
                val rim = if (c.knobGlow) c.accent.copy(alpha = 0.55f) else Color(0x4D000000)
                ThemeKnob(env, "BASSI", bassDb(env), TONE_MAX, 68.dp, c.faint, c.text, c.accent, ribbed, rim) { setBass(env, it) }
                ThemeKnob(env, "ALTI", trebleDb(env), TONE_MAX, 68.dp, c.faint, c.text, c.accent, ribbed, rim) { setTreble(env, it) }
            }
        }
    }
}

private fun DrawScope.drawDeck(c: DeckColors, angle: Float, lampOn: Boolean) {
    val pc = Offset(PX, PY)
    // adattatore dei 45 giri (decorazione)
    drawCircle(Color(0xFF0B0B0C), 20f, Offset(30f, 30f))
    drawCircle(Brush.radialGradient(listOf(Color(0xFFF4F5F6), Color(0xFFC9CBCE), Color(0xFF8D9095)), Offset(30f, 30f), 17f), 17f, Offset(30f, 30f))
    drawCircle(Color(0xFF1A1B1D), 3f, Offset(30f, 30f))
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
        drawCircle(Color(0xFF121214), VR, pc)
        var r = VR - 6f
        while (r > 56f) { drawCircle(Color(0xFF1C1D20), r, pc, style = Stroke(1f)); r -= 3f }
        drawCircle(Color(0xFF0D0D0F), VR, pc, style = Stroke(6f))
    }
    drawArc(Color(0x17FFFFFF), 30f, 30f, true, Offset(PX - VR, PY - VR), Size(2 * VR, 2 * VR))
    drawArc(Color(0x12FFFFFF), 205f, 30f, true, Offset(PX - VR, PY - VR), Size(2 * VR, 2 * VR))
    // luce stroboscopica che illumina il bordo del piatto
    val lp = Offset(30f, 306f)
    if (lampOn) drawCircle(Brush.radialGradient(listOf(c.lampOn.copy(alpha = 0.55f), Color.Transparent), lp, 34f), 34f, lp)
    drawCircle(if (lampOn) c.lampOn else c.lampOff, 6f, lp)
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
        // contrappeso argento con anelli neri
        drawRoundRect(c.metal, Offset(ox + 47f, oy - 4f), Size(26f, 38f), CornerRadius(5f))
        drawRoundRect(Color(0xFF44474B), Offset(ox + 47f, oy - 4f), Size(26f, 38f), CornerRadius(5f), style = Stroke(1f))
        drawLine(Color(0xFF1A1B1D), Offset(ox + 47f, oy + 6f), Offset(ox + 73f, oy + 6f), 2f)
        drawLine(Color(0xFF1A1B1D), Offset(ox + 47f, oy + 24f), Offset(ox + 73f, oy + 24f), 2f)
        drawCircle(c.joint, 16f, pivot)
        drawCircle(Color(0xFF44474B), 16f, pivot, style = Stroke(1.5f))
        drawCircle(Color(0xFF2A2C30), 6f, pivot)
        val tube = Path().apply {
            moveTo(ox + 60f, oy + 60f)
            if (c.sArm) {
                cubicTo(ox + 60f, oy + 120f, ox + 78f, oy + 160f, ox + 66f, oy + 205f)
                cubicTo(ox + 54f, oy + 250f, ox + 36f, oy + 248f, ox + 34f, oy + 268f)
            } else {
                lineTo(ox + 60f, oy + 205f); quadraticTo(ox + 60f, oy + 240f, ox + 34f, oy + 268f)
            }
        }
        drawPath(tube, Color(0xFF0B0B0C), style = Stroke(8f, cap = StrokeCap.Round))
        drawPath(tube, c.arm, style = Stroke(6f, cap = StrokeCap.Round))
        translate(-1.5f, 0f) { drawPath(tube, c.armHi, style = Stroke(1.2f, cap = StrokeCap.Round)) }
        rotate(28f, Offset(ox + 31f, oy + 282f)) {
            drawRoundRect(Color(0xFF141517), Offset(ox + 18f, oy + 262f), Size(26f, 40f), CornerRadius(3f))
            drawRoundRect(Color(0xFF55585D), Offset(ox + 18f, oy + 262f), Size(26f, 40f), CornerRadius(3f), style = Stroke(1f))
            drawRoundRect(c.metal, Offset(ox + 30f, oy + 258f), Size(10f, 8f), CornerRadius(1f))
            drawLine(c.metal, Offset(ox + 40f, oy + 290f), Offset(ox + 56f, oy + 296f), 2f, cap = StrokeCap.Round)
        }
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
private fun DeckRound(c: DeckColors, desc: String, active: Boolean = false, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(44.dp).shadow(3.dp, CircleShape).clip(CircleShape)
            .background(Brush.verticalGradient(listOf(c.btn1, c.btn2)))
            .border(if (active) 2.dp else 1.dp, if (active) c.accent else Color(0x59000000), CircleShape)
            .semantics { contentDescription = desc }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun DeckKey(c: DeckColors, desc: String, modifier: Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    Box(
        modifier
            .fillMaxHeight()
            .padding(top = if (pressed) 3.dp else 0.dp)
            .shadow(if (pressed) 1.dp else 5.dp, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(Brush.verticalGradient(listOf(c.btn1, c.btn2)))
            .border(1.dp, c.hl, RoundedCornerShape(6.dp))
            .semantics { contentDescription = desc }
            .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun DeckMeter(c: DeckColors, label: String, v: Float, peak: Float) {
    Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.weight(1f).width(8.dp)) {
            val w = 6.dp.toPx()
            val x = (size.width - w) / 2f
            drawRoundRect(c.meterBg, Offset(x, 0f), Size(w, size.height), CornerRadius(w / 2))
            val h = size.height * v
            drawRoundRect(c.meterFill, Offset(x, size.height - h), Size(w, h), CornerRadius(w / 2))
            if (peak > 0.01f) {
                val py = size.height * (1f - peak)
                drawLine(c.accent, Offset(0f, py), Offset(size.width, py), 2.dp.toPx())
            }
        }
        Text(label, color = c.faint, fontSize = 10.sp)
    }
}
