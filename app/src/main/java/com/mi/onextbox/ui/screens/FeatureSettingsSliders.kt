package com.mi.onextbox.ui.screens

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mi.onextbox.R
import com.mi.onextbox.ui.common.ColorOs17SettingsSlider
import com.mi.onextbox.ui.common.rememberColorOsHapticTick
import com.mi.onextbox.ui.settings.SettingsRowTextContent
import com.mi.onextbox.ui.settings.settingsInteractiveRowHighlight
import com.mi.onextbox.ui.settings.settingsInteractionGate
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.math.roundToInt
import kotlin.math.abs

@Composable
internal fun FeatureSettingsSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    enabled: Boolean,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    interactionEnabled: Boolean = true,
) {
    val inputModifier = modifier.settingsInteractionGate(interactionEnabled)
    val change: (Float) -> Unit = { if (interactionEnabled) onValueChange(it) }
    val finish: (() -> Unit)? = onValueChangeFinished?.let { callback -> { if (interactionEnabled) callback() } }
    run {
        ColorOs17SettingsSlider(
            value = value,
            onValueChange = change,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            modifier = inputModifier,
            onValueChangeFinished = finish,
        )
    }
}

@Composable
internal fun FeatureSliderRow(
    heading: String,
    currentValue: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit,
    onReset: () -> Unit,
    hasDividerAbove: Boolean = true,
    hasDividerBelow: Boolean = false,
    onValueChangeFinished: (() -> Unit)? = null,
    resetDescription: String = stringResource(R.string.feature_slider_reset),
    interactionEnabled: Boolean = true,
) {
    val hapticTick = rememberColorOsHapticTick()

    ColorOsSettingsSliderRow(
        heading = heading,
        title = currentValue,
        enabled = enabled,
        hasDividerAbove = hasDividerAbove,
        hasDividerBelow = hasDividerBelow,
        interactionEnabled = interactionEnabled,
        titleAction = {
            val tint = run {
                COUITheme.colorScheme.onSurfaceVariantActions
            }
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                IconButton(onClick = { if (interactionEnabled) onReset() }, enabled = enabled,
                    modifier = Modifier.size(32.dp).settingsInteractionGate(interactionEnabled)) {
                    Icon(
                        imageVector = Icons.Rounded.Restore,
                        contentDescription = resetDescription,
                        tint = if (enabled) tint else tint.copy(alpha = 0.38f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
    ) {
        FeatureSettingsSlider(
            value = value.coerceIn(valueRange),
            onValueChange = { next ->
                val snapped = snapFeatureSliderValue(next, valueRange, steps)
                if (enabled && interactionEnabled && abs(snapped - value) > 0.0001f) {
                    hapticTick()
                    onValueChange(snapped)
                }
            },
            onValueChangeFinished = onValueChangeFinished,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
            interactionEnabled = interactionEnabled,
        )
    }
}

internal fun snapFeatureSliderValue(value: Float, range: ClosedFloatingPointRange<Float>, steps: Int): Float {
    val bounded = value.coerceIn(range)
    if (steps <= 0 || range.endInclusive <= range.start) return bounded
    val intervals = steps + 1
    val index = ((bounded - range.start) / (range.endInclusive - range.start) * intervals).roundToInt()
    return (range.start + (range.endInclusive - range.start) * index / intervals).coerceIn(range)
}

@Composable
internal fun ColorOsSettingsSliderRow(
    title: String,
    enabled: Boolean,
    hasDividerAbove: Boolean,
    hasDividerBelow: Boolean,
    heading: String? = null,
    titleAction: (@Composable () -> Unit)? = null,
    interactionEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val rowModifier = Modifier.settingsInteractionGate(interactionEnabled)


    val interactionSource = remember { MutableInteractionSource() }
    val titleAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else ColorOsSettingsSliderDisabledTitleAlpha,
        animationSpec = tween(durationMillis = ColorOsSettingsSliderStateAnimationMillis),
        label = "ColorOsSettingsSliderTitleAlpha",
    )
    Column(
        modifier = rowModifier
            .fillMaxWidth()
            .settingsInteractiveRowHighlight(
                interactionSource = interactionSource,
                color = Color.Transparent,
                hasDividerAbove = hasDividerAbove,
                hasDividerBelow = hasDividerBelow,
            )
            // Slider title padding inside the card.
            .padding(horizontal = 16.dp),
    ) {
        if (heading != null) {
            Column(Modifier.padding(top = 10.dp)) {
                SettingsRowTextContent(title = heading, summary = null, enabled = enabled)
            }
        }
        Row(
            modifier = Modifier.padding(top = if (heading == null) 10.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = COUITheme.textStyles.title3,
                color = COUITheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f, fill = false).graphicsLayer { alpha = titleAlpha },
            )
            titleAction?.invoke()
        }
        content()
    }
}

internal const val ColorOsSettingsSliderStateAnimationMillis = 183
internal const val ColorOsSettingsSliderDisabledTitleAlpha = 0.38f

internal fun formatAodMultiplier(value: Float): String {
    val rounded = (value * 10f).roundToInt() / 10f
    return if (rounded % 1f == 0f) {
        rounded.toInt().toString()
    } else {
        rounded.toString()
    }
}

@Composable
internal fun FeatureSegmentPosition(
    index: Int,
    count: Int,
    content: @Composable () -> Unit,
) {
    run {
        content()
    }
}

@Composable
internal fun FeatureAnimatedSegmentPosition(
    index: Int,
    count: Int,
    content: @Composable () -> Unit,
) {
    run {
        content()
    }
}

@Composable
internal fun FeatureExpandableVisibility(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    run {
        AnimatedVisibility(
            visible = visible,
            enter = FeatureExpansionMotion.Enter,
            exit = FeatureExpansionMotion.Exit,
        ) {
            content()
        }
    }
}
