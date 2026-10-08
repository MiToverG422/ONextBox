package com.mi.onextbox.lsp

import kotlin.math.roundToInt

// Defaults and ranges for AOD brightness and recent task corner radius.
internal object FeatureSliderRules {
    const val SYSTEM_DEFAULT = -1
    const val SYSTEM_DEFAULT_MULTIPLIER = -1f
    const val DARK_BRIGHTNESS_PREVIEW = 80
    const val BRIGHT_BRIGHTNESS_PREVIEW = 160
    const val RECENT_RADIUS_PREVIEW_DP = 26
    val recentRadiusResourceNames = listOf("recent_task_view_radius", "task_view_radius_20", "task_view_radius_22")

    fun normalizeBrightness(value: Int): Int =
        if (value == SYSTEM_DEFAULT) value else value.coerceIn(0, 255)

    fun normalizeRadius(value: Int): Int =
        if (value == SYSTEM_DEFAULT) value else value.coerceIn(0, 260)

    fun normalizeRadius(value: Float): Float =
        if (!value.isFinite() || value == SYSTEM_DEFAULT.toFloat()) SYSTEM_DEFAULT.toFloat()
        else value.coerceIn(0f, 260f)

    fun radiusFromSlider(value: Int, systemDefaultDp: Int?): Int {
        val radius = normalizeRadius(value)
        return if (radius == systemDefaultDp) SYSTEM_DEFAULT else radius
    }

    fun normalizeMultiplier(value: Float): Float {
        if (!value.isFinite() || value == SYSTEM_DEFAULT_MULTIPLIER) return SYSTEM_DEFAULT_MULTIPLIER
        val multiplier = value.coerceIn(1f, 3f)
        return if (multiplier == 1f) SYSTEM_DEFAULT_MULTIPLIER else multiplier
    }

    fun multiplierFromSlider(value: Float): Float {
        val normalized = normalizeMultiplier(value)
        if (normalized == SYSTEM_DEFAULT_MULTIPLIER) return normalized
        return normalizeMultiplier((normalized * 10f).roundToInt() / 10f)
    }

    fun initBrightness(original: Int, configured: Int): Int {
        if (original < 0) return original
        val brightness = normalizeBrightness(configured)
        return if (brightness == SYSTEM_DEFAULT) original else brightness
    }
}
