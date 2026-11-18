package com.mi.onextbox.lsp

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardDismissButtonHookContractTest {
    @Test fun disabledFreshScopeDoesNotInstallHooks() {
        val text = source("lsp/KeyboardDismissButtonHooker.kt")
        val guard = text.indexOf("if (!LspConfig.isKeyboardDismissHiddenXposed() && saved == null) return")
        assertTrue(guard >= 0 && guard < text.indexOf("resolve(loader)"))
        assertTrue(guard < text.indexOf("ModernHookRegistry.installCompat("))
    }

    @Test fun onlyNativeBackDispatcherIsModifiedAndNoInsetsOrFlagsAreChanged() {
        val text = source("lsp/KeyboardDismissButtonHooker.kt")
        assertTrue(text.contains("getDeclaredMethod(\"getBackButton\")"))
        assertTrue(text.contains("getDeclaredMethod(\"updateNavButtonIcons\")"))
        assertTrue(text.contains("native.refresh(it, nativeUpdated = true)"))
        assertTrue(!text.contains("getImeSwitchButton"))
        assertTrue(!text.contains("getDeclaredMethod(\"setNavbarFlags\""))
        assertTrue(!text.contains("layoutParams ="))
        assertTrue(!text.contains("putInt"))
        assertTrue(!text.contains("setImeCaptionBarInsetsHeight"))
        assertTrue(!text.contains("View.GONE"))
    }

    @Test fun hiddenButtonStateIsRestoredOnDetachAndHotReload() {
        val text = source("lsp/KeyboardDismissButtonHooker.kt")
        assertTrue(text.contains("capture = { arrayOf(targets, native.snapshot()) }"))
        assertTrue(text.contains("detach = { native.restoreAll() }"))
        assertTrue(text.contains("native.restoreSnapshot((state as? Array<*>)?.get(1))"))
        assertTrue(text.contains("KeyboardDismissButtonRules.stockVisibility(current, restoredStock ?: hidden[view], nativeUpdated)"))
        assertTrue(text.contains("WeakHashMap<View, Int>()"))
        assertTrue(text.contains("getVisibility.invoke(button) == View.INVISIBLE"))
    }

    @Test fun nativeIconCallersAreDeoptimizedToCoverConfigurationChanges() {
        val text = source("lsp/KeyboardDismissButtonHooker.kt")
        assertTrue(text.contains("ModernHookRuntime.requireModule().deoptimize(it)"))
        assertTrue(text.contains("\"setNavbarFlags\", \"reorient\", \"onConfigurationChanged\", \"onAttachedToWindow\""))
    }

    @Test fun preferenceIsResetSyncedAndKeyboardScoped() {
        val config = source("lsp/LspConfig.kt")
        assertTrue(config.contains("KeyboardFeatureSettings.reset(context)"))
        val settings = source("lsp/KeyboardFeatureSettings.kt")
        assertTrue(settings.contains("HideDismiss(\"keyboard_hide_dismiss\")"))
        assertTrue(settings.contains("Option.entries.forEach { set(context, it, false) }"))
        assertTrue(config.contains("value = if (isKeyboardDismissHidden(context)) \"1\" else \"0\""))
        assertTrue(source("lsp/OosLspModuleEntry.kt").contains("KeyboardDismissButtonHooker.hook(classLoader)"))
        assertTrue(source("ui/screens/KeyboardFeaturesPage.kt").contains("R.string.keyboard_hide_dismiss_title"))
    }

    private fun source(path: String): String = listOf(File("src/main/java/com/mi/onextbox/$path"),
        File("app/src/main/java/com/mi/onextbox/$path")).first { it.isFile }.readText()
}
