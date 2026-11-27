package com.marco.mlplayer.ui

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import com.marco.mlplayer.data.Covers
import com.marco.mltheme.LocalSwitchStyle
import com.marco.mltheme.MLThemeSettings
import com.marco.mltheme.mlColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Colore principale di una copertina: il più vivace, altrimenti il più presente. */
internal fun coverSeed(bmp: Bitmap): Int? {
    val p = Palette.from(bmp).maximumColorCount(16).generate()
    val swatch = p.vibrantSwatch ?: p.lightVibrantSwatch ?: p.darkVibrantSwatch ?: p.dominantSwatch
    return swatch?.rgb
}

/**
 * Tema Material 3 dell'app (MLTheme): chiaro/scuro e colori scelti nelle impostazioni.
 * Con "Colori dalla copertina" acceso, i colori seguono la copertina del brano in riproduzione.
 */
@Composable
fun PlayerTheme(env: AppEnv, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val ml = MLThemeSettings.get(ctx)
    val track = env.engine.currentTrack
    val useCover = env.settings.coverColors
    val coverFile = if (useCover && track != null) env.library.trackCoverFile(track) else null

    val seed by produceState<Int?>(null, coverFile?.path, env.library.revision) {
        value = if (coverFile == null) null else withContext(Dispatchers.IO) {
            runCatching { Covers.load(coverFile, 160)?.let { coverSeed(it) } }.getOrNull()
        }
    }

    val target = if (seed != null) mlColorScheme(ml.theme, "auto", seed) else mlColorScheme(ml.theme, ml.accent)
    val scheme = animatedScheme(target)
    CompositionLocalProvider(LocalSwitchStyle provides ml.switchStyle) {
        MaterialTheme(colorScheme = scheme, typography = LastWaveTypography, shapes = LastWaveShapes, content = content)
    }
}

/** Passaggio morbido tra i colori di una copertina e quelli della successiva. */
@Composable
private fun animatedScheme(t: ColorScheme): ColorScheme {
    @Composable
    fun a(c: Color): Color = animateColorAsState(c, tween(600), label = "tema").value
    return t.copy(
        primary = a(t.primary), onPrimary = a(t.onPrimary),
        primaryContainer = a(t.primaryContainer), onPrimaryContainer = a(t.onPrimaryContainer),
        secondary = a(t.secondary), onSecondary = a(t.onSecondary),
        secondaryContainer = a(t.secondaryContainer), onSecondaryContainer = a(t.onSecondaryContainer),
        tertiary = a(t.tertiary), onTertiary = a(t.onTertiary),
        tertiaryContainer = a(t.tertiaryContainer), onTertiaryContainer = a(t.onTertiaryContainer),
        background = a(t.background), onBackground = a(t.onBackground),
        surface = a(t.surface), onSurface = a(t.onSurface),
        surfaceVariant = a(t.surfaceVariant), onSurfaceVariant = a(t.onSurfaceVariant),
        surfaceContainerLowest = a(t.surfaceContainerLowest), surfaceContainerLow = a(t.surfaceContainerLow),
        surfaceContainer = a(t.surfaceContainer), surfaceContainerHigh = a(t.surfaceContainerHigh),
        surfaceContainerHighest = a(t.surfaceContainerHighest),
        outline = a(t.outline), outlineVariant = a(t.outlineVariant),
    )
}
