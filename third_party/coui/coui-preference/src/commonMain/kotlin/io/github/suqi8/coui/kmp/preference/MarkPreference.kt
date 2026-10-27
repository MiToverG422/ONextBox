// Copyright 2026, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.suqi8.coui.kmp.preference

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.BasicComponentColors
import io.github.suqi8.coui.kmp.basic.BasicComponentDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.RadioButton
import io.github.suqi8.coui.kmp.basic.RadioButtonColors
import io.github.suqi8.coui.kmp.basic.RadioButtonDefaults
import io.github.suqi8.coui.kmp.preference.internal.StartActionSlot

/**
 * A single-select row with a selection mark, mirroring COUIMarkPreference.
 *
 * The mark itself is not clickable — the whole row is the click target. Rows are meant to be
 * used as a single-select group: clicking a row invokes [onClick] and the caller moves the
 * checked state to that row.
 *
 * @param title The title of the [MarkPreference].
 * @param checked Whether this row currently carries the mark.
 * @param onClick The callback when the [MarkPreference] is clicked.
 * @param modifier The modifier to be applied to the [MarkPreference].
 * @param titleColor The color of the title.
 * @param summary The summary of the [MarkPreference].
 * @param summaryColor The color of the summary.
 * @param markColors The [RadioButtonColors] of the mark.
 * @param startAction The [Composable] content on the start side of the [MarkPreference].
 * @param endActions The [Composable] content on the end side of the [MarkPreference].
 * @param markLocation The location of the mark, [MarkLocation.End] or [MarkLocation.Start].
 * @param bottomAction The [Composable] content at the bottom of the [MarkPreference].
 * @param insideMargin The margin inside the [MarkPreference].
 * @param cardListPosition The row's position inside its card group. Rounded outer edges receive extra padding.
 * @param holdDownState Used to determine whether it is in the pressed state.
 * @param enabled Whether the [MarkPreference] is clickable.
 */
@Composable
@NonRestartableComposable
fun MarkPreference(
    title: String,
    checked: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    markColors: RadioButtonColors = RadioButtonDefaults.radioButtonColors(),
    startAction: @Composable (() -> Unit)? = null,
    endActions: @Composable (RowScope.() -> Unit)? = null,
    markLocation: MarkLocation = MarkLocation.End,
    bottomAction: (@Composable () -> Unit)? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    cardListPosition: CardListPosition = CardListPosition.None,
    holdDownState: Boolean = false,
    enabled: Boolean = true,
) {
    val currentOnClick by rememberUpdatedState(onClick)

    BasicComponent(
        modifier = modifier,
        insideMargin = insideMargin,
        cardListPosition = cardListPosition,
        title = title,
        titleColor = titleColor,
        summary = summary,
        summaryColor = summaryColor,
        startAction = if (markLocation == MarkLocation.Start || startAction != null) {
            {
                Row {
                    if (markLocation == MarkLocation.Start) {
                        MarkPreferenceMark(
                            checked = checked,
                            enabled = enabled,
                            markColors = markColors,
                            modifier = Modifier.padding(end = 5.dp),
                        )
                    }

                    startAction?.let {
                        StartActionSlot(
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
                                .weight(1f, fill = false),
                            endSpacing = 5.dp,
                            content = it,
                        )
                    }
                }
            }
        } else {
            null
        },
        endActions = {
            endActions?.let {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .weight(1f, fill = false),
                ) {
                    it()
                }
            }

            if (markLocation == MarkLocation.End) {
                Row(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .align(Alignment.CenterVertically),
                ) {
                    MarkPreferenceMark(
                        checked = checked,
                        enabled = enabled,
                        markColors = markColors,
                    )
                }
            }
        },
        bottomAction = bottomAction,
        onClick = {
            currentOnClick.takeIf { enabled }?.invoke()
        },
        role = Role.RadioButton,
        holdDownState = holdDownState,
        enabled = enabled,
    )
}

@Composable
private fun MarkPreferenceMark(
    checked: Boolean,
    enabled: Boolean,
    markColors: RadioButtonColors,
    modifier: Modifier = Modifier,
) {
    RadioButton(
        modifier = modifier,
        selected = checked,
        onClick = null,
        enabled = enabled,
        colors = markColors,
    )
}

/**
 * The location of the mark in a [MarkPreference]: [End] maps to COUI TAIL_MARK (default),
 * [Start] to HEAD_MARK.
 */
enum class MarkLocation {
    Start,
    End,
}
