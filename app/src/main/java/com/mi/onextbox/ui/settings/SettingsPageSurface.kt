package com.mi.onextbox.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mi.onextbox.ui.common.ColorOsTopBarButton
import com.mi.onextbox.ui.common.ColorOs17DetailTopBar
import com.mi.onextbox.ui.common.AppUiStyle
import com.mi.onextbox.ui.common.LocalAppUiStyle
import com.mi.onextbox.ui.common.ScrollResetEffect
import com.mi.onextbox.ui.layout.BlurredChromeBar
import com.mi.onextbox.ui.layout.topBarColors
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.rememberTopAppBarState
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Back
import io.github.suqi8.coui.kmp.theme.COUITheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LargeFlexibleTopAppBar as MaterialLargeFlexibleTopAppBar
import androidx.compose.material3.TopAppBar as MaterialTopAppBar
import androidx.compose.material3.TopAppBarDefaults as MaterialTopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState as rememberMaterialTopAppBarState
import androidx.compose.material3.Scaffold as MaterialScaffold
import androidx.compose.material3.Text as MaterialText
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.material3.IconButton as MaterialIconButton
import kotlin.math.abs

@Composable
fun SettingsPageSurface(
    title: String,
    modifier: Modifier = Modifier,
    showBack: Boolean = false,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    blurBackdrop: LayerBackdrop? = null,
    bottomContentPadding: Dp = 0.dp,
    bottomChrome: (@Composable () -> Unit)? = null,
    contentScrollable: Boolean = true,
    externalScrollState: ScrollState? = null,
    scrollResetKey: Int? = null,
    backgroundContent: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalAppUiStyle.current == AppUiStyle.Material3Expressive) {
        Material3ExpressiveSettingsPageSurface(
            title = title,
            modifier = modifier,
            showBack = showBack,
            onBack = onBack,
            actions = actions,
            bottomContentPadding = bottomContentPadding,
            bottomChrome = bottomChrome,
            contentScrollable = contentScrollable,
            externalScrollState = externalScrollState,
            scrollResetKey = scrollResetKey,
            backgroundContent = backgroundContent,
            content = content,
        )
        return
    }
    val internalScrollState = rememberScrollState()
    val scrollState = externalScrollState ?: internalScrollState
    // Save the app-bar collapse state together with the list scroll state. NavDisplay removes
    // the parent entry while a detail page is active, so a plain remember would restore the
    // scrolled list but recreate an expanded title when returning.
    val topAppBarState = rememberTopAppBarState()
    ScrollResetEffect(scrollResetKey) {
        scrollState.scrollTo(0)
        topAppBarState.heightOffset = 0f
        topAppBarState.contentOffset = 0f
    }
    SideEffect {
        // Detail bars are pinned. Leaving the old collapsible range in place makes the
        // nested-scroll connection consume drags before the page itself can scroll.
        if (showBack && topAppBarState.heightOffsetLimit != 0f) {
            topAppBarState.heightOffsetLimit = 0f
        }
    }
    val scrollBehavior = COUIScrollBehavior(state = topAppBarState)
    val density = LocalDensity.current
    val isDark = COUITheme.colorScheme.surface.luminance() < 0.5f
    val nativeToolbarTitleColor = if (isDark) Color(0xE6FFFFFF) else Color(0xE6000000)
    val blurRevealDistancePx = with(density) { 30.dp.toPx() }
    val chromeBlurAlpha by remember(topAppBarState, blurRevealDistancePx) {
        derivedStateOf {
            val appBarDistance = abs(topAppBarState.heightOffset)
            val scrollDistance = if (appBarDistance > 0f) {
                appBarDistance
            } else {
                (-topAppBarState.contentOffset).coerceAtLeast(0f)
            }
            (scrollDistance / blurRevealDistancePx).coerceIn(0f, 1f)
        }
    }
    // ColorOS 17 Settings' HomepageImpl keeps the homepage blur overlay flush with the
    // app bar while it is expanded or moving. The 14 dp lower extension is applied only
    // after COUICollapsingToolbarLayout reports an exact fully-collapsed state.
    val homepageBlurExtension by remember(topAppBarState) {
        derivedStateOf {
            if (!showBack && topAppBarState.collapsedFraction == 1f) 14.dp else 0.dp
        }
    }
    val hasBackgroundContent = backgroundContent != null

    Scaffold(
        modifier = modifier
            .then(
                if (hasBackgroundContent) {
                    Modifier
                } else {
                    Modifier.background(COUITheme.colorScheme.surface)
                },
            )
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = if (hasBackgroundContent) Color.Transparent else COUITheme.colorScheme.surface,
        popupHost = {},
        topBar = {
        BlurredChromeBar(
            backdrop = blurBackdrop,
            effectAlpha = if (showBack) 1f else chromeBlurAlpha,
            // AppBarBlurOverlayView uses 8 dp for fixed detail bars. Only the
            // scroll-driven homepage replaces that with its 0/14 dp state machine.
            effectExtension = if (showBack) 8.dp else homepageBlurExtension,
            effectTopOffsetPx = { if (showBack) 0f else topAppBarState.heightOffset },
        ) {
            val navigationIcon: @Composable () -> Unit = {
                if (showBack) {
                    ColorOsTopBarButton(
                        onClick = { onBack?.invoke() },
                    ) {
                        Icon(
                            imageVector = COUIIcons.Back,
                            contentDescription = null,
                            tint = nativeToolbarTitleColor,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
            val spacedActions: @Composable RowScope.() -> Unit = {
                androidx.compose.foundation.layout.Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    actions()
                }
            }

            if (showBack) {
                ColorOs17DetailTopBar(
                    title = title,
                    containerColor = if (blurBackdrop != null || hasBackgroundContent) {
                        Color.Transparent
                    } else {
                        topBarColors()
                    },
                    titleColor = nativeToolbarTitleColor,
                    dividerColor = COUITheme.colorScheme.dividerLine,
                    showDivider = false,
                    scrollDistancePx = {
                        (-topAppBarState.contentOffset).coerceAtLeast(0f)
                    },
                    navigationIcon = navigationIcon,
                    actions = actions,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                TopAppBar(
                    title = title,
                    largeTitle = title,
                    scrollBehavior = scrollBehavior,
                    color = if (blurBackdrop != null || hasBackgroundContent) {
                        Color.Transparent
                    } else {
                        topBarColors()
                    },
                    showDivider = blurBackdrop == null && !hasBackgroundContent,
                    navigationIcon = navigationIcon,
                    actions = spacedActions,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        },
        bottomBar = {
            bottomChrome?.invoke()
        },
    ) { innerPadding ->
        val contentModifier = Modifier
                .fillMaxSize()
                // Root already consumes IME insets. Unlike raw asPaddingValues(), this
                // respects consumed insets and cannot subtract the keyboard twice.
                .imePadding()
                .then(
                    if (backgroundContent == null && blurBackdrop != null) {
                        Modifier.layerBackdrop(blurBackdrop)
                    } else {
                        Modifier
                    },
                )
                .then(if (contentScrollable) Modifier.verticalScroll(scrollState) else Modifier)
                .padding(
                    start = 0.dp,
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    end = 0.dp,
                    bottom = maxOf(innerPadding.calculateBottomPadding(), bottomContentPadding) + 12.dp,
                )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (backgroundContent != null && blurBackdrop != null) {
                        Modifier.layerBackdrop(blurBackdrop)
                    } else {
                        Modifier
                    },
                ),
        ) {
            backgroundContent?.invoke(this)
            Column(
                modifier = contentModifier,
                content = content,
            )
        }
        }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Material3ExpressiveSettingsPageSurface(
    title: String,
    modifier: Modifier,
    showBack: Boolean,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
    bottomContentPadding: Dp,
    bottomChrome: (@Composable () -> Unit)?,
    contentScrollable: Boolean,
    externalScrollState: ScrollState?,
    scrollResetKey: Int?,
    backgroundContent: (@Composable BoxScope.() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val internalScrollState = rememberScrollState()
    val scrollState = externalScrollState ?: internalScrollState
    val appBarState = rememberMaterialTopAppBarState()
    ScrollResetEffect(scrollResetKey) {
        scrollState.scrollTo(0)
        appBarState.heightOffset = 0f
        appBarState.contentOffset = 0f
    }
    val scrollBehavior = MaterialTopAppBarDefaults.exitUntilCollapsedScrollBehavior(appBarState)
    val topBarColors = MaterialTopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    )
    MaterialScaffold(
        modifier = modifier
            .fillMaxSize()
            .then(if (showBack) Modifier else Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            if (showBack) {
                MaterialTopAppBar(
                    title = { MaterialText(title, style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        MaterialIconButton(onClick = { onBack?.invoke() }) {
                            MaterialIcon(
                                imageVector = COUIIcons.Back,
                                contentDescription = null,
                            )
                        }
                    },
                    actions = actions,
                    colors = topBarColors,
                )
            } else {
                MaterialLargeFlexibleTopAppBar(
                    title = { MaterialText(title) },
                    actions = actions,
                    scrollBehavior = scrollBehavior,
                    colors = topBarColors,
                )
            }
        },
        bottomBar = { bottomChrome?.invoke() },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().consumeWindowInsets(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            backgroundContent?.invoke(this)
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxSize()
                    .imePadding()
                    .then(if (contentScrollable) Modifier.verticalScroll(scrollState) else Modifier)
                    .padding(
                        start = 0.dp,
                        top = innerPadding.calculateTopPadding() + 12.dp,
                        end = 0.dp,
                        bottom = maxOf(innerPadding.calculateBottomPadding(), bottomContentPadding) + 12.dp,
                    ),
                content = content,
            )
        }
    }
}
