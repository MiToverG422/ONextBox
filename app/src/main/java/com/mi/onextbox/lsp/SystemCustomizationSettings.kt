package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.HookConfigSnapshot
import kotlin.math.roundToInt

internal object SystemCustomizationSettings {
    const val MULTI_APP = "com.oplus.multiapp"

    enum class Feature(val key: String) {
        GalleryDeleteCountdown("gallery_skip_delete_countdown"),
        MultiAppRestrictions("multiapp_remove_restrictions"),
        MultiAppPerAppLimit("multiapp_extend_per_app_limit"),
        FolderLayout("launcher_custom_folder_layout"),
        DesktopIconSize("launcher_custom_icon_size"),
        VolumeSteps("audio_custom_volume_steps"),
        NotificationClearSize("notification_clear_custom_size"),
    }

    data class Number(val key: String, val min: Int, val max: Int, val default: Int) {
        fun normalize(value: Int) = value.coerceIn(min, max)
    }

    val folderColumns = Number("launcher_open_folder_columns", 2, 6, 3)
    val folderRows = Number("launcher_open_folder_rows", 2, 6, 4)
    val iconPercent = Number("launcher_icon_size_percent", 50, 150, 100)
    val clearPercent = Number("notification_clear_size_percent", 50, 150, 100)
    val mediaSteps = Number("audio_media_steps", 0, 200, 0)
    val ringSteps = Number("audio_ring_steps", 0, 100, 0)
    val notificationSteps = Number("audio_notification_steps", 0, 100, 0)
    val alarmSteps = Number("audio_alarm_steps", 0, 100, 0)
    val callSteps = Number("audio_call_steps", 0, 100, 0)
    val streamSteps = linkedMapOf(3 to mediaSteps, 2 to ringSteps, 5 to notificationSteps,
        4 to alarmSteps, 0 to callSteps)
    val numbers = listOf(folderColumns, folderRows, iconPercent, clearPercent) + streamSteps.values

    fun enabled(context: Context, feature: Feature) =
        LspPreferenceStore.prefs(context).getBoolean(feature.key, false)
    fun active(feature: Feature) = HookConfigSnapshot.boolean(feature.key, false) == true
    fun read(context: Context, value: Number) =
        value.normalize(LspPreferenceStore.prefs(context).getInt(value.key, value.default))
    fun value(value: Number) = value.normalize(HookConfigSnapshot.int(value.key, value.default) ?: value.default)
    fun set(context: Context, feature: Feature, enabled: Boolean) =
        ModernXposedPreferenceSync.setBooleanAndSync(context, feature.key, enabled)
    fun set(context: Context, value: Number, number: Int) =
        ModernXposedPreferenceSync.setIntAndSync(context, value.key, value.normalize(number))

    data class State(val enabled: Boolean, val values: Map<String, Int>)
    fun state(context: Context, feature: Feature, values: List<Number>) =
        State(enabled(context, feature), values.associate { it.key to read(context, it) })
}

internal object SystemCustomizationRules {
    fun scaled(pixels: Int, percent: Int): Int =
        if (pixels <= 0) pixels else (pixels * percent.coerceIn(50, 150) / 100f).roundToInt().coerceAtLeast(1)
    fun folderGrid(columns: Int, rows: Int) =
        columns.coerceIn(2, 6) to rows.coerceIn(2, 6)
    fun volumeMaximum(requested: Int, minimum: Int, native: Int): Int =
        if (requested <= 0) native else requested.coerceIn((minimum + 1).coerceAtLeast(1), 200)
    fun rescaleVolume(value: Int, oldMax: Int, newMax: Int): Int =
        if (oldMax <= 0 || value < 0) value else
            (value.toLong() * newMax / oldMax).toInt().coerceIn(0, newMax)
}
