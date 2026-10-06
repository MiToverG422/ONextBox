package com.mi.onextbox.ui.layout

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
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
import com.mi.onextbox.ui.home.HomePage
import com.mi.onextbox.ui.common.AppUiStyle
import com.mi.onextbox.ui.common.LocalAppUiStyle
import com.mi.onextbox.ui.common.ScrollResetEffect
import com.mi.onextbox.ui.layout.BlurredChromeBar
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.basic.ListPopup
import io.github.suqi8.coui.kmp.basic.NavigationBar
import io.github.suqi8.coui.kmp.basic.NavigationItem
import io.github.suqi8.coui.kmp.basic.PopupPositionProvider
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.Switch
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.rememberTopAppBarState
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.layerBackdrop
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
import kotlin.math.abs
import kotlin.math.max

@Composable
fun Page(
    modifier: Modifier,
    currentTab: Int,
    rootGranted: Boolean,
    bottomChromePadding: Dp,
    onHomeHeroLongPress: () -> Unit,
    blurBackdrop: LayerBackdrop?,
    scrollResetKey: Int? = null,
) {
    if (LocalAppUiStyle.current == AppUiStyle.Material3Expressive) {
        Material3ExpressivePage(
            modifier = modifier,
            currentTab = currentTab,
            rootGranted = rootGranted,
            bottomChromePadding = bottomChromePadding,
            onHomeHeroLongPress = onHomeHeroLongPress,
            scrollResetKey = scrollResetKey,
        )
        return
    }
    val scrollState = rememberScrollState()
    val topAppBarState = rememberTopAppBarState()
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
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Material3ExpressivePage(
    modifier: Modifier,
    currentTab: Int,
    rootGranted: Boolean,
    bottomChromePadding: Dp,
    onHomeHeroLongPress: () -> Unit,
    scrollResetKey: Int?,
) {
    val title = if (currentTab == 0) stringResource(R.string.tab_home) else ""
    val scrollState = rememberScrollState()
    val behavior = androidx.compose.material3.TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    ScrollResetEffect(scrollResetKey) {
        scrollState.scrollTo(0)
        behavior.state.heightOffset = 0f
        behavior.state.contentOffset = 0f
    }
    androidx.compose.material3.Scaffold(
        modifier = modifier.nestedScroll(behavior.nestedScrollConnection),
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            androidx.compose.material3.LargeFlexibleTopAppBar(
                title = { androidx.compose.material3.Text(title) },
                scrollBehavior = behavior,
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
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
                0 -> HomePage(rootGranted = rootGranted, onHeroLongPress = onHomeHeroLongPress)
            }
        }
        }
    }
}

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
