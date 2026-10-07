package com.mi.onextbox.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.material3.Checkbox as MaterialCheckbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton as MaterialRadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider as MaterialSlider
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import com.mi.onextbox.R
import com.mi.onextbox.lsp.EsimDiagnosticsSnapshot
import com.mi.onextbox.lsp.EsimDiagnosticsStore
import com.mi.onextbox.lsp.EsimProfileDiagnostic
import com.mi.onextbox.lsp.LspConfig
import com.mi.onextbox.ui.common.AssistantScreenOption
import com.mi.onextbox.ui.common.ColorOs17SettingsSlider
import com.mi.onextbox.ui.common.CouiConfirmDialog
import com.mi.onextbox.ui.common.ColorOsTopBarButton
import com.mi.onextbox.ui.common.AppUiStyle
import com.mi.onextbox.ui.common.LocalAppUiStyle
import com.mi.onextbox.ui.common.rememberColorOsHapticTick
import com.mi.onextbox.ui.common.rememberHapticClick
import com.mi.onextbox.ui.common.rememberHapticToggle
import com.mi.onextbox.ui.common.restartScopePackages
import com.mi.onextbox.ui.settings.SettingsCardRow
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsListGroup
import com.mi.onextbox.ui.settings.SettingsPageSurface
import com.mi.onextbox.ui.settings.SettingsSection
import com.mi.onextbox.ui.settings.SettingsTokens
import com.mi.onextbox.ui.settings.SettingsToggleRow
import com.mi.onextbox.ui.settings.SettingsRowTextContent
import com.mi.onextbox.ui.settings.SettingsWindowDropdownPreference
import com.mi.onextbox.ui.settings.Material3ExpressiveAnimatedSegmentPosition
import com.mi.onextbox.ui.settings.Material3ExpressiveInferredSegmentPosition
import com.mi.onextbox.ui.settings.Material3ExpressivePreferenceRow
import com.mi.onextbox.ui.settings.Material3ExpressiveSegmentContentCard
import com.mi.onextbox.ui.settings.Material3ExpressiveSegmentPosition
import com.mi.onextbox.ui.settings.LocalMaterial3ExpressiveSegmentShapes
import com.mi.onextbox.ui.settings.settingsInteractiveRowHighlight
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.Checkbox
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.RadioButton
import io.github.suqi8.coui.kmp.basic.Switch
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.basic.TextFieldMode
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Ok
import io.github.suqi8.coui.kmp.icon.extended.Refresh
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

enum class FeaturePageMode(val isNestedPage: Boolean = false) {
    Main,
    Desktop,
    SystemUi,
    SystemUiNative(true),
    SystemUiDynamicColor(true),
    SystemUiStatusBar(true),
    SystemUiNotificationCenter(true),
    SystemUiControlCenter(true),
    SystemUiSmallWindow(true),
    SystemUiLockScreen(true),
    NotificationRemoval(true),
    MobileNetwork,
    AndroidSystem,
    Installer(true),
    Esim,
    EsimDiagnostics(true),
    AppMarket,
    FileManager,
    GoogleMessages,
    SystemMessages,
    Athena,
    Settings,
    SettingsRegion(true),
    SecurityPermission,
    TouchSampling,
    RefreshRate,
    Wallpapers,
    Aod,
    Screenshot,
    ScreenRecording,
    Assistant,
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
    onLaunchOriginChanged: (FeaturePageMode, FeatureLaunchOrigin) -> Unit = { _, _ -> },
    onOpen: (FeaturePageMode, FeatureLaunchOrigin?) -> Unit,
) {
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
    aodPanoramicSupportEnabled: Boolean,
    onAodPanoramicSupportEnabledChange: (Boolean) -> Unit,
    aodSettingsSwitchEnabled: Boolean,
    onAodSettingsSwitchEnabledChange: (Boolean) -> Unit,
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
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val subPageStateHolder = rememberSaveableStateHolder()
    var restartConfirmMode by remember { mutableStateOf<FeaturePageMode?>(null) }
    var pushMonitorRefresh by remember { mutableStateOf(0) }
    val restartTargets = featureRestartPackages(pageMode)
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
            if (pageMode != FeaturePageMode.EsimDiagnostics && pageMode != FeaturePageMode.TouchSampling && pageMode != FeaturePageMode.RefreshRate && pageMode != FeaturePageMode.SystemMessages) {
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
                aodPanoramicSupportEnabled = aodPanoramicSupportEnabled,
                onAodPanoramicSupportEnabledChange = onAodPanoramicSupportEnabledChange,
                aodSettingsSwitchEnabled = aodSettingsSwitchEnabled,
                onAodSettingsSwitchEnabledChange = onAodSettingsSwitchEnabledChange,
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
        title = if (modeToRestart == null) {
            ""
        } else {
            stringResource(
                R.string.feature_restart_scope_confirm_title,
                featurePageTitle(modeToRestart),
            )
        },
        targets = modeToRestart?.let(::featureRestartPackages).orEmpty(),
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
    FeaturePageMode.Main -> stringResource(R.string.tab_features)
    FeaturePageMode.Desktop -> stringResource(R.string.section_system_desktop)
    FeaturePageMode.SystemUi -> stringResource(R.string.section_lsp)
    FeaturePageMode.SystemUiNative -> stringResource(R.string.feature_group_native)
    FeaturePageMode.SystemUiDynamicColor -> stringResource(R.string.feature_group_dynamic_color)
    FeaturePageMode.SystemUiStatusBar -> stringResource(R.string.feature_group_beautify)
    FeaturePageMode.SystemUiNotificationCenter -> stringResource(R.string.feature_group_notification_center)
    FeaturePageMode.SystemUiControlCenter -> stringResource(R.string.feature_group_control_center)
    FeaturePageMode.SystemUiSmallWindow -> stringResource(R.string.small_window_title)
    FeaturePageMode.SystemUiLockScreen -> stringResource(R.string.keyguard_page_title)
    FeaturePageMode.NotificationRemoval -> stringResource(R.string.notification_removal_title)
    FeaturePageMode.MobileNetwork -> stringResource(R.string.feature_mobile_network_title)
    FeaturePageMode.AndroidSystem -> stringResource(R.string.feature_android_system_title)
    FeaturePageMode.Installer -> stringResource(R.string.installer_title)
    FeaturePageMode.Esim -> stringResource(R.string.feature_esim_title)
    FeaturePageMode.EsimDiagnostics -> stringResource(R.string.feature_group_esim_diagnostics)
    FeaturePageMode.AppMarket -> stringResource(R.string.feature_app_market_title)
    FeaturePageMode.FileManager -> stringResource(R.string.feature_file_manager_title)
    FeaturePageMode.GoogleMessages -> stringResource(R.string.feature_google_messages_title)
    FeaturePageMode.SystemMessages -> stringResource(R.string.feature_system_messages_title)
    FeaturePageMode.Athena -> stringResource(R.string.feature_group_athena)
    FeaturePageMode.Settings -> stringResource(R.string.tab_settings)
    FeaturePageMode.SettingsRegion -> stringResource(R.string.feature_group_settings_hidden_features)
    FeaturePageMode.SecurityPermission -> stringResource(R.string.feature_permission_manager_title)
    FeaturePageMode.TouchSampling -> stringResource(R.string.feature_touch_rate_title)
    FeaturePageMode.RefreshRate -> stringResource(R.string.tab_refresh_rate)
    FeaturePageMode.Wallpapers -> stringResource(R.string.feature_wallpapers_title)
    FeaturePageMode.Aod -> stringResource(R.string.feature_aod_enhance_title)
    FeaturePageMode.Screenshot -> stringResource(R.string.feature_screenshot_title)
    FeaturePageMode.ScreenRecording -> stringResource(R.string.feature_screen_recording_title)
    FeaturePageMode.Assistant -> stringResource(R.string.feature_assistant_title)
}

private fun featureRestartPackages(mode: FeaturePageMode): List<String> = when (mode) {
    FeaturePageMode.Desktop -> listOf("com.android.launcher", "com.oplus.launcher", "com.coloros.launcher")
    FeaturePageMode.SystemUi,
    FeaturePageMode.SystemUiNative,
    FeaturePageMode.SystemUiDynamicColor,
    FeaturePageMode.SystemUiStatusBar,
    FeaturePageMode.SystemUiNotificationCenter,
    FeaturePageMode.SystemUiControlCenter -> listOf("com.android.systemui")
    FeaturePageMode.SystemUiLockScreen -> listOf("com.android.systemui")
    FeaturePageMode.Screenshot -> listOf("android", "system", "com.oplus.screenshot")
    FeaturePageMode.ScreenRecording -> listOf("android", "system", "com.android.systemui", "com.oplus.screenrecorder")
    FeaturePageMode.NotificationRemoval -> listOf("android", "system", "com.android.systemui")
    FeaturePageMode.MobileNetwork -> listOf("com.android.phone")
    FeaturePageMode.AndroidSystem -> listOf("android", "system")
    FeaturePageMode.Installer -> listOf("android", "system")
    FeaturePageMode.Esim -> listOf("com.oplus.euicc")
    FeaturePageMode.AppMarket -> listOf("com.heytap.market")
    FeaturePageMode.FileManager -> listOf("com.coloros.filemanager")
    FeaturePageMode.GoogleMessages -> listOf("com.google.android.apps.messaging")
    FeaturePageMode.Athena -> listOf("android", "system", "com.oplus.athena")
    FeaturePageMode.Settings,
    FeaturePageMode.SettingsRegion -> listOf("com.android.settings")
    FeaturePageMode.SecurityPermission -> listOf(
        "com.oplus.securitypermission", "com.android.settings",
        "com.android.permissioncontroller", "com.google.android.permissioncontroller", "android", "system",
    )
    FeaturePageMode.Wallpapers -> listOf("com.oplus.wallpapers")
    FeaturePageMode.Aod -> listOf("com.oplus.aod", "com.coloros.aod", "com.oplus.aodservice")
    FeaturePageMode.Assistant -> listOf(
        "android",
        "system",
        "com.android.systemui",
        "com.google.android.googlequicksearchbox",
    )
    FeaturePageMode.Main,
    // C17 flexible handles also live in system_server. Offer no misleading SystemUI-only restart.
    FeaturePageMode.SystemUiSmallWindow,
    FeaturePageMode.EsimDiagnostics,
    FeaturePageMode.SystemMessages,
    FeaturePageMode.RefreshRate,
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
    if (LocalAppUiStyle.current == AppUiStyle.Material3Expressive) {
        androidx.compose.material3.IconButton(onClick = onClick) {
            androidx.compose.material3.Icon(
                imageVector = COUIIcons.Refresh,
                contentDescription = contentDescription,
            )
        }
        return
    }
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
    val scope = rememberCoroutineScope()
    var restartSystem by remember { mutableStateOf(false) }
    LaunchedEffect(show) {
        if (show) restartSystem = false
    }

    CouiConfirmDialog(
        show = show,
        title = title,
        summary = stringResource(R.string.feature_restart_scope_confirm_summary),
        negativeText = stringResource(R.string.feature_restart_scope_confirm_cancel),
        positiveText = stringResource(R.string.feature_restart_scope_confirm_action),
        onDismissRequest = onDismissRequest,
        onPositive = {
            val shouldRestartSystem = restartSystem
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
            val isMaterial3Expressive = LocalAppUiStyle.current == AppUiStyle.Material3Expressive
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isMaterial3Expressive) Modifier.padding(top = 12.dp)
                        else Modifier.heightIn(min = 32.dp)
                    )
                    .toggleable(
                        value = restartSystem,
                        interactionSource = null,
                        indication = null,
                        role = Role.Checkbox,
                        onValueChange = { restartSystem = it },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isMaterial3Expressive) {
                    MaterialCheckbox(checked = restartSystem, onCheckedChange = null)
                    MaterialText(
                        text = stringResource(R.string.feature_restart_system_option),
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                } else {
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
        },
    )
}

@Composable
private fun FeatureMainPage(
    newStyleEnabled: Boolean,
    hiddenSourceModes: Set<FeaturePageMode>,
    externalIconScales: Map<FeaturePageMode, () -> Float>,
    onLaunchOriginChanged: (FeaturePageMode, FeatureLaunchOrigin) -> Unit,
    onOpen: (FeaturePageMode, FeatureLaunchOrigin?) -> Unit,
) {
    val entries = remember { featureMainEntries() }

    if (LocalAppUiStyle.current == AppUiStyle.Material3Expressive) {
        Material3ExpressiveFeatureMainPage(entries = entries, onOpen = onOpen)
        return
    }

    if (newStyleEnabled) {
        Box(modifier = Modifier.fillMaxSize()) {
            FeatureLauncherGrid(
                entries = entries,
                hiddenSourceModes = hiddenSourceModes,
                externalIconScales = externalIconScales,
                onLaunchOriginChanged = { mode, origin ->
                    // Cache only the untransformed launcher geometry. During a
                    // feature transition the whole workspace is scaled/blurred;
                    // reporting those temporary bounds would move the close
                    // endpoint and make the floating icon snap mid-rebound.
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
    val entries = remember { toolsMainEntries() }
    if (LocalAppUiStyle.current == AppUiStyle.Material3Expressive) {
        Material3ExpressiveFeatureMainPage(
            entries = entries,
            onOpen = { mode, _ -> onOpen(mode) },
        )
    } else {
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

@Composable
private fun Material3ExpressiveFeatureMainPage(
    entries: List<FeatureMainEntry>,
    onOpen: (FeaturePageMode, FeatureLaunchOrigin?) -> Unit,
) {
    SettingsGroup {
        entries.forEachIndexed { index, entry ->
            Material3ExpressiveSegmentPosition(index, entries.size) {
                FeatureEntryRow(
                    title = stringResource(entry.titleRes),
                    iconPackages = entry.iconPackages,
                    iconGlyph = entry.iconGlyph,
                    onClick = { onOpen(entry.pageMode, null) },
                    hasDividerAbove = index > 0,
                    hasDividerBelow = index < entries.lastIndex,
                )
            }
        }
    }
}

private fun featureMainEntries(): List<FeatureMainEntry> = buildList {
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
                titleRes = R.string.feature_file_manager_title,
                iconPackages = listOf("com.coloros.filemanager"),
                pageMode = FeaturePageMode.FileManager,
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
                iconPackages = listOf("com.oplus.aod", "com.coloros.aod", "com.oplus.aodservice"),
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

private fun toolsMainEntries(): List<FeatureMainEntry> = listOf(
    FeatureMainEntry(
        titleRes = R.string.tab_refresh_rate,
        iconPackages = emptyList(),
        pageMode = FeaturePageMode.RefreshRate,
        iconGlyph = Icons.Rounded.Speed,
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
    onLaunchOriginChanged: (FeaturePageMode, FeatureLaunchOrigin) -> Unit,
    onOpen: (FeaturePageMode, FeatureLaunchOrigin?) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
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
                val bounds = coordinates.boundsInWindow()
                launchPivotX = bounds.center.x
                launchPivotY = bounds.center.y
                hitLeft = bounds.left
                hitTop = bounds.top
                hitWidth = bounds.width
                hitHeight = bounds.height
                if (pressScale.value >= 0.999f && externallyPressedScale() >= 0.999f) {
                    launchOrigin?.let { origin ->
                        val measured = origin.copy(
                            hitLeft = bounds.left,
                            hitTop = bounds.top,
                            hitWidth = bounds.width,
                            hitHeight = bounds.height,
                        )
                        if (measured != origin) launchOrigin = measured
                        onLaunchOriginChanged(entry.pageMode, measured)
                    }
                }
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
                    val bounds = coordinates.boundsInWindow()
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
        (featureMainEntries() + toolsMainEntries()).firstOrNull { it.pageMode == pageMode }
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
    aodPanoramicSupportEnabled: Boolean,
    onAodPanoramicSupportEnabledChange: (Boolean) -> Unit,
    aodSettingsSwitchEnabled: Boolean,
    onAodSettingsSwitchEnabledChange: (Boolean) -> Unit,
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
    when (mode) {
        FeaturePageMode.Desktop -> DesktopFeaturesPage(
            launcherLayoutUnlocked = launcherLayoutUnlocked,
            onLauncherLayoutUnlockedChange = onLauncherLayoutUnlockedChange,
            assistantScreenOption = assistantScreenOption,
            onAssistantScreenOptionChange = onAssistantScreenOptionChange,
            recentTaskRadiusEnabled = recentTaskRadiusEnabled,
            onRecentTaskRadiusEnabledChange = onRecentTaskRadiusEnabledChange,
            recentTaskRadiusDp = recentTaskRadiusDp,
            onRecentTaskRadiusDpChange = onRecentTaskRadiusDpChange,
        )
        FeaturePageMode.NotificationRemoval -> NotificationRemovalPage()
        FeaturePageMode.SystemUi -> SystemUiCategoriesPage(onOpenSubPage)
        FeaturePageMode.SystemUiSmallWindow -> SmallWindowFeaturesPage()
        FeaturePageMode.SystemUiLockScreen -> KeyguardInteractionSettings()
        FeaturePageMode.Screenshot -> CaptureFeaturesPage(LspConfig.KeyguardFeature.AodScreenshot)
        FeaturePageMode.ScreenRecording -> CaptureFeaturesPage(LspConfig.KeyguardFeature.ScreenOffRecording)
        FeaturePageMode.SystemUiNative,
        FeaturePageMode.SystemUiDynamicColor,
        FeaturePageMode.SystemUiStatusBar,
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
        FeaturePageMode.Esim -> EsimFeaturesPage(onOpenSubPage = onOpenSubPage)
        FeaturePageMode.EsimDiagnostics -> EsimDiagnosticsPage()
        FeaturePageMode.AppMarket -> AppMarketFeaturesPage()
        FeaturePageMode.FileManager -> FileManagerFeaturesPage()
        FeaturePageMode.GoogleMessages -> GoogleMessagesFeaturesPage()
        FeaturePageMode.SystemMessages -> PushMonitorPage(pushMonitorRefresh)
        FeaturePageMode.Athena -> AthenaFeaturesPage()
        FeaturePageMode.Settings,
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
        FeaturePageMode.RefreshRate -> RefreshRatePage()
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
            aodPanoramicSupportEnabled = aodPanoramicSupportEnabled,
            onAodPanoramicSupportEnabledChange = onAodPanoramicSupportEnabledChange,
            aodSettingsSwitchEnabled = aodSettingsSwitchEnabled,
            onAodSettingsSwitchEnabledChange = onAodSettingsSwitchEnabledChange,
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
        FeaturePageMode.Main -> Unit
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
    if (LocalAppUiStyle.current == AppUiStyle.Material3Expressive) {
        SegmentedListItem(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp),
            shapes = LocalMaterial3ExpressiveSegmentShapes.current
                ?: ListItemDefaults.segmentedShapes(0, 1),
            colors = ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceBright,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceBright,
            ),
            leadingContent = {
                FeatureIcon(
                    packageNames = iconPackages,
                    iconGlyph = iconGlyph,
                    containerSize = 50.dp,
                    appIconSize = 44.dp,
                    fallbackIconSize = 29.dp,
                    cornerRadius = 12.dp,
                    shadowRadius = 0.dp,
                )
            },
            trailingContent = {
                MaterialIcon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MaterialText(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }

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
        val isMaterial3Expressive = LocalAppUiStyle.current == AppUiStyle.Material3Expressive
        val iconBackground = if (isMaterial3Expressive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            COUITheme.colorScheme.primary
        }
        val iconForeground = if (isMaterial3Expressive) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
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
                        alpha = if (isMaterial3Expressive) 0f else 0.7f,
                        borderRadius = cornerRadius,
                        shadowRadius = shadowRadius,
                        roundedRect = false,
                    )
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(iconBackground),
            ) {
                if (isMaterial3Expressive) {
                    MaterialIcon(
                        imageVector = iconGlyph,
                        contentDescription = null,
                        tint = iconForeground,
                        modifier = Modifier.size(fallbackIconSize),
                    )
                } else {
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
                    featureAppIconCache[cacheKey] ?: (packageNames + context.packageName)
                        .distinct()
                        .firstNotNullOfOrNull { packageName ->
                            loadPackageIconInfo(context, packageName, fallbackColor)
                        }
                        ?.also { loaded ->
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
