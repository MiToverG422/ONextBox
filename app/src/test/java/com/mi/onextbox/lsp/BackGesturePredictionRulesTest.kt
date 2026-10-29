package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class BackGesturePredictionRulesTest {
    @Test fun onlySideActivityAndTaskPredictionsUseTheChosenStyle() {
        for (type in -1..5) for (edge in -1..3) for (style in 0..2) {
            assertEquals(style == 1 && edge in 0..1 && type in 2..3,
                BackGestureRules.predictiveRoute(style, edge, type, false))
            assertFalse(BackGestureRules.predictiveRoute(style, edge, type, true))
        }
    }

    @Test fun switchingBackToColorOsRestoresTheCustomRunnerAndClearsOwnership() {
        val original = Any()
        val aosp = Any()
        val slot = BackGestureRouteSlot<Any>()
        assertSame(aosp, slot.replace(original, aosp))
        assertTrue(slot.active)
        assertSame(original, slot.restore(aosp))
        assertFalse(slot.active)
        assertSame(original, slot.restore(original))
    }

    @Test fun externalRegistrationOrRemovalDuringAGestureMustWinOverRestoration() {
        val original = Any()
        val aosp = Any()
        val newlyRegistered = Any()
        val slot = BackGestureRouteSlot<Any>()
        slot.replace(original, aosp)
        assertSame(newlyRegistered, slot.restore(newlyRegistered))
        assertFalse(slot.active)
        slot.replace(original, aosp)
        assertNull(slot.restore(null))
        assertFalse(slot.active)
    }

    @Test fun consecutiveGesturesUseTheirOwnOriginalRunnerAndDoNotAffectTheOtherType() {
        val activity = BackGestureRouteSlot<Any>()
        val task = BackGestureRouteSlot<Any>()
        val first = Any()
        val second = Any()
        val aosp = Any()
        val taskOriginal = Any()
        val taskAosp = Any()
        task.replace(taskOriginal, taskAosp)
        activity.replace(first, aosp)
        assertSame(first, activity.restore(aosp))
        activity.replace(second, aosp)
        assertSame(second, activity.restore(aosp))
        assertTrue(task.active)
        assertSame(taskOriginal, task.restore(taskAosp))
    }

    @Test fun homePreviewStartsAtFullBoundsAndEndsAtTheAospPreviewSize() {
        for (edge in 0..1) {
            val start = BackGestureRules.homePreview(1080f, 2400f, 0f, edge, 900f, 24f)!!
            assertEquals(0f, start.left, 0.001f)
            assertEquals(0f, start.top, 0.001f)
            assertEquals(1080f, start.width, 0.001f)
            assertEquals(2400f, start.height, 0.001f)
            val end = BackGestureRules.homePreview(1080f, 2400f, 1f, edge, 0f, 24f)!!
            assertEquals(918f, end.width, 0.001f)
            assertEquals(2040f, end.height, 0.001f)
        }
    }

    @Test fun homePreviewIsSymmetricAndStaysInsideTheDisplayForExtremeDragging() {
        for (progress in listOf(-1f, 0f, 0.1f, 0.5f, 1f, 2f)) for (y in listOf(-10000f, -200f, 0f, 200f, 10000f)) {
            val left = BackGestureRules.homePreview(1080f, 2400f, progress, 0, y, 24f)!!
            val right = BackGestureRules.homePreview(1080f, 2400f, progress, 1, y, 24f)!!
            assertEquals(1080f - left.width - left.left, right.left, 0.001f)
            assertEquals(left.top, right.top, 0.001f)
            assertTrue(left.top >= -0.001f && left.top + left.height <= 2400.001f)
            assertTrue(left.left >= -0.001f && left.left + left.width <= 1080.001f)
        }
    }

    @Test fun invalidHomeGeometryPreservesTheNativePreview() {
        assertNull(BackGestureRules.homePreview(0f, 2400f, 0.5f, 0, 0f, 24f))
        assertNull(BackGestureRules.homePreview(1080f, Float.NaN, 0.5f, 0, 0f, 24f))
        assertNull(BackGestureRules.homePreview(1080f, 2400f, Float.NaN, 0, 0f, 24f))
        assertNull(BackGestureRules.homePreview(1080f, 2400f, 0.5f, 2, 0f, 24f))
        assertNull(BackGestureRules.homePreview(1080f, 2400f, 0.5f, 0, Float.NaN, 24f))
    }

    @Test fun departingActivityFadesBeforeTheIncomingPageSettles() {
        assertEquals(1f, BackGestureRules.closingAlpha(0f), 0.0001f)
        assertEquals(0.5f, BackGestureRules.closingAlpha(0.1f), 0.0001f)
        assertEquals(0f, BackGestureRules.closingAlpha(0.2f), 0.0001f)
        assertEquals(0f, BackGestureRules.closingAlpha(1f), 0.0001f)
        assertEquals(1f, BackGestureRules.closingAlpha(Float.NaN), 0.0001f)
        assertEquals(1f, BackGestureRules.closingAlpha(-1f), 0.0001f)
        assertEquals(0f, BackGestureRules.closingAlpha(2f), 0.0001f)
    }
}
