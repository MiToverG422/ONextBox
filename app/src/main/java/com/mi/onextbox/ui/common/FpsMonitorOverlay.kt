package com.mi.onextbox.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.math.roundToInt

@Composable
fun FpsMonitorOverlay(modifier: Modifier = Modifier) {
    var stats by remember { mutableStateOf(FpsStats.Empty) }
    var refFps by remember { mutableIntStateOf(0) }
    var parentSize by remember { mutableStateOf(IntSize.Zero) }
    var pillSize by remember { mutableStateOf(IntSize.Zero) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val avgTargetColor by remember { derivedStateOf { healthColor(stats.avg, refFps) } }
    val lowTargetColor by remember { derivedStateOf { healthColor(stats.low1, refFps) } }
    val avgColor by animateColorAsState(avgTargetColor, label = "fps_avg_color")
    val lowColor by animateColorAsState(lowTargetColor, label = "fps_low_color")

    fun clampOffset(raw: Offset): Offset {
        val xRange = ((parentSize.width - pillSize.width) / 2f).coerceAtLeast(0f)
        val yMax = (parentSize.height - pillSize.height).toFloat().coerceAtLeast(0f)
        return Offset(
            x = raw.x.coerceIn(-xRange, xRange),
            y = raw.y.coerceIn(0f, yMax),
        )
    }

    Box(
        modifier = modifier.onSizeChanged {
            parentSize = it
            offset = clampOffset(offset)
        },
        contentAlignment = Alignment.TopCenter,
    ) {
        Row(
            modifier = Modifier
                .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                .onSizeChanged {
                    pillSize = it
                    offset = clampOffset(offset)
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        offset = clampOffset(offset + drag)
                    }
                }
                .background(
                    color = COUITheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(FpsPillCorner),
                )
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (stats.avg == 0) {
                Text(
                    text = "--",
                    style = COUITheme.textStyles.body2,
                    color = COUITheme.colorScheme.onSurfaceSecondary,
                )
            } else {
                val secondary = COUITheme.colorScheme.onSurfaceSecondary
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = secondary)) { append("AVG ") }
                        withStyle(SpanStyle(color = avgColor)) { append(stats.avg.toString()) }
                    },
                    style = COUITheme.textStyles.body2,
                )
                Text(
                    text = "|",
                    style = COUITheme.textStyles.body2,
                    color = secondary,
                )
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = secondary)) { append("LOW ") }
                        withStyle(SpanStyle(color = lowColor)) { append(stats.low1.toString()) }
                    },
                    style = COUITheme.textStyles.body2,
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        val samples = ArrayDeque<Long>(WindowFrameCap)
        val avgHistory = ArrayDeque<AvgTick>(RefHistoryCap)
        var sumNs = 0L
        var lastFrameNs = 0L
        var nextRefreshNs = 0L
        while (true) {
            withFrameNanos { frameNs ->
                if (lastFrameNs != 0L) {
                    val delta = frameNs - lastFrameNs
                    if (delta in 1L..IdleThresholdNs) {
                        samples.addLast(delta)
                        sumNs += delta
                        while (sumNs > WindowNs && samples.size > MinSamples) {
                            sumNs -= samples.removeFirst()
                        }
                        while (samples.size > WindowFrameCap) {
                            sumNs -= samples.removeFirst()
                        }
                        if (frameNs >= nextRefreshNs && samples.size >= MinSamples) {
                            val newStats = computeFpsStats(samples, sumNs)
                            stats = newStats
                            avgHistory.addLast(AvgTick(frameNs, newStats.avg))
                            while (avgHistory.size > 1 && frameNs - avgHistory.first().timeNs > RefHistoryNs) {
                                avgHistory.removeFirst()
                            }
                            while (avgHistory.size > RefHistoryCap) {
                                avgHistory.removeFirst()
                            }
                            refFps = avgHistory.maxOf { it.avg }
                            nextRefreshNs = frameNs + RefreshIntervalNs
                        }
                    }
                }
                lastFrameNs = frameNs
            }
        }
    }
}

@Immutable
private data class FpsStats(val avg: Int, val low1: Int) {
    companion object {
        val Empty = FpsStats(0, 0)
    }
}

private data class AvgTick(val timeNs: Long, val avg: Int)

private fun computeFpsStats(samples: ArrayDeque<Long>, sumNs: Long): FpsStats {
    val size = samples.size
    val avgNs = sumNs.toDouble() / size
    val sorted = samples.toLongArray().also { it.sort() }
    val low1Count = (size / 100).coerceAtLeast(1)
    var low1SumNs = 0L
    for (i in size - low1Count until size) low1SumNs += sorted[i]
    return FpsStats(
        avg = (NsPerSecond / avgNs).toInt(),
        low1 = (NsPerSecond / low1AvgNs(low1SumNs, low1Count)).toInt(),
    )
}

private fun low1AvgNs(sumNs: Long, count: Int): Double = sumNs.toDouble() / count

private fun healthColor(value: Int, ref: Int): Color {
    if (ref == 0 || value == 0) return Color.Gray
    val pct = (value.toFloat() / ref).coerceIn(0f, 1f)
    return when {
        pct >= 0.75f -> lerp(HealthYellow, HealthGreen, (pct - 0.75f) / 0.25f)
        pct >= 0.50f -> lerp(HealthRed, HealthYellow, (pct - 0.50f) / 0.25f)
        else -> HealthRed
    }
}

private val HealthGreen = Color(0xFF36D167)
private val HealthYellow = Color(0xFFFFB21D)
private val HealthRed = Color(0xFFFF5B29)
private val FpsPillCorner = 12.dp

private const val NsPerSecond = 1_000_000_000.0
private const val WindowNs = 5L * 1_000_000_000L
private const val WindowFrameCap = 1_200
private const val MinSamples = 30
private const val RefreshIntervalNs = 500_000_000L
private const val IdleThresholdNs = 500_000_000L
private const val RefHistoryNs = 30L * 1_000_000_000L
private const val RefHistoryCap = 120
