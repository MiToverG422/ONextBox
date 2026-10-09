package com.mi.onextbox.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspConfig
import com.mi.onextbox.lsp.LspConfig.StatusBarInteractionFeature
import com.mi.onextbox.ui.settings.SettingsDivider
import com.mi.onextbox.ui.settings.SettingsToggleRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Composable
internal fun StatusBarInteractionSettingsRows() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val writeLock = remember { Mutex() }
    val states = remember(context) {
        mutableStateMapOf<StatusBarInteractionFeature, Boolean>().apply {
            StatusBarInteractionFeature.entries.forEach {
                put(it, LspConfig.isStatusBarInteractionFeatureEnabled(context, it))
            }
        }
    }
    DisposableEffect(context, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) StatusBarInteractionFeature.entries.forEach {
                states[it] = LspConfig.isStatusBarInteractionFeatureEnabled(context, it)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    StatusBarInteractionFeature.entries.forEachIndexed { index, feature ->
        val title = when (feature) {
            StatusBarInteractionFeature.DoubleTapToTop -> R.string.feature_status_bar_double_tap_to_top_title
            StatusBarInteractionFeature.RemoveToTopWhitelist -> R.string.feature_status_bar_remove_to_top_whitelist_title
        }
        val summary = when (feature) {
            StatusBarInteractionFeature.DoubleTapToTop -> R.string.feature_status_bar_double_tap_to_top_summary
            StatusBarInteractionFeature.RemoveToTopWhitelist -> R.string.feature_status_bar_remove_to_top_whitelist_summary
        }
        SettingsToggleRow(
            title = stringResource(title),
            summary = stringResource(summary),
            checked = states[feature] == true,
            onCheckedChange = { checked ->
                states[feature] = checked
                scope.launch {
                    writeLock.withLock {
                        withContext(Dispatchers.IO) {
                            LspConfig.setStatusBarInteractionFeatureEnabled(context, feature, checked)
                        }
                    }
                }
            },
            hasDividerAbove = index > 0,
            hasDividerBelow = index < StatusBarInteractionFeature.entries.lastIndex,
        )
        if (index < StatusBarInteractionFeature.entries.lastIndex) SettingsDivider()
    }
}
