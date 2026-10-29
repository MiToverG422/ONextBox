package com.mi.onextbox.lsp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CouiDividerViewRulesTest {
    @Test fun matchesOnlyDedicatedDividerResources() {
        assertTrue(CouiDividerViewRules.isDividerBackground("couiColorDivider"))
        assertTrue(CouiDividerViewRules.isDividerBackground("coui_divider_horizontal_without_padding"))
        assertFalse(CouiDividerViewRules.isDividerBackground("couiColorPrimary"))
        assertFalse(CouiDividerViewRules.isDividerBackground("progress_background"))
    }

    @Test fun acceptsThinLinesAtDifferentDensities() {
        assertTrue(CouiDividerViewRules.isDividerView("android.view.View", -1, 1, 1f))
        assertTrue(CouiDividerViewRules.isDividerView("android.widget.ImageView", -1, 3, 3f))
        assertTrue(CouiDividerViewRules.isDividerView("android.view.View", 3, 66, 3f))
    }

    @Test fun excludesProgressControlsCardsIconsAndUnknownDimensions() {
        assertFalse(CouiDividerViewRules.isDividerView("android.widget.ProgressBar", -1, 1, 3f))
        assertFalse(CouiDividerViewRules.isDividerView("com.coui.appcompat.seekbar.COUISeekBar", -1, 1, 3f))
        assertFalse(CouiDividerViewRules.isDividerView("android.view.View", -1, 300, 3f))
        assertFalse(CouiDividerViewRules.isDividerView("android.widget.ImageView", 72, 72, 3f))
        assertFalse(CouiDividerViewRules.isDividerView("android.view.View", 1, 1, 3f))
        assertFalse(CouiDividerViewRules.isDividerView("android.view.View", -1, -2, 3f))
    }
}
