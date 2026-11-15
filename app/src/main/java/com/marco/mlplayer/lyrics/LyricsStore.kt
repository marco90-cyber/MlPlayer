package com.marco.mlplayer.lyrics

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.marco.mlplayer.data.Settings
import com.marco.mlplayer.data.Track
import com.marco.mlplayer.tags.TagFiles
import com.marco.mlplayer.tags.WriteResult
import com.marco.mlplayer.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/** Stato di una fonte nella scelta della fonte. */
sealed class SourceState {
    data object Idle : SourceState()
    data object Searching : SourceState()
    data object None : SourceState()
    data class Found(val lyrics: Lyrics) : SourceState()
}

/**
 * Testi dei brani: cache dell'app, tag del file, file .lrc accanto al brano e fonti su internet.
 * I testi trovati online si salvano nell'app subito e nel file quando il brano non è più in riproduzione
 * (riscrivere il file che sta suonando lo interromperebbe).
 */
class LyricsStore(private val ctx: Context, private val settings: Settings) {
    private val dir = File(ctx.filesDir, "lyrics").apply { mkdirs() }
    private val meta = ctx.getSharedPreferences("lyrics_meta", Context.MODE_PRIVATE)
    private val lock = Mutex()

    /** Fonti locali, mostrate nella scelta della fonte insieme a quelle online. */
    companion object {
        const val SRC_FILE = "Nel file"
        const val SRC_LRC = "File .lrc"
        const val SRC_MANUAL = "Scritto a mano"
        const val AUTO = "auto"
    }

    var trackId by mutableStateOf<String?>(null)
        private set
    var lyrics by mutableStateOf<Lyrics?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    /** Fonte in uso: id di [LyricsProvider], "auto" o una fonte locale. */
    var chosen by mutableStateOf(AUTO)
        private set
    val sources = mutableStateMapOf<String, SourceState>()
    var translation by mutableStateOf<List<String>?>(null)
        private set
    var romanization by mutableStateOf<List<String>?>(null)
        private set
    var extraLoading by mutableStateOf(false)
        private set
    /** Cartella su cui manca il permesso di scrittura (per salvare il testo nel file). */
    var needsAccess by mutableStateOf<String?>(null)
    /** Cambia quando il ritardo di un brano cambia. */
    var offsetTick by mutableIntStateOf(0)
        private set

    private val pending = LinkedHashMap<String, Pair<Track, String>>()

    private fun query(t: Track) = LyricsQuery(t.title, t.artist, if (t.album == "Album sconosciuto") "" else t.album, (t.durationMs / 1000).toInt())

    // ---------------------------------------------------------------- ritardo per brano

    fun offset(t: Track): Int = meta.getInt("off_${t.id}", 0)

    fun setOffset(t: Track, ms: Int) {
        meta.edit().putInt("off_${t.id}", ms.coerceIn(-10_000, 10_000)).apply()
        offsetTick++
    }

    // ---------------------------------------------------------------- caricamento

    /** Testo del brano: prima quello salvato, poi il file, poi internet (fonte automatica). */
    suspend fun load(t: Track, force: Boolean = false) {
        if (!force && trackId == t.id && (lyrics != null || loading)) return
        trackId = t.id
        lyrics = null
        translation = null
        romanization = null
        sources.clear()
        needsAccess = null
        loading = true
        try {
            val local = withContext(Dispatchers.IO) { readLocal(t) }
            if (trackId != t.id) return
            if (local != null) {
                lyrics = local
                chosen = meta.getString("src_${t.id}", AUTO) ?: AUTO
                return
            }
            searchOnline(t)
            if (trackId != t.id) return
            val best = pickBest()
            if (best != null) {
                chosen = AUTO
                apply(t, best.second, best.first)
            }
        } finally {
            if (trackId == t.id) loading = false
        }
    }

    private fun cacheFile(t: Track) = File(dir, "${t.id}.lrc")

    private fun readLocal(t: Track): Lyrics? {
        cacheFile(t).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }?.let {
            return Lrc.parse(it, meta.getString("name_${t.id}", "Salvato") ?: "Salvato")
        }
        TagFiles.readLyrics(ctx, t)?.let { text ->
            val l = Lrc.parse(text, SRC_FILE)
            if (!l.isEmpty) {
                sources[SRC_FILE] = SourceState.Found(l)
                return l
            }
        }
        readSidecar(t)?.let { text ->
            val l = Lrc.parse(text, SRC_LRC)
            if (!l.isEmpty) {
                sources[SRC_LRC] = SourceState.Found(l)
                return l
            }
        }
        return null
    }

    /** File .lrc con lo stesso nome del brano, nella stessa cartella. */
    private fun readSidecar(t: Track): String? = runCatching {
        val tree = TagFiles.treeOf(settings, t.uri) ?: return null
        val docId = DocumentsContract.getDocumentId(Uri.parse(t.uri))
        val base = docId.substringBeforeLast('.')
        for (ext in listOf("lrc", "LRC", "txt")) {
            val u = DocumentsContract.buildDocumentUriUsingTree(Uri.parse(tree), "$base.$ext")
            val text = runCatching { ctx.contentResolver.openInputStream(u)?.use { String(it.readBytes(), Charsets.UTF_8) } }.getOrNull()
            if (!text.isNullOrBlank()) return text
        }
        null
    }.getOrNull()

    /**
     * Cerca in tutte le fonti online insieme (ognuna con un tempo massimo).
     * Le fonti sperimentali solo con [experimental] (dalla scelta della fonte).
     */
    suspend fun searchOnline(t: Track, experimental: Boolean = false) = coroutineScope {
        val q = query(t)
        val list = LyricsProvider.entries.filter { (experimental || !it.experimental) && sources[it.id] !is SourceState.Found }
            .filter { sources[it.id] != SourceState.None && sources[it.id] != SourceState.Searching }
        for (p in list) sources[p.id] = SourceState.Searching
        list.map { p ->
            async(Dispatchers.IO) {
                val r = withTimeoutOrNull(12_000) { p.fetch(q) }
                if (trackId == t.id) sources[p.id] = if (r != null) SourceState.Found(r) else SourceState.None
            }
        }.awaitAll()
    }

    /** Scelta automatica: prima il sincronizzato a parole, poi a righe, poi il solo testo. */
    private fun pickBest(exclude: String? = null): Pair<String, Lyrics>? {
        val found = LyricsProvider.entries.filter { !it.experimental }.mapNotNull { p ->
            (sources[p.id] as? SourceState.Found)?.let { p.id to it.lyrics }
        }.filter { it.first != exclude }
        val wordsOrder = listOf("apple", "kugou", "qq", "unison", "lrclib")
        val linesOrder = listOf("lrclib", "apple", "qq", "kugou", "unison")
        return found.filter { it.second.kind == SyncKind.Words }.minByOrNull { wordsOrder.indexOf(it.first) }
            ?: found.filter { it.second.kind == SyncKind.Lines }.minByOrNull { linesOrder.indexOf(it.first) }
            ?: found.firstOrNull()
    }

    /** "Prova un'altra fonte": la migliore escludendo quella attuale. */
    suspend fun tryAnother(t: Track) {
        if (LyricsProvider.entries.any { !it.experimental && sources[it.id] == null }) searchOnline(t)
        val current = LyricsProvider.entries.firstOrNull { it.title == lyrics?.source }?.id
        val next = pickBest(exclude = current) ?: return
        chosen = next.first
        apply(t, next.second, next.first)
    }

    /** Scelta di una fonte precisa ("auto" = scelta automatica). */
    suspend fun choose(t: Track, id: String) {
        if (id == AUTO) {
            if (LyricsProvider.entries.any { !it.experimental && sources[it.id] == null }) searchOnline(t)
            chosen = AUTO
            pickBest()?.let { apply(t, it.second, it.first) }
            return
        }
        val st = sources[id]
        if (st is SourceState.Found) {
            chosen = id
            apply(t, st.lyrics, id)
        }
    }

    /** Testo scritto o corretto a mano. */
    suspend fun saveManual(t: Track, text: String) {
        val l = Lrc.parse(text, SRC_MANUAL)
        chosen = SRC_MANUAL
        apply(t, l, SRC_MANUAL)
    }

    private suspend fun apply(t: Track, l: Lyrics, srcId: String) {
        lyrics = l
        translation = null
        romanization = null
        withContext(Dispatchers.IO) {
            cacheFile(t).writeText(l.toLrc())
            meta.edit().putString("name_${t.id}", l.source).putString("src_${t.id}", srcId).apply()
        }
        if (settings.lyricsSaveToFile && srcId != SRC_FILE && TagFiles.canEdit(t.ext)) {
            lock.withLock { pending[t.uri] = t to l.toLrc() }
        }
    }

    /**
     * Scrive nei file i testi in attesa, tranne quello del brano in riproduzione.
     * [playingUri] = brano che sta suonando adesso.
     */
    suspend fun flushPending(playingUri: String?) {
        val todo = lock.withLock { pending.values.filter { it.first.uri != playingUri } }
        for ((t, text) in todo) {
            val r = withContext(Dispatchers.IO) { TagFiles.writeLyrics(ctx, settings, t, text) }
            when (r) {
                is WriteResult.Ok, is WriteResult.Unsupported -> lock.withLock { pending.remove(t.uri) }
                is WriteResult.NoAccess -> { needsAccess = r.tree; return }
                is WriteResult.Failed -> {
                    AppLog.w("Testi", "Testo non scritto nel file di ${t.title}: ${r.message}")
                    lock.withLock { pending.remove(t.uri) }
                }
            }
        }
    }

    // ---------------------------------------------------------------- traduzione e romanizzazione

    suspend fun loadTranslation() {
        val l = lyrics ?: return
        if (translation != null) return
        extraLoading = true
        translation = withContext(Dispatchers.IO) { LyricsTranslate.translate(l.lines.map { it.text }) } ?: emptyList()
        extraLoading = false
    }

    suspend fun loadRomanization() {
        val l = lyrics ?: return
        if (romanization != null) return
        extraLoading = true
        romanization = withContext(Dispatchers.IO) { LyricsTranslate.romanize(l.lines.map { it.text }) } ?: emptyList()
        extraLoading = false
    }
}
