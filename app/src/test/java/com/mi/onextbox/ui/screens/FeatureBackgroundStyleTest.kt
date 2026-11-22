package com.mi.onextbox.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class FeatureBackgroundStyleTest {
    @Test fun storedOffAndFlowingChoicesKeepTheirIds() {
        assertEquals(listOf(0, 2), FeatureBackgroundStyleIds)
        for (id in FeatureBackgroundStyleIds) assertEquals(id, resolveFeatureBackgroundStyle(id))
    }

    @Test fun missingLegacyAndUnknownChoicesUseFlowingBackground() {
        for (id in listOf(null, 1, -1, 3)) assertEquals(2, resolveFeatureBackgroundStyle(id))
    }
}
