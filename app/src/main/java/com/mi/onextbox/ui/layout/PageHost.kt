package com.mi.onextbox.ui.layout

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mi.onextbox.R
import com.mi.onextbox.ui.home.HomePage
import com.mi.onextbox.ui.common.ScrollResetEffect
import com.mi.onextbox.ui.layout.BlurredChromeBar
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.basic.TopAppBarState
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.rememberTopAppBarState
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.math.abs

@Composable
fun Page(
    modifier: Modifier,
    currentTab: Int,
    rootGranted: Boolean,
    bottomChromePadding: Dp,
    onHomeHeroLongPress: () -> Unit,
    onOpenDeviceDetails: () -> Unit,
    blurBackdrop: LayerBackdrop?,
    scrollResetKey: Int? = null,
    externalScrollState: ScrollState? = null,
    externalTopAppBarState: TopAppBarState? = null,
) {


    val internalScrollState = rememberScrollState()
    val scrollState = externalScrollState ?: internalScrollState
    val internalTopAppBarState = rememberTopAppBarState()
    val topAppBarState = externalTopAppBarState ?: internalTopAppBarState
    ScrollResetEffect(scrollResetKey) {
        scrollState.scrollTo(0)
        topAppBarState.heightOffset = 0f
        topAppBarState.contentOffset = 0f
    }
    val scrollBehavior = COUIScrollBehavior(state = topAppBarState)
    val density = LocalDensity.current
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
    val homepageBlurExtension by remember(topAppBarState) {
        derivedStateOf {
            // HomepageImpl.updateBlurOverlayExtraHeight() uses the 14 dp resource only
            // when the C17 collapsing toolbar has reached expansionFraction == 1f.
            if (topAppBarState.collapsedFraction == 1f) 14.dp else 0.dp
        }
    }
    val pageTitle = if (currentTab == 0) stringResource(R.string.tab_home) else ""
    Scaffold(
        modifier = modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = COUITheme.colorScheme.surface,
        popupHost = {},
        topBar = {
        BlurredChromeBar(
            backdrop = blurBackdrop,
            effectAlpha = chromeBlurAlpha,
            effectExtension = homepageBlurExtension,
            effectTopOffsetPx = { topAppBarState.heightOffset },
        ) {
            TopAppBar(
                title = pageTitle,
                largeTitle = pageTitle,
                scrollBehavior = scrollBehavior,
                color = if (blurBackdrop != null) Color.Transparent else topBarColors(),
                showDivider = blurBackdrop == null,
                actions = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (blurBackdrop != null) Modifier.layerBackdrop(blurBackdrop) else Modifier)
                .verticalScroll(scrollState)
                .padding(
                    start = 16.dp,
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    end = 16.dp,
                    bottom = maxOf(innerPadding.calculateBottomPadding(), bottomChromePadding) + 12.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (currentTab) {
                0 -> HomePage(
                    rootGranted = rootGranted,
                    onHeroLongPress = onHomeHeroLongPress,
                    onOpenDeviceDetails = onOpenDeviceDetails,
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun stableTopBarInsets(): WindowInsets {
    val statusBarHeight = stableStatusBarHeight()
    return WindowInsets(left = 0.dp, top = statusBarHeight, right = 0.dp, bottom = 0.dp)
}

@Composable
internal fun stableStatusBarHeight(): Dp {
    val density = LocalDensity.current
    val statusBarHeightPx = WindowInsets.statusBars.getTop(density)
    return if (statusBarHeightPx > 0) {
        with(density) { statusBarHeightPx.toDp() }
    } else {
        24.dp
    }
}
