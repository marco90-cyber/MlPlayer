package com.marco.mlplayer.ui

import androidx.compose.foundation.background
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** Sfondo morbido del lettore: un velo dei colori del tema in alto che sfuma nella superficie. */
fun Modifier.softBackground(cs: ColorScheme): Modifier =
    background(Brush.verticalGradient(0f to cs.primaryContainer.copy(alpha = 0.55f), 0.55f to cs.surface, 1f to cs.surface))

/** Colore scuro di contrasto derivato dal tema (mini player): primario scurito col tema chiaro, contenitore col tema scuro. */
internal fun deepAccent(cs: ColorScheme): Color =
    if (cs.surface.luminance() > 0.5f) lerp(cs.primary, Color.Black, 0.55f) else cs.primaryContainer

internal fun onDeepAccent(cs: ColorScheme): Color =
    if (cs.surface.luminance() > 0.5f) Color.White else cs.onPrimaryContainer
