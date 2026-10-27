package com.mi.onextbox.ui.screens

import com.mi.onextbox.lsp.SystemAdaptationVersion
import com.mi.onextbox.ui.common.rememberSystemAdaptationVersion

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import android.content.Context
import android.graphics.Bitmap
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspConfig
import com.mi.onextbox.lsp.PhoneManagerSettings
import com.mi.onextbox.lsp.ThemeStoreSettings
import com.mi.onextbox.lsp.SecurePaySettings
import com.mi.onextbox.lsp.GameAssistantSettings
import com.mi.onextbox.lsp.OShareSettings
import com.mi.onextbox.lsp.SoftwareUpdateSettings
import com.mi.onextbox.lsp.FluidCloudBrowserSettings
import com.mi.onextbox.ui.common.AssistantScreenOption
import com.mi.onextbox.ui.common.CouiConfirmDialog
import com.mi.onextbox.ui.common.ColorOsTopBarButton
import com.mi.onextbox.ui.common.restartScopePackages
import com.mi.onextbox.ui.settings.SettingsCardRow
import com.mi.onextbox.ui.settings.SettingsListGroup
import com.mi.onextbox.ui.settings.SettingsPageSurface
import io.github.suqi8.coui.kmp.basic.Checkbox
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Refresh
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

enum class FeaturePageMode(val isNestedPage: Boolean = false) {
    Main,
    Desktop,
    DesktopLayout(true),
    DesktopIcons(true),
    DesktopRecent(true),
    DesktopHiddenApps(true),
    DesktopPages(true),
    SystemUi,
    SystemUiNative(true),
    SystemUiDynamicColor(true),
    SystemUiStatusBar(true),
    SystemUiStatusBarLayout(true),
    SystemUiStatusBarNetwork(true),
    SystemUiStatusBarSpeed(true),
    SystemUiStatusBarPower(true),
    SystemUiStatusBarBattery(true),
    SystemUiStatusBarInteraction(true),
    SystemUiClock(true),
    SystemUiIconOrder(true),
    SystemUiFluidCloud(true),
    SystemUiFluidCloudMediaBlacklist(true),
    SystemUiNotificationCenter(true),
    SystemUiControlCenter(true),
    SystemUiCornerSettings(true),
    SystemUiPercentageSettings(true),
    SystemUiSideVolumePercentageSettings(true),
    SystemUiVolume(true),
    SystemUiSmallWindow(true),
    SystemUiLockScreen(true),
    SystemUiNavigationBar(true),
    NotificationRemoval(true),
    MobileNetwork,
    AndroidSystem,
    SideKey(true),
    Installer(true),
    CorePatch(true),
    Esim,
    EsimDiagnostics(true),
    AppMarket,
    Battery,
    TrafficManagement,
    PhoneManager,
    ThemeStore,
    Gallery,
    SecurePay,
    GameAssistant,
    OShare,
    SoftwareUpdate,
    Keyboard,
    QuickAppServices,
    FileManager,
    GoogleMessages,
    SystemMessages,
    AppErrors,
    NotificationBatch,
    Athena,
    Settings,
    SettingsRegion(true),
    SettingsRealme(true),
    SettingsAppearance(true),
    SettingsApps(true),
    SettingsPermissions(true),
    SettingsAboutDevice(true),
    SecurityPermission,
    TouchSampling,
    Wallpapers,
    Aod,
    Screenshot,
    ScreenRecording,
    Assistant,
    EnabledFeatures(true),
    Updates(true),
    Translation,
    AppInstaller,
    PackageInstaller,
    Camera,
}

data class FeatureLaunchOrigin(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val restingLeft: Float = left,
    val restingTop: Float = top,
    val restingWidth: Float = width,
    val restingHeight: Float = height,
    val hitLeft: Float = left,
    val hitTop: Float = top,
    val hitWidth: Float = width,
    val hitHeight: Float = height,
    val pressScale: Float = 1f,
    val gridLeft: Float = 0f,
    val gridTop: Float = 0f,
)

@Composable
fun FeatureMainRoute(
    modifier: Modifier,
    subPageBottomExtension: Dp,
    blurBackdrop: LayerBackdrop?,
    newStyleEnabled: Boolean,
    hiddenSourceModes: Set<FeaturePageMode> = emptySet(),
    externalIconScales: Map<FeaturePageMode, () -> Float> = emptyMap(),
    scrollResetKey: Int? = null,
    externalScrollState: ScrollState? = null,
    onGridPositionChanged: (Offset) -> Unit = {},
    onLaunchOriginChanged: (FeaturePageMode, FeatureLaunchOrigin) -> Unit = { _, _ -> },
    onOpen: (FeaturePageMode, FeatureLaunchOrigin?) -> Unit,
) {
    if (rememberSystemAdaptationVersion() == SystemAdaptationVersion.ColorOs16) {
        val searchState = rememberTextFieldState()
        val searchQuery = searchState.text.toString()
        androidx.activity.compose.BackHandler(enabled = searchQuery.isNotBlank()) {
            searchState.clearText()
        }
        SettingsPageSurface(
            title = stringResource(R.string.tab_features),
            modifier = modifier.fillMaxSize(),
            blurBackdrop = blurBackdrop,
            bottomContentPadding = subPageBottomExtension,
            scrollResetKey = scrollResetKey,
            externalScrollState = externalScrollState,
            actions = {
                FeatureUpdatesAction { onOpen(FeaturePageMode.Updates, null) }
                EnabledFeaturesAction { onOpen(FeaturePageMode.EnabledFeatures, null) }
            },
        ) {
            FeatureSearchBar(state = searchState)
            if (searchQuery.isNotBlank()) {
                FeatureSearchResults(query = searchQuery, onOpen = { onOpen(it, null) })
            } else {
                ColorOs16FeatureEntry { onOpen(it, null) }
            }
        }
        return
    }
    var showRestartConfirm by remember { mutableStateOf(false) }
    // The editor and result list read one state; no value/selection feedback loop.
    val searchState = rememberTextFieldState()
    val searchQuery = searchState.text.toString()
    androidx.activity.compose.BackHandler(enabled = searchQuery.isNotBlank()) {
        searchState.clearText()
    }
    val restartTargets = remember { allFeatureRestartPackages() }

    SettingsPageSurface(
        title = stringResource(R.string.tab_features),
        blurBackdrop = blurBackdrop,
        bottomContentPadding = subPageBottomExtension,
        scrollResetKey = scrollResetKey,
        externalScrollState = externalScrollState,
        // Keep the scroll modifier/focus tree stable when the editor first receives IME focus.
        contentScrollable = true,
        scrollEntranceEnabled = !newStyleEnabled,
        backgroundContent = if (newStyleEnabled) {
            // The video is owned by Root's stable base scene. Keeping an empty background slot
            // makes this page transparent without creating a second TextureView/MediaPlayer.
            {}
        } else {
            null
        },
        actions = {
            FeatureUpdatesAction { onOpen(FeaturePageMode.Updates, null) }
            EnabledFeaturesAction { onOpen(FeaturePageMode.EnabledFeatures, null) }
            FeatureRestartActionButton(
                contentDescription = stringResource(R.string.feature_restart_all_scope_title),
                onClick = { showRestartConfirm = true },
            )
        },
        modifier = modifier.fillMaxSize(),
    ) {
        FeatureSearchBar(state = searchState)
        if (searchQuery.isNotBlank()) {
            FeatureSearchResults(query = searchQuery, onOpen = { onOpen(it, null) })
        } else {
            FeatureMainPage(
                newStyleEnabled = newStyleEnabled,
                hiddenSourceModes = hiddenSourceModes,
                externalIconScales = externalIconScales,
                onGridPositionChanged = onGridPositionChanged,
                onLaunchOriginChanged = onLaunchOriginChanged,
                onOpen = onOpen,
            )
        }
    }

    FeatureRestartConfirmDialog(
        show = showRestartConfirm,
        title = stringResource(R.string.feature_restart_all_scope_confirm_title),
        targets = restartTargets,
        onDismissRequest = { showRestartConfirm = false },
    )
}

@Composable
fun FeatureSubRoute(
    modifier: Modifier,
    pageMode: FeaturePageMode,
    permissionMonitorVisible: Boolean,
    onPermissionMonitorVisibleChange: (Boolean) -> Unit,
    nativeNotifyIconEnabled: Boolean,
    onNativeNotifyIconEnabledChange: (Boolean) -> Unit,
    nativeNotificationBubblesEnabled: Boolean,
    onNativeNotificationBubblesEnabledChange: (Boolean) -> Unit,
    systemUiInternationalNetworkDisplayEnabled: Boolean,
    onSystemUiInternationalNetworkDisplayEnabledChange: (Boolean) -> Unit,
    systemUiHideMobileRoamingIndicatorEnabled: Boolean,
    onSystemUiHideMobileRoamingIndicatorEnabledChange: (Boolean) -> Unit,
    systemUiInternationalNotificationStyleEnabled: Boolean,
    onSystemUiInternationalNotificationStyleEnabledChange: (Boolean) -> Unit,
    systemUiHideQsEditEnabled: Boolean,
    onSystemUiHideQsEditEnabledChange: (Boolean) -> Unit,
    systemUiHideQsSettingsEnabled: Boolean,
    onSystemUiHideQsSettingsEnabledChange: (Boolean) -> Unit,
    systemUiHideQsTopCarrierEnabled: Boolean,
    onSystemUiHideQsTopCarrierEnabledChange: (Boolean) -> Unit,
    systemUiHideQsMoreEnabled: Boolean,
    onSystemUiHideQsMoreEnabledChange: (Boolean) -> Unit,
    systemUiForceNativeClipboardOverlayEnabled: Boolean,
    onSystemUiForceNativeClipboardOverlayEnabledChange: (Boolean) -> Unit,
    settingsForceGoogleEntryEnabled: Boolean,
    onSettingsForceGoogleEntryEnabledChange: (Boolean) -> Unit,
    gmsRegionRestrictionBypassEnabled: Boolean,
    onGmsRegionRestrictionBypassEnabledChange: (Boolean) -> Unit,
    extremeRefresh165Enabled: Boolean,
    onExtremeRefresh165EnabledChange: (Boolean) -> Unit,
    launcherLayoutUnlocked: Boolean,
    onLauncherLayoutUnlockedChange: (Boolean) -> Unit,
    assistantScreenOption: AssistantScreenOption,
    onAssistantScreenOptionChange: (AssistantScreenOption) -> Unit,
    recentTaskRadiusEnabled: Boolean,
    onRecentTaskRadiusEnabledChange: (Boolean) -> Unit,
    recentTaskRadiusDp: Int,
    onRecentTaskRadiusDpChange: (Int) -> Unit,
    aodEnhanceEnabled: Boolean,
    onAodEnhanceEnabledChange: (Boolean) -> Unit,
    aodInitDarkBrightness: Int,
    onAodInitDarkBrightnessChange: (Int) -> Unit,
    aodInitBrightBrightness: Int,
    onAodInitBrightBrightnessChange: (Int) -> Unit,
    aodRunningBrightnessMultiplier: Float,
    onAodRunningBrightnessMultiplierChange: (Float) -> Unit,
    aodAllDayUnlockEnabled: Boolean,
    onAodAllDayUnlockEnabledChange: (Boolean) -> Unit,
    aodSingleClickBlockEnabled: Boolean,
    onAodSingleClickBlockEnabledChange: (Boolean) -> Unit,
    assistantPowerMode: Int,
    onAssistantPowerModeChange: (Int) -> Unit,
    assistantGestureCircleEnabled: Boolean,
    onAssistantGestureCircleEnabledChange: (Boolean) -> Unit,
    assistantGestureCircleC17Enabled: Boolean,
    onAssistantGestureCircleC17EnabledChange: (Boolean) -> Unit,
    assistantNativePowerEnabled: Boolean,
    onAssistantNativePowerEnabledChange: (Boolean) -> Unit,
    assistantNativeCircleEnabled: Boolean,
    onAssistantNativeCircleEnabledChange: (Boolean) -> Unit,
    subPageBottomExtension: Dp,
    blurBackdrop: LayerBackdrop?,
    transitionScrollState: ScrollState,
    onBack: () -> Unit,
    onOpenSubPage: (FeaturePageMode) -> Unit,
    onOpenAppErrorDetail: (Long) -> Unit,
) {
    val adaptationVersion = rememberSystemAdaptationVersion()
    if (adaptationVersion == SystemAdaptationVersion.ColorOs16 &&
        !isColorOs16FeaturePageAvailable(pageMode)
    ) {
        AdaptationEmptyPage(
            title = featurePageTitle(pageMode),
            modifier = modifier.fillMaxSize(),
            bottomContentPadding = subPageBottomExtension,
            blurBackdrop = blurBackdrop,
            onBack = onBack,
        )
        return
    }
    val context = LocalContext.current
    val resources = LocalResources.current
    val subPageStateHolder = rememberSaveableStateHolder()
    if (pageMode == FeaturePageMode.Updates) {
        FeatureUpdatesPage(modifier.fillMaxSize(), subPageBottomExtension, blurBackdrop,
            transitionScrollState, onBack, onOpenSubPage)
        return
    }
    if (pageMode == FeaturePageMode.EnabledFeatures) {
        EnabledFeaturesPage(
            modifier = modifier.fillMaxSize(),
            bottomPadding = subPageBottomExtension,
            blurBackdrop = blurBackdrop,
            transitionScrollState = transitionScrollState,
            onBack = onBack,
            onOpen = onOpenSubPage,
        )
        return
    }
    if (pageMode == FeaturePageMode.AppErrors) {
        AppErrorTrackingRoute(modifier.fillMaxSize(), blurBackdrop, onBack, onOpenDetail = onOpenAppErrorDetail)
        return
    }
    if (pageMode == FeaturePageMode.NotificationBatch) {
        NotificationBatchPage(modifier.fillMaxSize(), blurBackdrop, onBack)
        return
    }
    if (pageMode == FeaturePageMode.SystemUiFluidCloudMediaBlacklist) {
        FluidCloudMediaBlacklistPage(modifier.fillMaxSize(), blurBackdrop, onBack)
        return
    }
    if (pageMode == FeaturePageMode.DesktopHiddenApps) {
        HideRecentAppsPage(modifier.fillMaxSize(), blurBackdrop, onBack)
        return
    }
    var restartConfirmMode by remember { mutableStateOf<FeaturePageMode?>(null) }
    var pushMonitorRefresh by remember { mutableStateOf(0) }
    val restartTargets = featureRestartPackages(pageMode, adaptationVersion)
    // The monitor owns its scroll padding and stays within the actual window size.
    val pageBottomExtension = if (pageMode == FeaturePageMode.SystemMessages) 0.dp else subPageBottomExtension

    SettingsPageSurface(
        title = featurePageTitle(pageMode),
        showBack = true,
        onBack = onBack,
        blurBackdrop = blurBackdrop,
        bottomContentPadding = pageBottomExtension,
        externalScrollState = transitionScrollState,
        contentScrollable = pageMode != FeaturePageMode.SystemMessages,
        edgeToEdgeBottom = pageMode == FeaturePageMode.SystemMessages,
        actions = {
            if (pageMode == FeaturePageMode.SystemMessages) {
                FeatureRestartActionButton(
                    contentDescription = stringResource(R.string.push_monitor_refresh),
                    onClick = { pushMonitorRefresh++ },
                )
            }
            if (pageMode != FeaturePageMode.EsimDiagnostics && pageMode != FeaturePageMode.TouchSampling && pageMode != FeaturePageMode.SystemMessages) {
                FeatureRestartActionButton(
                    contentDescription = stringResource(R.string.feature_restart_scope_confirm_action),
                    onClick = {
                        if (restartTargets.isEmpty()) {
                            Toast.makeText(
                                context,
                                resources.getString(R.string.feature_restart_scope_unsupported),
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            restartConfirmMode = pageMode
                        }
                    },
                )
            }
        },
        modifier = modifier
            .fillMaxSize()
            .extendPastBottom(pageBottomExtension),
    ) {
        subPageStateHolder.SaveableStateProvider(pageMode) {
            FeatureSubPage(
                mode = pageMode,
                permissionMonitorVisible = permissionMonitorVisible,
                onPermissionMonitorVisibleChange = onPermissionMonitorVisibleChange,
                nativeNotifyIconEnabled = nativeNotifyIconEnabled,
                onNativeNotifyIconEnabledChange = onNativeNotifyIconEnabledChange,
                nativeNotificationBubblesEnabled = nativeNotificationBubblesEnabled,
                onNativeNotificationBubblesEnabledChange = onNativeNotificationBubblesEnabledChange,
                systemUiInternationalNetworkDisplayEnabled =
                    systemUiInternationalNetworkDisplayEnabled,
                onSystemUiInternationalNetworkDisplayEnabledChange =
                    onSystemUiInternationalNetworkDisplayEnabledChange,
                systemUiHideMobileRoamingIndicatorEnabled =
                    systemUiHideMobileRoamingIndicatorEnabled,
                onSystemUiHideMobileRoamingIndicatorEnabledChange =
                    onSystemUiHideMobileRoamingIndicatorEnabledChange,
                systemUiInternationalNotificationStyleEnabled =
                    systemUiInternationalNotificationStyleEnabled,
                onSystemUiInternationalNotificationStyleEnabledChange =
                    onSystemUiInternationalNotificationStyleEnabledChange,
                systemUiHideQsEditEnabled = systemUiHideQsEditEnabled,
                onSystemUiHideQsEditEnabledChange = onSystemUiHideQsEditEnabledChange,
                systemUiHideQsSettingsEnabled = systemUiHideQsSettingsEnabled,
                onSystemUiHideQsSettingsEnabledChange = onSystemUiHideQsSettingsEnabledChange,
                systemUiHideQsTopCarrierEnabled = systemUiHideQsTopCarrierEnabled,
                onSystemUiHideQsTopCarrierEnabledChange = onSystemUiHideQsTopCarrierEnabledChange,
                systemUiHideQsMoreEnabled = systemUiHideQsMoreEnabled,
                onSystemUiHideQsMoreEnabledChange = onSystemUiHideQsMoreEnabledChange,
                systemUiForceNativeClipboardOverlayEnabled = systemUiForceNativeClipboardOverlayEnabled,
                onSystemUiForceNativeClipboardOverlayEnabledChange = onSystemUiForceNativeClipboardOverlayEnabledChange,
                settingsForceGoogleEntryEnabled = settingsForceGoogleEntryEnabled,
                onSettingsForceGoogleEntryEnabledChange = onSettingsForceGoogleEntryEnabledChange,
                gmsRegionRestrictionBypassEnabled = gmsRegionRestrictionBypassEnabled,
                onGmsRegionRestrictionBypassEnabledChange = onGmsRegionRestrictionBypassEnabledChange,
                extremeRefresh165Enabled = extremeRefresh165Enabled,
                onExtremeRefresh165EnabledChange = onExtremeRefresh165EnabledChange,
                launcherLayoutUnlocked = launcherLayoutUnlocked,
                onLauncherLayoutUnlockedChange = onLauncherLayoutUnlockedChange,
                assistantScreenOption = assistantScreenOption,
                onAssistantScreenOptionChange = onAssistantScreenOptionChange,
                recentTaskRadiusEnabled = recentTaskRadiusEnabled,
                onRecentTaskRadiusEnabledChange = onRecentTaskRadiusEnabledChange,
                recentTaskRadiusDp = recentTaskRadiusDp,
                onRecentTaskRadiusDpChange = onRecentTaskRadiusDpChange,
                aodEnhanceEnabled = aodEnhanceEnabled,
                onAodEnhanceEnabledChange = onAodEnhanceEnabledChange,
                aodInitDarkBrightness = aodInitDarkBrightness,
                onAodInitDarkBrightnessChange = onAodInitDarkBrightnessChange,
                aodInitBrightBrightness = aodInitBrightBrightness,
                onAodInitBrightBrightnessChange = onAodInitBrightBrightnessChange,
                aodRunningBrightnessMultiplier = aodRunningBrightnessMultiplier,
                onAodRunningBrightnessMultiplierChange = onAodRunningBrightnessMultiplierChange,
                aodAllDayUnlockEnabled = aodAllDayUnlockEnabled,
                onAodAllDayUnlockEnabledChange = onAodAllDayUnlockEnabledChange,
                aodSingleClickBlockEnabled = aodSingleClickBlockEnabled,
                onAodSingleClickBlockEnabledChange = onAodSingleClickBlockEnabledChange,
                assistantPowerMode = assistantPowerMode,
                onAssistantPowerModeChange = onAssistantPowerModeChange,
                assistantGestureCircleEnabled = assistantGestureCircleEnabled,
                onAssistantGestureCircleEnabledChange = onAssistantGestureCircleEnabledChange,
                assistantGestureCircleC17Enabled = assistantGestureCircleC17Enabled,
                onAssistantGestureCircleC17EnabledChange =
                    onAssistantGestureCircleC17EnabledChange,
                assistantNativePowerEnabled = assistantNativePowerEnabled,
                onAssistantNativePowerEnabledChange = onAssistantNativePowerEnabledChange,
                assistantNativeCircleEnabled = assistantNativeCircleEnabled,
                onAssistantNativeCircleEnabledChange = onAssistantNativeCircleEnabledChange,
                onOpenSubPage = onOpenSubPage,
                pushMonitorRefresh = pushMonitorRefresh,
            )
        }
    }

    val modeToRestart = restartConfirmMode
    FeatureRestartConfirmDialog(
        show = modeToRestart != null,
        title = stringResource(R.string.feature_restart_scope_confirm_title),
        targets = modeToRestart?.let { featureRestartPackages(it, adaptationVersion) }.orEmpty(),
        onDismissRequest = { restartConfirmMode = null },
    )
}

private fun Modifier.extendPastBottom(extra: Dp): Modifier = layout { measurable, constraints ->
    val extraPx = extra.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minHeight = constraints.minHeight + extraPx,
            maxHeight = constraints.maxHeight + extraPx,
        )
    )
    layout(placeable.width, constraints.maxHeight) {
        placeable.place(0, 0)
    }
}

@Composable
internal fun featurePageTitle(mode: FeaturePageMode): String = when (mode) {
    FeaturePageMode.EnabledFeatures -> stringResource(R.string.enabled_features_title)
    FeaturePageMode.Updates -> stringResource(R.string.feature_updates_title)
    FeaturePageMode.Main -> stringResource(R.string.tab_features)
    FeaturePageMode.Desktop -> stringResource(R.string.section_system_desktop)
    FeaturePageMode.DesktopLayout -> stringResource(R.string.desktop_category_layout)
    FeaturePageMode.DesktopIcons -> stringResource(R.string.desktop_category_icons)
    FeaturePageMode.DesktopRecent -> stringResource(R.string.feature_group_recent_tasks)
    FeaturePageMode.DesktopHiddenApps -> stringResource(R.string.hide_recent_apps_title)
    FeaturePageMode.DesktopPages -> stringResource(R.string.desktop_category_pages)
    FeaturePageMode.SystemUi -> stringResource(R.string.section_lsp)
    FeaturePageMode.SystemUiNative -> stringResource(R.string.feature_group_native)
    FeaturePageMode.SystemUiVolume -> stringResource(R.string.feature_group_volume)
    FeaturePageMode.SystemUiDynamicColor -> stringResource(R.string.feature_group_dynamic_color)
    FeaturePageMode.SystemUiClock -> stringResource(R.string.clock_page_title)
    FeaturePageMode.SystemUiStatusBarLayout -> stringResource(R.string.statusbar_layout_group)
    FeaturePageMode.SystemUiStatusBarNetwork -> stringResource(R.string.status_bar_group_network)
    FeaturePageMode.SystemUiStatusBarSpeed -> stringResource(R.string.network_speed_title)
    FeaturePageMode.SystemUiStatusBarPower -> stringResource(R.string.power_indicator_title)
    FeaturePageMode.SystemUiStatusBarBattery -> stringResource(R.string.status_bar_group_battery)
    FeaturePageMode.SystemUiStatusBarInteraction -> stringResource(R.string.status_bar_group_interaction)
    FeaturePageMode.SystemUiStatusBar -> stringResource(R.string.feature_group_beautify)
    FeaturePageMode.SystemUiIconOrder -> stringResource(R.string.statusbar_order_title)
    FeaturePageMode.SystemUiFluidCloud -> stringResource(R.string.feature_fluid_cloud_title)
    FeaturePageMode.SystemUiFluidCloudMediaBlacklist -> stringResource(R.string.fluid_media_apps_title)
    FeaturePageMode.SystemUiNotificationCenter -> stringResource(R.string.feature_group_notification_center)
    FeaturePageMode.SystemUiControlCenter -> stringResource(R.string.feature_group_control_center)
    FeaturePageMode.SystemUiCornerSettings -> stringResource(R.string.qs_tile_corner_settings_title)
    FeaturePageMode.SystemUiPercentageSettings -> stringResource(R.string.qs_slider_percentage_properties)
    FeaturePageMode.SystemUiSideVolumePercentageSettings -> stringResource(R.string.qs_slider_percentage_properties)
    FeaturePageMode.SystemUiSmallWindow -> stringResource(R.string.small_window_title)
    FeaturePageMode.SystemUiLockScreen -> stringResource(R.string.keyguard_page_title)
    FeaturePageMode.SystemUiNavigationBar -> stringResource(R.string.feature_group_navigation_bar)
    FeaturePageMode.NotificationRemoval -> stringResource(R.string.notification_removal_title)
    FeaturePageMode.MobileNetwork -> stringResource(R.string.feature_mobile_network_title)
    FeaturePageMode.AndroidSystem -> stringResource(R.string.feature_android_system_title)
    FeaturePageMode.CorePatch -> stringResource(R.string.corepatch_title)
    FeaturePageMode.SideKey -> stringResource(R.string.side_key_title)
    FeaturePageMode.Installer -> stringResource(R.string.installer_title)
    FeaturePageMode.Esim -> stringResource(R.string.feature_esim_title)
    FeaturePageMode.EsimDiagnostics -> stringResource(R.string.feature_group_esim_diagnostics)
    FeaturePageMode.AppMarket -> stringResource(R.string.feature_app_market_title)
    FeaturePageMode.Battery -> stringResource(R.string.battery_page_title)
    FeaturePageMode.TrafficManagement -> stringResource(R.string.traffic_page_title)
    FeaturePageMode.PhoneManager -> stringResource(R.string.phone_manager_title)
    FeaturePageMode.ThemeStore -> stringResource(R.string.theme_store_title)
    FeaturePageMode.Gallery -> stringResource(R.string.gallery_page_title)
    FeaturePageMode.SecurePay -> stringResource(R.string.secure_pay_title)
    FeaturePageMode.GameAssistant -> stringResource(R.string.game_assistant_title)
    FeaturePageMode.OShare -> stringResource(R.string.oshare_title)
    FeaturePageMode.SoftwareUpdate -> stringResource(R.string.ota_page_title)
    FeaturePageMode.Keyboard -> stringResource(R.string.keyboard_page_title)
    FeaturePageMode.QuickAppServices -> stringResource(R.string.feature_quick_app_services_title)
    FeaturePageMode.FileManager -> stringResource(R.string.feature_file_manager_title)
    FeaturePageMode.GoogleMessages -> stringResource(R.string.feature_google_messages_title)
    FeaturePageMode.SystemMessages -> stringResource(R.string.feature_system_messages_title)
    FeaturePageMode.AppErrors -> stringResource(R.string.app_errors_title)
    FeaturePageMode.NotificationBatch -> stringResource(R.string.notification_batch_title)
    FeaturePageMode.Athena -> stringResource(R.string.feature_group_athena)
    FeaturePageMode.Settings -> stringResource(R.string.tab_settings)
    FeaturePageMode.SettingsRegion -> stringResource(R.string.feature_group_settings_hidden_features)
    FeaturePageMode.SettingsRealme -> stringResource(R.string.settings_realme_title)
    FeaturePageMode.SettingsAppearance -> stringResource(R.string.settings_category_interface_display)
    FeaturePageMode.SettingsApps -> stringResource(R.string.feature_group_app_management)
    FeaturePageMode.SettingsPermissions -> stringResource(R.string.settings_category_permissions)
    FeaturePageMode.SettingsAboutDevice -> stringResource(R.string.feature_group_about_device)
    FeaturePageMode.SecurityPermission -> stringResource(R.string.feature_permission_manager_title)
    FeaturePageMode.TouchSampling -> stringResource(R.string.feature_touch_rate_title)
    FeaturePageMode.Wallpapers -> stringResource(R.string.feature_wallpapers_title)
    FeaturePageMode.Aod -> stringResource(R.string.feature_aod_enhance_title)
    FeaturePageMode.Screenshot -> stringResource(R.string.feature_screenshot_title)
    FeaturePageMode.ScreenRecording -> stringResource(R.string.feature_screen_recording_title)
    FeaturePageMode.Camera -> stringResource(R.string.camera_page_title)
    FeaturePageMode.Translation -> stringResource(R.string.feature_translation_title)
    FeaturePageMode.AppInstaller -> stringResource(R.string.installer_cleanup_title)
    FeaturePageMode.PackageInstaller -> stringResource(R.string.package_installer_title)
    FeaturePageMode.Assistant -> stringResource(R.string.feature_assistant_title)
}

internal fun featureRestartPackages(mode: FeaturePageMode): List<String> = when (mode) {
    FeaturePageMode.Desktop,
    FeaturePageMode.DesktopLayout,
    FeaturePageMode.DesktopRecent,
    FeaturePageMode.DesktopHiddenApps,
    FeaturePageMode.DesktopIcons,
    FeaturePageMode.DesktopPages -> listOf(
        "com.android.launcher",
    )
    FeaturePageMode.SystemUi,
    FeaturePageMode.SystemUiNative,
    FeaturePageMode.SystemUiVolume,
    FeaturePageMode.SystemUiDynamicColor,
    FeaturePageMode.SystemUiClock,
    FeaturePageMode.SystemUiStatusBar,
    FeaturePageMode.SystemUiStatusBarLayout,
    FeaturePageMode.SystemUiStatusBarNetwork,
    FeaturePageMode.SystemUiStatusBarSpeed,
    FeaturePageMode.SystemUiStatusBarPower,
    FeaturePageMode.SystemUiStatusBarBattery,
    FeaturePageMode.SystemUiStatusBarInteraction,
    FeaturePageMode.SystemUiIconOrder,
    FeaturePageMode.SystemUiFluidCloudMediaBlacklist,
    FeaturePageMode.SystemUiNotificationCenter,
    FeaturePageMode.SystemUiCornerSettings,
    FeaturePageMode.SystemUiPercentageSettings,
    FeaturePageMode.SystemUiSideVolumePercentageSettings,
    FeaturePageMode.SystemUiControlCenter -> listOf("com.android.systemui")
    FeaturePageMode.SystemUiFluidCloud -> listOf("com.android.systemui") + FluidCloudBrowserSettings.packages
    FeaturePageMode.SystemUiLockScreen -> listOf("com.android.systemui")
    FeaturePageMode.SystemUiNavigationBar -> listOf("android", "system", "com.android.systemui")
    FeaturePageMode.Screenshot -> listOf("android", "system", "com.oplus.screenshot")
    FeaturePageMode.ScreenRecording -> listOf("android", "system", "com.android.systemui", "com.oplus.screenrecorder")
    FeaturePageMode.Camera -> listOf(com.mi.onextbox.lsp.CameraGallerySettings.PACKAGE_NAME)
    FeaturePageMode.Translation -> listOf(com.mi.onextbox.lsp.ScreenTranslationSettings.PACKAGE_NAME)
    FeaturePageMode.AppInstaller -> listOf(com.mi.onextbox.lsp.InstallerCleanupSettings.APP_DETAIL)
    FeaturePageMode.PackageInstaller -> listOf(com.mi.onextbox.lsp.InstallerCleanupSettings.PACKAGE_INSTALLER)
    FeaturePageMode.NotificationRemoval -> listOf("android", "system", "com.android.systemui")
    FeaturePageMode.MobileNetwork -> listOf("com.android.phone")
    FeaturePageMode.AndroidSystem -> listOf("android", "system")
    FeaturePageMode.CorePatch -> emptyList() // Full device reboot, never kill system_server from this page.
    FeaturePageMode.SideKey -> listOf("android", "system", "com.android.systemui")
    FeaturePageMode.Installer -> listOf("android", "system")
    FeaturePageMode.Esim -> listOf("com.oplus.euicc")
    FeaturePageMode.AppMarket -> listOf("com.heytap.market")
    FeaturePageMode.Battery -> listOf("android", "system", "com.oplus.battery")
    FeaturePageMode.TrafficManagement -> listOf("com.oplus.trafficmonitor")
    FeaturePageMode.PhoneManager -> listOf(PhoneManagerSettings.PACKAGE_NAME)
    FeaturePageMode.ThemeStore -> listOf(ThemeStoreSettings.PACKAGE_NAME)
    FeaturePageMode.Gallery -> listOf(com.mi.onextbox.lsp.GalleryAiRetouchSettings.PACKAGE_NAME)
    FeaturePageMode.SecurePay -> listOf(SecurePaySettings.PACKAGE_NAME)
    FeaturePageMode.GameAssistant -> listOf(GameAssistantSettings.PACKAGE_NAME)
    FeaturePageMode.OShare -> listOf(OShareSettings.PACKAGE_NAME)
    FeaturePageMode.SoftwareUpdate -> listOf(SoftwareUpdateSettings.PACKAGE_NAME, SoftwareUpdateSettings.SYSTEM_FILES_PACKAGE_NAME)
    FeaturePageMode.Keyboard -> listOf("com.oplus.keyboard")
    FeaturePageMode.QuickAppServices -> listOf(
        "com.nearme.instant.platform", "com.android.launcher", "com.coloros.assistantscreen",
    )
    FeaturePageMode.FileManager -> listOf("android", "system", "com.coloros.filemanager")
    FeaturePageMode.GoogleMessages -> listOf("com.google.android.apps.messaging")
    FeaturePageMode.Athena -> listOf("android", "system", "com.oplus.athena")
    FeaturePageMode.Settings,
    FeaturePageMode.SettingsAppearance -> listOf("android", "system", "com.android.settings")
    FeaturePageMode.SettingsApps,
    FeaturePageMode.SettingsPermissions,
    FeaturePageMode.SettingsAboutDevice,
    FeaturePageMode.SettingsRegion,
    FeaturePageMode.SettingsRealme -> listOf("com.android.settings")
    FeaturePageMode.SecurityPermission -> listOf(
        "com.oplus.safecenter",
        "com.oplus.securitypermission", "com.android.settings",
        "com.android.permissioncontroller", "android", "system",
    )
    FeaturePageMode.Wallpapers -> listOf("com.oplus.wallpapers", "com.heytap.colorfulengine")
    FeaturePageMode.Aod -> listOf("com.oplus.aod")
    FeaturePageMode.Assistant -> listOf(
        "android",
        "system",
        "com.android.systemui",
        "com.google.android.googlequicksearchbox",
    )
    FeaturePageMode.EnabledFeatures,
    FeaturePageMode.Updates,
    FeaturePageMode.Main,
    // C17 flexible handles also live in system_server. Offer no misleading SystemUI-only restart.
    FeaturePageMode.SystemUiSmallWindow,
    FeaturePageMode.EsimDiagnostics,
    FeaturePageMode.SystemMessages,
    FeaturePageMode.AppErrors,
    FeaturePageMode.NotificationBatch,
    FeaturePageMode.TouchSampling -> emptyList()
}

private fun allFeatureRestartPackages(): List<String> = FeaturePageMode.entries
    .flatMap(::featureRestartPackages)
    .distinct()

@Composable
private fun FeatureRestartActionButton(
    contentDescription: String,
    onClick: () -> Unit,
) {

    ColorOsTopBarButton(
        onClick = onClick,
    ) {
        Icon(
            imageVector = COUIIcons.Refresh,
            contentDescription = contentDescription,
            tint = COUITheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun FeatureRestartConfirmDialog(
    show: Boolean,
    title: String,
    targets: List<String>,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val localeTag = LocalConfiguration.current.locales.toLanguageTags()
    val scope = rememberCoroutineScope()
    var lastVisibleTargets by remember { mutableStateOf(targets) }
    SideEffect {
        if (show) lastVisibleTargets = targets
    }
    val displayedTargets = if (show) targets else lastVisibleTargets
    val plan = remember(displayedTargets) { FeatureRestartPlan(displayedTargets) }
    val scopeLabels = remember(context, localeTag, plan) {
        plan.appPackages.map { packageName ->
            val label = when (packageName) {
                "com.android.settings" -> resources.getString(R.string.tab_settings)
                "com.android.systemui" -> resources.getString(R.string.section_lsp)
                "com.android.launcher" ->
                    resources.getString(R.string.section_system_desktop)
                else -> runCatching {
                    val info = context.packageManager.getApplicationInfo(packageName, 0)
                    context.packageManager.getApplicationLabel(info).toString()
                        .lineSequence().firstOrNull().orEmpty().trim().take(80)
                }.getOrDefault(packageName).ifBlank { packageName }
            }
            if (label == packageName) packageName else "$label ($packageName)"
        }
    }
    var restartSystem by remember(displayedTargets) { mutableStateOf(false) }
    LaunchedEffect(show) {
        if (show) restartSystem = false
    }
    val rebootDevice = plan.shouldRebootDevice(restartSystem)

    CouiConfirmDialog(
        show = show,
        title = title,
        summary = stringResource(
            when {
                plan.requiresDeviceReboot -> R.string.feature_restart_framework_only_summary
                rebootDevice -> R.string.feature_restart_device_summary
                else -> R.string.feature_restart_scope_confirm_summary
            },
        ),
        negativeText = stringResource(R.string.feature_restart_scope_confirm_cancel),
        positiveText = stringResource(
            if (rebootDevice) R.string.feature_restart_device_action
            else R.string.feature_restart_scope_confirm_action,
        ),
        onDismissRequest = onDismissRequest,
        onPositive = {
            val shouldRestartSystem = plan.shouldRebootDevice(restartSystem)
            onDismissRequest()
            scope.launch {
                val result = restartScopePackages(
                    packages = targets,
                    rebootSystem = shouldRestartSystem,
                )
                if (!shouldRestartSystem || !result.success) {
                    val message = if (result.success) {
                        resources.getString(R.string.feature_restart_scope_done)
                    } else {
                        result.detail?.takeIf { it.isNotBlank() }
                            ?: resources.getString(R.string.feature_restart_scope_failed)
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        },
        content = {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (scopeLabels.isNotEmpty()) {
                    FeatureRestartDialogText(
                        text = scopeLabels.joinToString("\n"),
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
                if (plan.includesFramework && !plan.requiresDeviceReboot) {
                    FeatureRestartDialogText(stringResource(R.string.feature_restart_framework_notice))
                }
            }
            if (!plan.requiresDeviceReboot) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 32.dp)
                        .toggleable(
                            value = restartSystem,
                            interactionSource = null,
                            indication = null,
                            role = Role.Checkbox,
                            onValueChange = { restartSystem = it },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    run {
                        Checkbox(
                            state = ToggleableState(restartSystem),
                            onClick = null,
                        )
                        Text(
                            text = stringResource(R.string.feature_restart_system_option),
                            modifier = Modifier.padding(start = 8.dp),
                            fontSize = 12.sp,
                            color = COUITheme.colorScheme.onBackgroundVariant,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun FeatureRestartDialogText(text: String, modifier: Modifier = Modifier) {
    run {
        Text(
            text = text,
            modifier = modifier,
            fontSize = 13.sp,
            color = COUITheme.colorScheme.onBackgroundVariant,
        )
    }
}

@Composable
private fun FeatureMainPage(
    newStyleEnabled: Boolean,
    hiddenSourceModes: Set<FeaturePageMode>,
    externalIconScales: Map<FeaturePageMode, () -> Float>,
    onGridPositionChanged: (Offset) -> Unit,
    onLaunchOriginChanged: (FeaturePageMode, FeatureLaunchOrigin) -> Unit,
    onOpen: (FeaturePageMode, FeatureLaunchOrigin?) -> Unit,
) {
    val entries = remember { colorOs17FeatureMainEntries() }


    if (newStyleEnabled) {
        Box(modifier = Modifier.fillMaxSize()) {
            FeatureLauncherGrid(
                entries = entries,
                hiddenSourceModes = hiddenSourceModes,
                externalIconScales = externalIconScales,
                onGridPositionChanged = onGridPositionChanged,
                onLaunchOriginChanged = { mode, origin ->
                    // Keep icon bounds unscaled, track grid movement separately.
                    if (hiddenSourceModes.isEmpty()) {
                        onLaunchOriginChanged(mode, origin)
                    }
                },
                onOpen = onOpen,
            )
        }
    } else {
        ClassicMainEntryList(entries = entries, onOpen = { onOpen(it, null) })
    }
}

@Composable
internal fun ToolsMainPage(onOpen: (FeaturePageMode) -> Unit) {
    val entries = remember { colorOs17ToolsMainEntries() }
    run {
        ClassicMainEntryList(entries = entries, onOpen = onOpen)
    }
}

@Composable
private fun ClassicMainEntryList(
    entries: List<FeatureMainEntry>,
    onOpen: (FeaturePageMode) -> Unit,
) {
    SettingsListGroup(entries, key = { it.pageMode }) { index, entry ->
        FeatureEntryRow(
            title = stringResource(entry.titleRes),
            iconPackages = entry.iconPackages,
            iconGlyph = entry.iconGlyph,
            onClick = { onOpen(entry.pageMode) },
            hasDividerAbove = index > 0,
            hasDividerBelow = index < entries.lastIndex,
        )
    }
}


private fun colorOs17FeatureMainEntries(): List<FeatureMainEntry> = buildList {
        add(
            FeatureMainEntry(
                titleRes = R.string.section_system_desktop,
                iconPackages = listOf("com.android.launcher", "com.oplus.launcher", "com.coloros.launcher"),
                pageMode = FeaturePageMode.Desktop,
            )
        )
        add(
            FeatureMainEntry(
                titleRes = R.string.section_lsp,
                iconPackages = listOf("com.android.systemui"),
                pageMode = FeaturePageMode.SystemUi,
            )
        )
        add(
            FeatureMainEntry(
                titleRes = R.string.tab_settings,
                iconPackages = listOf("com.android.settings"),
                pageMode = FeaturePageMode.Settings,
            )
        )
        add(
            FeatureMainEntry(
                titleRes = R.string.feature_permission_manager_title,
                iconPackages = listOf("com.oplus.securitypermission"),
                pageMode = FeaturePageMode.SecurityPermission,
            )
        )
        add(
            FeatureMainEntry(
                titleRes = R.string.phone_manager_title,
                iconPackages = listOf(PhoneManagerSettings.PACKAGE_NAME),
                pageMode = FeaturePageMode.PhoneManager,
            )
        )
        add(
            FeatureMainEntry(
                titleRes = R.string.secure_pay_title,
                iconPackages = listOf(SecurePaySettings.PACKAGE_NAME),
                pageMode = FeaturePageMode.SecurePay,
            )
        )
        add(
            FeatureMainEntry(
                titleRes = R.string.game_assistant_title,
                iconPackages = listOf(GameAssistantSettings.PACKAGE_NAME),
                pageMode = FeaturePageMode.GameAssistant,
            )
        )
        add(
            FeatureMainEntry(
                titleRes = R.string.theme_store_title,
                iconPackages = listOf(ThemeStoreSettings.PACKAGE_NAME),
                pageMode = FeaturePageMode.ThemeStore,
            )
        )
        add(
            FeatureMainEntry(
                titleRes = R.string.camera_page_title,
                iconPackages = listOf(com.mi.onextbox.lsp.CameraGallerySettings.PACKAGE_NAME),
                pageMode = FeaturePageMode.Camera,
            )
        )
        add(
            FeatureMainEntry(
                titleRes = R.string.gallery_page_title,
                iconPackages = listOf(com.mi.onextbox.lsp.GalleryAiRetouchSettings.PACKAGE_NAME),
                pageMode = FeaturePageMode.Gallery,
            )
        )
        add(
            FeatureMainEntry(
                titleRes = R.string.oshare_title,
                iconPackages = listOf(OShareSettings.PACKAGE_NAME),
                pageMode = FeaturePageMode.OShare,
            )
        )
        add(
            FeatureMainEntry(
                titleRes = R.string.ota_page_title,
                iconPackages = listOf(SoftwareUpdateSettings.PACKAGE_NAME),
                pageMode = FeaturePageMode.SoftwareUpdate,
            )
        )
        add(
            FeatureMainEntry(
                titleRes = R.string.feature_file_manager_title,
                iconPackages = listOf("com.coloros.filemanager"),
                pageMode = FeaturePageMode.FileManager,
            )
        )
        add(
            FeatureMainEntry(
                titleRes = R.string.battery_page_title,
                iconPackages = listOf("com.oplus.battery"),
                pageMode = FeaturePageMode.Battery,
            )
        )
        add(
            FeatureMainEntry(
                titleRes = R.string.traffic_page_title,
                iconPackages = listOf("com.oplus.trafficmonitor"),
                pageMode = FeaturePageMode.TrafficManagement,
            )
        )
        add(
            FeatureMainEntry(
                titleRes = R.string.keyboard_page_title,
                iconPackages = listOf("com.oplus.keyboard"),
                pageMode = FeaturePageMode.Keyboard,
            )
        )
        add(
            FeatureMainEntry(
                titleRes = R.string.feature_wallpapers_title,
                iconPackages = listOf("com.oplus.wallpapers"),
                pageMode = FeaturePageMode.Wallpapers,
            )
        )
        add(
            FeatureMainEntry(
                titleRes = R.string.feature_mobile_network_title,
                iconPackages = listOf("com.android.phone"),
                pageMode = FeaturePageMode.MobileNetwork,
            )
        )
        add(
            FeatureMainEntry(
                titleRes = R.string.feature_aod_enhance_title,
                iconPackages = listOf("com.oplus.aod"),
                pageMode = FeaturePageMode.Aod,
            )
        )
        add(
            FeatureMainEntry(
                titleRes = R.string.feature_screenshot_title,
                iconPackages = listOf("com.oplus.screenshot", "com.coloros.screenshot"),
                pageMode = FeaturePageMode.Screenshot,
            )
        )
        add(
            FeatureMainEntry(
                titleRes = R.string.feature_screen_recording_title,
                iconPackages = listOf("com.oplus.screenrecorder", "com.coloros.screenrecorder"),
                pageMode = FeaturePageMode.ScreenRecording,
            )
        )
        add(
            FeatureMainEntry(
                titleRes = R.string.feature_assistant_title,
                iconPackages = listOf(
                    "com.heytap.speechassist",
                    "com.coloros.speechassist",
                    "com.oplus.speechassist",
                    "com.google.android.googlequicksearchbox",
                ),
                pageMode = FeaturePageMode.Assistant,
            )
        )
        add(
            FeatureMainEntry(
                titleRes = R.string.feature_translation_title,
                iconPackages = listOf(com.mi.onextbox.lsp.ScreenTranslationSettings.PACKAGE_NAME),
                pageMode = FeaturePageMode.Translation,
            )
        )
        add(
            FeatureMainEntry(
                titleRes = R.string.installer_cleanup_title,
                iconPackages = listOf(com.mi.onextbox.lsp.InstallerCleanupSettings.APP_DETAIL),
                pageMode = FeaturePageMode.AppInstaller,
            )
        )
        add(
            FeatureMainEntry(
                titleRes = R.string.package_installer_title,
                iconPackages = listOf(com.mi.onextbox.lsp.InstallerCleanupSettings.PACKAGE_INSTALLER),
                pageMode = FeaturePageMode.PackageInstaller,
            )
        )
        add(
            FeatureMainEntry(
                titleRes = R.string.feature_android_system_title,
                // The framework package supplies the Android system icon shown in the scope UI.
                iconPackages = listOf("android"),
                pageMode = FeaturePageMode.AndroidSystem,
            )
        )
        add(
            FeatureMainEntry(
                titleRes = R.string.feature_esim_title,
                iconPackages = listOf("com.oplus.euicc"),
                pageMode = FeaturePageMode.Esim,
            )
        )
        add(
            FeatureMainEntry(
                titleRes = R.string.feature_app_market_title,
                iconPackages = listOf("com.heytap.market"),
                pageMode = FeaturePageMode.AppMarket,
            )
        )
        add(
            FeatureMainEntry(
                titleRes = R.string.feature_quick_app_services_title,
                iconPackages = listOf("com.nearme.instant.platform"),
                pageMode = FeaturePageMode.QuickAppServices,
            )
        )
        add(
            FeatureMainEntry(
                titleRes = R.string.feature_google_messages_title,
                iconPackages = listOf("com.google.android.apps.messaging"),
                pageMode = FeaturePageMode.GoogleMessages,
            )
        )
        add(
            FeatureMainEntry(
                titleRes = R.string.feature_group_athena,
                iconPackages = listOf("com.oplus.athena"),
                pageMode = FeaturePageMode.Athena,
            )
        )
}

private fun colorOs17ToolsMainEntries(): List<FeatureMainEntry> = listOf(
    FeatureMainEntry(
        titleRes = R.string.notification_batch_title,
        iconPackages = emptyList(),
        pageMode = FeaturePageMode.NotificationBatch,
        iconGlyph = Icons.Rounded.Notifications,
    ),
    FeatureMainEntry(
        titleRes = R.string.app_errors_title,
        iconPackages = emptyList(),
        pageMode = FeaturePageMode.AppErrors,
        iconGlyph = Icons.Rounded.BugReport,
    ),
    FeatureMainEntry(
        titleRes = R.string.feature_touch_rate_title,
        iconPackages = listOf("com.coloros.gamesettings", "com.oplus.games"),
        pageMode = FeaturePageMode.TouchSampling,
    ),
    FeatureMainEntry(
        titleRes = R.string.feature_system_messages_title,
        iconPackages = listOf("com.heytap.mcs"),
        pageMode = FeaturePageMode.SystemMessages,
    ),
)

@Composable
private fun FeatureLauncherGrid(
    entries: List<FeatureMainEntry>,
    hiddenSourceModes: Set<FeaturePageMode>,
    externalIconScales: Map<FeaturePageMode, () -> Float>,
    onGridPositionChanged: (Offset) -> Unit,
    onLaunchOriginChanged: (FeaturePageMode, FeatureLaunchOrigin) -> Unit,
    onOpen: (FeaturePageMode, FeatureLaunchOrigin?) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .onGloballyPositioned { coordinates ->
                onGridPositionChanged(coordinates.localToWindow(Offset.Zero))
            },
    ) {
        val columnCount = (maxWidth / 82.dp).toInt().coerceIn(2, 4)
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            entries.chunked(columnCount).forEach { rowEntries ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    rowEntries.forEach { entry ->
                        FeatureLauncherItem(
                            entry = entry,
                            sourceIconHidden = entry.pageMode in hiddenSourceModes,
                            externallyPressedScale = externalIconScales[entry.pageMode] ?: { 1f },
                            onLaunchOriginChanged = onLaunchOriginChanged,
                            onOpen = onOpen,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(columnCount - rowEntries.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureLauncherItem(
    entry: FeatureMainEntry,
    sourceIconHidden: Boolean,
    externallyPressedScale: () -> Float,
    onLaunchOriginChanged: (FeaturePageMode, FeatureLaunchOrigin) -> Unit,
    onOpen: (FeaturePageMode, FeatureLaunchOrigin?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale = animateFloatAsState(
        targetValue = if (pressed) 0.85f else 1f,
        animationSpec = tween(
            durationMillis = 200,
            easing = FastOutSlowInEasing,
        ),
        label = "featureLauncherPress",
    )
    var launchOrigin by remember(entry.pageMode) { mutableStateOf<FeatureLaunchOrigin?>(null) }
    var launchPivotX by remember(entry.pageMode) { mutableStateOf(0f) }
    var launchPivotY by remember(entry.pageMode) { mutableStateOf(0f) }
    var hitLeft by remember(entry.pageMode) { mutableStateOf(0f) }
    var hitTop by remember(entry.pageMode) { mutableStateOf(0f) }
    var hitWidth by remember(entry.pageMode) { mutableStateOf(0f) }
    var hitHeight by remember(entry.pageMode) { mutableStateOf(0f) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .height(104.dp)
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.featureUnclippedBoundsInWindow()
                launchPivotX = bounds.center.x
                launchPivotY = bounds.center.y
                hitLeft = bounds.left
                hitTop = bounds.top
                hitWidth = bounds.width
                hitHeight = bounds.height
            }
            .graphicsLayer {
                val resolvedScale = minOf(externallyPressedScale(), pressScale.value)
                scaleX = resolvedScale
                scaleY = resolvedScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    val restingOrigin = launchOrigin
                    val capturedOrigin = restingOrigin?.let { origin ->
                        // C17 captures the icon container's live press matrix
                        // before resetting the source view. Freeze that visual
                        // rect so the floating surface starts on the exact last
                        // pressed pixels instead of jumping straight back to 1x.
                        val capturedScale = minOf(pressScale.value, externallyPressedScale())
                            .coerceIn(0.85f, 1f)
                        val capturedWidth = origin.width * capturedScale
                        val capturedHeight = origin.height * capturedScale
                        val capturedLeft = launchPivotX +
                            (origin.left - launchPivotX) * capturedScale
                        val capturedTop = launchPivotY +
                            (origin.top - launchPivotY) * capturedScale
                        FeatureLaunchOrigin(
                            left = capturedLeft,
                            top = capturedTop,
                            width = capturedWidth,
                            height = capturedHeight,
                            restingLeft = origin.left,
                            restingTop = origin.top,
                            restingWidth = origin.width,
                            restingHeight = origin.height,
                            hitLeft = origin.hitLeft,
                            hitTop = origin.hitTop,
                            hitWidth = origin.hitWidth,
                            hitHeight = origin.hitHeight,
                            pressScale = capturedScale,
                        )
                    }
                    onOpen(entry.pageMode, capturedOrigin)
                },
            )
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.featureUnclippedBoundsInWindow()
                    val iconInsetPx = with(density) { 4.dp.toPx() }
                    val measured = FeatureLaunchOrigin(
                        left = bounds.left + iconInsetPx,
                        top = bounds.top + iconInsetPx,
                        width = (bounds.width - iconInsetPx * 2f).coerceAtLeast(1f),
                        height = (bounds.height - iconInsetPx * 2f).coerceAtLeast(1f),
                        hitLeft = if (hitWidth > 0f) hitLeft else bounds.left,
                        hitTop = if (hitHeight > 0f) hitTop else bounds.top,
                        hitWidth = if (hitWidth > 0f) hitWidth else bounds.width,
                        hitHeight = if (hitHeight > 0f) hitHeight else bounds.height,
                    )
                    // Parent graphicsLayer press feedback does not participate
                    // in layout. Keep the stored endpoint from the resting
                    // frame so the 0.85 transform is applied exactly once in
                    // onClick, even if global coordinates are refreshed while
                    // the finger is down.
                    if (
                        (pressScale.value >= 0.999f && externallyPressedScale() >= 0.999f) ||
                        launchOrigin == null
                    ) {
                        launchOrigin = measured
                        onLaunchOriginChanged(entry.pageMode, measured)
                    }
                }
                // C17 hands the source icon to a floating icon surface for the
                // whole transition. Preserve this measured slot but hide its
                // pixels so the static and floating halos never overlap.
                .graphicsLayer {
                    alpha = if (sourceIconHidden) 0f else 1f
                },
        ) {
            FeatureIcon(
                packageNames = entry.iconPackages,
                iconGlyph = entry.iconGlyph,
                containerSize = 62.dp,
                appIconSize = 54.dp,
                fallbackIconSize = 34.dp,
                cornerRadius = 16.dp,
            )
        }
        Text(
            text = stringResource(entry.titleRes),
            color = COUITheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp),
        )
    }
}

private fun LayoutCoordinates.featureUnclippedBoundsInWindow(): Rect = Rect(
    localToWindow(Offset.Zero),
    localToWindow(Offset(size.width.toFloat(), size.height.toFloat())),
)

private data class FeatureMainEntry(
    @param:StringRes val titleRes: Int,
    val iconPackages: List<String>,
    val pageMode: FeaturePageMode,
    val iconGlyph: ImageVector? = null,
)

@Composable
internal fun FeatureLaunchIcon(
    pageMode: FeaturePageMode,
    visualSize: Dp = 54.dp,
    surfaceHeight: Dp = visualSize,
    modifier: Modifier = Modifier,
) {
    val entry = remember(pageMode) {
        (colorOs17FeatureMainEntries() + colorOs17ToolsMainEntries()).firstOrNull { it.pageMode == pageMode }
    } ?: return
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier,
    ) {
        FeatureIcon(
            packageNames = entry.iconPackages,
            iconGlyph = entry.iconGlyph,
            containerSize = visualSize,
            containerHeight = surfaceHeight,
            appIconSize = visualSize,
            fallbackIconSize = visualSize * (34f / 54f),
            cornerRadius = visualSize * (16f / 54f),
            // Keep the app's source halo radius stable while the surface grows;
            // scaling the blur with the leash made a huge flash around the icon.
            shadowRadius = 7.dp,
            extendPlate = surfaceHeight > visualSize,
        )
    }
}

@Composable
private fun FeatureSubPage(
    mode: FeaturePageMode,
    permissionMonitorVisible: Boolean,
    onPermissionMonitorVisibleChange: (Boolean) -> Unit,
    nativeNotifyIconEnabled: Boolean,
    onNativeNotifyIconEnabledChange: (Boolean) -> Unit,
    nativeNotificationBubblesEnabled: Boolean,
    onNativeNotificationBubblesEnabledChange: (Boolean) -> Unit,
    systemUiInternationalNetworkDisplayEnabled: Boolean,
    onSystemUiInternationalNetworkDisplayEnabledChange: (Boolean) -> Unit,
    systemUiHideMobileRoamingIndicatorEnabled: Boolean,
    onSystemUiHideMobileRoamingIndicatorEnabledChange: (Boolean) -> Unit,
    systemUiInternationalNotificationStyleEnabled: Boolean,
    onSystemUiInternationalNotificationStyleEnabledChange: (Boolean) -> Unit,
    systemUiHideQsEditEnabled: Boolean,
    onSystemUiHideQsEditEnabledChange: (Boolean) -> Unit,
    systemUiHideQsSettingsEnabled: Boolean,
    onSystemUiHideQsSettingsEnabledChange: (Boolean) -> Unit,
    systemUiHideQsTopCarrierEnabled: Boolean,
    onSystemUiHideQsTopCarrierEnabledChange: (Boolean) -> Unit,
    systemUiHideQsMoreEnabled: Boolean,
    onSystemUiHideQsMoreEnabledChange: (Boolean) -> Unit,
    systemUiForceNativeClipboardOverlayEnabled: Boolean,
    onSystemUiForceNativeClipboardOverlayEnabledChange: (Boolean) -> Unit,
    settingsForceGoogleEntryEnabled: Boolean,
    onSettingsForceGoogleEntryEnabledChange: (Boolean) -> Unit,
    gmsRegionRestrictionBypassEnabled: Boolean,
    onGmsRegionRestrictionBypassEnabledChange: (Boolean) -> Unit,
    extremeRefresh165Enabled: Boolean,
    onExtremeRefresh165EnabledChange: (Boolean) -> Unit,
    launcherLayoutUnlocked: Boolean,
    onLauncherLayoutUnlockedChange: (Boolean) -> Unit,
    assistantScreenOption: AssistantScreenOption,
    onAssistantScreenOptionChange: (AssistantScreenOption) -> Unit,
    recentTaskRadiusEnabled: Boolean,
    onRecentTaskRadiusEnabledChange: (Boolean) -> Unit,
    recentTaskRadiusDp: Int,
    onRecentTaskRadiusDpChange: (Int) -> Unit,
    aodEnhanceEnabled: Boolean,
    onAodEnhanceEnabledChange: (Boolean) -> Unit,
    aodInitDarkBrightness: Int,
    onAodInitDarkBrightnessChange: (Int) -> Unit,
    aodInitBrightBrightness: Int,
    onAodInitBrightBrightnessChange: (Int) -> Unit,
    aodRunningBrightnessMultiplier: Float,
    onAodRunningBrightnessMultiplierChange: (Float) -> Unit,
    aodAllDayUnlockEnabled: Boolean,
    onAodAllDayUnlockEnabledChange: (Boolean) -> Unit,
    aodSingleClickBlockEnabled: Boolean,
    onAodSingleClickBlockEnabledChange: (Boolean) -> Unit,
    assistantPowerMode: Int,
    onAssistantPowerModeChange: (Int) -> Unit,
    assistantGestureCircleEnabled: Boolean,
    onAssistantGestureCircleEnabledChange: (Boolean) -> Unit,
    assistantGestureCircleC17Enabled: Boolean,
    onAssistantGestureCircleC17EnabledChange: (Boolean) -> Unit,
    assistantNativePowerEnabled: Boolean,
    onAssistantNativePowerEnabledChange: (Boolean) -> Unit,
    assistantNativeCircleEnabled: Boolean,
    onAssistantNativeCircleEnabledChange: (Boolean) -> Unit,
    onOpenSubPage: (FeaturePageMode) -> Unit,
    pushMonitorRefresh: Int,
) {
    if (rememberSystemAdaptationVersion() == SystemAdaptationVersion.ColorOs16 &&
        mode != FeaturePageMode.Aod && mode != FeaturePageMode.SystemMessages) {
        ColorOs16Features(mode, onOpenSubPage)
        return
    }
    when (mode) {
        FeaturePageMode.Desktop -> DesktopCategoriesPage(onOpenSubPage)
        FeaturePageMode.DesktopLayout,
        FeaturePageMode.DesktopIcons,
        FeaturePageMode.DesktopRecent,
        FeaturePageMode.DesktopPages -> DesktopFeaturesPage(
            mode = mode,
            launcherLayoutUnlocked = launcherLayoutUnlocked,
            onLauncherLayoutUnlockedChange = onLauncherLayoutUnlockedChange,
            assistantScreenOption = assistantScreenOption,
            onAssistantScreenOptionChange = onAssistantScreenOptionChange,
            recentTaskRadiusEnabled = recentTaskRadiusEnabled,
            onRecentTaskRadiusEnabledChange = onRecentTaskRadiusEnabledChange,
            recentTaskRadiusDp = recentTaskRadiusDp,
            onRecentTaskRadiusDpChange = onRecentTaskRadiusDpChange,
            onOpenSubPage = onOpenSubPage,
        )
        FeaturePageMode.NotificationRemoval -> NotificationRemovalPage()
        FeaturePageMode.SystemUi -> SystemUiCategoriesPage(onOpenSubPage)
        FeaturePageMode.SystemUiIconOrder -> StatusBarIconOrderPage()
        FeaturePageMode.SystemUiCornerSettings -> QsTileCornerSettingsPage()
        FeaturePageMode.SystemUiPercentageSettings -> QsSliderPercentageSettingsPage()
        FeaturePageMode.SystemUiSideVolumePercentageSettings -> QsSliderPercentageSettingsPage(sideVolume = true)
        FeaturePageMode.SystemUiFluidCloud -> FluidCloudFeaturesPage(onOpenSubPage)
        FeaturePageMode.SystemUiFluidCloudMediaBlacklist -> Unit
        FeaturePageMode.DesktopHiddenApps -> Unit
        FeaturePageMode.SystemUiSmallWindow -> SmallWindowFeaturesPage()
        FeaturePageMode.SystemUiLockScreen -> KeyguardInteractionSettings()
        FeaturePageMode.SystemUiNavigationBar -> NavigationBarFeaturesPage()
        FeaturePageMode.Screenshot -> CaptureFeaturesPage(LspConfig.KeyguardFeature.AodScreenshot)
        FeaturePageMode.ScreenRecording -> CaptureFeaturesPage(LspConfig.KeyguardFeature.ScreenOffRecording)
        FeaturePageMode.Camera -> CameraFeaturesPage()
        FeaturePageMode.Translation -> TranslationFeaturesPage()
        FeaturePageMode.AppInstaller, FeaturePageMode.PackageInstaller -> InstallerCleanupPage(mode, onOpenSubPage)
        FeaturePageMode.SystemUiNative,
        FeaturePageMode.SystemUiVolume,
        FeaturePageMode.SystemUiDynamicColor,
        FeaturePageMode.SystemUiClock,
        FeaturePageMode.SystemUiStatusBar,
        FeaturePageMode.SystemUiStatusBarLayout,
        FeaturePageMode.SystemUiStatusBarNetwork,
        FeaturePageMode.SystemUiStatusBarSpeed,
        FeaturePageMode.SystemUiStatusBarPower,
        FeaturePageMode.SystemUiStatusBarBattery,
        FeaturePageMode.SystemUiStatusBarInteraction,
        FeaturePageMode.SystemUiNotificationCenter,
        FeaturePageMode.SystemUiControlCenter -> SystemUiFeaturesPage(
            mode = mode,
            onOpenSubPage = onOpenSubPage,
            nativeNotifyIconEnabled = nativeNotifyIconEnabled,
            onNativeNotifyIconEnabledChange = onNativeNotifyIconEnabledChange,
            nativeNotificationBubblesEnabled = nativeNotificationBubblesEnabled,
            onNativeNotificationBubblesEnabledChange = onNativeNotificationBubblesEnabledChange,
            systemUiInternationalNetworkDisplayEnabled =
                systemUiInternationalNetworkDisplayEnabled,
            onSystemUiInternationalNetworkDisplayEnabledChange =
                onSystemUiInternationalNetworkDisplayEnabledChange,
            systemUiHideMobileRoamingIndicatorEnabled =
                systemUiHideMobileRoamingIndicatorEnabled,
            onSystemUiHideMobileRoamingIndicatorEnabledChange =
                onSystemUiHideMobileRoamingIndicatorEnabledChange,
            systemUiInternationalNotificationStyleEnabled =
                systemUiInternationalNotificationStyleEnabled,
            onSystemUiInternationalNotificationStyleEnabledChange =
                onSystemUiInternationalNotificationStyleEnabledChange,
            systemUiHideQsEditEnabled = systemUiHideQsEditEnabled,
            onSystemUiHideQsEditEnabledChange = onSystemUiHideQsEditEnabledChange,
            systemUiHideQsSettingsEnabled = systemUiHideQsSettingsEnabled,
            onSystemUiHideQsSettingsEnabledChange = onSystemUiHideQsSettingsEnabledChange,
            systemUiHideQsTopCarrierEnabled = systemUiHideQsTopCarrierEnabled,
            onSystemUiHideQsTopCarrierEnabledChange = onSystemUiHideQsTopCarrierEnabledChange,
            systemUiHideQsMoreEnabled = systemUiHideQsMoreEnabled,
            onSystemUiHideQsMoreEnabledChange = onSystemUiHideQsMoreEnabledChange,
            systemUiForceNativeClipboardOverlayEnabled = systemUiForceNativeClipboardOverlayEnabled,
            onSystemUiForceNativeClipboardOverlayEnabledChange = onSystemUiForceNativeClipboardOverlayEnabledChange,
        )
        FeaturePageMode.AndroidSystem -> AndroidSystemFeaturesPage(
            onOpenSubPage = onOpenSubPage,
            gmsRegionRestrictionBypassEnabled = gmsRegionRestrictionBypassEnabled,
            onGmsRegionRestrictionBypassEnabledChange = onGmsRegionRestrictionBypassEnabledChange,
        )
        FeaturePageMode.MobileNetwork -> MobileNetworkFeaturesPage()
        FeaturePageMode.Installer -> InstallerPage()
        FeaturePageMode.CorePatch -> CorePatchPage()
        FeaturePageMode.SideKey -> SideKeyFeaturesPage()
        FeaturePageMode.Esim -> EsimFeaturesPage(onOpenSubPage = onOpenSubPage)
        FeaturePageMode.EsimDiagnostics -> EsimDiagnosticsPage()
        FeaturePageMode.AppMarket -> AppMarketFeaturesPage()
        FeaturePageMode.Battery -> BatteryFeaturesPage()
        FeaturePageMode.TrafficManagement -> TrafficManagementPage()
        FeaturePageMode.PhoneManager -> PhoneManagerFeaturesPage()
        FeaturePageMode.ThemeStore -> ThemeStoreFeaturesPage()
        FeaturePageMode.Gallery -> GalleryFeaturesPage()
        FeaturePageMode.SecurePay -> SecurePayFeaturesPage()
        FeaturePageMode.GameAssistant -> GameAssistantFeaturesPage()
        FeaturePageMode.OShare -> OShareFeaturesPage()
        FeaturePageMode.SoftwareUpdate -> SoftwareUpdateFeaturesPage()
        FeaturePageMode.Keyboard -> KeyboardFeaturesPage()
        FeaturePageMode.QuickAppServices -> QuickAppServicesFeaturesPage()
        FeaturePageMode.FileManager -> FileManagerFeaturesPage()
        FeaturePageMode.GoogleMessages -> GoogleMessagesFeaturesPage()
        FeaturePageMode.SystemMessages -> PushMonitorPage(pushMonitorRefresh)
        FeaturePageMode.AppErrors -> Unit
        FeaturePageMode.NotificationBatch -> Unit
        FeaturePageMode.Athena -> AthenaFeaturesPage()
        FeaturePageMode.SettingsRealme -> SettingsRealmePage()
        FeaturePageMode.Settings,
        FeaturePageMode.SettingsAppearance,
        FeaturePageMode.SettingsApps,
        FeaturePageMode.SettingsPermissions,
        FeaturePageMode.SettingsAboutDevice,
        FeaturePageMode.SettingsRegion -> SettingsFeaturesPage(
            mode = mode,
            onOpenSubPage = onOpenSubPage,
            permissionMonitorVisible = permissionMonitorVisible,
            onPermissionMonitorVisibleChange = onPermissionMonitorVisibleChange,
            settingsForceGoogleEntryEnabled = settingsForceGoogleEntryEnabled,
            onSettingsForceGoogleEntryEnabledChange = onSettingsForceGoogleEntryEnabledChange,
            extremeRefresh165Enabled = extremeRefresh165Enabled,
            onExtremeRefresh165EnabledChange = onExtremeRefresh165EnabledChange,
        )
        FeaturePageMode.TouchSampling -> TouchSamplingPage()
        FeaturePageMode.SecurityPermission -> SecurityPermissionFeaturesPage()
        FeaturePageMode.Wallpapers -> WallpapersFeaturesPage()
        FeaturePageMode.Aod -> AodFeaturesPage(
            aodEnhanceEnabled = aodEnhanceEnabled,
            onAodEnhanceEnabledChange = onAodEnhanceEnabledChange,
            aodInitDarkBrightness = aodInitDarkBrightness,
            onAodInitDarkBrightnessChange = onAodInitDarkBrightnessChange,
            aodInitBrightBrightness = aodInitBrightBrightness,
            onAodInitBrightBrightnessChange = onAodInitBrightBrightnessChange,
            aodRunningBrightnessMultiplier = aodRunningBrightnessMultiplier,
            onAodRunningBrightnessMultiplierChange = onAodRunningBrightnessMultiplierChange,
            aodAllDayUnlockEnabled = aodAllDayUnlockEnabled,
            onAodAllDayUnlockEnabledChange = onAodAllDayUnlockEnabledChange,
            aodSingleClickBlockEnabled = aodSingleClickBlockEnabled,
            onAodSingleClickBlockEnabledChange = onAodSingleClickBlockEnabledChange,
        )
        FeaturePageMode.Assistant -> AssistantFeaturesPage(
            assistantPowerMode = assistantPowerMode,
            onAssistantPowerModeChange = onAssistantPowerModeChange,
            assistantGestureCircleEnabled = assistantGestureCircleEnabled,
            onAssistantGestureCircleEnabledChange = onAssistantGestureCircleEnabledChange,
            assistantGestureCircleC17Enabled = assistantGestureCircleC17Enabled,
            onAssistantGestureCircleC17EnabledChange =
                onAssistantGestureCircleC17EnabledChange,
            assistantNativePowerEnabled = assistantNativePowerEnabled,
            onAssistantNativePowerEnabledChange = onAssistantNativePowerEnabledChange,
            assistantNativeCircleEnabled = assistantNativeCircleEnabled,
            onAssistantNativeCircleEnabledChange = onAssistantNativeCircleEnabledChange,
        )
        FeaturePageMode.Main, FeaturePageMode.EnabledFeatures, FeaturePageMode.Updates -> Unit
    }
}

@Composable
private fun FeatureEntryRow(
    title: String,
    iconPackages: List<String>,
    iconGlyph: ImageVector? = null,
    onClick: () -> Unit,
    hasDividerAbove: Boolean = false,
    hasDividerBelow: Boolean = false,
) {


    SettingsCardRow(
        title = title,
        summary = "",
        onClick = onClick,
        showArrow = true,
        hasDividerAbove = hasDividerAbove,
        hasDividerBelow = hasDividerBelow,
        leadingContent = {
            FeatureIcon(packageNames = iconPackages, iconGlyph = iconGlyph)
        },
    )
}

@Composable
private fun FeatureIcon(
    packageNames: List<String> = emptyList(),
    iconGlyph: ImageVector? = null,
    containerSize: Dp = 52.dp,
    containerHeight: Dp = containerSize,
    appIconSize: Dp = 45.dp,
    fallbackIconSize: Dp = 30.dp,
    cornerRadius: Dp = 13.dp,
    shadowRadius: Dp = 7.dp,
    extendPlate: Boolean = false,
) {
    if (iconGlyph != null) {

        val iconBackground = run {
            COUITheme.colorScheme.primary
        }
        val iconForeground = run {
            Color.White
        }
        Box(
            contentAlignment = if (extendPlate) Alignment.TopCenter else Alignment.Center,
            modifier = Modifier.size(width = containerSize, height = containerHeight),
        ) {
            if (extendPlate) {
                Box(
                    modifier = Modifier
                        .size(width = containerSize, height = containerHeight)
                        .clip(RoundedCornerShape(cornerRadius))
                        .background(iconBackground),
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(appIconSize)
                    .drawColoredShadow(
                        color = iconBackground,
                        alpha = 0.7f,
                        borderRadius = cornerRadius,
                        shadowRadius = shadowRadius,
                        roundedRect = false,
                    )
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(iconBackground),
            ) {
                run {
                    Icon(
                        imageVector = iconGlyph,
                        contentDescription = null,
                        tint = iconForeground,
                        modifier = Modifier.size(fallbackIconSize),
                    )
                }
            }
        }
        return
    }
    val context = LocalContext.current.applicationContext
    val fallbackColor = COUITheme.colorScheme.primary
    val cacheKey = packageNames.joinToString(separator = "|")
    // A moving launch surface reuses this composition slot when A is handed
    // off to B. produceState keeps its backing State across key changes, so a
    // non-null A bitmap could survive the new producer and be rendered as B
    // until the leash was rebuilt. Key the State itself by icon identity so a
    // cache miss shows the neutral placeholder, never another app's icon.
    var appIconInfo by remember(cacheKey, fallbackColor) {
        mutableStateOf(featureAppIconCache[cacheKey])
    }
    LaunchedEffect(context.packageManager, cacheKey, fallbackColor) {
        if (appIconInfo == null) {
            appIconInfo = withContext(Dispatchers.IO) {
                featureAppIconLoadSemaphore.withPermit {
                    featureAppIconCache[cacheKey] ?: packageNames
                        .distinct()
                        .firstNotNullOfOrNull { packageName ->
                            loadPackageIconInfo(context, packageName, fallbackColor)
                        }
                        .let { loaded -> loaded ?: loadDefaultFeatureIconInfo(context, fallbackColor) }
                        .also { loaded ->
                            featureAppIconCache[cacheKey] = loaded
                        }
                }
            }
        }
    }
    if (appIconInfo != null) {
        val info = requireNotNull(appIconInfo)
        val iconBitmap = remember(info.icon) { info.icon.asImageBitmap() }
        Box(
            contentAlignment = if (extendPlate) Alignment.TopCenter else Alignment.Center,
            modifier = Modifier
                .size(width = containerSize, height = containerHeight)
        ) {
            if (extendPlate) {
                Box(
                    modifier = Modifier
                        .size(width = containerSize, height = containerHeight)
                        .clip(RoundedCornerShape(cornerRadius))
                        .background(
                            if (info.systemFrameworkIcon) Color.White else info.dominantColor,
                        ),
                )
            }
            if (info.systemFrameworkIcon) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(appIconSize)
                        .drawColoredShadow(
                            color = Color.White,
                            alpha = 0.9f,
                            borderRadius = cornerRadius,
                            shadowRadius = shadowRadius,
                            roundedRect = false,
                        )
                        .clip(RoundedCornerShape(cornerRadius))
                        .background(Color.White),
                ) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = null,
                        modifier = Modifier.size(fallbackIconSize),
                    )
                }
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(appIconSize)
                        .drawColoredShadow(
                            color = info.dominantColor,
                            alpha = 1f,
                            borderRadius = cornerRadius,
                            shadowRadius = shadowRadius,
                            roundedRect = false,
                        )
                        .clip(RoundedCornerShape(cornerRadius))
                        .background(info.dominantColor),
                ) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = null,
                        modifier = Modifier.size(appIconSize),
                    )
                }
            }
        }
        return
    }

    // Keep the launcher's geometry stable while a cold process loads system icons off the UI
    // thread. Collapsing this slot makes the grid jump and also invalidates launch endpoints.
    Box(
        contentAlignment = if (extendPlate) Alignment.TopCenter else Alignment.Center,
        modifier = Modifier.size(width = containerSize, height = containerHeight),
    ) {
        Box(
            modifier = Modifier
                .size(appIconSize)
                .clip(RoundedCornerShape(cornerRadius))
                .background(fallbackColor.copy(alpha = 0.10f)),
        )
    }
}

private data class AppIconInfo(
    val icon: Bitmap,
    val dominantColor: Color,
    val systemFrameworkIcon: Boolean = false,
)

private val featureAppIconCache = ConcurrentHashMap<String, AppIconInfo>()
private val featureAppIconLoadSemaphore = Semaphore(permits = 2)

private fun loadDefaultFeatureIconInfo(context: Context, defaultColor: Color): AppIconInfo {
    val targetPixels = (64f * context.resources.displayMetrics.density)
        .roundToInt().coerceIn(128, 256)
    val bitmap = context.packageManager.defaultActivityIcon.toBitmap(
        width = targetPixels, height = targetPixels,
    )
    return AppIconInfo(bitmap, bitmap.extractPlateColor(defaultColor))
}

private fun loadPackageIconInfo(
    context: Context,
    packageName: String,
    defaultColor: Color,
): AppIconInfo? {
    return runCatching {
        val packageManager = context.packageManager
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        val icon = appInfo.loadIcon(packageManager)
        // 62dp is the largest rendered launcher icon. A 256px decode is lossless at the
        // device's current density while avoiding Palette scanning full 432/512px assets.
        val targetPixels = (64f * context.resources.displayMetrics.density)
            .roundToInt()
            .coerceIn(128, 256)
        val bitmap = icon.toBitmap(width = targetPixels, height = targetPixels)
        AppIconInfo(
            icon = bitmap,
            dominantColor = bitmap.extractPlateColor(defaultColor),
            systemFrameworkIcon = packageName == "android",
        )
    }.getOrNull()
}

private fun Bitmap.extractPlateColor(fallback: Color): Color {
    return runCatching {
        Palette.from(this)
            .generate()
            .dominantSwatch
            ?.rgb
            ?.let(::Color)
    }.getOrNull() ?: fallback
}

@Suppress("DEPRECATION")
private fun Modifier.drawColoredShadow(
    color: Color,
    alpha: Float = 0.2f,
    borderRadius: Dp = 0.dp,
    shadowRadius: Dp = 20.dp,
    offsetX: Dp = 0.dp,
    offsetY: Dp = 0.dp,
    roundedRect: Boolean = true,
): Modifier = drawBehind {
    drawIntoCanvas { canvas ->
        val paint = Paint()
        val frameworkPaint = paint.asFrameworkPaint()
        frameworkPaint.color = color.copy(alpha = 0f).toArgb()
        frameworkPaint.setShadowLayer(
            shadowRadius.toPx(),
            offsetX.toPx(),
            offsetY.toPx(),
            color.copy(alpha = alpha).toArgb(),
        )
        canvas.save()
        canvas.drawRoundRect(
            left = 0f,
            top = 0f,
            right = size.width,
            bottom = size.height,
            radiusX = if (roundedRect) size.height / 2f else borderRadius.toPx(),
            radiusY = if (roundedRect) size.height / 2f else borderRadius.toPx(),
            paint = paint,
        )
        canvas.restore()
    }
}
