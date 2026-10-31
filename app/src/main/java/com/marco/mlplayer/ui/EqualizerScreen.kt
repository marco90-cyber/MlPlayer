package com.marco.mlplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.marco.mlplayer.ui.eq.EqPanel
import com.marco.mlplayer.ui.eq.Ml
import com.marco.mlplayer.ui.eq.MlPalette

/** Colori dell'equalizzatore presi dalla finitura scelta (schermo del frontale). */
private fun hifiPalette(): MlPalette {
    val f = Hifi.f
    return MlPalette(
        background = f.scrBg, panel = f.scrPanel, chip = f.scrChip, dialog = f.scrPanel,
        primary = f.vfd, onPrimary = Color.Black, primaryContainer = lerp(f.scrChip, f.vfd, 0.25f), tertiary = f.accent2,
        text = Color.White, textDim = f.scrDim, textFaint = lerp(f.scrDim, f.scrBg, 0.35f),
        outline = f.scrDim, outlineVariant = f.scrLine,
    )
}

/** Equalizzatore dentro il pannello "EQUALIZZATORE" del frontale. */
@Composable
fun EqualizerScreen(env: AppEnv) {
    Ml.palette = hifiPalette()
    EqPanel(env, "MLPlayer")
}
