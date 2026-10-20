package com.mi.onextbox.ui.settings

import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.lsp.SettingsEntryIconStyle
import com.mi.onextbox.lsp.SettingsEntryPosition
import com.mi.onextbox.lsp.SettingsHomeEntryOptions
import com.mi.onextbox.lsp.SettingsHomeEntrySettings
import com.mi.onextbox.ui.screens.FeatureAnimatedGroup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Shared by all UI styles in About > Settings. */
@Composable
internal fun SettingsHomeEntrySettingsGroup() {
    val app = LocalContext.current.applicationContext
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var options by remember(app) { mutableStateOf(SettingsHomeEntrySettings.options(app)) }
    var iconHidden by remember(app) { mutableStateOf(ModuleLauncherIcon.hidden(app)) }
    var saving by remember { mutableStateOf(false) }
    DisposableEffect(app, owner) {
        val prefs = LspPreferenceStore.prefs(app)
        fun refresh() {
            if (!saving) {
                options = SettingsHomeEntrySettings.options(app)
                iconHidden = ModuleLauncherIcon.hidden(app)
            }
        }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key in SettingsHomeEntrySettings.keys) refresh()
        }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        owner.lifecycle.addObserver(observer)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            owner.lifecycle.removeObserver(observer)
        }
    }
    fun save(next: SettingsHomeEntryOptions, write: () -> Unit) {
        if (saving) return
        saving = true
        options = next
        val pending = FeatureSettingWriteQueue.application.enqueue(write)
        scope.launch {
            try { pending.await() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { Toast.makeText(app, R.string.setting_settings_home_entry_save_failed, Toast.LENGTH_LONG).show() }
            finally { options = SettingsHomeEntrySettings.options(app); saving = false }
        }
    }
    val positions = listOf(
        stringResource(R.string.setting_settings_home_entry_disabled),
        stringResource(R.string.setting_settings_home_entry_top),
        stringResource(R.string.setting_settings_home_entry_middle),
        stringResource(R.string.setting_settings_home_entry_bottom),
    )
    val styles = listOf(
        stringResource(R.string.setting_settings_home_entry_style_system),
        stringResource(R.string.setting_settings_home_entry_style_app),
    )
    SettingsSection(title = stringResource(R.string.settings_section_system_entry))
    FeatureAnimatedGroup(animateChanges = true) {
        row("position") { _, _ ->
            SettingsWindowDropdownPreference(
                title = stringResource(R.string.setting_settings_home_entry),
                summary = stringResource(R.string.setting_settings_home_entry_summary),
                items = positions,
                selectedIndex = options.position.ordinal,
                interactionEnabled = !saving,
                onSelectedIndexChange = { index ->
                    SettingsEntryPosition.entries.getOrNull(index)?.let { selected ->
                        save(options.copy(position = selected)) { SettingsHomeEntrySettings.setPosition(app, selected) }
                    }
                },
            )
        }
        row("icon-style", visible = options.enabled) { _, _ ->
            SettingsWindowDropdownPreference(
                title = stringResource(R.string.setting_settings_home_entry_style),
                items = styles,
                selectedIndex = options.iconStyle.ordinal,
                interactionEnabled = !saving,
                onSelectedIndexChange = { index ->
                    SettingsEntryIconStyle.entries.getOrNull(index)?.let { selected ->
                        save(options.copy(iconStyle = selected)) { SettingsHomeEntrySettings.setIconStyle(app, selected) }
                    }
                },
            )
        }
        row("hide-launcher-icon") { above, below ->
            SettingsToggleRow(
                title = stringResource(R.string.setting_hide_module_icon),
                summary = "",
                checked = iconHidden,
                interactionEnabled = !saving,
                onCheckedChange = { hidden ->
                    if (!saving) {
                        saving = true
                        val pending = FeatureSettingWriteQueue.application.enqueue {
                            ModuleLauncherIcon.setHidden(app, hidden)
                        }
                        scope.launch {
                            try { pending.await() }
                            catch (cancel: CancellationException) { throw cancel }
                            catch (_: Exception) {
                                Toast.makeText(app, R.string.setting_hide_module_icon_failed, Toast.LENGTH_LONG).show()
                            } finally {
                                iconHidden = ModuleLauncherIcon.hidden(app)
                                options = SettingsHomeEntrySettings.options(app)
                                saving = false
                            }
                        }
                    }
                },
                hasDividerAbove = above,
                hasDividerBelow = below,
            )
        }
    }
}
