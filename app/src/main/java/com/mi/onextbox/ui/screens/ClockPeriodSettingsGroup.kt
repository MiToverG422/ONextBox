package com.mi.onextbox.ui.screens

import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.lsp.ClockPeriodSettings
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.lsp.LspConfig
import com.mi.onextbox.ui.settings.FeatureSettingWriteQueue
import com.mi.onextbox.ui.settings.SettingsToggleRow
import com.mi.onextbox.ui.settings.SettingsWindowDropdownPreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ClockPeriodSettingsGroup() {
    val context = LocalContext.current
    val app = context.applicationContext
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(ClockPeriodSettings.state(app)) }
    var permanent by remember { mutableStateOf(LspConfig.isPermanentClockSecondsEnabled(app)) }
    var saving by remember { mutableStateOf(false) }
    DisposableEffect(app) {
        val prefs = LspPreferenceStore.prefs(app)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            permanent = LspConfig.isPermanentClockSecondsEnabled(app)
            if (!saving && (key == null || key in ClockPeriodSettings.keys)) state = ClockPeriodSettings.state(app)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    fun save(next: ClockPeriodSettings.State) {
        if (saving) return
        state = next
        saving = true
        val pending = FeatureSettingWriteQueue.application.enqueue { ClockPeriodSettings.save(app, next) }
        scope.launch {
            try { pending.await() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { Toast.makeText(context, R.string.enabled_features_save_failed, Toast.LENGTH_LONG).show() }
            finally { saving = false; state = ClockPeriodSettings.state(app) }
        }
    }
    FeatureAnimatedGroup(animateChanges = true) {
        row("clock-seconds") { above, below ->
            PermanentClockSecondsSettingsRow(hasDividerAbove = above, hasDividerBelow = below, onEnabledChange = { permanent = it })
        }
        row("clock-seconds-lock", visible = permanent) { above, below ->
            SettingsToggleRow(title = stringResource(R.string.clock_lock_seconds), summary = "", checked = state.lockSeconds,
                interactionEnabled = !saving, onCheckedChange = { save(state.copy(lockSeconds = it)) },
                hasDividerAbove = above, hasDividerBelow = below)
        }
        row("clock-period") { above, below ->
            SettingsToggleRow(title = stringResource(R.string.clock_period_title), summary = "", checked = state.enabled,
                interactionEnabled = !saving, onCheckedChange = { save(state.copy(enabled = it)) },
                hasDividerAbove = above, hasDividerBelow = below)
        }
        row("clock-period-format", visible = state.enabled) { _, _ ->
            SettingsWindowDropdownPreference(title = stringResource(R.string.clock_period_format),
                items = listOf(stringResource(R.string.clock_period_am_pm), stringResource(R.string.clock_period_detailed)), selectedIndex = state.format, interactionEnabled = !saving,
                onSelectedIndexChange = { save(state.copy(format = it)) })
        }
        row("clock-period-small", visible = state.enabled) { above, below ->
            SettingsToggleRow(title = stringResource(R.string.clock_period_small), summary = "", checked = state.small,
                interactionEnabled = !saving, onCheckedChange = { save(state.copy(small = it)) },
                hasDividerAbove = above, hasDividerBelow = below)
        }
        row("clock-period-shade", visible = state.enabled) { above, below ->
            SettingsToggleRow(title = stringResource(R.string.clock_period_shade), summary = "", checked = state.shade,
                interactionEnabled = !saving, onCheckedChange = { save(state.copy(shade = it)) },
                hasDividerAbove = above, hasDividerBelow = below)
        }
        row("clock-spacing") { above, below ->
            SettingsToggleRow(title = stringResource(R.string.clock_compact_spacing), summary = "", checked = state.compact,
                interactionEnabled = !saving, onCheckedChange = { save(state.copy(compact = it)) },
                hasDividerAbove = above, hasDividerBelow = below)
        }
        row("clock-tabular-digits") { above, below ->
            SettingsToggleRow(title = stringResource(R.string.clock_tabular_digits), summary = "", checked = state.tabularDigits,
                interactionEnabled = !saving, onCheckedChange = { save(state.copy(tabularDigits = it)) },
                hasDividerAbove = above, hasDividerBelow = below)
        }
    }
}
