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
import com.mi.onextbox.lsp.GoogleMessagesConfig
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsSection
import com.mi.onextbox.ui.settings.SettingsToggleRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Composable
internal fun GoogleMessagesFeaturesPage() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val settingsMutex = remember { Mutex() }
    var gemini by remember {
        mutableStateOf(GoogleMessagesConfig.isEnabled(context, GoogleMessagesConfig.Switch.Gemini))
    }
    var copyOtp by remember {
        mutableStateOf(GoogleMessagesConfig.isEnabled(context, GoogleMessagesConfig.Switch.CopyOtp))
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                gemini = GoogleMessagesConfig.isEnabled(context, GoogleMessagesConfig.Switch.Gemini)
                copyOtp = GoogleMessagesConfig.isEnabled(context, GoogleMessagesConfig.Switch.CopyOtp)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun save(feature: GoogleMessagesConfig.Switch, enabled: Boolean) {
        scope.launch {
            settingsMutex.withLock {
                withContext(Dispatchers.IO) {
                    GoogleMessagesConfig.setEnabled(context, feature, enabled)
                }
            }
        }
    }

    SettingsSection(title = stringResource(R.string.feature_google_messages_chat_section))
    SettingsGroup {
        SettingsToggleRow(
            title = stringResource(R.string.feature_google_messages_gemini_title),
            summary = stringResource(R.string.feature_google_messages_gemini_summary),
            checked = gemini,
            onCheckedChange = {
                gemini = it
                save(GoogleMessagesConfig.Switch.Gemini, it)
            },
        )
    }

    SettingsSection(title = stringResource(R.string.feature_google_messages_notification_section))
    SettingsGroup {
        SettingsToggleRow(
            title = stringResource(R.string.feature_google_messages_copy_otp_title),
            summary = stringResource(R.string.feature_google_messages_copy_otp_summary),
            checked = copyOtp,
            onCheckedChange = {
                copyOtp = it
                save(GoogleMessagesConfig.Switch.CopyOtp, it)
            },
        )
    }
}
