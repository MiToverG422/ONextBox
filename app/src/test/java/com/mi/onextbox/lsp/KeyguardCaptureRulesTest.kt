package com.mi.onextbox.lsp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyguardCaptureRulesTest {
    @Test fun hardwareKeyCapturesAreAllowedOnlyOnTheDefaultAodDisplay() {
        listOf(3, 4).forEach { state ->
            assertTrue(KeyguardCaptureRules.allowAodScreenshot(true, "com.oplus.screenshot", "KeyPress", 0, state))
        }
    }

    @Test fun disabledAodScreenshotKeepsTheScreenOnGate() {
        assertFalse(KeyguardCaptureRules.allowAodScreenshot(false, "com.oplus.screenshot", "KeyPress", 0, 3))
    }

    @Test fun regularScreenOffAndAwakeStatesAreNotAod() {
        listOf(null, -1, 0, 1, 2, 5, 6).forEach { state ->
            assertFalse(KeyguardCaptureRules.allowAodScreenshot(true, "com.oplus.screenshot", "KeyPress", 0, state))
        }
    }

    @Test fun otherScreensAndUnknownDisplaysKeepStockHandling() {
        listOf(null, -1, 1, 2).forEach { display ->
            assertFalse(KeyguardCaptureRules.allowAodScreenshot(true, "com.oplus.screenshot", "KeyPress", display, 3))
        }
    }

    @Test fun otherScreenshotSourcesKeepStockHandling() {
        listOf(null, "", "ThreeFingers", "systemQuickTileScreenshotIn", "keyPress", "KeyPressExtra").forEach { source ->
            assertFalse(KeyguardCaptureRules.allowAodScreenshot(true, "com.oplus.screenshot", source, 0, 3))
        }
    }

    @Test fun otherPackagesKeepStockHandling() {
        listOf(null, "", "com.android.systemui", "com.example.screenshot", "com.oplus.screenshot.fake").forEach { pkg ->
            assertFalse(KeyguardCaptureRules.allowAodScreenshot(true, pkg, "KeyPress", 0, 3))
        }
    }

    @Test fun disabledFeaturesPreserveEveryStockRestriction() {
        assertFalse(KeyguardCaptureRules.allowProjectionStart(false, KeyguardCaptureRules.RECORDER_PACKAGE))
        assertFalse(KeyguardCaptureRules.allowProjectionToContinue(false, KeyguardCaptureRules.RECORDER_PACKAGE, 1))
        assertFalse(KeyguardCaptureRules.ignoreRecorderEvent(false, "POWER_OFF", KeyguardCaptureRules.SCREEN_OFF_ACTION))
        assertFalse(KeyguardCaptureRules.ignoreRecorderEvent(false, "SCREEN_OFF_TIME_OUT", null))
    }

    @Test fun projectionExceptionIsOnlyForTheStockRecorder() {
        assertTrue(KeyguardCaptureRules.allowProjectionStart(true, KeyguardCaptureRules.RECORDER_PACKAGE))
        listOf(null, "", "com.android.systemui", "com.example.recorder",
            "com.oplus.screenrecorder.fake").forEach {
            assertFalse(KeyguardCaptureRules.allowProjectionStart(true, it))
            assertFalse(KeyguardCaptureRules.allowProjectionToContinue(true, it, 1))
        }
    }

    @Test fun otherProjectionStopReasonsAreNeverSuppressed() {
        assertTrue(KeyguardCaptureRules.allowProjectionToContinue(true, KeyguardCaptureRules.RECORDER_PACKAGE, 1))
        listOf(-1, 0, 2, 3, 4, 5, 6, 7).forEach {
            assertFalse(KeyguardCaptureRules.allowProjectionToContinue(true, KeyguardCaptureRules.RECORDER_PACKAGE, it))
        }
    }

    @Test fun screenOffStopPauseAndTimeoutAreSuppressed() {
        listOf("SCREEN_OFF_OR_SHUT_DOWN", "POWER_OFF").forEach {
            assertTrue(KeyguardCaptureRules.ignoreRecorderEvent(true, it, KeyguardCaptureRules.SCREEN_OFF_ACTION))
        }
        assertTrue(KeyguardCaptureRules.ignoreRecorderEvent(true, "SCREEN_OFF_TIME_OUT", null))
    }

    @Test fun shutdownScreenOnAndMalformedActionsKeepTheirStockHandling() {
        listOf("SCREEN_OFF_OR_SHUT_DOWN", "POWER_OFF").forEach { type ->
            listOf(null, "", "android.intent.action.ACTION_SHUTDOWN", "android.intent.action.SCREEN_ON").forEach {
                assertFalse(KeyguardCaptureRules.ignoreRecorderEvent(true, type, it))
            }
        }
    }

    @Test fun explicitStopsUserSwitchAndResourceFailuresStillStopRecording() {
        listOf(null, "STOP_SCREEN_RECORDER", "STOP_RECORD", "QUIT_RECORD_SERVICE", "SWITCH_USER",
            "LOW_MEMORY_FINISH", "WORK_THREAD_UNCAUGHT_EXCEPTION", "IN_CALL", "PAUSE_RESUME").forEach {
            assertFalse(KeyguardCaptureRules.ignoreRecorderEvent(true, it, KeyguardCaptureRules.SCREEN_OFF_ACTION))
        }
    }
}
