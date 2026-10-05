package com.mi.onextbox.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
) {
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val haptic = rememberCouiHaptic()
    val easing = remember { CubicBezierEasing(0.3f, 0f, 0.1f, 1f) }
    val colorScheme = COUITheme.colorScheme
    val darkTheme = colorScheme.surface.luminance() < .5f

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) ThumbEndOffset else ThumbStartOffset,
        animationSpec = tween(durationMillis = 383, easing = easing),
        label = "colorOsSwitchThumbOffset",
    )
    val trackColor by animateColorAsState(
        targetValue = when {
            checked && enabled -> colorScheme.primary
            checked -> colorScheme.disabledPrimary
            enabled -> colorScheme.secondary
            else -> colorScheme.disabledSecondary
        },
        animationSpec = tween(durationMillis = 450, easing = easing),
        label = "colorOsSwitchTrackColor",
    )
    val thumbColor by animateColorAsState(
        targetValue = when {
            enabled -> Color.White
            darkTheme -> Color(0x29FFFFFF)
            else -> Color(0x8AFFFFFF)
        },
        label = "colorOsSwitchThumbColor",
    )
    val overlayColor by animateColorAsState(
        targetValue = when {
            !enabled -> Color.Transparent
            isPressed -> colorScheme.onSurface.copy(alpha = 0.12f)
            isHovered -> colorScheme.onSurface.copy(alpha = 0.078f)
            else -> Color.Transparent
        },
        label = "colorOsSwitchOverlayColor",
    )

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
            .then(toggleModifier),
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

private val TrackWidth = 44.dp
private val TrackHeight = 24.dp
private val ThumbSize = 18.dp
private val ThumbStartOffset = 3.dp
private val ThumbEndOffset = TrackWidth - ThumbSize - ThumbStartOffset
