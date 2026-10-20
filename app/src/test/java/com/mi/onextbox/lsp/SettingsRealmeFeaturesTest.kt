package com.mi.onextbox.lsp

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class SettingsRealmeFeaturesTest {
    private fun source(path: String) = File("src/main/java/com/mi/onextbox/$path").readText()
    @Test fun forcedVolumeEntryIsAvailableForPrimaryUserOnly() {
        assertEquals(3, SettingsRealmeFeatures.availability(false))
        assertEquals(0, SettingsRealmeFeatures.availability(true))
    }
    @Test fun brightnessOverrideIgnoresCachedVisibilityAndRestoresLiveNativeCheck() {
        var nativeSupported = false
        var reads = 0
        fun native() = nativeSupported.also { reads++ }
        // Controller construction used to cache false forever, or true after disabling.
        assertFalse(SettingsRealmeFeatures.brightnessAvailability(false, ::native))
        repeat(30) {
            assertTrue(SettingsRealmeFeatures.brightnessAvailability(true, ::native))
            assertFalse(SettingsRealmeFeatures.brightnessAvailability(false, ::native))
        }
        assertEquals(31, reads)
        nativeSupported = true
        assertTrue(SettingsRealmeFeatures.brightnessAvailability(false, ::native))
    }
    @Test fun processManagerKeepsItsExistingPreferenceAndFeaturesDefaultInactive() {
        assertEquals(SettingsProcessManager.KEY, SettingsRealmeFeature.ProcessManager.key)
        assertEquals(10, SettingsRealmeFeature.entries.map { it.key }.toSet().size)
        for (feature in SettingsRealmeFeature.entries) {
            assertFalse(feature.name, SettingsRealmeLaboratory.effective(feature) { _, default -> default })
        }
    }
    @Test fun newPageFollowsRegionAndOldProcessGroupIsGone() {
        val categories = source("ui/screens/FeatureCategoriesPage.kt")
        assertTrue(categories.indexOf("FeaturePageMode.SettingsRealme") > categories.indexOf("FeaturePageMode.SettingsRegion"))
        assertFalse(source("ui/screens/SettingsFeaturesPages.kt").contains("SettingsProcessManagerGroup()"))
        val root = source("ui/Root.kt")
        assertTrue(root.contains("entry<RootRoute.FeatureSettingsRealme>"))
        val page = source("ui/screens/SettingsRealmePage.kt")
        assertTrue(page.contains("FeatureAnimatedGroup"))
        assertTrue(page.contains("summary = summary"))
        val search = source("ui/screens/FeatureSearchIndex.kt")
        assertTrue(search.contains("Entry(FeaturePageMode.SettingsRealme, R.string.settings_show_process_manager_title)"))
    }

    @Test fun standaloneDependenciesAreExplicitAndAlwaysCanBeDisabled() {
        assertEquals("com.realme.wellbeing", SettingsRealmeDependencies.packageName(SettingsRealmeFeature.SleepCapsule))
        assertEquals("com.oplus.heartrate.detect", SettingsRealmeDependencies.packageName(SettingsRealmeFeature.HeartRate))
        assertNull(SettingsRealmeDependencies.packageName(SettingsRealmeFeature.DcDimming))
        assertNull(SettingsRealmeDependencies.packageName(SettingsRealmeFeature.MultipleFaces))
        assertFalse(SettingsRealmeDependencies.canChange(checked = false, dependencyAvailable = false))
        assertTrue(SettingsRealmeDependencies.canChange(checked = true, dependencyAvailable = false))
        assertTrue(SettingsRealmeDependencies.canChange(checked = false, dependencyAvailable = true))
        val page = source("ui/screens/SettingsRealmePage.kt")
        assertTrue(page.contains("Lifecycle.Event.ON_RESUME"))
        assertTrue(page.contains("settings_realme_dependency_unavailable"))
    }

    @Test fun allFiveFeaturesAreInSearchEnabledListActionsAndEveryLocale() {
        val features = listOf("multiple_faces", "sleep_capsule", "heart_rate", "laboratory", "dc_dimming", "dual_music")
        for (file in listOf("FeatureSearchIndex", "EnabledFeatureCatalog", "EnabledFeatureActions", "SettingsRealmePage")) {
            val content = source("ui/screens/$file.kt")
            for (feature in features) assertTrue("$file: $feature", content.contains("R.string.settings_realme_$feature"))
        }
        for (locale in listOf("values", "values-zh-rCN", "values-zh-rTW", "values-b+yue+Hant", "values-ja", "values-ja-rCN", "values-ko", "values-ru", "values-vi")) {
            val strings = File("src/main/res/$locale/strings.xml").readText()
            for (key in features + listOf("sleep_dependency", "heart_dependency", "dependency_unavailable")) {
                assertEquals("$locale: $key", 1, Regex("name=\"settings_realme_$key\"").findAll(strings).count())
            }
            assertTrue(strings.contains("com.realme.wellbeing"))
            assertTrue(strings.contains("com.oplus.heartrate.detect"))
        }
    }
}
