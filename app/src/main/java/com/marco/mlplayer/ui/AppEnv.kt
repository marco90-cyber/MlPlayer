package com.marco.mlplayer.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.marco.mlplayer.MLPlayerApp
import com.marco.mlplayer.data.Track
import com.marco.mlplayer.util.AppLog
import kotlinx.coroutines.launch

/** Schermate a tutto schermo sopra al frontale. */
enum class Overlay { None, Log }

/** Sezioni della barra in basso. */
enum class Tab(val label: String) {
    Library("LIBRERIA"),
    NowPlaying("IN RIPROD."),
    Eq("EQ"),
    Analyzer("ANALIZZ."),
    Output("USCITA"),
    Settings("IMPOST."),
}

/** Azioni che richiedono l'Activity (selettore cartelle, browser, condivisione). */
interface Actions {
    fun pickFolder()
    fun openUrl(url: String)
    fun shareLog()
}

class MenuItem(
    val label: String,
    val value: String? = null,
    val chevron: Boolean = false,
    val sub: String? = null,
    val track: Track? = null,
    val albumKey: String? = null,
    val dimmed: Boolean = false,
    val onClick: () -> Unit = {},
)

sealed class Screen

class MenuScreen(
    val title: String,
    val cache: Boolean = false,
    val build: AppEnv.() -> List<MenuItem>,
) : Screen()

/** Impostazioni › Aspetto: scelta della finitura con anteprima. */
object AppearanceScreen : Screen()

class Entry(val screen: Screen) {
    val listState = LazyListState()
}

class Nav(root: Screen) {
    val stack = mutableStateListOf(Entry(root))

    val top: Entry get() = stack.last()

    fun push(screen: Screen) {
        if (stack.last().screen === screen) return
        stack.add(Entry(screen))
    }

    fun pop(): Boolean {
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
            return true
        }
        return false
    }

    fun popToRoot() {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
}

/** Stato globale dell'interfaccia: sezione attiva, navigazione dei menu e overlay. */
class AppEnv(val app: MLPlayerApp) {
    val settings get() = app.settings
    val library get() = app.library
    val engine get() = app.engine
    val feedback get() = app.feedback

    var actions: Actions? = null

    var tab by mutableStateOf(Tab.NowPlaying)
    var overlay by mutableStateOf(Overlay.None)

    val libraryNav = Nav(MenuScreen("Libreria") { musicItems() })
    val settingsNav = Nav(MenuScreen("Impostazioni") { settingsItems() })

    /** Banda dell'analizzatore selezionata (0..30, 17 = 1 kHz) e richiesta di azzeramento picchi. */
    var analyzerBand by mutableStateOf(17)
    var analyzerResetToken by mutableStateOf(0)

    /** Navigazione della sezione attiva (null se la sezione non ha menu). */
    val currentNav: Nav?
        get() = when (tab) {
            Tab.Library -> libraryNav
            Tab.Settings -> settingsNav
            else -> null
        }

    fun select(t: Tab) {
        if (tab == t) currentNav?.popToRoot()
        tab = t
    }

    /** Tasto indietro: chiude l'overlay, poi risale nei menu, poi torna a "In riproduzione". */
    fun back(): Boolean {
        if (overlay != Overlay.None) {
            overlay = Overlay.None
            return true
        }
        if (currentNav?.pop() == true) return true
        if (tab != Tab.NowPlaying) {
            tab = Tab.NowPlaying
            return true
        }
        return false
    }

    fun startScan(rebuild: Boolean = false) {
        app.appScope.launch {
            library.rescan(settings.folders, rebuild)
        }
    }

    fun openNowPlaying() {
        tab = Tab.NowPlaying
    }

    fun playFrom(list: List<Track>, index: Int) {
        engine.playQueue(list, index, shuffle = false)
        openNowPlaying()
    }

    fun shuffleAll() {
        val all = library.tracks
        if (all.isEmpty()) return
        engine.playQueue(all, (all.indices).random(), shuffle = true)
        openNowPlaying()
    }

    fun shuffleAlbums() {
        val albums = library.albums
        if (albums.isEmpty()) return
        val queue = albums.shuffled().flatMap { it.tracks }
        engine.playQueue(queue, 0, shuffle = false)
        openNowPlaying()
    }

    fun playPause() {
        val e = engine
        if (e.currentTrack == null && library.tracks.isNotEmpty()) shuffleAll() else e.togglePlayPause()
    }

    fun log(msg: String) = AppLog.d("UI", msg)
}
