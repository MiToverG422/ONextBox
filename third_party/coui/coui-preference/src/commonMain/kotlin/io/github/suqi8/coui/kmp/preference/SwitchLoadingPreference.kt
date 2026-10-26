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
import io.github.suqi8.coui.kmp.basic.Switch
import io.github.suqi8.coui.kmp.basic.SwitchColors
import io.github.suqi8.coui.kmp.basic.SwitchDefaults

/**
 * A switch with a title, a summary and a loading state, mirroring COUISwitchLoadingPreference.
 *
 * While [isLoading] is `true` the switch thumb shows the COUI loading spinner and neither the
 * row nor the switch can be toggled. The usual flow is: the user taps the row, [onCheckedChange]
 * fires, the caller flips [isLoading] on, performs the asynchronous work, then updates [checked]
 * and turns [isLoading] off again.
 *
 * @param checked The checked state of the [SwitchLoadingPreference].
 * @param onCheckedChange The callback when the checked state of the [SwitchLoadingPreference] is
 *   changed. Not invoked while [isLoading] is `true`.
 * @param title The title of the [SwitchLoadingPreference].
 * @param modifier The modifier to be applied to the [SwitchLoadingPreference].
 * @param isLoading Whether the switch shows the COUI loading spinner on its thumb. While loading
 *   the preference cannot be toggled.
 * @param titleColor The color of the title.
 * @param summary The summary of the [SwitchLoadingPreference].
 * @param summaryColor The color of the summary.
 * @param startAction The [Composable] content on the start side of the [SwitchLoadingPreference].
 * @param endActions The [Composable] content on the end side of the [SwitchLoadingPreference].
 * @param bottomAction The [Composable] content at the bottom of the [SwitchLoadingPreference].
 * @param switchColors The [SwitchColors] of the [SwitchLoadingPreference].
 * @param insideMargin The margin inside the [SwitchLoadingPreference].
 * @param cardListPosition The row's position inside its card group. Rounded outer edges receive extra padding.
 * @param holdDownState Used to determine whether it is in the pressed state.
 * @param enabled Whether the [SwitchLoadingPreference] is clickable.
 */
@Composable
@NonRestartableComposable
fun SwitchLoadingPreference(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    startAction: @Composable (() -> Unit)? = null,
    endActions: @Composable RowScope.() -> Unit = {},
    bottomAction: (@Composable () -> Unit)? = null,
    switchColors: SwitchColors = SwitchDefaults.switchColors(),
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    cardListPosition: CardListPosition = CardListPosition.None,
    holdDownState: Boolean = false,
    enabled: Boolean = true,
) {
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)
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
            ) {
                endActions()
            }
            SwitchLoadingPreferenceEndActions(
                checked = checked,
                onCheckedChange = currentOnCheckedChange,
                enabled = enabled,
                isLoading = isLoading,
                switchColors = switchColors,
            )
        },
        bottomAction = bottomAction,
        onClick = {
            currentOnCheckedChange.takeIf { enabled && !isLoading }?.invoke(!checked)
        },
        role = Role.Switch,
        holdDownState = holdDownState,
        enabled = enabled,
    )
}

@Composable
private fun SwitchLoadingPreferenceEndActions(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    enabled: Boolean,
    isLoading: Boolean,
    switchColors: SwitchColors,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        isLoading = isLoading,
        colors = switchColors,
    )
}
