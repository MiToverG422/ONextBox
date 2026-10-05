package com.mi.onextbox.ui.common

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

internal class ColorOsDialogButtonMotion(private val density: Float) {
    var translationX by mutableFloatStateOf(0f)
        private set
    var translationY by mutableFloatStateOf(0f)
        private set
    var scaleX by mutableFloatStateOf(1f)
        private set
    var scaleY by mutableFloatStateOf(1f)
        private set

    private val xSpring = spring(0f, .5f) { translationX = it }
    private val ySpring = spring(0f, .5f) { translationY = it }
    private val scaleXSpring = spring(1f, .0005f) { scaleX = it }
    private val scaleYSpring = spring(1f, .0005f) { scaleY = it }
    private val springs = listOf(xSpring, ySpring, scaleXSpring, scaleYSpring)
    private val maxStretch = 28f * density
    private val maxDeform = 150f * density
    private var baseDelta = Offset.Zero
    private var dragging = false

    private fun spring(initial: Float, threshold: Float, update: (Float) -> Unit) =
        SpringAnimation(FloatValueHolder(initial)).apply {
            spring = SpringForce(initial)
            minimumVisibleChange = threshold
            addUpdateListener { _, value, _ -> update(value) }
        }

    private fun configure(animation: SpringAnimation, response: Float, bounce: Float) {
        animation.spring.stiffness = 39.47f / (response * response)
        animation.spring.dampingRatio = 1f - bounce * .5f
    }

    fun begin() {
        springs.forEach { it.cancel(); configure(it, .15f, .15f) }
        xSpring.setStartValue(translationX)
        ySpring.setStartValue(translationY)
        scaleXSpring.setStartValue(scaleX)
        scaleYSpring.setStartValue(scaleY)
        baseDelta = Offset(inverseRubber(translationX), inverseRubber(translationY))
        dragging = true
    }

    fun drag(delta: Offset, size: IntSize) {
        if (!dragging || (abs(delta.x) < 5f && abs(delta.y) < 5f)) return
        val x = delta.x + baseDelta.x
        val y = delta.y + baseDelta.y
        xSpring.animateToFinalPosition(rubber(x, .05f, maxStretch))
        ySpring.animateToFinalPosition(rubber(y, .05f, maxStretch))
        val maxScale = maximumScale(size)
        val xScale = 1f + abs(rubber(x, .55f, maxDeform)) / maxDeform * (maxScale - 1f)
        val yScale = 1f + abs(rubber(y, .55f, maxDeform)) / maxDeform * (maxScale - 1f)
        val total = abs(x) + abs(y)
        if (total > 0f) {
            val stretch = ln(xScale) * abs(x) / total - ln(yScale) * abs(y) / total
            scaleXSpring.animateToFinalPosition(exp(stretch))
            scaleYSpring.animateToFinalPosition(exp(-stretch))
        }
    }

    fun release(size: IntSize, velocity: Offset = Offset.Zero) {
        if (!dragging) return
        dragging = false
        val areaDp = size.width.toFloat() * size.height / (density * density)
        val areaProgress = ((areaDp - 56f * 56f) / (100f * 100f - 56f * 56f)).coerceIn(0f, 1f)
        val response = .42f + .03f * HandUpAreaEasing.transform(areaProgress)
        releaseAxis(xSpring, translationX, 0f, velocity.x, response)
        releaseAxis(ySpring, translationY, 0f, velocity.y, response)
        releaseAxis(scaleXSpring, scaleX, 1f, velocity.x, response)
        releaseAxis(scaleYSpring, scaleY, 1f, velocity.y, response)
    }

    private fun releaseAxis(animation: SpringAnimation, value: Float, rest: Float, velocity: Float, response: Float) {
        val initialVelocity = (velocity * .3f / maxStretch * abs(value - rest)).coerceIn(-1000f, 1000f)
        configure(animation, response, max(.65f, abs(initialVelocity) / 1000f))
        if (!animation.isRunning) {
            animation.setStartValue(value)
        }
        animation.setStartVelocity(initialVelocity)
        animation.animateToFinalPosition(rest)
    }

    fun localSpotlightPosition(position: Offset, size: IntSize): Offset {
        val center = Offset(size.width / 2f, size.height / 2f)
        return Offset(
            (position.x - center.x - translationX) / scaleX + center.x,
            (position.y - center.y - translationY) / scaleY + center.y,
        )
    }

    fun dispose() {
        dragging = false
        springs.forEach { it.cancel() }
    }

    private fun maximumScale(size: IntSize): Float {
        val width = size.width.toFloat()
        val height = size.height.toFloat()
        val area = width * height
        val shortSide = min(width, height)
        if (shortSide <= 0f || area <= 38416f) return 1.2f
        val attenuation = min(.35f * ln(area / 38416f) + .015f * (max(width, height) / shortSide - 1f), 1f)
        return max(.17f * (1f - attenuation) + 1.03f, 1.03f)
    }

    private fun rubber(value: Float, ratio: Float, limit: Float): Float =
        sign(value) * (1f - 1f / (abs(value) * ratio / limit + 1f)) * limit

    private fun inverseRubber(value: Float): Float {
        val distance = abs(value).coerceAtMost(maxStretch - .001f)
        return sign(value) * distance * maxStretch / (.05f * (maxStretch - distance))
    }

    private companion object {
        val HandUpAreaEasing = CubicBezierEasing(.55026454f, 0f, .8915344f, .18783069f)
    }
}
