@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.mi.onextbox.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.mi.onextbox.ui.common.AppUiStyle
import com.mi.onextbox.ui.common.LocalAppUiStyle
import com.mi.onextbox.ui.common.LocalMaterialSwitchIconsEnabled
import com.mi.onextbox.ui.common.ColorOsSwitch
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check as FilledCheck
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.material3.Switch as MaterialSwitch
import androidx.compose.material3.Text as MaterialText
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.DropdownColors
import io.github.suqi8.coui.kmp.basic.DropdownDefaults
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import kotlinx.coroutines.withTimeoutOrNull

@Composable
internal fun SettingsSwitchPreference(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    startAction: @Composable (() -> Unit)? = null,
    endActions: @Composable RowScope.() -> Unit = {},
    bottomAction: (@Composable () -> Unit)? = null,
    holdDownState: Boolean = false,
    enabled: Boolean = true,
    hiddenHoldDurationMillis: Long? = null,
    onHiddenHold: (() -> Unit)? = null,
    onRowClick: (() -> Unit)? = null,
    switchEnabled: Boolean = enabled,
    interactionEnabled: Boolean = true,
) {
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)
    val currentInteractionEnabled by rememberUpdatedState(interactionEnabled)
    val currentSwitchEnabled by rememberUpdatedState(enabled && switchEnabled)
    val onChange: (Boolean) -> Unit = { if (currentInteractionEnabled && currentSwitchEnabled) currentOnCheckedChange(it) }
    val currentOnHiddenHold by rememberUpdatedState(onHiddenHold)
    val hiddenHoldEnabled = hiddenHoldDurationMillis != null && onHiddenHold != null
    val rowClick: (() -> Unit)? = if (hiddenHoldEnabled) null else ({
        if (currentInteractionEnabled) {
            if (onRowClick != null) onRowClick()
            else if (enabled && switchEnabled) currentOnCheckedChange(!checked)
        }
    })
    val inputModifier = modifier.settingsInteractionGate(interactionEnabled)
    val gestureModifier = if (hiddenHoldEnabled) {
        inputModifier.pointerInput(checked, enabled, hiddenHoldDurationMillis) {
            var hiddenHoldTriggered = false
            detectTapGestures(
                onPress = {
                    hiddenHoldTriggered = false
                    val releasedBeforeTimeout = withTimeoutOrNull(hiddenHoldDurationMillis) {
                        tryAwaitRelease()
                        true
                    } ?: false
                    if (!releasedBeforeTimeout && currentInteractionEnabled) {
                        hiddenHoldTriggered = true
                        currentOnHiddenHold?.invoke()
                        tryAwaitRelease()
                    }
                },
                onTap = {
                    if (!hiddenHoldTriggered && enabled && currentInteractionEnabled) {
                        currentOnCheckedChange(!checked)
                    }
                },
            )
        }
    } else {
        inputModifier
    }
    if (LocalAppUiStyle.current == AppUiStyle.Miuix) {
        MiuixPreferenceRow(
            modifier = gestureModifier, title = title, summary = summary,
            startAction = startAction, bottomAction = bottomAction, enabled = enabled,
            onClick = rowClick, role = if (onRowClick == null) Role.Switch else Role.Button,
            endActions = {
                endActions()
                top.yukonga.miuix.kmp.basic.Switch(
                    checked = checked,
                    modifier = Modifier.settingsInteractionGate(interactionEnabled),
                    onCheckedChange = onChange.takeIf { enabled && switchEnabled && !hiddenHoldEnabled },
                    enabled = enabled && switchEnabled,
                )
            },
        )
        return
    }
    if (LocalAppUiStyle.current == AppUiStyle.Material3Expressive) {
        val showSwitchIcons = LocalMaterialSwitchIconsEnabled.current
        Material3ExpressivePreferenceRow(
            modifier = gestureModifier,
            title = title,
            summary = summary,
            startAction = startAction,
            endActions = {
                endActions()
                MaterialSwitch(
                    checked = checked,
                    modifier = Modifier.settingsInteractionGate(interactionEnabled),
                    onCheckedChange = onChange.takeIf { enabled && switchEnabled && !hiddenHoldEnabled },
                    enabled = enabled && switchEnabled,
                    thumbContent = if (showSwitchIcons && (checked || enabled)) ({
                        MaterialIcon(
                            imageVector = if (checked) Icons.Rounded.Check else Icons.Rounded.Close,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    }) else null,
                    colors = SwitchDefaults.colors(
                        checkedIconColor = MaterialTheme.colorScheme.primary,
                        uncheckedIconColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                )
            },
            bottomAction = bottomAction,
            enabled = enabled,
            onClick = rowClick,
            role = if (onRowClick == null) Role.Switch else Role.Button,
        )
        return
    }
    BasicComponent(
        modifier = gestureModifier,
        insideMargin = SettingsTokens.RowInsideMargin,
        title = title,
        summary = summary,
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
            ColorOsSwitch(
                checked = checked,
                modifier = Modifier.settingsInteractionGate(interactionEnabled),
                onCheckedChange = onChange.takeIf { enabled && switchEnabled && !hiddenHoldEnabled },
                enabled = enabled && switchEnabled,
            )
        },
        bottomAction = bottomAction,
        onClick = rowClick,
        role = if (onRowClick == null) Role.Switch else Role.Button,
        holdDownState = holdDownState,
        enabled = enabled,
    )
}

@Composable
internal fun SettingsWindowDropdownPreference(
    items: List<String>,
    selectedIndex: Int,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    dropdownColors: DropdownColors = DropdownDefaults.dropdownColors(),
    startAction: @Composable (() -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    maxHeight: Dp? = null,
    enabled: Boolean = true,
    showValue: Boolean = true,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    onSelectedIndexChange: ((Int) -> Unit)? = null,
    interactionEnabled: Boolean = true,
) {
    if (LocalAppUiStyle.current == AppUiStyle.Miuix) {
        MiuixDropdownPreference(
            entry = DropdownEntry(items = items.mapIndexed { index, text ->
                DropdownItem(text = text, selected = index == selectedIndex, onClick = { onSelectedIndexChange?.invoke(index) })
            }),
            title = title, modifier = modifier, summary = summary, startAction = startAction,
            bottomAction = bottomAction, maxHeight = maxHeight, enabled = enabled,
            showValue = showValue, collapseOnSelection = true, onExpandedChange = onExpandedChange,
            interactionEnabled = interactionEnabled,
        )
        return
    }
    if (LocalAppUiStyle.current == AppUiStyle.Material3Expressive) {
        Material3ExpressiveDropdownPreference(
            entry = DropdownEntry(
                items = items.mapIndexed { index, text ->
                    DropdownItem(
                        text = text,
                        selected = index == selectedIndex,
                        onClick = { onSelectedIndexChange?.invoke(index) },
                    )
                },
            ),
            title = title,
            modifier = modifier,
            summary = summary,
            startAction = startAction,
            bottomAction = bottomAction,
            maxHeight = maxHeight,
            enabled = enabled,
            showValue = showValue,
            collapseOnSelection = true,
            onExpandedChange = onExpandedChange,
            interactionEnabled = interactionEnabled,
        )
        return
    }
    ColorOsWindowDropdownPreference(
        items = items,
        selectedIndex = selectedIndex,
        title = title,
        modifier = modifier,
        summary = summary,
        dropdownColors = dropdownColors,
        startAction = startAction,
        bottomAction = bottomAction,
        maxHeight = maxHeight,
        enabled = enabled,
        showValue = showValue,
        onExpandedChange = onExpandedChange,
        onSelectedIndexChange = onSelectedIndexChange,
        interactionEnabled = interactionEnabled,
    )
}

@Composable
private fun Material3ExpressiveDropdownPreference(
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
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var anchorOffset by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(IntOffset.Zero) }
    val actualEnabled = enabled && entry.items.isNotEmpty()
    fun setExpanded(value: Boolean) {
        if (expanded != value) {
            expanded = value
            onExpandedChange?.invoke(value)
        }
    }
    Box(modifier = modifier.fillMaxWidth().settingsInteractionGate(interactionEnabled).trackMaterial3ExpressivePress { anchorOffset = it }) {
        Material3ExpressivePreferenceRow(
            title = title,
            summary = summary,
            startAction = startAction,
            bottomAction = bottomAction,
            enabled = actualEnabled,
            role = Role.DropdownList,
            onClick = { if (interactionEnabled) setExpanded(!expanded) },
            endActions = {
                val selectedText = entry.items.firstOrNull { it.selected }?.text
                if (showValue && !selectedText.isNullOrEmpty()) {
                    MaterialText(
                        text = selectedText,
                        modifier = Modifier.fillMaxWidth(0.3f),
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
        )
        Material3ExpressiveOffsetMenu(
            expanded = expanded,
            onDismissRequest = { setExpanded(false) },
            anchorOffset = anchorOffset,
            maxHeight = maxHeight,
        ) {
            entry.items.forEachIndexed { index, item ->
                key(index, item.text) {
                    SelectableDropdownMenuItem(
                        modifier = Modifier.settingsInteractionGate(interactionEnabled),
                        text = {
                            MaterialText(
                                text = item.text,
                            )
                        },
                        selected = item.selected,
                        enabled = item.enabled,
                        onClick = {
                            if (interactionEnabled && actualEnabled && item.enabled) {
                                item.onClick?.invoke()
                                if (collapseOnSelection) setExpanded(false)
                            }
                        },
                        shapes = MenuDefaults.itemShape(index = index, count = entry.items.size),
                        selectedLeadingIcon = {
                            MaterialIcon(
                                imageVector = Icons.Filled.FilledCheck,
                                contentDescription = null,
                                modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                            )
                        },
                    )
                }
            }
        }
    }
}

private fun Modifier.trackMaterial3ExpressivePress(onPress: (IntOffset) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            onPress(down.position.round())
        }
    }

@Composable
private fun Material3ExpressiveOffsetMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchorOffset: IntOffset,
    maxHeight: Dp?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(Constraints())
            layout(if (constraints.hasBoundedWidth) constraints.maxWidth else 0, 0) {
                placeable.place(anchorOffset.x, anchorOffset.y)
            }
        },
    ) {
        DropdownMenuPopup(expanded = expanded, onDismissRequest = onDismissRequest) {
            DropdownMenuGroup(
                shapes = MenuDefaults.groupShape(index = 0, count = 1),
                modifier = (if (maxHeight != null) Modifier.heightIn(max = maxHeight) else Modifier)
                    .verticalScroll(rememberScrollState()),
                content = content,
            )
        }
    }
}

@Composable
internal fun SettingsWindowDropdownPreference(
    entry: DropdownEntry,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    dropdownColors: DropdownColors = DropdownDefaults.dropdownColors(),
    startAction: @Composable (() -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    maxHeight: Dp? = null,
    enabled: Boolean = true,
    showValue: Boolean = true,
    collapseOnSelection: Boolean = true,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    interactionEnabled: Boolean = true,
) {
    if (LocalAppUiStyle.current == AppUiStyle.Miuix) {
        MiuixDropdownPreference(
            entry = entry, title = title, modifier = modifier, summary = summary, startAction = startAction,
            bottomAction = bottomAction, maxHeight = maxHeight, enabled = enabled,
            showValue = showValue, collapseOnSelection = collapseOnSelection, onExpandedChange = onExpandedChange,
            interactionEnabled = interactionEnabled,
        )
        return
    }
    if (LocalAppUiStyle.current == AppUiStyle.Material3Expressive) {
        Material3ExpressiveDropdownPreference(
            entry = entry,
            title = title,
            modifier = modifier,
            summary = summary,
            startAction = startAction,
            bottomAction = bottomAction,
            maxHeight = maxHeight,
            enabled = enabled,
            showValue = showValue,
            collapseOnSelection = collapseOnSelection,
            onExpandedChange = onExpandedChange,
            interactionEnabled = interactionEnabled,
        )
        return
    }
    ColorOsWindowDropdownPreference(
        entry = entry,
        title = title,
        modifier = modifier,
        summary = summary,
        dropdownColors = dropdownColors,
        startAction = startAction,
        bottomAction = bottomAction,
        maxHeight = maxHeight,
        enabled = enabled,
        showValue = showValue,
        collapseOnSelection = collapseOnSelection,
        onExpandedChange = onExpandedChange,
        interactionEnabled = interactionEnabled,
    )
}
