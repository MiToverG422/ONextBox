package com.mi.onextbox.lsp

import org.junit.Assert.assertEquals
import org.junit.Test

class SoftLightScopeMaskTest {
    @Test fun onlyAnExplicitLiquidStyleOptsExistingProfilesIntoTheLens() {
        val settings = SoftLightGlassSettings
        for (stored in listOf(null, -1, 0, 3)) assertEquals(settings.SOFT, settings.style(stored))
        assertEquals(settings.REFRACTION, settings.style(1))
        assertEquals(settings.LIQUID, settings.style(2))
        org.junit.Assert.assertTrue(settings.STYLE in settings.keys)
    }

    @Test fun liquidGlassControlsHaveIndependentKeysAndValidPluginDefaults() {
        val entries = LiquidGlassTuning.Surface.entries
        val controls = LiquidGlassTuning.Control.entries
        val keys = entries.flatMap { surface ->
            controls.map { c ->
                org.junit.Assert.assertTrue(surface.default(c) in c.range)
                surface.key(c)
            } + listOf(surface.modeKey, surface.colorKey)
        }
        assertEquals(keys.size, keys.distinct().size)
        org.junit.Assert.assertTrue(SoftLightGlassSettings.keys.containsAll(keys))
        assertEquals(55, LiquidGlassTuning.Surface.CONTROL.default(LiquidGlassTuning.Control.SPECULAR))
        assertEquals(30, LiquidGlassTuning.Surface.DOCK.default(LiquidGlassTuning.Control.SPECULAR))
    }

    private val independentSearch = 1 shl 30

    @Test fun unifiedOpticsPreserveIndividualSettingsAcrossModeChanges() {
        val control = LiquidGlassTuning.Control.BLUR
        val dock = LiquidGlassTuning.Surface.DOCK
        val panel = LiquidGlassTuning.Surface.CONTROL
        val prefs = mutableMapOf(dock.key(control) to 31, panel.key(control) to 72,
            LiquidGlassTuning.Surface.ALL.key(control) to 143)
        fun load() = LiquidGlassTuning.Parameters { key, fallback -> prefs[key] ?: fallback }
        assertEquals(3.1f, load().blur(dock), .001f)
        prefs[LiquidGlassTuning.UNIFIED] = 1
        val shared = load()
        LiquidGlassTuning.surfaces.forEach { assertEquals(14.3f, shared.blur(it), .001f) }
        prefs[LiquidGlassTuning.UNIFIED] = 0
        val individual = load()
        assertEquals(3.1f, individual.blur(dock), .001f)
        assertEquals(7.2f, individual.blur(panel), .001f)
    }
    private val independentLock = 1 shl 29
    private val independentDock = 1 shl 28
    private val lock = SoftLightGlassSettings.Scene.LOCK.bit
    private val aod = SoftLightGlassSettings.Scene.AOD.bit
    private val bar = SoftLightGlassSettings.Scene.BAR.bit
    private val dock = SoftLightGlassSettings.Scene.DOCK.bit
    private val search = SoftLightGlassSettings.Scene.GLOBAL_SEARCH.bit

    @Test fun oldCombinedLockScopeRetainsBothSurfaces() {
        assertEquals(lock or aod, SoftLightGlassSettings.selectionMask(lock))
        assertEquals(bar or dock or search or lock or aod, SoftLightGlassSettings.selectionMask(bar or lock))
    }

    @Test fun newLockAndAodSelectionsAreIndependent() {
        val version = independentSearch or independentLock
        assertEquals(lock, SoftLightGlassSettings.selectionMask(version or lock))
        assertEquals(aod, SoftLightGlassSettings.selectionMask(version or aod))
        assertEquals(0, SoftLightGlassSettings.selectionMask(version))
    }

    @Test fun oldSearchMigrationStillRetainsTheLockScope() {
        assertEquals(bar or dock or lock or aod, SoftLightGlassSettings.selectionMask(independentSearch or bar or lock))
    }

    @Test fun previousCombinedBarIncludesDockWithoutEnablingGlobalSearch() {
        assertEquals(bar or dock, SoftLightGlassSettings.selectionMask(independentSearch or independentLock or bar))
    }

    @Test fun newDockAndSearchBarSelectionsAreIndependent() {
        val flags = independentSearch or independentLock or independentDock
        for (selection in listOf(0, dock, bar, search, bar or dock)) {
            assertEquals(selection, SoftLightGlassSettings.selectionMask(flags or selection))
        }
    }

    @Test fun normalizedSelectionCanBeSavedWithoutReenablingDisabledScopes() {
        val old = SoftLightGlassSettings.selectionMask(independentSearch or lock)
        val updated = (old and lock.inv()) or independentSearch or independentLock
        assertEquals(aod, SoftLightGlassSettings.selectionMask(updated))
    }
}
