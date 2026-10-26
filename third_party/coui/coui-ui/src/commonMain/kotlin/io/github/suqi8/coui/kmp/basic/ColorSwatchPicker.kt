// Copyright 2026, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.suqi8.coui.kmp.basic

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * A [ColorSwatchPicker] component with COUI style.
 *
 * A horizontal row of circular color swatches with an animated selection ring, mirroring the
 * ColorOS 16 icon mono-color selector (UxColorSelectableView): a 34dp color dot inside a 44dp
 * cell, with a 2dp theme-colored ring that fades in on selection.
 *
 * Note: [colors] is a standard [List], which Compose treats as unstable; build it inside a
 * `remember { }` block (or keep the same instance across compositions) to avoid needless
 * recompositions.
 *
 * @param colors The list of colors to display as swatches.
 * @param selectedIndex The index of the currently selected swatch, or a negative value for none.
 * @param onSwatchSelected The callback invoked with the index of the swatch the user selects.
 * @param modifier The modifier to be applied to the [ColorSwatchPicker].
 * @param enabled Whether the [ColorSwatchPicker] is enabled.
 * @param swatchSize The diameter of each color dot (COUI ux_icon_color_select_inside).
 * @param cellSize The touch target / selection ring outer diameter (COUI ux_icon_color_select_out).
 * @param ringStrokeWidth The stroke width of the selection ring (COUI ux_theme_shadow_stroke).
 * @param ringColor The color of the selection ring (COUI couiColorPrimary).
 * @param spacing The spacing between adjacent cells.
 */
@Composable
fun ColorSwatchPicker(
    colors: List<Color>,
    selectedIndex: Int,
    onSwatchSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    swatchSize: Dp = ColorSwatchPickerDefaults.SwatchSize,
    cellSize: Dp = ColorSwatchPickerDefaults.CellSize,
    ringStrokeWidth: Dp = ColorSwatchPickerDefaults.RingStrokeWidth,
    ringColor: Color = COUITheme.colorScheme.primary,
    spacing: Dp = ColorSwatchPickerDefaults.Spacing,
) {
    val currentOnSwatchSelected by rememberUpdatedState(onSwatchSelected)
    Row(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        colors.forEachIndexed { index, color ->
            ColorSwatchCell(
                color = color,
                selected = index == selectedIndex,
                enabled = enabled,
                swatchSize = swatchSize,
                cellSize = cellSize,
                ringStrokeWidth = ringStrokeWidth,
                ringColor = ringColor,
                onClick = { currentOnSwatchSelected(index) },
            )
        }
    }
}

@Composable
private fun ColorSwatchCell(
    color: Color,
    selected: Boolean,
    enabled: Boolean,
    swatchSize: Dp,
    cellSize: Dp,
    ringStrokeWidth: Dp,
    ringColor: Color,
    onClick: () -> Unit,
) {
    // Selection ring fade, asymmetric in/out durations (COUI UxColorSelectableView).
    val ringAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (selected) {
                ColorSwatchPickerDefaults.RingFadeInDuration
            } else {
                ColorSwatchPickerDefaults.RingFadeOutDuration
            },
            easing = ColorSwatchPickerDefaults.RingEasing,
        ),
    )
    Box(
        modifier = Modifier
            .size(cellSize)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                interactionSource = null,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(cellSize)) {
            // Color dot centered in the cell
            drawCircle(
                color = color,
                radius = swatchSize.toPx() / 2f,
            )
            // Selection ring at the cell bounds, alpha animated
            if (ringAlpha > 0f) {
                val strokePx = ringStrokeWidth.toPx()
                drawCircle(
                    color = ringColor,
                    radius = (size.minDimension - strokePx) / 2f,
                    alpha = ringAlpha,
                    style = Stroke(width = strokePx),
                )
            }
        }
    }
}

/**
 * Contains default values used by [ColorSwatchPicker].
 */
object ColorSwatchPickerDefaults {

    /** The diameter of each color dot (COUI ux_icon_color_select_inside). */
    val SwatchSize: Dp = 34.dp

    /** The cell size, i.e. the touch target and selection ring outer diameter (COUI ux_icon_color_select_out). */
    val CellSize: Dp = 44.dp

    /** The stroke width of the selection ring (COUI ux_theme_shadow_stroke). */
    val RingStrokeWidth: Dp = 2.dp

    /** The spacing between adjacent cells (COUI lays 44dp cells edge to edge, so dots sit 10dp apart). */
    val Spacing: Dp = 0.dp

    /** The duration of the selection ring fade-in, in milliseconds. */
    val RingFadeInDuration: Int = 280

    /** The duration of the selection ring fade-out, in milliseconds. */
    val RingFadeOutDuration: Int = 150

    /** The easing of the selection ring fade (COUI PathInterpolator(0.33, 0, 0.67, 1)). */
    val RingEasing: CubicBezierEasing = CubicBezierEasing(0.33f, 0f, 0.67f, 1f)
}
