package com.mi.onextbox.ui.screens

import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mi.onextbox.R
import com.mi.onextbox.lsp.CarrierNameRules
import com.mi.onextbox.lsp.CarrierNameSettings
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.ui.common.AppUiStyle
import com.mi.onextbox.ui.common.CouiConfirmDialog
import com.mi.onextbox.ui.common.LocalAppUiStyle
import com.mi.onextbox.ui.settings.FeatureSettingWriteQueue
import com.mi.onextbox.ui.settings.SettingsCardRow
import com.mi.onextbox.ui.settings.SettingsSection
import com.mi.onextbox.ui.settings.SettingsToggleRow
import com.mi.onextbox.ui.settings.settingsInteractionGate
import io.github.suqi8.coui.kmp.basic.TextField as CouiTextField
import io.github.suqi8.coui.kmp.basic.TextFieldMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun CarrierNameSettingsGroup() {
    val context = LocalContext.current
    val application = context.applicationContext
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(CarrierNameSettings.state(application)) }
    var savingKey by remember { mutableStateOf<String?>(null) }
    var editingSlot by remember { mutableStateOf<Int?>(null) }
    var lastSlot by remember { mutableIntStateOf(0) }
    val input = rememberTextFieldState()
    fun refresh() { if (savingKey == null) state = CarrierNameSettings.state(application) }
    DisposableEffect(owner, application) {
        val prefs = LspPreferenceStore.prefs(application)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key in CarrierNameSettings.keys) refresh()
        }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        owner.lifecycle.addObserver(observer)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener); owner.lifecycle.removeObserver(observer) }
    }
    fun save(key: String, next: CarrierNameSettings.State, closeEditor: Boolean = false, write: () -> Unit) {
        if (savingKey != null) return
        savingKey = key
        state = next
        val pending = FeatureSettingWriteQueue.application.enqueue(write)
        scope.launch {
            try { pending.await(); if (closeEditor) editingSlot = null }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { Toast.makeText(context, R.string.enabled_features_save_failed, Toast.LENGTH_LONG).show() }
            finally { savingKey = null; refresh() }
        }
    }
    SettingsSection(title = stringResource(R.string.carrier_name_group))
    FeatureAnimatedGroup(animateChanges = true) {
        row(CarrierNameSettings.ENABLED) { above, below ->
            SettingsToggleRow(title = stringResource(R.string.carrier_name_title), summary = stringResource(R.string.carrier_name_summary),
                checked = state.enabled, interactionEnabled = savingKey != CarrierNameSettings.ENABLED,
                hasDividerAbove = above, hasDividerBelow = below,
                onCheckedChange = { enabled -> save(CarrierNameSettings.ENABLED, state.copy(enabled = enabled)) {
                    CarrierNameSettings.setEnabled(application, enabled)
                } })
        }
        listOf(0, 1).forEach { slot ->
            val key = if (slot == 0) CarrierNameSettings.SIM1 else CarrierNameSettings.SIM2
            row(key, visible = state.enabled) { above, below ->
                SettingsCardRow(title = stringResource(if (slot == 0) R.string.carrier_name_sim1 else R.string.carrier_name_sim2),
                    summary = state.name(slot).ifEmpty { stringResource(R.string.carrier_name_original) },
                    hasDividerAbove = above, hasDividerBelow = below, showArrow = true,
                    onClick = {
                        if (savingKey == null) {
                            input.edit { replace(0, length, state.name(slot)) }
                            lastSlot = slot
                            editingSlot = slot
                        }
                    })
            }
        }
        row("carrier-reset", visible = state.enabled) { above, below ->
            SettingsCardRow(title = stringResource(R.string.carrier_name_reset), summary = "",
                hasDividerAbove = above, hasDividerBelow = below,
                onClick = { save("carrier-reset", state.copy(sim1 = "", sim2 = "")) { CarrierNameSettings.resetNames(application) } })
        }
    }
    val limiter = remember { InputTransformation {
        val text = asCharSequence().toString()
        if (text.codePointCount(0, text.length) > CarrierNameRules.MAX_CODE_POINTS) {
            replace(text.offsetByCodePoints(0, CarrierNameRules.MAX_CODE_POINTS), length, "")
        }
    } }
    CouiConfirmDialog(
        show = editingSlot != null,
        title = stringResource(if (lastSlot == 0) R.string.carrier_name_sim1 else R.string.carrier_name_sim2),
        summary = stringResource(R.string.carrier_name_hint, CarrierNameRules.MAX_CODE_POINTS),
        negativeText = stringResource(android.R.string.cancel), positiveText = stringResource(android.R.string.ok),
        onDismissRequest = { editingSlot = null },
        onPositive = {
            val slot = editingSlot
            if (slot != null && savingKey == null) {
                val value = CarrierNameRules.normalize(input.text.toString())
                val next = if (slot == 0) state.copy(sim1 = value) else state.copy(sim2 = value)
                save(if (slot == 0) CarrierNameSettings.SIM1 else CarrierNameSettings.SIM2, next, closeEditor = true) {
                    CarrierNameSettings.setName(application, slot, value)
                }
            }
        },
        content = {
            val placeholder = stringResource(R.string.carrier_name_original)
            if (LocalAppUiStyle.current == AppUiStyle.Material3Expressive) {
                OutlinedTextField(state = input, modifier = Modifier.fillMaxWidth().settingsInteractionGate(savingKey == null), inputTransformation = limiter,
                    readOnly = savingKey != null, lineLimits = TextFieldLineLimits.SingleLine,
                    placeholder = { Text(placeholder) })
            } else {
                CouiTextField(state = input, modifier = Modifier.fillMaxWidth().settingsInteractionGate(savingKey == null), backgroundMode = TextFieldMode.Line,
                    label = placeholder, readOnly = savingKey != null, inputTransformation = limiter,
                    lineLimits = TextFieldLineLimits.SingleLine, showClearButton = true)
            }
        },
    )
}
