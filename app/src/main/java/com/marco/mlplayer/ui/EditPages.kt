package com.marco.mlplayer.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.marco.mlplayer.data.Track
import com.marco.mlplayer.data.rememberCover
import com.marco.mlplayer.tags.CoverResult
import com.marco.mlplayer.tags.CoverSearch
import com.marco.mlplayer.tags.TagData
import com.marco.mlplayer.tags.TagFiles
import com.marco.mlplayer.util.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

// ---------------------------------------------------------------- menu ⋮ del brano

/** Menu del brano: anteprima in alto, poi Testo, Copertina, Modifica tag. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackMenuSheet(env: AppEnv) {
    val t = env.engine.currentTrack
    if (t == null) return
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = { env.showTrackMenu = false }, containerColor = cs.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverBox(env, t, 72)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(t.artist, t.album).filter { it.isNotBlank() }.joinToString(" — "),
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                val s = t.durationMs / 1000
                Text(
                    String.format(Locale.ITALY, "%d:%02d · %s", s / 60, s % 60, t.ext.lowercase()),
                    style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(Modifier.padding(top = 16.dp, bottom = 4.dp), color = cs.outlineVariant)
        MenuEntry(Icons.Filled.Lyrics, "Testo", "Testo sincronizzato, fonti, traduzione") { env.openPage(Overlay.Lyrics) }
        MenuEntry(Icons.Filled.Image, "Copertina", "Cerca su internet o scegli dalla galleria") { env.openPage(Overlay.Cover) }
        MenuEntry(Icons.Filled.Edit, "Modifica tag", "Titolo, artista, album, anno, genere…") { env.openPage(Overlay.Tags) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MenuEntry(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(sub) },
        leadingContent = { Icon(icon, null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 4.dp)
    )
}

@Composable
private fun CoverBox(env: AppEnv, t: Track, sizeDp: Int) {
    val cs = MaterialTheme.colorScheme
    val file = remember(t.id, env.library.revision) { env.library.trackCoverFile(t) }
    val bmp = rememberCover(file, sizeDp * 3)
    Box(
        Modifier.size(sizeDp.dp).clip(RoundedCornerShape((sizeDp / 5).dp)).background(cs.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Icon(Icons.Filled.MusicNote, null, tint = cs.onPrimaryContainer)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageBar(env: AppEnv, title: String, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = { env.feedback.tapScreen(); env.overlay = Overlay.None }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

// ---------------------------------------------------------------- copertina

/** Pagina Copertina: ricerca su internet, galleria, scrittura nei file dell'album. */
@Composable
fun CoverPage(env: AppEnv) {
    val t = env.editTrack ?: env.engine.currentTrack
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    if (t == null) {
        Column(Modifier.fillMaxSize()) { PageBar(env, "Copertina") }
        return
    }
    var query by remember(t.id) {
        mutableStateOf(listOf(t.albumArtist.ifBlank { t.artist }, t.album.takeIf { it != "Album sconosciuto" } ?: t.title).joinToString(" "))
    }
    var results by remember { mutableStateOf<List<CoverResult>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<CoverResult?>(null) }
    val hidden = remember { mutableStateOf(setOf<String>()) }

    fun applyBytes(raw: ByteArray?) {
        val jpeg = raw?.let { CoverSearch.toJpeg(it) }
        if (jpeg == null) {
            saving = false
            env.message = "Immagine non valida"
            return
        }
        // solo questo brano: per un album intero c'è "Cerca le copertine" nella cartella
        env.library.setTrackCover(t, jpeg)
        env.writeFiles(listOf(t), "Copertina", { tr -> TagFiles.writeCover(env.app, env.settings, tr, jpeg) }, done = { saving = false })
    }

    fun search() {
        searching = true
        scope.launch {
            results = CoverSearch.search(query)
            hidden.value = emptySet()
            searching = false
        }
    }
    LaunchedEffect(t.id) { if (results == null) search() }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        saving = true
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() }
            withContext(Dispatchers.Default) { applyBytes(bytes) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        PageBar(env, "Copertina")
        if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverBox(env, t, 120)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(listOf(t.artist, t.album).filter { it.isNotBlank() }.joinToString(" — "), color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!TagFiles.canEdit(t.ext)) Text(
                        "Formato ${t.ext.uppercase()}: la copertina resta solo nell'app",
                        style = MaterialTheme.typography.bodySmall, color = cs.error
                    )
                }
            }
            OutlinedButton(
                onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !saving, modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.PhotoLibrary, null)
                Spacer(Modifier.width(8.dp))
                Text("Scegli dalla galleria")
            }
            Text("Cerca su internet", style = MaterialTheme.typography.titleSmall, color = cs.primary, modifier = Modifier.padding(top = 8.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                label = { Text("Artista e album") },
                trailingIcon = { IconButton(onClick = { search() }) { Icon(Icons.Filled.Search, "Cerca") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
                modifier = Modifier.fillMaxWidth()
            )
            when {
                searching -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                results?.isEmpty() == true -> Text("Nessuna copertina trovata. Prova a cambiare le parole.", color = cs.onSurfaceVariant)
                results != null -> {
                    val shown = results!!.filter { it.thumb !in hidden.value }
                    for (row in shown.chunked(2)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            for (r in row) CoverCard(r, Modifier.weight(1f), onError = { hidden.value = hidden.value + r.thumb }) {
                                if (!saving) confirm = r
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    Text(
                        "Fonti: iTunes, Deezer, Cover Art Archive (MusicBrainz)",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    val c = confirm
    if (c != null) AlertDialog(
        onDismissRequest = { confirm = null },
        title = { Text("Usare questa copertina?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AsyncImage(
                    model = c.thumb, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp))
                )
                Text("${c.title} — ${c.artist}", style = MaterialTheme.typography.bodyMedium)
                Text("Verrà scritta solo in questo brano.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = {
                confirm = null
                saving = true
                scope.launch {
                    val bytes = withContext(Dispatchers.IO) { Http.bytes(c.full) ?: Http.bytes(c.thumb) }
                    withContext(Dispatchers.Default) { applyBytes(bytes) }
                }
            }) { Text("Applica") }
        },
        dismissButton = { TextButton(onClick = { confirm = null }) { Text("Annulla") } },
    )
}

@Composable
private fun CoverCard(r: CoverResult, modifier: Modifier, onError: () -> Unit, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surfaceContainer, shape = RoundedCornerShape(16.dp), modifier = modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)) {
        Column {
            AsyncImage(
                model = r.thumb, contentDescription = r.title, contentScale = ContentScale.Crop,
                onError = { onError() },
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(cs.surfaceContainerHighest)
            )
            Column(Modifier.padding(10.dp)) {
                Text(r.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(r.artist, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${r.source} · ${r.size}", style = MaterialTheme.typography.labelSmall, color = cs.primary, maxLines = 1)
            }
        }
    }
}

// ---------------------------------------------------------------- tag

/** Pagina Modifica tag del brano. */
@Composable
fun TagEditorPage(env: AppEnv) {
    val t = env.editTrack ?: env.engine.currentTrack
    val cs = MaterialTheme.colorScheme
    if (t == null) {
        Column(Modifier.fillMaxSize()) { PageBar(env, "Modifica tag") }
        return
    }
    val fromLibrary = TagData(
        title = t.title, artist = t.artist, album = t.album.takeIf { it != "Album sconosciuto" }.orEmpty(),
        albumArtist = t.albumArtist, year = if (t.year > 0) "${t.year}" else "", genre = t.genre.takeIf { it != "Sconosciuto" }.orEmpty(),
        track = if (t.trackNo > 0) "${t.trackNo}" else "", disc = if (t.discNo > 0) "${t.discNo}" else "",
    )
    var data by remember(t.id) { mutableStateOf(fromLibrary) }
    var loaded by remember(t.id) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val editable = TagFiles.canEdit(t.ext)
    LaunchedEffect(t.id) {
        if (editable) withContext(Dispatchers.IO) { TagFiles.read(env.app, t) }?.let { data = it }
        loaded = true
    }

    Column(Modifier.fillMaxSize()) {
        PageBar(env, "Modifica tag") {
            TextButton(
                enabled = editable && loaded && !saving,
                onClick = {
                    saving = true
                    val d = data
                    env.writeFiles(listOf(t), "Tag", { tr -> TagFiles.writeTags(env.app, env.settings, tr, d) }, done = {
                        saving = false
                        env.overlay = Overlay.None
                    })
                }
            ) { Text("Salva") }
        }
        if (saving || !loaded) LinearProgressIndicator(Modifier.fillMaxWidth())
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                CoverBox(env, t, 64)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(t.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.detailLine(), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            if (!editable) {
                Surface(color = cs.errorContainer, shape = RoundedCornerShape(16.dp)) {
                    Text(
                        "I tag dei file ${t.ext.uppercase()} non si possono modificare. Formati supportati: MP3, FLAC, M4A, OGG, WAV, AIFF, DSF.",
                        color = cs.onErrorContainer, modifier = Modifier.padding(16.dp)
                    )
                }
            }
            TagField("Titolo", data.title, editable) { data = data.copy(title = it) }
            TagField("Artista", data.artist, editable) { data = data.copy(artist = it) }
            TagField("Album", data.album, editable) { data = data.copy(album = it) }
            TagField("Artista dell'album", data.albumArtist, editable) { data = data.copy(albumArtist = it) }
            TagField("Genere", data.genre, editable) { data = data.copy(genre = it) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TagField("Anno", data.year, editable, number = true, modifier = Modifier.weight(1f)) { data = data.copy(year = it) }
                TagField("Traccia", data.track, editable, number = true, modifier = Modifier.weight(1f)) { data = data.copy(track = it) }
                TagField("Disco", data.disc, editable, number = true, modifier = Modifier.weight(1f)) { data = data.copy(disc = it) }
            }
            Text(
                "Le modifiche vengono scritte dentro il file. La prima volta Android ti chiederà di confermare la cartella della musica.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TagField(
    label: String, value: String, enabled: Boolean, number: Boolean = false,
    modifier: Modifier = Modifier.fillMaxWidth(), onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true, enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text, imeAction = ImeAction.Next),
        modifier = modifier,
    )
}
