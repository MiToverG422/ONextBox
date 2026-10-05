package com.mi.onextbox.refresh

import android.content.Context

data class SavedRefreshRateMode(
    val id: Int,
    val surfaceFlingerModeIndex: Int,
    val width: Int,
    val height: Int,
    val refreshRate: Float
)

object RefreshRatePreferences {
    const val PREFS_NAME = "refresh_rate_page"
    const val KEY_CACHED_DISPLAY_MODES = "cached_display_modes"

    private const val KEY_SELECTED_MODE_ID = "selected_mode_id"
    private const val KEY_SELECTED_MODE_CONFIG = "selected_mode_config"
    private const val KEY_SELECTED_MODE_WIDTH = "selected_mode_width"
    private const val KEY_SELECTED_MODE_HEIGHT = "selected_mode_height"
    private const val KEY_SELECTED_MODE_RATE = "selected_mode_rate"
    private const val KEY_AUTO_START_ENABLED = "auto_start_enabled"

    fun readAutoStartEnabled(context: Context): Boolean = prefs(context)
        .getBoolean(KEY_AUTO_START_ENABLED, false)

    fun setAutoStartEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_START_ENABLED, enabled).apply()
    }

    fun readSelectedModeId(context: Context): Int? = prefs(context)
        .getInt(KEY_SELECTED_MODE_ID, -1)
        .takeIf { it >= 0 }

    fun saveSelectedMode(context: Context, mode: SavedRefreshRateMode) {
        prefs(context).edit()
            .putInt(KEY_SELECTED_MODE_ID, mode.id)
            .putInt(KEY_SELECTED_MODE_CONFIG, mode.surfaceFlingerModeIndex)
            .putInt(KEY_SELECTED_MODE_WIDTH, mode.width)
            .putInt(KEY_SELECTED_MODE_HEIGHT, mode.height)
            .putFloat(KEY_SELECTED_MODE_RATE, mode.refreshRate)
            .apply()
    }

    fun clearSelectedMode(context: Context) {
        prefs(context).edit()
            .putInt(KEY_SELECTED_MODE_ID, -1)
            .remove(KEY_SELECTED_MODE_CONFIG)
            .remove(KEY_SELECTED_MODE_WIDTH)
            .remove(KEY_SELECTED_MODE_HEIGHT)
            .remove(KEY_SELECTED_MODE_RATE)
            .apply()
    }

    fun readSelectedMode(context: Context): SavedRefreshRateMode? {
        val preferences = prefs(context)
        val id = readSelectedModeId(context) ?: return null
        if (preferences.contains(KEY_SELECTED_MODE_CONFIG)) {
            return SavedRefreshRateMode(
                id = id,
                surfaceFlingerModeIndex = preferences.getInt(KEY_SELECTED_MODE_CONFIG, -1),
                width = preferences.getInt(KEY_SELECTED_MODE_WIDTH, 0),
                height = preferences.getInt(KEY_SELECTED_MODE_HEIGHT, 0),
                refreshRate = preferences.getFloat(KEY_SELECTED_MODE_RATE, 0f)
            ).takeIf { it.surfaceFlingerModeIndex >= 0 }
        }

        // Migrate selections saved before the controller service existed.
        return preferences.getString(KEY_CACHED_DISPLAY_MODES, null)
            .orEmpty()
            .split(';')
            .firstNotNullOfOrNull { entry ->
                val values = entry.split('|')
                if (values.size != 5) return@firstNotNullOfOrNull null
                runCatching {
                    SavedRefreshRateMode(
                        id = values[0].toInt(),
                        surfaceFlingerModeIndex = values[1].toInt(),
                        width = values[2].toInt(),
                        height = values[3].toInt(),
                        refreshRate = values[4].toFloat()
                    )
                }.getOrNull()?.takeIf { it.id == id }
            }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
