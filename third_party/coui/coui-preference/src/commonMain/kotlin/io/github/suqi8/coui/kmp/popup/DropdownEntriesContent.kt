// Copyright 2026, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.suqi8.coui.kmp.popup

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import io.github.suqi8.coui.kmp.basic.DropdownColors
import io.github.suqi8.coui.kmp.basic.DropdownDefaults
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownHeaderItem
import io.github.suqi8.coui.kmp.basic.DropdownImpl
import io.github.suqi8.coui.kmp.basic.HorizontalDivider

/**
 * Renders a list of [DropdownEntry] groups inside a popup container. Computes popup-global
 * first/last per row internally so only the very first and very last rows of the entire popup
 * receive the larger first/last padding; group boundaries fall back to the middle padding.
 *
 * A group that declares a [DropdownEntry.title] gets a non-clickable [DropdownHeaderItem] above its
 * items. The header then owns the group's leading edge, so the first option row no longer counts as
 * the popup's first row.
 */
@Composable
internal fun DropdownEntriesPopupContent(
    entries: List<DropdownEntry>,
    dropdownColors: DropdownColors,
    onItemClick: (entryIdx: Int, itemIdx: Int) -> Unit,
) {
    val lastEntryIdx = entries.lastIndex
    entries.forEachIndexed { entryIdx, entry ->
        val lastItemIdx = entry.items.lastIndex
        val isFirstEntry = entryIdx == 0
        val isLastEntry = entryIdx == lastEntryIdx
        val header = entry.title
        if (header != null) {
            key(entryIdx, "header") {
                DropdownHeaderItem(
                    text = header,
                    color = dropdownColors.headerColor,
                    isFirst = isFirstEntry,
                )
            }
        }
        entry.items.forEachIndexed { itemIdx, option ->
            key(entryIdx, itemIdx) {
                DropdownImpl(
                    item = option,
                    optionSize = entry.items.size,
                    isSelected = option.selected,
                    index = itemIdx,
                    dropdownColors = dropdownColors,
                    enabled = entry.enabled && option.enabled,
                    isFirst = isFirstEntry && header == null && itemIdx == 0,
                    isLast = isLastEntry && itemIdx == lastItemIdx,
                    onSelectedIndexChange = { idx -> onItemClick(entryIdx, idx) },
                )
            }
        }
        if (entryIdx != lastEntryIdx) {
            HorizontalDivider(
                thickness = DropdownDefaults.PopupGroupDividerThickness,
                color = DropdownDefaults.PopupGroupDividerColor,
            )
        }
    }
}

/**
 * Adds [DropdownEntry] groups to a [LazyListScope] for use inside a dialog. Dialog mode uses
 * uniform vertical padding regardless of position, so popup-global first/last is intentionally
 * not propagated. A [DropdownEntry.title] still renders a header row so grouped dialogs stay
 * labelled, but COUI has no dialog header metrics to calibrate against — it reuses the popup one.
 */
internal fun LazyListScope.dropdownEntriesDialogItems(
    entries: List<DropdownEntry>,
    dropdownColors: DropdownColors,
    onItemClick: (entryIdx: Int, itemIdx: Int) -> Unit,
) {
    val lastEntryIdx = entries.lastIndex
    entries.forEachIndexed { entryIdx, entry ->
        entry.title?.let { header ->
            item(key = "header-$entryIdx") {
                DropdownHeaderItem(text = header, color = dropdownColors.headerColor)
            }
        }
        items(entry.items.size, key = { itemIdx -> "$entryIdx-$itemIdx" }) { itemIdx ->
            val item = entry.items[itemIdx]
            DropdownImpl(
                item = item,
                optionSize = entry.items.size,
                isSelected = item.selected,
                index = itemIdx,
                dropdownColors = dropdownColors,
                enabled = entry.enabled && item.enabled,
                dialogMode = true,
                onSelectedIndexChange = { idx -> onItemClick(entryIdx, idx) },
            )
        }
        if (entryIdx != lastEntryIdx) {
            item(key = "divider-$entryIdx") {
                HorizontalDivider(
                    thickness = DropdownDefaults.PopupGroupDividerThickness,
                    color = DropdownDefaults.PopupGroupDividerColor,
                )
            }
        }
    }
}
