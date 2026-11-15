package com.marco.mlplayer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marco.mlplayer.audio.Analyzer
import com.marco.mlplayer.audio.Fft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val FLOOR = -90f
private const val NB = 31
private const val NFFT = 8192
private const val NG = 256
private const val PUB_EVERY = 8       // numeri aggiornati ogni ~0,26 s
private const val HOLD_FRAMES = 60    // tenuta dei massimi (~2 s)
private const val FALL_NUM = 0.25f    // discesa automatica dei massimi (dB per aggiornamento)
private const val SILENCE_MS = 300L   // senza nuovi campioni per questo tempo si considera silenzio

private fun toDb(v: Float): Float = if (v <= 1e-5f) FLOOR else max(FLOOR, 20f * log10(v))

private fun fdb(v: Float): String =
    if (v <= -89f) "-∞" else String.format(Locale.ITALY, "%.1f", v)

private fun bandFreq(i: Int): Float = 1000f * 2f.pow((i - 17) / 3f)

private fun fmtHz(f: Float): String =
    if (f >= 1000f) String.format(Locale.ITALY, "%.2f kHz", f / 1000f) else "${f.roundToInt()} Hz"

/** Stato e calcoli dell'analizzatore (livelli, PPM, goniometro, 1/3 d'ottava). */
private class AnalyzerState {
    var tick by mutableIntStateOf(0)

    // Testi osservabili: cambiano ad ogni aggiornamento, quindi la UI li ridisegna sempre.
    var txtPeak by mutableStateOf("")
    var txtRms by mutableStateOf("")
    var txtPhase by mutableStateOf("")
    var txtBand by mutableStateOf("")
    var txtBandVal by mutableStateOf("")
    var txtPpm0 by mutableStateOf("")
    var txtPpm1 by mutableStateOf("")
    var buffers by mutableStateOf("")

    // finestra per i numeri: aggiornati ~4 volte al secondo (picco vero = massimo, RMS = media di potenza)
    private val wTp = FloatArray(2) { FLOOR }
    private val wRms = DoubleArray(2)
    private var wN = 0
    private var pubCnt = 0
    private val tpMaxT = IntArray(2)
    private val rmsMaxT = IntArray(2)
    private val ppmMaxT = IntArray(2)
    private val bandPeakT = IntArray(NB)
    var rmsShown = FloatArray(2) { FLOOR }
    var tpShown = FloatArray(2) { FLOOR }

    val barDb = FloatArray(2) { FLOOR }
    val barHold = FloatArray(2) { FLOOR }
    private val barHoldT = IntArray(2)
    val tpCur = FloatArray(2) { FLOOR }
    val tpMax = FloatArray(2) { FLOOR }
    val rmsCur = FloatArray(2) { FLOOR }
    val rmsMax = FloatArray(2) { FLOOR }
    val ppm = FloatArray(2) { FLOOR }
    val ppmMax = FloatArray(2) { FLOOR }

    val bands = FloatArray(NB) { FLOOR }
    val bandPeak = FloatArray(NB) { FLOOR }

    val gx = FloatArray(NG)
    val gy = FloatArray(NG)
    var gCount = 0
    var corr = 0f

    private val pk = FloatArray(2)
    private val l = FloatArray(NFFT)
    private val r = FloatArray(NFFT)
    private val re = FloatArray(NFFT)
    private val im = FloatArray(NFFT)
    private val fft = Fft(NFFT)
    private val win = FloatArray(NFFT) { (0.5 - 0.5 * cos(2.0 * PI * it / (NFFT - 1))).toFloat() }
    private var lastPub = 0L
    private var lastFreshMs = 0L

    fun reset() {
        for (c in 0..1) {
            tpMax[c] = FLOOR; rmsMax[c] = FLOOR; ppmMax[c] = FLOOR; barHold[c] = FLOOR
        }
        for (i in 0 until NB) bandPeak[i] = FLOOR
    }

    fun update(an: Analyzer, sel: Int) {
        step(an)
        if (++pubCnt >= PUB_EVERY) {
            pubCnt = 0
            for (c in 0..1) {
                tpShown[c] = wTp[c]
                rmsShown[c] = if (wN > 0 && wRms[c] > 0.0) toDb(sqrt(wRms[c] / wN).toFloat()) else FLOOR
                wTp[c] = FLOOR
                wRms[c] = 0.0
            }
            wN = 0
            publishTexts(an, sel)
        }
    }

    private fun publishTexts(an: Analyzer, sel: Int) {
        txtPeak = "PICCO VERO   L ${fdb(tpShown[0])}/${fdb(tpMax[0])}  R ${fdb(tpShown[1])}/${fdb(tpMax[1])}"
        txtRms = "RMS          L ${fdb(rmsShown[0])}/${fdb(rmsMax[0])}  R ${fdb(rmsShown[1])}/${fdb(rmsMax[1])}"
        txtPhase = "Fase " + String.format(Locale.ITALY, "%+.2f", corr)
        val b = sel.coerceIn(0, NB - 1)
        txtBand = "1/3 ott. ${fmtHz(bandFreq(b))}"
        txtBandVal = "${fdb(bands[b])} / ${fdb(bandPeak[b])} dBFS"
        txtPpm0 = "${fdb(ppm[0])}/${fdb(ppmMax[0])}"
        txtPpm1 = "${fdb(ppm[1])}/${fdb(ppmMax[1])}"
        buffers = "campioni ricevuti: ${an.published}"
    }

    private fun step(an: Analyzer) {
        val pub = an.published
        val fresh = pub != lastPub && pub > NFFT
        lastPub = pub
        // L'audio arriva a blocchi: se non è arrivato niente di nuovo ma il suono è recente,
        // si tengono i valori attuali invece di trattarlo come silenzio.
        val nowMs = System.currentTimeMillis()
        if (fresh) lastFreshMs = nowMs
        else if (nowMs - lastFreshMs < SILENCE_MS) return
        val active = fresh
        an.takePeaks(pk)

        for (c in 0..1) {
            val p = if (active) toDb(pk[c]) else FLOOR
            tpCur[c] = p
            if (p > wTp[c]) wTp[c] = p
            // massimo: tenuta ~2 s, poi scende da solo
            if (p >= tpMax[c]) { tpMax[c] = p; tpMaxT[c] = 0 }
            else if (++tpMaxT[c] > HOLD_FRAMES) tpMax[c] = max(FLOOR, tpMax[c] - FALL_NUM)
            barDb[c] = if (p > barDb[c]) p else max(p, barDb[c] - 1.6f)
            if (barDb[c] >= barHold[c]) {
                barHold[c] = barDb[c]; barHoldT[c] = 0
            } else if (++barHoldT[c] > 40) {
                barHold[c] = max(FLOOR, barHold[c] - 0.6f)
            }
            // PPM: attacco rapido, ritorno lento
            ppm[c] = if (p > ppm[c]) ppm[c] + (p - ppm[c]) * 0.6f else max(p, ppm[c] - 0.45f)
            if (ppm[c] >= ppmMax[c]) { ppmMax[c] = ppm[c]; ppmMaxT[c] = 0 }
            else if (++ppmMaxT[c] > HOLD_FRAMES) ppmMax[c] = max(FLOOR, ppmMax[c] - FALL_NUM)
        }

        if (!active) {
            for (c in 0..1) {
                rmsCur[c] = FLOOR
                if (++rmsMaxT[c] > HOLD_FRAMES) rmsMax[c] = max(FLOOR, rmsMax[c] - FALL_NUM)
            }
            wN++
            gCount = 0
            for (i in 0 until NB) bands[i] = max(FLOOR, bands[i] - 2f)
            return
        }

        an.copyLatest(l, r, NFFT)

        // RMS e correlazione sugli ultimi 2048 campioni
        val n0 = NFFT - 2048
        var sl = 0.0
        var sr = 0.0
        var slr = 0.0
        for (k in n0 until NFFT) {
            val a = l[k].toDouble()
            val b = r[k].toDouble()
            sl += a * a; sr += b * b; slr += a * b
        }
        val rl = sqrt(sl / 2048.0).toFloat()
        val rr = sqrt(sr / 2048.0).toFloat()
        rmsCur[0] = toDb(rl); rmsCur[1] = toDb(rr)
        wRms[0] += sl / 2048.0
        wRms[1] += sr / 2048.0
        wN++
        for (c in 0..1) {
            if (rmsCur[c] >= rmsMax[c]) { rmsMax[c] = rmsCur[c]; rmsMaxT[c] = 0 }
            else if (++rmsMaxT[c] > HOLD_FRAMES) rmsMax[c] = max(FLOOR, rmsMax[c] - FALL_NUM)
        }
        val cc = (slr / sqrt(sl * sr + 1e-12)).toFloat().coerceIn(-1f, 1f)
        corr += (cc - corr) * 0.25f

        // Goniometro (ultimi 512 campioni, passo 2)
        var g = 0
        var k = NFFT - 512
        while (k < NFFT && g < NG) {
            gx[g] = (l[k] - r[k]) * 0.7071f
            gy[g] = (l[k] + r[k]) * 0.7071f
            g++; k += 2
        }
        gCount = g

        // FFT 1/3 d'ottava (mono)
        for (i in 0 until NFFT) {
            re[i] = (l[i] + r[i]) * 0.5f * win[i]
            im[i] = 0f
        }
        fft.transform(re, im)
        val binHz = an.sampleRate.toFloat() / NFFT
        val half = NFFT / 2 - 1
        val norm = 16f / (NFFT.toFloat() * NFFT) / 1.5f
        for (i in 0 until NB) {
            val fc = bandFreq(i)
            var lo = Math.ceil((fc / 1.1225f / binHz).toDouble()).toInt()
            var hi = Math.floor((fc * 1.1225f / binHz).toDouble()).toInt()
            lo = lo.coerceIn(1, half)
            hi = hi.coerceIn(1, half)
            if (hi < lo) {
                val b = (fc / binHz).roundToInt().coerceIn(1, half)
                lo = b; hi = b
            }
            var p = 0f
            for (b in lo..hi) p += re[b] * re[b] + im[b] * im[b]
            val db = if (fc > an.sampleRate / 2.1f) FLOOR else toDb(sqrt(p * norm))
            bands[i] = if (db > bands[i]) db else max(db, bands[i] - 1.3f)
            if (bands[i] >= bandPeak[i]) { bandPeak[i] = bands[i]; bandPeakT[i] = 0 }
            else if (++bandPeakT[i] > 45) bandPeak[i] = max(FLOOR, bandPeak[i] - 0.5f)
        }
    }
}

/** Sensibilità: sposta solo la visualizzazione (i numeri restano i valori veri). */
private fun adj(db: Float, sens: Float): Float = if (db <= -89f) FLOOR else db + sens

private fun dbFrac(db: Float, min: Float = -60f): Float = ((db - min) / -min).coerceIn(0f, 1f)


// ---------------------------------------------------------------- interfaccia

/** Analizzatore a tutta pagina: spettro, livelli, fase, PPM e sensibilità, in schede Material. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyzerPage(env: AppEnv) {
    val an = env.engine.analyzer
    val st = remember { AnalyzerState() }
    val cs = MaterialTheme.colorScheme

    DisposableEffect(Unit) {
        an.acquire()
        onDispose { an.release() }
    }
    LaunchedEffect(Unit) {
        while (isActive) {
            withContext(Dispatchers.Default) { st.update(an, env.analyzerBand) }
            st.tick++
            delay(33)
        }
    }
    LaunchedEffect(env.analyzerResetToken) { st.reset() }

    val sens = env.settings.analyzerSens.toFloat()
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Analizzatore") },
            navigationIcon = {
                IconButton(onClick = { env.feedback.tapScreen(); env.overlay = Overlay.None }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                }
            },
            actions = {
                IconButton(onClick = { env.feedback.tapScreen(); env.analyzerResetToken++ }) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = "Azzera i picchi")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.surface),
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SpectrumCard(env, st, sens)
            LevelsCard(st, sens)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PhaseCard(st, sens, Modifier.weight(1f).fillMaxHeight())
                PpmCard(env, st, sens, Modifier.weight(1f).fillMaxHeight())
            }
            SensCard(env)
        }
    }
}

@Composable
private fun AnCard(
    title: String,
    info: String?,
    modifier: Modifier = Modifier,
    infoColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth(), color = cs.surfaceContainer, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (info != null) Text(info, style = MaterialTheme.typography.labelMedium, color = infoColor)
            }
            content()
        }
    }
}

/** Colore "caldo" per i valori alti: rosso del tema, ammorbidito. */
@Composable
private fun hotColor(): Color = lerp(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.tertiary, 0.25f)

@Composable
private fun SpectrumCard(env: AppEnv, st: AnalyzerState, sens: Float) {
    val cs = MaterialTheme.colorScheme
    val hot = hotColor()
    val sel = env.analyzerBand.coerceIn(0, NB - 1)
    @Suppress("UNUSED_VARIABLE") val t = st.tick
    AnCard("Spettro", "1/3 d'ottava") {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { o ->
                        env.analyzerBand = ((o.x / size.width) * NB).toInt().coerceIn(0, NB - 1)
                    })
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        env.analyzerBand = ((change.position.x / size.width) * NB).toInt().coerceIn(0, NB - 1)
                    }
                }
        ) {
            st.tick
            val w = size.width
            val h = size.height
            val bw = w / NB
            val gap = 3.dp.toPx().coerceAtMost(bw * 0.3f)
            val minDb = -80f
            fun y(db: Float) = h - h * ((db - minDb) / -minDb).coerceIn(0f, 1f)
            for (m in listOf(-60f, -40f, -20f)) drawLine(cs.outlineVariant, Offset(0f, y(m)), Offset(w, y(m)), 1f)
            drawRoundRect(cs.primary.copy(alpha = 0.12f), Offset(sel * bw, 0f), Size(bw, h), CornerRadius(4.dp.toPx()))
            for (i in 0 until NB) {
                val x = i * bw + gap / 2f
                val top = y(adj(st.bands[i], sens))
                val frac = 1f - top / h
                if (h - top > 0.5f) {
                    drawRoundRect(
                        if (frac > 0.85f) hot else cs.primary,
                        Offset(x, top), Size(bw - gap, h - top),
                        CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    )
                }
                val py = y(adj(st.bandPeak[i], sens))
                if (py < h - 1f) drawRect(cs.onSurface, Offset(x, py), Size(bw - gap, 2.dp.toPx()))
            }
            drawLine(cs.outlineVariant, Offset(0f, h), Offset(w, h), 1f)
        }
        Row(Modifier.fillMaxWidth()) {
            for (l in listOf("20", "100", "1k", "10k")) {
                Text(
                    l, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
                    modifier = Modifier.weight(when (l) { "20" -> 7f; "10k" -> 4f; else -> 10f })
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(st.txtBand.removePrefix("1/3 ott. "), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(st.txtBandVal, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun LevelsCard(st: AnalyzerState, sens: Float) {
    val cs = MaterialTheme.colorScheme
    val hot = hotColor()
    @Suppress("UNUSED_VARIABLE") val t = st.tick
    AnCard("Livelli", "dBFS") {
        for (c in 0..1) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (c == 0) "L" else "R", style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant, modifier = Modifier.width(18.dp)
                )
                Canvas(Modifier.weight(1f).height(12.dp)) {
                    st.tick
                    val w = size.width
                    val h = size.height
                    val r = CornerRadius(h / 2f)
                    val red = w * dbFrac(-6f)
                    drawRoundRect(cs.surfaceContainerHighest, cornerRadius = r)
                    drawRect(hot.copy(alpha = 0.2f), Offset(red, 0f), Size(w - red, h))
                    val lv = w * dbFrac(adj(st.barDb[c], sens))
                    if (lv > 0.5f) {
                        drawRoundRect(cs.primary, size = Size(min(lv, red).coerceAtLeast(h), h), cornerRadius = r)
                        if (lv > red) drawRoundRect(hot, Offset(red - h / 2f, 0f), Size(lv - red + h / 2f, h), cornerRadius = r)
                    }
                    val hx = w * dbFrac(adj(st.barHold[c], sens))
                    if (hx > 2f) drawRect(cs.onSurface, Offset(max(0f, hx - 1.5f), 0f), Size(3f, h))
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            LevelNumbers("Picco vero", st.tpShown, st.tpMax, Modifier.weight(1f))
            LevelNumbers("RMS", st.rmsShown, st.rmsMax, Modifier.weight(1f))
        }
    }
}

@Composable
private fun LevelNumbers(label: String, cur: FloatArray, peak: FloatArray, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        Text(
            "L ${fdb(cur[0])} / ${fdb(peak[0])}",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "R ${fdb(cur[1])} / ${fdb(peak[1])}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun PhaseCard(st: AnalyzerState, sens: Float, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val hot = hotColor()
    @Suppress("UNUSED_VARIABLE") val t = st.tick
    AnCard(
        "Fase", String.format(Locale.ITALY, "%+.2f", st.corr), modifier,
        infoColor = if (st.corr >= 0f) cs.primary else hot,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            Canvas(Modifier.fillMaxSize()) {
                st.tick
                val cx = size.width / 2f
                val cy = size.height / 2f
                val rad = min(cx, cy) - 2f
                val line = cs.outlineVariant
                drawCircle(line, rad, Offset(cx, cy), style = Stroke(1f))
                drawLine(line, Offset(cx, cy - rad), Offset(cx, cy + rad), 1f)
                drawLine(line, Offset(cx - rad, cy), Offset(cx + rad, cy), 1f)
                drawLine(line, Offset(cx - rad * 0.7f, cy - rad * 0.7f), Offset(cx + rad * 0.7f, cy + rad * 0.7f), 1f)
                drawLine(line, Offset(cx + rad * 0.7f, cy - rad * 0.7f), Offset(cx - rad * 0.7f, cy + rad * 0.7f), 1f)
                val col = cs.primary.copy(alpha = 0.7f)
                val zoom = 1.15f * 10f.pow(sens / 20f)
                for (k in 0 until st.gCount) {
                    val x = cx + (st.gx[k] * rad * zoom).coerceIn(-rad, rad)
                    val y = cy - (st.gy[k] * rad * zoom).coerceIn(-rad, rad)
                    drawCircle(col, 1.5f, Offset(x, y))
                }
            }
        }
        // correlazione: a destra in fase, a sinistra in controfase
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            st.tick
            val r = CornerRadius(size.height / 2f)
            drawRoundRect(cs.surfaceContainerHighest, cornerRadius = r)
            val mid = size.width / 2f
            val x = mid + st.corr * mid
            drawRect(if (st.corr >= 0f) cs.primary else hot, Offset(min(mid, x), 0f), Size(abs(x - mid), size.height))
            drawLine(cs.onSurface, Offset(mid, 0f), Offset(mid, size.height), 1.5f)
        }
        Row(Modifier.fillMaxWidth()) {
            Text("−1", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("mono", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text("+1", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun PpmCard(env: AppEnv, st: AnalyzerState, sens: Float, modifier: Modifier) {
    @Suppress("UNUSED_VARIABLE") val t = st.tick
    AnCard("PPM", "picco", modifier) {
        val ref = env.settings.meterRef.toFloat()
        for (c in 0..1) {
            PpmArc(if (c == 0) "L" else "R", adj(st.ppm[c], sens), adj(st.ppmMax[c], sens), st.ppm[c], ref, st)
        }
        Text("dBFS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Mezzo anello da −40 a 0 dBFS: riempito fino al valore, segno al massimo e al riferimento. */
@Composable
private fun PpmArc(label: String, value: Float, peak: Float, real: Float, ref: Float, st: AnalyzerState) {
    val cs = MaterialTheme.colorScheme
    val hot = hotColor()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(72.dp, 42.dp)) {
            st.tick
            val stroke = 8.dp.toPx()
            val d = min(size.width, size.height * 2f) - stroke
            val tl = Offset((size.width - d) / 2f, stroke / 2f)
            val arc = Size(d, d)
            val minDb = -40f
            fun frac(db: Float) = ((db - minDb) / -minDb).coerceIn(0f, 1f)
            drawArc(cs.surfaceContainerHighest, 180f, 180f, false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
            val f = frac(value)
            if (f > 0.005f) drawArc(if (value > -6f) hot else cs.primary, 180f, 180f * f, false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
            val cx = tl.x + d / 2f
            val cy = tl.y + d / 2f
            val rr = d / 2f
            fun tick(db: Float, color: Color, w: Float) {
                val a = (PI + PI * frac(db)).toFloat()
                drawLine(
                    color,
                    Offset(cx + cos(a) * (rr - stroke), cy + sin(a) * (rr - stroke)),
                    Offset(cx + cos(a) * (rr + stroke * 0.6f), cy + sin(a) * (rr + stroke * 0.6f)), w
                )
            }
            tick(ref, cs.tertiary, 2.dp.toPx())
            if (peak > minDb) tick(peak, cs.onSurface, 2.dp.toPx())
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            Text(fdb(real), style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun SensCard(env: AppEnv) {
    val s = env.settings
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Sensibilità", style = MaterialTheme.typography.titleSmall)
                Text("Sposta solo il disegno, i numeri restano veri", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalIconButton(onClick = {
                env.feedback.tapScreen(); s.analyzerSens = (s.analyzerSens - 3).coerceAtLeast(-24)
            }) { Icon(Icons.Filled.Remove, contentDescription = "Diminuisci sensibilità") }
            Text(
                String.format(Locale.ITALY, "%+d dB", s.analyzerSens).replace("-", "−"),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.width(56.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            FilledTonalIconButton(onClick = {
                env.feedback.tapScreen(); s.analyzerSens = (s.analyzerSens + 3).coerceAtMost(24)
            }) { Icon(Icons.Filled.Add, contentDescription = "Aumenta sensibilità") }
        }
    }
}
