package com.mi.onextbox.ui.onboarding

import android.os.SystemClock
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.BreakIterator
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

private fun welcomeGlyphScaleProgress(fraction: Float): Float {
    val frequency = 2.0 * Math.PI / 0.55
    val damping = 0.6
    val dampedFrequency = frequency * sqrt(1.0 - damping * damping)
    fun response(time: Double) = 1.0 - exp(-damping * frequency * time) *
        (cos(dampedFrequency * time) + damping * frequency / dampedFrequency * sin(dampedFrequency * time))
    return (response(fraction.toDouble()) / response(1.0)).toFloat()
}

internal fun welcomeCharacterEnds(text: String): List<Int> {
    val iterator = BreakIterator.getCharacterInstance().apply { setText(text) }
    return buildList {
        iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            add(end)
            end = iterator.next()
        }
    }
}

@Composable
internal fun WelcomeTypewriter(
    text: String,
    fullText: String,
    gradientStart: Int,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val births = remember(fullText) { mutableMapOf<Int, Long>() }
    val ends = remember(text) { welcomeCharacterEnds(text) }
    LaunchedEffect(text, fullText) {
        births.keys.retainAll(ends.toSet())
        val now = SystemClock.uptimeMillis()
        ends.forEach { births.putIfAbsent(it, now) }
    }
    var frame by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    val started = remember(active) { SystemClock.uptimeMillis() }
    LaunchedEffect(active, text) {
        if (active) while (true) {
            withFrameNanos { frame = SystemClock.uptimeMillis() }
        }
    }
    val ink = Color(0xFF17181A)
    val style = TextStyle(color = ink, fontSize = 40.sp, lineHeight = 50.sp, fontWeight = FontWeight.SemiBold)
    BoxWithConstraints(modifier.semantics { contentDescription = text }) {
        val width = constraints.maxWidth
        val plain = measurer.measure(fullText, style, constraints = Constraints(maxWidth = width))
        val gradientBounds = if (gradientStart in fullText.indices) {
            (gradientStart until fullText.length).filter { fullText[it] != '\n' }
                .map { plain.getBoundingBox(it) }
        } else emptyList()
        val brush = Brush.horizontalGradient(
            listOf(Color(0xFF00A1FF), Color(0xFF4964FD), Color(0xFFC851E6), Color(0xFFD64878)),
            startX = gradientBounds.minOfOrNull { it.left } ?: 0f,
            endX = (gradientBounds.maxOfOrNull { it.right } ?: width.toFloat()).coerceAtLeast(1f),
        )
        val annotated = buildAnnotatedString {
            append(fullText)
            if (gradientStart in fullText.indices) addStyle(SpanStyle(brush = brush), gradientStart, length)
        }
        val layout = measurer.measure(annotated, style, constraints = Constraints(maxWidth = width))
        Canvas(Modifier.fillMaxWidth().height(with(density) { layout.size.height.toDp() })) {
            var start = 0
            ends.forEach { end ->
                if (end <= fullText.length && fullText.substring(start, end) != "\n") {
                    val boxes = (start until end).map { layout.getBoundingBox(it) }
                    val left = boxes.minOf { it.left }
                    val right = boxes.maxOf { it.right }
                    val top = boxes.minOf { it.top }
                    val bottom = boxes.maxOf { it.bottom }
                    val elapsed = (frame - (births[end] ?: frame)).coerceAtLeast(0)
                    val alpha = (elapsed / 400f).coerceIn(0f, 1f)
                    val progress = welcomeGlyphScaleProgress((elapsed / 584f).coerceIn(0f, 1f))
                    scale(0.8f + 0.2f * progress, pivot = Offset((left + right) / 2, (top + bottom) / 2)) {
                        clipRect(left, top, right, bottom) { drawText(layout, alpha = alpha) }
                    }
                }
                start = end
            }
            if (active) {
                val caret = layout.getCursorRect(text.length.coerceAtMost(fullText.length))
                val elapsed = (frame - started).coerceAtLeast(0)
                val alpha = if (elapsed < 333) elapsed / 333f else {
                    val phase = ((elapsed - 333) % 900) / 450f
                    val value = if (phase <= 1f) 1f - phase else phase - 1f
                    CubicBezierEasing(0.17f, 0f, 0.83f, 1f).transform(value)
                }
                val x = caret.left + 4.dp.toPx()
                drawLine(ink.copy(alpha = alpha), Offset(x, caret.top), Offset(x, caret.bottom), 2.dp.toPx())
            }
        }
    }
}
