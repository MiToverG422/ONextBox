package com.mi.onextbox.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LauncherIosMotionSettings as Motion
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsSection
import com.mi.onextbox.ui.settings.SettingsToggleRow
import kotlin.math.roundToInt

@Composable
internal fun LauncherIosMotionPage() {
    val context = LocalContext.current.applicationContext
    val editor = rememberCustomization(Motion.defaults.keys + Motion.numbers.map { it.key }) { Motion.state(it) }
    val enabled = editor.state.enabled(Motion.ENABLED)
    val genieEnabled = editor.state.enabled(Motion.GENIE)
    val openingEnabled = editor.state.enabled(Motion.OPENING_APPS)
    val closingEnabled = editor.state.enabled(Motion.CLOSING_APPS)

    fun toggle(key: String, value: Boolean) = editor.save(
        key,
        editor.state.copy(switches = editor.state.switches + (key to value)),
    ) { Motion.set(context, key, value) }

    fun number(number: Motion.Number, value: Int) = editor.save(
        number.key,
        editor.state.copy(values = editor.state.values + (number.key to value)),
    ) { Motion.set(context, number, value) }

    @Composable
    fun Switch(key: String, title: Int, summary: Int = 0, above: Boolean = false, below: Boolean = false) {
        SettingsToggleRow(
            title = stringResource(title),
            summary = if (summary == 0) "" else stringResource(summary),
            checked = editor.state.enabled(key),
            enabled = key == Motion.ENABLED || enabled,
            interactionEnabled = editor.savingKey == null,
            onCheckedChange = { toggle(key, it) },
            hasDividerAbove = above,
            hasDividerBelow = below,
        )
    }

    @Composable
    fun Slider(value: Motion.Number, title: Int, above: Boolean, below: Boolean) {
        val selected = editor.state.value(value)
        FeatureSliderRow(
            heading = stringResource(title),
            currentValue = "$selected%",
            value = selected.toFloat(),
            valueRange = value.min.toFloat()..value.max.toFloat(),
            steps = value.max - value.min - 1,
            interactionEnabled = enabled && editor.savingKey == null,
            onValueChange = {
                editor.state = editor.state.copy(values = editor.state.values + (value.key to it.roundToInt()))
            },
            onValueChangeFinished = { number(value, editor.state.value(value)) },
            onReset = { number(value, value.default) },
            hasDividerAbove = above,
            hasDividerBelow = below,
        )
    }

    SettingsGroup {
        Switch(Motion.ENABLED, R.string.launcher_ios_motion_enable, R.string.launcher_ios_motion_summary)
    }

    SettingsSection(title = stringResource(R.string.launcher_ios_shape_group))
    FeatureAnimatedGroup {
        row(Motion.GENIE) { above, below ->
            Switch(Motion.GENIE, R.string.launcher_ios_genie, R.string.launcher_ios_genie_summary, above, below)
        }
        row(Motion.OPENING_APPS, visible = enabled && genieEnabled) { above, below ->
            Switch(Motion.OPENING_APPS, R.string.launcher_ios_opening_apps, above = above, below = below)
        }
        row(Motion.CLOSING_APPS, visible = enabled && genieEnabled) { above, below ->
            Switch(Motion.CLOSING_APPS, R.string.launcher_ios_closing_apps, above = above, below = below)
        }
        row(Motion.bend.key, visible = enabled && genieEnabled && (openingEnabled || closingEnabled)) { above, below ->
            Slider(Motion.bend, R.string.launcher_ios_bend, above, below)
        }
        row(Motion.DROPLET_CORNERS, visible = enabled) { above, below ->
            Switch(
                Motion.DROPLET_CORNERS,
                R.string.launcher_ios_droplet_corners,
                R.string.launcher_ios_droplet_corners_summary,
                above,
                below,
            )
        }
    }

    SettingsSection(title = stringResource(R.string.launcher_ios_motion_apps))
    FeatureAnimatedGroup {
        row(Motion.openSpeed.key) { above, below ->
            Slider(Motion.openSpeed, R.string.launcher_ios_open_speed, above, below)
        }
        row(Motion.closeSpeed.key) { above, below ->
            Slider(Motion.closeSpeed, R.string.launcher_ios_close_speed, above, below)
        }
        row(Motion.BOUNCE) { above, below ->
            Switch(Motion.BOUNCE, R.string.launcher_ios_bounce, R.string.launcher_ios_bounce_summary, above, below)
        }
        row(Motion.bounce.key, visible = enabled && editor.state.enabled(Motion.BOUNCE)) { above, below ->
            Slider(Motion.bounce, R.string.launcher_ios_bounce_strength, above, below)
        }
    }

    SettingsSection(title = stringResource(R.string.launcher_ios_timing_group))
    FeatureAnimatedGroup {
        row(Motion.slow.key) { above, below ->
            Slider(Motion.slow, R.string.launcher_ios_slow_motion, above, below)
        }
    }
}
