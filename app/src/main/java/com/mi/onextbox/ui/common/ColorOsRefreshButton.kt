package com.mi.onextbox.ui.common

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
internal fun ColorOsRefreshButton(refreshing: Boolean, label: String, onClick: () -> Unit) {
    ColorOsTopBarButton(
        onClick = { if (!refreshing) onClick() },
        modifier = Modifier.semantics {
            contentDescription = label
            if (refreshing) disabled()
        },
    ) {
        Crossfade(refreshing, animationSpec = tween(180), label = "refreshAction") { busy ->
            if (busy) CircularProgressIndicator(size = 24.dp)
            else Icon(Icons.Rounded.Refresh, null, tint = COUITheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
        }
    }
}
