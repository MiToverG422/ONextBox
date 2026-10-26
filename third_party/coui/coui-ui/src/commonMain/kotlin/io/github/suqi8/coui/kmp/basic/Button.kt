// Copyright 2025, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.suqi8.coui.kmp.basic

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.LocalContentColor
import kotlin.coroutines.cancellation.CancellationException

/**
 * A [Button] component with COUI style.
 *
 * @param onClick The callback when the [Button] is clicked.
 * @param modifier The modifier to be applied to the [Button].
 * @param enabled Whether the [Button] is enabled.
 * @param pressScaleEnabled Whether the [Button] shrinks while pressed (COUIButton `scaleEnable`).
 *   Set to `false` for buttons that fill a container cell, such as the alert dialog button bar,
 *   leaving the full-cell press tint as the only feedback.
 * @param cornerRadius The corner radius of the [Button].
 * @param minWidth The minimum width of the [Button].
 * @param minHeight The minimum height of the [Button].
 * @param colors The [ButtonColors] of the [Button].
 * @param insideMargin The margin inside the [Button].
 * @param interactionSource The [MutableInteractionSource] to be used for the [Button].
 * @param indication The [Indication] to be used for the [Button]. Defaults to `null` because
 *   the COUI press feedback (scale + press tint) is built into the button itself.
 * @param content The [Composable] content of the [Button].
 */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pressScaleEnabled: Boolean = true,
    cornerRadius: Dp = ButtonDefaults.CornerRadius,
    minWidth: Dp = ButtonDefaults.MinWidth,
    minHeight: Dp = ButtonDefaults.MinHeight,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    insideMargin: PaddingValues = ButtonDefaults.InsideMargin,
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = null,
    content: @Composable RowScope.() -> Unit,
) {
    @Suppress("NAME_SHADOWING")
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }
    val rowModifier = remember(minWidth, minHeight, insideMargin) {
        Modifier
            .defaultMinSize(minWidth = minWidth, minHeight = minHeight)
            .padding(insideMargin)
    }
    val containerColor = if (enabled) colors.color else colors.disabledColor
    val contentColor = if (enabled) colors.contentColor else colors.disabledContentColor

    // COUI press feedback: scale (COUIPressFeedbackHelper) + press tint (COUIMaskEffectDrawable).
    val isPressed by interactionSource.collectIsPressedAsState()
    val scaleProgress by animateFloatAsState(
        targetValue = if (enabled && isPressed && pressScaleEnabled) 1f else 0f,
        animationSpec = PressFeedbackSpring,
        label = "buttonPressScale",
    )
    val tintProgress = remember { Animatable(0f) }
    LaunchedEffect(enabled, isPressed) {
        if (enabled && isPressed) {
            tintProgress.animateTo(1f, PressFeedbackSpring)
        } else if (!enabled) {
            tintProgress.snapTo(0f)
        } else if (tintProgress.value > 0f) {
            if (tintProgress.value < PressedTintMinVisibleProgress) {
                // Keep the press-in animation running until the tint is clearly visible.
                try {
                    tintProgress.animateTo(1f, PressFeedbackSpring) {
                        if (value >= PressedTintMinVisibleProgress) throw PressTintThresholdReached()
                    }
                } catch (_: PressTintThresholdReached) {
                    // Minimum visible progress reached; fall through to the fade-out.
                }
            }
            tintProgress.animateTo(0f, PressFeedbackSpring)
        }
    }
    val pressTint = if (COUITheme.colorScheme.background.luminance() < 0.5f) PressedTintDark else PressedTintLight
    val fillColor = if (tintProgress.value > 0f) {
        pressTint.copy(alpha = pressTint.alpha * tintProgress.value).compositeOver(containerColor)
    } else {
        containerColor
    }

    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(
            modifier = modifier
                .semantics { role = Role.Button }
                .graphicsLayer {
                    // Branch inside the block so the modifier chain keeps a single graphicsLayer
                    // node instead of swapping nodes when the flag changes.
                    if (pressScaleEnabled) {
                        val scale = 1f - (1f - pressedScaleEndRatio(size)) * scaleProgress
                        scaleX = scale
                        scaleY = scale
                    }
                }
                .squircleSurface(color = fillColor, cornerRadius = cornerRadius)
                .clickable(
                    interactionSource = interactionSource,
                    indication = indication,
                    enabled = enabled,
                    onClick = onClick,
                ),
            propagateMinConstraints = true,
        ) {
            Row(
                modifier = rowModifier,
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

/**
 * A [TextButton] component with COUI style.
 *
 * @param text The text of the [TextButton].
 * @param onClick The callback when the [TextButton] is clicked.
 * @param modifier The modifier to be applied to the [TextButton].
 * @param enabled Whether the [TextButton] is enabled.
 * @param pressScaleEnabled Whether the [TextButton] shrinks while pressed (COUIButton
 *   `scaleEnable`). Set to `false` for buttons that fill a container cell, such as the alert dialog
 *   button bar, leaving the full-cell press tint as the only feedback.
 * @param cornerRadius The corner radius of the [TextButton].
 * @param minWidth The minimum width of the [TextButton].
 * @param minHeight The minimum height of the [TextButton].
 * @param colors The [TextButtonColors] of the [TextButton].
 * @param insideMargin The margin inside the [TextButton].
 * @param textStyle The [TextStyle] of the label. Pass a 14sp style together with the `Small`
 *   metrics from [ButtonDefaults] to get the COUI small size tier.
 * @param interactionSource The [MutableInteractionSource] to be used for the [TextButton].
 * @param indication The [Indication] to be used for the [TextButton]. Defaults to `null` because
 *   the COUI press feedback (scale + press tint) is built into the button itself.
 */
@Composable
fun TextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pressScaleEnabled: Boolean = true,
    cornerRadius: Dp = ButtonDefaults.CornerRadius,
    minWidth: Dp = ButtonDefaults.MinWidth,
    minHeight: Dp = ButtonDefaults.MinHeight,
    colors: TextButtonColors = ButtonDefaults.textButtonColors(),
    insideMargin: PaddingValues = ButtonDefaults.InsideMargin,
    textStyle: TextStyle = COUITheme.textStyles.button,
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = null,
) {
    val mappedColors = remember(colors) {
        ButtonColors(
            color = colors.color,
            disabledColor = colors.disabledColor,
            contentColor = colors.textColor,
            disabledContentColor = colors.disabledTextColor,
        )
    }
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        pressScaleEnabled = pressScaleEnabled,
        cornerRadius = cornerRadius,
        minWidth = minWidth,
        minHeight = minHeight,
        colors = mappedColors,
        insideMargin = insideMargin,
        interactionSource = interactionSource,
        indication = indication,
    ) {
        Text(
            text = text,
            style = textStyle,
        )
    }
}

object ButtonDefaults {

    /**
     * The smallest scale a button shrinks to while pressed (COUIPressFeedbackHelper end ratio
     * for surfaces up to 48 x 48dp).
     */
    val PressedScale = 0.92f

    /**
     * The legacy COUIButton `brightness` attribute value. Retained for source compatibility only
     * and no longer used by [Button].
     */
    val PressedBrightness = 0.8f

    /**
     * The default min width applied for all buttons. Note that you can override it by applying
     * Modifier.widthIn directly on the button composable.
     */
    val MinWidth = 58.dp

    /**
     * The default min height applied for all buttons. Note that you can override it by applying
     * Modifier.heightIn directly on the button composable.
     */
    val MinHeight = 44.dp

    /**
     * The default corner radius applied for all buttons (COUI height-derived capsule:
     * radius = [MinHeight] / 2).
     */
    val CornerRadius = 22.dp

    /**
     * The default inside margin applied for all buttons.
     */
    val InsideMargin = PaddingValues(horizontal = 12.dp, vertical = 0.dp)

    /**
     * The min width of the small size tier (COUI coui_btn_small_width_min).
     */
    val MinWidthSmall = 52.dp

    /**
     * The min height of the small size tier (COUI coui_btn_small_height_min).
     */
    val MinHeightSmall = 28.dp

    /**
     * The corner radius of the small size tier (COUI height-derived capsule: radius = [MinHeightSmall] / 2).
     */
    val CornerRadiusSmall = 14.dp

    /**
     * The inside margin of the small size tier (COUI Widget.COUI.Button.Small padding 12dp/4dp).
     */
    val InsideMarginSmall = PaddingValues(horizontal = 12.dp, vertical = 4.dp)

    /**
     * The default [ButtonColors] for all buttons.
     */
    @Composable
    fun buttonColors(
        color: Color = COUITheme.colorScheme.secondaryVariant,
        disabledColor: Color = COUITheme.colorScheme.disabledSecondaryVariant,
        contentColor: Color = COUITheme.colorScheme.onSecondaryVariant,
        disabledContentColor: Color = COUITheme.colorScheme.disabledOnSecondaryVariant,
    ): ButtonColors = remember(color, disabledColor, contentColor, disabledContentColor) {
        ButtonColors(
            color = color,
            disabledColor = disabledColor,
            contentColor = contentColor,
            disabledContentColor = disabledContentColor,
        )
    }

    /**
     * The [ButtonColors] for primary buttons.
     */
    @Composable
    fun buttonColorsPrimary(
        color: Color = COUITheme.colorScheme.primary,
        disabledColor: Color = COUITheme.colorScheme.disabledPrimaryButton,
        contentColor: Color = COUITheme.colorScheme.onPrimary,
        disabledContentColor: Color = COUITheme.colorScheme.disabledOnPrimaryButton,
    ): ButtonColors = remember(color, disabledColor, contentColor, disabledContentColor) {
        ButtonColors(
            color = color,
            disabledColor = disabledColor,
            contentColor = contentColor,
            disabledContentColor = disabledContentColor,
        )
    }

    /**
     * The default [TextButtonColors] for all text buttons.
     */
    @Composable
    fun textButtonColors(
        color: Color = COUITheme.colorScheme.secondaryVariant,
        disabledColor: Color = COUITheme.colorScheme.disabledSecondaryVariant,
        textColor: Color = COUITheme.colorScheme.onSecondaryVariant,
        disabledTextColor: Color = COUITheme.colorScheme.disabledOnSecondaryVariant,
    ): TextButtonColors = remember(color, disabledColor, textColor, disabledTextColor) {
        TextButtonColors(
            color = color,
            disabledColor = disabledColor,
            textColor = textColor,
            disabledTextColor = disabledTextColor,
        )
    }

    /**
     * The [TextButtonColors] for primary text buttons.
     */
    @Composable
    fun textButtonColorsPrimary(
        color: Color = COUITheme.colorScheme.primary,
        disabledColor: Color = COUITheme.colorScheme.disabledPrimaryButton,
        textColor: Color = COUITheme.colorScheme.onPrimary,
        disabledTextColor: Color = COUITheme.colorScheme.disabledOnPrimaryButton,
    ): TextButtonColors = remember(color, disabledColor, textColor, disabledTextColor) {
        TextButtonColors(
            color = color,
            disabledColor = disabledColor,
            textColor = textColor,
            disabledTextColor = disabledTextColor,
        )
    }

    /**
     * The [TextButtonColors] for borderless / translate text buttons (COUI
     * Widget.COUI.Button.Large.Borderless / Translate): no fill, primary-tinted label.
     */
    @Composable
    fun textButtonColorsBorderless(
        color: Color = Color.Transparent,
        disabledColor: Color = Color.Transparent,
        textColor: Color = COUITheme.colorScheme.primary,
        disabledTextColor: Color = COUITheme.colorScheme.disabledPrimary,
    ): TextButtonColors = remember(color, disabledColor, textColor, disabledTextColor) {
        TextButtonColors(
            color = color,
            disabledColor = disabledColor,
            textColor = textColor,
            disabledTextColor = disabledTextColor,
        )
    }
}

@Immutable
data class ButtonColors(
    val color: Color,
    val disabledColor: Color,
    val contentColor: Color,
    val disabledContentColor: Color,
)

@Immutable
data class TextButtonColors(
    val color: Color,
    val disabledColor: Color,
    val textColor: Color,
    val disabledTextColor: Color,
)

/** The COUI press feedback spring (COUISpringForce response 0.3f / bounce 0f). */
private val PressFeedbackSpring = spring<Float>(dampingRatio = 1f, stiffness = 438.65f)

/** couiColorPress in light themes (coui_color_press #1F000000). */
private val PressedTintLight = Color(0x1F000000)

/** couiColorPress in dark themes (coui_color_press_dark #33FFFFFF). */
private val PressedTintDark = Color(0x33FFFFFF)

/** The minimum press tint progress before a release may fade out (COUIMaskEffectDrawable). */
private val PressedTintMinVisibleProgress = 0.7f

/** Thrown to stop the deferred press-in tint animation once [PressedTintMinVisibleProgress] is reached. */
private class PressTintThresholdReached : CancellationException("Press tint reached the minimum visible progress")

/** COUIInEaseInterpolator, used to map the button area to the pressed end scale. */
private val PressedScaleEasing = CubicBezierEasing(0f, 0f, 0.1f, 1f)

/** COUIPressFeedbackHelper area bounds: coui_min_end_value_size (48dp) squared. */
private val PressedScaleMinAreaDp2 = 48f * 48f

/** COUIPressFeedbackHelper area bounds: coui_max_end_value_width x height (328dp x 220dp). */
private val PressedScaleMaxAreaDp2 = 328f * 220f

/** The pressed end scale of the largest surfaces (COUIPressFeedbackHelper MAX_SCALE_END_RATIO). */
private val PressedScaleMax = 0.98f

/** The scale a surface of [size] shrinks to when fully pressed (COUIPressFeedbackHelper.getCardScaleRatio). */
private fun Density.pressedScaleEndRatio(size: Size): Float {
    val area = size.width.toDp().value * size.height.toDp().value
    return when {
        area <= PressedScaleMinAreaDp2 -> ButtonDefaults.PressedScale

        area >= PressedScaleMaxAreaDp2 -> PressedScaleMax

        else -> ButtonDefaults.PressedScale + (PressedScaleMax - ButtonDefaults.PressedScale) *
            PressedScaleEasing.transform((area - PressedScaleMinAreaDp2) / (PressedScaleMaxAreaDp2 - PressedScaleMinAreaDp2))
    }
}
