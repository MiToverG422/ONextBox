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

class AnimationHapticPatternTest {
    private fun source(name: String): String = listOf(
        File("src/main/res/raw/c17_$name.he"), File("app/src/main/res/raw/c17_$name.he"),
    ).first { it.isFile }.readText()

    private fun root(pattern: String) = Json.parseToJsonElement(pattern).jsonObject
    private fun firstEvent(pattern: String): JsonObject = root(pattern).getValue("PatternList")
        .jsonArray.first().jsonObject.getValue("Pattern").jsonArray.first().jsonObject.getValue("Event").jsonObject

    @Test fun originalTracksKeepEveryEventAndCurveAtTheBeginning() {
        for ((name, count) in listOf("welcome_startup_haptic" to 9, "guide_next_morph_haptic" to 46,
                "vibration_res_of_complete_page" to 15)) {
            val raw = source(name)
            val replay = AnimationHapticPattern(raw).fromPosition(0)!!
            assertEquals(root(raw), root(replay))
            assertEquals(count, root(replay).getValue("PatternList").jsonArray.sumOf {
                it.jsonObject.getValue("Pattern").jsonArray.size
            })
        }
    }

    @Test fun firstRenderedFramePreservesTheRemainingDelayBeforeTheFirstPulse() {
        val track = AnimationHapticPattern(source("welcome_startup_haptic"))
        val output = root(track.fromPosition(120)!!)
        assertEquals(440, output.getValue("PatternList").jsonArray.first().jsonObject
            .getValue("AbsoluteTime").jsonPrimitive.int)
        assertEquals(300, firstEvent(output.toString()).getValue("Duration").jsonPrimitive.int)
    }

    @Test fun resumingInsideAnEnvelopeInterpolatesIntensityAndFrequency() {
        val output = AnimationHapticPattern(source("welcome_startup_haptic")).fromPosition(611)!!
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
        val output = root(AnimationHapticPattern(raw.toString()).fromPosition(position)!!)
        val patterns = output.getValue("PatternList").jsonArray
        assertTrue(patterns.sumOf { it.jsonObject.getValue("Pattern").jsonArray.size } < originalEvents)
        for (pattern in patterns) {
            assertTrue(pattern.jsonObject.getValue("AbsoluteTime").jsonPrimitive.int >= 0)
            for (event in pattern.jsonObject.getValue("Pattern").jsonArray) {
                assertTrue(event.jsonObject.getValue("Event").jsonObject.getValue("RelativeTime").jsonPrimitive.int >= 0)
            }
        }
    }

    @Test fun aFinishedTrackHasNothingToReplayAndNegativePositionsClampToZero() {
        val raw = source("vibration_res_of_complete_page")
        val track = AnimationHapticPattern(raw)
        assertNull(track.fromPosition(60_000))
        assertEquals(root(raw), root(track.fromPosition(-100)!!))
    }
}
