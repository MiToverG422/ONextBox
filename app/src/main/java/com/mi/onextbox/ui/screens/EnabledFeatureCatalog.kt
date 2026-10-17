package com.mi.onextbox.ui.screens

import android.content.Context
import com.mi.onextbox.R
import com.mi.onextbox.lsp.*
import com.mi.onextbox.errors.AppErrorTrackingSettings
import com.mi.onextbox.ui.common.queryLauncherLayoutUnlocked
import com.mi.onextbox.ui.common.queryPermissionMonitorVisibility

/** Explicit configuration inventory. Never treat configuration as proof of Hook success. */
internal object EnabledFeatureCatalog {
    data class Entry(
        val title: Int,
        val page: FeaturePageMode,
        val parents: Set<Int> = emptySet(),
        val excludes: Set<Int> = emptySet(),
        val read: suspend (Context) -> Boolean,
    )
    val entries = listOf(
        SharedFeatureDefinitions.networkIdentity.catalogEntry(),
        SharedFeatureDefinitions.qsCorner.catalogEntry(),
        Entry(R.string.statusbar_order_title, FeaturePageMode.SystemUiStatusBar) { StatusBarLayoutSettings.state(it).orderEnabled },
        Entry(R.string.statusbar_sim_order_title, FeaturePageMode.SystemUiIconOrder, parents = setOf(R.string.statusbar_order_title)) {
            StatusBarLayoutSettings.state(it).simEnabled
        },
        Entry(R.string.statusbar_margins_title, FeaturePageMode.SystemUiStatusBar) { StatusBarLayoutSettings.state(it).marginsEnabled },
        Entry(R.string.statusbar_vertical_margins_title, FeaturePageMode.SystemUiStatusBar) { StatusBarLayoutSettings.state(it).verticalMarginsEnabled },
        Entry(R.string.carrier_name_title, FeaturePageMode.SystemUiStatusBar) { CarrierNameSettings.state(it).enabled },
        Entry(R.string.launcher_disable_icon_menu_title, FeaturePageMode.DesktopIcons) { LauncherGestureSettings.menu(it) },
        Entry(R.string.launcher_widgets_menu_title, FeaturePageMode.DesktopIcons) { LauncherGestureSettings.widgets(it) },
        Entry(R.string.launcher_swipe_top_title, FeaturePageMode.DesktopRecent) { LauncherGestureSettings.top(it) },
        Entry(R.string.wallpaper_motion_title, FeaturePageMode.Wallpapers) { context -> WallpaperMotionSettings.enabled(context) },
        Entry(R.string.feature_aod_enhance_toggle_title, FeaturePageMode.Aod) { context -> LspConfig.isAodEnhanceEnabled(context) },
        Entry(R.string.feature_aod_all_day_unlock_title, FeaturePageMode.Aod) { context -> LspConfig.isAodAllDayUnlockEnabled(context) },
        Entry(R.string.feature_aod_single_click_block_title, FeaturePageMode.Aod) { context -> LspConfig.isAodSingleClickBlockEnabled(context) },
        Entry(R.string.feature_aosp_volume_title, FeaturePageMode.SystemUiNative) { context -> AospVolumeSettings.enabled(context) },
        SharedFeatureDefinitions.stackedSignal.catalogEntry(),
        Entry(R.string.app_errors_enable, FeaturePageMode.AppErrors) { context -> AppErrorTrackingSettings.isEnabled(context) },
        Entry(R.string.feature_assistant_native_combined_title, FeaturePageMode.Assistant) { context -> LspConfig.isAssistantNativePowerEnabled(context) && LspConfig.isAssistantNativeCircleEnabled(context) },
        Entry(R.string.feature_assistant_international_power_chord_title, FeaturePageMode.Assistant, parents = setOf(R.string.feature_assistant_native_combined_title)) { context -> LspConfig.isAssistantInternationalPowerChordEnabled(context) },
        Entry(R.string.feature_assistant_ok_google_compatibility_title, FeaturePageMode.Assistant) { context -> LspConfig.isOkGoogleHotwordCompatibilityEnabled(context) },
        Entry(R.string.feature_assistant_power_title, FeaturePageMode.Assistant) { context -> LspConfig.getAssistantPowerMode(context) != LspConfig.ASSISTANT_POWER_MODE_NONE },
        Entry(R.string.feature_assistant_gesture_title, FeaturePageMode.Assistant) { context -> LspConfig.isAssistantGestureCircleEnabled(context) },
        Entry(R.string.feature_assistant_gesture_c17_title, FeaturePageMode.Assistant) { context -> LspConfig.isAssistantGestureCircleC17Enabled(context) },
        Entry(R.string.feature_athena_c17_swipe_up_protection_title, FeaturePageMode.Athena) { context -> LspConfig.isAthenaC17SwipeUpProtectionEnabled(context) },
        Entry(R.string.feature_launcher_layout_unlock_title, FeaturePageMode.DesktopLayout) { context -> queryLauncherLayoutUnlocked() },
        Entry(R.string.feature_launcher_hide_widget_labels_title, FeaturePageMode.DesktopLayout) { context -> LspConfig.isLauncherHideWidgetLabelsEnabled(context) },
        Entry(R.string.feature_launcher_search_compatibility_title, FeaturePageMode.DesktopLayout, parents = setOf(R.string.feature_launcher_taskbar_search_box_title)) { context -> LspConfig.isLauncherSearchCompatibilityEnabled(context) },
        Entry(R.string.feature_hide_disconnected_bluetooth_title, FeaturePageMode.SystemUiStatusBar) { context -> LspConfig.isHideDisconnectedBluetoothEnabled(context) },
        Entry(R.string.feature_esim_region_bypass_title, FeaturePageMode.Esim) { context -> LspConfig.isEsimRegionRestrictionBypassEnabled(context) },
        Entry(R.string.feature_esim_profile_limit_bypass_title, FeaturePageMode.Esim) { context -> LspConfig.isEsimProfileLimitBypassEnabled(context) },
        Entry(R.string.feature_esim_confirmation_prompt_title, FeaturePageMode.Esim) { context -> LspConfig.isEsimConfirmationCodePromptEnabled(context) },
        Entry(R.string.feature_file_manager_hide_secure_access_tip_title, FeaturePageMode.FileManager) { context -> LspConfig.isFileManagerHideSecureAccessTipEnabled(context) },
        Entry(R.string.feature_file_manager_native_picker_title, FeaturePageMode.FileManager) { context -> LspConfig.isFileManagerNativePickerEnabled(context) },
        Entry(R.string.feature_fluid_cloud_battery_title, FeaturePageMode.SystemUiStatusBar) { context -> LspConfig.isFluidCloudBatteryEnabled(context) },
        Entry(R.string.feature_fluid_cloud_material_title, FeaturePageMode.SystemUiFluidCloud) { context -> LspConfig.isFluidCloudMaterialEnabled(context) },
        Entry(R.string.fluid_media_blacklist_title, FeaturePageMode.SystemUiFluidCloud) { context -> FluidCloudMediaSettings.isEnabled(context) },
        Entry(R.string.fluid_browser_title, FeaturePageMode.SystemUiFluidCloud) { context -> FluidCloudBrowserSettings.isEnabled(context) },
        Entry(R.string.feature_google_messages_gemini_title, FeaturePageMode.GoogleMessages) { context -> GoogleMessagesConfig.isEnabled(context, GoogleMessagesConfig.Switch.Gemini) },
        Entry(R.string.feature_google_messages_copy_otp_title, FeaturePageMode.GoogleMessages) { context -> GoogleMessagesConfig.isEnabled(context, GoogleMessagesConfig.Switch.CopyOtp) },
        Entry(R.string.keyboard_material_unlock_title, FeaturePageMode.Keyboard) { context -> LspConfig.isKeyboardMaterialUnlocked(context) },
        Entry(R.string.keyboard_bottom_lift_fix_title, FeaturePageMode.Keyboard) { context -> LspConfig.isKeyboardBottomLiftFixEnabled(context) },
        Entry(R.string.keyboard_hide_dismiss_title, FeaturePageMode.Keyboard) { context -> LspConfig.isKeyboardDismissHidden(context) },
        Entry(R.string.keyboard_flag_emoji_title, FeaturePageMode.Keyboard) { context -> LspConfig.isKeyboardFlagEmojiEnabled(context) },
        Entry(R.string.keyguard_face_tap_title, FeaturePageMode.SystemUiLockScreen) { context -> LspConfig.isKeyguardFeatureEnabled(context, LspConfig.KeyguardFeature.FaceTapUnlock) },
        Entry(R.string.keyguard_face_tap_animation_title, FeaturePageMode.SystemUiLockScreen, parents = setOf(R.string.keyguard_face_tap_title)) { context -> LspConfig.isKeyguardFeatureEnabled(context, LspConfig.KeyguardFeature.FaceTapAnimation) },
        Entry(R.string.keyguard_block_password_highlight_title, FeaturePageMode.SystemUiLockScreen) { context -> LspConfig.isKeyguardFeatureEnabled(context, LspConfig.KeyguardFeature.BlockPasswordHighlight) },
        Entry(R.string.feature_large_network_type_title, FeaturePageMode.SystemUiStatusBar) { context -> LargeNetworkTypeSettings.enabled(context) },
        Entry(R.string.feature_large_network_artwork_title, FeaturePageMode.SystemUiStatusBar, parents = setOf(R.string.feature_large_network_type_title)) { context -> LargeNetworkTypeSettings.usesArtwork(context) },
        Entry(R.string.feature_large_network_lte_title, FeaturePageMode.SystemUiStatusBar, parents = setOf(R.string.feature_large_network_artwork_title)) { context -> LargeNetworkTypeSettings.usesLte(context) },
        Entry(R.string.feature_large_network_font_title, FeaturePageMode.SystemUiStatusBar, parents = setOf(R.string.feature_large_network_type_title), excludes = setOf(R.string.feature_large_network_artwork_title)) { context -> LargeNetworkTypeSettings.followsSystemFont(context) },
        Entry(R.string.feature_large_network_bold_title, FeaturePageMode.SystemUiStatusBar, parents = setOf(R.string.feature_large_network_font_title)) { context -> LargeNetworkTypeSettings.usesBold(context) },
        Entry(R.string.launcher_custom_grid_title, FeaturePageMode.DesktopLayout) { context -> LauncherGridSettings.state(context).enabled },
        Entry(R.string.feature_navigation_handle_length_title, FeaturePageMode.SystemUiNavigationBar) { context -> LspConfig.isNavigationHandleCustomLengthEnabled(context) },
        Entry(R.string.feature_immersive_navigation_title, FeaturePageMode.SystemUiNavigationBar, parents = setOf(R.string.feature_navigation_handle_length_title)) { context -> LspConfig.isImmersiveNavigationEnabled(context) },
        Entry(R.string.feature_navigation_handle_auto_hide_title, FeaturePageMode.SystemUiNavigationBar, parents = setOf(R.string.feature_navigation_handle_length_title)) { context -> LspConfig.isNavigationHandleAutoHideEnabled(context) },
        Entry(R.string.feature_round_notification_clear_button_title, FeaturePageMode.SystemUiNotificationCenter) { context -> LspConfig.isRoundNotificationClearButtonEnabled(context) },
        Entry(R.string.oshare_unlimited_title, FeaturePageMode.OShare) { context -> OShareSettings.isEnabled(context) },
        Entry(R.string.feature_permanent_clock_seconds_title, FeaturePageMode.SystemUiStatusBar) { context -> LspConfig.isPermanentClockSecondsEnabled(context) },
        Entry(R.string.phone_manager_skip_countdown_title, FeaturePageMode.PhoneManager) { context -> PhoneManagerSettings.isEnabled(context) },
        Entry(R.string.theme_store_vip_title, FeaturePageMode.ThemeStore) { context -> ThemeStoreSettings.enabled(context) },
        Entry(R.string.theme_store_ads_title, FeaturePageMode.ThemeStore) { context -> ThemeStoreSettings.adsEnabled(context) },
        Entry(R.string.secure_pay_hide_risk_cloud_title, FeaturePageMode.SecurePay) { context -> SecurePaySettings.enabled(context) },
        Entry(R.string.game_assistant_promotions_title, FeaturePageMode.GameAssistant) { GameAssistantSettings.enabled(it, GameAssistantSettings.PROMOTIONS) },
        Entry(R.string.game_assistant_realme_startup_title, FeaturePageMode.GameAssistant) { GameAssistantSettings.enabled(it, GameAssistantSettings.REALME_STARTUP_ANIMATION) },
        Entry(R.string.game_assistant_unlimited_title, FeaturePageMode.GameAssistant) { GameAssistantSettings.enabled(it, GameAssistantSettings.UNLIMITED) },
        Entry(R.string.game_assistant_music_title, FeaturePageMode.GameAssistant) { GameAssistantSettings.enabled(it, GameAssistantSettings.MUSIC) },
        Entry(R.string.game_assistant_root_title, FeaturePageMode.GameAssistant) { GameAssistantSettings.enabled(it, GameAssistantSettings.BLOCK_ROOT) },
        Entry(R.string.game_assistant_developer_title, FeaturePageMode.GameAssistant) { GameAssistantSettings.enabled(it, GameAssistantSettings.DEVELOPER) },
        Entry(R.string.game_assistant_skins_title, FeaturePageMode.GameAssistant) { GameAssistantSettings.enabled(it, GameAssistantSettings.SKINS) },
        Entry(R.string.feature_express_no_mini_program_title, FeaturePageMode.QuickAppServices) { context -> LspConfig.isExpressNoMiniProgramEnabled(context) },
        Entry(R.string.feature_screen_capture_title, FeaturePageMode.AndroidSystem) { context -> ScreenCaptureSettings.enabled(context) },
        Entry(R.string.feature_permission_old_dialog_title, FeaturePageMode.SecurityPermission) { context -> LspConfig.isPermissionFeatureEnabled(context, LspConfig.PermissionFeature.OldAppStartDialog) },
        Entry(R.string.feature_permission_always_allow_title, FeaturePageMode.SecurityPermission) { context -> LspConfig.isPermissionFeatureEnabled(context, LspConfig.PermissionFeature.AlwaysAllowAppStart) },
        Entry(R.string.feature_permission_unlock_restricted_title, FeaturePageMode.SecurityPermission) { context -> LspConfig.isPermissionFeatureEnabled(context, LspConfig.PermissionFeature.AutoUnlockRestrictedSettings) },
        Entry(R.string.feature_permission_disable_malicious_title, FeaturePageMode.SecurityPermission) { context -> LspConfig.isPermissionFeatureEnabled(context, LspConfig.PermissionFeature.DisableMaliciousAppIntercept) },
        Entry(R.string.feature_wallpapers_red_one_entry_title, FeaturePageMode.Wallpapers) { context -> LspConfig.isWallpapersRedOneEntryEnabled(context) },
        Entry(R.string.feature_settings_title_collapsed_title, FeaturePageMode.SettingsAppearance) { context -> LspConfig.isSettingsTitleCollapsedEnabled(context) },
        Entry(R.string.feature_permission_monitor_title, FeaturePageMode.SettingsPermissions) { context -> queryPermissionMonitorVisibility() },
        Entry(R.string.feature_settings_app_info_card_title, FeaturePageMode.SettingsApps) { context -> LspConfig.isSettingsAppInfoCardEnabled(context) },
        Entry(R.string.feature_settings_restore_app_open_button_title, FeaturePageMode.SettingsApps) { context -> LspConfig.isSettingsRestoreAppOpenButtonEnabled(context) },
        Entry(R.string.feature_settings_skip_special_permission_risk_confirm_title, FeaturePageMode.SettingsPermissions) { context -> LspConfig.isSettingsSkipSpecialPermissionRiskConfirmEnabled(context) },
        Entry(R.string.feature_settings_c15_about_layout_title, FeaturePageMode.SettingsAboutDevice) { context -> LspConfig.isSettingsC15AboutLayoutEnabled(context) },
        Entry(R.string.feature_settings_unlock_refresh_rate_title, FeaturePageMode.SettingsAppearance) { context -> LspConfig.isSettingsRefreshRateUnlocked(context) },
        Entry(R.string.feature_settings_force_global_extreme_refresh_rate_title, FeaturePageMode.SettingsAppearance) { context -> LspConfig.isSettingsForceGlobalExtremeRefreshRateEnabled(context) },
        Entry(R.string.feature_extreme_refresh_165_title, FeaturePageMode.SettingsAppearance) { context -> LspConfig.isExtremeRefresh165Enabled(context) },
        Entry(R.string.feature_settings_force_google_title, FeaturePageMode.SettingsRegion) { context -> LspConfig.isSettingsForceGoogleEntryEnabled(context) },
        Entry(R.string.feature_settings_international_title, FeaturePageMode.SettingsRegion) { context -> LspConfig.isSettingsInternationalEnabled(context) },
        Entry(R.string.feature_settings_force_app_auto_start_title, FeaturePageMode.SettingsRegion, parents = setOf(R.string.feature_settings_international_title)) { context -> LspConfig.isSettingsForceAppAutoStartEnabled(context) },
        Entry(R.string.feature_settings_international_wallet_title, FeaturePageMode.SettingsRegion, parents = setOf(R.string.feature_settings_international_title)) { context -> LspConfig.isSettingsInternationalWalletEnabled(context) },
        Entry(R.string.feature_settings_restore_domestic_about_device_title, FeaturePageMode.SettingsRegion, parents = setOf(R.string.feature_settings_international_title)) { context -> LspConfig.isSettingsRestoreDomesticAboutDeviceEnabled(context) },
        Entry(R.string.feature_settings_restore_smart_lock_title, FeaturePageMode.SettingsRegion, parents = setOf(R.string.feature_settings_international_title)) { context -> LspConfig.isSettingsRestoreSmartLockEnabled(context) },
        Entry(R.string.feature_settings_restore_domestic_auxiliary_functions_title, FeaturePageMode.SettingsRegion, parents = setOf(R.string.feature_settings_international_title)) { context -> LspConfig.isSettingsRestoreDomesticAuxiliaryFunctionsEnabled(context) },
        Entry(R.string.feature_settings_gms_region_bypass_title, FeaturePageMode.AndroidSystem) { context -> LspConfig.isGmsRegionRestrictionBypassEnabled(context) },
        Entry(R.string.feature_app_market_region_bypass_title, FeaturePageMode.AppMarket) { context -> LspConfig.isAppMarketRegionRestrictionBypassEnabled(context) },
        Entry(R.string.feature_app_market_simplify_recommendations_title, FeaturePageMode.AppMarket) { context -> LspConfig.isAppMarketSimplifyRecommendationsEnabled(context) },
        Entry(R.string.feature_mobile_network_hide_phone_number_title, FeaturePageMode.MobileNetwork) { context -> LspConfig.isMobileNetworkHidePhoneNumberEnabled(context) },
        Entry(R.string.feature_mobile_network_force_carrier_options_title, FeaturePageMode.MobileNetwork) { context -> LspConfig.isMobileNetworkForceCarrierOptionsEnabled(context) },
        Entry(R.string.feature_mobile_network_hide_ai_link_boost_title, FeaturePageMode.MobileNetwork) { context -> LspConfig.isMobileNetworkHideAiLinkBoostEnabled(context) },
        Entry(R.string.feature_mobile_network_hide_roaming_service_title, FeaturePageMode.MobileNetwork) { context -> LspConfig.isMobileNetworkHideRoamingServiceEnabled(context) },
        Entry(R.string.feature_mobile_network_hide_high_data_sim_card_title, FeaturePageMode.MobileNetwork) { context -> LspConfig.isMobileNetworkHideHighDataSimCardEnabled(context) },
        Entry(R.string.feature_system_root_detection_title, FeaturePageMode.AndroidSystem) { context -> LspConfig.isSystemRootDetectionBlocked(context) },
        Entry(R.string.feature_native_notify_icon_title, FeaturePageMode.SystemUiNative) { context -> LspConfig.isNativeNotifyIconEnabled(context) },
        Entry(R.string.feature_native_notification_bubbles_title, FeaturePageMode.SystemUiNative) { context -> LspConfig.isNativeNotificationBubblesEnabled(context) },
        Entry(R.string.feature_native_power_menu_title, FeaturePageMode.SystemUiNative) { context -> LspConfig.isSystemUiNativePowerMenuEnabled(context) },
        Entry(R.string.feature_force_native_clipboard_overlay_title, FeaturePageMode.SystemUiNative) { context -> LspConfig.isSystemUiForceNativeClipboardOverlayEnabled(context) },
        Entry(R.string.feature_force_tonal_spot_title, FeaturePageMode.SystemUiDynamicColor) { context -> LspConfig.isSystemUiForceTonalSpotEnabled(context) },
        Entry(R.string.feature_international_network_display_title, FeaturePageMode.SystemUiStatusBar) { context -> LspConfig.isSystemUiInternationalNetworkDisplayEnabled(context) },
        Entry(R.string.feature_only_data_sim_signal_title, FeaturePageMode.SystemUiStatusBar) { context -> DataSimSignalSettings.enabled(context) },
        Entry(R.string.feature_only_data_sim_independent_title, FeaturePageMode.SystemUiStatusBar,
            parents = setOf(R.string.feature_only_data_sim_signal_title)) { context -> DataSimSignalSettings.independentOnly(context) },
        Entry(R.string.feature_restore_c16_network_icon_order_title, FeaturePageMode.SystemUiStatusBar, excludes = setOf(R.string.statusbar_order_title)) { context -> LspConfig.isSystemUiRestoreC16NetworkIconOrderEnabled(context) },
        Entry(R.string.feature_hide_mobile_roaming_indicator_title, FeaturePageMode.SystemUiStatusBar) { context -> LspConfig.isSystemUiHideMobileRoamingIndicatorEnabled(context) },
        Entry(R.string.feature_hide_network_activity_indicator_title, FeaturePageMode.SystemUiStatusBar) { context -> LspConfig.isSystemUiHideNetworkActivityIndicatorEnabled(context) },
        Entry(R.string.feature_international_notification_style_title, FeaturePageMode.SystemUiNotificationCenter) { context -> LspConfig.isSystemUiInternationalNotificationStyleEnabled(context) },
        Entry(R.string.feature_hide_qs_edit_title, FeaturePageMode.SystemUiControlCenter) { context -> LspConfig.isSystemUiHideQsEditEnabled(context) },
        Entry(R.string.feature_hide_qs_settings_title, FeaturePageMode.SystemUiControlCenter) { context -> LspConfig.isSystemUiHideQsSettingsEnabled(context) },
        Entry(R.string.feature_hide_qs_top_carrier_title, FeaturePageMode.SystemUiControlCenter) { context -> LspConfig.isSystemUiHideQsTopCarrierEnabled(context) },
        Entry(R.string.feature_hide_qs_more_title, FeaturePageMode.SystemUiControlCenter) { context -> LspConfig.isSystemUiHideQsMoreEnabled(context) },
        Entry(R.string.feature_aosp_back_panel_title, FeaturePageMode.SystemUiNative) { context -> LspConfig.isAospBackPanelEnabled(context) },
        Entry(R.string.small_window_hide_recents, FeaturePageMode.SystemUiSmallWindow) { context -> LspConfig.isSmallWindowFeatureEnabled(context, LspConfig.SmallWindowFeature.HideRecents) },
        Entry(R.string.small_window_mute_stashed, FeaturePageMode.SystemUiSmallWindow) { context -> LspConfig.isSmallWindowFeatureEnabled(context, LspConfig.SmallWindowFeature.MuteStashed) },
        Entry(R.string.small_window_unlimited_count, FeaturePageMode.SystemUiSmallWindow) { context -> LspConfig.isSmallWindowFeatureEnabled(context, LspConfig.SmallWindowFeature.UnlimitedCount) },
        Entry(R.string.small_window_unlimited_frame_rate, FeaturePageMode.SystemUiSmallWindow) { context -> LspConfig.isSmallWindowFeatureEnabled(context, LspConfig.SmallWindowFeature.UnlimitedFrameRate) },
        Entry(R.string.small_window_keep_running, FeaturePageMode.SystemUiSmallWindow) { context -> LspConfig.isSmallWindowFeatureEnabled(context, LspConfig.SmallWindowFeature.KeepRunning) },
        Entry(R.string.small_window_white_bar, FeaturePageMode.SystemUiSmallWindow) { context -> LspConfig.isSmallWindowFeatureEnabled(context, LspConfig.SmallWindowFeature.WhiteBar) },
        Entry(R.string.small_window_safe_inset, FeaturePageMode.SystemUiSmallWindow) { context -> LspConfig.isSmallWindowFeatureEnabled(context, LspConfig.SmallWindowFeature.SafeEdgeInset) },
        Entry(R.string.small_window_landscape_ratio, FeaturePageMode.SystemUiSmallWindow) { context -> LspConfig.isSmallWindowFeatureEnabled(context, LspConfig.SmallWindowFeature.LandscapeRatio) },
        Entry(R.string.small_window_larger_size, FeaturePageMode.SystemUiSmallWindow) { context -> LspConfig.isSmallWindowFeatureEnabled(context, LspConfig.SmallWindowFeature.LargerSize) },
        Entry(R.string.small_window_compact_caption, FeaturePageMode.SystemUiSmallWindow) { context -> LspConfig.isSmallWindowFeatureEnabled(context, LspConfig.SmallWindowFeature.CompactCaption) },
        Entry(R.string.notification_removal_overlay, FeaturePageMode.NotificationRemoval) { context -> LspConfig.isNotificationRemovalEnabled(context, LspConfig.NotificationRemovalFeature.Overlay) },
        Entry(R.string.notification_removal_vpn, FeaturePageMode.NotificationRemoval) { context -> LspConfig.isNotificationRemovalEnabled(context, LspConfig.NotificationRemovalFeature.Vpn) },
        Entry(R.string.notification_removal_developer, FeaturePageMode.NotificationRemoval) { context -> LspConfig.isNotificationRemovalEnabled(context, LspConfig.NotificationRemovalFeature.DeveloperMode) },
        Entry(R.string.notification_removal_charging, FeaturePageMode.NotificationRemoval) { context -> LspConfig.isNotificationRemovalEnabled(context, LspConfig.NotificationRemovalFeature.ChargingCompleted) },
        Entry(R.string.notification_removal_flashlight, FeaturePageMode.NotificationRemoval) { context -> LspConfig.isNotificationRemovalEnabled(context, LspConfig.NotificationRemovalFeature.Flashlight) },
        Entry(R.string.notification_removal_consumption, FeaturePageMode.NotificationRemoval) { context -> LspConfig.isNotificationRemovalEnabled(context, LspConfig.NotificationRemovalFeature.HighBatteryConsumption) },
        Entry(R.string.notification_removal_performance, FeaturePageMode.NotificationRemoval) { context -> LspConfig.isNotificationRemovalEnabled(context, LspConfig.NotificationRemovalFeature.HighPerformance) },
        Entry(R.string.notification_removal_dnd, FeaturePageMode.NotificationRemoval) { context -> LspConfig.isNotificationRemovalEnabled(context, LspConfig.NotificationRemovalFeature.DoNotDisturb) },
        Entry(R.string.notification_removal_hotspot, FeaturePageMode.NotificationRemoval) { context -> LspConfig.isNotificationRemovalEnabled(context, LspConfig.NotificationRemovalFeature.HotspotPowerConsumption) },
        Entry(R.string.notification_removal_mute, FeaturePageMode.NotificationRemoval) { context -> LspConfig.isNotificationRemovalEnabled(context, LspConfig.NotificationRemovalFeature.MuteNotifications) },
        Entry(R.string.notification_removal_gt, FeaturePageMode.NotificationRemoval) { context -> LspConfig.isNotificationRemovalEnabled(context, LspConfig.NotificationRemovalFeature.GtMode) },
        Entry(R.string.installer_enable, FeaturePageMode.Installer) { context -> LspConfig.isInstallerFeatureEnabled(context, LspConfig.InstallerFeature.Enabled) },
        Entry(R.string.installer_uninstall, FeaturePageMode.Installer, parents = setOf(R.string.installer_enable)) { context -> LspConfig.isInstallerFeatureEnabled(context, LspConfig.InstallerFeature.Uninstall) },
        Entry(R.string.installer_session, FeaturePageMode.Installer, parents = setOf(R.string.installer_enable)) { context -> LspConfig.isInstallerFeatureEnabled(context, LspConfig.InstallerFeature.Session) },
        Entry(R.string.installer_intercept_system, FeaturePageMode.Installer, parents = setOf(R.string.installer_enable)) { context -> LspConfig.isInstallerFeatureEnabled(context, LspConfig.InstallerFeature.InterceptSystem) },
        Entry(R.string.installer_remove_default_policy, FeaturePageMode.Installer, parents = setOf(R.string.installer_enable)) { context -> LspConfig.isInstallerFeatureEnabled(context, LspConfig.InstallerFeature.RemoveDefaultAppPolicy) },
        Entry(R.string.battery_show_cycle_count_title, FeaturePageMode.Battery) { context -> LspConfig.isBatteryFeatureEnabled(context, LspConfig.BatteryFeature.ShowCycleCount) },
        Entry(R.string.battery_remove_restrict_plugin_title, FeaturePageMode.Battery) { context -> LspConfig.isBatteryFeatureEnabled(context, LspConfig.BatteryFeature.RemoveRestrictPlugin) },
        Entry(R.string.battery_restore_default_whitelist_title, FeaturePageMode.Battery) { context -> LspConfig.isBatteryFeatureEnabled(context, LspConfig.BatteryFeature.RestoreDefaultWhitelist) },
        Entry(R.string.feature_status_bar_double_tap_to_top_title, FeaturePageMode.SystemUiStatusBar) { context -> LspConfig.isStatusBarInteractionFeatureEnabled(context, LspConfig.StatusBarInteractionFeature.DoubleTapToTop) },
        Entry(R.string.feature_status_bar_remove_to_top_whitelist_title, FeaturePageMode.SystemUiStatusBar) { context -> LspConfig.isStatusBarInteractionFeatureEnabled(context, LspConfig.StatusBarInteractionFeature.RemoveToTopWhitelist) },
        Entry(R.string.keyguard_aod_screenshot_title, FeaturePageMode.Screenshot) { LspConfig.isKeyguardFeatureEnabled(it, LspConfig.KeyguardFeature.AodScreenshot) },
        Entry(R.string.keyguard_screen_off_recording_title, FeaturePageMode.ScreenRecording) { LspConfig.isKeyguardFeatureEnabled(it, LspConfig.KeyguardFeature.ScreenOffRecording) },
        Entry(R.string.feature_recent_task_radius_title, FeaturePageMode.DesktopRecent) { LspConfig.isRecentTaskRadiusEnabled(it) },
        Entry(R.string.feature_launcher_taskbar_search_box_title, FeaturePageMode.DesktopLayout) { LspConfig.getLauncherSearchBarMode(it) != LspConfig.LAUNCHER_SEARCH_BAR_MODE_OFF },
        Entry(R.string.feature_monet_color_spec_title, FeaturePageMode.SystemUiDynamicColor) { LspConfig.getSystemUiMonetColorSpecMode(it) != LspConfig.SYSTEMUI_MONET_COLOR_SPEC_OFF },
    ) + listOf(
        FeaturePageMode.DesktopPages to LauncherFeatureItems.pages,
        FeaturePageMode.DesktopRecent to LauncherFeatureItems.recent,
        FeaturePageMode.DesktopIcons to (LauncherFeatureItems.badges + LauncherFeatureItems.folder),
        FeaturePageMode.DesktopLayout to (LauncherFeatureItems.dock + LauncherFeatureItems.effects),
    ).flatMap { (page, items) ->
        items.map { item ->
            Entry(item.title, page,
                parents = if (item.feature == LspConfig.LauncherFeature.DockBlur) setOf(R.string.launcher_dock) else emptySet(),
            ) { LspConfig.isLauncherFeatureEnabled(it, item.feature) }
        }
    } + TrafficFeatureItems.all.map { item ->
        Entry(item.title, FeaturePageMode.TrafficManagement) { LspConfig.isTrafficFeatureEnabled(it, item.feature) }
    } + SoftwareUpdateFeatureItems.all.map { item ->
        Entry(item.title, FeaturePageMode.SoftwareUpdate) { SoftwareUpdateSettings.enabled(it, item.feature) }
    }

    suspend fun read(context: Context): List<Entry> {
        val configured = configured(context)
        return entries.filter { isActive(it.title, configured) }
    }

    suspend fun configured(context: Context): Set<Int> =
        entries.filter { it.read(context) }.mapTo(mutableSetOf()) { it.title }

    internal fun dependenciesActive(title: Int, configured: Set<Int>): Boolean {
        val entry = entries.firstOrNull { it.title == title } ?: return false
        return entry.excludes.none { it in configured } && entry.parents.all { isActive(it, configured) }
    }

    /** Parameters are read once with the inventory, never by invoking feature setters. */
    fun detail(context: Context, title: Int): String = when (title) {
        R.string.statusbar_margins_title -> StatusBarLayoutSettings.state(context).let { "${it.left} dp / ${it.right} dp" }
        R.string.statusbar_vertical_margins_title -> StatusBarLayoutSettings.state(context).let { "${it.top} dp / ${it.bottom} dp" }
        R.string.launcher_swipe_top_title -> "${LauncherGestureSettings.width(context)}%"
        R.string.launcher_custom_grid_title -> LauncherGridSettings.state(context).grid.let { "${it.columns} × ${it.rows}" }
        R.string.feature_recent_task_radius_title -> "${LspConfig.getRecentTaskRadiusDp(context)} dp"
        R.string.feature_aod_enhance_toggle_title -> {
            val default = context.getString(R.string.feature_slider_current_default)
            val dark = LspConfig.getAodInitDarkBrightness(context).let { if (it == FeatureSliderRules.SYSTEM_DEFAULT) default else it.toString() }
            val bright = LspConfig.getAodInitBrightBrightness(context).let { if (it == FeatureSliderRules.SYSTEM_DEFAULT) default else it.toString() }
            val multiplier = LspConfig.getAodRunningBrightnessMultiplier(context).let { if (it == FeatureSliderRules.SYSTEM_DEFAULT_MULTIPLIER) default else "${it}×" }
            listOf(
                context.getString(R.string.feature_aod_dark_brightness_label) + ": " + dark,
                context.getString(R.string.feature_aod_bright_brightness_label) + ": " + bright,
                context.getString(R.string.feature_aod_multiplier_label) + ": " + multiplier,
            ).joinToString(" · ")
        }
        R.string.feature_navigation_handle_length_title -> "${LspConfig.getNavigationHandleLengthDp(context)} dp · ${LspConfig.getNavigationHandleOpacity(context)}%"
        R.string.fluid_media_blacklist_title -> context.getString(R.string.fluid_media_selected_count, FluidCloudMediaSettings.packages(context).size)
        R.string.feature_launcher_taskbar_search_box_title -> context.getString(
            if (LspConfig.getLauncherSearchBarMode(context) == LspConfig.LAUNCHER_SEARCH_BAR_MODE_CHINA)
                R.string.feature_launcher_search_bar_mode_china else R.string.feature_launcher_search_bar_mode_international)
        R.string.feature_monet_color_spec_title -> if (LspConfig.getSystemUiMonetColorSpecMode(context) == LspConfig.SYSTEMUI_MONET_COLOR_SPEC_2025) "SPEC_2025" else "SPEC_2021"
        else -> ""
    }

    internal fun isActive(title: Int, configured: Set<Int>): Boolean {
        return title in configured && dependenciesActive(title, configured)
    }

    fun group(page: FeaturePageMode): FeaturePageMode = when {
        page.name.startsWith("Desktop") -> FeaturePageMode.Desktop
        page.name.startsWith("SystemUi") || page == FeaturePageMode.NotificationRemoval -> FeaturePageMode.SystemUi
        page.name.startsWith("Settings") -> FeaturePageMode.Settings
        page == FeaturePageMode.Installer -> FeaturePageMode.AndroidSystem
        else -> page
    }
}
