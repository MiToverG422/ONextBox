package com.mi.onextbox.ui.common

import android.content.Context
import io.github.suqi8.coui.kmp.theme.ColorSchemeMode
import io.github.suqi8.coui.kmp.theme.ThemeColorSpec
import io.github.suqi8.coui.kmp.theme.ThemePaletteStyle

enum class AppThemeMode {
    System,
    Light,
    Dark,
    MonetSystem,
    MonetLight,
    MonetDark;

    fun toColorSchemeMode(): ColorSchemeMode {
        return when (this) {
            System -> ColorSchemeMode.System
            Light -> ColorSchemeMode.Light
            Dark -> ColorSchemeMode.Dark
            MonetSystem -> ColorSchemeMode.MonetSystem
            MonetLight -> ColorSchemeMode.MonetLight
            MonetDark -> ColorSchemeMode.MonetDark
        }
    }

    companion object {
        private const val PREFS_NAME = "onextbox_prefs"
        private const val PREF_KEY = "app_theme_mode"

        fun fromName(name: String?): AppThemeMode {
            return entries.firstOrNull { it.name == name } ?: System
        }

        fun get(context: Context): AppThemeMode {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return fromName(prefs.getString(PREF_KEY, System.name))
        }

        fun set(context: Context, mode: AppThemeMode) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_KEY, mode.name)
                .apply()
        }
    }
}

val AppThemeMode.isMonet: Boolean
    get() = when (this) {
        AppThemeMode.MonetSystem,
        AppThemeMode.MonetLight,
        AppThemeMode.MonetDark -> true
        AppThemeMode.System,
        AppThemeMode.Light,
        AppThemeMode.Dark -> false
    }

object AppThemeKeyColor {
    private const val PREFS_NAME = "onextbox_prefs"
    private const val PREF_KEY = "app_theme_key_color"

    fun get(context: Context): Long? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return if (prefs.contains(PREF_KEY)) prefs.getLong(PREF_KEY, 0L) else null
    }

    fun set(context: Context, color: Long?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .apply {
                if (color == null) {
                    remove(PREF_KEY)
                } else {
                    putLong(PREF_KEY, color)
                }
            }
            .apply()
    }
}

object AppThemePaletteStyle {
    private const val PREFS_NAME = "onextbox_prefs"
    private const val PREF_KEY = "app_theme_palette_style"

    fun get(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(PREF_KEY, ThemePaletteStyle.TonalSpot.ordinal)
            .coerceIn(ThemePaletteStyle.entries.indices)
    }

    fun set(context: Context, value: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_KEY, value.coerceIn(ThemePaletteStyle.entries.indices))
            .apply()
    }
}

object AppThemeColorSpec {
    private const val PREFS_NAME = "onextbox_prefs"
    private const val PREF_KEY = "app_theme_color_spec"

    fun get(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(PREF_KEY, ThemeColorSpec.Spec2021.ordinal)
            .coerceIn(ThemeColorSpec.entries.indices)
    }

    fun set(context: Context, value: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_KEY, value.coerceIn(ThemeColorSpec.entries.indices))
            .apply()
    }
}
