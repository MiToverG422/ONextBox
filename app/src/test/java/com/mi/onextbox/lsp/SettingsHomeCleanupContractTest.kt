package com.mi.onextbox.lsp

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class SettingsHomeCleanupContractTest {
    private val app = listOf(File("."), File("app")).first { File(it, "src/main/java").isDirectory }
    private fun source(name: String) = File(app, "src/main/java/com/mi/onextbox/$name.kt").readText()

    @Test fun onlySettingsHomeIsHookedAndNoAccountOrUpdateDataIsChanged() {
        val hook = source("lsp/SettingsHomeCleanupHooker")
        val entry = source("lsp/OosLspModuleEntry")
        assertTrue(entry.substringAfter("PACKAGE_SETTINGS ->").substringBefore("PACKAGE_LAUNCHER ->")
            .contains("SettingsHomeCleanupHooker.hook(classLoader)"))
        listOf("OplusTopLevelSettings", "updateFirstGroupCategory", "mMessageEntryController",
            "updateMessageEntry", "mMessageEntries", "isMessageEntriesEmpty", "setVisible")
            .forEach { assertTrue(it, hook.contains(it)) }
        listOf("getContentResolver", "deleteMessage", "removeAccount", "Settings.System.put", "killProcess", "RootShell")
            .forEach { assertFalse(it, hook.contains(it)) }
        assertFalse(hook.contains("removePreference"))
    }

    @Test fun reloadUsesOnlyNativeOwnersAndVisibilityBooleansAndReleasesAllCallbacks() {
        val hook = source("lsp/SettingsHomeCleanupHooker")
        listOf("capture = session::capture, detach = session::detach", "resumeAfterRejection = session::schedule",
            "arrayOf(preference, state.nativeVisible)", "writeVisibility(preference, visible)",
            "preferences?.unregisterOnSharedPreferenceChangeListener(listener)", "main.removeCallbacksAndMessages(null)",
            "HotReloadLifecycle.restoredState(STATE)", "HotReloadLifecycle.afterCommit", "writingVisibility.get() === owner")
            .forEach { assertTrue(it, hook.contains(it)) }
        assertFalse(hook.contains("setOnPreferenceClickListener"))
        assertFalse(hook.contains("postDelayed"))
        assertTrue(hook.contains("SettingsHomeCleanupRules.shouldHideAccountCategory"))
    }

    @Test fun switchesAreIndependentDefaultOffAndConfirmedBeforeUiSuccess() {
        val settings = source("lsp/SettingsHomeCleanupSettings")
        assertTrue(settings.contains("getBoolean(key, false)"))
        assertTrue(settings.contains("HookConfigSnapshot.boolean(key, false) == true"))
        assertTrue(settings.contains("ModernXposedPreferenceSync.setBooleanAndSync(context, key, enabled)"))
        val group = source("ui/screens/SettingsHomeCleanupGroup")
        listOf("FeatureSettingWriteQueue.application.enqueue", "pending.await()", "finally { saving = false; refresh() }",
            "Lifecycle.Event.ON_RESUME", "unregisterOnSharedPreferenceChangeListener")
            .forEach { assertTrue(it, group.contains(it)) }
        assertTrue(source("ui/screens/SettingsFeaturesPages").contains("SettingsHomeCleanupGroup()"))
    }

    @Test fun bothFeaturesAreSearchableAndSwitchableFromEnabledFeatures() {
        listOf("FeatureSearchIndex", "EnabledFeatureCatalog", "EnabledFeatureActions").forEach { file ->
            val text = source("ui/screens/$file")
            listOf("settings_home_hide_account_title", "settings_home_hide_update_title")
                .forEach { assertTrue("$file: $it", text.contains(it)) }
        }
    }

    @Test fun allLocalesHaveTheFiveStringsAndTheChineseDescriptionsMatchTheRequestedShortText() {
        val keys = setOf("settings_home_cleanup_group", "settings_home_hide_account_title", "settings_home_hide_account_summary",
            "settings_home_hide_update_title", "settings_home_hide_update_summary")
        listOf("values", "values-zh-rCN", "values-zh-rTW", "values-b+yue+Hant", "values-ja", "values-ja-rCN",
            "values-ko", "values-vi", "values-ru").forEach { locale ->
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(File(app, "src/main/res/$locale/strings.xml")).getElementsByTagName("string")
            val strings = (0 until nodes.length).map { nodes.item(it) }
            keys.forEach { key ->
                val matches = strings.filter { it.attributes.getNamedItem("name").nodeValue == key }
                assertEquals("$locale: $key", 1, matches.size)
                assertTrue(matches.single().textContent.isNotBlank())
            }
            if (locale == "values-zh-rCN") {
                fun value(key: String) = strings.single { it.attributes.getNamedItem("name").nodeValue == key }.textContent
                assertEquals("隐藏系统设置首页的账号卡片", value("settings_home_hide_account_summary"))
                assertEquals("隐藏系统设置首页的更新提醒", value("settings_home_hide_update_summary"))
            }
        }
    }
}
