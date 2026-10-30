package com.marco.mlplayer.ui

import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import com.marco.mlplayer.BuildConfig
import com.marco.mlplayer.data.Album
import com.marco.mlplayer.data.Artist
import com.marco.mlplayer.data.Track
import com.marco.mlplayer.util.AppLog

// ---------------------------------------------------------------- utilità

private fun toggleItem(label: String, value: Boolean, onChange: (Boolean) -> Unit) =
    MenuItem(label, value = if (value) "Sì" else "No") { onChange(!value) }

private fun info(label: String, value: String) = MenuItem(label, sub = value)

fun folderName(uriStr: String): String = try {
    val id = DocumentsContract.getTreeDocumentId(Uri.parse(uriStr))
    val volume = id.substringBefore(':')
    val path = id.substringAfter(':', "")
    val prefix = if (volume == "primary") "" else "[$volume] "
    prefix + path.ifBlank { "Memoria interna" }
} catch (_: Throwable) {
    uriStr
}

// ---------------------------------------------------------------- menu principale e musica

fun AppEnv.musicItems(): List<MenuItem> =
    if (library.tracks.isEmpty()) {
        listOf(
            MenuItem("Nessun brano", dimmed = true),
            MenuItem("Aggiungi cartella musicale", chevron = true) {
                tab = Tab.Settings
                settingsNav.popToRoot()
                settingsNav.push(libraryScreen())
            },
        )
    } else {
        listOf(
            MenuItem("Artisti", value = "${library.artists.size}", chevron = true) { libraryNav.push(artistsScreen()) },
            MenuItem("Album", value = "${library.albums.size}", chevron = true) { libraryNav.push(albumsScreen()) },
            MenuItem("Brani", value = "${library.tracks.size}", chevron = true) {
                libraryNav.push(songsScreen("Brani", library.tracks))
            },
            MenuItem("Generi", value = "${library.genres.size}", chevron = true) { libraryNav.push(genresScreen()) },
            MenuItem("Cartelle", value = "${settings.folders.size}", chevron = true) { openFolders() },
            MenuItem("Mescola brani") { shuffleAll() },
            MenuItem("Mescola album") { shuffleAlbums() },
        )
    }

// ---------------------------------------------------------------- cartelle

private fun Track.folderPath(): String = try {
    DocumentsContract.getDocumentId(Uri.parse(uri)).substringBeforeLast('/', "")
} catch (_: Throwable) {
    ""
}

private fun treeId(uri: String): String = DocumentsContract.getTreeDocumentId(Uri.parse(uri))

/** Apre la navigazione per cartelle (se c'è una sola cartella scelta entra direttamente). */
fun AppEnv.openFolders() {
    val roots = settings.folders
    when {
        roots.isEmpty() -> { tab = Tab.Settings; settingsNav.push(libraryScreen()) }
        roots.size == 1 -> libraryNav.push(folderBrowseScreen(treeId(roots[0]), folderName(roots[0])))
        else -> libraryNav.push(foldersRootScreen())
    }
}

fun AppEnv.foldersRootScreen(): MenuScreen = MenuScreen("Cartelle") {
    settings.folders.map { f ->
        MenuItem(folderName(f), chevron = true) { libraryNav.push(folderBrowseScreen(treeId(f), folderName(f))) }
    }
}

fun AppEnv.folderBrowseScreen(path: String, title: String): MenuScreen =
    MenuScreen(title, cache = true) {
        val prefix = if (path.endsWith(":")) path else "$path/"
        val ordered = library.tracks
            .filter { val f = it.folderPath(); f == path || f.startsWith(prefix) }
            .sortedWith(compareBy<Track>({ it.folderPath() }, { it.discNo }, { it.trackNo }, { it.title.lowercase() }))
        val direct = ordered.filter { it.folderPath() == path }
        val subs = ordered.map { it.folderPath() }
            .filter { it != path }
            .map { it.removePrefix(prefix).substringBefore('/') }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)

        val items = ArrayList<MenuItem>()
        if (ordered.isEmpty()) {
            items.add(MenuItem("Nessun brano in questa cartella", dimmed = true))
        } else {
            items.add(
                MenuItem(
                    "Riproduci cartella", value = "${ordered.size}",
                    albumKey = ordered.first().albumKey
                ) { playFrom(ordered, 0) }
            )
            items.add(
                MenuItem(
                    "Mescola cartella",
                    albumKey = ordered.first().albumKey
                ) {
                    engine.playQueue(ordered, ordered.indices.random(), shuffle = true)
                    openNowPlaying()
                }
            )
        }
        for (sub in subs) {
            val subPath = prefix + sub
            val inSub = ordered.filter { val f = it.folderPath(); f == subPath || f.startsWith("$subPath/") }
            items.add(
                MenuItem(
                    sub, value = "${inSub.size}", chevron = true,
                    albumKey = inSub.firstOrNull()?.albumKey
                ) { libraryNav.push(folderBrowseScreen(subPath, sub)) }
            )
        }
        direct.forEachIndexed { i, t -> items.add(MenuItem(t.title, track = t) { playFrom(direct, i) }) }
        items
    }

fun AppEnv.artistsScreen(): MenuScreen = MenuScreen("Artisti", cache = true) {
    library.artists.map { a ->
        MenuItem(
            a.name, chevron = true, sub = "${a.albums.size} album",
            albumKey = a.albums.firstOrNull()?.key
        ) { libraryNav.push(artistScreen(a)) }
    }
}

fun AppEnv.artistScreen(a: Artist): MenuScreen = MenuScreen(a.name, cache = true) {
    val all = a.tracks
    val head = MenuItem(
        "Tutti i brani", value = "${all.size}", chevron = true,
        albumKey = a.albums.firstOrNull()?.key
    ) { libraryNav.push(songsScreen(a.name, all)) }
    listOf(head) + a.albums.map { al ->
        MenuItem(
            al.title, chevron = true, albumKey = al.key
        ) { libraryNav.push(songsScreen(al.title, al.tracks)) }
    }
}

fun AppEnv.albumsScreen(): MenuScreen = MenuScreen("Album", cache = true) {
    library.albums.map { al: Album ->
        MenuItem(
            al.title, chevron = true, albumKey = al.key, sub = al.artist
        ) { libraryNav.push(songsScreen(al.title, al.tracks)) }
    }
}

fun AppEnv.songsScreen(title: String, list: List<Track>): MenuScreen =
    MenuScreen(title, cache = true) {
        list.mapIndexed { i, t -> MenuItem(t.title, track = t) { playFrom(list, i) } }
    }

fun AppEnv.genresScreen(): MenuScreen = MenuScreen("Generi", cache = true) {
    library.genres.map { g ->
        MenuItem(
            g.name, value = "${g.tracks.size}", chevron = true,
            albumKey = g.tracks.firstOrNull()?.albumKey
        ) { libraryNav.push(songsScreen(g.name, g.tracks)) }
    }
}

// ---------------------------------------------------------------- impostazioni

fun AppEnv.settingsItems(): List<MenuItem> = listOf(
    MenuItem("Aspetto", value = Hifi.finish.name, chevron = true) { settingsNav.push(AppearanceScreen) },
    MenuItem("Suoni e tatto", chevron = true) { settingsNav.push(soundScreen()) },
    MenuItem("Libreria", chevron = true) { settingsNav.push(libraryScreen()) },
    MenuItem("Equalizzatore", chevron = true) { tab = Tab.Eq },
    MenuItem("Riproduzione", chevron = true) { settingsNav.push(playbackScreen()) },
    MenuItem(
        "Aggiornamenti",
        value = if (com.marco.mlplayer.update.Updater.updateAvailable) "Nuova versione" else null,
        chevron = true
    ) { settingsNav.push(updateScreen()); com.marco.mlplayer.update.Updater.check() },
    MenuItem("Debug", chevron = true) { settingsNav.push(debugScreen()) },
    MenuItem("Informazioni", chevron = true) { settingsNav.push(aboutScreen()) },
)

fun AppEnv.updateScreen(): MenuScreen = MenuScreen("Aggiornamenti") {
    val u = com.marco.mlplayer.update.Updater
    val r = u.latest
    buildList {
        add(MenuItem("Cerca aggiornamenti", sub = u.status, dimmed = u.checking) { u.check() })
        add(info("Versione installata", "${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"))
        if (r != null) add(info("Ultima su GitHub", "${r.name} (build ${r.build})"))
        if (r != null && u.updateAvailable) {
            add(MenuItem("Scarica e installa", sub = "Download con pausa e ripresa", chevron = true) {
                com.marco.mlplayer.AppUpdate.open(app)
            })
        }
        if (r != null) add(MenuItem("Pagina della release", chevron = true) { actions?.openUrl(r.pageUrl) })
    }
}

fun AppEnv.soundScreen(): MenuScreen = MenuScreen("Suoni e tatto") {
    val strengths = listOf("Leggera", "Media", "Forte")
    val volumes = listOf("Basso", "Medio", "Alto")
    listOf(
        toggleItem("Vibrazione schermo", settings.hapticScreen) { settings.hapticScreen = it },
        toggleItem("Suono schermo", settings.soundScreen) { settings.soundScreen = it },
        toggleItem("Vibrazione manopole", settings.hapticWheel) { settings.hapticWheel = it },
        toggleItem("Suono manopole", settings.soundWheel) { settings.soundWheel = it },
        MenuItem("Intensità vibrazione", value = strengths[settings.hapticStrength.coerceIn(0, 2)]) {
            settings.hapticStrength = (settings.hapticStrength + 1) % 3
            feedback.test()
        },
        MenuItem("Volume del click", value = volumes[settings.clickVolume.coerceIn(0, 2)]) {
            settings.clickVolume = (settings.clickVolume + 1) % 3
            feedback.test()
        },
        MenuItem("Prova feedback") { feedback.test() },
    )
}

fun AppEnv.libraryScreen(): MenuScreen = MenuScreen("Libreria") {
    val s = library.scan
    val scanValue = if (s.running) {
        if (s.total > 0) "${s.done}/${s.total}" else s.message
    } else s.message.ifBlank { null }
    listOf(
        MenuItem("Cartelle musicali", value = "${settings.folders.size}", chevron = true) {
            settingsNav.push(foldersScreen())
        },
        MenuItem(
            if (s.running) "Scansione in corso..." else "Scansiona cartelle",
            value = scanValue,
        ) { if (!library.scan.running) startScan(false) },
        toggleItem("Scansione all'avvio", settings.scanOnStart) { settings.scanOnStart = it },
        MenuItem("Ricostruisci libreria", sub = "Rilegge tutto, copertine comprese") {
            if (!library.scan.running) startScan(true)
        },
        MenuItem("Brani", value = "${library.tracks.size}", dimmed = true),
        MenuItem("Album", value = "${library.albums.size}", dimmed = true),
        MenuItem("Artisti", value = "${library.artists.size}", dimmed = true),
    )
}

fun AppEnv.foldersScreen(): MenuScreen = MenuScreen("Cartelle musicali") {
    val add = MenuItem("Aggiungi cartella...", chevron = true) { actions?.pickFolder() }
    listOf(add) + settings.folders.map { f ->
        MenuItem(folderName(f), chevron = true) { settingsNav.push(folderActionScreen(f)) }
    }
}

fun AppEnv.folderActionScreen(uri: String): MenuScreen = MenuScreen(folderName(uri)) {
    listOf(
        MenuItem("Rimuovi cartella") {
            settings.removeFolder(uri)
            AppLog.i("Libreria", "Cartella rimossa: ${folderName(uri)}")
            settingsNav.pop()
            startScan(false)
        },
        MenuItem("Percorso", sub = folderName(uri), dimmed = true),
    )
}

fun AppEnv.playbackScreen(): MenuScreen = MenuScreen("Riproduzione") {
    val repeatLabels = listOf("No", "Tutto", "Brano")
    listOf(
        MenuItem("Mescola", value = if (engine.shuffleOn) "Sì" else "No") { engine.toggleShuffle() },
        MenuItem("Ripeti", value = repeatLabels[engine.repeatMode.coerceIn(0, 2)]) { engine.cycleRepeat() },
        MenuItem(
            "Uscita adattiva",
            value = if (settings.adaptiveOutput) "Sì" else "No",
            sub = "Bit e kHz si adattano a DAC o casse · dal prossimo brano"
        ) { engine.setAdaptiveOutput(!settings.adaptiveOutput) },
        MenuItem("Equalizzatore", chevron = true) { tab = Tab.Eq },
        MenuItem("Uscita audio", chevron = true) { tab = Tab.Output },
    )
}

fun AppEnv.debugScreen(): MenuScreen = MenuScreen("Debug") {
    listOf(
        MenuItem("Visualizza log", value = "${AppLog.snapshot().size}", chevron = true) { overlay = Overlay.Log },
        MenuItem("Esporta log") { actions?.shareLog() },
        MenuItem("Cancella log") { AppLog.clear() },
        toggleItem("Log dettagliato", settings.verboseLog) {
            settings.verboseLog = it
            AppLog.verbose = it
            AppLog.i("Debug", "Log dettagliato ${if (it) "attivato" else "disattivato"}")
        },
        MenuItem("Informazioni audio", chevron = true) { settingsNav.push(audioInfoScreen()) },
        MenuItem("Informazioni libreria", chevron = true) { settingsNav.push(libraryInfoScreen()) },
        MenuItem("Prova vibrazione e suono") { feedback.test() },
    )
}

fun AppEnv.audioInfoScreen(): MenuScreen = MenuScreen("Info audio") {
    val t = engine.currentTrack
    if (t == null) {
        listOf(MenuItem("Nessun brano in riproduzione", dimmed = true))
    } else {
        listOf(
            info("Brano", t.title),
            info("Formato", engine.audioInfo.ifBlank { t.detailLine() }),
            info("Hi-Res", if (engine.hiResNow) "Sì" else "No"),
            info("Uscita", if (settings.adaptiveOutput) "Adattiva" else "Standard"),
            info("Equalizzatore", if (settings.eq.enabled) "${settings.eq.bandCount} bande attivo" else "Spento"),
            info("File", t.uri),
        )
    }
}

fun AppEnv.libraryInfoScreen(): MenuScreen = MenuScreen("Info libreria") {
    listOf(
        info("Brani", "${library.tracks.size}"),
        info("Album", "${library.albums.size}"),
        info("Artisti", "${library.artists.size}"),
        info("Generi", "${library.genres.size}"),
        info("Copertine in cache", "${library.coverCount()}"),
        info("Cartelle", "${settings.folders.size}"),
        info("Ultima scansione", library.lastScanInfo.ifBlank { "Mai eseguita" }),
        info("Formati letti", "MP3, FLAC, WAV, OGG, Opus, AAC, M4A, MKA, AMR"),
    )
}

fun AppEnv.aboutScreen(): MenuScreen = MenuScreen("Informazioni") {
    listOf(
        info("MLPlayer", "Lettore musicale Hi-Res in stile hi-fi"),
        info("Versione", BuildConfig.VERSION_NAME),
        info("Build", "${BuildConfig.VERSION_CODE}"),
        info("Sviluppatore", "Marco"),
        info("Creata con", "Claude (Anthropic)"),
        MenuItem("Repository", sub = BuildConfig.REPO_URL, chevron = true) {
            actions?.openUrl(BuildConfig.REPO_URL)
        },
        info("Dispositivo", "${Build.MANUFACTURER} ${Build.MODEL}"),
        info("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"),
        info("Licenza", "Uso personale"),
        info("Componenti", "AndroidX Media3, Jetpack Compose (Apache 2.0)"),
    )
}
