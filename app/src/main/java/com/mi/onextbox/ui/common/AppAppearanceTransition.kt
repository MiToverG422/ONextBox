package com.mi.onextbox.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal val LocalAppAppearanceTransition = staticCompositionLocalOf<AppAppearanceTransitionState?> { null }
private val appearanceEnterEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val bottomExitEasing = Easing { t -> t * t * (5f * t - 4f) }
private val bottomEnterEasing = Easing { fraction ->
    val t = fraction - 1f
    t * t * (2.4f * t + 1.4f) + 1f
}

/** Keep navigation/scroll state alive while changing language, UI style, or theme mode. */
@Stable
internal class AppAppearanceTransitionState {
    val contentAlpha = Animatable(1f)
    val contentScale = Animatable(1f)
    val coverAlpha = Animatable(0f)
    val bottomOffsetDp = Animatable(0f)
    val bottomScale = Animatable(1f)
    var isChanging by mutableStateOf(false)
        private set

    suspend fun change(commit: () -> Unit) {
        if (isChanging) return
        isChanging = true
        try {
            if (currentCoroutineContext()[MotionDurationScale]?.scaleFactor == 0f) {
                commit()
                return
            }
            coroutineScope {
                launch { contentAlpha.animateTo(0f, tween(500, easing = FastOutSlowInEasing)) }
                launch { contentScale.animateTo(0.92f, tween(500, easing = FastOutSlowInEasing)) }
                launch { bottomOffsetDp.animateTo(150f, tween(500, easing = bottomExitEasing)) }
                launch { bottomScale.animateTo(0.6f, tween(500, easing = bottomExitEasing)) }
                launch {
                    delay(200)
                    coverAlpha.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
                }
            }
            // Apply appearance changes only once the outgoing view is fully covered.
            commit()
            contentScale.snapTo(0.85f)
            bottomOffsetDp.snapTo(250f)
            bottomScale.snapTo(1f)
            // Let resources, theme, and the new UI style recompose/layout before revealing them.
            withFrameNanos { }
            withFrameNanos { }
            coroutineScope {
                launch { coverAlpha.animateTo(0f, tween(250, easing = FastOutSlowInEasing)) }
                launch { contentAlpha.animateTo(1f, tween(1700, easing = appearanceEnterEasing)) }
                launch { contentScale.animateTo(1f, tween(1700, easing = appearanceEnterEasing)) }
                launch { bottomOffsetDp.animateTo(0f, tween(1200, easing = bottomEnterEasing)) }
            }
        } finally {
            // Disposal or a cancelled request must never leave an invisible or blocked screen.
            withContext(NonCancellable) {
                contentAlpha.snapTo(1f)
                contentScale.snapTo(1f)
                coverAlpha.snapTo(0f)
                bottomOffsetDp.snapTo(0f)
                bottomScale.snapTo(1f)
                isChanging = false
            }
        }
    }
}

@Composable
internal fun AppAppearanceTransitionHost(state: AppAppearanceTransitionState, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CompositionLocalProvider(LocalAppAppearanceTransition provides state) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                alpha = state.contentAlpha.value
                scaleX = state.contentScale.value
                scaleY = state.contentScale.value
            }) { content() }
        }
        if (state.isChanging) {
            Box(Modifier.fillMaxSize()
                .graphicsLayer { alpha = state.coverAlpha.value }
                .background(Color.Black)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent().changes.forEach { it.consume() }
                    }
                })
        }
    }
    // Register after the page handlers so Back cannot navigate beneath the transition cover.
    BackHandler(enabled = state.isChanging) { }
}

@Composable
internal fun Modifier.appearanceChangeBottomBar(): Modifier {
    val state = LocalAppAppearanceTransition.current ?: return this
    val density = LocalDensity.current
    return graphicsLayer {
        translationY = with(density) { state.bottomOffsetDp.value.dp.toPx() }
        scaleX = state.bottomScale.value
        scaleY = state.bottomScale.value
    }
}
