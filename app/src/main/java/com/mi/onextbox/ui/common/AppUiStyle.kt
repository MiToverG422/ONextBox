package com.mi.onextbox.ui.common

import android.content.Context

enum class AppUiStyle(val label: String) {
    ColorOs("ColorOS 17"),
    ColorOs16("ColorOS 16");

    companion object {
        private const val PREFS_NAME = "onextbox_prefs"
        private const val PREF_KEY = "app_ui_style"

        fun fromName(name: String?): AppUiStyle = entries.find { it.name == name } ?: ColorOs

        fun get(context: Context): AppUiStyle {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return fromName(prefs.getString(PREF_KEY, null))
        }

        fun set(context: Context, style: AppUiStyle) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_KEY, style.name)
                .apply()
        }
    }
}
