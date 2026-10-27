// Copyright 2026, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.suqi8.coui.kmp.preference

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.BasicComponentColors
import io.github.suqi8.coui.kmp.basic.BasicComponentDefaults
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.DropdownArrowEndAction
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.basic.Check
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * An entry of a [ListPreference] / [MultiSelectListPreference] selection panel.
 *
 * @param text The text of the entry.
 * @param summary The optional summary shown below the text.
 * @param enabled Whether the entry can be selected.
 */
@Immutable
data class ListPreferenceEntry(
    val text: String,
    val summary: String? = null,
    val enabled: Boolean = true,
)

/**
 * A list preference with a single-choice selection panel, mirroring COUIListPreference.
 *
 * The preference row shows the selected entry as trailing assignment text and opens a bottom
 * sheet single-choice panel when clicked. Tapping an entry commits the selection and dismisses
 * the panel; the cancel button or an outside tap dismisses without changes.
 *
 * @param entries The list of [ListPreferenceEntry] to choose from.
 * @param selectedIndex The index of the selected entry, or -1 when nothing is selected.
 * @param onSelectedIndexChange The callback when an entry is selected in the panel.
 * @param title The title of the [ListPreference].
 * @param cancelButtonText The label of the cancel button at the bottom of the panel.
 * @param modifier The modifier to be applied to the [ListPreference].
 * @param titleColor The color of the title.
 * @param summary The summary of the [ListPreference].
 * @param summaryColor The color of the summary.
 * @param dialogTitle The title of the selection panel. Defaults to [title].
 * @param colors The [ListPreferenceColors] used by the selection panel rows.
 * @param startAction The [Composable] content on the start side of the [ListPreference].
 * @param bottomAction The [Composable] content at the bottom of the [ListPreference].
 * @param insideMargin The margin inside the [ListPreference].
 * @param cardListPosition The row's position inside its card group. Rounded outer edges receive extra padding.
 * @param enabled Whether the [ListPreference] is clickable.
 * @param showValue Whether to show the selected entry as trailing assignment text.
 * @param renderInRootScaffold Whether to render the panel in the root (outermost) Scaffold.
 * @param onExpandedChange The callback when the panel is shown or dismissed.
 */
@Composable
fun ListPreference(
    entries: List<ListPreferenceEntry>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    title: String,
    cancelButtonText: String,
    modifier: Modifier = Modifier,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    dialogTitle: String = title,
    colors: ListPreferenceColors = ListPreferenceDefaults.listPreferenceColors(),
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
    val hapticFeedback = LocalHapticFeedback.current
    val currentHapticFeedback by rememberUpdatedState(hapticFeedback)
    val currentOnSelectedIndexChange by rememberUpdatedState(onSelectedIndexChange)
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
            val selectedValueText = entries.getOrNull(selectedIndex)?.text
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
                        val selected = index == selectedIndex
                        SelectPanelItem(
                            text = entry.text,
                            summary = entry.summary,
                            selected = selected,
                            checked = null,
                            showDivider = index != entries.lastIndex,
                            colors = colors,
                            enabled = entry.enabled,
                            onClick = {
                                currentOnSelectedIndexChange(index)
                                setExpanded(false)
                            },
                        ) {
                            SelectedCheckIndicator(
                                visible = selected,
                                enabled = entry.enabled,
                                colors = colors,
                            )
                        }
                    }
                }
                TextButton(
                    text = cancelButtonText,
                    onClick = { setExpanded(false) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = ListPreferenceDefaults.ButtonBarTopPadding,
                            bottom = ListPreferenceDefaults.ButtonBarBottomPadding,
                        ),
                    colors = ButtonDefaults.textButtonColorsBorderless(),
                )
            }
        }
    }
}

/**
 * The trailing assignment text and popup indicator of a [ListPreference] /
 * [MultiSelectListPreference] row (COUI assignment-in-right layout).
 */
@Composable
internal fun RowScope.PreferenceValueEndActions(
    valueText: String?,
    enabled: Boolean,
) {
    val actionColor = if (enabled) {
        COUITheme.colorScheme.onSurfaceVariantActions
    } else {
        COUITheme.colorScheme.disabledOnSecondaryVariant
    }
    val valueColor = if (enabled) {
        COUITheme.colorScheme.onSurfaceSecondary
    } else {
        COUITheme.colorScheme.disabledOnSecondaryVariant
    }
    if (!valueText.isNullOrBlank()) {
        Text(
            text = valueText,
            modifier = Modifier
                .padding(start = 8.dp, end = 4.dp)
                .weight(1f, fill = false),
            fontSize = COUITheme.textStyles.body2.fontSize,
            color = valueColor,
            textAlign = TextAlign.End,
            overflow = TextOverflow.Ellipsis,
        )
    }
    DropdownArrowEndAction(
        actionColor = actionColor,
    )
}

/**
 * A row of the bottom sheet selection panel: text and optional summary on the start side, a
 * selection indicator slot at the end, and a hairline divider below every row but the last.
 *
 * @param checked When null the row uses single-choice semantics ([Role.RadioButton]);
 *   otherwise it uses toggleable checkbox semantics with this checked value.
 */
@Composable
internal fun SelectPanelItem(
    text: String,
    summary: String?,
    selected: Boolean,
    checked: Boolean?,
    showDivider: Boolean,
    colors: ListPreferenceColors,
    enabled: Boolean,
    onClick: () -> Unit,
    endContent: @Composable () -> Unit,
) {
    val currentOnClick by rememberUpdatedState(onClick)
    Column(modifier = Modifier.fillMaxWidth()) {
        val interactionModifier = if (checked == null) {
            Modifier.selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = { currentOnClick() },
            )
        } else {
            Modifier.toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { currentOnClick() },
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ListPreferenceDefaults.PanelItemMinHeight)
                .then(interactionModifier)
                .padding(vertical = ListPreferenceDefaults.PanelItemVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = ListPreferenceDefaults.PanelItemIndicatorSpacing),
            ) {
                Text(
                    text = text,
                    fontSize = COUITheme.textStyles.headline2.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = colors.itemTextColor(enabled),
                )
                summary?.let {
                    Text(
                        text = it,
                        modifier = Modifier.padding(top = ListPreferenceDefaults.PanelItemSummarySpacing),
                        fontSize = COUITheme.textStyles.body2.fontSize,
                        color = colors.itemSummaryColor(enabled),
                    )
                }
            }
            endContent()
        }
        if (showDivider) {
            HorizontalDivider()
        }
    }
}

/**
 * The reserved end slot of a single-choice panel row: a primary-tinted check mark when the row
 * is selected, empty (but still occupying the indicator size) otherwise.
 */
@Composable
internal fun SelectedCheckIndicator(
    visible: Boolean,
    enabled: Boolean,
    colors: ListPreferenceColors,
) {
    Box(
        modifier = Modifier.size(ListPreferenceDefaults.CheckIconSize),
        contentAlignment = Alignment.Center,
    ) {
        if (visible) {
            val indicatorColor = colors.selectedIndicatorColor(enabled)
            val colorFilter = remember(indicatorColor) { ColorFilter.tint(indicatorColor) }
            Image(
                imageVector = COUIIcons.Basic.Check,
                contentDescription = null,
                modifier = Modifier.size(ListPreferenceDefaults.CheckIconSize),
                colorFilter = colorFilter,
            )
        }
    }
}

object ListPreferenceDefaults {

    /** The minimum height of a panel row (COUI coui_delete_alert_dialog_button_height). */
    val PanelItemMinHeight = 48.dp

    /** The vertical padding of a panel row (COUI alert_dialog_single_list_padding_vertical). */
    val PanelItemVerticalPadding = 10.dp

    /**
     * The gap between the text block and the selection indicator of a panel row
     * (COUI coui_dialog_layout_margin_horizontal).
     */
    val PanelItemIndicatorSpacing = 16.dp

    /**
     * The spacing between the text and the summary of a panel row
     * (COUI coui_alert_dialog_content_panel_padding_top).
     */
    val PanelItemSummarySpacing = 2.dp

    /** The size of the selected check indicator, matching the COUI 24dp selection widgets. */
    val CheckIconSize = 24.dp

    /**
     * The gap between the last panel row and the button bar
     * (COUI alert_dialog_single_list_last_item_padding_bottom).
     */
    val ButtonBarTopPadding = 6.dp

    /** The padding below the panel button bar. */
    val ButtonBarBottomPadding = 12.dp

    /**
     * The default [ListPreferenceColors] of the selection panel rows.
     */
    @Composable
    fun listPreferenceColors(
        itemTextColor: Color = COUITheme.colorScheme.onSurface,
        disabledItemTextColor: Color = COUITheme.colorScheme.disabledOnSecondaryVariant,
        itemSummaryColor: Color = COUITheme.colorScheme.onSurfaceSecondary,
        disabledItemSummaryColor: Color = COUITheme.colorScheme.disabledOnSecondaryVariant,
        selectedIndicatorColor: Color = COUITheme.colorScheme.primary,
        disabledSelectedIndicatorColor: Color = COUITheme.colorScheme.disabledPrimary,
    ): ListPreferenceColors = remember(
        itemTextColor,
        disabledItemTextColor,
        itemSummaryColor,
        disabledItemSummaryColor,
        selectedIndicatorColor,
        disabledSelectedIndicatorColor,
    ) {
        ListPreferenceColors(
            itemTextColor = itemTextColor,
            disabledItemTextColor = disabledItemTextColor,
            itemSummaryColor = itemSummaryColor,
            disabledItemSummaryColor = disabledItemSummaryColor,
            selectedIndicatorColor = selectedIndicatorColor,
            disabledSelectedIndicatorColor = disabledSelectedIndicatorColor,
        )
    }
}

@Immutable
data class ListPreferenceColors(
    val itemTextColor: Color,
    val disabledItemTextColor: Color,
    val itemSummaryColor: Color,
    val disabledItemSummaryColor: Color,
    val selectedIndicatorColor: Color,
    val disabledSelectedIndicatorColor: Color,
) {
    @Stable
    internal fun itemTextColor(enabled: Boolean): Color = if (enabled) itemTextColor else disabledItemTextColor

    @Stable
    internal fun itemSummaryColor(enabled: Boolean): Color = if (enabled) itemSummaryColor else disabledItemSummaryColor

    @Stable
    internal fun selectedIndicatorColor(enabled: Boolean): Color = if (enabled) selectedIndicatorColor else disabledSelectedIndicatorColor
}
