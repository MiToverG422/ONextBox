package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class CameraFilterRecommendationRulesTest {
    @Test fun recommendationUsesActualFilterIdEvenWhenPhotoAndStreetPositionsDiffer() {
        val city = mapOf("Test city" to listOf("city-lut.bin"))
        val registered = mapOf("city-lut.bin" to 3)
        assertTrue(CameraFilterRecommendationRules.recommended("city-lut.bin", true, "Test city", registered, city))
        assertFalse(CameraFilterRecommendationRules.recommended("portra400_normal.bin", true, "Test city", registered, city))
        assertFalse(CameraFilterRecommendationRules.recommended("city-lut.bin", false, "Test city", registered, city))
        assertFalse(CameraFilterRecommendationRules.recommended("city-lut.bin", true, "None", registered, city))
        assertFalse(CameraFilterRecommendationRules.recommended("city-lut.bin", true, "Missing city", registered, city))
    }

    @Test fun extraFiltersAndEmptyGroupsNeverReadPastTheNativeGroup() {
        assertFalse(CameraFilterRecommendationRules.validIndex(10, 10))
        assertFalse(CameraFilterRecommendationRules.validIndex(-1, 10))
        assertFalse(CameraFilterRecommendationRules.validIndex(0, 0))
        assertTrue(CameraFilterRecommendationRules.validIndex(9, 10))
    }
}
