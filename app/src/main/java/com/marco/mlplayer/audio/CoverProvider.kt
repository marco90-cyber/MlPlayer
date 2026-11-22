package com.marco.mlplayer.audio

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.marco.mlplayer.MLPlayerApp
import java.io.File
import java.io.FileNotFoundException

/**
 * Passa le copertine ad Android Auto (e a ogni altro controller esterno), che non può leggere
 * i file privati dell'app. Sola lettura, solo copertine: content://<id>.covers/a/<album> o /t/<brano>.
 */
class CoverProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("sola lettura")
        val app = context?.applicationContext as? MLPlayerApp ?: throw FileNotFoundException()
        val parts = uri.pathSegments
        if (parts.size != 2) throw FileNotFoundException()
        val file: File? = when (parts[0]) {
            "a" -> app.library.coverFile(parts[1])
            "t" -> app.library.tracks.firstOrNull { it.id == parts[1] }?.let { app.library.trackCoverFile(it) }
            else -> null
        }
        if (file == null || !file.exists()) throw FileNotFoundException()
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "image/jpeg"
    override fun query(uri: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<String>?): Int = 0

    companion object {
        private fun authority(c: Context) = "${c.packageName}.covers"
        fun albumUri(c: Context, albumKey: String): Uri = Uri.parse("content://${authority(c)}/a/${Uri.encode(albumKey)}")
        fun trackUri(c: Context, trackId: String): Uri = Uri.parse("content://${authority(c)}/t/${Uri.encode(trackId)}")
    }
}
