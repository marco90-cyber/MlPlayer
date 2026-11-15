package com.marco.mlplayer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Riferimenti selezionabili, dal meno al più sensibile: 0 = scala in dBFS di picco (come il PPM),
 * altrimenti il livello RMS in dBFS che corrisponde a 0 VU.
 */
val VU_REFS = listOf(0, -12, -14, -18, -20)

fun vuRefLabel(ref: Int): String = if (ref == 0) "dBFS di picco" else "0 VU = ${ref} dBFS".replace("-", "−")

/** Senza nuovi campioni per questo tempo (o in pausa) le barre tornano a zero. */
private const val VU_SILENCE_MS = 300L
private const val VU_MAX_DB = 3f
private const val VU_N = 2048

/** Posizione 0..1 per un valore in dB VU (scala in tensione, come gli strumenti veri). */
private fun vuFrac(db: Float): Float = (10f.pow(db / 20f) / 10f.pow(VU_MAX_DB / 20f)).coerceIn(0f, 1f)

private val vuLabels = listOf(-20f to "−20", -10f to "−10", -5f to "−5", -3f to "−3", 0f to "0", 3f to "+3")

/** Stato delle barre: livello RMS con balistica VU, tenuta del massimo e spia di picco. */
private class VuState {
    var frame by mutableIntStateOf(0)
    val level = FloatArray(2)
    val hold = FloatArray(2)
    val holdUntil = LongArray(2)
    val peakUntil = LongArray(2)
}

/** VU meter del lettore: due barre (sinistro e destro) con la zona rossa sopra lo 0. */
@Composable
fun VuCard(env: AppEnv) {
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
        val target = FloatArray(2)
        var lastPub = 0L
        var lastFreshMs = 0L
        var last = 0L
        while (isActive) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0.001f, 0.1f)
            last = now
            val ref = env.settings.vuRef
            val peakMode = ref == 0
            val nowMs = System.currentTimeMillis()
            // l'audio arriva a blocchi: tra un blocco e l'altro si tiene l'ultimo livello misurato
            val pub = an.published
            val fresh = pub != lastPub && pub > VU_N
            lastPub = pub
            if (fresh) {
                lastFreshMs = nowMs
                an.takePeaks(pk)
                an.copyLatest(l, r, VU_N)
                for (c in 0..1) {
                    if (peakMode) {
                        target[c] = if (pk[c] > 1e-6f) vuFrac(20f * log10(pk[c])) else 0f
                    } else {
                        val buf = if (c == 0) l else r
                        var sum = 0.0
                        for (i in 0 until VU_N) sum += buf[i] * buf[i]
                        val rms = sqrt(sum / VU_N).toFloat()
                        target[c] = if (rms > 1e-6f) vuFrac(20f * log10(rms) - ref) else 0f
                    }
                    if (pk[c] >= 0.89f) st.peakUntil[c] = nowMs + 600
                }
            }
            val silent = !env.engine.isPlaying || nowMs - lastFreshMs > VU_SILENCE_MS
            val tau = if (env.settings.vuSpeed == 1) 0.12f else 0.30f
            for (c in 0..1) {
                val tg = if (silent) 0f else target[c]
                // in dBFS di picco: salita quasi istantanea, discesa lenta come un PPM
                val k = if (peakMode) (if (tg > st.level[c]) 0.01f else 0.6f) else tau
                st.level[c] += (tg - st.level[c]) * (1f - exp(-dt / k))
                if (st.level[c] >= st.hold[c]) {
                    st.hold[c] = st.level[c]; st.holdUntil[c] = nowMs + 1500
                } else if (nowMs > st.holdUntil[c]) {
                    st.hold[c] = (st.hold[c] - dt * 0.5f).coerceAtLeast(st.level[c])
                }
            }
            st.frame++
            // a riposo (nessun suono e barre ferme) si aggiorna di rado
            if (silent && st.level[0] < 0.002f && st.level[1] < 0.002f && st.hold[0] < 0.002f && st.hold[1] < 0.002f) delay(120)
        }
    }

    val cs = MaterialTheme.colorScheme
    val s = env.settings
    Surface(color = cs.surfaceContainer, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("VU meter", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                // tocca il riferimento per cambiarlo
                Text(
                    if (s.vuRef == 0) "dBFS picco" else "rif. ${s.vuRef} dBFS".replace("-", "−"),
                    style = MaterialTheme.typography.labelMedium, color = cs.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            env.feedback.tapScreen()
                            val i = VU_REFS.indexOf(s.vuRef)
                            s.vuRef = VU_REFS[(i + 1).mod(VU_REFS.size)]
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
            for (c in 0..1) VuBar(st, c)
            VuScale()
        }
    }
}

@Composable
private fun VuBar(st: VuState, ch: Int) {
    val cs = MaterialTheme.colorScheme
    val red = lerp(cs.error, cs.tertiary, 0.25f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (ch == 0) "L" else "R", style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium, color = cs.onSurfaceVariant, modifier = Modifier.width(18.dp)
        )
        Canvas(Modifier.weight(1f).height(8.dp)) {
            st.frame
            val w = size.width
            val h = size.height
            val r = CornerRadius(h / 2f)
            val zero = vuFrac(0f) * w
            drawRoundRect(cs.surfaceContainerHighest, cornerRadius = r)
            // zona sopra lo 0 VU
            drawRect(red.copy(alpha = 0.22f), Offset(zero, 0f), Size(w - zero, h))
            val lv = st.level[ch].coerceIn(0f, 1f) * w
            if (lv > 0.5f) {
                drawRoundRect(cs.primary, size = Size(minOf(lv, zero).coerceAtLeast(h), h), cornerRadius = r)
                if (lv > zero) drawRoundRect(red, Offset(zero - h / 2f, 0f), Size(lv - zero + h / 2f, h), cornerRadius = r)
            }
            // massimo recente
            val hx = st.hold[ch].coerceIn(0f, 1f) * w
            if (hx > 2f) drawRect(cs.onSurface, Offset(hx - 1.5f, 0f), Size(3f, h))
            // spia di picco
            if (System.currentTimeMillis() < st.peakUntil[ch]) drawCircle(red, h * 0.9f, Offset(w - h / 2f, h / 2f))
        }
    }
}

/** Numeri della scala, ciascuno sotto la sua posizione. */
@Composable
private fun VuScale() {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().padding(start = 18.dp)) {
        Layout(
            content = {
                for ((db, label) in vuLabels) {
                    Text(
                        label, style = MaterialTheme.typography.labelSmall,
                        color = if (db > 0f) cs.error else cs.onSurfaceVariant
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { measurables, constraints ->
            val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
            val w = constraints.maxWidth
            val h = placeables.maxOfOrNull { it.height } ?: 0
            layout(w, h) {
                placeables.forEachIndexed { i, p ->
                    val x = (vuFrac(vuLabels[i].first) * w - p.width / 2f).roundToInt().coerceIn(0, w - p.width)
                    p.placeRelative(x, 0)
                }
            }
        }
    }
}
