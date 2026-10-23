package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class QsControlCenterCornerTargetsTest {
    @Test fun separateControlCenterBrightnessAndVolumeLayoutsAreSupported() {
        assertTrue(QsControlCenterCornerTargets.isMainSlider("com.oplus.systemui.plugins.qs.widget.OplusQsToggleSliderLayout"))
    }
    @Test fun combinedControlCenterBrightnessAndVolumeLayoutsAreSupported() {
        assertTrue(QsControlCenterCornerTargets.isMainSlider("com.oplus.systemui.qs.widget.OplusQsToggleSliderLayout"))
    }
    @Test fun detailSliderSharesCornersButIsNotAGridTile() {
        assertTrue(QsControlCenterCornerTargets.isSlider(QsControlCenterCornerTargets.DETAIL_SLIDER))
        assertFalse(QsControlCenterCornerTargets.isMainSlider(QsControlCenterCornerTargets.DETAIL_SLIDER))
        assertEquals(3, QsControlCenterCornerTargets.sliders.size)
    }
    @Test fun standaloneVolumePanelsAndUnrelatedSlidersAreExcluded() {
        for (type in listOf(
            "com.oplus.systemui.volume.OplusVolumeSeekBar",
            "com.oplus.systemui.qs.widget.OplusToggleSliderView",
            QsControlCenterCornerTargets.BASE_SLIDER,
            QsControlCenterCornerTargets.SEEK_BAR,
        )) assertFalse(type, QsControlCenterCornerTargets.isSlider(type))
    }
    @Test fun matchingIsExactNotAnUnboundedClassNameSubstring() {
        assertEquals(2, QsControlCenterCornerTargets.mainSliders.size)
        for (type in QsControlCenterCornerTargets.sliders) {
            assertFalse(QsControlCenterCornerTargets.isSlider("$type\$Detail"))
            assertFalse(QsControlCenterCornerTargets.isSlider("${type}Fake"))
        }
    }
}
