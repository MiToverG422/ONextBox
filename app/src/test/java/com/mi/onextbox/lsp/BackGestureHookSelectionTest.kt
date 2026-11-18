package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class BackGestureHookSelectionTest {
    private val colorOs = BackGestureSettings.State(aosp = false, haptics = 1, predictive = 0)

    @Test fun defaultAndExplicitColorOsSelectionsAreInactive() {
        for (state in listOf(BackGestureSettings.State(), colorOs)) {
            assertEquals(1, state.selectedStyle)
            assertEquals(1, state.selectedHaptics)
            assertFalse(state.active)
            assertFalse(state.hapticHooks)
            assertFalse(state.gestureHooks)
            assertFalse(state.predictiveHooks)
        }
    }

    @Test fun selectingColorOsHapticsDoesNotKeepTheAdaptersActive() {
        val aosp = colorOs.copy(haptics = 0)
        assertTrue(aosp.active)
        assertTrue(aosp.hapticHooks)
        assertFalse(aosp.gestureHooks)
        assertFalse(aosp.predictiveHooks)
        assertFalse(aosp.copy(haptics = 1).active)
    }

    @Test fun eachAospSelectionCanBeDisabledWithoutChangingTheOtherChoices() {
        val all = colorOs.copy(aosp = true, haptics = 0, predictive = 1)
        val indicatorOff = all.copy(aosp = false)
        assertEquals(1, indicatorOff.selectedStyle)
        assertTrue(indicatorOff.hapticHooks)
        assertTrue(indicatorOff.predictiveHooks)
        val feedbackOff = all.copy(haptics = 1)
        assertTrue(feedbackOff.aosp)
        assertFalse(feedbackOff.hapticHooks)
        assertTrue(feedbackOff.predictiveHooks)
        val predictionOff = all.copy(predictive = 0)
        assertTrue(predictionOff.aosp)
        assertTrue(predictionOff.hapticHooks)
        assertFalse(predictionOff.predictiveHooks)
        assertFalse(all.copy(aosp = false, haptics = 1, predictive = 0).active)
    }

    @Test fun predictiveStyleAloneDoesNotRequireGestureOrHapticAdapters() {
        val state = colorOs.copy(predictive = 1)
        assertTrue(state.active)
        assertTrue(state.predictiveHooks)
        assertFalse(state.gestureHooks)
        assertFalse(state.hapticHooks)
    }

    @Test fun areaAndReleaseFeaturesKeepOnlyTheirOwnRouting() {
        for (state in listOf(colorOs.copy(area = true), colorOs.copy(release = true),
                colorOs.copy(area = true, release = true))) {
            assertTrue(state.active)
            assertTrue(state.gestureHooks)
            assertFalse(state.hapticHooks)
            assertFalse(state.predictiveHooks)
        }
    }

    @Test fun unsetHapticsContinueToFollowTheIndicatorForExistingConfigurations() {
        assertFalse(BackGestureSettings.State().hapticHooks)
        assertTrue(BackGestureSettings.State(aosp = true).hapticHooks)
        assertFalse(BackGestureSettings.State(aosp = true, haptics = 1).hapticHooks)
        assertTrue(BackGestureSettings.State(aosp = false, haptics = 0).hapticHooks)
    }

    @Test fun colorOsEntrypointsDoNotResolveHostClasses() {
        val loader = RejectingLoader()
        AospBackPanelHooker.hook(loader, enabled = false)
        BackGestureHooker.hook(loader, colorOs)
        BackGesturePrediction.hook(loader, enabled = false)
        LauncherHomePredictionHooker.hook(loader, enabled = false)
        assertEquals(emptyList<String>(), loader.requested)
    }

    @Test fun aospIndicatorWithColorOsFeedbackDoesNotInstallOtherAdapters() {
        val loader = RejectingLoader()
        BackGestureHooker.hook(loader, colorOs.copy(aosp = true))
        assertEquals(emptyList<String>(), loader.requested)
    }

    private class RejectingLoader : ClassLoader(null) {
        val requested = arrayListOf<String>()
        override fun loadClass(name: String): Class<*> {
            requested += name
            throw ClassNotFoundException(name)
        }
    }
}
