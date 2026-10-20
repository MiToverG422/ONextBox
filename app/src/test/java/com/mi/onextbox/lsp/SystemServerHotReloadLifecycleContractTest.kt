package com.mi.onextbox.lsp

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards external resource ownership; Android/OEM callback behavior still needs device tests. */
class SystemServerHotReloadLifecycleContractTest {
    @Test fun permissionObserverOwnsItsQueueAndRebindsTheExistingController() {
        val text = source("PermissionSystemHooker.kt")
        assertTrue(text.contains("arrayOf(observerContext, observerController)"))
        assertTrue(text.contains("val handler = Handler(controllerLooper)"))
        assertTrue(text.contains("observerHandler?.removeCallbacksAndMessages(null)"))
        assertTrue(text.contains("unregisterContentObserver(it)"))
        assertTrue(text.contains("action = ::handleClearRequest"))
    }

    @Test fun refreshRateRestoresTheLivePolicyWithoutReplayingSystemReady() {
        val text = source("SettingsGlobalExtremeRefreshRateHooker.kt")
        assertTrue(text.contains("capture = { policy }"))
        assertTrue(text.contains("restore = { savedPolicy -> bindPolicy(savedPolicy) }"))
        assertTrue(text.contains("resumeAfterRejection = { policyReplay?.schedule() }"))
        assertTrue(text.contains("policyReplay?.detach()"))
        assertTrue(text.contains("observerHandler?.removeCallbacksAndMessages(null)"))
        assertTrue(text.contains("unregisterContentObserver(it)"))
    }

    @Test fun stashedMediaWorkerStartsAfterCommitAndReleasesItsOwnGainBeforeExit() {
        val text = source("SmallWindowPolicyHooker.kt")
        assertFalse(text.contains("Handler(HandlerThread("))
        assertTrue(text.contains("HotReloadLifecycle.afterCommit(::startWorker)"))
        assertTrue(text.contains("arrayOf(monitor?.get(), HashMap(owned))"))
        val detach = text.substringAfter("// Remove only our gain before retiring.")
        assertTrue(detach.indexOf("\"applyVolumeShaper\", id, terminate") < detach.indexOf("thread.quitSafely()"))
        assertTrue(detach.contains("thread.join(1_000)"))
        assertTrue(text.contains("reapplyOwned = true"))
    }

    @Test fun smallWindowVisualsRemoveExternalListenersAndRestoreNativeViews() {
        val text = source("SmallWindowHooker.kt")
        assertTrue(text.contains("view.removeOnLayoutChangeListener(listener)"))
        assertTrue(text.contains("animator.removeListener(listener)"))
        assertTrue(text.contains("view.removeCallbacks(task)"))
        assertTrue(text.contains("active.forEach(::finishStyle)"))
        assertTrue(text.contains("if (host.isAttachedToWindow) applyStyle(host, kind)"))
    }

    @Test fun assistantRetiresQueuesAndRejectsOutstandingRemoteCallbacks() {
        val assistant = source("AssistantHooker.kt")
        assertTrue(assistant.contains("canReload = { !assistantLaunchInProgress.get() }"))
        assertTrue(assistant.contains("assistantHandler.removeCallbacksAndMessages(null)"))
        assertTrue(assistant.contains("executor.awaitTermination(1, TimeUnit.SECONDS)"))
        assertTrue(assistant.contains("registerPowerSettingBridge(source, context)"))
        val native = source("NativeAssistantSessionRecovery.kt")
        assertTrue(native.contains("pending == null && outstandingCallbacks.get() == 0"))
        assertTrue(native.contains("callbackReleased.compareAndSet(false, true)"))
        assertTrue(native.contains("handler.removeCallbacksAndMessages(null)"))
    }

    @Test fun dynamicallyDiscoveredLoadersHaveExplicitReplayOwners() {
        assertTrue(source("AthenaPolicyHooker.kt").contains("registerReplay(\"athena-dynamic-loaders\""))
        assertTrue(source("AthenaPolicyHooker.kt").contains("discoveredLoaders.add(classLoader)"))
        assertTrue(source("AssistantHooker.kt").contains("registerReplay(\"assistant-runtime-loaders\""))
    }

    private fun source(name: String): String = listOf(
        File("src/main/java/com/mi/onextbox/lsp/$name"),
        File("app/src/main/java/com/mi/onextbox/lsp/$name"),
    ).first { it.isFile }.readText()
}
