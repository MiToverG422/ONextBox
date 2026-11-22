package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.StatusBarLayoutSettings as Legacy
import com.mi.onextbox.lsp.SystemUiAppearance as Option
import org.junit.Assert.*
import org.junit.Test

class StatusBarIconLayoutTest {
    private fun read(prefs: Map<String, Any>) = StatusBarIconLayout.resolve(
        prefs::containsKey,
        { key, fallback -> prefs[key] as? Boolean ?: fallback },
        { key, fallback -> prefs[key] as? Int ?: fallback },
    )

    private val margins = mapOf(Legacy.MARGINS_ENABLED to true, Legacy.VERTICAL_MARGINS_ENABLED to true,
        Legacy.LEFT to 40, Legacy.RIGHT to 39, Legacy.TOP to -16, Legacy.BOTTOM to 40)

    @Test fun untouchedMarginsRetainNativeSafeInsetsAndUsePhysicalSideSigns() {
        val state = read(margins + (Option.RightScale.key to 127))
        assertTrue(state.enabled && state.nativeX && state.nativeY)
        assertEquals(40, state.value(Option.LeftX))
        assertEquals(-39, state.value(Option.RightX))
        assertEquals(-28, state.value(Option.LeftY))
        assertEquals(-28, state.value(Option.RightY))
        assertEquals(100, state.value(Option.RightScale))
        assertTrue(state.edit(Option.LeftScale, 115).nativeX)
        assertTrue(state.edit(Option.LeftScale, 115).nativeY)
    }

    @Test fun translationWinsWhenBothOldMastersWereEnabled() {
        val state = read(margins + mapOf(Option.Sides.key to true, Option.LeftX.key to 5,
            Option.RightX.key to 7, Option.RightScale.key to 138))
        assertTrue(state.enabled)
        assertFalse(state.nativeX || state.nativeY)
        assertEquals(5, state.value(Option.LeftX))
        assertEquals(7, state.value(Option.RightX))
        assertEquals(138, state.value(Option.RightScale))
    }

    @Test fun disablingSurvivesColdReadAndReenablingRestoresEachAxisOnce() {
        val first = read(margins).edit(Option.LeftX, 7)
        assertFalse(first.nativeX)
        assertTrue(first.nativeY)
        val disabled = first.edit(Option.Sides, 0)
        val stored = margins + disabled.values.mapKeys { it.key.key } +
            mapOf(Option.Sides.key to disabled.enabled, StatusBarIconLayout.NATIVE_AXES to disabled.nativeAxes)
        val restored = read(stored)
        assertEquals(disabled, restored)
        assertFalse(restored.enabled || restored.nativeX || restored.nativeY)
        assertEquals(first, restored.edit(Option.Sides, 1))
        val reset = first.edit(Option.RightY, 0)
        assertFalse(reset.nativeX || reset.nativeY)
        assertEquals(-28, reset.value(Option.LeftY))
        assertEquals(0, reset.value(Option.RightY))
    }

    @Test fun emptyAndInactivePreferencesStayOff() {
        val disabled = read(emptyMap())
        assertFalse(disabled.enabled)
        assertEquals(100, disabled.value(Option.LeftScale))
        assertEquals(0, disabled.value(Option.RightY))
        val dormant = read(mapOf(Legacy.LEFT to 33, Legacy.RIGHT to 23, Legacy.TOP to 9, Legacy.BOTTOM to 3,
            Option.LeftX.key to 7))
        assertFalse(dormant.enabled)
        assertFalse(dormant.edit(Option.Sides, 1).nativeX)
        assertEquals(7, dormant.edit(Option.Sides, 1).value(Option.LeftX))
        assertEquals(-23, dormant.edit(Option.Sides, 1).value(Option.RightX))
        assertEquals(3, dormant.edit(Option.Sides, 1).value(Option.RightY))
        val explicitlyDisabled = read(mapOf(Option.Sides.key to false, Legacy.LEFT to 33))
        assertEquals(0, explicitlyDisabled.edit(Option.Sides, 1).value(Option.LeftX))
    }
}
