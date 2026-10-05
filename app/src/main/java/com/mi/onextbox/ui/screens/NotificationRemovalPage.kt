package com.mi.onextbox.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspConfig
import com.mi.onextbox.lsp.LspConfig.NotificationRemovalFeature as Feature
import com.mi.onextbox.ui.settings.Material3ExpressiveSegmentPosition
import com.mi.onextbox.ui.settings.SettingsDivider
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsToggleRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Composable
internal fun NotificationRemovalPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val writeLock = remember { Mutex() }
    val options = listOf(
        Triple(Feature.Overlay, R.string.notification_removal_overlay, 0),
        Triple(Feature.Vpn, R.string.notification_removal_vpn, 0),
        Triple(Feature.DeveloperMode, R.string.notification_removal_developer, 0),
        Triple(Feature.ChargingCompleted, R.string.notification_removal_charging, 0),
        Triple(Feature.Flashlight, R.string.notification_removal_flashlight, 0),
        Triple(Feature.HighBatteryConsumption, R.string.notification_removal_consumption, 0),
        Triple(Feature.HighPerformance, R.string.notification_removal_performance, 0),
        Triple(Feature.DoNotDisturb, R.string.notification_removal_dnd, 0),
        Triple(Feature.HotspotPowerConsumption, R.string.notification_removal_hotspot, 0),
        Triple(Feature.MuteNotifications, R.string.notification_removal_mute, 0),
        Triple(Feature.GtMode, R.string.notification_removal_gt, 0),
    )

    SettingsGroup {
        options.forEachIndexed { index, (feature, title, summary) ->
            var checked by rememberSaveable(feature.key) {
                mutableStateOf(LspConfig.isNotificationRemovalEnabled(context, feature))
            }
            if (index > 0) SettingsDivider()
            Material3ExpressiveSegmentPosition(index = index, count = options.size) {
                SettingsToggleRow(
                    title = stringResource(title),
                    summary = if (summary == 0) "" else stringResource(summary),
                    checked = checked,
                    onCheckedChange = { enabled ->
                        checked = enabled
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                writeLock.withLock { LspConfig.setNotificationRemovalEnabled(context, feature, enabled) }
                            }
                        }
                    },
                    hasDividerAbove = index > 0,
                    hasDividerBelow = index < options.lastIndex,
                )
            }
        }
    }
}
