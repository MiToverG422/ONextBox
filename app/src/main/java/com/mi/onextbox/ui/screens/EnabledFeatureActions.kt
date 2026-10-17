package com.mi.onextbox.ui.screens

import android.content.Context
import com.mi.onextbox.R
import com.mi.onextbox.lsp.*
import com.mi.onextbox.errors.AppErrorTrackingSettings
import com.mi.onextbox.ui.common.applyLauncherLayoutUnlocked
import com.mi.onextbox.ui.common.applyPermissionMonitorVisibility
import com.mi.onextbox.ui.settings.FeatureSettingWriteQueue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** App-level notification: root-owned preference states must not keep stale values after quick edits. */
internal object EnabledFeatureEdits {
    private val events = MutableSharedFlow<Unit>(extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    val changes = events.asSharedFlow()
    fun notifyChanged() { events.tryEmit(Unit) }
}

/** A page session remembers non-boolean choices while their switch is temporarily off. */
internal class EnabledFeatureModeMemory {
    private val values = mutableMapOf<Int, Int>()
    fun next(key: Int, current: Int, enabled: Boolean, fallback: Int, offValue: Int = 0): Int {
        if (current != offValue) values[key] = current
        return if (enabled) values[key] ?: fallback else offValue
    }
}

internal class EnabledFeatureActions {
    private val modes = EnabledFeatureModeMemory()
    private val writers: Map<Int, suspend (Context, Boolean) -> Unit> = mapOf<Int, suspend (Context, Boolean) -> Unit>(
        SharedFeatureDefinitions.networkIdentity.action(),
        R.string.statusbar_order_title to { context, enabled -> StatusBarLayoutSettings.setOrderEnabled(context, enabled) },
        R.string.statusbar_sim_order_title to { context, enabled -> StatusBarLayoutSettings.saveSimOrder(context, enabled, StatusBarLayoutSettings.state(context).simOrder) },
        R.string.statusbar_margins_title to { context, enabled -> StatusBarLayoutSettings.save(context, StatusBarLayoutSettings.state(context).copy(marginsEnabled = enabled)) },
        R.string.statusbar_vertical_margins_title to { context, enabled -> StatusBarLayoutSettings.setVerticalMarginsEnabled(context, enabled) },
        R.string.launcher_disable_icon_menu_title to { context, enabled -> LauncherGestureSettings.save(context, LauncherGestureSettings.MENU, enabled) },
        R.string.launcher_widgets_menu_title to { context, enabled -> LauncherGestureSettings.save(context, LauncherGestureSettings.WIDGETS, enabled) },
        R.string.launcher_swipe_top_title to { context, enabled -> LauncherGestureSettings.save(context, LauncherGestureSettings.TOP, enabled) },
        R.string.feature_aod_enhance_toggle_title to { context, enabled -> LspConfig.setAodEnhanceEnabled(context, enabled) },
        R.string.feature_aod_all_day_unlock_title to { context, enabled -> LspConfig.setAodAllDayUnlockEnabled(context, enabled) },
        R.string.feature_aod_single_click_block_title to { context, enabled -> LspConfig.setAodSingleClickBlockEnabled(context, enabled) },
        R.string.feature_assistant_international_power_chord_title to { context, enabled -> LspConfig.setAssistantInternationalPowerChordEnabled(context, enabled) },
        R.string.feature_assistant_ok_google_compatibility_title to { context, enabled -> LspConfig.setOkGoogleHotwordCompatibilityEnabled(context, enabled) },
        R.string.feature_assistant_gesture_title to { context, enabled -> LspConfig.setAssistantGestureCircleEnabled(context, enabled) },
        R.string.feature_assistant_gesture_c17_title to { context, enabled -> LspConfig.setAssistantGestureCircleC17Enabled(context, enabled) },
        R.string.feature_athena_c17_swipe_up_protection_title to { context, enabled -> LspConfig.setAthenaC17SwipeUpProtectionEnabled(context, enabled) },
        R.string.feature_launcher_hide_widget_labels_title to { context, enabled -> LspConfig.setLauncherHideWidgetLabelsEnabled(context, enabled) },
        R.string.feature_launcher_search_compatibility_title to { context, enabled -> LspConfig.setLauncherSearchCompatibilityEnabled(context, enabled) },
        R.string.feature_hide_disconnected_bluetooth_title to { context, enabled -> LspConfig.setHideDisconnectedBluetoothEnabled(context, enabled) },
        R.string.feature_esim_region_bypass_title to { context, enabled -> LspConfig.setEsimRegionRestrictionBypassEnabled(context, enabled) },
        R.string.feature_esim_profile_limit_bypass_title to { context, enabled -> LspConfig.setEsimProfileLimitBypassEnabled(context, enabled) },
        R.string.feature_esim_confirmation_prompt_title to { context, enabled -> LspConfig.setEsimConfirmationCodePromptEnabled(context, enabled) },
        R.string.feature_file_manager_hide_secure_access_tip_title to { context, enabled -> LspConfig.setFileManagerHideSecureAccessTipEnabled(context, enabled) },
        R.string.feature_file_manager_native_picker_title to { context, enabled -> LspConfig.setFileManagerNativePickerEnabled(context, enabled) },
        R.string.feature_fluid_cloud_battery_title to { context, enabled -> LspConfig.setFluidCloudBatteryEnabled(context, enabled) },
        R.string.feature_fluid_cloud_material_title to { context, enabled -> LspConfig.setFluidCloudMaterialEnabled(context, enabled) },
        R.string.carrier_name_title to { context, enabled -> CarrierNameSettings.setEnabled(context, enabled) },
        SharedFeatureDefinitions.qsCorner.action(),
        R.string.keyboard_material_unlock_title to { context, enabled -> LspConfig.setKeyboardMaterialUnlocked(context, enabled) },
        R.string.keyboard_bottom_lift_fix_title to { context, enabled -> LspConfig.setKeyboardBottomLiftFixEnabled(context, enabled) },
        R.string.keyboard_hide_dismiss_title to { context, enabled -> LspConfig.setKeyboardDismissHidden(context, enabled) },
        R.string.keyboard_flag_emoji_title to { context, enabled -> LspConfig.setKeyboardFlagEmojiEnabled(context, enabled) },
        R.string.keyguard_face_tap_title to { context, enabled -> LspConfig.setKeyguardFeatureEnabled(context, LspConfig.KeyguardFeature.FaceTapUnlock, enabled) },
        R.string.keyguard_face_tap_animation_title to { context, enabled -> LspConfig.setKeyguardFeatureEnabled(context, LspConfig.KeyguardFeature.FaceTapAnimation, enabled) },
        R.string.keyguard_block_password_highlight_title to { context, enabled -> LspConfig.setKeyguardFeatureEnabled(context, LspConfig.KeyguardFeature.BlockPasswordHighlight, enabled) },
        R.string.feature_navigation_handle_length_title to { context, enabled -> LspConfig.setNavigationHandleCustomLengthEnabled(context, enabled) },
        R.string.feature_immersive_navigation_title to { context, enabled -> LspConfig.setImmersiveNavigationEnabled(context, enabled) },
        R.string.feature_navigation_handle_auto_hide_title to { context, enabled -> LspConfig.setNavigationHandleAutoHideEnabled(context, enabled) },
        R.string.feature_round_notification_clear_button_title to { context, enabled -> LspConfig.setRoundNotificationClearButtonEnabled(context, enabled) },
        R.string.feature_permanent_clock_seconds_title to { context, enabled -> LspConfig.setPermanentClockSecondsEnabled(context, enabled) },
        R.string.feature_express_no_mini_program_title to { context, enabled -> LspConfig.setExpressNoMiniProgramEnabled(context, enabled) },
        R.string.feature_permission_old_dialog_title to { context, enabled -> LspConfig.setPermissionFeatureEnabled(context, LspConfig.PermissionFeature.OldAppStartDialog, enabled) },
        R.string.feature_permission_always_allow_title to { context, enabled -> LspConfig.setPermissionFeatureEnabled(context, LspConfig.PermissionFeature.AlwaysAllowAppStart, enabled) },
        R.string.feature_permission_unlock_restricted_title to { context, enabled -> LspConfig.setPermissionFeatureEnabled(context, LspConfig.PermissionFeature.AutoUnlockRestrictedSettings, enabled) },
        R.string.feature_permission_disable_malicious_title to { context, enabled -> LspConfig.setPermissionFeatureEnabled(context, LspConfig.PermissionFeature.DisableMaliciousAppIntercept, enabled) },
        R.string.feature_wallpapers_red_one_entry_title to { context, enabled -> LspConfig.setWallpapersRedOneEntryEnabled(context, enabled) },
        R.string.wallpaper_motion_title to { context, enabled -> WallpaperMotionSettings.save(context, enabled, WallpaperMotionSettings.amount(context)) },
        R.string.theme_store_vip_title to { context, enabled -> ThemeStoreSettings.set(context, enabled) },
        R.string.theme_store_ads_title to { context, enabled -> ThemeStoreSettings.setAds(context, enabled) },
        R.string.secure_pay_hide_risk_cloud_title to { context, enabled -> SecurePaySettings.setEnabled(context, enabled) },
        R.string.game_assistant_promotions_title to { context, enabled -> GameAssistantSettings.set(context, GameAssistantSettings.PROMOTIONS, enabled) },
        R.string.game_assistant_realme_startup_title to { context, enabled -> GameAssistantSettings.set(context, GameAssistantSettings.REALME_STARTUP_ANIMATION, enabled) },
        R.string.game_assistant_unlimited_title to { context, enabled -> GameAssistantSettings.set(context, GameAssistantSettings.UNLIMITED, enabled) },
        R.string.game_assistant_music_title to { context, enabled -> GameAssistantSettings.set(context, GameAssistantSettings.MUSIC, enabled) },
        R.string.game_assistant_root_title to { context, enabled -> GameAssistantSettings.set(context, GameAssistantSettings.BLOCK_ROOT, enabled) },
        R.string.game_assistant_developer_title to { context, enabled -> GameAssistantSettings.set(context, GameAssistantSettings.DEVELOPER, enabled) },
        R.string.game_assistant_skins_title to { context, enabled -> GameAssistantSettings.set(context, GameAssistantSettings.SKINS, enabled) },
        R.string.feature_settings_title_collapsed_title to { context, enabled -> LspConfig.setSettingsTitleCollapsedEnabled(context, enabled) },
        R.string.feature_settings_app_info_card_title to { context, enabled -> LspConfig.setSettingsAppInfoCardEnabled(context, enabled) },
        R.string.feature_settings_restore_app_open_button_title to { context, enabled -> LspConfig.setSettingsRestoreAppOpenButtonEnabled(context, enabled) },
        R.string.feature_settings_skip_special_permission_risk_confirm_title to { context, enabled -> LspConfig.setSettingsSkipSpecialPermissionRiskConfirmEnabled(context, enabled) },
        R.string.feature_settings_c15_about_layout_title to { context, enabled -> LspConfig.setSettingsC15AboutLayoutEnabled(context, enabled) },
        R.string.feature_settings_unlock_refresh_rate_title to { context, enabled -> LspConfig.setSettingsRefreshRateUnlocked(context, enabled) },
        R.string.feature_settings_force_global_extreme_refresh_rate_title to { context, enabled -> LspConfig.setSettingsForceGlobalExtremeRefreshRateEnabled(context, enabled) },
        R.string.feature_extreme_refresh_165_title to { context, enabled -> LspConfig.setExtremeRefresh165Enabled(context, enabled) },
        R.string.feature_settings_force_google_title to { context, enabled -> LspConfig.setSettingsForceGoogleEntryEnabled(context, enabled) },
        R.string.feature_settings_international_title to { context, enabled -> LspConfig.setSettingsInternationalEnabled(context, enabled) },
        R.string.feature_settings_force_app_auto_start_title to { context, enabled -> LspConfig.setSettingsForceAppAutoStartEnabled(context, enabled) },
        R.string.feature_settings_international_wallet_title to { context, enabled -> LspConfig.setSettingsInternationalWalletEnabled(context, enabled) },
        R.string.feature_settings_restore_domestic_about_device_title to { context, enabled -> LspConfig.setSettingsRestoreDomesticAboutDeviceEnabled(context, enabled) },
        R.string.feature_settings_restore_smart_lock_title to { context, enabled -> LspConfig.setSettingsRestoreSmartLockEnabled(context, enabled) },
        R.string.feature_settings_restore_domestic_auxiliary_functions_title to { context, enabled -> LspConfig.setSettingsRestoreDomesticAuxiliaryFunctionsEnabled(context, enabled) },
        R.string.feature_settings_gms_region_bypass_title to { context, enabled -> LspConfig.setGmsRegionRestrictionBypassEnabled(context, enabled) },
        R.string.feature_app_market_region_bypass_title to { context, enabled -> LspConfig.setAppMarketRegionRestrictionBypassEnabled(context, enabled) },
        R.string.feature_app_market_simplify_recommendations_title to { context, enabled -> LspConfig.setAppMarketSimplifyRecommendationsEnabled(context, enabled) },
        R.string.feature_mobile_network_hide_phone_number_title to { context, enabled -> LspConfig.setMobileNetworkHidePhoneNumberEnabled(context, enabled) },
        R.string.feature_mobile_network_force_carrier_options_title to { context, enabled -> LspConfig.setMobileNetworkForceCarrierOptionsEnabled(context, enabled) },
        R.string.feature_mobile_network_hide_ai_link_boost_title to { context, enabled -> LspConfig.setMobileNetworkHideAiLinkBoostEnabled(context, enabled) },
        R.string.feature_mobile_network_hide_roaming_service_title to { context, enabled -> LspConfig.setMobileNetworkHideRoamingServiceEnabled(context, enabled) },
        R.string.feature_mobile_network_hide_high_data_sim_card_title to { context, enabled -> LspConfig.setMobileNetworkHideHighDataSimCardEnabled(context, enabled) },
        R.string.feature_system_root_detection_title to { context, enabled -> LspConfig.setSystemRootDetectionBlocked(context, enabled) },
        R.string.feature_native_notify_icon_title to { context, enabled -> LspConfig.setNativeNotifyIconEnabled(context, enabled) },
        R.string.feature_native_notification_bubbles_title to { context, enabled -> LspConfig.setNativeNotificationBubblesEnabled(context, enabled) },
        R.string.feature_native_power_menu_title to { context, enabled -> LspConfig.setSystemUiNativePowerMenuEnabled(context, enabled) },
        R.string.feature_force_native_clipboard_overlay_title to { context, enabled -> LspConfig.setSystemUiForceNativeClipboardOverlayEnabled(context, enabled) },
        R.string.feature_force_tonal_spot_title to { context, enabled -> LspConfig.setSystemUiForceTonalSpotEnabled(context, enabled) },
        R.string.feature_international_network_display_title to { context, enabled -> LspConfig.setSystemUiInternationalNetworkDisplayEnabled(context, enabled) },
        R.string.feature_only_data_sim_signal_title to { context, enabled -> DataSimSignalSettings.set(context, enabled) },
        R.string.feature_only_data_sim_independent_title to { context, enabled -> DataSimSignalSettings.setIndependentOnly(context, enabled) },
        R.string.feature_restore_c16_network_icon_order_title to { context, enabled -> LspConfig.setSystemUiRestoreC16NetworkIconOrderEnabled(context, enabled) },
        R.string.feature_hide_mobile_roaming_indicator_title to { context, enabled -> LspConfig.setSystemUiHideMobileRoamingIndicatorEnabled(context, enabled) },
        R.string.feature_hide_network_activity_indicator_title to { context, enabled -> LspConfig.setSystemUiHideNetworkActivityIndicatorEnabled(context, enabled) },
        R.string.feature_international_notification_style_title to { context, enabled -> LspConfig.setSystemUiInternationalNotificationStyleEnabled(context, enabled) },
        R.string.feature_hide_qs_edit_title to { context, enabled -> LspConfig.setSystemUiHideQsEditEnabled(context, enabled) },
        R.string.feature_hide_qs_settings_title to { context, enabled -> LspConfig.setSystemUiHideQsSettingsEnabled(context, enabled) },
        R.string.feature_hide_qs_top_carrier_title to { context, enabled -> LspConfig.setSystemUiHideQsTopCarrierEnabled(context, enabled) },
        R.string.feature_hide_qs_more_title to { context, enabled -> LspConfig.setSystemUiHideQsMoreEnabled(context, enabled) },
        R.string.feature_aosp_back_panel_title to { context, enabled -> LspConfig.setAospBackPanelEnabled(context, enabled) },
        R.string.small_window_hide_recents to { context, enabled -> LspConfig.setSmallWindowFeatureEnabled(context, LspConfig.SmallWindowFeature.HideRecents, enabled) },
        R.string.small_window_mute_stashed to { context, enabled -> LspConfig.setSmallWindowFeatureEnabled(context, LspConfig.SmallWindowFeature.MuteStashed, enabled) },
        R.string.small_window_unlimited_count to { context, enabled -> LspConfig.setSmallWindowFeatureEnabled(context, LspConfig.SmallWindowFeature.UnlimitedCount, enabled) },
        R.string.small_window_unlimited_frame_rate to { context, enabled -> LspConfig.setSmallWindowFeatureEnabled(context, LspConfig.SmallWindowFeature.UnlimitedFrameRate, enabled) },
        R.string.small_window_keep_running to { context, enabled -> LspConfig.setSmallWindowFeatureEnabled(context, LspConfig.SmallWindowFeature.KeepRunning, enabled) },
        R.string.small_window_white_bar to { context, enabled -> LspConfig.setSmallWindowFeatureEnabled(context, LspConfig.SmallWindowFeature.WhiteBar, enabled) },
        R.string.small_window_safe_inset to { context, enabled -> LspConfig.setSmallWindowFeatureEnabled(context, LspConfig.SmallWindowFeature.SafeEdgeInset, enabled) },
        R.string.small_window_landscape_ratio to { context, enabled -> LspConfig.setSmallWindowFeatureEnabled(context, LspConfig.SmallWindowFeature.LandscapeRatio, enabled) },
        R.string.small_window_larger_size to { context, enabled -> LspConfig.setSmallWindowFeatureEnabled(context, LspConfig.SmallWindowFeature.LargerSize, enabled) },
        R.string.small_window_compact_caption to { context, enabled -> LspConfig.setSmallWindowFeatureEnabled(context, LspConfig.SmallWindowFeature.CompactCaption, enabled) },
        R.string.notification_removal_overlay to { context, enabled -> LspConfig.setNotificationRemovalEnabled(context, LspConfig.NotificationRemovalFeature.Overlay, enabled) },
        R.string.notification_removal_vpn to { context, enabled -> LspConfig.setNotificationRemovalEnabled(context, LspConfig.NotificationRemovalFeature.Vpn, enabled) },
        R.string.notification_removal_developer to { context, enabled -> LspConfig.setNotificationRemovalEnabled(context, LspConfig.NotificationRemovalFeature.DeveloperMode, enabled) },
        R.string.notification_removal_charging to { context, enabled -> LspConfig.setNotificationRemovalEnabled(context, LspConfig.NotificationRemovalFeature.ChargingCompleted, enabled) },
        R.string.notification_removal_flashlight to { context, enabled -> LspConfig.setNotificationRemovalEnabled(context, LspConfig.NotificationRemovalFeature.Flashlight, enabled) },
        R.string.notification_removal_consumption to { context, enabled -> LspConfig.setNotificationRemovalEnabled(context, LspConfig.NotificationRemovalFeature.HighBatteryConsumption, enabled) },
        R.string.notification_removal_performance to { context, enabled -> LspConfig.setNotificationRemovalEnabled(context, LspConfig.NotificationRemovalFeature.HighPerformance, enabled) },
        R.string.notification_removal_dnd to { context, enabled -> LspConfig.setNotificationRemovalEnabled(context, LspConfig.NotificationRemovalFeature.DoNotDisturb, enabled) },
        R.string.notification_removal_hotspot to { context, enabled -> LspConfig.setNotificationRemovalEnabled(context, LspConfig.NotificationRemovalFeature.HotspotPowerConsumption, enabled) },
        R.string.notification_removal_mute to { context, enabled -> LspConfig.setNotificationRemovalEnabled(context, LspConfig.NotificationRemovalFeature.MuteNotifications, enabled) },
        R.string.notification_removal_gt to { context, enabled -> LspConfig.setNotificationRemovalEnabled(context, LspConfig.NotificationRemovalFeature.GtMode, enabled) },
        R.string.installer_enable to { context, enabled -> LspConfig.setInstallerFeatureEnabled(context, LspConfig.InstallerFeature.Enabled, enabled) },
        R.string.installer_uninstall to { context, enabled -> LspConfig.setInstallerFeatureEnabled(context, LspConfig.InstallerFeature.Uninstall, enabled) },
        R.string.installer_session to { context, enabled -> LspConfig.setInstallerFeatureEnabled(context, LspConfig.InstallerFeature.Session, enabled) },
        R.string.installer_intercept_system to { context, enabled -> LspConfig.setInstallerFeatureEnabled(context, LspConfig.InstallerFeature.InterceptSystem, enabled) },
        R.string.installer_remove_default_policy to { context, enabled -> LspConfig.setInstallerFeatureEnabled(context, LspConfig.InstallerFeature.RemoveDefaultAppPolicy, enabled) },
        R.string.battery_show_cycle_count_title to { context, enabled -> LspConfig.setBatteryFeatureEnabled(context, LspConfig.BatteryFeature.ShowCycleCount, enabled) },
        R.string.battery_remove_restrict_plugin_title to { context, enabled -> LspConfig.setBatteryFeatureEnabled(context, LspConfig.BatteryFeature.RemoveRestrictPlugin, enabled) },
        R.string.battery_restore_default_whitelist_title to { context, enabled -> LspConfig.setBatteryFeatureEnabled(context, LspConfig.BatteryFeature.RestoreDefaultWhitelist, enabled) },
        R.string.feature_status_bar_double_tap_to_top_title to { context, enabled -> LspConfig.setStatusBarInteractionFeatureEnabled(context, LspConfig.StatusBarInteractionFeature.DoubleTapToTop, enabled) },
        R.string.feature_status_bar_remove_to_top_whitelist_title to { context, enabled -> LspConfig.setStatusBarInteractionFeatureEnabled(context, LspConfig.StatusBarInteractionFeature.RemoveToTopWhitelist, enabled) },
        R.string.keyguard_aod_screenshot_title to { context, enabled -> LspConfig.setKeyguardFeatureEnabled(context, LspConfig.KeyguardFeature.AodScreenshot, enabled) },
        R.string.keyguard_screen_off_recording_title to { context, enabled -> LspConfig.setKeyguardFeatureEnabled(context, LspConfig.KeyguardFeature.ScreenOffRecording, enabled) },
        R.string.feature_recent_task_radius_title to { context, enabled -> LspConfig.setRecentTaskRadiusEnabled(context, enabled) },
        R.string.feature_aosp_volume_title to { context, enabled -> AospVolumeSettings.set(context, enabled) },
        SharedFeatureDefinitions.stackedSignal.action(),
        R.string.app_errors_enable to { context, enabled -> AppErrorTrackingSettings.setEnabled(context, enabled) },
        R.string.feature_assistant_native_combined_title to { context, enabled -> LspConfig.setAssistantNativePowerEnabled(context, enabled); LspConfig.setAssistantNativeCircleEnabled(context, enabled) },
        R.string.feature_launcher_layout_unlock_title to { context, enabled -> check(applyLauncherLayoutUnlocked(enabled).success) },
        R.string.feature_permission_monitor_title to { context, enabled -> check(applyPermissionMonitorVisibility(enabled).success) },
        R.string.fluid_media_blacklist_title to { context, enabled -> FluidCloudMediaSettings.setEnabled(context, enabled) },
        R.string.fluid_browser_title to { context, enabled -> FluidCloudBrowserSettings.setEnabled(context, enabled) },
        R.string.feature_google_messages_gemini_title to { context, enabled -> GoogleMessagesConfig.setEnabled(context, GoogleMessagesConfig.Switch.Gemini, enabled) },
        R.string.feature_google_messages_copy_otp_title to { context, enabled -> GoogleMessagesConfig.setEnabled(context, GoogleMessagesConfig.Switch.CopyOtp, enabled) },
        R.string.feature_large_network_type_title to { context, enabled -> LargeNetworkTypeSettings.set(context, enabled) },
        R.string.feature_large_network_artwork_title to { context, enabled -> LargeNetworkTypeSettings.setArtwork(context, enabled) },
        R.string.feature_large_network_lte_title to { context, enabled -> LargeNetworkTypeSettings.setLte(context, enabled) },
        R.string.feature_large_network_font_title to { context, enabled -> LargeNetworkTypeSettings.setSystemFont(context, enabled) },
        R.string.feature_large_network_bold_title to { context, enabled -> LargeNetworkTypeSettings.setBold(context, enabled) },
        R.string.launcher_custom_grid_title to { context, enabled -> LauncherGridSettings.save(context, LauncherGridSettings.state(context).copy(enabled = enabled)) },
        R.string.oshare_unlimited_title to { context, enabled -> OShareSettings.setEnabled(context, enabled) },
        R.string.phone_manager_skip_countdown_title to { context, enabled -> PhoneManagerSettings.setEnabled(context, enabled) },
        R.string.feature_screen_capture_title to { context, enabled -> ScreenCaptureSettings.set(context, enabled) },
        R.string.feature_assistant_power_title to { context, enabled ->
            val next = modes.next(R.string.feature_assistant_power_title, LspConfig.getAssistantPowerMode(context), enabled, LspConfig.ASSISTANT_POWER_MODE_SYSTEM_DEFAULT, LspConfig.ASSISTANT_POWER_MODE_NONE)
            LspConfig.setAssistantPowerMode(context, next)
        },
        R.string.feature_launcher_taskbar_search_box_title to { context, enabled ->
            val next = modes.next(R.string.feature_launcher_taskbar_search_box_title, LspConfig.getLauncherSearchBarMode(context), enabled, LspConfig.LAUNCHER_SEARCH_BAR_MODE_INTERNATIONAL)
            LspConfig.setLauncherSearchBarMode(context, next)
        },
        R.string.feature_monet_color_spec_title to { context, enabled ->
            val next = modes.next(R.string.feature_monet_color_spec_title, LspConfig.getSystemUiMonetColorSpecMode(context), enabled, LspConfig.SYSTEMUI_MONET_COLOR_SPEC_2025)
            LspConfig.setSystemUiMonetColorSpecMode(context, next)
        },
    ) + LauncherFeatureItems.all.associate { item ->
        val write: suspend (Context, Boolean) -> Unit = { context, enabled -> LspConfig.setLauncherFeatureEnabled(context, item.feature, enabled) }
        item.title to write
    } + TrafficFeatureItems.all.associate { item ->
        val write: suspend (Context, Boolean) -> Unit = { context, enabled -> LspConfig.setTrafficFeatureEnabled(context, item.feature, enabled) }
        item.title to write
    } + SoftwareUpdateFeatureItems.all.associate { item ->
        val write: suspend (Context, Boolean) -> Unit = { context, enabled -> SoftwareUpdateSettings.setEnabled(context, item.feature, enabled) }
        item.title to write
    }

    internal fun supports(title: Int) = title in writers

    fun enqueue(context: Context, entry: EnabledFeatureCatalog.Entry, enabled: Boolean) =
        FeatureSettingWriteQueue.application.enqueue {
            runBlocking {
                try {
                    val configured = EnabledFeatureCatalog.configured(context)
                    check(!enabled || EnabledFeatureCatalog.dependenciesActive(entry.title, configured))
                    if (enabled && entry.title == R.string.feature_esim_region_bypass_title) {
                        check(LspConfig.isEsimRegionRestrictionBypassAvailable(context))
                    }
                    writers.getValue(entry.title)(context, enabled)
                    check(entry.read(context) == enabled)
                } finally {
                    // Also publish on partial failure so all pages reflect the actual stored state.
                    EnabledFeatureEdits.notifyChanged()
                }
            }
        }
}
