package com.marco.mlplayer.lyrics

import com.marco.mlplayer.util.Http
import org.json.JSONArray
import java.util.Locale

/**
 * Traduzione e romanizzazione delle righe (servizio di Google Traduttore usato anche da LastWave).
 * Le righe viaggiano in blocchi separati da marcatori, così ogni traduzione torna alla sua riga.
 */
object LyricsTranslate {
    private const val ENDPOINT = "https://translate.googleapis.com/translate_a/single"
    private const val MAX_CHARS = 1800
    private val MARKER = Regex("""\s*\d{4}\s*""")

    private fun marker(i: Int) = "${i.toString().padStart(4, '0')}"

    /** Lingua del telefono (es. "it"). */
    fun deviceLanguage(): String = Locale.getDefault().language.ifBlank { "it" }

    /** Una traduzione per ogni riga (vuota se la riga è vuota o è già nella lingua scelta); null se non riesce. */
    fun translate(lines: List<String>, target: String = deviceLanguage()): List<String>? = batched(lines) { payload ->
        val res = Http.request(
            ENDPOINT, accept = "application/json",
            post = "client=dict-chrome-ex&sl=auto&tl=${Http.enc(target)}&dt=t&q=${Http.enc(payload)}"
        )?.takeIf { it.ok }?.text ?: return@batched null
        val root = JSONArray(res)
        val segs = root.getJSONArray(0)
        val sb = StringBuilder()
        for (i in 0 until segs.length()) sb.append(segs.getJSONArray(i).optString(0, ""))
        val src = root.optString(2, "")
        if (src.equals(target, true)) "" else sb.toString()
    }

    /** Pronuncia in caratteri latini (giapponese, coreano, cinese, russo...). */
    fun romanize(lines: List<String>): List<String>? = batched(lines) { payload ->
        val res = Http.request(
            ENDPOINT, accept = "application/json",
            post = "client=dict-chrome-ex&sl=auto&tl=en&dt=rm&q=${Http.enc(payload)}"
        )?.takeIf { it.ok }?.text ?: return@batched null
        val segs = JSONArray(res).getJSONArray(0)
        val sb = StringBuilder()
        for (i in 0 until segs.length()) {
            val s = segs.getJSONArray(i)
            sb.append(if (s.length() > 3) s.optString(3, "") else "")
        }
        sb.toString()
    }

    private fun batched(lines: List<String>, call: (String) -> String?): List<String>? = runCatching {
        val out = MutableList(lines.size) { "" }
        val idx = lines.indices.filter { lines[it].isNotBlank() }
        var start = 0
        while (start < idx.size) {
            var end = start
            var len = 0
            while (end < idx.size && (end == start || len + lines[idx[end]].length + 8 <= MAX_CHARS)) {
                len += lines[idx[end]].length + 8; end++
            }
            val batch = idx.subList(start, end)
            val payload = buildString {
                batch.forEachIndexed { i, li -> if (i > 0) append('\n').append(marker(i)).append('\n'); append(lines[li]) }
            }
            val answer = call(payload) ?: return null
            if (answer.isNotEmpty()) {
                val parts = answer.split(MARKER).map { it.trim() }
                if (parts.size == batch.size) batch.forEachIndexed { i, li ->
                    out[li] = parts[i].takeIf { !it.equals(lines[li].trim(), ignoreCase = true) } ?: ""
                }
            }
            start = end
        }
        out
    }.getOrNull()
}
