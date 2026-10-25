package com.mi.onextbox.ui.screens

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
import com.mi.onextbox.lsp.SettingsRealmeFeature
import com.mi.onextbox.lsp.SettingsRealmeFeatures
import com.mi.onextbox.lsp.SettingsRealmeDependencies
import com.mi.onextbox.lsp.SettingsRealmeLaboratory
import com.mi.onextbox.ui.settings.FeatureSettingWriteQueue
import com.mi.onextbox.ui.settings.SettingsSection
import com.mi.onextbox.ui.settings.SettingsToggleRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal val realmeFeatureTitles = listOf(
    SettingsRealmeFeature.ProcessManager to R.string.settings_show_process_manager_title,
    SettingsRealmeFeature.AppVolume to R.string.settings_realme_app_volume,
    SettingsRealmeFeature.DirectionalSound to R.string.settings_realme_directional_sound,
    SettingsRealmeFeature.BrightnessRange to R.string.settings_realme_brightness_range,
    SettingsRealmeFeature.MultipleFaces to R.string.settings_realme_multiple_faces,
    SettingsRealmeFeature.Laboratory to R.string.settings_realme_laboratory,
    SettingsRealmeFeature.DcDimming to R.string.settings_realme_dc_dimming,
    SettingsRealmeFeature.DualMusic to R.string.settings_realme_dual_music,
    SettingsRealmeFeature.SleepCapsule to R.string.settings_realme_sleep_capsule,
    SettingsRealmeFeature.HeartRate to R.string.settings_realme_heart_rate,
)

@Composable
internal fun SettingsRealmePage(availableTitles: Set<Int>? = null) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current
    fun read() = SettingsRealmeFeature.entries.associateWith { SettingsRealmeFeatures.enabled(context, it) }
    var values by remember(context) { mutableStateOf(read()) }
    fun readDependencies() = SettingsRealmeFeature.entries.associateWith { SettingsRealmeDependencies.available(context, it) }
    var dependencies by remember(context) { mutableStateOf(readDependencies()) }
    var saving by remember { mutableStateOf(false) }
    DisposableEffect(context, owner) {
        val prefs = LspPreferenceStore.prefs(context)
        val keys = SettingsRealmeFeature.entries.map { it.key }.toSet()
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (!saving && (key == null || key in keys)) values = read()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                dependencies = readDependencies()
                if (!saving) values = read()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            owner.lifecycle.removeObserver(observer)
        }
    }
    SettingsSection(stringResource(R.string.settings_realme_title))
    FeatureAnimatedGroup(animateChanges = true) {
        realmeFeatureTitles.forEach { (feature, title) ->
            val ready = dependencies[feature] != false
            row(feature.key, visible = (availableTitles == null || title in availableTitles) &&
                (feature !in SettingsRealmeLaboratory.children || values[SettingsRealmeFeature.Laboratory] == true)) { above, below ->
                val dependencySummary = when (feature) {
                    SettingsRealmeFeature.SleepCapsule -> stringResource(R.string.settings_realme_sleep_dependency)
                    SettingsRealmeFeature.HeartRate -> stringResource(R.string.settings_realme_heart_dependency)
                    else -> ""
                }
                val summary = dependencySummary + if (!ready) "\n" + stringResource(R.string.settings_realme_dependency_unavailable) else ""
                SettingsToggleRow(
                    title = stringResource(title), summary = summary,
                    checked = values[feature] == true, interactionEnabled = !saving,
                    enabled = SettingsRealmeDependencies.canChange(values[feature] == true, ready),
                    hasDividerAbove = above, hasDividerBelow = below,
                    onCheckedChange = { next ->
                        if (!saving && (!next || SettingsRealmeDependencies.available(context, feature))) {
                            saving = true
                            values = values + (feature to next)
                            val pending = FeatureSettingWriteQueue.application.enqueue { SettingsRealmeFeatures.set(context, feature, next) }
                            scope.launch {
                                try { pending.await() }
                                catch (cancel: CancellationException) { throw cancel }
                                catch (_: Exception) { Toast.makeText(context, R.string.app_errors_sync_failed, Toast.LENGTH_LONG).show() }
                                finally { saving = false; values = read() }
                            }
                        }
                    },
                )
            }
        }
    }
}
