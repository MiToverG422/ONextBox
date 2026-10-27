package com.mi.onextbox.lsp

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class AospBackPanelHookContractTest {
    @Test fun disabledFreshScopeDoesNotResolveOrInstallHooks() {
        val text = source("lsp/AospBackPanelHooker.kt")
        val guard = text.indexOf("if (!LspConfig.isAospBackPanelEnabledXposed() && saved == null) return")
        assertTrue(guard >= 0 && guard < text.indexOf("resolve(loader)"))
        assertTrue(guard < text.indexOf("ModernHookRegistry.installFast("))
    }

    @Test fun nativeDrawingDoesNotReplaceStockGestureRecognitionOrBackCallbacks() {
        val text = source("lsp/AospBackPanelHooker.kt")
        assertTrue(text.contains("native.dispatch(detector, event, handlers) { chain.proceed() }"))
        assertTrue(text.contains("Proxy.newProxyInstance(callback.type.classLoader, arrayOf(callback.type)"))
        assertTrue(text.contains("AospBackPanelVisualCallback"))
        assertTrue(text.contains("callback.get(panel)"))
        assertTrue(text.contains("if (callback.get(panel) === binding.visual) callback.set(panel, binding.stock)"))
        assertTrue(text.contains("check(callback.get(panel) === binding.visual)"))
        assertTrue(text.contains("releaseIdlePanels(enabled && !broken)"))
        for (mutation in listOf("allowed.set", "sendKeyEvent",
            "setTriggerBack(true)", "addView(", "removeView(", "SurfaceControl")) {
            assertFalse("Unexpected mutation: $mutation", text.contains(mutation))
        }
    }

    @Test fun nativeInputAndViewSuppressionStartOnlyAfterTheStockVisualThreshold() {
        val text = source("lsp/AospBackPanelHooker.kt")
        assertTrue(text.contains("method(manager, \"onDownEvent\", integer, MotionEvent::class.java, integer)"))
        assertTrue(text.contains("gesture.routing.start {"))
        assertTrue(text.contains("send(gesture.panel, gesture.down)"))
        assertTrue(text.contains("if (!gesture.routing.usingNative) return false"))
        assertTrue(text.contains("gesture.routing.terminalAction(event.actionMasked)"))
        assertTrue(text.contains("copy.action = action"))
        assertFalse(text.contains("event.action ="))
    }

    @Test fun ownsNoExportedModuleCallbacksAndVetoesReloadDuringGestures() {
        val text = source("lsp/AospBackPanelHooker.kt")
        assertTrue(text.contains("canReload = { native.canReload() }"))
        assertTrue(text.contains("panels.keys.all { state.get(it) === gone }"))
        assertTrue(text.contains("arrayOf<Any>(it.key, it.value.stock, it.value.lastEvent, it.value.destroyed)"))
        assertFalse(text.contains("arrayOf<Any>(it.key, it.value.visual"))
        assertTrue(text.contains("removed.down.recycle()"))
        assertTrue(text.contains("if (previous == null) frame.remove() else frame.set(previous)"))
        assertTrue(text.contains("ModernHookRuntime.requireModule().deoptimize(it)"))
        assertTrue(text.indexOf("install(0) { chain ->") > text.indexOf("for (index in 1..4) install(index)"))
    }

    @Test fun nativePagePreferenceIsDefaultOffSyncedResettableAndIndexed() {
        val config = source("lsp/LspConfig.kt")
        assertTrue(config.contains("LspPreferenceStore.readBoolean(context, KEY_AOSP_BACK_PANEL, false)"))
        assertTrue(config.contains("setAospBackPanelEnabled(context, false)"))
        assertTrue(config.contains("value = if (isAospBackPanelEnabled(context)) \"1\" else \"0\""))
        val entry = source("lsp/OosLspModuleEntry.kt")
        val sharedDispatch = entry.indexOf("InstallerRedirectHooker.hookApp()")
        assertTrue(sharedDispatch >= 0)
        val call = entry.indexOf("AospBackPanelHooker.hook(classLoader)", sharedDispatch)
        assertTrue(call > entry.indexOf("PACKAGE_SYSTEM_UI ->", sharedDispatch))
        assertTrue(call < entry.indexOf("PACKAGE_SETTINGS ->", sharedDispatch))
        val page = source("ui/screens/SystemUiFeaturesPage.kt")
        val nativePage = page.substringAfter("FeaturePageMode.SystemUiNative ->")
            .substringBefore("FeaturePageMode.SystemUiDynamicColor ->")
        assertTrue(nativePage.contains("AospBackPanelSettingsRow()"))
        assertTrue(page.contains("R.string.feature_aosp_back_panel_title"))
        assertFalse(source("ui/screens/NavigationBarFeaturesPage.kt").contains("aospBackPanel"))
        assertFalse(source("ui/screens/NavigationBarFeaturesPage.kt").contains("feature_aosp_back_panel_title"))
        assertTrue(source("ui/screens/FeatureSearchIndex.kt").contains(
            "Entry(FeaturePageMode.SystemUiNative, R.string.feature_aosp_back_panel_title, R.string.feature_aosp_back_panel_summary)"))
    }

    @Test fun everySupportedLanguageHasBothStrings() {
        val root = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        for (folder in listOf("values", "values-zh-rCN", "values-zh-rTW", "values-b+yue+Hant",
            "values-ja", "values-ja-rCN", "values-ko", "values-ru", "values-vi")) {
            val document = parser.parse(File(root, "$folder/strings.xml"))
            val strings = document.getElementsByTagName("string")
            val names = (0 until strings.length).map { strings.item(it).attributes.getNamedItem("name").nodeValue }
                .filter { it.startsWith("feature_aosp_back_panel_") }
            assertEquals(folder, listOf("feature_aosp_back_panel_title", "feature_aosp_back_panel_summary"), names)
        }
    }

    private fun source(path: String): String = listOf(File("src/main/java/com/mi/onextbox/$path"),
        File("app/src/main/java/com/mi/onextbox/$path")).first { it.isFile }.readText()
}
