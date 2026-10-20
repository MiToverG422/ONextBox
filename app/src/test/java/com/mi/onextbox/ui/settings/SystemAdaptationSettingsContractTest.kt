package com.mi.onextbox.ui.settings

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class SystemAdaptationSettingsContractTest {
    @Test fun separateGroupIsAboveAppearanceAndUsesTheLanguageDropdownComponent() {
        val page = source("ui/settings/SettingsMainPage.kt").substringAfter("fun AppSettingsPage(")
            .substringBefore("private fun Material3ExpressiveAppearanceSettingsGroup(")
        assertTrue(page.indexOf("SettingsSystemAdaptationGroup()") >= 0)
        assertTrue(page.indexOf("SettingsSystemAdaptationGroup()") < page.indexOf("R.string.settings_section_display"))
        val group = source("ui/settings/SettingsSystemAdaptationGroup.kt")
        assertTrue(group.contains("SettingsSection("))
        assertTrue(group.contains("SettingsGroup {"))
        assertTrue(group.contains("SettingsWindowDropdownPreference("))
        assertTrue(source("ui/settings/SettingsLanguageDropdown.kt").contains("SettingsWindowDropdownPreference("))
    }

    @Test fun disabledOptionsAreGuardedInTheMenuHandlerAndPersistenceLayer() {
        val group = source("ui/settings/SettingsSystemAdaptationGroup.kt")
        assertTrue(group.contains("enabled = version.selectable,"))
        assertTrue(group.contains("interactionEnabled = !saving"))
        assertTrue(group.contains("if (saving || !version.selectable"))
        assertTrue(group.contains("!version.selectable"))
        val settings = source("lsp/SystemAdaptationSettings.kt")
        assertTrue(settings.contains("require(version.selectable)"))
        assertFalse(settings.contains("ModernXposedPreferenceSync.setIntAndSync"))
        assertTrue(settings.contains("ModernXposedPreferenceSync.syncNow(context)"))
        assertTrue(group.contains("SystemAdaptationWriteQueue.writes.enqueue"))
        assertTrue(group.contains("SystemAdaptationVersion.visibleVersions(allowHyperOs)"))
        assertTrue(group.contains("finally {"))
    }

    @Test fun versionObservationHandlesChangesResetAndResume() {
        val state = source("ui/common/SystemAdaptationState.kt")
        assertTrue(state.contains("key == null || key == SystemAdaptationSettings.KEY"))
        assertTrue(state.contains("key == SystemAdaptationSettings.ALLOW_HYPER_OS"))
        assertTrue(state.contains("Lifecycle.Event.ON_RESUME"))
        assertTrue(state.contains("prefs.unregisterOnSharedPreferenceChangeListener(listener)"))
        assertTrue(state.contains("owner.lifecycle.removeObserver(observer)"))
    }

    @Test fun developerGateResetsSelectionInOneLocalCommit() {
        val settings = source("lsp/SystemAdaptationSettings.kt")
        val setter = settings.substringAfter("fun setHyperOsAllowed(").substringBefore("fun selectedVersionXposed(")
        assertTrue(setter.contains("prefs.edit().putBoolean(ALLOW_HYPER_OS, allowed)"))
        assertTrue(setter.contains("if (!allowed || !previouslyAllowed) edit.putInt(KEY, SystemAdaptationVersion.ColorOs17.value)"))
        assertEquals(1, Regex("edit\\.commit\\(\\)").findAll(setter).count())
        assertTrue(settings.contains("HookConfigSnapshot.boolean(ALLOW_HYPER_OS, false) == true"))
        assertTrue(source("ui/settings/SettingsMainPage.kt").substringAfter("fun DeveloperOptionsPage(")
            .contains("DeveloperHyperOsSupportGroup()"))
        assertTrue(source("ui/settings/DeveloperHyperOsSupportGroup.kt")
            .contains("SystemAdaptationWriteQueue.writes.enqueue"))
    }

    @Test fun emptyAdaptationBlocksMainSearchActionsToolsAndRestoredSubroutes() {
        val features = source("ui/screens/FeaturesScreen.kt")
        val main = features.substringAfter("fun FeatureMainRoute(").substringBefore("fun FeatureSubRoute(")
        val sub = features.substringAfter("fun FeatureSubRoute(").substringBefore("internal fun featurePageTitle")
        for (route in listOf(main, sub, source("ui/screens/ToolsScreen.kt"))) {
            assertTrue(route.contains("if (!rememberSystemAdaptationVersion().hasColorOs17Pages)"))
            assertTrue(route.contains("AdaptationEmptyPage("))
            assertTrue(route.contains("return"))
        }
        assertTrue(main.indexOf("AdaptationEmptyPage(") < main.indexOf("rememberTextFieldState()"))
        assertTrue(sub.indexOf("AdaptationEmptyPage(") < sub.indexOf("EnabledFeaturesPage("))
        assertTrue(features.contains("private fun colorOs17FeatureMainEntries()"))
        assertTrue(features.contains("private fun colorOs17ToolsMainEntries()"))
    }

    private fun source(path: String): String = File("src/main/java/com/mi/onextbox/$path").readText()
}
