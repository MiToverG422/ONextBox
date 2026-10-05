package com.mi.onextbox.ui.settings

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mi.onextbox.ui.common.AppUiStyle
import com.mi.onextbox.ui.common.LocalAppUiStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon as MaterialIcon
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.theme.COUITheme

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
) {
    val row: @Composable () -> Unit = { SettingsSwitchPreference(
        modifier = modifier,
        checked = checked,
        onCheckedChange = onCheckedChange,
        title = title,
        summary = summary.takeIf { it.isNotBlank() },
        startAction = icon?.let { imageVector ->
            {
                if (LocalAppUiStyle.current == AppUiStyle.Material3Expressive) {
                    MaterialIcon(
                        imageVector = imageVector,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                } else {
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
    ) }
    if (LocalAppUiStyle.current == AppUiStyle.Material3Expressive) {
        Material3ExpressiveInferredSegmentPosition(hasDividerAbove, hasDividerBelow, row)
    } else {
        row()
    }
}
