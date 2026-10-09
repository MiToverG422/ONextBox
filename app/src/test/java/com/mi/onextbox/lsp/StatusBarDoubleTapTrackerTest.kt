package com.mi.onextbox.lsp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusBarDoubleTapTrackerTest {
    private val tracker = StatusBarDoubleTapTracker()
    private fun up(time: Long, x: Float = 10f, y: Float = 10f): Boolean =
        tracker.onUp(time, x, y, 8f, 400L, 300L, 100f)

    private fun tap(down: Long, up: Long, x: Float = 10f, y: Float = 10f): Boolean {
        tracker.onDown(down, x, y)
        return up(up, x, y)
    }

    @Test fun aSingleTapNeverTriggersScrollToTop() {
        assertFalse(tap(1000L, 1050L))
    }

    @Test fun twoCompletedTapsTriggerOnlyOnce() {
        assertFalse(tap(1000L, 1050L))
        assertTrue(tap(1150L, 1200L))
        assertFalse(tap(1250L, 1300L))
        assertTrue(tap(1350L, 1400L))
    }

    @Test fun anExpiredFirstTapMakesTheNextTapANewPair() {
        assertFalse(tap(1000L, 1050L))
        assertFalse(tap(1351L, 1400L))
        assertTrue(tap(1500L, 1550L))
    }

    @Test fun tapsInDistantLocationsCannotBePaired() {
        assertFalse(tap(1000L, 1050L))
        assertFalse(tap(1150L, 1200L, 150f, 10f))
        assertTrue(tap(1250L, 1300L, 155f, 10f))
    }

    @Test fun aDragThatReturnsToItsStartStillCancelsThePair() {
        assertFalse(tap(1000L, 1050L))
        tracker.onDown(1150L, 10f, 10f)
        tracker.onMove(10f, 60f, 8f)
        tracker.onMove(10f, 10f, 8f)
        assertFalse(up(1200L))
        assertFalse(tap(1250L, 1300L))
    }

    @Test fun diagonalMotionOutsideTouchSlopCancelsThePair() {
        assertFalse(tap(1000L, 1050L))
        tracker.onDown(1150L, 10f, 10f)
        tracker.onMove(16f, 16f, 8f)
        assertFalse(up(1200L, 16f, 16f))
    }

    @Test fun aLongPressDoesNotCountAsEitherTap() {
        assertFalse(tap(1000L, 1450L))
        assertFalse(tap(1500L, 1550L))
        assertTrue(tap(1650L, 1700L))
    }

    @Test fun cancellationOrAnAdditionalPointerClearsBothTaps() {
        assertFalse(tap(1000L, 1050L))
        tracker.onDown(1150L, 10f, 10f)
        tracker.cancel()
        assertFalse(up(1200L))
        assertFalse(tap(1250L, 1300L))
    }

    @Test fun aLostUpOrAnUpWithoutDownNeverCompletesAPair() {
        assertFalse(up(1000L))
        assertFalse(tap(1100L, 1150L))
        tracker.onDown(1200L, 10f, 10f)
        tracker.onDown(1250L, 10f, 10f)
        assertFalse(up(1300L))
    }

    @Test fun staleOrDuplicatedEventTimesCannotBePaired() {
        assertFalse(tap(1000L, 1050L))
        assertFalse(tap(1050L, 1100L))
        tracker.cancel()
        tracker.onDown(1500L, 10f, 10f)
        assertFalse(up(1499L))
    }

    @Test fun smallFingerMotionAndTheTimeoutBoundaryAreAccepted() {
        tracker.onDown(1000L, 10f, 10f)
        tracker.onMove(13f, 14f, 8f)
        assertFalse(up(1050L, 13f, 14f))
        assertTrue(tap(1350L, 1400L, 20f, 20f))
    }
}
