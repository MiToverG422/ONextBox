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
internal fun QuickAppServicesFeaturesPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expressNoMiniProgram by rememberSaveable {
        mutableStateOf(LspConfig.isExpressNoMiniProgramEnabled(context))
    }

    SettingsSection(title = stringResource(R.string.feature_group_express_service))
    SettingsGroup {
        SettingsToggleRow(
            title = stringResource(R.string.feature_express_no_mini_program_title),
            summary = stringResource(R.string.feature_express_no_mini_program_summary),
            checked = expressNoMiniProgram,
            onCheckedChange = { enabled ->
                expressNoMiniProgram = enabled
                scope.launch {
                    withContext(Dispatchers.IO) {
                        LspConfig.setExpressNoMiniProgramEnabled(context, enabled)
                    }
                }
            },
        )
    }
}
