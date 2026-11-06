package com.mi.onextbox.ui.home

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mi.onextbox.R
import com.mi.onextbox.ui.common.AppUiTokens
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
internal fun HomeSupportNotice() {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("home_notices", Context.MODE_PRIVATE)
    }
    var dismissed by remember(preferences) {
        mutableStateOf(preferences.getBoolean("coloros_17_only_dismissed", false))
    }
    if (dismissed) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = AppUiTokens.CardCornerRadius,
        insideMargin = PaddingValues(0.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, top = 20.dp, end = 8.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.home_support_notice_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = COUITheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.home_support_notice_summary),
                    fontSize = 14.sp,
                    color = COUITheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            IconButton(
                onClick = {
                    preferences.edit().putBoolean("coloros_17_only_dismissed", true).apply()
                    dismissed = true
                },
                backgroundColor = Color.Transparent,
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.home_support_notice_dismiss),
                    modifier = Modifier.size(20.dp),
                    tint = COUITheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }
    }
}
