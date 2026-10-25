package com.mi.onextbox.ui.common

import android.content.Context

enum class AppUiStyle {
    ColorOs;

    companion object {
        private const val PREFS_NAME = "onextbox_prefs"
        private const val PREF_KEY = "app_ui_style"

        @Suppress("UNUSED_PARAMETER")
        fun fromName(name: String?): AppUiStyle = ColorOs

        fun get(context: Context): AppUiStyle {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.getString(PREF_KEY, ColorOs.name) != ColorOs.name) {
                prefs.edit().putString(PREF_KEY, ColorOs.name).apply()
            }
            return ColorOs
        }

        fun set(context: Context, style: AppUiStyle) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_KEY, style.name)
                .apply()
        }
    }
}
