package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class NetworkIdentitySlotsTest {
    private val tw = NetworkIdentityRules.Profile("tw", "46692", "Chunghwa Telecom")
    private val us = NetworkIdentityRules.Profile("us", "310260", "T-Mobile")
    private fun native() = NetworkIdentityRules.properties.associateWith {
        when {
            it.endsWith("numeric") -> "46011,45507"
            it.endsWith("alpha") -> "China Telecom,CTM"
            else -> "cn,mo"
        }
    }
    @Test fun legacyProfileMigratesToBothSlotsWithoutEnablingMaster() {
        val values = mapOf(NetworkIdentitySettings.REGION to "us", NetworkIdentitySettings.NUMERIC to "310260", NetworkIdentitySettings.NAME to "T-Mobile")
        val state = NetworkIdentitySettings.fromValues(values)
        assertFalse(state.enabled)
        assertEquals(state.sim1, state.sim2)
        assertEquals("310260", state.sim2.numeric)
        assertTrue(state.profiles().isEmpty())
        assertEquals(mapOf(0 to us, 1 to us), state.copy(enabled = true).profiles())
    }
    @Test fun explicitSlotSettingsWinOverLegacyIncludingDisabled() {
        val state = NetworkIdentitySettings.fromValues(mapOf(NetworkIdentitySettings.ENABLED to true,
            NetworkIdentitySettings.REGION to "us", NetworkIdentitySettings.NUMERIC to "310260", NetworkIdentitySettings.NAME to "T-Mobile",
            NetworkIdentitySettings.slotKey(1, "enabled") to false,
            NetworkIdentitySettings.slotKey(0, "region") to "tw", NetworkIdentitySettings.slotKey(0, "numeric") to "46692",
            NetworkIdentitySettings.slotKey(0, "name") to "Chunghwa Telecom"))
        assertEquals(mapOf(0 to tw), state.profiles())
        assertFalse(state.sim2.enabled)
    }
    @Test fun editingSim2DoesNotTouchSim1() {
        val old = NetworkIdentitySettings.State(enabled = true)
        val next = old.withSlot(1, NetworkIdentitySettings.Slot(region = us.region, numeric = us.numeric, name = us.name))
        assertEquals(old.sim1, next.sim1)
        assertEquals(mapOf(0 to tw, 1 to us), next.profiles())
    }
    @Test fun distinctProfilesApplyAtPhysicalSlotIndices() {
        val values = NetworkIdentityRules.transformSlots(native(), mapOf(0 to tw, 1 to us))
        assertEquals("46692,310260", values["gsm.sim.operator.numeric"])
        assertEquals("tw,us", values["gsm.operator.iso-country"])
        assertEquals("Chunghwa Telecom,T-Mobile", values["gsm.operator.alpha"])
    }
    @Test fun disablingOneSlotRestoresOnlyThatSlotAndRestartRetainsRecovery() {
        val values = native().toMutableMap()
        var journal = emptyMap<String, NetworkIdentityProperties.Record>()
        val engine = NetworkIdentityProperties({ values.getValue(it) }, { key, value -> values[key] = value }, { journal = it.toMap() })
        engine.refreshSlots(mapOf(0 to tw, 1 to us))
        engine.refreshSlots(mapOf(1 to us))
        assertEquals("46011,310260", values["gsm.sim.operator.numeric"])
        assertEquals("cn,us", values["gsm.operator.iso-country"])
        val restarted = NetworkIdentityProperties({ values.getValue(it) }, { key, value -> values[key] = value }, { journal = it.toMap() }, journal)
        restarted.refreshSlots(emptyMap())
        assertEquals(native(), values)
        assertTrue(journal.isEmpty())
    }
    @Test fun emptyFirstSlotDoesNotShiftSecondSlotToFirst() {
        val original = native().mapValues { (_, v) -> "," + v.substringAfter(',') }
        val changed = NetworkIdentityRules.transformSlots(original, mapOf(0 to tw, 1 to us))
        assertEquals(",310260", changed["gsm.sim.operator.numeric"])
        assertEquals(",us", changed["gsm.operator.iso-country"])
    }
    @Test fun invalidSlotProfileLeavesOtherSlotFunctional() {
        val changed = NetworkIdentityRules.transformSlots(native(), mapOf(0 to tw.copy(region = "bad"), 1 to us))
        assertEquals("46011,310260", changed["gsm.sim.operator.numeric"])
    }

    @Test fun callRegionProtectionIsOptInAndScopedToConfiguredCallers() {
        val key = NetworkIdentitySettings.PRESERVE_CALL_REGION
        val legacy = mapOf(NetworkIdentitySettings.ENABLED to true)
        assertFalse(NetworkIdentitySettings.fromValues(legacy).preserveCallRegion)
        assertTrue(NetworkIdentitySettings.fromValues(legacy + (key to true)).preserveCallRegion)
        assertTrue(key in NetworkIdentitySettings.keys)
        val profiles = mapOf(0 to tw, 1 to us)
        val normal = NetworkIdentityRules.Publication(profiles)
        val protected = normal.copy(attributionAppIds = setOf(10136, 10087))
        assertEquals(profiles, normal.profilesForApp(10136))
        assertTrue(protected.profilesForApp(10136).isEmpty())
        assertTrue(protected.profilesForApp(10087).isEmpty())
        assertEquals(profiles, protected.profilesForApp(11001))
    }

    @Test fun protectionRestoresBothRegionsAndTracksRoamingAndSimRemoval() {
        val values = native().toMutableMap()
        var saved = emptyMap<String, NetworkIdentityProperties.Record>()
        fun engine() = NetworkIdentityProperties({ values.getValue(it) }, { k, v -> values[k] = v }, { saved = it.toMap() }, saved)
        val profiles = mapOf(0 to tw, 1 to us)
        engine().refreshSlots(profiles)
        val active = engine()
        active.refreshSlots(profiles, preserveCallRegion = true)
        for ((key, value) in native()) {
            assertEquals(key, if (key.endsWith(".alpha")) "Chunghwa Telecom,T-Mobile" else value, values[key])
        }
        val updates = mapOf("gsm.operator.iso-country" to "jp,", "gsm.operator.numeric" to "44010,",
            "gsm.sim.operator.numeric" to "46011,", "gsm.sim.operator.iso-country" to "cn,",
            "gsm.sim.operator.alpha" to "China Telecom,", "gsm.operator.alpha" to "Roaming carrier,")
        for ((key, value) in updates) {
            values[key] = value
            active.refreshAfterNativeWrite(key, value, profiles, preserveCallRegion = true)
        }
        assertEquals("cn,", values["gsm.sim.operator.iso-country"])
        assertEquals("jp,", values["gsm.operator.iso-country"])
        assertEquals("Chunghwa Telecom,", values["gsm.operator.alpha"])
        active.refreshSlots(profiles)
        assertEquals("tw,", values["gsm.operator.iso-country"])
        assertEquals("46692,", values["gsm.sim.operator.numeric"])
        engine().refreshSlots(emptyMap())
        assertEquals(updates, values)
        assertTrue(saved.isEmpty())
    }
}
