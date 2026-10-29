package com.mi.onextbox.ui.screens

import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.lsp.NetworkSpeedSettings
import com.mi.onextbox.ui.settings.FeatureSettingWriteQueue
import com.mi.onextbox.ui.settings.SettingsSection
import com.mi.onextbox.ui.settings.SettingsToggleRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun NetworkSpeedSettingsGroup() {
    val context = LocalContext.current
    val app = context.applicationContext
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(NetworkSpeedSettings.state(app)) }
    var saving by remember { mutableStateOf(false) }
    DisposableEffect(app) {
        val prefs = LspPreferenceStore.prefs(app)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (!saving && (key == null || key.startsWith("systemui_network_speed_"))) state = NetworkSpeedSettings.state(app)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    fun save(next: NetworkSpeedSettings.State) {
        if (saving) return
        state = next
        saving = true
        val pending = FeatureSettingWriteQueue.application.enqueue { NetworkSpeedSettings.save(app, next) }
        scope.launch {
            try { pending.await() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { Toast.makeText(context, R.string.enabled_features_save_failed, Toast.LENGTH_LONG).show() }
            finally { saving = false; state = NetworkSpeedSettings.state(app) }
        }
    }
    SettingsSection(title = stringResource(R.string.network_speed_title))
    FeatureAnimatedGroup(animateChanges = true) {
        row("speed-refresh") { above, below ->
            SettingsToggleRow(title = stringResource(R.string.network_speed_refresh_second), summary = "",
                checked = state.customRefresh, interactionEnabled = !saving,
                onCheckedChange = { save(state.copy(customRefresh = it)) }, hasDividerAbove = above, hasDividerBelow = below)
        }
        row("speed-interval", visible = state.customRefresh) { above, below ->
            FeatureSliderRow(heading = stringResource(R.string.network_speed_interval), currentValue = "${state.intervalMs / 1000f} s",
                value = state.intervalMs.toFloat(), valueRange = 500f..10000f, steps = 18, interactionEnabled = !saving,
                onValueChange = { state = state.copy(intervalMs = it.toInt()) }, onValueChangeFinished = { save(state) },
                onReset = { save(state.copy(intervalMs = 1000)) }, hasDividerAbove = above, hasDividerBelow = below)
        }
        row("speed-hide-low") { above, below ->
            SettingsToggleRow(title = stringResource(R.string.network_speed_hide_low_speed), summary = "",
                checked = state.hideLowSpeed, interactionEnabled = !saving,
                onCheckedChange = { save(state.copy(hideLowSpeed = it)) }, hasDividerAbove = above, hasDividerBelow = below)
        }
        row("speed-low-threshold", visible = state.hideLowSpeed) { above, below ->
            FeatureSliderRow(heading = stringResource(R.string.network_speed_low_speed_threshold),
                currentValue = "${state.lowSpeedThresholdKbps} KB/s", value = state.lowSpeedThresholdKbps.toFloat(),
                valueRange = NetworkSpeedSettings.MIN_THRESHOLD_KBPS.toFloat()..NetworkSpeedSettings.MAX_THRESHOLD_KBPS.toFloat(),
                steps = NetworkSpeedSettings.MAX_THRESHOLD_KBPS - NetworkSpeedSettings.MIN_THRESHOLD_KBPS - 1,
                interactionEnabled = !saving,
                onValueChange = { state = state.copy(lowSpeedThresholdKbps = it.toInt()) },
                onValueChangeFinished = { save(state) },
                onReset = { save(state.copy(lowSpeedThresholdKbps = NetworkSpeedSettings.DEFAULT_THRESHOLD_KBPS)) },
                hasDividerAbove = above, hasDividerBelow = below)
        }
        row("speed-hide-rate") { above, below ->
            SettingsToggleRow(title = stringResource(R.string.network_speed_hide_per_second), summary = "",
                checked = state.hidePerSecond, interactionEnabled = !saving,
                onCheckedChange = { save(state.copy(hidePerSecond = it)) }, hasDividerAbove = above, hasDividerBelow = below)
        }
        row("speed-hide-units") { above, below ->
            SettingsToggleRow(title = stringResource(R.string.network_speed_hide_units), summary = "",
                checked = state.hideUnits, interactionEnabled = !saving,
                onCheckedChange = { save(state.copy(hideUnits = it)) }, hasDividerAbove = above, hasDividerBelow = below)
        }
        row("speed-single-line") { above, below ->
            SettingsToggleRow(title = stringResource(R.string.network_speed_single_line),
                summary = "",
                checked = state.singleLine, interactionEnabled = !saving,
                onCheckedChange = { save(state.copy(singleLine = it)) }, hasDividerAbove = above, hasDividerBelow = below)
        }
        row("speed-font-weight") { above, below ->
            SettingsToggleRow(title = stringResource(R.string.network_speed_follow_font_weight), summary = "",
                checked = state.followFontWeight, interactionEnabled = !saving,
                onCheckedChange = { save(state.copy(followFontWeight = it)) }, hasDividerAbove = above, hasDividerBelow = below)
        }
        row("speed-font-size") { above, below ->
            SettingsToggleRow(title = stringResource(R.string.network_speed_custom_font_size), summary = "",
                checked = state.customFontSize, interactionEnabled = !saving,
                onCheckedChange = { save(state.copy(customFontSize = it)) }, hasDividerAbove = above, hasDividerBelow = below)
        }
        row("speed-font-slider", visible = state.customFontSize) { above, below ->
            FeatureSliderRow(heading = stringResource(R.string.network_speed_font_size), currentValue = "${state.fontSizeDp} dp",
                value = state.fontSizeDp.toFloat(), valueRange = 2f..14f, steps = 11, interactionEnabled = !saving,
                onValueChange = { state = state.copy(fontSizeDp = it.toInt()) }, onValueChangeFinished = { save(state) },
                onReset = { save(state.copy(fontSizeDp = 9)) }, hasDividerAbove = above, hasDividerBelow = below)
        }
    }
}
