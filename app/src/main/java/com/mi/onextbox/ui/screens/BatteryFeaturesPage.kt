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
import com.mi.onextbox.lsp.LspConfig.BatteryFeature
import com.mi.onextbox.ui.settings.SettingsDivider
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsSection
import com.mi.onextbox.ui.settings.SettingsToggleRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Composable
internal fun BatteryFeaturesPage() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val writeLock = remember { Mutex() }
    val states = remember(context) {
        mutableStateMapOf<BatteryFeature, Boolean>().apply {
            BatteryFeature.entries.forEach { put(it, LspConfig.isBatteryFeatureEnabled(context, it)) }
        }
    }
    DisposableEffect(context, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) BatteryFeature.entries.forEach {
                states[it] = LspConfig.isBatteryFeatureEnabled(context, it)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val groups = listOf(
        R.string.battery_information_group to listOf(BatteryFeature.ShowCycleCount),
        R.string.battery_optimization_group to listOf(
            BatteryFeature.RemoveRestrictPlugin, BatteryFeature.RestoreDefaultWhitelist,
        ),
    )
    groups.forEach { (title, features) ->
        SettingsSection(title = stringResource(title))
        SettingsGroup {
            features.forEachIndexed { index, feature ->
                if (index > 0) SettingsDivider()
                SettingsToggleRow(
                    title = stringResource(when (feature) {
                        BatteryFeature.ShowCycleCount -> R.string.battery_show_cycle_count_title
                        BatteryFeature.RemoveRestrictPlugin -> R.string.battery_remove_restrict_plugin_title
                        BatteryFeature.RestoreDefaultWhitelist -> R.string.battery_restore_default_whitelist_title
                    }),
                    summary = stringResource(when (feature) {
                        BatteryFeature.ShowCycleCount -> R.string.battery_show_cycle_count_summary
                        BatteryFeature.RemoveRestrictPlugin -> R.string.battery_remove_restrict_plugin_summary
                        BatteryFeature.RestoreDefaultWhitelist -> R.string.battery_restore_default_whitelist_summary
                    }),
                    checked = states[feature] == true,
                    onCheckedChange = { enabled ->
                        states[feature] = enabled
                        scope.launch {
                            writeLock.withLock {
                                withContext(Dispatchers.IO) { LspConfig.setBatteryFeatureEnabled(context, feature, enabled) }
                            }
                        }
                    },
                    hasDividerAbove = index > 0,
                    hasDividerBelow = index < features.lastIndex,
                )
            }
        }
    }
}
