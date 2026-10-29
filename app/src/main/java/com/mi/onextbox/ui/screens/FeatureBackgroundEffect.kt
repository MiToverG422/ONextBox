package com.mi.onextbox.ui.screens

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.*

internal val FeatureBackgroundStyleIds = listOf(0, 1, 2)

@Composable
internal fun rememberFeatureBackgroundSelection(): Pair<Int, (Int) -> Unit> {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("onextbox_prefs", Context.MODE_PRIVATE) }
    fun readStyle() = prefs.getInt("feature_background_style", 1).takeIf { it in FeatureBackgroundStyleIds } ?: 1
    var value by remember(prefs) { mutableIntStateOf(readStyle()) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "feature_background_style" || key == null) value = readStyle()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return value to { next -> prefs.edit().putInt("feature_background_style", next.takeIf { it in FeatureBackgroundStyleIds } ?: 1).apply() }
}

@Composable
internal fun FeatureBackgroundEffect(
    style: Int,
    dark: Boolean,
    surface: Color,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    if (style == 0) return
    if (style == 1) {
        FeaturePageAuroraBackground(dark, surface, modifier)
        return
    }
    val time = remember { mutableFloatStateOf(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(style, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var previous = System.nanoTime()
            while (isActive) {
                delay(33)
                val now = System.nanoTime()
                time.floatValue += (now - previous) / 1_000_000_000f
                previous = now
            }
        }
    }
    Canvas(modifier.fillMaxSize()) {
        drawRect(surface)
        val t = time.floatValue
        val w = size.width
        val h = size.height
        val intensity = if (dark) .28f else .18f
        fun glow(center: Offset, radius: Float, color: Color, alpha: Float) {
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)), center, radius), radius, center)
        }
        when (style) {
            2 -> repeat(3) { i ->
                val phase = t * .12f + i * 2.1f
                glow(Offset(w * (.5f + .36f * sin(phase)), h * (.42f + .3f * cos(phase * .7f))), w * .85f,
                    if (i == 1) Color(0xFF9C78E8) else accent, intensity)
            }
        }
    }
}
