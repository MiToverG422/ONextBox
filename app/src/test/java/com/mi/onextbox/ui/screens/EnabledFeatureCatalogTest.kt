package com.mi.onextbox.ui.screens

import com.mi.onextbox.R
import org.junit.Assert.*
import org.junit.Test

class EnabledFeatureCatalogTest {
    @Test fun inventoryIsANestedReadOnlyPage() {
        assertTrue(FeaturePageMode.EnabledFeatures.isNestedPage)
        assertTrue(featureRestartPackages(FeaturePageMode.EnabledFeatures).isEmpty())
        assertFalse(EnabledFeatureCatalog.entries.any { it.page == FeaturePageMode.EnabledFeatures })
    }

    @Test fun catalogHasUniqueTitlesAndValidAcyclicDependencies() {
        val entries = EnabledFeatureCatalog.entries
        val ids = entries.map { it.title }.toSet()
        assertEquals(entries.size, ids.size)
        fun visit(id: Int, path: Set<Int>) {
            assertFalse("Cyclic parent dependency", id in path)
            val entry = entries.single { it.title == id }
            assertTrue(ids.containsAll(entry.excludes))
            entry.parents.forEach { visit(it, path + id) }
        }
        ids.forEach { visit(it, emptySet()) }
        assertTrue(entries.size > 140)
    }

    @Test fun storedChildrenDoNotCountWithoutParents() {
        val child = R.string.feature_large_network_bold_title
        val font = R.string.feature_large_network_font_title
        val main = R.string.feature_large_network_type_title
        assertFalse(EnabledFeatureCatalog.isActive(child, setOf(child)))
        assertFalse(EnabledFeatureCatalog.isActive(child, setOf(child, font)))
        assertTrue(EnabledFeatureCatalog.isActive(child, setOf(child, font, main)))
        assertFalse(EnabledFeatureCatalog.isActive(child, setOf(child, font, main, R.string.feature_large_network_artwork_title)))
    }

    @Test fun artworkLteRequiresBothParents() {
        val lte = R.string.feature_large_network_lte_title
        val artwork = R.string.feature_large_network_artwork_title
        assertFalse(EnabledFeatureCatalog.isActive(lte, setOf(lte, artwork)))
        assertTrue(EnabledFeatureCatalog.isActive(lte, setOf(lte, artwork, R.string.feature_large_network_type_title)))
    }

    @Test fun googleEntryIsIndependentButRegionalChildrenAreNot() {
        assertTrue(EnabledFeatureCatalog.isActive(R.string.feature_settings_force_google_title, setOf(R.string.feature_settings_force_google_title)))
        assertFalse(EnabledFeatureCatalog.isActive(R.string.feature_settings_force_app_auto_start_title, setOf(R.string.feature_settings_force_app_auto_start_title)))
    }

    @Test fun subpagesShareScopeGroup() {
        assertEquals(FeaturePageMode.SystemUi, EnabledFeatureCatalog.group(FeaturePageMode.SystemUiNative))
        assertEquals(FeaturePageMode.Desktop, EnabledFeatureCatalog.group(FeaturePageMode.DesktopRecent))
        assertEquals(FeaturePageMode.PackageInstaller, EnabledFeatureCatalog.group(FeaturePageMode.Installer))
    }
}
