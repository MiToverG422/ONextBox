package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class QsSliderPercentageRulesTest {
    @Test fun detailBoundsAnimationKeepsTheSameScreenSpaceFontSizeAndAspectRatio() {
        val requestedSize = 28f
        for (step in 0..100) {
            val fraction = step / 100f
            val x = 0.4f + 0.6f * fraction
            val y = 0.25f + 0.75f * fraction
            val (sizeScale, widthScale) = QsSliderPercentageRules.textTransform(x, y)
            assertEquals(requestedSize, requestedSize * sizeScale * y, 0.0001f)
            assertEquals(requestedSize, requestedSize * sizeScale * widthScale * x, 0.0001f)
        }
        // Closing and spring overshoot follow the same calculation without an end-state jump.
        for ((x, y) in listOf(1f to 1f, 1.03f to 1.05f, .4f to .25f)) {
            val (sizeScale, widthScale) = QsSliderPercentageRules.textTransform(x, y)
            assertEquals(1f, sizeScale * y, 0.0001f)
            assertEquals(1f, sizeScale * widthScale * x, 0.0001f)
        }
    }

    @Test fun invalidDetailScalesLeaveTextAtItsNormalSize() {
        for ((x, y) in listOf(0f to 1f, 1f to 0f, -1f to 1f, Float.NaN to 1f,
            1f to Float.POSITIVE_INFINITY)) {
            assertEquals(1f to 1f, QsSliderPercentageRules.textTransform(x, y))
        }
    }

    @Test fun brightnessAndVolumeUseTheirActualRanges() {
        assertEquals(50, QsSliderPercentageRules.percentage(32767, 0, 65535))
        assertEquals(50, QsSliderPercentageRules.percentage(8, 1, 15))
        assertEquals(33, QsSliderPercentageRules.percentage(5, 0, 15))
        assertEquals(0, QsSliderPercentageRules.percentage(1, 1, 15))
        assertEquals(100, QsSliderPercentageRules.percentage(15, 1, 15))
    }

    @Test fun invalidRangesDoNotDrawAndOutOfRangeAnimationsAreClamped() {
        assertNull(QsSliderPercentageRules.percentage(1, 1, 1))
        assertNull(QsSliderPercentageRules.percentage(1, 10, 0))
        assertEquals(0, QsSliderPercentageRules.percentage(-1, 0, 100))
        assertEquals(100, QsSliderPercentageRules.percentage(120, 0, 100))
        assertEquals(50, QsSliderPercentageRules.percentage(0, Int.MIN_VALUE, Int.MAX_VALUE))
    }

    @Test fun roundedPercentagesStayMonotonicAcrossTheWholeBrightnessRange() {
        var last = 0
        for (progress in 0..65535) {
            val current = QsSliderPercentageRules.percentage(progress, 0, 65535)!!
            assertTrue(current in last..100)
            last = current
        }
        assertEquals(100, last)
    }

    @Test fun textBoundsRemainInsideTheTrackAtBothOffsetEndpoints() {
        val top = 12f
        val bottom = 212f
        val ascent = -24f
        val descent = 6f
        for (offset in 0..100) {
            val baseline = QsSliderPercentageRules.baseline(top, bottom, ascent, descent, offset)
            assertTrue(baseline + ascent >= top)
            assertTrue(baseline + descent <= bottom)
        }
        assertEquals(top, QsSliderPercentageRules.baseline(top, bottom, ascent, descent, 0) + ascent, 0.001f)
        assertEquals(bottom, QsSliderPercentageRules.baseline(top, bottom, ascent, descent, 100) + descent, 0.001f)
        assertEquals(53f, QsSliderPercentageRules.baseline(top, bottom, ascent, descent, 10), 0.001f)
    }

    @Test fun resizingTheSecondaryPanelPreservesTheRelativeOffset() {
        for (height in listOf(100f, 200f, 400f, 800f)) {
            val first = 20f
            val last = height - 5f
            val actual = QsSliderPercentageRules.baseline(0f, height, -20f, 5f, 10)
            assertEquals(0.1f, (actual - first) / (last - first), 0.00001f)
        }
    }

    @Test fun defaultSizeMatchesTheNativeSubtitleAndAttributeBoundsAreEnforced() {
        val state = QsSliderPercentageSettings.State()
        assertFalse(state.enabled)
        assertNull(state.color)
        assertEquals(11, state.sizeSp)
        assertEquals(10, state.offset)
        assertEquals(5, QsSliderPercentageRules.size(Int.MIN_VALUE))
        assertEquals(22, QsSliderPercentageRules.size(Int.MAX_VALUE))
        assertEquals(0, QsSliderPercentageRules.offset(-1))
        assertEquals(100, QsSliderPercentageRules.offset(101))
    }

    @Test fun offsetsClampAndTinyAnimatedTracksKeepTextCentered() {
        val first = QsSliderPercentageRules.baseline(0f, 100f, -20f, 5f, 0)
        val last = QsSliderPercentageRules.baseline(0f, 100f, -20f, 5f, 100)
        assertEquals(first, QsSliderPercentageRules.baseline(0f, 100f, -20f, 5f, -10))
        assertEquals(last, QsSliderPercentageRules.baseline(0f, 100f, -20f, 5f, 110))
        val center = QsSliderPercentageRules.baseline(0f, 10f, -20f, 5f, 10)
        assertEquals(5f, center + (-20f + 5f) / 2f, 0.001f)
    }
}
