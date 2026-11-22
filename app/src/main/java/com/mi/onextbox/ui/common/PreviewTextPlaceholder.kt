package com.mi.onextbox.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

internal val LocalPreviewTextPlaceholders = staticCompositionLocalOf { false }

@Composable
internal fun PreviewTextLine(width: Dp, height: Dp, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(width, height).background(color, CircleShape))
}
