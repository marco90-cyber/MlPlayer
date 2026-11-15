package com.marco.mlplayer.tags

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import com.marco.mlplayer.data.Settings
import com.marco.mlplayer.data.Track
import com.marco.mlplayer.util.AppLog
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

/** Tag modificabili di un brano. */
data class TagData(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val year: String = "",
    val genre: String = "",
    val track: String = "",
    val disc: String = "",
)

/** Esito di una scrittura nel file. */
sealed class WriteResult {
    data object Ok : WriteResult()
    /** Manca il permesso di scrittura sulla cartella [tree]. */
    data class NoAccess(val tree: String) : WriteResult()
    data class Unsupported(val ext: String) : WriteResult()
    data class Failed(val message: String) : WriteResult()
}

/**
 * Lettura e scrittura dei tag dentro i file audio (jaudiotagger, LGPL).
 * Il file passa da una copia temporanea: si modifica la copia e poi la si riscrive al suo posto,
 * perché le cartelle scelte con il selettore di Android non sono file veri ma documenti.
 */
object TagFiles {
    /** Formati che jaudiotagger sa scrivere. */
    val WRITABLE = setOf("mp3", "flac", "m4a", "m4b", "ogg", "oga", "wav", "aif", "aiff", "dsf", "wma")

    init {
        TagOptionSingleton.getInstance().isAndroid = true
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    fun canEdit(ext: String) = ext.lowercase() in WRITABLE

    /** Cartella scelta (tra quelle della libreria) che contiene il brano. */
    fun treeOf(settings: Settings, uri: String): String? =
        settings.folders.firstOrNull { uri.startsWith("$it/document/") } ?: settings.folders.firstOrNull { uri.startsWith(it) }

    /**
     * Indirizzo del brano dentro una cartella con il permesso di scrittura: va bene la stessa cartella
     * della libreria ma anche una che la contiene (es. "Music" per "Music/Album"). null = nessun permesso.
     */
    fun writableUri(ctx: Context, uri: String): Uri? = runCatching {
        val u = Uri.parse(uri)
        val docId = DocumentsContract.getDocumentId(u)
        for (p in ctx.contentResolver.persistedUriPermissions) {
            if (!p.isWritePermission || p.uri.authority != u.authority) continue
            val treeId = runCatching { DocumentsContract.getTreeDocumentId(p.uri) }.getOrNull() ?: continue
            val inside = docId == treeId ||
                docId.startsWith(treeId.trimEnd('/') + "/") ||
                (treeId.endsWith(":") && docId.startsWith(treeId))
            if (inside) return DocumentsContract.buildDocumentUriUsingTree(p.uri, docId)
        }
        null
    }.getOrNull()

    private fun tempFor(ctx: Context, ext: String): File {
        val dir = File(ctx.cacheDir, "tag-edit").apply { mkdirs() }
        dir.listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() > 60_000) it.delete() }
        return File.createTempFile("edit", ".${ext.lowercase()}", dir)
    }

    private fun copyIn(ctx: Context, uri: String, ext: String): File {
        val tmp = tempFor(ctx, ext)
        ctx.contentResolver.openInputStream(Uri.parse(uri))!!.use { input -> tmp.outputStream().use { input.copyTo(it) } }
        return tmp
    }

    private fun <T> withTag(ctx: Context, uri: String, ext: String, block: (Tag?) -> T): T? {
        if (!canEdit(ext)) return null
        val tmp = copyIn(ctx, uri, ext)
        return try {
            block(AudioFileIO.read(tmp).tag)
        } finally {
            tmp.delete()
        }
    }

    fun read(ctx: Context, t: Track): TagData? = runCatching {
        withTag(ctx, t.uri, t.ext) { tag ->
            fun f(k: FieldKey) = runCatching { tag?.getFirst(k) }.getOrNull().orEmpty().trim()
            TagData(
                title = f(FieldKey.TITLE), artist = f(FieldKey.ARTIST), album = f(FieldKey.ALBUM),
                albumArtist = f(FieldKey.ALBUM_ARTIST), year = f(FieldKey.YEAR), genre = f(FieldKey.GENRE),
                track = f(FieldKey.TRACK), disc = f(FieldKey.DISC_NO),
            )
        }
    }.onFailure { AppLog.w("Tag", "Lettura dei tag non riuscita: ${t.title}", it) }.getOrNull()

    /** Testo salvato nel tag del file (LRC o testo semplice), se c'è. */
    fun readLyrics(ctx: Context, t: Track): String? = runCatching {
        withTag(ctx, t.uri, t.ext) { tag -> tag?.getFirst(FieldKey.LYRICS)?.takeIf { it.isNotBlank() } }
    }.getOrNull()

    /** Modifica il tag del file con [edit] e lo riscrive. */
    fun write(ctx: Context, settings: Settings, t: Track, edit: (Tag) -> Unit): WriteResult {
        if (!canEdit(t.ext)) return WriteResult.Unsupported(t.ext)
        val target = writableUri(ctx, t.uri)
            ?: return WriteResult.NoAccess(treeOf(settings, t.uri) ?: return WriteResult.Failed("Cartella del brano non trovata"))
        var tmp: File? = null
        return try {
            val src = copyIn(ctx, t.uri, t.ext)
            tmp = src
            val af = AudioFileIO.read(src)
            val tag = af.tagOrCreateAndSetDefault
            edit(tag)
            af.commit()
            val out = runCatching { ctx.contentResolver.openOutputStream(target, "wt") }.getOrNull()
                ?: ctx.contentResolver.openOutputStream(target, "w")
                ?: return WriteResult.Failed("Il file non si può aprire in scrittura")
            out.use { o -> src.inputStream().use { it.copyTo(o) } }
            AppLog.i("Tag", "Tag scritti in ${t.title}")
            WriteResult.Ok
        } catch (e: Throwable) {
            AppLog.e("Tag", "Scrittura non riuscita in ${t.title}", e)
            WriteResult.Failed(e.message ?: e.javaClass.simpleName)
        } finally {
            tmp?.delete()
        }
    }

    private fun Tag.put(key: FieldKey, value: String) {
        val v = value.trim()
        if (v.isEmpty()) runCatching { deleteField(key) } else setField(key, v)
    }

    fun writeTags(ctx: Context, settings: Settings, t: Track, d: TagData): WriteResult = write(ctx, settings, t) { tag ->
        tag.put(FieldKey.TITLE, d.title)
        tag.put(FieldKey.ARTIST, d.artist)
        tag.put(FieldKey.ALBUM, d.album)
        tag.put(FieldKey.ALBUM_ARTIST, d.albumArtist)
        tag.put(FieldKey.YEAR, d.year)
        tag.put(FieldKey.GENRE, d.genre)
        tag.put(FieldKey.TRACK, d.track)
        tag.put(FieldKey.DISC_NO, d.disc)
    }

    fun writeLyrics(ctx: Context, settings: Settings, t: Track, lyrics: String): WriteResult = write(ctx, settings, t) { tag ->
        tag.put(FieldKey.LYRICS, lyrics)
    }

    /** Toglie la copertina incorporata nel file. */
    fun removeCover(ctx: Context, settings: Settings, t: Track): WriteResult = write(ctx, settings, t) { tag ->
        runCatching { tag.deleteArtworkField() }
    }

    /** Sostituisce la copertina incorporata (JPEG). */
    fun writeCover(ctx: Context, settings: Settings, t: Track, jpeg: ByteArray): WriteResult = write(ctx, settings, t) { tag ->
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        val art = ArtworkFactory.getNew().apply {
            binaryData = jpeg
            mimeType = "image/jpeg"
            pictureType = 3     // copertina frontale
            description = ""
            width = bounds.outWidth.coerceAtLeast(0)
            height = bounds.outHeight.coerceAtLeast(0)
        }
        runCatching { tag.deleteArtworkField() }
        tag.setField(art)
    }
}
