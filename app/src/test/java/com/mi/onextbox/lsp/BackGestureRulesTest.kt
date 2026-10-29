package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class BackGestureRulesTest {
    @Test fun absentStylePreservesBothLegacySwitchStates() {
        listOf(-1, -20, 3, 100).forEach { configured ->
            assertEquals(0, BackGestureRules.style(true, configured))
            assertEquals(1, BackGestureRules.style(false, configured))
        }
        for (style in 0..1) {
            assertEquals(style, BackGestureRules.style(false, style))
            assertEquals(style, BackGestureRules.style(true, style))
        }
    }

    @Test fun removedCapsuleFallsBackToColorOsRegardlessOfLegacyFlag() {
        assertEquals(1, BackGestureRules.style(false, 2))
        assertEquals(1, BackGestureRules.style(true, 2))
    }

    @Test fun unsetHapticSchemeKeepsTheActualNativeIndicatorsFeedback() {
        assertEquals(0, BackGestureRules.haptics(-1, true))
        assertEquals(1, BackGestureRules.haptics(-1, false))
        for (scheme in 0..1) {
            assertEquals(scheme, BackGestureRules.haptics(scheme, true))
            assertEquals(scheme, BackGestureRules.haptics(scheme, false))
        }
    }

    @Test fun reducedAreaFitsAtTopMiddleOrBottomWithoutExtendingPastTheDisplay() {
        assertTrue(BackGestureRules.areaAllows(0f, 1000, 50, 0))
        assertTrue(BackGestureRules.areaAllows(500f, 1000, 50, 0))
        assertFalse(BackGestureRules.areaAllows(501f, 1000, 50, 0))
        assertFalse(BackGestureRules.areaAllows(249f, 1000, 50, 50))
        assertTrue(BackGestureRules.areaAllows(250f, 1000, 50, 50))
        assertTrue(BackGestureRules.areaAllows(750f, 1000, 50, 50))
        assertFalse(BackGestureRules.areaAllows(751f, 1000, 50, 50))
        assertFalse(BackGestureRules.areaAllows(499f, 1000, 50, 100))
        assertTrue(BackGestureRules.areaAllows(500f, 1000, 50, 100))
        assertTrue(BackGestureRules.areaAllows(1000f, 1000, 50, 100))
    }

    @Test fun fullHeightKeepsTheNativeAreaAndZeroDisablesOnlyTheSelectedSide() {
        for (position in listOf(-100, 0, 50, 100, 200)) for (y in listOf(0f, 350f, 1000f)) {
            assertTrue(BackGestureRules.areaAllows(y, 1000, 100, position))
            assertFalse(BackGestureRules.areaAllows(y, 1000, 0, position))
        }
        assertTrue(BackGestureRules.areaAllows(Float.NaN, 1000, 50, 50))
        assertTrue(BackGestureRules.areaAllows(300f, 0, 50, 50))
    }

    @Test fun onlyCommittedSingleFingerUpGetsTheAdditionalPulse() {
        assertTrue(BackGestureRules.releaseHaptic(true, 1, true, 1))
        assertFalse(BackGestureRules.releaseHaptic(false, 1, true, 1))
        assertFalse(BackGestureRules.releaseHaptic(true, 3, true, 1))
        assertFalse(BackGestureRules.releaseHaptic(true, 1, false, 1))
        assertFalse(BackGestureRules.releaseHaptic(true, 1, true, 2))
        assertFalse(BackGestureRules.releaseHaptic(true, 2, true, 1))
    }
}
