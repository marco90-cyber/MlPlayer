package com.marco.mlplayer.audio

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.marco.mlplayer.MLPlayerApp
import com.marco.mlplayer.MainActivity
import com.marco.mlplayer.data.Track
import com.marco.mlplayer.util.AppLog
import java.text.Normalizer

/**
 * Servizio in background: notifica con controlli, riproduzione a schermo spento e
 * libreria sfogliabile per Android Auto (Brani, Album, Artisti, Generi, ricerca).
 */
class PlaybackService : MediaLibraryService() {

    private var session: MediaLibrarySession? = null

    private val app get() = application as MLPlayerApp

    override fun onCreate() {
        super.onCreate()
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaLibrarySession.Builder(this, app.engine.controls, LibraryCallback())
            .setSessionActivity(openApp)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }

    // ------------------------------------------------------------------ albero della libreria

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(folder(ROOT, "MLPlayer"), params))

        override fun onGetItem(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val item = when {
                mediaId == ROOT -> folder(ROOT, "MLPlayer")
                mediaId.startsWith("n:") || mediaId.startsWith("album:") ||
                    mediaId.startsWith("artist:") || mediaId.startsWith("genre:") ->
                    folder(mediaId, mediaId.substringAfter(':'))
                else -> {
                    val t = trackOf(mediaId.substringAfterLast('|'))
                    t?.let { trackItem(mediaId.substringBeforeLast('|', ALL), it) }
                }
            }
            return Futures.immediateFuture(
                if (item != null) LibraryResult.ofItem(item, null)
                else LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
            )
        }

        override fun onGetChildren(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
            parentId: String, page: Int, pageSize: Int, params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val lib = app.library
            val items: List<MediaItem> = when {
                parentId == ROOT -> listOf(
                    folder("n:tracks", "Brani"),
                    folder("n:albums", "Album"),
                    folder("n:artists", "Artisti"),
                    folder("n:genres", "Generi"),
                )
                parentId == "n:tracks" -> lib.tracks.map { trackItem(ALL, it) }
                parentId == "n:albums" -> lib.albums.map {
                    folder("album:${it.key}", it.title, it.artist, albumArt(it.key))
                }
                parentId == "n:artists" -> lib.artists.map {
                    folder("artist:${it.name}", it.name, null, it.albums.firstOrNull()?.let { a -> albumArt(a.key) })
                }
                parentId == "n:genres" -> lib.genres.map { folder("genre:${it.name}", it.name) }
                else -> tracksOf(parentId).map { trackItem(parentId, it) }
            }
            return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(slice(items, page, pageSize)), params))
        }

        override fun onSearch(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            session.notifySearchResultChanged(browser, query, search(query).size, params)
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
            query: String, page: Int, pageSize: Int, params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val ctx = "$SEARCH$query"
            val items = search(query).map { trackItem(ctx, it) }
            return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(slice(items, page, pageSize)), params))
        }

        /**
         * Android Auto chiede di riprodurre una voce: la espandiamo nella lista da cui viene
         * (album, artista, ricerca…), così "avanti" prosegue nel contesto giusto.
         * Anche i comandi vocali arrivano qui, con il testo cercato.
         */
        override fun onSetMediaItems(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val engine = app.engine
            val first = mediaItems.firstOrNull()
            val tracks: List<Track>
            var start = 0
            if (first == null) {
                tracks = emptyList()
            } else if (first.mediaId.isBlank()) {
                val q = first.requestMetadata.searchQuery.orEmpty()
                tracks = if (q.isBlank()) app.library.tracks else search(q)
            } else if (mediaItems.size == 1) {
                val ctx = if ('|' in first.mediaId) first.mediaId.substringBeforeLast('|') else ALL
                val id = first.mediaId.substringAfterLast('|')
                tracks = tracksOf(ctx).ifEmpty { listOfNotNull(trackOf(id)) }
                start = tracks.indexOfFirst { it.id == id }.coerceAtLeast(0)
            } else {
                tracks = mediaItems.mapNotNull { trackOf(it.mediaId.substringAfterLast('|')) }
                start = startIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
            }
            if (tracks.isEmpty()) {
                AppLog.i("Auto", "Niente da riprodurre per la richiesta")
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(emptyList(), C.INDEX_UNSET, C.TIME_UNSET)
                )
            }
            engine.adoptExternalQueue(tracks)
            AppLog.i("Auto", "Coda da Android Auto: ${tracks.size} brani, da #$start")
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(
                    tracks.map { engine.mediaItemFor(it) }, start,
                    if (mediaItems.size == 1) 0L else startPositionMs
                )
            )
        }
    }

    // ------------------------------------------------------------------ aiuti

    private fun trackOf(id: String): Track? = app.library.tracks.firstOrNull { it.id == id }

    /** I brani di una voce dell'albero: "tutti", un album, un artista, un genere o una ricerca. */
    private fun tracksOf(ctx: String): List<Track> {
        val lib = app.library
        return when {
            ctx == ALL || ctx == "n:tracks" -> lib.tracks
            ctx.startsWith("album:") -> lib.albums.firstOrNull { it.key == ctx.removePrefix("album:") }?.tracks.orEmpty()
            ctx.startsWith("artist:") -> lib.artists.firstOrNull { it.name == ctx.removePrefix("artist:") }?.tracks.orEmpty()
            ctx.startsWith("genre:") -> lib.genres.firstOrNull { it.name == ctx.removePrefix("genre:") }?.tracks.orEmpty()
            ctx.startsWith(SEARCH) -> search(ctx.removePrefix(SEARCH))
            else -> emptyList()
        }
    }

    private fun search(query: String): List<Track> {
        val words = fold(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return app.library.tracks.filter { t ->
            val hay = fold("${t.title} ${t.artist} ${t.album} ${t.genre}")
            words.all { it in hay }
        }
    }

    private fun fold(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").trim()

    private fun slice(items: List<MediaItem>, page: Int, pageSize: Int): List<MediaItem> {
        if (pageSize <= 0 || pageSize == Int.MAX_VALUE) return items
        val from = (page.toLong() * pageSize).coerceAtMost(items.size.toLong()).toInt()
        return items.subList(from, (from.toLong() + pageSize).coerceAtMost(items.size.toLong()).toInt())
    }

    private fun albumArt(key: String): Uri? =
        if (app.library.coverFile(key) != null) CoverProvider.albumUri(this, key) else null

    private fun folder(id: String, title: String, subtitle: String? = null, art: Uri? = null): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setArtworkUri(art)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .setExtras(Bundle().apply { putInt(CONTENT_STYLE_PLAYABLE, 1) })
                    .build()
            )
            .build()

    private fun trackItem(ctx: String, t: Track): MediaItem {
        val art = if (app.library.trackCoverFile(t) != null) CoverProvider.trackUri(this, t.id) else null
        return MediaItem.Builder()
            .setMediaId("$ctx|${t.id}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(t.title)
                    .setArtist(t.artist)
                    .setAlbumTitle(t.album)
                    .setArtworkUri(art)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build()
            )
            .build()
    }

    private companion object {
        const val ROOT = "root"
        const val ALL = "n:tracks"
        const val SEARCH = "search:"
        /** Chiede ad Android Auto di mostrare le liste di brani come elenco. */
        const val CONTENT_STYLE_PLAYABLE = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
    }
}
