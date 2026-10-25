package com.mi.onextbox.ui.screens

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue


import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspConfig
import com.mi.onextbox.lsp.DisplayEnhancementThermalSettings
import com.mi.onextbox.ui.common.restartScopePackages
import com.mi.onextbox.ui.settings.SettingsDivider
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsSection
import com.mi.onextbox.ui.settings.SettingsToggleRow
import com.mi.onextbox.ui.settings.FeatureSettingWriteQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun WallpapersFeaturesPage() {
    WallpaperMotionGroup()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var redOneEntryEnabled by rememberSaveable {
        mutableStateOf(LspConfig.isWallpapersRedOneEntryEnabled(context))
    }

    SettingsSection(title = stringResource(R.string.feature_wallpapers_clock_group))
    SettingsGroup {
        SettingsToggleRow(
            title = stringResource(R.string.feature_wallpapers_red_one_entry_title),
            summary = stringResource(R.string.feature_wallpapers_red_one_entry_summary),
            checked = redOneEntryEnabled,
            onCheckedChange = { enabled ->
                redOneEntryEnabled = enabled
                scope.launch {
                    withContext(Dispatchers.IO) {
                        LspConfig.setWallpapersRedOneEntryEnabled(context, enabled)
                    }
                    restartScopePackages(listOf("com.oplus.wallpapers", "com.android.systemui", "com.android.launcher"))
                }
            },
        )
    }
}

@Composable
internal fun SettingsFeaturesPage(
    mode: FeaturePageMode,
    onOpenSubPage: (FeaturePageMode) -> Unit,
    permissionMonitorVisible: Boolean,
    onPermissionMonitorVisibleChange: (Boolean) -> Unit,
    settingsForceGoogleEntryEnabled: Boolean,
    onSettingsForceGoogleEntryEnabledChange: (Boolean) -> Unit,
    extremeRefresh165Enabled: Boolean,
    onExtremeRefresh165EnabledChange: (Boolean) -> Unit,
) {
    if (mode == FeaturePageMode.Settings) {
        SettingsCategoriesPage(onOpenSubPage)
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var settingsInternationalEnabled by rememberSaveable {
        mutableStateOf(LspConfig.isSettingsInternationalEnabled(context))
    }
    var settingsTitleCollapsed by rememberSaveable {
        mutableStateOf(LspConfig.isSettingsTitleCollapsedEnabled(context))
    }
    var settingsAppInfoCard by rememberSaveable {
        mutableStateOf(LspConfig.isSettingsAppInfoCardEnabled(context))
    }
    var settingsForceAppAutoStartEnabled by rememberSaveable {
        mutableStateOf(LspConfig.isSettingsForceAppAutoStartEnabled(context))
    }
    var settingsInternationalWalletEnabled by rememberSaveable {
        mutableStateOf(LspConfig.isSettingsInternationalWalletEnabled(context))
    }
    var settingsRestoreDomesticAboutDeviceEnabled by rememberSaveable {
        mutableStateOf(LspConfig.isSettingsRestoreDomesticAboutDeviceEnabled(context))
    }
    var settingsRestoreDomesticAuxiliaryFunctionsEnabled by rememberSaveable {
        mutableStateOf(LspConfig.isSettingsRestoreDomesticAuxiliaryFunctionsEnabled(context))
    }
    var settingsC15AboutLayoutEnabled by rememberSaveable {
        mutableStateOf(LspConfig.isSettingsC15AboutLayoutEnabled(context))
    }
    var settingsSkipSpecialPermissionRiskConfirmEnabled by rememberSaveable {
        mutableStateOf(LspConfig.isSettingsSkipSpecialPermissionRiskConfirmEnabled(context))
    }
    var settingsRestoreAppOpenButtonEnabled by rememberSaveable {
        mutableStateOf(LspConfig.isSettingsRestoreAppOpenButtonEnabled(context))
    }
    var settingsRefreshRateUnlocked by rememberSaveable {
        mutableStateOf(LspConfig.isSettingsRefreshRateUnlocked(context))
    }
    var settingsForceGlobalExtremeRefreshRate by rememberSaveable {
        mutableStateOf(LspConfig.isSettingsForceGlobalExtremeRefreshRateEnabled(context))
    }
    var settingsDisableAdaptiveRefreshRate by rememberSaveable {
        mutableStateOf(LspConfig.isSettingsDisableAdaptiveRefreshRateEnabled(context))
    }
    var superResolutionThermalBypass by rememberSaveable {
        mutableStateOf(DisplayEnhancementThermalSettings.enabled(context, DisplayEnhancementThermalSettings.SUPER_RESOLUTION))
    }
    var videoColorThermalBypass by rememberSaveable {
        mutableStateOf(DisplayEnhancementThermalSettings.enabled(context, DisplayEnhancementThermalSettings.VIDEO_COLOR))
    }
    var enhancementSaving by remember { mutableStateOf(false) }
    fun saveEnhancement(key: String, enabled: Boolean) {
        if (enhancementSaving) return
        enhancementSaving = true
        val pending = FeatureSettingWriteQueue.application.enqueue {
            DisplayEnhancementThermalSettings.set(context, key, enabled)
        }
        scope.launch {
            try { pending.await() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { Toast.makeText(context, R.string.app_errors_sync_failed, Toast.LENGTH_LONG).show() }
            finally {
                superResolutionThermalBypass = DisplayEnhancementThermalSettings.enabled(context, DisplayEnhancementThermalSettings.SUPER_RESOLUTION)
                videoColorThermalBypass = DisplayEnhancementThermalSettings.enabled(context, DisplayEnhancementThermalSettings.VIDEO_COLOR)
                enhancementSaving = false
            }
        }
    }
    var settingsRestoreSmartLockEnabled by rememberSaveable {
        mutableStateOf(LspConfig.isSettingsRestoreSmartLockEnabled(context))
    }

    if (mode == FeaturePageMode.SettingsAppearance) {
        SettingsHomeCleanupGroup()
        SettingsSection(title = stringResource(R.string.feature_group_settings_interface))
        SettingsGroup {
            SettingsToggleRow(
                title = stringResource(R.string.feature_settings_title_collapsed_title),
                summary = stringResource(R.string.feature_settings_title_collapsed_summary),
                checked = settingsTitleCollapsed,
                onCheckedChange = { enabled ->
                    settingsTitleCollapsed = enabled
                    scope.launch {
                        withContext(Dispatchers.IO) { LspConfig.setSettingsTitleCollapsedEnabled(context, enabled) }
                    }
                },
            )
        }
    }

    if (mode == FeaturePageMode.SettingsPermissions) {
        SettingsSection(title = stringResource(R.string.feature_group_developer_options))
        SettingsGroup {
            SettingsToggleRow(
                title = stringResource(R.string.feature_permission_monitor_title),
                summary = stringResource(R.string.feature_permission_monitor_summary),
                checked = permissionMonitorVisible,
                onCheckedChange = onPermissionMonitorVisibleChange,
            )
        }
    }

    if (mode == FeaturePageMode.SettingsAppearance) {
        SettingsSection(title = stringResource(R.string.display_enhancement_group))
        SettingsGroup {
            run {SettingsToggleRow(
                    title = stringResource(R.string.display_enhancement_sr_thermal_title),
                    summary = stringResource(R.string.display_enhancement_sr_thermal_summary),
                    checked = superResolutionThermalBypass,
                    interactionEnabled = !enhancementSaving,
                    onCheckedChange = { saveEnhancement(DisplayEnhancementThermalSettings.SUPER_RESOLUTION, it) },
                    hasDividerBelow = true,
                )}
            SettingsDivider()
            run {SettingsToggleRow(
                    title = stringResource(R.string.display_enhancement_color_thermal_title),
                    summary = stringResource(R.string.display_enhancement_color_thermal_summary),
                    checked = videoColorThermalBypass,
                    interactionEnabled = !enhancementSaving,
                    onCheckedChange = { saveEnhancement(DisplayEnhancementThermalSettings.VIDEO_COLOR, it) },
                    hasDividerAbove = true,
                )}
        }
    }

    if (mode == FeaturePageMode.SettingsApps) {
        SettingsSection(title = stringResource(R.string.feature_group_app_management))
        SettingsGroup {
            SettingsToggleRow(
                title = stringResource(R.string.feature_settings_app_info_card_title),
                summary = stringResource(R.string.feature_settings_app_info_card_summary),
                checked = settingsAppInfoCard,
                onCheckedChange = { enabled ->
                    settingsAppInfoCard = enabled
                    scope.launch {
                        withContext(Dispatchers.IO) { LspConfig.setSettingsAppInfoCardEnabled(context, enabled) }
                    }
                },
                hasDividerBelow = true,
            )
            SettingsDivider()
            SettingsToggleRow(
                title = stringResource(R.string.feature_settings_restore_app_open_button_title),
                summary = stringResource(R.string.feature_settings_restore_app_open_button_summary),
                checked = settingsRestoreAppOpenButtonEnabled,
                onCheckedChange = { enabled ->
                    settingsRestoreAppOpenButtonEnabled = enabled
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            LspConfig.setSettingsRestoreAppOpenButtonEnabled(context, enabled)
                        }
                    }
                },
                hasDividerAbove = true,
            )
        }
    }

    if (mode == FeaturePageMode.SettingsPermissions) {
        SettingsSection(title = stringResource(R.string.feature_group_special_permissions))
        SettingsGroup {
            SettingsToggleRow(
                title = stringResource(R.string.feature_settings_skip_special_permission_risk_confirm_title),
                summary = stringResource(R.string.feature_settings_skip_special_permission_risk_confirm_summary),
                checked = settingsSkipSpecialPermissionRiskConfirmEnabled,
                onCheckedChange = { enabled ->
                    settingsSkipSpecialPermissionRiskConfirmEnabled = enabled
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            LspConfig.setSettingsSkipSpecialPermissionRiskConfirmEnabled(context, enabled)
                        }
                    }
                },
            )
        }
    }

    if (mode == FeaturePageMode.SettingsAboutDevice) {
        SettingsSection(title = stringResource(R.string.feature_group_about_device))
        SettingsGroup {
            SettingsToggleRow(
                title = stringResource(R.string.feature_settings_c15_about_layout_title),
                summary = stringResource(R.string.feature_settings_c15_about_layout_summary),
                checked = settingsC15AboutLayoutEnabled,
                onCheckedChange = { enabled ->
                    settingsC15AboutLayoutEnabled = enabled
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            LspConfig.setSettingsC15AboutLayoutEnabled(context, enabled)
                        }
                    }
                },
            )
        }
    }

    if (mode == FeaturePageMode.SettingsAppearance) {
        SettingsSection(title = stringResource(R.string.feature_group_screen_refresh_rate))
        SettingsGroup {
            run {SettingsToggleRow(
                    title = stringResource(R.string.feature_settings_unlock_refresh_rate_title),
                    summary = stringResource(R.string.feature_settings_unlock_refresh_rate_summary),
                    checked = settingsRefreshRateUnlocked,
                    onCheckedChange = { enabled ->
                        settingsRefreshRateUnlocked = enabled
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                LspConfig.setSettingsRefreshRateUnlocked(context, enabled)
                            }
                        }
                    },
                    hasDividerBelow = true,
                )}
            SettingsDivider()
            run {SettingsToggleRow(
                    title = stringResource(R.string.feature_settings_force_global_extreme_refresh_rate_title),
                    summary = stringResource(R.string.feature_settings_force_global_extreme_refresh_rate_summary),
                    checked = settingsForceGlobalExtremeRefreshRate,
                    onCheckedChange = { enabled ->
                        settingsForceGlobalExtremeRefreshRate = enabled
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                LspConfig.setSettingsForceGlobalExtremeRefreshRateEnabled(context, enabled)
                            }
                        }
                    },
                    hasDividerAbove = true,
                    hasDividerBelow = true,
                )}
            SettingsDivider()
            run {SettingsToggleRow(
                    title = stringResource(R.string.feature_settings_disable_adaptive_refresh_rate_title),
                    summary = stringResource(R.string.feature_settings_disable_adaptive_refresh_rate_summary),
                    checked = settingsDisableAdaptiveRefreshRate,
                    onCheckedChange = { enabled ->
                        settingsDisableAdaptiveRefreshRate = enabled
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                LspConfig.setSettingsDisableAdaptiveRefreshRateEnabled(context, enabled)
                            }
                        }
                    },
                    hasDividerAbove = true,
                    hasDividerBelow = true,
                )}
            SettingsDivider()
            run {SettingsToggleRow(
                    title = stringResource(R.string.feature_extreme_refresh_165_title),
                    summary = stringResource(R.string.feature_extreme_refresh_165_summary),
                    checked = extremeRefresh165Enabled,
                    onCheckedChange = onExtremeRefresh165EnabledChange,
                    hasDividerAbove = true,
                )}
        }
    }

    if (mode == FeaturePageMode.SettingsRegion) {
        FeatureAnimatedGroup {
        row(0) { above, below ->
            SettingsToggleRow(
                title = stringResource(R.string.feature_settings_force_google_title),
                summary = stringResource(R.string.feature_settings_force_google_summary),
                checked = settingsForceGoogleEntryEnabled,
                onCheckedChange = onSettingsForceGoogleEntryEnabledChange,
                hasDividerAbove = above,
                hasDividerBelow = below,
            )
        }
        row(1) { above, below ->
            SettingsToggleRow(
                title = stringResource(R.string.feature_settings_international_title),
                summary = stringResource(R.string.feature_settings_international_summary),
                checked = settingsInternationalEnabled,
                onCheckedChange = { enabled ->
                    settingsInternationalEnabled = enabled
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            LspConfig.setSettingsInternationalEnabled(context, enabled)
                        }
                    }
                },
                hasDividerAbove = above,
                hasDividerBelow = below,
            )
        }
        row(2, visible = settingsInternationalEnabled) { above, below ->
            SettingsToggleRow(
                title = stringResource(R.string.feature_settings_force_app_auto_start_title),
                summary = stringResource(R.string.feature_settings_force_app_auto_start_summary),
                checked = settingsForceAppAutoStartEnabled,
                enabled = settingsInternationalEnabled,
                onCheckedChange = { enabled ->
                    settingsForceAppAutoStartEnabled = enabled
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            LspConfig.setSettingsForceAppAutoStartEnabled(context, enabled)
                        }
                    }
                },
                hasDividerAbove = above,
                hasDividerBelow = below,
            )
        }
        row(3, visible = settingsInternationalEnabled) { above, below ->
            SettingsToggleRow(
                title = stringResource(R.string.feature_settings_international_wallet_title),
                summary = stringResource(R.string.feature_settings_international_wallet_summary),
                checked = settingsInternationalWalletEnabled,
                enabled = settingsInternationalEnabled,
                onCheckedChange = { enabled ->
                    settingsInternationalWalletEnabled = enabled
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            LspConfig.setSettingsInternationalWalletEnabled(context, enabled)
                        }
                    }
                },
                hasDividerAbove = above,
                hasDividerBelow = below,
            )
        }
        row(4, visible = settingsInternationalEnabled) { above, below ->
            SettingsToggleRow(
                title = stringResource(R.string.feature_settings_restore_domestic_about_device_title),
                summary = stringResource(R.string.feature_settings_restore_domestic_about_device_summary),
                checked = settingsRestoreDomesticAboutDeviceEnabled,
                enabled = settingsInternationalEnabled,
                onCheckedChange = { enabled ->
                    settingsRestoreDomesticAboutDeviceEnabled = enabled
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            LspConfig.setSettingsRestoreDomesticAboutDeviceEnabled(context, enabled)
                        }
                    }
                },
                hasDividerAbove = above,
                hasDividerBelow = below,
            )
        }
        row(5, visible = settingsInternationalEnabled) { above, below ->
            SettingsToggleRow(
                title = stringResource(R.string.feature_settings_restore_smart_lock_title),
                summary = stringResource(R.string.feature_settings_restore_smart_lock_summary),
                checked = settingsRestoreSmartLockEnabled,
                enabled = settingsInternationalEnabled,
                onCheckedChange = { enabled ->
                    settingsRestoreSmartLockEnabled = enabled
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            LspConfig.setSettingsRestoreSmartLockEnabled(context, enabled)
                        }
                    }
                },
                hasDividerAbove = above,
                hasDividerBelow = below,
            )
        }
        row(6, visible = settingsInternationalEnabled) { above, below ->
            SettingsToggleRow(
                title = stringResource(R.string.feature_settings_restore_domestic_auxiliary_functions_title),
                summary = stringResource(R.string.feature_settings_restore_domestic_auxiliary_functions_summary),
                checked = settingsRestoreDomesticAuxiliaryFunctionsEnabled,
                enabled = settingsInternationalEnabled,
                onCheckedChange = { enabled ->
                    if (settingsInternationalEnabled) {
                        settingsRestoreDomesticAuxiliaryFunctionsEnabled = enabled
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                LspConfig.setSettingsRestoreDomesticAuxiliaryFunctionsEnabled(context, enabled)
                            }
                        }
                    }
                },
                hasDividerAbove = above,
                hasDividerBelow = below,
            )
        }
    }
    }
}
