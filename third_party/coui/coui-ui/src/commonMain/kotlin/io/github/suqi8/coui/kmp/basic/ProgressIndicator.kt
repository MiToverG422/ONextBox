// Copyright 2025, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.suqi8.coui.kmp.basic

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.math.abs

/**
 * A [LinearProgressIndicator] with COUI style.
 *
 * @param modifier The modifier to be applied to the indicator.
 * @param progress The current progress value between 0.0f and 1.0f, or null for indeterminate state.
 * @param colors The colors used for the indicator.
 * @param height The height of the indicator.
 */
@Composable
fun LinearProgressIndicator(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    colors: ProgressIndicatorColors = ProgressIndicatorDefaults.progressIndicatorColors(),
    height: Dp = ProgressIndicatorDefaults.DefaultLinearProgressIndicatorHeight,
) {
    val currentBackgroundColor = colors.backgroundColor()
    val currentForegroundColor = colors.foregroundColor(true)

    if (progress == null) {
        val transition = rememberInfiniteTransition()
        val animatedValue by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1250, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        )

        Canvas(
            modifier = modifier
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }
                .fillMaxWidth()
                .height(height),
        ) {
            drawRoundRect(
                color = currentBackgroundColor,
                size = size,
                cornerRadius = CornerRadius(size.height / 2),
            )

            for (i in 0 until 3) {
                drawIndeterminateSegment(animatedValue, i, currentForegroundColor)
            }
        }
    } else {
        val progressValue = progress.coerceIn(0f, 1f)

        Canvas(
            modifier = modifier
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progressValue, 0f..1f) }
                .fillMaxWidth()
                .height(height),
        ) {
            // COUIHorizontalProgressBar.onDraw: the fill is a full-height rectangle with a
            // square leading edge, intersected with the capsule track path (Path.Op.INTERSECT),
            // so only the track's own corners round the fill.
            val cornerRadius = size.height / 2
            val trackPath = Path().apply {
                addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(cornerRadius)))
            }

            clipPath(trackPath) {
                drawRect(
                    color = currentBackgroundColor,
                    size = size,
                )
                drawRect(
                    color = currentForegroundColor,
                    size = Size(size.width * progressValue, size.height),
                )
            }
        }
    }
}

/**
 * A [CircularProgressIndicator] with COUI style.
 *
 * @param modifier The modifier to be applied to the indicator.
 * @param progress The current progress value between 0.0f and 1.0f, or null for indeterminate state.
 * @param colors The colors used for the indicator.
 * @param strokeWidth The width of the circular stroke.
 * @param size The size (diameter) of the circular indicator.
 */
@Composable
fun CircularProgressIndicator(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    colors: ProgressIndicatorColors = ProgressIndicatorDefaults.circularProgressIndicatorColors(),
    strokeWidth: Dp = ProgressIndicatorDefaults.DefaultCircularProgressIndicatorStrokeWidth,
    size: Dp = ProgressIndicatorDefaults.DefaultCircularProgressIndicatorSize,
) {
    val currentBackgroundColor = colors.backgroundColor()
    val currentForegroundColor = colors.foregroundColor(true)

    if (progress == null) {
        val transition = rememberInfiniteTransition()

        // COUILoadingView.onDraw: the arc angle is derived from a wall clock as
        // (uptimeMillis % 1000) * 360 / 1000, i.e. one full turn per 1000 ms, linear.
        val cycle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        )

        Canvas(
            modifier = modifier
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }
                .size(size),
        ) {
            val strokeWidthPx = strokeWidth.toPx()
            val radius = (size.toPx() - strokeWidthPx) / 2
            val center = Offset(size.toPx() / 2, size.toPx() / 2)
            // COUILoadingView: drawArc(rect, cycle - 30, (2 - |180 - cycle| / 180) * 60, ...)
            // on a canvas pre-rotated by -90 degrees; the sweep pulses 60 -> 120 -> 60
            // within a single rotation.
            val sweepAngle = (2f - abs(180f - cycle) / 180f) * 60f
            val startAngle = cycle - 30f - 90f

            withSrcCompositingLayer {
                drawCircularBackground(currentBackgroundColor, radius, center, strokeWidthPx)

                drawArc(
                    color = currentForegroundColor,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(strokeWidthPx / 2, strokeWidthPx / 2),
                    size = Size(2 * radius, 2 * radius),
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
                    blendMode = BlendMode.Src,
                )
            }
        }
    } else {
        val progressValue = progress.coerceIn(0f, 1f)

        Canvas(
            modifier = modifier
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progressValue, 0f..1f) }
                .size(size),
        ) {
            val strokeWidthPx = strokeWidth.toPx()
            val radius = (size.toPx() - strokeWidthPx) / 2
            val center = Offset(size.toPx() / 2, size.toPx() / 2)

            // COUICircularProgressDrawable.drawProgress: sweep = max(1e-4, progress * 360 / max).
            val sweepAngle = maxOf(1.0E-4f, progressValue * 360f)

            withSrcCompositingLayer {
                drawCircularBackground(currentBackgroundColor, radius, center, strokeWidthPx)

                drawArc(
                    color = currentForegroundColor,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(strokeWidthPx / 2, strokeWidthPx / 2),
                    size = Size(2 * radius, 2 * radius),
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
                    blendMode = BlendMode.Src,
                )
            }
        }
    }
}

/**
 * A [InfiniteProgressIndicator] with COUI style.
 *
 * A thin wrapper around [RotatingProgressIndicator] that keeps the accent tint and the
 * `coui_loading_view_*` size tier, for indeterminate waits inside dialogs and inline content.
 *
 * @param modifier The modifier to be applied to the indicator.
 * @param color The color of the arc.
 * @param size The size (the square view box) of the indicator.
 * @param strokeWidth The width of the arc stroke.
 */
@Composable
@NonRestartableComposable
fun InfiniteProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = COUITheme.colorScheme.primary,
    size: Dp = ProgressIndicatorDefaults.DefaultInfiniteProgressIndicatorSize,
    strokeWidth: Dp = ProgressIndicatorDefaults.DefaultInfiniteProgressIndicatorStrokeWidth,
) {
    RotatingProgressIndicator(
        modifier = modifier,
        color = color,
        size = size,
        ringDiameter = size - strokeWidth,
        strokeWidth = strokeWidth,
    )
}

/**
 * A [RotatingProgressIndicator] with COUI style.
 *
 * ColorOS's indeterminate spinner: a single bare stroked arc with flat caps and no background ring,
 * whose sweep pulses while the whole arc spins. Ported from the Lottie asset
 * `coui_rotating_loading.json`, which `Theme.COUI` binds to `couiRotatingSpinnerJsonName` and
 * `COUILottieLoadingView` renders (the same animation also ships as the `fb_loading.xml`
 * AnimatedVectorDrawable). The asset is one ellipse driven by one trim path, so it is reproduced
 * here with a single [drawArc] and needs no Lottie runtime.
 *
 * @param modifier The modifier to be applied to the indicator.
 * @param color The color of the arc.
 * @param size The size (the square view box) of the indicator.
 * @param ringDiameter The diameter of the arc's stroke centerline, centered inside [size].
 * @param strokeWidth The width of the arc stroke.
 */
@Composable
fun RotatingProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = COUITheme.colorScheme.onSurfaceContainer,
    size: Dp = ProgressIndicatorDefaults.DefaultRotatingProgressIndicatorSize,
    ringDiameter: Dp = ProgressIndicatorDefaults.DefaultRotatingProgressIndicatorRingDiameter,
    strokeWidth: Dp = ProgressIndicatorDefaults.DefaultRotatingProgressIndicatorStrokeWidth,
) {
    val transition = rememberInfiniteTransition()
    // coui_rotating_loading.json: fr = 60, ip = 0, op = 76, so frames 0..75 at 60 fps play in
    // 1250 ms and then repeat forever.
    val cycle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(RotatingSpinnerCycleMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
    )

    Canvas(
        modifier = modifier
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }
            .size(size),
    ) {
        val strokeWidthPx = strokeWidth.toPx()
        val diameterPx = ringDiameter.toPx()
        val inset = (this.size.minDimension - diameterPx) / 2

        // `Trim Paths 1` pins `end` at 100% and animates `start`, so the arc's trailing edge is
        // fixed while its leading edge swings; both the sweep and the start angle derive from it.
        val trimStart = rotatingSpinnerTrimStart(cycle)

        drawArc(
            color = color,
            startAngle = rotatingSpinnerStartAngle(cycle, trimStart),
            sweepAngle = 360f - trimStart,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(diameterPx, diameterPx),
            // Stroke 1 declares lc = 1, i.e. a butt/flat cap (not the round cap the other COUI
            // progress indicators use), and lj = 1 (miter, irrelevant for a single open arc).
            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Butt),
        )
    }
}

/** The cycle duration of [RotatingProgressIndicator] (coui_rotating_loading.json: 76 frames at 60 fps). */
private val RotatingSpinnerCycleMillis = 1250

/** The normalized time at which the arc is shortest (coui_rotating_loading.json keyframe t = 37 of 75). */
private val RotatingSpinnerBreakpoint = 37f / 75f

/** Lottie's standard keyframe easing, used by every keyframe pair except the trim-start ones. */
private val RotatingSpinnerStandardEasing = CubicBezierEasing(0.167f, 0.167f, 0.833f, 0.833f)

/** The trim-start easing while the arc shortens (keyframe 0 -> 37). */
private val RotatingSpinnerTrimGrowEasing = CubicBezierEasing(0.167f, 0.167f, 0.833f, 1f)

/** The trim-start easing while the arc lengthens again (keyframe 37 -> 75). */
private val RotatingSpinnerTrimShrinkEasing = CubicBezierEasing(0.167f, 0f, 0.833f, 0.833f)

/** `Trim Paths 1` start at keyframes t = 0 and t = 75, in degrees of arc. */
private val TrimStartMin = 24f / 100f * 360f

/** `Trim Paths 1` start at keyframe t = 37, where the arc is shortest, in degrees of arc. */
private val TrimStartMax = 86f / 100f * 360f

/** `Trim Paths 1` offset at keyframes t = 0 / 37 / 75. */
private val TrimOffsetStart = -90f
private val TrimOffsetMid = -41f
private val TrimOffsetEnd = 270f

/**
 * The eased `Trim Paths 1` start value at normalized time [cycle], in degrees.
 *
 * `start` animates 24% -> 86% -> 24% while `end` stays pinned at 100%, so the visible arc runs
 * 76% -> 14% -> 76% of the circle, i.e. 273.6 -> 50.4 -> 273.6 degrees.
 */
private fun rotatingSpinnerTrimStart(cycle: Float): Float = if (cycle < RotatingSpinnerBreakpoint) {
    val fraction = RotatingSpinnerTrimGrowEasing.transform(cycle / RotatingSpinnerBreakpoint)
    lerp(TrimStartMin, TrimStartMax, fraction)
} else {
    val fraction = RotatingSpinnerTrimShrinkEasing.transform(
        (cycle - RotatingSpinnerBreakpoint) / (1f - RotatingSpinnerBreakpoint),
    )
    lerp(TrimStartMax, TrimStartMin, fraction)
}

/**
 * The start angle of [RotatingProgressIndicator]'s arc at normalized time [cycle], given the already
 * eased [trimStart].
 *
 * Three rotations compose here. The layer's own rotation runs 90 -> 450 degrees over the cycle while
 * the shape group carries a constant -90 degree transform, which together reduce to `360 * cycle`.
 * The trim path's offset independently runs -90 -> -41 -> 270 degrees, so the arc advances 720
 * degrees per cycle. Finally the arc is drawn from its leading edge, which sits [trimStart] into the
 * path, and Compose measures angles from 3 o'clock while the ellipse path starts at 12 o'clock.
 *
 * The layer's `s: [-100, -100, 100]` scale is a 180 degree point reflection about the center; on a
 * centered circular arc that is a constant phase shift of an already continuously spinning
 * animation, so it is not applied.
 */
private fun rotatingSpinnerStartAngle(cycle: Float, trimStart: Float): Float {
    val trimOffset = if (cycle < RotatingSpinnerBreakpoint) {
        val fraction = RotatingSpinnerStandardEasing.transform(cycle / RotatingSpinnerBreakpoint)
        lerp(TrimOffsetStart, TrimOffsetMid, fraction)
    } else {
        val fraction = RotatingSpinnerStandardEasing.transform(
            (cycle - RotatingSpinnerBreakpoint) / (1f - RotatingSpinnerBreakpoint),
        )
        lerp(TrimOffsetMid, TrimOffsetEnd, fraction)
    }
    return -90f + 360f * cycle + trimOffset + trimStart
}

/**
 * Draws a single animated segment for the indeterminate linear progress indicator.
 */
private fun DrawScope.drawIndeterminateSegment(
    animatedValue: Float,
    segmentIndex: Int,
    color: Color,
) {
    val position = animatedValue - segmentIndex * (0.45f + 0.55f)
    val adjustedPos = (position % 1f + 1f) % 1f
    val cornerRadius = CornerRadius(size.height / 2)

    if (adjustedPos < 1f - 0.45f) {
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * adjustedPos, 0f),
            size = Size(size.width * 0.45f, size.height),
            cornerRadius = cornerRadius,
        )
    } else {
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * adjustedPos, 0f),
            size = Size(size.width * (1f - adjustedPos), size.height),
            cornerRadius = cornerRadius,
        )
        val remainingWidth = adjustedPos + 0.45f - 1f
        if (remainingWidth > 0) {
            drawRoundRect(
                color = color,
                size = Size(size.width * remainingWidth, size.height),
                cornerRadius = cornerRadius,
            )
        }
    }
}

/**
 * Draws a circular background ring used by [CircularProgressIndicator].
 */
private fun DrawScope.drawCircularBackground(
    color: Color,
    radius: Float,
    center: Offset,
    strokeWidthPx: Float,
) {
    drawCircle(
        color = color,
        radius = radius,
        center = center,
        style = Stroke(width = strokeWidthPx),
    )
}

/**
 * Runs [block] inside an offscreen layer so the progress arc drawn with [BlendMode.Src]
 * replaces the track pixels underneath it instead of stacking on top of them.
 * COUICircularProgressDrawable does the same with saveLayerAlpha + PorterDuff.Mode.SRC,
 * which matters because both track and progress colors are translucent.
 */
private inline fun DrawScope.withSrcCompositingLayer(block: DrawScope.() -> Unit) {
    val canvas = drawContext.canvas
    canvas.saveLayer(Rect(Offset.Zero, size), Paint())
    block()
    canvas.restore()
}

object ProgressIndicatorDefaults {
    /** The default height of [LinearProgressIndicator] (COUI coui_loading_dialog_progress_height). */
    val DefaultLinearProgressIndicatorHeight = 4.dp

    /** The default stroke width of [CircularProgressIndicator] (COUI coui_circular_progress_medium_stroke_width). */
    val DefaultCircularProgressIndicatorStrokeWidth = 3.dp

    /** The default size of [CircularProgressIndicator] (COUI coui_circular_progress_medium_length). */
    val DefaultCircularProgressIndicatorSize = 30.dp

    /** The stroke width of the large [CircularProgressIndicator] tier (COUI coui_circular_progress_large_stroke_width). */
    val LargeCircularProgressIndicatorStrokeWidth = 5.dp

    /** The size of the large [CircularProgressIndicator] tier (COUI coui_circular_progress_large_length). */
    val LargeCircularProgressIndicatorSize = 40.dp

    /** The default stroke width of [InfiniteProgressIndicator] (COUI coui_circle_loading_medium_strokewidth). */
    val DefaultInfiniteProgressIndicatorStrokeWidth = 2.67.dp

    /** The default size of [InfiniteProgressIndicator] (COUI coui_loading_view_default_length). */
    val DefaultInfiniteProgressIndicatorSize = 18.dp

    /** The stroke width of the large [InfiniteProgressIndicator] tier (COUI coui_circle_loading_large_strokewidth). */
    val LargeInfiniteProgressIndicatorStrokeWidth = 3.33.dp

    /** The size of the large [InfiniteProgressIndicator] tier (COUI coui_loading_view_large_width/height). */
    val LargeInfiniteProgressIndicatorSize = 26.dp

    /**
     * The default size of [RotatingProgressIndicator]
     * (COUI coui_lottie_loading_view_large_width/height, the tier COUILottieLoadingView defaults to).
     */
    val DefaultRotatingProgressIndicatorSize = 26.dp

    /**
     * The default ring diameter of [RotatingProgressIndicator].
     * coui_rotating_loading.json: a 26-unit ellipse at 300% group scale is 78 units across on an
     * 84-unit comp, so 78 / 84 * 26.dp = 24.14.dp.
     */
    val DefaultRotatingProgressIndicatorRingDiameter = 24.14.dp

    /**
     * The default stroke width of [RotatingProgressIndicator].
     * coui_rotating_loading.json: a 2-unit stroke at 300% group scale is 6 units on an 84-unit comp,
     * so 6 / 84 * 26.dp = 1.857.dp.
     */
    val DefaultRotatingProgressIndicatorStrokeWidth = 1.857.dp

    /**
     * The size of the small [RotatingProgressIndicator] tier
     * (COUI coui_lottie_loading_view_small_width/height).
     */
    val SmallRotatingProgressIndicatorSize = 16.dp

    /**
     * The ring diameter of the small [RotatingProgressIndicator] tier.
     * coui_lottie_loading_small.json: a 14-unit ellipse at 300% group scale is 42 units across on a
     * 53-unit comp, so 42 / 53 * 16.dp = 12.68.dp.
     */
    val SmallRotatingProgressIndicatorRingDiameter = 12.68.dp

    /**
     * The stroke width of the small [RotatingProgressIndicator] tier.
     * coui_lottie_loading_small.json: a 2-unit stroke at 300% group scale is 6 units on a 53-unit
     * comp, so 6 / 53 * 16.dp = 1.811.dp.
     */
    val SmallRotatingProgressIndicatorStrokeWidth = 1.811.dp

    /**
     * The largest supported size of [RotatingProgressIndicator]
     * (COUI coui_loading_max_large_width/height; COUICompProgressIndicator warns above it).
     */
    val MaxRotatingProgressIndicatorSize = 40.dp

    /**
     * The default [ProgressIndicatorColors] used by [LinearProgressIndicator]
     * (COUIProgressHorizontal style: progress = couiColorPrimary, track = couiColorDivider).
     */
    @Composable
    fun progressIndicatorColors(
        foregroundColor: Color = COUITheme.colorScheme.primary,
        disabledForegroundColor: Color = COUITheme.colorScheme.disabledPrimarySlider,
        backgroundColor: Color = COUITheme.colorScheme.dividerLine,
    ): ProgressIndicatorColors = remember(foregroundColor, disabledForegroundColor, backgroundColor) {
        ProgressIndicatorColors(
            foregroundColor = foregroundColor,
            disabledForegroundColor = disabledForegroundColor,
            backgroundColor = backgroundColor,
        )
    }

    /**
     * The default [ProgressIndicatorColors] used by [CircularProgressIndicator]
     * (Widget.COUI.COUICircularProgressBar style: progress = couiColorHintNeutral,
     * track = couiColorDivider).
     */
    @Composable
    fun circularProgressIndicatorColors(
        foregroundColor: Color = COUITheme.colorScheme.onSurfaceContainerHigh,
        disabledForegroundColor: Color = COUITheme.colorScheme.disabledPrimarySlider,
        backgroundColor: Color = COUITheme.colorScheme.dividerLine,
    ): ProgressIndicatorColors = remember(foregroundColor, disabledForegroundColor, backgroundColor) {
        ProgressIndicatorColors(
            foregroundColor = foregroundColor,
            disabledForegroundColor = disabledForegroundColor,
            backgroundColor = backgroundColor,
        )
    }
}

@Immutable
data class ProgressIndicatorColors(
    private val foregroundColor: Color,
    private val disabledForegroundColor: Color,
    private val backgroundColor: Color,
) {
    @Stable
    internal fun foregroundColor(enabled: Boolean): Color = if (enabled) foregroundColor else disabledForegroundColor

    @Stable
    internal fun backgroundColor(): Color = backgroundColor
}
