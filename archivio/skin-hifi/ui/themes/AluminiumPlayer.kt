package com.marco.mlplayer.ui.themes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marco.mlplayer.data.rememberCover
import com.marco.mlplayer.ui.AppEnv
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Colori del layout Alluminio (chiaro) e Grafite (scuro). */
private class AluColors(
    val dark: Boolean,
    val name: String,
    val text: Color,
    val faint: Color,
    val accent: Color,
    val accentText: Color,
    val g1: Color,
    val g2: Color,
    val brush1: Color,
    val brush2: Color,
    val d1: Color,
    val d2: Color,
    val d3: Color,
    val line: Color,
    val hi: Color,
    val groove: Color,
    val tick: Color,
    val ring: Color,
    val key1: Color,
    val key2: Color,
    val keyEdge: Color,
    val meterBg: Color,
    val meterFill: Color,
    val hub: Color,
    val hubDot: Color,
)

private val Light = AluColors(
    false, "ALLUMINIO", Color(0xFF26282B), Color(0xFF55585D), Color(0xFFE8501A), Color(0xFFB83A0C),
    Color(0xFFE3E4E5), Color(0xFFCDCED0), Color(0x1AFFFFFF), Color(0x06000000),
    Color(0xFFCFD0D2), Color(0xFFEEEFF0), Color(0xFFC2C3C5), Color(0x2E000000), Color(0xBFFFFFFF), Color(0x14000000),
    Color(0xFF7A7D82), Color(0x1F000000), Color(0xFFE9EAEB), Color(0xFFD3D4D6), Color(0x1F000000),
    Color(0xFFBFC0C2), Color(0xFF3A3C3F), Color(0xFFC9CACC), Color(0xFF8E9095),
)
private val Dark = AluColors(
    true, "GRAFITE", Color(0xFFECEDEE), Color(0xFFA9ACB1), Color(0xFFFF6A2B), Color(0xFFFF7F45),
    Color(0xFF323336), Color(0xFF232426), Color(0x09FFFFFF), Color(0x0F000000),
    Color(0xFF2B2C2F), Color(0xFF45474B), Color(0xFF252629), Color(0x1FFFFFFF), Color(0x1FFFFFFF), Color(0x0FFFFFFF),
    Color(0xFF8A8D92), Color(0x1FFFFFFF), Color(0xFF3A3B3E), Color(0xFF2A2B2E), Color(0x73000000),
    Color(0xFF1A1B1D), Color(0xFFD9DADB), Color(0xFF3A3C40), Color(0xFF151617),
)

/**
 * Layout "Alluminio": metallo spazzolato, display a pixel, disco che gira (con la copertina al
 * centro nella versione chiara), anello di avanzamento trascinabile, tasti larghi, livelli L/R e
 * manopole Bassi/Alti. Versione scura "Grafite" nelle impostazioni.
 */
@Composable
fun AluminiumPlayerPanel(env: AppEnv) {
    val c = if (env.settings.aluDark) Dark else Light
    val e = env.engine
    val t = e.currentTrack
    val fb = env.feedback
    val pp = rememberPlayPos(env)
    val spin = rememberSpinner(e.isPlaying, 360f / 9f, rampSec = 0.6f)
    val lv = rememberLevels(env)
    val coverFile = remember(t?.albumKey, env.library.revision) { t?.let { env.library.coverFile(it.albumKey) } }
    val cover = rememberCover(coverFile, 600)

    Column(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(c.g1, c.g2)))
            .brushed(c)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // intestazione e display
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 2.dp)) {
                Text("MlPlayer", color = c.text, fontSize = 21.sp, fontWeight = FontWeight.Light, letterSpacing = 0.5.sp)
                Text(c.name, color = c.faint, fontSize = 10.sp, letterSpacing = 2.sp)
            }
            Column(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0B0B0C))
                    .border(1.dp, Color.Black, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .width(132.dp)
            ) {
                Text(mmss(pp.pos), color = Color(0xFFF2F2F2), fontSize = 28.sp, fontFamily = ThemeFonts.Pixel, lineHeight = 28.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (pp.dur > 0) "-" + mmss(pp.dur - pp.pos) else "--:--", color = Color(0xFFCFCFCF), fontSize = 12.sp, fontFamily = ThemeFonts.Pixel, modifier = Modifier.weight(1f))
                    Text(
                        String.format(Locale.ITALY, "%02d", (t?.trackNo ?: 0).coerceAtLeast(0) % 100),
                        color = Color(0xFFCFCFCF), fontSize = 11.sp, fontFamily = ThemeFonts.Pixel,
                        modifier = Modifier.border(1.dp, Color(0xFFCFCFCF), RoundedCornerShape(3.dp)).padding(horizontal = 4.dp)
                    )
                }
            }
        }
        Column {
            Text(t?.title ?: "Nessun brano", color = c.text, fontSize = 19.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                t?.let { listOf(it.artist, it.album).filter { s -> s.isNotBlank() }.joinToString(" · ") } ?: "Scegli la musica da LIBRERIA",
                color = c.faint, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }

        // disco
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val d = min(maxWidth.value, maxHeight.value).dp
            Disc(env, c, d, spin.angle, pp, cover)
        }

        Text(
            qualityShort(env, t).uppercase(Locale.ITALY), color = c.faint, fontSize = 11.sp, letterSpacing = 1.5.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).clickable { fb.tapScreen(); env.showSignalPath = true }
        )

        // casuale/ripeti e volume
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoundKey(c, "Casuale", active = e.shuffleOn, onClick = { fb.tapScreen(); e.toggleShuffle() }) {
                Icon(Icons.Filled.Shuffle, null, tint = if (e.shuffleOn) c.accent else c.text, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            RoundKey(c, "Ripeti", active = e.repeatMode != 0, onClick = { fb.tapScreen(); e.cycleRepeat() }) {
                Icon(if (e.repeatMode == 2) Icons.Filled.RepeatOne else Icons.Filled.Repeat, null, tint = if (e.repeatMode != 0) c.accent else c.text, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.weight(1f))
            val vol = e.volume
            Text(
                if (vol <= 0.001f) "MUTO" else if (vol >= 0.999f) "VOL 0 dB" else String.format(Locale.ITALY, "VOL %.0f dB", 20f * log10(vol)),
                color = c.faint, fontSize = 10.sp, letterSpacing = 1.sp
            )
            Spacer(Modifier.width(10.dp))
            RoundKey(c, "Volume giù", onClick = { fb.tapScreen(); stepVolume(env, -2f) }) { Text("−", color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Light) }
            Spacer(Modifier.width(10.dp))
            RoundKey(c, "Volume su", onClick = { fb.tapScreen(); stepVolume(env, 2f) }) { Text("+", color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Light) }
        }

        // tasti e livelli
        Row(Modifier.fillMaxWidth().height(80.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WideKey(c, "Precedente", onClick = { fb.tapScreen(); e.previous() }) {
                Icon(Icons.Filled.SkipPrevious, null, tint = c.text, modifier = Modifier.size(28.dp))
            }
            WideKey(c, if (e.isPlaying) "Pausa" else "Riproduci", onClick = { fb.tapScreen(); env.playPause() }) {
                Icon(if (e.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = c.accent, modifier = Modifier.size(32.dp))
            }
            WideKey(c, "Successivo", onClick = { fb.tapScreen(); e.next() }) {
                Icon(Icons.Filled.SkipNext, null, tint = c.text, modifier = Modifier.size(28.dp))
            }
            Row(Modifier.width(52.dp).fillMaxHeight(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MeterBar(c, "L", lv.l, lv.pl)
                MeterBar(c, "R", lv.r, lv.pr)
            }
        }

        // manopole dei toni
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            val body = Brush.sweepGradient(listOf(c.d1, c.d2, c.d3, c.d2, c.d3, c.d2, c.d1))
            ThemeKnob(env, "BASSI", bassDb(env), TONE_MAX, 52.dp, c.faint, c.text, c.accent, body, c.line) { setBass(env, it) }
            ThemeKnob(env, "ALTI", trebleDb(env), TONE_MAX, 52.dp, c.faint, c.text, c.accent, body, c.line) { setTreble(env, it) }
        }
    }
}

/** Volume a passi di [db] (in dB), come i tasti − e +. */
fun stepVolume(env: AppEnv, db: Float) {
    val e = env.engine
    val cur = e.volume
    val next = when {
        cur <= 0.001f && db > 0 -> 10f.pow(-60f / 20f)
        else -> (cur * 10f.pow(db / 20f)).let { if (it < 10f.pow(-60f / 20f)) 0f else it.coerceAtMost(1f) }
    }
    e.changeVolume(next - cur)
}

private fun Modifier.brushed(c: AluColors): Modifier = this.then(
    Modifier.background(
        Brush.horizontalGradient(
            listOf(c.brush1, c.brush2, c.brush1, c.brush2, c.brush1, c.brush2, c.brush1, c.brush2),
            tileMode = androidx.compose.ui.graphics.TileMode.Repeated, startX = 0f, endX = 18f
        )
    )
)

@Composable
private fun Disc(env: AppEnv, c: AluColors, d: Dp, angle: Float, pp: PlayPos, cover: androidx.compose.ui.graphics.ImageBitmap?) {
    var drag by remember { mutableFloatStateOf(-1f) }
    val seek = rememberUpdatedState { f: Float -> env.engine.seekToFraction(f) }
    fun fracAt(o: Offset, w: Float): Float {
        val a = atan2(o.y - w / 2f, o.x - w / 2f) * 180f / PI.toFloat() + 90f
        return ((a + 360f) % 360f) / 360f
    }
    Box(
        Modifier
            .size(d)
            .semantics { contentDescription = "Disco: trascina l'anello per spostarti nel brano" }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { o -> drag = fracAt(o, size.width.toFloat()) },
                    onDragEnd = { if (drag >= 0f) seek.value(drag); drag = -1f },
                    onDragCancel = { drag = -1f },
                ) { ch, _ -> ch.consume(); drag = fracAt(ch.position, size.width.toFloat()) }
            },
        contentAlignment = Alignment.Center
    ) {
        val frac = if (drag >= 0f) drag else pp.frac
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            // tacche come una ghiera
            for (i in 0 until 60) {
                val a = (i / 60f) * 2f * PI.toFloat() - PI.toFloat() / 2f
                val r1 = r - (if (i % 5 == 0) 1.dp.toPx() else 4.dp.toPx())
                val r2 = r - 9.dp.toPx()
                drawLine(c.tick, center + Offset(r1 * cos(a), r1 * sin(a)), center + Offset(r2 * cos(a), r2 * sin(a)), if (i % 5 == 0) 1.4.dp.toPx() else 0.8.dp.toPx())
            }
            val rr = r - 14.dp.toPx()
            val st = 3.dp.toPx()
            drawCircle(c.ring, rr, style = Stroke(st))
            drawArc(c.accent, -90f, 360f * frac, false, topLeft = center - Offset(rr, rr), size = Size(2 * rr, 2 * rr), style = Stroke(st, cap = StrokeCap.Round))
            // disco
            val rd = rr - 18.dp.toPx()
            drawCircle(Color.Black.copy(alpha = 0.28f), rd, center + Offset(0f, 5.dp.toPx()))
            rotate(angle) {
                drawCircle(Brush.sweepGradient(listOf(c.d1, c.d2, c.d3, c.d2, c.d3, c.d2, c.d1), center), rd)
                drawCircle(c.groove, rd * 0.84f, style = Stroke(1.dp.toPx()))
                drawCircle(c.groove, rd * 0.68f, style = Stroke(1.dp.toPx()))
                drawCircle(c.accent, 3.dp.toPx(), center + Offset(0f, -rd + 12.dp.toPx()))
            }
            drawCircle(c.line, rd, style = Stroke(1.dp.toPx()))
            if (c.dark || cover == null) {
                val rh = rd * 0.33f
                drawCircle(c.hub, rh)
                drawCircle(c.hi, rh, style = Stroke(1.dp.toPx()))
                drawCircle(c.hubDot, 7.dp.toPx())
            }
        }
        if (!c.dark && cover != null) {
            val inner = d * 0.36f
            Image(
                cover, contentDescription = "Copertina", contentScale = ContentScale.Crop,
                modifier = Modifier.size(inner).rotate(angle).clip(CircleShape).border(1.dp, c.line, CircleShape)
            )
        }
    }
}

@Composable
private fun RoundKey(c: AluColors, desc: String, active: Boolean = false, onClick: () -> Unit, content: @Composable () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    Box(
        Modifier
            .size(44.dp)
            .offset(y = if (pressed) 1.dp else 0.dp)
            .shadow(if (pressed) 1.dp else 3.dp, CircleShape)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(c.key1, c.key2)))
            .border(1.dp, if (active) c.accent else c.line, CircleShape)
            .semantics { contentDescription = desc }
            .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun RowScope.WideKey(c: AluColors, desc: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(top = if (pressed) 3.dp else 0.dp)
            .shadow(if (pressed) 1.dp else 4.dp, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(Brush.verticalGradient(listOf(c.key1, c.key2)))
            .border(1.dp, c.line, RoundedCornerShape(6.dp))
            .semantics { contentDescription = desc }
            .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun MeterBar(c: AluColors, label: String, v: Float, peak: Float) {
    Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.weight(1f).width(8.dp)) {
            val w = 6.dp.toPx()
            val x = (size.width - w) / 2f
            drawRoundRect(c.meterBg, Offset(x, 0f), Size(w, size.height), androidx.compose.ui.geometry.CornerRadius(w / 2))
            val h = size.height * v
            drawRoundRect(c.meterFill, Offset(x, size.height - h), Size(w, h), androidx.compose.ui.geometry.CornerRadius(w / 2))
            val py = size.height * (1f - peak)
            if (peak > 0.01f) drawLine(c.accent, Offset(0f, py), Offset(size.width, py), 2.dp.toPx())
        }
        Text(label, color = c.faint, fontSize = 10.sp)
    }
}
