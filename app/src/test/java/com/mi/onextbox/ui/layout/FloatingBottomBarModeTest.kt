package com.mi.onextbox.ui.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingBottomBarModeTest {
    @Test fun legacyToggleKeepsThePreviouslySelectedBar() {
        assertEquals(FloatingBottomBarMode.Coui, FloatingBottomBarMode.restore(null, false))
        assertEquals(FloatingBottomBarMode.LiquidGlass, FloatingBottomBarMode.restore(null, true))
    }

    @Test fun savedModeTakesPrecedenceOverTheLegacyToggle() {
        FloatingBottomBarMode.entries.forEach { mode ->
            assertEquals(mode, FloatingBottomBarMode.restore(mode.preferenceValue, false))
            assertEquals(mode, FloatingBottomBarMode.restore(mode.preferenceValue, true))
        }
        assertEquals(FloatingBottomBarMode.Coui, FloatingBottomBarMode.restore("unknown", false))
    }

    @Test fun hiddenBarLeavesSwipeNavigationAvailable() {
        assertFalse(FloatingBottomBarMode.Off.visible)
        assertFalse(FloatingBottomBarMode.Off.collapsible)
        assertTrue(FloatingBottomBarMode.Off.allowsMainPageSwipe(false))
        for (mode in listOf(FloatingBottomBarMode.Coui, FloatingBottomBarMode.LiquidGlass)) {
            assertTrue(mode.visible)
            assertTrue(mode.collapsible)
            assertFalse(mode.allowsMainPageSwipe(false))
            assertTrue(mode.allowsMainPageSwipe(true))
        }
    }

    @Test fun standardBarStaysVisibleWhenScrollingAndKeepsSwipePreference() {
        val mode = FloatingBottomBarMode.Standard
        assertTrue(mode.visible)
        assertFalse(mode.collapsible)
        assertFalse(mode.allowsMainPageSwipe(false))
    }
}
