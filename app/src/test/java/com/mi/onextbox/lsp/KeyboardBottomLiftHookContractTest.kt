package com.mi.onextbox.lsp

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardBottomLiftHookContractTest {
    @Test fun disabledFreshScopeSkipsResolutionAndHooks() {
        val text = source("lsp/KeyboardBottomLiftHooker.kt")
        val guard = text.indexOf("if (!LspConfig.isKeyboardBottomLiftFixEnabledXposed() && saved == null) return")
        assertTrue(guard >= 0 && guard < text.indexOf("resolve(loader, apkPath)"))
        assertTrue(guard < text.indexOf("ModernHookRegistry.installCompat("))
    }

    @Test fun reusesHostInsetsAndNativeHeightInsteadOfHardcodedOffsets() {
        val text = source("lsp/KeyboardBottomLiftHooker.kt")
        assertTrue(text.contains("view.rootWindowInsets"))
        assertTrue(text.contains("if (param.args[0] != null) return"))
        assertTrue(text.contains("updateInsets.invoke(delegate.get(view), insets)"))
        assertTrue(text.contains("refreshMargin.invoke(adjustHelper.invoke(view))"))
        assertTrue(!text.contains("bottomMargin ="))
        assertTrue(!text.contains("Settings.Secure.put"))
        assertTrue(!text.contains("setOnApplyWindowInsetsListener"))
    }

    @Test fun oneShotDrawingWorkIsRetiredAndRestoredOnReload() {
        val text = source("lsp/KeyboardBottomLiftHooker.kt")
        assertTrue(text.contains("capture = { arrayOf(targets, native.pendingViews()) }"))
        assertTrue(text.contains("detach = { native.cancelAll() }"))
        assertTrue(text.contains("?.forEach(native::schedule)"))
        assertTrue(text.contains("HotReloadLifecycle.withHook({ true })"))
        assertTrue(text.contains("removeOnPreDrawListener(task.listener)"))
        assertTrue(Regex("""cancel\(view\)\s+if \(!shouldRefresh\(view\)\)""").containsMatchIn(text))
        assertTrue(text.contains("container.getDeclaredMethod(\"onDetachedFromWindow\")"))
    }

    @Test fun firstWindowCanRegisterBeforeAttachmentAndWaitsUntilDrawing() {
        val text = source("lsp/KeyboardBottomLiftHooker.kt")
        assertTrue(text.contains("if (!shouldRefresh(view, requireReady = false)) return"))
        assertTrue(text.contains("!requireReady || view.isAttachedToWindow, !requireReady || view.isShown"))
        assertTrue(text.contains("observer.addOnPreDrawListener(listener)"))
        assertTrue(text.contains("view.requestApplyInsets()"))
        assertTrue(text.contains(".firstOrNull { serviceRoot.type.isInstance(it) }"))
        assertTrue(!text.contains("getFieldInstance(loader)"))
        assertTrue(!text.contains("System.err.println"))
    }

    @Test fun preferenceIsExposedResetSyncedAndDispatched() {
        val config = source("lsp/LspConfig.kt")
        assertTrue(config.contains("setKeyboardBottomLiftFixEnabled(context, false)"))
        assertTrue(config.contains("value = if (isKeyboardBottomLiftFixEnabled(context)) \"1\" else \"0\""))
        assertTrue(source("lsp/OosLspModuleEntry.kt").contains("KeyboardBottomLiftHooker.hook(classLoader, target.sourceDir)"))
        assertTrue(source("ui/screens/KeyboardFeaturesPage.kt").contains("R.string.keyboard_bottom_lift_fix_title"))
    }

    private fun source(path: String): String = listOf(File("src/main/java/com/mi/onextbox/$path"),
        File("app/src/main/java/com/mi/onextbox/$path")).first { it.isFile }.readText()
}
