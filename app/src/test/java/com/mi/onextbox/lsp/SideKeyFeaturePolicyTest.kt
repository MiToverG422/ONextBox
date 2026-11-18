package com.mi.onextbox.lsp

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class SideKeyFeaturePolicyTest {
    @Test fun featureCannotBeEnabledByAnOldOrNewPreference() {
        assertFalse(SideKeyFeaturePolicy.available)
        assertFalse(SideKeyFeaturePolicy.enabled(false))
        assertFalse(SideKeyFeaturePolicy.enabled(true))
        assertFalse(SideKeySettings.enabledXposed())
    }

    @Test fun disablingRetainsActionsAndGestureTiming() {
        val config = SideKeyConfig(
            single = SideKeyBinding(SideKeyAction.WeChatScan),
            long = SideKeyBinding(SideKeyAction.App, "com.example.app"),
            longMillis = 800,
            doubleMillis = 400,
        )
        listOf(false, true).forEach { enabled ->
            val normalized = SideKeyFeaturePolicy.normalize(SideKeyState(enabled, config))
            assertFalse(normalized.enabled)
            assertEquals(config, normalized.config)
            assertEquals(normalized, SideKeyFeaturePolicy.normalize(normalized))
        }
    }

    @Test fun bothHookEntrypointsReturnBeforeResolvingOrInstalling() {
        listOf("SideKeyHooker.kt", "SideKeySystemUiHooker.kt").forEach { name ->
            val text = source("lsp/$name")
            val guard = text.indexOf("if (!SideKeyFeaturePolicy.available) return")
            assertTrue(name, guard >= 0)
            assertTrue(name, guard < text.indexOf("HostClassLookup.load("))
            assertTrue(name, guard < text.indexOf("ModernHookRegistry.install"))
        }
    }

    @Test fun entryIsHiddenAndTheRetainedToggleIsDisabled() {
        val entry = source("ui/screens/SystemAppFeaturesPages.kt")
            .substringAfter("if (SideKeyFeaturePolicy.available) {")
        assertTrue(entry.contains("SettingsSection(title = stringResource(R.string.side_key_group))"))
        assertTrue(entry.contains("onOpenSubPage(FeaturePageMode.SideKey)"))
        val page = source("ui/screens/SideKeyFeaturesPage.kt")
        assertTrue(page.contains("enabled = SideKeyFeaturePolicy.available"))
        assertTrue(page.contains("SideKeyFeaturePolicy.normalize(change(SideKeyState(enabled, config)))"))
    }

    @Test fun persistenceImportAndFrameworkMirrorsEnforceDisabledState() {
        val settings = source("lsp/SideKeySettings.kt")
        assertTrue(settings.contains("queue(context).enqueue { SideKeyFeaturePolicy.normalize(transform(it)) }"))
        assertTrue(settings.contains("putBoolean(ENABLED, SideKeyFeaturePolicy.enabled(nextState.enabled))"))
        assertTrue(settings.contains("preferences.edit().putBoolean(ENABLED, false).commit()"))
        val sync = source("lsp/ModernXposedPreferenceSync.kt")
        assertTrue(sync.contains("SideKeySettings.enforceDisabled(it)"))
        assertTrue(sync.contains("SideKeySettings.enforceDisabled(source)"))
        val requested = mapOf(SideKeySettings.ENABLED to true, "unrelated" to true)
        val published = frameworkPreferenceValues(requested, sideKeyAvailable = false)
        assertEquals(false, published[SideKeySettings.ENABLED])
        assertEquals(true, published["unrelated"])
        assertEquals(true, requested[SideKeySettings.ENABLED])
        assertTrue(sync.contains("frameworkPreferenceValues(source.all, SideKeyFeaturePolicy.available)"))
        val backup = source("ui/common/ConfigBackup.kt")
        assertTrue(backup.contains("key == SideKeySettings.ENABLED && value is Boolean"))
        assertTrue(backup.contains("SideKeyFeaturePolicy.enabled(value)"))
    }

    private fun source(path: String): String = listOf(
        File("src/main/java/com/mi/onextbox/$path"), File("app/src/main/java/com/mi/onextbox/$path"),
    ).first { it.isFile }.readText()
}
