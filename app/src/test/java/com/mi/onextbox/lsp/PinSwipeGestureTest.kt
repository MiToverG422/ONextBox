package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PinSwipeGestureTest {
    @Test fun tappingAndSmallMovementLeaveNativeInputUntouched() {
        val gesture = PinSwipeGesture(2)
        assertTrue(gesture.move(2, true).isEmpty())
        assertTrue(gesture.move(-1, false).isEmpty())
        assertTrue(gesture.move(3, false).isEmpty())
        assertFalse(gesture.sliding)
    }
    @Test fun crossingKeysEmitsFirstOnceAndNeverRepeatsWhileDwelling() {
        val gesture = PinSwipeGesture(2)
        assertEquals(listOf(2, 3), gesture.move(3, true))
        assertTrue(gesture.sliding)
        repeat(30) { assertTrue(gesture.move(3, true).isEmpty()) }
        assertEquals(listOf(5), gesture.move(5, true))
        assertTrue(gesture.move(-1, true).isEmpty())
        assertEquals(listOf(5), gesture.move(5, true))
        assertEquals(listOf(2), gesture.move(2, true))
    }
    @Test fun gestureStateDoesNotCarryOverToTheNextTouch() {
        PinSwipeGesture(1).move(2, true)
        val next = PinSwipeGesture(1)
        assertFalse(next.sliding)
        assertEquals(listOf(1, 2), next.move(2, true))
    }
    @Test fun repeatedFirstDigitRequiresLeavingItsKey() {
        val gesture = PinSwipeGesture(4)
        assertTrue(gesture.move(4, true).isEmpty())
        assertTrue(gesture.move(-1, true).isEmpty())
        assertEquals(listOf(4, 4), gesture.move(4, false))
        assertTrue(gesture.move(4, true).isEmpty())
    }
    @Test fun hookRetainsNativeVerificationAndGuardsInputScope() {
        val source = File("src/main/java/com/mi/onextbox/lsp/PinSwipeInputHooker.kt").readText()
        assertTrue(source.contains("target.performClick()"))
        assertTrue(source.contains("keys.size != 10"))
        assertTrue(source.contains("isTouchExplorationEnabled"))
        assertTrue(source.contains("event.pointerCount != 1"))
        assertTrue(source.contains("MotionEvent.ACTION_CANCEL"))
        assertTrue(source.contains("onDetachedFromWindow"))
        assertFalse(source.contains("setPassword"))
        assertFalse(source.contains("verifyPassword"))
    }
}
