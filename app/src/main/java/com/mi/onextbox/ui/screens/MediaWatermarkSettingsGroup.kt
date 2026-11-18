package com.mi.onextbox.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.lsp.MediaWatermarkSettings
import com.mi.onextbox.ui.common.CouiConfirmDialog
import com.mi.onextbox.ui.settings.FeatureSettingWriteQueue
import com.mi.onextbox.ui.settings.SettingsCardRow
import com.mi.onextbox.ui.settings.SettingsDivider
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsToggleRow
import com.mi.onextbox.ui.settings.settingsInteractionGate
import io.github.suqi8.coui.kmp.basic.TextField as CouiTextField
import io.github.suqi8.coui.kmp.basic.TextFieldMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun MediaWatermarkSettingsGroup(camera: Boolean) {
    val context = LocalContext.current
    val app = context.applicationContext
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(MediaWatermarkSettings.state(app)) }
    var saving by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<String?>(null) }
    var lastTitle by remember { mutableIntStateOf(R.string.media_watermark_model) }
    val input = rememberTextFieldState()
    val fields = listOf(MediaWatermarkSettings.MODEL to R.string.media_watermark_model,
        MediaWatermarkSettings.MAKE to R.string.media_watermark_make, MediaWatermarkSettings.AUTHOR to R.string.media_watermark_author,
        MediaWatermarkSettings.LOCATION to R.string.media_watermark_location, MediaWatermarkSettings.TIME to R.string.media_watermark_time)
        .filter { if (camera) it.first != MediaWatermarkSettings.MAKE else it.first != MediaWatermarkSettings.AUTHOR }
    fun save(close: Boolean = false, write: () -> Unit) {
        if (saving) return
        saving = true
        val pending = FeatureSettingWriteQueue.application.enqueue(write)
        scope.launch {
            try { pending.await(); if (close) editing = null }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { Toast.makeText(context, R.string.enabled_features_save_failed, Toast.LENGTH_LONG).show() }
            finally { state = MediaWatermarkSettings.state(app); saving = false }
        }
    }
    SettingsGroup {
        SettingsToggleRow(title = stringResource(R.string.media_watermark_custom), summary = "", checked = state.enabled,
            interactionEnabled = !saving, hasDividerAbove = false, hasDividerBelow = true,
            onCheckedChange = { value -> save { MediaWatermarkSettings.setFlag(app, MediaWatermarkSettings.ENABLED, value) } })
        SettingsDivider()
        fields.forEachIndexed { index, (key, title) ->
            if (index > 0) SettingsDivider()
            SettingsCardRow(title = stringResource(title),
                summary = state.text[key].orEmpty().ifEmpty { stringResource(R.string.media_watermark_default) },
                hasDividerAbove = index > 0, hasDividerBelow = true, showArrow = true,
                onClick = { if (!saving) { input.edit { replace(0, length, state.text[key].orEmpty()) }; lastTitle = title; editing = key } })
        }
        listOf(Triple(MediaWatermarkSettings.HIDE_LOCATION, R.string.media_watermark_hide_location, state.hideLocation),
            Triple(MediaWatermarkSettings.HIDE_TIME, R.string.media_watermark_hide_time, state.hideTime)).forEachIndexed { index, (key, title, checked) ->
            SettingsDivider()
            SettingsToggleRow(title = stringResource(title), summary = "", checked = checked, interactionEnabled = !saving,
                hasDividerAbove = true, hasDividerBelow = index == 0,
                onCheckedChange = { value -> save { MediaWatermarkSettings.setFlag(app, key, value) } })
        }
    }
    val limiter = remember { InputTransformation {
        val text = asCharSequence().toString()
        if (text.codePointCount(0, text.length) > MediaWatermarkSettings.MAX_CODE_POINTS)
            replace(text.offsetByCodePoints(0, MediaWatermarkSettings.MAX_CODE_POINTS), length, "")
    } }
    CouiConfirmDialog(show = editing != null, title = stringResource(lastTitle), summary = "",
        negativeText = stringResource(android.R.string.cancel), positiveText = stringResource(android.R.string.ok),
        onDismissRequest = { editing = null },
        onPositive = { editing?.let { key -> val value = input.text.toString(); save(true) { MediaWatermarkSettings.setText(app, key, value) } } },
        content = { CouiTextField(state = input, modifier = Modifier.fillMaxWidth().settingsInteractionGate(!saving),
            backgroundMode = TextFieldMode.Line, label = stringResource(R.string.media_watermark_default), readOnly = saving,
            inputTransformation = limiter, lineLimits = TextFieldLineLimits.SingleLine, showClearButton = true) })
}
