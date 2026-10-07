package com.mi.onextbox

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import com.mi.onextbox.lsp.ConfigSyncStatus
import com.mi.onextbox.ui.common.rememberConfigState
import com.mi.onextbox.lsp.commitOrReport
import android.os.LocaleList
import android.view.ContextThemeWrapper
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mi.onextbox.lsp.LspConfig
import com.mi.onextbox.lsp.LsposedScopeRequester
import com.mi.onextbox.ui.common.AppLocale
import com.mi.onextbox.ui.common.AppLogStore
import com.mi.onextbox.ui.common.AppThemeColorSpec
import com.mi.onextbox.ui.common.AppThemeKeyColor
import com.mi.onextbox.ui.common.AppThemeMode
import com.mi.onextbox.ui.common.AppThemePaletteStyle
import com.mi.onextbox.ui.common.AppUiStyle
import com.mi.onextbox.ui.common.AssistantScreenOption
import com.mi.onextbox.ui.common.RootAccessState
import com.mi.onextbox.ui.common.applyAssistantScreenOption
import com.mi.onextbox.ui.common.applyLauncherLayoutUnlocked
import com.mi.onextbox.ui.common.applyPermissionMonitorVisibility
import com.mi.onextbox.ui.common.queryAssistantScreenOption
import com.mi.onextbox.ui.common.queryLauncherLayoutUnlocked
import com.mi.onextbox.ui.common.queryPermissionMonitorVisibility
import com.mi.onextbox.ui.common.queryRootAccess
import com.mi.onextbox.ui.common.querySystemSettingsSnapshot
import com.mi.onextbox.ui.common.readCachedRootAccessInfo
import com.mi.onextbox.ui.Root
import com.mi.onextbox.ui.onboarding.OnboardingPreferences
import com.mi.onextbox.ui.onboarding.OnboardingAppearance
import com.mi.onextbox.ui.onboarding.OnboardingScreen
import com.mi.onextbox.ui.settings.AppUpdater
import com.mi.onextbox.ui.settings.UpdateChannelPreference
import com.mi.onextbox.ui.settings.UpdateNotificationScheduler

private data class StartupRefreshState(
    val lspSnapshot: LspConfig.UiSnapshot,
    val rootGranted: Boolean,
    val permissionMonitorVisible: Boolean,
    val launcherLayoutUnlocked: Boolean,
    val assistantScreenOption: AssistantScreenOption
)

private fun AppThemeMode.withDynamicColor(): AppThemeMode = when (this) {
    AppThemeMode.System -> AppThemeMode.MonetSystem
    AppThemeMode.Light -> AppThemeMode.MonetLight
    AppThemeMode.Dark -> AppThemeMode.MonetDark
    else -> this
}

private fun AppThemeMode.withStaticColor(): AppThemeMode = when (this) {
    AppThemeMode.MonetSystem -> AppThemeMode.System
    AppThemeMode.MonetLight -> AppThemeMode.Light
    AppThemeMode.MonetDark -> AppThemeMode.Dark
    else -> this
}

class MainActivity : ComponentActivity() {
    private var openSoftwareUpdateRequest by mutableIntStateOf(0)

    private companion object {
        const val PREF_BLUR_EFFECT_ENABLED = "blur_effect_enabled"
        const val PREF_FEATURE_PAGE_NEW_STYLE = "feature_page_new_style"
        const val PREF_COLOROS_PROGRESSIVE_CARD_ANIMATION = "coloros_progressive_card_animation"
        const val PREF_FEATURE_PAGE_VIDEO_HIDDEN = "feature_page_video_hidden"
        const val PREF_POP_DIRECTION_FOLLOWS_SWIPE_EDGE = "pop_direction_follows_swipe_edge"
        const val PREF_SHOW_FPS_MONITOR = "show_fps_monitor"
        const val PREF_LIQUID_GLASS_BOTTOM_BAR = "liquid_glass_bottom_bar"
        const val PREF_MATERIAL_FLOATING_BOTTOM_BAR = "material_floating_bottom_bar"
        const val PREF_MATERIAL_HAPTICS = "material_haptics"
        const val PREF_MATERIAL_SWITCH_ICONS = "material_switch_icons"
        const val PREF_COLOROS_THEME_MODE = "coloros_theme_mode"
        const val PREF_MATERIAL_THEME_MODE = "material_theme_mode"
        const val CONFIG_WRITE_DEBOUNCE_MS = 250L
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrapContext(newBase))
    }

    private fun recreateForLocaleChange() {
        window.decorView.post {
            recreate()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep onboarding and the prepared app destination in one window. Its final clear/dissolve
        // animation can then reveal Root directly instead of exposing Launcher between activities.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        handleLaunchIntent(intent)
        AppLogStore.i("App", "MainActivity onCreate")
        setContent {
            val prefs = remember {
                getSharedPreferences("onextbox_prefs", MODE_PRIVATE)
            }
            val rootCheckScope = rememberCoroutineScope()
            var onboardingCompleted by rememberSaveable {
                mutableStateOf(OnboardingPreferences.isCompleted(this@MainActivity))
            }
            var onboardingDestinationPrepared by remember {
                mutableStateOf(onboardingCompleted)
            }
            var onboardingVisible by rememberSaveable {
                mutableStateOf(!onboardingCompleted)
            }
            var rootGranted by rememberSaveable {
                mutableStateOf(
                    readCachedRootAccessInfo(this@MainActivity)?.state == RootAccessState.Granted,
                )
            }
            var blurEffectEnabled by rememberConfigState(
                prefs.getBoolean(PREF_BLUR_EFFECT_ENABLED, false),
            ) { value ->
                prefs.edit()
                    .putBoolean(PREF_BLUR_EFFECT_ENABLED, value)
                    .commitOrReport()
                AppLogStore.i("Settings", "Blur effect: $value")
            }
            var featurePageNewStyleEnabled by rememberConfigState(
                prefs.getBoolean(PREF_FEATURE_PAGE_NEW_STYLE, true),
            ) { value ->
                prefs.edit()
                    .putBoolean(PREF_FEATURE_PAGE_NEW_STYLE, value)
                    .commitOrReport()
                AppLogStore.i("Settings", "New Features page style: $value")
            }
            var featurePageVideoHidden by rememberConfigState(
                prefs.getBoolean(PREF_FEATURE_PAGE_VIDEO_HIDDEN, false),
            ) { value ->
                prefs.edit()
                    .putBoolean(PREF_FEATURE_PAGE_VIDEO_HIDDEN, value)
                    .commitOrReport()
                AppLogStore.i("Settings", "Features page background video hidden: $value")
            }
            var progressiveCardAnimationEnabled by rememberConfigState(
                prefs.getBoolean(PREF_COLOROS_PROGRESSIVE_CARD_ANIMATION, true),
            ) { value ->
                prefs.edit()
                    .putBoolean(PREF_COLOROS_PROGRESSIVE_CARD_ANIMATION, value)
                    .commitOrReport()
            }
            var popDirectionFollowsSwipeEdge by rememberConfigState(
                prefs.getBoolean(PREF_POP_DIRECTION_FOLLOWS_SWIPE_EDGE, false),
            ) { value ->
                prefs.edit()
                    .putBoolean(PREF_POP_DIRECTION_FOLLOWS_SWIPE_EDGE, value)
                    .commitOrReport()
                AppLogStore.i("Settings", "Pop follows swipe edge: $value")
            }
            var showFpsMonitor by rememberConfigState(
                prefs.getBoolean(PREF_SHOW_FPS_MONITOR, false),
            ) { value ->
                prefs.edit()
                    .putBoolean(PREF_SHOW_FPS_MONITOR, value)
                    .commitOrReport()
                AppLogStore.i("Settings", "Show FPS monitor: $value")
            }
            var liquidGlassBottomBarEnabled by rememberConfigState(
                prefs.getBoolean(PREF_LIQUID_GLASS_BOTTOM_BAR, false) &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
            ) { value ->
                prefs.edit()
                    .putBoolean(PREF_LIQUID_GLASS_BOTTOM_BAR, value)
                    .commitOrReport()
                AppLogStore.i("Settings", "LiquidGlass bottom bar: $value")
            }
            var currentTab by rememberSaveable {
                mutableIntStateOf(0)
            }
            // Reload the selected language from preferences after recreation.
            var appLanguageTag by remember {
                mutableStateOf(AppLocale.getSelectedLanguageTag(this@MainActivity))
            }
            val localizedUiContext = remember(appLanguageTag) {
                createLocalizedUiContext(appLanguageTag)
            }
            val initiallySelectedUiStyle = remember { AppUiStyle.get(this@MainActivity) }
            val initiallySavedThemeMode = remember { AppThemeMode.get(this@MainActivity) }
            var appThemeMode by rememberSaveable {
                val storedMode = prefs.getString(
                    if (initiallySelectedUiStyle == AppUiStyle.Material3Expressive) {
                        PREF_MATERIAL_THEME_MODE
                    } else {
                        PREF_COLOROS_THEME_MODE
                    },
                    null,
                )
                mutableStateOf(
                    if (storedMode != null) AppThemeMode.fromName(storedMode)
                    else if (initiallySelectedUiStyle == AppUiStyle.Material3Expressive) {
                        if (AppThemeKeyColor.get(this@MainActivity) != null) {
                            initiallySavedThemeMode.withStaticColor()
                        } else {
                            initiallySavedThemeMode.withDynamicColor()
                        }
                    } else initiallySavedThemeMode,
                )
            }
            var appUiStyle by rememberSaveable {
                mutableStateOf(initiallySelectedUiStyle)
            }
            var materialFloatingBottomBarEnabled by rememberConfigState(
                prefs.getBoolean(PREF_MATERIAL_FLOATING_BOTTOM_BAR, false),
            ) { value ->
                prefs.edit().putBoolean(PREF_MATERIAL_FLOATING_BOTTOM_BAR, value).commitOrReport()
            }
            var materialHapticsEnabled by rememberConfigState(
                prefs.getBoolean(PREF_MATERIAL_HAPTICS, true),
            ) { value ->
                prefs.edit().putBoolean(PREF_MATERIAL_HAPTICS, value).commitOrReport()
            }
            var materialSwitchIconsEnabled by rememberConfigState(
                prefs.getBoolean(PREF_MATERIAL_SWITCH_ICONS, false),
            ) { value ->
                prefs.edit().putBoolean(PREF_MATERIAL_SWITCH_ICONS, value).commitOrReport()
            }
            var appThemeKeyColor by rememberSaveable {
                mutableStateOf(AppThemeKeyColor.get(this@MainActivity))
            }
            LaunchedEffect(Unit) {
                val editor = prefs.edit()
                if (!prefs.contains(PREF_COLOROS_THEME_MODE)) {
                    editor.putString(
                        PREF_COLOROS_THEME_MODE,
                        if (initiallySelectedUiStyle == AppUiStyle.ColorOs) {
                            initiallySavedThemeMode.name
                        } else {
                            initiallySavedThemeMode.withStaticColor().name
                        },
                    )
                }
                if (appUiStyle == AppUiStyle.Material3Expressive) {
                    editor.putString(PREF_MATERIAL_THEME_MODE, appThemeMode.name)
                }
                editor.apply()
                AppThemeMode.set(this@MainActivity, appThemeMode)
            }
            var appThemePaletteStyle by rememberSaveable {
                mutableIntStateOf(AppThemePaletteStyle.get(this@MainActivity))
            }
            var appThemeColorSpec by rememberSaveable {
                mutableIntStateOf(AppThemeColorSpec.get(this@MainActivity))
            }
            val initialLspConfig = remember {
                LspConfig.readCachedUiSnapshot(this@MainActivity)
            }
            val nativeNotifyIconEnabledState = rememberConfigState(
                initialLspConfig.nativeNotifyIconEnabled,
            ) { value ->
                LspConfig.setNativeNotifyIconEnabled(this@MainActivity, value)
            }
            var nativeNotifyIconEnabled by nativeNotifyIconEnabledState
            val nativeNotificationBubblesEnabledState = rememberConfigState(
                initialLspConfig.nativeNotificationBubblesEnabled,
            ) { value ->
                LspConfig.setNativeNotificationBubblesEnabled(
                    this@MainActivity,
                    value
                )
            }
            var nativeNotificationBubblesEnabled by nativeNotificationBubblesEnabledState
            val systemUiInternationalNetworkDisplayEnabledState = rememberConfigState(
                initialLspConfig.systemUiInternationalNetworkDisplayEnabled,
            ) { value ->
                LspConfig.setSystemUiInternationalNetworkDisplayEnabled(
                    this@MainActivity,
                    value
                )
            }
            var systemUiInternationalNetworkDisplayEnabled by systemUiInternationalNetworkDisplayEnabledState
            val systemUiHideMobileRoamingIndicatorEnabledState = rememberConfigState(
                initialLspConfig.systemUiHideMobileRoamingIndicatorEnabled,
            ) { value ->
                LspConfig.setSystemUiHideMobileRoamingIndicatorEnabled(
                    this@MainActivity,
                    value
                )
            }
            var systemUiHideMobileRoamingIndicatorEnabled by systemUiHideMobileRoamingIndicatorEnabledState
            val systemUiInternationalNotificationStyleEnabledState = rememberConfigState(
                initialLspConfig.systemUiInternationalNotificationStyleEnabled,
            ) { value ->
                LspConfig.setSystemUiInternationalNotificationStyleEnabled(
                    this@MainActivity,
                    value
                )
            }
            var systemUiInternationalNotificationStyleEnabled by systemUiInternationalNotificationStyleEnabledState
            val systemUiHideQsEditEnabledState = rememberConfigState(
                initialLspConfig.systemUiHideQsEditEnabled,
            ) { value ->
                LspConfig.setSystemUiHideQsEditEnabled(this@MainActivity, value)
            }
            var systemUiHideQsEditEnabled by systemUiHideQsEditEnabledState
            val systemUiHideQsSettingsEnabledState = rememberConfigState(
                initialLspConfig.systemUiHideQsSettingsEnabled,
            ) { value ->
                LspConfig.setSystemUiHideQsSettingsEnabled(this@MainActivity, value)
            }
            var systemUiHideQsSettingsEnabled by systemUiHideQsSettingsEnabledState
            val systemUiHideQsTopCarrierEnabledState = rememberConfigState(
                initialLspConfig.systemUiHideQsTopCarrierEnabled,
            ) { value ->
                LspConfig.setSystemUiHideQsTopCarrierEnabled(this@MainActivity, value)
            }
            var systemUiHideQsTopCarrierEnabled by systemUiHideQsTopCarrierEnabledState
            val systemUiHideQsMoreEnabledState = rememberConfigState(
                initialLspConfig.systemUiHideQsMoreEnabled,
            ) { value ->
                LspConfig.setSystemUiHideQsMoreEnabled(this@MainActivity, value)
            }
            var systemUiHideQsMoreEnabled by systemUiHideQsMoreEnabledState
            val systemUiForceNativeClipboardOverlayEnabledState = rememberConfigState(
                initialLspConfig.systemUiForceNativeClipboardOverlayEnabled,
            ) { value ->
                LspConfig.setSystemUiForceNativeClipboardOverlayEnabled(
                    this@MainActivity,
                    value
                )
            }
            var systemUiForceNativeClipboardOverlayEnabled by systemUiForceNativeClipboardOverlayEnabledState
            val settingsForceGoogleEntryEnabledState = rememberConfigState(
                initialLspConfig.settingsForceGoogleEntryEnabled,
            ) { value ->
                LspConfig.setSettingsForceGoogleEntryEnabled(
                    this@MainActivity,
                    value
                )
            }
            var settingsForceGoogleEntryEnabled by settingsForceGoogleEntryEnabledState
            val gmsRegionRestrictionBypassEnabledState = rememberConfigState(
                initialLspConfig.gmsRegionRestrictionBypassEnabled,
            ) { value ->
                LspConfig.setGmsRegionRestrictionBypassEnabled(
                    this@MainActivity,
                    value,
                )
            }
            var gmsRegionRestrictionBypassEnabled by gmsRegionRestrictionBypassEnabledState
            val extremeRefresh165EnabledState = rememberConfigState(
                initialLspConfig.extremeRefresh165Enabled,
            ) { value ->
                LspConfig.setExtremeRefresh165Enabled(this@MainActivity, value)
            }
            var extremeRefresh165Enabled by extremeRefresh165EnabledState
            var permissionMonitorVisible by rememberSaveable {
                mutableStateOf(false)
            }
            var launcherLayoutUnlocked by rememberSaveable {
                mutableStateOf(false)
            }
            var assistantScreenOption by rememberSaveable {
                mutableStateOf(AssistantScreenOption.Default)
            }
            val recentTaskRadiusEnabledState = rememberConfigState(
                initialLspConfig.recentTaskRadiusEnabled,
            ) { value ->
                LspConfig.setRecentTaskRadiusEnabled(this@MainActivity, value)
            }
            var recentTaskRadiusEnabled by recentTaskRadiusEnabledState
            val recentTaskRadiusDpState = rememberConfigState(
                initialLspConfig.recentTaskRadiusDp,
            ) { value ->
                LspConfig.setRecentTaskRadiusDp(this@MainActivity, value)
            }
            var recentTaskRadiusDp by recentTaskRadiusDpState
            val aodEnhanceEnabledState = rememberConfigState(
                initialLspConfig.aodEnhanceEnabled,
            ) { value ->
                LspConfig.setAodEnhanceEnabled(this@MainActivity, value)
            }
            var aodEnhanceEnabled by aodEnhanceEnabledState
            val aodInitDarkBrightnessState = rememberConfigState(
                initialLspConfig.aodInitDarkBrightness,
            ) { value ->
                LspConfig.setAodInitDarkBrightness(this@MainActivity, value)
            }
            var aodInitDarkBrightness by aodInitDarkBrightnessState
            val aodInitBrightBrightnessState = rememberConfigState(
                initialLspConfig.aodInitBrightBrightness,
            ) { value ->
                LspConfig.setAodInitBrightBrightness(this@MainActivity, value)
            }
            var aodInitBrightBrightness by aodInitBrightBrightnessState
            val aodRunningBrightnessMultiplierState = rememberConfigState(
                initialLspConfig.aodRunningBrightnessMultiplier,
            ) { value ->
                LspConfig.setAodRunningBrightnessMultiplier(
                    this@MainActivity,
                    value
                )
            }
            var aodRunningBrightnessMultiplier by aodRunningBrightnessMultiplierState
            val aodPanoramicSupportEnabledState = rememberConfigState(
                initialLspConfig.aodPanoramicSupportEnabled,
            ) { value ->
                LspConfig.setAodPanoramicSupportEnabled(
                    this@MainActivity,
                    value
                )
            }
            var aodPanoramicSupportEnabled by aodPanoramicSupportEnabledState
            val aodSettingsSwitchEnabledState = rememberConfigState(
                initialLspConfig.aodSettingsSwitchEnabled,
            ) { value ->
                LspConfig.setAodSettingsSwitchEnabled(
                    this@MainActivity,
                    value
                )
            }
            var aodSettingsSwitchEnabled by aodSettingsSwitchEnabledState
            val aodSingleClickBlockEnabledState = rememberConfigState(
                initialLspConfig.aodSingleClickBlockEnabled,
            ) { value ->
                LspConfig.setAodSingleClickBlockEnabled(
                    this@MainActivity,
                    value
                )
            }
            var aodSingleClickBlockEnabled by aodSingleClickBlockEnabledState
            val assistantPowerModeState = rememberConfigState(
                initialLspConfig.assistantPowerMode,
            ) { value ->
                LspConfig.setAssistantPowerMode(this@MainActivity, value)
            }
            var assistantPowerMode by assistantPowerModeState
            val assistantGestureCircleEnabledState = rememberConfigState(
                initialLspConfig.assistantGestureCircleEnabled,
            ) { value ->
                LspConfig.setAssistantGestureCircleEnabled(
                    this@MainActivity,
                    value
                )
            }
            var assistantGestureCircleEnabled by assistantGestureCircleEnabledState
            val assistantGestureCircleC17EnabledState = rememberConfigState(
                initialLspConfig.assistantGestureCircleC17Enabled,
            ) { value ->
                LspConfig.setAssistantGestureCircleC17Enabled(
                    this@MainActivity,
                    value
                )
            }
            var assistantGestureCircleC17Enabled by assistantGestureCircleC17EnabledState
            val assistantNativePowerEnabledState = rememberConfigState(
                initialLspConfig.assistantNativePowerEnabled,
            ) { value ->
                LspConfig.setAssistantNativePowerEnabled(
                    this@MainActivity,
                    value,
                )
            }
            var assistantNativePowerEnabled by assistantNativePowerEnabledState
            val assistantNativeCircleEnabledState = rememberConfigState(
                initialLspConfig.assistantNativeCircleEnabled,
            ) { value ->
                LspConfig.setAssistantNativeCircleEnabled(
                    this@MainActivity,
                    value,
                )
            }
            var assistantNativeCircleEnabled by assistantNativeCircleEnabledState
            var resumeRefreshEnabled by remember {
                mutableStateOf(false)
            }
            fun applyLspConfigSnapshot(snapshot: LspConfig.UiSnapshot) {
                nativeNotifyIconEnabledState.replaceFromSource(snapshot.nativeNotifyIconEnabled)
                nativeNotificationBubblesEnabledState.replaceFromSource(snapshot.nativeNotificationBubblesEnabled)
                extremeRefresh165EnabledState.replaceFromSource(snapshot.extremeRefresh165Enabled)
                recentTaskRadiusEnabledState.replaceFromSource(snapshot.recentTaskRadiusEnabled)
                recentTaskRadiusDpState.replaceFromSource(snapshot.recentTaskRadiusDp)
                aodEnhanceEnabledState.replaceFromSource(snapshot.aodEnhanceEnabled)
                aodInitDarkBrightnessState.replaceFromSource(snapshot.aodInitDarkBrightness)
                aodInitBrightBrightnessState.replaceFromSource(snapshot.aodInitBrightBrightness)
                aodRunningBrightnessMultiplierState.replaceFromSource(snapshot.aodRunningBrightnessMultiplier)
                aodPanoramicSupportEnabledState.replaceFromSource(snapshot.aodPanoramicSupportEnabled)
                aodSettingsSwitchEnabledState.replaceFromSource(snapshot.aodSettingsSwitchEnabled)
                aodSingleClickBlockEnabledState.replaceFromSource(snapshot.aodSingleClickBlockEnabled)
                systemUiInternationalNetworkDisplayEnabledState.replaceFromSource(snapshot.systemUiInternationalNetworkDisplayEnabled)
                systemUiHideMobileRoamingIndicatorEnabledState.replaceFromSource(snapshot.systemUiHideMobileRoamingIndicatorEnabled)
                systemUiInternationalNotificationStyleEnabledState.replaceFromSource(snapshot.systemUiInternationalNotificationStyleEnabled)
                systemUiHideQsEditEnabledState.replaceFromSource(snapshot.systemUiHideQsEditEnabled)
                systemUiHideQsSettingsEnabledState.replaceFromSource(snapshot.systemUiHideQsSettingsEnabled)
                systemUiHideQsTopCarrierEnabledState.replaceFromSource(snapshot.systemUiHideQsTopCarrierEnabled)
                systemUiHideQsMoreEnabledState.replaceFromSource(snapshot.systemUiHideQsMoreEnabled)
                systemUiForceNativeClipboardOverlayEnabledState.replaceFromSource(snapshot.systemUiForceNativeClipboardOverlayEnabled)
                settingsForceGoogleEntryEnabledState.replaceFromSource(snapshot.settingsForceGoogleEntryEnabled)
                gmsRegionRestrictionBypassEnabledState.replaceFromSource(snapshot.gmsRegionRestrictionBypassEnabled)
                assistantPowerModeState.replaceFromSource(snapshot.assistantPowerMode)
                assistantGestureCircleEnabledState.replaceFromSource(snapshot.assistantGestureCircleEnabled)
                assistantGestureCircleC17EnabledState.replaceFromSource(snapshot.assistantGestureCircleC17Enabled)
                assistantNativePowerEnabledState.replaceFromSource(snapshot.assistantNativePowerEnabled)
                assistantNativeCircleEnabledState.replaceFromSource(snapshot.assistantNativeCircleEnabled)
            }
            LaunchedEffect(Unit) {
                ConfigSyncStatus.failures.collect {
                    Toast.makeText(this@MainActivity, R.string.config_sync_failed, Toast.LENGTH_LONG).show()
                }
            }
            suspend fun applyRefreshStateWithoutWriteBack(state: StartupRefreshState) {
                applyLspConfigSnapshot(state.lspSnapshot)
                if (rootGranted != state.rootGranted) {
                    rootGranted = state.rootGranted
                }
                permissionMonitorVisible = state.permissionMonitorVisible
                launcherLayoutUnlocked = state.launcherLayoutUnlocked
                assistantScreenOption = state.assistantScreenOption
            }
            LaunchedEffect(onboardingCompleted) {
                withFrameNanos { }
                withContext(Dispatchers.IO) {
                    AppLogStore.initialize(applicationContext)
                    AppUpdater.cleanupInstalledUpdate(this@MainActivity)
                }
                AppLogStore.i("App", "MainActivity started")
                if (!onboardingCompleted) {
                    resumeRefreshEnabled = false
                    return@LaunchedEffect
                }
                UpdateNotificationScheduler.schedule(this@MainActivity)
                UpdateNotificationScheduler.checkNow(this@MainActivity)
                val startupState = withContext(Dispatchers.IO) {
                    LsposedScopeRequester.initialize(this@MainActivity)
                    val currentRootAccess = queryRootAccess(this@MainActivity)
                    val systemSettings = querySystemSettingsSnapshot()
                    StartupRefreshState(
                        lspSnapshot = LspConfig.readSyncedUiSnapshot(this@MainActivity),
                        rootGranted = currentRootAccess.state == RootAccessState.Granted,
                        permissionMonitorVisible = systemSettings.permissionMonitorVisible,
                        launcherLayoutUnlocked = systemSettings.launcherLayoutUnlocked,
                        assistantScreenOption = systemSettings.assistantScreenOption
                    )
                }
                applyRefreshStateWithoutWriteBack(startupState)
                resumeRefreshEnabled = true
                rootCheckScope.launch {
                    delay(2_000)
                    withContext(Dispatchers.IO) {
                        LspConfig.syncTogglesForBoot(this@MainActivity)
                    }
                }
                rootCheckScope.launch {
                    delay(5_000)
                    withContext(Dispatchers.IO) {
                        AppUpdater.runAutomaticSilentUpdate(this@MainActivity)
                    }
                }
            }
            DisposableEffect(resumeRefreshEnabled, onboardingCompleted) {
                var skipCurrentResume = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME && resumeRefreshEnabled) {
                        if (skipCurrentResume) {
                            skipCurrentResume = false
                            return@LifecycleEventObserver
                        }
                        rootCheckScope.launch {
                            val refreshState = withContext(Dispatchers.IO) {
                                val currentRootAccess = queryRootAccess(this@MainActivity)
                                val systemSettings = querySystemSettingsSnapshot()
                                StartupRefreshState(
                                    lspSnapshot = LspConfig.readSyncedUiSnapshot(this@MainActivity),
                                    rootGranted = currentRootAccess.state == RootAccessState.Granted,
                                    permissionMonitorVisible = systemSettings.permissionMonitorVisible,
                                    launcherLayoutUnlocked = systemSettings.launcherLayoutUnlocked,
                                    assistantScreenOption = systemSettings.assistantScreenOption
                                )
                            }
                            applyRefreshStateWithoutWriteBack(refreshState)
                        }
                    }
                }
                lifecycle.addObserver(observer)
                onDispose {
                    lifecycle.removeObserver(observer)
                }
            }

            val systemDensity = LocalDensity.current
            val appDensity = remember(appUiStyle, systemDensity.density, systemDensity.fontScale) {
                if (appUiStyle == AppUiStyle.Material3Expressive) {
                    Density(systemDensity.density * 0.95f, systemDensity.fontScale)
                } else {
                    systemDensity
                }
            }
            CompositionLocalProvider(
                LocalContext provides localizedUiContext,
                LocalConfiguration provides localizedUiContext.resources.configuration,
                LocalResources provides localizedUiContext.resources,
                LocalDensity provides appDensity,
                // The localized context is a theme wrapper. Keep result launchers attached to the
                // real activity so migration import survives in-place language changes.
                LocalActivityResultRegistryOwner provides this@MainActivity,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (onboardingCompleted || onboardingDestinationPrepared) {
                        Root(
                openSoftwareUpdateRequest = openSoftwareUpdateRequest,
                currentTab = currentTab,
                onTabChange = { currentTab = it },
                rootGranted = rootGranted,
                blurEffectEnabled = blurEffectEnabled,
                onBlurEffectEnabledChange = { blurEffectEnabled = it },
                featurePageNewStyleEnabled = featurePageNewStyleEnabled,
                onFeaturePageNewStyleEnabledChange = { featurePageNewStyleEnabled = it },
                progressiveCardAnimationEnabled = progressiveCardAnimationEnabled,
                onProgressiveCardAnimationEnabledChange = { progressiveCardAnimationEnabled = it },
                featurePageVideoHidden = featurePageVideoHidden,
                onFeaturePageVideoHiddenChange = { featurePageVideoHidden = it },
                popDirectionFollowsSwipeEdge = popDirectionFollowsSwipeEdge,
                onPopDirectionFollowsSwipeEdgeChange = { popDirectionFollowsSwipeEdge = it },
                showFpsMonitor = showFpsMonitor,
                onShowFpsMonitorChange = { showFpsMonitor = it },
                liquidGlassBottomBarEnabled = liquidGlassBottomBarEnabled,
                onLiquidGlassBottomBarEnabledChange = { liquidGlassBottomBarEnabled = it },
                appLanguageTag = appLanguageTag,
                onAppLanguageChange = { languageTag ->
                    if (appLanguageTag != languageTag) {
                        appLanguageTag = languageTag
                        AppLocale.setSelectedLanguageTag(this@MainActivity, languageTag)
                        recreateForLocaleChange()
                    }
                },
                appThemeMode = appThemeMode,
                onAppThemeModeChange = { mode ->
                    appThemeMode = mode
                    AppThemeMode.set(this@MainActivity, mode)
                    prefs.edit().putString(
                        if (appUiStyle == AppUiStyle.Material3Expressive) {
                            PREF_MATERIAL_THEME_MODE
                        } else {
                            PREF_COLOROS_THEME_MODE
                        },
                        mode.name,
                    ).apply()
                },
                appUiStyle = appUiStyle,
                onAppUiStyleChange = { style ->
                    if (style != appUiStyle) {
                        val sourceKey = if (appUiStyle == AppUiStyle.Material3Expressive) {
                            PREF_MATERIAL_THEME_MODE
                        } else {
                            PREF_COLOROS_THEME_MODE
                        }
                        val targetKey = if (style == AppUiStyle.Material3Expressive) {
                            PREF_MATERIAL_THEME_MODE
                        } else {
                            PREF_COLOROS_THEME_MODE
                        }
                        val nextMode = prefs.getString(targetKey, null)?.let(AppThemeMode::fromName)
                            ?: if (style == AppUiStyle.Material3Expressive) {
                                appThemeMode.withDynamicColor()
                            } else {
                                appThemeMode.withStaticColor()
                            }
                        prefs.edit()
                            .putString(sourceKey, appThemeMode.name)
                            .putString(targetKey, nextMode.name)
                            .apply()
                        appThemeMode = nextMode
                        AppThemeMode.set(this@MainActivity, nextMode)
                    }
                    appUiStyle = style
                    AppUiStyle.set(this@MainActivity, style)
                },
                materialFloatingBottomBarEnabled = materialFloatingBottomBarEnabled,
                onMaterialFloatingBottomBarEnabledChange = { materialFloatingBottomBarEnabled = it },
                materialHapticsEnabled = materialHapticsEnabled,
                onMaterialHapticsEnabledChange = { materialHapticsEnabled = it },
                materialSwitchIconsEnabled = materialSwitchIconsEnabled,
                onMaterialSwitchIconsEnabledChange = { materialSwitchIconsEnabled = it },
                appThemeKeyColor = appThemeKeyColor,
                onAppThemeKeyColorChange = { color ->
                    appThemeKeyColor = color
                    AppThemeKeyColor.set(this@MainActivity, color)
                },
                appThemePaletteStyle = appThemePaletteStyle,
                onAppThemePaletteStyleChange = { style ->
                    appThemePaletteStyle = style
                    AppThemePaletteStyle.set(this@MainActivity, style)
                },
                appThemeColorSpec = appThemeColorSpec,
                onAppThemeColorSpecChange = { spec ->
                    appThemeColorSpec = spec
                    AppThemeColorSpec.set(this@MainActivity, spec)
                },
                permissionMonitorVisible = permissionMonitorVisible,
                onPermissionMonitorVisibleChange = { enabled ->
                    permissionMonitorVisible = enabled
                    rootCheckScope.launch {
                        val result = applyPermissionMonitorVisibility(enabled)
                        permissionMonitorVisible = queryPermissionMonitorVisibility()
                        if (!result.success) {
                            AppLogStore.w(
                                "PermissionMonitor",
                                "Toggle apply failed: ${result.detail.orEmpty()}"
                            )
                        }
                    }
                },
                nativeNotifyIconEnabled = nativeNotifyIconEnabled,
                onNativeNotifyIconEnabledChange = { nativeNotifyIconEnabled = it },
                nativeNotificationBubblesEnabled = nativeNotificationBubblesEnabled,
                onNativeNotificationBubblesEnabledChange = { nativeNotificationBubblesEnabled = it },
                systemUiInternationalNetworkDisplayEnabled =
                    systemUiInternationalNetworkDisplayEnabled,
                onSystemUiInternationalNetworkDisplayEnabledChange = {
                    systemUiInternationalNetworkDisplayEnabled = it
                },
                systemUiHideMobileRoamingIndicatorEnabled =
                    systemUiHideMobileRoamingIndicatorEnabled,
                onSystemUiHideMobileRoamingIndicatorEnabledChange = {
                    systemUiHideMobileRoamingIndicatorEnabled = it
                },
                systemUiInternationalNotificationStyleEnabled =
                    systemUiInternationalNotificationStyleEnabled,
                onSystemUiInternationalNotificationStyleEnabledChange = {
                    systemUiInternationalNotificationStyleEnabled = it
                },
                systemUiHideQsEditEnabled = systemUiHideQsEditEnabled,
                onSystemUiHideQsEditEnabledChange = { systemUiHideQsEditEnabled = it },
                systemUiHideQsSettingsEnabled = systemUiHideQsSettingsEnabled,
                onSystemUiHideQsSettingsEnabledChange = { systemUiHideQsSettingsEnabled = it },
                systemUiHideQsTopCarrierEnabled = systemUiHideQsTopCarrierEnabled,
                onSystemUiHideQsTopCarrierEnabledChange = { systemUiHideQsTopCarrierEnabled = it },
                systemUiHideQsMoreEnabled = systemUiHideQsMoreEnabled,
                onSystemUiHideQsMoreEnabledChange = { systemUiHideQsMoreEnabled = it },
                systemUiForceNativeClipboardOverlayEnabled = systemUiForceNativeClipboardOverlayEnabled,
                onSystemUiForceNativeClipboardOverlayEnabledChange = {
                    systemUiForceNativeClipboardOverlayEnabled = it
                },
                settingsForceGoogleEntryEnabled = settingsForceGoogleEntryEnabled,
                onSettingsForceGoogleEntryEnabledChange = { settingsForceGoogleEntryEnabled = it },
                gmsRegionRestrictionBypassEnabled = gmsRegionRestrictionBypassEnabled,
                onGmsRegionRestrictionBypassEnabledChange = {
                    gmsRegionRestrictionBypassEnabled = it
                },
                extremeRefresh165Enabled = extremeRefresh165Enabled,
                onExtremeRefresh165EnabledChange = { extremeRefresh165Enabled = it },
                launcherLayoutUnlocked = launcherLayoutUnlocked,
                onLauncherLayoutUnlockedChange = { enabled ->
                    launcherLayoutUnlocked = enabled
                    rootCheckScope.launch {
                        val result = applyLauncherLayoutUnlocked(enabled)
                        launcherLayoutUnlocked = queryLauncherLayoutUnlocked()
                        if (!result.success) {
                            AppLogStore.w(
                                "LauncherLayout",
                                "Toggle apply failed: ${result.detail.orEmpty()}"
                            )
                        }
                    }
                },
                assistantScreenOption = assistantScreenOption,
                onAssistantScreenOptionChange = { option ->
                    assistantScreenOption = option
                    rootCheckScope.launch {
                        val result = applyAssistantScreenOption(option)
                        assistantScreenOption = queryAssistantScreenOption()
                        if (!result.success) {
                            AppLogStore.w(
                                "DesktopAssistant",
                                "Apply failed: ${result.detail.orEmpty()}"
                            )
                        }
                    }
                },
                recentTaskRadiusEnabled = recentTaskRadiusEnabled,
                onRecentTaskRadiusEnabledChange = { recentTaskRadiusEnabled = it },
                recentTaskRadiusDp = recentTaskRadiusDp,
                onRecentTaskRadiusDpChange = { recentTaskRadiusDp = it },
                aodEnhanceEnabled = aodEnhanceEnabled,
                onAodEnhanceEnabledChange = { aodEnhanceEnabled = it },
                aodInitDarkBrightness = aodInitDarkBrightness,
                onAodInitDarkBrightnessChange = { aodInitDarkBrightness = it },
                aodInitBrightBrightness = aodInitBrightBrightness,
                onAodInitBrightBrightnessChange = { aodInitBrightBrightness = it },
                aodRunningBrightnessMultiplier = aodRunningBrightnessMultiplier,
                onAodRunningBrightnessMultiplierChange = { aodRunningBrightnessMultiplier = it },
                aodPanoramicSupportEnabled = aodPanoramicSupportEnabled,
                onAodPanoramicSupportEnabledChange = { aodPanoramicSupportEnabled = it },
                aodSettingsSwitchEnabled = aodSettingsSwitchEnabled,
                onAodSettingsSwitchEnabledChange = { aodSettingsSwitchEnabled = it },
                aodSingleClickBlockEnabled = aodSingleClickBlockEnabled,
                onAodSingleClickBlockEnabledChange = { aodSingleClickBlockEnabled = it },
                assistantPowerMode = assistantPowerMode,
                onAssistantPowerModeChange = { assistantPowerMode = it },
                assistantGestureCircleEnabled = assistantGestureCircleEnabled,
                onAssistantGestureCircleEnabledChange = { assistantGestureCircleEnabled = it },
                assistantGestureCircleC17Enabled = assistantGestureCircleC17Enabled,
                onAssistantGestureCircleC17EnabledChange = {
                    assistantGestureCircleC17Enabled = it
                },
                assistantNativePowerEnabled = assistantNativePowerEnabled,
                onAssistantNativePowerEnabledChange = {
                    assistantNativePowerEnabled = it
                },
                assistantNativeCircleEnabled = assistantNativeCircleEnabled,
                onAssistantNativeCircleEnabledChange = {
                    assistantNativeCircleEnabled = it
                },
                    )
                    }
                    if (onboardingVisible) {
                        // The app's Material UI uses a smaller density. Activation always keeps
                        // the same C17 sizing while its choices configure only the destination.
                        CompositionLocalProvider(LocalDensity provides systemDensity) {
                        OnboardingScreen(
                        appLanguageTag = appLanguageTag,
                        onAppLanguageChange = { languageTag ->
                            if (appLanguageTag != languageTag) {
                                appLanguageTag = languageTag
                                AppLocale.setSelectedLanguageTag(this@MainActivity, languageTag)
                            }
                        },
                        appUiStyle = appUiStyle,
                        onAppUiStyleChange = { style ->
                            OnboardingAppearance.apply(this@MainActivity, style, appThemeMode)
                                ?.let { effectiveMode ->
                                    appUiStyle = style
                                    appThemeMode = effectiveMode
                                }
                        },
                        appThemeMode = appThemeMode,
                        onAppThemeModeChange = { mode ->
                            OnboardingAppearance.apply(this@MainActivity, appUiStyle, mode)
                                ?.let { effectiveMode -> appThemeMode = effectiveMode }
                        },
                        featurePageNewStyleEnabled = featurePageNewStyleEnabled,
                        onFeaturePageNewStyleEnabledChange = { enabled ->
                            featurePageNewStyleEnabled = enabled
                            prefs.edit()
                                .putBoolean(PREF_FEATURE_PAGE_NEW_STYLE, enabled)
                                .apply()
                        },
                        featurePageVideoHidden = featurePageVideoHidden,
                        onFeaturePageVideoHiddenChange = { hidden ->
                            featurePageVideoHidden = hidden
                            prefs.edit()
                                .putBoolean(PREF_FEATURE_PAGE_VIDEO_HIDDEN, hidden)
                                .apply()
                        },
                        onDestinationPreparationRequested = {
                            onboardingDestinationPrepared = true
                        },
                        onActivationCommitted = { granted ->
                            rootGranted = granted
                            onboardingCompleted = true
                            resumeRefreshEnabled = false
                            val allowBackgroundUpdates =
                                OnboardingPreferences.isBackgroundNetworkAllowed(this@MainActivity)
                            UpdateChannelPreference.setUpdateNotificationsEnabled(
                                this@MainActivity,
                                allowBackgroundUpdates,
                            )
                            if (allowBackgroundUpdates) {
                                UpdateNotificationScheduler.schedule(this@MainActivity)
                                UpdateNotificationScheduler.checkNow(this@MainActivity)
                            }
                        },
                        onExitFinished = {
                            onboardingVisible = false
                        },
                        )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun handleLaunchIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(UpdateNotificationScheduler.EXTRA_OPEN_SOFTWARE_UPDATE, false) == true) {
            intent.removeExtra(UpdateNotificationScheduler.EXTRA_OPEN_SOFTWARE_UPDATE)
            openSoftwareUpdateRequest += 1
        }
    }

/** Localized UI context that preserves the Activity for window and lifecycle access. */
    private fun createLocalizedUiContext(languageTag: String): Context {
        val locales = if (languageTag.isBlank()) {
            Resources.getSystem().configuration.locales
        } else {
            LocaleList.forLanguageTags(AppLocale.resourceLanguageTag(languageTag))
        }
        val configuration = Configuration(resources.configuration).apply {
            setLocales(locales)
        }
        return ContextThemeWrapper(this, theme).apply {
            applyOverrideConfiguration(configuration)
        }
    }

}
