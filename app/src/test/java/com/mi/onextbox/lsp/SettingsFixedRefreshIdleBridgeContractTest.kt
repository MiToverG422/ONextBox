package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.HotReloadResourceCoordinator
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class SettingsFixedRefreshIdleBridgeContractTest {
    private fun source(name: String): String = listOf(File("src/main/java/com/mi/onextbox/lsp/$name.kt"),
        File("app/src/main/java/com/mi/onextbox/lsp/$name.kt")).first { it.isFile }.readText()

    @Test fun hooksTheVendorOutputInsteadOfMutatingItsRetainedEnableFlags() {
        val bridge = source("SettingsFixedRefreshIdleBridge")
        assertTrue(bridge.contains("\"setTouchIdleConfig\""))
        assertTrue(bridge.contains("\"setTouchIdleShouldPause\""))
        assertTrue(bridge.contains("fields.config.outgoing(original, true)"))
        assertFalse(bridge.contains("setTouchIdleFeatureSwitch"))
        assertFalse(bridge.contains("notifyDisableOtiMode"))
        assertFalse(bridge.contains("setBoolean("))
        assertFalse(bridge.contains(".transact("))
        assertFalse(bridge.contains("setprop"))
    }

    @Test fun restorationReconstructsCurrentNativeConfigAndPauseRatherThanUsingAnOldSnapshot() {
        val bridge = source("SettingsFixedRefreshIdleBridge")
        assertTrue(bridge.contains("fields.replayConfig.invoke(owner, false)"))
        assertTrue(bridge.contains("fields.nativePause.getBoolean(owner)"))
        assertTrue(bridge.contains("val nativePause = field(manager, \"mTouchIdleShouldPause\")"))
        val detach = bridge.substringAfter("private fun detach()")
        assertTrue(detach.indexOf("requested = false") < detach.indexOf("reapply()"))
        assertTrue(detach.indexOf("reapply()") < detach.indexOf("manager = null"))
    }

    @Test fun thePhysicalConstraintIsPublishedOnlyAfterReleasingTheWindowPolicyLock() {
        val hook = source("SettingsFixedRefreshRateHooker")
        val calculate = hook.substringAfter("private fun fixedRate(")
        assertTrue(calculate.contains("val result = synchronized(current.lock)"))
        assertTrue(calculate.contains("SettingsFixedRefreshIdleBridge.setRequested(result > 0f)"))
        assertTrue(calculate.indexOf("effectiveHertz(") < calculate.indexOf("setRequested(result > 0f)"))
        assertFalse(source("SettingsFixedRefreshIdleBridge").contains("fixedRate("))
    }

    @Test fun screenTransitionsAndColdServicePublicationReapplyWithoutPolling() {
        val bridge = source("SettingsFixedRefreshIdleBridge")
        assertTrue(bridge.contains("\"screenStateChange\""))
        assertTrue(bridge.contains("\"publish\""))
        assertTrue(bridge.contains("SettingsFixedRefreshRateHooker.hasFixedSelection()"))
        assertTrue(bridge.contains("fields.screen.getInt(owner) == Display.STATE_ON"))
        assertTrue(bridge.contains("if (!fields.service.isInstance(service)) return"))
        assertFalse(bridge.contains("postDelayed"))
        assertFalse(bridge.contains("while ("))
    }

    @Test fun hotReloadOwnsAndCancelsTheQueueButExportsOnlyTheNativeManager() {
        val bridge = source("SettingsFixedRefreshIdleBridge")
        assertTrue(bridge.contains("capture = { manager }"))
        assertTrue(bridge.contains("restore = { saved -> bind(saved) }"))
        assertTrue(bridge.contains("resumeAfterRejection = ::ensureBound"))
        assertTrue(bridge.contains("replay?.detach()"))
        assertTrue(bridge.contains("Handler(Looper.getMainLooper())"))
        assertTrue(bridge.contains("SettingsRefreshRatePolicyReplay(HotReloadLifecycle"))
        assertFalse(bridge.contains("removeCallbacksAndMessages"))
        val hostManager = Any()
        val owner = HotReloadResourceCoordinator { it() }
        owner.register("fixed-refresh-native-idle", capture = { hostManager })
        assertSame(hostManager, owner.prepare()["fixed-refresh-native-idle"])
    }

    @Test fun anObserverDetachFailureKeepsTheOldFixedPolicyAvailableForRollback() {
        val owner = source("SettingsGlobalExtremeRefreshRateHooker")
        val detach = owner.substringAfter("detach = {").substringBefore("restore =")
        assertTrue(detach.indexOf("unregisterContentObserver") < detach.indexOf("SettingsFixedRefreshRateHooker.clear()"))
        assertTrue(detach.indexOf("unregisterContentObserver") < detach.indexOf("policyReplay?.detach()"))
    }
}
