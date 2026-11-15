package com.marco.mlplayer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.marco.mlplayer.data.Track
import com.marco.mlplayer.data.rememberCover
import com.marco.mlplayer.tags.CoverResult
import com.marco.mlplayer.tags.CoverSearch
import com.marco.mlplayer.tags.TagFiles
import com.marco.mlplayer.tags.WriteResult
import com.marco.mlplayer.util.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private enum class JobState { Searching, Found, None, Saving, Done, Failed }

/** Un brano della cartella con la copertina trovata per lui. */
private class AlbumJob(val track: Track, val hadCover: Boolean) {
    val key: String get() = track.id
    val title: String get() = track.title
    val artist: String get() = track.artist
    var results by mutableStateOf<List<CoverResult>>(emptyList())
    var choice by mutableStateOf<CoverResult?>(null)
    var selected by mutableStateOf(false)
    var state by mutableStateOf(JobState.Searching)
    val query: String get() = "${Providers2.cleanArtist(artist)} ${Providers2.cleanTitle(title)}"
}

/** Pulizia di titolo e artista per la ricerca (feat., "official video"...). */
private object Providers2 {
    fun cleanTitle(s: String) = s.replace(Regex("""(?i)\s*[(\[](?:feat\.?|ft\.?|with|official|video|audio|lyrics?)[^)\]]*[)\]]"""), "").trim().ifBlank { s }
    fun cleanArtist(s: String) = s.replace(Regex("""(?i)\s*(?:feat\.?|ft\.?|&|,)\s+.*$"""), "").trim().ifBlank { s }
}

private fun norm(s: String) = s.lowercase().replace(Regex("\\(.*?\\)|\\[.*?]"), "").replace(Regex("[^\\p{L}\\p{N}]+"), "")

/** La copertina giusta per il brano: titolo e artista devono corrispondere, altrimenti nessuna. */
private fun bestMatch(job: AlbumJob, results: List<CoverResult>): CoverResult? {
    val a = norm(Providers2.cleanTitle(job.title))
    val ar = norm(Providers2.cleanArtist(job.artist))
    fun same(x: String, y: String) = x.isNotEmpty() && y.isNotEmpty() && (x == y || x.contains(y) || y.contains(x))
    return results.firstOrNull { same(norm(it.title), a) && same(norm(it.artist), ar) }
        ?: results.firstOrNull { norm(it.title) == a && a.length >= 4 }
}

/** "Cerca le copertine": ogni brano di una cartella, con la sua copertina trovata in automatico e scritta nel suo file. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkCoverPage(env: AppEnv) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val jobs = remember(env.bulkTracks) {
        env.bulkTracks.distinctBy { it.uri }
            .map { AlbumJob(it, env.library.trackCoverFile(it) != null) }
            .sortedWith(compareBy({ it.hadCover }, { it.title.lowercase() }))
    }
    var onlyMissing by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var done by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var picking by remember { mutableStateOf<AlbumJob?>(null) }

    LaunchedEffect(jobs) {
        val gate = Semaphore(3)
        jobs.map { j ->
            async {
                gate.withPermit {
                    val r = CoverSearch.searchSong(Providers2.cleanArtist(j.artist), Providers2.cleanTitle(j.title))
                    j.results = r
                    j.choice = bestMatch(j, r)
                    j.state = if (j.choice != null) JobState.Found else JobState.None
                    j.selected = j.choice != null && !j.hadCover
                }
            }
        }.awaitAll()
    }

    fun apply(list: List<AlbumJob>, askAccess: Boolean = true) {
        saving = true
        done = 0
        total = list.size
        env.app.appScope.launch {
            val okUris = ArrayList<String>()
            var appOnly = 0
            var needTree: String? = null
            val remaining = ArrayList<AlbumJob>()
            for ((i, j) in list.withIndex()) {
                if (needTree != null) { remaining += j; continue }
                val c = j.choice ?: continue
                j.state = JobState.Saving
                val jpeg = withContext(Dispatchers.IO) { (Http.bytes(c.full) ?: Http.bytes(c.thumb))?.let { CoverSearch.toJpeg(it) } }
                if (jpeg == null) { j.state = JobState.Failed; done = i + 1; continue }
                val t = j.track
                withContext(Dispatchers.IO) { env.library.setTrackCover(t, jpeg) }
                if (!TagFiles.canEdit(t.ext)) appOnly++
                else when (val r = withContext(Dispatchers.IO) { TagFiles.writeCover(env.app, env.settings, t, jpeg) }) {
                    is WriteResult.Ok -> okUris += t.uri
                    is WriteResult.NoAccess -> needTree = r.tree
                    else -> appOnly++
                }
                if (needTree != null) { j.state = JobState.Found; remaining += j; continue }
                j.state = JobState.Done
                j.selected = false
                done = i + 1
            }
            if (okUris.isNotEmpty()) {
                env.library.refreshTracks(okUris)
                val byUri = env.library.tracks.filter { it.uri in okUris }.associateBy { it.uri }
                env.engine.replaceTracks(byUri, okUris.toSet())
            }
            saving = false
            val tree = needTree
            if (tree != null) {
                if (askAccess) env.requestWrite(tree) { apply(remaining, askAccess = false) }
                else env.message = "La cartella scelta non contiene questi brani: serve «${folderName(tree)}» o una che la contiene"
            } else {
                env.message = buildString {
                    append("Copertine applicate a ${list.count { it.state == JobState.Done }} brani")
                    if (appOnly > 0) append(" · $appOnly brani solo nell'app (formato non scrivibile)")
                }
            }
        }
    }

    val shown = jobs.filter { !onlyMissing || !it.hadCover }
    val selected = jobs.filter { it.selected && it.choice != null }
    val searching = jobs.count { it.state == JobState.Searching }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Cerca le copertine")
                    Text(env.bulkTitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            navigationIcon = {
                IconButton(onClick = { env.feedback.tapScreen(); env.overlay = Overlay.None }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.surface),
        )
        if (saving) LinearProgressIndicator(progress = { if (total > 0) done.toFloat() / total else 0f }, modifier = Modifier.fillMaxWidth())
        else if (searching > 0) LinearProgressIndicator(Modifier.fillMaxWidth())

        com.marco.mltheme.MLGroup(modifier = Modifier.padding(top = 4.dp)) {
            com.marco.mltheme.MLSwitchRow(
                "Solo brani senza copertina",
                "${jobs.count { !it.hadCover }} senza copertina su ${jobs.size} brani",
                onlyMissing
            ) { onlyMissing = it }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (shown.isEmpty()) item {
                Text("Tutti i brani hanno già una copertina.", color = cs.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
            items(shown, key = { it.key }) { j -> AlbumJobRow(env, j, enabled = !saving) { picking = j } }
        }

        Surface(color = cs.surfaceContainer) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    when {
                        saving -> "Salvo… $done di $total"
                        searching > 0 -> "Cerco… ${jobs.size - searching} di ${jobs.size}"
                        else -> "${selected.size} brani selezionati"
                    },
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f)
                )
                Button(onClick = { apply(selected) }, enabled = !saving && selected.isNotEmpty()) {
                    Text("Applica a ${selected.size}")
                }
            }
        }
    }

    val p = picking
    if (p != null) PickCoverDialog(env, p, scope) { picking = null }
}

@Composable
private fun AlbumJobRow(env: AppEnv, j: AlbumJob, enabled: Boolean, onPick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.surfaceContainer, shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = j.selected, enabled = enabled && j.choice != null && j.state != JobState.Done,
                onCheckedChange = { j.selected = it }
            )
            // copertina attuale
            val file = remember(j.key, env.library.revision) { env.library.trackCoverFile(j.track) }
            val bmp = rememberCover(file, 160)
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                if (bmp != null) Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(Icons.Filled.MusicNote, null, tint = cs.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp).size(18.dp))
            // copertina trovata: tocca per sceglierne un'altra
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerHighest)
                    .clickable(enabled = enabled && j.state != JobState.Searching, onClick = onPick),
                contentAlignment = Alignment.Center
            ) {
                when {
                    j.state == JobState.Searching || j.state == JobState.Saving -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    j.choice != null -> AsyncImage(j.choice!!.thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else -> Icon(Icons.Filled.Search, "Scegli", tint = cs.onSurfaceVariant)
                }
                if (j.state == JobState.Done) Icon(
                    Icons.Filled.CheckCircle, "Applicata", tint = cs.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(18.dp).background(cs.surface, RoundedCornerShape(50))
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(j.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(j.artist, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when (j.state) {
                        JobState.Searching -> "Cerco…"
                        JobState.Found -> listOf(j.choice?.source, j.choice?.album).filter { !it.isNullOrBlank() }.joinToString(" · ")
                        JobState.None -> "Nessuna sicura · tocca per scegliere"
                        JobState.Saving -> "Salvo…"
                        JobState.Done -> "Applicata"
                        JobState.Failed -> "Download non riuscito"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (j.state == JobState.Failed) cs.error else cs.primary,
                    maxLines = 1
                )
            }
            if (j.state == JobState.Failed) Icon(Icons.Filled.ErrorOutline, null, tint = cs.error)
        }
    }
}

/** Scelta manuale tra i risultati (con una nuova ricerca se serve). */
@Composable
private fun PickCoverDialog(env: AppEnv, j: AlbumJob, scope: kotlinx.coroutines.CoroutineScope, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var query by remember { mutableStateOf(j.query) }
    var loading by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(j.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            loading = true
                            scope.launch {
                                // prima per brano, poi come album se non trova niente
                                j.results = CoverSearch.searchSong("", query).ifEmpty { CoverSearch.search(query) }
                                loading = false
                            }
                        }) { Icon(Icons.Filled.Search, "Cerca") }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (loading) Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                if (!loading && j.results.isEmpty()) Text("Nessun risultato", color = cs.onSurfaceVariant)
                for (row in j.results.chunked(3)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (r in row) Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable {
                                j.choice = r
                                j.state = JobState.Found
                                j.selected = true
                                onDismiss()
                            }
                        ) {
                            AsyncImage(
                                r.thumb, r.title, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerHighest)
                            )
                            Text(r.album.ifBlank { r.title }, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(r.source, style = MaterialTheme.typography.labelSmall, color = cs.primary, maxLines = 1)
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
    )
}
