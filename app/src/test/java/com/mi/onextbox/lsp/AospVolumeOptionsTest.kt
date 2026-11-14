package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class AospVolumeOptionsTest {
    class Shade {
        @JvmField var qsExpanded = false
        @JvmField var qsPanelVisible = false
        @JvmField var panelVisible = true
    }
    @Test fun notificationShadeAloneDoesNotSuppressVolume() {
        assertFalse(AospVolumeQsState.visible(Shade()))
    }
    @Test fun combinedAndSeparateControlCentersSuppressVolumeAndRecoverWhenClosed() {
        val shade = Shade()
        shade.qsExpanded = true
        assertTrue(AospVolumeQsState.visible(shade))
        shade.qsExpanded = false
        shade.qsPanelVisible = true
        assertTrue(AospVolumeQsState.visible(shade))
        shade.qsPanelVisible = false
        assertFalse(AospVolumeQsState.visible(shade))
    }
    @Test fun unavailableShadeDoesNotHideVolume() {
        assertFalse(AospVolumeQsState.visible(null))
        assertFalse(AospVolumeQsState.visible(Any()))
    }
    @Test fun layoutSentinelsAndConstraintsArePreserved() {
        for (percent in listOf(50, 100, 150)) {
            assertEquals(-1, AospVolumeSize.pixels(-1, percent))
            assertEquals(-2, AospVolumeSize.pixels(-2, percent))
            assertEquals(0, AospVolumeSize.pixels(0, percent))
        }
        assertEquals(32, AospVolumeSize.pixels(64, 50))
        assertEquals(64, AospVolumeSize.pixels(64, 100))
        assertEquals(96, AospVolumeSize.pixels(64, 150))
        assertEquals(1, AospVolumeSize.pixels(1, 50))
    }
    @Test fun corruptOrImportedValuesCannotProduceNegativeOrUnboundedSizes() {
        assertEquals(50, AospVolumeSize.percent(Int.MIN_VALUE))
        assertEquals(150, AospVolumeSize.percent(Int.MAX_VALUE))
        assertEquals(32, AospVolumeSize.pixels(64, -1000))
        assertEquals(96, AospVolumeSize.pixels(64, 1000))
    }
}
