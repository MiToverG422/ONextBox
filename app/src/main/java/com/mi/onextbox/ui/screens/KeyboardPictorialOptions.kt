package com.mi.onextbox.ui.screens

import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.lsp.CustomOption
import com.mi.onextbox.lsp.KeyboardPictorialSettings
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.lsp.ModernXposedPreferenceSync
import com.mi.onextbox.ui.settings.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable internal fun KeyboardPictorialOptions(options: List<CustomOption>, leading: (@Composable () -> Unit)? = null) {
    val context = LocalContext.current
    val prefs = remember(context) { LspPreferenceStore.prefs(context) }
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    fun write(action: () -> Unit) {
        if (saving) return
        saving = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { action() } }
                .onFailure { Toast.makeText(context, it.cause?.message ?: it.message, Toast.LENGTH_LONG).show() }
            saving = false
            revision++
        }
    }
    val values = remember(revision, options) { options.associate { it.key to prefs.getBoolean(it.key, false) } }
    options.groupBy { it.group }.forEach { (group, items) ->
        SettingsSection(stringResource(when (group) {
            "配色" -> R.string.keyboard_colors_group
            "材质" -> R.string.keyboard_material_group
            "工具栏" -> R.string.keyboard_toolbar_group
            "按键" -> R.string.keyboard_keys_group
            "角标" -> R.string.keyboard_secondary_labels_group
            "壁纸下载" -> R.string.pictorial_download_group
            "轮播壁纸" -> R.string.pictorial_rotation_group
            else -> error("Unknown keyboard or wallpaper group: $group")
        }))
        FeatureAnimatedGroup {
            if (leading != null) row("leading") { _, _ -> leading() }
            items.forEach { option ->
                val enabled = values.getValue(option.key)
                row(option.key) { above, below ->
                    SettingsToggleRow(title = stringResource(option.title), summary = if (option.summary == 0) "" else stringResource(option.summary),
                        checked = enabled, hasDividerAbove = above, hasDividerBelow = below,
                        onCheckedChange = { value -> write { KeyboardPictorialSettings.set(context, option.key, value) } })
                }
                if (option.max > option.min) row("${option.key}:slider", visible = enabled) { above, below ->
                    var value by remember(revision, option.key) {
                        mutableFloatStateOf(prefs.getInt("${option.key}_value", option.default).coerceIn(option.min, option.max).toFloat())
                    }
                    FeatureSliderRow(heading = "", currentValue = "${value.roundToInt()}${option.unit}", value = value,
                        valueRange = option.min.toFloat()..option.max.toFloat(), steps = option.max - option.min - 1,
                        hasDividerAbove = above, hasDividerBelow = below, interactionEnabled = !saving,
                        onValueChange = { value = it }, onValueChangeFinished = {
                            val selected = value.roundToInt()
                            write { ModernXposedPreferenceSync.setIntAndSync(context, "${option.key}_value", selected) }
                        }, onReset = { write { ModernXposedPreferenceSync.setIntAndSync(context, "${option.key}_value", option.default) } })
                }
            }
        }
    }
}
