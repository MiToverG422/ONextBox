package com.mi.onextbox.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.lsp.BackGestureSettings
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.ui.settings.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun BackGestureFeaturesPage(areaPage: Boolean, onOpenArea: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(BackGestureSettings.state(app)) }
    var saving by remember { mutableStateOf(false) }
    DisposableEffect(app) {
        val prefs = LspPreferenceStore.prefs(app)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (!saving && key in BackGestureSettings.keys) state = BackGestureSettings.state(app)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    fun save(key: String, value: Any) {
        if (saving) return
        saving = true
        val pending = FeatureSettingWriteQueue.application.enqueue {
            if (value is Boolean) BackGestureSettings.set(app, key, value)
            else BackGestureSettings.set(app, key, value as Int)
        }
        scope.launch {
            try { pending.await() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { Toast.makeText(context, R.string.enabled_features_save_failed, Toast.LENGTH_LONG).show() }
            finally { saving = false; state = BackGestureSettings.state(app) }
        }
    }
    if (!areaPage) {
        SettingsSection(title = stringResource(R.string.back_gesture_indicator_group))
        FeatureAnimatedGroup(animateChanges = true) {
            row(BackGestureSettings.STYLE) { above, _ ->
                if (above) SettingsDivider()
                SettingsWindowDropdownPreference(title = stringResource(R.string.back_gesture_style),
                    items = listOf("AOSP", "ColorOS"), selectedIndex = state.selectedStyle,
                    interactionEnabled = !saving, onSelectedIndexChange = { save(BackGestureSettings.STYLE, it) })
            }
        }
        SettingsSection(title = stringResource(R.string.back_gesture_feedback_group))
        FeatureAnimatedGroup(animateChanges = true) {
            row(BackGestureSettings.HAPTICS) { above, _ ->
                if (above) SettingsDivider()
                SettingsWindowDropdownPreference(title = stringResource(R.string.back_gesture_haptics),
                    items = listOf("AOSP", "ColorOS"), selectedIndex = state.selectedHaptics,
                    interactionEnabled = !saving, onSelectedIndexChange = { save(BackGestureSettings.HAPTICS, it) })
            }
            row(BackGestureSettings.RELEASE) { above, below ->
                SettingsToggleRow(title = stringResource(R.string.back_gesture_release),
                    summary = stringResource(R.string.back_gesture_release_summary), checked = state.release,
                    interactionEnabled = !saving, hasDividerAbove = above, hasDividerBelow = below,
                    onCheckedChange = { save(BackGestureSettings.RELEASE, it) })
            }
        }
        SettingsSection(title = stringResource(R.string.back_gesture_animation_group))
        FeatureAnimatedGroup {
            row(BackGestureSettings.PREDICTIVE) { above, _ ->
                if (above) SettingsDivider()
                SettingsWindowDropdownPreference(title = stringResource(R.string.back_gesture_predictive_style),
                    items = listOf("ColorOS", "AOSP"), selectedIndex = state.predictive,
                    interactionEnabled = !saving, onSelectedIndexChange = { save(BackGestureSettings.PREDICTIVE, it) })
            }
        }
        SettingsSection(title = stringResource(R.string.back_gesture_area_group))
        SettingsGroup {
            SettingsCardRow(title = stringResource(R.string.back_gesture_area),
                summary = stringResource(R.string.back_gesture_area_summary), showArrow = true, onClick = onOpenArea)
        }
        return
    }
    FeatureAnimatedGroup(animateChanges = true) {
        row(BackGestureSettings.AREA) { above, below ->
            SettingsToggleRow(title = stringResource(R.string.back_gesture_area_custom),
                summary = stringResource(R.string.back_gesture_area_custom_summary), checked = state.area,
                interactionEnabled = !saving, hasDividerAbove = above, hasDividerBelow = below,
                onCheckedChange = { save(BackGestureSettings.AREA, it) })
        }
    }
    FeatureExpandableVisibility(state.area) {
        Column {
            listOf(true, false).forEach { left ->
                SettingsSection(title = stringResource(if (left) R.string.back_gesture_left else R.string.back_gesture_right))
                FeatureAnimatedGroup(animateChanges = true) {
                    val heightKey = if (left) BackGestureSettings.LEFT_HEIGHT else BackGestureSettings.RIGHT_HEIGHT
                    val positionKey = if (left) BackGestureSettings.LEFT_POSITION else BackGestureSettings.RIGHT_POSITION
                    val height = if (left) state.leftHeight else state.rightHeight
                    val position = if (left) state.leftPosition else state.rightPosition
                    row(heightKey) { above, below ->
                        FeatureSliderRow(heading = stringResource(R.string.back_gesture_area_height), currentValue = "$height%",
                            value = height.toFloat(), valueRange = 0f..100f, steps = 99, interactionEnabled = !saving,
                            hasDividerAbove = above, hasDividerBelow = below,
                            onValueChange = { state = if (left) state.copy(leftHeight = it.toInt()) else state.copy(rightHeight = it.toInt()) },
                            onValueChangeFinished = { save(heightKey, if (left) state.leftHeight else state.rightHeight) },
                            onReset = { save(heightKey, 100) })
                    }
                    row(positionKey) { above, below ->
                        FeatureSliderRow(heading = stringResource(R.string.back_gesture_area_position), currentValue = "$position%",
                            value = position.toFloat(), valueRange = 0f..100f, steps = 99, interactionEnabled = !saving,
                            hasDividerAbove = above, hasDividerBelow = below,
                            onValueChange = { state = if (left) state.copy(leftPosition = it.toInt()) else state.copy(rightPosition = it.toInt()) },
                            onValueChangeFinished = { save(positionKey, if (left) state.leftPosition else state.rightPosition) },
                            onReset = { save(positionKey, 50) })
                    }
                }
            }
        }
    }
}
