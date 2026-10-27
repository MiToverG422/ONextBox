// Copyright 2025, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.suqi8.coui.kmp.basic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * A divider is a thin line that groups content in lists and layouts.
 *
 * For COUI card lists, place one divider between each pair of adjacent rows inside a [Card]
 * (never after the last row) and inset it with [DividerDefaults.CardInset].
 *
 * @param modifier the [Modifier] to be applied to this divider line.
 * @param thickness thickness of this divider line. Using [Dp.Hairline] will produce a single pixel
 *   divider regardless of screen density.
 * @param color color of this divider line.
 */
@Composable
@NonRestartableComposable
fun HorizontalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = DividerDefaults.Thickness,
    color: Color = DividerDefaults.DividerColor,
) = Canvas(modifier.fillMaxWidth().height(thickness)) {
    drawLine(
        color = color,
        strokeWidth = thickness.toPx(),
        start = Offset(0f, thickness.toPx() / 2),
        end = Offset(size.width, thickness.toPx() / 2),
    )
}

/**
 * A divider is a thin line that groups content in lists and layouts.
 *
 * @param modifier the [Modifier] to be applied to this divider line.
 * @param thickness thickness of this divider line. Using [Dp.Hairline] will produce a single pixel
 *   divider regardless of screen density.
 * @param color color of this divider line.
 */
@Composable
@NonRestartableComposable
fun VerticalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = DividerDefaults.Thickness,
    color: Color = DividerDefaults.DividerColor,
) = Canvas(modifier.fillMaxHeight().width(thickness)) {
    drawLine(
        color = color,
        strokeWidth = thickness.toPx(),
        start = Offset(thickness.toPx() / 2, 0f),
        end = Offset(thickness.toPx() / 2, size.height),
    )
}

object DividerDefaults {

    /**
     * Default thickness of the divider line (COUI coui_list_divider_height).
     */
    val Thickness = 0.33.dp

    /**
     * Default color of the divider line.
     */
    val DividerColor @Composable get() = COUITheme.colorScheme.dividerLine

    /**
     * Recommended horizontal inset for a [HorizontalDivider] placed between rows inside a [Card]
     * (COUI coui_preference_divider_default_horizontal_padding). For rows with a leading icon,
     * align the start inset with the title text start instead.
     *
     * Usage: `HorizontalDivider(Modifier.padding(horizontal = DividerDefaults.CardInset))`.
     */
    val CardInset = 16.dp
}
