package com.mi.onextbox.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LiquidGlassTuning
import com.mi.onextbox.lsp.LiquidGlassTuning.Control
import com.mi.onextbox.lsp.LiquidGlassTuning.Surface
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.lsp.SoftLightGlassSettings
import com.mi.onextbox.ui.settings.SettingsColorPicker
import com.mi.onextbox.ui.settings.SettingsSection
import com.mi.onextbox.ui.settings.SettingsWindowDropdownPreference
import kotlin.math.roundToInt

@Composable
internal fun LiquidGlassPage() {
    val context = LocalContext.current.applicationContext
    val editor = rememberCustomization(LiquidGlassTuning.defaults.keys) { ctx ->
        val prefs = LspPreferenceStore.prefs(ctx)
        LiquidGlassTuning.defaults.mapValues { (key, fallback) -> prefs.getInt(key, fallback) }
    }
    var selected by rememberSaveable { mutableStateOf(Surface.DOCK) }
    val unified = editor.state.getValue(LiquidGlassTuning.UNIFIED) == 1
    val surface = if (unified) Surface.ALL else selected
    val mode = editor.state.getValue(surface.modeKey).coerceIn(0, LiquidGlassTuning.CUSTOM_TINT)
    fun save(key: String, value: Int) = editor.save(key, editor.state + (key to value)) {
        SoftLightGlassSettings.save(context, key, value)
    }
    SettingsSection(title = stringResource(R.string.lg_beta_notice))
    FeatureAnimatedGroup {
        row("adjustment") { _, _ ->
            SettingsWindowDropdownPreference(title = stringResource(R.string.lg_adjustment),
                items = listOf(stringResource(R.string.lg_adjust_all), stringResource(R.string.lg_adjust_separately)),
                selectedIndex = if (unified) 0 else 1, interactionEnabled = editor.savingKey == null,
                onSelectedIndexChange = { save(LiquidGlassTuning.UNIFIED, if (it == 0) 1 else 0) })
        }
        row("surface", !unified) { _, _ ->
            SettingsWindowDropdownPreference(title = stringResource(R.string.lg_surface),
                items = LiquidGlassTuning.surfaces.map { stringResource(it.title) }, selectedIndex = selected.ordinal,
                interactionEnabled = editor.savingKey == null, onSelectedIndexChange = { selected = LiquidGlassTuning.surfaces[it] })
        }
    }
    fun FeatureAnimatedGroupScope.slider(c: Control, visible: Boolean = true) {
        val key = surface.key(c)
        row(c, visible) { above, below ->
            val value = editor.state.getValue(key).coerceIn(c.range)
            FeatureSliderRow(heading = stringResource(c.title),
                currentValue = c.format(value),
                value = value.toFloat(), valueRange = c.range.first.toFloat()..c.range.last.toFloat(),
                steps = 0, interactionEnabled = editor.savingKey == null,
                onValueChange = { editor.state = editor.state + (key to it.roundToInt()) },
                onValueChangeFinished = { save(key, editor.state.getValue(key)) },
                onReset = { save(key, surface.default(c)) }, hasDividerAbove = above, hasDividerBelow = below)
        }
    }
    SettingsSection(title = stringResource(R.string.glass_optics))
    FeatureAnimatedGroup(animateChanges = false) {
        slider(Control.BEZEL)
        slider(Control.BLUR)
        slider(Control.SAMPLING)
        slider(Control.THICKNESS)
        slider(Control.INDEX)
        slider(Control.REFRACTION)
        slider(Control.SPECULAR)
    }
    SettingsSection(title = stringResource(R.string.lg_tint))
    FeatureAnimatedGroup {
        row("tintMode") { _, _ ->
            SettingsWindowDropdownPreference(title = stringResource(R.string.lg_tint),
                items = listOf(R.string.theme_mode_system, R.string.theme_mode_light, R.string.theme_mode_dark,
                    R.string.lg_custom_color).map { stringResource(it) },
                selectedIndex = mode, interactionEnabled = editor.savingKey == null,
                onSelectedIndexChange = { save(surface.modeKey, it) })
        }
        slider(Control.LIGHT, mode == 0 || mode == 1)
        slider(Control.DARK, mode == 0 || mode == 2)
        row("customTint", mode == LiquidGlassTuning.CUSTOM_TINT) { above, below ->
            SettingsColorPicker(title = stringResource(R.string.lg_custom_color),
                selectedColor = editor.state.getValue(surface.colorKey).toLong() and 0xffffffffL,
                onColorChange = { save(surface.colorKey, it?.toInt() ?: LiquidGlassTuning.DEFAULT_COLOR) },
                interactionEnabled = editor.savingKey == null, hasDividerAbove = above, hasDividerBelow = below)
        }
    }
}
