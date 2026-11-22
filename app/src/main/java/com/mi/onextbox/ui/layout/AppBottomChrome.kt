package com.mi.onextbox.ui.layout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop as LiquidLayerBackdrop
import com.mi.onextbox.ui.common.BottomTab
import com.mi.onextbox.ui.common.LocalPreviewTextPlaceholders
import com.mi.onextbox.ui.common.appearanceChangeBottomBar
import io.github.suqi8.coui.kmp.basic.NavigationBarDisplayMode
import io.github.suqi8.coui.kmp.basic.NavigationBarDefaults
import io.github.suqi8.coui.kmp.basic.NavigationBarItem
import io.github.suqi8.coui.kmp.basic.NavigationItem
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
internal fun AppBottomChrome(
    mode: FloatingBottomBarMode,
    tabs: List<BottomTab>,
    pagerState: PagerState,
    selectedIndex: Int,
    hideLabels: Boolean,
    collapseState: BottomBarCollapseState,
    blurBackdrop: LayerBackdrop?,
    liquidBackdrop: LiquidLayerBackdrop?,
    visible: Boolean = true,
    liquidOnly: Boolean = liquidBackdrop != null,
    startupRevealProgress: () -> Float = { 1f },
    onBoundsChange: (FloatingBottomBarMode, Rect) -> Unit = { _, _ -> },
    onHeightChange: (Int) -> Unit = {},
    onTabSelected: (Int) -> Unit,
) {
    if (!mode.visible) return
    val textPlaceholders = LocalPreviewTextPlaceholders.current
    val liquidGlassActive = liquidBackdrop != null
    val liquidGlassVisible = visible && liquidOnly && liquidGlassActive
    AnimatedVisibility(
        visible = liquidGlassVisible,
        modifier = Modifier.appearanceChangeBottomBar(),
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
    ) {
        liquidBackdrop?.let { backdrop ->
            LiquidGlassBottomNavigationBar(
                hideLabels = hideLabels,
                startupRevealProgress = startupRevealProgress,
                collapseState = collapseState,
                tabs = tabs,
                selectedIndex = selectedIndex,
                pagerState = pagerState,
                backdrop = backdrop,
                onCapsuleBoundsChanged = { onBoundsChange(FloatingBottomBarMode.LiquidGlass, it) },
                onTabSelected = onTabSelected,
                modifier = Modifier
                    .onGloballyPositioned { coordinates ->
                        onHeightChange(coordinates.size.height)
                    },
            )
        }
    }
    AnimatedVisibility(
        visible = visible && !liquidOnly && !liquidGlassActive,
        modifier = Modifier.appearanceChangeBottomBar(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                DisposableEffect(mode) {
                    onDispose { onBoundsChange(mode, Rect.Zero) }
                }
                if (mode == FloatingBottomBarMode.Standard) {
                    BlurredStandardNavigationBar(
                        backdrop = blurBackdrop,
                        modifier = Modifier
                            .onGloballyPositioned { coordinates ->
                                onHeightChange(coordinates.size.height)
                                onBoundsChange(mode, coordinates.boundsInWindow())
                            }
                            .bottomBarCollapseGestures(
                                collapseState.takeIf { it.mode == BottomBarAutoCollapse.Bottom },
                                startupRevealProgress,
                            )
                            .graphicsLayer { alpha = startupRevealProgress().coerceIn(0f, 1f) },
                        mode = if (hideLabels) NavigationBarDisplayMode.IconOnly
                        else NavigationBarDisplayMode.IconAndText,
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            val selected = index == selectedIndex
                            val labelColor = if (selected) COUITheme.colorScheme.onSurfaceContainer
                            else COUITheme.colorScheme.onSurfaceSecondary
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (startupRevealProgress() >= 1f && collapseState.interactionReady) {
                                        onTabSelected(index)
                                    }
                                },
                                icon = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                label = if (textPlaceholders) "" else stringResource(tab.titleRes),
                                modifier = if (textPlaceholders && !hideLabels) Modifier.drawBehind {
                                    val width = 20.dp.toPx()
                                    val height = 4.dp.toPx()
                                    val y = size.height - NavigationBarDefaults.LabelBottomPadding.toPx() - height
                                    drawRoundRect(labelColor.copy(alpha = .7f),
                                        Offset((size.width - width) / 2f, y), Size(width, height), CornerRadius(height / 2f))
                                } else Modifier,
                            )
                        }
                    }
                } else {
                    BottomNavigationBar(
                        hideLabels = hideLabels,
                        startupRevealProgress = startupRevealProgress,
                        collapseState = collapseState,
                        onCapsuleBoundsChanged = { onBoundsChange(FloatingBottomBarMode.Coui, it) },
                        items = tabs.mapIndexed { index, tab ->
                            NavigationItem(
                                label = stringResource(tab.titleRes),
                                icon = if (index == selectedIndex) tab.selectedIcon else tab.unselectedIcon,
                            )
                        },
                        selectedIndex = selectedIndex,
                        onItemSelected = onTabSelected,
                        color = Color.Transparent,
                        showDivider = false,
                        mode = NavigationBarDisplayMode.IconAndText,
                        selectedIcons = tabs.map { it.selectedIcon },
                        unselectedIcons = tabs.map { it.unselectedIcon },
                        modifier = Modifier
                            .onGloballyPositioned { coordinates ->
                                onHeightChange(coordinates.size.height)
                            },
                    )
                }
            }
        }
    }
}
