package com.mi.onextbox.ui.screens

import org.junit.Assert.*
import org.junit.Test

class FeatureSearchTest {
    @Test fun systemRootDetectionOpensAndroidSystemPage() {
        val entry = FeatureSearchIndex.entries.single {
            it.title == com.mi.onextbox.R.string.feature_system_root_detection_title
        }
        assertEquals(FeaturePageMode.AndroidSystem, entry.page)
        assertEquals(com.mi.onextbox.R.string.feature_system_root_detection_summary, entry.description)
        assertEquals(FeaturePageMode.AndroidSystem, FeatureSearchIndex.entries.single {
            it.title == com.mi.onextbox.R.string.feature_group_system_root
        }.page)
    }

    @Test fun appInformationCardOpensTheSettingsAppsPage() {
        val entry = FeatureSearchIndex.entries.single {
            it.title == com.mi.onextbox.R.string.feature_settings_app_info_card_title
        }
        assertEquals(FeaturePageMode.SettingsApps, entry.page)
        assertEquals(com.mi.onextbox.R.string.feature_settings_app_info_card_summary, entry.description)
    }

    @Test fun batteryOptimizationFeaturesHaveAnIndependentDestination() {
        listOf(
            com.mi.onextbox.R.string.battery_show_cycle_count_title to
                com.mi.onextbox.R.string.battery_show_cycle_count_summary,
            com.mi.onextbox.R.string.battery_remove_restrict_plugin_title to
                com.mi.onextbox.R.string.battery_remove_restrict_plugin_summary,
            com.mi.onextbox.R.string.battery_restore_default_whitelist_title to
                com.mi.onextbox.R.string.battery_restore_default_whitelist_summary,
        ).forEach { (title, description) ->
            val entry = FeatureSearchIndex.entries.single { it.title == title }
            assertEquals(FeaturePageMode.Battery, entry.page)
            assertEquals(description, entry.description)
        }
    }
    @Test fun desktopFeaturesOpenTheirCategoryPages() {
        val groups = listOf(
            FeaturePageMode.DesktopPages to LauncherFeatureItems.pages,
            FeaturePageMode.DesktopRecent to LauncherFeatureItems.recent,
            FeaturePageMode.DesktopIcons to (LauncherFeatureItems.badges + LauncherFeatureItems.folder),
            FeaturePageMode.DesktopLayout to LauncherFeatureItems.dock,
        )
        groups.forEach { (page, items) ->
            assertTrue(page.isNestedPage)
            items.forEach { item ->
                assertEquals(page, FeatureSearchIndex.entries.single { it.title == item.title }.page)
            }
        }
        assertEquals(1, FeatureSearchIndex.entries.count { it.page == FeaturePageMode.Desktop })
        assertTrue(FeaturePageMode.DesktopLayout.isNestedPage)
    }

    @Test fun settingsFeaturesOpenTheirCategoryPages() {
        val groups = mapOf(
            FeaturePageMode.SettingsAppearance to listOf(
                com.mi.onextbox.R.string.settings_category_interface_display,
                com.mi.onextbox.R.string.feature_group_settings_interface,
                com.mi.onextbox.R.string.feature_group_screen_refresh_rate,
                com.mi.onextbox.R.string.feature_settings_title_collapsed_title,
                com.mi.onextbox.R.string.feature_settings_unlock_refresh_rate_title,
                com.mi.onextbox.R.string.feature_settings_force_global_extreme_refresh_rate_title,
                com.mi.onextbox.R.string.feature_extreme_refresh_165_title,
            ),
            FeaturePageMode.SettingsApps to listOf(
                com.mi.onextbox.R.string.feature_group_app_management,
                com.mi.onextbox.R.string.feature_settings_app_info_card_title,
                com.mi.onextbox.R.string.feature_settings_restore_app_open_button_title,
            ),
            FeaturePageMode.SettingsPermissions to listOf(
                com.mi.onextbox.R.string.settings_category_permissions,
                com.mi.onextbox.R.string.feature_group_developer_options,
                com.mi.onextbox.R.string.feature_group_special_permissions,
                com.mi.onextbox.R.string.feature_permission_monitor_title,
                com.mi.onextbox.R.string.feature_settings_skip_special_permission_risk_confirm_title,
            ),
            FeaturePageMode.SettingsAboutDevice to listOf(
                com.mi.onextbox.R.string.feature_group_about_device,
                com.mi.onextbox.R.string.feature_settings_c15_about_layout_title,
            ),
        )
        groups.forEach { (page, titles) ->
            assertTrue(page.isNestedPage)
            titles.forEach { title ->
                assertEquals(page, FeatureSearchIndex.entries.single { it.title == title }.page)
            }
        }
        assertEquals(1, FeatureSearchIndex.entries.count { it.page == FeaturePageMode.Settings })
    }

    @Test fun settingsRegionKeepsItsExistingDestinationAndControls() {
        assertTrue(FeaturePageMode.SettingsRegion.isNestedPage)
        val titles = setOf(
            com.mi.onextbox.R.string.feature_group_settings_hidden_features,
            com.mi.onextbox.R.string.feature_settings_force_google_title,
            com.mi.onextbox.R.string.feature_settings_international_title,
            com.mi.onextbox.R.string.feature_settings_force_app_auto_start_title,
            com.mi.onextbox.R.string.feature_settings_international_wallet_title,
            com.mi.onextbox.R.string.feature_settings_restore_domestic_about_device_title,
            com.mi.onextbox.R.string.feature_settings_restore_smart_lock_title,
            com.mi.onextbox.R.string.feature_settings_restore_domestic_auxiliary_functions_title,
        )
        val entries = FeatureSearchIndex.entries.filter { it.page == FeaturePageMode.SettingsRegion }
        assertEquals(titles.size, entries.size)
        assertEquals(titles, entries.map { it.title }.toSet())
    }

    @Test fun blankQueriesDoNotReturnEverything() {
        assertEquals(0, featureSearchScore("   ", "复制验证码", "通知"))
    }
    @Test fun matchesTitlesAndDescriptions() {
        assertEquals(3, featureSearchScore("复制验证码", "复制验证码", "通知"))
        assertEquals(2, featureSearchScore("验证码", "复制验证码", "通知"))
        assertEquals(1, featureSearchScore("短信", "复制验证码", "短信通知"))
        assertEquals(0, featureSearchScore("RCS", "复制验证码", "通知"))
    }
    @Test fun caseWidthAndMultipleWordsAreNormalized() {
        assertEquals(3, featureSearchScore(" ＧＥＭＩＮＩ ", "Gemini", ""))
        assertEquals(1, featureSearchScore("copy code", "Copy", "verification code"))
        assertEquals(0, featureSearchScore("copy rcs", "Copy", "verification code"))
    }
    @Test fun indexHasUniqueDestinationsAndNoMainPage() {
        val entries = FeatureSearchIndex.entries
        assertEquals(entries.size, entries.distinctBy { it.page to it.title }.size)
        assertFalse(entries.any { it.page == FeaturePageMode.Main })
        assertTrue(entries.any { it.page == FeaturePageMode.GoogleMessages })
        assertTrue(entries.any { it.page == FeaturePageMode.TouchSampling })
        assertTrue(entries.any { it.page == FeaturePageMode.SystemUiSmallWindow })
    }
    @Test fun brightnessAndRadiusSlidersAreSearchable() {
        for (title in listOf(
            com.mi.onextbox.R.string.feature_aod_dark_brightness_label,
            com.mi.onextbox.R.string.feature_aod_bright_brightness_label,
            com.mi.onextbox.R.string.feature_aod_multiplier_label,
        )) {
            assertTrue(FeatureSearchIndex.entries.any {
                it.page == FeaturePageMode.Aod && it.title == title
            })
        }
        assertTrue(FeatureSearchIndex.entries.any {
            it.page == FeaturePageMode.DesktopRecent && it.title == com.mi.onextbox.R.string.feature_recent_task_radius_label
        })
    }
    @Test fun recentTaskCardRadiusHasNoDescription() {
        val entry = FeatureSearchIndex.entries.single {
            it.page == FeaturePageMode.DesktopRecent && it.title == com.mi.onextbox.R.string.feature_recent_task_radius_title
        }
        assertEquals(0, entry.description)
    }
    @Test fun bundledSettingsAndExpressFeaturesAreSearchable() {
        assertTrue(FeatureSearchIndex.entries.any {
            it.page == FeaturePageMode.SettingsAppearance && it.title == com.mi.onextbox.R.string.feature_settings_title_collapsed_title
        })
        assertTrue(FeatureSearchIndex.entries.any {
            it.page == FeaturePageMode.QuickAppServices && it.title == com.mi.onextbox.R.string.feature_express_no_mini_program_title
        })
    }
    @Test fun expressServiceHasItsOwnTopLevelPage() {
        assertFalse(FeaturePageMode.QuickAppServices.isNestedPage)
        for (title in listOf(
            com.mi.onextbox.R.string.feature_quick_app_services_title,
            com.mi.onextbox.R.string.feature_group_express_service,
            com.mi.onextbox.R.string.feature_express_no_mini_program_title,
        )) {
            val destinations = FeatureSearchIndex.entries.filter { it.title == title }
            assertEquals(1, destinations.size)
            assertEquals(FeaturePageMode.QuickAppServices, destinations.single().page)
        }
    }
    @Test fun fluidCloudMaterialIsSearchableOnItsOwnPage() {
        assertTrue(FeaturePageMode.SystemUiFluidCloud.isNestedPage)
        val destinations = FeatureSearchIndex.entries.filter { it.page == FeaturePageMode.SystemUiFluidCloud }
        assertEquals(3, destinations.size)
        val category = destinations.single { it.title == com.mi.onextbox.R.string.feature_fluid_cloud_title }
        assertEquals(com.mi.onextbox.R.string.feature_fluid_cloud_entry_summary, category.description)
        assertTrue(destinations.any { it.title == com.mi.onextbox.R.string.feature_fluid_cloud_card_group })
        val material = destinations.single { it.title == com.mi.onextbox.R.string.feature_fluid_cloud_material_title }
        assertEquals(0, material.description)
    }
    @Test fun statusBarGroupsAndControlsKeepTheirDestination() {
        val titles = listOf(
            com.mi.onextbox.R.string.status_bar_group_network,
            com.mi.onextbox.R.string.status_bar_group_battery,
            com.mi.onextbox.R.string.status_bar_group_time,
            com.mi.onextbox.R.string.status_bar_group_interaction,
            com.mi.onextbox.R.string.feature_status_bar_double_tap_to_top_title,
            com.mi.onextbox.R.string.feature_status_bar_remove_to_top_whitelist_title,
            com.mi.onextbox.R.string.feature_international_network_display_title,
            com.mi.onextbox.R.string.feature_restore_c16_network_icon_order_title,
            com.mi.onextbox.R.string.feature_hide_mobile_roaming_indicator_title,
            com.mi.onextbox.R.string.feature_hide_network_activity_indicator_title,
            com.mi.onextbox.R.string.feature_hide_disconnected_bluetooth_title,
            com.mi.onextbox.R.string.feature_fluid_cloud_battery_title,
            com.mi.onextbox.R.string.feature_permanent_clock_seconds_title,
        )
        titles.forEach { title ->
            val destinations = FeatureSearchIndex.entries.filter { it.title == title }
            assertEquals(1, destinations.size)
            assertEquals(FeaturePageMode.SystemUiStatusBar, destinations.single().page)
        }
    }
    @Test fun scrollToTopSwitchesHaveSeparateStatusBarSearchEntries() {
        for ((title, description) in listOf(
            com.mi.onextbox.R.string.feature_status_bar_double_tap_to_top_title to
                com.mi.onextbox.R.string.feature_status_bar_double_tap_to_top_summary,
            com.mi.onextbox.R.string.feature_status_bar_remove_to_top_whitelist_title to
                com.mi.onextbox.R.string.feature_status_bar_remove_to_top_whitelist_summary,
        )) {
            val destinations = FeatureSearchIndex.entries.filter { it.title == title }
            assertEquals(1, destinations.size)
            assertEquals(FeaturePageMode.SystemUiStatusBar, destinations.single().page)
            assertEquals(description, destinations.single().description)
        }
    }
    @Test fun disconnectedBluetoothIsSearchableOnlyUnderStatusBar() {
        val destinations = FeatureSearchIndex.entries.filter {
            it.title == com.mi.onextbox.R.string.feature_hide_disconnected_bluetooth_title
        }
        assertEquals(1, destinations.size)
        assertEquals(FeaturePageMode.SystemUiStatusBar, destinations.single().page)
        assertEquals(com.mi.onextbox.R.string.feature_hide_disconnected_bluetooth_summary, destinations.single().description)
    }
    @Test fun fluidCloudBatteryIsSearchableOnlyUnderStatusBar() {
        val destinations = FeatureSearchIndex.entries.filter {
            it.title == com.mi.onextbox.R.string.feature_fluid_cloud_battery_title
        }
        assertEquals(1, destinations.size)
        assertEquals(FeaturePageMode.SystemUiStatusBar, destinations.single().page)
        assertEquals(com.mi.onextbox.R.string.feature_fluid_cloud_battery_summary, destinations.single().description)
    }
    @Test fun permanentClockSecondsIsSearchableOnlyUnderStatusBar() {
        val destinations = FeatureSearchIndex.entries.filter {
            it.title == com.mi.onextbox.R.string.feature_permanent_clock_seconds_title
        }
        assertEquals(1, destinations.size)
        assertEquals(FeaturePageMode.SystemUiStatusBar, destinations.single().page)
        assertEquals(com.mi.onextbox.R.string.feature_permanent_clock_seconds_summary, destinations.single().description)
    }
    @Test fun faceTapControlsAreSearchableOnlyUnderLockScreen() {
        assertTrue(FeatureSearchIndex.entries.any {
            it.page == FeaturePageMode.SystemUiLockScreen && it.title == com.mi.onextbox.R.string.keyguard_face_tap_title
        })
        assertTrue(FeatureSearchIndex.entries.any {
            it.page == FeaturePageMode.SystemUiLockScreen && it.title == com.mi.onextbox.R.string.keyguard_face_tap_animation_title
        })
        assertTrue(FeatureSearchIndex.entries.any {
            it.page == FeaturePageMode.SystemUiLockScreen && it.title == com.mi.onextbox.R.string.keyguard_page_title
        })
        assertFalse(FeatureSearchIndex.entries.any {
            it.page == FeaturePageMode.SystemUiNative &&
                (it.title == com.mi.onextbox.R.string.keyguard_face_tap_title ||
                    it.title == com.mi.onextbox.R.string.keyguard_face_tap_animation_title)
        })
    }
    @Test fun immersiveNavigationHasItsOwnDestination() {
        assertTrue(FeaturePageMode.SystemUiNavigationBar.isNestedPage)
        assertTrue(FeatureSearchIndex.entries.any {
            it.page == FeaturePageMode.SystemUiNavigationBar &&
                it.title == com.mi.onextbox.R.string.feature_immersive_navigation_title &&
                it.description == com.mi.onextbox.R.string.feature_immersive_navigation_summary
        })
        assertTrue(FeatureSearchIndex.entries.any {
            it.page == FeaturePageMode.SystemUiNavigationBar &&
                it.title == com.mi.onextbox.R.string.feature_navigation_handle_length_title &&
                it.description == 0
        })
        for (title in listOf(
            com.mi.onextbox.R.string.feature_navigation_handle_length_label,
            com.mi.onextbox.R.string.feature_navigation_handle_opacity_label,
        )) {
            assertTrue(FeatureSearchIndex.entries.any {
                it.page == FeaturePageMode.SystemUiNavigationBar && it.title == title
            })
        }
    }
    @Test fun captureControlsHaveTheirOwnDestinations() {
        assertTrue(FeatureSearchIndex.entries.any {
            it.page == FeaturePageMode.Screenshot && it.title == com.mi.onextbox.R.string.keyguard_aod_screenshot_title
        })
        assertTrue(FeatureSearchIndex.entries.any {
            it.page == FeaturePageMode.ScreenRecording && it.title == com.mi.onextbox.R.string.keyguard_screen_off_recording_title
        })
        assertFalse(FeatureSearchIndex.entries.any {
            it.page == FeaturePageMode.SystemUiLockScreen &&
                (it.title == com.mi.onextbox.R.string.keyguard_aod_screenshot_title ||
                    it.title == com.mi.onextbox.R.string.keyguard_screen_off_recording_title)
        })
    }
    @Test fun autoHideHandleIsSearchableUnderNavigationBar() {
        assertTrue(FeatureSearchIndex.entries.any {
            it.page == FeaturePageMode.SystemUiNavigationBar &&
                it.title == com.mi.onextbox.R.string.feature_navigation_handle_auto_hide_title &&
                it.description == com.mi.onextbox.R.string.feature_navigation_handle_auto_hide_summary
        })
    }
    @Test fun everySmallWindowControlIsSearchable() {
        val indexed = FeatureSearchIndex.entries.filter { it.page == FeaturePageMode.SystemUiSmallWindow }.map { it.title }.toSet()
        val controls = setOf(
            com.mi.onextbox.R.string.small_window_white_bar,
            com.mi.onextbox.R.string.small_window_safe_inset,
            com.mi.onextbox.R.string.small_window_hide_recents,
            com.mi.onextbox.R.string.small_window_keep_running,
            com.mi.onextbox.R.string.small_window_mute_stashed,
            com.mi.onextbox.R.string.small_window_landscape_ratio,
            com.mi.onextbox.R.string.small_window_larger_size,
            com.mi.onextbox.R.string.small_window_compact_caption,
            com.mi.onextbox.R.string.small_window_unlimited_count,
            com.mi.onextbox.R.string.small_window_unlimited_frame_rate,
        )
        assertTrue(indexed.containsAll(controls))
    }
}
