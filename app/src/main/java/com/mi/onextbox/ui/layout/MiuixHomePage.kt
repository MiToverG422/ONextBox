package com.mi.onextbox.ui.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mi.onextbox.R
import com.mi.onextbox.ui.home.HomePage
import com.mi.onextbox.ui.settings.MiuixSettingsPageSurface

@Composable
internal fun MiuixHomePage(
    modifier: Modifier,
    currentTab: Int,
    rootGranted: Boolean,
    bottomChromePadding: Dp,
    onHomeHeroLongPress: () -> Unit,
    scrollResetKey: Int?,
) {
    MiuixSettingsPageSurface(
        title = stringResource(R.string.app_name), modifier = modifier,
        showBack = false, onBack = null, actions = {},
        bottomContentPadding = bottomChromePadding, bottomChrome = null,
        contentScrollable = true, edgeToEdgeBottom = false,
        externalScrollState = null, scrollResetKey = scrollResetKey, backgroundContent = null,
    ) {
        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (currentTab == 0) HomePage(rootGranted, onHomeHeroLongPress)
        }
    }
}
