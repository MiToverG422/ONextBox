package com.mi.onextbox.ui.screens

import com.mi.onextbox.R
import com.mi.onextbox.lsp.AospVolumeSettings
import com.mi.onextbox.lsp.GameAssistantSettings
import com.mi.onextbox.lsp.KeyboardFeatureSettings
import com.mi.onextbox.lsp.LauncherIosMotionSettings

/** Additional migrations share the existing model rather than growing manual consumer lists. */
internal object AdditionalFeatureDefinitions {
    val launcherIosMotion = SharedFeatureDefinitions.Toggle(
        R.string.launcher_ios_motion_title,
        R.string.launcher_ios_motion_summary,
        FeaturePageMode.DesktopIosMotion,
        { LauncherIosMotionSettings.enabled(it) },
        { context, enabled -> LauncherIosMotionSettings.setEnabled(context, enabled) },
        preferenceKey = LauncherIosMotionSettings.ENABLED,
    )
    private fun keyboard(option: KeyboardFeatureSettings.Option, title: Int, summary: Int) = SharedFeatureDefinitions.Toggle(
        title, summary, FeaturePageMode.Keyboard, { KeyboardFeatureSettings.enabled(it, option) },
        { context, enabled -> KeyboardFeatureSettings.set(context, option, enabled) }, preferenceKey = option.key,
    )
    val keyboardMaterial = keyboard(KeyboardFeatureSettings.Option.Material, R.string.keyboard_material_unlock_title, R.string.keyboard_material_unlock_summary)
    val keyboardBottomLift = keyboard(KeyboardFeatureSettings.Option.BottomLift, R.string.keyboard_bottom_lift_fix_title, R.string.keyboard_bottom_lift_fix_summary)
    val keyboardHideDismiss = keyboard(KeyboardFeatureSettings.Option.HideDismiss, R.string.keyboard_hide_dismiss_title, R.string.keyboard_hide_dismiss_summary)
    val keyboardFlagEmoji = keyboard(KeyboardFeatureSettings.Option.FlagEmoji, R.string.keyboard_flag_emoji_title, R.string.keyboard_flag_emoji_summary)
    val aospVolume = SharedFeatureDefinitions.Toggle(R.string.feature_aosp_volume_title, R.string.feature_aosp_volume_summary,
        FeaturePageMode.SystemUiNative, { AospVolumeSettings.enabled(it) },
        { context, enabled -> AospVolumeSettings.set(context, enabled) }, preferenceKey = AospVolumeSettings.KEY)

    private fun game(key: String, title: Int, summary: Int = 0) = SharedFeatureDefinitions.Toggle(
        title, summary, FeaturePageMode.GameAssistant, { GameAssistantSettings.enabled(it, key) },
        { context, enabled -> GameAssistantSettings.set(context, key, enabled) }, preferenceKey = key,
    )
    val gamePromotions = game(GameAssistantSettings.PROMOTIONS, R.string.game_assistant_promotions_title, R.string.game_assistant_promotions_summary)
    val gameStartup = game(GameAssistantSettings.REALME_STARTUP_ANIMATION, R.string.game_assistant_realme_startup_title, R.string.game_assistant_realme_startup_summary)
    val gameUnlimited = game(GameAssistantSettings.UNLIMITED, R.string.game_assistant_unlimited_title, R.string.game_assistant_unlimited_summary)
    val gameMusic = game(GameAssistantSettings.MUSIC, R.string.game_assistant_music_title, R.string.game_assistant_music_summary)
    val gameTemperature = game(GameAssistantSettings.TEMPERATURE, R.string.game_assistant_temperature_title, R.string.game_assistant_temperature_summary)
    val gameToolbarTemperature = game(GameAssistantSettings.TOOLBAR_TEMPERATURE, R.string.game_assistant_toolbar_temperature_title, R.string.game_assistant_toolbar_temperature_summary)
    val gameRoot = game(GameAssistantSettings.BLOCK_ROOT, R.string.game_assistant_root_title, R.string.game_assistant_root_summary)
    val gameDeveloper = game(GameAssistantSettings.DEVELOPER, R.string.game_assistant_developer_title, R.string.game_assistant_developer_summary)
    val gameSkins = game(GameAssistantSettings.SKINS, R.string.game_assistant_skins_title)
    val all = listOf(launcherIosMotion, keyboardMaterial, keyboardBottomLift, keyboardHideDismiss, keyboardFlagEmoji, aospVolume,
        gamePromotions, gameStartup, gameUnlimited, gameMusic, gameTemperature, gameToolbarTemperature, gameRoot, gameDeveloper, gameSkins) + AospFeatureDefinitions.all
}
