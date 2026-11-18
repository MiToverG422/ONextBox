package com.mi.onextbox.ui.screens

import com.mi.onextbox.lsp.compat.MODERN_PREFERENCES_READY_KEY
import com.mi.onextbox.lsp.compat.MODERN_PREFERENCES_REVISION_KEY
import org.junit.Assert.*
import org.junit.Test

class FeatureInventoryRefreshTest {
    @Test fun narrowedPageStillReadsRecursiveParentsAndExclusionsWithoutUnrelatedFeatures() {
        val dependencies = mapOf(1 to setOf(2, 3), 2 to setOf(4), 3 to setOf(5), 9 to setOf(10))
        assertEquals(setOf(1, 2, 3, 4, 5), featureInventoryDependencyClosure(setOf(1), dependencies))
    }

    @Test fun cyclicAndUnknownDependenciesAreSafeAndIndependentRequestsAreKept() {
        val dependencies = mapOf(1 to setOf(2), 2 to setOf(1))
        assertEquals(setOf(1, 2, 42), featureInventoryDependencyClosure(setOf(1, 42), dependencies))
        assertTrue(featureInventoryDependencyClosure(emptySet(), dependencies).isEmpty())
    }

    @Test fun importsAndFeatureChangesStillRefreshButPublishingMetadataDoesNot() {
        assertTrue(isFeatureInventoryPreference(null))
        assertTrue(isFeatureInventoryPreference("systemui_aosp_volume"))
        assertFalse(isFeatureInventoryPreference(MODERN_PREFERENCES_READY_KEY))
        assertFalse(isFeatureInventoryPreference(MODERN_PREFERENCES_REVISION_KEY))
    }
}
