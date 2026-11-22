package com.mi.onextbox.ui

import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import com.mi.onextbox.ui.screens.FeatureLaunchOrigin
import com.mi.onextbox.ui.screens.FeaturePageMode
import com.mi.onextbox.ui.settings.AboutPageMode
import com.mi.onextbox.ui.settings.SoftwareUpdateUiState
import com.mi.onextbox.ui.common.AppThemeMode
import com.mi.onextbox.ui.common.AppUiStyle
import com.mi.onextbox.ui.common.AssistantScreenOption
import com.mi.onextbox.ui.common.BottomTab
import com.mi.onextbox.ui.layout.BottomBarCollapseState
import com.mi.onextbox.ui.layout.BottomBarAutoCollapse
import com.mi.onextbox.ui.layout.FloatingBottomBarMode
import com.kyant.backdrop.backdrops.LayerBackdrop as LiquidLayerBackdrop
import io.github.suqi8.coui.kmp.blur.LayerBackdrop

@Stable
internal data class RootUiState(
    val currentTab: Int,
    val rootGranted: Boolean,
    val featurePageNewStyleEnabled: Boolean,
    val featurePageDynamicBackgroundActive: Boolean,
    val bottomBarStartupProgress: () -> Float,
    val bottomBarCollapse: BottomBarCollapseState,
    val hiddenFeatureSourceModes: Set<FeaturePageMode>,
    val featureExternalIconScales: Map<FeaturePageMode, () -> Float>,
    val popDirectionFollowsSwipeEdge: Boolean,
    val showFpsMonitor: Boolean,
    val floatingBottomBarMode: FloatingBottomBarMode,
    val bottomBarHideLabels: Boolean,
    val mainPageSwipeEnabled: Boolean,
    val subPageSwipeEnabled: Boolean,
    val appLanguageTag: String,
    val appThemeMode: AppThemeMode,
    val appUiStyle: AppUiStyle,
    val appThemeKeyColor: Long?,
    val appThemePaletteStyle: Int,
    val appThemeColorSpec: Int,
    val permissionMonitorVisible: Boolean,
    val nativeNotifyIconEnabled: Boolean,
    val nativeNotificationBubblesEnabled: Boolean,
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
    val extremeRefresh165Enabled: Boolean,
    val launcherLayoutUnlocked: Boolean,
    val assistantScreenOption: AssistantScreenOption,
    val recentTaskRadiusEnabled: Boolean,
    val recentTaskRadiusDp: Int,
    val aodEnhanceEnabled: Boolean,
    val aodInitDarkBrightness: Int,
    val aodInitBrightBrightness: Int,
    val aodRunningBrightnessMultiplier: Float,
    val aodAllDayUnlockEnabled: Boolean,
    val aodSingleClickBlockEnabled: Boolean,
    val assistantPowerMode: Int,
    val assistantGestureCircleEnabled: Boolean,
    val assistantGestureCircleC17Enabled: Boolean,
    val assistantNativePowerEnabled: Boolean,
    val assistantNativeCircleEnabled: Boolean,
    val visibleTabs: List<BottomTab>,
    val selectedIndex: Int,
    val bottomNavigationHeight: Dp,
    val blurBackdrop: LayerBackdrop?,
    val liquidBackdrop: LiquidLayerBackdrop?,
    val softwareUpdateState: SoftwareUpdateUiState,
)

@Stable
internal data class RootActions(
    val onTabChange: (Int) -> Unit,
    val onPopDirectionFollowsSwipeEdgeChange: (Boolean) -> Unit,
    val onShowFpsMonitorChange: (Boolean) -> Unit,
    val onFloatingBottomBarModeChange: (FloatingBottomBarMode) -> Unit,
    val onBottomBarAutoCollapseChange: (BottomBarAutoCollapse) -> Unit,
    val onBottomBarHideLabelsChange: (Boolean) -> Unit,
    val onMainPageSwipeEnabledChange: (Boolean) -> Unit,
    val onSubPageSwipeEnabledChange: (Boolean) -> Unit,
    val onAppLanguageChange: (String) -> Unit,
    val onAppThemeModeChange: (AppThemeMode) -> Unit,
    val onAppUiStyleChange: (AppUiStyle) -> Unit,
    val onAppThemeKeyColorChange: (Long?) -> Unit,
    val onAppThemePaletteStyleChange: (Int) -> Unit,
    val onAppThemeColorSpecChange: (Int) -> Unit,
    val onPermissionMonitorVisibleChange: (Boolean) -> Unit,
    val onNativeNotifyIconEnabledChange: (Boolean) -> Unit,
    val onNativeNotificationBubblesEnabledChange: (Boolean) -> Unit,
    val onSystemUiInternationalNetworkDisplayEnabledChange: (Boolean) -> Unit,
    val onSystemUiHideMobileRoamingIndicatorEnabledChange: (Boolean) -> Unit,
    val onSystemUiInternationalNotificationStyleEnabledChange: (Boolean) -> Unit,
    val onSystemUiHideQsEditEnabledChange: (Boolean) -> Unit,
    val onSystemUiHideQsSettingsEnabledChange: (Boolean) -> Unit,
    val onSystemUiHideQsTopCarrierEnabledChange: (Boolean) -> Unit,
    val onSystemUiHideQsMoreEnabledChange: (Boolean) -> Unit,
    val onSystemUiForceNativeClipboardOverlayEnabledChange: (Boolean) -> Unit,
    val onSettingsForceGoogleEntryEnabledChange: (Boolean) -> Unit,
    val onGmsRegionRestrictionBypassEnabledChange: (Boolean) -> Unit,
    val onExtremeRefresh165EnabledChange: (Boolean) -> Unit,
    val onLauncherLayoutUnlockedChange: (Boolean) -> Unit,
    val onAssistantScreenOptionChange: (AssistantScreenOption) -> Unit,
    val onRecentTaskRadiusEnabledChange: (Boolean) -> Unit,
    val onRecentTaskRadiusDpChange: (Int) -> Unit,
    val onAodEnhanceEnabledChange: (Boolean) -> Unit,
    val onAodInitDarkBrightnessChange: (Int) -> Unit,
    val onAodInitBrightBrightnessChange: (Int) -> Unit,
    val onAodRunningBrightnessMultiplierChange: (Float) -> Unit,
    val onAodAllDayUnlockEnabledChange: (Boolean) -> Unit,
    val onAodSingleClickBlockEnabledChange: (Boolean) -> Unit,
    val onAssistantPowerModeChange: (Int) -> Unit,
    val onAssistantGestureCircleEnabledChange: (Boolean) -> Unit,
    val onAssistantGestureCircleC17EnabledChange: (Boolean) -> Unit,
    val onAssistantNativePowerEnabledChange: (Boolean) -> Unit,
    val onAssistantNativeCircleEnabledChange: (Boolean) -> Unit,
    val onBottomNavigationHeightChange: (Int) -> Unit,
    val onBottomChromeBoundsChange: (FloatingBottomBarMode, Rect) -> Unit,
    val onHapticClick: () -> Unit,
    val popRootRoute: () -> Unit,
    val popToMainRoute: () -> Unit,
    val openFeatureSubPage: (FeaturePageMode) -> Unit,
    val openAppErrorDetail: (Long) -> Unit,
    val openFeatureSubPageFromMain: (FeaturePageMode, FeatureLaunchOrigin?) -> Unit,
    val openAboutSubPage: (AboutPageMode) -> Unit,
    val requestAboutAutoCheckUpdate: () -> Unit,
)
