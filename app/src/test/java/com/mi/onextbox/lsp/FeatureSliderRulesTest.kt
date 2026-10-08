package com.mi.onextbox.lsp

import org.junit.Assert.assertEquals
import org.junit.Test

class FeatureSliderRulesTest {
    @Test fun defaultBrightnessLeavesTheSystemValueUnchanged() {
        for (original in listOf(0, 20, 40, 80, 160, 255)) {
            assertEquals(original, FeatureSliderRules.initBrightness(original, -1))
        }
    }

    @Test fun customBrightnessDoesNotReplacePanoramicSentinels() {
        for (configured in listOf(-1, 0, 80, 160, 255)) {
            assertEquals(-1, FeatureSliderRules.initBrightness(-1, configured))
            assertEquals(-2, FeatureSliderRules.initBrightness(-2, configured))
        }
    }

    @Test fun existingBrightnessValuesRemainCustom() {
        for (configured in listOf(0, 80, 160, 255)) {
            assertEquals(configured, FeatureSliderRules.normalizeBrightness(configured))
            assertEquals(configured, FeatureSliderRules.initBrightness(20, configured))
        }
    }

    @Test fun brightnessNormalizationPreservesDefaultAndBounds() {
        assertEquals(-1, FeatureSliderRules.normalizeBrightness(-1))
        assertEquals(0, FeatureSliderRules.normalizeBrightness(-2))
        assertEquals(255, FeatureSliderRules.normalizeBrightness(300))
    }

    @Test fun radiusNormalizationPreservesDefaultAndExistingValues() {
        for (value in listOf(-1, 0, 20, 26, 260)) {
            assertEquals(value, FeatureSliderRules.normalizeRadius(value))
            assertEquals(value.toFloat(), FeatureSliderRules.normalizeRadius(value.toFloat()), 0f)
        }
        assertEquals(0, FeatureSliderRules.normalizeRadius(-2))
        assertEquals(260, FeatureSliderRules.normalizeRadius(300))
        assertEquals(0f, FeatureSliderRules.normalizeRadius(-2f), 0f)
        assertEquals(260f, FeatureSliderRules.normalizeRadius(300f), 0f)
    }

    @Test fun invalidRadiusFallsBackToSystemDefault() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(-1f, FeatureSliderRules.normalizeRadius(value), 0f)
        }
    }

    @Test fun slidingToKnownNativeRadiusRestoresDefault() {
        assertEquals(-1, FeatureSliderRules.radiusFromSlider(20, 20))
        assertEquals(-1, FeatureSliderRules.radiusFromSlider(26, 26))
        assertEquals(-1, FeatureSliderRules.radiusFromSlider(0, 0))
        assertEquals(30, FeatureSliderRules.radiusFromSlider(30, 26))
        assertEquals(-1, FeatureSliderRules.radiusFromSlider(-1, 26))
    }

    @Test fun unknownNativeRadiusIsNotGuessedFromPreview() {
        assertEquals(26, FeatureSliderRules.radiusFromSlider(26, null))
        assertEquals(0, FeatureSliderRules.radiusFromSlider(0, null))
        assertEquals(-1, FeatureSliderRules.radiusFromSlider(-1, null))
    }

    @Test fun identityMultiplierIsSystemDefault() {
        assertEquals(-1f, FeatureSliderRules.normalizeMultiplier(-1f), 0f)
        assertEquals(-1f, FeatureSliderRules.normalizeMultiplier(1f), 0f)
        assertEquals(-1f, FeatureSliderRules.multiplierFromSlider(1f), 0f)
    }

    @Test fun existingMultiplierIsPreserved() {
        for (value in listOf(1.2f, 1.6f, 3f)) {
            assertEquals(value, FeatureSliderRules.normalizeMultiplier(value), 0f)
        }
    }

    @Test fun multiplierIsBoundedAndInvalidValuesUseDefault() {
        assertEquals(-1f, FeatureSliderRules.normalizeMultiplier(0f), 0f)
        assertEquals(3f, FeatureSliderRules.normalizeMultiplier(4f), 0f)
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(-1f, FeatureSliderRules.normalizeMultiplier(value), 0f)
        }
    }

    @Test fun sliderMultiplierUsesTenthsAndRestoresDefaultAtOne() {
        assertEquals(1.6f, FeatureSliderRules.multiplierFromSlider(1.5999999f), 0f)
        assertEquals(1.7f, FeatureSliderRules.multiplierFromSlider(1.66f), 0f)
        assertEquals(-1f, FeatureSliderRules.multiplierFromSlider(1.000001f), 0f)
    }
}
