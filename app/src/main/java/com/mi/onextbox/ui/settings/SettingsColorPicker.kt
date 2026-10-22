package com.mi.onextbox.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mi.onextbox.R
import com.mi.onextbox.ui.common.AppUiStyle
import com.mi.onextbox.ui.common.CouiConfirmDialog
import com.mi.onextbox.ui.common.LocalAppUiStyle
import com.mi.onextbox.ui.screens.ColorOsSettingsSliderRow
import io.github.suqi8.coui.kmp.theme.COUITheme

/** Shared library picker with a draft: slider dragging never queues preference writes. */
@Composable
internal fun SettingsColorPicker(
    title: String,
    selectedColor: Long?,
    onColorChange: (Long?) -> Unit,
    interactionEnabled: Boolean = true,
    hasDividerAbove: Boolean = false,
    hasDividerBelow: Boolean = false,
) {
    var showing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(Color(selectedColor ?: 0xFFFFFFFF)) }
    val currentValue = if (selectedColor == null) stringResource(R.string.feature_slider_current_default)
        else stringResource(R.string.feature_slider_current_value, "#%08X".format(selectedColor))
    Column(Modifier.clickable(enabled = interactionEnabled) {
                draft = Color(selectedColor ?: 0xFFFFFFFF)
                showing = true
    }) {
        ColorOsSettingsSliderRow(
            heading = title, title = currentValue, enabled = true,
            interactionEnabled = interactionEnabled,
            hasDividerAbove = hasDividerAbove, hasDividerBelow = hasDividerBelow,
            titleAction = {
                val tint = if (LocalAppUiStyle.current == AppUiStyle.Material3Expressive)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else COUITheme.colorScheme.onSurfaceVariantActions
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    IconButton(onClick = { onColorChange(null) }, enabled = interactionEnabled,
                        modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Restore,
                            contentDescription = stringResource(R.string.feature_slider_reset),
                            tint = tint, modifier = Modifier.size(20.dp))
                    }
                }
            },
        ) { Spacer(Modifier.height(10.dp)) }
    }
    CouiConfirmDialog(
        show = showing, title = title, summary = null,
        negativeText = stringResource(android.R.string.cancel),
        positiveText = stringResource(android.R.string.ok),
        onDismissRequest = { showing = false },
        onPositive = {
            showing = false
            onColorChange(draft.toArgb().toLong() and 0xFFFFFFFFL)
        },
        content = {
            // The dialog body already scrolls and measures custom content with unbounded height.
            // Bound this viewport before adding scrolling so every dialog style can measure it.
            Column(Modifier.fillMaxWidth().heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
                if (LocalAppUiStyle.current == AppUiStyle.Miuix) {
                    top.yukonga.miuix.kmp.basic.ColorPicker(
                        color = draft, onColorChanged = { draft = it }, modifier = Modifier.fillMaxWidth())
                } else {
                    io.github.suqi8.coui.kmp.basic.ColorPicker(
                        color = draft, onColorChanged = { draft = it }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
    )
}
