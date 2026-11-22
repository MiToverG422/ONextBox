package com.mi.onextbox.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop as liquidLayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop as rememberLiquidBackdrop
import com.mi.onextbox.R
import com.mi.onextbox.ui.common.LocalPreviewTextPlaceholders
import com.mi.onextbox.ui.common.PreviewTextLine
import com.mi.onextbox.ui.common.bottomTabs
import com.mi.onextbox.ui.home.HomeHeroCard
import com.mi.onextbox.ui.home.HomeInfoCard
import com.mi.onextbox.ui.home.HomeRuntimeStatusCards
import com.mi.onextbox.ui.home.rememberHomeVersionInfo
import com.mi.onextbox.ui.layout.AppBottomChrome
import com.mi.onextbox.ui.layout.BottomBarAutoCollapse
import com.mi.onextbox.ui.layout.FloatingBottomBarMode
import com.mi.onextbox.ui.layout.LocalChromeNativeBlurEnabled
import com.mi.onextbox.ui.layout.rememberBottomBarCollapseState
import com.mi.onextbox.ui.layout.rememberChromeBlurBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.theme.COUITheme

private val PreviewStatusInset = 29.dp

@Composable
internal fun DisplayThemePreview(rootGranted: Boolean, barMode: FloatingBottomBarMode, hideBarLabels: Boolean) {
    val theme = COUITheme.colorScheme
    val description = stringResource(R.string.setting_display_theme_preview)
    val frameShape = remember { RoundedCornerShape(23.dp) }
    val screenShape = remember { RoundedCornerShape(19.dp) }

    SettingsGroup {
        Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.fillMaxWidth().height(292.dp).background(
                    Brush.radialGradient(listOf(theme.primary.copy(alpha = .22f), Color.Transparent)),
                ),
            )
            Box(
                Modifier.height(292.dp)
                    .aspectRatio(9f / 19.5f)
                    .clearAndSetSemantics { contentDescription = description }
                    .border(1.dp, Color(0xFF4C5361), frameShape)
                    .background(Color(0xFF0B0D13), frameShape)
                    .padding(4.dp)
                    .clip(screenShape)
                    .background(theme.surface),
            ) {
                val density = LocalDensity.current
                val previewDensity = remember(density) { Density(density.density * .37f, density.fontScale) }
                CompositionLocalProvider(
                    LocalDensity provides previewDensity,
                    LocalChromeNativeBlurEnabled provides false,
                    LocalPreviewTextPlaceholders provides true,
                ) {
                    Box(Modifier.fillMaxSize().consumeWindowInsets(WindowInsets.systemBars)) {
                        HomeAppearancePreview(rootGranted, barMode, hideBarLabels)
                    }
                    PreviewStatusBar(theme.onBackground)
                    PreviewTextLine(
                        width = 57.dp,
                        height = 4.dp,
                        color = theme.onBackground.copy(alpha = .62f),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp),
                    )
                }
                // This surface receives taps while its parent can still scroll.
                Box(Modifier.matchParentSize().pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        waitForUpOrCancellation()
                    }
                })
            }
        }
    }
}

@Composable
private fun HomeAppearancePreview(rootGranted: Boolean, barMode: FloatingBottomBarMode, hideBarLabels: Boolean) {
    val version = rememberHomeVersionInfo(rootGranted)
    val colors = COUITheme.colorScheme
    val backdrop = rememberChromeBlurBackdrop()
    val liquid = if (barMode == FloatingBottomBarMode.LiquidGlass) rememberLiquidBackdrop() else null
    val home = bottomTabs.indexOfFirst { it.screenIndex == 0 }
    val pager = rememberPagerState(initialPage = home) { bottomTabs.size }
    val collapse = rememberBottomBarCollapseState(BottomBarAutoCollapse.Off)

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                .then(if (liquid != null) Modifier.liquidLayerBackdrop(liquid) else Modifier)
                .padding(start = 16.dp, end = 16.dp, top = PreviewStatusInset + 34.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PreviewTextLine(82.dp, 20.dp, colors.onBackground.copy(alpha = .90f),
                modifier = Modifier.padding(bottom = 8.dp))
            HomeHeroCard(
                rootGranted = rootGranted,
                lsposedReady = version.lsposedReady,
                lsposedStatus = version.lsposedStatus,
                onLongPress = {},
            )
            HomeRuntimeStatusCards(rootGranted, version)
            HomeInfoCard()
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            key(barMode, hideBarLabels) {
                AppBottomChrome(
                    mode = barMode,
                    tabs = bottomTabs,
                    pagerState = pager,
                    selectedIndex = home,
                    hideLabels = hideBarLabels,
                    collapseState = collapse,
                    blurBackdrop = backdrop,
                    liquidBackdrop = liquid,
                    onTabSelected = {},
                )
            }
        }
    }
}

@Composable
private fun PreviewStatusBar(ink: Color) {
    Box(Modifier.fillMaxWidth().height(PreviewStatusInset)) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 25.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PreviewTextLine(25.dp, 6.dp, ink)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(width = 15.dp, height = 10.dp)) {
                    val width = size.width / 6f
                    repeat(4) { index ->
                        val height = size.height * (.35f + .21f * index)
                        drawRoundRect(ink, Offset(index * size.width / 4f, size.height - height),
                            Size(width, height), CornerRadius(width * .4f))
                    }
                }
                PreviewTextLine(11.dp, 5.dp, ink)
                Canvas(Modifier.size(width = 18.dp, height = 9.dp)) {
                    drawRoundRect(ink, size = Size(size.width * .86f, size.height), cornerRadius = CornerRadius(2.dp.toPx()))
                    drawRoundRect(ink.copy(alpha = .58f), topLeft = Offset(size.width * .91f, size.height * .29f),
                        size = Size(size.width * .09f, size.height * .42f), cornerRadius = CornerRadius(1.dp.toPx()))
                }
            }
        }
        Box(
            Modifier.align(Alignment.Center).size(45.dp, 14.dp)
                .background(Color(0xFF0B0D13), RoundedCornerShape(50)),
        )
    }
}
