package com.mi.onextbox.lsp

import com.mi.onextbox.ui.common.LspHomeDisplayCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class LspHomeDisplayPolicyTest {
    @Test
    fun everyCompletedStatusCanReplaceTheDisplayCache() {
        LspStatus.entries.filter { it != LspStatus.CHECKING }.forEach { status ->
            val snapshot = LsposedScopeRequester.StatusSnapshot(
                status = status,
                moduleState = if (status == LspStatus.DISABLED) LspModuleState.DISABLED
                    else LspModuleState.ENABLED,
                reason = "completed",
            )
            assertEquals(status, lspHomeDisplayForCache(snapshot, true)?.status)
        }
    }

    @Test
    fun initialAndRefreshingResultsDoNotOverwriteTheLastDisplay() {
        assertNull(lspHomeDisplayForCache(LsposedScopeRequester.StatusSnapshot(), true))
        assertNull(lspHomeDisplayForCache(
            LsposedScopeRequester.StatusSnapshot(
                moduleState = LspModuleState.ENABLED, status = LspStatus.READY,
                isRefreshing = true,
            ), false,
        ))
    }

    @Test
    fun rootBootstrapUnknownIsSkippedButAConfirmedRootFailureIsCached() {
        val unavailable = LsposedScopeRequester.StatusSnapshot(
            status = LspStatus.UNKNOWN, reason = "root_shell_unavailable",
        )
        assertNull(lspHomeDisplayForCache(unavailable, true))
        assertEquals(LspStatus.UNKNOWN, lspHomeDisplayForCache(unavailable, false)?.status)
    }

    @Test
    fun actualProbeFailuresReplaceAnOldReadyDisplay() {
        listOf("config_unavailable", "probe_failed", "scope_unavailable", "targets_unavailable").forEach {
            assertEquals(LspStatus.UNKNOWN, lspHomeDisplayForCache(
                LsposedScopeRequester.StatusSnapshot(status = LspStatus.UNKNOWN, reason = it), true,
            )?.status)
        }
    }

    @Test
    fun missingScopeDetailsAndFrameworkVersionArePreservedTogether() {
        val missing = mutableSetOf("com.android.systemui", "com.android.settings")
        val display = lspHomeDisplayForCache(
            LsposedScopeRequester.StatusSnapshot(
                moduleState = LspModuleState.ENABLED,
                status = LspStatus.MISSING_SCOPE,
                frameworkVersionText = "LSPosed 1.10.2 (7292) / API 102",
                missingScopes = missing,
            ), false,
        )!!
        missing.clear()
        assertEquals(setOf("com.android.systemui", "com.android.settings"), display.missingScopes)
        assertEquals("LSPosed 1.10.2 (7292) / API 102", display.frameworkVersionText)
    }

    @Test
    fun cachedDisplayNeverMakesTheLiveSnapshotActivated() {
        val live = LsposedScopeRequester.StatusSnapshot()
        val previous = LsposedScopeRequester.StatusSnapshot(
            moduleState = LspModuleState.ENABLED, status = LspStatus.READY,
        )
        assertEquals(LspStatus.READY, lspHomeDisplayForCache(previous, false)?.status)
        assertFalse(live.moduleEnabled)
        assertFalse(live.isReady)
        assertFalse(live.canContinue)
        assertNull(lspHomeDisplayForCache(live.copy(status = LspStatus.READY), false))
    }

    @Test
    fun loadedOldTargetCachesReadyDisplayAndVersionForTheNextColdStart() {
        val live = LsposedScopeRequester.StatusSnapshot(
            moduleState = LspModuleState.ENABLED,
            status = LspStatus.WAITING_RESTART,
            serviceConnected = true,
            frameworkVersionText = "LSPosed 2.2.0-it (7899) / API 102",
            verifiedScopes = setOf("system", "com.android.systemui"),
            reason = "target_stale",
        )
        val display = lspHomeDisplayForCache(live, false)!!
        assertEquals(LspStatus.READY, display.status)
        assertEquals(live.frameworkVersionText, display.frameworkVersionText)
        val restored = LspHomeDisplayCodec.decode(
            LspHomeDisplayCodec.SCHEMA_VERSION, display.status.name,
            display.frameworkVersionText, display.missingScopes,
        )
        assertEquals(display, restored)
        assertEquals(LspStatus.WAITING_RESTART, live.status)
        assertFalse(live.isReady)
        assertNull(lspHomeDisplayForCache(live.copy(isRefreshing = true), false))
    }

    @Test
    fun genuineRestartRequirementsRemainInTheCache() {
        listOf("targets_not_loaded", "system_not_loaded", "systemui_not_loaded").forEach { reason ->
            val snapshot = LsposedScopeRequester.StatusSnapshot(
                moduleState = LspModuleState.ENABLED,
                status = LspStatus.WAITING_RESTART,
                serviceConnected = true,
                verifiedScopes = setOf("system", "com.android.systemui"),
                reason = reason,
            )
            assertEquals(LspStatus.WAITING_RESTART, lspHomeDisplayForCache(snapshot, false)?.status)
        }
    }
}
