package com.mi.onextbox.ui.onboarding

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToInt

internal class AnimationHapticPattern(source: String) {
    private val root = Json.parseToJsonElement(source).jsonObject

    init {
        require(root.getValue("Metadata").jsonObject.getValue("Version").jsonPrimitive.int == 2)
        require(root.getValue("PatternList").jsonArray.isNotEmpty())
    }

    fun fromPosition(positionMillis: Long): String? {
        val position = positionMillis.coerceAtLeast(0)
        val patterns = root.getValue("PatternList").jsonArray.mapNotNull { element ->
            val pattern = element.jsonObject
            val absolute = pattern.getValue("AbsoluteTime").jsonPrimitive.int.toLong()
            val skipped = (position - absolute).coerceAtLeast(0)
            val events = pattern.getValue("Pattern").jsonArray.mapNotNull eventLoop@ { wrapper ->
                val event = wrapper.jsonObject.getValue("Event").jsonObject
                val relative = event.getValue("RelativeTime").jsonPrimitive.int.toLong()
                val remaining = if (relative >= skipped) {
                    JsonObject(event + ("RelativeTime" to JsonPrimitive(relative - skipped)))
                } else {
                    if (event.getValue("Type").jsonPrimitive.content != "continuous") return@eventLoop null
                    trimContinuous(event, skipped - relative) ?: return@eventLoop null
                }
                JsonObject(wrapper.jsonObject + ("Event" to remaining))
            }
            if (events.isEmpty()) null else JsonObject(pattern + mapOf(
                "AbsoluteTime" to JsonPrimitive((absolute - position).coerceAtLeast(0)),
                "Pattern" to JsonArray(events),
            ))
        }
        return if (patterns.isEmpty()) null else JsonObject(root + ("PatternList" to JsonArray(patterns))).toString()
    }

    private fun trimContinuous(event: JsonObject, skipped: Long): JsonObject? {
        val duration = event.getValue("Duration").jsonPrimitive.int
        if (skipped >= duration) return null
        val parameters = event.getValue("Parameters").jsonObject
        val curve = parameters.getValue("Curve").jsonArray.map { it.jsonObject }
        val before = curve.last { it.getValue("Time").jsonPrimitive.int <= skipped }
        val after = curve.first { it.getValue("Time").jsonPrimitive.int > skipped }
        val from = before.getValue("Time").jsonPrimitive.int
        val to = after.getValue("Time").jsonPrimitive.int
        val fraction = (skipped - from).toDouble() / (to - from)
        fun interpolated(key: String): Double {
            val a = before.getValue(key).jsonPrimitive.double
            val b = after.getValue(key).jsonPrimitive.double
            return a + (b - a) * fraction
        }
        val points = listOf(JsonObject(mapOf(
            "Time" to JsonPrimitive(0),
            "Intensity" to JsonPrimitive(interpolated("Intensity")),
            "Frequency" to JsonPrimitive(interpolated("Frequency").roundToInt()),
        ))) + curve.filter { it.getValue("Time").jsonPrimitive.int > skipped }.map {
            JsonObject(it + ("Time" to JsonPrimitive(it.getValue("Time").jsonPrimitive.int - skipped)))
        }
        return JsonObject(event + mapOf(
            "RelativeTime" to JsonPrimitive(0),
            "Duration" to JsonPrimitive(duration - skipped),
            "Parameters" to JsonObject(parameters + ("Curve" to JsonArray(points))),
        ))
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
