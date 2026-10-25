package com.mi.onextbox.lsp.compat

import android.content.SharedPreferences
import com.mi.onextbox.lsp.SystemAdaptationVersion
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
        assertEquals(22, settings.stateXposed().sizeSp)
        assertEquals(10, settings.stateXposed().offset)
        preferences.putAndNotify(settings.OFFSET, 100)
        assertEquals(100, settings.stateXposed().offset)
        preferences.putAndNotify(settings.COLOR, null)
        assertNull(settings.stateXposed().color)
        preferences.putAndNotify(settings.ENABLED, false)
        assertNull(settings.configurationTokenXposed())
        assertEquals(22, settings.stateXposed().sizeSp)
        assertEquals(100, settings.stateXposed().offset)
    }

    @Test
    fun sideVolumeAndControlCenterPercentagesKeepIndependentSwitchesAndAttributes() {
        val side = com.mi.onextbox.lsp.SideVolumePercentageSettings
        val qs = com.mi.onextbox.lsp.QsSliderPercentageSettings
        val preferences = store(ready = true)
        assertTrue(HookConfigSnapshot.attach(preferences))
        assertEquals(com.mi.onextbox.lsp.QsSliderPercentageSettings.State(), side.stateXposed())
        assertTrue(side.keys.intersect(qs.keys).isEmpty())
        preferences.putAndNotify(side.ENABLED, true)
        preferences.putAndNotify(side.SIZE, 17)
        preferences.putAndNotify(side.COLOR, 0xFF36D167L)
        preferences.putAndNotify(side.OFFSET, 25)
        assertTrue(side.stateXposed().enabled)
        assertFalse(qs.stateXposed().enabled)
        assertEquals(11, qs.stateXposed().sizeSp)
        assertNull(qs.stateXposed().color)
        assertEquals(10, qs.stateXposed().offset)
        val originalToken = side.configurationTokenXposed()
        preferences.putAndNotify(qs.ENABLED, true)
        preferences.putAndNotify(qs.SIZE, 22)
        preferences.putAndNotify(qs.COLOR, 0xFFFFFFFFL)
        assertEquals(originalToken, side.configurationTokenXposed())
        preferences.putAndNotify(side.ENABLED, false)
        assertNull(side.configurationTokenXposed())
        assertTrue(qs.stateXposed().enabled)
        assertEquals(17, side.stateXposed().sizeSp)
        preferences.putAndNotify(side.COLOR, null)
        assertNull(side.stateXposed().color)
        assertEquals(0xFFFFFFFFL, qs.stateXposed().color)
    }

    @Test
    fun sideVolumeAppearanceUpdatesAndDefaultsStayIndependentOfPercentage() {
        val appearance = com.mi.onextbox.lsp.SideVolumeAppearanceSettings
        val percentage = com.mi.onextbox.lsp.SideVolumePercentageSettings
        val preferences = store(ready = true)
        assertTrue(HookConfigSnapshot.attach(preferences))
        assertNull(appearance.configurationTokenXposed())
        assertNull(percentage.configurationTokenXposed())
        preferences.putAndNotify(appearance.KEEP_WIDE, true)
        assertTrue(appearance.stateXposed().keepWide)
        assertTrue(appearance.configurationTokenXposed() != null)
        preferences.putAndNotify(percentage.ENABLED, true)
        assertTrue(appearance.stateXposed().keepWide)
        preferences.putAndNotify(appearance.KEEP_WIDE, false)
        assertNull(appearance.configurationTokenXposed())
        assertTrue(percentage.stateXposed().enabled)
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
    fun legacyProfilePreferencesCannotOverrideDetectedVersionOrChangeSavedFeatureChoices() {
        val preferences = store(ready = true, enabled = true)
        preferences.data["developer_allow_hyperos3"] = true
        preferences.data["system_adaptation_version"] = 4
        preferences.data["disabled"] = false
        preferences.data["recent_task_radius_dp"] = 24
        for (detectedVersion in SystemAdaptationVersion.entries) {
            val snapshot = HookPreferenceSnapshot { detectedVersion }
            try {
                assertTrue(snapshot.attach(preferences))
                for (legacyVersion in listOf(null, 0, 1, 1L, 2, 3, 4, -1, Int.MAX_VALUE, "1")) {
                    preferences.putAndNotify("system_adaptation_version", legacyVersion)
                    assertEquals(detectedVersion, snapshot.adaptationVersion())
                    assertEquals(detectedVersion == SystemAdaptationVersion.ColorOs17,
                        snapshot.boolean("enabled", false))
                    assertFalse(snapshot.boolean("disabled", true)!!)
                    assertEquals(24, snapshot.int("recent_task_radius_dp", 0))
                    assertEquals(true, preferences.data["enabled"])
                    assertEquals(false, preferences.data["disabled"])
                    assertEquals(legacyVersion, preferences.data["system_adaptation_version"])
                }
                preferences.putAndNotify("developer_allow_hyperos3", false)
                assertEquals(detectedVersion, snapshot.adaptationVersion())
            } finally {
                assertTrue(snapshot.detach())
            }
        }
    }

    @Test
    fun colorOs16MasksUnadaptedBooleansButPreservesSearchModeAndRestoresColorOs17Choices() {
        val preferences = store(ready = true, enabled = true)
        preferences.data["system_adaptation_version"] = 0
        preferences.data["launcher_taskbar_search_box"] = 2
        preferences.data["systemui_native_live_update"] = true
        val savedChoices = preferences.data.toMap()
        val colorOs16 = HookPreferenceSnapshot { SystemAdaptationVersion.ColorOs16 }
        val colorOs17 = HookPreferenceSnapshot { SystemAdaptationVersion.ColorOs17 }
        try {
            assertTrue(colorOs16.attach(preferences))
            assertEquals(SystemAdaptationVersion.ColorOs16, colorOs16.adaptationVersion())
            assertFalse(colorOs16.boolean("enabled", false)!!)
            assertFalse(colorOs16.boolean("systemui_native_live_update", false)!!)
            assertFalse(colorOs16.boolean("absent_default_enabled", true)!!)
            assertEquals(2, colorOs16.int("launcher_taskbar_search_box", 0))
            assertEquals(savedChoices, preferences.data)
            assertTrue(colorOs16.detach())

            assertTrue(colorOs17.attach(preferences))
            assertEquals(SystemAdaptationVersion.ColorOs17, colorOs17.adaptationVersion())
            assertTrue(colorOs17.boolean("enabled", false)!!)
            assertTrue(colorOs17.boolean("systemui_native_live_update", false)!!)
            assertEquals(2, colorOs17.int("launcher_taskbar_search_box", 0))
            assertEquals(savedChoices, preferences.data)
        } finally {
            assertTrue(colorOs16.detach())
            assertTrue(colorOs17.detach())
        }
    }

    @Test
    fun colorOs16AdaptedFlagsRemainLiveWithoutUnmaskingAdjacentColorOs17Settings() {
        val preferences = store(ready = true)
        val adapted = listOf(
            "systemui_hide_notification_clear", "systemui_hide_rotation_suggestion",
            "systemui_hide_disconnected_bluetooth", "settings_force_google_entry",
            "settings_restore_app_open_button", "settings_international_enabled",
            "settings_force_app_auto_start", "settings_international_wallet",
        )
        val unadapted = listOf(
            "systemui_native_live_update", "assistant_native_circle_enabled",
            "settings_restore_domestic_about_device", "settings_skip_special_permission_risk_confirm",
        )
        (adapted + unadapted).forEach { preferences.data[it] = true }
        val saved = preferences.data.toMap()
        val snapshot = HookPreferenceSnapshot { SystemAdaptationVersion.ColorOs16 }
        try {
            assertTrue(snapshot.attach(preferences))
            adapted.forEach { assertTrue(it, snapshot.boolean(it, false)!!) }
            unadapted.forEach { assertFalse(it, snapshot.boolean(it, true)!!) }
            assertEquals(saved, preferences.data)
            adapted.forEach { key ->
                preferences.putAndNotify(key, false)
                assertFalse(key, snapshot.boolean(key, true)!!)
                preferences.putAndNotify(key, true)
                assertTrue(key, snapshot.boolean(key, false)!!)
            }
            assertEquals(saved, preferences.data)
        } finally {
            assertTrue(snapshot.detach())
        }
    }

    @Test
    fun colorOs16MasksModesAndTextWhileKeepingSupportedCustomizationAndSavedChoices() {
        val preferences = store(ready = true).apply {
            data["assistant_power_mode"] = 3
            data["unknown_float"] = 2.5f
            data["unknown_long"] = 99L
            data["unknown_text"] = "saved"
            data["unknown_set"] = setOf("saved")
            data["recent_task_radius_dp"] = 42
            data["systemui_qs_slider_percentage_color"] = 0xFF123456L
            data["installer_selected_package"] = "org.example.installer"
        }
        val saved = preferences.data.toMap()
        val c16 = HookPreferenceSnapshot { SystemAdaptationVersion.ColorOs16 }
        val c17 = HookPreferenceSnapshot { SystemAdaptationVersion.ColorOs17 }
        try {
            assertTrue(c16.attach(preferences))
            assertEquals(0, c16.int("assistant_power_mode", 0))
            assertEquals(1f, c16.float("unknown_float", 1f))
            assertEquals(0L, c16.long("unknown_long", 0L))
            assertEquals("", c16.string("unknown_text", ""))
            assertEquals(emptySet<String>(), c16.stringSet("unknown_set", emptySet()))
            assertEquals(42, c16.int("recent_task_radius_dp", 0))
            assertEquals(0xFF123456L, c16.long("systemui_qs_slider_percentage_color", -1L))
            assertEquals("org.example.installer", c16.string("installer_selected_package", ""))
            assertEquals(saved, preferences.data)
            assertTrue(c17.attach(preferences))
            assertEquals(3, c17.int("assistant_power_mode", 0))
            assertEquals("saved", c17.string("unknown_text", ""))
            assertEquals(saved, preferences.data)
        } finally {
            assertTrue(c16.detach())
            assertTrue(c17.detach())
        }
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
