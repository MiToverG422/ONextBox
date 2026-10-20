package com.mi.onextbox.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import io.github.suqi8.coui.kmp.theme.COUITheme
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

/** Share configuration and content, not the visual implementation of each UI family. */
@Composable
fun AppUiStyleAppTheme(
    style: AppUiStyle,
    mode: AppThemeMode,
    keyColor: Long?,
    paletteStyle: Int = 0,
    colorSpec: Int = 0,
    content: @Composable () -> Unit,
) {
    Material3ExpressiveAppTheme(mode = mode, keyColor = keyColor, use2026 = style == AppUiStyle.Material3Expressive) {
        MiuixAppTheme(style == AppUiStyle.Miuix, mode, keyColor, paletteStyle, colorSpec, content)
    }
}

@Composable
private fun MiuixAppTheme(
    active: Boolean,
    mode: AppThemeMode,
    keyColor: Long?,
    paletteStyle: Int,
    colorSpec: Int,
    content: @Composable () -> Unit,
) {
    val originalIndication = LocalIndication.current
    val originalOverscroll = LocalOverscrollFactory.current
    val controller = remember(mode, keyColor, paletteStyle, colorSpec) {
        ThemeController(
            colorSchemeMode = ColorSchemeMode.valueOf(mode.name),
            keyColor = keyColor?.let(::Color),
            paletteStyle = ThemePaletteStyle.entries.getOrNull(paletteStyle) ?: ThemePaletteStyle.TonalSpot,
            colorSpec = ThemeColorSpec.entries.getOrNull(colorSpec) ?: ThemeColorSpec.Spec2021,
        )
    }
    val colors = controller.currentColors()
        // Legacy feature-specific content uses these locals too. Keep its text/icons readable
        // while the shared cards, controls and page chrome use native Miuix components.
        val compatibleColors = COUITheme.colorScheme.copy(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            primaryContainer = colors.primaryContainer,
            onPrimaryContainer = colors.onPrimaryContainer,
            secondary = colors.secondary,
            onSecondary = colors.onSecondary,
            secondaryContainer = colors.secondaryContainer,
            onSecondaryContainer = colors.onSecondaryContainer,
            background = colors.background,
            onBackground = colors.onBackground,
            surface = colors.surface,
            onSurface = colors.onSurface,
            surfaceVariant = colors.surfaceVariant,
            onSurfaceSecondary = colors.onSurfaceSecondary,
            onSurfaceVariantSummary = colors.onSurfaceVariantSummary,
            onSurfaceVariantActions = colors.onSurfaceVariantActions,
            surfaceContainer = colors.surfaceContainer,
            surfaceContainerHigh = colors.surfaceContainerHigh,
            surfaceContainerHighest = colors.surfaceContainerHighest,
            dividerLine = colors.dividerLine,
            error = colors.error,
            onError = colors.onError,
        )
        val materialColors = MaterialTheme.colorScheme.copy(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            primaryContainer = colors.primaryContainer,
            onPrimaryContainer = colors.onPrimaryContainer,
            background = colors.background,
            onBackground = colors.onBackground,
            surface = colors.surface,
            onSurface = colors.onSurface,
            surfaceContainer = colors.surfaceContainer,
            surfaceContainerHigh = colors.surfaceContainerHigh,
            surfaceContainerHighest = colors.surfaceContainerHighest,
            onSurfaceVariant = colors.onSurfaceVariantSummary,
            outline = colors.outline,
            error = colors.error,
        )
        // Keep identical provider nesting for every style: the pager and navigation back stack
        // are remembered below this theme, so branching the content would recreate them.
        COUITheme(colors = if (active) compatibleColors else COUITheme.colorScheme) {
            MaterialTheme(colorScheme = if (active) materialColors else MaterialTheme.colorScheme) {
                MiuixTheme(controller = controller) {
                    CompositionLocalProvider(
                        LocalIndication provides if (active) LocalIndication.current else originalIndication,
                        LocalOverscrollFactory provides if (active) LocalOverscrollFactory.current else originalOverscroll,
                        content = content,
                    )
                }
            }
        }
}
