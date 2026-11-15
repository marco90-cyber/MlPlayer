package com.marco.mlplayer.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import com.marco.mlplayer.ui.eq.EqPanel
import com.marco.mlplayer.ui.eq.Ml

/** Sezione Equalizzatore: il pannello di MLEqualizer con i colori del tema Material 3. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(env: AppEnv) {
    val cs = MaterialTheme.colorScheme
    Ml.palette = Ml.fromScheme(cs, amoled = false)
    EqPanel(env, "MLPlayer") {
        TopAppBar(
            title = { Text("Equalizzatore") },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.surface),
        )
    }
}
