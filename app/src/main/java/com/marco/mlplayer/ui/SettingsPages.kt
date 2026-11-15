package com.marco.mlplayer.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.runtime.Composable
import com.marco.mlplayer.AppUpdate
import com.marco.mlplayer.AppUpdateStatus
import com.marco.mltheme.MLChoiceRow
import com.marco.mltheme.MLDependent
import com.marco.mltheme.MLGroup
import com.marco.mltheme.MLNavRow
import com.marco.mltheme.MLSettingsScaffold
import com.marco.mltheme.MLSwitchRow
import com.marco.mltheme.MLThemeSettingsGroup

/** Pagina principale delle impostazioni, a gruppi come nelle altre MLapps. */
@Composable
fun SettingsHome(env: AppEnv) {
    val s = env.settings
    val nav = env.settingsNav
    fun go(screen: Screen) {
        env.feedback.tapScreen()
        nav.push(screen)
    }
    MLSettingsScaffold("Impostazioni", onBack = null) {
        MLGroup {
            MLNavRow("Aspetto", "Tema, colori, VU meter", icon = Icons.Filled.Palette) { go(AppearanceScreen) }
        }
        MLGroup("Audio") {
            MLNavRow("Equalizzatore", "Parametrico, AutoEQ, grafico e moduli DSP", icon = Icons.Filled.Equalizer) {
                env.feedback.tapScreen(); env.select(Tab.Eq)
            }
            MLNavRow("Riproduzione", "Mescola, ripeti, uscita adattiva", icon = Icons.Filled.PlayCircle) { go(env.playbackScreen()) }
            MLNavRow(
                "Percorso del segnale",
                "USB esclusiva, uscita diretta, bit-perfect",
                icon = Icons.Filled.Route
            ) { go(SignalPathScreen) }
            MLNavRow("Uscita audio", "Dispositivi, flusso e DSP in uso", icon = Icons.Filled.Headphones) {
                env.feedback.tapScreen(); env.overlay = Overlay.Output
            }
            MLNavRow("Analizzatore", "Spettro, livelli, fase e PPM", icon = Icons.Filled.Timeline) {
                env.feedback.tapScreen(); env.overlay = Overlay.Analyzer
            }
        }
        MLGroup("Libreria") {
            MLNavRow(
                "Cartelle e scansione",
                "${s.folders.size} cartelle · ${env.library.tracks.size} brani",
                icon = Icons.Filled.LibraryMusic
            ) { go(env.libraryScreen()) }
        }
        MLGroup("Tatto") {
            MLSwitchRow("Vibrazione al tocco", null, s.hapticScreen, icon = Icons.Filled.Vibration) {
                s.hapticScreen = it
                env.feedback.tapScreen()
            }
            MLDependent(s.hapticScreen) {
                MLChoiceRow(
                    "Intensità",
                    listOf("0" to "Leggera", "1" to "Media", "2" to "Forte"),
                    s.hapticStrength.coerceIn(0, 2).toString()
                ) { s.hapticStrength = it.toInt(); env.feedback.test() }
            }
        }
        MLGroup("App") {
            MLNavRow(
                "Aggiornamenti",
                if (AppUpdateStatus.release != null) "Nuova versione disponibile" else "Cerca la nuova versione su GitHub",
                icon = Icons.Filled.SystemUpdate
            ) { go(env.updateScreen()); AppUpdate.check(env.app) }
            MLNavRow("Debug", "Log e informazioni tecniche", icon = Icons.Filled.BugReport) { go(env.debugScreen()) }
            MLNavRow("Informazioni", "Versione, licenze, componenti", icon = Icons.Filled.Info) { go(env.aboutScreen()) }
        }
    }
}

/** Impostazioni › Aspetto: tema di MLTheme, colori dalla copertina, VU meter. */
@Composable
fun AppearancePage(env: AppEnv, onBack: (() -> Unit)?) {
    val s = env.settings
    MLSettingsScaffold("Aspetto", onBack) {
        MLThemeSettingsGroup("Tema")
        MLGroup("Colori") {
            MLSwitchRow(
                "Colori dalla copertina",
                "Mentre suona un brano, l'app prende i colori della sua copertina",
                s.coverColors
            ) { s.coverColors = it }
        }
        MLGroup("VU meter") {
            MLSwitchRow("VU meter nel lettore", "Livello dei canali sinistro e destro sotto i comandi", s.showVu) { s.showVu = it }
            MLDependent(s.showVu) {
                MLChoiceRow(
                    "Riferimento",
                    VU_REFS.map { it.toString() to vuRefLabel(it) },
                    s.vuRef.toString()
                ) { s.vuRef = it.toInt() }
                MLChoiceRow(
                    "Velocità",
                    listOf("0" to "Morbida (300 ms)", "1" to "Rapida (120 ms)"),
                    s.vuSpeed.coerceIn(0, 1).toString()
                ) { s.vuSpeed = it.toInt() }
            }
        }
        MLGroup("Altro") {
            MLSwitchRow("Copertine negli elenchi", null, s.coverInMenu) { s.coverInMenu = it }
            MLSwitchRow("Schermo sempre acceso", "Mentre l'app è aperta", s.keepScreenOn) { s.keepScreenOn = it }
        }
    }
}
