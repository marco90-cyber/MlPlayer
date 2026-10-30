package com.marco.mlplayer.ui

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

@Composable
fun HifiApp(env: AppEnv) {
    val s = env.settings
    // aspetto attivo letto dalle impostazioni
    Hifi.finish = Finishes.byId(s.finishId)
    Hifi.glow = s.displayGlow
    Hifi.brushed = s.brushedTexture
    Hifi.vuRef = s.vuRef
    Hifi.light = when (s.meterLight) { 0 -> 0.45f; 1 -> 0.75f; else -> 1f }
    val f = Hifi.finish

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = f.vfd, surface = f.scrPanel, background = f.scrBg, onSurface = Color.White,
        )
    ) {
        HifiContent(env)
    }
}

@Composable
private fun HifiContent(env: AppEnv) {
    val activity = LocalContext.current as? Activity
    val f = Hifi.finish

    SideEffect {
        val w = activity?.window
        if (w != null) {
            val c = WindowCompat.getInsetsController(w, w.decorView)
            c.isAppearanceLightStatusBars = f.lightBg
            c.isAppearanceLightNavigationBars = f.lightBg
        }
    }

    LaunchedEffect(env.settings.keepScreenOn) {
        val w = activity?.window ?: return@LaunchedEffect
        if (env.settings.keepScreenOn) w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    BackHandler {
        if (!env.back()) activity?.moveTaskToBack(true)
    }

    Box(Modifier.fillMaxSize().finishBackground(f)) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Crossfade(targetState = env.tab, animationSpec = tween(180), label = "tab") { t ->
                    when (t) {
                        Tab.NowPlaying -> NowPlayingPanel(env)
                        Tab.Library -> MenuPanel(env, env.libraryNav)
                        Tab.Settings -> MenuPanel(env, env.settingsNav)
                        Tab.Eq -> FramedPanel("EQUALIZZATORE") { EqualizerScreen(env) }
                        Tab.Analyzer -> FramedPanel(
                            "ANALIZZATORE",
                            action = {
                                MetalButton("Azzera i picchi", { env.feedback.tapScreen(); env.analyzerResetToken++ }, width = 44.dp, height = 36.dp) {
                                    Icon(Icons.Filled.RestartAlt, null, tint = Hifi.finish.btnIcon, modifier = Modifier.size(20.dp))
                                }
                            }
                        ) { AnalyzerView(env) }
                        Tab.Output -> FramedPanel("USCITA AUDIO") { OutputScreen(env) }
                    }
                }
            }
            TabBar(env)
        }

        AnimatedVisibility(
            visible = env.overlay == Overlay.Log,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Hifi.finish.scrBg)
                    .pointerInput(Unit) { detectTapGestures { } }
            ) { LogScreen(env) }
        }
    }
}

/** Sezione con intestazione incisa sul frontale e il contenuto dentro la finestra del display. */
@Composable
fun FramedPanel(
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val f = Hifi.finish
    Column(
        Modifier.fillMaxSize().plate().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Screw()
            HSpace(10.dp)
            Engraved(title, 15.sp, spacing = 4.sp, modifier = Modifier.weight(1f))
            if (action != null) action()
            HSpace(10.dp)
            Screw()
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(f.scrBg)
        ) { content() }
    }
}

@Composable
private fun TabBar(env: AppEnv) {
    val f = Hifi.finish
    Row(
        Modifier.fillMaxWidth().height(56.dp).plate().padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (t in Tab.entries) {
            val sel = env.tab == t
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable(role = Role.Tab) {
                        env.feedback.tapScreen()
                        env.select(t)
                    }
                    .padding(top = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Canvas(Modifier.size(20.dp, 4.dp)) {
                    if (sel) {
                        drawRoundRect(f.led.copy(alpha = 0.35f * Hifi.light), Offset(-3f, -3f), Size(size.width + 6f, size.height + 6f), CornerRadius(6f))
                        drawRoundRect(f.led, cornerRadius = CornerRadius(4f))
                    } else drawRoundRect(Color(0x44000000), cornerRadius = CornerRadius(4f))
                }
                VSpace(6.dp)
                Engraved(t.label, 10.sp, spacing = 1.sp, dim = !sel)
            }
        }
    }
}
