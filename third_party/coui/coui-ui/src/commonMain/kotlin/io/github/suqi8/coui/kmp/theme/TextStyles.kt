// Copyright 2025, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.suqi8.coui.kmp.theme

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The default text styles for the COUI components.
 *
 * @param main The main text style.
 * @param paragraph The paragraph text style.
 * @param body1 The body1 text style.
 * @param body2 The body2 text style.
 * @param button The button text style.
 * @param footnote1 The footnote1 text style.
 * @param footnote2 The footnote2 text style.
 * @param headline1 The headline1 text style.
 * @param headline2 The headline2 text style.
 * @param subtitle The subtitle text style.
 * @param title1 The title1 text style.
 * @param title2 The title2 text style.
 * @param title3 The title3 text style.
 * @param title4 The title4 text style.
 */
@Stable
class TextStyles(
    main: TextStyle,
    paragraph: TextStyle,
    body1: TextStyle,
    body2: TextStyle,
    button: TextStyle,
    footnote1: TextStyle,
    footnote2: TextStyle,
    headline1: TextStyle,
    headline2: TextStyle,
    subtitle: TextStyle,
    title1: TextStyle,
    title2: TextStyle,
    title3: TextStyle,
    title4: TextStyle,
) {
    var main by mutableStateOf(main, structuralEqualityPolicy())
        internal set
    var paragraph by mutableStateOf(paragraph, structuralEqualityPolicy())
        internal set
    var body1 by mutableStateOf(body1, structuralEqualityPolicy())
        internal set
    var body2 by mutableStateOf(body2, structuralEqualityPolicy())
        internal set
    var button by mutableStateOf(button, structuralEqualityPolicy())
        internal set
    var footnote1 by mutableStateOf(footnote1, structuralEqualityPolicy())
        internal set
    var footnote2 by mutableStateOf(footnote2, structuralEqualityPolicy())
        internal set
    var headline1 by mutableStateOf(headline1, structuralEqualityPolicy())
        internal set
    var headline2 by mutableStateOf(headline2, structuralEqualityPolicy())
        internal set
    var subtitle by mutableStateOf(subtitle, structuralEqualityPolicy())
        internal set
    var title1 by mutableStateOf(title1, structuralEqualityPolicy())
        internal set
    var title2 by mutableStateOf(title2, structuralEqualityPolicy())
        internal set
    var title3 by mutableStateOf(title3, structuralEqualityPolicy())
        internal set
    var title4 by mutableStateOf(title4, structuralEqualityPolicy())
        internal set

    fun copy(
        main: TextStyle = this.main,
        paragraph: TextStyle = this.paragraph,
        body1: TextStyle = this.body1,
        body2: TextStyle = this.body2,
        button: TextStyle = this.button,
        footnote1: TextStyle = this.footnote1,
        footnote2: TextStyle = this.footnote2,
        headline1: TextStyle = this.headline1,
        headline2: TextStyle = this.headline2,
        subtitle: TextStyle = this.subtitle,
        title1: TextStyle = this.title1,
        title2: TextStyle = this.title2,
        title3: TextStyle = this.title3,
        title4: TextStyle = this.title4,
    ): TextStyles = TextStyles(
        main,
        paragraph,
        body1,
        body2,
        button,
        footnote1,
        footnote2,
        headline1,
        headline2,
        subtitle,
        title1,
        title2,
        title3,
        title4,
    )
}

fun defaultTextStyles(
    main: TextStyle = Main,
    paragraph: TextStyle = Paragraph,
    body1: TextStyle = Body1,
    body2: TextStyle = Body2,
    button: TextStyle = Button,
    footnote1: TextStyle = Footnote1,
    footnote2: TextStyle = Footnote2,
    headline1: TextStyle = Headline1,
    headline2: TextStyle = Headline2,
    subtitle: TextStyle = Subtitle,
    title1: TextStyle = Title1,
    title2: TextStyle = Title2,
    title3: TextStyle = Title3,
    title4: TextStyle = Title4,
): TextStyles = TextStyles(
    main,
    paragraph,
    body1,
    body2,
    button,
    footnote1,
    footnote2,
    headline1,
    headline2,
    subtitle,
    title1,
    title2,
    title3,
    title4,
)

/** COUI couiTextAppearanceHeadline6 (coui_spacing_multiplier_headline_xs), the closest tier to 17sp. */
private val Main: TextStyle
    get() =
        TextStyle(
            fontSize = 17.sp,
            lineHeight = 1.158f.em,
            lineHeightStyle = COUILineHeightStyle,
        )

/** COUI couiTextAppearanceBodyL (coui_spacing_multiplier_body_l), the closest tier to 17sp. */
private val Paragraph: TextStyle
    get() =
        TextStyle(
            fontSize = 17.sp,
            lineHeight = 1.158f.em,
            lineHeightStyle = COUILineHeightStyle,
        )

/** COUI couiTextAppearanceBodyL 16sp (coui_spacing_multiplier_body_l). */
private val Body1: TextStyle
    get() =
        TextStyle(
            fontSize = 16.sp,
            lineHeight = 1.158f.em,
            lineHeightStyle = COUILineHeightStyle,
        )

/** COUI couiTextAppearanceBody 14sp (coui_spacing_multiplier_body_m). */
private val Body2: TextStyle
    get() =
        TextStyle(
            fontSize = 14.sp,
            lineHeight = 1.2245f.em,
            lineHeightStyle = COUILineHeightStyle,
        )

/** COUI couiTextAppearanceButtonL 16sp, sans-serif-medium (coui_spacing_multiplier_button_l). */
private val Button: TextStyle
    get() =
        TextStyle(
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 1.263f.em,
            lineHeightStyle = COUILineHeightStyle,
        )

/** COUI couiTextAppearanceBody (coui_spacing_multiplier_body_m); COUI has no 13sp tier. */
private val Footnote1: TextStyle
    get() =
        TextStyle(
            fontSize = 13.sp,
            lineHeight = 1.2245f.em,
            lineHeightStyle = COUILineHeightStyle,
        )

/** COUI couiTextAppearanceDescription 12sp (coui_spacing_multiplier_body_xs); COUI has no 11sp tier. */
private val Footnote2: TextStyle
    get() =
        TextStyle(
            fontSize = 11.sp,
            lineHeight = 1.143f.em,
            lineHeightStyle = COUILineHeightStyle,
        )

/**
 * COUI couiTextAppearanceHeadline6 16sp (coui_spacing_multiplier_headline_xs), the appearance behind
 * TextAppearance.COUI.List.Title. Confirmed on device: a ColorOS 16 preference title node measures
 * 21.71dp at 560 dpi, which is 16sp rather than the 17sp inherited from Miuix.
 */
private val Headline1: TextStyle
    get() =
        TextStyle(
            fontSize = 16.sp,
            lineHeight = 1.158f.em,
            lineHeightStyle = COUILineHeightStyle,
        )

/** COUI couiTextAppearanceHeadline6 16sp (coui_spacing_multiplier_headline_xs). */
private val Headline2: TextStyle
    get() =
        TextStyle(
            fontSize = 16.sp,
            lineHeight = 1.158f.em,
            lineHeightStyle = COUILineHeightStyle,
        )

/** COUI couiTextAppearanceBody 14sp (coui_spacing_multiplier_body_m). */
private val Subtitle: TextStyle
    get() =
        TextStyle(
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 1.2245f.em,
            lineHeightStyle = COUILineHeightStyle,
        )

/** COUI couiTextAppearanceHeadline1 32sp (coui_spacing_multiplier_display_s). */
private val Title1: TextStyle
    get() =
        TextStyle(
            fontSize = 32.sp,
            lineHeight = 1.2322f.em,
            lineHeightStyle = COUILineHeightStyle,
        )

/** COUI couiTextAppearanceHeadline3 24sp (coui_spacing_multiplier_display_xs). */
private val Title2: TextStyle
    get() =
        TextStyle(
            fontSize = 24.sp,
            lineHeight = 1.2f.em,
            lineHeightStyle = COUILineHeightStyle,
        )

/** COUI couiTextAppearanceHeadline4 20sp (coui_spacing_multiplier_headline_m). */
private val Title3: TextStyle
    get() =
        TextStyle(
            fontSize = 20.sp,
            lineHeight = 1.1831f.em,
            lineHeightStyle = COUILineHeightStyle,
        )

/** COUI couiTextAppearanceHeadline5 18sp (coui_spacing_multiplier_headline_s). */
private val Title4: TextStyle
    get() =
        TextStyle(
            fontSize = 18.sp,
            lineHeight = 1.2381f.em,
            lineHeightStyle = COUILineHeightStyle,
        )

/**
 * Distributes the extra leading the way Android's lineSpacingMultiplier does: below the line, never
 * trimmed. Compose's default centres and trims it, which would cancel the multipliers out on
 * single-line text.
 */
private val COUILineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Top,
    trim = LineHeightStyle.Trim.None,
)

@Stable
internal fun TextStyles.updateTextStylesFrom(other: TextStyles) {
    main = other.main
    paragraph = other.paragraph
    body1 = other.body1
    body2 = other.body2
    button = other.button
    footnote1 = other.footnote1
    footnote2 = other.footnote2
    headline1 = other.headline1
    headline2 = other.headline2
    subtitle = other.subtitle
    title1 = other.title1
    title2 = other.title2
    title3 = other.title3
    title4 = other.title4
}

internal val LocalTextStyles = staticCompositionLocalOf { defaultTextStyles() }
