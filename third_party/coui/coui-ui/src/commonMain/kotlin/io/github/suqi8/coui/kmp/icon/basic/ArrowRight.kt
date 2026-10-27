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
 * COUI jump/next arrow, ported 1:1 from ColorOS coui_btn_next_normal.xml
 * (12x24dp viewport, 1.4dp round-cap stroke, autoMirrored selector).
 * Render at its native 12x24dp size and tint with couiColorLabelTertiary.
 */
val COUIIcons.Basic.ArrowRight: ImageVector
    get() {
        if (_arrowRight != null) return _arrowRight!!
        _arrowRight = ImageVector.Builder("ArrowRight", 12.dp, 24.dp, 12f, 24f, autoMirror = true).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.4f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(5f, 6f)
                lineTo(10.646f, 11.646f)
                curveTo(10.842f, 11.842f, 10.842f, 12.158f, 10.646f, 12.354f)
                lineTo(5f, 18f)
            }
        }.build()
        return _arrowRight!!
    }

private var _arrowRight: ImageVector? = null
