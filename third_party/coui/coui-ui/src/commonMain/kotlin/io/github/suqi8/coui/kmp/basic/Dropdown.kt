// Copyright 2025, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.suqi8.coui.kmp.basic

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlendModeColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.basic.ArrowRight
import io.github.suqi8.coui.kmp.icon.basic.ArrowUpDown
import io.github.suqi8.coui.kmp.icon.basic.Check
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
@NonRestartableComposable
fun RowScope.DropdownArrowEndAction(
    actionColor: Color,
    modifier: Modifier = Modifier,
) {
    val colorFilter = remember(actionColor) { ColorFilter.tint(actionColor) }
    Image(
        modifier = modifier
            .size(width = DropdownDefaults.ArrowSize.width, height = DropdownDefaults.ArrowSize.height)
            .align(Alignment.CenterVertically),
        imageVector = COUIIcons.Basic.ArrowUpDown,
        colorFilter = colorFilter,
        contentDescription = null,
    )
}

/**
 * The implementation of the dropdown.
 *
 * @param item The item of the current option.
 * @param optionSize The size of the options.
 * @param isSelected Whether the option is selected.
 * @param index The index of the current option in the options.
 * @param dropdownColors The [DropdownColors] used to style the option row.
 * @param enabled Whether the option is clickable. Disabled rows ignore clicks and use the disabled text color.
 * @param dialogMode Whether the item is shown in dialog mode.
 * @param hasSubmenu When true, this row acts as a submenu trigger: a trailing chevron is shown
 *   instead of the selection check, and the row's accessibility role becomes [Role.Button].
 * @param isFirst Whether this row is the first row of the entire popup. Controls the larger top
 *   padding in popup mode. Defaults to `index == 0`; multi-entry callers should pass the
 *   popup-global flag so only the actual first row gets extra padding.
 * @param isLast Whether this row is the last row of the entire popup. Controls the larger bottom
 *   padding in popup mode. Defaults to `index == optionSize - 1`; multi-entry callers should pass
 *   the popup-global flag.
 * @param onSelectedIndexChange The callback invoked with [index] when the option is selected.
 */
@Composable
fun DropdownImpl(
    item: DropdownItem,
    optionSize: Int,
    isSelected: Boolean,
    index: Int,
    dropdownColors: DropdownColors = DropdownDefaults.dropdownColors(),
    enabled: Boolean = item.enabled,
    dialogMode: Boolean = false,
    hasSubmenu: Boolean = false,
    isFirst: Boolean = index == 0,
    isLast: Boolean = index == optionSize - 1,
    onSelectedIndexChange: (Int) -> Unit,
) {
    val additionalTopPadding =
        if (!dialogMode && isFirst) {
            DropdownDefaults.FirstLastVerticalPadding
        } else {
            DropdownDefaults.MiddleVerticalPadding
        }
    val additionalBottomPadding =
        if (!dialogMode && isLast) {
            DropdownDefaults.FirstLastVerticalPadding
        } else {
            DropdownDefaults.MiddleVerticalPadding
        }

    val backgroundColor = if (isSelected) {
        dropdownColors.selectedContainerColor
    } else {
        dropdownColors.containerColor
    }
    val backgroundColorState = rememberUpdatedState(backgroundColor)

    val checkColor = when {
        !isSelected -> Color.Transparent
        !enabled -> dropdownColors.disabledContentColor
        else -> dropdownColors.selectedIndicatorColor
    }

    // COUI DefaultAdapter.getTitleColor resolves the alert item type (ITEM_TYPE_ALERT) to
    // couiColorError before the tint selector is consulted, so an enabled alert row is red
    // whether or not it is selected; disabled alert rows fall back to the selector.
    val titleColor = when {
        !enabled -> dropdownColors.disabledContentColor
        item.alert -> dropdownColors.alertContentColor
        isSelected -> dropdownColors.selectedContentColor
        else -> dropdownColors.contentColor
    }

    // COUI DefaultAdapter.setDescription always paints the description with
    // couiColorLabelSecondary — no selected or disabled variant, unlike the title selector.
    val summaryColor = if (isSelected) dropdownColors.selectedSummaryColor else dropdownColors.summaryColor

    val containerModifier = remember(dialogMode, additionalTopPadding, additionalBottomPadding) {
        val sized = if (dialogMode) {
            Modifier
                .heightIn(min = DropdownDefaults.MinHeight)
                .widthIn(min = DropdownDefaults.MinWidth)
                .fillMaxWidth()
                .padding(horizontal = DropdownDefaults.DialogHorizontalPadding)
        } else {
            // COUI DefaultAdapter.configItemVerticalPadding enforces a per-row minimum height
            // (padding included): the base coui_popup_list_window_item_min_height grown, on
            // first/last rows, by the folded-in list inset carried by the larger edge padding.
            val minHeight = DropdownDefaults.PopupItemMinHeight +
                (additionalTopPadding - DropdownDefaults.MiddleVerticalPadding) +
                (additionalBottomPadding - DropdownDefaults.MiddleVerticalPadding)
            Modifier
                .heightIn(min = minHeight)
                .padding(horizontal = DropdownDefaults.InsideHorizontalPadding)
        }
        sized.padding(top = additionalTopPadding, bottom = additionalBottomPadding)
    }
    val innerRowModifier = remember(dialogMode) {
        if (dialogMode) Modifier else Modifier.widthIn(max = DropdownDefaults.MaxItemTextWidth)
    }

    val currentOnSelectedIndexChange by rememberUpdatedState(onSelectedIndexChange)
    val role = if (hasSubmenu) Role.Button else Role.RadioButton
    // COUI DefaultAdapter.setHint hides the whole hint layout on a disabled row, so the
    // surrounding gaps collapse with it (setGap only reserves them for a visible hint).
    val hint = item.hint?.takeIf { enabled }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .drawBehind { drawRect(backgroundColorState.value) }
            .popupListItem(
                selected = isSelected,
                enabled = enabled,
                role = role,
                hasIcon = item.icon != null,
                onClick = { currentOnSelectedIndexChange(index) },
            )
            .then(containerModifier),
    ) {
        Row(
            modifier = innerRowModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            item.icon?.let { it(IconCellModifier) }
            Column {
                Text(
                    text = item.text,
                    style = COUITheme.textStyles.body1,
                    // COUI popup rows are regular weight (coui_popup_list_window_item.xml
                    // textFontWeight=400); dialog single-choice rows use medium (couiTextAppearanceHeadline6).
                    fontWeight = if (dialogMode) FontWeight.Medium else FontWeight.Normal,
                    color = titleColor,
                    // COUI DefaultAdapter.setTitle: 3 lines when the row has no description, 2 when it does.
                    maxLines = if (item.summary == null) {
                        DropdownDefaults.TitleMaxLines
                    } else {
                        DropdownDefaults.TitleWithSummaryMaxLines
                    },
                    overflow = TextOverflow.Ellipsis,
                )
                item.summary?.let {
                    Text(
                        text = it,
                        style = COUITheme.textStyles.body2,
                        color = summaryColor,
                        // COUI DefaultAdapter.setDescription.
                        maxLines = DropdownDefaults.SummaryMaxLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (hint != null) {
            Spacer(TitleEndGapModifier)
            Box(HintCellModifier, contentAlignment = Alignment.Center) { hint() }
        }

        if (hasSubmenu) {
            // COUI tints the expandable indicator via the status-icon selector
            // (coui_popup_list_window_item_status_icon_tint_selector: couiColorLabelTertiary default,
            // couiColorLabelTheme while expanded). The chevron is intentionally static —
            // only the cloned header in the secondary popup rotates during expansion.
            val chevronColor = when {
                !enabled -> dropdownColors.disabledContentColor
                isSelected -> dropdownColors.selectedContentColor
                else -> COUITheme.colorScheme.onSurfaceVariantActions
            }
            val chevronColorFilter = remember(chevronColor) {
                BlendModeColorFilter(chevronColor, BlendMode.SrcIn)
            }
            Image(
                modifier = if (hint != null) HintedChevronIconModifier else ChevronIconBaseModifier,
                imageVector = COUIIcons.Basic.ArrowRight,
                colorFilter = chevronColorFilter,
                contentDescription = null,
            )
        } else {
            val checkColorFilter = remember(checkColor) {
                BlendModeColorFilter(checkColor, BlendMode.SrcIn)
            }
            Image(
                modifier = if (hint != null) HintedCheckIconModifier else CheckIconBaseModifier,
                imageVector = COUIIcons.Basic.Check,
                colorFilter = checkColorFilter,
                contentDescription = null,
            )
        }
    }
}

/**
 * A non-clickable group-title row shown inside a dropdown popup
 * (COUI coui_popup_list_window_header_item.xml).
 *
 * @param text The group title.
 * @param modifier The modifier applied to the header row.
 * @param color The title color.
 * @param isFirst Whether this header is the first row of the entire popup. Like [DropdownImpl], the
 *   top edge then absorbs the folded-in list inset carried by [DropdownDefaults.FirstLastVerticalPadding].
 */
@Composable
fun DropdownHeaderItem(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = DropdownDefaults.dropdownColors().headerColor,
    isFirst: Boolean = false,
) {
    val topPadding = if (isFirst) {
        DropdownDefaults.FirstLastVerticalPadding
    } else {
        DropdownDefaults.MiddleVerticalPadding
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(
                min = DropdownDefaults.PopupHeaderItemMinHeight +
                    (topPadding - DropdownDefaults.MiddleVerticalPadding),
            )
            .padding(horizontal = DropdownDefaults.InsideHorizontalPadding)
            .padding(top = topPadding, bottom = DropdownDefaults.MiddleVerticalPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            // COUI couiTextDescription: 12sp sans-serif-medium.
            fontSize = COUITheme.textStyles.footnote2.fontSize,
            fontWeight = FontWeight.Medium,
            color = color,
            maxLines = DropdownDefaults.HeaderMaxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
@NonRestartableComposable
fun DropdownImpl(
    text: String,
    optionSize: Int,
    isSelected: Boolean,
    index: Int,
    dropdownColors: DropdownColors = DropdownDefaults.dropdownColors(),
    enabled: Boolean = true,
    dialogMode: Boolean = false,
    onSelectedIndexChange: (Int) -> Unit,
) {
    val item = remember(text, enabled) { DropdownItem(text = text, enabled = enabled) }
    DropdownImpl(
        item = item,
        optionSize = optionSize,
        isSelected = isSelected,
        index = index,
        dropdownColors = dropdownColors,
        enabled = enabled,
        dialogMode = dialogMode,
        onSelectedIndexChange = onSelectedIndexChange,
    )
}

/**
 * The implementation of the spinner.
 *
 * @param entry the [DropdownItem] to be shown in the spinner.
 * @param entryCount the count of the entries in the spinner.
 * @param isSelected whether the entry is selected.
 * @param index the index of the entry.
 * @param spinnerColors the [DropdownColors] used to style the entry row.
 * @param dialogMode whether the spinner is in dialog mode.
 * @param onSelectedIndexChange the callback to be invoked when the selected index of the spinner is changed.
 */
@Deprecated(
    message = "Use DropdownImpl instead. SpinnerItemImpl is a thin alias kept for compatibility.",
    replaceWith = ReplaceWith(
        "DropdownImpl(item = entry, optionSize = entryCount, isSelected = isSelected, " +
            "index = index, dropdownColors = spinnerColors, enabled = entry.enabled, " +
            "dialogMode = dialogMode, onSelectedIndexChange = onSelectedIndexChange)",
    ),
    level = DeprecationLevel.WARNING,
)
@Composable
fun SpinnerItemImpl(
    entry: DropdownItem,
    entryCount: Int,
    isSelected: Boolean,
    index: Int,
    spinnerColors: DropdownColors,
    dialogMode: Boolean = false,
    onSelectedIndexChange: (Int) -> Unit,
) {
    DropdownImpl(
        item = entry,
        optionSize = entryCount,
        isSelected = isSelected,
        index = index,
        dropdownColors = spinnerColors,
        enabled = entry.enabled,
        dialogMode = dialogMode,
        onSelectedIndexChange = onSelectedIndexChange,
    )
}

/**
 * Colors used by dropdown option rows.
 *
 * @param contentColor The text color of an unselected option.
 * @param summaryColor The summary text color of an unselected option.
 * @param containerColor The background color of an unselected option.
 * @param selectedContentColor The text color of the selected option.
 * @param selectedSummaryColor The summary text color of the selected option.
 * @param selectedContainerColor The background color of the selected option.
 * @param selectedIndicatorColor The color of the selected indicator icon.
 * @param disabledContentColor The text color of a disabled option. Defaults to [contentColor] so
 *   existing constructor calls keep compiling; [DropdownDefaults.dropdownColors] supplies the
 *   COUI value.
 * @param alertContentColor The text color of an alert (destructive) option. Defaults to
 *   [contentColor], i.e. no alert tint unless a caller opts in.
 * @param headerColor The title color of a [DropdownHeaderItem] group header row. Defaults to
 *   [summaryColor], which already carries the same couiColorLabelSecondary value.
 */
@Immutable
data class DropdownColors(
    val contentColor: Color,
    val summaryColor: Color,
    val containerColor: Color,
    val selectedContentColor: Color,
    val selectedSummaryColor: Color,
    val selectedContainerColor: Color,
    val selectedIndicatorColor: Color,
    val disabledContentColor: Color = contentColor,
    val alertContentColor: Color = contentColor,
    val headerColor: Color = summaryColor,
)

/**
 * A group of dropdown items.
 *
 * A [DropdownEntry] represents one visual group in a dropdown menu.
 *
 * @param items Items shown in this dropdown group.
 * @param enabled Whether this group is enabled. When false, all items in this group are disabled;
 * when true, each item's [DropdownItem.enabled] value is still respected.
 * @param title Optional group header rendered above [items] as a non-clickable
 *   [DropdownHeaderItem] (COUI coui_popup_list_window_header_item.xml). Popup mode only; dialog
 *   mode has no COUI header presentation and ignores it.
 */
@Stable
data class DropdownEntry(
    val items: List<DropdownItem>,
    val enabled: Boolean = true,
    val title: String? = null,
)

/**
 * An item shown inside a dropdown, spinner, or dropdown menu.
 *
 * @param text Text shown for the item.
 * @param enabled Whether the item can be clicked.
 * @param selected Whether the item is selected.
 * @param onClick Callback invoked when the item is clicked. Ignored when [children] is non-null
 *   and non-empty (the click is consumed by the cascading layer to expand the submenu).
 * @param icon Optional icon shown before [text].
 * @param summary Optional summary shown below [text].
 * @param children Optional submenu items. When non-null and non-empty, this item becomes a
 *   submenu trigger: cascading dropdown popups render a chevron and open a child popup on click,
 *   recursively. Stabilize this list (e.g. via `remember`) to avoid unnecessary recomposition.
 * @param hint Optional trailing hint slot rendered between the title block and the selection
 *   indicator (COUI popup_list_window_item_hint_layout). Suitable for a badge, a red dot, or a
 *   short count; it is width-capped at [DropdownDefaults.HintMaxWidth] and, matching COUI
 *   DefaultAdapter.setHint, hidden entirely while the row is disabled.
 * @param alert Whether this is an alert (destructive) item. Its title is tinted with
 *   [DropdownColors.alertContentColor] (COUI ITEM_TYPE_ALERT → couiColorError).
 */
@Stable
data class DropdownItem(
    val text: String,
    val enabled: Boolean = true,
    val selected: Boolean = false,
    val onClick: (() -> Unit)? = null,
    val icon: @Composable ((Modifier) -> Unit)? = null,
    val summary: String? = null,
    val children: List<DropdownItem>? = null,
    val hint: @Composable (() -> Unit)? = null,
    val alert: Boolean = false,
) {
    /**
     * [SpinnerEntry] compatibility
     */
    constructor(
        icon: @Composable ((Modifier) -> Unit)? = null,
        title: String? = null,
        summary: String? = null,
    ) : this(
        text = title.orEmpty(),
        icon = icon,
        summary = summary,
    )
}

object DropdownDefaults {
    /** Minimum row height when the dropdown is shown in dialog mode (COUI alert_dialog_list_item_min_height). */
    val MinHeight: Dp = 50.dp

    /** Minimum row width when the dropdown is shown in dialog mode. */
    val MinWidth: Dp = 200.dp

    /**
     * Minimum height of each option row in popup mode, vertical padding included
     * (COUI coui_popup_list_window_item_min_height). Matching COUI
     * DefaultAdapter.configItemVerticalPadding, the first/last rows additionally grow by the
     * folded-in list inset (coui_popup_list_padding_vertical), i.e. by
     * [FirstLastVerticalPadding] - [MiddleVerticalPadding] per affected edge.
     */
    val PopupItemMinHeight: Dp = 48.dp

    /** Size of the trailing check icon shown on the selected option (COUI coui_popup_list_window_checkbox_width). */
    val CheckIconSize: Dp = 24.dp

    /**
     * Size of the up-down arrow rendered by [DropdownArrowEndAction]
     * (native box of COUI coui_pop_up_next_normal.xml, a 14x24dp vector).
     */
    val ArrowSize: DpSize = DpSize(width = 14.dp, height = 24.dp)

    /**
     * Size of the trailing chevron shown on rows that own a submenu
     * (native box of COUI coui_btn_next_normal.xml, a 12x24dp vector).
     */
    val ChevronSize: DpSize = DpSize(width = 12.dp, height = 24.dp)

    /** Minimum size of the leading icon cell (COUI coui_popup_list_window_item.xml icon, a 24dp ImageView). */
    val IconMinSize: Dp = 24.dp

    /**
     * Maximum width of the inner text/icon row when the dropdown is shown in popup mode
     * (COUI coui_popup_list_window_item_max_width; applied as maxWidth on the title/description
     * text views in coui_popup_list_window_item.xml).
     */
    val MaxItemTextWidth: Dp = 248.dp

    /**
     * Horizontal padding of each row in popup mode (COUI coui_popup_list_window_item_icon_margin_left,
     * used as both paddingStart and paddingEnd in coui_popup_list_window_item.xml).
     */
    val InsideHorizontalPadding: Dp = 16.dp

    /** Horizontal padding of each row in dialog mode. */
    val DialogHorizontalPadding: Dp = 28.dp

    /**
     * Top/bottom padding applied to the first/last row in popup mode: COUI
     * coui_popup_list_window_item_padding_top_and_bottom (8dp) plus the list inset
     * coui_popup_list_padding_vertical (2dp) that COUI DefaultAdapter folds into the edge rows.
     */
    val FirstLastVerticalPadding: Dp = 10.dp

    /**
     * Top/bottom padding applied to middle rows in popup mode and to all rows in dialog mode
     * (COUI coui_popup_list_window_item_padding_top_and_bottom).
     */
    val MiddleVerticalPadding: Dp = 8.dp

    /**
     * Color of the 4dp group divider band between dropdown groups in popup mode
     * (COUI coui_popup_list_group_divider_color #14000000). The resource has no values-night
     * override, so COUI renders the same translucent black band in both light and dark themes.
     */
    val PopupGroupDividerColor: Color = Color(0x14000000)

    /** Thickness of the group divider band (COUI coui_popup_list_group_divider_height). */
    val PopupGroupDividerThickness: Dp = 4.dp

    /** Padding between the leading icon cell and the title text (COUI coui_popup_list_window_item_icon_margin_right). */
    val IconEndPadding: Dp = 12.dp

    /**
     * Padding between the title/summary block and the trailing check icon
     * (COUI popup_list_window_item_title_end_gap, an 8dp Space in coui_popup_list_window_item.xml).
     */
    val CheckIconStartPadding: Dp = 8.dp

    /**
     * Minimum height of a [DropdownHeaderItem] group header row, vertical padding included
     * (COUI coui_popup_list_window_header_item_min_height).
     */
    val PopupHeaderItemMinHeight: Dp = 32.dp

    /**
     * Maximum width of the trailing hint slot
     * (COUI popup_list_window_item_hint_layout couiMaxWidth in coui_popup_list_window_item.xml).
     */
    val HintMaxWidth: Dp = 40.dp

    /**
     * Padding between the trailing hint slot and the selection indicator
     * (COUI popup_list_window_item_hint_end_gap, a 4dp Space in coui_popup_list_window_item.xml).
     */
    val HintEndPadding: Dp = 4.dp

    /** Title line limit on a row without a summary (COUI DefaultAdapter.setTitle). */
    val TitleMaxLines: Int = 3

    /** Title line limit on a row that also shows a summary (COUI DefaultAdapter.setTitle). */
    val TitleWithSummaryMaxLines: Int = 2

    /** Summary line limit (COUI DefaultAdapter.setDescription). */
    val SummaryMaxLines: Int = 2

    /** Group header line limit (COUI coui_popup_list_window_header_item.xml maxLines). */
    val HeaderMaxLines: Int = 2

    /**
     * Default popup row colors, matching COUI coui_popup_list_window_item_tint_selector
     * (default couiColorLabelPrimary, selected couiColorLabelTheme, disabled couiColorLabelTertiary).
     * Summaries always use couiColorLabelSecondary, and row containers stay transparent —
     * the popup surface provides the fill.
     */
    @Composable
    fun dropdownColors(
        contentColor: Color = COUITheme.colorScheme.onSurfaceContainer,
        summaryColor: Color = COUITheme.colorScheme.onSurfaceSecondary,
        containerColor: Color = Color.Transparent,
        selectedContentColor: Color = COUITheme.colorScheme.primary,
        selectedSummaryColor: Color = COUITheme.colorScheme.onSurfaceSecondary,
        selectedContainerColor: Color = Color.Transparent,
        selectedIndicatorColor: Color = COUITheme.colorScheme.primary,
        disabledContentColor: Color = COUITheme.colorScheme.disabledOnSecondaryVariant,
        alertContentColor: Color = COUITheme.colorScheme.error,
        headerColor: Color = COUITheme.colorScheme.onSurfaceSecondary,
    ): DropdownColors = rememberDropdownColorsImpl(
        contentColor = contentColor,
        summaryColor = summaryColor,
        containerColor = containerColor,
        selectedContentColor = selectedContentColor,
        selectedSummaryColor = selectedSummaryColor,
        selectedContainerColor = selectedContainerColor,
        selectedIndicatorColor = selectedIndicatorColor,
        disabledContentColor = disabledContentColor,
        alertContentColor = alertContentColor,
        headerColor = headerColor,
    )

    @Composable
    fun dialogDropdownColors(
        contentColor: Color = COUITheme.colorScheme.onSurfaceContainer,
        summaryColor: Color = COUITheme.colorScheme.onSurfaceVariantSummary,
        containerColor: Color = Color.Transparent,
        selectedContentColor: Color = COUITheme.colorScheme.onTertiaryContainer,
        selectedSummaryColor: Color = COUITheme.colorScheme.onTertiaryContainer,
        selectedContainerColor: Color = COUITheme.colorScheme.tertiaryContainer,
        selectedIndicatorColor: Color = COUITheme.colorScheme.onTertiaryContainer,
        disabledContentColor: Color = COUITheme.colorScheme.disabledOnSecondaryVariant,
        alertContentColor: Color = COUITheme.colorScheme.error,
        headerColor: Color = COUITheme.colorScheme.onSurfaceSecondary,
    ): DropdownColors = rememberDropdownColorsImpl(
        contentColor = contentColor,
        summaryColor = summaryColor,
        containerColor = containerColor,
        selectedContentColor = selectedContentColor,
        selectedSummaryColor = selectedSummaryColor,
        selectedContainerColor = selectedContainerColor,
        selectedIndicatorColor = selectedIndicatorColor,
        disabledContentColor = disabledContentColor,
        alertContentColor = alertContentColor,
        headerColor = headerColor,
    )
}

@Composable
private fun rememberDropdownColorsImpl(
    contentColor: Color,
    summaryColor: Color,
    containerColor: Color,
    selectedContentColor: Color,
    selectedSummaryColor: Color,
    selectedContainerColor: Color,
    selectedIndicatorColor: Color,
    disabledContentColor: Color,
    alertContentColor: Color,
    headerColor: Color,
): DropdownColors = remember(
    contentColor,
    summaryColor,
    containerColor,
    selectedContentColor,
    selectedSummaryColor,
    selectedContainerColor,
    selectedIndicatorColor,
    disabledContentColor,
    alertContentColor,
    headerColor,
) {
    DropdownColors(
        contentColor = contentColor,
        summaryColor = summaryColor,
        containerColor = containerColor,
        selectedContentColor = selectedContentColor,
        selectedSummaryColor = selectedSummaryColor,
        selectedContainerColor = selectedContainerColor,
        selectedIndicatorColor = selectedIndicatorColor,
        disabledContentColor = disabledContentColor,
        alertContentColor = alertContentColor,
        headerColor = headerColor,
    )
}

private val CheckIconBaseModifier = Modifier
    .padding(start = DropdownDefaults.CheckIconStartPadding)
    .size(DropdownDefaults.CheckIconSize)

private val ChevronIconBaseModifier = Modifier
    .padding(start = DropdownDefaults.CheckIconStartPadding)
    .size(width = DropdownDefaults.ChevronSize.width, height = DropdownDefaults.ChevronSize.height)

// With a hint present the 8dp title-end gap already sits before the hint, so the indicator only
// keeps the 4dp hint-end gap (COUI popup_list_window_item_hint_end_gap).
private val HintedCheckIconModifier = Modifier
    .padding(start = DropdownDefaults.HintEndPadding)
    .size(DropdownDefaults.CheckIconSize)

private val HintedChevronIconModifier = Modifier
    .padding(start = DropdownDefaults.HintEndPadding)
    .size(width = DropdownDefaults.ChevronSize.width, height = DropdownDefaults.ChevronSize.height)

private val TitleEndGapModifier = Modifier.width(DropdownDefaults.CheckIconStartPadding)

private val HintCellModifier = Modifier.widthIn(max = DropdownDefaults.HintMaxWidth)

private val IconCellModifier = Modifier
    .sizeIn(minWidth = DropdownDefaults.IconMinSize, minHeight = DropdownDefaults.IconMinSize)
    .padding(end = DropdownDefaults.IconEndPadding)

@Deprecated(
    message = "Use DropdownDefaults instead.",
    replaceWith = ReplaceWith("DropdownDefaults"),
)
object SpinnerDefaults {
    @Composable
    fun spinnerColors(
        contentColor: Color = COUITheme.colorScheme.onSurfaceContainer,
        summaryColor: Color = COUITheme.colorScheme.onSurfaceSecondary,
        containerColor: Color = Color.Transparent,
        selectedContentColor: Color = COUITheme.colorScheme.primary,
        selectedSummaryColor: Color = COUITheme.colorScheme.onSurfaceSecondary,
        selectedContainerColor: Color = Color.Transparent,
        selectedIndicatorColor: Color = COUITheme.colorScheme.primary,
        disabledContentColor: Color = COUITheme.colorScheme.disabledOnSecondaryVariant,
        alertContentColor: Color = COUITheme.colorScheme.error,
        headerColor: Color = COUITheme.colorScheme.onSurfaceSecondary,
    ): DropdownColors = DropdownDefaults.dropdownColors(
        contentColor = contentColor,
        summaryColor = summaryColor,
        containerColor = containerColor,
        selectedContentColor = selectedContentColor,
        selectedSummaryColor = selectedSummaryColor,
        selectedContainerColor = selectedContainerColor,
        selectedIndicatorColor = selectedIndicatorColor,
        disabledContentColor = disabledContentColor,
        alertContentColor = alertContentColor,
        headerColor = headerColor,
    )

    @Composable
    fun dialogSpinnerColors(
        contentColor: Color = COUITheme.colorScheme.onSurfaceContainer,
        summaryColor: Color = COUITheme.colorScheme.onSurfaceVariantSummary,
        containerColor: Color = Color.Transparent,
        selectedContentColor: Color = COUITheme.colorScheme.onTertiaryContainer,
        selectedSummaryColor: Color = COUITheme.colorScheme.onTertiaryContainer,
        selectedContainerColor: Color = COUITheme.colorScheme.tertiaryContainer,
        selectedIndicatorColor: Color = COUITheme.colorScheme.onTertiaryContainer,
        disabledContentColor: Color = COUITheme.colorScheme.disabledOnSecondaryVariant,
        alertContentColor: Color = COUITheme.colorScheme.error,
        headerColor: Color = COUITheme.colorScheme.onSurfaceSecondary,
    ): DropdownColors = DropdownDefaults.dialogDropdownColors(
        contentColor = contentColor,
        summaryColor = summaryColor,
        containerColor = containerColor,
        selectedContentColor = selectedContentColor,
        selectedSummaryColor = selectedSummaryColor,
        selectedContainerColor = selectedContainerColor,
        selectedIndicatorColor = selectedIndicatorColor,
        disabledContentColor = disabledContentColor,
        alertContentColor = alertContentColor,
        headerColor = headerColor,
    )
}

@Deprecated(
    message = "Use DropdownColors instead.",
    replaceWith = ReplaceWith("DropdownColors"),
)
typealias SpinnerColors = DropdownColors

@Deprecated(
    message = "Use DropdownItem instead.",
    replaceWith = ReplaceWith("DropdownItem"),
)
typealias SpinnerEntry = DropdownItem
