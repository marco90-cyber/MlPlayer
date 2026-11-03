package com.marco.mlplayer.ui.eq

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marco.mlplayer.audio.AutoEqDb
import com.marco.mlplayer.audio.DspSettings
import com.marco.mlplayer.audio.PeqBand
import com.marco.mlplayer.ui.AppEnv
import com.marco.mlplayer.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.ln
import kotlin.math.exp

// ------------------------------------------------------------------ parametrico

private fun logPos(v: Float, lo: Float, hi: Float) = (ln(v / lo) / ln(hi / lo)).coerceIn(0f, 1f)
private fun logVal(p: Float, lo: Float, hi: Float) = lo * exp(p * ln(hi / lo))
private fun fmtQ(q: Float) = String.format(Locale.ITALY, "%.2f", q)

@Composable
internal fun ParametricContent(
    env: AppEnv, s: DspSettings,
    onImport: () -> Unit, onExport: () -> Unit, onExportPoweramp: () -> Unit, onMessage: (String) -> Unit,
) {
    val st = env.settings
    val ctx = LocalContext.current
    var edit by remember { mutableIntStateOf(-1) }
    var saveDialog by remember { mutableStateOf(false) }

    fun update(block: (DspSettings) -> DspSettings) = st.updateDsp { d -> block(d).copy(eqMode = DspSettings.EQ_PARAMETRIC) }

    if (s.autoEqName.isNotBlank()) {
        Text("Basato su AutoEQ: ${s.autoEqName}", color = Ml.textDim, fontSize = 12.sp)
    }
    LabeledSlider("Preamp del preset", s.peqPreamp, -20f..0f, fmtDb(s.peqPreamp)) { v ->
        update { it.copy(peqPreamp = (Math.round(v * 10f) / 10f)) }
    }
    if (s.peq.isEmpty()) {
        Text("Nessun filtro. Aggiungine uno o importa un file AutoEQ / Poweramp.", color = Ml.textFaint, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
    }
    s.peq.forEachIndexed { i, b ->
        PeqRow(i, b, onToggle = { on ->
            env.feedback.tapScreen()
            update { d -> d.copy(peq = d.peq.toMutableList().also { l -> l[i] = l[i].copy(on = on) }) }
        }, onClick = { edit = i })
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (s.peq.size < DspSettings.PEQ_MAX) Pill("+ Filtro", selected = false, accent = true) {
            update { d -> d.copy(eq = true, master = true, peq = d.peq + PeqBand(true, PeqBand.PK, 1000f, 0f, 1f)) }
            edit = s.peq.size
        }
        Pill("Importa", selected = false, onClick = onImport)
        Pill("Esporta", selected = false, onClick = onExport)
        Pill("Poweramp", selected = false, onClick = onExportPoweramp)
        Pill("Salva cuffia", selected = false) { saveDialog = true }
        Pill("Azzera", selected = false) { update { it.copy(peq = emptyList(), peqPreamp = 0f, autoEqName = "") } }
    }
    Text("Tocca un filtro per modificarlo. \"Poweramp\" esporta il JSON per l'EQ parametrico di Poweramp.", color = Ml.textFaint, fontSize = 11.sp)

    if (edit in s.peq.indices) {
        PeqDialog(env, s.peq[edit],
            onChange = { nb -> val i = edit; update { d -> d.copy(peq = d.peq.toMutableList().also { l -> if (i in l.indices) l[i] = nb }) } },
            onDelete = { val i = edit; edit = -1; update { d -> d.copy(peq = d.peq.filterIndexed { k, _ -> k != i }) } },
            onDismiss = { edit = -1 })
    }
    if (saveDialog) {
        NameDialog("Salva come cuffia", s.autoEqName, onDismiss = { saveDialog = false }) { name ->
            AutoEqDb.saveCustom(ctx, name, st.dsp)
            st.updateDsp { it.copy(autoEqName = name.trim().take(40)) }
            saveDialog = false
            onMessage("\"${name.trim()}\" salvata: la trovi in cima all'elenco AutoEQ.")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PeqRow(i: Int, b: PeqBand, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp)).background(Ml.chip)
            .combinedClickable(onClick = onClick).padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${i + 1}", color = Ml.textDim, fontSize = 13.sp, modifier = Modifier.width(22.dp))
        Column(Modifier.weight(1f)) {
            Text(PeqBand.label(b.type), color = if (b.on) Ml.text else Ml.textFaint, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            val g = if (b.hasGain) " · ${fmtDb(b.gain)}" else ""
            Text("${fmtHz(b.freq)}$g · Q ${fmtQ(b.q)}", color = if (b.on) Ml.textDim else Ml.textFaint, fontSize = 12.sp)
        }
        MlSwitch(b.on, onToggle)
    }
}

@Composable
private fun PeqDialog(env: AppEnv, b: PeqBand, onChange: (PeqBand) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ml.dialog, titleContentColor = Ml.text, textContentColor = Ml.text,
        title = { Text("Filtro") },
        text = {
            Column {
                Choices(PeqBand.TYPES.map { PeqBand.label(it) }, PeqBand.TYPES.indexOf(b.type)) { k ->
                    env.feedback.tapScreen(); onChange(b.copy(type = PeqBand.TYPES[k]))
                }
                LabeledSlider("Frequenza", logPos(b.freq, 20f, 20000f), 0f..1f, fmtHz(b.freq)) { p ->
                    val f = logVal(p, 20f, 20000f)
                    val r = if (f < 100f) Math.round(f).toFloat() else if (f < 1000f) Math.round(f / 5f) * 5f else Math.round(f / 50f) * 50f
                    onChange(b.copy(freq = r.coerceIn(20f, 20000f)))
                }
                if (b.hasGain) LabeledSlider("Guadagno", b.gain, -15f..15f, fmtDb(b.gain)) { v -> onChange(b.copy(gain = Math.round(v * 10f) / 10f)) }
                LabeledSlider("Q (larghezza)", logPos(b.q, 0.1f, 10f), 0f..1f, fmtQ(b.q)) { p -> onChange(b.copy(q = Math.round(logVal(p, 0.1f, 10f) * 100f) / 100f)) }
                Text("Q alto = filtro stretto; Q basso = filtro largo.", color = Ml.textFaint, fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fine") } },
        dismissButton = { TextButton(onClick = onDelete) { Text("Elimina") } },
    )
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ml.dialog, titleContentColor = Ml.text, textContentColor = Ml.text,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(40) }, singleLine = true, label = { Text("Nome della cuffia") },
                colors = fieldColors(),
            )
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name) }) { Text("Salva") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Ml.text, unfocusedTextColor = Ml.text, focusedBorderColor = Ml.primary,
    unfocusedBorderColor = Ml.outlineVariant, focusedLabelColor = Ml.primary, unfocusedLabelColor = Ml.textDim, cursorColor = Ml.primary,
)

// ------------------------------------------------------------------ AutoEQ

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AutoEqContent(env: AppEnv, s: DspSettings, onMessage: (String) -> Unit) {
    val st = env.settings
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var all by remember { mutableStateOf<List<AutoEqDb.Entry>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    var delete by remember { mutableStateOf<AutoEqDb.Entry?>(null) }

    LaunchedEffect(reload) { all = withContext(Dispatchers.IO) { AutoEqDb.all(ctx.applicationContext) } }

    fun apply(e: AutoEqDb.Entry) {
        busy = "Scarico ${e.name}…"
        scope.launch {
            try {
                val full = withContext(Dispatchers.IO) { AutoEqDb.resolve(e) }
                st.updateDsp {
                    it.copy(eq = true, master = true, eqMode = DspSettings.EQ_AUTOEQ, peq = full.bands ?: emptyList(),
                        peqPreamp = full.preampDb, autoEqName = full.name)
                }
                env.feedback.tapScreen()
            } catch (ex: Exception) {
                AppLog.w("AutoEQ", "Profilo non scaricato", ex)
                onMessage("Non riesco a scaricare il profilo di ${e.name}: serve internet (${ex.message}).")
            }
            busy = null
        }
    }

    Text(
        if (s.autoEqName.isNotBlank()) "Cuffia: ${s.autoEqName}" else "Scegli la tua cuffia",
        color = Ml.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp)
    )
    if (s.autoEqName.isNotBlank()) {
        Text("${s.peq.count { it.on }} filtri · preamp ${fmtDb(s.peqPreamp)}", color = Ml.textDim, fontSize = 12.sp)
    }
    OutlinedTextField(
        value = query, onValueChange = { query = it.take(60) }, singleLine = true,
        label = { Text("Cerca (es. Sennheiser HD 650)") }, colors = fieldColors(),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
    val list = all
    when {
        list == null -> Text("Carico l'elenco…", color = Ml.textFaint, fontSize = 12.sp)
        else -> {
            val found = remember(list, query) { AutoEqDb.search(list, query, 40) }
            if (found.isEmpty()) Text("Nessuna cuffia trovata. Prova \"Aggiorna elenco\" o un nome più corto.", color = Ml.textFaint, fontSize = 12.sp)
            for (e in found) {
                val selected = e.name == s.autoEqName && s.eqMode == DspSettings.EQ_AUTOEQ
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Ml.primaryContainer else Ml.chip)
                        .combinedClickable(onClick = { apply(e) }, onLongClick = { if (e.custom) delete = e })
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(e.name, color = Ml.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(e.label + if (e.bands == null) " · da scaricare" else "", color = Ml.textDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text("${list.size} profili", color = Ml.textFaint, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
    busy?.let { Text(it, color = Ml.primary, fontSize = 12.sp) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Pill("Ritocca nel Parametrico", selected = false, accent = true) { st.updateDsp { it.copy(eqMode = DspSettings.EQ_PARAMETRIC) } }
        Pill("Aggiorna elenco", selected = false) {
            busy = "Aggiorno l'elenco da GitHub…"
            scope.launch {
                try {
                    val n = withContext(Dispatchers.IO) { AutoEqDb.refresh(ctx.applicationContext) }
                    onMessage(if (n == 0) "Elenco aggiornato: nessuna cuffia nuova." else "Elenco aggiornato: $n cuffie nuove (si scaricano quando le scegli).")
                    reload++
                } catch (ex: Exception) {
                    AppLog.w("AutoEQ", "Aggiornamento elenco non riuscito", ex)
                    onMessage("Aggiornamento non riuscito: serve internet (${ex.message}).")
                }
                busy = null
            }
        }
    }
    Text("Profili di AutoEQ (Jaakko Pasanen, licenza MIT). Tieni premuta una cuffia salvata da te per eliminarla.", color = Ml.textFaint, fontSize = 11.sp)

    delete?.let { e ->
        AlertDialog(
            onDismissRequest = { delete = null },
            containerColor = Ml.dialog, titleContentColor = Ml.text, textContentColor = Ml.text,
            title = { Text("Eliminare \"${e.name}\"?") },
            confirmButton = { TextButton(onClick = { AutoEqDb.deleteCustom(ctx, e.name); delete = null; reload++ }) { Text("Elimina") } },
            dismissButton = { TextButton(onClick = { delete = null }) { Text("Annulla") } },
        )
    }
}
