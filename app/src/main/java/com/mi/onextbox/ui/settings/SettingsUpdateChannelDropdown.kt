package com.mi.onextbox.ui.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R

enum class UpdateChannel {
    GitHubCi,
    GitHubReleases,
}

enum class UpdateBuildType {
    Release,
    Debug,
}

object UpdateChannelPreference {
    private const val PREFS_NAME = "onextbox_prefs"
    private const val KEY_UPDATE_CHANNEL = "update_channel"
    private const val KEY_UPDATE_BUILD_TYPE = "update_build_type"
    private const val KEY_UPDATE_NOTIFICATIONS = "update_notifications"
    private const val KEY_AUTO_SILENT_UPDATE = "auto_silent_update"
    private const val KEY_LAST_AUTO_SILENT_UPDATE_CHECK = "last_auto_silent_update_check"
    private const val AUTO_SILENT_UPDATE_INTERVAL_MS = 12 * 60 * 60 * 1000L

    fun get(context: Context): UpdateChannel {
        val raw = context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_UPDATE_CHANNEL, UpdateChannel.GitHubCi.name)
        return UpdateChannel.entries.firstOrNull { it.name == raw }
            ?: UpdateChannel.GitHubCi
    }

    fun set(context: Context, channel: UpdateChannel) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_UPDATE_CHANNEL, channel.name)
            .apply()
    }

    fun getBuildType(context: Context): UpdateBuildType {
        val raw = context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_UPDATE_BUILD_TYPE, UpdateBuildType.Release.name)
        return UpdateBuildType.entries.firstOrNull { it.name == raw }
            ?: UpdateBuildType.Release
    }

    fun setBuildType(context: Context, buildType: UpdateBuildType) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_UPDATE_BUILD_TYPE, buildType.name)
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

@Composable
fun SettingsUpdateChannelDropdown(
    title: String,
    hasDividerAbove: Boolean,
    hasDividerBelow: Boolean = false,
    selectedChannel: UpdateChannel? = null,
    onChannelChange: ((UpdateChannel) -> Unit)? = null,
) {
    val context = LocalContext.current
    var localSelectedChannel by remember {
        mutableStateOf(UpdateChannelPreference.get(context))
    }
    val currentSelectedChannel = selectedChannel ?: localSelectedChannel
    val channels = listOf(
        UpdateChannel.GitHubReleases to stringResource(R.string.update_channel_github_releases),
        UpdateChannel.GitHubCi to stringResource(R.string.update_channel_github_ci),
    )
    val selectedIndex = channels
        .indexOfFirst { it.first == currentSelectedChannel }
        .takeIf { it >= 0 }
        ?: 0

    SettingsWindowDropdownPreference(
        items = channels.map { it.second },
        selectedIndex = selectedIndex,
        title = title,
        onSelectedIndexChange = { index ->
            val channel = channels.getOrNull(index)?.first ?: return@SettingsWindowDropdownPreference
            if (onChannelChange != null) {
                onChannelChange(channel)
            } else {
                localSelectedChannel = channel
                UpdateChannelPreference.set(context, channel)
            }
        },
    )
}

@Composable
fun SettingsUpdateBuildTypeDropdown(
    title: String,
    selectedBuildType: UpdateBuildType? = null,
    onBuildTypeChange: ((UpdateBuildType) -> Unit)? = null,
) {
    val context = LocalContext.current
    var localSelectedBuildType by remember {
        mutableStateOf(UpdateChannelPreference.getBuildType(context))
    }
    val currentSelectedBuildType = selectedBuildType ?: localSelectedBuildType
    val buildTypes = listOf(
        UpdateBuildType.Release to stringResource(R.string.update_build_type_release),
        UpdateBuildType.Debug to stringResource(R.string.update_build_type_debug),
    )
    val selectedIndex = buildTypes
        .indexOfFirst { it.first == currentSelectedBuildType }
        .takeIf { it >= 0 }
        ?: 0

    SettingsWindowDropdownPreference(
        items = buildTypes.map { it.second },
        selectedIndex = selectedIndex,
        title = title,
        onSelectedIndexChange = { index ->
            val buildType = buildTypes.getOrNull(index)?.first
                ?: return@SettingsWindowDropdownPreference
            if (onBuildTypeChange != null) {
                onBuildTypeChange(buildType)
            } else {
                localSelectedBuildType = buildType
                UpdateChannelPreference.setBuildType(context, buildType)
            }
        },
    )
}
