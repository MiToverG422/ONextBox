// Copyright 2025, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.suqi8.coui.kmp.basic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.CouiHapticEffect
import io.github.suqi8.coui.kmp.utils.rememberCouiHaptic

/**
 * A [Stepper] component with COUI style.
 *
 * A stepper lets the user adjust an integer [value] in fixed [step] increments using a minus and a
 * plus button, mirroring COUI's COUIStepperView (32dp circular buttons, OPLUS step haptic).
 *
 * @param value The current value of the [Stepper].
 * @param onValueChange The callback invoked with the new value when the user steps up or down.
 * @param modifier The modifier to be applied to the [Stepper].
 * @param minValue The minimum value the [Stepper] can reach.
 * @param maxValue The maximum value the [Stepper] can reach.
 * @param step The increment/decrement applied on each button press.
 * @param enabled Whether the [Stepper] is enabled.
 * @param colors The [StepperColors] of the [Stepper].
 */
@Composable
fun Stepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minValue: Int = Int.MIN_VALUE,
    maxValue: Int = Int.MAX_VALUE,
    step: Int = 1,
    enabled: Boolean = true,
    colors: StepperColors = StepperDefaults.stepperColors(),
) {
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val couiHaptic = rememberCouiHaptic()
    val currentHaptic by rememberUpdatedState(couiHaptic)

    val canDecrease = enabled && value > minValue
    val canIncrease = enabled && value < maxValue

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StepperDefaults.Spacing),
    ) {
        StepperButton(
            symbol = StepperSymbol.Minus,
            enabled = canDecrease,
            buttonColor = colors.buttonColor,
            contentColor = colors.contentColor(canDecrease),
            onClick = {
                val next = (value - step).coerceAtLeast(minValue)
                if (next != value) {
                    currentOnValueChange(next)
                    currentHaptic(CouiHapticEffect.Strength)
                }
            },
        )
        Box(
            modifier = Modifier.widthIn(min = StepperDefaults.IndicatorMinWidth),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = value.toString(),
                color = colors.contentColor(enabled),
                style = StepperDefaults.textStyle(),
                textAlign = TextAlign.Center,
            )
        }
        StepperButton(
            symbol = StepperSymbol.Plus,
            enabled = canIncrease,
            buttonColor = colors.buttonColor,
            contentColor = colors.contentColor(canIncrease),
            onClick = {
                val next = (value + step).coerceAtMost(maxValue)
                if (next != value) {
                    currentOnValueChange(next)
                    currentHaptic(CouiHapticEffect.Strength)
                }
            },
        )
    }
}

private enum class StepperSymbol { Minus, Plus }

@Composable
private fun StepperButton(
    symbol: StepperSymbol,
    enabled: Boolean,
    buttonColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        backgroundColor = buttonColor,
        cornerRadius = StepperDefaults.ButtonSize / 2,
        minWidth = StepperDefaults.ButtonSize,
        minHeight = StepperDefaults.ButtonSize,
    ) {
        Box(
            modifier = Modifier.size(StepperDefaults.ButtonSize),
            contentAlignment = Alignment.Center,
        ) {
            // COUI ic_minus_sign / ic_plus_sign: a 1.6dp round-cap stroke spanning 15.2dp
            // (8.4..23.6) inside the 32dp button, drawn directly to avoid an icon dependency.
            Canvas(modifier = Modifier.size(StepperDefaults.GlyphSize)) {
                val strokeWidth = StepperDefaults.GlyphStrokeWidth.toPx()
                val cx = size.width / 2f
                val cy = size.height / 2f
                val half = (size.minDimension - strokeWidth) / 2f
                drawLine(
                    color = contentColor,
                    start = Offset(cx - half, cy),
                    end = Offset(cx + half, cy),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                if (symbol == StepperSymbol.Plus) {
                    drawLine(
                        color = contentColor,
                        start = Offset(cx, cy - half),
                        end = Offset(cx, cy + half),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

/** Contains default values used by [Stepper]. */
object StepperDefaults {
    /** The diameter of each stepper button (COUI stepper_button_size). */
    val ButtonSize = 32.dp

    /** The visual extent of the +/- glyph inside each button (COUI ic_minus_sign / ic_plus_sign span 8.4..23.6dp). */
    val GlyphSize = 15.2.dp

    /** The stroke width of the +/- glyph (COUI ic_minus_sign / ic_plus_sign strokeWidth). */
    val GlyphStrokeWidth = 1.6.dp

    /** The spacing between buttons and the indicator (COUI indicator horizontal margin). */
    val Spacing = 12.dp

    /** The minimum width reserved for the value indicator (COUI indicator minWidth). */
    val IndicatorMinWidth = 44.dp

    /** The default text style of the value indicator (COUIStepperViewTextDefStyle: 18sp, sans-serif-medium). */
    @Composable
    fun textStyle(): TextStyle = COUITheme.textStyles.title4.copy(fontWeight = FontWeight.Medium)

    /** The default colors for the [Stepper]. */
    @Composable
    fun stepperColors(
        buttonColor: Color = COUITheme.colorScheme.secondaryContainer,
        contentColor: Color = COUITheme.colorScheme.onSurface,
        disabledContentColor: Color = COUITheme.colorScheme.disabledOnSurface,
    ): StepperColors = remember(buttonColor, contentColor, disabledContentColor) {
        StepperColors(
            buttonColor = buttonColor,
            contentColor = contentColor,
            disabledContentColor = disabledContentColor,
        )
    }
}

/**
 * Colors used by a [Stepper].
 *
 * @param buttonColor The circular background color of the +/- buttons.
 * @param contentColor The color of the glyphs and the value when enabled.
 * @param disabledContentColor The color of the glyphs and the value when disabled.
 */
@Immutable
data class StepperColors(
    val buttonColor: Color,
    val contentColor: Color,
    val disabledContentColor: Color,
) {
    @Stable
    internal fun contentColor(enabled: Boolean): Color = if (enabled) contentColor else disabledContentColor
}
