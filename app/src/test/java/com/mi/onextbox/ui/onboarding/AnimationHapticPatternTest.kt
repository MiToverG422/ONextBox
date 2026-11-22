package com.mi.onextbox.ui.onboarding

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class AnimationHapticPatternTest {
    private fun track(name: String): AnimationHapticPattern {
        val resource = "src/main/res/raw/c17_$name.he"
        val source = listOf(File(resource), File("app/$resource")).first { it.isFile }.readText()
        return AnimationHapticPattern(source)
    }

    @Test fun originalTracksKeepTheirTimingAsPublicWaveforms() {
        for ((name, start, end) in listOf(
            Triple("welcome_startup_haptic", 560L, 6_248L),
            Triple("guide_next_morph_haptic", 1_133L, 15_856L),
            Triple("vibration_res_of_complete_page", 437L, 4_188L),
        )) {
            val waveform = track(name).fromPosition(0)!!
            assertEquals(name, start, waveform.timings.first())
            assertEquals(name, end, waveform.timings.sum())
            assertEquals(name, 0, waveform.amplitudes.first())
            // Very short low-amplitude opening segments may add to the initial silence.
            assertTrue(name, waveform.amplitudes[1] > 0)
            assertEquals(name, waveform.timings.size, waveform.amplitudes.size)
            assertTrue(name, waveform.timings.all { it > 0 })
            assertTrue(name, waveform.amplitudes.all { it in 0..255 })
        }
    }

    @Test fun aLateFirstFrameKeepsOnlyTheRemainingDelayBeforeTheFirstPulse() {
        val waveform = track("welcome_startup_haptic").fromPosition(120)!!
        assertEquals(440L, waveform.timings.first())
        assertEquals(0, waveform.amplitudes.first())
        assertEquals(6_128L, waveform.timings.sum())
    }

    @Test fun resumingInsideAnEnvelopeContinuesFromItsCurrentAmplitude() {
        val waveform = track("welcome_startup_haptic").fromPosition(611)!!
        assertEquals(9L, waveform.timings.first())
        assertTrue(waveform.amplitudes.first() in 27..37)
        assertEquals(5_637L, waveform.timings.sum())
    }

    @Test fun resumingBetweenTransientsDoesNotReplayAnExpiredPulse() {
        val waveform = track("welcome_startup_haptic").fromPosition(900)!!
        assertEquals(0, waveform.amplitudes.first())
        assertTrue(waveform.timings.first() >= 687L)
        assertEquals(5_348L, waveform.timings.sum())
    }

    @Test fun aFinishedTrackHasNothingToReplayAndNegativePositionsClampToZero() {
        val track = track("vibration_res_of_complete_page")
        assertNull(track.fromPosition(60_000))
        assertSame(track.fromPosition(0), track.fromPosition(-100))
    }
}
