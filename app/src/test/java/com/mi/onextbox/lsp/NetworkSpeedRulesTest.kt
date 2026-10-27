package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class NetworkSpeedRulesTest {
    @Test fun customRefreshSupportsFasterAndSlowerIntervalsAndPreservesImmediateUpdates() {
        assertEquals(1000L, NetworkSpeedRules.refreshDelay(4000L, true))
        assertEquals(4000L, NetworkSpeedRules.refreshDelay(4000L, false))
        assertEquals(0L, NetworkSpeedRules.refreshDelay(0L, true))
        assertEquals(1000L, NetworkSpeedRules.refreshDelay(500L, true))
        assertEquals(2500L, NetworkSpeedRules.refreshDelay(4000L, true, 2500))
        assertEquals(10000L, NetworkSpeedRules.refreshDelay(4000L, true, 10000))
        assertEquals(500L, NetworkSpeedRules.refreshDelay(4000L, true, -100))
        assertEquals(10000L, NetworkSpeedRules.refreshDelay(4000L, true, Int.MAX_VALUE))
        assertEquals(-1L, NetworkSpeedRules.refreshDelay(-1L, true))
    }

    @Test fun customValuesAreBoundedBeforeSavingOrApplying() {
        val state = NetworkSpeedSettings.normalize(NetworkSpeedSettings.State(intervalMs = 0, fontSizeDp = 100))
        assertEquals(500, state.intervalMs)
        assertEquals(14, state.fontSizeDp)
        assertEquals(2, NetworkSpeedSettings.normalize(state.copy(fontSizeDp = -1)).fontSizeDp)
    }

    @Test fun fontFittingKeepsRequestedSizesThatFitAndCapsLargerGlyphs() {
        assertEquals(40f, NetworkSpeedRules.fittedGlyphSize(60f, 75, 100f, 30f), 0.001f)
        assertEquals(25f, NetworkSpeedRules.fittedGlyphSize(25f, 75, 100f, 30f), 0.001f)
        assertEquals(25f, NetworkSpeedRules.fittedGlyphSize(25f, 75, 100f, 0f), 0.001f)
    }

    @Test fun singleLineFitsTheRemainingIconSpaceWithoutGrowingPastIt() {
        val normal = NetworkSpeedRules.fitSingleLine(48f, 1f, 219f, 191f)
        assertEquals(1f, normal.second, 0.001f)
        assertEquals(191f, 219f * normal.first / 48f, 0.01f)
        val crowded = NetworkSpeedRules.fitSingleLine(48f, 1f, 300f, 120f)
        assertEquals(1f, crowded.second, 0.001f)
        assertEquals(120f, 300f * crowded.first / 48f * crowded.second, 0.01f)
        assertEquals(48f to 1f, NetworkSpeedRules.fitSingleLine(48f, 1f, 100f, 200f))
    }

    @Test fun stockTwoLineTextKeepsTheNativeNumberAndUnits() {
        assertEquals(NetworkSpeedRules.Text("12.3", "MB/S", false),
            NetworkSpeedRules.text("12.3", "MB/S", false, false, false))
    }

    @Test fun rateAndMagnitudeUnitsCanBeHiddenIndependently() {
        for (unit in listOf("B/s", "KB/s", "MB/S", "GB/S", "TB/s", "KiB/s")) {
            assertEquals(unit.substringBefore('/'), NetworkSpeedRules.text("1.25", unit, true, false, false).unit)
            assertEquals(unit.substring(unit.indexOf('/')), NetworkSpeedRules.text("1.25", unit, false, true, false).unit)
        }
    }

    @Test fun hidingBothUnitsCentersTheOriginalNumericValue() {
        assertEquals(NetworkSpeedRules.Text("0.00", "", true),
            NetworkSpeedRules.text("0.00", "KB/S", true, true, false))
        assertEquals(NetworkSpeedRules.Text("1,25", "", true),
            NetworkSpeedRules.text("1,25", "MB/S", true, true, true))
    }

    @Test fun singleLineSupportsAllFourUnitCombinationsWithoutChangingTheValue() {
        assertEquals("12.3 MB/S", NetworkSpeedRules.text("12.3", "MB/S", false, false, true).number)
        assertEquals("12.3 MB", NetworkSpeedRules.text("12.3", "MB/S", true, false, true).number)
        assertEquals("12.3/S", NetworkSpeedRules.text("12.3", "MB/S", false, true, true).number)
        assertEquals("12.3", NetworkSpeedRules.text("12.3", "MB/S", true, true, true).number)
    }

    @Test fun emptyOrLocalizedUnitsDoNotLeaveBlankRows() {
        assertEquals(NetworkSpeedRules.Text("0", "", true), NetworkSpeedRules.text("0", "", false, false, false))
        assertEquals("/秒", NetworkSpeedRules.text("3.00", "MB/秒", false, true, false).unit)
    }
}
