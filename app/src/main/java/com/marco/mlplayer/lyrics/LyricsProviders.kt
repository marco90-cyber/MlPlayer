package com.marco.mlplayer.lyrics

import android.util.Base64
import android.util.Xml
import com.marco.mlplayer.util.Http
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.util.zip.InflaterInputStream
import kotlin.math.abs

/** Dati del brano usati per cercare il testo. */
data class LyricsQuery(val title: String, val artist: String, val album: String, val durationSec: Int)

/**
 * Fonti dei testi su internet. Idee e indirizzi presi da LastWave (GPL-3.0,
 * github.com/Clash-Projects/LastWave-native): solo fonti pubbliche, senza account né chiavi.
 */
enum class LyricsProvider(
    val id: String,
    val title: String,
    val description: String,
    /** Fonti sperimentali (YouTube): mai scelte in automatico, cercate solo dalla scelta della fonte. */
    val experimental: Boolean = false,
) {
    Lrclib("lrclib", "LRCLIB", "Archivio libero di testi sincronizzati riga per riga"),
    Apple("apple", "Apple Music", "Testi sincronizzati parola per parola (tramite BetterLyrics)"),
    QQ("qq", "QQ Music", "Tempi delle parole in stile karaoke (tramite BetterLyrics)"),
    Kugou("kugou", "Kugou", "Testi KRC sincronizzati parola per parola"),
    Unison("unison", "Unison", "Archivio di testi inviati dagli utenti"),
    LyricsOvh("lyricsovh", "Lyrics.ovh", "Solo testo, senza tempi"),
    YouTubeMusic("ytmusic", "YouTube Music", "Sperimentale · testo della scheda «Testo», senza tempi", experimental = true),
    YouTubeCaptions("ytcaptions", "YouTube (sottotitoli)", "Sperimentale · sottotitoli caricati a mano di un video; i tempi possono richiedere il ritardo", experimental = true);

    suspend fun fetch(q: LyricsQuery): Lyrics? = runCatching {
        when (this) {
            Lrclib -> Providers.lrclib(q)
            Apple -> Providers.boidu(q, listOf("https://lyrics-api.boidu.dev/getLyrics", "https://lyrics-api.boidu.dev/ttml/getLyrics"), title)
            QQ -> Providers.boidu(q, listOf("https://lyrics-api.boidu.dev/qq/getLyrics"), title)
            Kugou -> Providers.kugou(q)
            Unison -> Providers.unison(q)
            LyricsOvh -> Providers.lyricsOvh(q)
            YouTubeMusic -> YouTubeLyrics.musicTab(q)
            YouTubeCaptions -> YouTubeLyrics.captions(q)
        }
    }.getOrNull()?.takeIf { !it.isEmpty }
}

internal object Providers {

    // ---------------------------------------------------------------- pulizia dei nomi

    private val T_FEAT_PAREN = Regex("""(?i)\s*[(\[](?:feat\.?|ft\.?|featuring|with)[^)\]]*[)\]]""")
    private val T_FEAT_TAIL = Regex("""(?i)\s+(?:feat\.?|ft\.?|featuring)\s+.*$""")
    private val T_VIDEO = Regex(
        """(?i)\s*[(\[]\s*(?:official\s*)?(?:music\s*)?(?:video|audio|visualizer|lyrics?\s*video|lyrics?|m/?v|hd|hq|4k)\s*[)\]]"""
    )
    private val SPACES = Regex("""\s+""")
    private val A_FEAT_PAREN = Regex("""(?i)\s*[(\[](?:feat\.?|ft\.?)[^)\]]*[)\]]""")
    private val A_FEAT_TAIL = Regex("""(?i)\s*(?:feat\.?|ft\.?|&|,|;| x | e )\s+.*$""")
    private val NOT_ALNUM = Regex("[^\\p{L}\\p{N}]+")
    private val KRC_LINE = Regex("""^\[(\d+),(\d+)](.*)$""")
    private val KRC_SYL = Regex("""<(\d+),(\d+),\d+>([^<]*)""")
    private val KRC_OFFSET = Regex("""^\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
    private val BLANK_LINES = Regex("\n{3,}")

    fun cleanTitle(raw: String): String {
        var out = raw
        out = out.replace(T_FEAT_PAREN, "")
        out = out.replace(T_FEAT_TAIL, "")
        out = out.replace(T_VIDEO, "")
        return out.replace(SPACES, " ").trim().ifBlank { raw.trim() }
    }

    fun cleanArtist(raw: String): String = raw
        .replace(A_FEAT_PAREN, "")
        .replace(A_FEAT_TAIL, "")
        .trim().ifBlank { raw.trim() }

    private fun norm(s: String) = s.lowercase().replace(NOT_ALNUM, "")

    private fun similar(a: String, b: String): Boolean {
        val x = norm(a); val y = norm(b)
        return x.isNotEmpty() && y.isNotEmpty() && (x == y || x.contains(y) || y.contains(x))
    }

    // ---------------------------------------------------------------- LRCLIB

    private fun lrclibRecord(o: JSONObject): Lyrics? {
        val synced = o.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
        val plain = o.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
        return when {
            synced != null -> Lrc.parse(synced, "LRCLIB")
            plain != null -> Lrc.parse(plain, "LRCLIB")
            else -> null
        }
    }

    fun lrclib(q: LyricsQuery): Lyrics? {
        val title = cleanTitle(q.title)
        val artist = cleanArtist(q.artist)
        val attempts = listOf(
            Triple(q.title, q.artist, q.album),
            Triple(q.title, q.artist, null),
            Triple(title, artist, null),
        ).distinct()
        for ((t, a, al) in attempts) {
            for (d in listOf(q.durationSec.takeIf { it > 0 }, null).distinct()) {
                val body = Http.get(
                    Http.url(
                        "https://lrclib.net/api/get",
                        "track_name" to t, "artist_name" to a, "album_name" to al, "duration" to d?.toString()
                    )
                ) ?: continue
                lrclibRecord(JSONObject(body))?.let { return it }
            }
        }
        val body = Http.get(Http.url("https://lrclib.net/api/search", "track_name" to title, "artist_name" to artist))
            ?: Http.get(Http.url("https://lrclib.net/api/search", "q" to "$artist $title"))
            ?: return null
        val arr = JSONArray(body)
        val cands = (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { similar(it.optString("trackName"), title) && similar(it.optString("artistName"), artist) }
            .sortedWith(
                compareBy<JSONObject>(
                    { if (it.optString("syncedLyrics").isBlank() || it.optString("syncedLyrics") == "null") 1 else 0 },
                    { if (q.durationSec > 0) abs(it.optDouble("duration", 0.0) - q.durationSec) else 0.0 },
                )
            )
        for (c in cands) lrclibRecord(c)?.let { return it }
        return null
    }

    // ---------------------------------------------------------------- BetterLyrics (Apple Music / QQ)

    fun boidu(q: LyricsQuery, endpoints: List<String>, source: String): Lyrics? {
        val variants = listOf(q.title to q.artist, cleanTitle(q.title) to cleanArtist(q.artist)).distinct()
        for ((t, a) in variants) for (base in endpoints) {
            val res = Http.request(
                Http.url(base, "s" to t, "a" to a, "d" to q.durationSec.takeIf { it > 0 }?.toString(), "al" to q.album)
            ) ?: continue
            if (res.code == 401) break   // il brano non è nella cache pubblica: inutile provare gli altri indirizzi
            if (!res.ok) continue
            val payload = unwrap(res.text) ?: continue
            parseAny(payload, source)?.let { return it }
        }
        return null
    }

    /** Toglie l'involucro JSON ({"ttml": ...}, {"lyrics": ...}) e restituisce il testo. */
    private fun unwrap(raw: String): String? {
        val t = raw.replace("﻿", "").trim()
        if (t.isEmpty()) return null
        if (!t.startsWith("{")) return t
        return runCatching {
            val o = JSONObject(t)
            for (k in listOf("ttml", "lyrics", "lrc", "syncedLyrics", "data")) {
                val v = o.opt(k)
                when (v) {
                    is String -> if (v.isNotBlank()) return v
                    is JSONObject -> for (k2 in listOf("ttml", "lyrics", "lrc")) {
                        val v2 = v.optString(k2)
                        if (v2.isNotBlank()) return v2
                    }
                }
            }
            null
        }.getOrNull()
    }

    fun parseAny(payload: String, source: String): Lyrics? {
        if (payload.contains("<tt", ignoreCase = true)) return Ttml.parse(payload, source)?.takeIf { !it.isEmpty }
        if (Lrc.isLrc(payload)) return Lrc.parse(payload, source).takeIf { !it.isEmpty }
        return null
    }

    // ---------------------------------------------------------------- Kugou (KRC)

    private val KRC_KEY = byteArrayOf(
        0x40, 0x47, 0x61, 0x77, 0x5e, 0x32, 0x74, 0x47, 0x51, 0x36, 0x31, 0x2d,
        0xce.toByte(), 0xd2.toByte(), 0x6e, 0x69
    )

    fun kugou(q: LyricsQuery): Lyrics? {
        val title = cleanTitle(q.title)
        val artist = cleanArtist(q.artist)
        val search = Http.get(
            Http.url(
                "https://lyrics.kugou.com/search",
                "ver" to "1", "man" to "yes", "client" to "pc", "keyword" to "$artist - $title",
                "duration" to if (q.durationSec > 0) "${q.durationSec * 1000}" else null,
            ), ua = Http.BROWSER_UA
        ) ?: return null
        val arr = JSONObject(search).optJSONArray("candidates") ?: return null
        val cands = (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { similar(it.optString("song"), title) && similar(it.optString("singer"), artist) }
            .sortedBy { if (q.durationSec > 0) abs(it.optLong("duration") - q.durationSec * 1000L) else 0L }
        val c = cands.firstOrNull() ?: return null
        val dl = Http.get(
            Http.url(
                "https://lyrics.kugou.com/download",
                "ver" to "1", "client" to "pc", "id" to c.optString("id"), "accesskey" to c.optString("accesskey"),
                "fmt" to "krc", "charset" to "utf8",
            ), ua = Http.BROWSER_UA
        ) ?: return null
        val content = JSONObject(dl).optString("content").takeIf { it.isNotBlank() } ?: return null
        val krc = decryptKrc(content) ?: return null
        return parseKrc(krc)
    }

    private fun decryptKrc(b64: String): String? = runCatching {
        val enc = Base64.decode(b64, Base64.DEFAULT)
        if (enc.size <= 4) return null
        val x = ByteArray(enc.size - 4) { i -> (enc[i + 4].toInt() xor KRC_KEY[i % KRC_KEY.size].toInt()).toByte() }
        InflaterInputStream(ByteArrayInputStream(x)).use { String(it.readBytes(), Charsets.UTF_8) }
    }.getOrNull()

    private fun parseKrc(text: String): Lyrics? {
        var offset = 0L
        val lines = ArrayList<LLine>()
        for (raw in text.lines()) {
            val t = raw.trim()
            KRC_OFFSET.find(t)?.let { offset = it.groupValues[1].toLong() }
            val m = KRC_LINE.matchEntire(t) ?: continue
            val start = (m.groupValues[1].toLong() + offset).coerceAtLeast(0)
            val dur = m.groupValues[2].toLong()
            val words = KRC_SYL.findAll(m.groupValues[3]).map { s ->
                val ws = start + s.groupValues[1].toLong()
                LWord(ws, ws + s.groupValues[2].toLong(), s.groupValues[3])
            }.filter { it.text.isNotEmpty() }.toList()
            val textLine = words.joinToString("") { it.text }.trim()
            if (textLine.isEmpty()) continue
            // la prima riga di Kugou è spesso "Artista - Titolo": la salto
            if (lines.isEmpty() && (textLine.contains(" - ") || textLine.contains("：") || textLine.contains(":"))) continue
            lines += LLine(start, start + dur, textLine, words)
        }
        return if (lines.isEmpty()) null else Lyrics(lines, "Kugou")
    }

    // ---------------------------------------------------------------- Unison

    fun unison(q: LyricsQuery): Lyrics? {
        val body = Http.get(
            Http.url(
                "https://unison.boidu.dev/lyrics",
                "song" to q.title, "artist" to q.artist, "album" to q.album,
                "duration" to q.durationSec.takeIf { it > 0 }?.toString(),
            )
        ) ?: return null
        val o = JSONObject(body)
        if (!o.optBoolean("success")) return null
        val d = o.optJSONObject("data") ?: return null
        val text = d.optString("lyrics").takeIf { it.isNotBlank() } ?: return null
        return when {
            d.optString("format").equals("ttml", true) -> Ttml.parse(text, "Unison")
            d.optString("syncType").equals("plain", true) -> Lrc.parse(text, "Unison")
            else -> Lrc.parse(text, "Unison")
        }
    }

    // ---------------------------------------------------------------- Lyrics.ovh

    fun lyricsOvh(q: LyricsQuery): Lyrics? {
        val body = Http.get("https://api.lyrics.ovh/v1/${Http.enc(cleanArtist(q.artist))}/${Http.enc(cleanTitle(q.title))}")
            ?: return null
        val text = JSONObject(body).optString("lyrics").takeIf { it.isNotBlank() } ?: return null
        // la prima riga è spesso "Paroles de la chanson ..." (intestazione del sito)
        val clean = text.replace("\r", "").lines().let { ls ->
            if (ls.firstOrNull()?.startsWith("Paroles de la chanson") == true) ls.drop(1) else ls
        }.joinToString("\n").replace(BLANK_LINES, "\n\n").trim()
        return Lrc.parse(clean, "Lyrics.ovh")
    }
}

/** TTML (Apple Music): righe <p begin end> con le parole in <span begin end>. */
internal object Ttml {
    private val SPACES = Regex("\\s+")

    private fun time(s: String?): Long {
        if (s.isNullOrBlank()) return -1
        val v = s.trim()
        return runCatching {
            when {
                v.endsWith("ms") -> v.dropLast(2).toDouble().toLong()
                v.endsWith("s") -> (v.dropLast(1).toDouble() * 1000).toLong()
                else -> {
                    val p = v.split(':')
                    var sec = 0.0
                    for (x in p) sec = sec * 60 + x.toDouble()
                    (sec * 1000).toLong()
                }
            }
        }.getOrDefault(-1)
    }

    fun parse(xml: String, source: String): Lyrics? = runCatching {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(StringReader(xml))
        val lines = ArrayList<LLine>()
        var inP = false
        var pStart = -1L
        var pEnd = -1L
        var text = StringBuilder()
        var words = ArrayList<LWord>()
        var spanDepth = 0
        var spanStart = -1L
        var spanEnd = -1L
        var spanBg = false
        var spanText = StringBuilder()
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (p.name.substringAfter(':')) {
                    "p" -> {
                        inP = true
                        pStart = time(p.getAttributeValue(null, "begin"))
                        pEnd = time(p.getAttributeValue(null, "end"))
                        text = StringBuilder(); words = ArrayList()
                    }
                    "span" -> if (inP) {
                        spanDepth++
                        val role = (0 until p.attributeCount).firstOrNull { p.getAttributeName(it).endsWith("role") }
                            ?.let { p.getAttributeValue(it) }
                        if (role == "x-bg") spanBg = true
                        if (spanDepth == 1 || spanStart < 0) {
                            spanStart = time(p.getAttributeValue(null, "begin"))
                            spanEnd = time(p.getAttributeValue(null, "end"))
                            spanText = StringBuilder()
                        }
                    }
                }
                XmlPullParser.TEXT -> if (inP && !spanBg) {
                    val t = p.text ?: ""
                    if (spanDepth > 0) spanText.append(t) else if (t.isNotBlank() || text.isNotEmpty()) text.append(t)
                }
                XmlPullParser.END_TAG -> when (p.name.substringAfter(':')) {
                    "span" -> if (inP) {
                        if (!spanBg && spanText.isNotEmpty()) {
                            if (spanStart >= 0) words += LWord(spanStart, spanEnd.coerceAtLeast(spanStart), spanText.toString())
                            text.append(spanText)
                            spanText = StringBuilder()
                        }
                        spanDepth--
                        if (spanDepth <= 0) { spanDepth = 0; spanBg = false; spanStart = -1 }
                    }
                    "p" -> {
                        inP = false
                        val line = text.toString().replace(SPACES, " ").trim()
                        if (line.isNotEmpty()) {
                            // gli spazi tra le parole stanno nel testo fuori dagli span: li rimetto nelle parole
                            val ws = fixSpaces(words, line)
                            val st = if (pStart >= 0) pStart else ws.firstOrNull()?.start ?: -1
                            val en = if (pEnd >= 0) pEnd else ws.lastOrNull()?.end ?: st
                            lines += LLine(st, en, line, ws)
                        }
                    }
                }
            }
            event = p.next()
        }
        if (lines.isEmpty()) null else Lyrics(lines.sortedBy { it.start }, source)
    }.getOrNull()

    private fun fixSpaces(words: List<LWord>, line: String): List<LWord> {
        if (words.isEmpty()) return words
        val out = ArrayList<LWord>()
        var pos = 0
        for (w in words) {
            val t = w.text
            val idx = line.indexOf(t.trim(), pos)
            if (idx < 0) { out += w; continue }
            val end = idx + t.trim().length
            val spaced = if (end < line.length && line[end] == ' ') t.trim() + " " else t.trim()
            out += w.copy(text = spaced)
            pos = end
        }
        return out
    }
}
