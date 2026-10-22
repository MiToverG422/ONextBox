package com.mi.onextbox.lsp.compat

import android.content.SharedPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HookConfigSnapshotTest {
    private val stores = mutableListOf<FakePreferences>()

    @Before
    fun resetBeforeTest() {
        clearSnapshot()
    }

    @After
    fun resetAfterTest() {
        stores.forEach { it.failUnregister = false }
        clearSnapshot()
    }

    @Test
    fun successfulRegistrationMakesSnapshotListeningAndUpdatesLiveValues() {
        val preferences = store(ready = true, enabled = false)

        assertTrue(HookConfigSnapshot.attach(preferences))
        assertTrue(HookConfigSnapshot.isListening)
        assertTrue(HookConfigSnapshot.isAvailable)
        assertFalse(HookConfigSnapshot.boolean("enabled", true)!!)
        assertEquals(1, preferences.registerCalls)
        assertEquals(1, preferences.listeners.size)

        preferences.putAndNotify("enabled", true)

        assertTrue(HookConfigSnapshot.boolean("enabled", false)!!)
    }

    @Test
    fun registrationFailureKeepsReadableFallbackButDoesNotReportListening() {
        val preferences = store(ready = true, enabled = true).apply { failRegister = true }

        assertFalse(HookConfigSnapshot.attach(preferences))

        assertFalse(HookConfigSnapshot.isListening)
        assertTrue(HookConfigSnapshot.isAvailable)
        assertTrue(HookConfigSnapshot.boolean("enabled", false)!!)
        assertTrue(preferences.listeners.isEmpty())
        assertEquals(1, preferences.registerCalls)

        preferences.failRegister = false
        assertTrue(HookConfigSnapshot.attach(preferences))
        assertTrue(HookConfigSnapshot.isListening)
        assertEquals(2, preferences.registerCalls)
    }

    @Test
    fun reattachingSameStoreRefreshesWithoutDuplicatingListener() {
        val preferences = store(ready = true, enabled = false)
        assertTrue(HookConfigSnapshot.attach(preferences))
        preferences.data["enabled"] = true

        assertTrue(HookConfigSnapshot.attach(preferences))

        assertEquals(1, preferences.registerCalls)
        assertEquals(0, preferences.unregisterCalls)
        assertEquals(1, preferences.listeners.size)
        assertTrue(HookConfigSnapshot.boolean("enabled", false)!!)
    }

    @Test
    fun switchingStoresUnregistersPreviousListenerBeforeRegisteringNewOne() {
        val order = mutableListOf<String>()
        val old = store(ready = true, enabled = false).apply {
            onUnregister = { order += "unregister old" }
        }
        val replacement = store(ready = true, enabled = true).apply {
            onRegister = { order += "register new" }
        }
        assertTrue(HookConfigSnapshot.attach(old))

        assertTrue(HookConfigSnapshot.attach(replacement))

        assertEquals(listOf("unregister old", "register new"), order)
        assertTrue(old.listeners.isEmpty())
        assertEquals(1, replacement.listeners.size)
        assertTrue(HookConfigSnapshot.isListening)
        assertTrue(HookConfigSnapshot.boolean("enabled", false)!!)
    }

    @Test
    fun failedOldListenerRemovalRejectsSwitchAndPreservesExistingSubscription() {
        val old = store(ready = true, enabled = false)
        val replacement = store(ready = true, enabled = true)
        assertTrue(HookConfigSnapshot.attach(old))
        old.failUnregister = true

        assertFalse(HookConfigSnapshot.attach(replacement))

        assertTrue(HookConfigSnapshot.isListening)
        assertFalse(HookConfigSnapshot.boolean("enabled", true)!!)
        assertEquals(1, old.listeners.size)
        assertEquals(0, replacement.registerCalls)
        old.putAndNotify("enabled", true)
        assertTrue(HookConfigSnapshot.boolean("enabled", false)!!)
    }

    @Test
    fun newListenerRegistrationFailureDoesNotClaimStoreIsAttached() {
        val old = store(ready = true, enabled = false)
        val replacement = store(ready = true, enabled = true).apply { failRegister = true }
        assertTrue(HookConfigSnapshot.attach(old))

        assertFalse(HookConfigSnapshot.attach(replacement))

        assertTrue(old.listeners.isEmpty())
        assertTrue(replacement.listeners.isEmpty())
        assertFalse(HookConfigSnapshot.isListening)
        assertTrue(HookConfigSnapshot.boolean("enabled", false)!!)
    }

    @Test
    fun postRegistrationRefreshCatchesAnUpdateDuringAttach() {
        val preferences = store(ready = true, enabled = false).apply {
            onRegister = { putAndNotify("enabled", true) }
        }

        assertTrue(HookConfigSnapshot.attach(preferences))

        assertTrue(HookConfigSnapshot.isListening)
        assertTrue(HookConfigSnapshot.boolean("enabled", false)!!)
        assertEquals(2, preferences.allReads)
    }

    @Test
    fun delayedOldCallbackCannotOverwriteNewSnapshot() {
        val old = store(ready = true, enabled = false)
        val replacement = store(ready = true, enabled = true)
        assertTrue(HookConfigSnapshot.attach(old))
        val delayedCallback = old.listeners.single()
        assertTrue(HookConfigSnapshot.attach(replacement))

        delayedCallback.onSharedPreferenceChanged(old, "enabled")

        assertTrue(HookConfigSnapshot.boolean("enabled", false)!!)
    }

    @Test
    fun detachFailureRemainsTrackedAndCanBeRetried() {
        val preferences = store(ready = true, enabled = true)
        assertTrue(HookConfigSnapshot.attach(preferences))
        preferences.failUnregister = true

        assertFalse(HookConfigSnapshot.detach())
        assertTrue(HookConfigSnapshot.isListening)
        assertEquals(1, preferences.listeners.size)

        preferences.failUnregister = false
        assertTrue(HookConfigSnapshot.detach())
        assertFalse(HookConfigSnapshot.isListening)
        assertTrue(preferences.listeners.isEmpty())
        assertTrue(HookConfigSnapshot.isAvailable)
        assertTrue(HookConfigSnapshot.boolean("enabled", false)!!)
        assertTrue(HookConfigSnapshot.detach())
        assertEquals(2, preferences.unregisterCalls)
    }

    @Test
    fun detachedSnapshotIsRetainedAndIgnoresQueuedCallbacks() {
        val preferences = store(ready = true, enabled = true)
        assertTrue(HookConfigSnapshot.attach(preferences))
        val delayedCallback = preferences.listeners.single()
        assertTrue(HookConfigSnapshot.detach())
        preferences.data["enabled"] = false

        delayedCallback.onSharedPreferenceChanged(preferences, "enabled")

        assertFalse(HookConfigSnapshot.isListening)
        assertTrue(HookConfigSnapshot.boolean("enabled", false)!!)
    }

    @Test
    fun missingReadyMarkerKeepsLegacyFallbackUntilSyncArrives() {
        val preferences = store(ready = false, enabled = true)

        assertTrue(HookConfigSnapshot.attach(preferences))
        assertTrue(HookConfigSnapshot.isListening)
        assertFalse(HookConfigSnapshot.isAvailable)
        assertNull(HookConfigSnapshot.boolean("enabled", false))

        preferences.putAndNotify(MODERN_PREFERENCES_READY_KEY, true)

        assertTrue(HookConfigSnapshot.isAvailable)
        assertTrue(HookConfigSnapshot.boolean("enabled", false)!!)
    }

    @Test
    fun aReadyEmptySnapshotUsesDefaultsAndFiltersTheInternalMarker() {
        val preferences = store(ready = true)

        assertTrue(HookConfigSnapshot.attach(preferences))

        assertTrue(HookConfigSnapshot.isAvailable)
        assertFalse(HookConfigSnapshot.boolean("missing", false)!!)
        assertFalse(HookConfigSnapshot.boolean(MODERN_PREFERENCES_READY_KEY, false)!!)
    }

    @Test
    fun percentageAttributesUpdateIndependentlyAndResetColorToNative() {
        val settings = com.mi.onextbox.lsp.QsSliderPercentageSettings
        val preferences = store(ready = true)
        assertTrue(HookConfigSnapshot.attach(preferences))
        assertNull(settings.configurationTokenXposed())
        preferences.putAndNotify(settings.ENABLED, true)
        val originalToken = settings.configurationTokenXposed()
        assertTrue(originalToken != null)
        // Corner settings remain off; percentage rendering has its own refresh token.
        assertNull(com.mi.onextbox.lsp.QsTileCornerSettings.configurationTokenXposed())
        preferences.putAndNotify(settings.COLOR, 0xFFFFFFFFL)
        assertEquals(0xFFFFFFFFL, settings.stateXposed().color)
        assertTrue(originalToken != settings.configurationTokenXposed())
        preferences.putAndNotify(settings.SIZE, 28)
        assertEquals(28, settings.stateXposed().sizeSp)
        assertEquals(10, settings.stateXposed().offset)
        preferences.putAndNotify(settings.OFFSET, 100)
        assertEquals(100, settings.stateXposed().offset)
        preferences.putAndNotify(settings.COLOR, null)
        assertNull(settings.stateXposed().color)
        preferences.putAndNotify(settings.ENABLED, false)
        assertNull(settings.configurationTokenXposed())
        assertEquals(28, settings.stateXposed().sizeSp)
        assertEquals(100, settings.stateXposed().offset)
    }

    @Test
    fun percentageDefaultsAndClampsSurviveMissingAndInvalidStoredNumbers() {
        val settings = com.mi.onextbox.lsp.QsSliderPercentageSettings
        assertEquals(com.mi.onextbox.lsp.QsSliderPercentageSettings.State(), settings.stateXposed())
        val preferences = store(ready = true)
        assertTrue(HookConfigSnapshot.attach(preferences))
        assertNull(HookConfigSnapshot.long(settings.COLOR))
        preferences.putAndNotify(settings.COLOR, 0L)
        assertEquals(0L, HookConfigSnapshot.long(settings.COLOR))
        preferences.putAndNotify(settings.SIZE, -100)
        preferences.putAndNotify(settings.OFFSET, 200)
        assertEquals(5, settings.stateXposed().sizeSp)
        assertEquals(100, settings.stateXposed().offset)
    }

    @Test
    fun transientReadFailureDoesNotDiscardLastValidSnapshot() {
        val preferences = store(ready = true, enabled = true)
        assertTrue(HookConfigSnapshot.attach(preferences))
        preferences.failRead = true

        preferences.putAndNotify("enabled", false)

        assertTrue(HookConfigSnapshot.isListening)
        assertTrue(HookConfigSnapshot.isAvailable)
        assertTrue(HookConfigSnapshot.boolean("enabled", false)!!)

        preferences.failRead = false
        preferences.notifyChanged("enabled")
        assertFalse(HookConfigSnapshot.boolean("enabled", true)!!)
    }

    @Test
    fun profileSwitchMasksFeaturesWithoutOverwritingSavedChoices() {
        val preferences = store(ready = true, enabled = true)
        preferences.data["developer_allow_hyperos3"] = true
        preferences.data["system_adaptation_version"] = 0
        preferences.data["hyperos3_global_185hz"] = true
        preferences.data["radius"] = 24
        assertTrue(HookConfigSnapshot.attach(preferences))
        assertTrue(HookConfigSnapshot.boolean("enabled", false)!!)
        assertFalse(HookConfigSnapshot.boolean("hyperos3_global_185hz", false)!!)
        repeat(100) {
            preferences.putAndNotify("system_adaptation_version", 4)
            assertFalse(HookConfigSnapshot.boolean("enabled", true)!!)
            assertTrue(HookConfigSnapshot.boolean("hyperos3_global_185hz", false)!!)
            assertTrue(HookConfigSnapshot.boolean("developer_allow_hyperos3", false)!!)
            assertEquals(24, HookConfigSnapshot.int("radius", 0))
            assertEquals(true, preferences.data["enabled"])
            assertEquals(true, preferences.data["hyperos3_global_185hz"])
            preferences.putAndNotify("system_adaptation_version", 0)
            assertTrue(HookConfigSnapshot.boolean("enabled", false)!!)
            assertFalse(HookConfigSnapshot.boolean("hyperos3_global_185hz", true)!!)
        }
    }

    @Test
    fun removingDeveloperAccessRestoresColorOsEffectiveFlagsWithoutDeletingHyperOsChoices() {
        val preferences = store(ready = true, enabled = true)
        preferences.data["developer_allow_hyperos3"] = true
        preferences.data["system_adaptation_version"] = 4
        preferences.data["hyperos3_high_touch_rate"] = true
        assertTrue(HookConfigSnapshot.attach(preferences))
        assertFalse(HookConfigSnapshot.boolean("enabled", true)!!)
        preferences.putAndNotify("developer_allow_hyperos3", false)
        assertTrue(HookConfigSnapshot.boolean("enabled", false)!!)
        assertFalse(HookConfigSnapshot.boolean("hyperos3_high_touch_rate", true)!!)
        assertEquals(true, preferences.data["hyperos3_high_touch_rate"])
    }

    private fun store(ready: Boolean, enabled: Boolean? = null): FakePreferences =
        FakePreferences().also { preferences ->
            preferences.data[MODERN_PREFERENCES_READY_KEY] = ready
            enabled?.let { preferences.data["enabled"] = it }
            stores += preferences
        }

    private fun clearSnapshot() {
        check(HookConfigSnapshot.detach())
        check(HookConfigSnapshot.attach(FakePreferences()))
        check(HookConfigSnapshot.detach())
    }

    /** Pure JVM store: no Android Context, editor implementation, or stub method is invoked. */
    private class FakePreferences : SharedPreferences {
        val data = mutableMapOf<String, Any?>()
        val listeners = linkedSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()
        var registerCalls = 0
        var unregisterCalls = 0
        var allReads = 0
        var failRegister = false
        var failUnregister = false
        var failRead = false
        var onRegister: (() -> Unit)? = null
        var onUnregister: (() -> Unit)? = null

        fun putAndNotify(key: String, value: Any?) {
            data[key] = value
            notifyChanged(key)
        }

        fun notifyChanged(key: String) {
            listeners.toList().forEach { it.onSharedPreferenceChanged(this, key) }
        }

        override fun getAll(): MutableMap<String, *> {
            allReads++
            check(!failRead) { "Read failed" }
            return data.toMutableMap()
        }

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener,
        ) {
            registerCalls++
            check(!failRegister) { "Registration failed" }
            listeners += listener
            onRegister?.invoke()
        }

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener,
        ) {
            unregisterCalls++
            check(!failUnregister) { "Unregistration failed" }
            listeners -= listener
            onUnregister?.invoke()
        }

        override fun contains(key: String): Boolean = data.containsKey(key)
        override fun getBoolean(key: String, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun getInt(key: String, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getString(key: String, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
            (data[key] as? Set<*>)?.filterIsInstance<String>()?.toMutableSet() ?: defValues
        override fun edit(): SharedPreferences.Editor = error("The snapshot reader does not edit preferences")
    }
}
