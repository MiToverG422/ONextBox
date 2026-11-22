package com.mi.onextbox.ui.onboarding

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class RichTapPatternTest {
    private fun source(name: String): String = listOf(
        File("src/main/res/raw/c17_$name.he"), File("app/src/main/res/raw/c17_$name.he"),
    ).first { it.isFile }.readText()

    private fun root(pattern: String) = Json.parseToJsonElement(pattern).jsonObject
    private fun firstEvent(pattern: JsonObject): JsonObject = pattern.getValue("PatternList")
        .jsonArray.first().jsonObject.getValue("Pattern").jsonArray.first().jsonObject.getValue("Event").jsonObject

    @Test fun originalTracksKeepEveryEventAndCurveAtTheBeginning() {
        for ((name, count) in listOf("welcome_startup_haptic" to 9, "guide_next_morph_haptic" to 46,
                "vibration_res_of_complete_page" to 15)) {
            val raw = source(name)
            val replay = RichTapPattern(raw).fromPosition(0)!!
            assertEquals(root(raw), replay)
            assertEquals(count, replay.getValue("PatternList").jsonArray.sumOf {
                it.jsonObject.getValue("Pattern").jsonArray.size
            })
        }
    }

    @Test fun firstRenderedFramePreservesTheRemainingDelayBeforeTheFirstPulse() {
        val track = RichTapPattern(source("welcome_startup_haptic"))
        val output = track.fromPosition(120)!!
        assertEquals(440, output.getValue("PatternList").jsonArray.first().jsonObject
            .getValue("AbsoluteTime").jsonPrimitive.int)
        assertEquals(300, firstEvent(output).getValue("Duration").jsonPrimitive.int)
    }

    @Test fun resumingInsideAnEnvelopeInterpolatesIntensityAndFrequency() {
        val output = RichTapPattern(source("welcome_startup_haptic")).fromPosition(611)!!
        val event = firstEvent(output)
        assertEquals(0, event.getValue("RelativeTime").jsonPrimitive.int)
        assertEquals(249, event.getValue("Duration").jsonPrimitive.int)
        val curve = event.getValue("Parameters").jsonObject.getValue("Curve").jsonArray
        assertEquals(0, curve.first().jsonObject.getValue("Time").jsonPrimitive.int)
        assertEquals(0.175, curve.first().jsonObject.getValue("Intensity").jsonPrimitive.double, 0.00001)
        assertEquals(-40, curve.first().jsonObject.getValue("Frequency").jsonPrimitive.int)
        assertEquals(249, curve.last().jsonObject.getValue("Time").jsonPrimitive.int)
    }

    @Test fun resumingDropsExpiredEventsInsteadOfReplayingThemInABurst() {
        val raw = root(source("guide_next_morph_haptic"))
        val position = 10_000L
        val originalEvents = raw.getValue("PatternList").jsonArray.sumOf { it.jsonObject.getValue("Pattern").jsonArray.size }
        val output = RichTapPattern(raw.toString()).fromPosition(position)!!
        val patterns = output.getValue("PatternList").jsonArray
        assertTrue(patterns.sumOf { it.jsonObject.getValue("Pattern").jsonArray.size } < originalEvents)
        for (pattern in patterns) {
            assertTrue(pattern.jsonObject.getValue("AbsoluteTime").jsonPrimitive.int >= 0)
            for (event in pattern.jsonObject.getValue("Pattern").jsonArray) {
                assertTrue(event.jsonObject.getValue("Event").jsonObject.getValue("RelativeTime").jsonPrimitive.int >= 0)
            }
        }
    }

    @Test fun packetRetainsGroupBoundariesAndMotorInstructions() {
        val track = RichTapPattern(source("guide_next_morph_haptic"))
        val packet = track.packet(0, 123, 7)!!
        assertArrayEquals(intArrayOf(2, 2, 123, 7, 0x30003), packet.copyOf(5))
        var offset = 5
        for ((index, group) in root(source("guide_next_morph_haptic")).getValue("PatternList").jsonArray.withIndex()) {
            val events = group.jsonObject.getValue("Pattern").jsonArray
            assertEquals(index, packet[offset++])
            assertEquals(group.jsonObject.getValue("AbsoluteTime").jsonPrimitive.int, packet[offset++])
            assertEquals(events.size, packet[offset++])
            for (wrapped in events) {
                val event = wrapped.jsonObject.getValue("Event").jsonObject
                val params = event.getValue("Parameters").jsonObject
                val curve = params["Curve"]?.jsonArray
                assertEquals(if (curve == null) 4097 else 4096, packet[offset++])
                assertEquals(if (curve == null) 5 else 6 + curve.size * 3, packet[offset++])
                assertEquals(event.getValue("Index").jsonPrimitive.int, packet[offset++])
                assertEquals(event.getValue("RelativeTime").jsonPrimitive.int, packet[offset++])
                assertEquals(params.getValue("Intensity").jsonPrimitive.int, packet[offset++])
                assertEquals(params.getValue("Frequency").jsonPrimitive.int, packet[offset++])
                assertEquals(event["Duration"]?.jsonPrimitive?.int ?: 0, packet[offset++])
                if (curve != null) {
                    assertEquals(curve.size, packet[offset++])
                    for (point in curve) {
                        assertEquals(point.jsonObject.getValue("Time").jsonPrimitive.int, packet[offset++])
                        assertEquals((point.jsonObject.getValue("Intensity").jsonPrimitive.double * 100).toInt(), packet[offset++])
                        assertEquals(point.jsonObject.getValue("Frequency").jsonPrimitive.int, packet[offset++])
                    }
                }
            }
        }
        assertEquals(packet.size, offset)
    }

    @Test fun aFinishedTrackHasNothingToReplayAndNegativePositionsClampToZero() {
        val raw = source("vibration_res_of_complete_page")
        val track = RichTapPattern(raw)
        assertNull(track.fromPosition(60_000))
        assertEquals(root(raw), track.fromPosition(-100))
    }
}
