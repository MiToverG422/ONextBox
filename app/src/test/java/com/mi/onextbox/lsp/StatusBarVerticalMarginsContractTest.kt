package com.mi.onextbox.lsp

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class StatusBarVerticalMarginsContractTest {
    private fun source(path: String) = listOf(File("src/$path"), File("app/src/$path")).first { it.isFile }.readText()

    @Test fun verticalPreferencesAreIndependentDefaultOffAndIncludedInPersistence() {
        val settings = source("main/java/com/mi/onextbox/lsp/StatusBarLayoutSettings.kt")
        assertTrue(settings.contains("prefs.getBoolean(VERTICAL_MARGINS_ENABLED, false)"))
        assertTrue(settings.contains("prefs.getInt(TOP, 0)"))
        assertTrue(settings.contains("prefs.getInt(BOTTOM, 0)"))
        assertTrue(settings.contains("HookConfigSnapshot.boolean(VERTICAL_MARGINS_ENABLED, false) == true"))
        assertTrue(settings.contains(".putBoolean(VERTICAL_MARGINS_ENABLED, value.verticalMarginsEnabled)"))
        assertTrue(settings.contains(".putInt(TOP, StatusBarLayoutRules.offset(value.top))"))
        assertTrue(settings.contains(".putInt(BOTTOM, StatusBarLayoutRules.offset(value.bottom))"))
        val setter = settings.substringAfter("fun setVerticalMarginsEnabled(").substringBefore("fun saveOrder(")
        assertTrue(setter.contains("putBoolean(VERTICAL_MARGINS_ENABLED, enabled).commit()"))
        assertTrue(setter.contains("LspConfig.syncReadableState(context)"))
        assertFalse(setter.contains("putBoolean(MARGINS_ENABLED"))
        val reset = settings.substringAfter("fun resetOrder(")
        for (key in listOf("VERTICAL_MARGINS_ENABLED", "remove(TOP)", "remove(BOTTOM)", "putInt(TOP", "putInt(BOTTOM")) {
            assertFalse(key, reset.contains(key))
        }
        assertTrue(source("main/java/com/mi/onextbox/ui/common/ConfigBackup.kt").contains("LSP_PREFS_NAME"))
    }

    @Test fun nativeHomePaddingIsCopiedAndNeitherWindowHeightNorKeyguardAreModified() {
        val hook = source("main/java/com/mi/onextbox/lsp/StatusBarLayoutHooker.kt")
        assertTrue(hook.contains("getHomeStatusBarLeftRightPadding"))
        assertTrue(hook.contains("val vertical = StatusBarLayoutSettings.verticalMarginsXposed()"))
        assertTrue(hook.contains("if (offsets == null && vertical == null) return"))
        assertTrue(hook.contains("val result = Rect(original)"))
        assertTrue(hook.contains("StatusBarLayoutRules.verticalPadding(original.top, original.bottom"))
        assertTrue(hook.contains("statusBarHeight"))
        assertTrue(hook.contains("status_bar_icon_container_height"))
        assertTrue(hook.contains("result.top = padding.first"))
        assertTrue(hook.contains("result.bottom = padding.second"))
        assertTrue(hook.contains("param.result = result"))
        assertTrue(hook.contains("deoptimize(it)"))
        for (unsafe in listOf("updateKeyguardStatusBarPadding", "setPadding(", "setPaddingRelative(",
            "setLayoutParams(", "layoutParams.height", "original.top =", "original.bottom =", "updatePaddingTopBottom\"")) {
            assertFalse(unsafe, hook.contains(unsafe))
        }
    }

    @Test fun bothUiStylesShareJoinedAnimatedRowsAndIndividualResetSliders() {
        val ui = source("main/java/com/mi/onextbox/ui/screens/UiCustomizationSettings.kt")
            .substringAfter("internal fun StatusBarLayoutSettingsGroup").substringBefore("internal fun StatusBarIconOrderPage")
        assertEquals(1, Regex("FeatureAnimatedGroup\\(").findAll(ui).count())
        assertTrue(ui.contains("FeatureAnimatedGroup(animateChanges = true)"))
        assertTrue(ui.contains("row(\"vertical-margins\")"))
        assertTrue(ui.contains("R.string.statusbar_vertical_margins_title), summary = \"\""))
        assertTrue(ui.contains("row(key, visible = value.verticalMarginsEnabled)"))
        assertTrue(ui.contains("R.string.statusbar_margin_top else R.string.statusbar_margin_bottom"))
        assertTrue(ui.contains("valueRange = -16f..40f, steps = 55"))
        assertTrue(ui.contains("interactionEnabled = editor.savingKey != key"))
        assertTrue(ui.contains("value.copy(top = 0) else value.copy(bottom = 0)"))
        assertTrue(ui.contains("StatusBarLayoutSettings.VERTICAL_MARGINS_ENABLED, StatusBarLayoutSettings.TOP, StatusBarLayoutSettings.BOTTOM"))
        assertTrue(ui.contains("StatusBarLayoutSettings.setVerticalMarginsEnabled(context, next)"))
    }

    @Test fun searchInventoryAndQuickToggleIncludeTheNewFeature() {
        val index = source("main/java/com/mi/onextbox/ui/screens/FeatureSearchIndex.kt")
        for (key in listOf("statusbar_vertical_margins_title", "statusbar_margin_top", "statusbar_margin_bottom")) {
            assertTrue(index.contains("Entry(FeaturePageMode.SystemUiStatusBarLayout, R.string.$key)"))
        }
        val catalog = source("main/java/com/mi/onextbox/ui/screens/EnabledFeatureCatalog.kt")
        assertTrue(catalog.contains("Entry(R.string.statusbar_vertical_margins_title, FeaturePageMode.SystemUiStatusBarLayout)"))
        assertTrue(catalog.contains("R.string.statusbar_vertical_margins_title ->"))
        assertTrue(catalog.contains("\${it.top} dp / \${it.bottom} dp"))
        assertTrue(source("main/java/com/mi/onextbox/ui/screens/EnabledFeatureActions.kt")
            .contains("R.string.statusbar_vertical_margins_title to { context, enabled -> StatusBarLayoutSettings.setVerticalMarginsEnabled(context, enabled) }"))
    }

    @Test fun allNineLocalesHaveCompleteDescriptionsWithoutTrailingPeriods() {
        val keys = listOf("statusbar_vertical_margins_title", "statusbar_margin_top", "statusbar_margin_bottom")
        for (locale in listOf("values", "values-zh-rCN", "values-zh-rTW", "values-b+yue+Hant", "values-ja", "values-ja-rCN", "values-ko", "values-ru", "values-vi")) {
            val file = listOf(File("src/main/res/$locale/strings.xml"), File("app/src/main/res/$locale/strings.xml")).first { it.isFile }
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
            for (key in keys) {
                val matches = (0 until nodes.length).map(nodes::item).filter { it.attributes.getNamedItem("name").nodeValue == key }
                assertEquals("$locale/$key", 1, matches.size)
                val text = matches.single().textContent
                assertTrue("$locale/$key", text.isNotBlank())
                assertFalse("$locale/$key", text.endsWith(".") || text.endsWith("。"))
            }
        }
    }
}
