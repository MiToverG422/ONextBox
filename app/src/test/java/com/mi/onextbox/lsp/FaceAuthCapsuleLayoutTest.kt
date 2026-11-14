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

    @Test fun nativeCameraPaddingDeterminesThePositionEvenWithoutACapsuleChildLayout() {
        // Current device's idle keyguard: child is 0 x 0, parent is 1080 x 120 with 2 px top padding.
        val layout = FaceAuthCapsulePosition.fromNativeArea(
            FaceAuthCapsuleBounds(0f, 2f, 1080f, 120f), 90f, 300f, 60f, 61f, 14f, 76f,
        )!!
        assertEquals(16f, layout.top, 0f)
        assertEquals(540f, layout.centerX!!, 0f)
        assertEquals(30f, layout.hole!!.top, 0f)
        assertEquals(92f, layout.hole.bottom, 0f)
        assertNotEquals((120f - 90f) / 2f, layout.top)
    }

    @Test fun nativeModelHeightAndAsymmetricPaddingAreNotReplacedByStatusBarCentering() {
        for (scale in listOf(1f, 0.75f, 1.5f)) {
            // A deeper camera and the OEM's taller 33 dp cloud profile.
            val layout = FaceAuthCapsulePosition.fromNativeArea(
                FaceAuthCapsuleBounds(0f, 28f * scale, 1080f * scale, 144f * scale),
                99f * scale, 300f * scale, 60f * scale, 64f * scale, 18f * scale, 82f * scale,
            )!!
            assertEquals(36.5f * scale, layout.top, 0.001f)
            assertEquals(99f * scale, layout.height, 0.001f)
            assertEquals(540f * scale, layout.centerX!!, 0.001f)
            assertEquals(54.5f * scale, layout.hole!!.top, 0.001f)
        }
    }

    @Test fun fallbackAlignsToTheCameraVerticallyAndKeepsCornerCameraCloudsCentered() {
        val centered = FaceAuthCapsulePosition.fallback(400f, 48f, 30f, 100f, 20f,
            FaceAuthCapsuleBounds(190f, 19f, 210f, 39f), 20f, 4f)
        assertEquals(14f, centered.top, 0f)
        assertEquals(29f, centered.hole!!.centerY, 0f)
        val corner = FaceAuthCapsulePosition.fallback(400f, 48f, 30f, 100f, 20f,
            FaceAuthCapsuleBounds(20f, 19f, 40f, 39f), 20f, 4f)
        assertEquals(200f, corner.centerX!!, 0f)
        assertEquals(200f, corner.hole!!.centerX, 0f)
        val noCamera = FaceAuthCapsulePosition.fallback(400f, 40f, 30f, 100f, 20f, null, 20f, 4f)
        assertEquals(5f, noCamera.top, 0f)
    }

    @Test fun nativeScreenCoordinatesAreTranslatedTogetherWithTheRetractionTarget() {
        val layout = FaceAuthCapsuleNativeLayout(36f, 90f, 300f, 45f, 560f,
            FaceAuthCapsuleBounds(530f, 50f, 590f, 112f))
        val local = layout.offset(-20f, -20f)
        assertEquals(16f, local.top, 0f)
        assertEquals(540f, local.centerX!!, 0f)
        assertEquals(layout, local.offset(20f, 20f))
    }

    @Test fun incompleteOrInvalidNativeLayoutsAreRetriedInsteadOfUsedAsCoordinates() {
        val area = FaceAuthCapsuleBounds(0f, 2f, 1080f, 120f)
        assertNull(FaceAuthCapsulePosition.fromNativeArea(area, 0f, 300f, 60f, null, null, null))
        assertNull(FaceAuthCapsulePosition.fromNativeArea(area, 140f, 300f, 60f, null, null, null))
        assertNull(FaceAuthCapsulePosition.fromNativeArea(area.copy(top = Float.NaN), 90f, 300f, 60f, null, null, null))
        assertNull(FaceAuthCapsulePosition.fromNativeArea(area, 90f, 300f, 60f, 61f, -30f, 40f)!!.hole)
    }
}
