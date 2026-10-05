package com.mi.onextbox.refresh

import android.content.Context
import com.mi.onextbox.ui.common.AppLogStore
import com.mi.onextbox.ui.common.ShellLogger

object RefreshRateAutoApplier {
    fun applyIfEnabled(context: Context): Boolean {
        if (!RefreshRatePreferences.readAutoStartEnabled(context)) return true
        val mode = RefreshRatePreferences.readSelectedMode(context) ?: run {
            AppLogStore.w("RefreshRate", "Auto apply skipped: no saved display mode")
            return false
        }
        val result = ShellLogger.exec(
            "RefreshRate auto apply",
            "service call SurfaceFlinger 1035 i32 ${mode.surfaceFlingerModeIndex}"
        )
        if (result.isSuccess) {
            AppLogStore.i(
                "RefreshRate",
                "Auto applied config=${mode.surfaceFlingerModeIndex} " +
                    "${mode.width}x${mode.height}@${mode.refreshRate}"
            )
        } else {
            AppLogStore.w("RefreshRate", "Auto apply failed for config=${mode.surfaceFlingerModeIndex}")
        }
        return result.isSuccess
    }
}