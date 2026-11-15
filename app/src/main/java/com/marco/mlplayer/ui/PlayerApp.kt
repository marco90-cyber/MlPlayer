package com.marco.mlplayer.ui

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.marco.mlplayer.AppUpdateHost
import com.marco.mlplayer.data.rememberCover
import kotlinx.coroutines.delay

private fun Tab.icons(): Pair<ImageVector, ImageVector> = when (this) {
    Tab.Library -> Icons.Filled.LibraryMusic to Icons.Outlined.LibraryMusic
    Tab.NowPlaying -> Icons.Filled.PlayCircle to Icons.Outlined.PlayCircle
    Tab.Eq -> Icons.Filled.Equalizer to Icons.Outlined.Equalizer
    Tab.Settings -> Icons.Filled.Settings to Icons.Outlined.Settings
}

/** L'app intera: tema Material 3, barra di navigazione con 4 sezioni e pagine sopra. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerApp(env: AppEnv) {
    PlayerTheme(env) {
        val activity = LocalContext.current as? Activity
        val cs = MaterialTheme.colorScheme
        val lightBars = cs.surface.luminance() > 0.5f

        SideEffect {
            val w = activity?.window
            if (w != null) {
                val c = WindowCompat.getInsetsController(w, w.decorView)
                c.isAppearanceLightStatusBars = lightBars
                c.isAppearanceLightNavigationBars = lightBars
            }
        }

        // i testi trovati per un brano si scrivono nel suo file quando non sta più suonando
        LaunchedEffect(env.engine.currentTrack?.uri) {
            runCatching { env.lyrics.flushPending(env.engine.currentTrack?.uri) }
        }

        LaunchedEffect(env.settings.keepScreenOn) {
            val w = activity?.window ?: return@LaunchedEffect
            if (env.settings.keepScreenOn) w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        BackHandler {
            if (!env.back()) activity?.moveTaskToBack(true)
        }

        Surface(Modifier.fillMaxSize(), color = cs.surface) {
            Box(Modifier.fillMaxSize()) {
                Scaffold(
                    containerColor = cs.surface,
                    contentWindowInsets = WindowInsets(0),
                    bottomBar = {
                        Column {
                            if (env.tab != Tab.NowPlaying && env.engine.currentTrack != null) MiniPlayer(env)
                            NavBar(env)
                        }
                    }
                ) { pad ->
                    // le pagine dentro non devono riaggiungere lo spazio della barra di navigazione
                    Box(Modifier.padding(pad).consumeWindowInsets(pad).fillMaxSize()) {
                        Crossfade(targetState = env.tab, animationSpec = tween(200), label = "sezione") { t ->
                            when (t) {
                                Tab.NowPlaying -> NowPlayingPage(env)
                                Tab.Library -> MenuPage(env, env.libraryNav)
                                Tab.Eq -> EqualizerScreen(env)
                                Tab.Settings -> MenuPage(env, env.settingsNav)
                            }
                        }
                    }
                }

                // pagine sopra alle sezioni
                for (o in listOf(Overlay.Analyzer, Overlay.Output, Overlay.Log, Overlay.Lyrics, Overlay.Cover, Overlay.Tags, Overlay.BulkCovers)) {
                    AnimatedVisibility(
                        visible = env.overlay == o,
                        enter = slideInVertically(tween(260)) { it / 6 } + fadeIn(tween(200)),
                        exit = slideOutVertically(tween(220)) { it / 6 } + fadeOut(tween(180)),
                    ) {
                        Surface(Modifier.fillMaxSize(), color = cs.surface) {
                            when (o) {
                                Overlay.Analyzer -> AnalyzerPage(env)
                                Overlay.Output -> OutputPage(env)
                                Overlay.Log -> LogScreen(env)
                                Overlay.Lyrics -> LyricsPage(env)
                                Overlay.Cover -> CoverPage(env)
                                Overlay.Tags -> TagEditorPage(env)
                                Overlay.BulkCovers -> BulkCoverPage(env)
                                Overlay.None -> {}
                            }
                        }
                    }
                }

                if (env.showQueue) QueueSheet(env)
                if (env.showScanDialog) ScanDialog(env)
                if (env.showTrackMenu && env.engine.currentTrack != null) TrackMenuSheet(env)

                // messaggi brevi (salvataggi, permessi)
                val snack = remember { SnackbarHostState() }
                LaunchedEffect(env.message) {
                    val m = env.message ?: return@LaunchedEffect
                    snack.showSnackbar(m)
                    if (env.message == m) env.message = null
                }
                SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 88.dp))
                if (env.showSignalPath) SignalPathHost(env) { env.showSignalPath = false }
                AppUpdateHost()
            }
        }
    }
}

@Composable
private fun NavBar(env: AppEnv) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        for (t in Tab.entries) {
            val sel = env.tab == t
            val (on, off) = t.icons()
            NavigationBarItem(
                selected = sel,
                onClick = {
                    env.feedback.tapScreen()
                    env.overlay = Overlay.None
                    env.select(t)
                },
                icon = { Icon(if (sel) on else off, contentDescription = null) },
                label = { Text(t.label, maxLines = 1) },
            )
        }
    }
}

/** Brano in riproduzione sopra la barra, nelle altre sezioni: tocca per aprire il lettore. */
@Composable
private fun MiniPlayer(env: AppEnv) {
    val e = env.engine
    val t = e.currentTrack ?: return
    val cs = MaterialTheme.colorScheme
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(t.id, e.isPlaying) {
        while (true) {
            val d = e.player.duration
            progress = if (d > 0) (e.player.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
            delay(500)
        }
    }
    val file = remember(t.id, env.library.revision) { env.library.trackCoverFile(t) }
    val cover = rememberCover(file, 160)
    Column(
        Modifier
            .fillMaxWidth()
            .background(cs.surfaceContainer)
            .clickable { env.feedback.tapScreen(); env.openNowPlaying() }
    ) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(cs.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (cover != null) Image(cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(Icons.Filled.MusicNote, null, tint = cs.onPrimaryContainer)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (t.artist.isNotBlank()) {
                    Text(
                        t.artist, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = { env.feedback.tapScreen(); env.playPause() }) {
                Icon(
                    if (e.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (e.isPlaying) "Pausa" else "Riproduci"
                )
            }
            IconButton(onClick = { env.feedback.tapScreen(); e.next() }) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Brano successivo")
            }
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            drawStopIndicator = {},
            gapSize = 0.dp,
        )
    }
}
