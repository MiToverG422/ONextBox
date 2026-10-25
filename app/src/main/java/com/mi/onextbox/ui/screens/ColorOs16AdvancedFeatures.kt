package com.mi.onextbox.ui.screens

import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspConfig
import com.mi.onextbox.lsp.FluidCloudMediaSettings
import com.mi.onextbox.ui.common.AssistantScreenOption
import com.mi.onextbox.ui.common.applyAssistantScreenOption
import com.mi.onextbox.ui.common.queryAssistantScreenOption
import com.mi.onextbox.ui.settings.FeatureSettingWriteQueue
import com.mi.onextbox.ui.settings.SettingsCardRow
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsHomeEntrySettingsGroup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Reuse each audited feature's full editor, keeping sliders, selectors and app lists available. */
@Composable
internal fun ColorOs16AdvancedFeatureGroups(mode: FeaturePageMode, onOpen: (FeaturePageMode) -> Unit): Set<Int> {
    val supported = ColorOs16FeatureRegistry.titlesForPage(mode)
    val handled = mutableSetOf<Int>()
    when (mode) {
        FeaturePageMode.DesktopLayout -> {
            if (R.string.launcher_custom_grid_title in supported) {
                SettingsGroup { LauncherGridSettingsGroup() }
                handled += R.string.launcher_custom_grid_title
            }
            if (R.string.feature_launcher_taskbar_search_box_title in supported) {
                ColorOs16DesktopFeatures(mode, onOpen)
                handled += R.string.feature_launcher_taskbar_search_box_title
            }
        }
        FeaturePageMode.DesktopIcons -> {
            val titles = setOf(R.string.launcher_disable_icon_menu_title, R.string.launcher_widgets_menu_title,
                R.string.launcher_icon_label_spacing_title)
            if (titles.any { it in supported }) {
                LauncherIconMenuSettingsGroup(availableTitles = supported)
                handled += titles
            }
        }
        FeaturePageMode.DesktopRecent -> {
            if (R.string.launcher_swipe_top_title in supported) {
                LauncherSwipeTopSettingsGroup()
                handled += R.string.launcher_swipe_top_title
            }
            if (R.string.feature_recent_task_radius_title in supported) {
                ColorOs16RecentRadius()
                handled += R.string.feature_recent_task_radius_title
            }
            if (R.string.hide_recent_apps_title in supported) {
                SettingsGroup {
                    SettingsCardRow(title = stringResource(R.string.hide_recent_apps_title),
                        summary = stringResource(R.string.hide_recent_apps_summary), showArrow = true,
                        onClick = { onOpen(FeaturePageMode.DesktopHiddenApps) })
                }
                handled += R.string.hide_recent_apps_title
            }
        }
        FeaturePageMode.DesktopPages -> if (R.string.event_page_tool_title in supported) ColorOs16AssistantScreen()
        FeaturePageMode.SystemUiStatusBar -> {
            if (R.string.statusbar_order_title in supported && R.string.statusbar_margins_title in supported &&
                R.string.statusbar_vertical_margins_title in supported) {
                StatusBarLayoutSettingsGroup { onOpen(FeaturePageMode.SystemUiIconOrder) }
                handled += setOf(R.string.statusbar_order_title, R.string.statusbar_margins_title,
                    R.string.statusbar_vertical_margins_title)
            }
            if (R.string.carrier_name_title in supported) {
                CarrierNameSettingsGroup()
                handled += R.string.carrier_name_title
            }
            if (R.string.power_indicator_title in supported) {
                PowerIndicatorSettingsGroup()
                handled += R.string.power_indicator_title
            }
        }
        FeaturePageMode.SystemUiControlCenter -> {
            if (R.string.qs_tile_corner_title in supported && R.string.qs_slider_percentage_title in supported) {
                QsTileCornerSettingsGroup(
                    onOpenCorners = { onOpen(FeaturePageMode.SystemUiCornerSettings) },
                    onOpenPercentage = { onOpen(FeaturePageMode.SystemUiPercentageSettings) })
                handled += setOf(R.string.qs_tile_corner_title, R.string.qs_slider_percentage_title)
            }
        }
        FeaturePageMode.SystemUiVolume -> {
            if (R.string.side_volume_percentage_title in supported && R.string.side_volume_keep_wide_title in supported) {
                SideVolumePercentageSettingsGroup { onOpen(FeaturePageMode.SystemUiSideVolumePercentageSettings) }
                handled += setOf(R.string.side_volume_percentage_title, R.string.side_volume_keep_wide_title)
            }
        }
        FeaturePageMode.SystemUiFluidCloud -> if (R.string.fluid_media_blacklist_title in supported) {
            val context = LocalContext.current
            val setting = rememberColorOs16Setting(FluidCloudMediaSettings.ENABLED,
                FluidCloudMediaSettings::isEnabled, FluidCloudMediaSettings::setEnabled)
            if (setting.value) SettingsGroup {
                SettingsCardRow(title = stringResource(R.string.fluid_media_blacklist_title),
                    summary = FluidCloudMediaSettings.packages(context).size.toString(), showArrow = true,
                    onClick = { onOpen(FeaturePageMode.SystemUiFluidCloudMediaBlacklist) })
            }
        }
        FeaturePageMode.SystemUiNotificationCenter -> if (ColorOs16FeaturePolicy.isPageAvailable(FeaturePageMode.NotificationRemoval)) {
            SettingsGroup {
                SettingsCardRow(title = stringResource(R.string.notification_removal_title),
                    summary = stringResource(R.string.notification_removal_entry_summary), showArrow = true,
                    onClick = { onOpen(FeaturePageMode.NotificationRemoval) })
            }
        }
        FeaturePageMode.SettingsAppearance -> if (R.string.setting_settings_home_entry in supported) {
            SettingsHomeEntrySettingsGroup()
            handled += setOf(R.string.setting_settings_home_entry, R.string.setting_settings_home_entry_style)
        }
        FeaturePageMode.Assistant -> if (R.string.feature_assistant_native_combined_title in supported) {
            val context = LocalContext.current
            SettingsGroup {
                SettingsCardRow(title = stringResource(R.string.feature_assistant_default_settings_title),
                    summary = stringResource(R.string.feature_assistant_default_settings_summary), showArrow = true,
                    onClick = { openDefaultAssistantSettings(context) })
            }
        }
        FeaturePageMode.MobileNetwork -> if (R.string.network_identity_title in supported) {
            NetworkIdentitySettingsGroup()
            handled += R.string.network_identity_title
        }
        FeaturePageMode.AndroidSystem -> {
            FeatureAnimatedGroup {
                if (ColorOs16FeaturePolicy.isPageAvailable(FeaturePageMode.CorePatch)) {
                    row("corepatch") { above, below ->
                        SettingsCardRow(title = stringResource(R.string.corepatch_title), summary = "", showArrow = true,
                            onClick = { onOpen(FeaturePageMode.CorePatch) }, hasDividerAbove = above, hasDividerBelow = below)
                    }
                }
                if (ColorOs16FeaturePolicy.isPageAvailable(FeaturePageMode.Installer)) {
                    row("installer") { above, below ->
                        SettingsCardRow(title = stringResource(R.string.installer_title),
                            summary = stringResource(R.string.installer_entry_summary), showArrow = true,
                            onClick = { onOpen(FeaturePageMode.Installer) }, hasDividerAbove = above, hasDividerBelow = below)
                    }
                }
            }
        }
        FeaturePageMode.Wallpapers -> if (R.string.wallpaper_motion_title in supported) {
            WallpaperMotionGroup()
            handled += R.string.wallpaper_motion_title
        }
        else -> Unit
    }
    return handled
}

@Composable
private fun ColorOs16RecentRadius() {
    val enabled = rememberColorOs16Setting("recent_task_radius_enabled", LspConfig::isRecentTaskRadiusEnabled,
        LspConfig::setRecentTaskRadiusEnabled)
    val radius = rememberColorOs16Setting("recent_task_radius_dp", LspConfig::getRecentTaskRadiusDp,
        LspConfig::setRecentTaskRadiusDp)
    SettingsGroup {
        RecentTaskRadiusRow(title = stringResource(R.string.feature_recent_task_radius_title),
            checked = enabled.value, onCheckedChange = enabled.change,
            valueDp = radius.value, onValueDpChange = radius.change, hasDividerAbove = false)
    }
}

@Composable
private fun ColorOs16AssistantScreen() {
    val app = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var choice by remember { mutableStateOf(AssistantScreenOption.Default) }
    var ready by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(app) { choice = queryAssistantScreenOption(); ready = true }
    SettingsGroup {
        AssistantScreenRow(title = stringResource(R.string.event_page_tool_title),
            summary = stringResource(R.string.event_page_tool_summary), selectedOption = choice,
            onOptionChange = { next -> if (ready && !saving) {
                choice = next
                saving = true
                val pending = FeatureSettingWriteQueue.application.enqueue {
                    runBlocking { check(applyAssistantScreenOption(next).success) }
                }
                scope.launch {
                    try { pending.await() }
                    catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { Toast.makeText(app, R.string.enabled_features_save_failed, Toast.LENGTH_LONG).show() }
                    finally { choice = queryAssistantScreenOption(); saving = false }
                }
            } }, hasDividerAbove = false, hasDividerBelow = false)
    }
}
