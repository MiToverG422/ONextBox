package com.mi.onextbox.lsp

import android.content.Context
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class PinSwipeNativeClickTest {
    private class NativeKey(context: Context) : View(context) {
        val actions = mutableListOf<Int>()
        var clicks = 0
        var presses = 0
        var failOnDown = false
        init {
            layout(0, 0, 90, 120)
            isHapticFeedbackEnabled = true
            setOnClickListener { clicks++ }
        }
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            actions += event.actionMasked
            assertEquals(45f, event.x, 0f)
            assertEquals(60f, event.y, 0f)
            assertEquals(InputDevice.SOURCE_TOUCHSCREEN, event.source)
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                if (failOnDown) throw IllegalStateException("test native dispatch failure")
                if (isHapticFeedbackEnabled) presses++
                isPressed = true
            }
            if (event.actionMasked == MotionEvent.ACTION_CANCEL || event.actionMasked == MotionEvent.ACTION_UP) isPressed = false
            return true
        }
    }

    private fun onMain(block: (NativeKey, MotionEvent) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val source = MotionEvent.obtain(100, 150, MotionEvent.ACTION_MOVE, 999f, 888f, 0)
                .apply { this.source = InputDevice.SOURCE_TOUCHSCREEN }
            try { block(NativeKey(instrumentation.targetContext), source) }
            finally { source.recycle() }
        }
    }

    @Test fun initialKeyReusesItsOriginalFeedback() = onMain { key, source ->
        val feedback = PinSwipeNativeClick()
        feedback.perform(key, source, reuseInitialDown = true)
        feedback.release()
        assertEquals(1, key.clicks)
        assertEquals(0, key.presses)
        assertTrue(key.actions.isEmpty())
        assertEquals(MotionEvent.ACTION_MOVE, source.actionMasked)
    }

    @Test fun crossedAndReenteredKeysStayPressedUntilLeaving() = onMain { key, source ->
        val feedback = PinSwipeNativeClick()
        repeat(3) {
            feedback.perform(key, source, reuseInitialDown = false)
            assertTrue(key.isPressed)
            assertEquals(MotionEvent.ACTION_DOWN, key.actions.last())
            feedback.release()
            assertFalse(key.isPressed)
        }
        assertEquals(3, key.presses)
        assertEquals(3, key.clicks)
        assertEquals(List(3) { listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP) }.flatten(), key.actions)
        assertEquals(MotionEvent.ACTION_MOVE, source.actionMasked)
        assertEquals(999f, source.x, 0f)
        assertEquals(888f, source.y, 0f)
    }

    @Test fun disabledNativeFeedbackStillClicksWithoutAddingVibration() = onMain { key, source ->
        key.isHapticFeedbackEnabled = false
        val feedback = PinSwipeNativeClick()
        feedback.perform(key, source, reuseInitialDown = false)
        assertTrue(key.isPressed)
        feedback.release()
        assertEquals(0, key.presses)
        assertEquals(1, key.clicks)
    }

    @Test fun failedNativePressIsCancelledWithoutAppendingADigit() = onMain { key, source ->
        key.failOnDown = true
        val feedback = PinSwipeNativeClick()
        try {
            feedback.perform(key, source, reuseInitialDown = false)
            fail("Native dispatch should throw")
        } catch (_: IllegalStateException) {
            assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL), key.actions)
            assertEquals(0, key.clicks)
            assertEquals(MotionEvent.ACTION_MOVE, source.actionMasked)
        }
    }

    @Test fun movingToTheNextKeyReleasesOnlyThePreviousKey() = onMain { first, source ->
        val next = NativeKey(first.context)
        val feedback = PinSwipeNativeClick()
        feedback.perform(first, source, reuseInitialDown = false)
        feedback.perform(next, source, reuseInitialDown = false)
        assertFalse(first.isPressed)
        assertTrue(next.isPressed)
        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP), first.actions)
        assertEquals(listOf(MotionEvent.ACTION_DOWN), next.actions)
        assertEquals(1, first.clicks)
        assertEquals(1, next.clicks)
        feedback.release()
        assertFalse(next.isPressed)
        assertEquals(1, next.clicks)
    }

    @Test fun repeatedCleanupDoesNotLeaveAPressedKeyOrEmitAnotherDigit() = onMain { key, source ->
        val feedback = PinSwipeNativeClick()
        feedback.perform(key, source, reuseInitialDown = false)
        repeat(3) { feedback.release() }
        assertFalse(key.isPressed)
        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP), key.actions)
        assertEquals(1, key.clicks)
        assertEquals(1, key.presses)
    }
}
