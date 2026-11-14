package com.mi.onextbox.lsp

import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import java.util.WeakHashMap

/** Uses the same G2 path as the QS material; the caller retains the native level clip. */
internal class QsSliderProgressShape {
    private class Shape {
        val path = Path()
        val bounds = Rect()
        var radius = Float.NaN
        var weight: Float? = null
    }

    private val shapes = WeakHashMap<Drawable, Shape>()
    private val wrapper by lazy { Path::class.java.getMethod("getWrapper") }
    private val extension by lazy { wrapper.returnType.getMethod("getPathExt") }
    private val addSmooth by lazy { extension.returnType.getMethod("addSmoothRoundRect16", RectF::class.java,
        FloatArray::class.java, Path.Direction::class.java, Float::class.javaPrimitiveType) }

    fun path(drawable: Drawable, radius: Float, weight: Float?): Path {
        require(radius.isFinite() && radius >= 0f)
        val shape = shapes.getOrPut(drawable) { Shape() }
        val bounds = drawable.bounds
        if (shape.radius == radius && shape.weight == weight && shape.bounds == bounds) return shape.path
        shape.radius = Float.NaN
        replace(shape.path, RectF(bounds), radius, weight)
        check(!shape.path.isEmpty || bounds.isEmpty)
        shape.bounds.set(bounds)
        shape.radius = radius
        shape.weight = weight
        return shape.path
    }

    fun replace(path: Path, rect: RectF, radius: Float, weight: Float?) {
        require(radius.isFinite() && radius >= 0f)
        if (radius > 0f && weight != null && weight > 0f) {
            // OplusPathAdapter can fall back to circular corners at lower animation
            // levels, while the material still uses its explicit G2 CornerParams.
            val native = extension.invoke(wrapper.invoke(path))
            val add = addSmooth
            path.rewind()
            add.invoke(native, rect, FloatArray(8) { radius }, Path.Direction.CW, weight)
        } else {
            path.rewind()
            path.addRoundRect(rect, radius, radius, Path.Direction.CW)
        }
    }
}
