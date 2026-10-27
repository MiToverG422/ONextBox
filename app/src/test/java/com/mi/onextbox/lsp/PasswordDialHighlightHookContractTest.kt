package com.mi.onextbox.lsp

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class PasswordDialHighlightHookContractTest {
    @Test fun disabledFreshScopeDoesNotResolveOrInstallHooks() {
        val text = source("lsp/PasswordDialHighlightHooker.kt")
        val guard = text.indexOf("if (!enabled()) return")
        assertTrue(guard >= 0 && guard < text.indexOf("install(loader)"))
        assertTrue(guard < text.indexOf("Reflect.findClass("))
        assertTrue(text.contains("LspConfig.isKeyguardFeatureEnabledXposed(KeyguardFeature.BlockPasswordHighlight)"))
    }

    @Test fun bothLightLayersAreGatedWithoutRedrawingTheKey() {
        val text = source("lsp/PasswordDialHighlightHooker.kt")
        assertTrue(text.contains("com.coui.appcompat.passworddial.olivelink.PasswordDialEffectSwitchParams"))
        assertTrue(text.contains("getEnvLightEnabled"))
        assertTrue(text.contains("getOpticsEnabled"))
        assertTrue(text.contains("isEnvLightPressActive"))
        assertTrue(text.contains("isPressOpticsActive"))
        assertTrue(text.contains("applyPressLightFactor"))
        assertTrue(text.contains("createOpticsParams"))
        assertTrue(text.contains("refreshReceiver"))
        assertTrue(text.contains("\"applyAllParams\""))
        assertTrue(text.contains("check(gates.size == getters.size)"))
        assertTrue(text.contains("check(callerTargets.isNotEmpty())"))
        assertTrue(text.contains("if (enabled()) param.result = false"))
        assertTrue(text.contains("if (enabled()) param.args[1] = false"))
        assertEquals(2, Regex("ModernHookRegistry\\.installCompat\\(").findAll(text).count())
        assertFalse("Blur must stay stock", text.contains("getBlurEnabled"))
        for (mutation in listOf("setObjectField", "BlurDrawable", "disableEnvLight", "applySourceInternal",
            "View.GONE", "setBackground")) {
            assertFalse("Unexpected mutation: $mutation", text.contains(mutation))
        }
    }

    @Test fun defaultOffPreferenceIsSyncedAndIndexedUnderTheLockScreenPage() {
        val config = source("lsp/LspConfig.kt")
        val enum = config.substringAfter("enum class KeyguardFeature").substringBefore("fun isKeyguardFeatureEnabled")
        assertTrue(enum.contains("BlockPasswordHighlight(\"keyguard_block_password_highlight\")"))
        assertTrue(config.contains("LspPreferenceStore.readBoolean(context, feature.key, false)"))
        val entry = source("lsp/OosLspModuleEntry.kt")
        val sharedDispatch = entry.indexOf("InstallerRedirectHooker.hookApp()")
        assertTrue(sharedDispatch >= 0)
        val call = entry.indexOf("PasswordDialHighlightHooker.hook(classLoader)", sharedDispatch)
        assertTrue(call > entry.indexOf("PACKAGE_SYSTEM_UI ->", sharedDispatch))
        assertTrue(call < entry.indexOf("PACKAGE_SETTINGS ->", sharedDispatch))
        val page = source("ui/screens/KeyguardInteractionSettings.kt")
        assertTrue(page.contains("R.string.keyguard_block_password_highlight_title"))
        assertTrue(page.contains("R.string.keyguard_block_password_highlight_summary"))
        assertTrue(source("ui/screens/FeatureSearchIndex.kt").contains(
            "Entry(FeaturePageMode.SystemUiLockScreen, R.string.keyguard_block_password_highlight_title, R.string.keyguard_block_password_highlight_summary)"))
        assertTrue(source("ui/screens/EnabledFeatureCatalog.kt").contains(
            "Entry(R.string.keyguard_block_password_highlight_title, FeaturePageMode.SystemUiLockScreen)"))
        assertTrue(source("ui/screens/EnabledFeatureActions.kt").contains(
            "R.string.keyguard_block_password_highlight_title to { context, enabled -> LspConfig.setKeyguardFeatureEnabled(context, LspConfig.KeyguardFeature.BlockPasswordHighlight, enabled) }"))
        assertTrue(source("ui/screens/FeaturesScreen.kt").contains(
            "FeaturePageMode.SystemUiLockScreen -> listOf(\"com.android.systemui\")"))
    }

    @Test fun everySupportedLanguageHasBothStrings() {
        val root = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        for (folder in listOf("values", "values-zh-rCN", "values-zh-rTW", "values-b+yue+Hant",
            "values-ja", "values-ja-rCN", "values-ko", "values-ru", "values-vi")) {
            val document = parser.parse(File(root, "$folder/strings.xml"))
            val strings = document.getElementsByTagName("string")
            val names = (0 until strings.length).map { strings.item(it).attributes.getNamedItem("name").nodeValue }
                .filter { it.startsWith("keyguard_block_password_highlight_") }
            assertEquals(folder,
                listOf("keyguard_block_password_highlight_title", "keyguard_block_password_highlight_summary"), names)
        }
    }

    private fun source(path: String): String = listOf(File("src/main/java/com/mi/onextbox/$path"),
        File("app/src/main/java/com/mi/onextbox/$path")).first { it.isFile }.readText()
}
