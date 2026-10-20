package com.mi.onextbox.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.mi.onextbox.R
import com.mi.onextbox.ui.settings.SettingsPageSurface
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsCardRow
import io.github.suqi8.coui.kmp.blur.LayerBackdrop

@Composable
internal fun AdaptationEmptyPage(
    title: String,
    modifier: Modifier,
    bottomContentPadding: Dp,
    blurBackdrop: LayerBackdrop?,
    onBack: (() -> Unit)? = null,
) {
    SettingsPageSurface(
        title = title,
        modifier = modifier,
        bottomContentPadding = bottomContentPadding,
        blurBackdrop = blurBackdrop,
        showBack = onBack != null,
        onBack = onBack,
    ) {
        SettingsGroup {
            SettingsCardRow(title = stringResource(R.string.system_adaptation_no_content), summary = "")
        }
    }
}
