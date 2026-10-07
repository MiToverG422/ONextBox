package com.mi.onextbox.ui.common

import androidx.compose.animation.core.CubicBezierEasing

internal object ColorOsScrollEntranceMotion {
    const val Distance = 400f
    const val DurationMillis = 360
    val Easing = CubicBezierEasing(.05f, .15f, .15f, 1f)
}

internal class ColorOsScrollEntranceEntry {
    private var visible: Boolean? = null

    fun update(
        top: Float,
        bottom: Float,
        viewportTop: Float,
        viewportBottom: Float,
        direction: Float,
        scrolling: Boolean,
        baseline: Boolean = false,
    ): Float? {
        val wasVisible = visible
        val isVisible = top.isFinite() && bottom.isFinite() && bottom > top &&
            viewportTop.isFinite() && viewportBottom.isFinite() && viewportBottom > viewportTop &&
            bottom > viewportTop && top < viewportBottom
        visible = isVisible
        if (!isVisible || baseline) return 0f
        if (wasVisible == true || !scrolling || direction == 0f || !direction.isFinite()) return null
        return if (direction < 0f) ColorOsScrollEntranceMotion.Distance else -ColorOsScrollEntranceMotion.Distance
    }

    fun leave() { visible = false }
}
