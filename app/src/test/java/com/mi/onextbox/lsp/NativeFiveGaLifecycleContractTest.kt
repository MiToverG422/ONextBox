package com.mi.onextbox.lsp

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** The JVM retry tests cover behavior; these checks bind the Android entry points to that policy. */
class NativeFiveGaLifecycleContractTest {
    private val root = listOf(File("src/main/java/com/mi/onextbox"), File("app/src/main/java/com/mi/onextbox"))
        .first { it.isDirectory }
    private fun source(path: String) = root.resolve(path).readText()

    @Test fun disabledColdStartDoesNotCreatePhoneLoaderObserversOrReplayAStoredSession() {
        val phone = source("lsp/NativeFiveGaPhoneHooker.kt")
        val gate = "if (!NativeFiveGaSettings.getEnabled() || SafeModeRuntime.blocked)"
        val entry = phone.substringAfter("fun hook(loader: ClassLoader)").substringBefore("private class Discovery")
        assertTrue(entry.indexOf(gate) in 0 until entry.indexOf("Discovery(loader)"))
        val replay = phone.substringAfter("private fun attach(actual: ClassLoader)").substringBefore("private fun stopObservers")
        assertTrue(replay.indexOf(gate) in 0 until replay.indexOf("Session(actual).install()"))
    }

    @Test fun bothPhonePublicationPathsStopBeforePublishingDisabledState() {
        val phone = source("lsp/NativeFiveGaPhoneHooker.kt")
        for (method in listOf("private fun publish()", "private fun publishNow()")) {
            assertTrue(phone.substringAfter(method).trimStart().startsWith("{\n            if (!enabled()) { pausePublication(); return }"))
        }
        assertTrue(phone.contains("NativeFiveGaBridge.pausePublisher()"))
    }

    @Test fun disablingDuringLoaderDiscoveryRemovesObserversAndPollingBeforeFurtherWork() {
        val pending = source("lsp/NativeFiveGaPhoneHooker.kt")
            .substringAfter("private fun schedule() {").substringBefore("private fun attach(")
        val gate = pending.substringAfter("if (!NativeFiveGaSettings.getEnabled() || SafeModeRuntime.blocked)")
            .substringBefore("HotReloadLifecycle.withInfrastructureHook")
        assertTrue(gate.contains("stopObservers()"))
        assertTrue(gate.contains("main.removeCallbacks(poll)"))
        assertTrue(gate.contains("return@post"))
    }

    @Test fun cleanupUsesItsExistingClientAndCannotAcquireAnotherProvider() {
        val bridge = source("lsp/NativeFiveGaBridge.kt")
        val discovery = bridge.substringAfter("private class Discovery(").substringBefore("private fun discover(")
        assertTrue(discovery.contains("client.call(cleanupMethod"))
        assertFalse(discovery.contains("contentResolver"))
        assertEquals(1, Regex("acquireUnstableContentProviderClient").findAll(bridge).count())
        assertFalse(bridge.contains("postDelayed(connect, 3000)"))
        assertFalse(bridge.contains("worker.post(publish)"))
    }

    @Test fun settingsAndSystemUiStopTheirSubscriptionsWhenTheFeatureIsDisabled() {
        val rows = source("ui/screens/NativeFiveGaSettingsRows.kt")
        assertTrue(rows.contains("DisposableEffect(app, enabled, saving)"))
        assertTrue(rows.contains("if (enabled && !saving) NativeFiveGaBridge.subscribe"))
        val ui = source("lsp/NativeFiveGaUiHooker.kt")
        val update = ui.substringAfter("private fun updateSubscription()").substringBefore("private fun refresh()")
        assertTrue(update.contains("if (!enabled())"))
        assertTrue(update.contains("previous?.close()"))
        assertTrue(update.contains("snapshot = null"))
        assertTrue(ui.substringAfter("private fun refreshNow()").trimStart().startsWith("{\n            updateSubscription()"))
    }
}
