package com.mi.onextbox.ui.common

import android.graphics.Path
import android.graphics.RectF
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

internal data class ColorOs17CardShape(
    private val topRadius: Dp,
    private val bottomRadius: Dp,
    private val preferenceGroup: Boolean,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val top = with(density) { topRadius.toPx() }.coerceAtLeast(0f)
        val bottom = with(density) { bottomRadius.toPx() }.coerceAtLeast(0f)
        if (size.width <= 0f || size.height <= 0f || (top == 0f && bottom == 0f)) {
            return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        }

        nativeCardPath?.let { (constructor, addSmoothRoundRect) ->
            val path = Path()
            val bounds = RectF(0f, 0f, size.width, size.height)
            val radii = floatArrayOf(top, top, top, top, bottom, bottom, bottom, bottom)
            val rounded = runCatching {
                val adapter = constructor.newInstance(path, 1)
                val weight = if (preferenceGroup) 2.5f else 3f
                addSmoothRoundRect.invoke(adapter, bounds, radii, Path.Direction.CCW, weight)
                !path.isEmpty
            }.getOrDefault(false)
            if (rounded) return Outline.Generic(path.asComposePath())
        }

        return RoundedCornerShape(topRadius, topRadius, bottomRadius, bottomRadius)
            .createOutline(size, layoutDirection, density)
    }
}

private val nativeCardPath by lazy {
    runCatching {
        val adapter = Class.forName("com.oplus.graphics.OplusPathAdapter")
        adapter.getConstructor(Path::class.java, Int::class.javaPrimitiveType) to adapter.getMethod(
            "addSmoothRoundRect",
            RectF::class.java,
            FloatArray::class.java,
            Path.Direction::class.java,
            Float::class.javaPrimitiveType,
        )
    }.getOrNull()
}
