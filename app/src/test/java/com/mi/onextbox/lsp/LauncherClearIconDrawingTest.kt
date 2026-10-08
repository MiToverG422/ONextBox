package com.mi.onextbox.lsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherClearIconDrawingTest {
    // Simulate obfuscated system fields and unobfuscated accessors.
    class Button {
        private var b = true
        val changes = mutableListOf<Boolean>()

        fun getForceBlockDraw(): Boolean = b

        fun setForceBlockDraw(blocked: Boolean) {
            b = blocked
            changes += blocked
        }
    }

    @Test fun `drawing uses native accessors instead of metadata field names`() {
        val button = Button()
        val drawing = LauncherClearIconDrawing(Button::class.java)

        val result = drawing.draw(button) {
            assertFalse(button.getForceBlockDraw())
            "drawn"
        }

        assertEquals("drawn", result)
        assertTrue(button.getForceBlockDraw())
        assertEquals(listOf(false, true), button.changes)
    }

    @Test fun `unblocked drawing does not change native animation state`() {
        val button = Button().apply {
            setForceBlockDraw(false)
            changes.clear()
        }

        LauncherClearIconDrawing(Button::class.java).draw(button) {
            assertFalse(button.getForceBlockDraw())
        }

        assertFalse(button.getForceBlockDraw())
        assertTrue(button.changes.isEmpty())
    }

    @Test fun `drawing failure restores native animation state and preserves the exception`() {
        val button = Button()
        val failure = IllegalStateException("draw failure")

        val result = runCatching {
            LauncherClearIconDrawing(Button::class.java).draw(button) {
                assertFalse(button.getForceBlockDraw())
                throw failure
            }
        }

        assertSame(failure, result.exceptionOrNull())
        assertTrue(button.getForceBlockDraw())
        assertEquals(listOf(false, true), button.changes)
    }

    @Test fun `repeated entry frames draw the icon while retaining native isolation`() {
        val button = Button()
        val drawing = LauncherClearIconDrawing(Button::class.java)
        var frames = 0

        repeat(3) {
            drawing.draw(button) {
                assertFalse(button.getForceBlockDraw())
                frames++
            }
            assertTrue(button.getForceBlockDraw())
        }

        assertEquals(3, frames)
        assertEquals(listOf(false, true, false, true, false, true), button.changes)
    }
}
