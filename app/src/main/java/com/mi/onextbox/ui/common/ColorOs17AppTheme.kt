package com.mi.onextbox.ui.common

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowInsetsControllerCompat
import io.github.suqi8.coui.kmp.theme.COUITheme

internal val LocalColorOsMonetEnabled = staticCompositionLocalOf { false }

@Composable
internal fun ColorOs17AppTheme(
    nativeAccent: Boolean,
    monetColors: io.github.suqi8.coui.kmp.theme.Colors? = null,
    content: @Composable () -> Unit,
) {
    val base = monetColors ?: COUITheme.colorScheme
    val preset = if (nativeAccent) rememberCouiAccentPreset() else CouiAccentPreset.System
    val colors = if (nativeAccent) {
        val dark = base.surface.luminance() < .5f
        val accent = if (preset == CouiAccentPreset.System) rememberSystemCouiAccent(dark) else preset.accent(dark)
        base.withCouiAccent(accent, dark)
    } else base
    CompositionLocalProvider(LocalColorOsMonetEnabled provides !nativeAccent, LocalCouiAccentPreset provides preset) {
        COUITheme(colors = colors, content = content)
    }
}

@Composable
internal fun ColorOsDialogSystemBars(darkTheme: Boolean, dimProgress: Float) {
    var ancestor = LocalView.current.parent
    while (ancestor != null && ancestor !is DialogWindowProvider) ancestor = ancestor.parent
    val window = (ancestor as? DialogWindowProvider)?.window ?: return
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    DisposableEffect(window) {
        val previousStatus = controller.isAppearanceLightStatusBars
        val previousNavigation = controller.isAppearanceLightNavigationBars
        val previousDim = window.attributes.dimAmount
        val previousDimBehind = window.attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0
        onDispose {
            controller.isAppearanceLightStatusBars = previousStatus
            controller.isAppearanceLightNavigationBars = previousNavigation
            window.setDimAmount(previousDim)
            if (!previousDimBehind) window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
    }
    SideEffect {
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(.2f * dimProgress.coerceIn(0f, 1f))
    }
}
