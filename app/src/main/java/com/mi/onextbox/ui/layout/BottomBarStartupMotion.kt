package com.mi.onextbox.ui.layout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

@Composable
internal fun rememberBottomBarStartupProgress(ready: Boolean, animate: Boolean): () -> Float {
    var consumed by rememberSaveable { mutableStateOf(!animate) }
    val progress = remember { Animatable(1f) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(ready, animate, lifecycleOwner) {
        if (!animate || consumed) {
            consumed = true
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        if (!ready) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (consumed) return@repeatOnLifecycle
            consumed = true
            try {
                if (currentCoroutineContext()[MotionDurationScale]?.scaleFactor != 0f) {
                    progress.snapTo(0f)
                    progress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(420, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)),
                    )
                }
            } finally {
                withContext(NonCancellable) { progress.snapTo(1f) }
            }
        }
    }
    return remember { { progress.value } }
}
