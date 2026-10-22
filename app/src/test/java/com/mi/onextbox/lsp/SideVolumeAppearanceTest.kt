package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class SideVolumeAppearanceTest {
    @Test fun ordinarySliderShowsPercentageOnlyWhenEnabled() {
        assertTrue(SideVolumePercentageVisibility.shouldDraw(true, 0, false))
        assertFalse(SideVolumePercentageVisibility.shouldDraw(false, 0, false))
    }

    @Test fun nativeThinFlagsHidePercentageThroughoutThinAndReturnStates() {
        assertFalse(SideVolumePercentageVisibility.shouldDraw(true, 1, false))
        assertFalse(SideVolumePercentageVisibility.shouldDraw(true, 0, true))
        assertFalse(SideVolumePercentageVisibility.shouldDraw(true, 1, true))
    }

    @Test fun narrowingBeforeNativeFlagsUpdateAlreadyHidesText() {
        for (width in listOf(.98f, .8f, .5f, .16f)) {
            assertFalse(SideVolumePercentageVisibility.shouldDraw(true, 0, false, width))
        }
        assertTrue(SideVolumePercentageVisibility.shouldDraw(true, 0, false, 1f))
    }

    @Test fun defaultAppearanceRetainsSystemWidth() {
        val state = SideVolumeAppearanceSettings.State()
        assertFalse(state.keepWide)
        assertTrue(SideVolumeAppearanceSettings.keys.intersect(SideVolumePercentageSettings.keys).isEmpty())
    }
}
