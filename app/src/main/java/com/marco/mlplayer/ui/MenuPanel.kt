package com.marco.mlplayer.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marco.mlplayer.data.rememberCover

/** Menu di una sezione (Libreria o Impostazioni) con intestazione sul frontale. */
@Composable
fun MenuPanel(env: AppEnv, nav: Nav) {
    val entry = nav.top
    val title = when (val s = entry.screen) {
        is MenuScreen -> s.title
        AppearanceScreen -> "Aspetto"
    }
    val f = Hifi.finish
    Column(
        Modifier.fillMaxSize().plate().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().height(44.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (nav.stack.size > 1) {
                MetalButton("Indietro", { env.feedback.tapScreen(); nav.pop() }, width = 44.dp, height = 36.dp) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = f.btnIcon)
                }
            } else {
                HSpace(6.dp); Screw()
            }
            HSpace(12.dp)
            Engraved(title.uppercase(), 15.sp, spacing = 3.sp, modifier = Modifier.weight(1f))
            Screw()
            HSpace(6.dp)
        }
        Box(Modifier.weight(1f).fillMaxWidth().displayWindow()) {
            AnimatedContent(
                targetState = entry,
                transitionSpec = {
                    val forward = nav.stack.indexOf(targetState) > nav.stack.indexOf(initialState) && nav.stack.indexOf(initialState) >= 0
                    if (forward) {
                        (slideInHorizontally(tween(200)) { it } + fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(tween(200)) { -it / 3 } + fadeOut(tween(200)))
                    } else {
                        (slideInHorizontally(tween(200)) { -it / 3 } + fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(tween(200)) { it } + fadeOut(tween(200)))
                    }
                },
                label = "menu"
            ) { e ->
                when (val s = e.screen) {
                    is MenuScreen -> MenuList(env, e, s)
                    AppearanceScreen -> AppearanceView(env)
                }
            }
        }
    }
}

@Composable
private fun MenuList(env: AppEnv, entry: Entry, screen: MenuScreen) {
    val f = Hifi.finish
    val items: List<MenuItem> =
        if (screen.cache) remember(entry, env.library.revision) { screen.build(env) } else screen.build(env)
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Vfd("NESSUN ELEMENTO", 14.sp) }
        return
    }
    LazyColumn(state = entry.listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(items) { i, item ->
            MenuRow(env, item) {
                env.feedback.tapScreen()
                item.onClick()
            }
            if (i < items.lastIndex) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(1.dp).background(f.vfdDim.copy(alpha = 0.5f)))
            }
        }
    }
}

@Composable
private fun MenuRow(env: AppEnv, item: MenuItem, onClick: () -> Unit) {
    val f = Hifi.finish
    val showCover = env.settings.coverInMenu && (item.albumKey != null || item.track != null)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(enabled = !item.dimmed || item.sub != null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showCover) {
            val key = item.albumKey ?: item.track?.albumKey
            val file = remember(key, env.library.revision) { env.library.coverFile(key) }
            val bmp = rememberCover(file, 160)
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(3.dp)).border(1.dp, f.vfdDim, RoundedCornerShape(3.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (bmp != null) Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(Icons.Filled.MusicNote, null, tint = f.vfdDim, modifier = Modifier.size(20.dp))
            }
            HSpace(12.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(
                item.label,
                color = if (item.dimmed) f.vfd.copy(alpha = 0.55f) else f.vfd,
                fontSize = 16.sp, fontFamily = Hifi.Condensed, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            val sub = item.sub ?: item.track?.let { t -> listOf(t.artist, t.detailLine()).filter { it.isNotBlank() }.joinToString(" · ") }
            if (sub != null) {
                Text(
                    sub, color = f.vfd.copy(alpha = 0.7f), fontSize = 12.sp, fontFamily = Hifi.Mono,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (item.track != null && item.track.isHiResFile) {
            HSpace(8.dp)
            Box(Modifier.border(1.dp, f.vfd, RoundedCornerShape(2.dp)).padding(horizontal = 3.dp)) { Vfd("HR", 9.sp) }
        }
        if (item.value != null) {
            HSpace(8.dp)
            Text(item.value, color = f.vfd.copy(alpha = 0.8f), fontSize = 13.sp, fontFamily = Hifi.Mono, maxLines = 1)
        }
        if (item.chevron) {
            HSpace(4.dp)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = f.vfd.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
        }
    }
}

// ---------------------------------------------------------------- Aspetto

@Composable
private fun AppearanceView(env: AppEnv) {
    val s = env.settings
    val f = Hifi.finish
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Vfd("FINITURA", 12.sp)
        val rows = Finishes.all.chunked(3)
        for (row in rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (fin in row) {
                    FinishThumb(fin, fin.id == s.finishId, Modifier.weight(1f)) {
                        env.feedback.tapScreen()
                        s.finishId = fin.id
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        VSpace(4.dp)
        Vfd("STRUMENTI E DISPLAY", 12.sp)
        val lights = listOf("Bassa", "Media", "Alta")
        OptionRow("Luce degli strumenti", lights[s.meterLight.coerceIn(0, 2)]) {
            env.feedback.tapScreen(); s.meterLight = (s.meterLight + 1) % 3
        }
        OptionRow("Riferimento VU", vuRefLabel(s.vuRef)) {
            env.feedback.tapScreen()
            val i = VU_REFS.indexOf(s.vuRef)
            s.vuRef = VU_REFS[(i + 1).mod(VU_REFS.size)]
        }
        OptionRow("Lancette VU", if (s.vuSpeed == 1) "Rapide (120 ms)" else "Morbide (300 ms)") {
            env.feedback.tapScreen(); s.vuSpeed = (s.vuSpeed + 1) % 2
        }
        SwitchRow("Bagliore del display", s.displayGlow) { s.displayGlow = it }
        SwitchRow("Texture spazzolata", s.brushedTexture) { s.brushedTexture = it }
        SwitchRow("Copertine nei menu", s.coverInMenu) { s.coverInMenu = it }
        SwitchRow("Schermo sempre acceso", s.keepScreenOn) { s.keepScreenOn = it }
        Box(Modifier.height(1.dp).fillMaxWidth().background(f.vfdDim))
    }
}

@Composable
private fun OptionRow(label: String, value: String, onClick: () -> Unit) {
    val f = Hifi.finish
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = f.vfd, fontSize = 16.sp, fontFamily = Hifi.Condensed, modifier = Modifier.weight(1f))
        Text(value, color = f.vfd.copy(alpha = 0.8f), fontSize = 13.sp, fontFamily = Hifi.Mono)
    }
}

@Composable
private fun SwitchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    val f = Hifi.finish
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Switch) { onChange(!value) }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = f.vfd, fontSize = 16.sp, fontFamily = Hifi.Condensed, modifier = Modifier.weight(1f))
        HifiSwitch(value, onChange)
    }
}

/** Piccola anteprima della finitura: frontale, due VU, display e manopole. */
@Composable
private fun FinishThumb(fin: Finish, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cur = Hifi.finish
    Column(
        modifier.clickable(role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.78f)
                .clip(RoundedCornerShape(8.dp))
                .border(if (selected) 2.dp else 1.dp, if (selected) cur.vfd else cur.vfdDim, RoundedCornerShape(8.dp))
                .finishBackground(fin)
                .padding(6.dp)
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                // frontale
                drawRoundRect(Brush.verticalGradient(listOf(fin.plateTop, fin.plateBottom)), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f))
                drawRoundRect(fin.plateBorder, cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
                val pad = w * 0.08f
                // due VU
                val mw = (w - pad * 3) / 2
                val mh = h * 0.26f
                for (i in 0..1) {
                    val x = pad + i * (mw + pad)
                    val y = pad
                    drawRect(fin.meterFace, Offset(x, y), Size(mw, mh))
                    drawCircle(Brush.radialGradient(listOf(fin.meterGlow, Color.Transparent), Offset(x + mw / 2, y + mh * 0.6f), mw * 0.7f), mw * 0.7f, Offset(x + mw / 2, y + mh * 0.6f))
                    drawLine(fin.scale, Offset(x + mw * 0.15f, y + mh * 0.45f), Offset(x + mw * 0.7f, y + mh * 0.3f), 1.5f)
                    drawLine(fin.scaleRed, Offset(x + mw * 0.7f, y + mh * 0.3f), Offset(x + mw * 0.88f, y + mh * 0.36f), 3f)
                    drawLine(fin.needle, Offset(x + mw * 0.5f, y + mh), Offset(x + mw * (0.58f + i * 0.06f), y + mh * 0.2f), 2f)
                }
                // display
                val dy = pad * 2 + mh
                val dh = h * 0.2f
                drawRect(fin.vfdBg, Offset(pad, dy), Size(w - pad * 2, dh))
                drawRect(fin.vfd, Offset(pad * 1.8f, dy + dh * 0.25f), Size((w - pad * 2) * 0.6f, dh * 0.16f))
                drawRect(fin.vfd.copy(alpha = 0.7f), Offset(pad * 1.8f, dy + dh * 0.55f), Size((w - pad * 2) * 0.35f, dh * 0.12f))
                // manopole
                val ky = dy + dh + (h - dy - dh) * 0.5f
                val knob = Brush.sweepGradient(listOf(fin.knobDark, fin.knobLight, fin.knobDark, fin.knobLight, fin.knobDark))
                drawCircle(knob, w * 0.07f, Offset(w * 0.22f, ky))
                drawCircle(knob, w * 0.13f, Offset(w * 0.5f, ky))
                drawCircle(knob, w * 0.07f, Offset(w * 0.78f, ky))
                drawCircle(fin.knobMark, w * 0.02f, Offset(w * 0.5f, ky - w * 0.08f))
            }
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).background(cur.vfd),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Check, null, tint = Color.Black, modifier = Modifier.size(14.dp)) }
            }
        }
        VSpace(6.dp)
        Text(
            fin.name, color = if (selected) cur.vfd else cur.vfd.copy(alpha = 0.75f),
            fontSize = 13.sp, fontFamily = Hifi.CondensedBold, maxLines = 1
        )
    }
}

@Suppress("unused")
private fun Modifier.noRotate(): Modifier = this.rotate(0f)
