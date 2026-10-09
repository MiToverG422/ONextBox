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
import com.mi.onextbox.ui.settings.SettingsToggleRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Composable
internal fun PermanentClockSecondsSettingsRow(hasDividerAbove: Boolean = false) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val writeLock = remember { Mutex() }
    var enabled by remember(context) { mutableStateOf(LspConfig.isPermanentClockSecondsEnabled(context)) }
    DisposableEffect(context, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) enabled = LspConfig.isPermanentClockSecondsEnabled(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    SettingsToggleRow(
        title = stringResource(R.string.feature_permanent_clock_seconds_title),
        summary = stringResource(R.string.feature_permanent_clock_seconds_summary),
        checked = enabled,
        onCheckedChange = { checked ->
            enabled = checked
            scope.launch {
                writeLock.withLock {
                    withContext(Dispatchers.IO) { LspConfig.setPermanentClockSecondsEnabled(context, checked) }
                }
            }
        },
        hasDividerAbove = hasDividerAbove,
    )
}
