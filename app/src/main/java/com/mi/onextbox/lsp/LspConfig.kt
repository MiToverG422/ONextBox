package com.mi.onextbox.lsp

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.mi.onextbox.ui.common.ShellLogger
import com.mi.onextbox.lsp.LspPreferenceStore.readSystemPropertyValue
import com.mi.onextbox.lsp.LspPreferenceStore.readFlagFile
import com.mi.onextbox.lsp.LspPreferenceStore.readSettingsGlobalToggle
import com.mi.onextbox.lsp.LspPreferenceStore.readSettingsGlobalValue
import com.mi.onextbox.lsp.LspPreferenceStore.prefs
import com.mi.onextbox.lsp.LspPreferenceStore.readSystemPropertyToggle
import com.topjohnwu.superuser.Shell
import com.mi.onextbox.lsp.compat.HookConfigSnapshot
import com.mi.onextbox.lsp.compat.ModernRemotePreferences as XSharedPreferences
import java.io.File

// Feature preferences and system-mirror synchronization.
@SuppressLint("ApplySharedPref", "UseKtx")
object LspConfig {
    /** Collects boot-time writes so dozens of values can be synchronized in one root shell. */
    private val syncCommandBatch = ThreadLocal<MutableList<String>?>()
    private val xposedPreferences by lazy(LazyThreadSafetyMode.PUBLICATION) {
        XSharedPreferences(PREFS_NAME)
    }

    private const val MODULE_PACKAGE = "com.mi.onextbox"
    private const val PREFS_NAME = "lsp_features"

    private const val KEY_SYSTEM_ROOT_DETECTION = "system_root_detection_blocked"
    private const val PROP_SYSTEM_ROOT_DETECTION = "oost.$KEY_SYSTEM_ROOT_DETECTION"
    private const val PERSIST_PROP_SYSTEM_ROOT_DETECTION = "persist.sys.$PROP_SYSTEM_ROOT_DETECTION"
    private const val SETTINGS_SYSTEM_ROOT_DETECTION = "oost_$KEY_SYSTEM_ROOT_DETECTION"

    fun isSystemRootDetectionBlocked(context: Context): Boolean =
        LspPreferenceStore.readBoolean(context, KEY_SYSTEM_ROOT_DETECTION, false)

    fun setSystemRootDetectionBlocked(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context, KEY_SYSTEM_ROOT_DETECTION, enabled,
            listOf(PERSIST_PROP_SYSTEM_ROOT_DETECTION, PROP_SYSTEM_ROOT_DETECTION), SETTINGS_SYSTEM_ROOT_DETECTION,
        )
    }

    fun isSystemRootDetectionBlockedXposed(): Boolean = readXposedBoolean(
        PERSIST_PROP_SYSTEM_ROOT_DETECTION, PROP_SYSTEM_ROOT_DETECTION,
        SETTINGS_SYSTEM_ROOT_DETECTION, KEY_SYSTEM_ROOT_DETECTION, false,
    )

    private const val KEY_SETTINGS_TITLE_COLLAPSED = "settings_title_collapsed"
    private const val PROP_SETTINGS_TITLE_COLLAPSED = "oost.$KEY_SETTINGS_TITLE_COLLAPSED"
    private const val PERSIST_PROP_SETTINGS_TITLE_COLLAPSED = "persist.sys.$PROP_SETTINGS_TITLE_COLLAPSED"
    private const val SETTINGS_TITLE_COLLAPSED = "oost_$KEY_SETTINGS_TITLE_COLLAPSED"

    fun isSettingsTitleCollapsedEnabled(context: Context): Boolean =
        LspPreferenceStore.readBoolean(context, KEY_SETTINGS_TITLE_COLLAPSED, false)

    fun setSettingsTitleCollapsedEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context, KEY_SETTINGS_TITLE_COLLAPSED, enabled,
            listOf(PERSIST_PROP_SETTINGS_TITLE_COLLAPSED, PROP_SETTINGS_TITLE_COLLAPSED), SETTINGS_TITLE_COLLAPSED,
        )
    }

    fun isSettingsTitleCollapsedEnabledXposed(): Boolean = readXposedBoolean(
        PERSIST_PROP_SETTINGS_TITLE_COLLAPSED, PROP_SETTINGS_TITLE_COLLAPSED,
        SETTINGS_TITLE_COLLAPSED, KEY_SETTINGS_TITLE_COLLAPSED, false,
    )

    private const val KEY_SETTINGS_APP_INFO_CARD = "settings_app_info_card"
    private const val PROP_SETTINGS_APP_INFO_CARD = "oost.$KEY_SETTINGS_APP_INFO_CARD"
    private const val PERSIST_PROP_SETTINGS_APP_INFO_CARD = "persist.sys.$PROP_SETTINGS_APP_INFO_CARD"
    private const val SETTINGS_APP_INFO_CARD = "oost_$KEY_SETTINGS_APP_INFO_CARD"

    fun isSettingsAppInfoCardEnabled(context: Context): Boolean =
        LspPreferenceStore.readBoolean(context, KEY_SETTINGS_APP_INFO_CARD, false)

    fun setSettingsAppInfoCardEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(context, KEY_SETTINGS_APP_INFO_CARD, enabled,
            listOf(PERSIST_PROP_SETTINGS_APP_INFO_CARD, PROP_SETTINGS_APP_INFO_CARD), SETTINGS_APP_INFO_CARD)
    }

    fun isSettingsAppInfoCardEnabledXposed(): Boolean = readXposedBoolean(
        PERSIST_PROP_SETTINGS_APP_INFO_CARD, PROP_SETTINGS_APP_INFO_CARD,
        SETTINGS_APP_INFO_CARD, KEY_SETTINGS_APP_INFO_CARD, false,
    )

    private const val KEY_FLUID_CLOUD_BATTERY = "systemui_fluid_cloud_battery"
    private const val PROP_FLUID_CLOUD_BATTERY = "oost.$KEY_FLUID_CLOUD_BATTERY"
    private const val PERSIST_PROP_FLUID_CLOUD_BATTERY = "persist.sys.$PROP_FLUID_CLOUD_BATTERY"
    private const val SETTINGS_FLUID_CLOUD_BATTERY = "oost_$KEY_FLUID_CLOUD_BATTERY"

    fun isFluidCloudBatteryEnabled(context: Context): Boolean =
        LspPreferenceStore.readBoolean(context, KEY_FLUID_CLOUD_BATTERY, false)

    fun setFluidCloudBatteryEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context, KEY_FLUID_CLOUD_BATTERY, enabled,
            listOf(PERSIST_PROP_FLUID_CLOUD_BATTERY, PROP_FLUID_CLOUD_BATTERY), SETTINGS_FLUID_CLOUD_BATTERY,
        )
    }

    fun isFluidCloudBatteryEnabledXposed(): Boolean = readXposedBoolean(
        PERSIST_PROP_FLUID_CLOUD_BATTERY, PROP_FLUID_CLOUD_BATTERY,
        SETTINGS_FLUID_CLOUD_BATTERY, KEY_FLUID_CLOUD_BATTERY, false,
    )

    private const val KEY_FLUID_CLOUD_MATERIAL = "systemui_fluid_cloud_unified_material"
    private const val PROP_FLUID_CLOUD_MATERIAL = "oost.$KEY_FLUID_CLOUD_MATERIAL"
    private const val PERSIST_PROP_FLUID_CLOUD_MATERIAL = "persist.sys.$PROP_FLUID_CLOUD_MATERIAL"
    private const val SETTINGS_FLUID_CLOUD_MATERIAL = "oost_$KEY_FLUID_CLOUD_MATERIAL"

    fun isFluidCloudMaterialEnabled(context: Context): Boolean =
        LspPreferenceStore.readBoolean(context, KEY_FLUID_CLOUD_MATERIAL, false)

    fun setFluidCloudMaterialEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(context, KEY_FLUID_CLOUD_MATERIAL, enabled,
            listOf(PERSIST_PROP_FLUID_CLOUD_MATERIAL, PROP_FLUID_CLOUD_MATERIAL), SETTINGS_FLUID_CLOUD_MATERIAL)
    }

    fun isFluidCloudMaterialEnabledXposed(): Boolean = readXposedBoolean(
        PERSIST_PROP_FLUID_CLOUD_MATERIAL, PROP_FLUID_CLOUD_MATERIAL,
        SETTINGS_FLUID_CLOUD_MATERIAL, KEY_FLUID_CLOUD_MATERIAL, false,
    )

    enum class TrafficFeature(val key: String) {
        GoogleNetworkControl("traffic_google_network_control"),
        OtaNetworkControl("traffic_ota_network_control"),
        ShowPreinstalledApps("traffic_show_preinstalled_apps"),
        BlockCloudNetworkRules("traffic_block_cloud_network_rules"),
        ShowHiddenControls("traffic_show_hidden_controls"),
        RoamingBackgroundMode("traffic_roaming_background_mode"),
        RemoveDefaultLimit("traffic_remove_default_limit");

        val propertyKey get() = "oost.$key"
        val persistPropertyKey get() = "persist.sys.$propertyKey"
        val settingsKey get() = "oost_$key"
    }

    fun isTrafficFeatureEnabled(context: Context, feature: TrafficFeature): Boolean =
        LspPreferenceStore.readBoolean(context, feature.key, false)

    fun setTrafficFeatureEnabled(context: Context, feature: TrafficFeature, enabled: Boolean) {
        setSyncedBooleanPreference(context, feature.key, enabled,
            listOf(feature.persistPropertyKey, feature.propertyKey), feature.settingsKey)
    }

    fun isTrafficFeatureEnabledXposed(feature: TrafficFeature): Boolean = readXposedBoolean(
        feature.persistPropertyKey, feature.propertyKey, feature.settingsKey, feature.key, false,
    )

    enum class BatteryFeature(val key: String) {
        ShowCycleCount("show_battery_cycle_count"),
        RemoveRestrictPlugin("remove_battery_restrict_plugin"),
        RestoreDefaultWhitelist("restore_default_battery_optimization_whitelist");

        val propertyKey get() = "oost.$key"
        val persistPropertyKey get() = "persist.sys.$propertyKey"
        val settingsKey get() = "oost_$key"
    }

    fun isBatteryFeatureEnabled(context: Context, feature: BatteryFeature): Boolean =
        LspPreferenceStore.readBoolean(context, feature.key, false)

    fun setBatteryFeatureEnabled(context: Context, feature: BatteryFeature, enabled: Boolean) {
        setSyncedBooleanPreference(
            context, feature.key, enabled,
            listOf(feature.persistPropertyKey, feature.propertyKey), feature.settingsKey,
        )
    }

    fun isBatteryFeatureEnabledXposed(feature: BatteryFeature): Boolean = readXposedBoolean(
        feature.persistPropertyKey, feature.propertyKey, feature.settingsKey, feature.key, false,
    )

    enum class StatusBarInteractionFeature(val key: String) {
        DoubleTapToTop("systemui_double_tap_scroll_to_top"),
        RemoveToTopWhitelist("systemui_scroll_to_top_whitelist_bypass");

        val propertyKey get() = "oost.$key"
        val persistPropertyKey get() = "persist.sys.$propertyKey"
        val settingsKey get() = "oost_$key"
    }

    fun isStatusBarInteractionFeatureEnabled(context: Context, feature: StatusBarInteractionFeature): Boolean =
        LspPreferenceStore.readBoolean(context, feature.key, false)

    fun setStatusBarInteractionFeatureEnabled(context: Context, feature: StatusBarInteractionFeature, enabled: Boolean) {
        setSyncedBooleanPreference(
            context, feature.key, enabled,
            listOf(feature.persistPropertyKey, feature.propertyKey), feature.settingsKey,
        )
    }

    fun isStatusBarInteractionFeatureEnabledXposed(feature: StatusBarInteractionFeature): Boolean = readXposedBoolean(
        feature.persistPropertyKey, feature.propertyKey, feature.settingsKey, feature.key, false,
    )

    private const val KEY_HIDE_DISCONNECTED_BLUETOOTH = "systemui_hide_disconnected_bluetooth"
    private const val PROP_HIDE_DISCONNECTED_BLUETOOTH = "oost.$KEY_HIDE_DISCONNECTED_BLUETOOTH"
    private const val PERSIST_PROP_HIDE_DISCONNECTED_BLUETOOTH = "persist.sys.$PROP_HIDE_DISCONNECTED_BLUETOOTH"
    private const val SETTINGS_HIDE_DISCONNECTED_BLUETOOTH = "oost_$KEY_HIDE_DISCONNECTED_BLUETOOTH"

    fun isHideDisconnectedBluetoothEnabled(context: Context): Boolean =
        LspPreferenceStore.readBoolean(context, KEY_HIDE_DISCONNECTED_BLUETOOTH, false)

    fun setHideDisconnectedBluetoothEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context, KEY_HIDE_DISCONNECTED_BLUETOOTH, enabled,
            listOf(PERSIST_PROP_HIDE_DISCONNECTED_BLUETOOTH, PROP_HIDE_DISCONNECTED_BLUETOOTH),
            SETTINGS_HIDE_DISCONNECTED_BLUETOOTH,
        )
    }

    fun isHideDisconnectedBluetoothEnabledXposed(): Boolean = readXposedBoolean(
        PERSIST_PROP_HIDE_DISCONNECTED_BLUETOOTH, PROP_HIDE_DISCONNECTED_BLUETOOTH,
        SETTINGS_HIDE_DISCONNECTED_BLUETOOTH, KEY_HIDE_DISCONNECTED_BLUETOOTH, false,
    )

    private const val KEY_PERMANENT_CLOCK_SECONDS = "systemui_permanent_clock_seconds"
    private const val PROP_PERMANENT_CLOCK_SECONDS = "oost.$KEY_PERMANENT_CLOCK_SECONDS"
    private const val PERSIST_PROP_PERMANENT_CLOCK_SECONDS = "persist.sys.$PROP_PERMANENT_CLOCK_SECONDS"
    private const val SETTINGS_PERMANENT_CLOCK_SECONDS = "oost_$KEY_PERMANENT_CLOCK_SECONDS"

    fun isPermanentClockSecondsEnabled(context: Context): Boolean =
        LspPreferenceStore.readBoolean(context, KEY_PERMANENT_CLOCK_SECONDS, false)

    fun setPermanentClockSecondsEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context, KEY_PERMANENT_CLOCK_SECONDS, enabled,
            listOf(PERSIST_PROP_PERMANENT_CLOCK_SECONDS, PROP_PERMANENT_CLOCK_SECONDS), SETTINGS_PERMANENT_CLOCK_SECONDS,
        )
    }

    fun isPermanentClockSecondsEnabledXposed(): Boolean = readXposedBoolean(
        PERSIST_PROP_PERMANENT_CLOCK_SECONDS, PROP_PERMANENT_CLOCK_SECONDS,
        SETTINGS_PERMANENT_CLOCK_SECONDS, KEY_PERMANENT_CLOCK_SECONDS, false,
    )

    private const val KEY_EXPRESS_NO_MINI_PROGRAM = "express_no_mini_program"
    private const val PROP_EXPRESS_NO_MINI_PROGRAM = "oost.$KEY_EXPRESS_NO_MINI_PROGRAM"
    private const val PERSIST_PROP_EXPRESS_NO_MINI_PROGRAM = "persist.sys.$PROP_EXPRESS_NO_MINI_PROGRAM"
    private const val SETTINGS_EXPRESS_NO_MINI_PROGRAM = "oost_$KEY_EXPRESS_NO_MINI_PROGRAM"

    fun isExpressNoMiniProgramEnabled(context: Context): Boolean =
        LspPreferenceStore.readBoolean(context, KEY_EXPRESS_NO_MINI_PROGRAM, false)

    fun setExpressNoMiniProgramEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context, KEY_EXPRESS_NO_MINI_PROGRAM, enabled,
            listOf(PERSIST_PROP_EXPRESS_NO_MINI_PROGRAM, PROP_EXPRESS_NO_MINI_PROGRAM), SETTINGS_EXPRESS_NO_MINI_PROGRAM,
        )
    }

    fun isExpressNoMiniProgramEnabledXposed(): Boolean = readXposedBoolean(
        PERSIST_PROP_EXPRESS_NO_MINI_PROGRAM, PROP_EXPRESS_NO_MINI_PROGRAM,
        SETTINGS_EXPRESS_NO_MINI_PROGRAM, KEY_EXPRESS_NO_MINI_PROGRAM, false,
    )

    private const val KEY_IMMERSIVE_NAVIGATION = "immersive_navigation"
    private const val PROP_IMMERSIVE_NAVIGATION = "oost.$KEY_IMMERSIVE_NAVIGATION"
    private const val PERSIST_PROP_IMMERSIVE_NAVIGATION = "persist.sys.$PROP_IMMERSIVE_NAVIGATION"
    private const val SETTINGS_IMMERSIVE_NAVIGATION = "oost_$KEY_IMMERSIVE_NAVIGATION"

    fun isImmersiveNavigationEnabled(context: Context): Boolean =
        LspPreferenceStore.readBoolean(context, KEY_IMMERSIVE_NAVIGATION, false)

    fun setImmersiveNavigationEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_IMMERSIVE_NAVIGATION,
            enabled = enabled,
            propertyKeys = listOf(PERSIST_PROP_IMMERSIVE_NAVIGATION, PROP_IMMERSIVE_NAVIGATION),
            settingsGlobalKey = SETTINGS_IMMERSIVE_NAVIGATION,
        )
    }

    fun isImmersiveNavigationEnabledXposed(): Boolean = ImmersiveNavigationRules.isHandleOptionActive(
        customHandleEnabled = isNavigationHandleCustomLengthEnabledXposed(),
        optionEnabled = readXposedBoolean(
            persistPropertyKey = PERSIST_PROP_IMMERSIVE_NAVIGATION,
            propertyKey = PROP_IMMERSIVE_NAVIGATION,
            settingsKey = SETTINGS_IMMERSIVE_NAVIGATION,
            prefsKey = KEY_IMMERSIVE_NAVIGATION,
            defaultValue = false,
        ),
    )

    private const val KEY_NAVIGATION_HANDLE_CUSTOM_LENGTH = "navigation_handle_custom_length"
    private const val PROP_NAVIGATION_HANDLE_CUSTOM_LENGTH = "oost.$KEY_NAVIGATION_HANDLE_CUSTOM_LENGTH"
    private const val PERSIST_PROP_NAVIGATION_HANDLE_CUSTOM_LENGTH = "persist.sys.$PROP_NAVIGATION_HANDLE_CUSTOM_LENGTH"
    private const val SETTINGS_NAVIGATION_HANDLE_CUSTOM_LENGTH = "oost_$KEY_NAVIGATION_HANDLE_CUSTOM_LENGTH"
    private const val KEY_NAVIGATION_HANDLE_LENGTH_DP = "navigation_handle_length_dp"
    private const val PROP_NAVIGATION_HANDLE_LENGTH_DP = "oost.$KEY_NAVIGATION_HANDLE_LENGTH_DP"
    private const val PERSIST_PROP_NAVIGATION_HANDLE_LENGTH_DP = "persist.sys.$PROP_NAVIGATION_HANDLE_LENGTH_DP"
    private const val SETTINGS_NAVIGATION_HANDLE_LENGTH_DP = "oost_$KEY_NAVIGATION_HANDLE_LENGTH_DP"
    private const val KEY_NAVIGATION_HANDLE_OPACITY = "navigation_handle_opacity"
    private const val PROP_NAVIGATION_HANDLE_OPACITY = "oost.$KEY_NAVIGATION_HANDLE_OPACITY"
    private const val PERSIST_PROP_NAVIGATION_HANDLE_OPACITY = "persist.sys.$PROP_NAVIGATION_HANDLE_OPACITY"
    private const val SETTINGS_NAVIGATION_HANDLE_OPACITY = "oost_$KEY_NAVIGATION_HANDLE_OPACITY"
    private const val KEY_NAVIGATION_HANDLE_AUTO_HIDE = "navigation_handle_auto_hide"
    private const val PROP_NAVIGATION_HANDLE_AUTO_HIDE = "oost.$KEY_NAVIGATION_HANDLE_AUTO_HIDE"
    private const val PERSIST_PROP_NAVIGATION_HANDLE_AUTO_HIDE = "persist.sys.$PROP_NAVIGATION_HANDLE_AUTO_HIDE"
    private const val SETTINGS_NAVIGATION_HANDLE_AUTO_HIDE = "oost_$KEY_NAVIGATION_HANDLE_AUTO_HIDE"

    fun isNavigationHandleAutoHideEnabled(context: Context): Boolean =
        LspPreferenceStore.readBoolean(context, KEY_NAVIGATION_HANDLE_AUTO_HIDE, false)

    fun setNavigationHandleAutoHideEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_NAVIGATION_HANDLE_AUTO_HIDE,
            enabled = enabled,
            propertyKeys = listOf(PERSIST_PROP_NAVIGATION_HANDLE_AUTO_HIDE, PROP_NAVIGATION_HANDLE_AUTO_HIDE),
            settingsGlobalKey = SETTINGS_NAVIGATION_HANDLE_AUTO_HIDE,
        )
    }

    fun isNavigationHandleAutoHideEnabledXposed(): Boolean = ImmersiveNavigationRules.isHandleOptionActive(
        customHandleEnabled = isNavigationHandleCustomLengthEnabledXposed(),
        optionEnabled = readXposedBoolean(
            persistPropertyKey = PERSIST_PROP_NAVIGATION_HANDLE_AUTO_HIDE,
            propertyKey = PROP_NAVIGATION_HANDLE_AUTO_HIDE,
            settingsKey = SETTINGS_NAVIGATION_HANDLE_AUTO_HIDE,
            prefsKey = KEY_NAVIGATION_HANDLE_AUTO_HIDE,
            defaultValue = false,
        ),
    )

    fun isNavigationHandleCustomLengthEnabled(context: Context): Boolean =
        LspPreferenceStore.readBoolean(context, KEY_NAVIGATION_HANDLE_CUSTOM_LENGTH, false)

    fun setNavigationHandleCustomLengthEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_NAVIGATION_HANDLE_CUSTOM_LENGTH,
            enabled = enabled,
            propertyKeys = listOf(PERSIST_PROP_NAVIGATION_HANDLE_CUSTOM_LENGTH, PROP_NAVIGATION_HANDLE_CUSTOM_LENGTH),
            settingsGlobalKey = SETTINGS_NAVIGATION_HANDLE_CUSTOM_LENGTH,
        )
    }

    fun isNavigationHandleCustomLengthEnabledXposed(): Boolean = readXposedBoolean(
        persistPropertyKey = PERSIST_PROP_NAVIGATION_HANDLE_CUSTOM_LENGTH,
        propertyKey = PROP_NAVIGATION_HANDLE_CUSTOM_LENGTH,
        settingsKey = SETTINGS_NAVIGATION_HANDLE_CUSTOM_LENGTH,
        prefsKey = KEY_NAVIGATION_HANDLE_CUSTOM_LENGTH,
        defaultValue = false,
    )

    fun getNavigationHandleLengthDp(context: Context): Int = ImmersiveNavigationRules.normalizeLengthPreference(
        LspPreferenceStore.readInt(context, KEY_NAVIGATION_HANDLE_LENGTH_DP, ImmersiveNavigationRules.SYSTEM_DEFAULT),
    )

    fun setNavigationHandleLengthDp(context: Context, valueDp: Int) {
        val lengthDp = ImmersiveNavigationRules.normalizeLengthPreference(valueDp)
        if (!prefs(context).edit().putInt(KEY_NAVIGATION_HANDLE_LENGTH_DP, lengthDp).commitOrReport()) return
        syncReadableState(context)
        syncScalarState(
            value = lengthDp.toString(),
            propertyKeys = listOf(PERSIST_PROP_NAVIGATION_HANDLE_LENGTH_DP, PROP_NAVIGATION_HANDLE_LENGTH_DP),
            settingsGlobalKey = SETTINGS_NAVIGATION_HANDLE_LENGTH_DP,
        )
    }

    fun getNavigationHandleLengthDpXposed(): Int {
        val value = HookConfigSnapshot.int(KEY_NAVIGATION_HANDLE_LENGTH_DP, ImmersiveNavigationRules.SYSTEM_DEFAULT)
            ?: readSystemPropertyValue(PERSIST_PROP_NAVIGATION_HANDLE_LENGTH_DP)?.toIntOrNull()
            ?: readSystemPropertyValue(PROP_NAVIGATION_HANDLE_LENGTH_DP)?.toIntOrNull()
            ?: readSettingsGlobalValue(SETTINGS_NAVIGATION_HANDLE_LENGTH_DP)?.toIntOrNull()
            ?: runCatching {
                xposedPreferences.getInt(KEY_NAVIGATION_HANDLE_LENGTH_DP, ImmersiveNavigationRules.SYSTEM_DEFAULT)
            }.getOrDefault(ImmersiveNavigationRules.SYSTEM_DEFAULT)
        return ImmersiveNavigationRules.normalizeLengthPreference(value)
    }

    fun getNavigationHandleOpacity(context: Context): Int = ImmersiveNavigationRules.normalizeOpacity(
        LspPreferenceStore.readInt(context, KEY_NAVIGATION_HANDLE_OPACITY, ImmersiveNavigationRules.SYSTEM_DEFAULT),
    )

    fun setNavigationHandleOpacity(context: Context, value: Int) {
        val opacity = ImmersiveNavigationRules.normalizeOpacity(value)
        if (!prefs(context).edit().putInt(KEY_NAVIGATION_HANDLE_OPACITY, opacity).commitOrReport()) return
        syncReadableState(context)
        syncScalarState(
            value = opacity.toString(),
            propertyKeys = listOf(PERSIST_PROP_NAVIGATION_HANDLE_OPACITY, PROP_NAVIGATION_HANDLE_OPACITY),
            settingsGlobalKey = SETTINGS_NAVIGATION_HANDLE_OPACITY,
        )
    }

    fun getNavigationHandleOpacityXposed(): Int {
        val value = HookConfigSnapshot.int(KEY_NAVIGATION_HANDLE_OPACITY, ImmersiveNavigationRules.SYSTEM_DEFAULT)
            ?: readSystemPropertyValue(PERSIST_PROP_NAVIGATION_HANDLE_OPACITY)?.toIntOrNull()
            ?: readSystemPropertyValue(PROP_NAVIGATION_HANDLE_OPACITY)?.toIntOrNull()
            ?: readSettingsGlobalValue(SETTINGS_NAVIGATION_HANDLE_OPACITY)?.toIntOrNull()
            ?: runCatching {
                xposedPreferences.getInt(KEY_NAVIGATION_HANDLE_OPACITY, ImmersiveNavigationRules.SYSTEM_DEFAULT)
            }.getOrDefault(ImmersiveNavigationRules.SYSTEM_DEFAULT)
        return ImmersiveNavigationRules.normalizeOpacity(value)
    }

    private const val KEY_FILE_MANAGER_HIDE_SECURE_ACCESS_TIP = "file_manager_hide_secure_access_tip"
    private const val PROP_FILE_MANAGER_HIDE_SECURE_ACCESS_TIP =
        "oost.$KEY_FILE_MANAGER_HIDE_SECURE_ACCESS_TIP"
    private const val PERSIST_PROP_FILE_MANAGER_HIDE_SECURE_ACCESS_TIP =
        "persist.sys.$PROP_FILE_MANAGER_HIDE_SECURE_ACCESS_TIP"
    private const val SETTINGS_FILE_MANAGER_HIDE_SECURE_ACCESS_TIP =
        "oost_$KEY_FILE_MANAGER_HIDE_SECURE_ACCESS_TIP"

    fun isFileManagerHideSecureAccessTipEnabled(context: Context): Boolean =
        LspPreferenceStore.readBoolean(context, KEY_FILE_MANAGER_HIDE_SECURE_ACCESS_TIP, false)

    fun setFileManagerHideSecureAccessTipEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_FILE_MANAGER_HIDE_SECURE_ACCESS_TIP,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_FILE_MANAGER_HIDE_SECURE_ACCESS_TIP,
                PROP_FILE_MANAGER_HIDE_SECURE_ACCESS_TIP,
            ),
            settingsGlobalKey = SETTINGS_FILE_MANAGER_HIDE_SECURE_ACCESS_TIP,
        )
    }

    fun isFileManagerHideSecureAccessTipEnabledXposed(): Boolean = readXposedBoolean(
        persistPropertyKey = PERSIST_PROP_FILE_MANAGER_HIDE_SECURE_ACCESS_TIP,
        propertyKey = PROP_FILE_MANAGER_HIDE_SECURE_ACCESS_TIP,
        settingsKey = SETTINGS_FILE_MANAGER_HIDE_SECURE_ACCESS_TIP,
        prefsKey = KEY_FILE_MANAGER_HIDE_SECURE_ACCESS_TIP,
        defaultValue = false,
    )

    private const val KEY_FILE_MANAGER_NATIVE_PICKER = "file_manager_native_picker"
    private const val PROP_FILE_MANAGER_NATIVE_PICKER = "oost.$KEY_FILE_MANAGER_NATIVE_PICKER"
    private const val PERSIST_PROP_FILE_MANAGER_NATIVE_PICKER = "persist.sys.$PROP_FILE_MANAGER_NATIVE_PICKER"
    private const val SETTINGS_FILE_MANAGER_NATIVE_PICKER = "oost_$KEY_FILE_MANAGER_NATIVE_PICKER"

    fun isFileManagerNativePickerEnabled(context: Context): Boolean =
        LspPreferenceStore.readBoolean(context, KEY_FILE_MANAGER_NATIVE_PICKER, false)

    fun setFileManagerNativePickerEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_FILE_MANAGER_NATIVE_PICKER,
            enabled = enabled,
            propertyKeys = listOf(PERSIST_PROP_FILE_MANAGER_NATIVE_PICKER, PROP_FILE_MANAGER_NATIVE_PICKER),
            settingsGlobalKey = SETTINGS_FILE_MANAGER_NATIVE_PICKER,
        )
    }

    fun isFileManagerNativePickerEnabledXposed(): Boolean = readXposedBoolean(
        persistPropertyKey = PERSIST_PROP_FILE_MANAGER_NATIVE_PICKER,
        propertyKey = PROP_FILE_MANAGER_NATIVE_PICKER,
        settingsKey = SETTINGS_FILE_MANAGER_NATIVE_PICKER,
        prefsKey = KEY_FILE_MANAGER_NATIVE_PICKER,
        defaultValue = false,
    )

    /** Independent small-window options share the normal backup/boot mirrors, no master gate. */
    enum class SmallWindowFeature(val key: String) {
        WhiteBar("small_window_white_bar"),
        SafeEdgeInset("small_window_safe_edge_inset"),
        HideRecents("small_window_hide_recents"),
        KeepRunning("small_window_keep_running"),
        MuteStashed("small_window_mute_stashed"),
        LandscapeRatio("small_window_landscape_ratio"),
        LargerSize("small_window_larger_size"),
        CompactCaption("small_window_compact_caption"),
        UnlimitedCount("small_window_unlimited_count"),
        UnlimitedFrameRate("small_window_unlimited_frame_rate");

        val propertyKey: String get() = "oost.$key"
        val persistPropertyKey: String get() = "persist.sys.oost.$key"
        val settingsKey: String get() = "oost_$key"
    }

    fun isSmallWindowFeatureEnabled(context: Context, feature: SmallWindowFeature): Boolean =
        LspPreferenceStore.readBoolean(context, feature.key, false)

    fun setSmallWindowFeatureEnabled(context: Context, feature: SmallWindowFeature, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = feature.key,
            enabled = enabled,
            propertyKeys = listOf(feature.persistPropertyKey, feature.propertyKey),
            settingsGlobalKey = feature.settingsKey,
        )
    }

    fun isSmallWindowFeatureEnabledXposed(feature: SmallWindowFeature): Boolean {
        HookConfigSnapshot.boolean(feature.key, false)?.let { return it }
        readSystemPropertyToggle(feature.persistPropertyKey)?.let { return it }
        readSystemPropertyToggle(feature.propertyKey)?.let { return it }
        readSettingsGlobalToggle(feature.settingsKey)?.let { return it }
        return runCatching { xposedPreferences.getBoolean(feature.key, false) }.getOrDefault(false)
    }

    fun syncSmallWindowFeatures(context: Context) {
        SmallWindowFeature.entries.forEach { feature ->
            setSmallWindowFeatureEnabled(context, feature, prefs(context).getBoolean(feature.key, false))
        }
    }

    enum class LauncherFeature(val key: String) {
        RightmostCategories("launcher_rightmost_categories"),
        RecentMemory("launcher_recent_memory"),
        DisablePreviousTaskAutoFocus("launcher_disable_previous_task_auto_focus"),
        RecentIconAppDetails("launcher_recent_icon_app_details"),
        OldClearButton("launcher_old_clear_button"),
        HideClearButton("launcher_hide_clear_button"),
        HideShortcutBadge("launcher_hide_shortcut_badge"),
        HideWorkBadge("launcher_hide_work_badge"),
        HideCloneBadge("launcher_hide_clone_badge"),
        HideUpdateDot("launcher_hide_update_dot"),
        UnlimitedFolderInput("launcher_unlimited_folder_input"),
        Dock("launcher_dock"),
        DockBlur("launcher_dock_blur");

        val propertyKey: String get() = "oost.$key"
        val persistPropertyKey: String get() = "persist.sys.oost.$key"
        val settingsKey: String get() = "oost_$key"
    }

    fun isLauncherFeatureEnabled(context: Context, feature: LauncherFeature): Boolean =
        LspPreferenceStore.readBoolean(context, feature.key, false)

    fun setLauncherFeatureEnabled(context: Context, feature: LauncherFeature, enabled: Boolean) {
        setSyncedBooleanPreference(
            context, feature.key, enabled,
            listOf(feature.persistPropertyKey, feature.propertyKey), feature.settingsKey,
        )
    }

    fun isLauncherFeatureEnabledXposed(feature: LauncherFeature): Boolean = readXposedBoolean(
        feature.persistPropertyKey, feature.propertyKey, feature.settingsKey, feature.key, false,
    )

    fun syncLauncherFeatures(context: Context) {
        LauncherFeature.entries.forEach { feature ->
            setLauncherFeatureEnabled(context, feature, prefs(context).getBoolean(feature.key, false))
        }
    }

    enum class KeyguardFeature(val key: String) {
        FaceTapUnlock("keyguard_face_tap_unlock"),
        FaceTapAnimation("keyguard_face_tap_animation"),
        AodScreenshot("keyguard_aod_screenshot"),
        ScreenOffRecording("keyguard_screen_off_recording");

        val propertyKey: String get() = "oost.$key"
        val persistPropertyKey: String get() = "persist.sys.oost.$key"
        val settingsKey: String get() = "oost_$key"
    }

    fun isKeyguardFeatureEnabled(context: Context, feature: KeyguardFeature): Boolean =
        LspPreferenceStore.readBoolean(context, feature.key, false)

    fun setKeyguardFeatureEnabled(context: Context, feature: KeyguardFeature, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context, prefsKey = feature.key, enabled = enabled,
            propertyKeys = listOf(feature.persistPropertyKey, feature.propertyKey),
            settingsGlobalKey = feature.settingsKey,
        )
    }

    fun isKeyguardFeatureEnabledXposed(feature: KeyguardFeature): Boolean {
        HookConfigSnapshot.boolean(feature.key, false)?.let { return it }
        readSystemPropertyToggle(feature.persistPropertyKey)?.let { return it }
        readSystemPropertyToggle(feature.propertyKey)?.let { return it }
        readSettingsGlobalToggle(feature.settingsKey)?.let { return it }
        return runCatching { xposedPreferences.getBoolean(feature.key, false) }.getOrDefault(false)
    }

    fun syncKeyguardFeatures(context: Context) {
        KeyguardFeature.entries.forEach { feature ->
            setKeyguardFeatureEnabled(context, feature, prefs(context).getBoolean(feature.key, false))
        }
    }

    /** Permission Manager features share the same API 102, boot and backup mirrors. */
    enum class PermissionFeature(val key: String) {
        OldAppStartDialog("permission_old_app_start_dialog"),
        AlwaysAllowAppStart("permission_always_allow_app_start"),
        AutoUnlockRestrictedSettings("permission_auto_unlock_restricted_settings"),
        DisableMaliciousAppIntercept("permission_disable_malicious_app_intercept"),
        ExportPermissionPages("permission_export_pages"),
        NativePermissionDialogs("permission_native_dialogs");

        val propertyKey: String get() = "oost.$key"
        val persistPropertyKey: String get() = "persist.sys.oost.$key"
        val settingsKey: String get() = "oost_$key"
    }

    fun isPermissionFeatureEnabled(context: Context, feature: PermissionFeature): Boolean =
        LspPreferenceStore.readBoolean(context, feature.key, false)

    fun setPermissionFeatureEnabled(context: Context, feature: PermissionFeature, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = feature.key,
            enabled = enabled,
            propertyKeys = listOf(feature.persistPropertyKey, feature.propertyKey),
            settingsGlobalKey = feature.settingsKey,
        )
    }

    fun isPermissionFeatureEnabledXposed(feature: PermissionFeature): Boolean {
        HookConfigSnapshot.boolean(feature.key, false)?.let { return it }
        readSystemPropertyToggle(feature.persistPropertyKey)?.let { return it }
        readSystemPropertyToggle(feature.propertyKey)?.let { return it }
        readSettingsGlobalToggle(feature.settingsKey)?.let { return it }
        return xposedPreferences.getBoolean(feature.key, false)
    }

    fun syncPermissionFeatures(context: Context) {
        PermissionFeature.entries.forEach { feature ->
            setPermissionFeatureEnabled(context, feature, prefs(context).getBoolean(feature.key, false))
        }
    }
    /** Notification removal options share the same API 102, boot and backup mirrors. */
    enum class NotificationRemovalFeature(val key: String) {
        Overlay("notify_remove_overlay"),
        Vpn("notify_remove_vpn"),
        DeveloperMode("notify_remove_developer"),
        ChargingCompleted("notify_remove_charging"),
        Flashlight("notify_remove_flashlight"),
        HighBatteryConsumption("notify_remove_consumption"),
        HighPerformance("notify_remove_performance"),
        DoNotDisturb("notify_remove_dnd"),
        HotspotPowerConsumption("notify_remove_hotspot"),
        MuteNotifications("notify_remove_mute"),
        GtMode("notify_remove_gt");

        val propertyKey: String get() = "oost.$key"
        val persistPropertyKey: String get() = "persist.sys.oost.$key"
        val settingsKey: String get() = "oost_$key"
    }

    fun isNotificationRemovalEnabled(context: Context, feature: NotificationRemovalFeature): Boolean =
        LspPreferenceStore.readBoolean(context, feature.key, false)

    fun setNotificationRemovalEnabled(context: Context, feature: NotificationRemovalFeature, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = feature.key,
            enabled = enabled,
            propertyKeys = listOf(feature.persistPropertyKey, feature.propertyKey),
            settingsGlobalKey = feature.settingsKey,
        )
    }

    fun isNotificationRemovalEnabledXposed(feature: NotificationRemovalFeature): Boolean {
        HookConfigSnapshot.boolean(feature.key, false)?.let { return it }
        readSystemPropertyToggle(feature.persistPropertyKey)?.let { return it }
        readSystemPropertyToggle(feature.propertyKey)?.let { return it }
        readSettingsGlobalToggle(feature.settingsKey)?.let { return it }
        return xposedPreferences.getBoolean(feature.key, false)
    }

    fun syncNotificationRemovalFeatures(context: Context) {
        NotificationRemovalFeature.entries.forEach { feature ->
            setNotificationRemovalEnabled(context, feature, prefs(context).getBoolean(feature.key, false))
        }
    }
    enum class InstallerFeature(val key: String, val defaultValue: Boolean = false) {
        Enabled("installer_enabled"),
        Uninstall("installer_intercept_uninstall"),
        Session("installer_intercept_session_install"),
        InterceptSystem("installer_intercept_system", true),
        RemoveDefaultAppPolicy("installer_remove_default_app_policy", true);
    }

    const val INSTALLER_PACKAGE = "installer_selected_package"
    const val INSTALLER_SYSTEM_PACKAGE = "installer_system_package"
    private val installerTextKeys = setOf(INSTALLER_PACKAGE, INSTALLER_SYSTEM_PACKAGE)

    fun isInstallerFeatureEnabled(context: Context, feature: InstallerFeature): Boolean =
        prefs(context).getBoolean(feature.key, feature.defaultValue)

    fun setInstallerFeatureEnabled(context: Context, feature: InstallerFeature, enabled: Boolean) {
        setSyncedBooleanPreference(context, feature.key, enabled,
            listOf("persist.sys.oost.${feature.key}", "oost.${feature.key}"), "oost_${feature.key}")
    }

    fun isInstallerFeatureEnabledXposed(feature: InstallerFeature): Boolean {
        HookConfigSnapshot.boolean(feature.key, feature.defaultValue)?.let { return it }
        readSystemPropertyToggle("persist.sys.oost.${feature.key}")?.let { return it }
        return xposedPreferences.getBoolean(feature.key, feature.defaultValue)
    }

    fun installerText(context: Context, key: String): String = prefs(context).getString(key, "").orEmpty()

    fun installerTextXposed(key: String): String =
        if (HookConfigSnapshot.isAvailable) HookConfigSnapshot.string(key, "").orEmpty()
        else xposedPreferences.getString(key, "").orEmpty()

    fun setInstallerText(context: Context, key: String, value: String) {
        require(key in installerTextKeys && value.length <= 8192)
        require(InstallerRoutingPolicy.validPackage(value))
        val editor = prefs(context).edit().putString(key, value.trim())
        require(editor.commit())
        syncReadableState(context)
    }

    fun syncInstallerFeatures(context: Context) {
        InstallerFeature.entries.forEach { setInstallerFeatureEnabled(context, it, isInstallerFeatureEnabled(context, it)) }
        runCatching {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(
                android.net.Uri.parse("content://onextbox/choice.apk"), InstallerRoutingPolicy.APK_TYPE)
            val systemPackage = context.packageManager.queryIntentActivities(intent,
                android.content.pm.PackageManager.MATCH_ALL or android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                .firstOrNull {
                    it.activityInfo.exported && it.activityInfo.enabled && it.activityInfo.applicationInfo.enabled &&
                        it.activityInfo.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
                }?.activityInfo?.packageName.orEmpty()
            if (installerText(context, INSTALLER_SYSTEM_PACKAGE) != systemPackage) {
                setInstallerText(context, INSTALLER_SYSTEM_PACKAGE, systemPackage)
            }
        }.onFailure { HookLog.w("ONextBox-Installer", "System installer discovery failed", it) }
    }

    private const val KEY_NATIVE_NOTIFY_ICON = "native_notify_icon_enabled"
    private const val KEY_EXTREME_REFRESH_165 = "extreme_refresh_165_enabled"
    private const val KEY_RECENT_TASK_RADIUS = "recent_task_radius_enabled"
    private const val KEY_AOD_ENHANCE = "aod_enhance_enabled"
    private const val KEY_ASSISTANT_POWER_MODE = "assistant_power_mode"
    private const val KEY_ASSISTANT_GESTURE_CIRCLE = "assistant_gesture_circle_enabled"
    private const val KEY_ASSISTANT_GESTURE_CIRCLE_C17 = "assistant_gesture_circle_c17_enabled"
    private const val KEY_ASSISTANT_NATIVE_POWER = "assistant_native_power_enabled"
    private const val KEY_ASSISTANT_INTERNATIONAL_POWER_CHORD =
        "assistant_international_power_chord_enabled"
    private const val KEY_ASSISTANT_NATIVE_CIRCLE = "assistant_native_circle_enabled"
    private const val KEY_RECENT_TASK_RADIUS_DP = "recent_task_radius_dp"
    private const val KEY_AOD_INIT_DARK_BRIGHTNESS = "aod_init_dark_brightness"
    private const val KEY_AOD_INIT_BRIGHT_BRIGHTNESS = "aod_init_bright_brightness"
    private const val KEY_AOD_RUNNING_BRIGHTNESS_MULTIPLIER = "aod_running_brightness_multiplier"
    private const val KEY_AOD_PANORAMIC_SUPPORT = "aod_panoramic_support"
    private const val KEY_AOD_SETTINGS_SWITCH = "aod_settings_switch"
    private const val KEY_AOD_SINGLE_CLICK_BLOCK = "aod_single_click_block"
    private const val KEY_NATIVE_NOTIFICATION_BUBBLES = "native_notification_bubbles"
    private const val KEY_SYSTEMUI_INTERNATIONAL_NETWORK_DISPLAY =
        "systemui_international_network_display"
    private const val KEY_SYSTEMUI_HIDE_MOBILE_ROAMING_INDICATOR =
        "systemui_hide_mobile_roaming_indicator"
    private const val KEY_SYSTEMUI_HIDE_NETWORK_ACTIVITY_INDICATOR =
        "systemui_hide_network_activity_indicator"
    private const val KEY_SYSTEMUI_NATIVE_POWER_MENU = "systemui_native_power_menu"
    private const val KEY_SYSTEMUI_RESTORE_C16_NETWORK_ICON_ORDER =
        "systemui_restore_c16_network_icon_order"
    private const val KEY_SYSTEMUI_INTERNATIONAL_NOTIFICATION_STYLE =
        "systemui_international_notification_style"
    private const val KEY_SYSTEMUI_FORCE_TONAL_SPOT = "systemui_force_tonal_spot"
    private const val KEY_SYSTEMUI_MONET_COLOR_SPEC_MODE = "systemui_monet_color_spec_mode"
    private const val KEY_SYSTEMUI_HIDE_QS_EDIT = "systemui_hide_qs_edit"
    private const val KEY_SYSTEMUI_HIDE_QS_SETTINGS = "systemui_hide_qs_settings"
    private const val KEY_SYSTEMUI_HIDE_QS_TOP_CARRIER = "systemui_hide_qs_top_carrier"
    private const val KEY_SYSTEMUI_HIDE_QS_MORE = "systemui_hide_qs_more"
    private const val KEY_SYSTEMUI_FORCE_NATIVE_CLIPBOARD_OVERLAY = "systemui_force_native_clipboard_overlay"
    private const val KEY_SETTINGS_INTERNATIONAL = "settings_international_enabled"
    private const val KEY_SETTINGS_FORCE_APP_AUTO_START = "settings_force_app_auto_start"
    private const val KEY_SETTINGS_INTERNATIONAL_WALLET = "settings_international_wallet"
    private const val KEY_SETTINGS_RESTORE_DOMESTIC_ABOUT_DEVICE =
        "settings_restore_domestic_about_device"
    private const val KEY_SETTINGS_RESTORE_DOMESTIC_AUXILIARY_FUNCTIONS =
        "settings_restore_domestic_auxiliary_functions"
    private const val KEY_SETTINGS_RESTORE_SMART_LOCK = "settings_restore_smart_lock"
    private const val KEY_SETTINGS_FORCE_GOOGLE_ENTRY = "settings_force_google_entry"
    private const val KEY_SETTINGS_C15_ABOUT_LAYOUT = "settings_c15_about_layout"
    private const val KEY_SETTINGS_SKIP_SPECIAL_PERMISSION_RISK_CONFIRM = "settings_skip_special_permission_risk_confirm"
    private const val KEY_SETTINGS_RESTORE_APP_OPEN_BUTTON = "settings_restore_app_open_button"
    private const val KEY_WALLPAPERS_RED_ONE_ENTRY = "wallpapers_red_one_entry"
    private const val KEY_SETTINGS_UNLOCK_REFRESH_RATE = "settings_unlock_refresh_rate"
    private const val KEY_SETTINGS_FORCE_GLOBAL_EXTREME_REFRESH_RATE =
        "settings_force_global_extreme_refresh_rate"
    private const val KEY_GMS_REGION_RESTRICTION_BYPASS = "gms_region_restriction_bypass"
    private const val KEY_ESIM_REGION_RESTRICTION_BYPASS = "esim_region_restriction_bypass"
    private const val KEY_ESIM_CONFIRMATION_CODE_PROMPT = "esim_confirmation_code_prompt"
    private const val KEY_ESIM_REGION_RESTRICTION_OVERRIDE = "esim_region_restriction_override"
    private const val KEY_ESIM_PROFILE_LIMIT_BYPASS = "esim_profile_limit_bypass"
    private const val KEY_MOBILE_NETWORK_HIDE_AI_LINK_BOOST =
        "mobile_network_hide_ai_link_boost"
    private const val KEY_MOBILE_NETWORK_HIDE_ROAMING_SERVICE =
        "mobile_network_hide_roaming_service"
    private const val KEY_MOBILE_NETWORK_HIDE_HIGH_DATA_SIM_CARD =
        "mobile_network_hide_high_data_sim_card"
    private const val KEY_MOBILE_NETWORK_HIDE_SMART_CLOUD_ACCELERATION =
        "mobile_network_hide_smart_cloud_acceleration"
    private const val KEY_MOBILE_NETWORK_HIDE_PHONE_NUMBER =
        "mobile_network_hide_phone_number"
    private const val KEY_MOBILE_NETWORK_FORCE_CARRIER_OPTIONS =
        "mobile_network_force_carrier_options"
    private const val KEY_APP_MARKET_REGION_RESTRICTION_BYPASS =
        "app_market_region_restriction_bypass"
    private const val KEY_APP_MARKET_REMOVE_SPLASH_RECOMMENDATIONS =
        "app_market_remove_splash_recommendations"
    private const val KEY_APP_MARKET_REMOVE_UPDATE_DOWNLOAD_RECOMMENDATIONS =
        "app_market_remove_update_download_recommendations"
    private const val KEY_APP_MARKET_REMOVE_MINE_RECOMMENDATIONS =
        "app_market_remove_mine_recommendations"
    private const val KEY_APP_MARKET_HIDE_SEARCH_HOME_RECOMMENDATIONS =
        "app_market_hide_search_home_recommendations"
    private const val KEY_APP_MARKET_HIDE_SEARCH_RESULT_RECOMMENDATIONS =
        "app_market_hide_search_result_recommendations"
    private const val KEY_APP_MARKET_HIDE_DETAIL_RECOMMENDATIONS =
        "app_market_hide_detail_recommendations"
    private const val KEY_ATHENA_C17_SWIPE_UP_PROTECTION = "athena_c17_vpn_protection_enabled"
    private const val KEY_OK_GOOGLE_HOTWORD_COMPATIBILITY = "ok_google_hotword_compatibility_enabled"
    private const val KEY_LAUNCHER_HIDE_WIDGET_LABELS = "launcher_hide_widget_labels"
    private const val KEY_LAUNCHER_SEARCH_BAR_MODE = "launcher_taskbar_search_box"
    private const val KEY_LAUNCHER_SEARCH_COMPATIBILITY = "launcher_search_compatibility"
    private const val FLAG_FILE_PATH_NATIVE_NOTIFY_ICON = "/data/local/oost_native_notify_icon.flag"
    private const val FLAG_FILE_PATH_EXTREME_REFRESH_165 = "/data/local/oost_extreme_refresh_165.flag"
    private const val FLAG_FILE_PATH_RECENT_TASK_RADIUS = "/data/local/oost_recent_task_radius.flag"
    private const val FLAG_FILE_PATH_AOD_ENHANCE = "/data/local/oost_aod_enhance.flag"
    private const val FLAG_FILE_PATH_NATIVE_NOTIFICATION_BUBBLES = "/data/local/oost_native_notification_bubbles.flag"
    private const val LEGACY_FLAG_FILE_PATH_NATIVE_NOTIFY_ICON = "/data/local/tmp/oost_native_notify_icon.flag"
    private const val LEGACY_FLAG_FILE_PATH_EXTREME_REFRESH_165 = "/data/local/tmp/oost_extreme_refresh_165.flag"
    private const val LEGACY_FLAG_FILE_PATH_RECENT_TASK_RADIUS = "/data/local/tmp/oost_recent_task_radius.flag"
    private const val LEGACY_FLAG_FILE_PATH_AOD_ENHANCE = "/data/local/tmp/oost_aod_enhance.flag"
    private const val LEGACY_FLAG_FILE_PATH_NATIVE_NOTIFICATION_BUBBLES = "/data/local/tmp/oost_native_notification_bubbles.flag"
    private const val PROP_KEY_NATIVE_NOTIFY_ICON = "oost.native_notify_icon"
    private const val PROP_KEY_EXTREME_REFRESH_165 = "oost.extreme_refresh_165"
    private const val PROP_KEY_RECENT_TASK_RADIUS = "oost.recent_task_radius"
    private const val PROP_KEY_AOD_ENHANCE = "oost.aod_enhance"
    private const val PROP_KEY_ASSISTANT_POWER_MODE = "oost.assistant_power_mode"
    private const val PROP_KEY_ASSISTANT_GESTURE_CIRCLE = "oost.assistant_gesture_circle"
    private const val PROP_KEY_ASSISTANT_GESTURE_CIRCLE_C17 = "oost.assistant_gesture_circle_c17"
    private const val PROP_KEY_ASSISTANT_NATIVE_POWER = "oost.assistant_native_power"
    private const val PROP_KEY_ASSISTANT_INTERNATIONAL_POWER_CHORD =
        "oost.assistant_international_power_chord"
    private const val PROP_KEY_ASSISTANT_NATIVE_CIRCLE = "oost.assistant_native_circle"
    private const val PROP_KEY_RECENT_TASK_RADIUS_DP = "oost.recent_task_radius_dp"
    private const val PROP_KEY_AOD_INIT_DARK_BRIGHTNESS = "oost.aod_init_dark_brightness"
    private const val PROP_KEY_AOD_INIT_BRIGHT_BRIGHTNESS = "oost.aod_init_bright_brightness"
    private const val PROP_KEY_AOD_RUNNING_BRIGHTNESS_MULTIPLIER = "oost.aod_running_brightness_multiplier"
    private const val PROP_KEY_AOD_PANORAMIC_SUPPORT = "oost.aod_panoramic_support"
    private const val PROP_KEY_AOD_SETTINGS_SWITCH = "oost.aod_settings_switch"
    private const val PROP_KEY_AOD_SINGLE_CLICK_BLOCK = "oost.aod_single_click_block"
    private const val PROP_KEY_NATIVE_NOTIFICATION_BUBBLES = "oost.native_notification_bubbles"
    private const val PROP_KEY_SYSTEMUI_INTERNATIONAL_NETWORK_DISPLAY =
        "oost.systemui_international_network_display"
    private const val PROP_KEY_SYSTEMUI_HIDE_MOBILE_ROAMING_INDICATOR =
        "oost.systemui_hide_mobile_roaming_indicator"
    private const val PROP_KEY_SYSTEMUI_HIDE_NETWORK_ACTIVITY_INDICATOR =
        "oost.systemui_hide_network_activity_indicator"
    private const val PROP_KEY_SYSTEMUI_NATIVE_POWER_MENU = "oost.systemui_native_power_menu"
    private const val PROP_KEY_SYSTEMUI_RESTORE_C16_NETWORK_ICON_ORDER =
        "oost.systemui_restore_c16_network_icon_order"
    private const val PROP_KEY_SYSTEMUI_INTERNATIONAL_NOTIFICATION_STYLE =
        "oost.systemui_international_notification_style"
    private const val PROP_KEY_SYSTEMUI_FORCE_TONAL_SPOT = "oost.systemui_force_tonal_spot"
    private const val PROP_KEY_SYSTEMUI_MONET_COLOR_SPEC_MODE =
        "oost.systemui_monet_color_spec_mode"
    private const val PROP_KEY_SYSTEMUI_HIDE_QS_EDIT = "oost.systemui_hide_qs_edit"
    private const val PROP_KEY_SYSTEMUI_HIDE_QS_SETTINGS = "oost.systemui_hide_qs_settings"
    private const val PROP_KEY_SYSTEMUI_HIDE_QS_TOP_CARRIER = "oost.systemui_hide_qs_top_carrier"
    private const val PROP_KEY_SYSTEMUI_HIDE_QS_MORE = "oost.systemui_hide_qs_more"
    private const val PROP_KEY_SYSTEMUI_FORCE_NATIVE_CLIPBOARD_OVERLAY = "oost.systemui_force_native_clipboard_overlay"
    private const val PROP_KEY_SETTINGS_INTERNATIONAL = "oost.settings_international"
    private const val PROP_KEY_SETTINGS_FORCE_APP_AUTO_START = "oost.settings_force_app_auto_start"
    private const val PROP_KEY_SETTINGS_INTERNATIONAL_WALLET = "oost.settings_international_wallet"
    private const val PROP_KEY_SETTINGS_RESTORE_DOMESTIC_ABOUT_DEVICE =
        "oost.settings_restore_domestic_about_device"
    private const val PROP_KEY_SETTINGS_RESTORE_DOMESTIC_AUXILIARY_FUNCTIONS =
        "oost.settings_restore_domestic_auxiliary_functions"
    private const val PROP_KEY_SETTINGS_RESTORE_SMART_LOCK = "oost.settings_restore_smart_lock"
    private const val PROP_KEY_SETTINGS_FORCE_GOOGLE_ENTRY = "oost.settings_force_google_entry"
    private const val PROP_KEY_SETTINGS_C15_ABOUT_LAYOUT = "oost.settings_c15_about_layout"
    private const val PROP_KEY_SETTINGS_SKIP_SPECIAL_PERMISSION_RISK_CONFIRM = "oost.settings_skip_special_permission_risk_confirm"
    private const val PROP_KEY_SETTINGS_RESTORE_APP_OPEN_BUTTON = "oost.settings_restore_app_open_button"
    private const val PROP_KEY_WALLPAPERS_RED_ONE_ENTRY = "oost.wallpapers_red_one_entry"
    private const val PROP_KEY_SETTINGS_UNLOCK_REFRESH_RATE = "oost.settings_unlock_refresh_rate"
    private const val PROP_KEY_SETTINGS_FORCE_GLOBAL_EXTREME_REFRESH_RATE =
        "oost.settings_force_global_extreme_refresh_rate"
    private const val PROP_KEY_GMS_REGION_RESTRICTION_BYPASS = "oost.gms_region_restriction_bypass"
    private const val PROP_KEY_ESIM_REGION_RESTRICTION_BYPASS =
        "oost.esim_region_restriction_bypass"
    private const val PROP_KEY_ESIM_CONFIRMATION_CODE_PROMPT =
        "oost.esim_confirmation_code_prompt"
    private const val PROP_KEY_ESIM_REGION_RESTRICTION_OVERRIDE =
        "oost.esim_region_restriction_override"
    private const val PROP_KEY_ESIM_PROFILE_LIMIT_BYPASS =
        "oost.esim_profile_limit_bypass"
    private const val PROP_KEY_MOBILE_NETWORK_HIDE_AI_LINK_BOOST =
        "oost.mobile_network_hide_ai_link_boost"
    private const val PROP_KEY_MOBILE_NETWORK_HIDE_ROAMING_SERVICE =
        "oost.mobile_network_hide_roaming_service"
    private const val PROP_KEY_MOBILE_NETWORK_HIDE_HIGH_DATA_SIM_CARD =
        "oost.mobile_network_hide_high_data_sim_card"
    private const val PROP_KEY_MOBILE_NETWORK_HIDE_SMART_CLOUD_ACCELERATION =
        "oost.mobile_network_hide_smart_cloud_acceleration"
    private const val PROP_KEY_MOBILE_NETWORK_HIDE_PHONE_NUMBER =
        "oost.mobile_network_hide_phone_number"
    private const val PROP_KEY_MOBILE_NETWORK_FORCE_CARRIER_OPTIONS =
        "oost.mobile_network_force_carrier_options"
    private const val PROP_KEY_APP_MARKET_REGION_RESTRICTION_BYPASS =
        "oost.app_market_region_restriction_bypass"
    private const val PROP_KEY_APP_MARKET_REMOVE_SPLASH_RECOMMENDATIONS =
        "oost.app_market_remove_splash_recommendations"
    private const val PROP_KEY_APP_MARKET_REMOVE_UPDATE_DOWNLOAD_RECOMMENDATIONS =
        "oost.app_market_remove_update_download_recommendations"
    private const val PROP_KEY_APP_MARKET_REMOVE_MINE_RECOMMENDATIONS =
        "oost.app_market_remove_mine_recommendations"
    private const val PROP_KEY_APP_MARKET_HIDE_SEARCH_HOME_RECOMMENDATIONS =
        "oost.app_market_hide_search_home_recommendations"
    private const val PROP_KEY_APP_MARKET_HIDE_SEARCH_RESULT_RECOMMENDATIONS =
        "oost.app_market_hide_search_result_recommendations"
    private const val PROP_KEY_APP_MARKET_HIDE_DETAIL_RECOMMENDATIONS =
        "oost.app_market_hide_detail_recommendations"
    private const val PROP_KEY_ATHENA_C17_SWIPE_UP_PROTECTION = "oost.athena_c17_vpn_protection"
    private const val PROP_KEY_OK_GOOGLE_HOTWORD_COMPATIBILITY = "oost.ok_google_hotword_compatibility"
    private const val PROP_KEY_LAUNCHER_HIDE_WIDGET_LABELS = "oost.launcher_hide_widget_labels"
    private const val PROP_KEY_LAUNCHER_SEARCH_BAR_MODE = "oost.launcher_taskbar_search_box"
    private const val PROP_KEY_LAUNCHER_SEARCH_COMPATIBILITY = "oost.launcher_search_compatibility"
    private const val PERSIST_PROP_KEY_NATIVE_NOTIFY_ICON = "persist.sys.oost.native_notify_icon"
    private const val PERSIST_PROP_KEY_EXTREME_REFRESH_165 = "persist.sys.oost.extreme_refresh_165"
    private const val PERSIST_PROP_KEY_RECENT_TASK_RADIUS = "persist.sys.oost.recent_task_radius"
    private const val PERSIST_PROP_KEY_AOD_ENHANCE = "persist.sys.oost.aod_enhance"
    private const val PERSIST_PROP_KEY_ASSISTANT_POWER_MODE = "persist.sys.oost.assistant_power_mode"
    private const val PERSIST_PROP_KEY_ASSISTANT_GESTURE_CIRCLE = "persist.sys.oost.assistant_gesture_circle"
    private const val PERSIST_PROP_KEY_ASSISTANT_GESTURE_CIRCLE_C17 =
        "persist.sys.oost.assistant_gesture_circle_c17"
    private const val PERSIST_PROP_KEY_ASSISTANT_NATIVE_POWER =
        "persist.sys.oost.assistant_native_power"
    private const val PERSIST_PROP_KEY_ASSISTANT_INTERNATIONAL_POWER_CHORD =
        "persist.sys.oost.assistant_international_power_chord"
    private const val PERSIST_PROP_KEY_ASSISTANT_NATIVE_CIRCLE =
        "persist.sys.oost.assistant_native_circle"
    private const val PERSIST_PROP_KEY_RECENT_TASK_RADIUS_DP = "persist.sys.oost.recent_task_radius_dp"
    private const val PERSIST_PROP_KEY_AOD_INIT_DARK_BRIGHTNESS = "persist.sys.oost.aod_init_dark_brightness"
    private const val PERSIST_PROP_KEY_AOD_INIT_BRIGHT_BRIGHTNESS = "persist.sys.oost.aod_init_bright_brightness"
    private const val PERSIST_PROP_KEY_AOD_RUNNING_BRIGHTNESS_MULTIPLIER = "persist.sys.oost.aod_running_brightness_multiplier"
    private const val PERSIST_PROP_KEY_AOD_PANORAMIC_SUPPORT = "persist.sys.oost.aod_panoramic_support"
    private const val PERSIST_PROP_KEY_AOD_SETTINGS_SWITCH = "persist.sys.oost.aod_settings_switch"
    private const val PERSIST_PROP_KEY_AOD_SINGLE_CLICK_BLOCK = "persist.sys.oost.aod_single_click_block"
    private const val PERSIST_PROP_KEY_NATIVE_NOTIFICATION_BUBBLES = "persist.sys.oost.native_notification_bubbles"
    private const val PERSIST_PROP_KEY_SYSTEMUI_INTERNATIONAL_NETWORK_DISPLAY =
        "persist.sys.oost.systemui_international_network_display"
    private const val PERSIST_PROP_KEY_SYSTEMUI_HIDE_MOBILE_ROAMING_INDICATOR =
        "persist.sys.oost.systemui_hide_mobile_roaming_indicator"
    private const val PERSIST_PROP_KEY_SYSTEMUI_HIDE_NETWORK_ACTIVITY_INDICATOR =
        "persist.sys.oost.systemui_hide_network_activity_indicator"
    private const val PERSIST_PROP_KEY_SYSTEMUI_NATIVE_POWER_MENU =
        "persist.sys.oost.systemui_native_power_menu"
    private const val PERSIST_PROP_KEY_SYSTEMUI_RESTORE_C16_NETWORK_ICON_ORDER =
        "persist.sys.oost.systemui_restore_c16_network_icon_order"
    private const val PERSIST_PROP_KEY_SYSTEMUI_INTERNATIONAL_NOTIFICATION_STYLE =
        "persist.sys.oost.systemui_international_notification_style"
    private const val PERSIST_PROP_KEY_SYSTEMUI_FORCE_TONAL_SPOT =
        "persist.sys.oost.systemui_force_tonal_spot"
    private const val PERSIST_PROP_KEY_SYSTEMUI_MONET_COLOR_SPEC_MODE =
        "persist.sys.oost.systemui_monet_color_spec_mode"
    private const val PERSIST_PROP_KEY_SYSTEMUI_HIDE_QS_EDIT = "persist.sys.oost.systemui_hide_qs_edit"
    private const val PERSIST_PROP_KEY_SYSTEMUI_HIDE_QS_SETTINGS = "persist.sys.oost.systemui_hide_qs_settings"
    private const val PERSIST_PROP_KEY_SYSTEMUI_HIDE_QS_TOP_CARRIER = "persist.sys.oost.systemui_hide_qs_top_carrier"
    private const val PERSIST_PROP_KEY_SYSTEMUI_HIDE_QS_MORE = "persist.sys.oost.systemui_hide_qs_more"
    private const val PERSIST_PROP_KEY_SYSTEMUI_FORCE_NATIVE_CLIPBOARD_OVERLAY = "persist.sys.oost.systemui_force_native_clipboard_overlay"
    private const val PERSIST_PROP_KEY_SETTINGS_INTERNATIONAL = "persist.sys.oost.settings_international"
    private const val PERSIST_PROP_KEY_SETTINGS_FORCE_APP_AUTO_START =
        "persist.sys.oost.settings_force_app_auto_start"
    private const val PERSIST_PROP_KEY_SETTINGS_INTERNATIONAL_WALLET =
        "persist.sys.oost.settings_international_wallet"
    private const val PERSIST_PROP_KEY_SETTINGS_RESTORE_DOMESTIC_ABOUT_DEVICE =
        "persist.sys.oost.settings_restore_domestic_about_device"
    private const val PERSIST_PROP_KEY_SETTINGS_RESTORE_DOMESTIC_AUXILIARY_FUNCTIONS =
        "persist.sys.oost.settings_restore_domestic_auxiliary_functions"
    private const val PERSIST_PROP_KEY_SETTINGS_RESTORE_SMART_LOCK =
        "persist.sys.oost.settings_restore_smart_lock"
    private const val PERSIST_PROP_KEY_SETTINGS_FORCE_GOOGLE_ENTRY = "persist.sys.oost.settings_force_google_entry"
    private const val PERSIST_PROP_KEY_SETTINGS_C15_ABOUT_LAYOUT = "persist.sys.oost.settings_c15_about_layout"
    private const val PERSIST_PROP_KEY_SETTINGS_SKIP_SPECIAL_PERMISSION_RISK_CONFIRM = "persist.sys.oost.settings_skip_special_permission_risk_confirm"
    private const val PERSIST_PROP_KEY_SETTINGS_RESTORE_APP_OPEN_BUTTON = "persist.sys.oost.settings_restore_app_open_button"
    private const val PERSIST_PROP_KEY_WALLPAPERS_RED_ONE_ENTRY = "persist.sys.oost.wallpapers_red_one_entry"
    private const val PERSIST_PROP_KEY_SETTINGS_UNLOCK_REFRESH_RATE = "persist.sys.oost.settings_unlock_refresh_rate"
    private const val PERSIST_PROP_KEY_SETTINGS_FORCE_GLOBAL_EXTREME_REFRESH_RATE =
        "persist.sys.oost.settings_force_global_extreme_refresh_rate"
    private const val PERSIST_PROP_KEY_GMS_REGION_RESTRICTION_BYPASS = "persist.sys.oost.gms_region_restriction_bypass"
    private const val PERSIST_PROP_KEY_ESIM_REGION_RESTRICTION_BYPASS =
        "persist.sys.oost.esim_region_restriction_bypass"
    private const val PERSIST_PROP_KEY_ESIM_CONFIRMATION_CODE_PROMPT =
        "persist.sys.oost.esim_confirmation_code_prompt"
    private const val PERSIST_PROP_KEY_ESIM_REGION_RESTRICTION_OVERRIDE =
        "persist.sys.oost.esim_region_restriction_override"
    private const val PERSIST_PROP_KEY_ESIM_PROFILE_LIMIT_BYPASS =
        "persist.sys.oost.esim_profile_limit_bypass"
    private const val PERSIST_PROP_KEY_MOBILE_NETWORK_HIDE_AI_LINK_BOOST =
        "persist.sys.oost.mobile_network_hide_ai_link_boost"
    private const val PERSIST_PROP_KEY_MOBILE_NETWORK_HIDE_ROAMING_SERVICE =
        "persist.sys.oost.mobile_network_hide_roaming_service"
    private const val PERSIST_PROP_KEY_MOBILE_NETWORK_HIDE_HIGH_DATA_SIM_CARD =
        "persist.sys.oost.mobile_network_hide_high_data_sim_card"
    private const val PERSIST_PROP_KEY_MOBILE_NETWORK_HIDE_SMART_CLOUD_ACCELERATION =
        "persist.sys.oost.mobile_network_hide_smart_cloud_acceleration"
    private const val PERSIST_PROP_KEY_MOBILE_NETWORK_HIDE_PHONE_NUMBER =
        "persist.sys.oost.mobile_network_hide_phone_number"
    private const val PERSIST_PROP_KEY_MOBILE_NETWORK_FORCE_CARRIER_OPTIONS =
        "persist.sys.oost.mobile_network_force_carrier_options"
    private const val PERSIST_PROP_KEY_APP_MARKET_REGION_RESTRICTION_BYPASS =
        "persist.sys.oost.app_market_region_restriction_bypass"
    private const val PERSIST_PROP_KEY_APP_MARKET_REMOVE_SPLASH_RECOMMENDATIONS =
        "persist.sys.oost.app_market_remove_splash_recommendations"
    private const val PERSIST_PROP_KEY_APP_MARKET_REMOVE_UPDATE_DOWNLOAD_RECOMMENDATIONS =
        "persist.sys.oost.app_market_remove_update_download_recommendations"
    private const val PERSIST_PROP_KEY_APP_MARKET_REMOVE_MINE_RECOMMENDATIONS =
        "persist.sys.oost.app_market_remove_mine_recommendations"
    private const val PERSIST_PROP_KEY_APP_MARKET_HIDE_SEARCH_HOME_RECOMMENDATIONS =
        "persist.sys.oost.app_market_hide_search_home_recommendations"
    private const val PERSIST_PROP_KEY_APP_MARKET_HIDE_SEARCH_RESULT_RECOMMENDATIONS =
        "persist.sys.oost.app_market_hide_search_result_recommendations"
    private const val PERSIST_PROP_KEY_APP_MARKET_HIDE_DETAIL_RECOMMENDATIONS =
        "persist.sys.oost.app_market_hide_detail_recommendations"
    private const val PERSIST_PROP_KEY_ATHENA_C17_SWIPE_UP_PROTECTION =
        "persist.sys.oost.athena_c17_vpn_protection"
    private const val PERSIST_PROP_KEY_OK_GOOGLE_HOTWORD_COMPATIBILITY = "persist.sys.oost.ok_google_hotword_compatibility"
    private const val PERSIST_PROP_KEY_LAUNCHER_HIDE_WIDGET_LABELS =
        "persist.sys.oost.launcher_hide_widget_labels"
    private const val PERSIST_PROP_KEY_LAUNCHER_SEARCH_BAR_MODE =
        "persist.sys.oost.launcher_taskbar_search_box"
    private const val PERSIST_PROP_KEY_LAUNCHER_SEARCH_COMPATIBILITY =
        "persist.sys.oost.launcher_search_compatibility"
    private const val SETTINGS_KEY_NATIVE_NOTIFY_ICON = "oost_native_notify_icon"
    private const val SETTINGS_KEY_EXTREME_REFRESH_165 = "oost_extreme_refresh_165"
    private const val SETTINGS_KEY_RECENT_TASK_RADIUS = "oost_recent_task_radius"
    private const val SETTINGS_KEY_AOD_ENHANCE = "oost_aod_enhance"
    private const val SETTINGS_KEY_ASSISTANT_POWER_MODE = "oost_assistant_power_mode"
    private const val SETTINGS_KEY_ASSISTANT_GESTURE_CIRCLE = "oost_assistant_gesture_circle"
    private const val SETTINGS_KEY_ASSISTANT_GESTURE_CIRCLE_C17 = "oost_assistant_gesture_circle_c17"
    private const val SETTINGS_KEY_ASSISTANT_NATIVE_POWER = "oost_assistant_native_power"
    private const val SETTINGS_KEY_ASSISTANT_INTERNATIONAL_POWER_CHORD =
        "oost_assistant_international_power_chord"
    private const val SETTINGS_KEY_ASSISTANT_NATIVE_CIRCLE = "oost_assistant_native_circle"
    private const val SETTINGS_KEY_RECENT_TASK_RADIUS_DP = "oost_recent_task_radius_dp"
    private const val SETTINGS_KEY_AOD_INIT_DARK_BRIGHTNESS = "oost_aod_init_dark_brightness"
    private const val SETTINGS_KEY_AOD_INIT_BRIGHT_BRIGHTNESS = "oost_aod_init_bright_brightness"
    private const val SETTINGS_KEY_AOD_RUNNING_BRIGHTNESS_MULTIPLIER = "oost_aod_running_brightness_multiplier"
    private const val SETTINGS_KEY_AOD_PANORAMIC_SUPPORT = "oost_aod_panoramic_support"
    private const val SETTINGS_KEY_AOD_SETTINGS_SWITCH = "oost_aod_settings_switch"
    private const val SETTINGS_KEY_AOD_SINGLE_CLICK_BLOCK = "oost_aod_single_click_block"
    private const val SETTINGS_KEY_NATIVE_NOTIFICATION_BUBBLES = "oost_native_notification_bubbles"
    private const val SETTINGS_KEY_SYSTEMUI_INTERNATIONAL_NETWORK_DISPLAY =
        "oost_systemui_international_network_display"
    private const val SETTINGS_KEY_SYSTEMUI_HIDE_MOBILE_ROAMING_INDICATOR =
        "oost_systemui_hide_mobile_roaming_indicator"
    private const val SETTINGS_KEY_SYSTEMUI_HIDE_NETWORK_ACTIVITY_INDICATOR =
        "oost_systemui_hide_network_activity_indicator"
    private const val SETTINGS_KEY_SYSTEMUI_NATIVE_POWER_MENU =
        "oost_systemui_native_power_menu"
    private const val SETTINGS_KEY_SYSTEMUI_RESTORE_C16_NETWORK_ICON_ORDER =
        "oost_systemui_restore_c16_network_icon_order"
    private const val SETTINGS_KEY_SYSTEMUI_INTERNATIONAL_NOTIFICATION_STYLE =
        "oost_systemui_international_notification_style"
    private const val SETTINGS_KEY_SYSTEMUI_FORCE_TONAL_SPOT = "oost_systemui_force_tonal_spot"
    private const val SETTINGS_KEY_SYSTEMUI_MONET_COLOR_SPEC_MODE =
        "oost_systemui_monet_color_spec_mode"
    private const val SETTINGS_KEY_SYSTEMUI_HIDE_QS_EDIT = "oost_systemui_hide_qs_edit"
    private const val SETTINGS_KEY_SYSTEMUI_HIDE_QS_SETTINGS = "oost_systemui_hide_qs_settings"
    private const val SETTINGS_KEY_SYSTEMUI_HIDE_QS_TOP_CARRIER = "oost_systemui_hide_qs_top_carrier"
    private const val SETTINGS_KEY_SYSTEMUI_HIDE_QS_MORE = "oost_systemui_hide_qs_more"
    private const val SETTINGS_KEY_SYSTEMUI_FORCE_NATIVE_CLIPBOARD_OVERLAY = "oost_systemui_force_native_clipboard_overlay"
    private const val SETTINGS_KEY_SETTINGS_INTERNATIONAL = "oost_settings_international"
    private const val SETTINGS_KEY_SETTINGS_FORCE_APP_AUTO_START = "oost_settings_force_app_auto_start"
    private const val SETTINGS_KEY_SETTINGS_INTERNATIONAL_WALLET = "oost_settings_international_wallet"
    private const val SETTINGS_KEY_SETTINGS_RESTORE_DOMESTIC_ABOUT_DEVICE =
        "oost_settings_restore_domestic_about_device"
    private const val SETTINGS_KEY_SETTINGS_RESTORE_DOMESTIC_AUXILIARY_FUNCTIONS =
        "oost_settings_restore_domestic_auxiliary_functions"
    private const val SETTINGS_KEY_SETTINGS_RESTORE_SMART_LOCK =
        "oost_settings_restore_smart_lock"
    private const val SETTINGS_KEY_SETTINGS_FORCE_GOOGLE_ENTRY = "oost_settings_force_google_entry"
    private const val SETTINGS_KEY_SETTINGS_C15_ABOUT_LAYOUT = "oost_settings_c15_about_layout"
    private const val SETTINGS_KEY_SETTINGS_SKIP_SPECIAL_PERMISSION_RISK_CONFIRM = "oost_settings_skip_special_permission_risk_confirm"
    private const val SETTINGS_KEY_SETTINGS_RESTORE_APP_OPEN_BUTTON = "oost_settings_restore_app_open_button"
    private const val SETTINGS_KEY_WALLPAPERS_RED_ONE_ENTRY = "oost_wallpapers_red_one_entry"
    private const val SETTINGS_KEY_SETTINGS_UNLOCK_REFRESH_RATE = "oost_settings_unlock_refresh_rate"
    private const val SETTINGS_KEY_SETTINGS_FORCE_GLOBAL_EXTREME_REFRESH_RATE =
        "oost_settings_force_global_extreme_refresh_rate"
    private const val SETTINGS_KEY_GMS_REGION_RESTRICTION_BYPASS = "oost_gms_region_restriction_bypass"
    private const val SETTINGS_KEY_ESIM_REGION_RESTRICTION_BYPASS =
        "oost_esim_region_restriction_bypass"
    private const val SETTINGS_KEY_ESIM_CONFIRMATION_CODE_PROMPT =
        "oost_esim_confirmation_code_prompt"
    private const val SETTINGS_KEY_ESIM_REGION_RESTRICTION_OVERRIDE =
        "oost_esim_region_restriction_override"
    private const val SETTINGS_KEY_ESIM_PROFILE_LIMIT_BYPASS =
        "oost_esim_profile_limit_bypass"
    private const val SETTINGS_KEY_MOBILE_NETWORK_HIDE_AI_LINK_BOOST =
        "oost_mobile_network_hide_ai_link_boost"
    private const val SETTINGS_KEY_MOBILE_NETWORK_HIDE_ROAMING_SERVICE =
        "oost_mobile_network_hide_roaming_service"
    private const val SETTINGS_KEY_MOBILE_NETWORK_HIDE_HIGH_DATA_SIM_CARD =
        "oost_mobile_network_hide_high_data_sim_card"
    private const val SETTINGS_KEY_MOBILE_NETWORK_HIDE_SMART_CLOUD_ACCELERATION =
        "oost_mobile_network_hide_smart_cloud_acceleration"
    private const val SETTINGS_KEY_MOBILE_NETWORK_HIDE_PHONE_NUMBER =
        "oost_mobile_network_hide_phone_number"
    private const val SETTINGS_KEY_MOBILE_NETWORK_FORCE_CARRIER_OPTIONS =
        "oost_mobile_network_force_carrier_options"
    private const val SETTINGS_KEY_APP_MARKET_REGION_RESTRICTION_BYPASS =
        "oost_app_market_region_restriction_bypass"
    private const val SETTINGS_KEY_APP_MARKET_REMOVE_SPLASH_RECOMMENDATIONS =
        "oost_app_market_remove_splash_recommendations"
    private const val SETTINGS_KEY_APP_MARKET_REMOVE_UPDATE_DOWNLOAD_RECOMMENDATIONS =
        "oost_app_market_remove_update_download_recommendations"
    private const val SETTINGS_KEY_APP_MARKET_REMOVE_MINE_RECOMMENDATIONS =
        "oost_app_market_remove_mine_recommendations"
    private const val SETTINGS_KEY_APP_MARKET_HIDE_SEARCH_HOME_RECOMMENDATIONS =
        "oost_app_market_hide_search_home_recommendations"
    private const val SETTINGS_KEY_APP_MARKET_HIDE_SEARCH_RESULT_RECOMMENDATIONS =
        "oost_app_market_hide_search_result_recommendations"
    private const val SETTINGS_KEY_APP_MARKET_HIDE_DETAIL_RECOMMENDATIONS =
        "oost_app_market_hide_detail_recommendations"
    private const val SETTINGS_KEY_ATHENA_C17_SWIPE_UP_PROTECTION =
        "oost_athena_c17_vpn_protection"
    private const val SETTINGS_KEY_OK_GOOGLE_HOTWORD_COMPATIBILITY = "oost_ok_google_hotword_compatibility"
    private const val SETTINGS_KEY_LAUNCHER_HIDE_WIDGET_LABELS = "oost_launcher_hide_widget_labels"
    private const val SETTINGS_KEY_LAUNCHER_SEARCH_BAR_MODE = "oost_launcher_taskbar_search_box"
    private const val SETTINGS_KEY_LAUNCHER_SEARCH_COMPATIBILITY = "oost_launcher_search_compatibility"

    private const val DEFAULT_RECENT_TASK_RADIUS_DP = FeatureSliderRules.SYSTEM_DEFAULT
    private const val DEFAULT_AOD_INIT_DARK_BRIGHTNESS = FeatureSliderRules.SYSTEM_DEFAULT
    private const val DEFAULT_AOD_INIT_BRIGHT_BRIGHTNESS = FeatureSliderRules.SYSTEM_DEFAULT
    private const val DEFAULT_AOD_RUNNING_BRIGHTNESS_MULTIPLIER = FeatureSliderRules.SYSTEM_DEFAULT_MULTIPLIER
    private const val DEFAULT_AOD_PANORAMIC_SUPPORT = true
    private const val DEFAULT_AOD_SETTINGS_SWITCH = true
    private const val DEFAULT_AOD_SINGLE_CLICK_BLOCK = true
    private const val DEFAULT_NATIVE_NOTIFICATION_BUBBLES = false
    private const val DEFAULT_SYSTEMUI_INTERNATIONAL_NETWORK_DISPLAY = false
    private const val DEFAULT_SYSTEMUI_HIDE_MOBILE_ROAMING_INDICATOR = false
    private const val DEFAULT_SYSTEMUI_HIDE_NETWORK_ACTIVITY_INDICATOR = false
    private const val DEFAULT_SYSTEMUI_NATIVE_POWER_MENU = false
    private const val DEFAULT_SYSTEMUI_RESTORE_C16_NETWORK_ICON_ORDER = false
    private const val DEFAULT_SYSTEMUI_INTERNATIONAL_NOTIFICATION_STYLE = false
    private const val DEFAULT_SYSTEMUI_FORCE_TONAL_SPOT = false
    const val SYSTEMUI_MONET_COLOR_SPEC_OFF = 0
    const val SYSTEMUI_MONET_COLOR_SPEC_2025 = 1
    const val SYSTEMUI_MONET_COLOR_SPEC_2021 = 2
    private const val DEFAULT_SYSTEMUI_MONET_COLOR_SPEC_MODE = SYSTEMUI_MONET_COLOR_SPEC_OFF
    private const val DEFAULT_SYSTEMUI_HIDE_QS_EDIT = false
    private const val DEFAULT_SYSTEMUI_HIDE_QS_SETTINGS = false
    private const val DEFAULT_SYSTEMUI_HIDE_QS_TOP_CARRIER = false
    private const val DEFAULT_SYSTEMUI_HIDE_QS_MORE = false
    private const val DEFAULT_SYSTEMUI_FORCE_NATIVE_CLIPBOARD_OVERLAY = false
    private const val DEFAULT_SETTINGS_INTERNATIONAL = false
    private const val DEFAULT_SETTINGS_FORCE_APP_AUTO_START = false
    private const val DEFAULT_SETTINGS_INTERNATIONAL_WALLET = false
    private const val DEFAULT_SETTINGS_RESTORE_DOMESTIC_ABOUT_DEVICE = false
    private const val DEFAULT_SETTINGS_RESTORE_DOMESTIC_AUXILIARY_FUNCTIONS = false
    private const val DEFAULT_SETTINGS_RESTORE_SMART_LOCK = false
    private const val DEFAULT_SETTINGS_FORCE_GOOGLE_ENTRY = false
    private const val DEFAULT_SETTINGS_C15_ABOUT_LAYOUT = false
    private const val DEFAULT_SETTINGS_SKIP_SPECIAL_PERMISSION_RISK_CONFIRM = false
    private const val DEFAULT_SETTINGS_RESTORE_APP_OPEN_BUTTON = false
    private const val DEFAULT_WALLPAPERS_RED_ONE_ENTRY = false
    private const val DEFAULT_SETTINGS_UNLOCK_REFRESH_RATE = false
    private const val DEFAULT_SETTINGS_FORCE_GLOBAL_EXTREME_REFRESH_RATE = false
    private const val DEFAULT_GMS_REGION_RESTRICTION_BYPASS = false
    private const val DEFAULT_ESIM_REGION_RESTRICTION_BYPASS = false
    private const val DEFAULT_ESIM_CONFIRMATION_CODE_PROMPT = false
    private const val DEFAULT_ESIM_REGION_RESTRICTION_OVERRIDE = false
    private const val DEFAULT_ESIM_PROFILE_LIMIT_BYPASS = false
    private const val DEFAULT_MOBILE_NETWORK_HIDE_AI_LINK_BOOST = false
    private const val DEFAULT_MOBILE_NETWORK_HIDE_ROAMING_SERVICE = false
    private const val DEFAULT_MOBILE_NETWORK_HIDE_HIGH_DATA_SIM_CARD = false
    private const val DEFAULT_MOBILE_NETWORK_HIDE_SMART_CLOUD_ACCELERATION = false
    private const val DEFAULT_MOBILE_NETWORK_HIDE_PHONE_NUMBER = false
    private const val DEFAULT_MOBILE_NETWORK_FORCE_CARRIER_OPTIONS = false
    private const val DEFAULT_APP_MARKET_REGION_RESTRICTION_BYPASS = false
    private const val DEFAULT_APP_MARKET_REMOVE_SPLASH_RECOMMENDATIONS = false
    private const val DEFAULT_APP_MARKET_REMOVE_UPDATE_DOWNLOAD_RECOMMENDATIONS = false
    private const val DEFAULT_APP_MARKET_REMOVE_MINE_RECOMMENDATIONS = false
    private const val DEFAULT_APP_MARKET_HIDE_SEARCH_HOME_RECOMMENDATIONS = false
    private const val DEFAULT_APP_MARKET_HIDE_SEARCH_RESULT_RECOMMENDATIONS = false
    private const val DEFAULT_APP_MARKET_HIDE_DETAIL_RECOMMENDATIONS = false
    private const val DEFAULT_ATHENA_C17_SWIPE_UP_PROTECTION = false
    private const val DEFAULT_OK_GOOGLE_HOTWORD_COMPATIBILITY = false
    private const val DEFAULT_LAUNCHER_HIDE_WIDGET_LABELS = false
    const val LAUNCHER_SEARCH_BAR_MODE_OFF = 0
    const val LAUNCHER_SEARCH_BAR_MODE_INTERNATIONAL = 1
    const val LAUNCHER_SEARCH_BAR_MODE_CHINA = 2
    private const val DEFAULT_LAUNCHER_SEARCH_BAR_MODE = LAUNCHER_SEARCH_BAR_MODE_OFF
    const val ASSISTANT_POWER_MODE_NONE = -1
    const val ASSISTANT_POWER_MODE_SYSTEM_DEFAULT = 0
    private const val DEFAULT_ASSISTANT_POWER_MODE = ASSISTANT_POWER_MODE_NONE

    data class UiSnapshot(
        val nativeNotifyIconEnabled: Boolean,
        val nativeNotificationBubblesEnabled: Boolean,
        val extremeRefresh165Enabled: Boolean,
        val recentTaskRadiusEnabled: Boolean,
        val recentTaskRadiusDp: Int,
        val aodEnhanceEnabled: Boolean,
        val aodInitDarkBrightness: Int,
        val aodInitBrightBrightness: Int,
        val aodRunningBrightnessMultiplier: Float,
        val aodPanoramicSupportEnabled: Boolean,
        val aodSettingsSwitchEnabled: Boolean,
        val aodSingleClickBlockEnabled: Boolean,
        val systemUiInternationalNetworkDisplayEnabled: Boolean,
        val systemUiHideMobileRoamingIndicatorEnabled: Boolean,
        val systemUiInternationalNotificationStyleEnabled: Boolean,
        val systemUiHideQsEditEnabled: Boolean,
        val systemUiHideQsSettingsEnabled: Boolean,
        val systemUiHideQsTopCarrierEnabled: Boolean,
        val systemUiHideQsMoreEnabled: Boolean,
        val systemUiForceNativeClipboardOverlayEnabled: Boolean,
        val settingsForceGoogleEntryEnabled: Boolean,
        val gmsRegionRestrictionBypassEnabled: Boolean,
        val athenaC17SwipeUpProtectionEnabled: Boolean,
        val okGoogleHotwordCompatibilityEnabled: Boolean,
        val assistantPowerMode: Int,
        val assistantGestureCircleEnabled: Boolean,
        val assistantGestureCircleC17Enabled: Boolean,
        val assistantNativePowerEnabled: Boolean,
        val assistantNativeCircleEnabled: Boolean,
    )

    fun readCachedUiSnapshot(context: Context): UiSnapshot {
        val prefs = prefs(context)
        return UiSnapshot(
            nativeNotifyIconEnabled = prefs.getBoolean(KEY_NATIVE_NOTIFY_ICON, true),
            nativeNotificationBubblesEnabled = prefs.getBoolean(
                KEY_NATIVE_NOTIFICATION_BUBBLES,
                DEFAULT_NATIVE_NOTIFICATION_BUBBLES
            ),
            extremeRefresh165Enabled = prefs.getBoolean(KEY_EXTREME_REFRESH_165, false),
            recentTaskRadiusEnabled = prefs.getBoolean(KEY_RECENT_TASK_RADIUS, false),
            recentTaskRadiusDp = FeatureSliderRules.normalizeRadius(prefs.getInt(
                KEY_RECENT_TASK_RADIUS_DP,
                DEFAULT_RECENT_TASK_RADIUS_DP
            )),
            aodEnhanceEnabled = prefs.getBoolean(KEY_AOD_ENHANCE, false),
            aodInitDarkBrightness = FeatureSliderRules.normalizeBrightness(prefs.getInt(
                KEY_AOD_INIT_DARK_BRIGHTNESS,
                DEFAULT_AOD_INIT_DARK_BRIGHTNESS
            )),
            aodInitBrightBrightness = FeatureSliderRules.normalizeBrightness(prefs.getInt(
                KEY_AOD_INIT_BRIGHT_BRIGHTNESS,
                DEFAULT_AOD_INIT_BRIGHT_BRIGHTNESS
            )),
            aodRunningBrightnessMultiplier = FeatureSliderRules.normalizeMultiplier(prefs.getFloat(
                KEY_AOD_RUNNING_BRIGHTNESS_MULTIPLIER,
                DEFAULT_AOD_RUNNING_BRIGHTNESS_MULTIPLIER
            )),
            aodPanoramicSupportEnabled = prefs.getBoolean(
                KEY_AOD_PANORAMIC_SUPPORT,
                DEFAULT_AOD_PANORAMIC_SUPPORT
            ),
            aodSettingsSwitchEnabled = prefs.getBoolean(
                KEY_AOD_SETTINGS_SWITCH,
                DEFAULT_AOD_SETTINGS_SWITCH
            ),
            aodSingleClickBlockEnabled = prefs.getBoolean(
                KEY_AOD_SINGLE_CLICK_BLOCK,
                DEFAULT_AOD_SINGLE_CLICK_BLOCK
            ),
            systemUiInternationalNetworkDisplayEnabled = prefs.getBoolean(
                KEY_SYSTEMUI_INTERNATIONAL_NETWORK_DISPLAY,
                DEFAULT_SYSTEMUI_INTERNATIONAL_NETWORK_DISPLAY
            ),
            systemUiHideMobileRoamingIndicatorEnabled = prefs.getBoolean(
                KEY_SYSTEMUI_HIDE_MOBILE_ROAMING_INDICATOR,
                DEFAULT_SYSTEMUI_HIDE_MOBILE_ROAMING_INDICATOR
            ),
            systemUiInternationalNotificationStyleEnabled = prefs.getBoolean(
                KEY_SYSTEMUI_INTERNATIONAL_NOTIFICATION_STYLE,
                DEFAULT_SYSTEMUI_INTERNATIONAL_NOTIFICATION_STYLE
            ),
            systemUiHideQsEditEnabled = prefs.getBoolean(
                KEY_SYSTEMUI_HIDE_QS_EDIT,
                DEFAULT_SYSTEMUI_HIDE_QS_EDIT
            ),
            systemUiHideQsSettingsEnabled = prefs.getBoolean(
                KEY_SYSTEMUI_HIDE_QS_SETTINGS,
                DEFAULT_SYSTEMUI_HIDE_QS_SETTINGS
            ),
            systemUiHideQsTopCarrierEnabled = prefs.getBoolean(
                KEY_SYSTEMUI_HIDE_QS_TOP_CARRIER,
                DEFAULT_SYSTEMUI_HIDE_QS_TOP_CARRIER
            ),
            systemUiHideQsMoreEnabled = prefs.getBoolean(
                KEY_SYSTEMUI_HIDE_QS_MORE,
                DEFAULT_SYSTEMUI_HIDE_QS_MORE
            ),
            systemUiForceNativeClipboardOverlayEnabled = prefs.getBoolean(
                KEY_SYSTEMUI_FORCE_NATIVE_CLIPBOARD_OVERLAY,
                DEFAULT_SYSTEMUI_FORCE_NATIVE_CLIPBOARD_OVERLAY
            ),
            settingsForceGoogleEntryEnabled = prefs.getBoolean(
                KEY_SETTINGS_FORCE_GOOGLE_ENTRY,
                DEFAULT_SETTINGS_FORCE_GOOGLE_ENTRY
            ),
            gmsRegionRestrictionBypassEnabled = prefs.getBoolean(
                KEY_GMS_REGION_RESTRICTION_BYPASS,
                DEFAULT_GMS_REGION_RESTRICTION_BYPASS,
            ),
            athenaC17SwipeUpProtectionEnabled = prefs.getBoolean(
                KEY_ATHENA_C17_SWIPE_UP_PROTECTION,
                DEFAULT_ATHENA_C17_SWIPE_UP_PROTECTION,
            ),
            okGoogleHotwordCompatibilityEnabled = prefs.getBoolean(
                KEY_OK_GOOGLE_HOTWORD_COMPATIBILITY,
                DEFAULT_OK_GOOGLE_HOTWORD_COMPATIBILITY,
            ),
            assistantPowerMode = prefs.getInt(
                KEY_ASSISTANT_POWER_MODE,
                DEFAULT_ASSISTANT_POWER_MODE
            ).sanitizeAssistantPowerMode(),
            assistantGestureCircleEnabled = prefs.getBoolean(
                KEY_ASSISTANT_GESTURE_CIRCLE,
                false
            ),
            assistantGestureCircleC17Enabled = prefs.getBoolean(
                KEY_ASSISTANT_GESTURE_CIRCLE_C17,
                false
            ),
            assistantNativePowerEnabled = prefs.getBoolean(
                KEY_ASSISTANT_NATIVE_POWER,
                false
            ),
            assistantNativeCircleEnabled = prefs.getBoolean(
                KEY_ASSISTANT_NATIVE_CIRCLE,
                false
            ),
        )
    }

    fun readSyncedUiSnapshot(context: Context): UiSnapshot {
        return UiSnapshot(
            nativeNotifyIconEnabled = isNativeNotifyIconEnabled(context),
            nativeNotificationBubblesEnabled = isNativeNotificationBubblesEnabled(context),
            extremeRefresh165Enabled = isExtremeRefresh165Enabled(context),
            recentTaskRadiusEnabled = isRecentTaskRadiusEnabled(context),
            recentTaskRadiusDp = getRecentTaskRadiusDp(context),
            aodEnhanceEnabled = isAodEnhanceEnabled(context),
            aodInitDarkBrightness = getAodInitDarkBrightness(context),
            aodInitBrightBrightness = getAodInitBrightBrightness(context),
            aodRunningBrightnessMultiplier = getAodRunningBrightnessMultiplier(context),
            aodPanoramicSupportEnabled = isAodPanoramicSupportEnabled(context),
            aodSettingsSwitchEnabled = isAodSettingsSwitchEnabled(context),
            aodSingleClickBlockEnabled = isAodSingleClickBlockEnabled(context),
            systemUiInternationalNetworkDisplayEnabled =
                isSystemUiInternationalNetworkDisplayEnabled(context),
            systemUiHideMobileRoamingIndicatorEnabled =
                isSystemUiHideMobileRoamingIndicatorEnabled(context),
            systemUiInternationalNotificationStyleEnabled =
                isSystemUiInternationalNotificationStyleEnabled(context),
            systemUiHideQsEditEnabled = isSystemUiHideQsEditEnabled(context),
            systemUiHideQsSettingsEnabled = isSystemUiHideQsSettingsEnabled(context),
            systemUiHideQsTopCarrierEnabled = isSystemUiHideQsTopCarrierEnabled(context),
            systemUiHideQsMoreEnabled = isSystemUiHideQsMoreEnabled(context),
            systemUiForceNativeClipboardOverlayEnabled = isSystemUiForceNativeClipboardOverlayEnabled(context),
            settingsForceGoogleEntryEnabled = isSettingsForceGoogleEntryEnabled(context),
            gmsRegionRestrictionBypassEnabled = isGmsRegionRestrictionBypassEnabled(context),
            athenaC17SwipeUpProtectionEnabled = isAthenaC17SwipeUpProtectionEnabled(context),
            okGoogleHotwordCompatibilityEnabled = isOkGoogleHotwordCompatibilityEnabled(context),
            assistantPowerMode = getAssistantPowerMode(context),
            assistantGestureCircleEnabled = isAssistantGestureCircleEnabled(context),
            assistantGestureCircleC17Enabled = isAssistantGestureCircleC17Enabled(context),
            assistantNativePowerEnabled = isAssistantNativePowerEnabled(context),
            assistantNativeCircleEnabled = isAssistantNativeCircleEnabled(context),
        )
    }

    fun isNativeNotifyIconEnabled(context: Context): Boolean {
        return LspPreferenceStore.readBoolean(context, KEY_NATIVE_NOTIFY_ICON, true)
    }

    fun setNativeNotifyIconEnabled(context: Context, enabled: Boolean) {
        if (!prefs(context).edit().putBoolean(KEY_NATIVE_NOTIFY_ICON, enabled).commitOrReport()) return
        syncReadableState(context)
        syncFlagState(
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_NATIVE_NOTIFY_ICON,
                PROP_KEY_NATIVE_NOTIFY_ICON
            ),
            settingsGlobalKey = SETTINGS_KEY_NATIVE_NOTIFY_ICON,
            flagFilePath = FLAG_FILE_PATH_NATIVE_NOTIFY_ICON
        )
    }

    fun isExtremeRefresh165Enabled(context: Context): Boolean {
        return LspPreferenceStore.readBoolean(context, KEY_EXTREME_REFRESH_165, false)
    }

    fun setExtremeRefresh165Enabled(context: Context, enabled: Boolean) {
        if (!prefs(context).edit().putBoolean(KEY_EXTREME_REFRESH_165, enabled).commitOrReport()) return
        syncReadableState(context)
        syncFlagState(
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_EXTREME_REFRESH_165,
                PROP_KEY_EXTREME_REFRESH_165
            ),
            settingsGlobalKey = SETTINGS_KEY_EXTREME_REFRESH_165,
            flagFilePath = FLAG_FILE_PATH_EXTREME_REFRESH_165
        )
    }

    fun isRecentTaskRadiusEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_RECENT_TASK_RADIUS, false)
    }

    fun setRecentTaskRadiusEnabled(context: Context, enabled: Boolean) {
        if (!prefs(context).edit().putBoolean(KEY_RECENT_TASK_RADIUS, enabled).commitOrReport()) return
        syncReadableState(context)
        syncFlagState(
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_RECENT_TASK_RADIUS,
                PROP_KEY_RECENT_TASK_RADIUS
            ),
            settingsGlobalKey = SETTINGS_KEY_RECENT_TASK_RADIUS,
            flagFilePath = FLAG_FILE_PATH_RECENT_TASK_RADIUS
        )
    }

    fun isAodEnhanceEnabled(context: Context): Boolean {
        return LspPreferenceStore.readBoolean(context, KEY_AOD_ENHANCE, false)
    }

    fun setAodEnhanceEnabled(context: Context, enabled: Boolean) {
        if (!prefs(context).edit().putBoolean(KEY_AOD_ENHANCE, enabled).commitOrReport()) return
        syncReadableState(context)
        syncFlagState(
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_AOD_ENHANCE,
                PROP_KEY_AOD_ENHANCE
            ),
            settingsGlobalKey = SETTINGS_KEY_AOD_ENHANCE,
            flagFilePath = FLAG_FILE_PATH_AOD_ENHANCE
        )
    }

    fun getAssistantPowerMode(context: Context): Int {
        return LspPreferenceStore.readInt(context, KEY_ASSISTANT_POWER_MODE, DEFAULT_ASSISTANT_POWER_MODE).sanitizeAssistantPowerMode()
    }

    fun setAssistantPowerMode(context: Context, mode: Int) {
        val normalized = mode.sanitizeAssistantPowerMode()
        if (!prefs(context).edit().putInt(KEY_ASSISTANT_POWER_MODE, normalized).commitOrReport()) return
        syncReadableState(context)
        syncScalarState(
            value = normalized.toString(),
            propertyKeys = listOf(
                PERSIST_PROP_KEY_ASSISTANT_POWER_MODE,
                PROP_KEY_ASSISTANT_POWER_MODE
            ),
            settingsGlobalKey = SETTINGS_KEY_ASSISTANT_POWER_MODE
        )
    }

    fun isAssistantGestureCircleEnabled(context: Context): Boolean {
        return LspPreferenceStore.readBoolean(context, KEY_ASSISTANT_GESTURE_CIRCLE, false)
    }

    fun setAssistantGestureCircleEnabled(context: Context, enabled: Boolean) {
        if (!prefs(context).edit().putBoolean(KEY_ASSISTANT_GESTURE_CIRCLE, enabled).commitOrReport()) return
        syncReadableState(context)
        syncScalarState(
            value = if (enabled) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_ASSISTANT_GESTURE_CIRCLE,
                PROP_KEY_ASSISTANT_GESTURE_CIRCLE
            ),
            settingsGlobalKey = SETTINGS_KEY_ASSISTANT_GESTURE_CIRCLE
        )
    }

    fun isAssistantGestureCircleC17Enabled(context: Context): Boolean {
        return LspPreferenceStore.readBoolean(context, KEY_ASSISTANT_GESTURE_CIRCLE_C17, false)
    }

    fun setAssistantGestureCircleC17Enabled(context: Context, enabled: Boolean) {
        if (!prefs(context).edit().putBoolean(KEY_ASSISTANT_GESTURE_CIRCLE_C17, enabled).commitOrReport()) return
        syncReadableState(context)
        syncScalarState(
            value = if (enabled) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_ASSISTANT_GESTURE_CIRCLE_C17,
                PROP_KEY_ASSISTANT_GESTURE_CIRCLE_C17
            ),
            settingsGlobalKey = SETTINGS_KEY_ASSISTANT_GESTURE_CIRCLE_C17
        )
    }

    fun isAssistantNativePowerEnabled(context: Context): Boolean {
        return LspPreferenceStore.readBoolean(context, KEY_ASSISTANT_NATIVE_POWER, false)
    }

    fun setAssistantNativePowerEnabled(context: Context, enabled: Boolean) {
        if (!prefs(context).edit().putBoolean(KEY_ASSISTANT_NATIVE_POWER, enabled).commitOrReport()) return
        syncReadableState(context)
        syncScalarState(
            value = if (enabled) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_ASSISTANT_NATIVE_POWER,
                PROP_KEY_ASSISTANT_NATIVE_POWER,
            ),
            settingsGlobalKey = SETTINGS_KEY_ASSISTANT_NATIVE_POWER,
        )
    }

    fun isAssistantInternationalPowerChordEnabled(context: Context): Boolean {
        return LspPreferenceStore.readBoolean(context, KEY_ASSISTANT_INTERNATIONAL_POWER_CHORD, true)
    }

    fun setAssistantInternationalPowerChordEnabled(context: Context, enabled: Boolean) {
        if (!prefs(context).edit()
            .putBoolean(KEY_ASSISTANT_INTERNATIONAL_POWER_CHORD, enabled)
            .commitOrReport()) return
        syncReadableState(context)
        syncScalarState(
            value = if (enabled) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_ASSISTANT_INTERNATIONAL_POWER_CHORD,
                PROP_KEY_ASSISTANT_INTERNATIONAL_POWER_CHORD,
            ),
            settingsGlobalKey = SETTINGS_KEY_ASSISTANT_INTERNATIONAL_POWER_CHORD,
        )
    }

    fun isAssistantNativeCircleEnabled(context: Context): Boolean {
        return LspPreferenceStore.readBoolean(context, KEY_ASSISTANT_NATIVE_CIRCLE, false)
    }

    fun setAssistantNativeCircleEnabled(context: Context, enabled: Boolean) {
        if (!prefs(context).edit().putBoolean(KEY_ASSISTANT_NATIVE_CIRCLE, enabled).commitOrReport()) return
        syncReadableState(context)
        syncScalarState(
            value = if (enabled) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_ASSISTANT_NATIVE_CIRCLE,
                PROP_KEY_ASSISTANT_NATIVE_CIRCLE,
            ),
            settingsGlobalKey = SETTINGS_KEY_ASSISTANT_NATIVE_CIRCLE,
        )
    }

    fun getRecentTaskRadiusDp(context: Context): Int {
        return FeatureSliderRules.normalizeRadius(
            prefs(context).getInt(KEY_RECENT_TASK_RADIUS_DP, DEFAULT_RECENT_TASK_RADIUS_DP),
        )
    }

    fun setRecentTaskRadiusDp(context: Context, value: Int) {
        val normalized = FeatureSliderRules.normalizeRadius(value)
        if (!prefs(context).edit().putInt(KEY_RECENT_TASK_RADIUS_DP, normalized).commitOrReport()) return
        syncReadableState(context)
        syncScalarState(
            value = normalized.toString(),
            propertyKeys = listOf(
                PERSIST_PROP_KEY_RECENT_TASK_RADIUS_DP,
                PROP_KEY_RECENT_TASK_RADIUS_DP
            ),
            settingsGlobalKey = SETTINGS_KEY_RECENT_TASK_RADIUS_DP
        )
    }

    fun getAodInitDarkBrightness(context: Context): Int {
        return FeatureSliderRules.normalizeBrightness(
            prefs(context).getInt(KEY_AOD_INIT_DARK_BRIGHTNESS, DEFAULT_AOD_INIT_DARK_BRIGHTNESS),
        )
    }

    fun setAodInitDarkBrightness(context: Context, value: Int) {
        val normalized = FeatureSliderRules.normalizeBrightness(value)
        if (!prefs(context).edit().putInt(KEY_AOD_INIT_DARK_BRIGHTNESS, normalized).commitOrReport()) return
        syncReadableState(context)
        syncScalarState(
            value = normalized.toString(),
            propertyKeys = listOf(
                PERSIST_PROP_KEY_AOD_INIT_DARK_BRIGHTNESS,
                PROP_KEY_AOD_INIT_DARK_BRIGHTNESS
            ),
            settingsGlobalKey = SETTINGS_KEY_AOD_INIT_DARK_BRIGHTNESS
        )
    }

    fun getAodInitBrightBrightness(context: Context): Int {
        return FeatureSliderRules.normalizeBrightness(
            prefs(context).getInt(KEY_AOD_INIT_BRIGHT_BRIGHTNESS, DEFAULT_AOD_INIT_BRIGHT_BRIGHTNESS),
        )
    }

    fun setAodInitBrightBrightness(context: Context, value: Int) {
        val normalized = FeatureSliderRules.normalizeBrightness(value)
        if (!prefs(context).edit().putInt(KEY_AOD_INIT_BRIGHT_BRIGHTNESS, normalized).commitOrReport()) return
        syncReadableState(context)
        syncScalarState(
            value = normalized.toString(),
            propertyKeys = listOf(
                PERSIST_PROP_KEY_AOD_INIT_BRIGHT_BRIGHTNESS,
                PROP_KEY_AOD_INIT_BRIGHT_BRIGHTNESS
            ),
            settingsGlobalKey = SETTINGS_KEY_AOD_INIT_BRIGHT_BRIGHTNESS
        )
    }

    fun getAodRunningBrightnessMultiplier(context: Context): Float {
        return FeatureSliderRules.normalizeMultiplier(
            prefs(context).getFloat(
                KEY_AOD_RUNNING_BRIGHTNESS_MULTIPLIER,
                DEFAULT_AOD_RUNNING_BRIGHTNESS_MULTIPLIER
            ),
        )
    }

    fun setAodRunningBrightnessMultiplier(context: Context, value: Float) {
        val normalized = FeatureSliderRules.normalizeMultiplier(value)
        if (!prefs(context).edit().putFloat(KEY_AOD_RUNNING_BRIGHTNESS_MULTIPLIER, normalized).commitOrReport()) return
        syncReadableState(context)
        syncScalarState(
            value = normalized.toString(),
            propertyKeys = listOf(
                PERSIST_PROP_KEY_AOD_RUNNING_BRIGHTNESS_MULTIPLIER,
                PROP_KEY_AOD_RUNNING_BRIGHTNESS_MULTIPLIER
            ),
            settingsGlobalKey = SETTINGS_KEY_AOD_RUNNING_BRIGHTNESS_MULTIPLIER
        )
    }

    fun isAodPanoramicSupportEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_AOD_PANORAMIC_SUPPORT, DEFAULT_AOD_PANORAMIC_SUPPORT)
    }

    fun setAodPanoramicSupportEnabled(context: Context, enabled: Boolean) {
        if (!prefs(context).edit().putBoolean(KEY_AOD_PANORAMIC_SUPPORT, enabled).commitOrReport()) return
        syncReadableState(context)
        syncScalarState(
            value = if (enabled) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_AOD_PANORAMIC_SUPPORT,
                PROP_KEY_AOD_PANORAMIC_SUPPORT
            ),
            settingsGlobalKey = SETTINGS_KEY_AOD_PANORAMIC_SUPPORT
        )
    }

    fun isAodSettingsSwitchEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_AOD_SETTINGS_SWITCH, DEFAULT_AOD_SETTINGS_SWITCH)
    }

    fun setAodSettingsSwitchEnabled(context: Context, enabled: Boolean) {
        if (!prefs(context).edit().putBoolean(KEY_AOD_SETTINGS_SWITCH, enabled).commitOrReport()) return
        syncReadableState(context)
        syncScalarState(
            value = if (enabled) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_AOD_SETTINGS_SWITCH,
                PROP_KEY_AOD_SETTINGS_SWITCH
            ),
            settingsGlobalKey = SETTINGS_KEY_AOD_SETTINGS_SWITCH
        )
    }

    fun isAodSingleClickBlockEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_AOD_SINGLE_CLICK_BLOCK, DEFAULT_AOD_SINGLE_CLICK_BLOCK)
    }

    fun setAodSingleClickBlockEnabled(context: Context, enabled: Boolean) {
        if (!prefs(context).edit().putBoolean(KEY_AOD_SINGLE_CLICK_BLOCK, enabled).commitOrReport()) return
        syncReadableState(context)
        syncScalarState(
            value = if (enabled) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_AOD_SINGLE_CLICK_BLOCK,
                PROP_KEY_AOD_SINGLE_CLICK_BLOCK
            ),
            settingsGlobalKey = SETTINGS_KEY_AOD_SINGLE_CLICK_BLOCK
        )
    }

    fun isNativeNotificationBubblesEnabled(context: Context): Boolean {
        return LspPreferenceStore.readBoolean(context, KEY_NATIVE_NOTIFICATION_BUBBLES, DEFAULT_NATIVE_NOTIFICATION_BUBBLES)
    }

    fun setNativeNotificationBubblesEnabled(context: Context, enabled: Boolean) {
        if (!prefs(context).edit().putBoolean(KEY_NATIVE_NOTIFICATION_BUBBLES, enabled).commitOrReport()) return
        syncReadableState(context)
        syncFlagState(
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_NATIVE_NOTIFICATION_BUBBLES,
                PROP_KEY_NATIVE_NOTIFICATION_BUBBLES
            ),
            settingsGlobalKey = SETTINGS_KEY_NATIVE_NOTIFICATION_BUBBLES,
            flagFilePath = FLAG_FILE_PATH_NATIVE_NOTIFICATION_BUBBLES
        )
    }

    fun isSystemUiInternationalNetworkDisplayEnabled(context: Context): Boolean {
        return LspPreferenceStore.readBoolean(context, KEY_SYSTEMUI_INTERNATIONAL_NETWORK_DISPLAY, DEFAULT_SYSTEMUI_INTERNATIONAL_NETWORK_DISPLAY)
    }

    fun setSystemUiInternationalNetworkDisplayEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_SYSTEMUI_INTERNATIONAL_NETWORK_DISPLAY,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SYSTEMUI_INTERNATIONAL_NETWORK_DISPLAY,
                PROP_KEY_SYSTEMUI_INTERNATIONAL_NETWORK_DISPLAY
            ),
            settingsGlobalKey = SETTINGS_KEY_SYSTEMUI_INTERNATIONAL_NETWORK_DISPLAY
        )
    }

    fun isSystemUiHideMobileRoamingIndicatorEnabled(context: Context): Boolean {
        return LspPreferenceStore.readBoolean(context, KEY_SYSTEMUI_HIDE_MOBILE_ROAMING_INDICATOR, DEFAULT_SYSTEMUI_HIDE_MOBILE_ROAMING_INDICATOR)
    }

    fun setSystemUiHideMobileRoamingIndicatorEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_SYSTEMUI_HIDE_MOBILE_ROAMING_INDICATOR,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SYSTEMUI_HIDE_MOBILE_ROAMING_INDICATOR,
                PROP_KEY_SYSTEMUI_HIDE_MOBILE_ROAMING_INDICATOR
            ),
            settingsGlobalKey = SETTINGS_KEY_SYSTEMUI_HIDE_MOBILE_ROAMING_INDICATOR
        )
    }

    fun isSystemUiHideNetworkActivityIndicatorEnabled(context: Context): Boolean {
        return LspPreferenceStore.readBoolean(context, KEY_SYSTEMUI_HIDE_NETWORK_ACTIVITY_INDICATOR, DEFAULT_SYSTEMUI_HIDE_NETWORK_ACTIVITY_INDICATOR)
    }

    fun setSystemUiHideNetworkActivityIndicatorEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_SYSTEMUI_HIDE_NETWORK_ACTIVITY_INDICATOR,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SYSTEMUI_HIDE_NETWORK_ACTIVITY_INDICATOR,
                PROP_KEY_SYSTEMUI_HIDE_NETWORK_ACTIVITY_INDICATOR,
            ),
            settingsGlobalKey = SETTINGS_KEY_SYSTEMUI_HIDE_NETWORK_ACTIVITY_INDICATOR,
        )
    }

    fun isSystemUiNativePowerMenuEnabled(context: Context): Boolean {
        return LspPreferenceStore.readBoolean(context, KEY_SYSTEMUI_NATIVE_POWER_MENU, DEFAULT_SYSTEMUI_NATIVE_POWER_MENU)
    }

    fun setSystemUiNativePowerMenuEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_SYSTEMUI_NATIVE_POWER_MENU,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SYSTEMUI_NATIVE_POWER_MENU,
                PROP_KEY_SYSTEMUI_NATIVE_POWER_MENU,
            ),
            settingsGlobalKey = SETTINGS_KEY_SYSTEMUI_NATIVE_POWER_MENU,
        )
    }

    fun isSystemUiRestoreC16NetworkIconOrderEnabled(context: Context): Boolean {
        return LspPreferenceStore.readBoolean(context, KEY_SYSTEMUI_RESTORE_C16_NETWORK_ICON_ORDER, DEFAULT_SYSTEMUI_RESTORE_C16_NETWORK_ICON_ORDER)
    }

    fun setSystemUiRestoreC16NetworkIconOrderEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_SYSTEMUI_RESTORE_C16_NETWORK_ICON_ORDER,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SYSTEMUI_RESTORE_C16_NETWORK_ICON_ORDER,
                PROP_KEY_SYSTEMUI_RESTORE_C16_NETWORK_ICON_ORDER,
            ),
            settingsGlobalKey = SETTINGS_KEY_SYSTEMUI_RESTORE_C16_NETWORK_ICON_ORDER,
        )
    }

    fun isSystemUiInternationalNotificationStyleEnabled(context: Context): Boolean {
        return LspPreferenceStore.readBoolean(context, KEY_SYSTEMUI_INTERNATIONAL_NOTIFICATION_STYLE, DEFAULT_SYSTEMUI_INTERNATIONAL_NOTIFICATION_STYLE)
    }

    fun setSystemUiInternationalNotificationStyleEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_SYSTEMUI_INTERNATIONAL_NOTIFICATION_STYLE,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SYSTEMUI_INTERNATIONAL_NOTIFICATION_STYLE,
                PROP_KEY_SYSTEMUI_INTERNATIONAL_NOTIFICATION_STYLE
            ),
            settingsGlobalKey = SETTINGS_KEY_SYSTEMUI_INTERNATIONAL_NOTIFICATION_STYLE
        )
    }

    fun isSystemUiForceTonalSpotEnabled(context: Context): Boolean {
        return LspPreferenceStore.readBoolean(context, KEY_SYSTEMUI_FORCE_TONAL_SPOT, DEFAULT_SYSTEMUI_FORCE_TONAL_SPOT)
    }

    fun setSystemUiForceTonalSpotEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_SYSTEMUI_FORCE_TONAL_SPOT,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SYSTEMUI_FORCE_TONAL_SPOT,
                PROP_KEY_SYSTEMUI_FORCE_TONAL_SPOT,
            ),
            settingsGlobalKey = SETTINGS_KEY_SYSTEMUI_FORCE_TONAL_SPOT,
        )
    }

    fun getSystemUiMonetColorSpecMode(context: Context): Int {
        return LspPreferenceStore.readInt(context, KEY_SYSTEMUI_MONET_COLOR_SPEC_MODE, DEFAULT_SYSTEMUI_MONET_COLOR_SPEC_MODE).sanitizeSystemUiMonetColorSpecMode()
    }

    fun setSystemUiMonetColorSpecMode(context: Context, mode: Int) {
        val normalized = mode.sanitizeSystemUiMonetColorSpecMode()
        if (!prefs(context).edit().putInt(KEY_SYSTEMUI_MONET_COLOR_SPEC_MODE, normalized).commitOrReport()) return
        syncReadableState(context)
        syncScalarState(
            value = normalized.toString(),
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SYSTEMUI_MONET_COLOR_SPEC_MODE,
                PROP_KEY_SYSTEMUI_MONET_COLOR_SPEC_MODE,
            ),
            settingsGlobalKey = SETTINGS_KEY_SYSTEMUI_MONET_COLOR_SPEC_MODE,
        )
    }

    fun isSystemUiHideQsEditEnabled(context: Context): Boolean {
        return LspPreferenceStore.readBoolean(context, KEY_SYSTEMUI_HIDE_QS_EDIT, DEFAULT_SYSTEMUI_HIDE_QS_EDIT)
    }

    fun setSystemUiHideQsEditEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_SYSTEMUI_HIDE_QS_EDIT,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SYSTEMUI_HIDE_QS_EDIT,
                PROP_KEY_SYSTEMUI_HIDE_QS_EDIT
            ),
            settingsGlobalKey = SETTINGS_KEY_SYSTEMUI_HIDE_QS_EDIT
        )
    }

    fun isSystemUiHideQsSettingsEnabled(context: Context): Boolean {
        return LspPreferenceStore.readBoolean(context, KEY_SYSTEMUI_HIDE_QS_SETTINGS, DEFAULT_SYSTEMUI_HIDE_QS_SETTINGS)
    }

    fun setSystemUiHideQsSettingsEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_SYSTEMUI_HIDE_QS_SETTINGS,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SYSTEMUI_HIDE_QS_SETTINGS,
                PROP_KEY_SYSTEMUI_HIDE_QS_SETTINGS
            ),
            settingsGlobalKey = SETTINGS_KEY_SYSTEMUI_HIDE_QS_SETTINGS
        )
    }

    fun isSystemUiHideQsTopCarrierEnabled(context: Context): Boolean {
        return LspPreferenceStore.readBoolean(context, KEY_SYSTEMUI_HIDE_QS_TOP_CARRIER, DEFAULT_SYSTEMUI_HIDE_QS_TOP_CARRIER)
    }

    fun setSystemUiHideQsTopCarrierEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_SYSTEMUI_HIDE_QS_TOP_CARRIER,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SYSTEMUI_HIDE_QS_TOP_CARRIER,
                PROP_KEY_SYSTEMUI_HIDE_QS_TOP_CARRIER
            ),
            settingsGlobalKey = SETTINGS_KEY_SYSTEMUI_HIDE_QS_TOP_CARRIER
        )
    }

    fun isSystemUiHideQsMoreEnabled(context: Context): Boolean {
        return LspPreferenceStore.readBoolean(context, KEY_SYSTEMUI_HIDE_QS_MORE, DEFAULT_SYSTEMUI_HIDE_QS_MORE)
    }

    fun setSystemUiHideQsMoreEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_SYSTEMUI_HIDE_QS_MORE,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SYSTEMUI_HIDE_QS_MORE,
                PROP_KEY_SYSTEMUI_HIDE_QS_MORE
            ),
            settingsGlobalKey = SETTINGS_KEY_SYSTEMUI_HIDE_QS_MORE
        )
    }

    fun isSystemUiForceNativeClipboardOverlayEnabled(context: Context): Boolean {
        return LspPreferenceStore.readBoolean(context, KEY_SYSTEMUI_FORCE_NATIVE_CLIPBOARD_OVERLAY, DEFAULT_SYSTEMUI_FORCE_NATIVE_CLIPBOARD_OVERLAY)
    }

    fun setSystemUiForceNativeClipboardOverlayEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_SYSTEMUI_FORCE_NATIVE_CLIPBOARD_OVERLAY,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SYSTEMUI_FORCE_NATIVE_CLIPBOARD_OVERLAY,
                PROP_KEY_SYSTEMUI_FORCE_NATIVE_CLIPBOARD_OVERLAY
            ),
            settingsGlobalKey = SETTINGS_KEY_SYSTEMUI_FORCE_NATIVE_CLIPBOARD_OVERLAY
        )
    }

    fun isSettingsInternationalEnabled(context: Context): Boolean {
        return LspPreferenceStore.readBoolean(context, KEY_SETTINGS_INTERNATIONAL, DEFAULT_SETTINGS_INTERNATIONAL)
    }

    fun setSettingsInternationalEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_SETTINGS_INTERNATIONAL,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SETTINGS_INTERNATIONAL,
                PROP_KEY_SETTINGS_INTERNATIONAL
            ),
            settingsGlobalKey = SETTINGS_KEY_SETTINGS_INTERNATIONAL
        )
    }

    fun isSettingsForceAppAutoStartEnabled(context: Context): Boolean {
        return LspPreferenceStore.readBoolean(context, KEY_SETTINGS_FORCE_APP_AUTO_START, DEFAULT_SETTINGS_FORCE_APP_AUTO_START)
    }

    fun setSettingsForceAppAutoStartEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_SETTINGS_FORCE_APP_AUTO_START,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SETTINGS_FORCE_APP_AUTO_START,
                PROP_KEY_SETTINGS_FORCE_APP_AUTO_START
            ),
            settingsGlobalKey = SETTINGS_KEY_SETTINGS_FORCE_APP_AUTO_START
        )
    }

    fun isSettingsInternationalWalletEnabled(context: Context): Boolean {
        return LspPreferenceStore.readBoolean(context, KEY_SETTINGS_INTERNATIONAL_WALLET, DEFAULT_SETTINGS_INTERNATIONAL_WALLET)
    }

    fun setSettingsInternationalWalletEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_SETTINGS_INTERNATIONAL_WALLET,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SETTINGS_INTERNATIONAL_WALLET,
                PROP_KEY_SETTINGS_INTERNATIONAL_WALLET
            ),
            settingsGlobalKey = SETTINGS_KEY_SETTINGS_INTERNATIONAL_WALLET
        )
    }

    fun isSettingsRestoreDomesticAboutDeviceEnabled(context: Context): Boolean {
        return LspPreferenceStore.readBoolean(context, KEY_SETTINGS_RESTORE_DOMESTIC_ABOUT_DEVICE, DEFAULT_SETTINGS_RESTORE_DOMESTIC_ABOUT_DEVICE)
    }

    fun isSettingsRestoreDomesticAuxiliaryFunctionsEnabled(context: Context): Boolean {
        return LspPreferenceStore.readBoolean(context, KEY_SETTINGS_RESTORE_DOMESTIC_AUXILIARY_FUNCTIONS, DEFAULT_SETTINGS_RESTORE_DOMESTIC_AUXILIARY_FUNCTIONS)
    }

    fun setSettingsRestoreDomesticAboutDeviceEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_SETTINGS_RESTORE_DOMESTIC_ABOUT_DEVICE,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SETTINGS_RESTORE_DOMESTIC_ABOUT_DEVICE,
                PROP_KEY_SETTINGS_RESTORE_DOMESTIC_ABOUT_DEVICE,
            ),
            settingsGlobalKey = SETTINGS_KEY_SETTINGS_RESTORE_DOMESTIC_ABOUT_DEVICE,
        )
    }

    fun setSettingsRestoreDomesticAuxiliaryFunctionsEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_SETTINGS_RESTORE_DOMESTIC_AUXILIARY_FUNCTIONS,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SETTINGS_RESTORE_DOMESTIC_AUXILIARY_FUNCTIONS,
                PROP_KEY_SETTINGS_RESTORE_DOMESTIC_AUXILIARY_FUNCTIONS,
            ),
            settingsGlobalKey = SETTINGS_KEY_SETTINGS_RESTORE_DOMESTIC_AUXILIARY_FUNCTIONS,
        )
    }

    fun isSettingsSkipSpecialPermissionRiskConfirmEnabled(context: Context): Boolean = LspPreferenceStore.readBoolean(context, KEY_SETTINGS_SKIP_SPECIAL_PERMISSION_RISK_CONFIRM, DEFAULT_SETTINGS_SKIP_SPECIAL_PERMISSION_RISK_CONFIRM)

    fun setSettingsSkipSpecialPermissionRiskConfirmEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_SETTINGS_SKIP_SPECIAL_PERMISSION_RISK_CONFIRM,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SETTINGS_SKIP_SPECIAL_PERMISSION_RISK_CONFIRM,
                PROP_KEY_SETTINGS_SKIP_SPECIAL_PERMISSION_RISK_CONFIRM,
            ),
            settingsGlobalKey = SETTINGS_KEY_SETTINGS_SKIP_SPECIAL_PERMISSION_RISK_CONFIRM,
        )
    }

    fun isSettingsRestoreAppOpenButtonEnabled(context: Context): Boolean = LspPreferenceStore.readBoolean(context, KEY_SETTINGS_RESTORE_APP_OPEN_BUTTON, DEFAULT_SETTINGS_RESTORE_APP_OPEN_BUTTON)

    fun setSettingsRestoreAppOpenButtonEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_SETTINGS_RESTORE_APP_OPEN_BUTTON,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SETTINGS_RESTORE_APP_OPEN_BUTTON,
                PROP_KEY_SETTINGS_RESTORE_APP_OPEN_BUTTON,
            ),
            settingsGlobalKey = SETTINGS_KEY_SETTINGS_RESTORE_APP_OPEN_BUTTON,
        )
    }

    fun isSettingsC15AboutLayoutEnabled(context: Context): Boolean {
        return LspPreferenceStore.readBoolean(context, KEY_SETTINGS_C15_ABOUT_LAYOUT, DEFAULT_SETTINGS_C15_ABOUT_LAYOUT)
    }

    fun setSettingsC15AboutLayoutEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_SETTINGS_C15_ABOUT_LAYOUT,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SETTINGS_C15_ABOUT_LAYOUT,
                PROP_KEY_SETTINGS_C15_ABOUT_LAYOUT,
            ),
            settingsGlobalKey = SETTINGS_KEY_SETTINGS_C15_ABOUT_LAYOUT,
        )
    }

    fun isWallpapersRedOneEntryEnabled(context: Context): Boolean = LspPreferenceStore.readBoolean(context, KEY_WALLPAPERS_RED_ONE_ENTRY, DEFAULT_WALLPAPERS_RED_ONE_ENTRY)

    fun setWallpapersRedOneEntryEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_WALLPAPERS_RED_ONE_ENTRY,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_WALLPAPERS_RED_ONE_ENTRY,
                PROP_KEY_WALLPAPERS_RED_ONE_ENTRY,
            ),
            settingsGlobalKey = SETTINGS_KEY_WALLPAPERS_RED_ONE_ENTRY,
        )
    }

    fun isSettingsRefreshRateUnlocked(context: Context): Boolean {
        return LspPreferenceStore.readBoolean(context, KEY_SETTINGS_UNLOCK_REFRESH_RATE, DEFAULT_SETTINGS_UNLOCK_REFRESH_RATE)
    }

    fun setSettingsRefreshRateUnlocked(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_SETTINGS_UNLOCK_REFRESH_RATE,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SETTINGS_UNLOCK_REFRESH_RATE,
                PROP_KEY_SETTINGS_UNLOCK_REFRESH_RATE,
            ),
            settingsGlobalKey = SETTINGS_KEY_SETTINGS_UNLOCK_REFRESH_RATE,
        )
    }

    fun isSettingsForceGlobalExtremeRefreshRateEnabled(context: Context): Boolean {
        return LspPreferenceStore.readBoolean(context, KEY_SETTINGS_FORCE_GLOBAL_EXTREME_REFRESH_RATE, DEFAULT_SETTINGS_FORCE_GLOBAL_EXTREME_REFRESH_RATE)
    }

    fun setSettingsForceGlobalExtremeRefreshRateEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_SETTINGS_FORCE_GLOBAL_EXTREME_REFRESH_RATE,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SETTINGS_FORCE_GLOBAL_EXTREME_REFRESH_RATE,
                PROP_KEY_SETTINGS_FORCE_GLOBAL_EXTREME_REFRESH_RATE,
            ),
            settingsGlobalKey = SETTINGS_KEY_SETTINGS_FORCE_GLOBAL_EXTREME_REFRESH_RATE,
        )
    }

    fun isSettingsRestoreSmartLockEnabled(context: Context): Boolean {
        return LspPreferenceStore.readBoolean(context, KEY_SETTINGS_RESTORE_SMART_LOCK, DEFAULT_SETTINGS_RESTORE_SMART_LOCK)
    }

    fun setSettingsRestoreSmartLockEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_SETTINGS_RESTORE_SMART_LOCK,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SETTINGS_RESTORE_SMART_LOCK,
                PROP_KEY_SETTINGS_RESTORE_SMART_LOCK,
            ),
            settingsGlobalKey = SETTINGS_KEY_SETTINGS_RESTORE_SMART_LOCK,
        )
    }

    fun isSettingsForceGoogleEntryEnabled(context: Context): Boolean {
        return LspPreferenceStore.readBoolean(context, KEY_SETTINGS_FORCE_GOOGLE_ENTRY, DEFAULT_SETTINGS_FORCE_GOOGLE_ENTRY)
    }

    fun setSettingsForceGoogleEntryEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_SETTINGS_FORCE_GOOGLE_ENTRY,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SETTINGS_FORCE_GOOGLE_ENTRY,
                PROP_KEY_SETTINGS_FORCE_GOOGLE_ENTRY
            ),
            settingsGlobalKey = SETTINGS_KEY_SETTINGS_FORCE_GOOGLE_ENTRY
        )
    }

    fun isGmsRegionRestrictionBypassEnabled(context: Context): Boolean {
        return LspPreferenceStore.readBoolean(context, KEY_GMS_REGION_RESTRICTION_BYPASS, DEFAULT_GMS_REGION_RESTRICTION_BYPASS)
    }

    fun setGmsRegionRestrictionBypassEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_GMS_REGION_RESTRICTION_BYPASS,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_GMS_REGION_RESTRICTION_BYPASS,
                PROP_KEY_GMS_REGION_RESTRICTION_BYPASS,
            ),
            settingsGlobalKey = SETTINGS_KEY_GMS_REGION_RESTRICTION_BYPASS,
        )
    }

    fun isEsimRegionRestrictionBypassEnabled(context: Context): Boolean {
        val enabled = LspPreferenceStore.readBoolean(context, KEY_ESIM_REGION_RESTRICTION_BYPASS, DEFAULT_ESIM_REGION_RESTRICTION_BYPASS)
        return enabled && isEsimRegionRestrictionBypassAvailable(context)
    }

    fun setEsimRegionRestrictionBypassEnabled(context: Context, enabled: Boolean) {
        val hiddenOverrideEnabled = prefs(context).getBoolean(
            KEY_ESIM_REGION_RESTRICTION_OVERRIDE,
            DEFAULT_ESIM_REGION_RESTRICTION_OVERRIDE,
        )
        val effectiveEnabled = enabled && (
            hiddenOverrideEnabled || !isEsimRegionRestrictionCountryRestricted(context)
        )
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_ESIM_REGION_RESTRICTION_BYPASS,
            enabled = effectiveEnabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_ESIM_REGION_RESTRICTION_BYPASS,
                PROP_KEY_ESIM_REGION_RESTRICTION_BYPASS,
            ),
            settingsGlobalKey = SETTINGS_KEY_ESIM_REGION_RESTRICTION_BYPASS,
        )
    }

    fun isEsimRegionRestrictionBypassAvailable(context: Context): Boolean {
        return isEsimRegionRestrictionOverrideEnabled(context) ||
            !isEsimRegionRestrictionCountryRestricted(context)
    }

    fun isEsimRegionRestrictionCountryRestricted(context: Context): Boolean {
        return CurrentNetworkCountryGuard.isChina(context)
    }

    fun isEsimRegionRestrictionOverrideEnabled(context: Context): Boolean {
        return LspPreferenceStore.readBoolean(context, KEY_ESIM_REGION_RESTRICTION_OVERRIDE, DEFAULT_ESIM_REGION_RESTRICTION_OVERRIDE)
    }

    fun setEsimRegionRestrictionOverrideEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_ESIM_REGION_RESTRICTION_OVERRIDE,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_ESIM_REGION_RESTRICTION_OVERRIDE,
                PROP_KEY_ESIM_REGION_RESTRICTION_OVERRIDE,
            ),
            settingsGlobalKey = SETTINGS_KEY_ESIM_REGION_RESTRICTION_OVERRIDE,
        )
    }

    fun setEsimRegionRestrictionHiddenOverride(context: Context, enabled: Boolean) {
        if (enabled) {
            setEsimRegionRestrictionOverrideEnabled(context, true)
            setSyncedBooleanPreference(
                context = context,
                prefsKey = KEY_ESIM_REGION_RESTRICTION_BYPASS,
                enabled = true,
                propertyKeys = listOf(
                    PERSIST_PROP_KEY_ESIM_REGION_RESTRICTION_BYPASS,
                    PROP_KEY_ESIM_REGION_RESTRICTION_BYPASS,
                ),
                settingsGlobalKey = SETTINGS_KEY_ESIM_REGION_RESTRICTION_BYPASS,
            )
        } else {
            setEsimRegionRestrictionBypassEnabled(context, false)
            setEsimRegionRestrictionOverrideEnabled(context, false)
        }
    }

    fun isEsimConfirmationCodePromptEnabled(context: Context): Boolean {
        return LspPreferenceStore.readBoolean(context, KEY_ESIM_CONFIRMATION_CODE_PROMPT, isEsimRegionRestrictionBypassEnabled(context))
    }

    fun setEsimConfirmationCodePromptEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_ESIM_CONFIRMATION_CODE_PROMPT,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_ESIM_CONFIRMATION_CODE_PROMPT,
                PROP_KEY_ESIM_CONFIRMATION_CODE_PROMPT,
            ),
            settingsGlobalKey = SETTINGS_KEY_ESIM_CONFIRMATION_CODE_PROMPT,
        )
    }

    fun isEsimProfileLimitBypassEnabled(context: Context): Boolean {
        return LspPreferenceStore.readBoolean(context, KEY_ESIM_PROFILE_LIMIT_BYPASS, DEFAULT_ESIM_PROFILE_LIMIT_BYPASS)
    }

    fun setEsimProfileLimitBypassEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_ESIM_PROFILE_LIMIT_BYPASS,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_ESIM_PROFILE_LIMIT_BYPASS,
                PROP_KEY_ESIM_PROFILE_LIMIT_BYPASS,
            ),
            settingsGlobalKey = SETTINGS_KEY_ESIM_PROFILE_LIMIT_BYPASS,
        )
    }

    fun isMobileNetworkHideAiLinkBoostEnabled(context: Context): Boolean =
        LspPreferenceStore.readBoolean(context, KEY_MOBILE_NETWORK_HIDE_AI_LINK_BOOST, DEFAULT_MOBILE_NETWORK_HIDE_AI_LINK_BOOST)

    fun setMobileNetworkHideAiLinkBoostEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_MOBILE_NETWORK_HIDE_AI_LINK_BOOST,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_MOBILE_NETWORK_HIDE_AI_LINK_BOOST,
                PROP_KEY_MOBILE_NETWORK_HIDE_AI_LINK_BOOST,
            ),
            settingsGlobalKey = SETTINGS_KEY_MOBILE_NETWORK_HIDE_AI_LINK_BOOST,
        )
    }

    fun isMobileNetworkHideRoamingServiceEnabled(context: Context): Boolean =
        LspPreferenceStore.readBoolean(context, KEY_MOBILE_NETWORK_HIDE_ROAMING_SERVICE, DEFAULT_MOBILE_NETWORK_HIDE_ROAMING_SERVICE)

    fun setMobileNetworkHideRoamingServiceEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_MOBILE_NETWORK_HIDE_ROAMING_SERVICE,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_MOBILE_NETWORK_HIDE_ROAMING_SERVICE,
                PROP_KEY_MOBILE_NETWORK_HIDE_ROAMING_SERVICE,
            ),
            settingsGlobalKey = SETTINGS_KEY_MOBILE_NETWORK_HIDE_ROAMING_SERVICE,
        )
    }

    fun isMobileNetworkHideHighDataSimCardEnabled(context: Context): Boolean =
        LspPreferenceStore.readBoolean(context, KEY_MOBILE_NETWORK_HIDE_HIGH_DATA_SIM_CARD, DEFAULT_MOBILE_NETWORK_HIDE_HIGH_DATA_SIM_CARD)

    fun setMobileNetworkHideHighDataSimCardEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_MOBILE_NETWORK_HIDE_HIGH_DATA_SIM_CARD,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_MOBILE_NETWORK_HIDE_HIGH_DATA_SIM_CARD,
                PROP_KEY_MOBILE_NETWORK_HIDE_HIGH_DATA_SIM_CARD,
            ),
            settingsGlobalKey = SETTINGS_KEY_MOBILE_NETWORK_HIDE_HIGH_DATA_SIM_CARD,
        )
    }

    fun isMobileNetworkHideSmartCloudAccelerationEnabled(context: Context): Boolean =
        LspPreferenceStore.readBoolean(context, KEY_MOBILE_NETWORK_HIDE_SMART_CLOUD_ACCELERATION, DEFAULT_MOBILE_NETWORK_HIDE_SMART_CLOUD_ACCELERATION)

    fun setMobileNetworkHideSmartCloudAccelerationEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_MOBILE_NETWORK_HIDE_SMART_CLOUD_ACCELERATION,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_MOBILE_NETWORK_HIDE_SMART_CLOUD_ACCELERATION,
                PROP_KEY_MOBILE_NETWORK_HIDE_SMART_CLOUD_ACCELERATION,
            ),
            settingsGlobalKey = SETTINGS_KEY_MOBILE_NETWORK_HIDE_SMART_CLOUD_ACCELERATION,
        )
    }

    fun isMobileNetworkHidePhoneNumberEnabled(context: Context): Boolean =
        LspPreferenceStore.readBoolean(context, KEY_MOBILE_NETWORK_HIDE_PHONE_NUMBER, DEFAULT_MOBILE_NETWORK_HIDE_PHONE_NUMBER)

    fun setMobileNetworkHidePhoneNumberEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_MOBILE_NETWORK_HIDE_PHONE_NUMBER,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_MOBILE_NETWORK_HIDE_PHONE_NUMBER,
                PROP_KEY_MOBILE_NETWORK_HIDE_PHONE_NUMBER,
            ),
            settingsGlobalKey = SETTINGS_KEY_MOBILE_NETWORK_HIDE_PHONE_NUMBER,
        )
    }

    fun isMobileNetworkForceCarrierOptionsEnabled(context: Context): Boolean =
        LspPreferenceStore.readBoolean(context, KEY_MOBILE_NETWORK_FORCE_CARRIER_OPTIONS, DEFAULT_MOBILE_NETWORK_FORCE_CARRIER_OPTIONS)

    fun setMobileNetworkForceCarrierOptionsEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_MOBILE_NETWORK_FORCE_CARRIER_OPTIONS,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_MOBILE_NETWORK_FORCE_CARRIER_OPTIONS,
                PROP_KEY_MOBILE_NETWORK_FORCE_CARRIER_OPTIONS,
            ),
            settingsGlobalKey = SETTINGS_KEY_MOBILE_NETWORK_FORCE_CARRIER_OPTIONS,
        )
    }

    fun isAppMarketRegionRestrictionBypassEnabled(context: Context): Boolean =
        LspPreferenceStore.readBoolean(context, KEY_APP_MARKET_REGION_RESTRICTION_BYPASS, DEFAULT_APP_MARKET_REGION_RESTRICTION_BYPASS)

    fun setAppMarketRegionRestrictionBypassEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_APP_MARKET_REGION_RESTRICTION_BYPASS,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_APP_MARKET_REGION_RESTRICTION_BYPASS,
                PROP_KEY_APP_MARKET_REGION_RESTRICTION_BYPASS,
            ),
            settingsGlobalKey = SETTINGS_KEY_APP_MARKET_REGION_RESTRICTION_BYPASS,
        )
    }

    fun isAppMarketRemoveSplashRecommendationsEnabled(context: Context): Boolean =
        LspPreferenceStore.readBoolean(context, KEY_APP_MARKET_REMOVE_SPLASH_RECOMMENDATIONS, DEFAULT_APP_MARKET_REMOVE_SPLASH_RECOMMENDATIONS)

    fun setAppMarketRemoveSplashRecommendationsEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_APP_MARKET_REMOVE_SPLASH_RECOMMENDATIONS,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_APP_MARKET_REMOVE_SPLASH_RECOMMENDATIONS,
                PROP_KEY_APP_MARKET_REMOVE_SPLASH_RECOMMENDATIONS,
            ),
            settingsGlobalKey = SETTINGS_KEY_APP_MARKET_REMOVE_SPLASH_RECOMMENDATIONS,
        )
    }

    fun isAppMarketRemoveUpdateDownloadRecommendationsEnabled(context: Context): Boolean =
        LspPreferenceStore.readBoolean(context, KEY_APP_MARKET_REMOVE_UPDATE_DOWNLOAD_RECOMMENDATIONS, DEFAULT_APP_MARKET_REMOVE_UPDATE_DOWNLOAD_RECOMMENDATIONS)

    fun setAppMarketRemoveUpdateDownloadRecommendationsEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_APP_MARKET_REMOVE_UPDATE_DOWNLOAD_RECOMMENDATIONS,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_APP_MARKET_REMOVE_UPDATE_DOWNLOAD_RECOMMENDATIONS,
                PROP_KEY_APP_MARKET_REMOVE_UPDATE_DOWNLOAD_RECOMMENDATIONS,
            ),
            settingsGlobalKey = SETTINGS_KEY_APP_MARKET_REMOVE_UPDATE_DOWNLOAD_RECOMMENDATIONS,
        )
    }

    fun isAppMarketRemoveMineRecommendationsEnabled(context: Context): Boolean =
        LspPreferenceStore.readBoolean(context, KEY_APP_MARKET_REMOVE_MINE_RECOMMENDATIONS, DEFAULT_APP_MARKET_REMOVE_MINE_RECOMMENDATIONS)

    fun setAppMarketRemoveMineRecommendationsEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_APP_MARKET_REMOVE_MINE_RECOMMENDATIONS,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_APP_MARKET_REMOVE_MINE_RECOMMENDATIONS,
                PROP_KEY_APP_MARKET_REMOVE_MINE_RECOMMENDATIONS,
            ),
            settingsGlobalKey = SETTINGS_KEY_APP_MARKET_REMOVE_MINE_RECOMMENDATIONS,
        )
    }

    fun isAppMarketHideSearchHomeRecommendationsEnabled(context: Context): Boolean =
        LspPreferenceStore.readBoolean(context, KEY_APP_MARKET_HIDE_SEARCH_HOME_RECOMMENDATIONS, DEFAULT_APP_MARKET_HIDE_SEARCH_HOME_RECOMMENDATIONS)

    fun setAppMarketHideSearchHomeRecommendationsEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_APP_MARKET_HIDE_SEARCH_HOME_RECOMMENDATIONS,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_APP_MARKET_HIDE_SEARCH_HOME_RECOMMENDATIONS,
                PROP_KEY_APP_MARKET_HIDE_SEARCH_HOME_RECOMMENDATIONS,
            ),
            settingsGlobalKey = SETTINGS_KEY_APP_MARKET_HIDE_SEARCH_HOME_RECOMMENDATIONS,
        )
    }

    fun isAppMarketHideSearchResultRecommendationsEnabled(context: Context): Boolean =
        LspPreferenceStore.readBoolean(context, KEY_APP_MARKET_HIDE_SEARCH_RESULT_RECOMMENDATIONS, DEFAULT_APP_MARKET_HIDE_SEARCH_RESULT_RECOMMENDATIONS)

    fun setAppMarketHideSearchResultRecommendationsEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_APP_MARKET_HIDE_SEARCH_RESULT_RECOMMENDATIONS,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_APP_MARKET_HIDE_SEARCH_RESULT_RECOMMENDATIONS,
                PROP_KEY_APP_MARKET_HIDE_SEARCH_RESULT_RECOMMENDATIONS,
            ),
            settingsGlobalKey = SETTINGS_KEY_APP_MARKET_HIDE_SEARCH_RESULT_RECOMMENDATIONS,
        )
    }

    fun isAppMarketHideDetailRecommendationsEnabled(context: Context): Boolean =
        LspPreferenceStore.readBoolean(context, KEY_APP_MARKET_HIDE_DETAIL_RECOMMENDATIONS, DEFAULT_APP_MARKET_HIDE_DETAIL_RECOMMENDATIONS)

    fun setAppMarketHideDetailRecommendationsEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_APP_MARKET_HIDE_DETAIL_RECOMMENDATIONS,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_APP_MARKET_HIDE_DETAIL_RECOMMENDATIONS,
                PROP_KEY_APP_MARKET_HIDE_DETAIL_RECOMMENDATIONS,
            ),
            settingsGlobalKey = SETTINGS_KEY_APP_MARKET_HIDE_DETAIL_RECOMMENDATIONS,
        )
    }

    /** Unified UI state backed by the existing per-surface switches for hook compatibility. */
    fun isAppMarketSimplifyRecommendationsEnabled(context: Context): Boolean =
        isAppMarketRemoveSplashRecommendationsEnabled(context) ||
            isAppMarketRemoveUpdateDownloadRecommendationsEnabled(context) ||
            isAppMarketRemoveMineRecommendationsEnabled(context) ||
            isAppMarketHideSearchHomeRecommendationsEnabled(context) ||
            isAppMarketHideSearchResultRecommendationsEnabled(context) ||
            isAppMarketHideDetailRecommendationsEnabled(context)

    fun setAppMarketSimplifyRecommendationsEnabled(context: Context, enabled: Boolean) {
        setAppMarketRemoveSplashRecommendationsEnabled(context, enabled)
        setAppMarketRemoveUpdateDownloadRecommendationsEnabled(context, enabled)
        setAppMarketRemoveMineRecommendationsEnabled(context, enabled)
        setAppMarketHideSearchHomeRecommendationsEnabled(context, enabled)
        setAppMarketHideSearchResultRecommendationsEnabled(context, enabled)
        setAppMarketHideDetailRecommendationsEnabled(context, enabled)
    }

    fun isAthenaC17SwipeUpProtectionEnabled(context: Context): Boolean {
        return LspPreferenceStore.readBoolean(context, KEY_ATHENA_C17_SWIPE_UP_PROTECTION, DEFAULT_ATHENA_C17_SWIPE_UP_PROTECTION)
    }

    fun setAthenaC17SwipeUpProtectionEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_ATHENA_C17_SWIPE_UP_PROTECTION,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_ATHENA_C17_SWIPE_UP_PROTECTION,
                PROP_KEY_ATHENA_C17_SWIPE_UP_PROTECTION,
            ),
            settingsGlobalKey = SETTINGS_KEY_ATHENA_C17_SWIPE_UP_PROTECTION,
        )
    }

    fun isOkGoogleHotwordCompatibilityEnabled(context: Context): Boolean {
        return LspPreferenceStore.readBoolean(context, KEY_OK_GOOGLE_HOTWORD_COMPATIBILITY, DEFAULT_OK_GOOGLE_HOTWORD_COMPATIBILITY)
    }

    fun setOkGoogleHotwordCompatibilityEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_OK_GOOGLE_HOTWORD_COMPATIBILITY,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_OK_GOOGLE_HOTWORD_COMPATIBILITY,
                PROP_KEY_OK_GOOGLE_HOTWORD_COMPATIBILITY,
            ),
            settingsGlobalKey = SETTINGS_KEY_OK_GOOGLE_HOTWORD_COMPATIBILITY,
        )
    }

    fun isLauncherHideWidgetLabelsEnabled(context: Context): Boolean {
        return LspPreferenceStore.readBoolean(context, KEY_LAUNCHER_HIDE_WIDGET_LABELS, DEFAULT_LAUNCHER_HIDE_WIDGET_LABELS)
    }

    fun setLauncherHideWidgetLabelsEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_LAUNCHER_HIDE_WIDGET_LABELS,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_LAUNCHER_HIDE_WIDGET_LABELS,
                PROP_KEY_LAUNCHER_HIDE_WIDGET_LABELS,
            ),
            settingsGlobalKey = SETTINGS_KEY_LAUNCHER_HIDE_WIDGET_LABELS,
        )
    }

    fun getLauncherSearchBarMode(context: Context): Int {
        val preferences = prefs(context)
        val stored = preferences.all[KEY_LAUNCHER_SEARCH_BAR_MODE]
        val mode = launcherSearchBarModeValue(stored).sanitizeLauncherSearchBarMode()
        // Migrate the old Boolean setting to a three-state mode.
        if (stored is Boolean && preferences.edit()
                .putInt(KEY_LAUNCHER_SEARCH_BAR_MODE, mode).commitOrReport()
        ) {
            syncReadableState(context)
        }
        return mode
    }

    fun setLauncherSearchBarMode(context: Context, mode: Int) {
        val normalized = mode.sanitizeLauncherSearchBarMode()
        if (!prefs(context).edit().putInt(KEY_LAUNCHER_SEARCH_BAR_MODE, normalized).commitOrReport()) return
        syncReadableState(context)
        syncScalarState(
            value = normalized.toString(),
            propertyKeys = listOf(
                PERSIST_PROP_KEY_LAUNCHER_SEARCH_BAR_MODE,
                PROP_KEY_LAUNCHER_SEARCH_BAR_MODE,
            ),
            settingsGlobalKey = SETTINGS_KEY_LAUNCHER_SEARCH_BAR_MODE,
        )
    }

    fun isLauncherSearchCompatibilityEnabled(context: Context): Boolean =
        LspPreferenceStore.readBoolean(context, KEY_LAUNCHER_SEARCH_COMPATIBILITY, false)

    fun setLauncherSearchCompatibilityEnabled(context: Context, enabled: Boolean) {
        setSyncedBooleanPreference(
            context = context,
            prefsKey = KEY_LAUNCHER_SEARCH_COMPATIBILITY,
            enabled = enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_LAUNCHER_SEARCH_COMPATIBILITY,
                PROP_KEY_LAUNCHER_SEARCH_COMPATIBILITY,
            ),
            settingsGlobalKey = SETTINGS_KEY_LAUNCHER_SEARCH_COMPATIBILITY,
        )
    }

    fun resetToDefaults(context: Context): Boolean {
        require(prefs(context).edit().clear().commit()) {
            "Failed to clear $PREFS_NAME"
        }

        val batchedCommands = mutableListOf<String>()
        syncCommandBatch.set(batchedCommands)
        try {
            setImmersiveNavigationEnabled(context, false)
            setNavigationHandleCustomLengthEnabled(context, false)
            setNavigationHandleLengthDp(context, ImmersiveNavigationRules.SYSTEM_DEFAULT)
            setNavigationHandleOpacity(context, ImmersiveNavigationRules.SYSTEM_DEFAULT)
            setNavigationHandleAutoHideEnabled(context, false)
            setSettingsTitleCollapsedEnabled(context, false)
            setSettingsAppInfoCardEnabled(context, false)
            setSystemRootDetectionBlocked(context, false)
            setExpressNoMiniProgramEnabled(context, false)
            setFluidCloudBatteryEnabled(context, false)
            setFluidCloudMaterialEnabled(context, false)
            setHideDisconnectedBluetoothEnabled(context, false)
            StatusBarInteractionFeature.entries.forEach { setStatusBarInteractionFeatureEnabled(context, it, false) }
            BatteryFeature.entries.forEach { setBatteryFeatureEnabled(context, it, false) }
            TrafficFeature.entries.forEach { setTrafficFeatureEnabled(context, it, false) }
            setPermanentClockSecondsEnabled(context, false)
            SmallWindowFeature.entries.forEach { setSmallWindowFeatureEnabled(context, it, false) }
            LauncherFeature.entries.forEach { setLauncherFeatureEnabled(context, it, false) }
            KeyguardFeature.entries.forEach { setKeyguardFeatureEnabled(context, it, false) }
            PermissionFeature.entries.forEach { setPermissionFeatureEnabled(context, it, false) }
            NotificationRemovalFeature.entries.forEach { setNotificationRemovalEnabled(context, it, false) }
            InstallerFeature.entries.forEach { setInstallerFeatureEnabled(context, it, it.defaultValue) }
            setNativeNotifyIconEnabled(context, true)
            setExtremeRefresh165Enabled(context, false)
            setRecentTaskRadiusEnabled(context, false)
            setAodEnhanceEnabled(context, false)
            setAssistantPowerMode(context, DEFAULT_ASSISTANT_POWER_MODE)
            setAssistantGestureCircleEnabled(context, false)
            setAssistantGestureCircleC17Enabled(context, false)
            setAssistantNativePowerEnabled(context, false)
            setAssistantInternationalPowerChordEnabled(context, true)
            setAssistantNativeCircleEnabled(context, false)
            setRecentTaskRadiusDp(context, DEFAULT_RECENT_TASK_RADIUS_DP)
            setAodInitDarkBrightness(context, DEFAULT_AOD_INIT_DARK_BRIGHTNESS)
            setAodInitBrightBrightness(context, DEFAULT_AOD_INIT_BRIGHT_BRIGHTNESS)
            setAodRunningBrightnessMultiplier(context, DEFAULT_AOD_RUNNING_BRIGHTNESS_MULTIPLIER)
            setAodPanoramicSupportEnabled(context, DEFAULT_AOD_PANORAMIC_SUPPORT)
            setAodSettingsSwitchEnabled(context, DEFAULT_AOD_SETTINGS_SWITCH)
            setAodSingleClickBlockEnabled(context, DEFAULT_AOD_SINGLE_CLICK_BLOCK)
            setNativeNotificationBubblesEnabled(context, DEFAULT_NATIVE_NOTIFICATION_BUBBLES)
            setSystemUiInternationalNetworkDisplayEnabled(
                context,
                DEFAULT_SYSTEMUI_INTERNATIONAL_NETWORK_DISPLAY,
            )
            setSystemUiHideMobileRoamingIndicatorEnabled(
                context,
                DEFAULT_SYSTEMUI_HIDE_MOBILE_ROAMING_INDICATOR,
            )
            setSystemUiHideNetworkActivityIndicatorEnabled(
                context,
                DEFAULT_SYSTEMUI_HIDE_NETWORK_ACTIVITY_INDICATOR,
            )
            setSystemUiNativePowerMenuEnabled(
                context,
                DEFAULT_SYSTEMUI_NATIVE_POWER_MENU,
            )
            setSystemUiRestoreC16NetworkIconOrderEnabled(
                context,
                DEFAULT_SYSTEMUI_RESTORE_C16_NETWORK_ICON_ORDER,
            )
            setSystemUiInternationalNotificationStyleEnabled(
                context,
                DEFAULT_SYSTEMUI_INTERNATIONAL_NOTIFICATION_STYLE,
            )
            setSystemUiForceTonalSpotEnabled(context, DEFAULT_SYSTEMUI_FORCE_TONAL_SPOT)
            setSystemUiMonetColorSpecMode(context, DEFAULT_SYSTEMUI_MONET_COLOR_SPEC_MODE)
            setSystemUiHideQsEditEnabled(context, DEFAULT_SYSTEMUI_HIDE_QS_EDIT)
            setSystemUiHideQsSettingsEnabled(context, DEFAULT_SYSTEMUI_HIDE_QS_SETTINGS)
            setSystemUiHideQsTopCarrierEnabled(context, DEFAULT_SYSTEMUI_HIDE_QS_TOP_CARRIER)
            setSystemUiHideQsMoreEnabled(context, DEFAULT_SYSTEMUI_HIDE_QS_MORE)
            setSystemUiForceNativeClipboardOverlayEnabled(
                context,
                DEFAULT_SYSTEMUI_FORCE_NATIVE_CLIPBOARD_OVERLAY,
            )
            setSettingsInternationalEnabled(context, DEFAULT_SETTINGS_INTERNATIONAL)
            setSettingsForceAppAutoStartEnabled(context, DEFAULT_SETTINGS_FORCE_APP_AUTO_START)
            setSettingsInternationalWalletEnabled(context, DEFAULT_SETTINGS_INTERNATIONAL_WALLET)
            setSettingsRestoreDomesticAboutDeviceEnabled(
                context,
                DEFAULT_SETTINGS_RESTORE_DOMESTIC_ABOUT_DEVICE,
            )
            setSettingsRestoreDomesticAuxiliaryFunctionsEnabled(
                context,
                DEFAULT_SETTINGS_RESTORE_DOMESTIC_AUXILIARY_FUNCTIONS,
            )
            setSettingsC15AboutLayoutEnabled(context, DEFAULT_SETTINGS_C15_ABOUT_LAYOUT)
            setSettingsSkipSpecialPermissionRiskConfirmEnabled(context, DEFAULT_SETTINGS_SKIP_SPECIAL_PERMISSION_RISK_CONFIRM)
            setSettingsRestoreAppOpenButtonEnabled(context, DEFAULT_SETTINGS_RESTORE_APP_OPEN_BUTTON)
            setWallpapersRedOneEntryEnabled(context, DEFAULT_WALLPAPERS_RED_ONE_ENTRY)
            setSettingsRefreshRateUnlocked(context, DEFAULT_SETTINGS_UNLOCK_REFRESH_RATE)
            setSettingsForceGlobalExtremeRefreshRateEnabled(
                context,
                DEFAULT_SETTINGS_FORCE_GLOBAL_EXTREME_REFRESH_RATE,
            )
            setSettingsRestoreSmartLockEnabled(context, DEFAULT_SETTINGS_RESTORE_SMART_LOCK)
            setSettingsForceGoogleEntryEnabled(context, DEFAULT_SETTINGS_FORCE_GOOGLE_ENTRY)
            setGmsRegionRestrictionBypassEnabled(context, DEFAULT_GMS_REGION_RESTRICTION_BYPASS)
            setEsimRegionRestrictionBypassEnabled(context, DEFAULT_ESIM_REGION_RESTRICTION_BYPASS)
            setEsimConfirmationCodePromptEnabled(context, DEFAULT_ESIM_CONFIRMATION_CODE_PROMPT)
            setEsimProfileLimitBypassEnabled(context, DEFAULT_ESIM_PROFILE_LIMIT_BYPASS)
            setEsimRegionRestrictionOverrideEnabled(
                context,
                DEFAULT_ESIM_REGION_RESTRICTION_OVERRIDE,
            )
            setMobileNetworkHideAiLinkBoostEnabled(
                context,
                DEFAULT_MOBILE_NETWORK_HIDE_AI_LINK_BOOST,
            )
            setMobileNetworkHideRoamingServiceEnabled(
                context,
                DEFAULT_MOBILE_NETWORK_HIDE_ROAMING_SERVICE,
            )
            setMobileNetworkHideHighDataSimCardEnabled(
                context,
                DEFAULT_MOBILE_NETWORK_HIDE_HIGH_DATA_SIM_CARD,
            )
            setMobileNetworkHideSmartCloudAccelerationEnabled(
                context,
                DEFAULT_MOBILE_NETWORK_HIDE_SMART_CLOUD_ACCELERATION,
            )
            setMobileNetworkHidePhoneNumberEnabled(
                context,
                DEFAULT_MOBILE_NETWORK_HIDE_PHONE_NUMBER,
            )
            setMobileNetworkForceCarrierOptionsEnabled(
                context,
                DEFAULT_MOBILE_NETWORK_FORCE_CARRIER_OPTIONS,
            )
            setAppMarketRegionRestrictionBypassEnabled(
                context,
                DEFAULT_APP_MARKET_REGION_RESTRICTION_BYPASS,
            )
            setAppMarketRemoveSplashRecommendationsEnabled(
                context,
                DEFAULT_APP_MARKET_REMOVE_SPLASH_RECOMMENDATIONS,
            )
            setAppMarketRemoveUpdateDownloadRecommendationsEnabled(
                context,
                DEFAULT_APP_MARKET_REMOVE_UPDATE_DOWNLOAD_RECOMMENDATIONS,
            )
            setAppMarketRemoveMineRecommendationsEnabled(
                context,
                DEFAULT_APP_MARKET_REMOVE_MINE_RECOMMENDATIONS,
            )
            setAppMarketHideSearchHomeRecommendationsEnabled(
                context,
                DEFAULT_APP_MARKET_HIDE_SEARCH_HOME_RECOMMENDATIONS,
            )
            setAppMarketHideSearchResultRecommendationsEnabled(
                context,
                DEFAULT_APP_MARKET_HIDE_SEARCH_RESULT_RECOMMENDATIONS,
            )
            setAppMarketHideDetailRecommendationsEnabled(
                context,
                DEFAULT_APP_MARKET_HIDE_DETAIL_RECOMMENDATIONS,
            )
            setFileManagerHideSecureAccessTipEnabled(context, false)
            setFileManagerNativePickerEnabled(context, false)
            setAthenaC17SwipeUpProtectionEnabled(
                context,
                DEFAULT_ATHENA_C17_SWIPE_UP_PROTECTION,
            )
            setOkGoogleHotwordCompatibilityEnabled(context, DEFAULT_OK_GOOGLE_HOTWORD_COMPATIBILITY)
            setLauncherHideWidgetLabelsEnabled(context, DEFAULT_LAUNCHER_HIDE_WIDGET_LABELS)
            setLauncherSearchBarMode(context, DEFAULT_LAUNCHER_SEARCH_BAR_MODE)
            setLauncherSearchCompatibilityEnabled(context, false)
        } finally {
            syncCommandBatch.remove()
        }
        return executeSyncCommands("LSP reset", batchedCommands)
    }

    fun syncTogglesForBoot(context: Context): Boolean {
        val nativeEnabled = isNativeNotifyIconEnabled(context)
        val extremeRefresh165Enabled = isExtremeRefresh165Enabled(context)
        val recentTaskRadiusEnabled = isRecentTaskRadiusEnabled(context)
        val aodEnhanceEnabled = isAodEnhanceEnabled(context)
        val assistantPowerMode = getAssistantPowerMode(context)
        val assistantGestureCircleEnabled = isAssistantGestureCircleEnabled(context)
        val assistantGestureCircleC17Enabled = isAssistantGestureCircleC17Enabled(context)
        val assistantNativePowerEnabled = isAssistantNativePowerEnabled(context)
        val assistantInternationalPowerChordEnabled =
            isAssistantInternationalPowerChordEnabled(context)
        val assistantNativeCircleEnabled = isAssistantNativeCircleEnabled(context)
        val recentTaskRadiusDp = getRecentTaskRadiusDp(context)
        val aodInitDarkBrightness = getAodInitDarkBrightness(context)
        val aodInitBrightBrightness = getAodInitBrightBrightness(context)
        val aodRunningMultiplier = getAodRunningBrightnessMultiplier(context)
        val aodPanoramicSupport = isAodPanoramicSupportEnabled(context)
        val aodSettingsSwitch = isAodSettingsSwitchEnabled(context)
        val aodSingleClickBlock = isAodSingleClickBlockEnabled(context)
        val nativeNotificationBubbles = isNativeNotificationBubblesEnabled(context)
        val systemUiInternationalNetworkDisplay =
            isSystemUiInternationalNetworkDisplayEnabled(context)
        val systemUiHideMobileRoamingIndicator =
            isSystemUiHideMobileRoamingIndicatorEnabled(context)
        val systemUiHideNetworkActivityIndicator =
            isSystemUiHideNetworkActivityIndicatorEnabled(context)
        val systemUiNativePowerMenu = isSystemUiNativePowerMenuEnabled(context)
        val systemUiRestoreC16NetworkIconOrder =
            isSystemUiRestoreC16NetworkIconOrderEnabled(context)
        val systemUiInternationalNotificationStyle =
            isSystemUiInternationalNotificationStyleEnabled(context)
        val systemUiForceTonalSpot = isSystemUiForceTonalSpotEnabled(context)
        val systemUiMonetColorSpecMode = getSystemUiMonetColorSpecMode(context)
        val systemUiHideQsEdit = isSystemUiHideQsEditEnabled(context)
        val systemUiHideQsSettings = isSystemUiHideQsSettingsEnabled(context)
        val systemUiHideQsTopCarrier = isSystemUiHideQsTopCarrierEnabled(context)
        val systemUiHideQsMore = isSystemUiHideQsMoreEnabled(context)
        val systemUiForceNativeClipboardOverlay = isSystemUiForceNativeClipboardOverlayEnabled(context)
        val settingsInternational = isSettingsInternationalEnabled(context)
        val settingsForceAppAutoStart = isSettingsForceAppAutoStartEnabled(context)
        val settingsInternationalWallet = isSettingsInternationalWalletEnabled(context)
        val settingsRestoreDomesticAboutDevice =
            isSettingsRestoreDomesticAboutDeviceEnabled(context)
        val settingsRestoreDomesticAuxiliaryFunctions =
            isSettingsRestoreDomesticAuxiliaryFunctionsEnabled(context)
        val settingsC15AboutLayout = isSettingsC15AboutLayoutEnabled(context)
        val settingsSkipSpecialPermissionRiskConfirm = isSettingsSkipSpecialPermissionRiskConfirmEnabled(context)
        val settingsRestoreAppOpenButton = isSettingsRestoreAppOpenButtonEnabled(context)
        val wallpapersRedOneEntry = isWallpapersRedOneEntryEnabled(context)
        val settingsUnlockRefreshRate = isSettingsRefreshRateUnlocked(context)
        val settingsForceGlobalExtremeRefreshRate =
            isSettingsForceGlobalExtremeRefreshRateEnabled(context)
        val settingsRestoreSmartLock = isSettingsRestoreSmartLockEnabled(context)
        val settingsForceGoogleEntry = isSettingsForceGoogleEntryEnabled(context)
        val gmsRegionRestrictionBypass = isGmsRegionRestrictionBypassEnabled(context)
        val esimRegionRestrictionBypass = isEsimRegionRestrictionBypassEnabled(context)
        val esimConfirmationCodePrompt = isEsimConfirmationCodePromptEnabled(context)
        val esimProfileLimitBypass = isEsimProfileLimitBypassEnabled(context)
        val esimRegionRestrictionOverride = isEsimRegionRestrictionOverrideEnabled(context)
        val mobileNetworkHideAiLinkBoost = isMobileNetworkHideAiLinkBoostEnabled(context)
        val mobileNetworkHideRoamingService =
            isMobileNetworkHideRoamingServiceEnabled(context)
        val mobileNetworkHideHighDataSimCard =
            isMobileNetworkHideHighDataSimCardEnabled(context)
        val mobileNetworkHideSmartCloudAcceleration =
            isMobileNetworkHideSmartCloudAccelerationEnabled(context)
        val mobileNetworkHidePhoneNumber =
            isMobileNetworkHidePhoneNumberEnabled(context)
        val mobileNetworkForceCarrierOptions =
            isMobileNetworkForceCarrierOptionsEnabled(context)
        val appMarketRegionRestrictionBypass =
            isAppMarketRegionRestrictionBypassEnabled(context)
        val appMarketRemoveSplashRecommendations =
            isAppMarketRemoveSplashRecommendationsEnabled(context)
        val appMarketRemoveUpdateDownloadRecommendations =
            isAppMarketRemoveUpdateDownloadRecommendationsEnabled(context)
        val appMarketRemoveMineRecommendations =
            isAppMarketRemoveMineRecommendationsEnabled(context)
        val appMarketHideSearchHomeRecommendations =
            isAppMarketHideSearchHomeRecommendationsEnabled(context)
        val appMarketHideSearchResultRecommendations =
            isAppMarketHideSearchResultRecommendationsEnabled(context)
        val appMarketHideDetailRecommendations =
            isAppMarketHideDetailRecommendationsEnabled(context)
        val fileManagerHideSecureAccessTip = isFileManagerHideSecureAccessTipEnabled(context)
        val fileManagerNativePicker = isFileManagerNativePickerEnabled(context)
        val athenaC17SwipeUpProtection = isAthenaC17SwipeUpProtectionEnabled(context)
        val okGoogleHotwordCompatibility = isOkGoogleHotwordCompatibilityEnabled(context)
        val launcherHideWidgetLabels = isLauncherHideWidgetLabelsEnabled(context)
        val launcherSearchBarMode = getLauncherSearchBarMode(context)
        val launcherSearchCompatibility = isLauncherSearchCompatibilityEnabled(context)
        syncReadableState(context)
        val batchedCommands = mutableListOf<String>()
        syncCommandBatch.set(batchedCommands)
        try {
        syncScalarState(
            value = if (isImmersiveNavigationEnabled(context)) "1" else "0",
            propertyKeys = listOf(PERSIST_PROP_IMMERSIVE_NAVIGATION, PROP_IMMERSIVE_NAVIGATION),
            settingsGlobalKey = SETTINGS_IMMERSIVE_NAVIGATION,
        )
        syncScalarState(
            value = if (isNavigationHandleCustomLengthEnabled(context)) "1" else "0",
            propertyKeys = listOf(PERSIST_PROP_NAVIGATION_HANDLE_CUSTOM_LENGTH, PROP_NAVIGATION_HANDLE_CUSTOM_LENGTH),
            settingsGlobalKey = SETTINGS_NAVIGATION_HANDLE_CUSTOM_LENGTH,
        )
        syncScalarState(
            value = getNavigationHandleLengthDp(context).toString(),
            propertyKeys = listOf(PERSIST_PROP_NAVIGATION_HANDLE_LENGTH_DP, PROP_NAVIGATION_HANDLE_LENGTH_DP),
            settingsGlobalKey = SETTINGS_NAVIGATION_HANDLE_LENGTH_DP,
        )
        syncScalarState(
            value = getNavigationHandleOpacity(context).toString(),
            propertyKeys = listOf(PERSIST_PROP_NAVIGATION_HANDLE_OPACITY, PROP_NAVIGATION_HANDLE_OPACITY),
            settingsGlobalKey = SETTINGS_NAVIGATION_HANDLE_OPACITY,
        )
        syncScalarState(
            value = if (isNavigationHandleAutoHideEnabled(context)) "1" else "0",
            propertyKeys = listOf(PERSIST_PROP_NAVIGATION_HANDLE_AUTO_HIDE, PROP_NAVIGATION_HANDLE_AUTO_HIDE),
            settingsGlobalKey = SETTINGS_NAVIGATION_HANDLE_AUTO_HIDE,
        )
        syncPermissionFeatures(context)
        syncScalarState(
            value = if (isSystemRootDetectionBlocked(context)) "1" else "0",
            propertyKeys = listOf(PERSIST_PROP_SYSTEM_ROOT_DETECTION, PROP_SYSTEM_ROOT_DETECTION),
            settingsGlobalKey = SETTINGS_SYSTEM_ROOT_DETECTION,
        )
        syncScalarState(
            value = if (isSettingsTitleCollapsedEnabled(context)) "1" else "0",
            propertyKeys = listOf(PERSIST_PROP_SETTINGS_TITLE_COLLAPSED, PROP_SETTINGS_TITLE_COLLAPSED),
            settingsGlobalKey = SETTINGS_TITLE_COLLAPSED,
        )
        syncScalarState(
            value = if (isSettingsAppInfoCardEnabled(context)) "1" else "0",
            propertyKeys = listOf(PERSIST_PROP_SETTINGS_APP_INFO_CARD, PROP_SETTINGS_APP_INFO_CARD),
            settingsGlobalKey = SETTINGS_APP_INFO_CARD,
        )
        syncScalarState(
            value = if (isExpressNoMiniProgramEnabled(context)) "1" else "0",
            propertyKeys = listOf(PERSIST_PROP_EXPRESS_NO_MINI_PROGRAM, PROP_EXPRESS_NO_MINI_PROGRAM),
            settingsGlobalKey = SETTINGS_EXPRESS_NO_MINI_PROGRAM,
        )
        syncSmallWindowFeatures(context)
        syncLauncherFeatures(context)
        syncScalarState(
            value = if (isFluidCloudBatteryEnabled(context)) "1" else "0",
            propertyKeys = listOf(PERSIST_PROP_FLUID_CLOUD_BATTERY, PROP_FLUID_CLOUD_BATTERY),
            settingsGlobalKey = SETTINGS_FLUID_CLOUD_BATTERY,
        )
        syncScalarState(
            value = if (isFluidCloudMaterialEnabled(context)) "1" else "0",
            propertyKeys = listOf(PERSIST_PROP_FLUID_CLOUD_MATERIAL, PROP_FLUID_CLOUD_MATERIAL),
            settingsGlobalKey = SETTINGS_FLUID_CLOUD_MATERIAL,
        )
        syncScalarState(
            value = if (isHideDisconnectedBluetoothEnabled(context)) "1" else "0",
            propertyKeys = listOf(PERSIST_PROP_HIDE_DISCONNECTED_BLUETOOTH, PROP_HIDE_DISCONNECTED_BLUETOOTH),
            settingsGlobalKey = SETTINGS_HIDE_DISCONNECTED_BLUETOOTH,
        )
        syncKeyguardFeatures(context)
        TrafficFeature.entries.forEach { feature ->
            syncScalarState(
                value = if (isTrafficFeatureEnabled(context, feature)) "1" else "0",
                propertyKeys = listOf(feature.persistPropertyKey, feature.propertyKey),
                settingsGlobalKey = feature.settingsKey,
            )
        }
        BatteryFeature.entries.forEach { feature ->
            syncScalarState(
                value = if (isBatteryFeatureEnabled(context, feature)) "1" else "0",
                propertyKeys = listOf(feature.persistPropertyKey, feature.propertyKey),
                settingsGlobalKey = feature.settingsKey,
            )
        }
        StatusBarInteractionFeature.entries.forEach { feature ->
            syncScalarState(
                value = if (isStatusBarInteractionFeatureEnabled(context, feature)) "1" else "0",
                propertyKeys = listOf(feature.persistPropertyKey, feature.propertyKey),
                settingsGlobalKey = feature.settingsKey,
            )
        }
        syncScalarState(
            value = if (isPermanentClockSecondsEnabled(context)) "1" else "0",
            propertyKeys = listOf(PERSIST_PROP_PERMANENT_CLOCK_SECONDS, PROP_PERMANENT_CLOCK_SECONDS),
            settingsGlobalKey = SETTINGS_PERMANENT_CLOCK_SECONDS,
        )
        syncNotificationRemovalFeatures(context)
        syncInstallerFeatures(context)
        syncFlagState(
            enabled = nativeEnabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_NATIVE_NOTIFY_ICON,
                PROP_KEY_NATIVE_NOTIFY_ICON
            ),
            settingsGlobalKey = SETTINGS_KEY_NATIVE_NOTIFY_ICON,
            flagFilePath = FLAG_FILE_PATH_NATIVE_NOTIFY_ICON
        )
        syncFlagState(
            enabled = extremeRefresh165Enabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_EXTREME_REFRESH_165,
                PROP_KEY_EXTREME_REFRESH_165
            ),
            settingsGlobalKey = SETTINGS_KEY_EXTREME_REFRESH_165,
            flagFilePath = FLAG_FILE_PATH_EXTREME_REFRESH_165
        )
        syncFlagState(
            enabled = recentTaskRadiusEnabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_RECENT_TASK_RADIUS,
                PROP_KEY_RECENT_TASK_RADIUS
            ),
            settingsGlobalKey = SETTINGS_KEY_RECENT_TASK_RADIUS,
            flagFilePath = FLAG_FILE_PATH_RECENT_TASK_RADIUS
        )
        syncFlagState(
            enabled = aodEnhanceEnabled,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_AOD_ENHANCE,
                PROP_KEY_AOD_ENHANCE
            ),
            settingsGlobalKey = SETTINGS_KEY_AOD_ENHANCE,
            flagFilePath = FLAG_FILE_PATH_AOD_ENHANCE
        )
        syncScalarState(
            value = assistantPowerMode.toString(),
            propertyKeys = listOf(
                PERSIST_PROP_KEY_ASSISTANT_POWER_MODE,
                PROP_KEY_ASSISTANT_POWER_MODE
            ),
            settingsGlobalKey = SETTINGS_KEY_ASSISTANT_POWER_MODE
        )
        syncScalarState(
            value = if (assistantGestureCircleEnabled) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_ASSISTANT_GESTURE_CIRCLE,
                PROP_KEY_ASSISTANT_GESTURE_CIRCLE
            ),
            settingsGlobalKey = SETTINGS_KEY_ASSISTANT_GESTURE_CIRCLE
        )
        syncScalarState(
            value = if (assistantGestureCircleC17Enabled) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_ASSISTANT_GESTURE_CIRCLE_C17,
                PROP_KEY_ASSISTANT_GESTURE_CIRCLE_C17
            ),
            settingsGlobalKey = SETTINGS_KEY_ASSISTANT_GESTURE_CIRCLE_C17
        )
        syncScalarState(
            value = if (assistantNativePowerEnabled) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_ASSISTANT_NATIVE_POWER,
                PROP_KEY_ASSISTANT_NATIVE_POWER,
            ),
            settingsGlobalKey = SETTINGS_KEY_ASSISTANT_NATIVE_POWER,
        )
        syncScalarState(
            value = if (assistantInternationalPowerChordEnabled) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_ASSISTANT_INTERNATIONAL_POWER_CHORD,
                PROP_KEY_ASSISTANT_INTERNATIONAL_POWER_CHORD,
            ),
            settingsGlobalKey = SETTINGS_KEY_ASSISTANT_INTERNATIONAL_POWER_CHORD,
        )
        syncScalarState(
            value = if (assistantNativeCircleEnabled) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_ASSISTANT_NATIVE_CIRCLE,
                PROP_KEY_ASSISTANT_NATIVE_CIRCLE,
            ),
            settingsGlobalKey = SETTINGS_KEY_ASSISTANT_NATIVE_CIRCLE,
        )
        syncScalarState(
            value = recentTaskRadiusDp.toString(),
            propertyKeys = listOf(
                PERSIST_PROP_KEY_RECENT_TASK_RADIUS_DP,
                PROP_KEY_RECENT_TASK_RADIUS_DP
            ),
            settingsGlobalKey = SETTINGS_KEY_RECENT_TASK_RADIUS_DP
        )
        syncScalarState(
            value = aodInitDarkBrightness.toString(),
            propertyKeys = listOf(
                PERSIST_PROP_KEY_AOD_INIT_DARK_BRIGHTNESS,
                PROP_KEY_AOD_INIT_DARK_BRIGHTNESS
            ),
            settingsGlobalKey = SETTINGS_KEY_AOD_INIT_DARK_BRIGHTNESS
        )
        syncScalarState(
            value = aodInitBrightBrightness.toString(),
            propertyKeys = listOf(
                PERSIST_PROP_KEY_AOD_INIT_BRIGHT_BRIGHTNESS,
                PROP_KEY_AOD_INIT_BRIGHT_BRIGHTNESS
            ),
            settingsGlobalKey = SETTINGS_KEY_AOD_INIT_BRIGHT_BRIGHTNESS
        )
        syncScalarState(
            value = aodRunningMultiplier.toString(),
            propertyKeys = listOf(
                PERSIST_PROP_KEY_AOD_RUNNING_BRIGHTNESS_MULTIPLIER,
                PROP_KEY_AOD_RUNNING_BRIGHTNESS_MULTIPLIER
            ),
            settingsGlobalKey = SETTINGS_KEY_AOD_RUNNING_BRIGHTNESS_MULTIPLIER
        )
        syncScalarState(
            value = if (aodPanoramicSupport) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_AOD_PANORAMIC_SUPPORT,
                PROP_KEY_AOD_PANORAMIC_SUPPORT
            ),
            settingsGlobalKey = SETTINGS_KEY_AOD_PANORAMIC_SUPPORT
        )
        syncScalarState(
            value = if (aodSettingsSwitch) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_AOD_SETTINGS_SWITCH,
                PROP_KEY_AOD_SETTINGS_SWITCH
            ),
            settingsGlobalKey = SETTINGS_KEY_AOD_SETTINGS_SWITCH
        )
        syncScalarState(
            value = if (aodSingleClickBlock) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_AOD_SINGLE_CLICK_BLOCK,
                PROP_KEY_AOD_SINGLE_CLICK_BLOCK
            ),
            settingsGlobalKey = SETTINGS_KEY_AOD_SINGLE_CLICK_BLOCK
        )
        syncFlagState(
            enabled = nativeNotificationBubbles,
            propertyKeys = listOf(
                PERSIST_PROP_KEY_NATIVE_NOTIFICATION_BUBBLES,
                PROP_KEY_NATIVE_NOTIFICATION_BUBBLES
            ),
            settingsGlobalKey = SETTINGS_KEY_NATIVE_NOTIFICATION_BUBBLES,
            flagFilePath = FLAG_FILE_PATH_NATIVE_NOTIFICATION_BUBBLES
        )
        syncScalarState(
            value = if (systemUiInternationalNetworkDisplay) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SYSTEMUI_INTERNATIONAL_NETWORK_DISPLAY,
                PROP_KEY_SYSTEMUI_INTERNATIONAL_NETWORK_DISPLAY
            ),
            settingsGlobalKey = SETTINGS_KEY_SYSTEMUI_INTERNATIONAL_NETWORK_DISPLAY
        )
        syncScalarState(
            value = if (systemUiHideMobileRoamingIndicator) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SYSTEMUI_HIDE_MOBILE_ROAMING_INDICATOR,
                PROP_KEY_SYSTEMUI_HIDE_MOBILE_ROAMING_INDICATOR
            ),
            settingsGlobalKey = SETTINGS_KEY_SYSTEMUI_HIDE_MOBILE_ROAMING_INDICATOR
        )
        syncScalarState(
            value = if (systemUiHideNetworkActivityIndicator) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SYSTEMUI_HIDE_NETWORK_ACTIVITY_INDICATOR,
                PROP_KEY_SYSTEMUI_HIDE_NETWORK_ACTIVITY_INDICATOR,
            ),
            settingsGlobalKey = SETTINGS_KEY_SYSTEMUI_HIDE_NETWORK_ACTIVITY_INDICATOR,
        )
        syncScalarState(
            value = if (systemUiNativePowerMenu) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SYSTEMUI_NATIVE_POWER_MENU,
                PROP_KEY_SYSTEMUI_NATIVE_POWER_MENU,
            ),
            settingsGlobalKey = SETTINGS_KEY_SYSTEMUI_NATIVE_POWER_MENU,
        )
        syncScalarState(
            value = if (systemUiRestoreC16NetworkIconOrder) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SYSTEMUI_RESTORE_C16_NETWORK_ICON_ORDER,
                PROP_KEY_SYSTEMUI_RESTORE_C16_NETWORK_ICON_ORDER,
            ),
            settingsGlobalKey = SETTINGS_KEY_SYSTEMUI_RESTORE_C16_NETWORK_ICON_ORDER,
        )
        syncScalarState(
            value = if (systemUiInternationalNotificationStyle) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SYSTEMUI_INTERNATIONAL_NOTIFICATION_STYLE,
                PROP_KEY_SYSTEMUI_INTERNATIONAL_NOTIFICATION_STYLE
            ),
            settingsGlobalKey = SETTINGS_KEY_SYSTEMUI_INTERNATIONAL_NOTIFICATION_STYLE
        )
        syncScalarState(
            value = if (systemUiForceTonalSpot) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SYSTEMUI_FORCE_TONAL_SPOT,
                PROP_KEY_SYSTEMUI_FORCE_TONAL_SPOT,
            ),
            settingsGlobalKey = SETTINGS_KEY_SYSTEMUI_FORCE_TONAL_SPOT,
        )
        syncScalarState(
            value = systemUiMonetColorSpecMode.toString(),
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SYSTEMUI_MONET_COLOR_SPEC_MODE,
                PROP_KEY_SYSTEMUI_MONET_COLOR_SPEC_MODE,
            ),
            settingsGlobalKey = SETTINGS_KEY_SYSTEMUI_MONET_COLOR_SPEC_MODE,
        )
        syncScalarState(
            value = if (systemUiHideQsEdit) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SYSTEMUI_HIDE_QS_EDIT,
                PROP_KEY_SYSTEMUI_HIDE_QS_EDIT
            ),
            settingsGlobalKey = SETTINGS_KEY_SYSTEMUI_HIDE_QS_EDIT
        )
        syncScalarState(
            value = if (systemUiHideQsSettings) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SYSTEMUI_HIDE_QS_SETTINGS,
                PROP_KEY_SYSTEMUI_HIDE_QS_SETTINGS
            ),
            settingsGlobalKey = SETTINGS_KEY_SYSTEMUI_HIDE_QS_SETTINGS
        )
        syncScalarState(
            value = if (systemUiHideQsTopCarrier) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SYSTEMUI_HIDE_QS_TOP_CARRIER,
                PROP_KEY_SYSTEMUI_HIDE_QS_TOP_CARRIER
            ),
            settingsGlobalKey = SETTINGS_KEY_SYSTEMUI_HIDE_QS_TOP_CARRIER
        )
        syncScalarState(
            value = if (systemUiHideQsMore) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SYSTEMUI_HIDE_QS_MORE,
                PROP_KEY_SYSTEMUI_HIDE_QS_MORE
            ),
            settingsGlobalKey = SETTINGS_KEY_SYSTEMUI_HIDE_QS_MORE
        )
        syncScalarState(
            value = if (systemUiForceNativeClipboardOverlay) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SYSTEMUI_FORCE_NATIVE_CLIPBOARD_OVERLAY,
                PROP_KEY_SYSTEMUI_FORCE_NATIVE_CLIPBOARD_OVERLAY
            ),
            settingsGlobalKey = SETTINGS_KEY_SYSTEMUI_FORCE_NATIVE_CLIPBOARD_OVERLAY
        )
        syncScalarState(
            value = if (settingsInternational) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SETTINGS_INTERNATIONAL,
                PROP_KEY_SETTINGS_INTERNATIONAL
            ),
            settingsGlobalKey = SETTINGS_KEY_SETTINGS_INTERNATIONAL
        )
        syncScalarState(
            value = if (settingsForceAppAutoStart) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SETTINGS_FORCE_APP_AUTO_START,
                PROP_KEY_SETTINGS_FORCE_APP_AUTO_START
            ),
            settingsGlobalKey = SETTINGS_KEY_SETTINGS_FORCE_APP_AUTO_START
        )
        syncScalarState(
            value = if (settingsInternationalWallet) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SETTINGS_INTERNATIONAL_WALLET,
                PROP_KEY_SETTINGS_INTERNATIONAL_WALLET
            ),
            settingsGlobalKey = SETTINGS_KEY_SETTINGS_INTERNATIONAL_WALLET
        )
        syncScalarState(
            value = if (settingsRestoreDomesticAboutDevice) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SETTINGS_RESTORE_DOMESTIC_ABOUT_DEVICE,
                PROP_KEY_SETTINGS_RESTORE_DOMESTIC_ABOUT_DEVICE,
            ),
            settingsGlobalKey = SETTINGS_KEY_SETTINGS_RESTORE_DOMESTIC_ABOUT_DEVICE,
        )
        syncScalarState(
            value = if (settingsRestoreDomesticAuxiliaryFunctions) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SETTINGS_RESTORE_DOMESTIC_AUXILIARY_FUNCTIONS,
                PROP_KEY_SETTINGS_RESTORE_DOMESTIC_AUXILIARY_FUNCTIONS,
            ),
            settingsGlobalKey = SETTINGS_KEY_SETTINGS_RESTORE_DOMESTIC_AUXILIARY_FUNCTIONS,
        )
        syncScalarState(
            value = if (settingsSkipSpecialPermissionRiskConfirm) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SETTINGS_SKIP_SPECIAL_PERMISSION_RISK_CONFIRM,
                PROP_KEY_SETTINGS_SKIP_SPECIAL_PERMISSION_RISK_CONFIRM,
            ),
            settingsGlobalKey = SETTINGS_KEY_SETTINGS_SKIP_SPECIAL_PERMISSION_RISK_CONFIRM,
        )
        syncScalarState(
            value = if (settingsRestoreAppOpenButton) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SETTINGS_RESTORE_APP_OPEN_BUTTON,
                PROP_KEY_SETTINGS_RESTORE_APP_OPEN_BUTTON,
            ),
            settingsGlobalKey = SETTINGS_KEY_SETTINGS_RESTORE_APP_OPEN_BUTTON,
        )
        syncScalarState(
            value = if (settingsC15AboutLayout) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SETTINGS_C15_ABOUT_LAYOUT,
                PROP_KEY_SETTINGS_C15_ABOUT_LAYOUT,
            ),
            settingsGlobalKey = SETTINGS_KEY_SETTINGS_C15_ABOUT_LAYOUT,
        )
        syncScalarState(
            value = if (wallpapersRedOneEntry) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_WALLPAPERS_RED_ONE_ENTRY,
                PROP_KEY_WALLPAPERS_RED_ONE_ENTRY,
            ),
            settingsGlobalKey = SETTINGS_KEY_WALLPAPERS_RED_ONE_ENTRY,
        )
        syncScalarState(
            value = if (settingsUnlockRefreshRate) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SETTINGS_UNLOCK_REFRESH_RATE,
                PROP_KEY_SETTINGS_UNLOCK_REFRESH_RATE,
            ),
            settingsGlobalKey = SETTINGS_KEY_SETTINGS_UNLOCK_REFRESH_RATE,
        )
        syncScalarState(
            value = if (settingsForceGlobalExtremeRefreshRate) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SETTINGS_FORCE_GLOBAL_EXTREME_REFRESH_RATE,
                PROP_KEY_SETTINGS_FORCE_GLOBAL_EXTREME_REFRESH_RATE,
            ),
            settingsGlobalKey = SETTINGS_KEY_SETTINGS_FORCE_GLOBAL_EXTREME_REFRESH_RATE,
        )
        syncScalarState(
            value = if (settingsRestoreSmartLock) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SETTINGS_RESTORE_SMART_LOCK,
                PROP_KEY_SETTINGS_RESTORE_SMART_LOCK,
            ),
            settingsGlobalKey = SETTINGS_KEY_SETTINGS_RESTORE_SMART_LOCK,
        )
        syncScalarState(
            value = if (settingsForceGoogleEntry) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_SETTINGS_FORCE_GOOGLE_ENTRY,
                PROP_KEY_SETTINGS_FORCE_GOOGLE_ENTRY
            ),
            settingsGlobalKey = SETTINGS_KEY_SETTINGS_FORCE_GOOGLE_ENTRY
        )
        syncScalarState(
            value = if (gmsRegionRestrictionBypass) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_GMS_REGION_RESTRICTION_BYPASS,
                PROP_KEY_GMS_REGION_RESTRICTION_BYPASS,
            ),
            settingsGlobalKey = SETTINGS_KEY_GMS_REGION_RESTRICTION_BYPASS,
        )
        syncScalarState(
            value = if (esimRegionRestrictionBypass) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_ESIM_REGION_RESTRICTION_BYPASS,
                PROP_KEY_ESIM_REGION_RESTRICTION_BYPASS,
            ),
            settingsGlobalKey = SETTINGS_KEY_ESIM_REGION_RESTRICTION_BYPASS,
        )
        syncScalarState(
            value = if (esimConfirmationCodePrompt) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_ESIM_CONFIRMATION_CODE_PROMPT,
                PROP_KEY_ESIM_CONFIRMATION_CODE_PROMPT,
            ),
            settingsGlobalKey = SETTINGS_KEY_ESIM_CONFIRMATION_CODE_PROMPT,
        )
        syncScalarState(
            value = if (esimProfileLimitBypass) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_ESIM_PROFILE_LIMIT_BYPASS,
                PROP_KEY_ESIM_PROFILE_LIMIT_BYPASS,
            ),
            settingsGlobalKey = SETTINGS_KEY_ESIM_PROFILE_LIMIT_BYPASS,
        )
        syncScalarState(
            value = if (esimRegionRestrictionOverride) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_ESIM_REGION_RESTRICTION_OVERRIDE,
                PROP_KEY_ESIM_REGION_RESTRICTION_OVERRIDE,
            ),
            settingsGlobalKey = SETTINGS_KEY_ESIM_REGION_RESTRICTION_OVERRIDE,
        )
        syncScalarState(
            value = if (mobileNetworkHideAiLinkBoost) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_MOBILE_NETWORK_HIDE_AI_LINK_BOOST,
                PROP_KEY_MOBILE_NETWORK_HIDE_AI_LINK_BOOST,
            ),
            settingsGlobalKey = SETTINGS_KEY_MOBILE_NETWORK_HIDE_AI_LINK_BOOST,
        )
        syncScalarState(
            value = if (mobileNetworkHideRoamingService) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_MOBILE_NETWORK_HIDE_ROAMING_SERVICE,
                PROP_KEY_MOBILE_NETWORK_HIDE_ROAMING_SERVICE,
            ),
            settingsGlobalKey = SETTINGS_KEY_MOBILE_NETWORK_HIDE_ROAMING_SERVICE,
        )
        syncScalarState(
            value = if (mobileNetworkHideHighDataSimCard) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_MOBILE_NETWORK_HIDE_HIGH_DATA_SIM_CARD,
                PROP_KEY_MOBILE_NETWORK_HIDE_HIGH_DATA_SIM_CARD,
            ),
            settingsGlobalKey = SETTINGS_KEY_MOBILE_NETWORK_HIDE_HIGH_DATA_SIM_CARD,
        )
        syncScalarState(
            value = if (mobileNetworkHideSmartCloudAcceleration) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_MOBILE_NETWORK_HIDE_SMART_CLOUD_ACCELERATION,
                PROP_KEY_MOBILE_NETWORK_HIDE_SMART_CLOUD_ACCELERATION,
            ),
            settingsGlobalKey = SETTINGS_KEY_MOBILE_NETWORK_HIDE_SMART_CLOUD_ACCELERATION,
        )
        syncScalarState(
            value = if (mobileNetworkHidePhoneNumber) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_MOBILE_NETWORK_HIDE_PHONE_NUMBER,
                PROP_KEY_MOBILE_NETWORK_HIDE_PHONE_NUMBER,
            ),
            settingsGlobalKey = SETTINGS_KEY_MOBILE_NETWORK_HIDE_PHONE_NUMBER,
        )
        syncScalarState(
            value = if (mobileNetworkForceCarrierOptions) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_MOBILE_NETWORK_FORCE_CARRIER_OPTIONS,
                PROP_KEY_MOBILE_NETWORK_FORCE_CARRIER_OPTIONS,
            ),
            settingsGlobalKey = SETTINGS_KEY_MOBILE_NETWORK_FORCE_CARRIER_OPTIONS,
        )
        syncScalarState(
            value = if (appMarketRegionRestrictionBypass) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_APP_MARKET_REGION_RESTRICTION_BYPASS,
                PROP_KEY_APP_MARKET_REGION_RESTRICTION_BYPASS,
            ),
            settingsGlobalKey = SETTINGS_KEY_APP_MARKET_REGION_RESTRICTION_BYPASS,
        )
        syncScalarState(
            value = if (appMarketRemoveSplashRecommendations) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_APP_MARKET_REMOVE_SPLASH_RECOMMENDATIONS,
                PROP_KEY_APP_MARKET_REMOVE_SPLASH_RECOMMENDATIONS,
            ),
            settingsGlobalKey = SETTINGS_KEY_APP_MARKET_REMOVE_SPLASH_RECOMMENDATIONS,
        )
        syncScalarState(
            value = if (appMarketRemoveUpdateDownloadRecommendations) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_APP_MARKET_REMOVE_UPDATE_DOWNLOAD_RECOMMENDATIONS,
                PROP_KEY_APP_MARKET_REMOVE_UPDATE_DOWNLOAD_RECOMMENDATIONS,
            ),
            settingsGlobalKey = SETTINGS_KEY_APP_MARKET_REMOVE_UPDATE_DOWNLOAD_RECOMMENDATIONS,
        )
        syncScalarState(
            value = if (appMarketRemoveMineRecommendations) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_APP_MARKET_REMOVE_MINE_RECOMMENDATIONS,
                PROP_KEY_APP_MARKET_REMOVE_MINE_RECOMMENDATIONS,
            ),
            settingsGlobalKey = SETTINGS_KEY_APP_MARKET_REMOVE_MINE_RECOMMENDATIONS,
        )
        syncScalarState(
            value = if (appMarketHideSearchHomeRecommendations) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_APP_MARKET_HIDE_SEARCH_HOME_RECOMMENDATIONS,
                PROP_KEY_APP_MARKET_HIDE_SEARCH_HOME_RECOMMENDATIONS,
            ),
            settingsGlobalKey = SETTINGS_KEY_APP_MARKET_HIDE_SEARCH_HOME_RECOMMENDATIONS,
        )
        syncScalarState(
            value = if (appMarketHideSearchResultRecommendations) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_APP_MARKET_HIDE_SEARCH_RESULT_RECOMMENDATIONS,
                PROP_KEY_APP_MARKET_HIDE_SEARCH_RESULT_RECOMMENDATIONS,
            ),
            settingsGlobalKey = SETTINGS_KEY_APP_MARKET_HIDE_SEARCH_RESULT_RECOMMENDATIONS,
        )
        syncScalarState(
            value = if (appMarketHideDetailRecommendations) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_APP_MARKET_HIDE_DETAIL_RECOMMENDATIONS,
                PROP_KEY_APP_MARKET_HIDE_DETAIL_RECOMMENDATIONS,
            ),
            settingsGlobalKey = SETTINGS_KEY_APP_MARKET_HIDE_DETAIL_RECOMMENDATIONS,
        )
        syncScalarState(
            value = if (fileManagerHideSecureAccessTip) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_FILE_MANAGER_HIDE_SECURE_ACCESS_TIP,
                PROP_FILE_MANAGER_HIDE_SECURE_ACCESS_TIP,
            ),
            settingsGlobalKey = SETTINGS_FILE_MANAGER_HIDE_SECURE_ACCESS_TIP,
        )
        syncScalarState(
            value = if (fileManagerNativePicker) "1" else "0",
            propertyKeys = listOf(PERSIST_PROP_FILE_MANAGER_NATIVE_PICKER, PROP_FILE_MANAGER_NATIVE_PICKER),
            settingsGlobalKey = SETTINGS_FILE_MANAGER_NATIVE_PICKER,
        )
        syncScalarState(
            value = if (athenaC17SwipeUpProtection) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_ATHENA_C17_SWIPE_UP_PROTECTION,
                PROP_KEY_ATHENA_C17_SWIPE_UP_PROTECTION,
            ),
            settingsGlobalKey = SETTINGS_KEY_ATHENA_C17_SWIPE_UP_PROTECTION,
        )
        syncScalarState(
            value = if (okGoogleHotwordCompatibility) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_OK_GOOGLE_HOTWORD_COMPATIBILITY,
                PROP_KEY_OK_GOOGLE_HOTWORD_COMPATIBILITY,
            ),
            settingsGlobalKey = SETTINGS_KEY_OK_GOOGLE_HOTWORD_COMPATIBILITY,
        )
        syncScalarState(
            value = if (launcherHideWidgetLabels) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_LAUNCHER_HIDE_WIDGET_LABELS,
                PROP_KEY_LAUNCHER_HIDE_WIDGET_LABELS,
            ),
            settingsGlobalKey = SETTINGS_KEY_LAUNCHER_HIDE_WIDGET_LABELS,
        )
        syncScalarState(
            value = launcherSearchBarMode.toString(),
            propertyKeys = listOf(
                PERSIST_PROP_KEY_LAUNCHER_SEARCH_BAR_MODE,
                PROP_KEY_LAUNCHER_SEARCH_BAR_MODE,
            ),
            settingsGlobalKey = SETTINGS_KEY_LAUNCHER_SEARCH_BAR_MODE,
        )
        syncScalarState(
            value = if (launcherSearchCompatibility) "1" else "0",
            propertyKeys = listOf(
                PERSIST_PROP_KEY_LAUNCHER_SEARCH_COMPATIBILITY,
                PROP_KEY_LAUNCHER_SEARCH_COMPATIBILITY,
            ),
            settingsGlobalKey = SETTINGS_KEY_LAUNCHER_SEARCH_COMPATIBILITY,
        )
        } finally {
            syncCommandBatch.remove()
        }
        return executeSyncCommands("LSP boot sync", batchedCommands)
    }

    fun syncReadableState(context: Context) {
        ModernXposedPreferenceSync.syncNow(context)
    }

    fun isNativeNotifyIconEnabledXposed(): Boolean {
        HookConfigSnapshot.boolean(KEY_NATIVE_NOTIFY_ICON, true)?.let { return it }
        readSystemPropertyToggle(PERSIST_PROP_KEY_NATIVE_NOTIFY_ICON)?.let { return it }
        readSystemPropertyToggle(PROP_KEY_NATIVE_NOTIFY_ICON)?.let { return it }
        readSettingsGlobalToggle(SETTINGS_KEY_NATIVE_NOTIFY_ICON)?.let { return it }
        readFlagFile(FLAG_FILE_PATH_NATIVE_NOTIFY_ICON)?.let { return it }
        readFlagFile(LEGACY_FLAG_FILE_PATH_NATIVE_NOTIFY_ICON)?.let { return it }
        return runCatching {
            val prefs = xposedPreferences
            prefs.getBoolean(KEY_NATIVE_NOTIFY_ICON, true)
        }.getOrDefault(true)
    }

    fun isExtremeRefresh165EnabledXposed(): Boolean {
        HookConfigSnapshot.boolean(KEY_EXTREME_REFRESH_165, false)?.let { return it }
        readSystemPropertyToggle(PERSIST_PROP_KEY_EXTREME_REFRESH_165)?.let { return it }
        readSystemPropertyToggle(PROP_KEY_EXTREME_REFRESH_165)?.let { return it }
        readSettingsGlobalToggle(SETTINGS_KEY_EXTREME_REFRESH_165)?.let { return it }
        readFlagFile(FLAG_FILE_PATH_EXTREME_REFRESH_165)?.let { return it }
        readFlagFile(LEGACY_FLAG_FILE_PATH_EXTREME_REFRESH_165)?.let { return it }
        return runCatching {
            val prefs = xposedPreferences
            prefs.getBoolean(KEY_EXTREME_REFRESH_165, false)
        }.getOrDefault(false)
    }

    fun isRecentTaskRadiusEnabledXposed(): Boolean {
        HookConfigSnapshot.boolean(KEY_RECENT_TASK_RADIUS, false)?.let { return it }
        readSystemPropertyToggle(PERSIST_PROP_KEY_RECENT_TASK_RADIUS)?.let { return it }
        readSystemPropertyToggle(PROP_KEY_RECENT_TASK_RADIUS)?.let { return it }
        readSettingsGlobalToggle(SETTINGS_KEY_RECENT_TASK_RADIUS)?.let { return it }
        readFlagFile(FLAG_FILE_PATH_RECENT_TASK_RADIUS)?.let { return it }
        readFlagFile(LEGACY_FLAG_FILE_PATH_RECENT_TASK_RADIUS)?.let { return it }
        return runCatching {
            val prefs = xposedPreferences
            prefs.getBoolean(KEY_RECENT_TASK_RADIUS, false)
        }.getOrDefault(false)
    }

    fun isAodEnhanceEnabledXposed(): Boolean {
        HookConfigSnapshot.boolean(KEY_AOD_ENHANCE, false)?.let { return it }
        readSystemPropertyToggle(PERSIST_PROP_KEY_AOD_ENHANCE)?.let { return it }
        readSystemPropertyToggle(PROP_KEY_AOD_ENHANCE)?.let { return it }
        readSettingsGlobalToggle(SETTINGS_KEY_AOD_ENHANCE)?.let { return it }
        readFlagFile(FLAG_FILE_PATH_AOD_ENHANCE)?.let { return it }
        readFlagFile(LEGACY_FLAG_FILE_PATH_AOD_ENHANCE)?.let { return it }
        return runCatching {
            val prefs = xposedPreferences
            prefs.getBoolean(KEY_AOD_ENHANCE, false)
        }.getOrDefault(false)
    }

    fun getAssistantPowerModeXposed(): Int {
        HookConfigSnapshot.int(KEY_ASSISTANT_POWER_MODE, DEFAULT_ASSISTANT_POWER_MODE)?.let {
            return it.sanitizeAssistantPowerMode()
        }
        readSystemPropertyValue(PERSIST_PROP_KEY_ASSISTANT_POWER_MODE)?.toIntOrNull()?.let {
            return it.sanitizeAssistantPowerMode()
        }
        readSystemPropertyValue(PROP_KEY_ASSISTANT_POWER_MODE)?.toIntOrNull()?.let {
            return it.sanitizeAssistantPowerMode()
        }
        readSettingsGlobalValue(SETTINGS_KEY_ASSISTANT_POWER_MODE)?.toIntOrNull()?.let {
            return it.sanitizeAssistantPowerMode()
        }
        return runCatching {
            val prefs = xposedPreferences
            prefs.getInt(KEY_ASSISTANT_POWER_MODE, DEFAULT_ASSISTANT_POWER_MODE)
        }.getOrDefault(DEFAULT_ASSISTANT_POWER_MODE).sanitizeAssistantPowerMode()
    }

    fun isAssistantGestureCircleEnabledXposed(): Boolean {
        HookConfigSnapshot.boolean(KEY_ASSISTANT_GESTURE_CIRCLE, false)?.let { return it }
        readSystemPropertyToggle(PERSIST_PROP_KEY_ASSISTANT_GESTURE_CIRCLE)?.let { return it }
        readSystemPropertyToggle(PROP_KEY_ASSISTANT_GESTURE_CIRCLE)?.let { return it }
        readSettingsGlobalToggle(SETTINGS_KEY_ASSISTANT_GESTURE_CIRCLE)?.let { return it }
        return runCatching {
            val prefs = xposedPreferences
            prefs.getBoolean(KEY_ASSISTANT_GESTURE_CIRCLE, false)
        }.getOrDefault(false)
    }

    fun isAssistantGestureCircleC17EnabledXposed(): Boolean {
        HookConfigSnapshot.boolean(KEY_ASSISTANT_GESTURE_CIRCLE_C17, false)?.let { return it }
        readSystemPropertyToggle(PERSIST_PROP_KEY_ASSISTANT_GESTURE_CIRCLE_C17)?.let { return it }
        readSystemPropertyToggle(PROP_KEY_ASSISTANT_GESTURE_CIRCLE_C17)?.let { return it }
        readSettingsGlobalToggle(SETTINGS_KEY_ASSISTANT_GESTURE_CIRCLE_C17)?.let { return it }
        return runCatching {
            val prefs = xposedPreferences
            prefs.getBoolean(KEY_ASSISTANT_GESTURE_CIRCLE_C17, false)
        }.getOrDefault(false)
    }

    fun isAssistantNativePowerEnabledXposed(): Boolean {
        HookConfigSnapshot.boolean(KEY_ASSISTANT_NATIVE_POWER, false)?.let { return it }
        readSystemPropertyToggle(PERSIST_PROP_KEY_ASSISTANT_NATIVE_POWER)?.let { return it }
        readSystemPropertyToggle(PROP_KEY_ASSISTANT_NATIVE_POWER)?.let { return it }
        readSettingsGlobalToggle(SETTINGS_KEY_ASSISTANT_NATIVE_POWER)?.let { return it }
        return runCatching {
            val prefs = xposedPreferences
            prefs.getBoolean(KEY_ASSISTANT_NATIVE_POWER, false)
        }.getOrDefault(false)
    }

    fun isAssistantInternationalPowerChordEnabledXposed(): Boolean {
        HookConfigSnapshot.boolean(
            KEY_ASSISTANT_INTERNATIONAL_POWER_CHORD,
            true,
        )?.let { return it }
        readSystemPropertyToggle(PERSIST_PROP_KEY_ASSISTANT_INTERNATIONAL_POWER_CHORD)
            ?.let { return it }
        readSystemPropertyToggle(PROP_KEY_ASSISTANT_INTERNATIONAL_POWER_CHORD)
            ?.let { return it }
        readSettingsGlobalToggle(SETTINGS_KEY_ASSISTANT_INTERNATIONAL_POWER_CHORD)
            ?.let { return it }
        return runCatching {
            val prefs = xposedPreferences
            prefs.getBoolean(KEY_ASSISTANT_INTERNATIONAL_POWER_CHORD, true)
        }.getOrDefault(true)
    }

    fun isAssistantNativeCircleEnabledXposed(): Boolean {
        HookConfigSnapshot.boolean(KEY_ASSISTANT_NATIVE_CIRCLE, false)?.let { return it }
        readSystemPropertyToggle(PERSIST_PROP_KEY_ASSISTANT_NATIVE_CIRCLE)?.let { return it }
        readSystemPropertyToggle(PROP_KEY_ASSISTANT_NATIVE_CIRCLE)?.let { return it }
        readSettingsGlobalToggle(SETTINGS_KEY_ASSISTANT_NATIVE_CIRCLE)?.let { return it }
        return runCatching {
            val prefs = xposedPreferences
            prefs.getBoolean(KEY_ASSISTANT_NATIVE_CIRCLE, false)
        }.getOrDefault(false)
    }

    fun getRecentTaskRadiusDpXposed(): Float {
        HookConfigSnapshot.int(KEY_RECENT_TASK_RADIUS_DP, DEFAULT_RECENT_TASK_RADIUS_DP)?.let {
            return FeatureSliderRules.normalizeRadius(it.toFloat())
        }
        readSystemPropertyValue(PERSIST_PROP_KEY_RECENT_TASK_RADIUS_DP)?.toFloatOrNull()?.let { return FeatureSliderRules.normalizeRadius(it) }
        readSystemPropertyValue(PROP_KEY_RECENT_TASK_RADIUS_DP)?.toFloatOrNull()?.let { return FeatureSliderRules.normalizeRadius(it) }
        readSettingsGlobalValue(SETTINGS_KEY_RECENT_TASK_RADIUS_DP)?.toFloatOrNull()?.let { return FeatureSliderRules.normalizeRadius(it) }
        return FeatureSliderRules.normalizeRadius(runCatching {
            val prefs = xposedPreferences
            prefs.getInt(KEY_RECENT_TASK_RADIUS_DP, DEFAULT_RECENT_TASK_RADIUS_DP).toFloat()
        }.getOrDefault(DEFAULT_RECENT_TASK_RADIUS_DP.toFloat()))
    }

    fun getAodInitDarkBrightnessXposed(): Int {
        HookConfigSnapshot.int(KEY_AOD_INIT_DARK_BRIGHTNESS, DEFAULT_AOD_INIT_DARK_BRIGHTNESS)?.let {
            return FeatureSliderRules.normalizeBrightness(it)
        }
        readSystemPropertyValue(PERSIST_PROP_KEY_AOD_INIT_DARK_BRIGHTNESS)?.toIntOrNull()?.let { return FeatureSliderRules.normalizeBrightness(it) }
        readSystemPropertyValue(PROP_KEY_AOD_INIT_DARK_BRIGHTNESS)?.toIntOrNull()?.let { return FeatureSliderRules.normalizeBrightness(it) }
        readSettingsGlobalValue(SETTINGS_KEY_AOD_INIT_DARK_BRIGHTNESS)?.toIntOrNull()?.let { return FeatureSliderRules.normalizeBrightness(it) }
        return FeatureSliderRules.normalizeBrightness(runCatching {
            val prefs = xposedPreferences
            prefs.getInt(KEY_AOD_INIT_DARK_BRIGHTNESS, DEFAULT_AOD_INIT_DARK_BRIGHTNESS)
        }.getOrDefault(DEFAULT_AOD_INIT_DARK_BRIGHTNESS))
    }

    fun getAodInitBrightBrightnessXposed(): Int {
        HookConfigSnapshot.int(KEY_AOD_INIT_BRIGHT_BRIGHTNESS, DEFAULT_AOD_INIT_BRIGHT_BRIGHTNESS)?.let {
            return FeatureSliderRules.normalizeBrightness(it)
        }
        readSystemPropertyValue(PERSIST_PROP_KEY_AOD_INIT_BRIGHT_BRIGHTNESS)?.toIntOrNull()?.let { return FeatureSliderRules.normalizeBrightness(it) }
        readSystemPropertyValue(PROP_KEY_AOD_INIT_BRIGHT_BRIGHTNESS)?.toIntOrNull()?.let { return FeatureSliderRules.normalizeBrightness(it) }
        readSettingsGlobalValue(SETTINGS_KEY_AOD_INIT_BRIGHT_BRIGHTNESS)?.toIntOrNull()?.let { return FeatureSliderRules.normalizeBrightness(it) }
        return FeatureSliderRules.normalizeBrightness(runCatching {
            val prefs = xposedPreferences
            prefs.getInt(KEY_AOD_INIT_BRIGHT_BRIGHTNESS, DEFAULT_AOD_INIT_BRIGHT_BRIGHTNESS)
        }.getOrDefault(DEFAULT_AOD_INIT_BRIGHT_BRIGHTNESS))
    }

    fun getAodRunningBrightnessMultiplierXposed(): Float {
        HookConfigSnapshot.float(
            KEY_AOD_RUNNING_BRIGHTNESS_MULTIPLIER,
            DEFAULT_AOD_RUNNING_BRIGHTNESS_MULTIPLIER,
        )?.let { return FeatureSliderRules.normalizeMultiplier(it) }
        readSystemPropertyValue(PERSIST_PROP_KEY_AOD_RUNNING_BRIGHTNESS_MULTIPLIER)?.toFloatOrNull()?.let { return FeatureSliderRules.normalizeMultiplier(it) }
        readSystemPropertyValue(PROP_KEY_AOD_RUNNING_BRIGHTNESS_MULTIPLIER)?.toFloatOrNull()?.let { return FeatureSliderRules.normalizeMultiplier(it) }
        readSettingsGlobalValue(SETTINGS_KEY_AOD_RUNNING_BRIGHTNESS_MULTIPLIER)?.toFloatOrNull()?.let { return FeatureSliderRules.normalizeMultiplier(it) }
        return FeatureSliderRules.normalizeMultiplier(runCatching {
            val prefs = xposedPreferences
            prefs.getFloat(
                KEY_AOD_RUNNING_BRIGHTNESS_MULTIPLIER,
                DEFAULT_AOD_RUNNING_BRIGHTNESS_MULTIPLIER
            )
        }.getOrDefault(DEFAULT_AOD_RUNNING_BRIGHTNESS_MULTIPLIER))
    }

    fun isAodPanoramicSupportEnabledXposed(): Boolean {
        HookConfigSnapshot.boolean(
            KEY_AOD_PANORAMIC_SUPPORT,
            DEFAULT_AOD_PANORAMIC_SUPPORT,
        )?.let { return it }
        readSystemPropertyToggle(PERSIST_PROP_KEY_AOD_PANORAMIC_SUPPORT)?.let { return it }
        readSystemPropertyToggle(PROP_KEY_AOD_PANORAMIC_SUPPORT)?.let { return it }
        readSettingsGlobalToggle(SETTINGS_KEY_AOD_PANORAMIC_SUPPORT)?.let { return it }
        return runCatching {
            val prefs = xposedPreferences
            prefs.getBoolean(KEY_AOD_PANORAMIC_SUPPORT, DEFAULT_AOD_PANORAMIC_SUPPORT)
        }.getOrDefault(DEFAULT_AOD_PANORAMIC_SUPPORT)
    }

    fun isAodSettingsSwitchEnabledXposed(): Boolean {
        HookConfigSnapshot.boolean(KEY_AOD_SETTINGS_SWITCH, DEFAULT_AOD_SETTINGS_SWITCH)?.let {
            return it
        }
        readSystemPropertyToggle(PERSIST_PROP_KEY_AOD_SETTINGS_SWITCH)?.let { return it }
        readSystemPropertyToggle(PROP_KEY_AOD_SETTINGS_SWITCH)?.let { return it }
        readSettingsGlobalToggle(SETTINGS_KEY_AOD_SETTINGS_SWITCH)?.let { return it }
        return runCatching {
            val prefs = xposedPreferences
            prefs.getBoolean(KEY_AOD_SETTINGS_SWITCH, DEFAULT_AOD_SETTINGS_SWITCH)
        }.getOrDefault(DEFAULT_AOD_SETTINGS_SWITCH)
    }

    fun isAodSingleClickBlockEnabledXposed(): Boolean {
        HookConfigSnapshot.boolean(
            KEY_AOD_SINGLE_CLICK_BLOCK,
            DEFAULT_AOD_SINGLE_CLICK_BLOCK,
        )?.let { return it }
        readSystemPropertyToggle(PERSIST_PROP_KEY_AOD_SINGLE_CLICK_BLOCK)?.let { return it }
        readSystemPropertyToggle(PROP_KEY_AOD_SINGLE_CLICK_BLOCK)?.let { return it }
        readSettingsGlobalToggle(SETTINGS_KEY_AOD_SINGLE_CLICK_BLOCK)?.let { return it }
        return runCatching {
            val prefs = xposedPreferences
            prefs.getBoolean(KEY_AOD_SINGLE_CLICK_BLOCK, DEFAULT_AOD_SINGLE_CLICK_BLOCK)
        }.getOrDefault(DEFAULT_AOD_SINGLE_CLICK_BLOCK)
    }

    fun isNativeNotificationBubblesEnabledXposed(): Boolean {
        HookConfigSnapshot.boolean(
            KEY_NATIVE_NOTIFICATION_BUBBLES,
            DEFAULT_NATIVE_NOTIFICATION_BUBBLES,
        )?.let { return it }
        readSystemPropertyToggle(PERSIST_PROP_KEY_NATIVE_NOTIFICATION_BUBBLES)?.let { return it }
        readSystemPropertyToggle(PROP_KEY_NATIVE_NOTIFICATION_BUBBLES)?.let { return it }
        readSettingsGlobalToggle(SETTINGS_KEY_NATIVE_NOTIFICATION_BUBBLES)?.let { return it }
        readFlagFile(FLAG_FILE_PATH_NATIVE_NOTIFICATION_BUBBLES)?.let { return it }
        readFlagFile(LEGACY_FLAG_FILE_PATH_NATIVE_NOTIFICATION_BUBBLES)?.let { return it }
        return runCatching {
            val prefs = xposedPreferences
            prefs.getBoolean(KEY_NATIVE_NOTIFICATION_BUBBLES, DEFAULT_NATIVE_NOTIFICATION_BUBBLES)
        }.getOrDefault(DEFAULT_NATIVE_NOTIFICATION_BUBBLES)
    }

    fun isSystemUiInternationalNetworkDisplayEnabledXposed(): Boolean {
        return readXposedBoolean(
            persistPropertyKey = PERSIST_PROP_KEY_SYSTEMUI_INTERNATIONAL_NETWORK_DISPLAY,
            propertyKey = PROP_KEY_SYSTEMUI_INTERNATIONAL_NETWORK_DISPLAY,
            settingsKey = SETTINGS_KEY_SYSTEMUI_INTERNATIONAL_NETWORK_DISPLAY,
            prefsKey = KEY_SYSTEMUI_INTERNATIONAL_NETWORK_DISPLAY,
            defaultValue = DEFAULT_SYSTEMUI_INTERNATIONAL_NETWORK_DISPLAY
        )
    }

    fun isSystemUiHideMobileRoamingIndicatorEnabledXposed(): Boolean {
        return readXposedBoolean(
            persistPropertyKey = PERSIST_PROP_KEY_SYSTEMUI_HIDE_MOBILE_ROAMING_INDICATOR,
            propertyKey = PROP_KEY_SYSTEMUI_HIDE_MOBILE_ROAMING_INDICATOR,
            settingsKey = SETTINGS_KEY_SYSTEMUI_HIDE_MOBILE_ROAMING_INDICATOR,
            prefsKey = KEY_SYSTEMUI_HIDE_MOBILE_ROAMING_INDICATOR,
            defaultValue = DEFAULT_SYSTEMUI_HIDE_MOBILE_ROAMING_INDICATOR
        )
    }

    fun isSystemUiHideNetworkActivityIndicatorEnabledXposed(): Boolean {
        return readXposedBoolean(
            persistPropertyKey = PERSIST_PROP_KEY_SYSTEMUI_HIDE_NETWORK_ACTIVITY_INDICATOR,
            propertyKey = PROP_KEY_SYSTEMUI_HIDE_NETWORK_ACTIVITY_INDICATOR,
            settingsKey = SETTINGS_KEY_SYSTEMUI_HIDE_NETWORK_ACTIVITY_INDICATOR,
            prefsKey = KEY_SYSTEMUI_HIDE_NETWORK_ACTIVITY_INDICATOR,
            defaultValue = DEFAULT_SYSTEMUI_HIDE_NETWORK_ACTIVITY_INDICATOR,
        )
    }

    fun isSystemUiNativePowerMenuEnabledXposed(): Boolean {
        return readXposedBoolean(
            persistPropertyKey = PERSIST_PROP_KEY_SYSTEMUI_NATIVE_POWER_MENU,
            propertyKey = PROP_KEY_SYSTEMUI_NATIVE_POWER_MENU,
            settingsKey = SETTINGS_KEY_SYSTEMUI_NATIVE_POWER_MENU,
            prefsKey = KEY_SYSTEMUI_NATIVE_POWER_MENU,
            defaultValue = DEFAULT_SYSTEMUI_NATIVE_POWER_MENU,
        )
    }

    fun isSystemUiRestoreC16NetworkIconOrderEnabledXposed(): Boolean {
        return readXposedBoolean(
            persistPropertyKey = PERSIST_PROP_KEY_SYSTEMUI_RESTORE_C16_NETWORK_ICON_ORDER,
            propertyKey = PROP_KEY_SYSTEMUI_RESTORE_C16_NETWORK_ICON_ORDER,
            settingsKey = SETTINGS_KEY_SYSTEMUI_RESTORE_C16_NETWORK_ICON_ORDER,
            prefsKey = KEY_SYSTEMUI_RESTORE_C16_NETWORK_ICON_ORDER,
            defaultValue = DEFAULT_SYSTEMUI_RESTORE_C16_NETWORK_ICON_ORDER,
        )
    }

    fun isSystemUiInternationalNotificationStyleEnabledXposed(): Boolean {
        return readXposedBoolean(
            persistPropertyKey = PERSIST_PROP_KEY_SYSTEMUI_INTERNATIONAL_NOTIFICATION_STYLE,
            propertyKey = PROP_KEY_SYSTEMUI_INTERNATIONAL_NOTIFICATION_STYLE,
            settingsKey = SETTINGS_KEY_SYSTEMUI_INTERNATIONAL_NOTIFICATION_STYLE,
            prefsKey = KEY_SYSTEMUI_INTERNATIONAL_NOTIFICATION_STYLE,
            defaultValue = DEFAULT_SYSTEMUI_INTERNATIONAL_NOTIFICATION_STYLE
        )
    }

    fun isSystemUiForceTonalSpotEnabledXposed(): Boolean {
        return readXposedBoolean(
            persistPropertyKey = PERSIST_PROP_KEY_SYSTEMUI_FORCE_TONAL_SPOT,
            propertyKey = PROP_KEY_SYSTEMUI_FORCE_TONAL_SPOT,
            settingsKey = SETTINGS_KEY_SYSTEMUI_FORCE_TONAL_SPOT,
            prefsKey = KEY_SYSTEMUI_FORCE_TONAL_SPOT,
            defaultValue = DEFAULT_SYSTEMUI_FORCE_TONAL_SPOT,
        )
    }

    fun getSystemUiMonetColorSpecModeXposed(): Int {
        HookConfigSnapshot.int(
            KEY_SYSTEMUI_MONET_COLOR_SPEC_MODE,
            DEFAULT_SYSTEMUI_MONET_COLOR_SPEC_MODE,
        )?.let { return it.sanitizeSystemUiMonetColorSpecMode() }
        readSystemPropertyValue(PERSIST_PROP_KEY_SYSTEMUI_MONET_COLOR_SPEC_MODE)
            ?.toIntOrNull()
            ?.let { return it.sanitizeSystemUiMonetColorSpecMode() }
        readSystemPropertyValue(PROP_KEY_SYSTEMUI_MONET_COLOR_SPEC_MODE)
            ?.toIntOrNull()
            ?.let { return it.sanitizeSystemUiMonetColorSpecMode() }
        readSettingsGlobalValue(SETTINGS_KEY_SYSTEMUI_MONET_COLOR_SPEC_MODE)
            ?.toIntOrNull()
            ?.let { return it.sanitizeSystemUiMonetColorSpecMode() }
        return runCatching {
            xposedPreferences.getInt(
                KEY_SYSTEMUI_MONET_COLOR_SPEC_MODE,
                DEFAULT_SYSTEMUI_MONET_COLOR_SPEC_MODE,
            )
        }.getOrDefault(DEFAULT_SYSTEMUI_MONET_COLOR_SPEC_MODE)
            .sanitizeSystemUiMonetColorSpecMode()
    }

    fun isSystemUiHideQsEditEnabledXposed(): Boolean {
        return readXposedBoolean(
            persistPropertyKey = PERSIST_PROP_KEY_SYSTEMUI_HIDE_QS_EDIT,
            propertyKey = PROP_KEY_SYSTEMUI_HIDE_QS_EDIT,
            settingsKey = SETTINGS_KEY_SYSTEMUI_HIDE_QS_EDIT,
            prefsKey = KEY_SYSTEMUI_HIDE_QS_EDIT,
            defaultValue = DEFAULT_SYSTEMUI_HIDE_QS_EDIT
        )
    }

    fun isSystemUiHideQsSettingsEnabledXposed(): Boolean {
        return readXposedBoolean(
            persistPropertyKey = PERSIST_PROP_KEY_SYSTEMUI_HIDE_QS_SETTINGS,
            propertyKey = PROP_KEY_SYSTEMUI_HIDE_QS_SETTINGS,
            settingsKey = SETTINGS_KEY_SYSTEMUI_HIDE_QS_SETTINGS,
            prefsKey = KEY_SYSTEMUI_HIDE_QS_SETTINGS,
            defaultValue = DEFAULT_SYSTEMUI_HIDE_QS_SETTINGS
        )
    }

    fun isSystemUiHideQsTopCarrierEnabledXposed(): Boolean {
        return readXposedBoolean(
            persistPropertyKey = PERSIST_PROP_KEY_SYSTEMUI_HIDE_QS_TOP_CARRIER,
            propertyKey = PROP_KEY_SYSTEMUI_HIDE_QS_TOP_CARRIER,
            settingsKey = SETTINGS_KEY_SYSTEMUI_HIDE_QS_TOP_CARRIER,
            prefsKey = KEY_SYSTEMUI_HIDE_QS_TOP_CARRIER,
            defaultValue = DEFAULT_SYSTEMUI_HIDE_QS_TOP_CARRIER
        )
    }

    fun isSystemUiHideQsMoreEnabledXposed(): Boolean {
        return readXposedBoolean(
            persistPropertyKey = PERSIST_PROP_KEY_SYSTEMUI_HIDE_QS_MORE,
            propertyKey = PROP_KEY_SYSTEMUI_HIDE_QS_MORE,
            settingsKey = SETTINGS_KEY_SYSTEMUI_HIDE_QS_MORE,
            prefsKey = KEY_SYSTEMUI_HIDE_QS_MORE,
            defaultValue = DEFAULT_SYSTEMUI_HIDE_QS_MORE
        )
    }

    fun isSystemUiForceNativeClipboardOverlayEnabledXposed(): Boolean {
        return readXposedBoolean(
            persistPropertyKey = PERSIST_PROP_KEY_SYSTEMUI_FORCE_NATIVE_CLIPBOARD_OVERLAY,
            propertyKey = PROP_KEY_SYSTEMUI_FORCE_NATIVE_CLIPBOARD_OVERLAY,
            settingsKey = SETTINGS_KEY_SYSTEMUI_FORCE_NATIVE_CLIPBOARD_OVERLAY,
            prefsKey = KEY_SYSTEMUI_FORCE_NATIVE_CLIPBOARD_OVERLAY,
            defaultValue = DEFAULT_SYSTEMUI_FORCE_NATIVE_CLIPBOARD_OVERLAY
        )
    }

    fun isSettingsForceGoogleEntryEnabledXposed(): Boolean {
        return readXposedBoolean(
            persistPropertyKey = PERSIST_PROP_KEY_SETTINGS_FORCE_GOOGLE_ENTRY,
            propertyKey = PROP_KEY_SETTINGS_FORCE_GOOGLE_ENTRY,
            settingsKey = SETTINGS_KEY_SETTINGS_FORCE_GOOGLE_ENTRY,
            prefsKey = KEY_SETTINGS_FORCE_GOOGLE_ENTRY,
            defaultValue = DEFAULT_SETTINGS_FORCE_GOOGLE_ENTRY
        )
    }

    fun isSettingsForceAppAutoStartEnabledXposed(): Boolean {
        return isSettingsInternationalEnabledXposed() && readXposedBoolean(
            persistPropertyKey = PERSIST_PROP_KEY_SETTINGS_FORCE_APP_AUTO_START,
            propertyKey = PROP_KEY_SETTINGS_FORCE_APP_AUTO_START,
            settingsKey = SETTINGS_KEY_SETTINGS_FORCE_APP_AUTO_START,
            prefsKey = KEY_SETTINGS_FORCE_APP_AUTO_START,
            defaultValue = DEFAULT_SETTINGS_FORCE_APP_AUTO_START
        )
    }

    fun isSettingsInternationalWalletEnabledXposed(): Boolean {
        return isSettingsInternationalEnabledXposed() && readXposedBoolean(
            persistPropertyKey = PERSIST_PROP_KEY_SETTINGS_INTERNATIONAL_WALLET,
            propertyKey = PROP_KEY_SETTINGS_INTERNATIONAL_WALLET,
            settingsKey = SETTINGS_KEY_SETTINGS_INTERNATIONAL_WALLET,
            prefsKey = KEY_SETTINGS_INTERNATIONAL_WALLET,
            defaultValue = DEFAULT_SETTINGS_INTERNATIONAL_WALLET
        )
    }

    fun isSettingsRestoreDomesticAboutDeviceEnabledXposed(): Boolean {
        return isSettingsInternationalEnabledXposed() && readXposedBoolean(
            persistPropertyKey = PERSIST_PROP_KEY_SETTINGS_RESTORE_DOMESTIC_ABOUT_DEVICE,
            propertyKey = PROP_KEY_SETTINGS_RESTORE_DOMESTIC_ABOUT_DEVICE,
            settingsKey = SETTINGS_KEY_SETTINGS_RESTORE_DOMESTIC_ABOUT_DEVICE,
            prefsKey = KEY_SETTINGS_RESTORE_DOMESTIC_ABOUT_DEVICE,
            defaultValue = DEFAULT_SETTINGS_RESTORE_DOMESTIC_ABOUT_DEVICE,
        )
    }

    fun isSettingsRestoreDomesticAuxiliaryFunctionsEnabledXposed(): Boolean {
        return isSettingsInternationalEnabledXposed() && readXposedBoolean(
            persistPropertyKey = PERSIST_PROP_KEY_SETTINGS_RESTORE_DOMESTIC_AUXILIARY_FUNCTIONS,
            propertyKey = PROP_KEY_SETTINGS_RESTORE_DOMESTIC_AUXILIARY_FUNCTIONS,
            settingsKey = SETTINGS_KEY_SETTINGS_RESTORE_DOMESTIC_AUXILIARY_FUNCTIONS,
            prefsKey = KEY_SETTINGS_RESTORE_DOMESTIC_AUXILIARY_FUNCTIONS,
            defaultValue = DEFAULT_SETTINGS_RESTORE_DOMESTIC_AUXILIARY_FUNCTIONS,
        )
    }

    fun isSettingsSkipSpecialPermissionRiskConfirmEnabledXposed(): Boolean = readXposedBooleanWithFallback(
        persistPropertyKey = PERSIST_PROP_KEY_SETTINGS_SKIP_SPECIAL_PERMISSION_RISK_CONFIRM,
        propertyKey = PROP_KEY_SETTINGS_SKIP_SPECIAL_PERMISSION_RISK_CONFIRM,
        settingsKey = SETTINGS_KEY_SETTINGS_SKIP_SPECIAL_PERMISSION_RISK_CONFIRM,
        prefsKey = KEY_SETTINGS_SKIP_SPECIAL_PERMISSION_RISK_CONFIRM,
        defaultValue = DEFAULT_SETTINGS_SKIP_SPECIAL_PERMISSION_RISK_CONFIRM,
    )

    fun isSettingsRestoreAppOpenButtonEnabledXposed(): Boolean = readXposedBooleanWithFallback(
        persistPropertyKey = PERSIST_PROP_KEY_SETTINGS_RESTORE_APP_OPEN_BUTTON,
        propertyKey = PROP_KEY_SETTINGS_RESTORE_APP_OPEN_BUTTON,
        settingsKey = SETTINGS_KEY_SETTINGS_RESTORE_APP_OPEN_BUTTON,
        prefsKey = KEY_SETTINGS_RESTORE_APP_OPEN_BUTTON,
        defaultValue = DEFAULT_SETTINGS_RESTORE_APP_OPEN_BUTTON,
    )

    fun isSettingsC15AboutLayoutEnabledXposed(): Boolean {
        return readXposedBooleanWithFallback(
            persistPropertyKey = PERSIST_PROP_KEY_SETTINGS_C15_ABOUT_LAYOUT,
            propertyKey = PROP_KEY_SETTINGS_C15_ABOUT_LAYOUT,
            settingsKey = SETTINGS_KEY_SETTINGS_C15_ABOUT_LAYOUT,
            prefsKey = KEY_SETTINGS_C15_ABOUT_LAYOUT,
            defaultValue = DEFAULT_SETTINGS_C15_ABOUT_LAYOUT,
        )
    }

    fun isWallpapersRedOneEntryEnabledXposed(): Boolean = readXposedBooleanWithFallback(
        persistPropertyKey = PERSIST_PROP_KEY_WALLPAPERS_RED_ONE_ENTRY,
        propertyKey = PROP_KEY_WALLPAPERS_RED_ONE_ENTRY,
        settingsKey = SETTINGS_KEY_WALLPAPERS_RED_ONE_ENTRY,
        prefsKey = KEY_WALLPAPERS_RED_ONE_ENTRY,
        defaultValue = DEFAULT_WALLPAPERS_RED_ONE_ENTRY,
    )

    fun isSettingsRefreshRateUnlockedXposed(): Boolean {
        return readXposedBooleanWithFallback(
            persistPropertyKey = PERSIST_PROP_KEY_SETTINGS_UNLOCK_REFRESH_RATE,
            propertyKey = PROP_KEY_SETTINGS_UNLOCK_REFRESH_RATE,
            settingsKey = SETTINGS_KEY_SETTINGS_UNLOCK_REFRESH_RATE,
            prefsKey = KEY_SETTINGS_UNLOCK_REFRESH_RATE,
            defaultValue = DEFAULT_SETTINGS_UNLOCK_REFRESH_RATE,
        )
    }

    fun isSettingsForceGlobalExtremeRefreshRateEnabledXposed(): Boolean {
        return readXposedBooleanWithFallback(
            persistPropertyKey = PERSIST_PROP_KEY_SETTINGS_FORCE_GLOBAL_EXTREME_REFRESH_RATE,
            propertyKey = PROP_KEY_SETTINGS_FORCE_GLOBAL_EXTREME_REFRESH_RATE,
            settingsKey = SETTINGS_KEY_SETTINGS_FORCE_GLOBAL_EXTREME_REFRESH_RATE,
            prefsKey = KEY_SETTINGS_FORCE_GLOBAL_EXTREME_REFRESH_RATE,
            defaultValue = DEFAULT_SETTINGS_FORCE_GLOBAL_EXTREME_REFRESH_RATE,
        )
    }

    fun isSettingsRestoreSmartLockEnabledXposed(): Boolean {
        return isSettingsInternationalEnabledXposed() && readXposedBoolean(
            persistPropertyKey = PERSIST_PROP_KEY_SETTINGS_RESTORE_SMART_LOCK,
            propertyKey = PROP_KEY_SETTINGS_RESTORE_SMART_LOCK,
            settingsKey = SETTINGS_KEY_SETTINGS_RESTORE_SMART_LOCK,
            prefsKey = KEY_SETTINGS_RESTORE_SMART_LOCK,
            defaultValue = DEFAULT_SETTINGS_RESTORE_SMART_LOCK,
        )
    }

    fun isSettingsInternationalEnabledXposed(): Boolean {
        return readXposedBoolean(
            persistPropertyKey = PERSIST_PROP_KEY_SETTINGS_INTERNATIONAL,
            propertyKey = PROP_KEY_SETTINGS_INTERNATIONAL,
            settingsKey = SETTINGS_KEY_SETTINGS_INTERNATIONAL,
            prefsKey = KEY_SETTINGS_INTERNATIONAL,
            defaultValue = DEFAULT_SETTINGS_INTERNATIONAL
        )
    }

    fun isGmsRegionRestrictionBypassEnabledXposed(): Boolean {
        return readXposedBoolean(
            persistPropertyKey = PERSIST_PROP_KEY_GMS_REGION_RESTRICTION_BYPASS,
            propertyKey = PROP_KEY_GMS_REGION_RESTRICTION_BYPASS,
            settingsKey = SETTINGS_KEY_GMS_REGION_RESTRICTION_BYPASS,
            prefsKey = KEY_GMS_REGION_RESTRICTION_BYPASS,
            defaultValue = DEFAULT_GMS_REGION_RESTRICTION_BYPASS,
        )
    }

    fun isEsimRegionRestrictionBypassEnabledXposed(): Boolean {
        return readXposedBoolean(
            persistPropertyKey = PERSIST_PROP_KEY_ESIM_REGION_RESTRICTION_BYPASS,
            propertyKey = PROP_KEY_ESIM_REGION_RESTRICTION_BYPASS,
            settingsKey = SETTINGS_KEY_ESIM_REGION_RESTRICTION_BYPASS,
            prefsKey = KEY_ESIM_REGION_RESTRICTION_BYPASS,
            defaultValue = DEFAULT_ESIM_REGION_RESTRICTION_BYPASS,
        )
    }

    fun isEsimConfirmationCodePromptEnabledXposed(): Boolean {
        return readXposedBoolean(
            persistPropertyKey = PERSIST_PROP_KEY_ESIM_CONFIRMATION_CODE_PROMPT,
            propertyKey = PROP_KEY_ESIM_CONFIRMATION_CODE_PROMPT,
            settingsKey = SETTINGS_KEY_ESIM_CONFIRMATION_CODE_PROMPT,
            prefsKey = KEY_ESIM_CONFIRMATION_CODE_PROMPT,
            // Older builds coupled the prompt to region bypass. Use that value only as the
            // migration default; once this setting is written it is fully independent.
            defaultValue = isEsimRegionRestrictionBypassEnabledXposed(),
        )
    }

    fun isEsimRegionRestrictionOverrideEnabledXposed(): Boolean {
        return readXposedBoolean(
            persistPropertyKey = PERSIST_PROP_KEY_ESIM_REGION_RESTRICTION_OVERRIDE,
            propertyKey = PROP_KEY_ESIM_REGION_RESTRICTION_OVERRIDE,
            settingsKey = SETTINGS_KEY_ESIM_REGION_RESTRICTION_OVERRIDE,
            prefsKey = KEY_ESIM_REGION_RESTRICTION_OVERRIDE,
            defaultValue = DEFAULT_ESIM_REGION_RESTRICTION_OVERRIDE,
        )
    }

    fun isEsimProfileLimitBypassEnabledXposed(): Boolean {
        return readXposedBoolean(
            persistPropertyKey = PERSIST_PROP_KEY_ESIM_PROFILE_LIMIT_BYPASS,
            propertyKey = PROP_KEY_ESIM_PROFILE_LIMIT_BYPASS,
            settingsKey = SETTINGS_KEY_ESIM_PROFILE_LIMIT_BYPASS,
            prefsKey = KEY_ESIM_PROFILE_LIMIT_BYPASS,
            defaultValue = DEFAULT_ESIM_PROFILE_LIMIT_BYPASS,
        )
    }

    fun isMobileNetworkHideAiLinkBoostEnabledXposed(): Boolean =
        readXposedBoolean(
            persistPropertyKey = PERSIST_PROP_KEY_MOBILE_NETWORK_HIDE_AI_LINK_BOOST,
            propertyKey = PROP_KEY_MOBILE_NETWORK_HIDE_AI_LINK_BOOST,
            settingsKey = SETTINGS_KEY_MOBILE_NETWORK_HIDE_AI_LINK_BOOST,
            prefsKey = KEY_MOBILE_NETWORK_HIDE_AI_LINK_BOOST,
            defaultValue = DEFAULT_MOBILE_NETWORK_HIDE_AI_LINK_BOOST,
        )

    fun isMobileNetworkHideRoamingServiceEnabledXposed(): Boolean =
        readXposedBoolean(
            persistPropertyKey = PERSIST_PROP_KEY_MOBILE_NETWORK_HIDE_ROAMING_SERVICE,
            propertyKey = PROP_KEY_MOBILE_NETWORK_HIDE_ROAMING_SERVICE,
            settingsKey = SETTINGS_KEY_MOBILE_NETWORK_HIDE_ROAMING_SERVICE,
            prefsKey = KEY_MOBILE_NETWORK_HIDE_ROAMING_SERVICE,
            defaultValue = DEFAULT_MOBILE_NETWORK_HIDE_ROAMING_SERVICE,
        )

    fun isMobileNetworkHideHighDataSimCardEnabledXposed(): Boolean =
        readXposedBoolean(
            persistPropertyKey = PERSIST_PROP_KEY_MOBILE_NETWORK_HIDE_HIGH_DATA_SIM_CARD,
            propertyKey = PROP_KEY_MOBILE_NETWORK_HIDE_HIGH_DATA_SIM_CARD,
            settingsKey = SETTINGS_KEY_MOBILE_NETWORK_HIDE_HIGH_DATA_SIM_CARD,
            prefsKey = KEY_MOBILE_NETWORK_HIDE_HIGH_DATA_SIM_CARD,
            defaultValue = DEFAULT_MOBILE_NETWORK_HIDE_HIGH_DATA_SIM_CARD,
        )

    fun isMobileNetworkHideSmartCloudAccelerationEnabledXposed(): Boolean =
        readXposedBoolean(
            persistPropertyKey =
                PERSIST_PROP_KEY_MOBILE_NETWORK_HIDE_SMART_CLOUD_ACCELERATION,
            propertyKey = PROP_KEY_MOBILE_NETWORK_HIDE_SMART_CLOUD_ACCELERATION,
            settingsKey = SETTINGS_KEY_MOBILE_NETWORK_HIDE_SMART_CLOUD_ACCELERATION,
            prefsKey = KEY_MOBILE_NETWORK_HIDE_SMART_CLOUD_ACCELERATION,
            defaultValue = DEFAULT_MOBILE_NETWORK_HIDE_SMART_CLOUD_ACCELERATION,
        )

    fun isMobileNetworkHidePhoneNumberEnabledXposed(): Boolean =
        readXposedBoolean(
            persistPropertyKey = PERSIST_PROP_KEY_MOBILE_NETWORK_HIDE_PHONE_NUMBER,
            propertyKey = PROP_KEY_MOBILE_NETWORK_HIDE_PHONE_NUMBER,
            settingsKey = SETTINGS_KEY_MOBILE_NETWORK_HIDE_PHONE_NUMBER,
            prefsKey = KEY_MOBILE_NETWORK_HIDE_PHONE_NUMBER,
            defaultValue = DEFAULT_MOBILE_NETWORK_HIDE_PHONE_NUMBER,
        )

    fun isMobileNetworkForceCarrierOptionsEnabledXposed(): Boolean =
        readXposedBoolean(
            persistPropertyKey = PERSIST_PROP_KEY_MOBILE_NETWORK_FORCE_CARRIER_OPTIONS,
            propertyKey = PROP_KEY_MOBILE_NETWORK_FORCE_CARRIER_OPTIONS,
            settingsKey = SETTINGS_KEY_MOBILE_NETWORK_FORCE_CARRIER_OPTIONS,
            prefsKey = KEY_MOBILE_NETWORK_FORCE_CARRIER_OPTIONS,
            defaultValue = DEFAULT_MOBILE_NETWORK_FORCE_CARRIER_OPTIONS,
        )

    fun isAppMarketRegionRestrictionBypassEnabledXposed(): Boolean =
        readXposedBoolean(
            persistPropertyKey = PERSIST_PROP_KEY_APP_MARKET_REGION_RESTRICTION_BYPASS,
            propertyKey = PROP_KEY_APP_MARKET_REGION_RESTRICTION_BYPASS,
            settingsKey = SETTINGS_KEY_APP_MARKET_REGION_RESTRICTION_BYPASS,
            prefsKey = KEY_APP_MARKET_REGION_RESTRICTION_BYPASS,
            defaultValue = DEFAULT_APP_MARKET_REGION_RESTRICTION_BYPASS,
        )

    fun isAppMarketRemoveSplashRecommendationsEnabledXposed(): Boolean =
        readXposedBoolean(
            persistPropertyKey = PERSIST_PROP_KEY_APP_MARKET_REMOVE_SPLASH_RECOMMENDATIONS,
            propertyKey = PROP_KEY_APP_MARKET_REMOVE_SPLASH_RECOMMENDATIONS,
            settingsKey = SETTINGS_KEY_APP_MARKET_REMOVE_SPLASH_RECOMMENDATIONS,
            prefsKey = KEY_APP_MARKET_REMOVE_SPLASH_RECOMMENDATIONS,
            defaultValue = DEFAULT_APP_MARKET_REMOVE_SPLASH_RECOMMENDATIONS,
        )

    fun isAppMarketRemoveUpdateDownloadRecommendationsEnabledXposed(): Boolean =
        readXposedBoolean(
            persistPropertyKey =
                PERSIST_PROP_KEY_APP_MARKET_REMOVE_UPDATE_DOWNLOAD_RECOMMENDATIONS,
            propertyKey = PROP_KEY_APP_MARKET_REMOVE_UPDATE_DOWNLOAD_RECOMMENDATIONS,
            settingsKey = SETTINGS_KEY_APP_MARKET_REMOVE_UPDATE_DOWNLOAD_RECOMMENDATIONS,
            prefsKey = KEY_APP_MARKET_REMOVE_UPDATE_DOWNLOAD_RECOMMENDATIONS,
            defaultValue = DEFAULT_APP_MARKET_REMOVE_UPDATE_DOWNLOAD_RECOMMENDATIONS,
        )

    fun isAppMarketRemoveMineRecommendationsEnabledXposed(): Boolean =
        readXposedBoolean(
            persistPropertyKey = PERSIST_PROP_KEY_APP_MARKET_REMOVE_MINE_RECOMMENDATIONS,
            propertyKey = PROP_KEY_APP_MARKET_REMOVE_MINE_RECOMMENDATIONS,
            settingsKey = SETTINGS_KEY_APP_MARKET_REMOVE_MINE_RECOMMENDATIONS,
            prefsKey = KEY_APP_MARKET_REMOVE_MINE_RECOMMENDATIONS,
            defaultValue = DEFAULT_APP_MARKET_REMOVE_MINE_RECOMMENDATIONS,
        )

    fun isAppMarketHideSearchHomeRecommendationsEnabledXposed(): Boolean =
        readXposedBoolean(
            persistPropertyKey = PERSIST_PROP_KEY_APP_MARKET_HIDE_SEARCH_HOME_RECOMMENDATIONS,
            propertyKey = PROP_KEY_APP_MARKET_HIDE_SEARCH_HOME_RECOMMENDATIONS,
            settingsKey = SETTINGS_KEY_APP_MARKET_HIDE_SEARCH_HOME_RECOMMENDATIONS,
            prefsKey = KEY_APP_MARKET_HIDE_SEARCH_HOME_RECOMMENDATIONS,
            defaultValue = DEFAULT_APP_MARKET_HIDE_SEARCH_HOME_RECOMMENDATIONS,
        )

    fun isAppMarketHideSearchResultRecommendationsEnabledXposed(): Boolean =
        readXposedBooleanWithFallback(
            persistPropertyKey = PERSIST_PROP_KEY_APP_MARKET_HIDE_SEARCH_RESULT_RECOMMENDATIONS,
            propertyKey = PROP_KEY_APP_MARKET_HIDE_SEARCH_RESULT_RECOMMENDATIONS,
            settingsKey = SETTINGS_KEY_APP_MARKET_HIDE_SEARCH_RESULT_RECOMMENDATIONS,
            prefsKey = KEY_APP_MARKET_HIDE_SEARCH_RESULT_RECOMMENDATIONS,
            defaultValue = DEFAULT_APP_MARKET_HIDE_SEARCH_RESULT_RECOMMENDATIONS,
        )

    fun isAppMarketHideDetailRecommendationsEnabledXposed(): Boolean =
        readXposedBooleanWithFallback(
            persistPropertyKey = PERSIST_PROP_KEY_APP_MARKET_HIDE_DETAIL_RECOMMENDATIONS,
            propertyKey = PROP_KEY_APP_MARKET_HIDE_DETAIL_RECOMMENDATIONS,
            settingsKey = SETTINGS_KEY_APP_MARKET_HIDE_DETAIL_RECOMMENDATIONS,
            prefsKey = KEY_APP_MARKET_HIDE_DETAIL_RECOMMENDATIONS,
            defaultValue = DEFAULT_APP_MARKET_HIDE_DETAIL_RECOMMENDATIONS,
        )

    fun isAthenaC17SwipeUpProtectionEnabledXposed(): Boolean {
        return readXposedBoolean(
            persistPropertyKey = PERSIST_PROP_KEY_ATHENA_C17_SWIPE_UP_PROTECTION,
            propertyKey = PROP_KEY_ATHENA_C17_SWIPE_UP_PROTECTION,
            settingsKey = SETTINGS_KEY_ATHENA_C17_SWIPE_UP_PROTECTION,
            prefsKey = KEY_ATHENA_C17_SWIPE_UP_PROTECTION,
            defaultValue = DEFAULT_ATHENA_C17_SWIPE_UP_PROTECTION,
        )
    }

    fun isOkGoogleHotwordCompatibilityEnabledXposed(): Boolean {
        return readXposedBoolean(
            persistPropertyKey = PERSIST_PROP_KEY_OK_GOOGLE_HOTWORD_COMPATIBILITY,
            propertyKey = PROP_KEY_OK_GOOGLE_HOTWORD_COMPATIBILITY,
            settingsKey = SETTINGS_KEY_OK_GOOGLE_HOTWORD_COMPATIBILITY,
            prefsKey = KEY_OK_GOOGLE_HOTWORD_COMPATIBILITY,
            defaultValue = DEFAULT_OK_GOOGLE_HOTWORD_COMPATIBILITY,
        )
    }

    fun isLauncherHideWidgetLabelsEnabledXposed(): Boolean {
        return readXposedBoolean(
            persistPropertyKey = PERSIST_PROP_KEY_LAUNCHER_HIDE_WIDGET_LABELS,
            propertyKey = PROP_KEY_LAUNCHER_HIDE_WIDGET_LABELS,
            settingsKey = SETTINGS_KEY_LAUNCHER_HIDE_WIDGET_LABELS,
            prefsKey = KEY_LAUNCHER_HIDE_WIDGET_LABELS,
            defaultValue = DEFAULT_LAUNCHER_HIDE_WIDGET_LABELS,
        )
    }

    fun getLauncherSearchBarModeXposed(): Int {
        HookConfigSnapshot.int(KEY_LAUNCHER_SEARCH_BAR_MODE, Int.MIN_VALUE)
            ?.takeIf { it != Int.MIN_VALUE }
            ?.let { return it.sanitizeLauncherSearchBarMode() }
        readSystemPropertyValue(PERSIST_PROP_KEY_LAUNCHER_SEARCH_BAR_MODE)?.toIntOrNull()?.let {
            return it.sanitizeLauncherSearchBarMode()
        }
        readSystemPropertyValue(PROP_KEY_LAUNCHER_SEARCH_BAR_MODE)?.toIntOrNull()?.let {
            return it.sanitizeLauncherSearchBarMode()
        }
        readSettingsGlobalValue(SETTINGS_KEY_LAUNCHER_SEARCH_BAR_MODE)?.toIntOrNull()?.let {
            return it.sanitizeLauncherSearchBarMode()
        }
        return runCatching {
            xposedPreferences.getInt(
                KEY_LAUNCHER_SEARCH_BAR_MODE,
                DEFAULT_LAUNCHER_SEARCH_BAR_MODE,
            )
        }.getOrDefault(DEFAULT_LAUNCHER_SEARCH_BAR_MODE).sanitizeLauncherSearchBarMode()
    }

    fun isLauncherSearchCompatibilityEnabledXposed(): Boolean = readXposedBoolean(
        persistPropertyKey = PERSIST_PROP_KEY_LAUNCHER_SEARCH_COMPATIBILITY,
        propertyKey = PROP_KEY_LAUNCHER_SEARCH_COMPATIBILITY,
        settingsKey = SETTINGS_KEY_LAUNCHER_SEARCH_COMPATIBILITY,
        prefsKey = KEY_LAUNCHER_SEARCH_COMPATIBILITY,
        defaultValue = false,
    )

    private fun setSyncedBooleanPreference(
        context: Context,
        prefsKey: String,
        enabled: Boolean,
        propertyKeys: List<String>,
        settingsGlobalKey: String
    ) {
        if (!prefs(context).edit().putBoolean(prefsKey, enabled).commitOrReport()) return
        syncReadableState(context)
        syncScalarState(
            value = if (enabled) "1" else "0",
            propertyKeys = propertyKeys,
            settingsGlobalKey = settingsGlobalKey
        )
    }

    private fun readTextFileValue(filePath: String): String? {
        return runCatching {
            val file = File(filePath)
            if (!file.exists()) return@runCatching null
            file.readText().trim().takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private fun getStringSet(context: Context, key: String): Set<String> {
        return prefs(context).getStringSet(key, emptySet()).orEmpty()
    }

    private fun readXposedString(key: String, defaultValue: String): String? {
        HookConfigSnapshot.string(key, defaultValue)?.let { return it }
        return runCatching {
            val prefs = xposedPreferences
            prefs.getString(key, defaultValue)
        }.getOrDefault(defaultValue)
    }

    private fun readXposedStringSet(key: String): Set<String> {
        HookConfigSnapshot.stringSet(key, emptySet())?.let { return it }
        return runCatching {
            val prefs = xposedPreferences
            prefs.getStringSet(key, emptySet()).orEmpty()
        }.getOrDefault(emptySet())
    }

    private fun readXposedBoolean(
        persistPropertyKey: String,
        propertyKey: String,
        settingsKey: String,
        prefsKey: String,
        defaultValue: Boolean
    ): Boolean {
        HookConfigSnapshot.boolean(prefsKey, defaultValue)?.let { return it }
        readSystemPropertyToggle(persistPropertyKey)?.let { return it }
        readSystemPropertyToggle(propertyKey)?.let { return it }
        readSettingsGlobalToggle(settingsKey)?.let { return it }
        return runCatching {
            val prefs = xposedPreferences
            prefs.getBoolean(prefsKey, defaultValue)
        }.getOrDefault(defaultValue)
    }

    private fun readXposedBooleanWithFallback(
        persistPropertyKey: String,
        propertyKey: String,
        settingsKey: String,
        prefsKey: String,
        defaultValue: Boolean,
    ): Boolean {
        HookConfigSnapshot.boolean(prefsKey, defaultValue)?.let { return it }
        readSystemPropertyToggle(persistPropertyKey)?.let { return it }
        readSystemPropertyToggle(propertyKey)?.let { return it }
        readSettingsGlobalToggle(settingsKey)?.let { return it }
        return runCatching {
            val prefs = xposedPreferences
            prefs.getBoolean(prefsKey, defaultValue)
        }.getOrDefault(defaultValue)
    }

    private fun Int.sanitizeAssistantPowerMode(): Int {
        return when (this) {
            ASSISTANT_POWER_MODE_NONE,
            ASSISTANT_POWER_MODE_SYSTEM_DEFAULT -> this
            else -> DEFAULT_ASSISTANT_POWER_MODE
        }
    }

    private fun Int.sanitizeLauncherSearchBarMode(): Int {
        return when (this) {
            LAUNCHER_SEARCH_BAR_MODE_OFF,
            LAUNCHER_SEARCH_BAR_MODE_INTERNATIONAL,
            LAUNCHER_SEARCH_BAR_MODE_CHINA -> this
            else -> DEFAULT_LAUNCHER_SEARCH_BAR_MODE
        }
    }

    private fun Int.sanitizeSystemUiMonetColorSpecMode(): Int {
        return when (this) {
            SYSTEMUI_MONET_COLOR_SPEC_OFF,
            SYSTEMUI_MONET_COLOR_SPEC_2025,
            SYSTEMUI_MONET_COLOR_SPEC_2021 -> this
            else -> DEFAULT_SYSTEMUI_MONET_COLOR_SPEC_MODE
        }
    }

    private fun syncFlagState(
        enabled: Boolean,
        propertyKeys: List<String>,
        settingsGlobalKey: String,
        flagFilePath: String
    ) {
        val value = if (enabled) "1" else "0"
        val legacyFlagPath = when (flagFilePath) {
            FLAG_FILE_PATH_NATIVE_NOTIFY_ICON -> LEGACY_FLAG_FILE_PATH_NATIVE_NOTIFY_ICON
            FLAG_FILE_PATH_EXTREME_REFRESH_165 -> LEGACY_FLAG_FILE_PATH_EXTREME_REFRESH_165
            FLAG_FILE_PATH_RECENT_TASK_RADIUS -> LEGACY_FLAG_FILE_PATH_RECENT_TASK_RADIUS
            FLAG_FILE_PATH_AOD_ENHANCE -> LEGACY_FLAG_FILE_PATH_AOD_ENHANCE
            FLAG_FILE_PATH_NATIVE_NOTIFICATION_BUBBLES -> LEGACY_FLAG_FILE_PATH_NATIVE_NOTIFICATION_BUBBLES
            else -> null
        }
        runCatching {
            val directCommands = propertyKeys.map { key -> "setprop $key ${shellQuote(value)}" } +
                listOf(
                    "settings put global $settingsGlobalKey $value",
                    "echo $value > $flagFilePath",
                    "chmod 644 $flagFilePath"
                ) +
                if (legacyFlagPath != null) {
                    listOf(
                        "echo $value > $legacyFlagPath",
                        "chmod 644 $legacyFlagPath"
                    )
                } else {
                    emptyList()
                }
            syncCommandBatch.get()?.let { batch ->
                batch.addAll(directCommands)
                return@runCatching
            }
            executeSyncCommands("LSP sync toggle:$settingsGlobalKey", directCommands)
        }.onFailure { ConfigSyncStatus.failed(settingsGlobalKey) }
    }

    private fun syncScalarState(
        value: String,
        propertyKeys: List<String>,
        settingsGlobalKey: String,
        textFilePath: String? = null
    ) {
        runCatching {
            val textFileCommands = if (textFilePath != null) {
                listOf(
                    "printf %s ${shellQuote(value)} > $textFilePath",
                    "chmod 644 $textFilePath"
                )
            } else {
                emptyList()
            }
            val directCommands = propertyKeys.map { key -> "setprop $key $value" } +
                listOf("settings put global $settingsGlobalKey ${shellQuote(value)}") +
                textFileCommands
            syncCommandBatch.get()?.let { batch ->
                batch.addAll(directCommands)
                return@runCatching
            }
            executeSyncCommands("LSP sync scalar:$settingsGlobalKey", directCommands)
        }.onFailure { ConfigSyncStatus.failed(settingsGlobalKey) }
    }

    @Synchronized
    private fun executeSyncCommands(tag: String, commands: List<String>): Boolean {
        if (commands.isEmpty()) return true
        val script = "(set -e;\n" + commands.joinToString("\n") + "\n)"
        val success = runCatching {
            ShellLogger.exec(tag, script).isSuccess ||
                ShellLogger.exec("$tag su", "su -c ${shellQuote(script)}").isSuccess
        }.getOrDefault(false)
        LspPreferenceStore.invalidateMirrors()
        if (!success) ConfigSyncStatus.failed(tag)
        return success
    }

    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }

}
