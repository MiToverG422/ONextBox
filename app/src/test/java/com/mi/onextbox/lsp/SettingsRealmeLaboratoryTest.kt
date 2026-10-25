package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class SettingsRealmeLaboratoryTest {
    private val master = SettingsRealmeFeature.Laboratory
    private val dc = SettingsRealmeFeature.DcDimming
    private val dual = SettingsRealmeFeature.DualMusic
    private fun selected(feature: SettingsRealmeFeature, values: Map<SettingsRealmeFeature, Boolean>) =
        SettingsRealmeLaboratory.selected(feature) { item, default -> values[item] ?: default }
    private fun effective(feature: SettingsRealmeFeature, values: Map<SettingsRealmeFeature, Boolean>) =
        SettingsRealmeLaboratory.effective(feature) { item, default -> values[item] ?: default }

    @Test fun cleanInstallKeepsLabOffAndBothChildrenDefaultSelectedButInactive() {
        assertFalse(selected(master, emptyMap()))
        for (child in listOf(dc, dual)) {
            assertTrue(selected(child, emptyMap()))
            assertFalse(effective(child, emptyMap()))
        }
    }

    @Test fun enablingParentAloneActivatesBothDefaultsWithOnePreferenceWrite() {
        val values = mapOf(master to true)
        assertTrue(effective(master, values))
        assertTrue(effective(dc, values))
        assertTrue(effective(dual, values))
    }

    @Test fun closingParentPreservesCustomChoicesAcrossRepeatedTogglesAndReloads() {
        val values = mutableMapOf(master to true, dc to false, dual to true)
        repeat(30) {
            values[master] = false
            assertFalse(effective(dc, values))
            assertFalse(effective(dual, values))
            assertFalse(selected(dc, values))
            assertTrue(selected(dual, values))
            values[master] = true
            assertFalse(effective(dc, values.toMap()))
            assertTrue(effective(dual, values.toMap()))
        }
    }

    @Test fun legacyChoicesMigrateWithoutReEnablingAnExplicitlyDisabledChild() {
        val legacy = mapOf(dc to true, dual to false)
        assertTrue(selected(master, legacy))
        assertTrue(effective(dc, legacy))
        assertFalse(effective(dual, legacy))
        assertFalse(selected(master, mapOf(dc to false, dual to false)))
        assertFalse(selected(master, legacy + (master to false)))
    }

    @Test fun parentRemainsIndependentWhenBothChildrenAreDisabled() {
        val values = mapOf(master to true, dc to false, dual to false)
        assertTrue(effective(master, values))
        assertFalse(effective(dc, values))
        assertFalse(effective(dual, values))
    }

    @Test fun legacyParentIsFrozenBeforeEditingItsLastEnabledChild() {
        val values = mutableMapOf(dc to true, dual to false)
        values[master] = selected(master, values)
        values[dc] = false
        assertTrue(selected(master, values))
        assertFalse(effective(dc, values))
        assertFalse(effective(dual, values))
        val source = java.io.File("src/main/java/com/mi/onextbox/lsp/SettingsRealmeFeatures.kt").readText()
        assertTrue(source.contains("if (!prefs.contains(SettingsRealmeFeature.Laboratory.key))"))
        assertTrue(source.indexOf("context, SettingsRealmeFeature.Laboratory.key") < source.indexOf("context, feature.key, enabled"))
    }

    @Test fun standaloneLabEntriesKeepLegacyEntranceButHonorExplicitParentOff() {
        for (app in listOf(SettingsRealmeFeature.SleepCapsule, SettingsRealmeFeature.HeartRate)) {
            val legacy = mapOf(app to true)
            assertTrue(selected(master, legacy))
            assertTrue(effective(app, legacy))
            assertFalse(effective(app, legacy + (master to false)))
            assertTrue(selected(app, legacy + (master to false)))
        }
    }

    @Test fun allFourNativeLabEntriesAreSubordinateAndStandaloneAppsDoNotAutoEnable() {
        assertEquals(SettingsRealmeLabEntries.entries.map { it.feature }.toSet(), SettingsRealmeLaboratory.children)
        for (app in listOf(SettingsRealmeFeature.SleepCapsule, SettingsRealmeFeature.HeartRate)) {
            assertFalse(selected(app, mapOf(master to true)))
            assertFalse(effective(app, mapOf(master to true)))
        }
        val page = java.io.File("src/main/java/com/mi/onextbox/ui/screens/SettingsRealmePage.kt").readText()
        val parentPosition = page.indexOf("SettingsRealmeFeature.Laboratory to")
        for (child in SettingsRealmeLaboratory.children) {
            assertTrue(child.name, page.indexOf("SettingsRealmeFeature.${child.name} to") > parentPosition)
        }
        assertTrue(page.contains("FeatureAnimatedGroup(animateChanges = true)"))
        assertTrue(page.contains("availableTitles == null || title in availableTitles"))
        assertTrue(page.contains("(feature !in SettingsRealmeLaboratory.children || values[SettingsRealmeFeature.Laboratory] == true)"))
    }

    @Test fun otherRealmeFeaturesAreNotControlledByLaboratory() {
        for (feature in listOf(SettingsRealmeFeature.ProcessManager, SettingsRealmeFeature.AppVolume,
            SettingsRealmeFeature.DirectionalSound, SettingsRealmeFeature.BrightnessRange, SettingsRealmeFeature.MultipleFaces)) {
            assertTrue(effective(feature, mapOf(feature to true, master to false)))
            assertFalse(effective(feature, mapOf(master to true)))
        }
    }
}
