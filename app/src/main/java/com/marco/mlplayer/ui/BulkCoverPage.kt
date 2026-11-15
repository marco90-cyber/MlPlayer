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
    val query: String get() = Providers2.variants(artist, title).getOrNull(1)?.let { "${it.first} ${it.second}".trim() }
        ?: "${Providers2.cleanArtist(artist)} ${Providers2.cleanTitle(title)}".trim()
}

/**
 * Pulizia di titolo e artista SOLO per la ricerca delle copertine: i tag del file non cambiano.
 * Separatori ("-", "_", "|", "/"), numero di traccia, parentesi, feat. e parole di versione (remix, edit...).
 */
private object Providers2 {
    private val VERSION = Regex(
        """(?i)\b(?:radio\s+edit|extended(?:\s+(?:mix|version|edit))?|club\s+mix|original\s+mix|sped\s+up|slowed(?:\s+(?:down|reverb))?|""" +
            """remix(?:ed)?|mix|edit|version|versione|mashup|mash\s+up|bootleg|rework|remaster(?:ed)?|live|cover|""" +
            """official|video|audio|lyrics?|testo|visualizer|hd|hq|4k)\b"""
    )
    private val FEAT = Regex("""(?i)\s+(?:feat\.?|ft\.?|featuring)\s+.*$""")
    private val MASHUP = Regex("""(?i)\s+(?:x|vs\.?|versus)\s+""")
    private val TRACK_NUMBER = Regex("""^\s*\d{1,3}\s*[-._)]\s*""")
    private val SEPARATORS = Regex("""[-_–—|/]+""")
    private val SPACES = Regex("""\s+""")
    private val BRACKETS = Regex("""[()\[\]]""")
    private val PARENS = Regex("""\s*[(\[][^)\]]*[)\]]""")
    private val ARTIST_TAIL = Regex("""(?i)\s*(?:feat\.?|ft\.?|&|,| x | vs\.? )\s*.*$""")
    private val DASH = Regex("""\s+[-–—]\s+""")

    private fun base(s: String): String = s
        .replace(TRACK_NUMBER, "")   // numero di traccia: "01 - ", "1_" (non "99 Luftballons")
        .replace(FEAT, "")

    private fun separators(s: String) = s.replace(SEPARATORS, " ")
    private fun spaces(s: String) = s.replace(SPACES, " ").trim()

    /** Pulizia leggera: separatori via, testo tra parentesi tenuto (per la copertina del remix). */
    fun light(s: String): String = spaces(separators(base(s)).replace(BRACKETS, " ")).ifBlank { s.trim() }

    /** Pulizia completa: anche parentesi e parole di versione. */
    fun cleanTitle(s: String): String =
        spaces(separators(base(s).replace(PARENS, " ")).replace(VERSION, " ")).ifBlank { light(s) }

    /** Solo la prima canzone di un mashup ("A X B", "A vs B"); null se non è un mashup. */
    fun mashupFirst(s: String): String? {
        val t = base(s).replace(PARENS, " ")
        val parts = t.split(MASHUP)
        return if (parts.size > 1) cleanTitle(parts[0]).takeIf { it.isNotBlank() } else null
    }

    fun cleanArtist(s: String): String {
        if (s.equals("Artista sconosciuto", true) || s.equals("<unknown>", true)) return ""
        return s.replace(ARTIST_TAIL, "").trim()
    }

    /** Artista e titolo da cercare, dal più preciso al più largo. */
    fun variants(artist: String, title: String): List<Pair<String, String>> {
        var a = cleanArtist(artist)
        var t = title
        // "Artista - Titolo" nel titolo (file scaricati): se l'artista manca o è lo stesso, lo separo
        val dash = DASH.find(t)
        if (dash != null) {
            val left = t.substring(0, dash.range.first).trim()
            val right = t.substring(dash.range.last + 1).trim()
            if (left.any { it.isLetter() } && right.isNotEmpty() && (a.isEmpty() || similar(left, a, 0.6))) {
                if (a.isEmpty()) a = cleanArtist(left)
                t = right
            }
        }
        return listOfNotNull(
            a to light(t),
            a to cleanTitle(t),
            mashupFirst(t)?.let { a to it },
        ).filter { it.second.isNotBlank() }.distinct()
    }
}

private val NOT_ALNUM = Regex("[^\\p{L}\\p{N}]+")
private val IN_BRACKETS = Regex("\\(.*?\\)|\\[.*?]")

private fun tokens(s: String) = s.lowercase().split(NOT_ALNUM).filter { it.length > 1 }.toSet()

/** Somiglianza tra due testi: contenuti l'uno nell'altro o con molte parole in comune. */
private fun similar(a: String, b: String, min: Double): Boolean {
    val x = norm(a); val y = norm(b)
    if (x.isEmpty() || y.isEmpty()) return false
    if (x == y || x.contains(y) || y.contains(x)) return true
    val ta = tokens(a); val tb = tokens(b)
    if (ta.isEmpty() || tb.isEmpty()) return false
    return ta.intersect(tb).size.toDouble() / minOf(ta.size, tb.size) >= min
}

private fun norm(s: String) = s.lowercase().replace(IN_BRACKETS, "").replace(NOT_ALNUM, "")

/** La copertina giusta per il brano: titolo e artista devono corrispondere, altrimenti nessuna. */
private fun bestMatch(artist: String, title: String, results: List<CoverResult>): CoverResult? {
    val t = Providers2.cleanTitle(title)
    fun titleOk(r: CoverResult) = similar(Providers2.cleanTitle(r.title), t, 0.7)
    fun artistOk(r: CoverResult) = artist.isEmpty() || similar(r.artist, artist, 0.5)
    return results.firstOrNull { titleOk(it) && artistOk(it) }
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
    var onlyMissing by remember { mutableStateOf(jobs.any { !it.hadCover }) }
    var saving by remember { mutableStateOf(false) }
    var done by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var picking by remember { mutableStateOf<AlbumJob?>(null) }

    LaunchedEffect(jobs) {
        val gate = Semaphore(3)
        jobs.map { j ->
            async {
                gate.withPermit {
                    // ricerca a gradini: titolo quasi intero, poi senza remix/parentesi, poi la prima canzone del mashup
                    var all = emptyList<CoverResult>()
                    var found: CoverResult? = null
                    for ((a, t) in Providers2.variants(j.artist, j.title)) {
                        val r = CoverSearch.searchSong(a, t)
                        all = (all + r).distinctBy { it.full }
                        found = bestMatch(a, t, r)
                        if (found != null) break
                    }
                    j.results = all
                    j.choice = found
                    j.state = if (j.choice != null) JobState.Found else JobState.None
                    j.selected = j.choice != null
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
            var removed = 0
            for ((i, j) in list.withIndex()) {
                if (needTree != null) { remaining += j; continue }
                val c = j.choice
                val t = j.track
                j.state = JobState.Saving
                // nessuna copertina trovata: la tolgo (dal file e nell'app)
                val jpeg = if (c == null) null
                else withContext(Dispatchers.IO) { (Http.bytes(c.full) ?: Http.bytes(c.thumb))?.let { CoverSearch.toJpeg(it) } }
                if (c != null && jpeg == null) { j.state = JobState.Failed; done = i + 1; continue }
                withContext(Dispatchers.IO) { if (jpeg != null) env.library.setTrackCover(t, jpeg) else env.library.removeTrackCover(t) }
                if (jpeg == null) removed++
                if (!TagFiles.canEdit(t.ext)) appOnly++
                else when (val r = withContext(Dispatchers.IO) {
                    if (jpeg != null) TagFiles.writeCover(env.app, env.settings, t, jpeg) else TagFiles.removeCover(env.app, env.settings, t)
                }) {
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
                    append("Fatto: ${list.count { it.state == JobState.Done } - removed} copertine applicate")
                    if (removed > 0) append(", $removed tolte")
                    if (appOnly > 0) append(" · $appOnly brani solo nell'app (formato non scrivibile)")
                }
            }
        }
    }

    val shown = jobs.filter { !onlyMissing || !it.hadCover }
    val selected = jobs.filter { it.selected && it.state != JobState.Done && it.state != JobState.Searching }
    val toRemove = selected.count { it.choice == null }
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

        val selectable = shown.filter { it.state != JobState.Done && it.state != JobState.Searching }
        val allOn = selectable.isNotEmpty() && selectable.all { it.selected }
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.TriStateCheckbox(
                state = when {
                    allOn -> androidx.compose.ui.state.ToggleableState.On
                    selectable.any { it.selected } -> androidx.compose.ui.state.ToggleableState.Indeterminate
                    else -> androidx.compose.ui.state.ToggleableState.Off
                },
                enabled = !saving && selectable.isNotEmpty(),
                onClick = { val v = !allOn; selectable.forEach { it.selected = v } }
            )
            Text("Seleziona tutti (${selectable.size})", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            TextButton(
                enabled = !saving && selectable.isNotEmpty(),
                onClick = { selectable.forEach { it.selected = it.choice != null } }
            ) { Text("Solo trovati") }
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
                        toRemove > 0 -> "${selected.size} selezionati · $toRemove senza copertina da togliere"
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
                checked = j.selected, enabled = enabled && j.state != JobState.Done && j.state != JobState.Searching,
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
                        JobState.None -> if (j.selected) "Non trovata · verrà tolta la copertina" else "Non trovata · tocca per scegliere"
                        JobState.Saving -> "Salvo…"
                        JobState.Done -> "Applicata"
                        JobState.Failed -> "Download non riuscito"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (j.state == JobState.Failed || (j.state == JobState.None && j.selected)) cs.error else cs.primary,
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
