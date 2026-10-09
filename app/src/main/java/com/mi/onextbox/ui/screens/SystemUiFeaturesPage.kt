package com.mi.onextbox.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspConfig
import com.mi.onextbox.ui.settings.SettingsCardRow
import com.mi.onextbox.ui.settings.SettingsDivider
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsSection
import com.mi.onextbox.ui.settings.SettingsToggleRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SystemUiFeaturesPage(
    mode: FeaturePageMode,
    onOpenSubPage: (FeaturePageMode) -> Unit,
    nativeNotifyIconEnabled: Boolean,
    onNativeNotifyIconEnabledChange: (Boolean) -> Unit,
    nativeNotificationBubblesEnabled: Boolean,
    onNativeNotificationBubblesEnabledChange: (Boolean) -> Unit,
    systemUiInternationalNetworkDisplayEnabled: Boolean,
    onSystemUiInternationalNetworkDisplayEnabledChange: (Boolean) -> Unit,
    systemUiHideMobileRoamingIndicatorEnabled: Boolean,
    onSystemUiHideMobileRoamingIndicatorEnabledChange: (Boolean) -> Unit,
    systemUiInternationalNotificationStyleEnabled: Boolean,
    onSystemUiInternationalNotificationStyleEnabledChange: (Boolean) -> Unit,
    systemUiHideQsEditEnabled: Boolean,
    onSystemUiHideQsEditEnabledChange: (Boolean) -> Unit,
    systemUiHideQsSettingsEnabled: Boolean,
    onSystemUiHideQsSettingsEnabledChange: (Boolean) -> Unit,
    systemUiHideQsTopCarrierEnabled: Boolean,
    onSystemUiHideQsTopCarrierEnabledChange: (Boolean) -> Unit,
    systemUiHideQsMoreEnabled: Boolean,
    onSystemUiHideQsMoreEnabledChange: (Boolean) -> Unit,
    systemUiForceNativeClipboardOverlayEnabled: Boolean,
    onSystemUiForceNativeClipboardOverlayEnabledChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var forceTonalSpotEnabled by rememberSaveable {
        mutableStateOf(LspConfig.isSystemUiForceTonalSpotEnabled(context))
    }
    var monetColorSpecMode by remember {
        mutableStateOf(LspConfig.getSystemUiMonetColorSpecMode(context))
    }
    var restoreC16NetworkIconOrderEnabled by rememberSaveable {
        mutableStateOf(LspConfig.isSystemUiRestoreC16NetworkIconOrderEnabled(context))
    }
    var nativePowerMenuEnabled by rememberSaveable {
        mutableStateOf(LspConfig.isSystemUiNativePowerMenuEnabled(context))
    }
    var hideNetworkActivityIndicatorEnabled by rememberSaveable {
        mutableStateOf(LspConfig.isSystemUiHideNetworkActivityIndicatorEnabled(context))
    }

    when (mode) {
        FeaturePageMode.SystemUiNative -> {
            SettingsGroup {
                SettingsToggleRow(
                    title = stringResource(R.string.feature_native_notify_icon_title),
                    summary = stringResource(R.string.feature_native_notify_icon_summary),
                    checked = nativeNotifyIconEnabled,
                    onCheckedChange = onNativeNotifyIconEnabledChange,
                    hasDividerBelow = true,
                )
                SettingsDivider()
                SettingsToggleRow(
                    title = stringResource(R.string.feature_native_notification_bubbles_title),
                    summary = stringResource(R.string.feature_native_notification_bubbles_summary),
                    checked = nativeNotificationBubblesEnabled,
                    onCheckedChange = onNativeNotificationBubblesEnabledChange,
                    hasDividerAbove = true,
                    hasDividerBelow = true,
                )
                SettingsDivider()
                SettingsToggleRow(
                    title = stringResource(R.string.feature_native_power_menu_title),
                    summary = stringResource(R.string.feature_native_power_menu_summary),
                    checked = nativePowerMenuEnabled,
                    onCheckedChange = { enabled ->
                        nativePowerMenuEnabled = enabled
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                LspConfig.setSystemUiNativePowerMenuEnabled(context, enabled)
                            }
                        }
                    },
                    hasDividerAbove = true,
                    hasDividerBelow = true,
                )
                SettingsDivider()
                SettingsToggleRow(
                    title = stringResource(R.string.feature_force_native_clipboard_overlay_title),
                    summary = stringResource(R.string.feature_force_native_clipboard_overlay_summary),
                    checked = systemUiForceNativeClipboardOverlayEnabled,
                    onCheckedChange = onSystemUiForceNativeClipboardOverlayEnabledChange,
                    hasDividerAbove = true,
                )
            }
        }
        FeaturePageMode.SystemUiDynamicColor -> {
            SettingsGroup {
                OptionDropdownRow(
                    title = stringResource(R.string.feature_monet_color_spec_title),
                    summary = stringResource(R.string.feature_monet_color_spec_summary),
                    selectedLabel = when (monetColorSpecMode) {
                        LspConfig.SYSTEMUI_MONET_COLOR_SPEC_2025 -> "SPEC_2025"
                        LspConfig.SYSTEMUI_MONET_COLOR_SPEC_2021 -> "SPEC_2021"
                        else -> stringResource(R.string.feature_monet_color_spec_off)
                    },
                    options = listOf(
                        LspConfig.SYSTEMUI_MONET_COLOR_SPEC_2025 to "SPEC_2025",
                        LspConfig.SYSTEMUI_MONET_COLOR_SPEC_2021 to "SPEC_2021",
                        LspConfig.SYSTEMUI_MONET_COLOR_SPEC_OFF to
                            stringResource(R.string.feature_monet_color_spec_off),
                    ),
                    selectedValue = monetColorSpecMode,
                    onValueChange = { mode ->
                        monetColorSpecMode = mode
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                LspConfig.setSystemUiMonetColorSpecMode(context, mode)
                            }
                        }
                    },
                    hasDividerAbove = false,
                    hasDividerBelow = true,
                )
                SettingsDivider()
                SettingsToggleRow(
                    title = stringResource(R.string.feature_force_tonal_spot_title),
                    summary = stringResource(R.string.feature_force_tonal_spot_summary),
                    checked = forceTonalSpotEnabled,
                    onCheckedChange = { enabled ->
                        forceTonalSpotEnabled = enabled
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                LspConfig.setSystemUiForceTonalSpotEnabled(context, enabled)
                            }
                        }
                    },
                    hasDividerAbove = true,
                )
            }
        }
        FeaturePageMode.SystemUiStatusBar -> {
            SettingsSection(title = stringResource(R.string.status_bar_group_network))
            SettingsGroup {
                SettingsToggleRow(
                    title = stringResource(R.string.feature_international_network_display_title),
                    summary = stringResource(R.string.feature_international_network_display_summary),
                    checked = systemUiInternationalNetworkDisplayEnabled,
                    onCheckedChange = onSystemUiInternationalNetworkDisplayEnabledChange,
                    hasDividerBelow = true,
                )
                SettingsDivider()
                SettingsToggleRow(
                    title = stringResource(R.string.feature_restore_c16_network_icon_order_title),
                    summary = stringResource(R.string.feature_restore_c16_network_icon_order_summary),
                    checked = restoreC16NetworkIconOrderEnabled,
                    onCheckedChange = { enabled ->
                        restoreC16NetworkIconOrderEnabled = enabled
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                LspConfig.setSystemUiRestoreC16NetworkIconOrderEnabled(context, enabled)
                            }
                        }
                    },
                    hasDividerAbove = true,
                    hasDividerBelow = true,
                )
                SettingsDivider()
                SettingsToggleRow(
                    title = stringResource(R.string.feature_hide_mobile_roaming_indicator_title),
                    summary = stringResource(R.string.feature_hide_mobile_roaming_indicator_summary),
                    checked = systemUiHideMobileRoamingIndicatorEnabled,
                    onCheckedChange = onSystemUiHideMobileRoamingIndicatorEnabledChange,
                    hasDividerAbove = true,
                    hasDividerBelow = true,
                )
                SettingsDivider()
                SettingsToggleRow(
                    title = stringResource(R.string.feature_hide_network_activity_indicator_title),
                    summary = stringResource(R.string.feature_hide_network_activity_indicator_summary),
                    checked = hideNetworkActivityIndicatorEnabled,
                    onCheckedChange = { enabled ->
                        hideNetworkActivityIndicatorEnabled = enabled
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                LspConfig.setSystemUiHideNetworkActivityIndicatorEnabled(context, enabled)
                            }
                        }
                    },
                    hasDividerAbove = true,
                    hasDividerBelow = true,
                )
                SettingsDivider()
                DisconnectedBluetoothIconSettingsRow(hasDividerAbove = true)
            }

            SettingsSection(title = stringResource(R.string.status_bar_group_battery))
            SettingsGroup {
                FluidCloudBatterySettingsRow()
            }

            SettingsSection(title = stringResource(R.string.status_bar_group_time))
            SettingsGroup {
                PermanentClockSecondsSettingsRow()
            }
            SettingsSection(title = stringResource(R.string.status_bar_group_interaction))
            SettingsGroup {
                StatusBarInteractionSettingsRows()
            }
        }
        FeaturePageMode.SystemUiNotificationCenter -> {
            SettingsGroup {
                SettingsToggleRow(
                    title = stringResource(R.string.feature_international_notification_style_title),
                    summary = stringResource(R.string.feature_international_notification_style_summary),
                    checked = systemUiInternationalNotificationStyleEnabled,
                    onCheckedChange = onSystemUiInternationalNotificationStyleEnabledChange,
                    hasDividerBelow = true,
                )
                SettingsDivider()
                SettingsCardRow(
                    title = stringResource(R.string.notification_removal_title),
                    summary = stringResource(R.string.notification_removal_entry_summary),
                    onClick = { onOpenSubPage(FeaturePageMode.NotificationRemoval) },
                    showArrow = true,
                    hasDividerAbove = true,
                )
            }
        }
        FeaturePageMode.SystemUiControlCenter -> {
            SettingsGroup {
                SettingsToggleRow(
                    title = stringResource(R.string.feature_hide_qs_edit_title),
                    summary = stringResource(R.string.feature_hide_qs_edit_summary),
                    checked = systemUiHideQsEditEnabled,
                    onCheckedChange = onSystemUiHideQsEditEnabledChange,
                    hasDividerBelow = true,
                )
                SettingsDivider()
                SettingsToggleRow(
                    title = stringResource(R.string.feature_hide_qs_settings_title),
                    summary = stringResource(R.string.feature_hide_qs_settings_summary),
                    checked = systemUiHideQsSettingsEnabled,
                    onCheckedChange = onSystemUiHideQsSettingsEnabledChange,
                    hasDividerAbove = true,
                    hasDividerBelow = true,
                )
                SettingsDivider()
                SettingsToggleRow(
                    title = stringResource(R.string.feature_hide_qs_top_carrier_title),
                    summary = stringResource(R.string.feature_hide_qs_top_carrier_summary),
                    checked = systemUiHideQsTopCarrierEnabled,
                    onCheckedChange = onSystemUiHideQsTopCarrierEnabledChange,
                    hasDividerAbove = true,
                    hasDividerBelow = true,
                )
                SettingsDivider()
                SettingsToggleRow(
                    title = stringResource(R.string.feature_hide_qs_more_title),
                    summary = stringResource(R.string.feature_hide_qs_more_summary),
                    checked = systemUiHideQsMoreEnabled,
                    onCheckedChange = onSystemUiHideQsMoreEnabledChange,
                    hasDividerAbove = true,
                )
            }
        }
        else -> Unit
    }
}
