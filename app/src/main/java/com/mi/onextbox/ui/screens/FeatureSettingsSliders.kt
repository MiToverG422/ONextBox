package com.mi.onextbox.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider as MaterialSlider
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mi.onextbox.R
import com.mi.onextbox.ui.common.ColorOs17SettingsSlider
import com.mi.onextbox.ui.common.AppUiStyle
import com.mi.onextbox.ui.common.LocalAppUiStyle
import com.mi.onextbox.ui.common.rememberColorOsHapticTick
import com.mi.onextbox.ui.settings.Material3ExpressiveAnimatedSegmentPosition
import com.mi.onextbox.ui.settings.Material3ExpressiveInferredSegmentPosition
import com.mi.onextbox.ui.settings.Material3ExpressiveSegmentContentCard
import com.mi.onextbox.ui.settings.Material3ExpressiveSegmentPosition
import com.mi.onextbox.ui.settings.SettingsRowTextContent
import com.mi.onextbox.ui.settings.settingsInteractiveRowHighlight
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
) {
    if (LocalAppUiStyle.current == AppUiStyle.Material3Expressive) {
        MaterialSlider(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            modifier = modifier,
            onValueChangeFinished = onValueChangeFinished,
        )
    } else {
        ColorOs17SettingsSlider(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            modifier = modifier,
            onValueChangeFinished = onValueChangeFinished,
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
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onReset: () -> Unit,
    hasDividerAbove: Boolean = true,
    hasDividerBelow: Boolean = false,
    onValueChangeFinished: (() -> Unit)? = null,
    resetDescription: String = stringResource(R.string.feature_slider_reset),
) {
    val hapticTick = rememberColorOsHapticTick()
    ColorOsSettingsSliderRow(
        heading = heading,
        title = currentValue,
        enabled = enabled,
        hasDividerAbove = hasDividerAbove,
        hasDividerBelow = hasDividerBelow,
        titleAction = {
            val tint = if (LocalAppUiStyle.current == AppUiStyle.Material3Expressive) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                COUITheme.colorScheme.onSurfaceVariantActions
            }
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                IconButton(onClick = onReset, enabled = enabled, modifier = Modifier.size(32.dp)) {
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
                if (enabled && abs(snapped - value) > 0.0001f) {
                    hapticTick()
                    onValueChange(snapped)
                }
            },
            onValueChangeFinished = onValueChangeFinished,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
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
    content: @Composable () -> Unit,
) {
    if (LocalAppUiStyle.current == AppUiStyle.Material3Expressive) {
        Material3ExpressiveInferredSegmentPosition(hasDividerAbove, hasDividerBelow) {
            Material3ExpressiveSegmentContentCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    if (heading != null) {
                        SettingsRowTextContent(
                            title = heading,
                            summary = null,
                            enabled = enabled,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MaterialText(
                            text = title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (enabled) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        titleAction?.invoke()
                    }
                    content()
                }
            }
        }
        return
    }
    val interactionSource = remember { MutableInteractionSource() }
    val titleAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else ColorOsSettingsSliderDisabledTitleAlpha,
        animationSpec = tween(durationMillis = ColorOsSettingsSliderStateAnimationMillis),
        label = "ColorOsSettingsSliderTitleAlpha",
    )
    Column(
        modifier = Modifier
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
    if (LocalAppUiStyle.current == AppUiStyle.Material3Expressive) {
        Material3ExpressiveSegmentPosition(index, count, content)
    } else {
        content()
    }
}

@Composable
internal fun FeatureAnimatedSegmentPosition(
    index: Int,
    count: Int,
    content: @Composable () -> Unit,
) {
    if (LocalAppUiStyle.current == AppUiStyle.Material3Expressive) {
        Material3ExpressiveAnimatedSegmentPosition(
            index = index,
            count = count,
            durationMillis = if (count > 1) 280 else 240,
            content = content,
        )
    } else {
        content()
    }
}

@Composable
internal fun FeatureExpandableVisibility(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    if (LocalAppUiStyle.current == AppUiStyle.Material3Expressive) {
        val visibleState = remember { MutableTransitionState(visible) }
        visibleState.targetState = visible
        if (visibleState.currentState || visibleState.targetState || !visibleState.isIdle) {
            AnimatedVisibility(
                visibleState = visibleState,
                enter = expandVertically(
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 800f),
                    expandFrom = Alignment.Top,
                    clip = true,
                ) + fadeIn(spring(dampingRatio = 0.9f, stiffness = 800f)),
                exit = shrinkVertically(
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 800f),
                    shrinkTowards = Alignment.Top,
                    clip = true,
                ) + fadeOut(spring(dampingRatio = 0.9f, stiffness = 800f)),
            ) {
                content()
            }
        }
    } else {
        AnimatedVisibility(
            visible = visible,
            enter = expandVertically(tween(260, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(220, easing = FastOutSlowInEasing)) + fadeOut(tween(140)),
        ) {
            content()
        }
    }
}
