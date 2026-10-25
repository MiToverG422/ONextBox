package com.mi.onextbox.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

internal class ColorOsTopBarMotion(density: Float) {
    private val drag = ColorOsDialogButtonMotion(density)
    private val press = TopBarSpring(1f)
    private val mask = TopBarSpring(0f)

    val scaleX get() = press.value * drag.scaleX
    val scaleY get() = press.value * drag.scaleY
    val translationX get() = drag.translationX
    val translationY get() = drag.translationY
    val maskProgress get() = mask.value.coerceIn(0f, 1f)

    fun begin() {
        drag.begin()
        press.to(0.88858957f, stiffness = 631.655f, damping = 1f)
        mask.to(1f, stiffness = 438.649f, damping = 1f)
    }

    fun move(delta: Offset, size: IntSize) = drag.drag(delta, size)

    fun release(size: IntSize, velocity: Offset = Offset.Zero) {
        drag.release(size, velocity)
        press.to(1f, stiffness = 157.914f, damping = 0.4f)
        // Only the geometry bounces; the highlight must fade without a brightness spike.
        mask.to(0f, stiffness = 438.649f, damping = 1f, resetVelocity = true)
    }

    fun localPosition(position: Offset, size: IntSize): Offset = colorOsTopBarLocalPosition(
        position, size, scaleX, scaleY, translationX, translationY,
    )

    fun dispose() {
        drag.dispose()
        press.cancel()
        mask.cancel()
    }
}

internal fun colorOsTopBarLocalPosition(
    position: Offset,
    size: IntSize,
    scaleX: Float,
    scaleY: Float,
    translationX: Float,
    translationY: Float,
): Offset {
    val center = Offset(size.width / 2f, size.height / 2f)
    return Offset(
        center.x + (position.x - center.x - translationX) / scaleX.coerceAtLeast(.001f),
        center.y + (position.y - center.y - translationY) / scaleY.coerceAtLeast(.001f),
    )
}

private class TopBarSpring(initial: Float) {
    var value by mutableFloatStateOf(initial)
        private set
    private val animation = SpringAnimation(FloatValueHolder(initial)).apply {
        minimumVisibleChange = .0001f
        spring = SpringForce(initial)
        addUpdateListener { _, position, _ -> value = position }
    }

    fun to(target: Float, stiffness: Float, damping: Float, resetVelocity: Boolean = false) {
        if (resetVelocity) {
            animation.cancel()
            animation.setStartValue(value)
            animation.setStartVelocity(0f)
        }
        animation.spring.stiffness = stiffness
        animation.spring.dampingRatio = damping
        animation.animateToFinalPosition(target)
    }

    fun cancel() = animation.cancel()
}
