package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class ColorOs16RefreshRateRulesTest {
    @Test fun choosesHardwareModeAtTheRequestedResolution() {
        val modes = listOf(
            ColorOs16RefreshRateRules.Mode(5, 1080, 2400, 120f),
            ColorOs16RefreshRateRules.Mode(11, 1440, 3200, 165f),
            ColorOs16RefreshRateRules.Mode(8, 1080, 2400, 164.98f),
        )
        assertEquals(8, ColorOs16RefreshRateRules.modeId(modes, 1080, 2400))
        assertEquals(11, ColorOs16RefreshRateRules.modeId(modes, 1440, 3200))
        assertNull(ColorOs16RefreshRateRules.modeId(modes, 720, 1600))
    }

    @Test fun leavesHardwareWithout165HzAndUnknownResolutionAlone() {
        val modes = listOf(ColorOs16RefreshRateRules.Mode(5, 1080, 2400, 144f))
        assertNull(ColorOs16RefreshRateRules.modeId(modes, 1080, 2400))
        assertNull(ColorOs16RefreshRateRules.modeId(modes, 0, 0))
        assertNull(ColorOs16RefreshRateRules.modeId(emptyList(), 1080, 2400))
        assertNull(ColorOs16RefreshRateRules.modeId(listOf(ColorOs16RefreshRateRules.Mode(-1, 1080, 2400, 165f)), 1080, 2400))
    }
}
