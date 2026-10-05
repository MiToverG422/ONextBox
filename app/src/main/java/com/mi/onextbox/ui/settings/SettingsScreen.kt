package com.mi.onextbox.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LsposedScopeRequester
import com.mi.onextbox.ui.common.AppLocale
import com.mi.onextbox.ui.common.AppThemeMode
import com.mi.onextbox.ui.common.AppUiStyle
import com.mi.onextbox.ui.common.AppIcons
import com.mi.onextbox.ui.common.bottomTabs
import com.mi.onextbox.ui.common.readCachedRootAccessInfo
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.basic.ListPopup
import io.github.suqi8.coui.kmp.basic.NavigationBar
import io.github.suqi8.coui.kmp.basic.NavigationItem
import io.github.suqi8.coui.kmp.basic.PopupPositionProvider
import io.github.suqi8.coui.kmp.basic.Switch
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronForward
import io.github.suqi8.coui.kmp.icon.extended.Ok
import io.github.suqi8.coui.kmp.icon.extended.Back
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.darkColorScheme
import io.github.suqi8.coui.kmp.theme.lightColorScheme
import io.github.suqi8.coui.kmp.utils.COUIPopupUtils.Companion.COUIPopupHost
import io.github.suqi8.coui.kmp.utils.PressFeedbackType
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.max

enum class AboutPageMode {
    Main,
    AppSettings,
    DeveloperOptions,
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
    scrollResetKey: Any? = null,
    onOpenAppSettings: () -> Unit,
    onOpenSoftwareUpdate: () -> Unit,
    onOpenContributors: () -> Unit,
    onOpenReferences: () -> Unit,
) {
    SettingsPageSurface(
        title = stringResource(R.string.section_about),
        blurBackdrop = blurBackdrop,
        bottomContentPadding = bottomContentPadding,
        scrollResetKey = scrollResetKey,
        modifier = modifier.fillMaxSize(),
    ) {
        AboutMainPage(
            onOpenAppSettings = onOpenAppSettings,
            onOpenSoftwareUpdate = onOpenSoftwareUpdate,
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
    featurePageVideoHidden: Boolean,
    onFeaturePageVideoHiddenChange: (Boolean) -> Unit,
    popDirectionFollowsSwipeEdge: Boolean,
    onPopDirectionFollowsSwipeEdgeChange: (Boolean) -> Unit,
    showFpsMonitor: Boolean,
    onShowFpsMonitorChange: (Boolean) -> Unit,
    liquidGlassBottomBarEnabled: Boolean,
    onLiquidGlassBottomBarEnabledChange: (Boolean) -> Unit,
    appLanguageTag: String,
    onAppLanguageChange: (String) -> Unit,
    appThemeMode: AppThemeMode,
    onAppThemeModeChange: (AppThemeMode) -> Unit,
    appUiStyle: AppUiStyle,
    onAppUiStyleChange: (AppUiStyle) -> Unit,
    materialFloatingBottomBarEnabled: Boolean,
    onMaterialFloatingBottomBarEnabledChange: (Boolean) -> Unit,
    materialHapticsEnabled: Boolean,
    onMaterialHapticsEnabledChange: (Boolean) -> Unit,
    materialSwitchIconsEnabled: Boolean,
    onMaterialSwitchIconsEnabledChange: (Boolean) -> Unit,
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
) {
    val showDownloadBar = pageMode == AboutPageMode.Update &&
        softwareUpdateState.availableUpdate != null
    var downloadBarHeightPx by remember { mutableIntStateOf(0) }
    val downloadBarHeight = with(LocalDensity.current) { downloadBarHeightPx.toDp() }

    Box(modifier = modifier.fillMaxSize()) {
        SettingsPageSurface(
            title = when (pageMode) {
                AboutPageMode.AppSettings -> stringResource(R.string.setting_theme_settings)
                AboutPageMode.DeveloperOptions -> stringResource(R.string.setting_developer_options)
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
                    featurePageVideoHidden = featurePageVideoHidden,
                    onFeaturePageVideoHiddenChange = onFeaturePageVideoHiddenChange,
                    appLanguageTag = appLanguageTag,
                    onAppLanguageChange = onAppLanguageChange,
                    appThemeMode = appThemeMode,
                    onAppThemeModeChange = onAppThemeModeChange,
                    appUiStyle = appUiStyle,
                    onAppUiStyleChange = onAppUiStyleChange,
                    materialFloatingBottomBarEnabled = materialFloatingBottomBarEnabled,
                    onMaterialFloatingBottomBarEnabledChange = onMaterialFloatingBottomBarEnabledChange,
                    materialHapticsEnabled = materialHapticsEnabled,
                    onMaterialHapticsEnabledChange = onMaterialHapticsEnabledChange,
                    materialSwitchIconsEnabled = materialSwitchIconsEnabled,
                    onMaterialSwitchIconsEnabledChange = onMaterialSwitchIconsEnabledChange,
                    appThemeKeyColor = appThemeKeyColor,
                    onAppThemeKeyColorChange = onAppThemeKeyColorChange,
                    appThemePaletteStyle = appThemePaletteStyle,
                    onAppThemePaletteStyleChange = onAppThemePaletteStyleChange,
                    appThemeColorSpec = appThemeColorSpec,
                    onAppThemeColorSpecChange = onAppThemeColorSpecChange,
                    liquidGlassBottomBarEnabled = liquidGlassBottomBarEnabled,
                    onLiquidGlassBottomBarEnabledChange = onLiquidGlassBottomBarEnabledChange,
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
                AboutPageMode.UpdateReleaseNotes -> SoftwareUpdateReleaseNotesPage(softwareUpdateState)
                AboutPageMode.Contributors -> AboutContributorsPage()
                AboutPageMode.References -> AboutReferencesPage()
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
