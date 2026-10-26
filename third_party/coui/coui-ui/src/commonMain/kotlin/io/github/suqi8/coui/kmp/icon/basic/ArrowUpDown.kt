// Copyright 2025, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0

package io.github.suqi8.coui.kmp.icon.basic

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.icon.COUIIcons

/**
 * COUI menu/spinner up-down indicator, ported 1:1 from ColorOS coui_pop_up_next_normal.xml
 * (14x24dp viewport, two 1.4dp round-cap stroke chevrons, autoMirrored selector).
 * Render at its native 14x24dp size and tint with couiColorLabelTertiary.
 */
val COUIIcons.Basic.ArrowUpDown: ImageVector
    get() {
        if (_arrowUpDown != null) return _arrowUpDown!!
        _arrowUpDown = ImageVector.Builder("ArrowUpDown", 14.dp, 24.dp, 14f, 24f, autoMirror = true).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.4f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(4.199f, 10f)
                lineTo(8.328f, 5.413f)
                curveTo(8.526f, 5.192f, 8.872f, 5.192f, 9.071f, 5.413f)
                lineTo(13.199f, 10f)
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.4f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(13.199f, 14f)
                lineTo(9.071f, 18.587f)
                curveTo(8.872f, 18.808f, 8.526f, 18.808f, 8.328f, 18.587f)
                lineTo(4.199f, 14f)
            }
        }.build()
        return _arrowUpDown!!
    }

private var _arrowUpDown: ImageVector? = null
