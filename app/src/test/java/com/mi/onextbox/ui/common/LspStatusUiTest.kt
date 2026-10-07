package com.mi.onextbox.ui.common

import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LspStatusUiTest {
    @Test
    fun homepageWaitsForRootStartupInsteadOfShowingTemporaryUnknown() {
        assertEquals(
            LspStatus.CHECKING,
            homeLspDisplayStatus(LspStatus.UNKNOWN, "root_shell_unavailable", true),
        )
    }

    @Test
    fun homepageKeepsActualUnknownReasonsDuringRootStartup() {
        listOf("db_unreadable", "scopes_unavailable", "", "checking").forEach { reason ->
            assertEquals(LspStatus.UNKNOWN, homeLspDisplayStatus(LspStatus.UNKNOWN, reason, true))
        }
    }

    @Test
    fun homepageShowsRootShellUnknownAfterStartupCompletes() {
        assertEquals(
            LspStatus.UNKNOWN,
            homeLspDisplayStatus(LspStatus.UNKNOWN, "root_shell_unavailable", false),
        )
    }

    @Test
    fun homepageDoesNotHideVerifiedOrOtherPendingStatesDuringRootStartup() {
        LspStatus.entries.filter { it != LspStatus.UNKNOWN }.forEach { status ->
            assertEquals(status, homeLspDisplayStatus(status, "root_shell_unavailable", true))
        }
    }

    @Test
    fun homepageTemporaryRootUnknownUsesExistingCheckingPlaceholder() {
        val displayStatus = homeLspDisplayStatus(LspStatus.UNKNOWN, "root_shell_unavailable", true)
        assertEquals("—", formatLspStatusText(displayStatus, "Checking", showChecking = false))
    }

    @Test
    fun unknownAndPendingStatesDoNotSayDisabled() {
        listOf(
            LspStatus.CHECKING,
            LspStatus.UNKNOWN,
            LspStatus.WAITING_CONNECTION,
            LspStatus.MISSING_SCOPE,
            LspStatus.WAITING_RESTART,
            LspStatus.API_UNSUPPORTED,
        ).forEach { status ->
            assertNotEquals(R.string.lsp_status_module_disabled, lspStatusTextResource(status))
            assertNotEquals(R.string.lsp_status_ready, lspStatusTextResource(status))
        }
    }

    @Test
    fun everyStatusHasItsOwnDescription() {
        assertEquals(LspStatus.entries.size, LspStatus.entries.map(::lspStatusTextResource).toSet().size)
    }

    @Test
    fun verifiedStatesUseTheirExplicitDescriptions() {
        assertEquals(R.string.lsp_status_module_disabled, lspStatusTextResource(LspStatus.DISABLED))
        assertEquals(R.string.lsp_status_ready, lspStatusTextResource(LspStatus.READY))
    }

    @Test
    fun readyShowsOnlyTrimmedLiveVersion() {
        assertEquals(
            "LSPosed 1.10.2-7292 (7292) / API 102",
            formatLspStatusText(
                status = LspStatus.READY,
                label = "Ready",
                frameworkVersionText = "  LSPosed 1.10.2-7292 (7292) / API 102 \n",
            ),
        )
    }

    @Test
    fun readyWithoutLiveVersionKeepsLocalizedStatus() {
        listOf(null, "", "  \n\t").forEach { version ->
            assertEquals("Ready", formatLspStatusText(LspStatus.READY, "Ready", version))
        }
    }

    @Test
    fun pendingOrDisabledStatesDoNotShowVersionInsteadOfStatus() {
        LspStatus.entries.filter { it != LspStatus.READY }.forEach { status ->
            assertEquals(
                "Status: $status",
                formatLspStatusText(status, "Status: $status", "LSPosed 1.10.2 / API 102"),
            )
        }
    }

    @Test
    fun homepageCheckingPlaceholderDoesNotAffectOtherStatuses() {
        assertEquals(
            "—",
            formatLspStatusText(LspStatus.CHECKING, "Checking", "LSPosed 1.10.2 / API 102",
                showChecking = false),
        )
        assertEquals(
            "Checking",
            formatLspStatusText(LspStatus.CHECKING, "Checking", showChecking = true),
        )
        assertEquals(
            "Missing scope",
            formatLspStatusText(LspStatus.MISSING_SCOPE, "Missing scope", showChecking = false),
        )
    }

    @Test
    fun missingScopeNoticeCannotReuseOldEvidenceWhileChecking() {
        val missing = setOf("com.android.systemui")
        LspStatus.entries.filter { it != LspStatus.MISSING_SCOPE }.forEach { status ->
            assertFalse(shouldShowLspMissingScopes(status, missing))
        }
        assertFalse(shouldShowLspMissingScopes(LspStatus.MISSING_SCOPE, emptySet()))
        assertTrue(shouldShowLspMissingScopes(LspStatus.MISSING_SCOPE, missing))
    }

    @Test
    fun missingScopeOrderKeepsSystemAndAndroidDistinct() {
        assertEquals(
            listOf("system", "android", "com.android.systemui", "com.example.a", "com.example.z"),
            orderedLspMissingScopes(setOf("com.example.z", "android", "com.android.systemui",
                "com.example.a", "system")),
        )
    }
}
