package com.marco.mlplayer.tags

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.marco.mlplayer.util.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/** Una copertina trovata su internet. */
data class CoverResult(
    val thumb: String,
    val full: String,
    val title: String,
    val artist: String,
    val source: String,
    val size: String,
    /** Album o singolo che contiene il brano (solo nella ricerca per brano). */
    val album: String = "",
)

/** Ricerca copertine: iTunes, Deezer e Cover Art Archive (MusicBrainz). Tutte gratuite e senza chiave. */
object CoverSearch {

    suspend fun search(query: String): List<CoverResult> = coroutineScope {
        val q = query.trim()
        if (q.isEmpty()) return@coroutineScope emptyList()
        val parts = listOf(
            async(Dispatchers.IO) { withTimeoutOrNull(12_000) { runCatching { itunes(q) }.getOrNull() } },
            async(Dispatchers.IO) { withTimeoutOrNull(12_000) { runCatching { deezer(q) }.getOrNull() } },
            async(Dispatchers.IO) { withTimeoutOrNull(12_000) { runCatching { musicBrainz(q) }.getOrNull() } },
        ).awaitAll().map { it.orEmpty() }
        // alternate le fonti, così i primi risultati vengono da tutte
        val out = ArrayList<CoverResult>()
        val max = parts.maxOfOrNull { it.size } ?: 0
        for (i in 0 until max) for (p in parts) p.getOrNull(i)?.let { r -> if (out.none { it.full == r.full }) out += r }
        out
    }

    /** Copertine per un singolo brano: l'uscita che contiene la canzone (iTunes e Deezer). */
    suspend fun searchSong(artist: String, title: String): List<CoverResult> = coroutineScope {
        val a = artist.trim()
        val t = title.trim()
        if (t.isEmpty()) return@coroutineScope emptyList()
        val parts = listOf(
            async(Dispatchers.IO) { withTimeoutOrNull(12_000) { runCatching { itunesSong("$a $t") }.getOrNull() } },
            async(Dispatchers.IO) { withTimeoutOrNull(12_000) { runCatching { deezerSong(a, t) }.getOrNull() } },
        ).awaitAll().map { it.orEmpty() }
        val out = ArrayList<CoverResult>()
        val max = parts.maxOfOrNull { it.size } ?: 0
        for (i in 0 until max) for (p in parts) p.getOrNull(i)?.let { r -> if (out.none { it.full == r.full }) out += r }
        out
    }

    private fun itunesSong(q: String): List<CoverResult> {
        val body = Http.get(Http.url("https://itunes.apple.com/search", "term" to q, "entity" to "song", "limit" to "15")) ?: return emptyList()
        val arr = JSONObject(body).optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val art = o.optString("artworkUrl100").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CoverResult(
                thumb = art.replace("100x100bb", "300x300bb"),
                full = art.replace("100x100bb", "1200x1200bb"),
                title = o.optString("trackName"), artist = o.optString("artistName"),
                source = "iTunes", size = "1200 px", album = o.optString("collectionName"),
            )
        }
    }

    private fun deezerSong(artist: String, title: String): List<CoverResult> {
        val q = if (artist.isNotBlank()) "artist:\"$artist\" track:\"$title\"" else title
        val body = Http.get(Http.url("https://api.deezer.com/search", "q" to q, "limit" to "15")) ?: return emptyList()
        val arr = JSONObject(body).optJSONArray("data") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val al = o.optJSONObject("album") ?: return@mapNotNull null
            val xl = al.optString("cover_xl").takeIf { it.isNotBlank() && it != "null" } ?: return@mapNotNull null
            CoverResult(
                thumb = al.optString("cover_medium").ifBlank { xl }, full = xl,
                title = o.optString("title"), artist = o.optJSONObject("artist")?.optString("name").orEmpty(),
                source = "Deezer", size = "1000 px", album = al.optString("title"),
            )
        }
    }

    private fun itunes(q: String): List<CoverResult> {
        val body = Http.get(Http.url("https://itunes.apple.com/search", "term" to q, "entity" to "album", "limit" to "15")) ?: return emptyList()
        val arr = JSONObject(body).optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val art = o.optString("artworkUrl100").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CoverResult(
                thumb = art.replace("100x100bb", "300x300bb"),
                full = art.replace("100x100bb", "1200x1200bb"),
                title = o.optString("collectionName"), artist = o.optString("artistName"),
                source = "iTunes", size = "1200 px",
            )
        }
    }

    private fun deezer(q: String): List<CoverResult> {
        val body = Http.get(Http.url("https://api.deezer.com/search/album", "q" to q, "limit" to "15")) ?: return emptyList()
        val arr = JSONObject(body).optJSONArray("data") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val xl = o.optString("cover_xl").takeIf { it.isNotBlank() && it != "null" } ?: return@mapNotNull null
            CoverResult(
                thumb = o.optString("cover_medium").ifBlank { xl }, full = xl,
                title = o.optString("title"), artist = o.optJSONObject("artist")?.optString("name").orEmpty(),
                source = "Deezer", size = "1000 px",
            )
        }
    }

    private fun musicBrainz(q: String): List<CoverResult> {
        val body = Http.get(Http.url("https://musicbrainz.org/ws/2/release-group/", "query" to q, "fmt" to "json", "limit" to "10"))
            ?: return emptyList()
        val arr = JSONObject(body).optJSONArray("release-groups") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val id = o.optString("id")
            val artist = o.optJSONArray("artist-credit")?.optJSONObject(0)?.optString("name").orEmpty()
            CoverResult(
                thumb = "https://coverartarchive.org/release-group/$id/front-250",
                full = "https://coverartarchive.org/release-group/$id/front-1200",
                title = o.optString("title"), artist = artist,
                source = "Cover Art Archive", size = "fino a 1200 px",
            )
        }
    }

    /** Immagine pronta per il file: JPEG, lato lungo al massimo 1200 px. */
    fun toJpeg(bytes: ByteArray, maxPx: Int = 1200): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scaled = if (bmp.width > maxPx || bmp.height > maxPx) {
            val r = maxPx.toFloat() / maxOf(bmp.width, bmp.height)
            Bitmap.createScaledBitmap(bmp, (bmp.width * r).toInt().coerceAtLeast(1), (bmp.height * r).toInt().coerceAtLeast(1), true)
        } else bmp
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 92, out)
        return out.toByteArray()
    }
}
