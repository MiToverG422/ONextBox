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

internal class RichTapPattern(source: String) {
    private val root = Json.parseToJsonElement(source).jsonObject

    init {
        require(root.getValue("Metadata").jsonObject.getValue("Version").jsonPrimitive.int == 2)
        require(root.getValue("PatternList").jsonArray.isNotEmpty())
    }

    fun fromPosition(positionMillis: Long): JsonObject? {
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
        return if (patterns.isEmpty()) null else JsonObject(root + ("PatternList" to JsonArray(patterns)))
    }

    fun packet(position: Long, pid: Int, sequence: Int): IntArray? {
        val groups = fromPosition(position)?.getValue("PatternList")?.jsonArray ?: return null
        // A RichTap HE 2 wrapper carries up to ten groups. The bundled tracks have one to three.
        require(groups.size in 1..10)
        return buildList {
            addAll(listOf(2, 2, pid, sequence, (groups.size shl 16) or groups.size))
            groups.forEachIndexed { index, item ->
                val group = item.jsonObject
                val events = group.getValue("Pattern").jsonArray
                addAll(listOf(index, group.int("AbsoluteTime"), events.size))
                events.forEach { addEvent(it.jsonObject.getValue("Event").jsonObject) }
            }
        }.toIntArray()
    }

    private fun MutableList<Int>.addEvent(event: JsonObject) {
        val parameters = event.getValue("Parameters").jsonObject
        val continuous = event.getValue("Type").jsonPrimitive.content == "continuous"
        val curve = if (continuous) parameters.getValue("Curve").jsonArray else null
        add(if (continuous) 4096 else 4097)
        add(if (curve != null) 6 + 3 * curve.size else 5)
        add(event.int("Index"))
        add(event.int("RelativeTime"))
        add(parameters.int("Intensity"))
        add(parameters.int("Frequency"))
        add(if (continuous) event.int("Duration") else 0)
        if (curve != null) {
            add(curve.size)
            for (point in curve) {
                val fields = point.jsonObject
                add(fields.int("Time"))
                add((fields.getValue("Intensity").jsonPrimitive.double * 100).toInt())
                add(fields.int("Frequency"))
            }
        }
    }

    private fun JsonObject.int(key: String) = getValue(key).jsonPrimitive.int

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
