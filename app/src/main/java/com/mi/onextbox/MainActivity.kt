package com.mi.onextbox

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
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
            var blurEffectEnabled by rememberSaveable {
                mutableStateOf(prefs.getBoolean(PREF_BLUR_EFFECT_ENABLED, false))
            }
            var featurePageNewStyleEnabled by rememberSaveable {
                mutableStateOf(prefs.getBoolean(PREF_FEATURE_PAGE_NEW_STYLE, true))
            }
            var featurePageVideoHidden by rememberSaveable {
                mutableStateOf(prefs.getBoolean(PREF_FEATURE_PAGE_VIDEO_HIDDEN, false))
            }
            var popDirectionFollowsSwipeEdge by rememberSaveable {
                mutableStateOf(prefs.getBoolean(PREF_POP_DIRECTION_FOLLOWS_SWIPE_EDGE, false))
            }
            var showFpsMonitor by rememberSaveable {
                mutableStateOf(prefs.getBoolean(PREF_SHOW_FPS_MONITOR, false))
            }
            var liquidGlassBottomBarEnabled by rememberSaveable {
                mutableStateOf(
                    prefs.getBoolean(PREF_LIQUID_GLASS_BOTTOM_BAR, false) &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                )
            }
            var currentTab by rememberSaveable {
                mutableIntStateOf(0)
            }
            // Preferences are authoritative after recreation; restoring a saved old tag would
            // override a language selected from a settings popup.
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
            var materialFloatingBottomBarEnabled by rememberSaveable {
                mutableStateOf(prefs.getBoolean(PREF_MATERIAL_FLOATING_BOTTOM_BAR, false))
            }
            var materialHapticsEnabled by rememberSaveable {
                mutableStateOf(prefs.getBoolean(PREF_MATERIAL_HAPTICS, true))
            }
            var materialSwitchIconsEnabled by rememberSaveable {
                mutableStateOf(prefs.getBoolean(PREF_MATERIAL_SWITCH_ICONS, false))
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
            var nativeNotifyIconEnabled by rememberSaveable {
                mutableStateOf(initialLspConfig.nativeNotifyIconEnabled)
            }
            var nativeNotificationBubblesEnabled by rememberSaveable {
                mutableStateOf(initialLspConfig.nativeNotificationBubblesEnabled)
            }
            var systemUiInternationalNetworkDisplayEnabled by rememberSaveable {
                mutableStateOf(initialLspConfig.systemUiInternationalNetworkDisplayEnabled)
            }
            var systemUiHideMobileRoamingIndicatorEnabled by rememberSaveable {
                mutableStateOf(initialLspConfig.systemUiHideMobileRoamingIndicatorEnabled)
            }
            var systemUiInternationalNotificationStyleEnabled by rememberSaveable {
                mutableStateOf(initialLspConfig.systemUiInternationalNotificationStyleEnabled)
            }
            var systemUiHideQsEditEnabled by rememberSaveable {
                mutableStateOf(initialLspConfig.systemUiHideQsEditEnabled)
            }
            var systemUiHideQsSettingsEnabled by rememberSaveable {
                mutableStateOf(initialLspConfig.systemUiHideQsSettingsEnabled)
            }
            var systemUiHideQsTopCarrierEnabled by rememberSaveable {
                mutableStateOf(initialLspConfig.systemUiHideQsTopCarrierEnabled)
            }
            var systemUiHideQsMoreEnabled by rememberSaveable {
                mutableStateOf(initialLspConfig.systemUiHideQsMoreEnabled)
            }
            var systemUiForceNativeClipboardOverlayEnabled by rememberSaveable {
                mutableStateOf(initialLspConfig.systemUiForceNativeClipboardOverlayEnabled)
            }
            var settingsForceGoogleEntryEnabled by rememberSaveable {
                mutableStateOf(initialLspConfig.settingsForceGoogleEntryEnabled)
            }
            var gmsRegionRestrictionBypassEnabled by rememberSaveable {
                mutableStateOf(initialLspConfig.gmsRegionRestrictionBypassEnabled)
            }
            var extremeRefresh165Enabled by rememberSaveable {
                mutableStateOf(initialLspConfig.extremeRefresh165Enabled)
            }
            var permissionMonitorVisible by rememberSaveable {
                mutableStateOf(false)
            }
            var launcherLayoutUnlocked by rememberSaveable {
                mutableStateOf(false)
            }
            var assistantScreenOption by rememberSaveable {
                mutableStateOf(AssistantScreenOption.Default)
            }
            var recentTaskRadiusEnabled by rememberSaveable {
                mutableStateOf(initialLspConfig.recentTaskRadiusEnabled)
            }
            var recentTaskRadiusDp by rememberSaveable {
                mutableIntStateOf(initialLspConfig.recentTaskRadiusDp)
            }
            var aodEnhanceEnabled by rememberSaveable {
                mutableStateOf(initialLspConfig.aodEnhanceEnabled)
            }
            var aodInitDarkBrightness by rememberSaveable {
                mutableIntStateOf(initialLspConfig.aodInitDarkBrightness)
            }
            var aodInitBrightBrightness by rememberSaveable {
                mutableIntStateOf(initialLspConfig.aodInitBrightBrightness)
            }
            var aodRunningBrightnessMultiplier by rememberSaveable {
                mutableFloatStateOf(initialLspConfig.aodRunningBrightnessMultiplier)
            }
            var aodPanoramicSupportEnabled by rememberSaveable {
                mutableStateOf(initialLspConfig.aodPanoramicSupportEnabled)
            }
            var aodSettingsSwitchEnabled by rememberSaveable {
                mutableStateOf(initialLspConfig.aodSettingsSwitchEnabled)
            }
            var aodSingleClickBlockEnabled by rememberSaveable {
                mutableStateOf(initialLspConfig.aodSingleClickBlockEnabled)
            }
            var assistantPowerMode by rememberSaveable {
                mutableIntStateOf(initialLspConfig.assistantPowerMode)
            }
            var assistantGestureCircleEnabled by rememberSaveable {
                mutableStateOf(initialLspConfig.assistantGestureCircleEnabled)
            }
            var assistantGestureCircleC17Enabled by rememberSaveable {
                mutableStateOf(initialLspConfig.assistantGestureCircleC17Enabled)
            }
            var assistantNativePowerEnabled by rememberSaveable {
                mutableStateOf(initialLspConfig.assistantNativePowerEnabled)
            }
            var assistantNativeCircleEnabled by rememberSaveable {
                mutableStateOf(initialLspConfig.assistantNativeCircleEnabled)
            }
            var settingsEffectsReady by remember {
                mutableStateOf(false)
            }
            var resumeRefreshEnabled by remember {
                mutableStateOf(false)
            }
            fun applyLspConfigSnapshot(snapshot: LspConfig.UiSnapshot) {
                nativeNotifyIconEnabled = snapshot.nativeNotifyIconEnabled
                nativeNotificationBubblesEnabled = snapshot.nativeNotificationBubblesEnabled
                extremeRefresh165Enabled = snapshot.extremeRefresh165Enabled
                recentTaskRadiusEnabled = snapshot.recentTaskRadiusEnabled
                recentTaskRadiusDp = snapshot.recentTaskRadiusDp
                aodEnhanceEnabled = snapshot.aodEnhanceEnabled
                aodInitDarkBrightness = snapshot.aodInitDarkBrightness
                aodInitBrightBrightness = snapshot.aodInitBrightBrightness
                aodRunningBrightnessMultiplier = snapshot.aodRunningBrightnessMultiplier
                aodPanoramicSupportEnabled = snapshot.aodPanoramicSupportEnabled
                aodSettingsSwitchEnabled = snapshot.aodSettingsSwitchEnabled
                aodSingleClickBlockEnabled = snapshot.aodSingleClickBlockEnabled
                systemUiInternationalNetworkDisplayEnabled =
                    snapshot.systemUiInternationalNetworkDisplayEnabled
                systemUiHideMobileRoamingIndicatorEnabled =
                    snapshot.systemUiHideMobileRoamingIndicatorEnabled
                systemUiInternationalNotificationStyleEnabled =
                    snapshot.systemUiInternationalNotificationStyleEnabled
                systemUiHideQsEditEnabled = snapshot.systemUiHideQsEditEnabled
                systemUiHideQsSettingsEnabled = snapshot.systemUiHideQsSettingsEnabled
                systemUiHideQsTopCarrierEnabled = snapshot.systemUiHideQsTopCarrierEnabled
                systemUiHideQsMoreEnabled = snapshot.systemUiHideQsMoreEnabled
                systemUiForceNativeClipboardOverlayEnabled = snapshot.systemUiForceNativeClipboardOverlayEnabled
                settingsForceGoogleEntryEnabled = snapshot.settingsForceGoogleEntryEnabled
                gmsRegionRestrictionBypassEnabled = snapshot.gmsRegionRestrictionBypassEnabled
                assistantPowerMode = snapshot.assistantPowerMode
                assistantGestureCircleEnabled = snapshot.assistantGestureCircleEnabled
                assistantGestureCircleC17Enabled = snapshot.assistantGestureCircleC17Enabled
                assistantNativePowerEnabled = snapshot.assistantNativePowerEnabled
                assistantNativeCircleEnabled = snapshot.assistantNativeCircleEnabled
            }
            suspend fun applyRefreshStateWithoutWriteBack(state: StartupRefreshState) {
                settingsEffectsReady = false
                withFrameNanos { }
                applyLspConfigSnapshot(state.lspSnapshot)
                if (rootGranted != state.rootGranted) {
                    rootGranted = state.rootGranted
                }
                permissionMonitorVisible = state.permissionMonitorVisible
                launcherLayoutUnlocked = state.launcherLayoutUnlocked
                assistantScreenOption = state.assistantScreenOption
                withFrameNanos { }
                withFrameNanos { }
                settingsEffectsReady = true
            }
            LaunchedEffect(onboardingCompleted) {
                withFrameNanos { }
                withContext(Dispatchers.IO) {
                    AppLogStore.initialize(applicationContext)
                    AppUpdater.cleanupInstalledUpdate(this@MainActivity)
                }
                AppLogStore.i("App", "MainActivity started")
                if (!onboardingCompleted) {
                    settingsEffectsReady = false
                    resumeRefreshEnabled = false
                    return@LaunchedEffect
                }
                UpdateNotificationScheduler.schedule(this@MainActivity)
                UpdateNotificationScheduler.checkNow(this@MainActivity)
                delay(1_200)
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
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME && resumeRefreshEnabled) {
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
            LaunchedEffect(blurEffectEnabled) {
                if (!settingsEffectsReady) return@LaunchedEffect
                prefs.edit()
                    .putBoolean(PREF_BLUR_EFFECT_ENABLED, blurEffectEnabled)
                    .apply()
                AppLogStore.i("Settings", "Blur effect: $blurEffectEnabled")
            }
            LaunchedEffect(featurePageNewStyleEnabled) {
                if (!settingsEffectsReady) return@LaunchedEffect
                prefs.edit()
                    .putBoolean(PREF_FEATURE_PAGE_NEW_STYLE, featurePageNewStyleEnabled)
                    .apply()
                AppLogStore.i("Settings", "New Features page style: $featurePageNewStyleEnabled")
            }
            LaunchedEffect(featurePageVideoHidden) {
                if (!settingsEffectsReady) return@LaunchedEffect
                prefs.edit()
                    .putBoolean(PREF_FEATURE_PAGE_VIDEO_HIDDEN, featurePageVideoHidden)
                    .apply()
                AppLogStore.i("Settings", "Features page background video hidden: $featurePageVideoHidden")
            }
            LaunchedEffect(popDirectionFollowsSwipeEdge) {
                if (!settingsEffectsReady) return@LaunchedEffect
                prefs.edit()
                    .putBoolean(PREF_POP_DIRECTION_FOLLOWS_SWIPE_EDGE, popDirectionFollowsSwipeEdge)
                    .apply()
                AppLogStore.i("Settings", "Pop follows swipe edge: $popDirectionFollowsSwipeEdge")
            }
            LaunchedEffect(showFpsMonitor) {
                if (!settingsEffectsReady) return@LaunchedEffect
                prefs.edit()
                    .putBoolean(PREF_SHOW_FPS_MONITOR, showFpsMonitor)
                    .apply()
                AppLogStore.i("Settings", "Show FPS monitor: $showFpsMonitor")
            }
            LaunchedEffect(liquidGlassBottomBarEnabled) {
                if (!settingsEffectsReady) return@LaunchedEffect
                prefs.edit()
                    .putBoolean(PREF_LIQUID_GLASS_BOTTOM_BAR, liquidGlassBottomBarEnabled)
                    .apply()
                AppLogStore.i("Settings", "LiquidGlass bottom bar: $liquidGlassBottomBarEnabled")
            }
            LaunchedEffect(materialFloatingBottomBarEnabled) {
                if (!settingsEffectsReady) return@LaunchedEffect
                prefs.edit().putBoolean(PREF_MATERIAL_FLOATING_BOTTOM_BAR, materialFloatingBottomBarEnabled).apply()
            }
            LaunchedEffect(materialHapticsEnabled) {
                if (!settingsEffectsReady) return@LaunchedEffect
                prefs.edit().putBoolean(PREF_MATERIAL_HAPTICS, materialHapticsEnabled).apply()
            }
            LaunchedEffect(materialSwitchIconsEnabled) {
                if (!settingsEffectsReady) return@LaunchedEffect
                prefs.edit().putBoolean(PREF_MATERIAL_SWITCH_ICONS, materialSwitchIconsEnabled).apply()
            }
            LaunchedEffect(nativeNotifyIconEnabled) {
                if (!settingsEffectsReady) return@LaunchedEffect
                withContext(Dispatchers.IO) {
                    LspConfig.setNativeNotifyIconEnabled(this@MainActivity, nativeNotifyIconEnabled)
                }
                AppLogStore.i("NativeNotifyIcon", "Native notify icon toggle: $nativeNotifyIconEnabled")
            }
            LaunchedEffect(nativeNotificationBubblesEnabled) {
                if (!settingsEffectsReady) return@LaunchedEffect
                withContext(Dispatchers.IO) {
                    LspConfig.setNativeNotificationBubblesEnabled(
                        this@MainActivity,
                        nativeNotificationBubblesEnabled
                    )
                }
                AppLogStore.i(
                    "NativeNotificationBubbles",
                    "Native notification bubbles toggle: $nativeNotificationBubblesEnabled"
                )
            }
            LaunchedEffect(systemUiInternationalNetworkDisplayEnabled) {
                if (!settingsEffectsReady) return@LaunchedEffect
                withContext(Dispatchers.IO) {
                    LspConfig.setSystemUiInternationalNetworkDisplayEnabled(
                        this@MainActivity,
                        systemUiInternationalNetworkDisplayEnabled
                    )
                }
                AppLogStore.i(
                    "SystemUI",
                    "International network display: $systemUiInternationalNetworkDisplayEnabled"
                )
            }
            LaunchedEffect(systemUiHideMobileRoamingIndicatorEnabled) {
                if (!settingsEffectsReady) return@LaunchedEffect
                withContext(Dispatchers.IO) {
                    LspConfig.setSystemUiHideMobileRoamingIndicatorEnabled(
                        this@MainActivity,
                        systemUiHideMobileRoamingIndicatorEnabled
                    )
                }
                AppLogStore.i(
                    "SystemUI",
                    "Hide mobile roaming indicator: $systemUiHideMobileRoamingIndicatorEnabled"
                )
            }
            LaunchedEffect(systemUiInternationalNotificationStyleEnabled) {
                if (!settingsEffectsReady) return@LaunchedEffect
                withContext(Dispatchers.IO) {
                    LspConfig.setSystemUiInternationalNotificationStyleEnabled(
                        this@MainActivity,
                        systemUiInternationalNotificationStyleEnabled
                    )
                }
                AppLogStore.i(
                    "SystemUI",
                    "International notification style: $systemUiInternationalNotificationStyleEnabled"
                )
            }
            LaunchedEffect(systemUiHideQsEditEnabled) {
                if (!settingsEffectsReady) return@LaunchedEffect
                withContext(Dispatchers.IO) {
                    LspConfig.setSystemUiHideQsEditEnabled(this@MainActivity, systemUiHideQsEditEnabled)
                }
                AppLogStore.i("SystemUI", "Hide QS edit entry: $systemUiHideQsEditEnabled")
            }
            LaunchedEffect(systemUiHideQsSettingsEnabled) {
                if (!settingsEffectsReady) return@LaunchedEffect
                withContext(Dispatchers.IO) {
                    LspConfig.setSystemUiHideQsSettingsEnabled(this@MainActivity, systemUiHideQsSettingsEnabled)
                }
                AppLogStore.i("SystemUI", "Hide QS settings button: $systemUiHideQsSettingsEnabled")
            }
            LaunchedEffect(systemUiHideQsTopCarrierEnabled) {
                if (!settingsEffectsReady) return@LaunchedEffect
                withContext(Dispatchers.IO) {
                    LspConfig.setSystemUiHideQsTopCarrierEnabled(this@MainActivity, systemUiHideQsTopCarrierEnabled)
                }
                AppLogStore.i("SystemUI", "Hide QS top carrier: $systemUiHideQsTopCarrierEnabled")
            }
            LaunchedEffect(systemUiHideQsMoreEnabled) {
                if (!settingsEffectsReady) return@LaunchedEffect
                withContext(Dispatchers.IO) {
                    LspConfig.setSystemUiHideQsMoreEnabled(this@MainActivity, systemUiHideQsMoreEnabled)
                }
                AppLogStore.i("SystemUI", "Hide QS more entry: $systemUiHideQsMoreEnabled")
            }
            LaunchedEffect(systemUiForceNativeClipboardOverlayEnabled) {
                if (!settingsEffectsReady) return@LaunchedEffect
                withContext(Dispatchers.IO) {
                    LspConfig.setSystemUiForceNativeClipboardOverlayEnabled(
                        this@MainActivity,
                        systemUiForceNativeClipboardOverlayEnabled
                    )
                }
                AppLogStore.i(
                    "SystemUI",
                    "Force native clipboard overlay: $systemUiForceNativeClipboardOverlayEnabled"
                )
            }
            LaunchedEffect(settingsForceGoogleEntryEnabled) {
                if (!settingsEffectsReady) return@LaunchedEffect
                withContext(Dispatchers.IO) {
                    LspConfig.setSettingsForceGoogleEntryEnabled(
                        this@MainActivity,
                        settingsForceGoogleEntryEnabled
                    )
                }
                AppLogStore.i("SettingsHook", "Force Google entry: $settingsForceGoogleEntryEnabled")
            }
            LaunchedEffect(gmsRegionRestrictionBypassEnabled) {
                if (!settingsEffectsReady) return@LaunchedEffect
                withContext(Dispatchers.IO) {
                    LspConfig.setGmsRegionRestrictionBypassEnabled(
                        this@MainActivity,
                        gmsRegionRestrictionBypassEnabled,
                    )
                }
                AppLogStore.i(
                    "GmsRegion",
                    "CN-GMS restriction bypass: $gmsRegionRestrictionBypassEnabled",
                )
            }
            LaunchedEffect(extremeRefresh165Enabled) {
                if (!settingsEffectsReady) return@LaunchedEffect
                withContext(Dispatchers.IO) {
                    LspConfig.setExtremeRefresh165Enabled(this@MainActivity, extremeRefresh165Enabled)
                }
                AppLogStore.i("ExtremeRefresh165", "165Hz extreme refresh toggle: $extremeRefresh165Enabled")
            }
            LaunchedEffect(recentTaskRadiusEnabled) {
                if (!settingsEffectsReady) return@LaunchedEffect
                withContext(Dispatchers.IO) {
                    LspConfig.setRecentTaskRadiusEnabled(this@MainActivity, recentTaskRadiusEnabled)
                }
                AppLogStore.i("RecentTaskRadius", "Recent task radius toggle: $recentTaskRadiusEnabled")
            }
            LaunchedEffect(recentTaskRadiusDp) {
                if (!settingsEffectsReady) return@LaunchedEffect
                delay(CONFIG_WRITE_DEBOUNCE_MS)
                withContext(Dispatchers.IO) {
                    LspConfig.setRecentTaskRadiusDp(this@MainActivity, recentTaskRadiusDp)
                }
                AppLogStore.i("RecentTaskRadius", "Recent task radius dp: $recentTaskRadiusDp")
            }
            LaunchedEffect(aodEnhanceEnabled) {
                if (!settingsEffectsReady) return@LaunchedEffect
                withContext(Dispatchers.IO) {
                    LspConfig.setAodEnhanceEnabled(this@MainActivity, aodEnhanceEnabled)
                }
                AppLogStore.i("AodEnhance", "AOD enhance toggle: $aodEnhanceEnabled")
            }
            LaunchedEffect(aodInitDarkBrightness) {
                if (!settingsEffectsReady) return@LaunchedEffect
                delay(CONFIG_WRITE_DEBOUNCE_MS)
                withContext(Dispatchers.IO) {
                    LspConfig.setAodInitDarkBrightness(this@MainActivity, aodInitDarkBrightness)
                }
                AppLogStore.i("AodEnhance", "AOD init dark brightness: $aodInitDarkBrightness")
            }
            LaunchedEffect(aodInitBrightBrightness) {
                if (!settingsEffectsReady) return@LaunchedEffect
                delay(CONFIG_WRITE_DEBOUNCE_MS)
                withContext(Dispatchers.IO) {
                    LspConfig.setAodInitBrightBrightness(this@MainActivity, aodInitBrightBrightness)
                }
                AppLogStore.i("AodEnhance", "AOD init bright brightness: $aodInitBrightBrightness")
            }
            LaunchedEffect(aodRunningBrightnessMultiplier) {
                if (!settingsEffectsReady) return@LaunchedEffect
                delay(CONFIG_WRITE_DEBOUNCE_MS)
                withContext(Dispatchers.IO) {
                    LspConfig.setAodRunningBrightnessMultiplier(
                        this@MainActivity,
                        aodRunningBrightnessMultiplier
                    )
                }
                AppLogStore.i(
                    "AodEnhance",
                    "AOD running brightness multiplier: $aodRunningBrightnessMultiplier"
                )
            }
            LaunchedEffect(aodPanoramicSupportEnabled) {
                if (!settingsEffectsReady) return@LaunchedEffect
                withContext(Dispatchers.IO) {
                    LspConfig.setAodPanoramicSupportEnabled(
                        this@MainActivity,
                        aodPanoramicSupportEnabled
                    )
                }
                AppLogStore.i(
                    "AodEnhance",
                    "AOD panoramic support: $aodPanoramicSupportEnabled"
                )
            }
            LaunchedEffect(aodSettingsSwitchEnabled) {
                if (!settingsEffectsReady) return@LaunchedEffect
                withContext(Dispatchers.IO) {
                    LspConfig.setAodSettingsSwitchEnabled(
                        this@MainActivity,
                        aodSettingsSwitchEnabled
                    )
                }
                AppLogStore.i(
                    "AodEnhance",
                    "AOD settings switch support: $aodSettingsSwitchEnabled"
                )
            }
            LaunchedEffect(aodSingleClickBlockEnabled) {
                if (!settingsEffectsReady) return@LaunchedEffect
                withContext(Dispatchers.IO) {
                    LspConfig.setAodSingleClickBlockEnabled(
                        this@MainActivity,
                        aodSingleClickBlockEnabled
                    )
                }
                AppLogStore.i(
                    "AodEnhance",
                    "AOD single-click wake block: $aodSingleClickBlockEnabled"
                )
            }
            LaunchedEffect(assistantPowerMode) {
                if (!settingsEffectsReady) return@LaunchedEffect
                withContext(Dispatchers.IO) {
                    LspConfig.setAssistantPowerMode(this@MainActivity, assistantPowerMode)
                }
                AppLogStore.i("Assistant", "Power long press mode: $assistantPowerMode")
            }
            LaunchedEffect(assistantGestureCircleEnabled) {
                if (!settingsEffectsReady) return@LaunchedEffect
                withContext(Dispatchers.IO) {
                    LspConfig.setAssistantGestureCircleEnabled(
                        this@MainActivity,
                        assistantGestureCircleEnabled
                    )
                }
                AppLogStore.i("Assistant", "Gesture Circle to Search: $assistantGestureCircleEnabled")
            }
            LaunchedEffect(assistantGestureCircleC17Enabled) {
                if (!settingsEffectsReady) return@LaunchedEffect
                withContext(Dispatchers.IO) {
                    LspConfig.setAssistantGestureCircleC17Enabled(
                        this@MainActivity,
                        assistantGestureCircleC17Enabled
                    )
                }
                AppLogStore.i(
                    "Assistant",
                    "Gesture Circle to Search (ColorOS 17): $assistantGestureCircleC17Enabled"
                )
            }
            LaunchedEffect(assistantNativePowerEnabled) {
                if (!settingsEffectsReady) return@LaunchedEffect
                withContext(Dispatchers.IO) {
                    LspConfig.setAssistantNativePowerEnabled(
                        this@MainActivity,
                        assistantNativePowerEnabled,
                    )
                }
                AppLogStore.i(
                    "Assistant",
                    "Native international assistant route: $assistantNativePowerEnabled",
                )
            }
            LaunchedEffect(assistantNativeCircleEnabled) {
                if (!settingsEffectsReady) return@LaunchedEffect
                withContext(Dispatchers.IO) {
                    LspConfig.setAssistantNativeCircleEnabled(
                        this@MainActivity,
                        assistantNativeCircleEnabled,
                    )
                }
                AppLogStore.i(
                    "Assistant",
                    "Native Circle to Search route: $assistantNativeCircleEnabled",
                )
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
                            settingsEffectsReady = false
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

    /**
     * Re-localizes both the activation surface and the Root destination without recreating the
     * activity. ContextThemeWrapper keeps the real Activity in the base-context chain, which is
     * required by Root's window and lifecycle helpers once it is pre-rendered below onboarding.
     */
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
