package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class SettingsHomeCleanupRulesTest {
    @Test fun onlyKnownOtaPackagesAndTheNativeOtaEntranceAreMatched() {
        assertTrue(SettingsHomeCleanupRules.isUpdateReminder("com.oplus.ota", null, null))
        assertTrue(SettingsHomeCleanupRules.isUpdateReminder(null, "com.coloros.ota", null))
        assertTrue(SettingsHomeCleanupRules.isUpdateReminder("com.coloros.operationManual", null, "ota_settings"))
        assertFalse(SettingsHomeCleanupRules.isUpdateReminder("com.oplus.ota.other", "com.example.ota", "ota_settings_other"))
        assertFalse(SettingsHomeCleanupRules.isUpdateReminder(null, null, null))
        assertFalse(SettingsHomeCleanupRules.isUpdateReminder("com.coloros.operationManual", null, "user_center"))
    }

    @Test fun filteringKeepsOtherRecommendationsAndTheirOriginalOrderWithoutMutatingTheSource() {
        val original = arrayListOf("tip", "ota", "account", "support")
        val filtered = SettingsHomeCleanupRules.filterUpdates(original) { it == "ota" }
        assertEquals(listOf("tip", "account", "support"), filtered)
        assertEquals(listOf("tip", "ota", "account", "support"), original)
        assertNotSame(original, filtered)
    }

    @Test fun unchangedUnknownAndEmptyListsKeepTheirNativeIdentity() {
        val entries = listOf("unknown", null, "tip")
        assertSame(entries, SettingsHomeCleanupRules.filterUpdates(entries) { it == "ota" })
        val empty = emptyList<Any>()
        assertSame(empty, SettingsHomeCleanupRules.filterUpdates(empty) { true })
    }

    @Test fun anUpdateOnlyBannerBecomesEmpty() {
        assertTrue(SettingsHomeCleanupRules.filterUpdates(listOf("ota")) { true }.isEmpty())
    }

    @Test fun anAccountOnlyCategoryCanDisappearWithoutADeadSpacer() {
        val account = SettingsHomeCleanupRules.accountKeys.map { it to true }
        assertTrue(SettingsHomeCleanupRules.shouldHideAccountCategory(account))
        assertTrue(SettingsHomeCleanupRules.shouldHideAccountCategory(emptyList()))
    }

    @Test fun realmeAppUpdaterAndUnknownNativeSiblingsArePreserved() {
        val account = SettingsHomeCleanupRules.accountKeys.map { it to true }
        assertFalse(SettingsHomeCleanupRules.shouldHideAccountCategory(account + ("top_level_app_updater" to true)))
        assertTrue(SettingsHomeCleanupRules.shouldHideAccountCategory(account + ("top_level_app_updater" to false)))
        assertFalse(SettingsHomeCleanupRules.shouldHideAccountCategory(account + (null to true)))
    }

    @Test fun visibilityOffRestoresTheGenuineNativeState() {
        val visible = SettingsHomeVisibility(true)
        assertFalse(visible.displayed(hidden = true))
        assertTrue(visible.displayed(hidden = false))
        val unavailable = SettingsHomeVisibility(false)
        assertFalse(unavailable.displayed(hidden = false))
    }

    @Test fun asynchronousNativeVisibilityRequestsCannotReshowHiddenAccounts() {
        val state = SettingsHomeVisibility(true)
        assertFalse(state.requested(visible = true, hidden = true))
        assertTrue(state.nativeVisible)
        assertFalse(state.requested(visible = false, hidden = true))
        assertFalse(state.displayed(hidden = false))
        assertTrue(state.requested(visible = true, hidden = false))
    }

    private class Owner(@JvmField var messages: List<String>)
    private val field = Owner::class.java.getDeclaredField("messages").apply { isAccessible = true }

    @Test fun presentationOverrideRestoresTheRawEntriesSoSwitchingOffNeedsNoNewQuery() {
        val raw = listOf("ota", "tip")
        val display = listOf("tip")
        val owner = Owner(raw)
        val result = SettingsHomeCleanupRules.withDisplayMessages(owner, field, display) {
            assertSame(display, owner.messages)
            "result"
        }
        assertEquals("result", result)
        assertSame(raw, owner.messages)
    }

    @Test fun nativeExceptionsStillRestoreTheRawEntriesAndAreNotRetried() {
        val raw = listOf("ota", "tip")
        val owner = Owner(raw)
        var calls = 0
        val error = IllegalStateException("native")
        try {
            SettingsHomeCleanupRules.withDisplayMessages(owner, field, listOf("tip")) { calls++; throw error }
            fail("Expected the original exception")
        } catch (caught: IllegalStateException) { assertSame(error, caught) }
        assertEquals(1, calls)
        assertSame(raw, owner.messages)
    }

    @Test fun nestedNativeRefreshesRestoreTheirOwnFrames() {
        val raw = listOf("ota", "tip")
        val display = listOf("tip")
        val owner = Owner(raw)
        SettingsHomeCleanupRules.withDisplayMessages(owner, field, display) {
            SettingsHomeCleanupRules.withDisplayMessages(owner, field, display) { assertSame(display, owner.messages) }
            assertSame(display, owner.messages)
        }
        assertSame(raw, owner.messages)
    }

    @Test fun nativeReplacementDuringARefreshWinsOverTheOldSnapshot() {
        val owner = Owner(listOf("ota", "tip"))
        val newer = listOf("new native entry")
        SettingsHomeCleanupRules.withDisplayMessages(owner, field, listOf("tip")) { owner.messages = newer }
        assertSame(newer, owner.messages)
    }
}
