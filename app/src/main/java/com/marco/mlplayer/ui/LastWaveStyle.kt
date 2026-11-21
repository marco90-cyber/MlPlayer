@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.marco.mlplayer.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marco.mlplayer.R

/**
 * Carattere e forme nello stile di LastWave (GPL-3.0, github.com/Clash-Projects/LastWave-native):
 * Google Sans Flex (carattere variabile, licenza OFL) con peso, larghezza e rotondità diversi per ogni stile di testo,
 * e angoli morbidi a 8, 12, 16, 20 e 28 dp.
 */
private fun flex(weight: Float, width: Float, round: Float, opsz: Float): FontFamily = FontFamily(
    Font(
        R.font.google_sans_flex,
        weight = when {
            weight >= 700f -> FontWeight.Bold
            weight >= 600f -> FontWeight.SemiBold
            weight >= 500f -> FontWeight.Medium
            else -> FontWeight.Normal
        },
        variationSettings = FontVariation.Settings(
            FontVariation.weight(weight.toInt().coerceIn(1, 1000)),
            FontVariation.width(width),
            FontVariation.Setting("ROND", round),
            FontVariation.Setting("opsz", opsz),
        ),
    ),
)

private fun st(w: Float, wd: Float, r: Float, o: Float, fw: FontWeight, size: Int, line: Int) =
    TextStyle(fontFamily = flex(w, wd, r, o), fontWeight = fw, fontSize = size.sp, lineHeight = line.sp)

/** Pesi leggermente più leggeri del massimo (come il «Carattere normale» di LastWave). */
private const val TRIM = -60f

val LastWaveTypography = Typography(
    displayLarge = st(760f + TRIM, 122f, 42f, 57f, FontWeight.Bold, 57, 64),
    displayMedium = st(740f + TRIM, 120f, 40f, 45f, FontWeight.Bold, 45, 52),
    displaySmall = st(720f + TRIM, 118f, 38f, 36f, FontWeight.Bold, 36, 44),
    headlineLarge = st(800f + TRIM, 122f, 40f, 32f, FontWeight.Bold, 32, 40),
    headlineMedium = st(780f + TRIM, 120f, 38f, 28f, FontWeight.Bold, 28, 36),
    headlineSmall = st(760f + TRIM, 116f, 36f, 24f, FontWeight.Bold, 24, 32),
    titleLarge = st(640f + TRIM, 110f, 30f, 22f, FontWeight.SemiBold, 22, 28),
    titleMedium = st(600f + TRIM, 106f, 28f, 16f, FontWeight.SemiBold, 16, 24),
    titleSmall = st(580f + TRIM, 105f, 26f, 14f, FontWeight.Medium, 14, 20),
    bodyLarge = st(440f + TRIM, 102f, 18f, 16f, FontWeight.Normal, 16, 24),
    bodyMedium = st(430f + TRIM, 102f, 18f, 15f, FontWeight.Normal, 15, 22),
    bodySmall = st(420f + TRIM, 100f, 16f, 13f, FontWeight.Normal, 13, 18),
    labelLarge = st(680f + TRIM, 108f, 46f, 14f, FontWeight.Bold, 14, 20),
    labelMedium = st(660f + TRIM, 108f, 44f, 12f, FontWeight.SemiBold, 12, 16),
    labelSmall = st(660f + TRIM, 108f, 44f, 11f, FontWeight.SemiBold, 11, 16),
)

val LastWaveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
