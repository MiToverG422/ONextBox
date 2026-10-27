// Copyright 2025, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.suqi8.coui.kmp.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import io.github.suqi8.coui.kmp.utils.COUIIndication
import io.github.suqi8.coui.kmp.utils.COUIOverscrollFactory

/**
 * The COUI theme that provides color and text styles for the COUI components.
 * This theme supports dynamic color schemes through the [ThemeController].
 *
 * @param controller The [ThemeController] that controls the current color scheme.
 * @param textStyles The text styles for the COUI components.
 * @param content The content of the COUI theme.
 */
@Composable
fun COUITheme(
    controller: ThemeController,
    textStyles: TextStyles = COUITheme.textStyles,
    content: @Composable () -> Unit,
) {
    val rawColors = controller.currentColors()
    val couiColors = remember { rawColors.copy() }.apply { updateColorsFrom(rawColors) }
    val couiTextStyles = remember { textStyles.copy() }.apply { updateTextStylesFrom(textStyles) }
    val couiIndication = remember(couiColors.onBackground) { COUIIndication(color = couiColors.onBackground) }
    CompositionLocalProvider(
        LocalColors provides couiColors,
        LocalTextStyles provides couiTextStyles,
        LocalIndication provides couiIndication,
        LocalContentColor provides couiColors.onBackground,
        LocalColorSchemeMode provides controller.colorSchemeMode,
        LocalOverscrollFactory provides COUIOverscrollFactory,
    ) {
        content()
    }
}

/**
 * The COUI theme that provides color and text styles for the COUI components.
 * This theme uses the provided [colors] and [textStyles].
 *
 * @param colors The color scheme for the COUI components.
 * @param textStyles The text styles for the COUI components.
 * @param content The content of the COUI theme.
 */
@Composable
fun COUITheme(
    colors: Colors = COUITheme.colorScheme,
    textStyles: TextStyles = COUITheme.textStyles,
    content: @Composable () -> Unit,
) {
    val couiColors = remember { colors.copy() }.apply { updateColorsFrom(colors) }
    val couiTextStyles = remember { textStyles.copy() }.apply { updateTextStylesFrom(textStyles) }
    val couiIndication = remember(couiColors.onBackground) { COUIIndication(color = couiColors.onBackground) }
    CompositionLocalProvider(
        LocalColors provides couiColors,
        LocalTextStyles provides couiTextStyles,
        LocalIndication provides couiIndication,
        LocalOverscrollFactory provides COUIOverscrollFactory,
    ) {
        content()
    }
}

object COUITheme {
    val colorScheme: Colors
        @Composable @ReadOnlyComposable
        get() = LocalColors.current

    val textStyles: TextStyles
        @Composable @ReadOnlyComposable
        get() = LocalTextStyles.current

    val colorSchemeMode: ColorSchemeMode?
        @Composable @ReadOnlyComposable
        get() = LocalColorSchemeMode.current

    val isDynamicColor: Boolean
        @Composable @ReadOnlyComposable
        get() = when (colorSchemeMode) {
            ColorSchemeMode.MonetSystem,
            ColorSchemeMode.MonetLight,
            ColorSchemeMode.MonetDark,
            -> true

            else -> false
        }
}
