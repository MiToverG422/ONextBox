package com.mi.onextbox.ui.common

import android.content.Context

enum class AppUiStyle {
    ColorOs,
    Material3Expressive;

    companion object {
        private const val PREFS_NAME = "onextbox_prefs"
        private const val PREF_KEY = "app_ui_style"

        fun fromName(name: String?): AppUiStyle = when (name) {
            // Preserve the selection saved by early builds using the old label.
            "Clarity" -> Material3Expressive
            else -> entries.firstOrNull { it.name == name } ?: ColorOs
        }

        fun get(context: Context): AppUiStyle {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return fromName(prefs.getString(PREF_KEY, ColorOs.name))
        }

        fun set(context: Context, style: AppUiStyle) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_KEY, style.name)
                .apply()
        }
    }
}
