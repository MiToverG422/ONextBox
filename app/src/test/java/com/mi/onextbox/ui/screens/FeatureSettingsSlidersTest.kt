package com.mi.onextbox.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class FeatureSettingsSlidersTest {
    @Test fun brightnessUsesIntegerTicks() {
        assertEquals(80f, snapFeatureSliderValue(79.8f, 0f..255f, 254), 0f)
        assertEquals(160f, snapFeatureSliderValue(160.2f, 0f..255f, 254), 0f)
        assertEquals(0f, snapFeatureSliderValue(-1f, 0f..255f, 254), 0f)
        assertEquals(255f, snapFeatureSliderValue(256f, 0f..255f, 254), 0f)
    }

    @Test fun recentTaskRadiusUsesIntegerTicks() {
        assertEquals(26f, snapFeatureSliderValue(26.2f, 0f..260f, 259), 0f)
        assertEquals(0f, snapFeatureSliderValue(0f, 0f..260f, 259), 0f)
        assertEquals(260f, snapFeatureSliderValue(260f, 0f..260f, 259), 0f)
    }

    @Test fun multiplierUsesTenths() {
        assertEquals(1.6f, snapFeatureSliderValue(1.599f, 1f..3f, 19), 0.000001f)
        assertEquals(1.7f, snapFeatureSliderValue(1.66f, 1f..3f, 19), 0.000001f)
        assertEquals(1f, snapFeatureSliderValue(0.9f, 1f..3f, 19), 0f)
        assertEquals(3f, snapFeatureSliderValue(3.1f, 1f..3f, 19), 0f)
    }

    @Test fun navigationHandleTicksRemainUnchanged() {
        assertEquals(120f, snapFeatureSliderValue(120.1f, 40f..200f, 159), 0f)
        assertEquals(40f, snapFeatureSliderValue(39f, 40f..200f, 159), 0f)
        assertEquals(200f, snapFeatureSliderValue(201f, 40f..200f, 159), 0f)
        assertEquals(75f, snapFeatureSliderValue(75.2f, 0f..100f, 99), 0f)
    }

    @Test fun continuousSliderDoesNotRound() {
        assertEquals(1.234f, snapFeatureSliderValue(1.234f, 1f..3f, 0), 0f)
        assertEquals(1f, snapFeatureSliderValue(0f, 1f..3f, 0), 0f)
    }

    @Test fun multiplierLabelHasNoExtraDecimalZeros() {
        assertEquals("1", formatAodMultiplier(1f))
        assertEquals("1.6", formatAodMultiplier(1.5999999f))
        assertEquals("3", formatAodMultiplier(3f))
    }
}
