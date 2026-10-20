package com.mi.onextbox.lsp

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards ownership contracts which otherwise require the OEM host's Android classes. */
class SystemUiLauncherHotReloadContractTest {
    @Test fun fingerprintPointerHooksReplayNativeMethodsWithoutAuthorizationState() {
        val source = source("FaceTapUnlockHooker.kt")
        assertTrue(source.contains("registerReplay(\"face-tap:pointer-hooks\""))
        assertTrue(source.contains("capture = { pointerHookMethods.toTypedArray() }"))
        assertTrue(source.contains("NativePointerCallbackReplay.owners(methods, callbackBase).forEach(::installNativePointerCallbacks)"))
        assertTrue(source.contains("ModernHotReloadSession.current?.previousTargets.orEmpty()"))
        val replay = source.substringAfter("fun replayPointers(saved: Any?)")
            .substringBefore("after(face)")
        assertFalse(replay.contains("pointerDown("))
        assertFalse(replay.contains("pointerUp("))
        assertFalse(replay.contains("sessionUser ="))
        assertFalse(replay.contains("requestDismiss("))
    }

    @Test fun volumeCallbackBridgeIsRestoredWithoutConstructingAnotherVolumeDialog() {
        val source = source("AospVolumeCallbackBridge.kt")
        assertTrue(source.contains("registerReplay(\"aosp-volume:callback-bridge@"))
        assertTrue(source.contains("capture = { installed }, replay = { if (it == true) install(loader) }"))
        assertTrue(source.contains("ModernHotReloadSession.current?.previousTargets.orEmpty()"))
        assertTrue(source.contains("if (installed) return"))
        assertTrue(source.contains("HotReloadLifecycle.withHook(fallback = {}, action = {"))
        assertFalse(source.contains("AospVolumeGraph.create"))
        assertContains("AospVolumeHooker.kt", "AospVolumeCallbackBridge.registerHotReload(loader)")
    }

    @Test fun powerIndicatorHandsOverHostRootsInsteadOfVetoingAttachedViews() {
        val source = source("PowerIndicatorHooker.kt")
        assertTrue(source.contains("capture = { owners.keys.toTypedArray() }"))
        assertTrue(source.contains("detach = ::detachOwners"))
        assertTrue(source.contains("if (root.isAttachedToWindow && !owners.containsKey(root)) attach(root)"))
        assertTrue(source.contains("owners.clear()"))
        assertTrue(source.contains("owner.close()"))
        assertFalse(source.contains("canReload = { owners.isEmpty() }"))
        assertFalse(source.contains("capture = { owners.values"))
    }

    @Test fun powerSamplingDrainsAndRejectedReloadRearmsOnlyTheOldOwners() {
        val source = source("PowerIndicatorHooker.kt")
        assertTrue(source.contains("HotReloadLifecycle.withHook(fallback = {}, action = sample@{"))
        assertTrue(source.contains("if (closed || token != generation) return@sample"))
        assertTrue(source.contains("if (closed || HotReloadLifecycle.isRetiring || token != generation ||"))
        assertTrue(source.contains("resumeAfterRejection = { owners.values.toList().forEach { it.resume() } }"))
        val resume = source.substringAfter("fun resume()").substringBefore("fun close()")
        assertTrue(resume.contains("generation++"))
        assertTrue(resume.contains("main.removeCallbacksAndMessages(null)"))
        assertTrue(resume.contains("worker.removeCallbacksAndMessages(null)"))
        assertTrue(resume.contains("main.post(tick)"))
    }

    @Test fun recentTaskDynamicHooksReplayBeforeCoverageCheckingEvenForOlderGenerations() {
        val source = source("HideRecentAppsHooker.kt")
        assertTrue(source.contains("HotReloadLifecycle.registerReplay(\"\$TAG:types@"))
        assertTrue(source.contains("capture = replay::capture, replay = { replay.restore(it) }"))
        assertTrue(source.contains("replay.restore(null, ModernHotReloadSession.current?.previousTargets.orEmpty())"))
        assertTrue(source.contains("replay.discover(concrete)"))
        val entry = source("OosLspModuleEntry.kt").substringAfter("override fun onHotReloaded(")
        assertTrue(entry.contains("previousTargets = param.oldHookHandles.map { it.executable }"))
        assertTrue(entry.indexOf("HotReloadLifecycle.replaySavedState()") <
            entry.indexOf("session.commit(param.oldHookHandles)"))
    }

    @Test fun nativePluginHooksHavePreCommitReplayOwners() {
        assertContains("FluidCloudMaterialHooker.kt", "registerReplay(\"fluid-material:plugins\"", "forEach(::installPlugin)")
        assertContains("SystemUiRedOneHooker.kt", "registerReplay(\"red-one:plugins\"", "forEach(::installPersonalityClockHooks)")
        assertContains("FluidCloudTemplateMaterialHooks.kt", "registerReplay(\"\$prefix:template-state\"", "Bundle(state.original)")
    }

    @Test fun launcherStartupOnlyHooksAreReplayedWithoutCallingApplicationOnCreate() {
        assertContains("LauncherSearchDeviceCompat.kt", "registerReplay(\"launcher:search-bootstrap\"", "bootstrap.onApplicationReady(true)")
        assertContains("LauncherRecentInteractionHooker.kt", "registerReplay(\"launcher:recent-bootstrap\"", "if (saved == true) installReadyHooks()")
        assertContains("LauncherFeaturesHooker.kt", "registerReplay(\"launcher:folder-watchers\"", "forEach(::observeWatcher)")
    }

    @Test fun viewListenersHaveExplicitDetachPaths() {
        assertContains("SystemUiHooker.kt", "removeOnLayoutChangeListener(guard.layout)", "removeOnPreDrawListener(guard.drawing)")
        assertContains("LauncherHooker.kt", "view.removeOnLayoutChangeListener(listener)", "forEach(::keepCardLabelHidden)")
        assertContains("LauncherCategoryPageHooker.kt", "sessions.values.toList().forEach { it.dispose() }")
    }

    @Test fun ownedMaterialIsRebuiltInsteadOfPassingItsDrawableAcrossGenerations() {
        assertContains("FluidCloudMaterialHooker.kt", "material.releaseAll()", "material.restoreReloadState(it)")
        val material = source("FluidCloudNotificationMaterial.kt")
        assertTrue(material.contains("arrayOf(view, fallback, radius, weight, currentAlpha, view.background === this)"))
        assertTrue(material.contains("delegate.callback = null"))
        assertFalse(material.contains("arrayOf(view, this,"))
    }

    @Test fun authorizationAndPendingGestureActionsVetoReload() {
        assertContains("FaceTapUnlockHooker.kt", "sessionUser == null && pendingDismiss == null && pendingHold == null",
            "pendingOpticalEvents.get() == 0", "wake.dispose()")
        assertContains("LauncherRecentInteractionHooker.kt", "canReload = { pending.isEmpty() }")
        assertContains("PermanentClockSecondsHooker.kt", "canReload = { pendingToggles.get() == 0 }")
    }

    @Test fun searchTimersAndDecoderCannotOutliveRetirement() {
        assertContains("LauncherSearchDeviceCompat.kt", "pending.values.toList().forEach(handler::removeCallbacks)",
            "if (HotReloadLifecycle.isRetiring || launcher.isDestroyed")
        assertContains("FaceTapWakeHandoff.kt", "disposed = true", "decoder.shutdownNow()",
            "if (disposed || epoch != generation")
    }

    @Test fun rejectedRetirementRestartsSearchRecoveryWithoutDuplicateTimers() {
        assertContains("LauncherSearchDeviceCompat.kt", "resumeAfterRejection = {",
            "if (repairEnabled()) owners.keys.toList().forEach(::schedule)")
        val schedule = source("LauncherSearchDeviceCompat.kt").substringAfter("fun schedule(launcher: Activity)")
            .substringBefore("fun cancel(launcher: Activity)")
        assertTrue(schedule.indexOf("cancel(launcher)") < schedule.indexOf("handler.postDelayed"))
    }

    @Test fun rejectedRetirementRestoresFacePollButNeverReplaysAuthorizationActions() {
        val source = source("FaceTapUnlockHooker.kt")
        assertTrue(source.contains("resumeAfterRejection = ::resumeAfterRejectedReload"))
        val resume = source.substringAfter("private fun resumeAfterRejectedReload()")
            .substringBefore("private fun reconcile()")
        assertTrue(resume.contains("tap?.invalidate()"))
        assertTrue(resume.contains("cancelFeedbackAndDismiss(hard = false)"))
        assertTrue(resume.contains("main.removeCallbacks(refresh)"))
        assertTrue(resume.contains("reconcile()"))
        assertFalse(resume.contains("requestDismiss("))
        assertFalse(resume.contains("commitDismiss("))
        assertFalse(resume.contains("sessionUser = "))
    }

    private fun assertContains(file: String, vararg fragments: String) {
        val text = source(file)
        fragments.forEach { assertTrue("$file: $it", text.contains(it)) }
    }

    private fun source(file: String): String = listOf(
        File("src/main/java/com/mi/onextbox/lsp/$file"),
        File("app/src/main/java/com/mi/onextbox/lsp/$file"),
    ).first { it.isFile }.readText()
}
