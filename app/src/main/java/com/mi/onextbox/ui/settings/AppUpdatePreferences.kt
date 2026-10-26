package com.mi.onextbox.ui.settings

import android.content.Context
import com.mi.onextbox.BuildConfig

enum class UpdateBuildType {
    Release,
    Debug,
}

object AppUpdatePreferences {
    private const val PREFS_NAME = "onextbox_prefs"
    private const val KEY_BETA_PLAN = "update_beta_plan"
    private const val KEY_FEATURE_UPDATES_SEEN = "feature_updates_seen"
    private const val KEY_UPDATE_NOTIFICATIONS = "update_notifications"
    private const val KEY_AUTO_SILENT_UPDATE = "auto_silent_update"
    private const val KEY_LAST_AUTO_SILENT_UPDATE_CHECK = "last_auto_silent_update_check"
    private const val AUTO_SILENT_UPDATE_INTERVAL_MS = 12 * 60 * 60 * 1000L

    val currentBuildType: UpdateBuildType
        get() = AppUpdateRules.buildType(BuildConfig.BUILD_TYPE)

    internal fun preferences(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    internal fun hasSeenFeatureUpdates(context: Context, edition: String): Boolean =
        preferences(context).getString(KEY_FEATURE_UPDATES_SEEN, null) == edition

    internal fun markFeatureUpdatesSeen(context: Context, edition: String) {
        preferences(context).edit().putString(KEY_FEATURE_UPDATES_SEEN, edition).apply()
    }

    fun getBetaPlanEnabled(context: Context): Boolean = context
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_BETA_PLAN, false)

    fun setBetaPlanEnabled(context: Context, enabled: Boolean) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BETA_PLAN, enabled)
            .apply()
    }

    fun getUpdateNotificationsEnabled(context: Context): Boolean {
        return context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_UPDATE_NOTIFICATIONS, true)
    }

    fun setUpdateNotificationsEnabled(context: Context, enabled: Boolean) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_UPDATE_NOTIFICATIONS, enabled)
            .apply()
    }

    fun getAutomaticSilentUpdate(context: Context): Boolean {
        return context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_SILENT_UPDATE, false)
    }

    fun setAutomaticSilentUpdate(context: Context, enabled: Boolean) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_SILENT_UPDATE, enabled)
            .apply()
    }

    fun shouldRunAutomaticSilentUpdate(context: Context): Boolean {
        val lastCheckedAt = context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_AUTO_SILENT_UPDATE_CHECK, 0L)
        return System.currentTimeMillis() - lastCheckedAt >= AUTO_SILENT_UPDATE_INTERVAL_MS
    }

    fun markAutomaticSilentUpdateChecked(context: Context) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_AUTO_SILENT_UPDATE_CHECK, System.currentTimeMillis())
            .apply()
    }
}
