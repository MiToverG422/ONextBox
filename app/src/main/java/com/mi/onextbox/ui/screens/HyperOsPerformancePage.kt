package com.mi.onextbox.ui.screens

import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mi.onextbox.R
import com.mi.onextbox.lsp.HyperOsPerformanceSettings
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.lsp.hyperos.Force185
import com.mi.onextbox.ui.settings.*
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun HyperOsPerformancePage(
    modifier: Modifier,
    bottomContentPadding: Dp,
    blurBackdrop: LayerBackdrop?,
) {
    val supported = remember { Force185.supported() }
    SettingsPageSurface(
        title = stringResource(R.string.tab_features),
        modifier = modifier,
        bottomContentPadding = bottomContentPadding,
        blurBackdrop = blurBackdrop,
    ) {
        SettingsSection(title = stringResource(R.string.hyperos_performance_group))
        FeatureAnimatedGroup {
            row(HyperOsPerformanceSettings.REFRESH) { above, below ->
                HyperOsPerformanceToggle(
                    setting = HyperOsPerformanceSettings.REFRESH,
                    title = stringResource(R.string.hyperos_refresh_title),
                    supported = supported,
                    hasDividerAbove = above,
                    hasDividerBelow = below,
                )
            }
            row(HyperOsPerformanceSettings.IDLE) { above, below ->
                HyperOsPerformanceToggle(
                    setting = HyperOsPerformanceSettings.IDLE,
                    title = stringResource(R.string.hyperos_idle_title),
                    supported = supported,
                    hasDividerAbove = above,
                    hasDividerBelow = below,
                )
            }
            row(HyperOsPerformanceSettings.TOUCH) { above, below ->
                HyperOsPerformanceToggle(
                    setting = HyperOsPerformanceSettings.TOUCH,
                    title = stringResource(R.string.hyperos_touch_title),
                    supported = supported,
                    hasDividerAbove = above,
                    hasDividerBelow = below,
                )
            }
        }
    }
}

@Composable
private fun HyperOsPerformanceToggle(
    setting: String,
    title: String,
    supported: Boolean,
    hasDividerAbove: Boolean,
    hasDividerBelow: Boolean,
) {
    val app = LocalContext.current.applicationContext
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var enabled by remember(app, setting) { mutableStateOf(HyperOsPerformanceSettings.enabled(app, setting)) }
    var saving by remember(setting) { mutableStateOf(false) }
    DisposableEffect(app, owner, setting) {
        val prefs = LspPreferenceStore.prefs(app)
        fun refresh() { if (!saving) enabled = HyperOsPerformanceSettings.enabled(app, setting) }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == setting) refresh()
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        owner.lifecycle.addObserver(observer)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            owner.lifecycle.removeObserver(observer)
        }
    }
    SettingsToggleRow(
        title = title, summary = "", checked = enabled,
        hasDividerAbove = hasDividerAbove,
        hasDividerBelow = hasDividerBelow,
        switchEnabled = supported || enabled,
        interactionEnabled = !saving,
        onCheckedChange = { next ->
            if (!saving && (!next || supported)) {
                saving = true
                enabled = next
                val pending = FeatureSettingWriteQueue.application.enqueue {
                    HyperOsPerformanceSettings.set(app, setting, next)
                }
                scope.launch {
                    try {
                        pending.await()
                        Toast.makeText(app, R.string.hyperos_reboot_required, Toast.LENGTH_LONG).show()
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (_: Exception) {
                        Toast.makeText(app, R.string.ota_save_failed, Toast.LENGTH_LONG).show()
                    } finally {
                        enabled = HyperOsPerformanceSettings.enabled(app, setting)
                        saving = false
                    }
                }
            }
        },
    )
}
