package com.mi.onextbox.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColorOsScrollEntranceTest {
    @Test fun initialVisibleCardsStayStillOnFirstDrag() {
        val entry = ColorOsScrollEntranceEntry()
        assertNull(entry.update(20f, 120f, 0f, 500f, 0f, false))
        assertNull(entry.update(10f, 110f, 0f, 500f, -30f, true))
    }

    @Test fun newBottomCardsEnterFromBelow() {
        val entry = ColorOsScrollEntranceEntry()
        assertEquals(0f, entry.update(520f, 620f, 0f, 500f, 0f, false))
        assertEquals(400f, entry.update(490f, 590f, 0f, 500f, -30f, true))
        assertNull(entry.update(470f, 570f, 0f, 500f, -20f, true))
    }

    @Test fun newTopCardsEnterFromAbove() {
        val entry = ColorOsScrollEntranceEntry()
        assertEquals(0f, entry.update(-120f, -20f, 0f, 500f, 0f, false))
        assertEquals(-400f, entry.update(-90f, 10f, 0f, 500f, 30f, true))
    }

    @Test fun lazyCardsFirstPlacedDuringScrollAnimate() {
        assertEquals(400f, ColorOsScrollEntranceEntry().update(490f, 590f, 0f, 500f, -20f, true))
        assertEquals(-400f, ColorOsScrollEntranceEntry().update(-90f, 10f, 0f, 500f, 20f, true))
    }

    @Test fun leavingResetsAndReentryCanAnimateAgain() {
        val entry = ColorOsScrollEntranceEntry()
        assertNull(entry.update(20f, 120f, 0f, 500f, 0f, false))
        assertEquals(0f, entry.update(-100f, 0f, 0f, 500f, -100f, true))
        assertEquals(-400f, entry.update(-90f, 10f, 0f, 500f, 10f, true))
        entry.leave()
        assertEquals(400f, entry.update(400f, 500f, 0f, 500f, -10f, true))
    }

    @Test fun idleEntryDoesNotAnimateAndCannotReplayWhenScrollStarts() {
        val entry = ColorOsScrollEntranceEntry()
        assertEquals(0f, entry.update(500f, 600f, 0f, 500f, -10f, false))
        assertNull(entry.update(490f, 590f, 0f, 500f, -10f, false))
        assertNull(entry.update(480f, 580f, 0f, 500f, -10f, true))
    }

    @Test fun viewportInitializationAndResizeOnlyEstablishBaseline() {
        val entry = ColorOsScrollEntranceEntry()
        assertEquals(0f, entry.update(100f, 200f, 0f, 500f, -10f, true, baseline = true))
        assertNull(entry.update(90f, 190f, 0f, 500f, -10f, true))
        assertEquals(0f, entry.update(80f, 180f, 20f, 450f, -10f, true, baseline = true))
    }

    @Test fun onePixelInsideEitherHostEdgeIsAlreadyVisibleAtInitialization() {
        val top = ColorOsScrollEntranceEntry()
        val bottom = ColorOsScrollEntranceEntry()
        assertEquals(0f, top.update(-99f, 1f, 0f, 500f, 20f, true, baseline = true))
        assertEquals(0f, bottom.update(499f, 599f, 0f, 500f, -20f, true, baseline = true))
        assertNull(top.update(-98f, 2f, 0f, 500f, 1f, true))
        assertNull(bottom.update(498f, 598f, 0f, 500f, -1f, true))
    }

    @Test fun crossingInnerToolbarAndNavigationEdgesDoesNotReplayEntrance() {
        val top = ColorOsScrollEntranceEntry()
        val bottom = ColorOsScrollEntranceEntry()
        assertNull(top.update(-99f, 1f, 0f, 500f, 0f, false))
        assertNull(bottom.update(499f, 599f, 0f, 500f, 0f, false))
        // The toolbar ends at 80 px, the navigation bar starts at 420 px.
        assertNull(top.update(-21f, 79f, 0f, 500f, 78f, true))
        assertNull(top.update(-19f, 81f, 0f, 500f, 2f, true))
        assertNull(bottom.update(421f, 521f, 0f, 500f, -78f, true))
        assertNull(bottom.update(419f, 519f, 0f, 500f, -2f, true))
        assertNull(top.update(-99f, 1f, 0f, 500f, -80f, true))
        assertNull(bottom.update(499f, 599f, 0f, 500f, 80f, true))
    }

    @Test fun actualHostExitAllowsTopAndBottomEntranceToReplayOnReversal() {
        val top = ColorOsScrollEntranceEntry()
        val bottom = ColorOsScrollEntranceEntry()
        assertNull(top.update(-99f, 1f, 0f, 500f, 0f, false))
        assertNull(bottom.update(499f, 599f, 0f, 500f, 0f, false))
        assertEquals(0f, top.update(-100f, 0f, 0f, 500f, -1f, true))
        assertEquals(0f, bottom.update(500f, 600f, 0f, 500f, 1f, true))
        assertEquals(-400f, top.update(-99f, 1f, 0f, 500f, 1f, true))
        assertEquals(400f, bottom.update(499f, 599f, 0f, 500f, -1f, true))
    }

    @Test fun emptyInvalidAndUnmovedBoundsCannotEnter() {
        val entry = ColorOsScrollEntranceEntry()
        assertEquals(0f, entry.update(20f, 20f, 0f, 500f, -10f, true))
        assertEquals(0f, entry.update(20f, 120f, 0f, 0f, -10f, true))
        assertEquals(0f, entry.update(Float.NaN, 120f, 0f, 500f, -10f, true))
        assertNull(entry.update(20f, 120f, 0f, 500f, 0f, true))
    }

    @Test fun animationCurveReachesBothEndpoints() {
        assertEquals(0f, ColorOsScrollEntranceMotion.Easing.transform(0f), .0001f)
        assertEquals(1f, ColorOsScrollEntranceMotion.Easing.transform(1f), .0001f)
        assertEquals(360, ColorOsScrollEntranceMotion.DurationMillis)
        assertEquals(400f, ColorOsScrollEntranceMotion.Distance, .0001f)
    }
}
