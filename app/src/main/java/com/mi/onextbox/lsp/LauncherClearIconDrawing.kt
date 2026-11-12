package com.mi.onextbox.lsp

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.PathShape
import android.widget.TextView
import kotlin.math.roundToInt

// Clear button icon drawing during entry animations.
internal class LauncherClearIconDrawing(buttonClass: Class<*>) {
    companion object {
        fun createIcon(button: TextView): ShapeDrawable {
            val bar = Path().apply { addRoundRect(7.5f, 0f, 9.5f, 17f, 0.33f, 0.33f, Path.Direction.CW) }
            val cross = Path().apply {
                for (angle in listOf(-45f, 45f)) addPath(bar, Matrix().apply { setRotate(angle, 8.5f, 8.5f) })
            }
            val side = (16 * button.resources.displayMetrics.density).roundToInt()
            return ShapeDrawable(PathShape(cross, 17f, 17f)).apply {
                intrinsicWidth = side
                intrinsicHeight = side
                paint.color = button.currentTextColor
                paint.isAntiAlias = true
            }
        }
    }

    private val getBlocked = buttonClass.getDeclaredMethod("getForceBlockDraw")
    private val setBlocked = buttonClass.getDeclaredMethod("setForceBlockDraw", Boolean::class.javaPrimitiveType)

    init {
        require(getBlocked.returnType == Boolean::class.javaPrimitiveType && setBlocked.returnType == Void.TYPE)
    }

    fun <T> draw(button: Any, draw: () -> T): T {
        val wasBlocked = getBlocked.invoke(button) as Boolean
        if (!wasBlocked) return draw()
        // Allow the icon for this draw without changing the system animation state.
        setBlocked.invoke(button, false)
        return try {
            draw()
        } finally {
            setBlocked.invoke(button, true)
        }
    }
}
