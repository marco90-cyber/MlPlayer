package com.marco.mlplayer.util

import com.marco.mlplayer.BuildConfig
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Richieste HTTP semplici per testi e copertine (sempre da chiamare fuori dal thread principale). */
object Http {
    val UA = "MLPlayer/${BuildConfig.VERSION_NAME} (${BuildConfig.REPO_URL})"
    const val BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    /** URL con parametri (quelli vuoti vengono saltati). */
    fun url(base: String, vararg params: Pair<String, String?>): String {
        val q = params.filter { !it.second.isNullOrBlank() }.joinToString("&") { "${it.first}=${enc(it.second!!)}" }
        return if (q.isEmpty()) base else "$base?$q"
    }

    class Response(val code: Int, val body: ByteArray) {
        val ok get() = code in 200..299
        val text: String get() = String(body, Charsets.UTF_8)
    }

    fun request(
        url: String,
        ua: String = UA,
        accept: String = "application/json",
        post: String? = null,
        timeoutMs: Int = 9000,
        maxBytes: Int = 16 * 1024 * 1024,
    ): Response? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", ua)
            c.setRequestProperty("Accept", accept)
            if (post != null) {
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                c.outputStream.use { it.write(post.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val out = ByteArrayOutputStream()
            stream?.use { s ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = s.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > maxBytes) break
                }
            }
            Response(code, out.toByteArray())
        } finally {
            c.disconnect()
        }
    } catch (e: Throwable) {
        AppLog.d("Rete", "Richiesta non riuscita: ${url.substringBefore('?')} (${e.javaClass.simpleName})")
        null
    }

    /** Testo della risposta se la richiesta è andata a buon fine. */
    fun get(url: String, ua: String = UA, accept: String = "application/json"): String? =
        request(url, ua, accept)?.takeIf { it.ok }?.text

    fun bytes(url: String, ua: String = UA): ByteArray? =
        request(url, ua, accept = "image/*,*/*", timeoutMs = 15000)?.takeIf { it.ok && it.body.isNotEmpty() }?.body
}
