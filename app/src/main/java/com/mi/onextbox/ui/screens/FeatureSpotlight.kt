package com.mi.onextbox.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay

@Stable
internal class FeatureSpotlightState {
    data class Target(val page: FeaturePageMode, val title: Int)
    var target by mutableStateOf<Target?>(null)
        private set

    fun show(entry: FeatureSearchIndex.Entry) { target = Target(entry.page, entry.title) }
    fun clear(expected: Target) { if (target === expected) target = null }
}

internal val LocalFeatureSpotlight = staticCompositionLocalOf<FeatureSpotlightState?> { null }
internal val LocalFeatureSpotlightPage = staticCompositionLocalOf { FeaturePageMode.Main }
internal val LocalFeatureSpotlightBottomPadding = staticCompositionLocalOf { 0.dp }

@Composable
internal fun FeatureSpotlightPage(page: FeaturePageMode, bottomPadding: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    val state = LocalFeatureSpotlight.current
    val target = state?.target?.takeIf { it.page == page }
    LaunchedEffect(target) {
        if (target != null) { delay(4000); state.clear(target) }
    }
    DisposableEffect(target) {
        onDispose { if (target != null) state.clear(target) }
    }
    CompositionLocalProvider(LocalFeatureSpotlightPage provides page,
        LocalFeatureSpotlightBottomPadding provides bottomPadding, content = content)
}

@Composable
internal fun featureSpotlightModifier(title: String): Modifier {
    val state = LocalFeatureSpotlight.current ?: return Modifier
    val target = state.target ?: return Modifier
    if (target.page != LocalFeatureSpotlightPage.current || LocalResources.current.getString(target.title) != title) return Modifier
    val requester = remember(target) { BringIntoViewRequester() }
    val strength = remember(target) { Animatable(0f) }
    var measured by remember(target) { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val bottom = with(density) { (LocalFeatureSpotlightBottomPadding.current + 16.dp).toPx() }
    val color = COUITheme.colorScheme.primary
    val radius = with(density) { 12.dp.toPx() }
    LaunchedEffect(target, measured) {
        if (measured == IntSize.Zero) return@LaunchedEffect
        delay(320)
        requester.bringIntoView(Rect(0f, 0f, measured.width.toFloat(), measured.height + bottom))
        strength.animateTo(1f, tween(180))
        delay(900)
        strength.animateTo(0f, tween(650))
        state.clear(target)
    }
    return Modifier.bringIntoViewRequester(requester).onSizeChanged { measured = it }
        .drawBehind { drawRoundRect(color.copy(alpha = .16f * strength.value), cornerRadius = CornerRadius(radius)) }
        .testTag("featureUpdateTarget")
}
