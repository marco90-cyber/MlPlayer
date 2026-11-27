package com.marco.mlplayer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.outlined.LightMode as LightModeOutlined
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.marco.mlplayer.data.Track
import com.marco.mlplayer.lyrics.LLine
import com.marco.mlplayer.lyrics.LyricsProvider
import com.marco.mlplayer.lyrics.LyricsStore
import com.marco.mlplayer.lyrics.SourceState
import com.marco.mlplayer.util.mmss
import android.widget.Toast
import com.marco.mltheme.MLGroup
import com.marco.mltheme.MLNavRow
import com.marco.mltheme.MLSwitchRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** Nome della lingua del telefono, per "Traduci in italiano". */
private fun languageName(): String = Locale.getDefault().getDisplayLanguage(Locale.getDefault()).lowercase()

private fun fmtOffset(ms: Int): String =
    (if (ms > 0) "+" else if (ms < 0) "−" else "") + String.format(Locale.ITALY, "%.1f s", kotlin.math.abs(ms) / 1000f)

/** Pagina Testo: testo sincronizzato che scorre con la musica, oppure testo semplice. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsPage(env: AppEnv) {
    val e = env.engine
    val t = e.currentTrack
    val store = env.lyrics
    val s = env.settings
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var showOptions by remember { mutableStateOf(false) }
    var showSources by remember { mutableStateOf(false) }
    var showOffset by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    // schermo sempre acceso: vale solo in questa schermata, si spegne da solo quando esci dai testi
    var keepOn by remember { mutableStateOf(false) }
    val view = LocalView.current
    val ctx = LocalContext.current
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(t?.id) { if (t != null) store.load(t) }
    LaunchedEffect(store.lyrics, s.lyricsTranslate) { if (s.lyricsTranslate) store.loadTranslation() }
    LaunchedEffect(store.lyrics, s.lyricsRomanize) { if (s.lyricsRomanize) store.loadRomanization() }

    val tint = rememberCoverTint(env, t)
    val onDark = cs.copy(
        primary = Color.White, onPrimary = Color(0xFF1B1B1B),
        onSurface = Color.White, onSurfaceVariant = Color.White.copy(alpha = 0.72f),
        secondaryContainer = Color.White.copy(alpha = 0.18f), onSecondaryContainer = Color.White,
        surfaceContainer = Color.Black.copy(alpha = 0.22f),
    )
    MaterialTheme(colorScheme = onDark, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize()) {
    LyricsBackdrop(tint, Modifier.matchParentSize())
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(t?.title ?: "Testo", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (t != null) Text(
                        t.artist, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = { env.feedback.tapScreen(); env.overlay = Overlay.None }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        )

        val l = store.lyrics
        // fonte e tipo di sincronizzazione (tocca per cambiare fonte), con accanto schermo sempre acceso e opzioni
        if (t != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                if (l != null && !store.loading) {
                    Surface(
                        color = cs.secondaryContainer, shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { showSources = true }
                    ) {
                        Text(
                            "${l.kind.label} · ${l.source}", style = MaterialTheme.typography.labelLarge,
                            color = cs.onSecondaryContainer, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                val awakeClick = {
                    env.feedback.tapScreen()
                    keepOn = !keepOn
                    Toast.makeText(
                        ctx, if (keepOn) "Schermo sempre acceso: attivo" else "Schermo sempre acceso: disattivato",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                if (keepOn) FilledTonalIconButton(onClick = awakeClick) {
                    Icon(Icons.Filled.LightMode, contentDescription = "Schermo sempre acceso: attivo")
                } else IconButton(onClick = awakeClick) {
                    Icon(Icons.Outlined.LightModeOutlined, contentDescription = "Schermo sempre acceso: disattivato", tint = cs.onSurfaceVariant)
                }
                IconButton(onClick = { env.feedback.tapScreen(); showOptions = true }) {
                    Icon(Icons.Filled.Tune, contentDescription = "Opzioni del testo")
                }
            }
        }
        val tree = store.needsAccess
        if (tree != null) {
            Surface(color = cs.tertiaryContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
                Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Il testo è salvato nell'app. Per scriverlo nel file serve il permesso sulla cartella.",
                        style = MaterialTheme.typography.bodySmall, color = cs.onTertiaryContainer, modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        env.requestWrite(tree) { scope.launch { store.flushPending(e.currentTrack?.uri) } }
                    }) { Text("Consenti") }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                t == null -> Center("Nessun brano in riproduzione")
                store.loading -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Cerco il testo…", color = cs.onSurfaceVariant)
                }
                l == null || l.isEmpty -> Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Filled.Lyrics, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(48.dp))
                    Text("Nessun testo trovato", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Puoi scegliere un'altra fonte o scriverlo tu.",
                        color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { showSources = true }) { Text("Scegli la fonte") }
                        OutlinedButton(onClick = { showEdit = true }) { Text("Scrivi il testo") }
                    }
                }
                l!!.synced -> SyncedLyrics(env, t!!, l.lines)
                else -> PlainLyrics(env, l!!.lines)
            }
            if (t != null && showOffset) OffsetPanel(env, t, Modifier.align(Alignment.BottomCenter)) { showOffset = false }
        }

        if (t != null) LyricsTransportBar(env)
    }
    }
    }

    if (t != null && showOptions) LyricsOptionsSheet(
        env, t,
        onDismiss = { showOptions = false },
        onSources = { showOptions = false; showSources = true },
        onOffset = { showOptions = false; showOffset = true },
        onEdit = { showOptions = false; showEdit = true },
    )
    if (t != null && showSources) LyricsSourcesSheet(env, t) { showSources = false }
    if (t != null && showEdit) EditLyricsDialog(env, t) { showEdit = false }
}

@Composable
private fun Center(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------- testo sincronizzato

@Composable
private fun SyncedLyrics(env: AppEnv, t: Track, lines: List<LLine>) {
    val e = env.engine
    val store = env.lyrics
    val s = env.settings
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var pos by remember { mutableLongStateOf(0L) }
    @Suppress("UNUSED_VARIABLE") val tick = store.offsetTick
    val offset = store.offset(t)

    LaunchedEffect(t.id, e.isPlaying) {
        while (true) {
            pos = e.player.currentPosition
            delay(if (e.isPlaying) 40 else 250)
        }
    }
    val now = pos + offset
    val current = lines.indexOfLast { it.start in 0..now }.coerceAtLeast(-1)

    val list = rememberLazyListState()
    var userScrollAt by remember { mutableLongStateOf(0L) }
    var auto by remember { mutableStateOf(false) }
    LaunchedEffect(list.isScrollInProgress) {
        if (list.isScrollInProgress && !auto) userScrollAt = System.currentTimeMillis()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val third = constraints.maxHeight / 3
        LaunchedEffect(current) {
            if (current >= 0 && System.currentTimeMillis() - userScrollAt > 3500) {
                auto = true
                runCatching { list.animateScrollToItem(current, -third) }
                auto = false
            }
        }
        val scale = s.lyricsScale
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 320.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            itemsIndexed(lines) { i, line ->
                val active = i == current
                val dist = if (current < 0) i else kotlin.math.abs(i - current)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            env.feedback.tapScreen()
                            if (line.start >= 0) e.player.seekTo((line.start - offset).coerceAtLeast(0))
                            userScrollAt = 0
                        }
                        .padding(vertical = 4.dp)
                ) {
                    // parole che si alzano mentre vengono cantate, righe lontane sfumate (stile LastWave)
                    LyricRow(line, active, dist, if (active) now else 0L, (30 * scale).sp)
                    store.romanization?.getOrNull(i)?.takeIf { it.isNotBlank() && s.lyricsRomanize }?.let {
                        Text(it, fontSize = (16 * scale).sp, fontStyle = FontStyle.Italic, color = cs.onSurfaceVariant.copy(alpha = if (active) 1f else 0.6f))
                    }
                    store.translation?.getOrNull(i)?.takeIf { it.isNotBlank() && s.lyricsTranslate }?.let {
                        Text(it, fontSize = (17 * scale).sp, color = cs.primary.copy(alpha = if (active) 1f else 0.6f))
                    }
                }
            }
        }
        // torna alla riga attuale dopo aver scorso a mano
        if (System.currentTimeMillis() - userScrollAt < 3500 && current >= 0) {
            FilledTonalButton(
                onClick = { userScrollAt = 0; scope.launch { list.animateScrollToItem(current, -third) } },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
            ) { Text("Torna alla riga attuale") }
        }
    }
}

@Composable
private fun PlainLyrics(env: AppEnv, lines: List<LLine>) {
    val s = env.settings
    val store = env.lyrics
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        lines.forEachIndexed { i, line ->
            Text(line.text, fontSize = (20 * s.lyricsScale).sp, lineHeight = (28 * s.lyricsScale).sp, fontWeight = FontWeight.Medium)
            store.romanization?.getOrNull(i)?.takeIf { it.isNotBlank() && s.lyricsRomanize }?.let {
                Text(it, fontSize = (15 * s.lyricsScale).sp, fontStyle = FontStyle.Italic, color = cs.onSurfaceVariant)
            }
            store.translation?.getOrNull(i)?.takeIf { it.isNotBlank() && s.lyricsTranslate }?.let {
                Text(it, fontSize = (16 * s.lyricsScale).sp, color = cs.primary)
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}

/** Avanzamento e comandi in basso nella pagina Testo. */
@Composable
private fun LyricsTransport(env: AppEnv) {
    val e = env.engine
    val cs = MaterialTheme.colorScheme
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var drag by remember { mutableStateOf(-1f) }
    LaunchedEffect(e.currentTrack?.id, e.isPlaying) {
        while (true) {
            pos = e.player.currentPosition
            dur = e.player.duration.coerceAtLeast(0)
            delay(300)
        }
    }
    Column(
        Modifier.fillMaxWidth().background(cs.surfaceContainer).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Slider(
            value = if (drag >= 0) drag else if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
            onValueChange = { drag = it },
            onValueChangeFinished = { if (drag >= 0) e.seekToFraction(drag); drag = -1f },
            modifier = Modifier.height(28.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(mmss(pos), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.width(48.dp))
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { env.feedback.tapScreen(); e.previous() }) { Icon(Icons.Filled.SkipPrevious, "Brano precedente") }
            FilledIconButton(onClick = { env.feedback.tapScreen(); env.playPause() }, modifier = Modifier.size(52.dp)) {
                Icon(if (e.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (e.isPlaying) "Pausa" else "Riproduci")
            }
            IconButton(onClick = { env.feedback.tapScreen(); e.next() }) { Icon(Icons.Filled.SkipNext, "Brano successivo") }
            Spacer(Modifier.weight(1f))
            Text(
                if (dur > 0) "−" + mmss(dur - pos) else "--:--", style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant, modifier = Modifier.width(48.dp)
            )
        }
    }
}


// ---------------------------------------------------------------- opzioni

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LyricsOptionsSheet(
    env: AppEnv, t: Track,
    onDismiss: () -> Unit, onSources: () -> Unit, onOffset: () -> Unit, onEdit: () -> Unit,
) {
    val s = env.settings
    val store = env.lyrics
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Lyrics, null, tint = cs.onPrimaryContainer)
            }
            Text("Opzioni del testo", style = MaterialTheme.typography.headlineSmall)
            MLGroup {
                val l = store.lyrics
                MLNavRow(
                    "Fonte del testo",
                    if (l != null) "${l.source} · ${l.kind.label}" else "Nessun testo",
                    icon = Icons.Filled.Lyrics
                ) { onSources() }
                if (l != null && !l.isEmpty) MLNavRow("Condividi il testo", null, icon = Icons.Filled.Share) {
                    env.actions?.shareText("${t.title} — ${t.artist}\n\n${l.plainText}", "Testo di ${t.title}")
                }
                MLNavRow("Modifica il testo", "Correggi o scrivi il testo a mano", icon = Icons.Filled.EditNote) { onEdit() }
            }
            MLGroup {
                Column(Modifier.fillMaxWidth().background(cs.surfaceContainer).padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.FormatSize, null, tint = cs.primary)
                        Spacer(Modifier.width(16.dp))
                        Text("Dimensione del testo: ${(s.lyricsScale * 100).toInt()}%", style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("A", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = s.lyricsScale, onValueChange = { s.lyricsScale = (it * 20).toInt() / 20f },
                            valueRange = 0.7f..1.6f, modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                        )
                        Text("A", style = MaterialTheme.typography.titleLarge)
                    }
                }
                MLSwitchRow(
                    "Traduci in ${languageName()}", "Traduzione sotto ogni riga (Google Traduttore)",
                    s.lyricsTranslate, icon = Icons.Filled.Translate
                ) { s.lyricsTranslate = it }
                MLSwitchRow(
                    "Romanizza", "Pronuncia in lettere latine (giapponese, coreano, cinese, russo…)",
                    s.lyricsRomanize, icon = Icons.Filled.Language
                ) { s.lyricsRomanize = it }
                MLNavRow(
                    "Ritardo di sincronizzazione", fmtOffset(store.offset(t) + store.offsetTick * 0),
                    icon = Icons.Filled.Timer
                ) { onOffset() }
                MLSwitchRow(
                    "Salva i testi nei file", "I testi trovati vengono scritti anche dentro il brano",
                    s.lyricsSaveToFile, icon = Icons.Filled.Save
                ) { s.lyricsSaveToFile = it }
            }
            if (store.extraLoading) Text("Sto preparando traduzione e pronuncia…", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 52.dp)) {
                Text("Fatto")
            }
        }
    }
}

/** Scelta della fonte del testo, con lo stato di ogni fonte. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LyricsSourcesSheet(env: AppEnv, t: Track, onDismiss: () -> Unit) {
    val store = env.lyrics
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    LaunchedEffect(t.id) {
        if (LyricsProvider.entries.any { store.sources[it.id] == null }) store.searchOnline(t, experimental = true)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Fonte del testo", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Chiudi") }
            }
            val l = store.lyrics
            Surface(color = cs.surfaceContainerHigh, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(
                    if (l != null) "Attuale: ${l.source} (${l.kind.label})" else "Attuale: nessun testo",
                    style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp)
                )
            }
            Surface(
                color = cs.primaryContainer, shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable {
                    env.feedback.tapScreen()
                    scope.launch { store.tryAnother(t) }
                }
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Refresh, null, tint = cs.onPrimaryContainer)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Prova un'altra fonte", style = MaterialTheme.typography.titleMedium, color = cs.onPrimaryContainer)
                        Text("Esclude la fonte attuale e cerca nelle altre", style = MaterialTheme.typography.bodySmall, color = cs.onPrimaryContainer)
                    }
                }
            }
            Text(
                "Oppure scegli una fonte", style = MaterialTheme.typography.titleSmall, color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
            )
            SourceRow("Automatica", "Prima il testo sincronizzato a parole, poi a righe, poi quello semplice", store.chosen == LyricsStore.AUTO, "Auto") {
                scope.launch { store.choose(t, LyricsStore.AUTO) }
            }
            for (local in listOf(LyricsStore.SRC_FILE, LyricsStore.SRC_LRC)) {
                val st = store.sources[local] as? SourceState.Found ?: continue
                SourceRow(local, if (local == LyricsStore.SRC_FILE) "Testo salvato dentro il brano" else "File .lrc nella cartella del brano",
                    store.chosen == local, st.lyrics.kind.label) { scope.launch { store.choose(t, local) } }
            }
            for (p in LyricsProvider.entries.sortedBy { it.experimental }) {
                if (p.experimental && p == LyricsProvider.entries.first { it.experimental }) Text(
                    "Sperimentali · solo se le scegli tu", style = MaterialTheme.typography.titleSmall, color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
                val st = store.sources[p.id]
                val status = when (st) {
                    is SourceState.Found -> st.lyrics.kind.label
                    SourceState.Searching -> "Cerco…"
                    SourceState.None -> "Nessun testo"
                    else -> ""
                }
                SourceRow(p.title, p.description, store.chosen == p.id, status, enabled = st is SourceState.Found) {
                    scope.launch { store.choose(t, p.id) }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(title: String, sub: String, selected: Boolean, status: String, enabled: Boolean = true, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) cs.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) cs.onSurface else cs.onSurface.copy(alpha = 0.5f))
            Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        if (status == "Cerco…") CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        else Text(status, style = MaterialTheme.typography.labelLarge, color = if (enabled) cs.primary else cs.onSurfaceVariant)
    }
}

/**
 * Ritardo di sincronizzazione: pannello compatto sopra il testo (non una finestra a tutto schermo con velo scuro),
 * così mentre si cambia il valore si vede subito la riga evidenziata spostarsi.
 */
@Composable
private fun OffsetPanel(env: AppEnv, t: Track, modifier: Modifier = Modifier, onDismiss: () -> Unit) {
    val store = env.lyrics
    val cs = MaterialTheme.colorScheme
    @Suppress("UNUSED_VARIABLE") val tick = store.offsetTick
    val v = store.offset(t)
    Surface(
        modifier = modifier.fillMaxWidth().padding(12.dp),
        shape = RoundedCornerShape(24.dp),
        color = cs.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Timer, contentDescription = null, tint = cs.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text("Ritardo", style = MaterialTheme.typography.titleSmall, color = cs.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Text(fmtOffset(v), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { store.setOffset(t, 0) }) { Text("Azzera") }
                TextButton(onClick = onDismiss) { Text("Fatto") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (d in listOf(-500, -100, 100, 500)) {
                    OutlinedButton(
                        onClick = { store.setOffset(t, v + d) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) {
                        Text((if (d > 0) "+" else "−") + String.format(Locale.ITALY, "%.1f", kotlin.math.abs(d) / 1000f))
                    }
                }
            }
            Text(
                "Positivo: il testo arriva prima · negativo: dopo. Vale solo per questo brano.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun EditLyricsDialog(env: AppEnv, t: Track, onDismiss: () -> Unit) {
    val store = env.lyrics
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(store.lyrics?.toLrc() ?: "") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Annulla") }
                    Text("Modifica il testo", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Button(onClick = {
                        scope.launch { store.saveManual(t, text) }
                        onDismiss()
                    }) { Text("Salva") }
                }
                Text(
                    "Puoi usare il formato LRC con i tempi, per esempio [01:23.45] seguito dalla riga.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    textStyle = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
