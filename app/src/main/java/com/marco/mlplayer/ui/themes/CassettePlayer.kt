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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marco.mlplayer.ui.AppEnv
import java.util.Locale
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow

/** Colori del corpo per la Cassetta: blu, rosso, verde, nero. */
class CassetteColor(val name: String, val c1: Color, val c2: Color, val c3: Color, val soft: Color, val rim: Color)

val CassetteColors = listOf(
    CassetteColor("Blu", Color(0xFF3456B0), Color(0xFF22408F), Color(0xFF172E6C), Color(0xFFC9D4F2), Color(0xFF1A3170)),
    CassetteColor("Rosso", Color(0xFFC83A3A), Color(0xFFA82A2C), Color(0xFF7E1C20), Color(0xFFF6D2D2), Color(0xFF7C1D21)),
    CassetteColor("Verde", Color(0xFF3E8463), Color(0xFF2D6B4E), Color(0xFF1E4D38), Color(0xFFCFE8DA), Color(0xFF1F4E39)),
    CassetteColor("Nero", Color(0xFF36383C), Color(0xFF26272A), Color(0xFF18191B), Color(0xFFC4C6CA), Color(0xFF111213)),
)

private val Ink = Color(0xFF232427)
private val InkDim = Color(0xFF4A4C50)
private val Pen = Color(0xFF1E2A6B)

// misure della cassetta (in orizzontale; sullo schermo è girata in verticale)
private const val CW = 350f
private const val CH = 236f
private const val MECH = 30f

/**
 * Layout "Cassetta": lettore portatile colorato con la cassetta in verticale dietro lo sportellino.
 * L'etichetta è scritta a penna con titolo e artista; le bobine girano e il nastro passa da una
 * all'altra man mano che il brano avanza. Toccando lo sportellino si apre (pausa) e la cassetta
 * salta su; richiudendolo riparte. Sotto, il pannello argento con i tasti meccanici.
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
    val door by animateFloatAsState(if (open) 1f else 0f, tween(450), label = "door")
    val eject by animateFloatAsState(if (open) 1f else 0f, tween(500, delayMillis = if (open) 150 else 0), label = "eject")
    val measurer = rememberTextMeasurer()

    Column(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(col.c1, col.c2, col.c3)))
    ) {
        Column(Modifier.weight(1f).padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("MLPLAYER", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                    Text("LETTORE STEREO PORTATILE", color = col.soft, fontSize = 9.sp, letterSpacing = 1.5.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val lit = isDirectRoute(env) || e.isPlaying
                    Box(
                        Modifier.size(18.dp).clip(CircleShape).background(Color(0xFF121314))
                            .border(3.dp, if (lit) Color(0xFF2FB36A) else Color(0xFF1C4A30), CircleShape)
                    )
                    Text("USCITA", color = col.soft, fontSize = 8.sp, letterSpacing = 1.sp)
                }
                Spacer(Modifier.width(14.dp))
                Box(
                    Modifier
                        .size(48.dp, 32.dp)
                        .shadow(2.dp, RoundedCornerShape(4.dp))
                        .clip(RoundedCornerShape(4.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFFFFB13B), Color(0xFFF28C12))))
                        .semantics { contentDescription = if (e.shuffleOn) "Casuale attivo" else "Casuale" }
                        .clickable(role = Role.Button) { fb.tapScreen(); e.toggleShuffle() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Shuffle, null, tint = if (e.shuffleOn) Color(0xFF3A1D00) else Color(0xFF7A4A10), modifier = Modifier.size(18.dp)) }
            }

            // cassetta dietro lo sportellino
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                // la cassetta è girata: sullo schermo larga CH (+ meccanismo) e alta CW
                val s = min(maxWidth.value / (CH + MECH + 24f), maxHeight.value / (CW + 24f))
                val fw = ((CH + MECH) * s + 18f).dp
                val fh = (CW * s + 18f).dp
                Box(
                    Modifier
                        .size(fw, fh)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.verticalGradient(listOf(Color(0x6B000000), Color(0x47000000))))
                        .border(2.dp, col.rim, RoundedCornerShape(16.dp))
                        .semantics { contentDescription = if (open) "Chiudi lo sportello" else "Apri lo sportello" }
                        .pointerInput(Unit) {
                            detectTapGestures {
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
                        }
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val k = s * density
                        // centro della cassetta sullo schermo (il meccanismo sta a destra)
                        val cx = 9.dp.toPx() + CH * k / 2f
                        val cy = size.height / 2f
                        rotate(-90f, Offset(cx, cy)) {
                            translate(cx - CW * k / 2f, cy - CH * k / 2f) {
                                scale(k, k, Offset.Zero) {
                                    drawMechanism(spin.angle)
                                    translate(left = eject * 34f) {
                                        drawCassette(col, pp.frac, spin.angle, measurer, t?.title ?: "Nessun brano",
                                            t?.let { listOf(it.artist, it.album).filter { x -> x.isNotBlank() }.joinToString(" · ") } ?: "",
                                            t?.let { tr -> if (tr.sampleRate > 0 && tr.bits > 0) "${tr.sampleRate / 1000}/${tr.bits}" else tr.formatLabel } ?: "",
                                            String.format(Locale.ITALY, "%02d", (t?.trackNo ?: 0) % 100), t?.formatLabel ?: "")
                                    }
                                }
                            }
                        }
                    }
                    // sportellino fumé
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(6.dp)
                            .graphicsLayer {
                                transformOrigin = TransformOrigin(0.5f, 1f)
                                rotationX = -72f * door
                                cameraDistance = 14f * density
                            }
                            .clip(RoundedCornerShape(10.dp))
                            .background(Brush.verticalGradient(listOf(Color(0x29141820), Color(0x5C141820))))
                            .border(2.dp, Color(0x1AFFFFFF), RoundedCornerShape(10.dp))
                    ) {
                        Box(
                            Modifier.fillMaxWidth(0.46f).fillMaxHeight()
                                .background(Brush.linearGradient(listOf(Color(0x29FFFFFF), Color(0x00FFFFFF))))
                        )
                        Box(
                            Modifier.align(Alignment.TopCenter).size(58.dp, 18.dp)
                                .clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                                .background(Brush.verticalGradient(listOf(Color(0xFFD9DADC), Color(0xFFAEB0B3)))),
                            contentAlignment = Alignment.Center
                        ) { Text(if (open) "CHIUDI" else "APRI", color = Color(0xFF2A2B2E), fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp) }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(mmss(pp.pos), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                TapeBar(env, pp, Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                Text(if (pp.dur > 0) "-" + mmss(pp.dur - pp.pos) else "--:--", color = col.soft, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                qualityShort(env, t).uppercase(Locale.ITALY), color = col.soft, fontSize = 11.sp, letterSpacing = 2.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).clickable { fb.tapScreen(); env.showSignalPath = true }
            )
        }

        // pannello argento
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFFD9DADC), Color(0xFFB9BBBE))))
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MechKey("INDIETRO", "Precedente", false, { fb.tapScreen(); e.previous() }) { Icon(Icons.Filled.SkipPrevious, null, tint = Ink) }
                MechKey("PLAY", if (e.isPlaying) "Pausa" else "Riproduci", e.isPlaying && !open, {
                    fb.tapScreen()
                    if (open) { open = false; if (!e.isPlaying) env.playPause() } else env.playPause()
                }) { Icon(Icons.Filled.PlayArrow, null, tint = col.c2) }
                MechKey("STOP", "Stop", false, {
                    fb.tapScreen(); if (e.isPlaying) env.playPause(); e.seekToFraction(0f)
                }) { Icon(Icons.Filled.Stop, null, tint = Ink) }
                MechKey("AVANTI", "Successivo", false, { fb.tapScreen(); e.next() }) { Icon(Icons.Filled.SkipNext, null, tint = Ink) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                val ribbed = Brush.sweepGradient(List(24) { if (it % 2 == 0) Color(0xFFCFD0D2) else Color(0xFFB4B6B9) })
                ThemeKnob(env, "BASSI", bassDb(env), TONE_MAX, 46.dp, InkDim, Ink, col.c2, ribbed, Color(0x33000000)) { setBass(env, it) }
                VolumeWheel(env)
                ThemeKnob(env, "ALTI", trebleDb(env), TONE_MAX, 46.dp, InkDim, Ink, col.c2, ribbed, Color(0x33000000)) { setTreble(env, it) }
            }
        }
    }
}

/** Disegna la cassetta in orizzontale (0..CW, 0..CH). */
private fun DrawScope.drawCassette(
    col: CassetteColor, frac: Float, angle: Float, measurer: androidx.compose.ui.text.TextMeasurer,
    title: String, artist: String, quality: String, trackNo: String, format: String,
) {
    val shell = Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(2f, 2f, CW - 2f, CH - 2f), CornerRadius(10f)))
    }
    drawPath(shell, Color(0xFF26272B))
    drawPath(shell, Color(0xFF121315), style = Stroke(2f))
    val trap = Path().apply {
        moveTo(60f, CH - 2f); lineTo(76f, CH - 46f); lineTo(CW - 76f, CH - 46f); lineTo(CW - 60f, CH - 2f); close()
    }
    drawPath(trap, Color(0xFF1C1D20))
    for (x in listOf(112f, CW - 112f)) drawCircle(Color(0xFF0B0B0C), 5f, Offset(x, CH - 22f))
    for (x in listOf(CW / 2 - 14f, CW / 2 + 14f)) drawCircle(Color(0xFF0B0B0C), 3.5f, Offset(x, CH - 14f))
    for (p in listOf(Offset(12f, 12f), Offset(CW - 12f, 12f), Offset(12f, CH - 12f), Offset(CW - 12f, CH - 12f), Offset(CW / 2, CH - 34f)))
        drawCircle(Color(0xFF4A4C51), 4f, p)
    // etichetta
    drawRoundRect(Color(0xFFEFE8D6), Offset(18f, 14f), Size(CW - 36f, CH - 66f), CornerRadius(5f))
    drawRect(col.c2, Offset(18f, 14f), Size(CW - 36f, 14f))
    drawRect(col.c2, Offset(18f, CH - 66f), Size(CW - 36f, 14f))
    drawRoundRect(Color(0xFF2A2620), Offset(26f, 34f), Size(22f, 22f), CornerRadius(3f), style = Stroke(1.5f))
    drawLine(Color(0xFFB9AF98), Offset(56f, 62f), Offset(CW - 26f, 62f), 1f)
    drawLine(Color(0xFFB9AF98), Offset(26f, 92f), Offset(CW - 26f, 92f), 1f)
    val a = measurer.measure("A", TextStyle(color = Color(0xFF2A2620), fontSize = 16.sp, fontWeight = FontWeight.Bold), density = androidx.compose.ui.unit.Density(1f, 1f))
    drawText(a, topLeft = Offset(37f - a.size.width / 2f, 45f - a.size.height / 2f))
    val ttl = measurer.measure(
        title, TextStyle(color = Pen, fontSize = 34.sp, fontFamily = ThemeFonts.Hand, fontWeight = FontWeight.Bold),
        overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = Constraints(maxWidth = (CW - 86f).toInt()), density = androidx.compose.ui.unit.Density(1f, 1f)
    )
    rotate(-1.5f, Offset(58f, 46f)) { drawText(ttl, topLeft = Offset(58f, 62f - ttl.size.height + 6f)) }
    val hand2 = TextStyle(color = Pen, fontSize = 24.sp, fontFamily = ThemeFonts.Hand, fontWeight = FontWeight.SemiBold)
    val q = measurer.measure(quality, hand2.copy(fontSize = 20.sp), density = androidx.compose.ui.unit.Density(1f, 1f))
    val art = measurer.measure(
        artist, hand2, overflow = TextOverflow.Ellipsis, maxLines = 1,
        constraints = Constraints(maxWidth = (CW - 70f - q.size.width).toInt().coerceAtLeast(10)), density = androidx.compose.ui.unit.Density(1f, 1f)
    )
    drawText(art, topLeft = Offset(28f, 92f - art.size.height + 4f))
    drawText(q, topLeft = Offset(CW - 28f - q.size.width, 92f - q.size.height + 4f))
    val small = TextStyle(color = Color(0xFF6B6355), fontSize = 10.sp, fontWeight = FontWeight.Bold)
    drawText(measurer.measure(trackNo, small, density = androidx.compose.ui.unit.Density(1f, 1f)), topLeft = Offset(26f, 116f))
    val fm = measurer.measure(format, small, density = androidx.compose.ui.unit.Density(1f, 1f))
    drawText(fm, topLeft = Offset(CW - 26f - fm.size.width, 116f))
    // finestra con le bobine
    drawRoundRect(Color(0xFF14151A), Offset(78f, 112f), Size(178f, 58f), CornerRadius(8f))
    clipRect(78f, 112f, 256f, 170f) {
        drawRect(Color(0x0AFFFFFF), Offset(78f, 134f), Size(178f, 12f))
        val rl = 18f + 26f * (1f - frac)
        val rr = 18f + 26f * frac
        reel(Offset(118f, 141f), rl, angle)
        reel(Offset(216f, 141f), rr, angle)
    }
    drawRoundRect(Color(0x26FFFFFF), Offset(78f, 112f), Size(178f, 58f), CornerRadius(8f), style = Stroke(1f))
}

private fun DrawScope.reel(c: Offset, spool: Float, angle: Float) {
    drawCircle(Color(0xFF3B2A1F), spool, c)
    drawCircle(Color(0x14FFFFFF), spool, c, style = Stroke(1f))
    rotate(angle, c) {
        drawCircle(Color(0xFFEDEDED), 17f, c)
        drawCircle(Color(0xFF2C2D30), 9f, c)
        for (i in 0 until 6) rotate(i * 60f, c) {
            drawRoundRect(Color(0xFF2C2D30), Offset(c.x - 2.5f, c.y - 15f), Size(5f, 6f), CornerRadius(1f))
        }
    }
}

/** Testina, testina di cancellazione, perno e rullino pressore, sul lato del nastro (non escono con la cassetta). */
private fun DrawScope.drawMechanism(angle: Float) {
    val metal = Brush.horizontalGradient(listOf(Color(0xFF9DA0A5), Color(0xFFF1F2F3), Color(0xFF9DA0A5)), startX = CW / 2 - 17f, endX = CW / 2 + 17f)
    drawRoundRect(Color(0xFF2A2C31), Offset(CW / 2 - 30f, CH + 6f), Size(60f, 16f), CornerRadius(3f))
    drawRoundRect(metal, Offset(CW / 2 - 17f, CH - 24f), Size(34f, 32f), CornerRadius(4f))
    drawLine(Color(0xFF5E6166), Offset(CW / 2 - 17f, CH - 14f), Offset(CW / 2 + 17f, CH - 14f), 1f)
    drawRect(Color(0xFF3A3C40), Offset(CW / 2 - 3f, CH - 24f), Size(6f, 4f))
    val m2 = Brush.horizontalGradient(listOf(Color(0xFF9DA0A5), Color(0xFFF1F2F3), Color(0xFF9DA0A5)), startX = 96f, endX = 116f)
    drawRoundRect(m2, Offset(96f, CH - 16f), Size(20f, 24f), CornerRadius(3f))
    drawRoundRect(Color(0xFF2A2C31), Offset(88f, CH + 6f), Size(36f, 12f), CornerRadius(3f))
    drawCircle(Color(0xFFD5D7DA), 3.5f, Offset(CW - 112f, CH - 22f))
    val pr = Offset(CW - 112f, CH + 4f)
    drawCircle(Color(0xFF0E0F11), 12f, pr)
    drawCircle(Color(0xFF3C3E43), 12f, pr, style = Stroke(2f))
    rotate(angle * 3f, pr) { drawRect(Color(0xFF5B5E63), Offset(pr.x - 1f, pr.y - 10f), Size(2f, 5f)) }
    drawCircle(Color(0xFFC9CBCE), 3f, pr)
    drawRoundRect(Color(0xFF2A2C31), Offset(CW - 128f, CH + 14f), Size(32f, 10f), CornerRadius(3f))
}

@Composable
private fun TapeBar(env: AppEnv, pp: PlayPos, modifier: Modifier) {
    var drag by remember { mutableStateOf(-1f) }
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
        drawRoundRect(Color(0xFFFFB13B), Offset(size.width * frac - 2.dp.toPx(), y - 7.dp.toPx()), Size(4.dp.toPx(), 14.dp.toPx()), CornerRadius(1.dp.toPx()))
    }
}

@Composable
private fun RowScope.MechKey(label: String, desc: String, latched: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(66.dp)
                .padding(top = if (latched) 4.dp else 0.dp)
                .shadow(if (latched) 0.dp else 4.dp, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 6.dp, bottomEnd = 6.dp))
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 6.dp, bottomEnd = 6.dp))
                .background(
                    if (latched) Brush.verticalGradient(listOf(Color(0xFFB9BBBE), Color(0xFFA2A4A8)))
                    else Brush.verticalGradient(listOf(Color(0xFFEFEFF0), Color(0xFFCFD0D2)))
                )
                .semantics { contentDescription = desc }
                .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Canvas(Modifier.width(36.dp).height(12.dp)) {
                    for (i in 0 until 4) drawLine(Color(0x24000000), Offset(0f, i * 3.dp.toPx()), Offset(size.width, i * 3.dp.toPx()), 1f)
                }
                icon()
            }
        }
        Text(label, color = InkDim, fontSize = 10.sp, letterSpacing = 1.5.sp)
    }
}

/** Volume a levetta zigrinata (stessa scala in dB della manopola del layout Hi-Fi). */
@Composable
private fun VolumeWheel(env: AppEnv) {
    val e = env.engine
    val cur = rememberUpdatedState(e.volume)
    fun toPos(g: Float): Float = if (g <= 0.001f) 0f else (1f + 20f * log10(g) / 60f).coerceIn(0f, 1f)
    fun toGain(p: Float): Float = if (p <= 0.005f) 0f else 10f.pow((p - 1f) * 60f / 20f)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Canvas(
            Modifier
                .width(120.dp)
                .height(30.dp)
                .semantics { contentDescription = "Volume" }
                .pointerInput(Unit) {
                    detectDragGestures { c, _ ->
                        c.consume()
                        val p = ((c.position.x - 11.dp.toPx()) / (size.width - 22.dp.toPx())).coerceIn(0f, 1f)
                        val g = toGain(p)
                        if ((toPos(g) * 30).toInt() != (toPos(cur.value) * 30).toInt()) env.feedback.wheelStep()
                        e.changeVolume(g - cur.value)
                    }
                }
        ) {
            val y = size.height / 2f
            val th = 22.dp.toPx()
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(0xFF9FA1A4), Color(0xFFC5C6C8))),
                Offset(0f, y - th / 2), Size(size.width, th), CornerRadius(th / 2)
            )
            val p = toPos(e.volume)
            val x = 11.dp.toPx() + (size.width - 22.dp.toPx()) * p
            val tw = 22.dp.toPx()
            drawRoundRect(Color(0x59000000), Offset(x - tw / 2, 1.dp.toPx() + 2f), Size(tw, size.height - 2.dp.toPx()), CornerRadius(4.dp.toPx()))
            drawRoundRect(Color(0xFFE6E7E8), Offset(x - tw / 2, 0f), Size(tw, size.height - 2.dp.toPx()), CornerRadius(4.dp.toPx()))
            var xx = x - tw / 2 + 2f
            while (xx < x + tw / 2 - 2f) { drawLine(Color(0xFFA7A9AC), Offset(xx, 3f), Offset(xx, size.height - 2.dp.toPx() - 3f), 2f); xx += 4f }
        }
        val v = e.volume
        Text(
            if (v <= 0.001f) "MUTO" else if (v >= 0.999f) "VOLUME 0 dB" else String.format(Locale.ITALY, "VOLUME %.0f dB", 20f * log10(v)),
            color = InkDim, fontSize = 10.sp, letterSpacing = 1.sp
        )
    }
}
