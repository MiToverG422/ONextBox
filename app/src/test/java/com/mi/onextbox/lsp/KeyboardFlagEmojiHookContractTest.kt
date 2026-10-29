package com.mi.onextbox.lsp

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class KeyboardFlagEmojiHookContractTest {
    @Test fun disabledFreshScopesKeepTheHookReadyForLaterEnabling() {
        val text = source("lsp/KeyboardFlagEmojiHooker.kt")
        assertFalse(text.contains("if (!LspConfig.isKeyboardFlagEmojiEnabledXposed() && saved == null) return"))
        assertTrue(text.contains("if (!LspConfig.isKeyboardFlagEmojiEnabledXposed()) return"))
        assertTrue(text.contains("ModernHookRegistry.installCompat("))
    }

    @Test fun onlyTheHostFlagPanelPayloadIsChanged() {
        val text = source("lsp/KeyboardFlagEmojiHooker.kt")
        assertTrue(text.contains("if (!LspConfig.isKeyboardFlagEmojiEnabledXposed()) return"))
        assertTrue(text.contains("category.declaringClass.isInstance(owner)"))
        assertTrue(text.contains("KeyboardFlagEmojiRules.shouldAugment(true, selected)"))
        assertTrue(text.contains("models.supplement(param.args[0])"))
        assertFalse(text.contains("hasGlyph"))
        assertFalse(text.contains("commitText"))
        assertFalse(text.contains(".set(null"))
        assertFalse(text.contains("import kotlin.Pair"))
    }

    @Test fun reloadExportsOnlyHostTargetsAndReinstallsTheToggleGate() {
        val text = source("lsp/KeyboardFlagEmojiHooker.kt")
        assertTrue(text.contains("DexTargetCache.restoredState(stateKey)"))
        assertTrue(text.contains("HotReloadLifecycle.register(stateKey, capture = { targets })"))
        assertTrue(text.contains("ApkDexSession.withSession(apkPath)"))
        assertFalse(text.contains("DexFile("))
        assertTrue(text.contains("usingStrings(\"emojiCategory\")"))
        assertTrue(text.contains("usingStrings(\"Emoji(value=\")"))
        assertTrue(text.contains("usingStrings(\"EmojiList(emojis=\")"))
        assertTrue(text.contains("collector.first.callers"))
        assertTrue(text.contains("deoptimize(target)"))
        assertFalse(text.contains("System.loadLibrary"))
        assertTrue(source("lsp/OosLspModuleEntry.kt").contains("KeyboardFlagEmojiHooker.hook(classLoader, target.sourceDir)"))
        assertTrue(ModuleHotReloadPolicy.supportedPackages.contains(KeyboardMaterialRules.PACKAGE_NAME))
    }

    @Test fun settingHasDefaultResetAndBootSynchronization() {
        val text = source("lsp/LspConfig.kt")
        assertTrue(text.contains("readBoolean(context, KEY_KEYBOARD_FLAG_EMOJI, false)"))
        assertTrue(text.contains("setKeyboardFlagEmojiEnabled(context, false)"))
        assertTrue(text.contains("value = if (isKeyboardFlagEmojiEnabled(context)) \"1\" else \"0\""))
        assertTrue(source("ui/screens/KeyboardFeaturesPage.kt").contains("LspConfig.setKeyboardFlagEmojiEnabled(context, checked)"))
    }

    @Test fun optimizedBuildsKeepTheHostPairLookupName() {
        val rules = listOf(File("proguard-rules.pro"), File("app/proguard-rules.pro"))
            .first { it.isFile }.readLines().map { it.trim() }
        assertTrue(rules.contains("-keepnames class kotlin.Pair"))
        assertTrue(source("lsp/KeyboardFlagEmojiHooker.kt").contains(
            "Class.forName(\"kotlin.Pair\", false, loader)",
        ))
    }

    private fun source(path: String): String = listOf(File("src/main/java/com/mi/onextbox/$path"),
        File("app/src/main/java/com/mi/onextbox/$path")).first { it.isFile }.readText()
}
