package com.mi.onextbox.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class NormalizedDragValueTest {
    @Test fun regularRangesNormalizeProgressAndVelocity() {
        assertEquals(0.5f, normalizedDragValue(1.5f, 0f..3f), 0f)
        assertEquals(-2f, normalizedDragValue(-6f, 1f..4f), 0f)
    }

    @Test fun aSingleTabCannotProduceNaNOrInfinity() {
        assertEquals(0f, normalizedDragValue(0f, 0f..0f), 0f)
        assertEquals(0f, normalizedDragValue(1f, 0f..0f), 0f)
    }

    @Test fun invalidOrOverflowingValuesFailClosed() {
        for (value in listOf(Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY)) {
            assertEquals(0f, normalizedDragValue(value, 0f..3f), 0f)
        }
        assertEquals(0f, normalizedDragValue(1f, 3f..0f), 0f)
        assertEquals(0f, normalizedDragValue(1f, Float.NEGATIVE_INFINITY..Float.POSITIVE_INFINITY), 0f)
        assertEquals(0f, normalizedDragValue(Float.MAX_VALUE, 0f..Float.MIN_VALUE), 0f)
    }
}
