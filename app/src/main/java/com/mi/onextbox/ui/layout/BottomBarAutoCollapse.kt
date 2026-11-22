package com.mi.onextbox.ui.layout

import android.content.SharedPreferences

enum class BottomBarAutoCollapse(internal val preferenceValue: String) {
    Left("left"),
    Right("right"),
    Bottom("bottom"),
    Off("off");

    internal val isSide: Boolean get() = this == Left || this == Right

    companion object {
        internal const val PreferenceKey = "bottom_bar_auto_collapse"

        internal fun choicesFor(bar: FloatingBottomBarMode): List<BottomBarAutoCollapse> = when {
            bar.collapsible -> entries
            bar.visible -> listOf(Bottom, Off)
            else -> listOf(Off)
        }

        internal fun restore(value: String?): BottomBarAutoCollapse =
            entries.firstOrNull { it.preferenceValue == value } ?: Off

        internal fun read(prefs: SharedPreferences): BottomBarAutoCollapse =
            restore(prefs.all[PreferenceKey] as? String)
    }

    internal fun forBar(bar: FloatingBottomBarMode): BottomBarAutoCollapse = when {
        !bar.visible || (isSide && !bar.collapsible) -> Off
        else -> this
    }
}
