// Copyright 2026, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.suqi8.coui.kmp.basic

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.suqi8.coui.kmp.squircle.squircleBackground
import io.github.suqi8.coui.kmp.squircle.squircleBorder
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A [CodeTextField] component with COUI style.
 *
 * A verification-code input that splits the code into [cellCount] cells, mirroring
 * COUICodeInputView: 42x46dp card cells with an 8dp corner radius, a 1.6dp primary-colored
 * stroke on the active cell, digits popping in with a 0.6->1.0 scale + fade (100ms, COUI move
 * ease) and fading out with a 33ms delay. A transparent text field overlays the cells (like
 * COUI's transparent EditText), so tapping anywhere focuses the component and typing fills the
 * cells from left to right. A multi-character insertion (paste) replaces the whole code and is
 * distributed into the cells. A blinking cursor is shown in the active empty cell while focused.
 *
 * On narrow layouts the cells shrink proportionally below the 360dp reference width and the gap
 * between cells adapts within COUI's min/max bounds.
 *
 * @param value The code to be displayed in the cells. Characters beyond [cellCount] are ignored.
 * @param onValueChange The callback to be called when the code changes. Whitespace is stripped
 *   and the code is truncated to [cellCount] before this is called.
 * @param modifier The modifier to be applied to the [CodeTextField].
 * @param cellCount The number of code cells.
 * @param enabled Whether the [CodeTextField] is enabled.
 * @param security Whether to show a dot instead of the digit in filled cells (COUI
 *   couiEnableSecurityInput).
 * @param cellSize The size of one cell before adaptive scaling.
 * @param cellCornerRadius The corner radius of one cell.
 * @param colors The [CodeTextFieldColors] applied to the [CodeTextField]. Use
 *   [CodeTextFieldDefaults.codeTextFieldColors] to customize.
 * @param textStyle The text style of the cell digits.
 * @param keyboardOptions The keyboard options to be applied to the hidden input field.
 * @param onComplete The callback to be called with the full code once all cells are filled
 *   (COUI OnInputListener.onSuccess).
 * @param interactionSource The interaction source to be applied to the hidden input field.
 */
@Composable
fun CodeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    cellCount: Int = CodeTextFieldDefaults.CellCount,
    enabled: Boolean = true,
    security: Boolean = false,
    cellSize: DpSize = CodeTextFieldDefaults.CellSize,
    cellCornerRadius: Dp = CodeTextFieldDefaults.CellCornerRadius,
    colors: CodeTextFieldColors = CodeTextFieldDefaults.codeTextFieldColors(),
    textStyle: TextStyle = CodeTextFieldDefaults.textStyle(),
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    onComplete: ((String) -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    @Suppress("NAME_SHADOWING")
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnComplete by rememberUpdatedState(onComplete)
    val code = value.take(cellCount)

    // The hidden field is fully controlled: its buffer always holds the sanitized code with the
    // selection at the end, so backspace removes the last cell like COUICodeInputView.
    var fieldValue by remember { mutableStateOf(TextFieldValue(code, TextRange(code.length))) }
    if (fieldValue.text != code) {
        fieldValue = TextFieldValue(code, TextRange(code.length))
    }

    var cursorVisible by remember { mutableStateOf(false) }
    LaunchedEffect(isFocused, enabled, code) {
        if (isFocused && enabled) {
            cursorVisible = true
            while (true) {
                delay(CursorBlinkHalfPeriodMillis)
                cursorVisible = false
                delay(CursorBlinkHalfPeriodMillis)
                cursorVisible = true
            }
        } else {
            cursorVisible = false
        }
    }

    BasicTextField(
        value = fieldValue,
        onValueChange = { raw ->
            val cleaned = raw.text.filterNot { it.isWhitespace() }
            // A multi-character insertion at the end is a paste: it replaces the whole code,
            // mirroring COUICodeInputView's TextWatcher which splits the pasted string into cells.
            val replaced = if (cleaned.length > code.length + 1 && cleaned.startsWith(code)) {
                cleaned.substring(code.length)
            } else {
                cleaned
            }
            val next = replaced.take(cellCount)
            fieldValue = TextFieldValue(next, TextRange(next.length))
            if (next != code) {
                currentOnValueChange(next)
                if (next.length == cellCount) currentOnComplete?.invoke(next)
            }
        },
        modifier = modifier,
        enabled = enabled,
        textStyle = HiddenFieldTextStyle,
        keyboardOptions = keyboardOptions,
        singleLine = true,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(Color.Transparent),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.Center) {
                CodeCellRow(
                    code = code,
                    cellCount = cellCount,
                    isFocused = isFocused,
                    cursorVisible = cursorVisible,
                    security = security,
                    cellSize = cellSize,
                    cellCornerRadius = cellCornerRadius,
                    colors = colors,
                    textStyle = textStyle,
                )
                // The real text field stays invisible on top of the cells, like COUI's
                // transparent EditText overlay in coui_phone_code_layout.xml.
                Box(modifier = Modifier.matchParentSize().alpha(0f)) { innerTextField() }
            }
        },
    )
}

/**
 * Lays out the code cells centered in the available width. Mirrors
 * COUICodeInputView.setCodeItemWidth: cells scale down proportionally below the 360dp reference
 * width and the gap between adjacent cells adapts within the COUI min/max bounds.
 */
@Composable
private fun CodeCellRow(
    code: String,
    cellCount: Int,
    isFocused: Boolean,
    cursorVisible: Boolean,
    security: Boolean,
    cellSize: DpSize,
    cellCornerRadius: Dp,
    colors: CodeTextFieldColors,
    textStyle: TextStyle,
) {
    BoxWithConstraints {
        val bounded = maxWidth != Dp.Infinity
        val scale = if (bounded) (maxWidth / CodeTextFieldDefaults.ReferenceWidth).coerceAtMost(1f) else 1f
        val scaledCellSize = DpSize(cellSize.width * scale, cellSize.height * scale)
        val spacing = if (bounded && cellCount > 1) {
            // COUI getCellMarginHorizontal: per-side margin = (w - n * cellW) / (2n - 2), which
            // equals an even (w - n * cellW) / (n - 1) gap between adjacent cells.
            ((maxWidth - scaledCellSize.width * cellCount) / (cellCount - 1))
                .coerceIn(CodeTextFieldDefaults.MinCellSpacing, CodeTextFieldDefaults.MaxCellSpacing)
        } else {
            CodeTextFieldDefaults.MaxCellSpacing
        }
        val activeIndex = code.length.coerceAtMost(cellCount - 1)
        Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
            repeat(cellCount) { index ->
                val digit = code.getOrNull(index)?.toString().orEmpty()
                CodeCell(
                    digit = digit,
                    selected = isFocused && index == activeIndex,
                    showCursor = isFocused && cursorVisible && index == activeIndex && digit.isEmpty(),
                    security = security,
                    size = scaledCellSize,
                    cornerRadius = cellCornerRadius,
                    colors = colors,
                    textStyle = textStyle,
                )
            }
        }
    }
}

/**
 * One code cell: a card-colored round rect, an animated focus stroke, and either the digit
 * (with COUI's appear/disappear animation) or a security dot. Mirrors
 * COUICodeInputView.CodeItemView + COUICodeInputHelper.
 */
@Composable
private fun CodeCell(
    digit: String,
    selected: Boolean,
    showCursor: Boolean,
    security: Boolean,
    size: DpSize,
    cornerRadius: Dp,
    colors: CodeTextFieldColors,
    textStyle: TextStyle,
) {
    val strokeAlpha = remember { Animatable(if (selected) 1f else 0f) }
    LaunchedEffect(selected) {
        if (selected) {
            strokeAlpha.animateTo(1f, tween(BoxAnimDurationMillis, delayMillis = BoxAppearDelayMillis, easing = CodeMoveEase))
        } else {
            strokeAlpha.animateTo(0f, tween(BoxAnimDurationMillis, easing = CodeMoveEase))
        }
    }

    // Keep the outgoing digit around so the disappear animation can still draw it, mirroring
    // COUICodeInputHelper.getAnimatorNumber. Security mode never animates (COUI skips it).
    var displayDigit by remember { mutableStateOf(digit) }
    val digitAlpha = remember { Animatable(if (digit.isEmpty()) 0f else 1f) }
    val digitScale = remember { Animatable(1f) }
    LaunchedEffect(digit, security) {
        when {
            digit == displayDigit -> Unit

            digit.isNotEmpty() -> {
                val wasEmpty = displayDigit.isEmpty()
                displayDigit = digit
                if (wasEmpty && !security) {
                    digitAlpha.snapTo(0f)
                    digitScale.snapTo(NumberScaleStart)
                    launch { digitScale.animateTo(1f, tween(NumberAnimDurationMillis, easing = CodeMoveEase)) }
                    digitAlpha.animateTo(1f, tween(NumberAnimDurationMillis, easing = CodeMoveEase))
                } else {
                    digitAlpha.snapTo(1f)
                    digitScale.snapTo(1f)
                }
            }

            else -> {
                if (!security) {
                    digitAlpha.animateTo(0f, tween(NumberAnimDurationMillis, delayMillis = NumberDisappearDelayMillis, easing = CodeMoveEase))
                } else {
                    digitAlpha.snapTo(0f)
                }
                displayDigit = ""
                digitScale.snapTo(1f)
            }
        }
    }

    Box(
        modifier = Modifier
            .size(size)
            .squircleBackground(color = colors.cellBackgroundColor, cornerRadius = cornerRadius)
            .squircleBorder(
                width = { if (strokeAlpha.value > 0f) CodeTextFieldDefaults.CellStrokeWidth else 0.dp },
                color = { colors.focusedStrokeColor.copy(alpha = colors.focusedStrokeColor.alpha * strokeAlpha.value) },
                cornerRadius = cornerRadius,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (security) {
            if (digit.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(CodeTextFieldDefaults.SecurityCircleRadius * 2)
                        .background(colors.securityCircleColor, CircleShape),
                )
            }
        } else if (displayDigit.isNotEmpty()) {
            Text(
                text = displayDigit,
                style = textStyle,
                color = colors.textColor,
                modifier = Modifier.graphicsLayer {
                    alpha = digitAlpha.value
                    scaleX = digitScale.value
                    scaleY = digitScale.value
                },
            )
        }
        if (showCursor) {
            Box(
                modifier = Modifier
                    .size(width = CodeTextFieldDefaults.CursorWidth, height = size.height / 2)
                    .background(colors.cursorColor),
            )
        }
    }
}

/** Contains default values used by [CodeTextField]. */
object CodeTextFieldDefaults {
    /** The default number of cells (COUICodeInputView CELL_COUNT / couiCodeInputCount). */
    val CellCount: Int = 6

    /** The size of one cell (COUI coui_code_input_cell_width x coui_code_input_cell_height). */
    val CellSize = DpSize(42.dp, 46.dp)

    /** The corner radius of one cell (COUI couiRoundCornerS / coui_code_input_cell_radius). */
    val CellCornerRadius = 8.dp

    /** The minimum gap between adjacent cells (2 x COUI coui_code_input_cell_min_margin_horizontal). */
    val MinCellSpacing = 4.dp

    /** The maximum gap between adjacent cells (2 x COUI coui_code_input_cell_max_margin_horizontal). */
    val MaxCellSpacing = 16.dp

    /** The stroke width of the active-cell highlight (COUI coui_code_input_cell_stroke_width). */
    val CellStrokeWidth = 1.6.dp

    /** The radius of the dot shown in security mode (COUI coui_code_input_cell_security_circle_radius). */
    val SecurityCircleRadius = 5.dp

    /** The width of the blinking cursor (COUI coui_text_cursor_width). */
    val CursorWidth = 2.dp

    /** The reference width below which cells scale down (COUICodeInputView DEFAULT_SCREEN_WIDTH_DP). */
    val ReferenceWidth = 360.dp

    /** The default text style of a cell digit (COUI coui_code_input_cell_text_size). */
    @Composable
    fun textStyle(): TextStyle = COUITheme.textStyles.title2.copy(fontSize = 30.sp)

    /**
     * Default colors used by the [CodeTextField].
     *
     * @param cellBackgroundColor The background color of a cell (COUI couiColorCardBackground).
     * @param textColor The color of the digits (COUI couiColorPrimaryNeutral).
     * @param focusedStrokeColor The color of the active-cell stroke (COUI couiColorPrimary).
     * @param securityCircleColor The color of the security dot
     *   (COUI coui_code_input_security_circle_color = 84.7% on-surface ink).
     * @param cursorColor The color of the blinking cursor.
     */
    @Composable
    fun codeTextFieldColors(
        cellBackgroundColor: Color = COUITheme.colorScheme.surfaceContainer,
        textColor: Color = COUITheme.colorScheme.onSurface,
        focusedStrokeColor: Color = COUITheme.colorScheme.primary,
        securityCircleColor: Color = COUITheme.colorScheme.onSurface.copy(alpha = 0.847f),
        cursorColor: Color = COUITheme.colorScheme.primary,
    ): CodeTextFieldColors = remember(cellBackgroundColor, textColor, focusedStrokeColor, securityCircleColor, cursorColor) {
        CodeTextFieldColors(
            cellBackgroundColor = cellBackgroundColor,
            textColor = textColor,
            focusedStrokeColor = focusedStrokeColor,
            securityCircleColor = securityCircleColor,
            cursorColor = cursorColor,
        )
    }
}

/**
 * Colors used by a [CodeTextField] in different parts of the component.
 *
 * @param cellBackgroundColor The background color of a cell.
 * @param textColor The color of the digits.
 * @param focusedStrokeColor The color of the active-cell stroke.
 * @param securityCircleColor The color of the dot shown in security mode.
 * @param cursorColor The color of the blinking cursor.
 */
@Immutable
data class CodeTextFieldColors(
    val cellBackgroundColor: Color,
    val textColor: Color,
    val focusedStrokeColor: Color,
    val securityCircleColor: Color,
    val cursorColor: Color,
)

/** COUIMoveEaseInterpolator (0.3, 0, 0.1, 1) used by all cell animations. */
private val CodeMoveEase = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)

/** COUICodeInputHelper NUMBER_APPEAR_ANIMATOR_DURATION. */
private val NumberAnimDurationMillis = 100

/** COUICodeInputHelper NUMBER_DELAY_ANIMATOR_DURATION (digit fade-out delay). */
private val NumberDisappearDelayMillis = 33

/** COUICodeInputHelper INBOX_APPEAR_ANIMATOR_DURATION. */
private val BoxAnimDurationMillis = 100

/** COUICodeInputHelper INBOX_DELAY_ANIMATOR_DURATION (stroke fade-in delay). */
private val BoxAppearDelayMillis = 33

/** COUICodeInputHelper NUMBER_SCALE_START. */
private val NumberScaleStart = 0.6f

/** Half of the standard cursor blink period. */
private val CursorBlinkHalfPeriodMillis = 500L

/** Text style of the hidden overlay field: fully transparent, like COUI's overlay EditText. */
private val HiddenFieldTextStyle = TextStyle(color = Color.Transparent)
