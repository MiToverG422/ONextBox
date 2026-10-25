package com.mi.onextbox.lsp

import kotlin.math.abs

internal object SettingsFixedRefreshRateRules {
    const val TOGGLE_KEY = "oost_settings_disable_adaptive_refresh_rate"

    fun shouldBlockIdle(requested: Boolean, hasSelection: Boolean, screenOn: Boolean): Boolean =
        requested && hasSelection && screenOn

    fun selectedHertz(mode: Int, choice: Int): Float = when (mode) {
        1 -> 90f
        2 -> 60f
        3 -> if (choice in 60..120) choice.toFloat() else 120f
        4, 7, 8 -> if (choice > 120) choice.toFloat() else 0f
        else -> 0f // Auto and unknown firmware modes stay native.
    }

    /** C16 stores the native mode directly and has no C17 high-gear choice setting. */
    fun selectedHertzColorOs16(mode: Int): Float = when (mode) {
        1 -> 90f
        2 -> 60f
        3 -> 120f
        4 -> 144f
        7 -> 165f
        else -> 0f
    }

    fun isSupported(hertz: Float, supported: FloatArray): Boolean =
        hertz.isFinite() && hertz >= 30f && supported.any { abs(it - hertz) < 0.5f }

    fun effectiveHertz(selected: Float, supported: FloatArray, displayOn: Boolean,
                       suspended: Boolean, safetyCap: Float = Float.POSITIVE_INFINITY): Float {
        if (!displayOn || suspended || !isSupported(selected, supported) ||
            safetyCap.isNaN() || safetyCap <= 0f) return 0f
        val limit = minOf(selected, safetyCap)
        var result = 0f
        for (rate in supported) {
            if (rate.isFinite() && rate >= 30f && rate <= limit + 0.5f && rate > result) result = rate
        }
        return result
    }
}
