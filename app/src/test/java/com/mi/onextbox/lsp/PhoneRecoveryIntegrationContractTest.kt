package com.mi.onextbox.lsp

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Bind the isolated transaction/reload tests to the Android-only dispatch and recovery paths. */
class PhoneRecoveryIntegrationContractTest {
    private val root = listOf(File("src/main/java/com/mi/onextbox"), File("app/src/main/java/com/mi/onextbox"))
        .first { it.isDirectory }

    @Test fun recoveryIsDispatchedBeforePreferenceDeferralAndBeforeTheFeatureSafetyGate() {
        val entry = root.resolve("lsp/OosLspModuleEntry.kt").readText()
        val defer = entry.substringAfter("private fun dispatchLifecycle").substringBefore("ModernHookRuntime.attach(this)")
        assertTrue(defer.indexOf("preparePhoneRecovery(target)") in 0 until defer.indexOf("deferLifecycle"))
        val hooks = entry.substringAfter("private fun dispatchPackageHooks").substringBefore("CouiDividerHooker.hook")
        assertTrue(hooks.indexOf("preparePhoneRecovery(target)") in 0 until hooks.indexOf("if (!AdaptationHookGate.isActive"))
    }

    @Test fun truthGettersSettersAndRecoveryCallbacksDoNotUseTheFeatureGate() {
        val identity = root.resolve("lsp/NetworkIdentityHooker.kt").readText()
        assertTrue(identity.contains("Bridge.hookInfrastructureFast(setter)"))
        assertTrue(identity.contains("Bridge.hookInfrastructureFast(getter)"))
        assertTrue(identity.contains("private val update = Runnable { HotReloadLifecycle.withInfrastructureHook"))
        assertTrue(identity.contains("key == SafeModePolicy.MANUAL"))
        assertTrue(identity.contains("refreshAfterNativeWrite"))
        val callback = identity.substringAfter("Bridge.hookInfrastructureFast(setter)").substringBefore("properties.declaredMethods")
        assertFalse(callback.contains(".all"))
        assertFalse(callback.contains("NetworkIdentitySettings.read"))
    }
}
