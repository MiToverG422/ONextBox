package com.mi.onextbox.lsp

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationClearButtonHookContractTest {
    @Test fun disabledFreshScopeDoesNotResolveOrInstallHooks() {
        val text = source("lsp/NotificationClearButtonHooker.kt")
        val guard = text.indexOf("if (!LspConfig.isRoundNotificationClearButtonEnabledXposed() && saved == null) return")
        assertTrue(guard >= 0 && guard < text.indexOf("resolve(loader)"))
        assertTrue(guard < text.indexOf("ModernHookRegistry.installCompat("))
    }

    @Test fun onlyClearButtonWidthChangesAndAllNativeBehaviorIsPreserved() {
        val text = source("lsp/NotificationClearButtonHooker.kt")
        assertTrue(text.contains("com.oplus.systemui.notification.clearall.ClearAllController"))
        assertTrue(text.contains("com.oplus.systemui.notification.clearall.OplusClearAllButton"))
        assertTrue(text.contains("params.width = desired"))
        for (mutation in listOf("params.height =", "bottomMargin =", "setPadding(", "setBackground",
            "setOnClickListener", "setOnTouchListener", "setOutlineProvider", "setVisibility", "putInt(", "setInt(")) {
            assertTrue("Unexpected mutation: $mutation", !text.contains(mutation))
        }
    }

    @Test fun nativeLayoutUpdatesCoverConfigurationAndMarginChanges() {
        val text = source("lsp/NotificationClearButtonHooker.kt")
        assertTrue(text.contains("getDeclaredMethod(\"updateLayoutParams\", String::class.java)"))
        assertTrue(text.contains("getDeclaredMethod(\"updateLayoutParams\", Int::class.javaPrimitiveType)"))
        assertTrue(text.contains("ModernHookRuntime.requireModule().deoptimize(it)"))
        assertTrue(text.contains("\"updateResource\", \"onConfigChanged\", \"bindViews\""))
        assertTrue(text.contains("\"updateClearAllButtonLayoutParams\""))
    }

    @Test fun hotReloadRestoresStockBeforePassingViewsToTheNextGeneration() {
        val text = source("lsp/NotificationClearButtonHooker.kt")
        assertTrue(text.contains("WeakHashMap<View, IntArray>()"))
        assertTrue(text.contains("capture = { arrayOf(targets, native.snapshot()) }"))
        assertTrue(text.contains("detach = { native.restoreAll() }"))
        assertTrue(text.contains("native.restoreSnapshot((state as? Array<*>)?.get(1))"))
        assertTrue(text.contains("NotificationClearButtonRules.restoreWidth"))
    }

    @Test fun preferenceIsDefaultOffResetSyncedAndOnlyInSystemUi() {
        val config = source("lsp/LspConfig.kt")
        assertTrue(config.contains("LspPreferenceStore.readBoolean(context, KEY_ROUND_NOTIFICATION_CLEAR_BUTTON, false)"))
        assertTrue(config.contains("setRoundNotificationClearButtonEnabled(context, false)"))
        assertTrue(config.contains("value = if (isRoundNotificationClearButtonEnabled(context)) \"1\" else \"0\""))
        val entry = source("lsp/OosLspModuleEntry.kt")
        val sharedDispatch = entry.indexOf("InstallerRedirectHooker.hookApp()")
        assertTrue(sharedDispatch >= 0)
        val call = entry.indexOf("NotificationClearButtonHooker.hook(classLoader)", sharedDispatch)
        assertTrue(call > entry.indexOf("PACKAGE_SYSTEM_UI ->", sharedDispatch))
        assertTrue(call < entry.indexOf("PACKAGE_SETTINGS ->", sharedDispatch))
        assertTrue(source("ui/screens/SystemUiFeaturesPage.kt").contains("NotificationClearButtonSettingsRow()"))
    }

    private fun source(path: String): String = listOf(File("src/main/java/com/mi/onextbox/$path"),
        File("app/src/main/java/com/mi/onextbox/$path")).first { it.isFile }.readText()
}
