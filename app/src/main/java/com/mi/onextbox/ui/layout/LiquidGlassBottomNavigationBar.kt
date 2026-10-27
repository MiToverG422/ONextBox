package com.mi.onextbox.ui.layout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.mi.onextbox.ui.common.BottomTab
import com.mi.onextbox.ui.LocalBottomBarContentOpacity
import com.mi.onextbox.ui.common.DampedDragAnimation
import com.mi.onextbox.ui.common.InteractiveHighlight
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

val LocalLiquidBottomTabScale = compositionLocalOf { { 1f } }

@Composable
fun LiquidGlassBottomNavigationBar(
    tabs: List<BottomTab>,
    selectedIndex: Int,
    pagerState: PagerState,
    onTabSelected: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    hideLabels: Boolean = false,
    onCapsuleBoundsChanged: (Rect) -> Unit = {},
    startupRevealProgress: () -> Float = { 1f },
) {
    if (tabs.isEmpty()) return
    val contentOpacity = LocalBottomBarContentOpacity.current
    val latestOnCapsuleBoundsChanged by rememberUpdatedState(onCapsuleBoundsChanged)
    val latestStartupRevealProgress by rememberUpdatedState(startupRevealProgress)
    val startupInteractionReady by remember {
        derivedStateOf { latestStartupRevealProgress() >= 1f }
    }
    DisposableEffect(Unit) {
        onDispose { latestOnCapsuleBoundsChanged(Rect.Zero) }
    }

    val colors = COUITheme.colorScheme
    val isLightTheme = colors.background.red + colors.background.green + colors.background.blue > 1.5f
    val contentColor = if (isLightTheme) Color.Black else Color.White
    val accentColor = if (isLightTheme) Color(0xFF0088FF) else Color(0xFF0091FF)
    val containerColor = if (isLightTheme) Color(0xFFFAFAFA).copy(0.4f) else Color(0xFF121212).copy(0.4f)
    val iconColorFilter = ColorFilter.tint(contentColor)
    val tabsBackdrop = rememberLayerBackdrop()
    val capsuleShape = remember { Capsule() }

    BoxWithConstraints(
        modifier = modifier
            .padding(bottom = 16.dp)
            .padding(horizontal = 40.dp)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(maxHeight = 64.dp.roundToPx()))
                layout(placeable.width, placeable.height) {
                    placeable.place(0, 0)
                }
            }
            .bottomBarStartupReveal(startupRevealProgress)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (latestStartupRevealProgress() >= 1f) return@awaitEachGesture
                    down.consume()
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            ((constraints.maxWidth.toFloat() - 8.dp.toPx()) / tabs.size).coerceAtLeast(1f)
        }
        val barWidthPx = constraints.maxWidth.toFloat()
        val horizontalPaddingPx = with(density) { 4.dp.toPx() }
        val innerWidthPx = (barWidthPx - horizontalPaddingPx * 2f).coerceAtLeast(0f)
        val animationScope = rememberCoroutineScope()
        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density, constraints.maxWidth) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth.coerceAtLeast(1).toFloat()).fastCoerceIn(-1f, 1f)
                with(density) {
                    4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        var currentIndex by remember(tabs.size) { mutableIntStateOf(selectedIndex.coerceIn(0, tabs.size - 1)) }
        val latestSelectedIndex by rememberUpdatedState(selectedIndex)
        val latestOnTabSelected by rememberUpdatedState(onTabSelected)
        val dampedDragAnimation = remember(animationScope, tabs.size, tabWidth, isLtr) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedIndex.coerceIn(0, tabs.size - 1).toFloat(),
                valueRange = 0f..(tabs.size - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().coerceIn(0, tabs.size - 1)
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                    }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabs.size - 1).toFloat()),
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                },
            )
        }

        LaunchedEffect(selectedIndex, tabs.size) {
            currentIndex = selectedIndex.coerceIn(0, tabs.size - 1)
        }

        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }.drop(1).collectLatest { index ->
                dampedDragAnimation.animateToValue(index.toFloat())
                if (latestSelectedIndex != index) {
                    latestOnTabSelected(index)
                }
            }
        }

        val interactiveHighlight = remember(animationScope, dampedDragAnimation, tabWidth, isLtr) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        x = if (isLtr) {
                            (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        } else {
                            size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        },
                        y = size.height / 2f,
                    )
                },
            )
        }

        val content: @Composable RowScope.() -> Unit = {
            tabs.forEachIndexed { index, tab ->
                val scale = lerp(1f, 1.05f, dampedDragAnimation.pressProgress)
                val icon = if (index == currentIndex) tab.selectedIcon else tab.unselectedIcon
                val logicalCenter = horizontalPaddingPx + (index + 0.5f) * tabWidth
                val physicalCenter = if (isLtr) logicalCenter else barWidthPx - logicalCenter
                val startupCenterOffset = barWidthPx / 2f - physicalCenter
                Column(
                    Modifier
                        .graphicsLayer {
                            translationX = startupCenterOffset *
                                (1f - latestStartupRevealProgress().coerceIn(0f, 1f))
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(capsuleShape)
                        .clickable(enabled = startupInteractionReady) { latestOnTabSelected(index) }
                        .fillMaxHeight()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val contentScale = LocalLiquidBottomTabScale.current()
                    Image(
                        imageVector = icon,
                        contentDescription = stringResource(tab.titleRes),
                        modifier = Modifier
                            .size(28.dp)
                            .graphicsLayer {
                                scaleX = contentScale
                                scaleY = contentScale
                            },
                        colorFilter = iconColorFilter,
                    )
                    if (!hideLabels) {
                        BasicText(
                            text = stringResource(tab.titleRes),
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = contentScale
                                    scaleY = contentScale
                                },
                            style = TextStyle(
                                color = contentColor,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                        )
                    }
                }
            }
        }


        Box(
            Modifier
                .height(64.dp)
                .fillMaxWidth()
                .bottomBarStartupMaterialWidth(startupRevealProgress)
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { capsuleShape },
                    effects = {
                        vibrancy()
                        blur(8.dp.toPx())
                        lens(24.dp.toPx(), 24.dp.toPx())
                    },
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .then(interactiveHighlight.modifier)
                .onGloballyPositioned { coordinates ->
                    latestOnCapsuleBoundsChanged(Rect(
                        coordinates.localToWindow(Offset.Zero),
                        coordinates.localToWindow(Offset(
                            coordinates.size.width.toFloat(),
                            coordinates.size.height.toFloat(),
                        )),
                    ))
                },
        )

        Row(
            Modifier
                .height(64.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = panelOffset
                    alpha = contentOpacity().coerceIn(0f, 1f)
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                    val scale = lerp(1f, 1f + 16.dp.toPx() / size.width,
                        dampedDragAnimation.pressProgress)
                    scaleX = scale
                    scaleY = scale
                }
                .bottomBarStartupContentClip(startupRevealProgress)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )

        CompositionLocalProvider(
            LocalLiquidBottomTabScale provides { lerp(1f, 1.2f, dampedDragAnimation.pressProgress) },
        ) {
            Box(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .height(56.dp)
                    .fillMaxWidth(),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .bottomBarStartupMaterialWidth(startupRevealProgress)
                        .graphicsLayer { translationX = panelOffset }
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { capsuleShape },
                            highlight = {
                                Highlight.Default.copy(alpha = dampedDragAnimation.pressProgress)
                            },
                            effects = {
                                val progress = dampedDragAnimation.pressProgress
                                vibrancy()
                                blur(8.dp.toPx())
                                lens(24.dp.toPx() * progress, 24.dp.toPx() * progress)
                            },
                            onDrawSurface = { drawRect(containerColor) },
                        )
                        .then(interactiveHighlight.modifier),
                )
                Row(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationX = panelOffset }
                        .bottomBarStartupContentClip(startupRevealProgress)
                        .padding(horizontal = 4.dp)
                        .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                    verticalAlignment = Alignment.CenterVertically,
                    content = content,
                )
            }
        }

        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    alpha = contentOpacity().coerceIn(0f, 1f)
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                    val targetCenter = if (isLtr) {
                        (dampedDragAnimation.value + 0.5f) * tabWidth
                    } else {
                        innerWidthPx - (dampedDragAnimation.value + 0.5f) * tabWidth
                    }
                    val center = lerp(innerWidthPx / 2f, targetCenter,
                        latestStartupRevealProgress().coerceIn(0f, 1f))
                    translationX = center - tabWidth / 2f + panelOffset
                }
                .then(if (startupInteractionReady) interactiveHighlight.gestureModifier else Modifier)
                .then(if (startupInteractionReady) dampedDragAnimation.modifier else Modifier)
                .height(56.dp)
                .fillMaxWidth(1f / tabs.size)
                .bottomBarStartupMaterialWidth(startupRevealProgress)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { capsuleShape },
                    highlight = {
                        Highlight.Default.copy(alpha = dampedDragAnimation.pressProgress)
                    },
                    shadow = {
                        Shadow(alpha = dampedDragAnimation.pressProgress)
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(radius = 8.dp * progress, alpha = progress)
                    },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        lens(
                            10.dp.toPx() * progress,
                            14.dp.toPx() * progress,
                            chromaticAberration = true,
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(
                            color = if (isLightTheme) Color.Black.copy(0.1f) else Color.White.copy(0.1f),
                            alpha = 1f - progress,
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    },
                ),
        )
    }
}
