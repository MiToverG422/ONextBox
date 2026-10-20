package com.mi.onextbox.ui.screens

import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.lsp.PowerIndicatorSettings
import com.mi.onextbox.ui.settings.FeatureSettingWriteQueue
import com.mi.onextbox.ui.settings.SettingsSection
import com.mi.onextbox.ui.settings.SettingsToggleRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun PowerIndicatorSettingsGroup() {
    val context = LocalContext.current
    val app = context.applicationContext
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(PowerIndicatorSettings.state(app)) }
    var saving by remember { mutableStateOf(false) }
    DisposableEffect(app) {
        val prefs = LspPreferenceStore.prefs(app)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (!saving && (key == null || key.startsWith(PowerIndicatorSettings.PREFIX))) state = PowerIndicatorSettings.state(app)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    fun save(next: PowerIndicatorSettings.State) {
        if (saving) return
        state = next
        saving = true
        val pending = FeatureSettingWriteQueue.application.enqueue { PowerIndicatorSettings.save(app, next) }
        scope.launch {
            try { pending.await() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { Toast.makeText(context, R.string.enabled_features_save_failed, Toast.LENGTH_LONG).show() }
            finally { saving = false; state = PowerIndicatorSettings.state(app) }
        }
    }
    SettingsSection(title = stringResource(R.string.power_indicator_title))
    FeatureAnimatedGroup(animateChanges = true) {
        row(PowerIndicatorSettings.ENABLED) { above, below ->
            SettingsToggleRow(title = stringResource(R.string.power_indicator_title),
                summary = stringResource(R.string.power_indicator_summary), checked = state.enabled, interactionEnabled = !saving,
                onCheckedChange = { save(state.copy(enabled = it)) }, hasDividerAbove = above, hasDividerBelow = below)
        }
        row("power-interval", visible = state.enabled) { above, below ->
            FeatureSliderRow(heading = stringResource(R.string.power_indicator_interval), currentValue = "${state.interval / 1000f} s",
                value = state.interval.toFloat(), valueRange = 500f..10000f, steps = 18, interactionEnabled = !saving,
                onValueChange = { state = state.copy(interval = it.toInt()) }, onValueChangeFinished = { save(state) },
                onReset = { save(state.copy(interval = 2000)) }, hasDividerAbove = above, hasDividerBelow = below)
        }
        row("power-decimals", visible = state.enabled) { above, below ->
            val options = listOf(0 to "0 W", 1 to "0.0 W", 2 to "0.00 W")
            OptionDropdownRow(title = stringResource(R.string.power_indicator_precision), summary = "",
                selectedLabel = options[state.decimals].second, options = options, selectedValue = state.decimals,
                onValueChange = { save(state.copy(decimals = it)) }, interactionEnabled = !saving, hasDividerAbove = above, hasDividerBelow = below)
        }
        row("power-scale", visible = state.enabled) { above, below ->
            val options = listOf(0 to stringResource(R.string.power_indicator_scale_original),
                1 to stringResource(R.string.power_indicator_scale_double), 2 to stringResource(R.string.power_indicator_scale_half))
            OptionDropdownRow(title = stringResource(R.string.power_indicator_scale), summary = stringResource(R.string.power_indicator_scale_notice),
                selectedLabel = options[state.scale].second, options = options, selectedValue = state.scale,
                onValueChange = { save(state.copy(scale = it)) }, interactionEnabled = !saving, hasDividerAbove = above, hasDividerBelow = below)
        }
        row("power-source", visible = state.enabled) { above, below ->
            val options = listOf(0 to stringResource(R.string.power_indicator_source_api),
                1 to stringResource(R.string.power_indicator_source_ma), 2 to stringResource(R.string.power_indicator_source_ua))
            OptionDropdownRow(title = stringResource(R.string.power_indicator_source), summary = stringResource(R.string.power_indicator_source_notice),
                selectedLabel = options[state.source].second, options = options, selectedValue = state.source,
                onValueChange = { save(state.copy(source = it)) }, interactionEnabled = !saving, hasDividerAbove = above, hasDividerBelow = below)
        }
        row("power-charging", visible = state.enabled) { above, below ->
            SettingsToggleRow(title = stringResource(R.string.power_indicator_hide_charging),
                summary = stringResource(R.string.power_indicator_charging_notice), checked = state.hideCharging, interactionEnabled = !saving,
                onCheckedChange = { save(state.copy(hideCharging = it)) }, hasDividerAbove = above, hasDividerBelow = below)
        }
        row("power-font", visible = state.enabled) { above, below ->
            FeatureSliderRow(heading = stringResource(R.string.power_indicator_font), currentValue = "${state.fontSize} sp",
                value = state.fontSize.toFloat(), valueRange = 8f..16f, steps = 7, interactionEnabled = !saving,
                onValueChange = { state = state.copy(fontSize = it.toInt()) }, onValueChangeFinished = { save(state) },
                onReset = { save(state.copy(fontSize = 10)) }, hasDividerAbove = above, hasDividerBelow = below)
        }
        row(PowerIndicatorSettings.FOLLOW_FONT_WEIGHT, visible = state.enabled) { above, below ->
            SettingsToggleRow(title = stringResource(R.string.power_indicator_follow_font_weight),
                summary = "", checked = state.followFontWeight, interactionEnabled = !saving,
                onCheckedChange = { save(state.copy(followFontWeight = it)) }, hasDividerAbove = above, hasDividerBelow = below)
        }
        row(PowerIndicatorSettings.HIDE_DISCHARGE_SIGN, visible = state.enabled) { above, below ->
            SettingsToggleRow(title = stringResource(R.string.power_indicator_hide_discharge_sign),
                summary = "", checked = state.hideDischargeSign, interactionEnabled = !saving,
                onCheckedChange = { save(state.copy(hideDischargeSign = it)) }, hasDividerAbove = above, hasDividerBelow = below)
        }
        row(PowerIndicatorSettings.SHOW_TEMPERATURE, visible = state.enabled) { above, below ->
            SettingsToggleRow(title = stringResource(R.string.power_indicator_temperature),
                summary = stringResource(R.string.power_indicator_temperature_summary), checked = state.showTemperature, interactionEnabled = !saving,
                onCheckedChange = { save(state.copy(showTemperature = it)) }, hasDividerAbove = above, hasDividerBelow = below)
        }
        row(PowerIndicatorSettings.TEMPERATURE_UNIT, visible = state.enabled && state.showTemperature) { above, below ->
            val options = listOf(0 to stringResource(R.string.power_indicator_temperature_celsius),
                1 to stringResource(R.string.power_indicator_temperature_fahrenheit))
            OptionDropdownRow(title = stringResource(R.string.power_indicator_temperature_unit), summary = "",
                selectedLabel = options[state.temperatureUnit].second, options = options, selectedValue = state.temperatureUnit,
                onValueChange = { save(state.copy(temperatureUnit = it)) }, interactionEnabled = !saving, hasDividerAbove = above, hasDividerBelow = below)
        }
    }
}
