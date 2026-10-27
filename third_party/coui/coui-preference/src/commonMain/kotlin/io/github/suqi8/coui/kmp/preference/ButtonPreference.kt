// Copyright 2026, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.suqi8.coui.kmp.preference

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.BasicComponentColors
import io.github.suqi8.coui.kmp.basic.BasicComponentDefaults
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.ButtonColors
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * A preference row with a small inline button at the end, mirroring COUIButtonPreference.
 *
 * The button follows the COUI small button style and its click is independent from the row click.
 *
 * @param title The title of the [ButtonPreference].
 * @param buttonText The label of the inline button.
 * @param onButtonClick The callback when the inline button is clicked.
 * @param modifier The modifier to be applied to the [ButtonPreference].
 * @param titleColor The color of the title.
 * @param summary The summary of the [ButtonPreference].
 * @param summaryColor The color of the summary.
 * @param startAction The [Composable] content on the start side of the [ButtonPreference].
 * @param endActions The [Composable] content on the end side of the [ButtonPreference],
 *   placed before the button.
 * @param bottomAction The [Composable] content at the bottom of the [ButtonPreference].
 * @param buttonColors The [ButtonColors] of the inline button.
 * @param buttonMinWidth The minimum width of the inline button.
 * @param buttonMinHeight The minimum height of the inline button.
 * @param buttonCornerRadius The corner radius of the inline button.
 * @param buttonInsideMargin The margin inside the inline button.
 * @param insideMargin The margin inside the [ButtonPreference].
 * @param cardListPosition The row's position inside its card group. Rounded outer edges receive extra padding.
 * @param onClick The callback when the row (not the button) is clicked.
 * @param holdDownState Used to determine whether it is in the pressed state.
 * @param enabled Whether the [ButtonPreference] and its button are enabled.
 */
@Composable
@NonRestartableComposable
fun ButtonPreference(
    title: String,
    buttonText: String,
    onButtonClick: () -> Unit,
    modifier: Modifier = Modifier,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    startAction: @Composable (() -> Unit)? = null,
    endActions: @Composable RowScope.() -> Unit = {},
    bottomAction: (@Composable () -> Unit)? = null,
    buttonColors: ButtonColors = ButtonPreferenceDefaults.buttonColors(),
    buttonMinWidth: Dp = ButtonPreferenceDefaults.ButtonMinWidth,
    buttonMinHeight: Dp = ButtonPreferenceDefaults.ButtonMinHeight,
    buttonCornerRadius: Dp = ButtonPreferenceDefaults.ButtonCornerRadius,
    buttonInsideMargin: PaddingValues = ButtonPreferenceDefaults.ButtonInsideMargin,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    cardListPosition: CardListPosition = CardListPosition.None,
    onClick: (() -> Unit)? = null,
    holdDownState: Boolean = false,
    enabled: Boolean = true,
) {
    BasicComponent(
        modifier = modifier,
        insideMargin = insideMargin,
        cardListPosition = cardListPosition,
        title = title,
        titleColor = titleColor,
        summary = summary,
        summaryColor = summaryColor,
        startAction = startAction,
        endActions = {
            Row(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .align(Alignment.CenterVertically)
                    .weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                endActions()
            }
            ButtonPreferenceEndAction(
                buttonText = buttonText,
                onButtonClick = onButtonClick,
                enabled = enabled,
                buttonColors = buttonColors,
                buttonMinWidth = buttonMinWidth,
                buttonMinHeight = buttonMinHeight,
                buttonCornerRadius = buttonCornerRadius,
                buttonInsideMargin = buttonInsideMargin,
            )
        },
        bottomAction = bottomAction,
        onClick = onClick,
        holdDownState = holdDownState,
        enabled = enabled,
    )
}

@Composable
private fun ButtonPreferenceEndAction(
    buttonText: String,
    onButtonClick: () -> Unit,
    enabled: Boolean,
    buttonColors: ButtonColors,
    buttonMinWidth: Dp,
    buttonMinHeight: Dp,
    buttonCornerRadius: Dp,
    buttonInsideMargin: PaddingValues,
) {
    Button(
        onClick = onButtonClick,
        enabled = enabled,
        cornerRadius = buttonCornerRadius,
        minWidth = buttonMinWidth,
        minHeight = buttonMinHeight,
        colors = buttonColors,
        insideMargin = buttonInsideMargin,
    ) {
        Text(
            text = buttonText,
            fontSize = COUITheme.textStyles.body2.fontSize,
            fontWeight = FontWeight.Medium,
        )
    }
}

object ButtonPreferenceDefaults {

    /** The minimum width of the inline button (COUI coui_btn_small_width_min). */
    val ButtonMinWidth = 52.dp

    /** The minimum height of the inline button (COUI coui_btn_small_height_min). */
    val ButtonMinHeight = 28.dp

    /** The corner radius of the inline button (COUI auto capsule, [ButtonMinHeight] / 2). */
    val ButtonCornerRadius = 14.dp

    /** The margin inside the inline button (Widget.COUI.Button.Small padding 12dp/4dp). */
    val ButtonInsideMargin = PaddingValues(horizontal = 12.dp, vertical = 4.dp)

    /**
     * The default [ButtonColors] of the inline button (COUI theme accent fill with on-color label).
     */
    @Composable
    fun buttonColors(
        color: Color = COUITheme.colorScheme.primary,
        disabledColor: Color = COUITheme.colorScheme.disabledPrimaryButton,
        contentColor: Color = COUITheme.colorScheme.onPrimary,
        disabledContentColor: Color = COUITheme.colorScheme.disabledOnPrimaryButton,
    ): ButtonColors = ButtonDefaults.buttonColors(
        color = color,
        disabledColor = disabledColor,
        contentColor = contentColor,
        disabledContentColor = disabledContentColor,
    )
}
