package com.mi.onextbox.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.mi.onextbox.R
import com.mi.onextbox.ui.settings.SettingsPageSurface
import io.github.suqi8.coui.kmp.blur.LayerBackdrop

@Composable
fun ToolsMainRoute(
    modifier: Modifier,
    bottomContentPadding: Dp,
    blurBackdrop: LayerBackdrop?,
    scrollResetKey: Any? = null,
    onOpen: (FeaturePageMode) -> Unit,
) {
    SettingsPageSurface(
        title = stringResource(R.string.tab_tools),
        modifier = modifier.fillMaxSize(),
        bottomContentPadding = bottomContentPadding,
        blurBackdrop = blurBackdrop,
        scrollResetKey = scrollResetKey,
    ) {
        ToolsMainPage(onOpen = onOpen)
    }
}
