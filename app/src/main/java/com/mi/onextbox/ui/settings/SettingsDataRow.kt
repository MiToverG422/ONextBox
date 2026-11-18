package com.mi.onextbox.ui.settings

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mi.onextbox.ui.common.AnimatedDataText
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
internal fun SettingsDataRow(
    title: String,
    summary: String,
    onClick: (() -> Unit)? = null,
    refreshing: Boolean? = null,
) {
    BasicComponent(
        insideMargin = SettingsTokens.RowInsideMargin,
        onClick = onClick?.takeUnless { refreshing == true },
        endActions = if (refreshing == null) null else ({
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                androidx.compose.animation.AnimatedVisibility(refreshing, enter = fadeIn(tween(180)), exit = fadeOut(tween(180))) {
                    CircularProgressIndicator(size = 20.dp)
                }
            }
        }),
    ) {
        AnimatedDataText(title, style = COUITheme.textStyles.headline1, fontWeight = FontWeight.Medium,
            color = COUITheme.colorScheme.onSurface)
        AnimatedDataText(summary, modifier = Modifier.padding(top = if (summary.isEmpty()) 0.dp else 2.dp))
    }
}
