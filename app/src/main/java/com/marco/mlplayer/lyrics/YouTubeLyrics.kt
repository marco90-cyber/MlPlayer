package com.marco.mlplayer.lyrics

import android.util.Base64
import com.marco.mlplayer.util.AppLog
import com.marco.mlplayer.util.Http
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * Fonti sperimentali da YouTube (come in LastWave, GPL-3.0): la scheda "Testo" di YouTube Music
 * e i sottotitoli caricati a mano di un video. Usano l'interfaccia interna di YouTube, che può
 * cambiare senza preavviso: per questo non vengono mai scelte in automatico.
 */
internal object YouTubeLyrics {
    private const val ORIGIN = "https://music.youtube.com"
    private val NOT_ALNUM = Regex("[^\\p{L}\\p{N}]")
    private const val API = "$ORIGIN/youtubei/v1"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
    private const val CONSENT = "CONSENT=YES+cb.20210328-17-p0.en+FX+100; SOCS=CAISEwgDEgk0ODE3Nzk3MjQaAmVuIAEaBgiA_LyaBg"
    private const val FALLBACK_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
    private const val FALLBACK_VERSION = "1.20260707.12.00"
    /** Filtri della ricerca di YouTube Music: brani e video. */
    private const val FILTER_SONGS = "EgWKAQIIAWoKEAkQBRAKEAMQBA=="
    private const val FILTER_VIDEOS = "EgWKAQIQAWoKEAkQBRAKEAMQBA=="

    private data class Config(val key: String, val version: String, val visitor: String?)

    @Volatile private var config: Config? = null

    private fun config(): Config {
        config?.let { return it }
        val html = Http.request("$ORIGIN/", ua = UA, accept = "text/html", headers = mapOf("Cookie" to CONSENT))?.takeIf { it.ok }?.text.orEmpty()
        fun find(k: String) = Regex("\"$k\"\\s*:\\s*\"([^\"]+)\"").find(html)?.groupValues?.get(1)
        return Config(find("INNERTUBE_API_KEY") ?: FALLBACK_KEY, find("INNERTUBE_CONTEXT_CLIENT_VERSION") ?: FALLBACK_VERSION, find("VISITOR_DATA"))
            .also { config = it }
    }

    private fun post(endpoint: String, body: JSONObject): JSONObject? {
        val c = config()
        val ctx = JSONObject().put(
            "client", JSONObject()
                .put("clientName", "WEB_REMIX").put("clientVersion", c.version)
                .put("hl", "en").put("gl", "IT")
                .apply { c.visitor?.let { put("visitorData", it) } }
        )
        body.put("context", ctx)
        val headers = buildMap {
            put("Origin", ORIGIN)
            put("Referer", "$ORIGIN/")
            put("Cookie", CONSENT)
            put("X-YouTube-Client-Name", "67")
            put("X-YouTube-Client-Version", c.version)
            c.visitor?.let { put("X-Goog-Visitor-Id", it) }
        }
        val res = Http.request(
            "$API/$endpoint?key=${c.key}&prettyPrint=false", ua = UA, post = body.toString(),
            headers = headers, contentType = "application/json"
        ) ?: return null
        if (!res.ok) {
            AppLog.d("Testi", "YouTube $endpoint: HTTP ${res.code}")
            if (res.code == 400 || res.code == 403) config = null   // chiave o versione scadute: le riprendo la prossima volta
            return null
        }
        return runCatching { JSONObject(res.text) }.getOrNull()
    }

    // ---------------------------------------------------------------- lettura del JSON di YouTube

    /** Tutti gli oggetti con chiave [key], a qualsiasi profondità. */
    private fun collect(node: Any?, key: String, out: MutableList<JSONObject>) {
        when (node) {
            is JSONObject -> {
                val it = node.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    val v = node.opt(k)
                    if (k == key && v is JSONObject) out += v
                    collect(v, key, out)
                }
            }
            is JSONArray -> for (i in 0 until node.length()) collect(node.opt(i), key, out)
        }
    }

    private fun text(node: Any?): String? = when (node) {
        is String -> node
        is JSONObject -> node.optString("simpleText").takeIf { it.isNotEmpty() }
            ?: node.optJSONArray("runs")?.let { r -> (0 until r.length()).joinToString("") { r.optJSONObject(it)?.optString("text").orEmpty() } }
        else -> null
    }

    private fun allTexts(node: Any?, out: MutableList<String>) {
        when (node) {
            is JSONObject -> {
                if (node.has("text") && node.opt("text") is String) out += node.optString("text")
                val it = node.keys()
                while (it.hasNext()) allTexts(node.opt(it.next()), out)
            }
            is JSONArray -> for (i in 0 until node.length()) allTexts(node.opt(i), out)
        }
    }

    private fun parseDuration(s: String): Int? {
        val p = s.trim().split(':')
        if (p.size !in 2..3 || p.any { it.toIntOrNull() == null }) return null
        return p.fold(0) { acc, x -> acc * 60 + x.toInt() }
    }

    private data class Hit(val videoId: String, val title: String, val seconds: Int?)

    /** Risultati della ricerca (brani o video) con la durata, il più vicino al brano per primo. */
    private fun search(q: LyricsQuery, filter: String): List<Hit> {
        val res = post("search", JSONObject().put("query", "${Providers.cleanArtist(q.artist)} ${Providers.cleanTitle(q.title)}").put("params", filter))
            ?: return emptyList()
        val items = ArrayList<JSONObject>()
        collect(res, "musicResponsiveListItemRenderer", items)
        val hits = items.mapNotNull { item ->
            val ids = ArrayList<JSONObject>()
            collect(item, "watchEndpoint", ids)
            val id = ids.firstNotNullOfOrNull { it.optString("videoId").takeIf { v -> v.length == 11 } }
                ?: item.optJSONObject("playlistItemData")?.optString("videoId")?.takeIf { it.length == 11 }
                ?: return@mapNotNull null
            val texts = ArrayList<String>()
            allTexts(item, texts)
            val title = texts.firstOrNull().orEmpty()
            Hit(id, title, texts.firstNotNullOfOrNull { parseDuration(it) })
        }.distinctBy { it.videoId }.take(8)
        val title = Providers.cleanTitle(q.title)
        // solo risultati con il titolo giusto, poi per durata più vicina
        return hits.filter { h ->
            val a = h.title.lowercase().replace(NOT_ALNUM, "")
            val b = title.lowercase().replace(NOT_ALNUM, "")
            a.isNotEmpty() && b.isNotEmpty() && (a.contains(b) || b.contains(a))
        }.sortedBy { h -> if (q.durationSec > 0 && h.seconds != null) abs(h.seconds - q.durationSec) else 999 }
    }

    // ---------------------------------------------------------------- scheda "Testo" di YouTube Music

    fun musicTab(q: LyricsQuery): Lyrics? {
        for (hit in search(q, FILTER_SONGS).take(2)) {
            val next = post("next", JSONObject().put("videoId", hit.videoId).put("isAudioOnly", true)) ?: continue
            val tabs = ArrayList<JSONObject>()
            collect(next, "tabRenderer", tabs)
            val tab = tabs.firstOrNull { (text(it.opt("title")) ?: "").contains("lyrics", true) } ?: continue
            val eps = ArrayList<JSONObject>()
            collect(tab, "browseEndpoint", eps)
            val ep = eps.firstOrNull() ?: continue
            val body = JSONObject().put("browseId", ep.optString("browseId"))
            ep.optString("params").takeIf { it.isNotBlank() }?.let { body.put("params", it) }
            val page = post("browse", body) ?: continue
            val shelves = ArrayList<JSONObject>()
            collect(page, "musicDescriptionShelfRenderer", shelves)
            val txt = shelves.firstNotNullOfOrNull { text(it.opt("description"))?.trim()?.takeIf { s -> s.isNotBlank() } } ?: continue
            return Lrc.parse(txt, "YouTube Music")
        }
        return null
    }

    // ---------------------------------------------------------------- sottotitoli di un video

    /** La lingua scelta nel menu della trascrizione è "(auto-generated)"? */
    private fun isAutoGenerated(res: JSONObject): Boolean {
        val menus = ArrayList<JSONObject>()
        collect(res, "sortFilterSubMenuRenderer", menus)
        for (m in menus) {
            val items = m.optJSONArray("subMenuItems") ?: continue
            for (i in 0 until items.length()) {
                val it = items.optJSONObject(i) ?: continue
                if (it.optBoolean("selected")) return it.optString("title").contains("auto-generated", true)
            }
        }
        return false
    }

    fun captions(q: LyricsQuery): Lyrics? {
        for (hit in search(q, FILTER_VIDEOS).take(3)) {
            // durata troppo diversa: i tempi non tornerebbero
            if (q.durationSec > 0 && hit.seconds != null && abs(hit.seconds - q.durationSec) > 25) continue
            val idBytes = hit.videoId.toByteArray(Charsets.UTF_8)
            val params = Base64.encodeToString(byteArrayOf(0x0A, idBytes.size.toByte()) + idBytes, Base64.NO_WRAP)
            val res = post("get_transcript", JSONObject().put("params", params)) ?: continue
            // sottotitoli automatici: troppi errori sui testi cantati, li scarto
            if (isAutoGenerated(res)) continue
            val cues = ArrayList<JSONObject>()
            collect(res, "transcriptCueRenderer", cues)
            val raw = cues.mapNotNull { c ->
                val start = c.optString("startOffsetMs").toLongOrNull() ?: return@mapNotNull null
                val txt = text(c.opt("cue"))?.replace('\n', ' ')?.trim(' ', '\t', '♪')?.trim().orEmpty()
                if (txt.isEmpty() || txt.startsWith("[") && txt.endsWith("]")) null else start to txt
            }.sortedBy { it.first }
            if (raw.size < 4) continue
            val lines = raw.mapIndexed { i, (s, txt) -> LLine(s, raw.getOrNull(i + 1)?.first ?: (s + 4000), txt) }
            return Lyrics(lines, "YouTube (sottotitoli)")
        }
        return null
    }
}
