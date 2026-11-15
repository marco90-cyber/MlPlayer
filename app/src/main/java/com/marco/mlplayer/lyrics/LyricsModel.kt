package com.marco.mlplayer.lyrics

import java.util.Locale

/** Una parola (o sillaba) con il suo tempo, per il testo sincronizzato a parole. */
data class LWord(val start: Long, val end: Long, val text: String)

/** Una riga del testo; [start] < 0 = testo senza tempi. */
data class LLine(val start: Long, val end: Long, val text: String, val words: List<LWord> = emptyList())

enum class SyncKind(val label: String) {
    Words("Sync parole"),
    Lines("Sync righe"),
    Plain("Solo testo"),
}

/** Testo di un brano con la fonte da cui viene. */
data class Lyrics(val lines: List<LLine>, val source: String) {
    val synced: Boolean get() = lines.any { it.start >= 0 }
    val kind: SyncKind
        get() = when {
            lines.any { it.words.isNotEmpty() } -> SyncKind.Words
            synced -> SyncKind.Lines
            else -> SyncKind.Plain
        }
    val plainText: String get() = lines.joinToString("\n") { it.text }
    val isEmpty: Boolean get() = lines.none { it.text.isNotBlank() }

    /** In formato LRC (con le parole in formato "enhanced" se ci sono), per il file e la cache. */
    fun toLrc(): String = if (!synced) plainText else buildString {
        for (l in lines) {
            append('[').append(stamp(l.start)).append(']')
            if (l.words.isEmpty()) append(l.text)
            else for ((i, w) in l.words.withIndex()) {
                append('<').append(stamp(w.start)).append('>').append(w.text)
                if (i == l.words.lastIndex) append('<').append(stamp(w.end)).append('>')
            }
            append('\n')
        }
    }.trimEnd()

    companion object {
        fun stamp(ms: Long): String {
            val t = ms.coerceAtLeast(0)
            return String.format(Locale.ROOT, "%02d:%02d.%02d", t / 60000, (t / 1000) % 60, (t % 1000) / 10)
        }
    }
}

/** Lettura dei formati LRC: normale, con più tempi per riga, "enhanced" con i tempi delle parole. */
object Lrc {
    private val TIME = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val WORD = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")
    private val OFFSET = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
    private val SPACES = Regex("\\s+")
    private val TAG = Regex("""^\[[a-zA-Z#]+:.*]$""")

    private fun ms(m: String, s: String, frac: String): Long {
        val f = when (frac.length) {
            0 -> 0L
            1 -> frac.toLong() * 100
            2 -> frac.toLong() * 10
            else -> frac.take(3).toLong()
        }
        return m.toLong() * 60000 + s.toLong() * 1000 + f
    }

    fun isLrc(text: String): Boolean = text.lineSequence().take(40).count { TIME.containsMatchIn(it) } >= 2

    fun parse(text: String, source: String): Lyrics {
        if (!isLrc(text)) {
            val lines = text.replace("\r", "").lines().map { LLine(-1, -1, it.trim()) }
                .dropWhile { it.text.isBlank() }.dropLastWhile { it.text.isBlank() }
            return Lyrics(lines, source)
        }
        var offset = 0L
        data class Raw(val t: Long, val body: String)
        val raws = ArrayList<Raw>()
        for (row in text.replace("\r", "").lines()) {
            val line = row.trim()
            if (line.isEmpty()) continue
            OFFSET.find(line)?.let { offset = it.groupValues[1].toLongOrNull() ?: 0L; return@let }
            val stamps = TIME.findAll(line).toList()
            if (stamps.isEmpty() || (stamps.size == 1 && TAG.matches(line) && stamps[0].range.first != 0)) continue
            val body = line.replace(TIME, "")
            for (m in stamps) raws += Raw(ms(m.groupValues[1], m.groupValues[2], m.groupValues[3]), body)
        }
        raws.sortBy { it.t }
        val out = ArrayList<LLine>()
        for ((i, r) in raws.withIndex()) {
            val next = raws.getOrNull(i + 1)?.t ?: (r.t + 5000)
            // l'offset del file sposta in avanti il testo (valore positivo = testo prima)
            val start = (r.t - offset).coerceAtLeast(0)
            val end = (next - offset).coerceAtLeast(start)
            val words = parseWords(r.body, -offset, end)
            val clean = r.body.replace(WORD, "").replace(SPACES, " ").trim()
            out += LLine(start, end, clean, words)
        }
        return Lyrics(out, source)
    }

    /** Parole di una riga enhanced: "<00:01.00>Ciao <00:01.50>mondo<00:02.10>". */
    private fun parseWords(body: String, shift: Long, lineEnd: Long): List<LWord> {
        val marks = WORD.findAll(body).toList()
        if (marks.isEmpty()) return emptyList()
        val words = ArrayList<LWord>()
        for ((i, m) in marks.withIndex()) {
            val textStart = m.range.last + 1
            val textEnd = marks.getOrNull(i + 1)?.range?.first ?: body.length
            val t = body.substring(textStart, textEnd)
            if (t.isBlank()) continue
            val s = (ms(m.groupValues[1], m.groupValues[2], m.groupValues[3]) + shift).coerceAtLeast(0)
            val e = marks.getOrNull(i + 1)?.let { n -> ms(n.groupValues[1], n.groupValues[2], n.groupValues[3]) + shift } ?: lineEnd
            words += LWord(s, e.coerceAtLeast(s), t)
        }
        return words
    }
}
