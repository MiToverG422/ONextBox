package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class SettingsRealmeFaceModeTest {
    @Test fun liveNativeControllersKeepTheModeTheyWereCreatedWith() {
        val mode = SettingsRealmeFaceMode()
        val oldPage = Any()
        mode.acquire(oldPage, false)
        assertFalse(mode.forced(true))
        mode.acquire(oldPage, true)
        assertFalse(mode.forced(true))
        mode.release(oldPage)
        assertTrue(mode.forced(true))
        val newPage = Any()
        mode.acquire(newPage, true)
        assertTrue(mode.forced(false))
        mode.release(newPage)
        assertFalse(mode.forced(false))
    }

    @Test fun nestedPagesShareOneNativeModeAndReleaseIndependently() {
        val mode = SettingsRealmeFaceMode()
        val first = Any()
        val second = Any()
        mode.acquire(first, true)
        mode.acquire(second, false)
        mode.release(first)
        assertTrue(mode.forced(false))
        mode.release(second)
        assertFalse(mode.forced(false))
    }

    @Test fun pageCreatedBeforeHookUsesItsExistingUiWithoutChangingAlreadyPinnedPages() {
        val mode = SettingsRealmeFaceMode()
        val oldPage = Any()
        mode.acquireExisting(oldPage) { false }
        assertFalse(mode.forced(true))
        mode.acquireExisting(oldPage) { error("Existing page mode must not be re-evaluated") }
        assertFalse(mode.forced(true))
        mode.release(oldPage)
        val newPage = Any()
        mode.acquire(newPage, true)
        mode.acquireExisting(newPage) { error("Fresh controller mode is already pinned") }
        assertTrue(mode.forced(false))
    }

    @Test fun hotReloadAndRollbackPreserveControllerMode() {
        val old = SettingsRealmeFaceMode()
        val page = Any()
        old.acquire(page, true)
        val state = old.capture()
        old.clear()
        val replacement = SettingsRealmeFaceMode()
        replacement.restore(state)
        assertTrue(replacement.forced(false))
        replacement.release(page)
        assertFalse(replacement.forced(false))
        old.restore(state)
        assertTrue(old.forced(false))
    }
}
