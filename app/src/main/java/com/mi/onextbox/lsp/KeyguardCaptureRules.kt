package com.mi.onextbox.lsp

/** Narrow capture exceptions, never relax capture permissions or secure-window protections. */
internal object KeyguardCaptureRules {
    const val SCREENSHOT_PACKAGE = "com.oplus.screenshot"
    const val HARDWARE_KEY_SOURCE = "KeyPress"
    const val RECORDER_PACKAGE = "com.oplus.screenrecorder"
    const val SCREEN_OFF_ACTION = "android.intent.action.SCREEN_OFF"
    const val STOP_REASON_KEYGUARD = 1

    fun allowAodScreenshot(
        enabled: Boolean,
        packageName: String?,
        source: String?,
        displayId: Int?,
        displayState: Int?,
    ): Boolean = enabled && packageName == SCREENSHOT_PACKAGE &&
        source == HARDWARE_KEY_SOURCE && displayId == 0 &&
        // Display.STATE_DOZE / STATE_DOZE_SUSPEND, not ordinary screen-off or ON_SUSPEND.
        (displayState == 3 || displayState == 4)

    fun allowProjectionStart(enabled: Boolean, packageName: String?): Boolean =
        enabled && packageName == RECORDER_PACKAGE

    fun allowProjectionToContinue(enabled: Boolean, packageName: String?, reason: Int): Boolean =
        allowProjectionStart(enabled, packageName) && reason == STOP_REASON_KEYGUARD

    fun ignoreRecorderEvent(enabled: Boolean, type: String?, action: String?): Boolean =
        enabled && when (type) {
            "SCREEN_OFF_OR_SHUT_DOWN", "POWER_OFF" -> action == SCREEN_OFF_ACTION
            "SCREEN_OFF_TIME_OUT" -> true
            else -> false
        }
}
