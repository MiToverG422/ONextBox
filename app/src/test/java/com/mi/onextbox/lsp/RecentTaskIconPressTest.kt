package com.mi.onextbox.lsp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentTaskIconPressTest {
    @Test fun `short taps are not consumed`() {
        val press = RecentTaskIconPress(20f, 30f, 8)
        press.cancel()
        assertFalse(press.handled)
        assertFalse(press.trigger(true, true, true))
    }

    @Test fun `stationary long press is handled only once`() {
        val press = RecentTaskIconPress(20f, 30f, 8)
        assertTrue(press.move(28f, 22f))
        assertTrue(press.trigger(true, true, true))
        assertTrue(press.handled)
        assertFalse(press.trigger(true, true, true))
    }

    @Test fun `scrolling cancels long press without consuming native gesture`() {
        for ((x, y) in listOf(29f to 30f, 20f to 21f, 10f to 40f)) {
            val press = RecentTaskIconPress(20f, 30f, 8)
            assertFalse(press.move(x, y))
            assertFalse(press.move(20f, 30f))
            assertFalse(press.trigger(true, true, true))
            assertFalse(press.handled)
        }
    }

    @Test fun `disabled switch recycled task and split selection do not open details`() {
        for ((enabled, sameTask, ready) in listOf(
            Triple(false, true, true), Triple(true, false, true), Triple(true, true, false),
        )) {
            val press = RecentTaskIconPress(20f, 30f, 8)
            assertFalse(press.trigger(enabled, sameTask, ready))
            assertFalse(press.handled)
            assertFalse(press.trigger(true, true, true))
        }
    }

    @Test fun `cancel and additional pointers cannot retrigger the gesture`() {
        val press = RecentTaskIconPress(20f, 30f, 8)
        press.cancel()
        assertFalse(press.trigger(true, true, true))
        assertFalse(press.handled)
    }

    @Test fun `handled release stays consumed after cleanup`() {
        val press = RecentTaskIconPress(20f, 30f, 8)
        assertTrue(press.trigger(true, true, true))
        press.cancel()
        assertTrue(press.handled)
        assertFalse(press.move(29f, 40f))
        assertFalse(press.trigger(true, true, true))
    }
}
