package com.mi.onextbox.lsp.compat

import com.mi.onextbox.lsp.SystemAdaptationVersion
import org.junit.Assert.*
import org.junit.Test

class AdaptationRuntimePolicyTest {
    @Test fun safetyGatePausesAndResumesWithoutChangingBoundProfile() {
        var enabled = false
        val gate = AdaptationProfileGate({ enabled }, { SystemAdaptationVersion.ColorOs16 })
        assertFalse(gate.isBoundProfileActive())
        val profile = gate.bind()
        assertFalse(gate.isActive(profile))
        enabled = true
        assertTrue(gate.isActive(profile))
        enabled = false
        assertFalse(gate.isBoundProfileActive())
        assertEquals(profile, gate.bind())
    }
    @Test fun colorOs16OnlyAllowsItsAdaptedPackagesWhileColorOs17KeepsItsExistingScopes() {
        val supported = setOf("com.android.launcher", "com.android.systemui", "com.android.settings",
            "com.coloros.phonemanager", "com.oplus.games", "com.oplus.screenrecorder", "system")
        val absent = setOf("com.coloros.gallery3d", "com.oplus.euicc", "com.oplus.keyboard",
            "com.google.android.googlequicksearchbox", "com.google.android.apps.messaging", "android", "unknown.app")
        for (packageName in supported + absent) {
            assertTrue(AdaptationRuntimePolicy.allowsPackage(SystemAdaptationVersion.ColorOs17, packageName))
            assertEquals(packageName in supported,
                AdaptationRuntimePolicy.allowsPackage(SystemAdaptationVersion.ColorOs16, packageName))
        }
        assertTrue(AdaptationRuntimePolicy.allowsSystemServer(SystemAdaptationVersion.ColorOs17))
        assertTrue(AdaptationRuntimePolicy.allowsSystemServer(SystemAdaptationVersion.ColorOs16))
    }

    @Test fun colorOs16AllowsVerifiedFeaturesAndRejectsAbsentOrUnverifiedCapabilities() {
        val adapted = setOf(
            "systemui_hide_notification_clear", "systemui_hide_rotation_suggestion",
            "systemui_hide_disconnected_bluetooth", "settings_force_google_entry",
            "settings_restore_app_open_button", "settings_international_enabled",
            "settings_force_app_auto_start",
            "systemui_qs_slider_percentage_enabled", "settings_international_wallet",
            "allow_secure_window_capture", "corepatch_downgrade", "small_window_landscape_ratio",
            "keyguard_screen_off_recording", "assistant_native_power_enabled",
        )
        val unadapted = setOf(
            "systemui_native_live_update", "corepatch_bypass_block",
            "settings_skip_special_permission_risk_confirm", "assistant_native_circle_enabled",
            "systemui_permanent_clock_seconds", "launcher_search_compatibility",
            "gallery_ai_retouch_enabled", "", "unknown_future_feature",
        )
        adapted.forEach {
            assertTrue(it, AdaptationRuntimePolicy.allowsFeatureBoolean(SystemAdaptationVersion.ColorOs16, it))
        }
        unadapted.forEach {
            assertFalse(it, AdaptationRuntimePolicy.allowsFeatureBoolean(SystemAdaptationVersion.ColorOs16, it))
        }
        (adapted + unadapted).forEach {
            assertTrue(it, AdaptationRuntimePolicy.allowsFeatureBoolean(SystemAdaptationVersion.ColorOs17, it))
        }
    }

    @Test fun numericAndTextConfigurationUsesTheSameCapabilityBoundary() {
        val c16 = SystemAdaptationVersion.ColorOs16
        assertTrue(AdaptationRuntimePolicy.allowsFeatureValue(c16, "recent_task_radius_dp"))
        assertTrue(AdaptationRuntimePolicy.allowsFeatureValue(c16, "systemui_qs_slider_percentage_color"))
        assertTrue(AdaptationRuntimePolicy.allowsFeatureValue(c16, "installer_selected_package"))
        assertFalse(AdaptationRuntimePolicy.allowsFeatureValue(c16, "assistant_power_mode"))
        assertFalse(AdaptationRuntimePolicy.allowsFeatureValue(c16, "unverified_future_mode"))
        assertTrue(AdaptationRuntimePolicy.allowsFeatureValue(SystemAdaptationVersion.ColorOs17, "unverified_future_mode"))
    }

    @Test fun profileChangesSuspendTheExistingGenerationWithoutRebindingItsHooks() {
        var selected = SystemAdaptationVersion.ColorOs17
        val generation = AdaptationProfileGate { selected }
        assertTrue(generation.isBoundProfileActive())
        assertEquals(SystemAdaptationVersion.ColorOs17, generation.bind())
        selected = SystemAdaptationVersion.ColorOs16
        assertFalse(generation.isBoundProfileActive())
        assertFalse(generation.isActive(SystemAdaptationVersion.ColorOs17))
        assertEquals(SystemAdaptationVersion.ColorOs17, generation.bind())

        val replacement = AdaptationProfileGate { selected }
        assertEquals(SystemAdaptationVersion.ColorOs16, replacement.bind())
        assertTrue(replacement.isBoundProfileActive())
        selected = SystemAdaptationVersion.ColorOs17
        assertTrue(generation.isBoundProfileActive())
        assertFalse(replacement.isBoundProfileActive())
    }
}
