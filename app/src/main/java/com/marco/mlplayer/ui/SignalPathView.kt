package com.marco.mlplayer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marco.mlplayer.audio.direct.PathStep
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Percorso del segnale (idea da LastWave): Sorgente -> DSP -> Volume -> Uscita -> Dispositivo, con
 * quello che è stato davvero verificato. In alto le tre opzioni d'uscita.
 */
@Composable
fun SignalPathView(env: AppEnv, showOptions: Boolean = true) {
    val f = Hifi.finish
    val outs = env.engine.outputs
    val path = outs.path
    var checked by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Vfd("PERCORSO DEL SEGNALE", 13.sp, bold = true)
                Text(
                    path.verdict, color = if (path.bitPerfectReal) f.vfd else f.vfd.copy(alpha = 0.8f),
                    fontSize = 13.sp, fontFamily = Hifi.Condensed
                )
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, f.vfd, RoundedCornerShape(16.dp))
                    .clickable(role = Role.Button) {
                        env.feedback.tapScreen()
                        checked = outs.recheck() + " · " + SimpleDateFormat("HH:mm:ss", Locale.ITALY).format(Date())
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) { Vfd("CONTROLLA", 11.sp) }
        }
        if (checked.isNotEmpty()) Text(checked, color = f.vfd.copy(alpha = 0.8f), fontSize = 12.sp, fontFamily = Hifi.Mono)

        if (path.steps.isEmpty()) {
            Text("Avvia un brano per vedere il percorso.", color = f.vfd.copy(alpha = 0.7f), fontSize = 13.sp)
        }
        path.steps.forEachIndexed { i, s -> StepRow(s, last = i == path.steps.lastIndex) }

        if (showOptions) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(f.vfdDim))
            Vfd("USCITA", 12.sp)
            OptRow(
                "USB esclusiva",
                "Il PCM va dritto al DAC con il driver USB dell'app: il mixer di Android non lo tocca. " +
                    "Volume hardware del DAC se c'è, altrimenti software. Serve il permesso USB.",
                env.settings.usbExclusive
            ) { outs.setUsbExclusive(it) }
            OptRow(
                "Uscita diretta",
                "Senza DAC USB: AudioTrack a 24 bit alla frequenza del brano su un'uscita DIRECT (verificata) " +
                    "oppure AAudio esclusiva (MMAP), anche per altoparlante, cuffie e Bluetooth se il telefono lo permette.",
                env.settings.directOutput
            ) { outs.setDirectOutput(it) }
            OptRow(
                "Bit-perfect",
                "Niente DSP, niente ricampionamento: il brano arriva all'uscita così com'è (al 100% del volume). " +
                    "Con il mixer di Android serve Android 14 e un DAC USB che lo supporti.",
                env.settings.bitPerfect
            ) { outs.setBitPerfect(it) }
            Text(
                "Le modifiche valgono dal prossimo brano (o dopo pausa e ripresa del brano).",
                color = f.vfd.copy(alpha = 0.6f), fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun StepRow(s: PathStep, last: Boolean) {
    val f = Hifi.finish
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(26.dp).fillMaxHeight()) {
            Canvas(Modifier.width(26.dp).fillMaxHeight()) {
                val cx = size.width / 2
                if (!last) drawLine(f.vfdDim, Offset(cx, 20.dp.toPx()), Offset(cx, size.height), 2.dp.toPx())
                val col = when (s.ok) { true -> f.vfd; false -> f.specHigh; null -> f.vfdDim }
                drawCircle(col.copy(alpha = 0.35f), 9.dp.toPx(), Offset(cx, 10.dp.toPx()))
                drawCircle(col, 5.dp.toPx(), Offset(cx, 10.dp.toPx()))
            }
        }
        Column(Modifier.weight(1f).padding(start = 8.dp, bottom = if (last) 0.dp else 14.dp)) {
            Text(s.title.uppercase(), color = f.vfd.copy(alpha = 0.65f), fontSize = 11.sp, fontFamily = Hifi.Mono)
            Text(s.main, color = f.vfd, fontSize = 15.sp, fontFamily = Hifi.Condensed)
            for ((k, v) in s.details) {
                Row(Modifier.padding(top = 2.dp)) {
                    Text("$k: ", color = f.vfd.copy(alpha = 0.6f), fontSize = 12.sp, fontFamily = Hifi.Condensed)
                    Text(v, color = f.vfd.copy(alpha = 0.9f), fontSize = 12.sp, fontFamily = Hifi.Condensed)
                }
            }
        }
    }
}

@Composable
private fun OptRow(title: String, desc: String, value: Boolean, onChange: (Boolean) -> Unit) {
    val f = Hifi.finish
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Switch) { onChange(!value) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, color = f.vfd, fontSize = 16.sp, fontFamily = Hifi.Condensed)
            Text(desc, color = f.vfd.copy(alpha = 0.65f), fontSize = 12.sp, fontFamily = Hifi.Condensed)
        }
        HifiSwitch(value, onChange)
    }
}
