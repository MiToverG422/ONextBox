package com.mi.onextbox.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowListPopup

@Composable
internal fun MiuixPreferenceRow(
    title: String,
    summary: String?,
    modifier: Modifier = Modifier,
    startAction: (@Composable () -> Unit)? = null,
    endActions: (@Composable RowScope.() -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    role: Role? = null,
) {
    BasicComponent(
        modifier = modifier,
        title = title,
        summary = summary?.takeIf(String::isNotBlank),
        startAction = startAction,
        endActions = endActions,
        bottomAction = bottomAction,
        enabled = enabled,
        onClick = onClick,
        role = role,
    )
}

@Composable
internal fun MiuixDropdownPreference(
    entry: DropdownEntry,
    title: String,
    modifier: Modifier,
    summary: String?,
    startAction: (@Composable () -> Unit)?,
    bottomAction: (@Composable () -> Unit)?,
    maxHeight: Dp?,
    enabled: Boolean,
    showValue: Boolean,
    collapseOnSelection: Boolean,
    onExpandedChange: ((Boolean) -> Unit)?,
    interactionEnabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    fun expand(value: Boolean) {
        if (expanded != value) {
            expanded = value
            onExpandedChange?.invoke(value)
        }
    }
    Box(modifier.fillMaxWidth().settingsInteractionGate(interactionEnabled)) {
        BasicComponent(
            startAction = startAction,
            bottomAction = bottomAction,
            enabled = enabled && entry.items.isNotEmpty(),
            onClick = { if (interactionEnabled) expand(!expanded) },
            role = Role.DropdownList,
        ) {
            // Keep the arrow beside the selected value even when the description wraps.
            MiuixPreferenceValueLayout(
                title = title,
                summary = summary,
                value = if (showValue) entry.items.firstOrNull { it.selected }?.text else null,
                enabled = enabled && entry.items.isNotEmpty(),
                endAction = {
                    Row {
                        DropdownArrowEndAction(if (enabled && entry.items.isNotEmpty())
                            MiuixTheme.colorScheme.onSurfaceVariantActions else MiuixTheme.colorScheme.disabledOnSecondaryVariant)
                    }
                },
            )
        }
        WindowListPopup(
            show = expanded && enabled,
            alignment = PopupPositionProvider.Align.End,
            onDismissRequest = { expand(false) },
            maxHeight = maxHeight,
        ) {
            ListPopupColumn {
                entry.items.forEachIndexed { index, item ->
                    Box(Modifier.settingsInteractionGate(interactionEnabled)) {
                        DropdownImpl(
                            item = DropdownItem(text = item.text, enabled = item.enabled, selected = item.selected),
                            optionSize = entry.items.size,
                            isSelected = item.selected,
                            index = index,
                            enabled = item.enabled,
                            onSelectedIndexChange = {
                                if (item.enabled && enabled && interactionEnabled) {
                                    item.onClick?.invoke()
                                    if (collapseOnSelection) expand(false)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
