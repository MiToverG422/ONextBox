package com.mi.onextbox.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mi.onextbox.R
import com.mi.onextbox.ui.settings.SettingsCardRow
import com.mi.onextbox.ui.settings.SettingsDivider
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsTokens
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
internal fun GlobalLocalizationPage(onOpen: (FeaturePageMode) -> Unit) {
    val warningColor = if (COUITheme.colorScheme.background.luminance() < 0.5f) Color(0xFFFF6B64) else Color(0xFFC62828)
    SettingsGroup {
        Column(Modifier.fillMaxWidth().padding(SettingsTokens.RowInsideMargin)) {
            Text(stringResource(R.string.global_localization_warning_title),
                fontSize = COUITheme.textStyles.headline1.fontSize, color = warningColor)
            Text(stringResource(R.string.global_localization_warning_summary),
                modifier = Modifier.padding(top = 2.dp),
                fontSize = COUITheme.textStyles.body2.fontSize, color = warningColor)
        }
    }
    SettingsGroup {
        FeatureSegmentPosition(index = 0, count = 2) {
            SettingsCardRow(title = stringResource(R.string.global_network_title), summary = "",
                showArrow = true, onClick = { onOpen(FeaturePageMode.GlobalNetwork) })
        }
        SettingsDivider()
        FeatureSegmentPosition(index = 1, count = 2) {
            SettingsCardRow(title = stringResource(R.string.global_spoofing_title), summary = "",
                showArrow = true, onClick = { onOpen(FeaturePageMode.GlobalSpoofing) })
        }
    }
}
