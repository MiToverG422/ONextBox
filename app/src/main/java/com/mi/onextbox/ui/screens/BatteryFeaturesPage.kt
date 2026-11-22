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
    SystemExtrasGroups("com.oplus.battery")
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
        R.string.battery_information_group to listOf(
            BatteryFeature.ShowCycleCount, BatteryFeature.ShowHealthStatus,
            BatteryFeature.ShowFullChargeCapacity, BatteryFeature.ShowRatedCapacity,
            BatteryFeature.ShowTypicalCapacity,
            BatteryFeature.ShowCalculatedHealth,
        ),
        R.string.battery_optimization_group to listOf(
            BatteryFeature.RemoveRestrictPlugin, BatteryFeature.RestoreDefaultWhitelist, BatteryFeature.BlockWhitelistRestore,
        ),
    )
    groups.forEach { (title, features) ->
        SettingsSection(title = stringResource(title))
        SettingsGroup {
            if (title == R.string.battery_optimization_group) {
                LowBatteryAutoSwitchRow()
                SettingsDivider()
            }
            features.forEachIndexed { index, feature ->
                if (index > 0) SettingsDivider()
                SettingsToggleRow(
                    title = stringResource(when (feature) {
                        BatteryFeature.ShowCycleCount -> R.string.battery_show_cycle_count_title
                        BatteryFeature.ShowHealthStatus -> R.string.battery_show_health_status_title
                        BatteryFeature.ShowFullChargeCapacity -> R.string.battery_show_full_charge_capacity_title
                        BatteryFeature.ShowRatedCapacity -> R.string.battery_show_rated_capacity_title
                        BatteryFeature.ShowTypicalCapacity -> R.string.battery_show_typical_capacity_title
                        BatteryFeature.ShowCalculatedHealth -> R.string.battery_show_calculated_health_title
                        BatteryFeature.RemoveRestrictPlugin -> R.string.battery_remove_restrict_plugin_title
                        BatteryFeature.RestoreDefaultWhitelist -> R.string.battery_restore_default_whitelist_title
                        BatteryFeature.BlockWhitelistRestore -> R.string.battery_block_whitelist_restore_title
                    }),
                    summary = when (feature) {
                        BatteryFeature.RemoveRestrictPlugin -> stringResource(R.string.battery_remove_restrict_plugin_summary)
                        BatteryFeature.RestoreDefaultWhitelist -> stringResource(R.string.battery_restore_default_whitelist_summary)
                        BatteryFeature.BlockWhitelistRestore -> stringResource(R.string.battery_block_whitelist_restore_summary)
                        else -> ""
                    },
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

@Composable
private fun LowBatteryAutoSwitchRow() {
    val key = com.mi.onextbox.lsp.LowBatteryAutoSwitch.KEY
    val context = LocalContext.current.applicationContext
    val editor = rememberCustomization(setOf(key)) { com.mi.onextbox.lsp.LspPreferenceStore.readBoolean(it, key, false) }
    SettingsToggleRow(title = stringResource(R.string.block_low_power_auto_super), summary = "",
            checked = editor.state, interactionEnabled = editor.savingKey == null,
            onCheckedChange = { enabled -> editor.save(key, enabled) {
                com.mi.onextbox.lsp.ModernXposedPreferenceSync.setBooleanAndSync(context, key, enabled)
            } })
}
