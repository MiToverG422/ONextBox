// Copyright 2026, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.suqi8.coui.kmp.preference

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.BasicComponentColors
import io.github.suqi8.coui.kmp.basic.BasicComponentDefaults
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.Checkbox
import io.github.suqi8.coui.kmp.basic.CheckboxColors
import io.github.suqi8.coui.kmp.basic.CheckboxDefaults
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * A multi-select list preference with a multi-choice selection panel, mirroring
 * COUIMultiSelectListPreference.
 *
 * The preference row shows the selected entries as trailing assignment text and opens a bottom
 * sheet multi-choice panel when clicked. Toggling rows only updates a pending selection; the
 * confirm button commits it, while the cancel button or an outside tap dismisses the panel
 * without changes.
 *
 * @param entries The list of [ListPreferenceEntry] to choose from.
 * @param selectedIndices The indices of the selected entries.
 * @param onSelectedIndicesChange The callback with the new selection when the confirm button
 *   is clicked.
 * @param title The title of the [MultiSelectListPreference].
 * @param confirmButtonText The label of the confirm (positive) button of the panel.
 * @param cancelButtonText The label of the cancel (negative) button of the panel.
 * @param modifier The modifier to be applied to the [MultiSelectListPreference].
 * @param titleColor The color of the title.
 * @param summary The summary of the [MultiSelectListPreference].
 * @param summaryColor The color of the summary.
 * @param dialogTitle The title of the selection panel. Defaults to [title].
 * @param colors The [ListPreferenceColors] used by the selection panel rows.
 * @param checkboxColors The [CheckboxColors] of the row checkboxes.
 * @param startAction The [Composable] content on the start side of the [MultiSelectListPreference].
 * @param bottomAction The [Composable] content at the bottom of the [MultiSelectListPreference].
 * @param insideMargin The margin inside the [MultiSelectListPreference].
 * @param cardListPosition The row's position inside its card group. Rounded outer edges receive extra padding.
 * @param enabled Whether the [MultiSelectListPreference] is clickable.
 * @param showValue Whether to show the selected entries as trailing assignment text.
 * @param renderInRootScaffold Whether to render the panel in the root (outermost) Scaffold.
 * @param onExpandedChange The callback when the panel is shown or dismissed.
 */
// The staged selection mirrors the public Set<Int> API; boxing is bounded by the entry count.
@Suppress("ktlint:compose:mutable-state-autoboxing")
@Composable
fun MultiSelectListPreference(
    entries: List<ListPreferenceEntry>,
    selectedIndices: Set<Int>,
    onSelectedIndicesChange: (Set<Int>) -> Unit,
    title: String,
    confirmButtonText: String,
    cancelButtonText: String,
    modifier: Modifier = Modifier,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    dialogTitle: String = title,
    colors: ListPreferenceColors = ListPreferenceDefaults.listPreferenceColors(),
    checkboxColors: CheckboxColors = CheckboxDefaults.checkboxColors(),
    startAction: @Composable (() -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    cardListPosition: CardListPosition = CardListPosition.None,
    enabled: Boolean = true,
    showValue: Boolean = true,
    renderInRootScaffold: Boolean = true,
    onExpandedChange: ((Boolean) -> Unit)? = null,
) {
    val isSheetShown = remember { mutableStateOf(false) }
    val isHoldDown = remember { mutableStateOf(false) }
    val pendingSelection = remember { mutableStateOf(selectedIndices) }
    val hapticFeedback = LocalHapticFeedback.current
    val currentHapticFeedback by rememberUpdatedState(hapticFeedback)
    val currentSelectedIndices by rememberUpdatedState(selectedIndices)
    val currentOnSelectedIndicesChange by rememberUpdatedState(onSelectedIndicesChange)
    val currentOnExpandedChange = rememberUpdatedState(onExpandedChange)
    val setExpanded: (Boolean) -> Unit = remember {
        { expanded ->
            if (isSheetShown.value != expanded) {
                isSheetShown.value = expanded
                currentOnExpandedChange.value?.invoke(expanded)
            }
        }
    }

    val hasEntries = entries.isNotEmpty()
    val actualEnabled = enabled && hasEntries

    val handleClick = remember(actualEnabled) {
        {
            if (actualEnabled) {
                // Restart the pending selection from the committed one on every open.
                pendingSelection.value = currentSelectedIndices
                setExpanded(true)
                isHoldDown.value = true
                currentHapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
            }
        }
    }

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
            val selectedValueText = remember(entries, selectedIndices) {
                selectedIndices
                    .filter { it in entries.indices }
                    .sorted()
                    .joinToString(", ") { entries[it].text }
                    .ifBlank { null }
            }
            PreferenceValueEndActions(
                valueText = if (showValue) selectedValueText else null,
                enabled = actualEnabled,
            )
        },
        bottomAction = bottomAction,
        onClick = handleClick,
        role = Role.DropdownList,
        holdDownState = isHoldDown.value,
        enabled = actualEnabled,
    )

    if (hasEntries) {
        OverlayBottomSheet(
            show = isSheetShown.value,
            title = dialogTitle,
            onDismissRequest = { setExpanded(false) },
            onDismissFinished = { isHoldDown.value = false },
            renderInRootScaffold = renderInRootScaffold,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    entries.forEachIndexed { index, entry ->
                        val checked = index in pendingSelection.value
                        SelectPanelItem(
                            text = entry.text,
                            summary = entry.summary,
                            selected = checked,
                            checked = checked,
                            showDivider = index != entries.lastIndex,
                            colors = colors,
                            enabled = entry.enabled,
                            onClick = {
                                pendingSelection.value = if (checked) {
                                    pendingSelection.value - index
                                } else {
                                    pendingSelection.value + index
                                }
                            },
                        ) {
                            // The row itself handles the toggle interaction.
                            Checkbox(
                                state = ToggleableState(checked),
                                onClick = null,
                                enabled = entry.enabled,
                                colors = checkboxColors,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = ListPreferenceDefaults.ButtonBarTopPadding,
                            bottom = ListPreferenceDefaults.ButtonBarBottomPadding,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        text = cancelButtonText,
                        onClick = { setExpanded(false) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsBorderless(),
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(20.dp)
                            .background(COUITheme.colorScheme.dividerLine),
                    )
                    TextButton(
                        text = confirmButtonText,
                        onClick = {
                            currentOnSelectedIndicesChange(pendingSelection.value)
                            setExpanded(false)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsBorderless(),
                    )
                }
            }
        }
    }
}
