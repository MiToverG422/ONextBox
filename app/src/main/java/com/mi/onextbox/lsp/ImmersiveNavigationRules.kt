package com.mi.onextbox.lsp

internal object ImmersiveNavigationRules {
    const val SYSTEM_DEFAULT = -1
    const val DEFAULT_LENGTH_DP = 92
    const val MIN_LENGTH_DP = 40
    const val MAX_LENGTH_DP = 200
    const val MIN_OPACITY_PERCENT = 0
    const val MAX_OPACITY_PERCENT = 100

    data class Dimension(val value: Float, val inPixels: Boolean = false)

    fun isHandleOptionActive(customHandleEnabled: Boolean, optionEnabled: Boolean): Boolean =
        customHandleEnabled && optionEnabled

    fun isFadedHandleColor(resourcePackage: String, name: String): Boolean =
        resourcePackage == "com.android.systemui" && (name == "navigation_bar_home_handle_dark_color_faded" ||
            name == "navigation_bar_home_handle_light_color_faded")

    fun fadedHandleColor(
        resourcePackage: String,
        name: String,
        gestureNavigation: Boolean,
        autoHideEnabled: Boolean,
    ): Int? {
        if (!autoHideEnabled || !gestureNavigation || !isFadedHandleColor(resourcePackage, name)) return null
        return when (name) {
            "navigation_bar_home_handle_dark_color_faded" -> 0x1affffff
            "navigation_bar_home_handle_light_color_faded" -> 0x1a000000
            else -> null
        }
    }

    fun isDimension(resourcePackage: String, name: String): Boolean =
        name == "navigation_bar_frame_height" && resourcePackage == "android" ||
            isHandleLength(resourcePackage, name) ||
            dimension(resourcePackage, name, 0, true) != null

    fun normalizeLength(valueDp: Int): Int = valueDp.coerceIn(MIN_LENGTH_DP, MAX_LENGTH_DP)

    fun normalizeLengthPreference(valueDp: Int): Int =
        if (valueDp == SYSTEM_DEFAULT) SYSTEM_DEFAULT else normalizeLength(valueDp)

    fun normalizeOpacity(value: Int): Int =
        if (value == SYSTEM_DEFAULT) SYSTEM_DEFAULT else value.coerceIn(MIN_OPACITY_PERCENT, MAX_OPACITY_PERCENT)

    fun lengthPreferenceFromSlider(valueDp: Int, systemDefaultDp: Int?): Int {
        val length = normalizeLengthPreference(valueDp)
        return if (length == systemDefaultDp) SYSTEM_DEFAULT else length
    }

    fun opacityPreferenceFromSlider(value: Int): Int {
        val opacity = normalizeOpacity(value)
        return if (opacity == MAX_OPACITY_PERCENT) SYSTEM_DEFAULT else opacity
    }

    fun handleAlpha(originalAlpha: Int, opacityPercent: Int): Int {
        val alpha = originalAlpha.coerceIn(0, 255)
        val opacity = normalizeOpacity(opacityPercent)
        return if (opacity == SYSTEM_DEFAULT) alpha else (alpha * opacity + 50) / 100
    }

    private fun isHandleLength(resourcePackage: String, name: String): Boolean = when (resourcePackage) {
        "oplus" -> name == "navigation_gesture_view_width" || name == "navigation_gesture_view_landscape_width"
        "com.android.systemui" -> name == "navigation_home_handle_width"
        else -> false
    }

    fun isBoolean(resourcePackage: String, name: String): Boolean =
        boolean(resourcePackage, name, true) != null

    fun dimension(
        resourcePackage: String,
        name: String,
        smallestWidthDp: Int,
        gestureNavigation: Boolean,
        immersiveEnabled: Boolean = true,
        customLengthEnabled: Boolean = false,
        lengthDp: Int = SYSTEM_DEFAULT,
    ): Dimension? {
        if (!gestureNavigation) return null
        if (customLengthEnabled && isHandleLength(resourcePackage, name)) {
            if (lengthDp == SYSTEM_DEFAULT) return null
            return Dimension(normalizeLength(lengthDp).toFloat())
        }
        if (!immersiveEnabled) return null
        return when (resourcePackage) {
            "android" -> when (name) {
                "navigation_bar_height" -> if (smallestWidthDp >= 900) {
                    Dimension(16f)
                } else {
                    Dimension(1f, inPixels = true)
                }
                "navigation_bar_height_landscape", "navigation_bar_width" ->
                    Dimension(1f, inPixels = true)
                "navigation_bar_gesture_height" -> Dimension(32f)
                else -> null
            }
            "com.android.systemui" -> when (name) {
                "navigation_handle_radius" -> Dimension(20f)
                "navigation_handle_height" -> Dimension(4f)
                "navigation_handle_bottom" -> Dimension(2.199982f)
                "navigation_home_handle_width" -> Dimension(92f)
                else -> null
            }
            else -> null
        }
    }

    fun boolean(resourcePackage: String, name: String, gestureNavigation: Boolean): Boolean? {
        if (resourcePackage != "android" || !gestureNavigation) return null
        return when (name) {
            "config_navBarNeedsScrim", "config_attachNavBarToAppDuringTransition" -> false
            "config_navBarTapThrough" -> true
            else -> null
        }
    }
}
