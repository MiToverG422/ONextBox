// Copyright 2025, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.suqi8.coui.kmp.basic

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.then
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.suqi8.coui.kmp.squircle.squircleBackground
import io.github.suqi8.coui.kmp.squircle.squircleBorder
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.LocalContentColor
import kotlin.math.roundToInt

/**
 * Background mode of a [TextField], mirroring COUIEditText couiBackgroundMode.
 *
 * - [Rectangle]: stroke-only rounded rectangle (no fill), 10dp corners; unfocused
 *   stroke 0.33dp couiColorDivider, focused 1dp couiColorPrimary
 *   (COUIEditText MODE_BACKGROUND_RECT, Widget.COUI.EditText.HintAnim.Rectangle).
 * - [Line]: no fill; a 0.33dp bottom underline plus a focused 1dp primary line that
 *   expands from the start edge (COUIEditText MODE_BACKGROUND_LINE — the form used by
 *   ColorOS Settings dialogs and bottom-sheet editors via
 *   Widget.COUI.EditText.HintAnim.Line.HintDisable / Widget.COUI.Input).
 * - [None]: no background decoration at all — bare text (COUIEditText
 *   MODE_BACKGROUND_NONE / MODE_BACKGROUND_NO_LINE, the form used inside white
 *   input cards such as COUICardSingleInputView).
 */
enum class TextFieldMode { Rectangle, Line, None }

/**
 * A [TextField] component with COUI style.
 *
 * @param state The [TextFieldState] to be shown in the text field.
 * @param modifier The modifier to be applied to the [TextField].
 * @param backgroundMode The [TextFieldMode] of the background decoration. Defaults to
 *   [TextFieldMode.Line], the form ColorOS Settings uses for dialog / panel inputs.
 * @param insideMargin The margin inside the [TextField]. Defaults to the
 *   COUI padding of the selected [backgroundMode].
 * @param colors The [TextFieldColors] applied to the [TextField]. Use
 *   [TextFieldDefaults.textFieldColors] to customize.
 * @param cornerRadius The corner radius of the [TextField] (only used by
 *   [TextFieldMode.Rectangle]).
 * @param label The label (hint) to be displayed when the [TextField] is empty.
 * @param useLabelAsPlaceholder Whether the label behaves as a plain placeholder that
 *   disappears once text is entered (COUI default). Set to `false` to enable the COUI
 *   HintAnim floating-label animation.
 * @param justShowFocusLine Only used by [TextFieldMode.Line]: hide the resting
 *   underline and show just the focused expanding line, mirroring
 *   COUIInputPreference couiJustShowFocusLine.
 * @param enabled Whether the [TextField] is enabled.
 * @param readOnly Whether the [TextField] is read-only.
 * @param isError Whether the [TextField] is in error state. Turning this on tints
 *   the border / underline and label with [TextFieldColors.errorColor] and plays a
 *   horizontal shake animation, mirroring COUIErrorEditTextHelper.
 * @param maxCount The maximum number of characters. When not null a "count/max"
 *   counter is shown at the end and input is truncated at [maxCount], mirroring
 *   COUIInputView couiInputMaxCount.
 * @param showClearButton Whether to show a clear (fast delete) button when the
 *   [TextField] is focused and not empty, mirroring COUIEditText quickDelete.
 * @param showPasswordToggle Whether to show an eye toggle that switches the
 *   password visibility, mirroring COUIInputView couiEnablePassword. While the
 *   password is hidden, the text is masked and [outputTransformation] is ignored.
 * @param inputTransformation The input transformation to be applied to the [TextField].
 * @param textStyle The text style to be applied to the [TextField]. Defaults to the
 *   COUI input text of the selected [backgroundMode]: 16sp regular
 *   (couiEditTextTextAppearance), bold in [TextFieldMode.Rectangle]
 *   (Widget.COUI.EditText.HintAnim.Rectangle android:textStyle=bold).
 * @param keyboardOptions The keyboard options to be applied to the [TextField].
 * @param onKeyboardAction The keyboard action handler for the [TextField].
 * @param lineLimits The line limits for the [TextField].
 * @param leadingIcon The leading icon to be displayed in the [TextField].
 * @param trailingIcon The trailing icon to be displayed in the [TextField].
 * @param onTextLayout The callback to be called when the text layout changes.
 * @param interactionSource The interaction source to be applied to the [TextField].
 * @param cursorBrush The brush to be used for the cursor.
 * @param outputTransformation The output transformation for the text field.
 * @param scrollState The scroll state for the text field.
 */
@Composable
fun TextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    backgroundMode: TextFieldMode = TextFieldMode.Line,
    insideMargin: DpSize = TextFieldDefaults.insideMargin(backgroundMode),
    colors: TextFieldColors = TextFieldDefaults.textFieldColors(),
    cornerRadius: Dp = TextFieldDefaults.CornerRadius,
    label: String = "",
    useLabelAsPlaceholder: Boolean = true,
    justShowFocusLine: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    maxCount: Int? = null,
    showClearButton: Boolean = false,
    showPasswordToggle: Boolean = false,
    inputTransformation: InputTransformation? = null,
    textStyle: TextStyle = TextFieldDefaults.textStyle(backgroundMode),
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.Default,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    onTextLayout: (Density.(getResult: () -> TextLayoutResult?) -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    cursorBrush: Brush = SolidColor(colors.borderColor),
    outputTransformation: OutputTransformation? = null,
    scrollState: ScrollState = rememberScrollState(),
) {
    @Suppress("NAME_SHADOWING")
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val labelState by remember(label, useLabelAsPlaceholder, enabled) {
        derivedStateOf {
            resolveLabelState(label, useLabelAsPlaceholder, state.text.isNotEmpty(), isFocused && enabled)
        }
    }

    val currentOnTextLayout by rememberUpdatedState(onTextLayout)

    val contentColor = LocalContentColor.current
    val resolvedTextStyle = remember(textStyle, contentColor, enabled, colors.disabledTextColor) {
        val textColor = if (enabled) textStyle.color.takeOrElse { contentColor } else colors.disabledTextColor
        textStyle.copy(color = textColor)
    }

    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val resolvedOutputTransformation =
        if (showPasswordToggle && !passwordVisible) PasswordMaskOutputTransformation else outputTransformation
    val resolvedInputTransformation = if (maxCount != null) {
        remember(inputTransformation, maxCount) {
            val limiter = InputTransformation {
                // Truncate instead of rejecting, mirroring COUIInputView subSequence(0, maxCount).
                if (length > maxCount) replace(maxCount, length, "")
            }
            inputTransformation?.then(limiter) ?: limiter
        }
    } else {
        inputTransformation
    }

    BasicTextField(
        state = state,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = resolvedTextStyle,
        cursorBrush = cursorBrush,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        lineLimits = lineLimits,
        onTextLayout = currentOnTextLayout,
        interactionSource = interactionSource,
        inputTransformation = resolvedInputTransformation,
        outputTransformation = resolvedOutputTransformation,
        scrollState = scrollState,
        decorator = TextFieldDecorator { innerTextField ->
            val textLength = state.text.length
            TextFieldChrome(
                label = label,
                labelState = labelState,
                labelNormalFontSize = resolvedTextStyle.fontSize.takeIf { it.isSp }?.value ?: DefaultLabelFontSize,
                colors = colors,
                cornerRadius = cornerRadius,
                insideMargin = insideMargin,
                backgroundMode = backgroundMode,
                justShowFocusLine = justShowFocusLine,
                enabled = enabled,
                isError = isError,
                counterText = maxCount?.let { "$textLength/$it" },
                counterOverflow = maxCount != null && textLength >= maxCount,
                clearButtonVisible = showClearButton && enabled && isFocused && textLength > 0,
                onClearText = if (showClearButton) ({ state.clearText() }) else null,
                passwordVisible = if (showPasswordToggle) passwordVisible else null,
                onPasswordToggle = { passwordVisible = !passwordVisible },
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
                isFocused = isFocused,
                innerTextField = innerTextField,
            )
        },
    )
}

/**
 * A [TextField] component with COUI style.
 *
 * @param value The input [TextFieldValue] to be shown in the text field.
 * @param onValueChange The callback that is triggered when the input service updates values in
 *   [TextFieldValue]. An updated [TextFieldValue] comes as a parameter of the callback.
 * @param modifier The modifier to be applied to the [TextField].
 * @param backgroundMode The [TextFieldMode] of the background decoration. Defaults to
 *   [TextFieldMode.Line], the form ColorOS Settings uses for dialog / panel inputs.
 * @param insideMargin The margin inside the [TextField]. Defaults to the
 *   COUI padding of the selected [backgroundMode].
 * @param colors The [TextFieldColors] applied to the [TextField]. Use
 *   [TextFieldDefaults.textFieldColors] to customize.
 * @param cornerRadius The corner radius of the [TextField] (only used by
 *   [TextFieldMode.Rectangle]).
 * @param label The label (hint) to be displayed when the [TextField] is empty.
 * @param useLabelAsPlaceholder Whether the label behaves as a plain placeholder that
 *   disappears once text is entered (COUI default). Set to `false` to enable the COUI
 *   HintAnim floating-label animation.
 * @param justShowFocusLine Only used by [TextFieldMode.Line]: hide the resting
 *   underline and show just the focused expanding line, mirroring
 *   COUIInputPreference couiJustShowFocusLine.
 * @param enabled Whether the [TextField] is enabled.
 * @param readOnly Whether the [TextField] is read-only.
 * @param isError Whether the [TextField] is in error state. Turning this on tints
 *   the border / underline and label with [TextFieldColors.errorColor] and plays a
 *   horizontal shake animation, mirroring COUIErrorEditTextHelper.
 * @param maxCount The maximum number of characters. When not null a "count/max"
 *   counter is shown at the end and input is truncated at [maxCount], mirroring
 *   COUIInputView couiInputMaxCount.
 * @param showClearButton Whether to show a clear (fast delete) button when the
 *   [TextField] is focused and not empty, mirroring COUIEditText quickDelete.
 * @param showPasswordToggle Whether to show an eye toggle that switches the
 *   password visibility, mirroring COUIInputView couiEnablePassword. While the
 *   password is hidden, the text is masked and [visualTransformation] is ignored.
 * @param textStyle The text style to be applied to the [TextField]. Defaults to the
 *   COUI input text of the selected [backgroundMode].
 * @param keyboardOptions The keyboard options to be applied to the [TextField].
 * @param keyboardActions The keyboard actions to be applied to the [TextField].
 * @param leadingIcon The leading icon to be displayed in the [TextField].
 * @param trailingIcon The trailing icon to be displayed in the [TextField].
 * @param singleLine Whether the text field is single line.
 * @param maxLines The maximum number of lines allowed to be displayed in [TextField].
 * @param minLines The minimum number of lines allowed to be displayed in [TextField]. It is required
 *   that 1 <= [minLines] <= [maxLines].
 * @param visualTransformation The visual transformation to be applied to the [TextField].
 * @param onTextLayout The callback to be called when the text layout changes.
 * @param interactionSource The interaction source to be applied to the [TextField].
 * @param cursorBrush The brush to be used for the cursor.
 */
@Composable
fun TextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    backgroundMode: TextFieldMode = TextFieldMode.Line,
    insideMargin: DpSize = TextFieldDefaults.insideMargin(backgroundMode),
    colors: TextFieldColors = TextFieldDefaults.textFieldColors(),
    cornerRadius: Dp = TextFieldDefaults.CornerRadius,
    label: String = "",
    useLabelAsPlaceholder: Boolean = true,
    justShowFocusLine: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    maxCount: Int? = null,
    showClearButton: Boolean = false,
    showPasswordToggle: Boolean = false,
    textStyle: TextStyle = TextFieldDefaults.textStyle(backgroundMode),
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    interactionSource: MutableInteractionSource? = null,
    cursorBrush: Brush = SolidColor(colors.borderColor),
) {
    @Suppress("NAME_SHADOWING")
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val labelState = resolveLabelState(label, useLabelAsPlaceholder, value.text.isNotEmpty(), isFocused && enabled)

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnTextLayout by rememberUpdatedState(onTextLayout)

    val contentColor = LocalContentColor.current
    val resolvedTextStyle = remember(textStyle, contentColor, enabled, colors.disabledTextColor) {
        val textColor = if (enabled) textStyle.color.takeOrElse { contentColor } else colors.disabledTextColor
        textStyle.copy(color = textColor)
    }

    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val resolvedVisualTransformation = if (showPasswordToggle && !passwordVisible) {
        remember { PasswordVisualTransformation() }
    } else {
        visualTransformation
    }
    val handleValueChange: (TextFieldValue) -> Unit = if (maxCount == null) {
        { currentOnValueChange(it) }
    } else {
        remember(maxCount) {
            { new ->
                if (new.text.length > maxCount) {
                    // Truncate instead of rejecting, mirroring COUIInputView subSequence(0, maxCount).
                    currentOnValueChange(
                        TextFieldValue(
                            text = new.text.take(maxCount),
                            selection = TextRange(
                                minOf(new.selection.start, maxCount),
                                minOf(new.selection.end, maxCount),
                            ),
                        ),
                    )
                } else {
                    currentOnValueChange(new)
                }
            }
        }
    }

    BasicTextField(
        value = value,
        onValueChange = handleValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = resolvedTextStyle,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        visualTransformation = resolvedVisualTransformation,
        onTextLayout = currentOnTextLayout,
        interactionSource = interactionSource,
        cursorBrush = cursorBrush,
        decorationBox = @Composable { innerTextField ->
            TextFieldChrome(
                label = label,
                labelState = labelState,
                labelNormalFontSize = resolvedTextStyle.fontSize.takeIf { it.isSp }?.value ?: DefaultLabelFontSize,
                colors = colors,
                cornerRadius = cornerRadius,
                insideMargin = insideMargin,
                backgroundMode = backgroundMode,
                justShowFocusLine = justShowFocusLine,
                enabled = enabled,
                isError = isError,
                counterText = maxCount?.let { "${value.text.length}/$it" },
                counterOverflow = maxCount != null && value.text.length >= maxCount,
                clearButtonVisible = showClearButton && enabled && isFocused && value.text.isNotEmpty(),
                onClearText = if (showClearButton) ({ currentOnValueChange(TextFieldValue("")) }) else null,
                passwordVisible = if (showPasswordToggle) passwordVisible else null,
                onPasswordToggle = { passwordVisible = !passwordVisible },
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
                isFocused = isFocused,
                innerTextField = innerTextField,
            )
        },
    )
}

/**
 * A text field component with COUI style.
 *
 * @param value The text to be displayed in the text field.
 * @param onValueChange The callback to be called when the value changes.
 * @param modifier The modifier to be applied to the [TextField].
 * @param backgroundMode The [TextFieldMode] of the background decoration. Defaults to
 *   [TextFieldMode.Line], the form ColorOS Settings uses for dialog / panel inputs.
 * @param insideMargin The margin inside the [TextField]. Defaults to the
 *   COUI padding of the selected [backgroundMode].
 * @param colors The [TextFieldColors] applied to the [TextField]. Use
 *   [TextFieldDefaults.textFieldColors] to customize.
 * @param cornerRadius The corner radius of the [TextField] (only used by
 *   [TextFieldMode.Rectangle]).
 * @param label The label (hint) to be displayed when the [TextField] is empty.
 * @param useLabelAsPlaceholder Whether the label behaves as a plain placeholder that
 *   disappears once text is entered (COUI default). Set to `false` to enable the COUI
 *   HintAnim floating-label animation.
 * @param justShowFocusLine Only used by [TextFieldMode.Line]: hide the resting
 *   underline and show just the focused expanding line, mirroring
 *   COUIInputPreference couiJustShowFocusLine.
 * @param enabled Whether the [TextField] is enabled.
 * @param readOnly Whether the [TextField] is read-only.
 * @param isError Whether the [TextField] is in error state. Turning this on tints
 *   the border / underline and label with [TextFieldColors.errorColor] and plays a
 *   horizontal shake animation, mirroring COUIErrorEditTextHelper.
 * @param maxCount The maximum number of characters. When not null a "count/max"
 *   counter is shown at the end and input is truncated at [maxCount], mirroring
 *   COUIInputView couiInputMaxCount.
 * @param showClearButton Whether to show a clear (fast delete) button when the
 *   [TextField] is focused and not empty, mirroring COUIEditText quickDelete.
 * @param showPasswordToggle Whether to show an eye toggle that switches the
 *   password visibility, mirroring COUIInputView couiEnablePassword. While the
 *   password is hidden, the text is masked and [visualTransformation] is ignored.
 * @param textStyle The text style to be applied to the [TextField]. Defaults to the
 *   COUI input text of the selected [backgroundMode].
 * @param keyboardOptions The keyboard options to be applied to the [TextField].
 * @param keyboardActions The keyboard actions to be applied to the [TextField].
 * @param leadingIcon The leading icon to be displayed in the [TextField].
 * @param trailingIcon The trailing icon to be displayed in the [TextField].
 * @param singleLine Whether the text field is single line.
 * @param maxLines The maximum number of lines allowed to be displayed in [TextField].
 * @param minLines The minimum number of lines allowed to be displayed in [TextField]. It is required
 *   that 1 <= [minLines] <= [maxLines].
 * @param visualTransformation The visual transformation to be applied to the [TextField].
 * @param onTextLayout The callback to be called when the text layout changes.
 * @param interactionSource The interaction source to be applied to the [TextField].
 * @param cursorBrush The brush to be used for the cursor.
 */
@Composable
fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    backgroundMode: TextFieldMode = TextFieldMode.Line,
    insideMargin: DpSize = TextFieldDefaults.insideMargin(backgroundMode),
    colors: TextFieldColors = TextFieldDefaults.textFieldColors(),
    cornerRadius: Dp = TextFieldDefaults.CornerRadius,
    label: String = "",
    useLabelAsPlaceholder: Boolean = true,
    justShowFocusLine: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    maxCount: Int? = null,
    showClearButton: Boolean = false,
    showPasswordToggle: Boolean = false,
    textStyle: TextStyle = TextFieldDefaults.textStyle(backgroundMode),
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    interactionSource: MutableInteractionSource? = null,
    cursorBrush: Brush = SolidColor(colors.borderColor),
) {
    @Suppress("NAME_SHADOWING")
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val labelState = resolveLabelState(label, useLabelAsPlaceholder, value.isNotEmpty(), isFocused && enabled)

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnTextLayout by rememberUpdatedState(onTextLayout)

    val contentColor = LocalContentColor.current
    val resolvedTextStyle = remember(textStyle, contentColor, enabled, colors.disabledTextColor) {
        val textColor = if (enabled) textStyle.color.takeOrElse { contentColor } else colors.disabledTextColor
        textStyle.copy(color = textColor)
    }

    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val resolvedVisualTransformation = if (showPasswordToggle && !passwordVisible) {
        remember { PasswordVisualTransformation() }
    } else {
        visualTransformation
    }
    val handleValueChange: (String) -> Unit = if (maxCount == null) {
        { currentOnValueChange(it) }
    } else {
        remember(maxCount) {
            { new ->
                // Truncate instead of rejecting, mirroring COUIInputView subSequence(0, maxCount).
                currentOnValueChange(if (new.length > maxCount) new.take(maxCount) else new)
            }
        }
    }

    BasicTextField(
        value = value,
        onValueChange = handleValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = resolvedTextStyle,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        visualTransformation = resolvedVisualTransformation,
        onTextLayout = currentOnTextLayout,
        interactionSource = interactionSource,
        cursorBrush = cursorBrush,
        decorationBox = @Composable { innerTextField ->
            TextFieldChrome(
                label = label,
                labelState = labelState,
                labelNormalFontSize = resolvedTextStyle.fontSize.takeIf { it.isSp }?.value ?: DefaultLabelFontSize,
                colors = colors,
                cornerRadius = cornerRadius,
                insideMargin = insideMargin,
                backgroundMode = backgroundMode,
                justShowFocusLine = justShowFocusLine,
                enabled = enabled,
                isError = isError,
                counterText = maxCount?.let { "${value.length}/$it" },
                counterOverflow = maxCount != null && value.length >= maxCount,
                clearButtonVisible = showClearButton && enabled && isFocused && value.isNotEmpty(),
                onClearText = if (showClearButton) ({ currentOnValueChange("") }) else null,
                passwordVisible = if (showPasswordToggle) passwordVisible else null,
                onPasswordToggle = { passwordVisible = !passwordVisible },
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
                isFocused = isFocused,
                innerTextField = innerTextField,
            )
        },
    )
}

private enum class LabelAnimState { Hidden, Placeholder, Normal, Floating }

/**
 * Resolves the visual state of the label.
 *
 * With [useLabelAsPlaceholder] (the COUI HintDisable default) the label is a plain
 * placeholder: visible while the field is empty, gone once text is entered.
 * Otherwise it mirrors COUIEditText couiHintEnabled: the label collapses (floats)
 * as soon as the field is focused or filled
 * (COUIEditText.updateLabelState: `!isEmpty || (isEnabled && hasFocus())`).
 */
private fun resolveLabelState(
    label: String,
    useLabelAsPlaceholder: Boolean,
    hasText: Boolean,
    isFocused: Boolean,
): LabelAnimState = when {
    label.isEmpty() -> LabelAnimState.Hidden
    useLabelAsPlaceholder -> if (hasText) LabelAnimState.Placeholder else LabelAnimState.Normal
    hasText || isFocused -> LabelAnimState.Floating
    else -> LabelAnimState.Normal
}

/** Contains default values used by [TextField]. */
object TextFieldDefaults {
    /** The default corner radius of the [TextField] (COUI coui_textinput_corner_radius). */
    val CornerRadius = 10.dp

    /**
     * The default inside margin of the [TextField] in [TextFieldMode.Rectangle].
     * From COUI Widget.COUI.EditText.HintAnim.Rectangle: paddingLeft 16dp / rectModePaddingTop 12dp /
     * paddingBottom 12dp (COUI paddingRight is 10dp to leave room for the trailing delete icon;
     * the symmetric [DpSize] API keeps 16dp on both sides).
     */
    val InsideMargin = DpSize(16.dp, 12.dp)

    /**
     * The default inside margin of the [TextField] in [TextFieldMode.Line].
     * From COUI Widget.COUI.EditText.HintAnim.Line.HintDisable (the style ColorOS Settings
     * uses for dialog inputs): horizontal padding 0dp,
     * coui_input_edit_text_no_title_padding_top / _bottom 15dp.
     */
    val LineInsideMargin = DpSize(0.dp, 15.dp)

    /**
     * The default inside margin of the [TextField] in [TextFieldMode.None].
     * From COUI Widget.COUI.EditText base style: vertical padding 9dp.
     */
    val NoneInsideMargin = DpSize(0.dp, 9.dp)

    /** Returns the default inside margin for the given [mode]. */
    fun insideMargin(mode: TextFieldMode): DpSize = when (mode) {
        TextFieldMode.Rectangle -> InsideMargin
        TextFieldMode.Line -> LineInsideMargin
        TextFieldMode.None -> NoneInsideMargin
    }

    /** The character counter font size (COUI coui_input_view input_count: 10sp). */
    val CounterFontSize = 10.sp

    /** The default border width when the [TextField] is focused (COUI coui_textinput_focus_stroke_width). */
    internal val BorderWidth = 1.dp

    /** The default border width when the [TextField] is not focused (COUI coui_textinput_stroke_width). */
    internal val UnfocusedBorderWidth = 0.33.dp

    /**
     * The label font size when the label is floating above the text
     * (COUI collapsedTextSize = coui_textinput_hint_text_size: 10sp).
     */
    internal val LabelFontSizeFloating = 10.dp

    /** Horizontal amplitude of the error shake animation (COUI coui_edit_text_shake_amplitude). */
    internal val ShakeAmplitude = 5.33.dp

    /** Spacing before the trailing counter / buttons (COUI coui_input_button_layout_margin_start). */
    internal val CounterPadding = 8.dp

    /** The circular touch target size of the built-in trailing buttons. */
    internal val TrailingButtonTouchTarget = 36.dp

    /** The visual circle size of the clear button (ic_edit_text_delete: 18dp circle in a 22dp icon). */
    internal val ClearButtonVisualSize = 18.dp

    /** The visual size of the password eye icon (COUI coui_inputview_password_size). */
    internal val PasswordIconSize = 28.dp

    /**
     * The default input text style for the given [mode]: 16sp regular
     * (COUI couiEditTextTextAppearance / couiTextBodyL — couiTextAppearanceHeadline6 16sp
     * with fontFamily reset to sans-serif-regular), bold in [TextFieldMode.Rectangle]
     * (Widget.COUI.EditText.HintAnim.Rectangle sets android:textStyle=bold).
     */
    @Composable
    fun textStyle(mode: TextFieldMode): TextStyle {
        val base = COUITheme.textStyles.body1
        return remember(base, mode) {
            if (mode == TextFieldMode.Rectangle) base.copy(fontWeight = FontWeight.Bold) else base
        }
    }

    /**
     * Default colors used by the [TextField].
     *
     * @param backgroundColor The fill color of the [TextFieldMode.Rectangle] box.
     *   COUI rect mode is stroke-only (GradientDrawable with no solid color), so this
     *   defaults to transparent.
     * @param labelColor The color of the label / placeholder
     *   (COUI android:textColorHint = couiColorLabelSecondary; the collapsed label
     *   couiColorSecondNeutral resolves to the same 54% neutral).
     * @param borderColor The color of the border / focused underline
     *   (COUI couiStrokeColor = couiColorPrimary).
     * @param unfocusedBorderColor The color of the border / underline when the [TextField]
     *   is not focused (COUI couiDefaultStrokeColor = couiColorDivider).
     * @param errorColor The border / underline / label / counter-overflow color in error state
     *   (COUI couiColorError).
     * @param counterColor The color of the character counter (COUI couiColorHintNeutral).
     * @param iconColor The tint of the built-in clear / password buttons (COUI couiColorLabelSecondary).
     * @param disabledTextColor The input text / label color when the [TextField] is disabled
     *   (COUI coui_edit_text_color state_enabled=false -> couiColorDisabledNeutral).
     */
    @Composable
    fun textFieldColors(
        backgroundColor: Color = Color.Transparent,
        labelColor: Color = COUITheme.colorScheme.onSurfaceSecondary,
        borderColor: Color = COUITheme.colorScheme.primary,
        unfocusedBorderColor: Color = COUITheme.colorScheme.dividerLine,
        errorColor: Color = COUITheme.colorScheme.error,
        counterColor: Color = COUITheme.colorScheme.onSurfaceContainerHigh,
        iconColor: Color = COUITheme.colorScheme.onSurfaceSecondary,
        disabledTextColor: Color = COUITheme.colorScheme.disabledOnSurface,
    ): TextFieldColors = remember(
        backgroundColor,
        labelColor,
        borderColor,
        unfocusedBorderColor,
        errorColor,
        counterColor,
        iconColor,
        disabledTextColor,
    ) {
        TextFieldColors(
            backgroundColor = backgroundColor,
            labelColor = labelColor,
            borderColor = borderColor,
            unfocusedBorderColor = unfocusedBorderColor,
            errorColor = errorColor,
            counterColor = counterColor,
            iconColor = iconColor,
            disabledTextColor = disabledTextColor,
        )
    }
}

/**
 * Colors used by a [TextField] in different parts of the component.
 *
 * @param backgroundColor The fill color of the [TextFieldMode.Rectangle] box
 *   (transparent in COUI).
 * @param labelColor The color of the label / placeholder.
 * @param borderColor The color of the border / focused underline.
 * @param unfocusedBorderColor The color of the border / underline when the [TextField] is not focused.
 * @param errorColor The border / underline / label / counter-overflow color in error state.
 * @param counterColor The color of the character counter.
 * @param iconColor The tint of the built-in clear / password buttons.
 * @param disabledTextColor The input text / label color when the [TextField] is disabled.
 */
@Immutable
data class TextFieldColors(
    val backgroundColor: Color,
    val labelColor: Color,
    val borderColor: Color,
    val unfocusedBorderColor: Color,
    val errorColor: Color,
    val counterColor: Color,
    val iconColor: Color,
    val disabledTextColor: Color,
)

/** Fallback label font size (COUI input text: 16sp) when [TextStyle.fontSize] is not in sp. */
private val DefaultLabelFontSize = 16f

/** COUIEaseInterpolator: cubic-bezier(0.33, 0, 0.67, 1). */
private val CouiEaseEasing = CubicBezierEasing(0.33f, 0f, 0.67f, 1f)

/** COUIInEaseInterpolator: cubic-bezier(0, 0, 0.1, 1). */
private val CouiInEaseEasing = CubicBezierEasing(0f, 0f, 0.1f, 1f)

/** COUIMoveEaseInterpolator: cubic-bezier(0.3, 0, 0.1, 1), used by the label collapse. */
private val CouiMoveEaseEasing = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)

/** Duration of the error color blend (COUIErrorEditTextHelper DURATION_HINT_ANIMATOR). */
private val ErrorColorAnimDuration = 217

/** Duration of the line mode focus underline animation (COUIEditText BACKGROUND_ANIMATION_DURATION). */
private val LineAnimDuration = 250

/** Duration of the label collapse animation (COUIEditText LABEL_SCALE_ANIMATION_DURATION). */
private val LabelAnimDuration = 200

/** Duration of the counter color change (COUICardMultiInputView ERROR_COUNT_COLOR_ANIMATOR_TIME). */
private val CounterColorAnimDuration = 250

/** Total duration of the error shake animation (COUIErrorEditTextHelper ShakeInterpolator TOTAL_DURATION). */
private val ShakeDuration = 450

/** Masks every character with a bullet, used while the password toggle hides the text. */
private object PasswordMaskOutputTransformation : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        val masked = "•".repeat(length)
        replace(0, length, masked)
    }
}

/**
 * Builds the shared chrome (background / underline, animated border, label, error shake,
 * counter, clear / password buttons, leading/trailing icons, padding) around the inner text field.
 * Centralizes derived state + animations so the three [TextField] overloads only differ in the
 * [BasicTextField] call.
 */
@Composable
private fun TextFieldChrome(
    label: String,
    labelState: LabelAnimState,
    labelNormalFontSize: Float,
    colors: TextFieldColors,
    cornerRadius: Dp,
    insideMargin: DpSize,
    backgroundMode: TextFieldMode,
    justShowFocusLine: Boolean,
    enabled: Boolean,
    isError: Boolean,
    counterText: String?,
    counterOverflow: Boolean,
    clearButtonVisible: Boolean,
    onClearText: (() -> Unit)?,
    passwordVisible: Boolean?,
    onPasswordToggle: () -> Unit,
    leadingIcon: @Composable (() -> Unit)?,
    trailingIcon: @Composable (() -> Unit)?,
    isFocused: Boolean,
    innerTextField: @Composable () -> Unit,
) {
    // Rectangle mode: stroke width/color (error keeps the focused width, per
    // COUIErrorEditTextHelper which strokes with mStrokeWidthFocused). Disabled fields
    // fall back to the thin resting stroke (COUIEditText updateTextInputBoxState).
    val borderWidthState = animateDpAsState(
        if (enabled && (isFocused || isError)) TextFieldDefaults.BorderWidth else TextFieldDefaults.UnfocusedBorderWidth,
    )
    val borderColorState = animateColorAsState(
        when {
            !enabled -> colors.unfocusedBorderColor
            isError -> colors.errorColor
            isFocused -> colors.borderColor
            else -> colors.unfocusedBorderColor
        },
        animationSpec = tween(ErrorColorAnimDuration, easing = CouiEaseEasing),
    )
    // Line mode: base underline (0.33dp, thickened to the 1dp focused width in error state)
    // plus a focused overlay expanding from the start edge. With justShowFocusLine
    // (COUIInputPreference couiJustShowFocusLine) the resting underline is not drawn.
    val underlineBaseColor = animateColorAsState(
        if (isError && enabled) colors.errorColor else colors.unfocusedBorderColor,
        animationSpec = tween(ErrorColorAnimDuration, easing = CouiEaseEasing),
    )
    val underlineBaseWidth = animateDpAsState(
        if (isError && enabled) TextFieldDefaults.BorderWidth else TextFieldDefaults.UnfocusedBorderWidth,
        animationSpec = tween(ErrorColorAnimDuration, easing = CouiEaseEasing),
    )
    val underlineFocusColor = animateColorAsState(
        if (isError) colors.errorColor else colors.borderColor,
        animationSpec = tween(ErrorColorAnimDuration, easing = CouiEaseEasing),
    )
    val lineFraction = remember { Animatable(if (isFocused) 1f else 0f) }
    val lineAlpha = remember { Animatable(if (isFocused) 1f else 0f) }
    if (backgroundMode == TextFieldMode.Line) {
        LaunchedEffect(isFocused, enabled) {
            if (isFocused && enabled) {
                // COUIEditText animateToShowBackground: alpha snaps to 255, the focused line
                // expands from 0 to full width in 250ms with COUIInEaseInterpolator.
                lineAlpha.snapTo(1f)
                lineFraction.snapTo(0f)
                lineFraction.animateTo(1f, tween(LineAnimDuration, easing = CouiInEaseEasing))
            } else if (!enabled) {
                // COUIEditText updateLineModeBackground: disabled resets mDrawXProgress to 0.
                lineAlpha.snapTo(0f)
                lineFraction.snapTo(0f)
            } else if (lineAlpha.value > 0f) {
                // COUIEditText animateToHideBackground: the focused line fades out in place.
                lineAlpha.animateTo(0f, tween(LineAnimDuration, easing = CouiInEaseEasing))
            }
        }
    }
    // Error shake: COUIErrorEditTextHelper.ShakeInterpolator keyframes
    // offsets {0, -1, 0.5, -0.5, 0} x amplitude at {0, 83, 216, 333, 450}ms,
    // eased between keyframes with COUIEaseInterpolator.
    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(isError) {
        if (isError) {
            shakeOffset.snapTo(0f)
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = ShakeDuration
                    0f at 0 using CouiEaseEasing
                    -1f at 83 using CouiEaseEasing
                    0.5f at 216 using CouiEaseEasing
                    -0.5f at 333 using CouiEaseEasing
                    0f at ShakeDuration
                },
            )
        } else {
            shakeOffset.snapTo(0f)
        }
    }

    val labelColorState = animateColorAsState(
        when {
            !enabled -> colors.disabledTextColor
            isError -> colors.errorColor
            else -> colors.labelColor
        },
        animationSpec = tween(ErrorColorAnimDuration, easing = CouiEaseEasing),
    )
    // Label collapse: 200ms with COUIMoveEaseInterpolator
    // (COUIEditText LABEL_SCALE_ANIMATION_DURATION / mPathInterpolator1).
    val labelAnim = animateDpAsState(
        if (labelState == LabelAnimState.Floating) -insideMargin.height / 2 else 0.dp,
        animationSpec = tween(LabelAnimDuration, easing = CouiMoveEaseEasing),
    )
    val labelFontSize by animateDpAsState(
        when (labelState) {
            LabelAnimState.Floating -> TextFieldDefaults.LabelFontSizeFloating
            else -> labelNormalFontSize.dp
        },
        animationSpec = tween(LabelAnimDuration, easing = CouiMoveEaseEasing),
    )
    // COUI only switches the collapsed label to sans-serif-medium in rect mode
    // (COUIEditText initHintMode: setTypefaces for couiBackgroundMode == 2).
    val labelFontWeight = if (labelState == LabelAnimState.Floating && backgroundMode == TextFieldMode.Rectangle) {
        FontWeight.Medium
    } else {
        FontWeight.Normal
    }

    val backgroundModifier = when (backgroundMode) {
        TextFieldMode.Rectangle ->
            Modifier
                .squircleBackground(color = colors.backgroundColor, cornerRadius = cornerRadius)
                .squircleBorder(
                    width = { borderWidthState.value },
                    color = { borderColorState.value },
                    cornerRadius = cornerRadius,
                )

        TextFieldMode.Line -> Modifier.drawBehind {
            if (!justShowFocusLine || (isError && enabled)) {
                val baseWidth = underlineBaseWidth.value.toPx()
                drawRect(
                    color = underlineBaseColor.value,
                    topLeft = Offset(0f, size.height - baseWidth),
                    size = Size(size.width, baseWidth),
                )
            }
            val alpha = lineAlpha.value
            if (alpha > 0f) {
                val focusWidth = TextFieldDefaults.BorderWidth.toPx()
                drawRect(
                    color = underlineFocusColor.value,
                    topLeft = Offset(0f, size.height - focusWidth),
                    size = Size(size.width * lineFraction.value, focusWidth),
                    alpha = alpha,
                )
            }
        }

        TextFieldMode.None -> Modifier
    }

    val hasBuiltinTrailing = counterText != null || onClearText != null || passwordVisible != null
    val builtinTrailing: (@Composable () -> Unit)? = if (!hasBuiltinTrailing) {
        null
    } else {
        {
            val clusterEndPadding = if (onClearText != null || passwordVisible != null) 0.dp else insideMargin.width
            Row(
                modifier = Modifier.padding(end = clusterEndPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (counterText != null) {
                    // The counter turns to the error color at the limit; COUICardMultiInputView
                    // animates the change over 250ms.
                    val counterColorState = animateColorAsState(
                        if (counterOverflow) colors.errorColor else colors.counterColor,
                        animationSpec = tween(CounterColorAnimDuration),
                    )
                    Text(
                        text = counterText,
                        fontSize = TextFieldDefaults.CounterFontSize,
                        color = counterColorState.value,
                        modifier = Modifier.padding(start = TextFieldDefaults.CounterPadding),
                    )
                }
                if (onClearText != null) {
                    AnimatedVisibility(
                        visible = clearButtonVisible,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        TextFieldClearButton(circleColor = colors.iconColor, onClear = onClearText)
                    }
                }
                if (passwordVisible != null) {
                    TextFieldPasswordToggle(
                        visible = passwordVisible,
                        tint = colors.iconColor,
                        onToggle = onPasswordToggle,
                    )
                }
            }
        }
    }

    val hasLeadingIcon = leadingIcon != null
    val hasTrailingContent = trailingIcon != null || hasBuiltinTrailing
    val paddingModifier = remember(hasLeadingIcon, hasTrailingContent, insideMargin) {
        when {
            !hasLeadingIcon && !hasTrailingContent -> Modifier.padding(insideMargin.width, vertical = insideMargin.height)
            !hasLeadingIcon -> Modifier.padding(start = insideMargin.width).padding(vertical = insideMargin.height)
            !hasTrailingContent -> Modifier.padding(end = insideMargin.width).padding(vertical = insideMargin.height)
            else -> Modifier.padding(vertical = insideMargin.height)
        }
    }

    TextFieldDecorationBox(
        label = label,
        labelFontSize = labelFontSize,
        labelFontWeight = labelFontWeight,
        labelColor = labelColorState.value,
        labelState = labelState,
        backgroundModifier = backgroundModifier,
        paddingModifier = paddingModifier,
        labelAnim = { labelAnim.value },
        shakeOffset = { shakeOffset.value },
        insideMargin = insideMargin,
        leadingIcon = leadingIcon,
        builtinTrailing = builtinTrailing,
        trailingIcon = trailingIcon,
        innerTextField = innerTextField,
    )
}

@Composable
private fun TextFieldDecorationBox(
    label: String,
    labelFontSize: Dp,
    labelFontWeight: FontWeight,
    labelColor: Color,
    labelState: LabelAnimState,
    labelAnim: () -> Dp,
    shakeOffset: () -> Float,
    insideMargin: DpSize,
    leadingIcon: @Composable (() -> Unit)?,
    builtinTrailing: (@Composable () -> Unit)?,
    trailingIcon: @Composable (() -> Unit)?,
    backgroundModifier: Modifier = Modifier,
    paddingModifier: Modifier = Modifier,
    innerTextField: @Composable () -> Unit,
) {
    Box(
        modifier = backgroundModifier,
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingIcon?.invoke()
            Box(
                modifier = Modifier.weight(1f).then(paddingModifier),
                contentAlignment = Alignment.TopStart,
            ) {
                if (labelState != LabelAnimState.Hidden && labelState != LabelAnimState.Placeholder) {
                    Text(
                        text = label,
                        fontSize = labelFontSize.value.sp,
                        color = labelColor,
                        fontWeight = labelFontWeight,
                        modifier = Modifier.offset { IntOffset(0, labelAnim().roundToPx()) },
                        textAlign = TextAlign.Start,
                    )
                }
                Box(
                    modifier = Modifier
                        .offset(y = if (labelState == LabelAnimState.Floating) insideMargin.height / 2 else 0.dp)
                        .offset {
                            // COUIErrorEditTextHelper only shakes the text canvas, not the box.
                            IntOffset((shakeOffset() * TextFieldDefaults.ShakeAmplitude.toPx()).roundToInt(), 0)
                        },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    innerTextField()
                }
            }
            builtinTrailing?.invoke()
            trailingIcon?.invoke()
        }
    }
}

/**
 * Clear (fast delete) button: a 36dp circular touch target holding an 18dp filled circle
 * with a cross knocked out of it (ic_edit_text_delete: 22dp vector, 9dp-radius circle
 * tinted couiColorLabelSecondary; the cross is a cutout in COUI, drawn white here which
 * is equivalent on card / panel surfaces).
 */
@Composable
private fun TextFieldClearButton(
    circleColor: Color,
    onClear: () -> Unit,
) {
    val currentOnClear by rememberUpdatedState(onClear)
    Box(
        modifier = Modifier
            .size(TextFieldDefaults.TrailingButtonTouchTarget)
            .clip(CircleShape)
            .clickable { currentOnClear() }
            .semantics { contentDescription = "Clear text" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(TextFieldDefaults.ClearButtonVisualSize)) {
            drawCircle(color = circleColor)
            val half = 4.1.dp.toPx()
            val stroke = 1.6.dp.toPx()
            drawLine(
                color = Color.White,
                start = Offset(center.x - half, center.y - half),
                end = Offset(center.x + half, center.y + half),
                strokeWidth = stroke,
            )
            drawLine(
                color = Color.White,
                start = Offset(center.x - half, center.y + half),
                end = Offset(center.x + half, center.y - half),
                strokeWidth = stroke,
            )
        }
    }
}

/**
 * Password visibility toggle: a 36dp circular touch target holding a 28dp eye icon
 * (COUI coui_inputview_password_size) redrawn from the coui_btn_eye_on / coui_btn_eye_off
 * 34dp vectors, tinted couiColorLabelSecondary.
 */
@Composable
private fun TextFieldPasswordToggle(
    visible: Boolean,
    tint: Color,
    onToggle: () -> Unit,
) {
    val currentOnToggle by rememberUpdatedState(onToggle)
    val description = if (visible) "Hide password" else "Show password"
    Box(
        modifier = Modifier
            .size(TextFieldDefaults.TrailingButtonTouchTarget)
            .clip(CircleShape)
            .clickable { currentOnToggle() }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(TextFieldDefaults.PasswordIconSize)) {
            // Geometry from the 34dp coui_btn_eye_on vector: almond eye from (6,17) to (28,17)
            // with cubic bulges to y 27.716 / 6.286, pupil ring r 5.02 and dot r 3.016 at (17,17).
            val s = size.width / 34f
            val eyeCenter = Offset(17f * s, 17f * s)
            val eye = Path().apply {
                fillType = PathFillType.EvenOdd
                moveTo(6f * s, 17f * s)
                cubicTo(12f * s, 27.716f * s, 22f * s, 27.716f * s, 28f * s, 17f * s)
                cubicTo(22f * s, 6.286f * s, 12f * s, 6.286f * s, 6f * s, 17f * s)
                close()
                addOval(Rect(center = eyeCenter, radius = 5.02f * s))
                addOval(Rect(center = eyeCenter, radius = 3.016f * s))
            }
            if (visible) {
                drawPath(eye, color = tint)
            } else {
                // coui_btn_eye_off: the same eye cut along a 45-degree slash from (6.7,6.7)
                // to (27.2,27.3) with a small gap, plus the 2dp rounded slash stroke.
                val slashStart = Offset(6.7f * s, 6.73f * s)
                val slashEnd = Offset(27.24f * s, 27.27f * s)
                val normal = Offset(-0.7071f, 0.7071f)
                val halfBand = 2.8f * s
                val band = Path().apply {
                    moveTo(slashStart.x + normal.x * halfBand, slashStart.y + normal.y * halfBand)
                    lineTo(slashEnd.x + normal.x * halfBand, slashEnd.y + normal.y * halfBand)
                    lineTo(slashEnd.x - normal.x * halfBand, slashEnd.y - normal.y * halfBand)
                    lineTo(slashStart.x - normal.x * halfBand, slashStart.y - normal.y * halfBand)
                    close()
                }
                clipPath(band, clipOp = ClipOp.Difference) {
                    drawPath(eye, color = tint)
                }
                drawLine(
                    color = tint,
                    start = slashStart,
                    end = slashEnd,
                    strokeWidth = 2f * s,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
