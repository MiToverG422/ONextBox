package com.mi.onextbox.ui.screens

import androidx.annotation.StringRes
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
import com.mi.onextbox.lsp.LspConfig.TrafficFeature
import com.mi.onextbox.ui.settings.SettingsDivider
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsSection
import com.mi.onextbox.ui.settings.SettingsToggleRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal object TrafficFeatureItems {
    data class Item(val feature: TrafficFeature, @param:StringRes val title: Int, @param:StringRes val summary: Int)

    val groups = listOf(
        R.string.traffic_network_group to listOf(
            Item(TrafficFeature.GoogleNetworkControl, R.string.traffic_google_title, R.string.traffic_google_summary),
            Item(TrafficFeature.OtaNetworkControl, R.string.traffic_ota_title, R.string.traffic_ota_summary),
            Item(TrafficFeature.ShowPreinstalledApps, R.string.traffic_preinstalled_title, R.string.traffic_preinstalled_summary),
            Item(TrafficFeature.ShowHiddenControls, R.string.traffic_hidden_title, R.string.traffic_hidden_summary),
        ),
        R.string.traffic_rules_group to listOf(
            Item(TrafficFeature.BlockCloudNetworkRules, R.string.traffic_cloud_title, R.string.traffic_cloud_summary),
        ),
        R.string.traffic_data_group to listOf(
            Item(TrafficFeature.RoamingBackgroundMode, R.string.traffic_roaming_title, R.string.traffic_roaming_summary),
            Item(TrafficFeature.RemoveDefaultLimit, R.string.traffic_limit_title, R.string.traffic_limit_summary),
        ),
    )
    val all = groups.flatMap { it.second }
}

@Composable
internal fun TrafficManagementPage() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val writeLock = remember { Mutex() }
    val states = remember(context) {
        mutableStateMapOf<TrafficFeature, Boolean>().apply {
            TrafficFeature.entries.forEach { put(it, LspConfig.isTrafficFeatureEnabled(context, it)) }
        }
    }
    DisposableEffect(context, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) TrafficFeature.entries.forEach {
                states[it] = LspConfig.isTrafficFeatureEnabled(context, it)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    TrafficFeatureItems.groups.forEach { (title, items) ->
        SettingsSection(title = stringResource(title))
        SettingsGroup {
            items.forEachIndexed { index, item ->
                if (index > 0) SettingsDivider()
                SettingsToggleRow(
                    title = stringResource(item.title),
                    summary = stringResource(item.summary),
                    checked = states[item.feature] == true,
                    onCheckedChange = { enabled ->
                        states[item.feature] = enabled
                        scope.launch {
                            writeLock.withLock {
                                withContext(Dispatchers.IO) {
                                    LspConfig.setTrafficFeatureEnabled(context, item.feature, enabled)
                                }
                            }
                        }
                    },
                    hasDividerAbove = index > 0,
                    hasDividerBelow = index < items.lastIndex,
                )
            }
        }
    }
}
