package com.mi.onextbox.lsp

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class SettingsHomeEntryContractTest {
    private fun mainFile(path: String): File = listOf(File("src/main/$path"), File("app/src/main/$path")).first { it.isFile }
    private fun source(name: String) = mainFile("java/com/mi/onextbox/$name.kt").readText()

    @Test fun choicesSupportLegacyMigrationAndConfirmFrameworkPersistence() {
        val settings = source("lsp/SettingsHomeEntrySettings")
        assertTrue(settings.contains("SettingsHomeEntryOptions.resolve"))
        assertTrue(settings.contains("HookConfigSnapshot.boolean(LEGACY_KEY, false) == true"))
        assertTrue(settings.contains("ModernXposedPreferenceSync.setIntAndSync(context, POSITION_KEY, position.value)"))
        assertTrue(settings.contains("ModernXposedPreferenceSync.setIntAndSync(context, ICON_STYLE_KEY, style.value)"))
        assertFalse(settings.contains("RootShell"))
    }

    @Test fun standaloneGroupIsSharedByBothAppearanceStyles() {
        val page = source("ui/settings/SettingsMainPage").substringAfter("fun AppSettingsPage(")
            .substringBefore("private fun Material3ExpressiveAppearanceSettingsGroup(")
        assertEquals(1, Regex("SettingsHomeEntrySettingsGroup\\(\\)").findAll(page).count())
        assertTrue(page.indexOf("SettingsHomeEntrySettingsGroup()") > page.indexOf("R.string.settings_section_display"))
        assertTrue(page.indexOf("SettingsHomeEntrySettingsGroup()") < page.indexOf("R.string.section_restore_backup"))
        val group = source("ui/settings/SettingsHomeEntrySettingsGroup")
        assertTrue(group.contains("SettingsSection(title = stringResource(R.string.settings_section_system_entry))"))
        assertTrue(group.contains("FeatureAnimatedGroup(animateChanges = true)"))
        assertEquals(2, Regex("SettingsWindowDropdownPreference\\(").findAll(group).count())
        assertTrue(group.contains("row(\"icon-style\", visible = options.enabled)"))
        assertEquals(1, Regex("SettingsToggleRow\\(").findAll(group).count())
        assertTrue(group.contains("row(\"hide-launcher-icon\")"))
        assertFalse(group.contains("setting_hide_module_icon_summary"))
        assertTrue(group.contains("FeatureSettingWriteQueue.application.enqueue"))
        assertTrue(group.contains("unregisterOnSharedPreferenceChangeListener"))
        assertTrue(group.contains("Lifecycle.Event.ON_RESUME"))
    }

    @Test fun entryUsesHostPreferencesExplicitLaunchAndNoGlobalResources() {
        val hooks = source("lsp/SettingsHomeEntryHooker")
        assertTrue(source("lsp/OosLspModuleEntry").contains("SettingsHomeEntryHooker.hook(classLoader)"))
        assertTrue(hooks.contains("com.oplus.settings.feature.homepage.OplusTopLevelSettings"))
        assertTrue(hooks.contains("SettingsSimpleJumpPreference"))
        assertTrue(hooks.contains("HomepageTopCategory"))
        assertTrue(hooks.contains("ComponentName(MODULE, \"\$MODULE.MainActivity\")"))
        assertTrue(hooks.contains("extras.getInt(POSITION_META, -1) == options.position.value"))
        assertTrue(hooks.contains("extras.getInt(STYLE_META, -1) == options.iconStyle.value"))
        assertTrue(hooks.contains("if (!options.enabled)"))
        assertTrue(hooks.contains("SettingsHomeEntryPlacement.orderFor"))
        assertTrue(hooks.contains("removeGroup(requireNotNull(call(existing, \"getParent\")), existing)"))
        assertTrue(hooks.contains("SettingsHomeEntryPlacement.attach("))
        assertTrue(hooks.contains("attachGroup = {"))
        assertTrue(hooks.contains("attachEntry = { call(group, \"addPreference\", row) == true }"))
        assertTrue(hooks.contains("rollback = { removeGroup(parent, group) }"))
        assertTrue(hooks.contains("SettingsHomeEntryPlacement.shouldRestore"))
        assertTrue(hooks.contains("Intent.makeMainActivity"))
        assertTrue(hooks.contains("ModernHookRegistry.unhookPrefix(prefix)"))
        assertTrue(hooks.contains("HotReloadLifecycle.register(STATE_KEY"))
        assertTrue(hooks.contains("detach = { fragments.keys.toList().forEach(::remove) }"))
        listOf("setOnPreferenceClickListener", "setReplacement", "RootShell", "killProcess").forEach {
            assertFalse(it, hooks.contains(it))
        }
    }

    @Test fun allNineLocalesContainTheEntryStringsExactlyOnce() {
        val keys = setOf("settings_section_system_entry", "setting_settings_home_entry",
            "setting_settings_home_entry_summary", "setting_settings_home_entry_save_failed",
            "setting_settings_home_entry_disabled", "setting_settings_home_entry_top", "setting_settings_home_entry_middle",
            "setting_settings_home_entry_bottom", "setting_settings_home_entry_style", "setting_settings_home_entry_style_system",
            "setting_settings_home_entry_style_app")
        listOf("values", "values-zh-rCN", "values-zh-rTW", "values-b+yue+Hant", "values-ja",
            "values-ja-rCN", "values-ko", "values-vi", "values-ru").forEach { locale ->
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(mainFile("res/$locale/strings.xml")).getElementsByTagName("string")
            keys.forEach { key ->
                val matches = (0 until nodes.length).map(nodes::item)
                    .filter { it.attributes.getNamedItem("name").nodeValue == key }
                assertEquals("$locale: $key", 1, matches.size)
                assertTrue(matches.single().textContent.isNotBlank())
            }
        }
    }
}
