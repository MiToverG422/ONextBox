package com.mi.onextbox.ui.screens

import com.mi.onextbox.lsp.SystemCustomizationSettings
import org.junit.Assert.*
import org.junit.Test

class SystemCustomizationNavigationTest {
    @Test fun allNewControlsHaveSearchInventoryAndQuickSwitchEntries() {
        val actions = EnabledFeatureActions()
        for (feature in SystemCustomizationSettings.Feature.entries) {
            val definition = systemCustomizationDefinition(feature)
            assertEquals(definition.page, FeatureSearchIndex.entries.single { it.title == definition.title }.page)
            assertEquals(definition.page, EnabledFeatureCatalog.entries.single { it.title == definition.title }.page)
            assertTrue(actions.supports(definition.title))
        }
    }

    @Test fun audioAndClonePagesRequestTheirActualBackendScope() {
        assertTrue("system" in featureRestartPackages(FeaturePageMode.SystemUiVolume))
        assertTrue("system" in featureRestartPackages(FeaturePageMode.MultiApp))
        assertTrue(SystemCustomizationSettings.MULTI_APP in featureRestartPackages(FeaturePageMode.MultiApp))
    }
}
