package com.mi.onextbox.ui.layout

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.min
import kotlin.math.roundToInt

internal fun Modifier.bottomBarStartupReveal(progress: () -> Float): Modifier = graphicsLayer {
    val reveal = progress().coerceIn(0f, 1f)
    alpha = (reveal * 5f).coerceIn(0f, 1f)
    compositingStrategy = CompositingStrategy.ModulateAlpha
}

internal fun Modifier.bottomBarStartupContentClip(progress: () -> Float): Modifier = graphicsLayer {
    val reveal = progress().coerceIn(0f, 1f)
    clip = reveal < 1f
    shape = BottomBarStartupRevealShape(reveal)
    compositingStrategy = CompositingStrategy.ModulateAlpha
}

internal fun Modifier.bottomBarStartupMaterialWidth(progress: () -> Float): Modifier = layout { measurable, constraints ->
    val fullWidth = constraints.maxWidth
    val materialWidth = (fullWidth * progress().coerceIn(0f, 1f)).roundToInt()
        .coerceIn(1, fullWidth.coerceAtLeast(1))
    val placeable = measurable.measure(constraints.copy(minWidth = materialWidth, maxWidth = materialWidth))
    layout(fullWidth, placeable.height) {
        placeable.place((fullWidth - materialWidth) / 2, 0)
    }
}

private data class BottomBarStartupRevealShape(val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val width = size.width * progress
        val left = (size.width - width) / 2f
        val radius = min(width, size.height) / 2f
        return Outline.Rounded(
            RoundRect(
                left = left,
                top = 0f,
                right = left + width,
                bottom = size.height,
                cornerRadius = CornerRadius(radius),
            ),
        )
    }
}
