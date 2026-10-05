package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.LspConfig.NotificationRemovalFeature as Feature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationRemovalRulesTest {
    @Test fun matchesOnlySelectedSystemNotificationIdentities() {
        val examples = listOf(
            Triple("INS", 10002, Feature.DeveloperMode),
            Triple("FLA", 10011, Feature.Flashlight),
            Triple("FLA", 10012, Feature.Flashlight),
            Triple("channel_dnd_notice", 10001, Feature.DoNotDisturb),
            Triple("BLOCK_BANNER", 10008, Feature.MuteNotifications),
            Triple("gt_mode_channel_id_2.0", 6, Feature.GtMode),
        )
        examples.forEach { (channel, id, feature) ->
            assertEquals(feature, NotificationRemovalRules.match("com.android.systemui", channel, id))
            assertNull(NotificationRemovalRules.match("third.party.app", channel, id))
            assertNull(NotificationRemovalRules.match("com.android.systemui", channel, id + 100))
        }
    }

    @Test fun batteryAndHotspotFiltersDoNotHideOtherWarnings() {
        assertEquals(Feature.HighPerformance, NotificationRemovalRules.match(
            "com.oplus.battery", "high_performance_channel_id", 5))
        assertEquals(Feature.HighBatteryConsumption, NotificationRemovalRules.match(
            "com.oplus.battery", "PowerConsumptionOptimizationChannel", 17))
        assertEquals(Feature.HighBatteryConsumption, NotificationRemovalRules.match(
            "com.coloros.phonemanager", "PowerConsumptionOptimizationChannelLow", 17))
        assertEquals(Feature.HotspotPowerConsumption, NotificationRemovalRules.match(
            "android", "DurationNotification", 4))
        listOf("BATTERY", "battery", "bms_heat_active", "NETWORK_STATUS", "TetherNotification", "VPN")
            .forEach { channel ->
                listOf("android", "com.android.systemui", "com.oplus.battery").forEach { pkg ->
                    assertNull(NotificationRemovalRules.match(pkg, channel, 10006))
                }
            }
        assertNull(NotificationRemovalRules.match("com.oplus.battery", null, 5))
        assertNull(NotificationRemovalRules.match("third.party.app", "high_performance_channel_id", 5))
    }
}
