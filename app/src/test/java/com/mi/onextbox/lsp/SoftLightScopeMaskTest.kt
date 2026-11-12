package com.mi.onextbox.lsp

import org.junit.Assert.assertEquals
import org.junit.Test

class SoftLightScopeMaskTest {
    private val independentSearch = 1 shl 30
    private val independentLock = 1 shl 29
    private val lock = SoftLightGlassSettings.Scene.LOCK.bit
    private val aod = SoftLightGlassSettings.Scene.AOD.bit
    private val bar = SoftLightGlassSettings.Scene.BAR.bit
    private val search = SoftLightGlassSettings.Scene.GLOBAL_SEARCH.bit

    @Test fun oldCombinedLockScopeRetainsBothSurfaces() {
        assertEquals(lock or aod, SoftLightGlassSettings.selectionMask(lock))
        assertEquals(bar or search or lock or aod, SoftLightGlassSettings.selectionMask(bar or lock))
    }

    @Test fun newLockAndAodSelectionsAreIndependent() {
        val version = independentSearch or independentLock
        assertEquals(lock, SoftLightGlassSettings.selectionMask(version or lock))
        assertEquals(aod, SoftLightGlassSettings.selectionMask(version or aod))
        assertEquals(0, SoftLightGlassSettings.selectionMask(version))
    }

    @Test fun oldSearchMigrationStillRetainsTheLockScope() {
        assertEquals(bar or lock or aod, SoftLightGlassSettings.selectionMask(independentSearch or bar or lock))
    }

    @Test fun normalizedSelectionCanBeSavedWithoutReenablingDisabledScopes() {
        val old = SoftLightGlassSettings.selectionMask(independentSearch or lock)
        val updated = (old and lock.inv()) or independentSearch or independentLock
        assertEquals(aod, SoftLightGlassSettings.selectionMask(updated))
    }
}
