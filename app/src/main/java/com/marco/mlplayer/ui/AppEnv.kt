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

/** Pagine a tutto schermo sopra alle sezioni (si chiudono con la freccia o il tasto indietro). */
enum class Overlay { None, Log, Analyzer, Output, Lyrics, Cover, Tags, BulkCovers }

/** Sezioni della barra di navigazione in basso. */
enum class Tab(val label: String) {
    Library("Libreria"),
    NowPlaying("Lettore"),
    Eq("Equalizzatore"),
    Settings("Impostazioni"),
}

/** Azioni che richiedono l'Activity (selettore cartelle, browser, condivisione). */
interface Actions {
    fun pickFolder()
    fun openUrl(url: String)
    fun shareLog()
    /** Chiede di nuovo la cartella [tree] con il permesso di scrittura (per tag, copertine e testi). */
    fun requestWriteAccess(tree: String)
    fun shareText(text: String, subject: String)
}

class MenuItem(
    val label: String,
    val value: String? = null,
    val chevron: Boolean = false,
    val sub: String? = null,
    val track: Track? = null,
    val albumKey: String? = null,
    val dimmed: Boolean = false,
    /** Non null = la voce è un interruttore acceso/spento. */
    val toggle: Boolean? = null,
    val onClick: () -> Unit = {},
)

sealed class Screen

class MenuScreen(
    val title: String,
    val cache: Boolean = false,
    val build: AppEnv.() -> List<MenuItem>,
) : Screen()

/** Impostazioni › Aspetto: tema, colori, VU meter. */
object AppearanceScreen : Screen()

/** Impostazioni (pagina principale, con i gruppi di MLTheme). */
object SettingsHomeScreen : Screen()

/** Percorso del segnale e opzioni d'uscita (USB esclusiva, diretta, bit-perfect). */
object SignalPathScreen : Screen()

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

/** Stato globale dell'interfaccia: sezione attiva, navigazione dei menu e pagine sopra. */
class AppEnv(val app: MLPlayerApp) {
    val settings get() = app.settings
    val library get() = app.library
    val engine get() = app.engine
    val feedback get() = app.feedback

    /** Curva calcolata dal motore nativo per l'equalizzatore. */
    val preview by lazy { com.marco.mlplayer.audio.DspPreview() }

    var actions: Actions? = null

    var tab by mutableStateOf(Tab.NowPlaying)
    var overlay by mutableStateOf(Overlay.None)

    /** Pannello "Percorso del segnale" aperto (dalle informazioni sulla qualità nel lettore). */
    var showSignalPath by mutableStateOf(false)

    /** Coda di riproduzione aperta (foglio dal basso nel lettore). */
    var showQueue by mutableStateOf(false)

    /** Menu ⋮ del brano (Testo, Copertina, Modifica tag). */
    var showTrackMenu by mutableStateOf(false)

    /** Brano su cui lavorano le pagine Copertina e Modifica tag. */
    var editTrack by mutableStateOf<Track?>(null)

    /** Brani e nome della cartella per "Cerca le copertine". */
    var bulkTracks by mutableStateOf<List<Track>>(emptyList())
    var bulkTitle by mutableStateOf("")

    /** Finestra con l'avanzamento della scansione (dopo aver scelto una cartella). */
    var showScanDialog by mutableStateOf(false)

    /** Messaggio breve in basso (Snackbar). */
    var message by mutableStateOf<String?>(null)

    /** Testi dei brani (cache, file, fonti online). */
    val lyrics by lazy { com.marco.mlplayer.lyrics.LyricsStore(app, app.settings) }

    /** Cosa rifare quando l'utente concede il permesso di scrittura. */
    private var afterWriteGrant: (() -> Unit)? = null

    fun requestWrite(tree: String, then: () -> Unit) {
        afterWriteGrant = then
        message = "Scegli la cartella «${folderName(tree)}» (o una che la contiene) e tocca «Usa questa cartella»"
        actions?.requestWriteAccess(tree)
    }

    fun onWriteGranted() {
        val f = afterWriteGrant
        afterWriteGrant = null
        lyrics.needsAccess = null
        f?.invoke()
    }

    fun openPage(o: Overlay, t: Track? = engine.currentTrack) {
        showTrackMenu = false
        editTrack = t
        overlay = o
    }

    /**
     * Scrive nei file dei [tracks] con [op], poi rilegge la libreria e aggiorna la coda.
     * Se manca il permesso chiede la cartella e, una volta concesso, ricomincia da capo.
     */
    fun writeFiles(
        tracks: List<Track>, what: String, op: (Track) -> com.marco.mlplayer.tags.WriteResult,
        done: () -> Unit = {}, askAccess: Boolean = true,
    ) {
        app.appScope.launch {
            val ok = ArrayList<String>()
            var failed = 0
            var stop = false
            for (t in tracks) {
                val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { op(t) }
                when (r) {
                    is com.marco.mlplayer.tags.WriteResult.Ok -> ok += t.uri
                    is com.marco.mlplayer.tags.WriteResult.NoAccess -> {
                        if (askAccess) {
                            // dopo il permesso si riprova una volta sola: niente selettore all'infinito
                            requestWrite(r.tree) { writeFiles(tracks.filter { it.uri !in ok }, what, op, done, askAccess = false) }
                        } else {
                            message = "La cartella scelta non contiene questo brano: serve «${folderName(r.tree)}» o una cartella che la contiene"
                            done()
                        }
                        stop = true
                    }
                    is com.marco.mlplayer.tags.WriteResult.Unsupported -> failed++
                    is com.marco.mlplayer.tags.WriteResult.Failed -> failed++
                }
                if (stop) break
            }
            if (ok.isNotEmpty()) {
                library.refreshTracks(ok)
                val byUri = library.tracks.filter { it.uri in ok }.associateBy { it.uri }
                engine.replaceTracks(byUri, ok.toSet())
                editTrack = editTrack?.let { byUri[it.uri] ?: it }
            }
            if (!stop) {
                message = when {
                    failed == 0 -> "$what: salvato"
                    ok.isEmpty() -> "$what: non salvato (formato non supportato o file non scrivibile)"
                    else -> "$what: salvato in ${ok.size} brani, ${failed} non riusciti"
                }
                done()
            }
        }
    }

    val libraryNav = Nav(MenuScreen("Libreria") { musicItems() })
    val settingsNav = Nav(SettingsHomeScreen)

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

    /** Tasto indietro: chiude coda e pagine sopra, poi risale nei menu, poi torna al lettore. */
    fun back(): Boolean {
        if (showQueue) {
            showQueue = false
            return true
        }
        if (showTrackMenu) {
            showTrackMenu = false
            return true
        }
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
        overlay = Overlay.None
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
