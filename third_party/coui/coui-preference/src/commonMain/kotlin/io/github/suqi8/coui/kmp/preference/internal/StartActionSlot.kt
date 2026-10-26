// Copyright 2026, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.suqi8.coui.kmp.preference.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
@NonRestartableComposable
internal fun StartActionSlot(
    modifier: Modifier = Modifier,
    startSpacing: Dp = 0.dp,
    endSpacing: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier,
        content = content,
    ) { measurables, constraints ->
        val measurable = measurables.firstOrNull() ?: return@Layout layout(0, 0) {}
        val placeable = measurable.measure(constraints.copy(minWidth = 0))
        val hasContent = placeable.width > 0
        val startPx = if (hasContent) startSpacing.roundToPx() else 0
        val endPx = if (hasContent) endSpacing.roundToPx() else 0
        val rawWidth = placeable.width + startPx + endPx
        val width = if (constraints.maxWidth == Constraints.Infinity) {
            rawWidth
        } else {
            rawWidth.coerceAtMost(constraints.maxWidth)
        }
        layout(width, placeable.height) {
            placeable.place(startPx, 0)
        }
    }
}
