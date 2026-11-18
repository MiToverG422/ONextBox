package com.mi.onextbox.ui.onboarding

import org.junit.Assert.*
import org.junit.Test

class AnimationHapticPlaybackTest {
    @Test fun waitsForAVisibleFrameAndDoesNotRestartOnRecompositionOrEveryFrame() {
        val positions = mutableListOf<Long>()
        var reads = 0
        val playback = AnimationHapticPlayback({ positions.add(it) }, {})
        playback.onFrame { ++reads; 0L }
        playback.setAllowed(true)
        assertTrue(positions.isEmpty())
        playback.onFrame { ++reads; 33L }
        repeat(100) {
            playback.setAllowed(true)
            playback.onFrame { ++reads; 500L }
        }
        assertEquals(listOf(33L), positions)
        assertEquals(1, reads)
    }

    @Test fun backgroundingStopsOnceAndResumesFromTheNextActualFrame() {
        val positions = mutableListOf<Long>()
        var stops = 0
        val playback = AnimationHapticPlayback({ positions.add(it) }, { stops++ })
        playback.setAllowed(true)
        playback.onFrame { 20L }
        playback.setAllowed(false)
        playback.setAllowed(false)
        playback.onFrame { error("Background frames must not read the media clock") }
        assertEquals(1, stops)
        playback.setAllowed(true)
        assertEquals(listOf(20L), positions)
        playback.onFrame { 1_200L }
        assertEquals(listOf(20L, 1_200L), positions)
    }

    @Test fun eachVideoLoopResetsTheTrackWithoutDuplicatingStopOrStart() {
        val positions = mutableListOf<Long>()
        var stops = 0
        val playback = AnimationHapticPlayback({ positions.add(it) }, { stops++ })
        playback.setAllowed(true)
        playback.onFrame { 16L }
        playback.reset()
        playback.reset()
        playback.onFrame { 0L }
        playback.onFrame { 100L }
        assertEquals(listOf(16L, 0L), positions)
        assertEquals(1, stops)
    }

    @Test fun hidingOrDisposingBeforeTheFirstFrameProducesNoVibration() {
        val playback = AnimationHapticPlayback({ fail("No visible frame") }, { fail("Nothing was started") })
        playback.setAllowed(true)
        playback.setAllowed(false)
        playback.reset()
        playback.onFrame { error("Disposed") }
    }
}
