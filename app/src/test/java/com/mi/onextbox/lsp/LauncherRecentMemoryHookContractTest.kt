package com.mi.onextbox.lsp

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherRecentMemoryHookContractTest {
    @Test fun entryProvidesTheNativeApkAndRetiresTheHardCodedMemoryImplementation() {
        assertTrue(source("OosLspModuleEntry.kt").contains("LauncherFeaturesHooker.hook(classLoader, target.sourceDir)"))
        val shared = source("LauncherFeaturesHooker.kt")
        assertTrue(shared.contains("LauncherRecentMemoryHooker.hook(classLoader, apkPath)"))
        assertTrue(shared.indexOf("LauncherRecentMemoryHooker.hook") > shared.indexOf("LauncherDockHooker.hook"))
        assertFalse(shared.contains("private fun hookMemory("))
        assertFalse(source("LauncherMemoryTargets.kt").contains("\"fp.e\""))
    }

    @Test fun disabledColdStartsAvoidDexScansAndNativeHookInstallation() {
        val bootstrap = source("LauncherRecentMemoryHooker.kt").substringAfter("fun restoreTargets")
            .substringBefore("private fun installTargets")
        assertTrue(bootstrap.contains("if (values != null || enabled()) installTargets(values)"))
        val changed = source("LauncherRecentMemoryHooker.kt").substringAfter("private val update = Runnable")
            .substringBefore("private val listener")
        assertTrue(changed.contains("if (targets == null && enabled()) installTargets(null)"))
    }

    @Test fun preferencesAreCoalescedOnMainAndReleasedWithOldGenerationCallbacks() {
        val text = source("LauncherRecentMemoryHooker.kt")
        assertTrue(text.contains("key == LauncherFeature.RecentMemory.key"))
        assertTrue(text.contains("main.removeCallbacks(update)"))
        assertTrue(text.contains("main.postDelayed(update, 80L)"))
        assertTrue(text.contains("unregisterOnSharedPreferenceChangeListener(listener)"))
        assertTrue(text.contains("HotReloadLifecycle.withHook<Unit>"))
        assertTrue(text.contains("resumeAfterRejection = session::schedule"))
    }

    @Test fun targetsAndOriginalsSurviveReloadWithoutExportingModuleOwnedObjects() {
        val text = source("LauncherRecentMemoryHooker.kt")
        assertTrue(text.contains("DexTargetCache.restoredState(stateKey)"))
        assertTrue(text.contains("arrayOf(targets?.values, memory?.export(), panels.keys.toTypedArray())"))
        assertTrue(text.contains("session.restoreTargets(saved)"))
        assertTrue(text.contains("HotReloadLifecycle.afterCommit"))
        assertTrue(text.contains("if (saved == null) session.restore(null)"))
        assertTrue(text.contains("releaseMemory()"))
        assertTrue(text.contains("memory?.import(it)"))
    }

    @Test fun nativeFormatterLayoutAndClearButtonArePreserved() {
        val text = source("LauncherRecentMemoryHooker.kt")
        assertTrue(text.contains("native.updatePanel.invoke(panel, false)"))
        assertTrue(text.contains("native.singletonField.get(null)"))
        assertTrue(text.contains("native.callers.forEach"))
        assertTrue(text.contains("deoptimize(method)"))
        assertFalse(text.contains(".setText("))
        assertFalse(text.contains("Settings.Secure.put"))
        assertFalse(text.contains("setVisibility("))
        assertFalse(text.contains("btn_clear"))
    }

    @Test fun queuedNativeResultsAreRecheckedBeforeTheyCanReshowDisabledInformation() {
        val text = source("LauncherRecentMemoryHooker.kt").substringAfter("hook(native.bindPanel)")
            .substringBefore("after(native.inflatePanel)")
        assertTrue(text.contains("synchronize(owner)"))
        assertTrue(text.contains("if (!enabled() && !native.detail.getBoolean(owner))"))
        assertTrue(text.contains("native.updatePanel.invoke(panel, false)"))
        assertTrue(text.contains("else chain.proceed()"))
    }

    @Test fun signaturesAndFieldRelationshipsAreValidatedAndFailuresRollBackOnlyMemoryHooks() {
        val targets = source("LauncherMemoryTargets.kt")
        assertTrue(targets.contains("usingStrings(ALLOW_KEY)"))
        assertTrue(targets.contains("usingStrings(UPDATE_ANCHOR)"))
        assertTrue(targets.contains("booleanFields.single { Modifier.isFinal(it.modifiers) }"))
        assertTrue(targets.contains("booleanFields.single { !Modifier.isFinal(it.modifiers) }"))
        val text = source("LauncherRecentMemoryHooker.kt")
        assertTrue(text.contains("ModernHookRegistry.unhookPrefix(prefix)"))
        assertTrue(text.contains("if (targets == null) chain.proceed() else body(chain)"))
    }

    private fun source(name: String): String = listOf(
        File("src/main/java/com/mi/onextbox/lsp/$name"),
        File("app/src/main/java/com/mi/onextbox/lsp/$name"),
    ).first { it.isFile }.readText().replace("\r\n", "\n")
}
