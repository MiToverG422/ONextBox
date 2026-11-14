package com.mi.onextbox.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mi.onextbox.R
import com.mi.onextbox.lsp.AospVolumeSettings
import com.mi.onextbox.ui.settings.SettingsToggleRow
import com.mi.onextbox.ui.settings.SettingsDivider
import com.mi.onextbox.ui.settings.FeatureSettingWriteQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun AospVolumeSettingsRow() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(AospVolumeSettings.enabled(context)) }
    var scale by remember { mutableFloatStateOf(AospVolumeSettings.scale(context).toFloat()) }
    var hideInQs by remember { mutableStateOf(AospVolumeSettings.hideInQs(context)) }
    var saving by remember { mutableStateOf(false) }
    fun refresh() {
        enabled = AospVolumeSettings.enabled(context)
        scale = AospVolumeSettings.scale(context).toFloat()
        hideInQs = AospVolumeSettings.hideInQs(context)
    }
    fun persist(write: () -> Unit) {
        if (saving) return
        saving = true
        val pending = FeatureSettingWriteQueue.application.enqueue(write)
        scope.launch {
            try { pending.await() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { Toast.makeText(context, R.string.feature_aosp_volume_save_failed, Toast.LENGTH_LONG).show() }
            finally { saving = false; refresh() }
        }
    }
    DisposableEffect(context, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !saving) refresh()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    SettingsToggleRow(title = stringResource(R.string.feature_aosp_volume_title),
        summary = stringResource(R.string.feature_aosp_volume_summary), checked = enabled,
        hasDividerAbove = true, hasDividerBelow = true,
        interactionEnabled = !saving,
        onCheckedChange = { checked ->
            if (!saving) {
                enabled = checked
                persist { AospVolumeSettings.set(context, checked) }
            }
        })
    FeatureExpandableVisibility(enabled) {
        Column {
            SettingsDivider()
            FeatureSliderRow(
                heading = stringResource(R.string.feature_aosp_volume_scale),
                currentValue = stringResource(R.string.feature_aosp_volume_scale_value, scale.roundToInt()),
                value = scale, valueRange = 50f..150f, steps = 19,
                enabled = enabled, interactionEnabled = !saving,
                onValueChange = { scale = it },
                onValueChangeFinished = { val value = scale.roundToInt(); persist { AospVolumeSettings.setScale(context, value) } },
                onReset = { scale = 100f; persist { AospVolumeSettings.setScale(context, 100) } },
            )
            SettingsDivider()
            SettingsToggleRow(
                title = stringResource(R.string.feature_aosp_volume_hide_in_qs),
                summary = stringResource(R.string.feature_aosp_volume_hide_in_qs_summary),
                checked = hideInQs, enabled = enabled, interactionEnabled = !saving,
                onCheckedChange = { checked -> hideInQs = checked; persist { AospVolumeSettings.setHideInQs(context, checked) } },
                hasDividerAbove = true, hasDividerBelow = true,
            )
        }
    }
}
