package com.mi.onextbox.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.CouiHapticEffect
import io.github.suqi8.coui.kmp.utils.rememberCouiHaptic

/** ColorOS 17 switch geometry: 44x24dp track, 18dp thumb, and 3dp edge inset. */
@Composable
fun ColorOsSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onPressStateChange: (Boolean) -> Unit = {},
) {
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val haptic = rememberCouiHaptic()
    val easing = remember { CubicBezierEasing(0.3f, 0f, 0.1f, 1f) }
    val colorScheme = COUITheme.colorScheme
    val darkTheme = colorScheme.surface.luminance() < .5f

    val position = remember { Animatable(if (checked) 1f else 0f) }
    var dragOffset by remember { mutableStateOf<Float?>(null) }
    val density = LocalDensity.current
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val startPx = with(density) { ThumbStartOffset.toPx() }
    val endPx = with(density) { ThumbEndOffset.toPx() }
    val currentChecked by rememberUpdatedState(checked)
    val currentHaptic by rememberUpdatedState(haptic)
    val dragModifier = if (enabled && onCheckedChange != null) {
        Modifier.pointerInput(enabled, startPx, endPx, direction) {
            try {
                detectHorizontalDragGestures(
                    onDragStart = { dragOffset = startPx + position.value.coerceIn(0f, 1f) * (endPx - startPx) },
                    onDragCancel = { dragOffset = null },
                    onDragEnd = {
                        val progress = ((dragOffset ?: startPx) - startPx) / (endPx - startPx)
                        val next = if (currentChecked) progress > 0.35f else progress >= 0.65f
                        dragOffset = null
                        if (next != currentChecked) {
                            currentOnCheckedChange?.invoke(next)
                            currentHaptic(CouiHapticEffect.Switch)
                        }
                    },
                    onHorizontalDrag = { change, amount ->
                        dragOffset = ((dragOffset ?: startPx) + amount * direction).coerceIn(startPx, endPx)
                        change.consume()
                    },
                )
            } finally {
                dragOffset = null
            }
        }
    } else Modifier
    LaunchedEffect(dragOffset, checked, enabled) {
        val drag = dragOffset
        if (drag != null && enabled) {
            position.animateTo(
                ((drag - startPx) / (endPx - startPx)).coerceIn(0f, 1f),
                spring(dampingRatio = 1f, stiffness = 438.65f, visibilityThreshold = 0.005f),
            )
        } else {
            position.animateTo(
                if (checked) 1f else 0f,
                spring(dampingRatio = 0.8f, stiffness = 246.74f, visibilityThreshold = 0.005f),
            )
        }
    }
    val visualProgress = position.value.coerceIn(0f, 1f)
    val thumbOffset = ThumbStartOffset + (ThumbEndOffset - ThumbStartOffset) * visualProgress
    val uncheckedTrack = if (enabled) colorScheme.secondary else colorScheme.disabledSecondary
    val checkedTrack = if (enabled) colorScheme.primary else colorScheme.disabledPrimary
    val trackColor = Color(
        red = uncheckedTrack.red + (checkedTrack.red - uncheckedTrack.red) * visualProgress,
        green = uncheckedTrack.green + (checkedTrack.green - uncheckedTrack.green) * visualProgress,
        blue = uncheckedTrack.blue + (checkedTrack.blue - uncheckedTrack.blue) * visualProgress,
        alpha = uncheckedTrack.alpha + (checkedTrack.alpha - uncheckedTrack.alpha) * visualProgress,
    )
    val thumbColor by animateColorAsState(
        targetValue = colorOsSwitchThumbColor(
            monet = LocalColorOsMonetEnabled.current,
            checked = checked,
            enabled = enabled,
            darkTheme = darkTheme,
            onPrimary = colorScheme.onPrimary,
            onSurface = colorScheme.onSurfaceVariantSummary,
        ),
        animationSpec = tween(durationMillis = 450, easing = easing),
        label = "colorOsSwitchThumbColor",
    )
    val pressed = enabled && (isPressed || dragOffset != null)
    val currentOnPressStateChange by rememberUpdatedState(onPressStateChange)
    SideEffect { currentOnPressStateChange(pressed) }
    DisposableEffect(Unit) {
        onDispose { currentOnPressStateChange(false) }
    }
    val overlayAlpha by animateFloatAsState(
        targetValue = when {
            !enabled -> 0f
            pressed -> if (darkTheme) 0.2f else 0.12f
            isHovered -> if (darkTheme) 0.15f else 0.078f
            else -> 0f
        },
        animationSpec = tween(durationMillis = if (pressed) 100 else 250, easing = easing),
        label = "colorOsSwitchOverlayAlpha",
    )
    val overlayColor = (if (darkTheme) Color.White else Color.Black).copy(alpha = overlayAlpha)

    val toggleModifier = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            interactionSource = interactionSource,
            indication = null,
            onValueChange = { value ->
                currentOnCheckedChange?.invoke(value)
                haptic(CouiHapticEffect.Switch)
            },
        )
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
            .size(TrackWidth, TrackHeight)
            .clip(CircleShape)
            .drawBehind {
                drawRect(trackColor)
                drawRect(overlayColor)
            }
            .hoverable(interactionSource = interactionSource, enabled = enabled)
            .then(toggleModifier)
            .then(dragModifier),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = thumbOffset)
                .size(ThumbSize)
                .then(
                    if (enabled) {
                        Modifier.dropShadow(
                            shape = CircleShape,
                            shadow = Shadow(
                                radius = 2.29.dp,
                                color = Color(0x19000000),
                                offset = DpOffset(x = 0.dp, y = 1.14.dp),
                            ),
                        )
                    } else {
                        Modifier
                    },
                )
                .background(thumbColor, CircleShape),
        )
    }
}

internal fun colorOsSwitchThumbColor(
    monet: Boolean,
    checked: Boolean,
    enabled: Boolean,
    darkTheme: Boolean,
    onPrimary: Color,
    onSurface: Color,
): Color {
    if (!monet) return when {
        enabled -> Color.White
        darkTheme -> Color(0x29FFFFFF)
        else -> Color(0x8AFFFFFF)
    }
    val color = if (checked) onPrimary else onSurface
    return if (enabled) color else color.copy(alpha = color.alpha * .38f)
}

private val TrackWidth = 44.dp
private val TrackHeight = 24.dp
private val ThumbSize = 18.dp
private val ThumbStartOffset = 3.dp
private val ThumbEndOffset = TrackWidth - ThumbSize - ThumbStartOffset
