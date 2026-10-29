package com.marco.mlplayer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------- sfondo e frontali

/** Sfondo dietro i frontali (tinta unita o venature del legno). */
fun Modifier.finishBackground(f: Finish): Modifier =
    if (f.wood) this.background(
        Brush.linearGradient(
            f.bg, start = Offset(0f, 0f), end = Offset(70f, 6f), tileMode = TileMode.Mirror
        )
    ) else this.background(Brush.verticalGradient(f.bg))

private val brushNoise = FloatArray(97) { i -> (((i * 7919) % 97) / 97f) }

/** Frontale metallico: sfumatura, spazzolatura orizzontale (o rilievo anni '90) e bordo. */
fun Modifier.plate(shape: Shape = RoundedCornerShape(10.dp), elevation: Dp = 6.dp): Modifier = composed {
    val f = Hifi.finish
    val brushed = Hifi.brushed
    this
        .shadow(elevation, shape)
        .clip(shape)
        .background(Brush.verticalGradient(listOf(f.plateTop, f.plateBottom)))
        .drawBehind {
            if (f.bevel) {
                val s = 2.dp.toPx()
                drawLine(Color(0x55FFFFFF), Offset(0f, s / 2), Offset(size.width, s / 2), s)
                drawLine(Color(0x55FFFFFF), Offset(s / 2, 0f), Offset(s / 2, size.height), s)
                drawLine(Color(0x88000000), Offset(0f, size.height - s / 2), Offset(size.width, size.height - s / 2), s)
                drawLine(Color(0x88000000), Offset(size.width - s / 2, 0f), Offset(size.width - s / 2, size.height), s)
            } else if (brushed && (f.brushLight.alpha > 0f || f.brushDark.alpha > 0f)) {
                val step = 1.5.dp.toPx()
                var y = 0f
                var i = 0
                while (y < size.height) {
                    val n = brushNoise[i % brushNoise.size]
                    val c = if (i % 2 == 0) f.brushLight else f.brushDark
                    drawLine(c.copy(alpha = c.alpha * (0.4f + n)), Offset(0f, y), Offset(size.width, y), 1f)
                    y += step; i++
                }
            }
        }
        .border(1.dp, f.plateBorder, shape)
}

/** Finestra del display (vetro scuro incassato nel frontale). */
fun Modifier.displayWindow(shape: Shape = RoundedCornerShape(6.dp)): Modifier = composed {
    val f = Hifi.finish
    this
        .clip(shape)
        .background(f.vfdBg)
        .border(1.dp, Color(0x99000000), shape)
}

// ---------------------------------------------------------------- testi

@Composable
fun Engraved(
    text: String,
    size: TextUnit = 11.sp,
    dim: Boolean = false,
    bold: Boolean = true,
    spacing: TextUnit = 2.sp,
    color: Color? = null,
    modifier: Modifier = Modifier,
    align: TextAlign? = null,
) {
    val f = Hifi.finish
    Text(
        text,
        color = color ?: if (dim) f.engraveDim else f.engrave,
        fontSize = size,
        fontFamily = if (bold) Hifi.CondensedBold else Hifi.Condensed,
        letterSpacing = spacing,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = align,
        modifier = modifier,
    )
}

/** Testo del display VFD (monospazio con bagliore). */
@Composable
fun Vfd(
    text: String,
    size: TextUnit = 13.sp,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    color: Color? = null,
    family: FontFamily = Hifi.Mono,
    bold: Boolean = false,
) {
    val f = Hifi.finish
    val c = (color ?: f.vfd).copy(alpha = alpha)
    val style = if (Hifi.glow) TextStyle(
        shadow = androidx.compose.ui.graphics.Shadow(c.copy(alpha = 0.7f * Hifi.light), Offset.Zero, 10f)
    ) else TextStyle.Default
    Text(
        text, color = c, fontSize = size, fontFamily = family, maxLines = 1,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        overflow = TextOverflow.Ellipsis, style = style, modifier = modifier,
    )
}

// ---------------------------------------------------------------- spie e viti

@Composable
fun Lamp(text: String, on: Boolean, color: Color = Hifi.finish.led) {
    val f = Hifi.finish
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(7.dp)) {
            if (on) drawCircle(color.copy(alpha = 0.35f * Hifi.light), radius = size.minDimension)
            drawCircle(if (on) color else Color(0x55000000), radius = size.minDimension / 2f)
        }
        Spacer(Modifier.width(5.dp))
        Engraved(text, 10.sp, spacing = 1.5.sp, color = f.engrave)
    }
}

@Composable
fun Screw(modifier: Modifier = Modifier) {
    val f = Hifi.finish
    Canvas(modifier.size(9.dp)) {
        drawCircle(Brush.sweepGradient(listOf(f.knobDark, f.knobLight, f.knobDark, f.knobLight, f.knobDark)))
        drawCircle(Color(0x66000000), style = Stroke(1f))
        val r = size.minDimension / 2f * 0.8f
        val a = 0.6f
        drawLine(
            Color(0x99000000),
            Offset(center.x - cos(a) * r, center.y - sin(a) * r),
            Offset(center.x + cos(a) * r, center.y + sin(a) * r), 1.5f
        )
    }
}

// ---------------------------------------------------------------- tasti

@Composable
fun MetalButton(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 56.dp,
    height: Dp = 48.dp,
    active: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val f = Hifi.finish
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val shape = RoundedCornerShape(6.dp)
    val click = rememberUpdatedState(onClick)
    val long = rememberUpdatedState(onLongClick)
    val longHandler: ((Offset) -> Unit)? = if (onLongClick != null) ({ _: Offset -> long.value?.invoke() }) else null
    Box(
        modifier
            .size(width, height)
            .shadow(if (pressed) 1.dp else 4.dp, shape)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    if (pressed) listOf(f.btnBottom, f.btnTop) else listOf(f.btnTop, f.btnBottom)
                )
            )
            .border(1.dp, f.btnBorder, shape)
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        val p = androidx.compose.foundation.interaction.PressInteraction.Press(it)
                        src.emit(p)
                        val ok = tryAwaitRelease()
                        src.emit(
                            if (ok) androidx.compose.foundation.interaction.PressInteraction.Release(p)
                            else androidx.compose.foundation.interaction.PressInteraction.Cancel(p)
                        )
                    },
                    onTap = { click.value() },
                    onLongPress = longHandler,
                )
            },
        contentAlignment = Alignment.Center
    ) {
        content()
        if (active) {
            Canvas(Modifier.align(Alignment.TopEnd).size(14.dp)) {
                drawCircle(f.led.copy(alpha = 0.35f * Hifi.light), 4.5.dp.toPx(), Offset(size.width - 7.dp.toPx() + 3.dp.toPx(), 7.dp.toPx()))
                drawCircle(f.led, 2.5.dp.toPx(), Offset(size.width - 7.dp.toPx() + 3.dp.toPx(), 7.dp.toPx()))
            }
        }
    }
}

// ---------------------------------------------------------------- manopola metallica

/**
 * Manopola metallica zigrinata con tacche. [value] 0..1, si gira trascinando il dito attorno
 * al centro (rotazione relativa, come una manopola vera). [steps] tacche con feedback.
 */
@Composable
fun MetalKnob(
    value: Float,
    size: Dp,
    label: String,
    sub: String,
    modifier: Modifier = Modifier,
    steps: Int = 20,
    onStep: () -> Unit = {},
    onChange: (Float) -> Unit,
) {
    val f = Hifi.finish
    val cur = rememberUpdatedState(value)
    val change = rememberUpdatedState(onChange)
    val step = rememberUpdatedState(onStep)
    var acc by remember { mutableFloatStateOf(0f) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(size + 22.dp)
                .semantics { contentDescription = "$label $sub" }
                .pointerInput(Unit) {
                    var last = 0f
                    detectDragGestures(
                        onDragStart = { o ->
                            last = atan2(o.y - this.size.height / 2f, o.x - this.size.width / 2f)
                            acc = cur.value
                        },
                        onDrag = { c, _ ->
                            c.consume()
                            val a = atan2(c.position.y - this.size.height / 2f, c.position.x - this.size.width / 2f)
                            var d = a - last
                            if (d > PI) d -= (2 * PI).toFloat()
                            if (d < -PI) d += (2 * PI).toFloat()
                            last = a
                            val before = (acc * steps).toInt()
                            acc = (acc + d / (1.5f * PI.toFloat())).coerceIn(0f, 1f)
                            if ((acc * steps).toInt() != before) step.value()
                            change.value(acc)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(size + 22.dp)) {
                val c = center
                val r0 = size.toPx() / 2f + 4.dp.toPx()
                for (i in 0..10) {
                    val ang = ((-225f + 27f * i) * PI / 180f).toFloat()
                    val r1 = r0 + if (i % 5 == 0) 5.dp.toPx() else 3.dp.toPx()
                    drawLine(
                        f.engraveDim,
                        Offset(c.x + cos(ang) * r0, c.y + sin(ang) * r0),
                        Offset(c.x + cos(ang) * r1, c.y + sin(ang) * r1), 1.2.dp.toPx()
                    )
                }
                val br = size.toPx() / 2f
                drawCircle(Color(0x66000000), br + 2.dp.toPx(), c + Offset(0f, 3.dp.toPx()))
                drawCircle(
                    Brush.sweepGradient(
                        listOf(f.knobDark, f.knobLight, f.knobDark, f.knobLight, f.knobDark, f.knobLight, f.knobDark),
                        c
                    ), br, c
                )
                drawCircle(Color(0x55000000), br, c, style = Stroke(1f))
                val cap = br * 0.72f
                drawCircle(Brush.radialGradient(listOf(f.capLight, f.capDark), c - Offset(cap * 0.2f, cap * 0.3f), cap * 1.3f), cap, c)
            }
            val deg = -135f + 270f * value.coerceIn(0f, 1f)
            Canvas(Modifier.size(size).rotate(deg)) {
                val cap = this.size.minDimension / 2f * 0.72f
                val top = center.y - cap + 5.dp.toPx()
                val len = maxOf(8.dp.toPx(), cap * 0.32f)
                if (Hifi.glow) drawLine(f.knobMark.copy(alpha = 0.35f * Hifi.light), Offset(center.x, top), Offset(center.x, top + len), 6.dp.toPx(), StrokeCap.Round)
                drawLine(f.knobMark, Offset(center.x, top), Offset(center.x, top + len), 3.dp.toPx(), StrokeCap.Round)
            }
        }
        Engraved(label, 11.sp)
        Engraved(sub, 10.sp, dim = true, bold = false, spacing = 0.5.sp)
    }
}

// ---------------------------------------------------------------- barra a segmenti

@Composable
fun SegmentBar(fraction: Float, modifier: Modifier = Modifier, segments: Int = 40, onSeek: ((Float) -> Unit)? = null) {
    val f = Hifi.finish
    val seek = rememberUpdatedState(onSeek)
    var m = modifier
    if (onSeek != null) {
        m = m
            .pointerInput(Unit) {
                detectTapGestures { o -> seek.value?.invoke((o.x / this.size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectDragGestures(onDragEnd = {}) { c, _ ->
                    c.consume()
                    seek.value?.invoke((c.position.x / this.size.width).coerceIn(0f, 1f))
                }
            }
    }
    Canvas(m) {
        val gap = 2.dp.toPx()
        val w = (size.width - gap * (segments - 1)) / segments
        val on = (fraction.coerceIn(0f, 1f) * segments)
        for (i in 0 until segments) {
            val x = i * (w + gap)
            val lit = i < on
            if (lit && Hifi.glow) drawRect(f.vfd.copy(alpha = 0.25f * Hifi.light), Offset(x - 1f, -1f), androidx.compose.ui.geometry.Size(w + 2f, size.height + 2f))
            drawRect(
                if (lit) f.vfd else f.vfdDim.copy(alpha = 0.45f),
                Offset(x, 0f), androidx.compose.ui.geometry.Size(w, size.height)
            )
        }
    }
}

/** Interruttore a levetta del frontale. */
@Composable
fun HifiSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val f = Hifi.finish
    Box(
        Modifier
            .size(42.dp, 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (checked) f.vfdDim else Color(0x33000000))
            .border(1.dp, if (checked) f.vfd else f.btnBorder, RoundedCornerShape(12.dp))
            .clickable { onChange(!checked) },
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            Modifier
                .padding(horizontal = 3.dp)
                .size(16.dp)
                .clip(CircleShape)
                .background(if (checked) f.vfd else f.engraveDim)
        )
    }
}


@Composable
fun HSpace(w: Dp) = Spacer(Modifier.width(w))

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))
