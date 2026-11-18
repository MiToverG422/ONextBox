package com.mi.onextbox.lsp

import com.mi.onextbox.diagnostics.SafeModePolicy
import com.mi.onextbox.lsp.compat.AdaptationProfileGate
import com.mi.onextbox.lsp.compat.HotReloadResourceCoordinator
import com.mi.onextbox.lsp.compat.MODERN_PREFERENCES_READY_KEY
import org.junit.Assert.*
import org.junit.Test

class PhoneHookRecoveryTest {
    private val native = NetworkIdentityRules.properties.associateWith {
        when { it.endsWith("numeric") -> "46000,46001"; it.endsWith("alpha") -> "Real1,Real2"; else -> "cn,cn" }
    }
    private val profile = NetworkIdentityRules.Profile("tw", "46692", "Demo")
    private val enabled = mapOf(NetworkIdentitySettings.ENABLED to true, MODERN_PREFERENCES_READY_KEY to true)

    @Test fun safeModeRestoresGlobalPropertiesWhileGenuineReadsRemainProtected() {
        val values = native.toMutableMap()
        val engine = NetworkIdentityProperties({ values.getValue(it) }, { k, v -> values[k] = v }, {})
        engine.refreshSlots(NetworkIdentitySettings.activeProfiles(enabled, true))
        assertEquals(native.getValue("gsm.operator.numeric"), engine.actual("gsm.operator.numeric", values.getValue("gsm.operator.numeric")))
        val disabled = NetworkIdentitySettings.activeProfiles(enabled + (SafeModePolicy.MANUAL to true), true)
        assertTrue(disabled.isEmpty())
        engine.refreshSlots(disabled)
        assertEquals(native, values)
        assertFalse(engine.hasOverrides)
    }

    @Test fun unavailableConfigurationAndSuspendedAdaptationCannotPublishNewIdentity() {
        assertTrue(NetworkIdentitySettings.activeProfiles(enabled - MODERN_PREFERENCES_READY_KEY, true).isEmpty())
        assertTrue(NetworkIdentitySettings.activeProfiles(enabled, false).isEmpty())
        var selected = SystemAdaptationVersion.ColorOs17
        val gate = AdaptationProfileGate { selected }
        gate.bind()
        selected = SystemAdaptationVersion.ColorOs16
        assertTrue(NetworkIdentitySettings.activeProfiles(enabled, gate.isBoundProfileActive()).isEmpty())
        assertFalse(NetworkIdentitySettings.activeProfiles(enabled, true).isEmpty())
    }

    @Test fun crashRestartInSafeModeRestoresThePersistedRecoveryJournal() {
        val values = native.toMutableMap()
        var journal = emptyMap<String, NetworkIdentityProperties.Record>()
        NetworkIdentityProperties({ values.getValue(it) }, { k, v -> values[k] = v }, { journal = it.toMap() })
            .refreshSlots(mapOf(0 to profile))
        val restart = NetworkIdentityProperties({ values.getValue(it) }, { k, v -> values[k] = v }, { journal = it.toMap() }, journal)
        restart.refreshSlots(NetworkIdentitySettings.activeProfiles(enabled + (SafeModePolicy.MANUAL to true), false))
        assertEquals(native, values)
        assertTrue(journal.isEmpty())
    }

    @Test fun disabledIdentityDoesNotReadPropertiesOrWriteStorage() {
        val engine = NetworkIdentityProperties({ error("Unexpected property read") }, { _, _ -> fail() }, { fail() })
        repeat(100) { engine.refreshSlots(emptyMap()); engine.refresh(null) }
    }

    @Test fun nativeIdentityUpdateUsesTwoDurableCommitsAndRestoresTheNewestValue() {
        val values = native.toMutableMap()
        var commits = 0
        val engine = NetworkIdentityProperties({ values.getValue(it) }, { k, v -> values[k] = v }, { commits++ })
        engine.refreshSlots(mapOf(0 to profile))
        commits = 0
        values["gsm.operator.numeric"] = "46002,46001"
        engine.refreshAfterNativeWrite("gsm.operator.numeric", "46002,46001", mapOf(0 to profile))
        assertEquals(2, commits)
        engine.refreshSlots(emptyMap())
        assertEquals("46002,46001", values["gsm.operator.numeric"])
    }

    @Test fun nativeValueEqualToReplacementBecomesGenuineInsteadOfRestoringStaleData() {
        val values = native.toMutableMap()
        val engine = NetworkIdentityProperties({ values.getValue(it) }, { k, v -> values[k] = v }, {})
        engine.refreshSlots(mapOf(0 to profile))
        values["gsm.operator.numeric"] = "46692,46001"
        engine.refreshAfterNativeWrite("gsm.operator.numeric", "46692,46001", mapOf(0 to profile))
        engine.refreshSlots(emptyMap())
        assertEquals("46692,46001", values["gsm.operator.numeric"])
    }

    @Test fun nativeUpdateCanRecoverAfterEitherJournalCommitOrAnyPropertyWriteFails() {
        for (failure in 0..7) {
            val values = native.toMutableMap()
            var journal = emptyMap<String, NetworkIdentityProperties.Record>()
            NetworkIdentityProperties({ values.getValue(it) }, { k, v -> values[k] = v }, { journal = it.toMap() })
                .refreshSlots(mapOf(0 to profile))
            values["gsm.operator.numeric"] = "46002,46001"
            var operation = 0
            val engine = NetworkIdentityProperties({ values.getValue(it) }, { k, v ->
                if (operation++ == failure) error("Process terminated before write")
                values[k] = v
            }, {
                if (operation++ == failure) error("Process terminated before commit")
                journal = it.toMap()
            }, journal)
            runCatching { engine.refreshAfterNativeWrite("gsm.operator.numeric", "46002,46001", mapOf(0 to profile.copy(name = "New"))) }
            val recovered = NetworkIdentityProperties({ values.getValue(it) }, { k, v -> values[k] = v }, { journal = it.toMap() }, journal)
            recovered.refreshSlots(emptyMap())
            assertEquals("failure=$failure", native + ("gsm.operator.numeric" to "46002,46001"), values)
            assertTrue(journal.isEmpty())
        }
    }

    @Test fun disabledReplacementAcceptsAllPreviousFiveGaResourceKeysWithoutStartingWork() {
        val old = HotReloadResourceCoordinator { it() }
        old.register("phone:native-five-ga")
        old.register("phone:native-five-ga-discovery")
        old.registerReplay("phone:native-five-ga-loader", { ClassLoader.getSystemClassLoader().parent }, {})
        val saved = old.prepare()
        val replacement = HotReloadResourceCoordinator { it() }
        replacement.begin(saved)
        NativeFiveGaPhoneResources.registerInactive(replacement)
        replacement.replaySavedState()
        replacement.complete()
        assertFalse(replacement.isRetiring)
        assertEquals("hook", replacement.withHook({ "native" }) { "hook" })
        assertEquals(saved.keys, replacement.prepare().keys)
    }

    @Test fun inertOwnersCanBeReplacedByAnEnabledOrUnsupportedSession() {
        val old = HotReloadResourceCoordinator { it() }
        NativeFiveGaPhoneResources.registerInactive(old)
        val saved = old.prepare()
        val replacement = HotReloadResourceCoordinator { it() }
        replacement.begin(saved)
        NativeFiveGaPhoneResources.registerInactive(replacement)
        var restored = false
        replacement.register("phone:native-five-ga", restore = { restored = true })
        replacement.replaySavedState()
        replacement.complete()
        assertTrue(restored)
    }

    @Test fun disabledInstalledSessionRejectsHotReloadBeforeDetachingItsMethodCoverage() {
        var detached = false
        var resumed = false
        val old = HotReloadResourceCoordinator { it() }
        old.register("phone:native-five-ga",
            canPrepare = { NativeFiveGaPhoneResources.canReplaceInstalledSession(false, false) },
            detach = { detached = true }, resumeAfterRejection = { resumed = true })
        assertThrows(IllegalStateException::class.java) { old.prepare() }
        assertFalse(detached)
        assertTrue(resumed)
        assertFalse(old.isRetiring)
        assertEquals("hook", old.withHook({ "native" }) { "hook" })
        assertTrue(NativeFiveGaPhoneResources.canReplaceInstalledSession(true, false))
        assertFalse(NativeFiveGaPhoneResources.canReplaceInstalledSession(true, true))
    }

    @Test fun inactiveHandoverRetainsOnlyHostLoaderAndRecognizesOnlyFiveGaSessionOwners() {
        val loader = ClassLoader.getSystemClassLoader().parent
        val old = HotReloadResourceCoordinator { it() }
        NativeFiveGaPhoneResources.registerInactive(old, loader)
        val saved = old.prepare()
        assertSame(loader, saved["phone:native-five-ga-loader"])
        var replayed: Any? = null
        val next = HotReloadResourceCoordinator { it() }
        next.begin(saved)
        NativeFiveGaPhoneResources.registerInactive(next)
        next.registerReplay("phone:native-five-ga-loader", { null }, { replayed = it })
        next.replaySavedState()
        assertSame(loader, replayed)
        next.complete()
        assertTrue(NativeFiveGaPhoneResources.isSessionOwner("com.oplus.internal.telephony.OplusServiceStateTrackerImpl"))
        assertTrue(NativeFiveGaPhoneResources.isSessionOwner("com.oplus.internal.telephony.OplusServiceStateTrackerImpl\$3"))
        assertFalse(NativeFiveGaPhoneResources.isSessionOwner("android.os.SystemProperties"))
        assertFalse(NativeFiveGaPhoneResources.isSessionOwner("com.android.phone.PhoneInterfaceManager"))
    }
}
