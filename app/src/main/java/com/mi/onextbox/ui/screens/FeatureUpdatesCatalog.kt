package com.mi.onextbox.ui.screens

import com.mi.onextbox.R
import com.mi.onextbox.lsp.SystemAdaptationVersion

internal object FeatureUpdatesCatalog {
    const val EDITION = "2026-10-29-r1"
    const val BASE_VERSION = "17.0.0.96"

    enum class Kind { Added, Changed }

    data class Change(val title: Int, val kind: Kind, val note: Int = 0)
    data class Item(val change: Change, val destination: FeatureSearchIndex.Entry)
    data class Overview(val title: Int, val note: Int)

    val changes = listOf(
        Change(R.string.browser_page_title, Kind.Added, R.string.feature_updates_browser_note),
        Change(R.string.weather_page_title, Kind.Added, R.string.feature_updates_weather_note),
        Change(R.string.keyboard_monet_title, Kind.Added, R.string.feature_updates_monet_note),
        Change(R.string.keyboard_blur_corner_title, Kind.Added, R.string.feature_updates_keyboard_material_note),
        Change(R.string.keyboard_toolbar_bg_alpha_title, Kind.Added, R.string.feature_updates_keyboard_toolbar_note),
        Change(R.string.keyboard_key_corner_title, Kind.Added, R.string.feature_updates_keyboard_keys_note),
        Change(R.string.engineer_spoof_keys, Kind.Added, R.string.engineer_spoof_keys_summary),
    ) + listOf(
        R.string.photopicker_native,
        R.string.engineer_show_hidden,
        R.string.battery_ai_assistant,
        R.string.sidebar_allow_apps_title,
        R.string.sidebar_allow_in_apps_title,
        R.string.file_manager_default_title,
        R.string.coui_hide_dividers_title,
        R.string.pictorial_sync_home_title,
        R.string.pictorial_clock_color_title,
        R.string.pictorial_screen_off_title,
        R.string.pictorial_keep_clock_title,
        R.string.pictorial_remove_download_watermark_title,
        R.string.back_gesture_haptics,
        R.string.back_gesture_release,
        R.string.back_gesture_predictive_style,
        R.string.back_gesture_area,
        R.string.clock_tabular_digits,
        R.string.network_speed_hide_low_speed,
        R.string.power_indicator_only_charging,
    ).map { Change(it, Kind.Added) } + listOf(
        Change(R.string.feature_app_market_title, Kind.Changed, R.string.feature_updates_market_note),
        Change(R.string.back_gesture_style, Kind.Changed, R.string.feature_updates_back_note),
        Change(R.string.battery_block_whitelist_restore_title, Kind.Changed, R.string.feature_updates_battery_note),
        Change(R.string.installer_native_ui_title, Kind.Changed, R.string.feature_updates_installer_note),
        Change(R.string.game_assistant_skins_title, Kind.Changed, R.string.feature_updates_game_note),
        Change(R.string.keyboard_flag_emoji_title, Kind.Changed, R.string.feature_updates_emoji_note),
        Change(R.string.clock_compact_spacing, Kind.Changed, R.string.feature_updates_clock_note),
        Change(R.string.feature_settings_app_info_card_title, Kind.Changed, R.string.feature_updates_app_info_note),
        Change(R.string.feature_file_manager_native_picker_title, Kind.Changed, R.string.feature_updates_picker_note),
    )

    val overview = listOf(
        Overview(R.string.diagnostics_debug_section, R.string.feature_updates_diagnostics_note),
        Overview(R.string.diagnostics_intercept, R.string.feature_updates_crash_note),
        Overview(R.string.feature_updates_safe_mode, R.string.feature_updates_safe_mode_note),
        Overview(R.string.feature_updates_device, R.string.feature_updates_device_note),
        Overview(R.string.feature_updates_navigation, R.string.feature_updates_navigation_note),
        Overview(R.string.feature_updates_controls, R.string.feature_updates_controls_note),
        Overview(R.string.setting_feature_background, R.string.feature_updates_background_note),
        Overview(R.string.feature_updates_organization, R.string.feature_updates_organization_note),
        Overview(R.string.update_beta_plan_title, R.string.update_beta_plan_summary),
    )

    fun items(version: SystemAdaptationVersion): List<Item> {
        val destinations = featureSearchEntries(version).associateBy { it.title }
        return changes.mapNotNull { change -> destinations[change.title]?.let { Item(change, it) } }
    }

    fun edition(versionName: String): String = "$EDITION:$versionName"
}
