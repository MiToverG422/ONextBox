package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class OverviewPersistenceRulesTest {
    private fun capsule(
        enabled: Boolean,
        statusBar: Boolean,
        original: Int,
        allowed: Boolean = false,
        canShow: Boolean = true,
        mini: Boolean = false,
        privacy: Boolean = false,
        native: Boolean = false,
    ) = FluidCloudRecentsRules.keepCapsuleVisible(enabled, original, canShow, mini, privacy, allowed, statusBar, native)

    @Test fun theFourSwitchCombinationsRemainIndependent() {
        assertEquals(4, capsule(false, false, 4))
        assertEquals(0, capsule(true, false, 4))
        assertEquals(4, capsule(false, true, 0))
        assertEquals(0, capsule(true, true, 0))
    }

    @Test fun bothDisabledNeverChangeAnyNativeVisibility() {
        for (original in listOf(0, 4, 8)) {
            assertEquals(original, capsule(false, false, original))
        }
    }

    @Test fun nativeChipsPrivacyAndMiniCapsulesKeepTheirOwnVisibility() {
        for (original in listOf(0, 4, 8)) for (statusBar in listOf(false, true)) {
            assertEquals(original, capsule(true, statusBar, original, native = true))
            assertEquals(original, capsule(true, statusBar, original, canShow = false))
            assertEquals(original, capsule(true, statusBar, original, mini = true))
            assertEquals(original, capsule(true, statusBar, original, privacy = true))
        }
    }

    @Test fun ordinaryStatusBarScenesAreUnchanged() {
        for (enabled in listOf(false, true)) for (statusBar in listOf(false, true)) {
            for (original in listOf(0, 4, 8)) assertEquals(original,
                capsule(enabled, statusBar, original, allowed = true))
        }
    }

    @Test fun aHiddenHomeStatusBarCannotBeForcedVisible() {
        assertFalse(StatusBarKeepRules.visible(true, false, false))
        assertFalse(StatusBarKeepRules.visible(true, false, true))
        assertFalse(StatusBarKeepRules.visible(false, true, false))
        assertTrue(StatusBarKeepRules.visible(true, true, false))
    }
}
