package com.mi.onextbox.ui.settings

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * [enabled] and [switchEnabled] represent real availability (and may dim the controls).
 * Use [interactionEnabled] for transient preference writes to lock input without flashing colors.
 */
@Composable
fun SettingsToggleRow(
    modifier: Modifier = Modifier,
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    hasDividerAbove: Boolean = false,
    hasDividerBelow: Boolean = false,
    enabled: Boolean = true,
    bottomAction: (@Composable () -> Unit)? = null,
    hiddenHoldDurationMillis: Long? = null,
    onHiddenHold: (() -> Unit)? = null,
    onRowClick: (() -> Unit)? = null,
    switchEnabled: Boolean = enabled,
    interactionEnabled: Boolean = true,
) {
    val row: @Composable () -> Unit = { SettingsSwitchPreference(
        modifier = modifier,
        checked = checked,
        onCheckedChange = onCheckedChange,
        title = title,
        summary = summary.takeIf { it.isNotBlank() },
        startAction = icon?.let { imageVector ->
            {
                run {
                    Icon(
                        imageVector = imageVector,
                        contentDescription = null,
                        tint = COUITheme.colorScheme.onSurface,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        },
        enabled = enabled,
        bottomAction = bottomAction,
        hiddenHoldDurationMillis = hiddenHoldDurationMillis,
        onHiddenHold = onHiddenHold,
        onRowClick = onRowClick,
        switchEnabled = switchEnabled,
        interactionEnabled = interactionEnabled,
    ) }
    run {
        row()
    }
}
