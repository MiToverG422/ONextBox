package com.mi.onextbox.lsp

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class GameAssistantHookContractTest {
    private fun source(path: String) = listOf(File("src/$path"), File("app/src/$path")).first { it.isFile }.readText()
    @Test fun everyToggleIsDiscoverableAndEditable() {
        val resources = listOf("promotions", "realme_startup", "unlimited", "music", "root", "developer", "skins")
        for (name in listOf("FeatureSearchIndex.kt", "EnabledFeatureCatalog.kt", "EnabledFeatureActions.kt")) {
            val content = source("main/java/com/mi/onextbox/ui/screens/$name")
            resources.forEach { assertTrue("$name/$it", content.contains("R.string.game_assistant_${it}_title")) }
        }
        val routes = source("main/java/com/mi/onextbox/ui/Root.kt")
        assertTrue(routes.contains("subclass(RootRoute.FeatureGameAssistant::class)"))
        assertTrue(routes.contains("entry<RootRoute.FeatureGameAssistant>"))
        val screen = source("main/java/com/mi/onextbox/ui/screens/FeaturesScreen.kt")
        assertTrue(screen.contains("FeaturePageMode.GameAssistant -> GameAssistantFeaturesPage()"))
        assertTrue(screen.contains("FeaturePageMode.GameAssistant -> listOf(GameAssistantSettings.PACKAGE_NAME)"))
        assertEquals(1, source("main/resources/META-INF/xposed/scope.list").lineSequence().count { it == "com.oplus.games" })
        assertTrue(source("main/AndroidManifest.xml").contains("<package android:name=\"com.oplus.games\" />"))
        assertTrue(source("main/java/com/mi/onextbox/lsp/OosLspModuleEntry.kt").contains("GameAssistantHooker.hook(classLoader, target.sourceDir)"))
    }
    @Test fun noGlobalSecurityOrModelPropertiesAreFaked() {
        val hook = source("main/java/com/mi/onextbox/lsp/GameAssistantHooker.kt")
        assertTrue(hook.contains("Bundle(original)"))
        assertTrue(hook.contains("original.getInt(\"isSafe\") != 1"))
        assertTrue(hook.contains("GameAssistantRules.safetyStatus("))
        listOf("SystemProperties.set", "ro.vendor.oplus.market.name", "Build.MODEL", "adb_enabled", "ro.debuggable").forEach {
            assertFalse(it, hook.contains(it))
        }
        assertTrue(hook.contains("chain.args[11]"))
        assertFalse(hook.contains("NotificationManager::class"))
        assertFalse(hook.contains("subList("))
    }
    @Test fun developerPageUsesHostResourcesAndDoesNotApplyDefaultsOnOpen() {
        val source = source("main/java/com/mi/onextbox/lsp/GameAssistantDeveloperController.kt")
        assertTrue(source.contains("getIdentifier(\"game_develop_options_"))
        assertTrue(source.contains("GameAssistantRules.ENV_PREFERENCES"))
        assertTrue(source.contains("setOnCheckedChangeListener"))
        assertTrue(source.contains("canReload = { dialog?.isShowing != true && choiceDialog?.isShowing != true }"))
        assertFalse(source.contains("putBoolean(key, true)"))
        assertFalse(source.contains("getSharedPreferences(\"settings\""))
    }
    @Test fun developerBackendIsReadByTypeWithoutUnsupportedEnumeration() {
        val controller = source("main/java/com/mi/onextbox/lsp/GameAssistantDeveloperController.kt")
        val preferences = source("main/java/com/mi/onextbox/lsp/GameAssistantDeveloperPreferences.kt")
        assertFalse(controller.contains("prefs.all"))
        assertFalse(preferences.contains("prefs.all"))
        assertFalse(controller.contains(".getAll("))
        assertFalse(preferences.contains(".getAll("))
        assertTrue(preferences.contains("prefs.getBoolean(key, defaultValue)"))
        assertTrue(preferences.contains("prefs.getInt(key,"))
        assertTrue(preferences.contains("prefs.getString(key, defaultValue)"))
        assertTrue(controller.contains("GameAssistantDeveloperPreferences.boolean("))
        assertTrue(controller.contains("GameAssistantDeveloperPreferences.selection("))
        assertTrue(controller.contains("getAttributeResourceValue(ANDROID, \"defaultValue\", 0)"))
        assertTrue(controller.contains("host.resources.getValue(id, value, true)"))
    }
    @Test fun developerClickFailuresAndPickerLifetimeCannotCrashTheHost() {
        val controller = source("main/java/com/mi/onextbox/lsp/GameAssistantDeveloperController.kt")
        for (action in listOf("entry", "category", "choice:", "switch:", "selection:")) {
            assertTrue(action, controller.contains("action(host, \"$action"))
        }
        assertTrue(controller.contains("return runCatching { block(); true }"))
        assertTrue(controller.contains("R.string.game_assistant_developer_failed"))
        assertTrue(controller.contains("host.isFinishing || host.isDestroyed || HotReloadLifecycle.isRetiring"))
        assertTrue(controller.contains("finally {\n                                        button.setOnCheckedChangeListener(listener)"))
        assertTrue(controller.contains("popup.setOnDismissListener"))
        assertTrue(controller.contains("choiceDialog?.dismiss(); choiceDialog = null"))
    }
    @Test fun sidebarCacheIsReadOnlyAfterTheNativeHelperHasInitialized() {
        val cache = source("main/java/com/mi/onextbox/lsp/GameAssistantHooker.kt").substringAfter("private fun maxCache(")
            .substringBefore("private fun rootSafety(")
        assertFalse(cache.substringBefore("override fun beforeHookedMethod").contains("getInt(null)"))
        assertTrue(cache.contains("runCatching"))
        assertTrue(cache.contains("if (field.getInt(null) != limit) field.setInt(null, limit)"))
        assertTrue(cache.contains("resolved.setInt(null, stock)"))
    }
    @Test fun musicWhitelistReplacesMembershipOnlyAndFollowsTheTogglePerCall() {
        val hook = source("main/java/com/mi/onextbox/lsp/GameAssistantHooker.kt")
        val whitelist = hook.substringAfter("private fun musicWhitelist(")
        assertTrue(whitelist.contains("usingStrings(\"whitelistedPackages loaded size=\")"))
        assertTrue(whitelist.contains("usingStrings(\"media_controller_config\")"))
        assertTrue(whitelist.contains("if (on(GameAssistantSettings.MUSIC)) stock?.let { matchAll(loader, it, whitelist) } else stock"))
        assertTrue(whitelist.contains("if (on(GameAssistantSettings.MUSIC) && musicPlayersOnly.get() == true) stock?.let { matchAll(loader, it, List::class.java) } else stock"))
        assertTrue(whitelist.contains("musicPlayersOnly.set(true)"))
        assertTrue(whitelist.contains("finally { musicPlayersOnly.remove() }"))
        assertTrue(whitelist.contains("detach = { cache.set(null, null) }"))
        assertTrue(whitelist.contains("\"contains\" -> HotReloadLifecycle.withHook("))
        assertTrue(whitelist.contains("GameAssistantRules.musicMembership("))
        assertTrue(whitelist.contains("else -> called.invoke(stock, *(args ?: emptyArray()))"))
    }
    @Test fun skinChildUsesTheSamePopupSelectorAsStyleAndLanguageInBothUis() {
        val page = source("main/java/com/mi/onextbox/ui/screens/GameAssistantFeaturesPage.kt")
        assertTrue(page.contains("FeatureAnimatedGroup(animateChanges = true)"))
        assertTrue(page.contains("row(GameAssistantSettings.SKIN, states[GameAssistantSettings.SKINS] == true)"))
        assertTrue(page.contains("hasDividerAbove = above, hasDividerBelow = below"))
        assertFalse(page.contains("CouiConfirmDialog"))
        assertTrue(page.contains("SettingsWindowDropdownPreference("))
        assertTrue(page.contains("items = names"))
        assertTrue(page.contains("selectedIndex = GameAssistantRules.skins.indexOf(skin).coerceAtLeast(0)"))
        assertFalse(page.contains("skinExpanded"))
        assertFalse(page.contains("row(\"skin:"))
        assertFalse(page.contains("showExpandArrow"))
        assertTrue(page.contains("save(GameAssistantSettings.SKIN, selectedSkin = id)"))
        assertFalse(page.contains("game_assistant_skins_summary"))
        assertFalse(page.contains("game_assistant_skin_summary"))
        listOf("SettingsUiStyleDropdown.kt", "SettingsLanguageDropdown.kt").forEach { file ->
            assertTrue(source("main/java/com/mi/onextbox/ui/settings/$file").contains("SettingsWindowDropdownPreference("))
        }
        val selector = source("main/java/com/mi/onextbox/ui/settings/SettingsPreferenceRows.kt")
            .substringAfter("internal fun SettingsWindowDropdownPreference(").substringBefore("private fun Material3ExpressiveDropdownPreference")
        assertTrue(selector.contains("Material3ExpressiveDropdownPreference("))
        assertTrue(selector.contains("ColorOsWindowDropdownPreference("))
        assertTrue(selector.contains("collapseOnSelection = true"))
        assertTrue(page.contains("FeatureSettingWriteQueue.application.enqueue"))
    }
    @Test fun everySupportedLanguageHasEveryNewString() {
        for (locale in listOf("values", "values-zh-rCN", "values-zh-rTW", "values-b+yue+Hant", "values-ja", "values-ja-rCN", "values-ko", "values-ru", "values-vi")) {
            val file = listOf(File("src/main/res/$locale/strings.xml"), File("app/src/main/res/$locale/strings.xml")).first { it.isFile }
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
            val strings = (0 until nodes.length).map { nodes.item(it) }
                .filter { it.attributes.getNamedItem("name").nodeValue.startsWith("game_assistant_") }
            assertEquals(locale, 25, strings.size)
            strings.forEach { assertTrue(locale, it.textContent.isNotBlank()); assertFalse(locale, it.textContent.endsWith(".") || it.textContent.endsWith("。")) }
        }
    }
}
