package com.mi.onextbox.ui.screens

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import android.content.Intent
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.lsp.SideKeyAction
import com.mi.onextbox.lsp.SideKeyBinding
import com.mi.onextbox.lsp.SideKeyFeaturePolicy
import com.mi.onextbox.lsp.SideKeyGesture
import com.mi.onextbox.lsp.SideKeyRules
import com.mi.onextbox.lsp.SideKeySettings
import com.mi.onextbox.lsp.SideKeyState
import com.mi.onextbox.ui.settings.SettingsDivider
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsSection
import com.mi.onextbox.ui.settings.SettingsToggleRow
import com.mi.onextbox.ui.settings.SettingsWindowDropdownPreference
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

internal object SideKeyFeatureItems {
    val actions = listOf(
        SideKeyAction.Stock to R.string.side_key_stock,
        SideKeyAction.None to R.string.side_key_none,
        SideKeyAction.WeChatScan to R.string.side_key_wechat_scan,
        SideKeyAction.WeChatPay to R.string.side_key_wechat_pay,
        SideKeyAction.AlipayScan to R.string.side_key_alipay_scan,
        SideKeyAction.AlipayPay to R.string.side_key_alipay_pay,
        SideKeyAction.AlipayTransit to R.string.side_key_alipay_transit,
        SideKeyAction.Flashlight to R.string.side_key_flashlight,
        SideKeyAction.MediaPlayPause to R.string.side_key_media_play_pause,
        SideKeyAction.MediaPrevious to R.string.side_key_media_previous,
        SideKeyAction.MediaNext to R.string.side_key_media_next,
        SideKeyAction.Notifications to R.string.side_key_notifications,
        SideKeyAction.ControlCenter to R.string.side_key_control_center,
        SideKeyAction.Screenshot to R.string.side_key_screenshot,
        SideKeyAction.LockScreen to R.string.side_key_lock_screen,
        SideKeyAction.DoNotDisturb to R.string.side_key_do_not_disturb,
        SideKeyAction.App to R.string.side_key_app,
    )

    fun actionsFor(gesture: SideKeyGesture) = actions.filterNot {
        gesture == SideKeyGesture.Double && it.first == SideKeyAction.Stock
    }
}

private data class SideKeyApp(val packageName: String, val label: String)

@Composable
internal fun SideKeyFeaturesPage() {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val initial = remember(context) { SideKeySettings.state(context) }
    var enabled by remember(context) { mutableStateOf(initial.enabled) }
    var config by remember(context) { mutableStateOf(initial.config) }
    var apps by remember(context) { mutableStateOf<List<SideKeyApp>>(emptyList()) }
    var writing by remember { mutableStateOf(0) }

    fun refreshSavedState() {
        if (writing != 0) return
        val saved = SideKeySettings.state(context)
        enabled = saved.enabled
        config = saved.config
    }

    fun updateBinding(state: SideKeyState, gesture: SideKeyGesture, update: (SideKeyBinding) -> SideKeyBinding): SideKeyState {
        val binding = update(state.config.binding(gesture))
        return state.copy(config = when (gesture) {
            SideKeyGesture.Single -> state.config.copy(single = binding)
            SideKeyGesture.Double -> state.config.copy(double = binding)
            SideKeyGesture.Long -> state.config.copy(long = binding)
        })
    }

    fun persist(change: (SideKeyState) -> SideKeyState) {
        val next = SideKeyFeaturePolicy.normalize(change(SideKeyState(enabled, config)))
        enabled = next.enabled
        config = next.config
        val pending = SideKeySettings.enqueueUpdate(context, change)
        writing++
        scope.launch {
            try {
                pending.await()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                Toast.makeText(context, R.string.side_key_save_failed, Toast.LENGTH_SHORT).show()
            } finally {
                writing--
                refreshSavedState()
            }
        }
    }

    DisposableEffect(context, lifecycleOwner) {
        val preferences = LspPreferenceStore.prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key.startsWith("side_key_")) refreshSavedState()
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshSavedState()
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        lifecycleOwner.lifecycle.addObserver(observer)
        refreshSavedState()
        onDispose {
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    LaunchedEffect(context, locale) {
        apps = withContext(Dispatchers.IO) {
            runCatching {
                val manager = context.packageManager
                val collator = Collator.getInstance(locale)
                manager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                    .filter { it.activityInfo.enabled && it.activityInfo.exported }
                    .map { SideKeyApp(it.activityInfo.packageName, it.loadLabel(manager).toString()) }
                    .distinctBy { it.packageName }
                    .sortedWith { a, b -> collator.compare(a.label, b.label).takeIf { it != 0 }
                        ?: a.packageName.compareTo(b.packageName) }
            }.getOrDefault(emptyList())
        }
    }

    SettingsGroup {
        SettingsToggleRow(
            title = stringResource(R.string.side_key_enable),
            summary = stringResource(R.string.side_key_enable_summary),
            checked = enabled,
            enabled = SideKeyFeaturePolicy.available,
            onCheckedChange = { checked -> persist { it.copy(enabled = checked) } },
        )
    }
    SettingsSection(title = stringResource(R.string.side_key_bindings))
    SettingsGroup {

        SideKeyGesture.entries.forEachIndexed { index, gesture -> key(gesture) {
            val binding = config.binding(gesture)
            val choices = SideKeyFeatureItems.actionsFor(gesture)
            val hasApp = binding.action == SideKeyAction.App
            val selectedTitle = choices.firstOrNull { it.first == binding.action }?.second ?: R.string.side_key_none
            if (index > 0) SettingsDivider()
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                run {SettingsWindowDropdownPreference(
                        title = stringResource(when (gesture) {
                            SideKeyGesture.Single -> R.string.side_key_single
                            SideKeyGesture.Double -> R.string.side_key_double
                            SideKeyGesture.Long -> R.string.side_key_long
                        }),
                        summary = stringResource(selectedTitle),
                        showValue = false,
                        entry = DropdownEntry(items = choices.map { (action, title) ->
                            DropdownItem(text = stringResource(title), selected = action == binding.action,
                                onClick = { persist { state -> updateBinding(state, gesture) { it.copy(action = action) } } })
                        }),
                        maxHeight = 400.dp,
                    )}
                FeatureExpandableVisibility(
                    visible = hasApp,
                ) {
                    Column {
                        SettingsDivider()
                        val installed = if (binding.packageName.isNotEmpty() && apps.none { it.packageName == binding.packageName })
                            listOf(SideKeyApp(binding.packageName, binding.packageName)) + apps else apps
                        val selectedApp = installed.firstOrNull { it.packageName == binding.packageName }
                        val duplicateLabels = remember(installed) {
                            installed.groupingBy { it.label }.eachCount().filterValues { it > 1 }.keys
                        }
                        run {SettingsWindowDropdownPreference(
                                title = selectedApp?.label ?: stringResource(R.string.side_key_select_app),
                                summary = selectedApp?.packageName,
                                showValue = false,
                                enabled = hasApp,
                                entry = DropdownEntry(items = listOf(
                                    DropdownItem(text = stringResource(R.string.side_key_none),
                                        selected = binding.packageName.isEmpty(),
                                        onClick = { persist { state -> updateBinding(state, gesture) { it.copy(packageName = "") } } }),
                                ) + installed.map { app ->
                                    DropdownItem(
                                        text = if (app.label in duplicateLabels) "${app.label} (${app.packageName})" else app.label,
                                        selected = app.packageName == binding.packageName,
                                        onClick = { persist { state -> updateBinding(state, gesture) { it.copy(packageName = app.packageName) } } },
                                    )
                                }),
                                maxHeight = 400.dp,
                            )}
                    }
                }
            }
        } }
    }
    SettingsSection(title = stringResource(R.string.side_key_timing))
    SettingsGroup {
        run {SettingsWindowDropdownPreference(
                title = stringResource(R.string.side_key_long_threshold),
                entry = DropdownEntry(items = SideKeyRules.longIntervals.map { millis ->
                    DropdownItem(text = stringResource(R.string.side_key_milliseconds, millis),
                        selected = millis.toLong() == config.longMillis,
                        onClick = { persist { it.copy(config = it.config.copy(longMillis = millis.toLong())) } })
                }),
            )}
        SettingsDivider()
        run {SettingsWindowDropdownPreference(
                title = stringResource(R.string.side_key_double_interval),
                summary = stringResource(R.string.side_key_double_summary),
                entry = DropdownEntry(items = SideKeyRules.doubleIntervals.map { millis ->
                    DropdownItem(text = stringResource(R.string.side_key_milliseconds, millis),
                        selected = millis.toLong() == config.doubleMillis,
                        onClick = { persist { it.copy(config = it.config.copy(doubleMillis = millis.toLong())) } })
                }),
                enabled = config.double.action != SideKeyAction.None,
            )}
    }
}
