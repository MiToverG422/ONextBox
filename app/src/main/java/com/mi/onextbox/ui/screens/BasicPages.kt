package com.mi.onextbox.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mi.onextbox.ui.common.AppUiTokens
import com.mi.onextbox.ui.common.ColorOsSwitch
import com.mi.onextbox.ui.common.rememberHapticClick
import com.mi.onextbox.ui.common.rememberHapticToggle
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
fun Section(title: String) {
    Text(
        text = title,
        style = COUITheme.textStyles.title4,
        color = COUITheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
fun CardRow(
    title: String,
    summary: String,
    icon: ImageVector,
    trailing: String? = null,
    accent: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val hapticClick = rememberHapticClick()

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = AppUiTokens.CardCornerRadius,
        insideMargin = PaddingValues(18.dp),
        onClick = onClick?.let {
            {
                hapticClick()
                it()
            }
        },
        showIndication = true,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (accent) COUITheme.colorScheme.primary else COUITheme.colorScheme.onSurface,
                modifier = Modifier.size(30.dp),
            )
            Spacer(modifier = Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = COUITheme.textStyles.title3,
                    color = COUITheme.colorScheme.onSurface,
                    fontSize = 20.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = summary,
                    style = COUITheme.textStyles.body1,
                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 14.sp,
                )
            }
            trailing?.let {
                Text(
                    text = it,
                    style = COUITheme.textStyles.body1,
                    color = COUITheme.colorScheme.primary,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
fun ToggleRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
) {
    val hapticToggle = rememberHapticToggle()

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = AppUiTokens.CardCornerRadius,
        insideMargin = PaddingValues(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = COUITheme.colorScheme.onSurface,
                modifier = Modifier.size(30.dp),
            )
            Spacer(modifier = Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = COUITheme.textStyles.title3,
                    color = COUITheme.colorScheme.onSurface,
                    fontSize = 20.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = summary,
                    style = COUITheme.textStyles.body1,
                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 14.sp,
                )
            }
            ColorOsSwitch(
                checked = checked,
                onCheckedChange = {
                    hapticToggle()
                    onCheckedChange(it)
                },
            )
        }
    }
}
