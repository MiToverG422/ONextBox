package com.mi.onextbox.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorOsSearchMotionTest {
    @Test fun editingTapsKeepWaterdropAnchorStableButUnclaimedDragsStillBounce() {
        assertTrue(colorOsSearchMotionAllowed(editorFocused = false, pastSlop = false, consumed = false))
        assertEquals(false, colorOsSearchMotionAllowed(editorFocused = true, pastSlop = false, consumed = false))
        assertTrue(colorOsSearchMotionAllowed(editorFocused = true, pastSlop = true, consumed = false))
        assertEquals(false, colorOsSearchMotionAllowed(editorFocused = true, pastSlop = true, consumed = true))
        assertEquals(false, colorOsSearchMotionAllowed(editorFocused = false, pastSlop = true, consumed = true))
    }
    @Test fun emptyHorizontalDragCanBounceWhenEditorConsumesMovement() {
        assertTrue(colorOsSearchEmptyDragAllowed(true, true, true, 120L, 500L))
        assertTrue(colorOsSearchMotionAllowed(true, true, true, decorativeEmptyDrag = true))
        assertEquals(false, colorOsSearchMotionAllowed(true, false, true, decorativeEmptyDrag = true))
    }
    @Test fun existingTextNeverBypassesSelectionOwnership() {
        assertEquals(false, colorOsSearchEmptyDragAllowed(false, true, true, 120L, 500L))
        assertEquals(false, colorOsSearchMotionAllowed(true, true, true))
    }
    @Test fun longPressKeepsToolbarAndHandleOwnership() {
        assertEquals(false, colorOsSearchEmptyDragAllowed(true, true, true, 500L, 500L))
        assertEquals(false, colorOsSearchEmptyDragAllowed(true, true, true, 800L, 500L))
        assertEquals(false, colorOsSearchEmptyDragAllowed(true, true, true, -1L, 500L))
    }
    @Test fun verticalAndMultitouchDragsDoNotBypassConsumption() {
        assertEquals(false, colorOsSearchEmptyDragAllowed(true, true, false, 120L, 500L))
        assertEquals(false, colorOsSearchEmptyDragAllowed(true, false, true, 120L, 500L))
    }
    @Test fun spotlightCoordinatesFollowTheWholeCapsuleTransform() {
        assertEquals(100f to 20f, colorOsSearchLocalPosition(100f, 20f, 360f, 40f, 1f, 1f, 0f, 0f))
        val point = colorOsSearchLocalPosition(119f, 25f, 360f, 40f, .9f, .95f, 11f, 5f)
        assertEquals(100f, point.first, .0001f)
        assertEquals(20f, point.second, .0001f)
    }
    @Test fun rubberIsSymmetricAndBounded() {
        assertEquals(0f, colorOsSearchRubber(0f, .05f, 28f), .0001f)
        assertEquals(-colorOsSearchRubber(100f, .05f, 28f), colorOsSearchRubber(-100f, .05f, 28f), .0001f)
        assertTrue(colorOsSearchRubber(10000f, .05f, 28f) in 0f..28f)
        assertEquals(0f, colorOsSearchRubber(100f, .05f, 0f), .0001f)
    }
    @Test fun deformationConservesAreaAndHasDeadZone() {
        assertEquals(1f to 1f, colorOsSearchDeformation(1f, 2f, 1080f, 120f, 3f))
        val (x, y) = colorOsSearchDeformation(300f, 60f, 1080f, 120f, 3f)
        assertEquals(1f, x * y, .0001f)
        assertTrue(x > 1f)
        assertTrue(y < 1f)
    }
    @Test fun capsulePressUsesAspectAndSizeNotToolbarCircleScale() {
        val scale = colorOsSearchPressScale(360f, 40f)
        assertTrue(scale in .9f..1f)
        assertEquals(scale, colorOsSearchPressScale(40f, 360f), .0001f)
        assertEquals(1f, colorOsSearchPressScale(0f, 40f), .0001f)
    }
}
