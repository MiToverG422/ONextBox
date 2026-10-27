// Copyright 2026, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.suqi8.coui.kmp.basic

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.suqi8.coui.kmp.squircle.squircleBackground
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * An [InputView] component with COUI style — the ColorOS Settings "card input".
 *
 * Mirrors COUICardSingleInputView / COUICardMultiInputView (coui_single_input_card_view.xml /
 * coui_multi_input_card_view.xml): a white card (couiColorCardBackground, couiRoundCornerM 12dp
 * corners) holding an optional 16sp medium title and a bare, undecorated text field
 * (COUIEditText MODE_BACKGROUND_NO_LINE — no underline, no border), with the trailing
 * counter / clear / password buttons overlaid at the end of the field. The error caption sits
 * *below* the card (10sp couiColorError, 4dp top padding), fades in over 217ms / out over 283ms
 * with the COUI ease curve, and the field plays the COUI error shake while it is shown.
 *
 * In the multi-line form ([singleLine] = false, mirroring COUICardMultiInputView) the counter
 * moves to the bottom-end corner of the card (12sp couiColorLabelTertiary) and its color
 * animates to the error color for 1s when the limit is hit.
 *
 * When [maxCount] is greater than 0 the input is hard-limited to that many characters.
 *
 * @param value The text to be displayed in the input field.
 * @param onValueChange The callback to be called when the value changes.
 * @param modifier The modifier to be applied to the [InputView].
 * @param title The title displayed above the input field, inside the card. Hidden when empty.
 * @param label The label (hint) of the inner [TextField], shown while the field is empty.
 * @param enabled Whether the [InputView] is enabled.
 * @param readOnly Whether the input field is read-only.
 * @param showCount Whether to show the "length/max" counter. Only shown when [maxCount] is
 *   greater than 0 (COUIInputView couiEnableInputCount).
 * @param maxCount The maximum number of characters. 0 means unlimited.
 * @param showClearButton Whether to show a clear button while the field is focused and not
 *   empty (COUIEditText quickDelete).
 * @param showPasswordToggle Whether to show the password eye toggle (COUIInputView
 *   couiEnablePassword). While the password is hidden the text is masked.
 * @param errorMessage The error message displayed below the card. Empty hides the error line.
 * @param cornerRadius The corner radius of the card (COUI couiRoundCornerM).
 * @param colors The [InputViewColors] applied to the card, title, counter and error line. Use
 *   [InputViewDefaults.inputViewColors] to customize.
 * @param textFieldColors The [TextFieldColors] applied to the inner [TextField].
 * @param textStyle The text style to be applied to the input text (COUI couiTextBodyL:
 *   16sp regular).
 * @param keyboardOptions The keyboard options to be applied to the input field.
 * @param keyboardActions The keyboard actions to be applied to the input field.
 * @param singleLine Whether the input field is single line (COUICardSingleInputView). Set to
 *   `false` for the multi-line card form (COUICardMultiInputView).
 * @param maxLines The maximum number of lines allowed to be displayed (COUIInputView MAX_LINE).
 * @param visualTransformation The visual transformation to be applied to the input field.
 * @param leadingIcon The leading icon to be displayed in the input field.
 * @param trailingIcon The trailing icon to be displayed after the built-in trailing buttons.
 * @param interactionSource The interaction source to be applied to the input field.
 */
@Composable
fun InputView(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "",
    label: String = "",
    enabled: Boolean = true,
    readOnly: Boolean = false,
    showCount: Boolean = false,
    maxCount: Int = 0,
    showClearButton: Boolean = false,
    showPasswordToggle: Boolean = false,
    errorMessage: String = "",
    cornerRadius: Dp = InputViewDefaults.CornerRadius,
    colors: InputViewColors = InputViewDefaults.inputViewColors(),
    textFieldColors: TextFieldColors = TextFieldDefaults.textFieldColors(),
    textStyle: TextStyle = TextFieldDefaults.textStyle(TextFieldMode.None),
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else InputViewDefaults.MaxLines,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val handleValueChange = remember(maxCount) {
        { new: String -> currentOnValueChange(if (maxCount > 0) new.take(maxCount) else new) }
    }

    val isError = errorMessage.isNotEmpty()
    val countVisible = showCount && maxCount > 0

    // Keep the last non-empty message so the fade-out still shows it, like COUIInputView's
    // error TextView which is only hidden after the disappear animation ends.
    var displayedError by remember { mutableStateOf(errorMessage) }
    if (isError && displayedError != errorMessage) displayedError = errorMessage

    val hasTitle = title.isNotEmpty()
    // COUICardSingleInputView edit paddings: with title top 0
    // (coui_input_edit_text_has_title_padding_top) / bottom 12
    // (coui_input_preference_single_title_padding_bottom); without title 15/15
    // (coui_input_edit_text_no_title_padding_top / _bottom). The multi card uses 13/13
    // (coui_card_multi_edittext_padding).
    val fieldTopPadding = when {
        !singleLine -> InputViewDefaults.MultiFieldPadding
        hasTitle -> 0.dp
        else -> InputViewDefaults.FieldPaddingVertical
    }
    val fieldBottomPadding = when {
        !singleLine -> if (countVisible) InputViewDefaults.MultiCountSpacing else InputViewDefaults.MultiFieldPadding
        hasTitle -> InputViewDefaults.FieldPaddingBottomWithTitle
        else -> InputViewDefaults.FieldPaddingVertical
    }

    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .squircleBackground(color = colors.cardColor, cornerRadius = cornerRadius)
                .padding(horizontal = InputViewDefaults.ContentPadding),
        ) {
            if (hasTitle) {
                Text(
                    text = title,
                    style = InputViewDefaults.titleTextStyle(),
                    color = colors.titleColor(enabled),
                    modifier = Modifier
                        .padding(top = InputViewDefaults.TitlePaddingTop, bottom = InputViewDefaults.TitlePaddingBottom)
                        .defaultMinSize(minHeight = InputViewDefaults.TitleMinHeight),
                )
            }
            TextField(
                value = value,
                onValueChange = handleValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = fieldTopPadding, bottom = fieldBottomPadding),
                backgroundMode = TextFieldMode.None,
                insideMargin = DpSize(0.dp, 0.dp),
                colors = textFieldColors,
                label = label,
                enabled = enabled,
                readOnly = readOnly,
                isError = isError,
                maxCount = if (countVisible && singleLine) maxCount else null,
                showClearButton = showClearButton,
                showPasswordToggle = showPasswordToggle,
                textStyle = textStyle,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
                singleLine = singleLine,
                maxLines = maxLines,
                visualTransformation = visualTransformation,
                interactionSource = interactionSource,
            )
            if (countVisible && !singleLine) {
                MultiInputViewCount(
                    length = value.length.coerceAtMost(maxCount),
                    maxCount = maxCount,
                    atLimit = value.length >= maxCount,
                    colors = colors,
                )
            }
        }
        AnimatedVisibility(
            visible = isError,
            enter = fadeIn(tween(durationMillis = ErrorAppearDuration, easing = CouiEase)),
            exit = fadeOut(tween(durationMillis = ErrorDisappearDuration, easing = CouiEase)),
        ) {
            Text(
                text = displayedError,
                fontSize = InputViewDefaults.ErrorFontSize,
                color = colors.errorColor,
                modifier = Modifier.padding(
                    top = InputViewDefaults.ErrorPaddingTop,
                    start = InputViewDefaults.ContentPadding,
                    end = InputViewDefaults.ContentPadding,
                ),
            )
        }
    }
}

/**
 * The bottom-end "length/max" counter of the multi-line card form: 12sp couiColorLabelTertiary
 * (coui_multi_input_card_view.xml input_count, couiTextBodyXS), with a 12dp bottom margin
 * (coui_input_edit_count_margin_bottom). The color animates to the error color over 250ms
 * when the limit is reached (COUICardMultiInputView ERROR_COUNT_COLOR_ANIMATOR_TIME).
 */
@Composable
private fun MultiInputViewCount(
    length: Int,
    maxCount: Int,
    atLimit: Boolean,
    colors: InputViewColors,
) {
    val countColor by animateColorAsState(
        targetValue = if (atLimit) colors.errorColor else colors.countColor,
        animationSpec = tween(durationMillis = CountColorAnimDuration),
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = InputViewDefaults.MultiCountMarginBottom),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(
            text = "$length/$maxCount",
            fontSize = InputViewDefaults.MultiCountFontSize,
            color = countColor,
        )
    }
}

/** Contains default values used by [InputView]. */
object InputViewDefaults {
    /** The corner radius of the card (COUI couiRoundCornerM = coui_round_corner_m). */
    val CornerRadius = 12.dp

    /**
     * The horizontal content padding inside the card (COUI coui_single_input_card_view.xml
     * paddingStart/End 32dp minus the 16dp coui_preference_input_margin_horizontal card inset).
     */
    val ContentPadding = 16.dp

    /** The top padding of the title (COUI coui_input_preference_single_title_padding_top). */
    val TitlePaddingTop = 12.dp

    /** The bottom padding of the title (COUI coui_input_preference_title_padding_bottom). */
    val TitlePaddingBottom = 4.dp

    /** The minimum height of the title (COUI coui_inputView_title_minheight). */
    val TitleMinHeight = 22.dp

    /**
     * The vertical padding of the field without a title
     * (COUI coui_input_edit_text_no_title_padding_top / _bottom).
     */
    val FieldPaddingVertical = 15.dp

    /**
     * The bottom padding of the field when a title is shown
     * (COUI coui_input_preference_single_title_padding_bottom; top padding is 0,
     * coui_input_edit_text_has_title_padding_top).
     */
    val FieldPaddingBottomWithTitle = 12.dp

    /** The vertical padding of the multi-line field (COUI coui_card_multi_edittext_padding). */
    val MultiFieldPadding = 13.dp

    /** The gap between the multi-line field and its counter. */
    val MultiCountSpacing = 4.dp

    /** The bottom margin of the multi-line counter (COUI coui_input_edit_count_margin_bottom). */
    val MultiCountMarginBottom = 12.dp

    /** The font size of the multi-line counter (COUI couiTextBodyXS: 12sp). */
    val MultiCountFontSize: TextUnit = 12.sp

    /** The font size of the single-line inline counter (COUI coui_input_view.xml input_count: 10sp). */
    val CountFontSize: TextUnit = 10.sp

    /** The font size of the error line (COUI couiTextCaption: 10sp). */
    val ErrorFontSize: TextUnit = 10.sp

    /** The top padding of the error line (COUI coui_card_single_input_padding_top). */
    val ErrorPaddingTop = 4.dp

    /** The default maximum number of lines (COUIInputView / COUICardMultiInputView MAX_LINE). */
    val MaxLines: Int = 5

    /**
     * The default text style of the title
     * (COUI COUIInputTitleStyle / couiTextAppearanceHeadline6: 16sp, sans-serif-medium).
     */
    @Composable
    fun titleTextStyle(): TextStyle = COUITheme.textStyles.headline2.copy(fontWeight = FontWeight.Medium)

    /**
     * Default colors used by the [InputView].
     *
     * @param cardColor The background color of the card (COUI couiColorCardBackground).
     * @param titleColor The color of the title (COUI coui_input_title_color -> couiColorPrimaryNeutral).
     * @param disabledTitleColor The color of the title when disabled (COUI coui_input_title_color -> couiColorLabelTertiary).
     * @param countColor The color of the counter (COUI couiColorHintNeutral / couiColorLabelTertiary).
     * @param errorColor The color of the error line and the at-limit counter (COUI couiColorError).
     */
    @Composable
    fun inputViewColors(
        cardColor: Color = COUITheme.colorScheme.surfaceContainer,
        titleColor: Color = COUITheme.colorScheme.onSurface,
        disabledTitleColor: Color = COUITheme.colorScheme.disabledOnSurface,
        countColor: Color = COUITheme.colorScheme.onSurfaceVariantActions,
        errorColor: Color = COUITheme.colorScheme.error,
    ): InputViewColors = remember(cardColor, titleColor, disabledTitleColor, countColor, errorColor) {
        InputViewColors(
            cardColor = cardColor,
            titleColor = titleColor,
            disabledTitleColor = disabledTitleColor,
            countColor = countColor,
            errorColor = errorColor,
        )
    }
}

/**
 * Colors used by an [InputView] in different parts of the component.
 *
 * @param cardColor The background color of the card.
 * @param titleColor The color of the title.
 * @param disabledTitleColor The color of the title when the component is disabled.
 * @param countColor The color of the character counter.
 * @param errorColor The color of the error line and the at-limit counter.
 */
@Immutable
data class InputViewColors(
    val cardColor: Color,
    val titleColor: Color,
    val disabledTitleColor: Color,
    val countColor: Color,
    val errorColor: Color,
) {
    @Stable
    internal fun titleColor(enabled: Boolean): Color = if (enabled) titleColor else disabledTitleColor
}

/** COUIEaseInterpolator (0.33, 0, 0.67, 1) used by the error line fade. */
private val CouiEase = CubicBezierEasing(0.33f, 0f, 0.67f, 1f)

/** COUIInputView APPEAR_DURATION. */
private val ErrorAppearDuration = 217

/** COUIInputView DISAPPEAR_DURATION. */
private val ErrorDisappearDuration = 283

/** COUICardMultiInputView ERROR_COUNT_COLOR_ANIMATOR_TIME. */
private val CountColorAnimDuration = 250
