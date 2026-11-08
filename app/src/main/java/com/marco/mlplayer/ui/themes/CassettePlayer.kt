package com.marco.mlplayer.ui.themes

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marco.mlplayer.audio.direct.Route
import com.marco.mlplayer.ui.AppEnv
import java.util.Locale
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow

/** Colori del corpo per la Cassetta: blu, rosso, verde, nero. */
class CassetteColor(val name: String, val c1: Color, val c2: Color, val c3: Color, val soft: Color)

val CassetteColors = listOf(
    CassetteColor("Blu", Color(0xFF3A5DB8), Color(0xFF2A4A99), Color(0xFF182F6B), Color(0xFFC9D4F2)),
    CassetteColor("Rosso", Color(0xFFC83A3A), Color(0xFFA82A2C), Color(0xFF7E1C20), Color(0xFFF6D2D2)),
    CassetteColor("Verde", Color(0xFF3E8463), Color(0xFF2D6B4E), Color(0xFF1E4D38), Color(0xFFCFE8DA)),
    CassetteColor("Nero", Color(0xFF3A3C40), Color(0xFF2A2B2E), Color(0xFF18191B), Color(0xFFC4C6CA)),
)

private val Ink = Color(0xFF2A2B2E)
private val InkDim = Color(0xFF3A3C40)
private val Orange1 = Color(0xFFFFB13B)
private val Orange2 = Color(0xFFF08A10)
private val Tape = Color(0xFF4A3324)
private val Unit1 = Density(1f, 1f)

// vano dello sportello, in unità di riferimento (centrato e scalato in modo uniforme)
private const val RW = 270f
private const val RH = 480f
// cassetta: disegnata nelle coordinate 180×290 e messa a (6, 46) con scala 4/3
private const val CAS_X = 6f
private const val CAS_Y = 46f
private const val CAS_K = 240f / 180f
// finestra dello sportello
private val Win = Rect(40f, 94f, 186f, 386f)

/**
 * Layout "Cassetta": lettore portatile con lo sportello che occupa tutto il frontale, incernierato a
 * sinistra, e la cassetta al ferro con le bobine cromate dietro la finestra. Toccando lo sportello
 * (o STOP·EJECT) si apre in orizzontale e la musica va in pausa; richiudendolo riparte. Titolo e
 * artista stanno sotto lo sportello; a destra la striscia argento con i tasti e il volume L/R.
 */
@Composable
fun CassettePlayerPanel(env: AppEnv) {
    val col = CassetteColors[env.settings.cassetteColor.coerceIn(0, CassetteColors.lastIndex)]
    val e = env.engine
    val t = e.currentTrack
    val fb = env.feedback
    val pp = rememberPlayPos(env)
    var open by remember { mutableStateOf(false) }
    var resumeOnClose by remember { mutableStateOf(false) }
    val spin = rememberSpinner(e.isPlaying && !open, 150f, rampSec = 0.25f)
    val door by animateFloatAsState(if (open) 1f else 0f, tween(600), label = "door")
    val measurer = rememberTextMeasurer()

    fun toggleDoor() {
        fb.tapScreen()
        if (!open) {
            resumeOnClose = e.isPlaying
            if (e.isPlaying) env.playPause()
            open = true
        } else {
            open = false
            if (resumeOnClose && !e.isPlaying) env.playPause()
        }
    }

    Row(Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))) {
        // ---------------------------------------------------------------- corpo colorato
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Brush.linearGradient(listOf(col.c1, col.c2, col.c3)))
        ) {
            // fascia superiore: tasti arancioni, spie e LED
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .background(Brush.verticalGradient(listOf(col.c2, col.c3)))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OrangeKey("CASUALE", if (e.shuffleOn) "Casuale attivo" else "Casuale", e.shuffleOn, Icons.Filled.Shuffle, col.soft) {
                    fb.tapScreen(); e.toggleShuffle()
                }
                OrangeKey(
                    "RIPETI", when (e.repeatMode) { 1 -> "Ripeti tutto"; 2 -> "Ripeti brano"; else -> "Ripeti" },
                    e.repeatMode != 0, if (e.repeatMode == 2) Icons.Filled.RepeatOne else Icons.Filled.Repeat, col.soft
                ) { fb.tapScreen(); e.cycleRepeat() }
                Spacer(Modifier.weight(1f))
                val p = e.outputs.path
                val dac = p.route == Route.UsbExclusive
                val hires = t != null && (t.sampleRate > 48000 || t.bits > 16)
                val bypass = p.bitPerfectReal || (p.report.valid && !p.report.dspOn)
                Row(
                    Modifier.clickable { fb.tapScreen(); env.showSignalPath = true },
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Indicator("DAC", dac, col.soft)
                    Indicator("HI-RES", hires, col.soft)
                    Indicator("BYPASS", bypass, col.soft)
                }
                val lit = e.isPlaying && !open
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .then(if (lit) Modifier.shadow(4.dp, CircleShape, ambientColor = Color(0xFFFF4436), spotColor = Color(0xFFFF4436)) else Modifier)
                            .clip(CircleShape)
                            .background(if (lit) Color(0xFFFF4436) else Color(0xFF4A1512))
                    )
                    Text("PLAY", color = col.soft, fontSize = 7.sp, letterSpacing = 1.sp)
                }
            }

            // vano con la cassetta e lo sportello
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp)
                    .semantics { contentDescription = if (open) "Chiudi lo sportello" else "Apri lo sportello" }
                    .pointerInput(Unit) { detectTapGestures { toggleDoor() } }
            ) {
                // fondo puntinato (statico)
                Canvas(Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))) {
                    drawRect(Color(0xFF151619))
                    val step = 5.dp.toPx()
                    var y = step / 2
                    while (y < size.height) {
                        var x = step / 2
                        while (x < size.width) { drawCircle(Color(0xFF26272B), 0.9f * density, Offset(x, y)); x += step }
                        y += step
                    }
                    drawRoundRect(Color(0xFF0B0B0C), size = size, cornerRadius = CornerRadius(8.dp.toPx()), style = Stroke(2.dp.toPx()))
                }
                // cassetta e meccanica
                Canvas(Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))) {
                    val k = min(size.width / RW, size.height / RH)
                    translate((size.width - RW * k) / 2f, (size.height - RH * k) / 2f) {
                        scale(k, k, Offset.Zero) {
                            translate(CAS_X, CAS_Y) {
                                scale(CAS_K, CAS_K, Offset.Zero) { drawCassette(pp.frac, spin.angle, measurer) }
                            }
                            drawMechanism(spin.angle)
                        }
                    }
                }
                // sportello: ruota sulla cerniera di sinistra
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            rotationY = -105f * door
                            cameraDistance = 16f * density
                        }
                ) {
                    val k = min(size.width / RW, size.height / RH)
                    val ox = (size.width - RW * k) / 2f
                    val oy = (size.height - RH * k) / 2f
                    drawDoor(col, k, ox, oy, measurer)
                }
            }

            // titoli sotto lo sportello
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                Text(
                    t?.title ?: "Nessun brano", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, lineHeight = 22.sp
                )
                val sub = t?.let { listOf(it.artist, it.album).filter { x -> x.isNotBlank() }.joinToString(" · ") } ?: ""
                if (sub.isNotEmpty()) Text(sub, color = col.soft, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TapeCounter(pp.pos)
                Spacer(Modifier.width(10.dp))
                TapeBar(env, pp, Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                Text(if (pp.dur > 0) "-" + mmss(pp.dur - pp.pos) else "--:--", color = col.soft, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                qualityShort(env, t).uppercase(Locale.ITALY), color = col.soft, fontSize = 10.sp, letterSpacing = 2.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp, start = 12.dp, end = 12.dp)
                    .clickable { fb.tapScreen(); env.showSignalPath = true }
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp, start = 8.dp, end = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically
            ) {
                val ribbed = Brush.sweepGradient(List(30) { if (it % 2 == 0) Color(0xFFE3E4E6) else Color(0xFFB4B6B9) })
                ThemeKnob(env, "BASSI", bassDb(env), TONE_MAX, 48.dp, col.soft, Color.White, Orange2, ribbed, Color(0xFFA9ABAE)) { setBass(env, it) }
                ThemeKnob(env, "ALTI", trebleDb(env), TONE_MAX, 48.dp, col.soft, Color.White, Orange2, ribbed, Color(0xFFA9ABAE)) { setTreble(env, it) }
            }
        }

        // ---------------------------------------------------------------- striscia argento
        Column(
            Modifier
                .width(92.dp)
                .fillMaxHeight()
                .drawBehind {
                    drawRect(Brush.horizontalGradient(listOf(Color(0xFFA9ABAE), Color(0xFFD6D7D9), Color(0xFFC3C5C8), Color(0xFF9EA0A4))))
                    var y = 0f
                    while (y < size.height) { drawLine(Color(0x24FFFFFF), Offset(0f, y), Offset(size.width, y), 1f); y += 3f }
                    drawRect(Brush.horizontalGradient(listOf(Color(0x59000000), Color.Transparent), endX = 4.dp.toPx()), size = Size(4.dp.toPx(), size.height))
                }
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // griglia EQ (decorativa)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Canvas(
                    Modifier.size(42.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFD9DADC))
                        .border(1.dp, Color(0x40000000), RoundedCornerShape(4.dp))
                ) {
                    val g = size.width / 4f
                    for (i in 0 until 4) for (j in 0 until 4) drawCircle(InkDim, 3.dp.toPx(), Offset(g * (i + 0.5f), g * (j + 0.5f)))
                }
                Text("EQ", color = Ink, fontSize = 8.sp, letterSpacing = 1.sp)
            }
            SilverKey("Precedente", 44.dp, false, { fb.tapScreen(); e.previous() }) { Icon(Icons.Filled.FastRewind, null, tint = Ink, modifier = Modifier.size(20.dp)) }
            SilverKey("Successivo", 44.dp, false, { fb.tapScreen(); e.next() }) { Icon(Icons.Filled.FastForward, null, tint = Ink, modifier = Modifier.size(20.dp)) }
            SilverKey(if (e.isPlaying) "Pausa" else "Riproduci", 78.dp, e.isPlaying && !open, {
                fb.tapScreen()
                if (open) { open = false; if (!e.isPlaying) env.playPause() } else env.playPause()
            }) { Icon(Icons.Filled.PlayArrow, null, tint = col.c2, modifier = Modifier.size(24.dp)) }
            // STOP·EJECT: apre e chiude lo sportello
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val slide by animateFloatAsState(if (open) 1f else 0f, tween(250), label = "eject")
                Canvas(
                    Modifier
                        .size(22.dp, 60.dp)
                        .semantics { contentDescription = if (open) "Chiudi lo sportello" else "Stop ed espelli" }
                        .clickable(role = Role.Button) { toggleDoor() }
                ) {
                    drawRoundRect(Color(0xFF6E7176), cornerRadius = CornerRadius(3.dp.toPx()))
                    drawRoundRect(Color(0x66000000), cornerRadius = CornerRadius(3.dp.toPx()), style = Stroke(1.5f))
                    val th = 28.dp.toPx()
                    val y0 = 4.dp.toPx() + (size.height - th - 8.dp.toPx()) * slide
                    val x0 = 2.dp.toPx()
                    drawRoundRect(Color(0x66000000), Offset(x0, y0 + 2.dp.toPx()), Size(size.width - 2 * x0, th), CornerRadius(2.dp.toPx()))
                    drawRoundRect(Color(0xFFE6E7E8), Offset(x0, y0), Size(size.width - 2 * x0, th), CornerRadius(2.dp.toPx()))
                    var x = x0 + 2f
                    while (x < size.width - x0 - 2f) { drawLine(Color(0xFFA7A9AC), Offset(x, y0 + 2f), Offset(x, y0 + th - 2f), 2f); x += 4f }
                }
                Text("STOP\nEJECT", color = Ink, fontSize = 8.sp, letterSpacing = 1.sp, lineHeight = 10.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            LinkedVolume(env)
        }
    }
}

// ------------------------------------------------------------------------------- disegni

/** Cassetta al ferro nelle coordinate 180×290 (in verticale, lato nastro a destra). */
private fun DrawScope.drawCassette(frac: Float, angle: Float, measurer: TextMeasurer) {
    val shell = RoundRect(2f, 2f, 178f, 288f, CornerRadius(9f))
    val shellPath = Path().apply { addRoundRect(shell) }
    drawPath(shellPath, Brush.linearGradient(listOf(Color(0xEB464A54), Color(0xF222242A)), Offset(0f, 0f), Offset(180f, 290f)))
    drawPath(shellPath, Color(0xFF0E0F11), style = Stroke(2f))
    drawPath(Path().apply { moveTo(178f, 64f); lineTo(150f, 80f); lineTo(150f, 210f); lineTo(178f, 226f); close() }, Color(0xFF1A1B1F))
    drawCircle(Color(0xFF0B0B0C), 4f, Offset(164f, 104f))
    drawCircle(Color(0xFF0B0B0C), 4f, Offset(164f, 186f))
    val lab = TextStyle(color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    drawRoundRect(Color(0xFFC9CBCE), Offset(14f, 14f), Size(120f, 22f), CornerRadius(3f))
    val l1 = measurer.measure("FERRO · 90", lab, density = Unit1)
    drawText(l1, topLeft = Offset(22f, 25f - l1.size.height / 2f))
    drawRoundRect(Color(0xFFC9CBCE), Offset(14f, 254f), Size(120f, 22f), CornerRadius(3f))
    val l2 = measurer.measure("LATO A", lab.copy(fontSize = 10.sp), density = Unit1)
    drawText(l2, topLeft = Offset(22f, 265f - l2.size.height / 2f))
    drawRoundRect(Color(0x0DFFFFFF), Offset(16f, 36f), Size(130f, 218f), CornerRadius(8f))
    drawRoundRect(Color(0x2EFFFFFF), Offset(16f, 36f), Size(130f, 218f), CornerRadius(8f), style = Stroke(1f))
    // il nastro scende dalla bobina di sopra a quella di sotto
    val rl = 19f + 30f * (1f - frac)
    val rr = 19f + 30f * frac
    chromeReel(Offset(80f, 90f), rl, angle)
    chromeReel(Offset(80f, 200f), rr, angle)
    drawLine(Tape, Offset(80f + rl, 90f), Offset(150f, 106f), 2f)
    drawLine(Tape, Offset(80f + rr, 200f), Offset(150f, 184f), 2f)
    drawLine(Tape, Offset(150f, 106f), Offset(150f, 184f), 2f)
    for (p in listOf(Offset(10f, 10f), Offset(170f, 10f), Offset(10f, 280f), Offset(170f, 280f))) drawCircle(Color(0xFF55585D), 3f, p)
}

/** Bobina con la flangia cromata a tre finestre: il nastro avvolto si vede attraverso le aperture. */
private fun DrawScope.chromeReel(c: Offset, spool: Float, angle: Float) {
    drawCircle(Color(0xE6141418), 47f, c)
    drawCircle(Tape, spool, c)
    drawCircle(Color(0x1FFFFFFF), spool, c, style = Stroke(0.8f))
    val disc = Path().apply { addOval(Rect(c, 52f)) }
    val holes = Path()
    for (a in listOf(90f, 210f, 330f)) {
        val s = a + angle - 32f
        holes.addPath(Path().apply {
            arcTo(Rect(c, 46f), s, 64f, true)
            arcTo(Rect(c, 19f), s + 64f, -64f, false)
            close()
        })
    }
    val flange = Path.combine(PathOperation.Difference, disc, holes)
    drawPath(
        flange,
        Brush.radialGradient(
            0f to Color.White, 0.35f to Color(0xFFD9DBDE), 0.7f to Color(0xFFA9ACB1), 1f to Color(0xFFE4E6E8),
            center = Offset(c.x - 10f, c.y - 16f), radius = 60f
        )
    )
    drawCircle(Color(0xFF7D8086), 52f, c, style = Stroke(1f))
    drawCircle(Color(0x8CFFFFFF), 50f, c, style = Stroke(0.8f))
    drawPath(holes, Color(0x99000000), style = Stroke(0.8f))
    drawCircle(Color(0xFFEDEEF0), 15f, c)
    drawCircle(Color(0xFF8D9095), 15f, c, style = Stroke(1f))
    drawCircle(Color(0xFF15161A), 9f, c)
    rotate(angle, c) {
        for (i in 0 until 6) rotate(i * 60f, c) { drawRect(Color(0xFF2A2B2E), Offset(c.x - 1.2f, c.y - 6.5f), Size(2.4f, 3f)) }
    }
}

/** Guide, testina e rullino pressore sul lato del nastro (non si muovono con lo sportello). */
private fun DrawScope.drawMechanism(angle: Float) {
    drawRoundRect(Color(0xFF111214), Offset(224f, 58f), Size(41f, 364f), CornerRadius(4f))
    drawRoundRect(Color(0xFF2A2C30), Offset(224f, 58f), Size(41f, 364f), CornerRadius(4f), style = Stroke(1f))
    fun guide(y: Float) {
        drawPath(Path().apply { moveTo(231f, y + 22f); lineTo(236.5f, y); lineTo(242f, y + 22f); close() }, Color(0xFFD9DBDE))
        drawRoundRect(Color(0xFF8D9095), Offset(231f, y + 22f), Size(11f, 22f), CornerRadius(2f))
    }
    guide(92f)
    guide(332f)
    val head = Brush.horizontalGradient(listOf(Color(0xFF9DA0A5), Color(0xFFF1F2F3), Color(0xFF9DA0A5)), startX = 224f, endX = 244f)
    drawRoundRect(head, Offset(224f, 201f), Size(20f, 64f), CornerRadius(3f))
    drawRoundRect(Color(0xFF5E6166), Offset(224f, 201f), Size(20f, 64f), CornerRadius(3f), style = Stroke(1f))
    drawLine(Color(0xFF5E6166), Offset(224f, 233f), Offset(244f, 233f), 1f)
    val pr = Offset(253f, 312f)
    drawCircle(Color(0xFF0E0F11), 11f, pr)
    drawCircle(Color(0xFF3C3E43), 11f, pr, style = Stroke(2f))
    rotate(angle * 3f, pr) { drawRect(Color(0xFF5B5E63), Offset(pr.x - 1f, pr.y - 10f), Size(2f, 5f)) }
    drawCircle(Color(0xFFC9CBCE), 3f, pr)
    drawRoundRect(Color(0xFFB9BBBF), Offset(255f, 44f), Size(11f, 28f), CornerRadius(2f))
}

/** Sportello: tutto il frontale colorato con la finestra fumé, la scritta e la freccia del nastro. */
private fun DrawScope.drawDoor(col: CassetteColor, k: Float, ox: Float, oy: Float, measurer: TextMeasurer) {
    val win = Rect(ox + Win.left * k, oy + Win.top * k, ox + Win.right * k, oy + Win.bottom * k)
    val wr = CornerRadius(14f * k)
    val r8 = CornerRadius(8.dp.toPx())
    val panel = Path().apply {
        fillType = PathFillType.EvenOdd
        addRoundRect(RoundRect(Rect(Offset.Zero, size), r8))
        addRoundRect(RoundRect(win, wr))
    }
    drawPath(panel, Brush.verticalGradient(listOf(col.c1, col.c2, col.c2, col.c3)))
    drawRoundRect(Color(0x66000000), size = size, cornerRadius = r8, style = Stroke(1.dp.toPx()))
    // vetro fumé con riflesso
    drawRoundRect(Color(0x610C0E14), win.topLeft, win.size, wr)
    drawRoundRect(
        Brush.linearGradient(listOf(Color(0x2EFFFFFF), Color(0x00FFFFFF)), win.topLeft, Offset(win.left + win.width * 0.5f, win.bottom)),
        win.topLeft, Size(win.width * 0.48f, win.height), wr
    )
    drawRoundRect(Color(0xFF0E0F12), win.topLeft, win.size, wr, style = Stroke(3f * k))
    // scritte
    val t1 = measurer.measure("MLPLAYER", TextStyle(color = Color(0xFFE9EAEC), fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp), density = Unit1)
    translate(ox + (RW * k - t1.size.width * k) / 2f, oy + 30f * k) { scale(k, k, Offset.Zero) { drawText(t1) } }
    val t2 = measurer.measure("LETTORE STEREO", TextStyle(color = col.soft, fontSize = 9.sp, letterSpacing = 3.sp), density = Unit1)
    translate(ox + (RW * k - t2.size.width * k) / 2f, oy + (RH - 30f) * k - t2.size.height * k) { scale(k, k, Offset.Zero) { drawText(t2) } }
    // freccia: il nastro scorre verso il basso
    val ax = ox + 210f * k
    val ay = oy + 205f * k
    val s = 30f / 34f * k
    val arrow = Path().apply {
        moveTo(ax + 17f * s, ay + 67f * s); lineTo(ax + 31f * s, ay + 46f * s); lineTo(ax + 22f * s, ay + 46f * s)
        lineTo(ax + 22f * s, ay + 3f * s); lineTo(ax + 12f * s, ay + 3f * s); lineTo(ax + 12f * s, ay + 46f * s)
        lineTo(ax + 3f * s, ay + 46f * s); close()
    }
    drawPath(arrow, Color(0xFFF2F2F2), style = Stroke(2.4f * k, join = StrokeJoin.Round))
    // cerniere
    for (y in listOf(oy + 40f * k, oy + (RH - 90f) * k)) drawRoundRect(Color(0x59000000), Offset(4f * k, y), Size(4f * k, 50f * k), CornerRadius(2f * k))
}

// ------------------------------------------------------------------------------- comandi

@Composable
private fun OrangeKey(label: String, desc: String, active: Boolean, icon: ImageVector, labelColor: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            Modifier
                .padding(top = if (active) 2.dp else 0.dp)
                .size(40.dp, 26.dp)
                .shadow(if (active) 1.dp else 3.dp, RoundedCornerShape(4.dp))
                .clip(RoundedCornerShape(4.dp))
                .background(Brush.verticalGradient(listOf(Orange1, Orange2)))
                .then(if (active) Modifier.border(2.dp, Color.White, RoundedCornerShape(4.dp)) else Modifier)
                .semantics { contentDescription = desc }
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = if (active) Color(0xFF3A1D00) else Color(0xFF7A4A10), modifier = Modifier.size(16.dp)) }
        Text(label, color = labelColor, fontSize = 7.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun Indicator(label: String, on: Boolean, labelColor: Color) {
    Column(
        Modifier.semantics { contentDescription = "$label ${if (on) "attivo" else "spento"}" },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Canvas(Modifier.size(20.dp)) {
            val r = size.minDimension / 2f
            if (on) drawCircle(Color(0x592FD07A), r * 1.25f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFF2FA35F), Color(0xFF1E6B3E)), center, r), r)
            drawCircle(Color(0xFFC9CBCE), r * 0.58f)
            if (on) drawCircle(Brush.radialGradient(listOf(Color(0xFF9BFFC2), Color(0xFF2FD07A), Color(0xFF0F5A30)), center, r * 0.42f), r * 0.42f)
            else drawCircle(Color(0xFF0B0B0C), r * 0.38f)
        }
        Text(label, color = labelColor, fontSize = 7.sp, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun SilverKey(desc: String, h: Dp, latched: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Box(
        Modifier
            .padding(top = if (latched) 3.dp else 0.dp)
            .size(56.dp, h)
            .shadow(if (latched) 1.dp else 4.dp, RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
            .drawBehind {
                drawRect(Brush.verticalGradient(if (latched) listOf(Color(0xFFC9CBCE), Color(0xFFA2A4A8)) else listOf(Color(0xFFF3F4F5), Color(0xFFBFC1C4))))
                val step = 3.dp.toPx()
                var y = 0f
                while (y < size.height) { drawLine(Color(0x2E000000), Offset(0f, y), Offset(size.width, y), 1f); y += step }
                var x = 0f
                while (x < size.width) { drawLine(Color(0x2E000000), Offset(x, 0f), Offset(x, size.height), 1f); x += step }
            }
            .semantics { contentDescription = desc }
            .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { icon() }
}

/** Contatore del nastro a rulli (m:ss). */
@Composable
private fun TapeCounter(ms: Long) {
    val s = (ms / 1000).coerceAtLeast(0)
    val txt = String.format(Locale.ITALY, "%d:%02d", (s / 60) % 100, s % 60)
    Row(
        Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFF0B0B0C)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        for (ch in txt) {
            if (ch == ':') Text(":", color = Color(0xFFF2F2F2), fontSize = 13.sp)
            else Box(
                Modifier.width(15.dp).clip(RoundedCornerShape(2.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF2A2B2E), Color(0xFF3C3E42), Color(0xFF2A2B2E)))),
                contentAlignment = Alignment.Center
            ) { Text(ch.toString(), color = Color(0xFFF2F2F2), fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun TapeBar(env: AppEnv, pp: PlayPos, modifier: Modifier) {
    var drag by remember { mutableFloatStateOf(-1f) }
    val seek = rememberUpdatedState { f: Float -> env.engine.seekToFraction(f) }
    val frac = if (drag >= 0f) drag else pp.frac
    Canvas(
        modifier
            .height(28.dp)
            .semantics { contentDescription = "Posizione nel brano" }
            .pointerInput(Unit) { detectTapGestures { o -> seek.value((o.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { if (drag >= 0f) seek.value(drag); drag = -1f },
                    onDragCancel = { drag = -1f },
                ) { c, _ -> c.consume(); drag = (c.position.x / size.width).coerceIn(0f, 1f) }
            }
    ) {
        val y = size.height / 2f
        val h = 4.dp.toPx()
        drawRoundRect(Color.White.copy(alpha = 0.25f), Offset(0f, y - h / 2), Size(size.width, h), CornerRadius(h / 2))
        drawRoundRect(Color.White, Offset(0f, y - h / 2), Size(size.width * frac, h), CornerRadius(h / 2))
        drawRoundRect(Orange1, Offset(size.width * frac - 2.dp.toPx(), y - 7.dp.toPx()), Size(4.dp.toPx(), 14.dp.toPx()), CornerRadius(1.dp.toPx()))
    }
}

/** Volume a due cursori L e R che si muovono insieme (stessa scala in dB del layout Hi-Fi). */
@Composable
private fun ColumnScope.LinkedVolume(env: AppEnv) {
    val e = env.engine
    val cur = rememberUpdatedState(e.volume)
    fun toPos(g: Float): Float = if (g <= 0.001f) 0f else (1f + 20f * log10(g) / 60f).coerceIn(0f, 1f)
    fun toGain(p: Float): Float = if (p <= 0.005f) 0f else 10f.pow((p - 1f) * 60f / 20f)
    fun set(p: Float) {
        val g = toGain(p)
        if ((toPos(g) * 30).toInt() != (toPos(cur.value) * 30).toInt()) env.feedback.wheelStep()
        e.changeVolume(g - cur.value)
    }
    val pos = toPos(e.volume)
    Column(
        Modifier.weight(1f).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(max = 190.dp)
                .semantics {
                    contentDescription = if (e.volume <= 0.001f) "Volume muto" else String.format(Locale.ITALY, "Volume %.0f dB", 20f * log10(e.volume))
                },
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VSlider("L", pos, ::set)
            VSlider("R", pos, ::set)
        }
        Text("VOLUME", color = Ink, fontSize = 8.sp, letterSpacing = 1.5.sp)
    }
}

@Composable
private fun VSlider(label: String, pos: Float, onPos: (Float) -> Unit) {
    val set = rememberUpdatedState(onPos)
    Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Canvas(
            Modifier
                .weight(1f)
                .width(28.dp)
                .pointerInput(Unit) {
                    val th = 30.dp.toPx()
                    fun p(y: Float) = (1f - (y - th / 2f) / (size.height - th)).coerceIn(0f, 1f)
                    detectTapGestures { o -> set.value(p(o.y)) }
                }
                .pointerInput(Unit) {
                    val th = 30.dp.toPx()
                    fun p(y: Float) = (1f - (y - th / 2f) / (size.height - th)).coerceIn(0f, 1f)
                    detectDragGestures { c, _ -> c.consume(); set.value(p(c.position.y)) }
                }
        ) {
            val tw = 14.dp.toPx()
            val tx = size.width - tw - 1.dp.toPx()
            // scala a puntini
            for (i in 0..10) drawCircle(Color(0xFF55585D), 1.3.dp.toPx(), Offset(3.dp.toPx(), 4.dp.toPx() + (size.height - 8.dp.toPx()) * i / 10f))
            drawRoundRect(
                Brush.horizontalGradient(listOf(Color(0xFF6E7176), Color(0xFF9EA0A4)), startX = tx, endX = tx + tw),
                Offset(tx, 0f), Size(tw, size.height), CornerRadius(3.dp.toPx())
            )
            val th = 30.dp.toPx()
            val y = (size.height - th) * (1f - pos)
            val kx = tx - 3.dp.toPx()
            val kw = tw + 6.dp.toPx()
            drawRoundRect(Color(0x66000000), Offset(kx, y + 2.dp.toPx()), Size(kw, th), CornerRadius(3.dp.toPx()))
            drawRoundRect(Color(0xFFE6E7E8), Offset(kx, y), Size(kw, th), CornerRadius(3.dp.toPx()))
            var yy = y + 2f
            while (yy < y + th - 2f) { drawLine(Color(0xFFA7A9AC), Offset(kx + 1f, yy), Offset(kx + kw - 1f, yy), 2f); yy += 4f }
        }
        Text(label, color = Ink, fontSize = 8.sp, letterSpacing = 1.sp)
    }
}

