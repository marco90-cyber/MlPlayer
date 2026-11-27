package com.marco.mlplayer.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * Apertura e chiusura del lettore come in LastWave (GPL-3.0): la scheda cresce dal mini player fino
 * allo spazio sopra la barra di navigazione, il contenuto compare mentre cresce, e si richiude
 * trascinando verso il basso (o con indietro). [expansion] va da 0 (mini player) a 1 (lettore aperto).
 */
class PlayerSheetState(initial: Float = 0f) {
    val expansion = Animatable(initial)

    /** Posizione del mini player e dell'inizio della barra di navigazione, nello schermo. */
    var miniBounds by mutableStateOf<Rect?>(null)
    var navTop by mutableFloatStateOf(0f)
    var origin by mutableStateOf(Offset.Zero)
    var dragging by mutableStateOf(false)
}

private val OpenSpec = spring<Float>(dampingRatio = 0.88f, stiffness = 110f, visibilityThreshold = 0.001f)
private val CloseSpec = spring<Float>(dampingRatio = 0.92f, stiffness = 95f, visibilityThreshold = 0.001f)

/** Rettangolo arrotondato che va da [left] a [right] e da 0 a [height] (il livello è già spostato in alto). */
private data class MorphShape(val left: Float, val right: Float, val height: Float, val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val rect = Rect(left, 0f, right, height.coerceAtLeast(0f))
        val r = radius.coerceIn(0f, rect.minDimension / 2f)
        return if (r <= 0.5f) Outline.Rectangle(rect) else Outline.Rounded(RoundRect(rect, CornerRadius(r, r)))
    }
}

@Composable
fun PlayerSheetLayer(env: AppEnv, sheet: PlayerSheetState) {
    val hasTrack = env.engine.currentTrack != null
    val expanded = env.tab == Tab.NowPlaying && hasTrack
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current

    LaunchedEffect(expanded) {
        if (sheet.dragging) return@LaunchedEffect
        sheet.expansion.animateTo(if (expanded) 1f else 0f, if (expanded) OpenSpec else CloseSpec)
    }

    val shown by remember { derivedStateOf { sheet.expansion.value > 0f } }
    if (!hasTrack || !(shown || expanded)) return

    var hostH by remember { mutableFloatStateOf(0f) }
    val miniColor = deepAccent(cs)
    val surface = cs.surface
    val startRadiusPx = with(density) { 28.dp.toPx() }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { hostH = it.height.toFloat() }
            .graphicsLayer {
                val p = sheet.expansion.value.coerceIn(0f, 1f)
                val endBottom = if (sheet.navTop > 0f) sheet.navTop - sheet.origin.y else size.height
                val start = sheet.miniBounds?.translate(-sheet.origin) ?: Rect(0f, endBottom, size.width, endBottom)
                val left = lerp(start.left, 0f, p)
                val right = lerp(start.right, size.width, p)
                val top = lerp(start.top, 0f, p)
                val bottom = lerp(start.bottom, endBottom, p)
                translationY = top
                // prima di partire il livello è già pronto ma non si vede sopra il mini player
                alpha = if (p == 0f) 0f else 1f
                shape = MorphShape(left, right, bottom - top, lerp(minOf(start.height / 2f, startRadiusPx), 0f, p))
                clip = true
            }
            .drawBehind { drawRect(lerpColor(miniColor, surface, (sheet.expansion.value * 2f).coerceIn(0f, 1f))) }
            .draggable(
                orientation = Orientation.Vertical,
                enabled = expanded,
                state = rememberDraggableState { d ->
                    val range = (hostH * 0.7f).coerceAtLeast(1f)
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        sheet.expansion.snapTo((sheet.expansion.value - d / range).coerceIn(0f, 1f))
                    }
                },
                onDragStarted = { sheet.dragging = true },
                onDragStopped = { v ->
                    sheet.dragging = false
                    val p = sheet.expansion.value
                    if (v > 1400f || (p < 0.65f && v > -800f)) {
                        env.tab = env.tabBeforePlayer.takeIf { it != Tab.NowPlaying } ?: Tab.Library
                    } else {
                        scope.launch { sheet.expansion.animateTo(1f, OpenSpec) }
                    }
                },
            )
    ) {
        // il lettore ha l'altezza finale (sopra la barra), non quella dello schermo
        val h = if (sheet.navTop > 0f) with(density) { (sheet.navTop - sheet.origin.y).toDp() } else null
        Box(
            (if (h != null) Modifier.fillMaxWidth().height(h) else Modifier.fillMaxSize())
                .graphicsLayer {
                    // invisibile fino al 25% dell'apertura, pieno al 100%, con un piccolo assestamento verso l'alto
                    val a = ((sheet.expansion.value - 0.25f) / 0.75f).coerceIn(0f, 1f)
                    alpha = a
                    translationY = lerp(24.dp.toPx(), 0f, a)
                }
        ) {
            NowPlayingPage(env)
        }
    }
}
