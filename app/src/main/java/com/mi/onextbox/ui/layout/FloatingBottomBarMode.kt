package com.mi.onextbox.ui.layout

import android.content.SharedPreferences

enum class FloatingBottomBarMode(internal val preferenceValue: String) {
    Coui("coui"),
    LiquidGlass("liquid_glass"),
    Standard("standard"),
    Off("off");

    internal val visible: Boolean get() = this != Off
    internal val collapsible: Boolean get() = this == Coui || this == LiquidGlass

    internal fun allowsMainPageSwipe(savedPreference: Boolean): Boolean =
        savedPreference || !visible

    companion object {
        internal const val PreferenceKey = "floating_bottom_bar_mode"
        private const val LegacyLiquidGlassKey = "liquid_glass_bottom_bar"

        internal fun read(prefs: SharedPreferences): FloatingBottomBarMode {
            val values = prefs.all
            return restore(values[PreferenceKey] as? String, values[LegacyLiquidGlassKey] == true)
        }

        internal fun restore(savedMode: String?, legacyLiquidGlass: Boolean): FloatingBottomBarMode =
            entries.firstOrNull { it.preferenceValue == savedMode }
                ?: if (legacyLiquidGlass) LiquidGlass else Coui
    }
}
