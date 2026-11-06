package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class FaceAuthCapsuleLayoutTest {
    @Test fun nativeTipsAndClockSlotsForceAShortCapsuleWithTheFaceAwayFromTheCamera() {
        for (d in listOf(1f, 2.75f, 3.5f)) {
            val layout = FaceAuthCapsuleLayout.resolve(5f * d, 30f * d, d, 44f * d, 150f * d)
            assertFalse(layout.vertical)
            assertEquals(30f * d, layout.expandedHeight, 0.001f)
            assertTrue(5f * d + layout.expandedHeight < 44f * d)
            assertTrue(layout.faceOffsetX + 19.1f * layout.iconScale * d < -12f * d)
            assertTrue(layout.faceCenterY - 19.1f * layout.iconScale * d > 5f * d)
        }
    }

    @Test fun verticalExpansionReservesSpaceForBothItsIconAndTheNativeContent() {
        val layout = FaceAuthCapsuleLayout.resolve(5f, 30f, 1f, 100f, 150f)
        assertTrue(layout.vertical)
        assertTrue(5f + layout.expandedHeight * 1.025f + 6f < 100f)
        assertTrue(layout.faceCenterY - 19.1f * layout.iconScale > 35f)
        assertTrue(layout.faceCenterY + 19.1f * layout.iconScale < 5f + layout.expandedHeight)
    }

    @Test fun missingNativeContentMetricsCannotCreateAnUnboundedVerticalOverlay() {
        assertFalse(FaceAuthCapsuleLayout.resolve(5f, 30f, 1f, null, 150f).vertical)
        assertFalse(FaceAuthCapsuleLayout.resolve(5f, 30f, 1f, 100f, 75f).vertical)
    }

    @Test fun retractionEndsAtTheActualCameraPositionInsteadOfTheCompactCapsule() {
        val pill = FaceAuthCapsuleBounds(150f, 5f, 250f, 35f)
        val hole = FaceAuthCapsuleBounds(202f, 12f, 222f, 32f)
        assertEquals(pill, pill.toward(hole, 0f))
        val middle = pill.toward(hole, 0.5f)
        assertTrue(middle.width < pill.width && middle.width > hole.width)
        assertEquals(hole, pill.toward(hole, 1f))
    }
}
