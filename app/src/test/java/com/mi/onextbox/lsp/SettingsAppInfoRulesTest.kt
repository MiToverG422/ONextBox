package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class SettingsAppInfoRulesTest {
    @Test fun includesVersionNameAndLongVersionCodeWithoutTruncating() {
        assertEquals("1.2 (4294967297)", SettingsAppInfoRules.version("1.2", 4294967297L, "unknown"))
        assertEquals("0", SettingsAppInfoRules.version(null, 0, "unknown"))
        assertEquals("123", SettingsAppInfoRules.version(" ", 123, "unknown"))
        assertEquals("unknown", SettingsAppInfoRules.version(null, -1, "unknown"))
    }

    @Test fun unknownInstallerDoesNotBecomeAdbOrSystem() {
        assertEquals("unknown", SettingsAppInfoRules.installer(null, null, "unknown"))
        assertEquals("unknown", SettingsAppInfoRules.installer(" ", "System", "unknown"))
        assertEquals("org.example.removed", SettingsAppInfoRules.installer("org.example.removed", null, "unknown"))
        assertEquals("InstallerX\norg.example.installer", SettingsAppInfoRules.installer("org.example.installer", "InstallerX", "unknown"))
        assertEquals("com.android.shell", SettingsAppInfoRules.installer("com.android.shell", "com.android.shell", "unknown"))
    }

    @Test fun preservesRealTimesAndUsesTheDeviceTimeZone() {
        val time = 86_400_000L
        val pattern = "yyyy-MM-dd HH:mm:ss"
        assertEquals("1970-01-02 08:00:00", SettingsAppInfoRules.timestamp(time, pattern, Locale.US, TimeZone.getTimeZone("GMT+08:00"), "unknown"))
        assertEquals("1970-01-02 00:00:00", SettingsAppInfoRules.timestamp(time, pattern, Locale.US, TimeZone.getTimeZone("UTC"), "unknown"))
        assertEquals("unknown", SettingsAppInfoRules.timestamp(0, pattern, Locale.US, TimeZone.getTimeZone("UTC"), "unknown"))
        assertEquals("unknown", SettingsAppInfoRules.timestamp(-1, pattern, Locale.US, TimeZone.getTimeZone("UTC"), "unknown"))
    }

    @Test fun insertingCardOnlyShiftsFollowingPreferencesAndAvoidsOverflow() {
        assertEquals(0, SettingsAppInfoRules.shiftedOrder(0, 2))
        assertEquals(1, SettingsAppInfoRules.shiftedOrder(1, 2))
        assertEquals(3, SettingsAppInfoRules.shiftedOrder(2, 2))
        assertEquals(8, SettingsAppInfoRules.shiftedOrder(7, 2))
        assertEquals(Int.MAX_VALUE, SettingsAppInfoRules.shiftedOrder(Int.MAX_VALUE, 2))
    }

    @Test fun exposesExactlyTheSixRequestedFields() {
        assertEquals(6, SettingsAppInfoRules.Field.entries.size)
        assertEquals(SettingsAppInfoRules.Field.PackageName, SettingsAppInfoRules.Field.entries.first())
        assertEquals(SettingsAppInfoRules.Field.Installer, SettingsAppInfoRules.Field.entries.last())
    }

    @Test fun titleDividerFollowsNativeBodyHeightInBothDirections() {
        assertEquals(0f, SettingsAppInfoRules.titleDividerOpacity(true, true, true, 0, 1000), 0f)
        assertEquals(0.25f, SettingsAppInfoRules.titleDividerOpacity(true, true, true, 250, 1000), 0f)
        assertEquals(0.25f, SettingsAppInfoRules.titleDividerOpacity(true, false, false, 250, 1000), 0f)
        assertEquals(1f, SettingsAppInfoRules.titleDividerOpacity(true, true, true, 1000, 1000), 0f)
    }

    @Test fun titleDividerDoesNotJumpWhenNativeAnimationReverses() {
        val opening = SettingsAppInfoRules.titleDividerOpacity(true, true, true, 650, 1000)
        val closing = SettingsAppInfoRules.titleDividerOpacity(true, false, false, 650, 1000)
        assertEquals(opening, closing, 0f)
        assertEquals(0.65f, closing, 0f)
    }

    @Test fun titleDividerHandlesStableAndUnmeasuredNativeStates() {
        assertEquals(1f, SettingsAppInfoRules.titleDividerOpacity(false, true, false, -1, -1), 0f)
        assertEquals(0f, SettingsAppInfoRules.titleDividerOpacity(false, false, true, 1000, 1000), 0f)
        assertEquals(0f, SettingsAppInfoRules.titleDividerOpacity(true, true, true, -1, -1), 0f)
        assertEquals(1f, SettingsAppInfoRules.titleDividerOpacity(true, false, false, -1, 0), 0f)
        assertEquals(1f, SettingsAppInfoRules.titleDividerOpacity(true, true, true, 2000, 1000), 0f)
    }
}
