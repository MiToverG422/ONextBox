// Copyright 2025, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0

package io.github.suqi8.coui.kmp.icon.basic

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.icon.COUIIcons

/**
 * COUI coui_menu_ic_checkbox_selected: a stroked polyline rather than a filled glyph, so the tick
 * keeps a constant 1.4 weight with round caps and joins at any size.
 */
val COUIIcons.Basic.Check: ImageVector
    get() {
        if (_check != null) return _check!!
        _check = ImageVector.Builder("Check", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.4f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5.62f, 12.25f)
                lineToRelative(5.26f, 5.25f)
                lineTo(22.38f, 6f)
            }
        }.build()
        return _check!!
    }

private var _check: ImageVector? = null
