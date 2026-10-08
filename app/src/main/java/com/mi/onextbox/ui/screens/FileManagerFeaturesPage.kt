package com.mi.onextbox.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspConfig
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsSection
import com.mi.onextbox.ui.settings.SettingsToggleRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Composable
internal fun FileManagerFeaturesPage() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val writeLock = remember { Mutex() }
    var hideSecureAccessTip by remember(context) {
        mutableStateOf(LspConfig.isFileManagerHideSecureAccessTipEnabled(context))
    }
    var nativePicker by remember(context) {
        mutableStateOf(LspConfig.isFileManagerNativePickerEnabled(context))
    }
    DisposableEffect(context, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hideSecureAccessTip = LspConfig.isFileManagerHideSecureAccessTipEnabled(context)
                nativePicker = LspConfig.isFileManagerNativePickerEnabled(context)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    SettingsSection(title = stringResource(R.string.feature_file_manager_ui_group))
    SettingsGroup {
        SettingsToggleRow(
            title = stringResource(R.string.feature_file_manager_hide_secure_access_tip_title),
            summary = stringResource(R.string.feature_file_manager_hide_secure_access_tip_summary),
            checked = hideSecureAccessTip,
            onCheckedChange = { checked ->
                hideSecureAccessTip = checked
                scope.launch {
                    writeLock.withLock {
                        withContext(Dispatchers.IO) {
                            LspConfig.setFileManagerHideSecureAccessTipEnabled(context, checked)
                        }
                    }
                }
            },
        )
    }
    SettingsSection(title = stringResource(R.string.feature_file_manager_picker_group))
    SettingsGroup {
        SettingsToggleRow(
            title = stringResource(R.string.feature_file_manager_native_picker_title),
            summary = stringResource(R.string.feature_file_manager_native_picker_summary),
            checked = nativePicker,
            onCheckedChange = { checked ->
                nativePicker = checked
                scope.launch {
                    writeLock.withLock {
                        withContext(Dispatchers.IO) {
                            LspConfig.setFileManagerNativePickerEnabled(context, checked)
                        }
                    }
                }
            },
        )
    }
}
