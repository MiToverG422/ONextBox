package com.mi.onextbox.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspConfig
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsSection
import com.mi.onextbox.ui.settings.SettingsToggleRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AthenaFeaturesPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var athenaC17SwipeUpProtectionEnabled by rememberSaveable {
        mutableStateOf(LspConfig.isAthenaC17SwipeUpProtectionEnabled(context))
    }

    SettingsSection(title = stringResource(R.string.feature_group_athena_policy))
    SettingsGroup {
        SettingsToggleRow(
            title = stringResource(R.string.feature_athena_c17_swipe_up_protection_title),
            summary = stringResource(R.string.feature_athena_c17_swipe_up_protection_summary),
            checked = athenaC17SwipeUpProtectionEnabled,
            onCheckedChange = { enabled ->
                athenaC17SwipeUpProtectionEnabled = enabled
                scope.launch {
                    withContext(Dispatchers.IO) {
                        LspConfig.setAthenaC17SwipeUpProtectionEnabled(context, enabled)
                    }
                }
            },
        )
    }
}
