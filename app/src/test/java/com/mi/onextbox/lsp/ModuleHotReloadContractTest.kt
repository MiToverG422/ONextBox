package com.mi.onextbox.lsp

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ModuleHotReloadContractTest {
    private fun mainRoot(): File = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    private fun source(path: String): String = File(mainRoot(), "java/com/mi/onextbox/$path.kt").readText()

    @Test fun noAppPreferenceCanDisableHotReload() {
        assertFalse(File(mainRoot(), "java/com/mi/onextbox/lsp/ModuleHotReloadSettings.kt").exists())
        val callback = source("lsp/OosLspModuleEntry").substringAfter("override fun onHotReloading(")
            .substringBefore("override fun onHotReloaded(")
        assertFalse(callback.contains("getRemotePreferences"))
        assertFalse(callback.contains("getBoolean"))
        assertFalse(callback.contains("enabled"))
        File(mainRoot(), "java").walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            assertFalse(file.path, file.readText().contains("module_hot_reload_enabled"))
        }
    }

    @Test fun settingsEntryIsGoneFromTheSharedUi() {
        val settings = source("ui/settings/SettingsMainPage")
        assertFalse(settings.contains("ModuleHotReloadSettingsGroup"))
        assertFalse(settings.contains("ModuleHotReloadSettings"))
        assertFalse(settings.contains("setting_module_hot_reload"))
        assertTrue(settings.contains("SettingsHomeEntrySettingsGroup()"))
        assertTrue(settings.contains("R.string.section_restore_backup"))
    }

    @Test fun safetyChecksAndFailureRecoveryRemainActive() {
        val entry = source("lsp/OosLspModuleEntry")
        val preflight = entry.substringAfter("override fun onHotReloading(")
            .substringBefore("HotReloadLifecycle.prepare()")
        assertTrue(preflight.contains("HookConfigSnapshot.isListening"))
        assertTrue(preflight.contains("HookConfigSnapshot.isAvailable"))
        assertTrue(preflight.contains("!HotReloadLifecycle.isRetiring"))
        assertTrue(entry.contains("HotReloadLifecycle.prepare()"))
        assertTrue(entry.contains("HotReloadLifecycle.cancelPreparation(state)"))
        assertTrue(entry.contains("session.commit(param.oldHookHandles)"))
        assertTrue(entry.contains("session?.markFailed()"))
        assertTrue(entry.contains("HotReloadLifecycle.fail()"))
        assertTrue(ModuleHotReloadPolicy.canReload("com.android.settings", false, setOf("com.android.settings"), true))
        assertFalse(ModuleHotReloadPolicy.canReload("com.android.settings", false, setOf("com.android.settings"), false))
        assertFalse(ModuleHotReloadPolicy.canReload("com.android.settings", false, emptySet(), true))
        assertFalse(ModuleHotReloadPolicy.canReload("com.example.app", false, setOf("com.example.app"), true))
    }

    @Test fun obsoleteToggleStringsAreRemovedFromEveryLocale() {
        val retired = listOf("settings_section_module_hot_reload", "setting_module_hot_reload",
            "setting_module_hot_reload_summary", "setting_module_hot_reload_save_failed")
        listOf("values", "values-zh-rCN", "values-zh-rTW", "values-b+yue+Hant", "values-ja",
            "values-ja-rCN", "values-ko", "values-ru", "values-vi").forEach { locale ->
            val xml = File(mainRoot(), "res/$locale/strings.xml").readText()
            retired.forEach { assertFalse("$locale: $it", xml.contains("name=\"$it\"")) }
        }
    }
}
