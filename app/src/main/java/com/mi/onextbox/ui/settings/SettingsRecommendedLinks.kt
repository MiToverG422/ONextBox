package com.mi.onextbox.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mi.onextbox.R
import io.github.suqi8.coui.kmp.preference.RecommendedItem
import io.github.suqi8.coui.kmp.preference.RecommendedPreference
import io.github.suqi8.coui.kmp.preference.RecommendedPreferenceColors
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
internal fun SettingsRecommendedLinks(items: List<RecommendedItem>) {
    val dark = COUITheme.colorScheme.background.luminance() < 0.5f
    val neutral = if (dark) Color.White else Color.Black
    val textStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        platformStyle = PlatformTextStyle(includeFontPadding = true),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
    RecommendedPreference(
        title = stringResource(R.string.settings_recommended_title),
        items = items,
        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
        cornerRadius = 24.dp,
        colors = RecommendedPreferenceColors(
            containerColor = neutral.copy(alpha = (if (dark) 0x1A else 0x0A) / 255f),
            titleColor = neutral.copy(alpha = 0x8A / 255f),
            itemColor = neutral.copy(alpha = 0xE6 / 255f),
        ),
        titleTextStyle = textStyle.copy(fontSize = 12.sp, lineHeight = 1.em),
        itemTextStyle = textStyle.copy(fontSize = 14.sp, lineHeight = 1.1.em),
    )
}
