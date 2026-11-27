package com.marco.mlplayer.ui

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.marco.mlplayer.data.Covers
import com.marco.mlplayer.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Colore principale della copertina del brano, sempre (anche con «Colori dalla copertina» spento):
 * serve allo sfondo della pagina Testo. Senza copertina torna il colore principale del tema.
 */
@Composable
fun rememberCoverTint(env: AppEnv, t: Track?): Color {
    val ctx = LocalContext.current
    val file = remember(t?.id, env.library.revision) { t?.let { env.library.trackCoverFile(it) } }
    val url = t?.coverUrl.orEmpty()
    val seed by produceState<Int?>(null, file?.path, url, env.library.revision) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bmp = when {
                    file != null -> Covers.load(file, 160)
                    url.isNotBlank() -> {
                        val req = ImageRequest.Builder(ctx).data(url).size(160).allowHardware(false).build()
                        ((ctx.imageLoader.execute(req) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
                    }
                    else -> null
                }
                bmp?.let { coverSeed(it) }
            }.getOrNull()
        }
    }
    return seed?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
}

/** Stesso colore con luminosità [v] e saturazione al massimo [sMax]. */
private fun Color.tone(v: Float, sMax: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(), hsv)
    hsv[1] = hsv[1].coerceAtMost(sMax)
    hsv[2] = v
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** Sfondo a sfumatura nel colore della copertina: più chiaro in alto, quasi nero in basso. */
@Composable
fun LyricsBackdrop(tint: Color, modifier: Modifier = Modifier) {
    val top by animateColorAsState(tint.tone(0.58f, 0.60f), tween(700), label = "testoAlto")
    val mid by animateColorAsState(tint.tone(0.34f, 0.62f), tween(700), label = "testoMezzo")
    val bottom by animateColorAsState(tint.tone(0.12f, 0.55f), tween(700), label = "testoBasso")
    Box(modifier.background(Brush.verticalGradient(listOf(top, mid, bottom))))
}
