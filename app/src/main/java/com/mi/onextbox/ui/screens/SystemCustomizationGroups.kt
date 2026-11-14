package com.mi.onextbox.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.lsp.SystemCustomizationSettings as Settings
import com.mi.onextbox.ui.settings.SettingsToggleRow
import com.mi.onextbox.ui.settings.SettingsGroup
import kotlin.math.roundToInt

@Composable
internal fun SystemCustomizationGroup(feature: Settings.Feature) {
    SettingsGroup { SystemCustomizationRows(feature) }
}

@Composable
internal fun SystemCustomizationRows(
    feature: Settings.Feature,
    hasDividerAbove: Boolean = false,
    hasDividerBelow: Boolean = false,
) {
    val definition = systemCustomizationDefinition(feature)
    val values = when (feature) {
        Settings.Feature.FolderLayout -> listOf(Settings.folderColumns to R.string.custom_folder_columns,
            Settings.folderRows to R.string.custom_folder_rows)
        Settings.Feature.DesktopIconSize -> listOf(Settings.iconPercent to R.string.custom_icon_size)
        Settings.Feature.NotificationClearSize -> listOf(Settings.clearPercent to R.string.custom_clear_size)
        Settings.Feature.VolumeSteps -> listOf(Settings.mediaSteps to R.string.custom_volume_media,
            Settings.ringSteps to R.string.custom_volume_ring, Settings.notificationSteps to R.string.custom_volume_notification,
            Settings.alarmSteps to R.string.custom_volume_alarm, Settings.callSteps to R.string.custom_volume_call)
        else -> emptyList()
    }
    val context = LocalContext.current.applicationContext
    val editor = rememberCustomization(setOf(feature.key) + values.map { it.first.key }) {
        Settings.state(it, feature, values.map { pair -> pair.first })
    }
    val state = editor.state
    FeatureAnimatedRows(hasDividerAbove = hasDividerAbove, hasDividerBelow = hasDividerBelow) {
        row(feature.key) { above, below ->
            SettingsToggleRow(title = stringResource(definition.title),
                summary = if (definition.summary != 0) stringResource(definition.summary) else "",
                checked = state.enabled, interactionEnabled = editor.savingKey == null,
                onCheckedChange = { enabled -> editor.save(feature.key, state.copy(enabled = enabled)) {
                    Settings.set(context, feature, enabled)
                } }, hasDividerAbove = above, hasDividerBelow = below)
        }
        values.forEach { (number, title) ->
            row(number.key, visible = state.enabled) { above, below ->
                val selected = state.values.getValue(number.key)
                fun save(value: Int) = editor.save(number.key,
                    editor.state.copy(values = editor.state.values + (number.key to value))) {
                    Settings.set(context, number, value)
                }
                val label = when {
                    number == Settings.iconPercent || number == Settings.clearPercent -> "$selected%"
                    feature == Settings.Feature.VolumeSteps && selected == 0 -> stringResource(R.string.custom_system_default)
                    else -> selected.toString()
                }
                FeatureSliderRow(heading = stringResource(title), currentValue = label,
                    value = selected.toFloat(), valueRange = number.min.toFloat()..number.max.toFloat(),
                    steps = number.max - number.min - 1, interactionEnabled = editor.savingKey == null,
                    onValueChange = { editor.state = editor.state.copy(values = editor.state.values +
                        (number.key to it.roundToInt())) },
                    onValueChangeFinished = { save(editor.state.values.getValue(number.key)) },
                    onReset = { save(number.default) }, hasDividerAbove = above, hasDividerBelow = below)
            }
        }
    }
}

private val systemCustomizationDefinitions = Settings.Feature.entries.associateWith(::createSystemCustomizationDefinition)

internal fun systemCustomizationDefinition(feature: Settings.Feature): SharedFeatureDefinitions.Toggle =
    systemCustomizationDefinitions.getValue(feature)

private fun createSystemCustomizationDefinition(feature: Settings.Feature): SharedFeatureDefinitions.Toggle {
    val (title, summary, page) = when (feature) {
        Settings.Feature.GalleryDeleteCountdown -> Triple(R.string.gallery_skip_delete_countdown,
            R.string.gallery_skip_delete_countdown_summary, FeaturePageMode.Gallery)
        Settings.Feature.MultiAppRestrictions -> Triple(R.string.multiapp_remove_restrictions,
            R.string.multiapp_remove_restrictions_summary, FeaturePageMode.MultiApp)
        Settings.Feature.MultiAppPerAppLimit -> Triple(R.string.multiapp_extend_per_app_limit,
            R.string.multiapp_extend_per_app_limit_summary, FeaturePageMode.MultiApp)
        Settings.Feature.FolderLayout -> Triple(R.string.custom_folder_layout,
            R.string.custom_folder_layout_summary, FeaturePageMode.DesktopIcons)
        Settings.Feature.DesktopIconSize -> Triple(R.string.custom_desktop_icon_size,
            R.string.custom_desktop_icon_size_summary, FeaturePageMode.DesktopIcons)
        Settings.Feature.VolumeSteps -> Triple(R.string.custom_volume_steps,
            R.string.custom_volume_steps_summary, FeaturePageMode.SystemUiVolume)
        Settings.Feature.NotificationClearSize -> Triple(R.string.custom_notification_clear_size,
            R.string.custom_notification_clear_size_summary, FeaturePageMode.SystemUiNotificationCenter)
    }
    return SharedFeatureDefinitions.Toggle(title, summary, page,
        { Settings.enabled(it, feature) }, { context, enabled -> Settings.set(context, feature, enabled) })
}
