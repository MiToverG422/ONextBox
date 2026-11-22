package com.mi.onextbox.ui.onboarding

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToInt

internal class AnimationHapticWaveform(val timings: LongArray, val amplitudes: IntArray)

internal class AnimationHapticPattern(source: String) {
    private val waveform: AnimationHapticWaveform

    init {
        val root = Json.parseToJsonElement(source).jsonObject
        require(root.getValue("Metadata").jsonObject.getValue("Version").jsonPrimitive.int == 2)
        val events = root.getValue("PatternList").jsonArray.flatMap { element ->
            val pattern = element.jsonObject
            val absolute = pattern.getValue("AbsoluteTime").jsonPrimitive.int.toLong()
            pattern.getValue("Pattern").jsonArray.map { wrapper ->
                val event = wrapper.jsonObject.getValue("Event").jsonObject
                val relative = event.getValue("RelativeTime").jsonPrimitive.int
                (absolute + relative) to event
            }
        }.sortedBy { it.first }

        val timings = mutableListOf<Long>()
        val amplitudes = mutableListOf<Int>()
        fun append(duration: Long, amplitude: Int) {
            if (duration == 0L) return
            if (amplitudes.lastOrNull() == amplitude) {
                timings[timings.lastIndex] += duration
            } else {
                timings += duration
                amplitudes += amplitude
            }
        }

        var position = 0L
        for ((start, event) in events) {
            require(start >= position)
            append(start - position, 0)
            position = start
            val parameters = event.getValue("Parameters").jsonObject
            val peak = parameters.getValue("Intensity").jsonPrimitive.double * 255 / 100
            when (event.getValue("Type").jsonPrimitive.content) {
                "transient" -> {
                    append(TransientDurationMillis, peak.toVibrationAmplitude())
                    position += TransientDurationMillis
                }
                "continuous" -> {
                    val duration = event.getValue("Duration").jsonPrimitive.int
                    val curve = parameters.getValue("Curve").jsonArray.map { point ->
                        val fields = point.jsonObject
                        fields.getValue("Time").jsonPrimitive.int to fields.getValue("Intensity").jsonPrimitive.double
                    }
                    require(curve.first().first == 0 && curve.last().first == duration)
                    for ((before, after) in curve.zipWithNext()) {
                        val (from, low) = before
                        val (to, high) = after
                        require(to > from)
                        for (sample in from until to step EnvelopeSampleMillis) {
                            val length = minOf(EnvelopeSampleMillis, to - sample)
                            val fraction = (sample - from + length / 2.0) / (to - from)
                            append(length.toLong(), (peak * (low + (high - low) * fraction)).toVibrationAmplitude())
                        }
                    }
                    position += duration
                }
                else -> error("Unknown haptic event type")
            }
        }

        val activeSegments = amplitudes.indexOfLast { it > 0 } + 1
        require(activeSegments > 0)
        waveform = AnimationHapticWaveform(
            timings.take(activeSegments).toLongArray(),
            amplitudes.take(activeSegments).toIntArray(),
        )
    }

    fun fromPosition(positionMillis: Long): AnimationHapticWaveform? {
        var remaining = positionMillis.coerceAtLeast(0)
        if (remaining == 0L) return waveform
        waveform.timings.forEachIndexed { index, timing ->
            if (remaining < timing) {
                val timings = waveform.timings.copyOfRange(index, waveform.timings.size)
                timings[0] -= remaining
                return AnimationHapticWaveform(timings, waveform.amplitudes.copyOfRange(index, waveform.amplitudes.size))
            }
            remaining -= timing
        }
        return null
    }

    private fun Double.toVibrationAmplitude() = roundToInt().coerceIn(0, 255)

    private companion object {
        const val TransientDurationMillis = 18L
        const val EnvelopeSampleMillis = 12
    }
}

internal class AnimationHapticPlayback(
    private val play: (Long) -> Unit,
    private val stop: () -> Unit,
) {
    private var allowed = false
    private var awaitingFrame = true
    private var playing = false

    fun setAllowed(value: Boolean) {
        if (allowed == value) return
        allowed = value
        if (!value) reset() else awaitingFrame = true
    }

    fun onFrame(positionMillis: () -> Long) {
        if (!allowed || !awaitingFrame) return
        awaitingFrame = false
        playing = true
        play(positionMillis().coerceAtLeast(0))
    }

    fun reset() {
        awaitingFrame = true
        if (playing) {
            playing = false
            stop()
        }
    }
}
