package com.mi.onextbox.touch

import android.content.Context

object TouchSamplingPreferences {
    const val PREFS_NAME = "touch_sampling_page"

    private const val KEY_AUTO_START_ENABLED = "auto_start_enabled"
    private const val KEY_INDEX = "selected_index"
    private const val KEY_HZ = "selected_hz"
    private const val KEY_CHIP_VALUE = "selected_chip_value"
    private const val KEY_IST = "selected_ist"
    private const val KEY_BACKEND = "selected_backend"

    fun readAutoStartEnabled(context: Context): Boolean = prefs(context)
        .getBoolean(KEY_AUTO_START_ENABLED, false)

    fun setAutoStartEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_START_ENABLED, enabled).apply()
    }

    fun saveSelectedPreset(context: Context, preset: TouchRatePreset, backendId: String? = null) {
        val editor = prefs(context).edit()
            .putInt(KEY_INDEX, preset.index)
            .putInt(KEY_CHIP_VALUE, preset.chipValue)
            .putBoolean(KEY_IST, preset.isIstMode)
        preset.hz?.let { editor.putInt(KEY_HZ, it) } ?: editor.remove(KEY_HZ)
        backendId?.let { editor.putString(KEY_BACKEND, it) } ?: editor.remove(KEY_BACKEND)
        editor.apply()
    }

    fun readSelectedBackendId(context: Context): String? = prefs(context).getString(KEY_BACKEND, null)

    fun readSelectedPreset(context: Context): TouchRatePreset? {
        val preferences = prefs(context)
        if (!preferences.contains(KEY_INDEX) || !preferences.contains(KEY_CHIP_VALUE)
        ) return null
        return TouchRatePreset(
            index = preferences.getInt(KEY_INDEX, -1),
            hz = preferences.getInt(KEY_HZ, 0).takeIf { it in 30..4000 },
            chipValue = preferences.getInt(KEY_CHIP_VALUE, -1),
            isIstMode = preferences.getBoolean(KEY_IST, false),
        ).takeIf { it.index in 1..255 && it.chipValue >= 0 }
    }

    fun clearSelectedPreset(context: Context) {
        prefs(context).edit()
            .remove(KEY_INDEX)
            .remove(KEY_HZ)
            .remove(KEY_CHIP_VALUE)
            .remove(KEY_IST)
            .remove(KEY_BACKEND)
            .apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
