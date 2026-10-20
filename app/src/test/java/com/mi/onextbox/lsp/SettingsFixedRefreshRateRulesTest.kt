package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class SettingsFixedRefreshRateRulesTest {
    private val rates = floatArrayOf(30f, 60f, 90f, 120f, 144f, 165f, 185f)

    @Test fun staleWindowCallbacksCannotBlockIdleAfterTheSelectionIsReleasedOrScreenTurnsOff() {
        for (request in listOf(false, true)) for (selected in listOf(false, true)) for (screen in listOf(false, true)) {
            assertEquals(request && selected && screen,
                SettingsFixedRefreshRateRules.shouldBlockIdle(request, selected, screen))
        }
    }

    @Test fun explicitNativeChoicesResolveWithoutHardCodedDisplayModeIds() {
        assertEquals(90f, SettingsFixedRefreshRateRules.selectedHertz(1, 144), 0f)
        assertEquals(60f, SettingsFixedRefreshRateRules.selectedHertz(2, 144), 0f)
        assertEquals(120f, SettingsFixedRefreshRateRules.selectedHertz(3, 144), 0f)
        assertEquals(120f, SettingsFixedRefreshRateRules.selectedHertz(3, -1), 0f)
        assertEquals(90f, SettingsFixedRefreshRateRules.selectedHertz(3, 90), 0f)
        for (rate in listOf(144, 165, 185, 240)) {
            assertEquals(rate.toFloat(), SettingsFixedRefreshRateRules.selectedHertz(8, rate), 0f)
        }
    }

    @Test fun automaticUnknownAndMissingExtremeSelectionsAreNotForced() {
        for (mode in listOf(-1, 0, 5, 6, 9, 999)) {
            assertEquals(0f, SettingsFixedRefreshRateRules.selectedHertz(mode, 144), 0f)
        }
        for (mode in listOf(4, 7, 8)) {
            assertEquals(0f, SettingsFixedRefreshRateRules.selectedHertz(mode, -1), 0f)
            assertEquals(0f, SettingsFixedRefreshRateRules.selectedHertz(mode, 120), 0f)
        }
    }

    @Test fun eachSupportedSelectionStaysAtItsOwnRateNotThePanelMaximum() {
        for (selected in rates) {
            assertEquals(selected, SettingsFixedRefreshRateRules.effectiveHertz(selected, rates, true, false), 0f)
        }
    }

    @Test fun unsupportedRatesAndResolutionSpecificGapsFailClosed() {
        assertFalse(SettingsFixedRefreshRateRules.isSupported(90f, floatArrayOf(60f, 120f)))
        assertEquals(0f, SettingsFixedRefreshRateRules.effectiveHertz(144f, floatArrayOf(60f, 120f), true, false), 0f)
        assertEquals(0f, SettingsFixedRefreshRateRules.effectiveHertz(144f, floatArrayOf(), true, false), 0f)
        assertEquals(0f, SettingsFixedRefreshRateRules.effectiveHertz(240f, rates, true, false), 0f)
    }

    @Test fun physicalTimingToleranceUsesTheActualSupportedMode() {
        assertTrue(SettingsFixedRefreshRateRules.isSupported(120f, floatArrayOf(119.88f)))
        assertEquals(119.88f,
            SettingsFixedRefreshRateRules.effectiveHertz(120f, floatArrayOf(119.88f), true, false), 0f)
    }

    @Test fun screenOffAndSensitiveNativeScenesAlwaysRestoreOemPolicy() {
        assertEquals(0f, SettingsFixedRefreshRateRules.effectiveHertz(144f, rates, false, false), 0f)
        assertEquals(0f, SettingsFixedRefreshRateRules.effectiveHertz(144f, rates, true, true), 0f)
    }

    @Test fun thermalAndBrightnessCapsReduceTheFixedRateToASupportedSafeMode() {
        assertEquals(120f, SettingsFixedRefreshRateRules.effectiveHertz(144f, rates, true, false, 120f), 0f)
        assertEquals(60f, SettingsFixedRefreshRateRules.effectiveHertz(144f, rates, true, false, 80f), 0f)
        assertEquals(90f, SettingsFixedRefreshRateRules.effectiveHertz(90f, rates, true, false, 120f), 0f)
        assertEquals(0f, SettingsFixedRefreshRateRules.effectiveHertz(144f, rates, true, false, 20f), 0f)
    }

    @Test fun malformedRatesAndCapsNeverProduceAnInvalidPhysicalRequest() {
        for (value in listOf(Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, -1f, 0f)) {
            assertFalse(SettingsFixedRefreshRateRules.isSupported(value, rates))
            assertEquals(0f, SettingsFixedRefreshRateRules.effectiveHertz(value, rates, true, false), 0f)
        }
        assertEquals(0f, SettingsFixedRefreshRateRules.effectiveHertz(144f, rates, true, false, Float.NaN), 0f)
        assertEquals(0f, SettingsFixedRefreshRateRules.effectiveHertz(144f, rates, true, false, 0f), 0f)
        assertEquals(144f, SettingsFixedRefreshRateRules.effectiveHertz(144f,
            floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, -60f, 144f), true, false), 0f)
    }
}
