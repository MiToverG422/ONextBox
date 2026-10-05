package com.mi.onextbox.ui.onboarding

import android.content.Context
import com.mi.onextbox.ui.common.AppThemeKeyColor
import com.mi.onextbox.ui.common.AppThemeMode
import com.mi.onextbox.ui.common.AppUiStyle
import com.mi.onextbox.ui.common.isMonet

/** Keeps activation's brightness choice in sync with the app's two UI-style theme slots. */
object OnboardingAppearance {
    private const val PREFS_NAME = "onextbox_prefs"
    private const val UI_STYLE_KEY = "app_ui_style"
    private const val THEME_MODE_KEY = "app_theme_mode"
    private const val COLOROS_THEME_MODE_KEY = "coloros_theme_mode"
    private const val MATERIAL_THEME_MODE_KEY = "material_theme_mode"

    fun brightnessOf(mode: AppThemeMode): AppThemeMode = when (mode) {
        AppThemeMode.System, AppThemeMode.MonetSystem -> AppThemeMode.System
        AppThemeMode.Light, AppThemeMode.MonetLight -> AppThemeMode.Light
        AppThemeMode.Dark, AppThemeMode.MonetDark -> AppThemeMode.Dark
    }

    /** Returns the effective mode only when the app preference transaction was saved. */
    fun apply(context: Context, style: AppUiStyle, mode: AppThemeMode): AppThemeMode? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val brightness = brightnessOf(mode)
        val currentStyle = AppUiStyle.get(context)
        val currentMode = AppThemeMode.get(context)
        val colorOsDynamic = prefs.getString(COLOROS_THEME_MODE_KEY, null)
            ?.let(AppThemeMode::fromName)
            ?.isMonet
            ?: (currentStyle == AppUiStyle.ColorOs && currentMode.isMonet)
        val materialDynamic = prefs.getString(MATERIAL_THEME_MODE_KEY, null)
            ?.let(AppThemeMode::fromName)
            ?.isMonet
            ?: if (currentStyle == AppUiStyle.Material3Expressive) {
                currentMode.isMonet
            } else {
                AppThemeKeyColor.get(context) == null
            }
        val colorOsMode = brightness.withDynamicColor(colorOsDynamic)
        val materialMode = brightness.withDynamicColor(materialDynamic)
        val effectiveMode = if (style == AppUiStyle.ColorOs) colorOsMode else materialMode

        return if (
            prefs.edit()
                .putString(UI_STYLE_KEY, style.name)
                .putString(THEME_MODE_KEY, effectiveMode.name)
                .putString(COLOROS_THEME_MODE_KEY, colorOsMode.name)
                .putString(MATERIAL_THEME_MODE_KEY, materialMode.name)
                .commit()
        ) effectiveMode else null
    }

    private fun AppThemeMode.withDynamicColor(enabled: Boolean): AppThemeMode = when (this) {
        AppThemeMode.System -> if (enabled) AppThemeMode.MonetSystem else AppThemeMode.System
        AppThemeMode.Light -> if (enabled) AppThemeMode.MonetLight else AppThemeMode.Light
        AppThemeMode.Dark -> if (enabled) AppThemeMode.MonetDark else AppThemeMode.Dark
        else -> error("Brightness must be normalized before applying dynamic color")
    }
}
