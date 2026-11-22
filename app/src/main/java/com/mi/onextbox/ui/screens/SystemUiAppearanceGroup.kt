package com.mi.onextbox.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.lsp.StatusBarIconLayout
import com.mi.onextbox.lsp.SystemUiAppearance as Option
import com.mi.onextbox.ui.settings.SettingsSection
import com.mi.onextbox.ui.settings.SettingsToggleRow
import kotlin.math.roundToInt

private val shadeOptions = listOf(Option.HideSettings, Option.Corners, Option.Radius, Option.Popup, Option.Shade, Option.Lock)

internal val systemUiAppearanceFeatures = listOf(Option.Mirror, Option.Sides, Option.HideSettings, Option.Corners).map { option ->
    val page = if (option == Option.Mirror || option == Option.Sides) FeaturePageMode.SystemUiStatusBarLayout else FeaturePageMode.SystemUiNotificationCenter
    SharedFeatureDefinitions.Toggle(option.title, 0, page, { option.read(it) != 0 },
        { context, enabled -> option.save(context, if (enabled) 1 else 0) }, option.key)
}

@Composable
internal fun SystemUiAppearanceGroup(statusBar: Boolean) {
    val context = LocalContext.current.applicationContext
    val options = if (statusBar) StatusBarIconLayout.options else shadeOptions
    val master = if (statusBar) Option.Sides else Option.Corners
    val keys = if (statusBar) StatusBarIconLayout.keys else options.map { it.key }.toSet()
    val editor = rememberCustomization(keys) { ctx ->
        if (statusBar) options.associateWith(StatusBarIconLayout.read(ctx)::value) else options.associateWith { it.read(ctx) }
    }
    fun save(option: Option, value: Int) = editor.save(option.key, editor.state + (option to value)) {
        option.save(context, value)
    }
    SettingsSection(title = stringResource(if (statusBar) R.string.statusbar_geometry_group else master.title))
    FeatureAnimatedGroup(animateChanges = true) {
        options.forEach { option ->
            row(option.key, visible = option == master || option == Option.HideSettings || editor.state[master] == 1) { above, below ->
                val value = editor.state.getValue(option)
                if (option.flag) {
                    SettingsToggleRow(title = stringResource(option.title), summary = "", checked = value != 0,
                        interactionEnabled = editor.savingKey == null, hasDividerAbove = above, hasDividerBelow = below,
                        onCheckedChange = { save(option, if (it) 1 else 0) })
                } else {
                    val label = when (option) {
                        Option.LeftScale, Option.RightScale -> "$value%"
                        Option.Radius -> "${value / 10}.${value % 10} dp"
                        else -> "$value dp"
                    }
                    FeatureSliderRow(heading = stringResource(option.title), currentValue = label, value = value.toFloat(),
                        valueRange = option.range.first.toFloat()..option.range.last.toFloat(),
                        steps = if (option == Option.Radius) 0 else option.range.last - option.range.first - 1,
                        interactionEnabled = editor.savingKey == null, hasDividerAbove = above, hasDividerBelow = below,
                        onValueChange = { editor.state = editor.state + (option to it.roundToInt()) },
                        onValueChangeFinished = { save(option, editor.state.getValue(option)) },
                        onReset = { save(option, option.default) })
                }
            }
        }
    }
}
