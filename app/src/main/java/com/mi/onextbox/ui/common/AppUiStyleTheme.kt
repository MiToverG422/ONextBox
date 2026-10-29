package com.mi.onextbox.ui.common

import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.luminance
import io.github.suqi8.coui.kmp.theme.COUITheme

val LocalAppUiStyle = compositionLocalOf { AppUiStyle.ColorOs }
/** Keep shared Compose primitives in sync with the surrounding COUI palette. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun AppUiStyleAppTheme(
    style: AppUiStyle,
    mode: AppThemeMode,
    keyColor: Long?,
    paletteStyle: Int = 0,
    colorSpec: Int = 0,
    content: @Composable () -> Unit,
) {
    val couiIndication = LocalIndication.current
    val coui = COUITheme.colorScheme
    val base = if (coui.background.luminance() < .5f) darkColorScheme() else lightColorScheme()
    MaterialTheme(
        colorScheme = base.copy(
            primary = coui.primary,
            background = coui.background,
            onBackground = coui.onBackground,
            surface = coui.surface,
            onSurface = coui.onSurface,
            onSurfaceVariant = coui.onSurfaceVariantSummary,
        ),
    ) {
        CompositionLocalProvider(LocalIndication provides couiIndication) {
            content()
        }
    }
}
