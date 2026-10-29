@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.mi.onextbox.ui.settings

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mi.onextbox.ui.common.ColorOsSwitch
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.DropdownColors
import io.github.suqi8.coui.kmp.basic.DropdownDefaults
import io.github.suqi8.coui.kmp.basic.DropdownEntry
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
    var switchPressed by remember { mutableStateOf(false) }
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
    val inputModifier = modifier.then(com.mi.onextbox.ui.screens.featureSpotlightModifier(title)).settingsInteractionGate(interactionEnabled)
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
                onPressStateChange = { switchPressed = it },
            )
        },
        bottomAction = bottomAction,
        onClick = rowClick,
        role = if (onRowClick == null) Role.Switch else Role.Button,
        holdDownState = holdDownState || switchPressed,
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


    ColorOsWindowDropdownPreference(
        items = items,
        selectedIndex = selectedIndex,
        title = title,
        modifier = modifier.then(com.mi.onextbox.ui.screens.featureSpotlightModifier(title)),
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


    ColorOsWindowDropdownPreference(
        entry = entry,
        title = title,
        modifier = modifier.then(com.mi.onextbox.ui.screens.featureSpotlightModifier(title)),
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
