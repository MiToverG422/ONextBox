package com.mi.onextbox.lsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

internal data class LauncherIosDeformation(
    val topOffset: Float,
    val bottomOffset: Float,
    val topWidth: Float,
    val bottomWidth: Float,
    val topCenter: Float,
    val bottomCenter: Float
) {
    fun isVisible(width:Float,height:Float):Boolean =
        maxOf(abs(topOffset),abs(bottomOffset))*height>=.35f ||
            maxOf(abs(topCenter-.5f),abs(bottomCenter-.5f),
                abs(topWidth-1f)*.5f,abs(bottomWidth-1f)*.5f)*width>=.35f
    fun settleAtFullscreen(minimized: Double): LauncherIosDeformation? {
        val phase = (minimized / .08).coerceIn(0.0, 1.0).toFloat()
        if (phase >= 1f) return this
        if (phase <= 0f) return null
        val weight = phase * phase * (3f - 2f * phase)
        return LauncherIosDeformation(topOffset * weight, bottomOffset * weight,
            1f + (topWidth - 1f) * weight, 1f + (bottomWidth - 1f) * weight,
            .5f + (topCenter - .5f) * weight, .5f + (bottomCenter - .5f) * weight)
    }
    fun writeMatrix(out: FloatArray, left: Float, top: Float, width: Float, height: Float) {
        val bottom = top + height
        val topWeight = 1f / topWidth
        val bottomWeight = 1f / bottomWidth
        val weightSlope = (bottomWeight - topWeight) / height
        val topY = (top + topOffset * height) * topWeight
        val bottomY = (bottom + bottomOffset * height) * bottomWeight
        val sourceCenter = left + width * .5f
        val topX = (left + topCenter * width) * topWeight - sourceCenter
        val bottomX = (left + bottomCenter * width) * bottomWeight - sourceCenter
        val xSlope = (bottomX - topX) / height
        val ySlope = (bottomY - topY) / height

        out.fill(0f)
        out[0] = 1f
        out[1] = xSlope
        out[3] = topX - xSlope * top
        out[5] = ySlope
        out[7] = topY - ySlope * top
        out[10] = 1f
        out[13] = weightSlope
        out[15] = topWeight - weightSlope * top
    }

    companion object {
        fun droplet(progress: Float, opening: Boolean): Float {
            val elapsed = if (opening) progress else 1f - progress
            val release = if (opening) .46f else .65f
            return smoothstep(elapsed / .18f) *
                (1f - smoothstep((elapsed - release) / .19f))
        }

        private fun smoothstep(value: Float): Float {
            val t = value.coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }
    }
}
internal class LauncherIosGenieMotion {
    private class Axis {
        var position = 0.0
        var velocity = 0.0
        private var nextVelocity = 0.0
        val evaluatedVelocity get() = nextVelocity

        fun evaluate(target: Double, frequency: Double, damping: Double, dt: Double): Double {
            val displacement = position - target
            if (damping == 1.0) {
                val slope = velocity + frequency * displacement
                val decay = exp(-frequency * dt)
                nextVelocity = (velocity - frequency * slope * dt) * decay
                return target + (displacement + slope * dt) * decay
            }
            val spread = sqrt(damping * damping - 1.0)
            val slow = frequency * (spread - damping)
            val fast = -frequency * (damping + spread)
            val coefficient = (velocity - fast * displacement) / (slow - fast)
            val slowTerm = coefficient * exp(slow * dt)
            val fastTerm = (displacement - coefficient) * exp(fast * dt)
            nextVelocity = slow * slowTerm + fast * fastTerm
            return target + slowTerm + fastTerm
        }

        fun advance(target: Double, frequency: Double, damping: Double, dt: Double) {
            position = evaluate(target, frequency, damping, dt)
            velocity = nextVelocity
        }

        fun reset(value: Double) {
            position = value
            velocity = 0.0
        }
    }

    private val bounds = Axis()
    private val rows = Array(3) { Axis() }
    private var lastFrame = 0L
    private var lastTarget = Double.NaN
    private var lastMeasured = Double.NaN
    private var directionAnchor = Double.NaN
    private var nominalOpening = false
    private var lastShape: LauncherIosDeformation? = null

    @Synchronized
    fun rebaseClock(frameTime: Long) {
        if (lastFrame != 0L && frameTime > lastFrame) lastFrame = frameTime
    }

    @Synchronized
    fun update(
        minimized: Double, iconLeft: Double, iconWidth: Double, screenY: Double,
        intensity: Double, opening: Boolean, openResponse: Double, closeResponse: Double,
        damping: Double, frameTime: Long, preserveState: Boolean = false,
    ): LauncherIosDeformation? {
        if (lastFrame != 0L && frameTime <= lastFrame) return lastShape
        val fresh = lastFrame == 0L || (!preserveState && frameTime - lastFrame > 250_000_000L)
        val dt = if (fresh) 1.0 / 120 else ((frameTime - lastFrame) * 1e-9).coerceIn(0.0, .05)
        val target = targetForBounds(minimized,opening,fresh)
        val duplicate = !fresh && target == lastTarget && minimized == lastMeasured && dt < .003
        if (fresh) {
            val start = if (abs(minimized - target) < .001) target else 1.0 - target
            bounds.reset(start)
            rows.forEach { it.reset(start) }
        }
        if (!duplicate) {
            lastFrame = frameTime
            lastTarget = target
            lastMeasured = minimized
            val frequency = 2 * PI / if (target == 0.0) openResponse else closeResponse
            val direction = if (target == 0.0) -1.0 else 1.0
            val time = followBounds(minimized, target, direction, frequency, damping, dt, fresh)
            bounds.advance(target, frequency, damping, time)
            val amount = screenY.coerceIn(-1.0, 1.0).let { if (it < 0) .75 * it else it }
            for (index in rows.indices) {
                val row = index * .5
                val weight = if (amount >= 0) sqrt(row) else 1.0 - sqrt(1.0 - row)
                val relativeResponse = 1.0 + .5 * amount * direction * (1.0 - 2.0 * weight)
                rows[index].advance(target, frequency / relativeResponse, damping, time)
            }
        }
        lastShape = if (intensity > 0.0) project(iconLeft, iconWidth, intensity) else null
        return lastShape
    }
    private fun targetForBounds(measured:Double,opening:Boolean,fresh:Boolean):Double {
        if(fresh || opening!=nominalOpening){
            nominalOpening=opening
            directionAnchor=measured
            return if(opening)0.0 else 1.0
        }
        if(lastTarget==0.0){
            directionAnchor=minOf(directionAnchor,measured)
            if(measured-directionAnchor>.018 && measured>.025){
                directionAnchor=measured
                return 1.0
            }
        }else{
            directionAnchor=maxOf(directionAnchor,measured)
            if(directionAnchor-measured>.018 && measured<.975){
                directionAnchor=measured
                return 0.0
            }
        }
        return lastTarget
    }

    private fun followBounds(
        measured: Double, target: Double, direction: Double, frequency: Double,
        damping: Double, dt: Double, fresh: Boolean
    ): Double {
        if (abs(measured - target) < .001 || bounds.velocity * direction < -.001) return dt
        val distance = (measured - bounds.position) * direction
        if (distance <= 0) return if (distance > -.02) 0.0 else dt
        var high = if (fresh) 24 / frequency else maxOf(.1, dt * 4)
        if ((bounds.evaluate(target, frequency, damping, high) - measured) * direction < 0) return dt
        var low = 0.0
        var time=dt.coerceIn(0.0,high)
        repeat(8) {
            val error=(bounds.evaluate(target,frequency,damping,time)-measured)*direction
            if(abs(error)<.00008)return time
            if(error<0)low=time else high=time
            val slope=bounds.evaluatedVelocity*direction
            val candidate=if(slope>0)time-error/slope else Double.NaN
            time=if(candidate>low && candidate<high)candidate else (low+high)*.5
        }
        return time
    }

    private fun project(iconLeft: Double, iconWidth: Double, intensity: Double): LauncherIosDeformation? {
        val b = bounds.position.coerceIn(0.0, 1.0)
        val icon = (iconWidth / .4).coerceIn(.05, .95)
        val scale = (1.0 - b + b * icon) / (1.0 - b + b * icon * .4)
        val centerMinimized = rows[1].position
        val topMinimized = rows[0].position
        val bottomMinimized = rows[2].position
        val pivot = (iconLeft.coerceIn(0.0, 1.0) - .5) * .6
        val centerOffset = pivot * (centerMinimized - b)
        val top = intensity * .3 * scale * (topMinimized - b)
        val bottom = -intensity * .3 * scale * (bottomMinimized - b)
        val topX = intensity * (centerOffset + scale * pivot * (topMinimized - centerMinimized))
        val bottomX = intensity * (centerOffset + scale * pivot * (bottomMinimized - centerMinimized))
        if (maxOf(abs(top), abs(bottom), abs(topX), abs(bottomX)) < .00002) return null
        return LauncherIosDeformation(top.toFloat(), bottom.toFloat(),
            (1.0 - 2.0 * top).coerceAtLeast(.2).toFloat(),
            (1.0 + 2.0 * bottom).coerceAtLeast(.2).toFloat(),
            (.5 + topX).toFloat(), (.5 + bottomX).toFloat())
    }
}
