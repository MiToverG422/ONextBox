// Copyright 2025, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.suqi8.coui.kmp.basic

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.COUITheme.isDynamicColor
import io.github.suqi8.coui.kmp.theme.LocalColors
import io.github.suqi8.coui.kmp.utils.CouiHapticEffect
import io.github.suqi8.coui.kmp.utils.rememberCouiHaptic
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A [Switch] component with COUI style.
 *
 * @param checked The checked state of the [Switch].
 * @param onCheckedChange The callback to be called when the state of the [Switch] changes.
 * @param modifier The modifier to be applied to the [Switch].
 * @param colors The [SwitchColors] of the [Switch].
 * @param enabled Whether the [Switch] is enabled.
 * @param isLoading Whether the [Switch] is in the COUI loading state: the thumb shrinks away and
 *   is replaced by a spinning gradient arc; touch input is swallowed while loading.
 */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    colors: SwitchColors = SwitchDefaults.switchColors(),
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()

    val couiHaptic = rememberCouiHaptic()

    val capsuleShape = CircleShape
    // COUI toggle interpolator, shared by every child animator (COUISwitch.animateWhenStateChanged()).
    val couiToggleEasing = remember { CubicBezierEasing(0.3f, 0f, 0.1f, 1f) }

    // COUI switch is tap-driven: the thumb never tracks the finger.
    val thumbOffset = remember {
        Animatable(
            if (checked) SwitchDefaults.ThumbEndOffset else SwitchDefaults.ThumbStartOffset,
            Dp.VectorConverter,
        )
    }

    // COUI thumb stretch ("tail drag"), played on toggle only (COUISwitch.animateWhenStateChanged()).
    val thumbSquash = remember { Animatable(1f) }
    var isFirstComposition by remember { mutableStateOf(true) }
    LaunchedEffect(checked) {
        if (isFirstComposition) {
            isFirstComposition = false
            return@LaunchedEffect
        }
        // A new toggle snaps the running animation to its settled values first (COUISwitch.setChecked()).
        thumbOffset.snapTo(if (checked) SwitchDefaults.ThumbStartOffset else SwitchDefaults.ThumbEndOffset)
        thumbSquash.snapTo(1f)
        launch {
            thumbOffset.animateTo(
                if (checked) SwitchDefaults.ThumbEndOffset else SwitchDefaults.ThumbStartOffset,
                tween(durationMillis = 383, easing = couiToggleEasing),
            )
        }
        thumbSquash.animateTo(1.3f, tween(durationMillis = 133, easing = couiToggleEasing))
        thumbSquash.animateTo(1f, tween(durationMillis = 250, easing = couiToggleEasing))
    }

    val thumbColorState = animateColorAsState(
        if (checked) colors.checkedThumbColor(enabled) else colors.uncheckedThumbColor(enabled),
    )

    val backgroundColorState = animateColorAsState(
        if (checked) colors.checkedTrackColor(enabled) else colors.uncheckedTrackColor(enabled),
        animationSpec = tween(durationMillis = 450, easing = couiToggleEasing),
    )

    // COUI press/hover feedback: translucent overlay over the bar color (coui_color_press / coui_color_hover).
    val overlayTone = COUITheme.colorScheme.onSurface
    val pressOverlayState = animateColorAsState(
        when {
            !enabled -> Color.Transparent
            isPressed -> overlayTone.copy(alpha = 0.12f)
            isHovered -> overlayTone.copy(alpha = 0.078f)
            else -> Color.Transparent
        },
    )

    // COUI loading state (COUISwitch.startLoading()/stopLoading()).
    val thumbScale = remember { Animatable(1f) }
    val loadingAlpha = remember { Animatable(0f) }
    val loadingScale = remember { Animatable(0.5f) }
    val loadingRotation = remember { Animatable(0f) }
    LaunchedEffect(isLoading) {
        if (isLoading) {
            loadingAlpha.snapTo(0f)
            loadingScale.snapTo(0.5f)
            launch { thumbScale.animateTo(0f, tween(durationMillis = 433, easing = couiToggleEasing)) }
            launch { loadingScale.animateTo(1f, tween(durationMillis = 550, easing = couiToggleEasing)) }
            launch { loadingAlpha.animateTo(1f, tween(durationMillis = 550, easing = couiToggleEasing)) }
            while (true) {
                loadingRotation.snapTo(0f)
                loadingRotation.animateTo(360f, tween(durationMillis = 800, easing = LinearEasing))
            }
        } else {
            thumbScale.snapTo(1f)
            loadingAlpha.animateTo(0f, tween(durationMillis = 100, easing = couiToggleEasing))
        }
    }

    val currentCouiHaptic by rememberUpdatedState(couiHaptic)
    // Loading switches cannot be toggled (COUISwitch loading style swallows touch).
    val interactive = enabled && !isLoading
    val hasCallback = onCheckedChange != null
    val toggleableModifier = if (hasCallback) {
        remember(checked, interactive, interactionSource) {
            Modifier.toggleable(
                value = checked,
                onValueChange = { v ->
                    currentOnCheckedChange?.invoke(v)
                    currentCouiHaptic(CouiHapticEffect.Switch)
                },
                enabled = interactive,
                role = Role.Switch,
                interactionSource = interactionSource,
            )
        }
    } else {
        Modifier.semantics {
            role = Role.Switch
            toggleableState = ToggleableState(checked)
            if (!enabled) disabled()
        }
    }

    Box(
        modifier = modifier
            .wrapContentSize(Alignment.Center)
            .size(SwitchDefaults.TrackWidth, SwitchDefaults.TrackHeight)
            .clip(capsuleShape)
            .drawBehind {
                drawRect(backgroundColorState.value)
                drawRect(pressOverlayState.value)
            }
            .hoverable(
                interactionSource = interactionSource,
                enabled = interactive,
            )
            .then(toggleableModifier),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset {
                    // The stretch extends backwards, against the direction of travel
                    // (COUISwitch.setOuterCircleRectF()).
                    val thumbPx = SwitchDefaults.ThumbSize.roundToPx()
                    val stretchedPx = (thumbPx * thumbSquash.value).roundToInt()
                    val restingLeft = thumbOffset.value.roundToPx()
                    val left = if (checked) restingLeft + thumbPx - stretchedPx else restingLeft
                    IntOffset(left, 0)
                }
                .layout { measurable, _ ->
                    // Stretch by resizing instead of scaling so the thumb stays a capsule.
                    val thumbPx = SwitchDefaults.ThumbSize.roundToPx()
                    val stretchedPx = (thumbPx * thumbSquash.value).roundToInt()
                    val placeable = measurable.measure(Constraints.fixed(stretchedPx, thumbPx))
                    layout(stretchedPx, thumbPx) { placeable.place(0, 0) }
                },
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        // COUI loading hides the thumb by scaling it about its centre.
                        scaleX = thumbScale.value
                        scaleY = thumbScale.value
                    }
                    .then(
                        if (enabled) {
                            // COUISwitch clears the shadow layer when disabled.
                            Modifier.dropShadow(shape = CircleShape, shadow = SwitchDefaults.ThumbShadow)
                        } else {
                            Modifier
                        },
                    )
                    .drawBehind {
                        // A circle at rest, a capsule while stretched (COUISwitch.drawOuterCircle).
                        drawRoundRect(
                            color = thumbColorState.value,
                            cornerRadius = CornerRadius(size.height / 2f),
                        )
                    },
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        // COUISwitch.drawLoading(): arc geometry follows switch_loading.png
                        // (stroke 8/40, centre-line diameter 32/40, ~312 deg alpha-tail sweep).
                        val alpha = loadingAlpha.value
                        if (alpha > 0f) {
                            val side = size.minDimension
                            val strokeWidth = side * 0.2f
                            val arcDiameter = side * 0.8f
                            val topLeft = Offset((size.width - arcDiameter) / 2f, (size.height - arcDiameter) / 2f)
                            val color = thumbColorState.value
                            val brush = Brush.sweepGradient(
                                0f to color.copy(alpha = 0f),
                                312f / 360f to color.copy(alpha = color.alpha * alpha),
                                center = Offset(size.width / 2f, size.height / 2f),
                            )
                            scale(loadingScale.value) {
                                // +150 deg phase puts the head at ~102 deg like the source PNG.
                                rotate(loadingRotation.value + 150f) {
                                    drawArc(
                                        brush = brush,
                                        startAngle = 0f,
                                        sweepAngle = 312f,
                                        useCenter = false,
                                        topLeft = topLeft,
                                        size = Size(arcDiameter, arcDiameter),
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                                    )
                                }
                            }
                        }
                    },
            )
        }
    }
}

object SwitchDefaults {

    /** The track width of the [Switch] (COUISwitch bar_width). */
    val TrackWidth: Dp = 38.dp

    /** The track height of the [Switch] (COUISwitch bar_height). */
    val TrackHeight: Dp = 24.dp

    /** The diameter of the [Switch] thumb (COUISwitch outer_circle_width). */
    val ThumbSize: Dp = 18.dp

    /** The inset of the thumb from the track edge when fully on one side (COUISwitch circle_padding). */
    private val ThumbMargin: Dp = 3.dp

    /** The thumb offset in the unchecked (off) position. */
    val ThumbStartOffset: Dp = ThumbMargin

    /** The thumb offset in the checked (on) position. */
    val ThumbEndOffset: Dp = TrackWidth - ThumbSize - ThumbMargin

    /** The distance the thumb travels between off and on. */
    val Travel: Dp = ThumbEndOffset - ThumbStartOffset

    /**
     * The drop shadow under the thumb while the [Switch] is enabled
     * (COUISwitch.setPaintShadowLayer at the 3.5x ColorOS reference density).
     */
    val ThumbShadow: Shadow = Shadow(
        radius = 2.29.dp,
        color = Color(0x19000000),
        offset = DpOffset(x = 0.dp, y = 1.14.dp),
    )

    /**
     * The default colors for the [Switch]. COUISwitch keeps the thumb white in both states.
     */
    @Composable
    fun switchColors(
        checkedThumbColor: Color = if (isDynamicColor) LocalColors.current.onPrimary else COUITheme.colorScheme.onPrimary,
        uncheckedThumbColor: Color = if (isDynamicColor) LocalColors.current.onPrimary else COUITheme.colorScheme.onSecondary,
        disabledCheckedThumbColor: Color = if (isDynamicColor) LocalColors.current.surface else COUITheme.colorScheme.disabledOnPrimary,
        disabledUncheckedThumbColor: Color = COUITheme.colorScheme.disabledOnSecondary,
        checkedTrackColor: Color = COUITheme.colorScheme.primary,
        uncheckedTrackColor: Color = COUITheme.colorScheme.secondary,
        disabledCheckedTrackColor: Color = COUITheme.colorScheme.disabledPrimary,
        disabledUncheckedTrackColor: Color = COUITheme.colorScheme.disabledSecondary,
    ): SwitchColors = remember(
        checkedThumbColor,
        uncheckedThumbColor,
        disabledCheckedThumbColor,
        disabledUncheckedThumbColor,
        checkedTrackColor,
        uncheckedTrackColor,
        disabledCheckedTrackColor,
        disabledUncheckedTrackColor,
    ) {
        SwitchColors(
            checkedThumbColor = checkedThumbColor,
            uncheckedThumbColor = uncheckedThumbColor,
            disabledCheckedThumbColor = disabledCheckedThumbColor,
            disabledUncheckedThumbColor = disabledUncheckedThumbColor,
            checkedTrackColor = checkedTrackColor,
            uncheckedTrackColor = uncheckedTrackColor,
            disabledCheckedTrackColor = disabledCheckedTrackColor,
            disabledUncheckedTrackColor = disabledUncheckedTrackColor,
        )
    }
}

@Immutable
data class SwitchColors(
    private val checkedThumbColor: Color,
    private val uncheckedThumbColor: Color,
    private val disabledCheckedThumbColor: Color,
    private val disabledUncheckedThumbColor: Color,
    private val checkedTrackColor: Color,
    private val uncheckedTrackColor: Color,
    private val disabledCheckedTrackColor: Color,
    private val disabledUncheckedTrackColor: Color,
) {
    @Stable
    internal fun checkedThumbColor(enabled: Boolean): Color = if (enabled) checkedThumbColor else disabledCheckedThumbColor

    @Stable
    internal fun uncheckedThumbColor(enabled: Boolean): Color = if (enabled) uncheckedThumbColor else disabledUncheckedThumbColor

    @Stable
    internal fun checkedTrackColor(enabled: Boolean): Color = if (enabled) checkedTrackColor else disabledCheckedTrackColor

    @Stable
    internal fun uncheckedTrackColor(enabled: Boolean): Color = if (enabled) uncheckedTrackColor else disabledUncheckedTrackColor
}
