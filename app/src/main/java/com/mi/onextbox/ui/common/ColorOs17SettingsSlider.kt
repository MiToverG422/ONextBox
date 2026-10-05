package com.mi.onextbox.ui.common

import android.annotation.SuppressLint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

@SuppressLint("NewApi")
@Composable
fun ColorOs17SettingsSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    require(steps >= 0) { "steps should be >= 0" }
    require(valueRange.start < valueRange.endInclusive) {
        "valueRange start should be less than end"
    }

    val colorScheme = COUITheme.colorScheme
    val isDarkPalette = colorScheme.background.luminance() < 0.5f
    val layoutDirection = LocalLayoutDirection.current
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    val currentValue by rememberUpdatedState(value.coerceIn(valueRange.start, valueRange.endInclusive))
    val scope = rememberCoroutineScope()

    var isDragging by remember { mutableStateOf(false) }
    var isThumbPressed by remember { mutableStateOf(false) }
    var isPointerDown by remember { mutableStateOf(false) }
    val spotLightX = remember { Animatable(0f) }
    val spotLightY = remember { Animatable(0f) }
    val spotLightShader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { RuntimeShader(ColorOs17SpotlightShader) }.getOrNull()
        } else {
            null
        }
    }
    val spotLightBrush = remember(spotLightShader) {
        spotLightShader?.let(::ShaderBrush)
    }

    val animatedValue by animateFloatAsState(
        targetValue = currentValue,
        animationSpec = spring(
            dampingRatio = 0.96f,
            stiffness = if (isDragging) 1755f else 322f,
        ),
        label = "C17SeekBarValue",
    )
    val trackGrowFraction by animateFloatAsState(
        // C17 starts the background-height transition only after the pointer
        // has crossed touchSlop and the seek bar has entered drag state. A
        // stationary press only grows the thumb.
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = tween(
            durationMillis = TouchResizeDurationMillis,
            easing = TouchResizeEasing,
        ),
        label = "C17SeekBarTrackGrow",
    )
    val thumbScale by animateFloatAsState(
        targetValue = if (isThumbPressed) ThumbPressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = ThumbSpringStiffness,
        ),
        label = "C17SeekBarThumbScale",
    )
    val spotLightProgress by animateFloatAsState(
        targetValue = if (enabled && isPointerDown) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            // COUISpotLightEffect uses response=0.15 while glowing and
            // response=0.4 while extinguishing.
            stiffness = if (isPointerDown) {
                SpotLightGlowStiffness
            } else {
                SpotLightExtinguishStiffness
            },
        ),
        label = "C17SeekBarSpotLight",
    )

    val foregroundColor by animateColorAsState(
        targetValue = if (enabled) {
            colorScheme.primary
        } else {
            colorScheme.disabledPrimarySlider
        },
        animationSpec = tween(TouchResizeDurationMillis, easing = TouchResizeEasing),
        label = "C17SeekBarForegroundColor",
    )
    val backgroundColor by animateColorAsState(
        targetValue = if (isDarkPalette) {
            DarkTrackBackgroundColor
        } else {
            LightTrackBackgroundColor
        },
        animationSpec = tween(TouchResizeDurationMillis, easing = TouchResizeEasing),
        label = "C17SeekBarBackgroundColor",
    )
    val thumbColor by animateColorAsState(
        targetValue = if (enabled) {
            colorScheme.onPrimary
        } else {
            colorScheme.disabledOnPrimary
        },
        animationSpec = tween(TouchResizeDurationMillis, easing = TouchResizeEasing),
        label = "C17SeekBarThumbColor",
    )
    val thumbShadowColor = if (isDarkPalette) {
        DarkThumbShadowColor
    } else {
        LightThumbShadowColor
    }

    fun snapValue(fraction: Float): Float {
        val clampedFraction = fraction.coerceIn(0f, 1f)
        if (steps == 0) {
            return valueRange.start +
                (valueRange.endInclusive - valueRange.start) * clampedFraction
        }
        val intervalCount = steps + 1
        val interval = (clampedFraction * intervalCount)
            .roundToInt()
            .coerceIn(0, intervalCount)
        return (
            valueRange.start.toDouble() +
                (valueRange.endInclusive.toDouble() - valueRange.start.toDouble()) *
                interval / intervalCount
            ).toFloat()
    }

    val semanticsModifier = Modifier.semantics {
        progressBarRangeInfo = ProgressBarRangeInfo(
            currentValue,
            valueRange,
            if (steps > 0) steps else 0,
        )
        if (!enabled) {
            disabled()
        } else {
            setProgress { targetValue ->
                currentOnValueChange(targetValue.coerceIn(valueRange.start, valueRange.endInclusive))
                true
            }
        }
    }

    Canvas(
        modifier = modifier
            .height(TouchTargetHeight)
            .then(semanticsModifier)
            .then(
                if (enabled) {
                    Modifier.pointerInput(valueRange, steps, layoutDirection) {
                        val trackHeightPx = TrackHeight.toPx()
                        val capInsetPx = trackHeightPx * TrackEnlargeScale / 2f

                        fun fractionAt(x: Float): Float {
                            val availableWidth = (size.width - capInsetPx * 2f).coerceAtLeast(1f)
                            val visualFraction = (x - capInsetPx) / availableWidth
                            return if (layoutDirection == LayoutDirection.Rtl) {
                                1f - visualFraction
                            } else {
                                visualFraction
                            }
                        }

                        awaitEachGesture {
                            val down = awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial,
                            )
                            val availableWidth = (size.width - capInsetPx * 2f).coerceAtLeast(1f)
                            val currentFraction =
                                (currentValue - valueRange.start) /
                                    (valueRange.endInclusive - valueRange.start)
                            val visualFraction = if (layoutDirection == LayoutDirection.Rtl) {
                                1f - currentFraction
                            } else {
                                currentFraction
                            }
                            val thumbX = capInsetPx + visualFraction * availableWidth
                            scope.launch { spotLightX.snapTo(down.position.x) }
                            scope.launch { spotLightY.snapTo(down.position.y) }
                            isPointerDown = true
                            isThumbPressed = abs(down.position.x - thumbX) <= capInsetPx

                            var moved = false
                            var lastPosition = down.position

                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                if (!change.pressed) {
                                    if (!moved) {
                                        currentOnValueChange(snapValue(fractionAt(change.position.x)))
                                    }
                                    break
                                }

                                lastPosition = change.position
                                scope.launch {
                                    spotLightX.animateTo(
                                        targetValue = lastPosition.x,
                                        animationSpec = spring(
                                            dampingRatio = SpotLightFollowDampingRatio,
                                            stiffness = SpotLightFollowStiffness,
                                        ),
                                    )
                                }
                                scope.launch {
                                    spotLightY.animateTo(
                                        targetValue = lastPosition.y,
                                        animationSpec = spring(
                                            dampingRatio = SpotLightFollowDampingRatio,
                                            stiffness = SpotLightFollowStiffness,
                                        ),
                                    )
                                }
                                if (!moved &&
                                    (lastPosition - down.position).getDistance() > viewConfiguration.touchSlop
                                ) {
                                    moved = true
                                    isDragging = true
                                }

                                if (moved) {
                                    change.consume()
                                    val rawFraction = fractionAt(lastPosition.x)
                                    currentOnValueChange(snapValue(rawFraction))
                                }
                            }

                            isPointerDown = false
                            isDragging = false
                            isThumbPressed = false
                            currentOnValueChangeFinished?.invoke()
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        val trackHeightPx = TrackHeight.toPx()
        val backgroundHeight = trackHeightPx *
            (1f + (TrackEnlargeScale - 1f) * trackGrowFraction)
        val centerY = size.height / 2f
        val capInset = trackHeightPx * TrackEnlargeScale / 2f
        val availableWidth = (size.width - capInset * 2f).coerceAtLeast(0f)
        // C17's finger-tracking path updates the drawn scale directly. Keep
        // the spring for click/release changes, but never put it between a
        // dragging finger and the rendered thumb.
        val renderedValue = if (isDragging) currentValue else animatedValue
        val fraction = (
            (renderedValue - valueRange.start) /
                (valueRange.endInclusive - valueRange.start)
            ).coerceIn(0f, 1f)
        val visualFraction = if (layoutDirection == LayoutDirection.Rtl) 1f - fraction else fraction
        val thumbCenterX = capInset + visualFraction * availableWidth

        val backgroundLeft = capInset - backgroundHeight / 2f
        val backgroundRight = capInset + availableWidth + backgroundHeight / 2f
        drawRoundRect(
            color = backgroundColor,
            topLeft = Offset(backgroundLeft, centerY - backgroundHeight / 2f),
            size = Size(availableWidth + backgroundHeight, backgroundHeight),
            cornerRadius = CornerRadius(backgroundHeight / 2f),
        )

        val halfTrack = trackHeightPx / 2f
        val progressLeft = if (layoutDirection == LayoutDirection.Rtl) {
            thumbCenterX - halfTrack
        } else {
            capInset - halfTrack
        }
        val progressRight = if (layoutDirection == LayoutDirection.Rtl) {
            size.width - capInset + halfTrack
        } else {
            thumbCenterX + halfTrack
        }
        val progressWidth = (progressRight - progressLeft).coerceAtLeast(0f)
        val progressTop = centerY - halfTrack
        val progressSize = Size(progressWidth, trackHeightPx)

        drawRoundRect(
            color = foregroundColor,
            topLeft = Offset(progressLeft, progressTop),
            size = progressSize,
            cornerRadius = CornerRadius(halfTrack),
        )

        val thumbRadius = ThumbRadius.toPx() * thumbScale
        if (enabled) {
            val shadowRadius = thumbRadius + ThumbShadowBlurRadius.toPx()
            val shadowCenter = Offset(thumbCenterX, centerY + ThumbShadowOffsetY.toPx())
            drawCircle(
                brush = Brush.radialGradient(
                    0f to thumbShadowColor,
                    thumbRadius / shadowRadius to thumbShadowColor,
                    1f to thumbShadowColor.copy(alpha = 0f),
                    center = shadowCenter,
                    radius = shadowRadius,
                ),
                radius = shadowRadius,
                center = shadowCenter,
            )
        }
        drawCircle(
            color = thumbColor,
            radius = thumbRadius,
            center = Offset(thumbCenterX, centerY),
        )

        // DrawPhase.OVERLAY from C17's SpotLightEffectRenderer: the complete
        // seek bar is rendered first, then one TYPE_OPAQUE_MEDIUM_1 spotlight
        // is blended over it and clipped to the background track. This remains
        // the final Canvas operation, matching View.draw() after super.draw().
        if (enabled && spotLightProgress > 0f) {
            val radius3d = SpotLightStartRadiusPx +
                (SpotLightEndRadiusPx - SpotLightStartRadiusPx) * spotLightProgress +
                if (!isPointerDown) {
                    SpotLightEndRadiusPx * 2f * (1f - spotLightProgress)
                } else {
                    0f
                }
            val endIntensity = if (isDarkPalette) {
                SpotLightDarkEndIntensity
            } else {
                SpotLightLightEndIntensity
            }
            val intensity = endIntensity * spotLightProgress
            if (spotLightBrush != null && spotLightShader != null) {
                spotLightShader.setFloatUniform("resolution", size.width, size.height)
                spotLightShader.setFloatUniform(
                    "u_lightPosAndHeight",
                    spotLightX.value,
                    spotLightY.value,
                    radius3d,
                    intensity,
                )
                spotLightShader.setFloatUniform(
                    "u_lightColorAndAmbient",
                    1f,
                    1f,
                    1f,
                    SpotLightAmbientStrength,
                )
                spotLightShader.setFloatUniform(
                    "u_lightingParams",
                    SpotLightDiffuseStrength,
                    SpotLightHeightPx,
                    SpotLightNoise,
                    SpotLightDither,
                )
                drawRoundRect(
                    brush = spotLightBrush,
                    topLeft = Offset(backgroundLeft, centerY - backgroundHeight / 2f),
                    size = Size(backgroundRight - backgroundLeft, backgroundHeight),
                    cornerRadius = CornerRadius(backgroundHeight / 2f),
                    blendMode = BlendMode.Overlay,
                )
            } else if (radius3d > SpotLightHeightPx) {
                // Android 12 and older cannot run AGSL. Keep the same lighting
                // equation as a compatibility fallback, sampled radially.
                val planarRadius = sqrt(
                    radius3d * radius3d - SpotLightHeightPx * SpotLightHeightPx,
                )
                val stops = Array(SpotLightSampleCount) { index ->
                    val planarFraction = index.toFloat() / (SpotLightSampleCount - 1)
                    val planarDistance = planarRadius * planarFraction
                    val distance3d = sqrt(
                        planarDistance * planarDistance +
                            SpotLightHeightPx * SpotLightHeightPx,
                    )
                    val inverseDistance = (1f - distance3d / radius3d).coerceIn(0f, 1f)
                    val attenuation = inverseDistance * inverseDistance *
                        (3f - 2f * inverseDistance)
                    val diffuse = SpotLightHeightPx / distance3d
                    val alpha = (
                        (SpotLightAmbientStrength + SpotLightDiffuseStrength * diffuse) *
                            attenuation * SpotLightBaseIntensity * intensity
                        ).coerceIn(0f, 1f)
                    planarFraction to Color.White.copy(alpha = alpha)
                }
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colorStops = stops,
                        center = Offset(spotLightX.value, spotLightY.value),
                        radius = planarRadius,
                    ),
                    topLeft = Offset(backgroundLeft, centerY - backgroundHeight / 2f),
                    size = Size(backgroundRight - backgroundLeft, backgroundHeight),
                    cornerRadius = CornerRadius(backgroundHeight / 2f),
                    blendMode = BlendMode.Overlay,
                )
            }
        }
    }
}

private val TrackHeight = 20.dp
private val TouchTargetHeight = 48.dp
private const val TrackEnlargeScale = 1.4f
private val ThumbRadius = 6.dp
private const val ThumbPressedScale = 8f / 6f
private val ThumbShadowBlurRadius = 4.dp
private val ThumbShadowOffsetY = 2.dp
private val LightThumbShadowColor = Color(0x29000000)
private val DarkThumbShadowColor = Color(0x40000000)
private val LightTrackBackgroundColor = Color(0x1F000000)
private val DarkTrackBackgroundColor = Color(0x33FFFFFF)

// COUISpotLightEffect TYPE_OPAQUE_MEDIUM_1 parameters. These values are raw
// Canvas pixels in the C17 runtime shader, not dp values.
private const val SpotLightStartRadiusPx = 100f
private const val SpotLightEndRadiusPx = 500f
private const val SpotLightHeightPx = 200f
private const val SpotLightLightEndIntensity = 0.6f
private const val SpotLightDarkEndIntensity = 0.8f
private const val SpotLightAmbientStrength = 0.4f
private const val SpotLightDiffuseStrength = 0.4f
private const val SpotLightBaseIntensity = 1.5f
private const val SpotLightNoise = 0.08f
private const val SpotLightDither = 0f
private const val SpotLightSampleCount = 13
private const val SpotLightGlowStiffness = 1755f
private const val SpotLightExtinguishStiffness = 247f
private const val SpotLightFollowStiffness = 322f
private const val SpotLightFollowDampingRatio = 0.85f

private const val TouchResizeDurationMillis = 183
private val TouchResizeEasing = CubicBezierEasing(0.33f, 0f, 0.67f, 1f)
private const val ThumbSpringStiffness = 987f
