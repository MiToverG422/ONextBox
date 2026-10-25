package com.mi.onextbox.ui.screens

import com.mi.onextbox.R
import org.junit.Assert.*
import org.junit.Test

class EnabledFeatureActionsTest {
    @Test fun independentSignalStyleChildRequiresItsParentAndHasAQuickWriter() {
        val parent = R.string.feature_only_data_sim_signal_title
        val child = R.string.feature_only_data_sim_independent_title
        assertFalse(EnabledFeatureCatalog.isActive(child, setOf(child)))
        assertTrue(EnabledFeatureCatalog.isActive(child, setOf(parent, child)))
        assertTrue(EnabledFeatureActions().supports(child))
    }
    @Test fun everyInventoryEntryHasAnExplicitWriterOrDedicatedSettingsDestination() {
        val actions = EnabledFeatureActions()
        EnabledFeatureCatalog.entries.forEach {
            if (it.page == FeaturePageMode.CorePatch) {
                assertFalse("Core Patch options use their dedicated settings page", actions.supports(it.title))
                assertEquals(FeaturePageMode.AndroidSystem, EnabledFeatureCatalog.group(it.page))
            } else {
                assertTrue("Missing writer: ${it.title}", actions.supports(it.title))
            }
        }
    }

    @Test fun temporaryDisableRestoresExactSelectedMode() {
        val modes = EnabledFeatureModeMemory()
        assertEquals(0, modes.next(1, 2, false, 1))
        assertEquals(2, modes.next(1, 0, true, 1))
        assertEquals(0, modes.next(1, 2, false, 1))
        assertEquals(2, modes.next(1, 0, true, 1))
    }

    @Test fun choicesAreIndependentAndHonorExternalChanges() {
        val modes = EnabledFeatureModeMemory()
        modes.next(1, 2, false, 1)
        assertEquals(1, modes.next(2, 0, true, 1))
        assertEquals(3, modes.next(1, 3, true, 1))
    }

    @Test fun assistantZeroIsEnabledAndMinusOneIsOff() {
        val modes = EnabledFeatureModeMemory()
        assertEquals(-1, modes.next(3, 0, false, 0, -1))
        assertEquals(0, modes.next(3, -1, true, 0, -1))
    }

    @Test fun offRowCanRemainVisibleWhileItsChildrenCollapse() {
        val parent = R.string.feature_large_network_type_title
        val child = R.string.feature_large_network_artwork_title
        val configured = setOf(child)
        assertTrue(EnabledFeatureCatalog.dependenciesActive(parent, configured))
        assertFalse(EnabledFeatureCatalog.isActive(parent, configured))
        assertFalse(EnabledFeatureCatalog.dependenciesActive(child, configured))
        assertTrue(EnabledFeatureCatalog.dependenciesActive(child, configured + parent))
    }
}
