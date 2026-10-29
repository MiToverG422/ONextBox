package com.mi.onextbox.ui.settings

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mi.onextbox.R
import com.mi.onextbox.ui.common.AppThemeMode
import com.mi.onextbox.ui.common.AppUiStyle
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.basic.TopAppBarState

enum class AboutPageMode {
    Main,
    AppSettings,
    DeveloperOptions,
    Logs,
    Update,
    UpdateSettings,
    UpdateReleaseNotes,
    Contributors,
    References,
}

@Composable
fun AboutMainRoute(
    modifier: Modifier,
    blurBackdrop: LayerBackdrop?,
    bottomContentPadding: Dp,
    scrollResetKey: Int? = null,
    externalScrollState: ScrollState? = null,
    externalTopAppBarState: TopAppBarState? = null,
    onOpenAppSettings: () -> Unit,
    onOpenSoftwareUpdate: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenContributors: () -> Unit,
    onOpenReferences: () -> Unit,
) {
    SettingsPageSurface(
        title = stringResource(R.string.section_about),
        blurBackdrop = blurBackdrop,
        bottomContentPadding = bottomContentPadding,
        scrollResetKey = scrollResetKey,
        externalScrollState = externalScrollState,
        externalTopAppBarState = externalTopAppBarState,
        modifier = modifier.fillMaxSize(),
    ) {
        AboutMainPage(
            onOpenAppSettings = onOpenAppSettings,
            onOpenSoftwareUpdate = onOpenSoftwareUpdate,
            onOpenLogs = onOpenLogs,
            onOpenContributors = onOpenContributors,
            onOpenReferences = onOpenReferences,
        )
    }
}

@Composable
fun AboutSubRoute(
    modifier: Modifier,
    pageMode: AboutPageMode,
    softwareUpdateState: SoftwareUpdateUiState,
    blurEffectEnabled: Boolean,
    onBlurEffectEnabledChange: (Boolean) -> Unit,
    featurePageNewStyleEnabled: Boolean,
    onFeaturePageNewStyleEnabledChange: (Boolean) -> Unit,
    popDirectionFollowsSwipeEdge: Boolean,
    onPopDirectionFollowsSwipeEdgeChange: (Boolean) -> Unit,
    showFpsMonitor: Boolean,
    onShowFpsMonitorChange: (Boolean) -> Unit,
    liquidGlassBottomBarEnabled: Boolean,
    onLiquidGlassBottomBarEnabledChange: (Boolean) -> Unit,
    bottomBarHideLabels: Boolean,
    mainPageSwipeEnabled: Boolean,
    subPageSwipeEnabled: Boolean,
    onBottomBarHideLabelsChange: (Boolean) -> Unit,
    onMainPageSwipeEnabledChange: (Boolean) -> Unit,
    onSubPageSwipeEnabledChange: (Boolean) -> Unit,
    appLanguageTag: String,
    onAppLanguageChange: (String) -> Unit,
    appThemeMode: AppThemeMode,
    onAppThemeModeChange: (AppThemeMode) -> Unit,
    appUiStyle: AppUiStyle,
    onAppUiStyleChange: (AppUiStyle) -> Unit,
    appThemeKeyColor: Long?,
    onAppThemeKeyColorChange: (Long?) -> Unit,
    appThemePaletteStyle: Int,
    onAppThemePaletteStyleChange: (Int) -> Unit,
    appThemeColorSpec: Int,
    onAppThemeColorSpecChange: (Int) -> Unit,
    bottomContentPadding: Dp,
    subPageBottomExtension: Dp,
    blurBackdrop: LayerBackdrop?,
    onBack: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    onOpenUpdateSettings: () -> Unit,
    onOpenUpdateReleaseNotes: () -> Unit,
    onOpenFeatureUpdates: (() -> Unit)? = null,
) {
    if (pageMode == AboutPageMode.Logs) {
        AppLogsRoute(
            modifier = modifier.fillMaxSize().extendPastBottom(subPageBottomExtension),
            blurBackdrop = blurBackdrop,
            bottomContentPadding = bottomContentPadding,
            onBack = onBack,
        )
        return
    }
    val showDownloadBar = pageMode == AboutPageMode.Update &&
        softwareUpdateState.availableUpdate != null
    var downloadBarHeightPx by remember { mutableIntStateOf(0) }
    val downloadBarHeight = with(LocalDensity.current) { downloadBarHeightPx.toDp() }

    Box(modifier = modifier.fillMaxSize()) {
        SettingsPageSurface(
            title = when (pageMode) {
                AboutPageMode.AppSettings -> stringResource(R.string.setting_theme_settings)
                AboutPageMode.DeveloperOptions -> stringResource(R.string.setting_developer_options)
                AboutPageMode.Logs -> stringResource(R.string.about_logs_title)
                AboutPageMode.Update -> stringResource(R.string.setting_software_update)
                AboutPageMode.UpdateSettings -> stringResource(R.string.software_update_auto_settings)
                AboutPageMode.UpdateReleaseNotes -> stringResource(R.string.software_update_release_notes_title)
                AboutPageMode.Contributors -> stringResource(R.string.about_contributors_title)
                AboutPageMode.References -> stringResource(R.string.about_references_title)
                AboutPageMode.Main -> stringResource(R.string.section_about)
            },
            showBack = true,
            onBack = onBack,
            actions = {
                if (pageMode == AboutPageMode.Update) {
                    SoftwareUpdateTopBarActions(
                        onOpenAutoUpdateSettings = onOpenUpdateSettings,
                    )
                }
            },
            blurBackdrop = blurBackdrop,
            bottomContentPadding = bottomContentPadding + if (showDownloadBar) {
                downloadBarHeight
            } else {
                0.dp
            },
            modifier = Modifier
                .fillMaxSize()
                .extendPastBottom(subPageBottomExtension),
        ) {
            when (pageMode) {
                AboutPageMode.AppSettings -> AppSettingsPage(
                    blurEffectEnabled = blurEffectEnabled,
                    onBlurEffectEnabledChange = onBlurEffectEnabledChange,
                    featurePageNewStyleEnabled = featurePageNewStyleEnabled,
                    onFeaturePageNewStyleEnabledChange = onFeaturePageNewStyleEnabledChange,
                    appLanguageTag = appLanguageTag,
                    onAppLanguageChange = onAppLanguageChange,
                    appThemeMode = appThemeMode,
                    onAppThemeModeChange = onAppThemeModeChange,
                    appUiStyle = appUiStyle,
                    onAppUiStyleChange = onAppUiStyleChange,
                    appThemeKeyColor = appThemeKeyColor,
                    onAppThemeKeyColorChange = onAppThemeKeyColorChange,
                    appThemePaletteStyle = appThemePaletteStyle,
                    onAppThemePaletteStyleChange = onAppThemePaletteStyleChange,
                    appThemeColorSpec = appThemeColorSpec,
                    onAppThemeColorSpecChange = onAppThemeColorSpecChange,
                    liquidGlassBottomBarEnabled = liquidGlassBottomBarEnabled,
                    onLiquidGlassBottomBarEnabledChange = onLiquidGlassBottomBarEnabledChange,
                    bottomBarHideLabels = bottomBarHideLabels,
                    mainPageSwipeEnabled = mainPageSwipeEnabled,
                    subPageSwipeEnabled = subPageSwipeEnabled,
                    onBottomBarHideLabelsChange = onBottomBarHideLabelsChange,
                    onMainPageSwipeEnabledChange = onMainPageSwipeEnabledChange,
                    onSubPageSwipeEnabledChange = onSubPageSwipeEnabledChange,
                    onOpenDeveloperOptions = onOpenDeveloperOptions,
                )
                AboutPageMode.DeveloperOptions -> DeveloperOptionsPage(
                    showFpsMonitor = showFpsMonitor,
                    onShowFpsMonitorChange = onShowFpsMonitorChange,
                )
                AboutPageMode.Update -> SoftwareUpdatePage(
                    state = softwareUpdateState,
                    onOpenReleaseNotes = onOpenUpdateReleaseNotes,
                )
                AboutPageMode.UpdateSettings -> SoftwareUpdateAutoSettingsPage(softwareUpdateState)
                AboutPageMode.UpdateReleaseNotes -> SoftwareUpdateReleaseNotesPage(softwareUpdateState, onOpenFeatureUpdates)
                AboutPageMode.Contributors -> AboutContributorsPage()
                AboutPageMode.References -> AboutReferencesPage()
                AboutPageMode.Logs -> Unit
                AboutPageMode.Main -> Unit
            }
        }

        AnimatedVisibility(
            visible = showDownloadBar,
            enter = slideInVertically(
                animationSpec = tween(durationMillis = 450),
                initialOffsetY = { it },
            ) + fadeIn(animationSpec = tween(durationMillis = 260)),
            exit = slideOutVertically(
                animationSpec = tween(durationMillis = 320),
                targetOffsetY = { it },
            ) + fadeOut(animationSpec = tween(durationMillis = 220)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onGloballyPositioned { downloadBarHeightPx = it.size.height }
                .navigationBarsPadding(),
        ) {
            SoftwareUpdateDownloadBar(softwareUpdateState)
        }
    }
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
