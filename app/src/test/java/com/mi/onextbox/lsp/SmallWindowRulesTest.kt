package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class SmallWindowRulesTest {
    @Test fun recentsClassificationDoesNotHideSplitOrEmbeddedTasks() {
        assertTrue(SmallWindowRules.isSmallWindowTask(100, 0, false, false))
        assertTrue(SmallWindowRules.isSmallWindowTask(1, 1, false, true))
        assertFalse(SmallWindowRules.isSmallWindowTask(120, 1, false, true))
        assertFalse(SmallWindowRules.isSmallWindowTask(1, 1, true, true))
        assertFalse(SmallWindowRules.isSmallWindowTask(1, 1, false, false))
        assertFalse(SmallWindowRules.isSmallWindowTask(1, 0, false, true))
    }

    @Test fun stashedPolicyExcludesSystemAndVisibleSharedUidsAcrossUsers() {
        assertEquals(setOf(10001, 110002), SmallWindowRules.exclusiveStashedUids(
            setOf(1000, 10001, 10002, 101000, 110002, -1), setOf(10002)))
    }

    @Test fun landscapeRatioIsRotationInvariantAndOnlyFixedLandscapeIsMatched() {
        assertEquals(0.45f, SmallWindowRules.landscapeRatio(900, 2000)!!, 0.0001f)
        assertEquals(0.45f, SmallWindowRules.landscapeRatio(2000, 900)!!, 0.0001f)
        assertNull(SmallWindowRules.landscapeRatio(0, 2000))
        listOf(0, 6, 8, 11).forEach { assertTrue(SmallWindowRules.isLandscapeOrientation(it)) }
        listOf(-1, 1, 2, 3, 4, 5, 7, 9, 10, 12).forEach { assertFalse(SmallWindowRules.isLandscapeOrientation(it)) }
    }

    @Test fun maximumSizeHasScreenBoundsAndNeverShrinksNativeSizes() {
        assertEquals(322, SmallWindowRules.largerWidth(280, 200, 1.7f, 400f, 900f))
        assertEquals(294, SmallWindowRules.largerWidth(280, 200, 1.7f, 320f, 900f))
        assertEquals(280, SmallWindowRules.largerWidth(280, 200, 2f, 400f, 500f))
        assertEquals(280, SmallWindowRules.largerWidth(280, 200, Float.NaN, 400f, 900f))
        assertEquals(280, SmallWindowRules.largerWidth(280, 200, 0f, 400f, 900f))
        assertEquals(280, SmallWindowRules.largerWidth(280, 200, 1f, 400f, 0f))
    }

    @Test fun mutingNeverTargetsCallsAlarmsOrNotifications() {
        val uids = setOf(10001)
        assertTrue(SmallWindowRules.shouldMuteMedia(10001, 1, uids))
        assertTrue(SmallWindowRules.shouldMuteMedia(10001, 14, uids))
        listOf(0, 2, 3, 4, 5, 6, 7, 8, 9, 11, 12, 13, 16).forEach {
            assertFalse(SmallWindowRules.shouldMuteMedia(10001, it, uids))
        }
        assertFalse(SmallWindowRules.shouldMuteMedia(10002, 1, uids))
        assertFalse(SmallWindowRules.shouldMuteMedia(10001, 1, emptySet()))
    }

    @Test fun disablingOrExpandingOnlyRestoresOwnedLivePlayers() {
        val own = Any()
        val other = Any()
        val changes = SmallWindowRules.mediaChanges(mapOf(1 to own), mapOf(1 to own, 2 to other), emptySet())
        assertEquals(setOf(1), changes.restore)
        assertTrue(changes.discard.isEmpty())
        assertTrue(changes.mute.isEmpty())
    }

    @Test fun playerDeathAndReusedIdsNeverRestoreAnUnrelatedPlayer() {
        val dead = Any()
        val reused = Any()
        val changes = SmallWindowRules.mediaChanges(mapOf(1 to dead, 2 to dead), mapOf(2 to reused), setOf(2))
        assertEquals(setOf(1, 2), changes.discard)
        assertTrue(changes.restore.isEmpty())
        assertEquals(setOf(2), changes.mute)
    }

    @Test fun stablePlayersAreNotRemutedAndFailedCleanupCanRetry() {
        val own = Any()
        val owned = mapOf(1 to own)
        val stable = SmallWindowRules.mediaChanges(owned, owned, setOf(1))
        assertTrue(stable.mute.isEmpty())
        assertTrue(stable.restore.isEmpty())
        val restore = SmallWindowRules.mediaChanges(owned, owned, emptySet())
        assertEquals(restore, SmallWindowRules.mediaChanges(owned, owned, emptySet()))
        assertEquals(setOf(1), restore.restore)
    }

    @Test fun onlyStableNonEmptyStashedHandleUsesBar() {
        assertTrue(SmallWindowRules.showWhiteBar(true, 4, 8, false, 1))
        assertFalse(SmallWindowRules.showWhiteBar(false, 4, 8, false, 1))
        assertFalse(SmallWindowRules.showWhiteBar(true, 2, 8, false, 1))
        assertFalse(SmallWindowRules.showWhiteBar(true, 4, 2, false, 1))
        assertFalse(SmallWindowRules.showWhiteBar(true, 4, 8, true, 1))
        assertFalse(SmallWindowRules.showWhiteBar(true, 4, 8, false, 0))
        assertFalse(SmallWindowRules.showWhiteBar(true, 4, 8, false, -1))
    }

    @Test fun twoOrMoreWindowsKeepBarButTheirExpandedPickerDoesNot() {
        for (count in listOf(2, 3, 4)) {
            assertTrue(SmallWindowRules.showWhiteBar(true, 4, 8, false, count))
            assertFalse(SmallWindowRules.showWhiteBar(true, 2, 8, false, count))
            assertFalse(SmallWindowRules.showWhiteBar(true, 4, 2, false, count))
            assertTrue(SmallWindowRules.showWhiteBar(true, 2, 4, false, count))
            assertFalse(SmallWindowRules.showWhiteBar(true, 4, 8, true, count))
        }
    }

    @Test fun addingAndRemovingWindowsNeverLeavesAnEmptyBar() {
        val counts = listOf(1, 2, 3, 2, 1, 0)
        assertEquals(listOf(true, true, true, true, true, false), counts.map {
            SmallWindowRules.showWhiteBar(true, 4, 8, false, it)
        })
    }

    @Test fun fadeBeginsDuringNativeCollapseAndReversesDuringExpansion() {
        assertFalse(SmallWindowRules.showWhiteBar(true, 2, 8, false, 2))
        assertTrue(SmallWindowRules.showWhiteBar(true, 2, 4, false, 2))
        assertTrue(SmallWindowRules.showWhiteBar(true, 4, 4, false, 2))
        assertTrue(SmallWindowRules.showWhiteBar(true, 4, 8, false, 2))
        assertFalse(SmallWindowRules.showWhiteBar(true, 4, 2, false, 2))
        assertFalse(SmallWindowRules.showWhiteBar(true, 2, 4, true, 2))
        assertFalse(SmallWindowRules.showWhiteBar(true, 1, 4, false, 2))
    }

    @Test fun interruptedFadeUsesCurrentProgressAndOnlyRemainingDuration() {
        assertEquals(200L, SmallWindowRules.barTransitionDuration(0f, 1f))
        assertEquals(200L, SmallWindowRules.barTransitionDuration(1f, 0f))
        assertEquals(80L, SmallWindowRules.barTransitionDuration(0.6f, 1f))
        assertEquals(120L, SmallWindowRules.barTransitionDuration(0.6f, 0f))
        assertEquals(40L, SmallWindowRules.barTransitionDuration(0.2f, 0f))
        assertEquals(0L, SmallWindowRules.barTransitionDuration(0.6f, 0.6f))
    }

    @Test fun invalidFadeValuesNeverProduceOutOfRangeOpacityOrDuration() {
        assertEquals(0f, SmallWindowRules.barProgress(Float.NaN), 0f)
        assertEquals(0f, SmallWindowRules.barProgress(Float.POSITIVE_INFINITY), 0f)
        assertEquals(0f, SmallWindowRules.barProgress(-1f), 0f)
        assertEquals(1f, SmallWindowRules.barProgress(2f), 0f)
        assertEquals(200L, SmallWindowRules.barTransitionDuration(-2f, 3f))
        assertEquals(0L, SmallWindowRules.barTransitionDuration(Float.NaN, Float.NEGATIVE_INFINITY))
    }

    @Test fun countCapacityGrowsBeyondTwoWithoutAReplacementFixedCap() {
        for (count in 0..512) {
            val capacity = SmallWindowRules.growingWindowCapacity(2, count)
            assertTrue(capacity > count)
            assertTrue(capacity >= 2)
            assertTrue(capacity <= count + 2)
        }
        assertEquals(12, SmallWindowRules.growingWindowCapacity(4, 10))
        assertEquals(14, SmallWindowRules.growingWindowCapacity(7, 10, 4))
        assertEquals(8, SmallWindowRules.growingWindowCapacity(8, 1))
    }

    @Test fun invalidCountsAndCapacityOverflowKeepNativeLimits() {
        assertEquals(0, SmallWindowRules.growingWindowCapacity(0, 4))
        assertEquals(2, SmallWindowRules.growingWindowCapacity(2, -1))
        assertEquals(2, SmallWindowRules.growingWindowCapacity(2, 4, 0))
        assertEquals(2, SmallWindowRules.growingWindowCapacity(2, Int.MAX_VALUE))
        assertEquals(2, SmallWindowRules.growingWindowCapacity(2, Int.MAX_VALUE - 2))
    }

    @Test fun frameRateSwitchesAreIndependentAndOnlyRemoveTheirOwnPolicy() {
        for (unlimited in listOf(false, true)) for (keep in listOf(false, true)) {
            for (small in listOf(false, true)) for (stashed in listOf(false, true)) {
                assertEquals((unlimited && small) || (keep && stashed),
                    SmallWindowRules.removeFrameRateLimit(unlimited, keep, small, stashed, false))
                assertFalse(SmallWindowRules.removeFrameRateLimit(unlimited, keep, small, stashed, true))
            }
        }
    }

    @Test fun safeInsetNeverReducesNativeMargin() {
        assertEquals(48, SmallWindowRules.safeInset(20, 48, 800, 60))
        assertEquals(90, SmallWindowRules.safeInset(90, 48, 800, 60))
        assertEquals(19, SmallWindowRules.safeInset(8, 48, 100, 60))
        assertEquals(40, SmallWindowRules.safeInset(40, 48, 100, 60))
    }

    @Test fun invalidGeometryKeepsNativeInset() {
        assertEquals(20, SmallWindowRules.safeInset(20, 48, 60, 60))
        assertEquals(20, SmallWindowRules.safeInset(20, 48, 0, 60))
        assertEquals(20, SmallWindowRules.safeInset(20, 48, 800, 0))
        assertEquals(20, SmallWindowRules.safeInset(20, -48, 800, 60))
    }

    @Test fun whiteBarIsInsideVisiblePortionOnBothSides() {
        val left = SmallWindowRules.barBounds(65f, 60f, 45f, 1f, true)!!
        val right = SmallWindowRules.barBounds(65f, 60f, 45f, 1f, false)!!
        assertEquals(47f, left.left, 0f)
        assertEquals(51f, left.right, 0f)
        assertEquals(14f, right.left, 0f)
        assertEquals(18f, right.right, 0f)
        assertEquals(14f, left.top, 0f)
        assertEquals(46f, left.bottom, 0f)
    }

    @Test fun barIsBoundedOnTinyHandlesAndScalesWithDensity() {
        val tiny = SmallWindowRules.barBounds(10f, 4f, 9f, 2f, true)!!
        assertTrue(tiny.left >= 9f && tiny.right <= 10f)
        assertTrue(tiny.top >= 0f && tiny.bottom <= 4f)
        val scaled = SmallWindowRules.barBounds(130f, 120f, 90f, 2f, true)!!
        assertEquals(94f, scaled.left, 0f)
        assertEquals(102f, scaled.right, 0f)
    }

    @Test fun unsupportedBarGeometryReturnsNull() {
        assertNull(SmallWindowRules.barBounds(65f, 60f, 65f, 1f, true))
        assertNull(SmallWindowRules.barBounds(65f, 0f, 45f, 1f, true))
        assertNull(SmallWindowRules.barBounds(65f, 60f, 45f, Float.NaN, true))
        assertNull(SmallWindowRules.barBounds(65f, 60f, -1f, 1f, true))
    }
}
