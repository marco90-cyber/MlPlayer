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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.foundation.shape.CircleShape
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

        val sheet = remember { PlayerSheetState(if (env.tab == Tab.NowPlaying && env.engine.currentTrack != null) 1f else 0f) }
        Surface(Modifier.fillMaxSize(), color = cs.surface) {
            Box(Modifier.fillMaxSize().onGloballyPositioned { sheet.origin = it.positionInRoot() }) {
                Scaffold(
                    containerColor = cs.surface,
                    contentWindowInsets = WindowInsets(0),
                    bottomBar = {
                        Column(Modifier.background(cs.surface)) {
                            if (env.engine.currentTrack != null) MiniPlayer(
                                env,
                                Modifier
                                    .graphicsLayer { alpha = (1f - sheet.expansion.value * 2.2f).coerceIn(0f, 1f) }
                                    .onGloballyPositioned { sheet.miniBounds = it.boundsInRoot() }
                            )
                            NavBar(env, Modifier.onGloballyPositioned { sheet.navTop = it.positionInRoot().y })
                        }
                    }
                ) { pad ->
                    // le pagine dentro non devono riaggiungere lo spazio della barra di navigazione
                    Box(Modifier.padding(pad).consumeWindowInsets(pad).fillMaxSize()) {
                        // con un brano il lettore è lo strato sopra (PlayerSheetLayer): sotto resta la pagina da cui sei arrivato
                        val below = if (env.tab == Tab.NowPlaying && env.engine.currentTrack != null)
                            env.tabBeforePlayer.takeIf { it != Tab.NowPlaying } ?: Tab.Library else env.tab
                        Crossfade(targetState = below, animationSpec = tween(200), label = "sezione") { t ->
                            when (t) {
                                Tab.NowPlaying -> NowPlayingPage(env)
                                Tab.Library -> MenuPage(env, env.libraryNav)
                                Tab.Eq -> EqualizerScreen(env)
                                Tab.Settings -> MenuPage(env, env.settingsNav)
                            }
                        }
                    }
                }

                // lettore: cresce dal mini player e si chiude trascinando in basso
                PlayerSheetLayer(env, sheet)

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
                SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (env.tab != Tab.NowPlaying && env.engine.currentTrack != null) 160.dp else 88.dp))
                if (env.showSignalPath) SignalPathHost(env) { env.showSignalPath = false }
                AppUpdateHost()
            }
        }
    }
}

@Composable
private fun NavBar(env: AppEnv, modifier: Modifier = Modifier) {
    NavigationBar(modifier = modifier, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
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
                label = { Text(t.label, maxLines = 1, softWrap = false, fontSize = 11.sp) },
            )
        }
    }
}

/** Brano in riproduzione, in una scheda scura e arrotondata sopra la barra: tocca per aprire il lettore. */
@Composable
private fun MiniPlayer(env: AppEnv, modifier: Modifier = Modifier) {
    val e = env.engine
    val t = e.currentTrack ?: return
    val cs = MaterialTheme.colorScheme
    val bg = deepAccent(cs)
    val fg = onDeepAccent(cs)
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
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp)
            .then(modifier)
            .clip(RoundedCornerShape(28.dp))
            .background(bg)
            .clickable { env.feedback.tapScreen(); env.openNowPlaying() }
    ) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Apri il lettore", tint = fg, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(cs.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (cover != null) Image(cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(Icons.Filled.MusicNote, null, tint = cs.onPrimaryContainer)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, style = MaterialTheme.typography.titleSmall, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (t.artist.isNotBlank()) {
                    Text(
                        t.artist, style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.75f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = { env.feedback.tapScreen(); e.previous() }) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Brano precedente", tint = fg)
            }
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(fg).clickable { env.feedback.tapScreen(); env.playPause() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (e.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (e.isPlaying) "Pausa" else "Riproduci", tint = bg,
                )
            }
            IconButton(onClick = { env.feedback.tapScreen(); e.next() }) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Brano successivo", tint = fg)
            }
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = fg, trackColor = fg.copy(alpha = 0.2f),
            drawStopIndicator = {},
            gapSize = 0.dp,
        )
    }
}
