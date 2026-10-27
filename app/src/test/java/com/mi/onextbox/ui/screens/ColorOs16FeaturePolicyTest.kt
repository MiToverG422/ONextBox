package com.mi.onextbox.ui.screens

import com.mi.onextbox.R
import com.mi.onextbox.lsp.SystemAdaptationVersion
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ColorOs16FeaturePolicyTest {
    @Test fun availabilityComesFromTheAuditedRegistryAndAllCategoriesHaveChildren() {
        assertTrue(ColorOs16FeatureRegistry.entries.isNotEmpty())
        ColorOs16FeaturePolicy.categories.forEach { (page, children) ->
            assertTrue(page.name, children.isNotEmpty())
            assertEquals(children.size, children.distinct().size)
            children.forEach { assertTrue(it.name, ColorOs16FeaturePolicy.isPageAvailable(it)) }
        }
        FeaturePageMode.entries.forEach { page ->
            val titles = ColorOs16FeatureRegistry.titlesForPage(page)
            assertEquals(page.name, titles, ColorOs16FeaturePolicy.featureTitles[page].orEmpty())
            assertEquals(page.name, page in ColorOs16FeaturePolicy.categories || titles.isNotEmpty() ||
                page in setOf(FeaturePageMode.EnabledFeatures, FeaturePageMode.Updates), isColorOs16FeaturePageAvailable(page))
        }
    }

    @Test fun c16SearchAndInventoryContainAuditedFeaturesAndTheRelocatedInstallerCategory() {
        val search = featureSearchEntries(SystemAdaptationVersion.ColorOs16)
        val inventory = enabledFeatureEntries(SystemAdaptationVersion.ColorOs16)
        assertTrue(search.size > 7)
        assertTrue(inventory.size > 7)
        search.forEach {
            if (it.page == FeaturePageMode.PackageInstaller && it.title == R.string.package_installer_title) {
                assertEquals(listOf(FeaturePageMode.Installer, FeaturePageMode.CorePatch),
                    ColorOs16FeaturePolicy.categories[it.page])
            } else assertTrue(it.title.toString(), it.title in ColorOs16FeatureRegistry.titlesForPage(it.page))
        }
        inventory.forEach { assertTrue(it.title.toString(), it.title in ColorOs16FeatureRegistry.titlesForPage(it.page)) }
        assertFalse(ColorOs16FeaturePolicy.isFeatureAvailable(SystemAdaptationVersion.ColorOs16,
            FeaturePageMode.DesktopIcons, R.string.feature_hide_disconnected_bluetooth_title))
        assertFalse(search.any { it.title == R.string.feature_permanent_clock_seconds_title })
        assertFalse(search.any { it.title == R.string.feature_monet_color_spec_title })
        assertFalse(search.any { it.title == R.string.keyguard_swipe_pin_title })
    }

    @Test fun c17RetainsItsEntireSearchAndInventory() {
        assertEquals(FeatureSearchIndex.entries, featureSearchEntries(SystemAdaptationVersion.ColorOs17))
        assertEquals(EnabledFeatureCatalog.entries, enabledFeatureEntries(SystemAdaptationVersion.ColorOs17))
    }

    @Test fun advancedEditorsAreNotDuplicatedByGenericToggles() {
        val advanced = setOf(R.string.statusbar_order_title, R.string.carrier_name_title, R.string.power_indicator_title)
        val entries = listOf(FeaturePageMode.SystemUiStatusBarLayout,
            FeaturePageMode.SystemUiStatusBarNetwork, FeaturePageMode.SystemUiStatusBarPower)
            .flatMap { colorOs16CatalogEntries(it, advanced) }
        assertTrue(colorOs16CatalogEntries(FeaturePageMode.SystemUiStatusBar, emptySet()).isEmpty())
        assertTrue(entries.none { it.title in advanced })
        assertTrue(entries.any { it.title == R.string.feature_hide_disconnected_bluetooth_title })
        val actions = EnabledFeatureActions()
        entries.forEach { assertTrue(it.title.toString(), actions.supports(it.title)) }
    }

    @Test fun reusedWholePagesRequireEveryCurrentOptionAndRealmeFiltersUnsupportedChildren() {
        assertTrue(colorOs16PageHasAll(FeaturePageMode.MobileNetwork, colorOs16MobileNetworkTitles))
        assertTrue(colorOs16PageHasAll(FeaturePageMode.SecurityPermission, colorOs16SecurityPermissionTitles))
        assertFalse(colorOs16PageHasAll(FeaturePageMode.SystemUiNative, setOf(R.string.feature_aosp_volume_title)))
        val actions = EnabledFeatureActions()
        colorOs16AdditionalCatalogEntries.forEach { entry ->
            assertTrue(actions.supports(entry.title))
            assertTrue(entry.title in ColorOs16FeatureRegistry.titlesForPage(entry.page))
        }
        assertTrue(source("SettingsRealmePage.kt").contains("availableTitles == null || title in availableTitles"))
        assertTrue(source("ColorOs16Features.kt").contains("SettingsRealmePage(availableTitles ="))
    }

    @Test fun parentsAndExclusionsRemainTheCatalogRules() {
        val child = R.string.feature_settings_force_app_auto_start_title
        val parent = R.string.feature_settings_international_title
        assertFalse(ColorOs16FeaturePolicy.isAutoStartVisible(false))
        assertTrue(ColorOs16FeaturePolicy.isAutoStartVisible(true))
        assertFalse(EnabledFeatureCatalog.isActive(child, setOf(child)))
        assertTrue(EnabledFeatureCatalog.isActive(child, setOf(child, parent)))
        val artifact = R.string.feature_large_network_artwork_title
        val main = R.string.feature_large_network_type_title
        assertFalse(EnabledFeatureCatalog.dependenciesActive(artifact, emptySet()))
        assertTrue(EnabledFeatureCatalog.dependenciesActive(artifact, setOf(main)))
    }

    @Test fun restartTargetsUseAllRegisteredScopesAndDoNotRestartCorePatchOrInventory() {
        val c16 = SystemAdaptationVersion.ColorOs16
        val recorders = featureRestartPackages(FeaturePageMode.ScreenRecording, c16)
        assertTrue(recorders.containsAll(listOf("system", "com.android.systemui", "com.oplus.screenrecorder")))
        assertTrue(featureRestartPackages(FeaturePageMode.CorePatch, c16).isEmpty())
        assertTrue(featureRestartPackages(FeaturePageMode.EnabledFeatures, c16).isEmpty())
        FeaturePageMode.entries.forEach { page ->
            assertEquals(featureRestartPackages(page), featureRestartPackages(page, SystemAdaptationVersion.ColorOs17))
        }
    }

    @Test fun rendererPreservesAdvancedEditorsAndSerializedDependencyCheckedWrites() {
        val renderer = source("ColorOs16Features.kt")
        assertTrue(renderer.contains("actions.enqueue(app, entry, next)"))
        assertTrue(renderer.contains("EnabledFeatureCatalog.dependenciesActive(it.title, configured)"))
        assertTrue(renderer.contains("FeatureSettingWriteQueue.application.enqueue"))
        assertTrue(renderer.contains("preferences.unregisterOnSharedPreferenceChangeListener(listener)"))
        assertFalse(renderer.contains("SystemUiFeaturesPage("))
        assertFalse(renderer.contains("SettingsFeaturesPage("))
        val advanced = source("ColorOs16AdvancedFeatures.kt")
        listOf("LauncherGridSettingsGroup(", "CarrierNameSettingsGroup(", "PowerIndicatorSettingsGroup(",
            "StatusBarLayoutSettingsGroup", "QsTileCornerSettingsGroup(", "SideVolumePercentageSettingsGroup",
            "SettingsHomeEntrySettingsGroup(", "RecentTaskRadiusRow(", "WallpaperMotionGroup(",
            "NetworkIdentitySettingsGroup(", "openDefaultAssistantSettings(context)")
            .forEach { assertTrue(it, advanced.contains(it)) }
        assertTrue(source("FeaturesScreen.kt").contains("mode != FeaturePageMode.Aod && mode != FeaturePageMode.SystemMessages"))
    }

    private fun source(name: String): String = listOf(File("src/main/java/com/mi/onextbox/ui/screens/$name"),
        File("app/src/main/java/com/mi/onextbox/ui/screens/$name")).first { it.isFile }.readText()
}
