package com.mi.onextbox.lsp

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class LauncherWidgetMenuContractTest {
    private fun source(path: String) = listOf(File("src/$path"), File("app/src/$path")).first { it.isFile }.readText()

    @Test fun settingIsDefaultOffIndependentlyWritableAndUsesTheExistingBackupStore() {
        val setting = source("main/java/com/mi/onextbox/lsp/LauncherGestureSettings.kt")
        assertTrue(setting.contains("const val WIDGETS = \"launcher_widgets_in_icon_menu\""))
        assertTrue(setting.contains("getBoolean(WIDGETS, false)"))
        assertTrue(setting.contains("HookConfigSnapshot.boolean(WIDGETS, false) == true"))
        assertTrue(setting.contains("require(key == MENU || key == WIDGETS || key == TOP || key == SPACING_ENABLED)"))
        assertTrue(setting.contains("putBoolean(key, enabled).commit()"))
        assertTrue(setting.contains("LspConfig.syncReadableState(context)"))
        assertTrue(source("main/java/com/mi/onextbox/ui/common/ConfigBackup.kt").contains("LSP_PREFS_NAME"))
    }

    @Test fun adapterOnlyRestoresTheNativeFactoryAndDelegatesWidgetDiscoveryAndClickHandling() {
        val hook = source("main/java/com/mi/onextbox/lsp/LauncherWidgetMenuHooker.kt")
        assertTrue(hook.contains("com.android.launcher.Launcher"))
        assertTrue(hook.contains("getDeclaredField(\"WIDGETS\")"))
        assertTrue(hook.contains("getDeclaredMethod(\"getShortcut\", Context::class.java, item, View::class.java)"))
        assertTrue(hook.contains("launcher.getDeclaredMethod(\"getSupportedShortcuts\")"))
        assertTrue(hook.contains("if (!enabled()) return@Hooker original"))
        assertTrue(hook.contains("LauncherWidgetMenuRules.appendFactory(factories, widgets).stream()"))
        assertTrue(hook.contains("chain.proceed().also"))
        assertTrue(hook.contains("!HotReloadLifecycle.isRetiring && LauncherGestureSettings.widgetsXposed()"))
        for (unsafe in listOf("setAppWidget", "bindAppWidgetId", "allocateAppWidgetId", "onClick\"", "getWidgetsForPackageUser\"",
            "startActivity", "Intent(", "mAddShortcutCount", "menuXposed()", "setObjectField", "newInstance")) {
            assertFalse(unsafe, hook.contains(unsafe))
        }
    }

    @Test fun eligibleIconsAreCheckedAndCompiledCallersAreDeoptimized() {
        val hook = source("main/java/com/mi/onextbox/lsp/LauncherWidgetMenuHooker.kt")
        for (api in listOf("itemType", "isDisabled", "isPromise", "getIsArchived", "isNonInstalledApp", "LauncherWidgetMenuRules.eligible")) {
            assertTrue(api, hook.contains(api))
        }
        assertTrue(hook.contains("if (!allowed) return@Hooker null"))
        assertTrue(hook.contains("deoptimize(supported)"))
        assertTrue(hook.contains("deoptimize(factoryMethod)"))
        assertTrue(hook.contains("collectDefaultSystemShortcuts"))
        assertTrue(hook.contains("buildPopupData"))
        assertTrue(source("main/java/com/mi/onextbox/lsp/LauncherGestureHooker.kt").contains("LauncherWidgetMenuHooker.hook(loader)"))
        val entry = source("main/java/com/mi/onextbox/lsp/OosLspModuleEntry.kt")
        assertEquals(1, Regex("""LauncherGestureHooker\.hook\(""").findAll(entry).count())
    }

    @Test fun bothUiStylesHaveJoinedStableRowsWithoutCrossDisablingOrChangingTheOtherSwitch() {
        val ui = source("main/java/com/mi/onextbox/ui/screens/UiCustomizationSettings.kt")
            .substringAfter("internal fun LauncherIconMenuSettingsGroup").substringBefore("private data class LauncherTopState")
        assertTrue(ui.contains("LauncherGestureSettings.MENU, LauncherGestureSettings.WIDGETS"))
        assertTrue(ui.contains("FeatureAnimatedGroup(animateChanges = true)"))
        assertTrue(ui.contains("row(\"secondary-menu\", visible ="))
        assertTrue(ui.contains("row(\"widgets-menu\", visible ="))
        assertTrue(ui.contains("availableTitles == null || R.string.launcher_disable_icon_menu_title in availableTitles"))
        assertTrue(ui.contains("availableTitles == null || R.string.launcher_widgets_menu_title in availableTitles"))
        assertTrue(ui.contains("interactionEnabled = editor.savingKey != LauncherGestureSettings.WIDGETS"))
        assertTrue(ui.contains("interactionEnabled = editor.savingKey != LauncherGestureSettings.MENU"))
        assertTrue(ui.contains("value.copy(widgets = next)"))
        assertTrue(ui.contains("value.copy(disableSecondary = next)"))
        assertTrue(ui.contains("hasDividerAbove = above, hasDividerBelow = below"))
        assertTrue(source("main/java/com/mi/onextbox/ui/screens/DesktopFeaturesPage.kt").contains("LauncherIconMenuSettingsGroup()"))
    }

    @Test fun searchEnabledFeatureInventoryAndQuickEditShareTheSameDesktopIconDestination() {
        assertTrue(source("main/java/com/mi/onextbox/ui/screens/FeatureSearchIndex.kt")
            .contains("Entry(FeaturePageMode.DesktopIcons, R.string.launcher_widgets_menu_title, R.string.launcher_widgets_menu_summary)"))
        assertTrue(source("main/java/com/mi/onextbox/ui/screens/EnabledFeatureCatalog.kt")
            .contains("Entry(R.string.launcher_widgets_menu_title, FeaturePageMode.DesktopIcons) { LauncherGestureSettings.widgets(it) }"))
        assertTrue(source("main/java/com/mi/onextbox/ui/screens/EnabledFeatureActions.kt")
            .contains("R.string.launcher_widgets_menu_title to { context, enabled -> LauncherGestureSettings.save(context, LauncherGestureSettings.WIDGETS, enabled) }"))
    }

    @Test fun allLocalesHaveOneCompleteTitleAndSummaryWithoutTrailingPeriods() {
        for (locale in listOf("values", "values-zh-rCN", "values-zh-rTW", "values-b+yue+Hant", "values-ja", "values-ja-rCN", "values-ko", "values-ru", "values-vi")) {
            val file = listOf(File("src/main/res/$locale/strings.xml"), File("app/src/main/res/$locale/strings.xml")).first { it.isFile }
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
            for (key in listOf("launcher_widgets_menu_title", "launcher_widgets_menu_summary")) {
                val matches = (0 until nodes.length).map(nodes::item).filter { it.attributes.getNamedItem("name").nodeValue == key }
                assertEquals("$locale/$key", 1, matches.size)
                val text = matches.single().textContent
                assertTrue("$locale/$key", text.isNotBlank())
                assertFalse("$locale/$key", text.endsWith(".") || text.endsWith("。"))
            }
        }
    }
}
