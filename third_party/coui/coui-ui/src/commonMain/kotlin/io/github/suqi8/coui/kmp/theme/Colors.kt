// Copyright 2025, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.suqi8.coui.kmp.theme

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.graphics.Color

/**
 * The default color scheme for the COUI components.
 *
 * @param primary The primary color. Cases: Switch, Button, Slider.
 * @param onPrimary The color of the text on primary color. Cases: Switch, Button, Slider.
 * @param primaryVariant The variant color of the primary color.Cases:Card
 * @param onPrimaryVariant The color of the text on primary variant color.
 * @param disabledPrimary The disabled primary color of the switch.
 * @param disabledOnPrimary The color of the switch on disabled primary color.
 * @param disabledPrimaryButton The disabled primary color of the button.
 * @param disabledOnPrimaryButton The color of the button on disabled primary color.
 * @param disabledPrimarySlider The disabled primary color of the slider.
 * @param primaryContainer The container color of the primary color.
 * @param onPrimaryContainer The color of the text on primary container color.
 * @param secondary The secondary color.
 * @param onSecondary The color of the text on secondary color.
 * @param secondaryVariant The variant color of the secondary color.
 * @param onSecondaryVariant The color of the text on secondary variant color.
 * @param disabledSecondary The disabled secondary color.
 * @param disabledOnSecondary The color of the text on disabled secondary color.
 * @param disabledSecondaryVariant The disabled secondary color.
 * @param disabledOnSecondaryVariant The color of the text on disabled secondary variant color.
 * @param secondaryContainer The container color of the secondary color.
 * @param onSecondaryContainer The color of the text on secondary container color.
 * @param secondaryContainerVariant The variant color of the secondary container color.
 * @param onSecondaryContainerVariant The color of the text on secondary container variant color.
 * @param tertiaryContainer The container color of the tertiary color.
 * @param onTertiaryContainer The color of the text on tertiary container color.
 * @param tertiaryContainerVariant The variant color of the tertiary container color.
 * @param background The background color.
 * @param onBackground The color of the text on background color.
 * @param onBackgroundVariant The color of the text on background variant color.
 * @param surface The surface color.
 * @param onSurface The color of the text on surface color.
 * @param surfaceVariant The variant color of the surface color.
 * @param onSurfaceSecondary The color of the text on surface secondary color.
 * @param onSurfaceVariantSummary The color of the summary on surface variant color.
 * @param onSurfaceVariantActions The color of the actions on surface variant color.
 * @param disabledOnSurface The color of the text on disabled surface color.
 * @param outline The outline color.
 * @param dividerLine The divider line color.
 * @param surfaceContainer The container color of the surface color.
 * @param onSurfaceContainer The color of the text on surface container color.
 * @param onSurfaceContainerVariant The color of the text on surface container variant color.
 * @param surfaceContainerHigh The container color of the surface color.
 * @param onSurfaceContainerHigh The color of the text on surface container high color.
 * @param surfaceContainerHighest The container color of the surface color.
 * @param onSurfaceContainerHighest The color of the text on surface container highest color.
 * @param windowDimming The color of the window dimming. Cases: Dialog, Dropdown, Spinner, BottomSheet.
 * @param sliderKeyPoint The color of the slider key point.
 * @param sliderKeyPointForeground The foreground color of the slider key point.
 * @param sliderBackground The background color of the slider.
 */
@Stable
class Colors(
    primary: Color,
    onPrimary: Color,
    primaryVariant: Color,
    onPrimaryVariant: Color,
    error: Color,
    onError: Color,
    errorContainer: Color,
    onErrorContainer: Color,
    disabledPrimary: Color,
    disabledOnPrimary: Color,
    disabledPrimaryButton: Color,
    disabledOnPrimaryButton: Color,
    disabledPrimarySlider: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    secondary: Color,
    onSecondary: Color,
    secondaryVariant: Color,
    onSecondaryVariant: Color,
    disabledSecondary: Color,
    disabledOnSecondary: Color,
    disabledSecondaryVariant: Color,
    disabledOnSecondaryVariant: Color,
    secondaryContainer: Color,
    onSecondaryContainer: Color,
    secondaryContainerVariant: Color,
    onSecondaryContainerVariant: Color,
    tertiaryContainer: Color,
    onTertiaryContainer: Color,
    tertiaryContainerVariant: Color,
    background: Color,
    onBackground: Color,
    onBackgroundVariant: Color,
    surface: Color,
    onSurface: Color,
    surfaceVariant: Color,
    onSurfaceSecondary: Color,
    onSurfaceVariantSummary: Color,
    onSurfaceVariantActions: Color,
    disabledOnSurface: Color,
    surfaceContainer: Color,
    onSurfaceContainer: Color,
    onSurfaceContainerVariant: Color,
    surfaceContainerHigh: Color,
    onSurfaceContainerHigh: Color,
    surfaceContainerHighest: Color,
    onSurfaceContainerHighest: Color,
    outline: Color,
    dividerLine: Color,
    windowDimming: Color,
    sliderKeyPoint: Color,
    sliderKeyPointForeground: Color,
    sliderBackground: Color,
) {
    var primary by mutableStateOf(primary, structuralEqualityPolicy())
        internal set
    var onPrimary by mutableStateOf(onPrimary, structuralEqualityPolicy())
        internal set
    var primaryVariant by mutableStateOf(primaryVariant, structuralEqualityPolicy())
        internal set
    var onPrimaryVariant by mutableStateOf(onPrimaryVariant, structuralEqualityPolicy())
        internal set
    var error by mutableStateOf(error, structuralEqualityPolicy())
        internal set
    var onError by mutableStateOf(onError, structuralEqualityPolicy())
        internal set
    var errorContainer by mutableStateOf(errorContainer, structuralEqualityPolicy())
        internal set
    var onErrorContainer by mutableStateOf(onErrorContainer, structuralEqualityPolicy())
        internal set
    var disabledPrimary by mutableStateOf(disabledPrimary, structuralEqualityPolicy())
        internal set
    var disabledOnPrimary by mutableStateOf(disabledOnPrimary, structuralEqualityPolicy())
        internal set
    var disabledPrimaryButton by mutableStateOf(disabledPrimaryButton, structuralEqualityPolicy())
        internal set
    var disabledOnPrimaryButton by mutableStateOf(disabledOnPrimaryButton, structuralEqualityPolicy())
        internal set
    var disabledPrimarySlider by mutableStateOf(disabledPrimarySlider, structuralEqualityPolicy())
        internal set
    var primaryContainer by mutableStateOf(primaryContainer, structuralEqualityPolicy())
        internal set
    var onPrimaryContainer by mutableStateOf(onPrimaryContainer, structuralEqualityPolicy())
        internal set
    var secondary by mutableStateOf(secondary, structuralEqualityPolicy())
        internal set
    var onSecondary by mutableStateOf(onSecondary, structuralEqualityPolicy())
        internal set
    var secondaryVariant by mutableStateOf(secondaryVariant, structuralEqualityPolicy())
        internal set
    var onSecondaryVariant by mutableStateOf(onSecondaryVariant, structuralEqualityPolicy())
        internal set
    var disabledSecondary by mutableStateOf(disabledSecondary, structuralEqualityPolicy())
        internal set
    var disabledOnSecondary by mutableStateOf(disabledOnSecondary, structuralEqualityPolicy())
        internal set
    var disabledSecondaryVariant by mutableStateOf(disabledSecondaryVariant, structuralEqualityPolicy())
        internal set
    var disabledOnSecondaryVariant by mutableStateOf(disabledOnSecondaryVariant, structuralEqualityPolicy())
        internal set
    var secondaryContainer by mutableStateOf(secondaryContainer, structuralEqualityPolicy())
        internal set
    var onSecondaryContainer by mutableStateOf(onSecondaryContainer, structuralEqualityPolicy())
        internal set
    var secondaryContainerVariant by mutableStateOf(secondaryContainerVariant, structuralEqualityPolicy())
        internal set
    var onSecondaryContainerVariant by mutableStateOf(onSecondaryContainerVariant, structuralEqualityPolicy())
        internal set
    var tertiaryContainer by mutableStateOf(tertiaryContainer, structuralEqualityPolicy())
        internal set
    var onTertiaryContainer by mutableStateOf(onTertiaryContainer, structuralEqualityPolicy())
        internal set
    var tertiaryContainerVariant by mutableStateOf(tertiaryContainerVariant, structuralEqualityPolicy())
        internal set
    var background by mutableStateOf(background, structuralEqualityPolicy())
        internal set
    var onBackground by mutableStateOf(onBackground, structuralEqualityPolicy())
        internal set
    var onBackgroundVariant by mutableStateOf(onBackgroundVariant, structuralEqualityPolicy())
        internal set
    var surface by mutableStateOf(surface, structuralEqualityPolicy())
        internal set
    var onSurface by mutableStateOf(onSurface, structuralEqualityPolicy())
        internal set
    var surfaceVariant by mutableStateOf(surfaceVariant, structuralEqualityPolicy())
        internal set
    var onSurfaceSecondary by mutableStateOf(onSurfaceSecondary, structuralEqualityPolicy())
        internal set
    var onSurfaceVariantSummary by mutableStateOf(onSurfaceVariantSummary, structuralEqualityPolicy())
        internal set
    var onSurfaceVariantActions by mutableStateOf(onSurfaceVariantActions, structuralEqualityPolicy())
        internal set
    var disabledOnSurface by mutableStateOf(disabledOnSurface, structuralEqualityPolicy())
        internal set
    var surfaceContainer by mutableStateOf(surfaceContainer, structuralEqualityPolicy())
        internal set
    var onSurfaceContainer by mutableStateOf(onSurfaceContainer, structuralEqualityPolicy())
        internal set
    var onSurfaceContainerVariant by mutableStateOf(onSurfaceContainerVariant, structuralEqualityPolicy())
        internal set
    var surfaceContainerHigh by mutableStateOf(surfaceContainerHigh, structuralEqualityPolicy())
        internal set
    var onSurfaceContainerHigh by mutableStateOf(onSurfaceContainerHigh, structuralEqualityPolicy())
        internal set
    var surfaceContainerHighest by mutableStateOf(surfaceContainerHighest, structuralEqualityPolicy())
        internal set
    var onSurfaceContainerHighest by mutableStateOf(onSurfaceContainerHighest, structuralEqualityPolicy())
        internal set
    var outline by mutableStateOf(outline, structuralEqualityPolicy())
        internal set
    var dividerLine by mutableStateOf(dividerLine, structuralEqualityPolicy())
        internal set
    var windowDimming by mutableStateOf(windowDimming, structuralEqualityPolicy())
        internal set
    var sliderKeyPoint by mutableStateOf(sliderKeyPoint, structuralEqualityPolicy())
        internal set
    var sliderKeyPointForeground by mutableStateOf(sliderKeyPointForeground, structuralEqualityPolicy())
        internal set
    var sliderBackground by mutableStateOf(sliderBackground, structuralEqualityPolicy())
        internal set

    fun copy(
        primary: Color = this.primary,
        onPrimary: Color = this.onPrimary,
        primaryVariant: Color = this.primaryVariant,
        onPrimaryVariant: Color = this.onPrimaryVariant,
        error: Color = this.error,
        onError: Color = this.onError,
        errorContainer: Color = this.errorContainer,
        onErrorContainer: Color = this.onErrorContainer,
        disabledPrimary: Color = this.disabledPrimary,
        disabledOnPrimary: Color = this.disabledOnPrimary,
        disabledPrimaryButton: Color = this.disabledPrimaryButton,
        disabledOnPrimaryButton: Color = this.disabledOnPrimaryButton,
        disabledPrimarySlider: Color = this.disabledPrimarySlider,
        primaryContainer: Color = this.primaryContainer,
        onPrimaryContainer: Color = this.onPrimaryContainer,
        secondary: Color = this.secondary,
        onSecondary: Color = this.onSecondary,
        secondaryVariant: Color = this.secondaryVariant,
        onSecondaryVariant: Color = this.onSecondaryVariant,
        disabledSecondary: Color = this.disabledSecondary,
        disabledOnSecondary: Color = this.disabledOnSecondary,
        disabledSecondaryVariant: Color = this.disabledSecondaryVariant,
        disabledOnSecondaryVariant: Color = this.disabledOnSecondaryVariant,
        secondaryContainer: Color = this.secondaryContainer,
        onSecondaryContainer: Color = this.onSecondaryContainer,
        secondaryContainerVariant: Color = this.secondaryContainerVariant,
        onSecondaryContainerVariant: Color = this.onSecondaryContainerVariant,
        tertiaryContainer: Color = this.tertiaryContainer,
        onTertiaryContainer: Color = this.onTertiaryContainer,
        tertiaryContainerVariant: Color = this.tertiaryContainerVariant,
        background: Color = this.background,
        onBackground: Color = this.onBackground,
        onBackgroundVariant: Color = this.onBackgroundVariant,
        surface: Color = this.surface,
        onSurface: Color = this.onSurface,
        surfaceVariant: Color = this.surfaceVariant,
        onSurfaceSecondary: Color = this.onSurfaceSecondary,
        onSurfaceVariantSummary: Color = this.onSurfaceVariantSummary,
        onSurfaceVariantActions: Color = this.onSurfaceVariantActions,
        disabledOnSurface: Color = this.disabledOnSurface,
        surfaceContainer: Color = this.surfaceContainer,
        onSurfaceContainer: Color = this.onSurfaceContainer,
        onSurfaceContainerVariant: Color = this.onSurfaceContainerVariant,
        surfaceContainerHigh: Color = this.surfaceContainerHigh,
        onSurfaceContainerHigh: Color = this.onSurfaceContainerHigh,
        surfaceContainerHighest: Color = this.surfaceContainerHighest,
        onSurfaceContainerHighest: Color = this.onSurfaceContainerHighest,
        outline: Color = this.outline,
        dividerLine: Color = this.dividerLine,
        windowDimming: Color = this.windowDimming,
        sliderKeyPoint: Color = this.sliderKeyPoint,
        sliderKeyPointForeground: Color = this.sliderKeyPointForeground,
        sliderBackground: Color = this.sliderBackground,
    ): Colors = Colors(
        primary,
        onPrimary,
        primaryVariant,
        onPrimaryVariant,
        error,
        onError,
        errorContainer,
        onErrorContainer,
        disabledPrimary,
        disabledOnPrimary,
        disabledPrimaryButton,
        disabledOnPrimaryButton,
        disabledPrimarySlider,
        primaryContainer,
        onPrimaryContainer,
        secondary,
        onSecondary,
        secondaryVariant,
        onSecondaryVariant,
        disabledSecondary,
        disabledOnSecondary,
        disabledSecondaryVariant,
        disabledOnSecondaryVariant,
        secondaryContainer,
        onSecondaryContainer,
        secondaryContainerVariant,
        onSecondaryContainerVariant,
        tertiaryContainer,
        onTertiaryContainer,
        tertiaryContainerVariant,
        background,
        onBackground,
        onBackgroundVariant,
        surface,
        onSurface,
        surfaceVariant,
        onSurfaceSecondary,
        onSurfaceVariantSummary,
        onSurfaceVariantActions,
        disabledOnSurface,
        surfaceContainer,
        onSurfaceContainer,
        onSurfaceContainerVariant,
        surfaceContainerHigh,
        onSurfaceContainerHigh,
        surfaceContainerHighest,
        onSurfaceContainerHighest,
        outline,
        dividerLine,
        windowDimming,
        sliderKeyPoint,
        sliderKeyPointForeground,
        sliderBackground,
    )
}

/** COUI light scheme. COUI: coui_color_blue #0066FF; gray page surface, white cards and dialog panels. */
fun lightColorScheme(
    primary: Color = Color(0xFF0066FF),
    onPrimary: Color = Color.White,
    primaryVariant: Color = Color(0xFF0066FF),
    onPrimaryVariant: Color = Color(0xFFAECDFF),
    error: Color = Color(0xFFDB382C),
    onError: Color = Color(0xFFFFFFFF),
    errorContainer: Color = Color(0x26EB3B2F),
    onErrorContainer: Color = Color(0xFFDB382C),
    disabledPrimary: Color = Color(0x4D0066FF),
    disabledOnPrimary: Color = Color.White,
    disabledPrimaryButton: Color = Color(0x4D0066FF),
    disabledOnPrimaryButton: Color = Color(0x8AFFFFFF),
    disabledPrimarySlider: Color = Color(0xFFEBEBEB),
    primaryContainer: Color = Color(0xFF0066FF),
    onPrimaryContainer: Color = Color.White,
    secondary: Color = Color(0xFFE5E5E5),
    onSecondary: Color = Color.White,
    secondaryVariant: Color = Color(0x14000000),
    onSecondaryVariant: Color = Color(0xE6000000),
    disabledSecondary: Color = Color(0xFFF2F2F2),
    disabledOnSecondary: Color = Color(0x42000000),
    disabledSecondaryVariant: Color = Color(0x14000000),
    disabledOnSecondaryVariant: Color = Color(0x42000000),
    secondaryContainer: Color = Color(0x14000000),
    onSecondaryContainer: Color = Color(0x66000000),
    secondaryContainerVariant: Color = Color(0x14000000),
    onSecondaryContainerVariant: Color = Color(0x66000000),
    tertiaryContainer: Color = Color(0x260066FF),
    onTertiaryContainer: Color = Color(0xFF0066FF),
    tertiaryContainerVariant: Color = Color(0x260066FF),
    background: Color = Color.White,
    onBackground: Color = Color(0xE6000000),
    onBackgroundVariant: Color = Color(0x8A000000),
    surface: Color = Color(0xFFF0F1F2),
    onSurface: Color = Color(0xE6000000),
    surfaceVariant: Color = Color.White,
    onSurfaceSecondary: Color = Color(0x8A000000),
    onSurfaceVariantSummary: Color = Color(0x66000000),
    onSurfaceVariantActions: Color = Color(0x42000000),
    disabledOnSurface: Color = Color(0x42000000),
    surfaceContainer: Color = Color.White,
    onSurfaceContainer: Color = Color(0xE6000000),
    onSurfaceContainerVariant: Color = Color(0x66000000),
    surfaceContainerHigh: Color = Color(0xFFE6E6E6),
    onSurfaceContainerHigh: Color = Color(0x42000000),
    surfaceContainerHighest: Color = Color.White,
    onSurfaceContainerHighest: Color = Color(0xE6000000),
    outline: Color = Color(0x26000000),
    dividerLine: Color = Color(0x1F000000),
    windowDimming: Color = Color.Black.copy(alpha = 0.2F),
    sliderKeyPoint: Color = Color(0xFFF2F2F2),
    sliderKeyPointForeground: Color = Color(0xFF6EB5FF),
    sliderBackground: Color = Color(0x0D000000),
): Colors = Colors(
    primary,
    onPrimary,
    primaryVariant,
    onPrimaryVariant,
    error,
    onError,
    errorContainer,
    onErrorContainer,
    disabledPrimary,
    disabledOnPrimary,
    disabledPrimaryButton,
    disabledOnPrimaryButton,
    disabledPrimarySlider,
    primaryContainer,
    onPrimaryContainer,
    secondary,
    onSecondary,
    secondaryVariant,
    onSecondaryVariant,
    disabledSecondary,
    disabledOnSecondary,
    disabledSecondaryVariant,
    disabledOnSecondaryVariant,
    secondaryContainer,
    onSecondaryContainer,
    secondaryContainerVariant,
    onSecondaryContainerVariant,
    tertiaryContainer,
    onTertiaryContainer,
    tertiaryContainerVariant,
    background,
    onBackground,
    onBackgroundVariant,
    surface,
    onSurface,
    surfaceVariant,
    onSurfaceSecondary,
    onSurfaceVariantSummary,
    onSurfaceVariantActions,
    disabledOnSurface,
    surfaceContainer,
    onSurfaceContainer,
    onSurfaceContainerVariant,
    surfaceContainerHigh,
    onSurfaceContainerHigh,
    surfaceContainerHighest,
    onSurfaceContainerHighest,
    outline,
    dividerLine,
    windowDimming,
    sliderKeyPoint,
    sliderKeyPointForeground,
    sliderBackground,
)

/** COUI dark scheme. COUI: coui_color_blue_dark #247CFF; pure black page surface, translucent white cards. */
fun darkColorScheme(
    primary: Color = Color(0xFF247CFF),
    onPrimary: Color = Color.White,
    primaryVariant: Color = Color(0xFF247CFF),
    onPrimaryVariant: Color = Color(0xFF99C7F1),
    error: Color = Color(0xFFFF6C61),
    onError: Color = Color.White,
    errorContainer: Color = Color(0x40EB493D),
    onErrorContainer: Color = Color(0xFFFF6C61),
    disabledPrimary: Color = Color(0x4D247CFF),
    disabledOnPrimary: Color = Color(0xFF404040),
    disabledPrimaryButton: Color = Color(0x4D247CFF),
    disabledOnPrimaryButton: Color = Color(0x29FFFFFF),
    disabledPrimarySlider: Color = Color(0xFF262626),
    primaryContainer: Color = Color(0xFF5C9DFF),
    onPrimaryContainer: Color = Color.White,
    secondary: Color = Color(0xFF757575),
    onSecondary: Color = Color.White,
    secondaryVariant: Color = Color(0x26FFFFFF),
    onSecondaryVariant: Color = Color(0xE6FFFFFF),
    disabledSecondary: Color = Color(0x4D757575),
    disabledOnSecondary: Color = Color(0x4DFFFFFF),
    disabledSecondaryVariant: Color = Color(0x26FFFFFF),
    disabledOnSecondaryVariant: Color = Color(0x4DFFFFFF),
    secondaryContainer: Color = Color(0x26FFFFFF),
    onSecondaryContainer: Color = Color(0x66FFFFFF),
    secondaryContainerVariant: Color = Color(0x26FFFFFF),
    onSecondaryContainerVariant: Color = Color(0x66FFFFFF),
    tertiaryContainer: Color = Color(0x40247CFF),
    onTertiaryContainer: Color = Color(0xFF5C9DFF),
    tertiaryContainerVariant: Color = Color(0x40247CFF),
    background: Color = Color(0xFF1E1E1E),
    onBackground: Color = Color(0xE6FFFFFF),
    onBackgroundVariant: Color = Color(0x8AFFFFFF),
    surface: Color = Color.Black,
    onSurface: Color = Color(0xE6FFFFFF),
    surfaceVariant: Color = Color(0x1AFFFFFF),
    onSurfaceSecondary: Color = Color(0x8AFFFFFF),
    onSurfaceVariantSummary: Color = Color(0x66FFFFFF),
    onSurfaceVariantActions: Color = Color(0x4DFFFFFF),
    disabledOnSurface: Color = Color(0x4DFFFFFF),
    surfaceContainer: Color = Color(0x1AFFFFFF),
    onSurfaceContainer: Color = Color(0xE6FFFFFF),
    onSurfaceContainerVariant: Color = Color(0x66FFFFFF),
    surfaceContainerHigh: Color = Color(0x33FFFFFF),
    onSurfaceContainerHigh: Color = Color(0x4DFFFFFF),
    surfaceContainerHighest: Color = Color(0xFF1E1E1E),
    onSurfaceContainerHighest: Color = Color(0xE6FFFFFF),
    outline: Color = Color(0x26FFFFFF),
    dividerLine: Color = Color(0x33FFFFFF),
    windowDimming: Color = Color.Black.copy(alpha = 0.6F),
    sliderKeyPoint: Color = Color(0xFF595959),
    sliderKeyPointForeground: Color = Color(0xFF5DAAFF),
    sliderBackground: Color = Color(0x1AFFFFFF),
): Colors = Colors(
    primary,
    onPrimary,
    primaryVariant,
    onPrimaryVariant,
    error,
    onError,
    errorContainer,
    onErrorContainer,
    disabledPrimary,
    disabledOnPrimary,
    disabledPrimaryButton,
    disabledOnPrimaryButton,
    disabledPrimarySlider,
    primaryContainer,
    onPrimaryContainer,
    secondary,
    onSecondary,
    secondaryVariant,
    onSecondaryVariant,
    disabledSecondary,
    disabledOnSecondary,
    disabledSecondaryVariant,
    disabledOnSecondaryVariant,
    secondaryContainer,
    onSecondaryContainer,
    secondaryContainerVariant,
    onSecondaryContainerVariant,
    tertiaryContainer,
    onTertiaryContainer,
    tertiaryContainerVariant,
    background,
    onBackground,
    onBackgroundVariant,
    surface,
    onSurface,
    surfaceVariant,
    onSurfaceSecondary,
    onSurfaceVariantSummary,
    onSurfaceVariantActions,
    disabledOnSurface,
    surfaceContainer,
    onSurfaceContainer,
    onSurfaceContainerVariant,
    surfaceContainerHigh,
    onSurfaceContainerHigh,
    surfaceContainerHighest,
    onSurfaceContainerHighest,
    outline,
    dividerLine,
    windowDimming,
    sliderKeyPoint,
    sliderKeyPointForeground,
    sliderBackground,
)

@Stable
internal fun Colors.updateColorsFrom(other: Colors) {
    primary = other.primary
    onPrimary = other.onPrimary
    primaryVariant = other.primaryVariant
    onPrimaryVariant = other.onPrimaryVariant
    error = other.error
    onError = other.onError
    errorContainer = other.errorContainer
    onErrorContainer = other.onErrorContainer
    disabledPrimary = other.disabledPrimary
    disabledOnPrimary = other.disabledOnPrimary
    disabledPrimaryButton = other.disabledPrimaryButton
    disabledOnPrimaryButton = other.disabledOnPrimaryButton
    disabledPrimarySlider = other.disabledPrimarySlider
    primaryContainer = other.primaryContainer
    onPrimaryContainer = other.onPrimaryContainer
    secondary = other.secondary
    onSecondary = other.onSecondary
    secondaryVariant = other.secondaryVariant
    onSecondaryVariant = other.onSecondaryVariant
    disabledSecondary = other.disabledSecondary
    disabledOnSecondary = other.disabledOnSecondary
    disabledSecondaryVariant = other.disabledSecondaryVariant
    disabledOnSecondaryVariant = other.disabledOnSecondaryVariant
    secondaryContainer = other.secondaryContainer
    onSecondaryContainer = other.onSecondaryContainer
    secondaryContainerVariant = other.secondaryContainerVariant
    onSecondaryContainerVariant = other.onSecondaryContainerVariant
    tertiaryContainer = other.tertiaryContainer
    onTertiaryContainer = other.onTertiaryContainer
    tertiaryContainerVariant = other.tertiaryContainerVariant
    background = other.background
    onBackground = other.onBackground
    onBackgroundVariant = other.onBackgroundVariant
    surface = other.surface
    onSurface = other.onSurface
    surfaceVariant = other.surfaceVariant
    onSurfaceSecondary = other.onSurfaceSecondary
    onSurfaceVariantSummary = other.onSurfaceVariantSummary
    onSurfaceVariantActions = other.onSurfaceVariantActions
    disabledOnSurface = other.disabledOnSurface
    surfaceContainer = other.surfaceContainer
    onSurfaceContainer = other.onSurfaceContainer
    onSurfaceContainerVariant = other.onSurfaceContainerVariant
    surfaceContainerHigh = other.surfaceContainerHigh
    onSurfaceContainerHigh = other.onSurfaceContainerHigh
    surfaceContainerHighest = other.surfaceContainerHighest
    onSurfaceContainerHighest = other.onSurfaceContainerHighest
    outline = other.outline
    dividerLine = other.dividerLine
    windowDimming = other.windowDimming
    sliderKeyPoint = other.sliderKeyPoint
    sliderKeyPointForeground = other.sliderKeyPointForeground
    sliderBackground = other.sliderBackground
}

internal val LocalColors = staticCompositionLocalOf { lightColorScheme() }
