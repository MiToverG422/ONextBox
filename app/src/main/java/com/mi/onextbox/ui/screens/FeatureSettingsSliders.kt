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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider as MaterialSlider
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mi.onextbox.ui.common.ColorOs17SettingsSlider
import com.mi.onextbox.ui.common.AppUiStyle
import com.mi.onextbox.ui.common.LocalAppUiStyle
import com.mi.onextbox.ui.settings.Material3ExpressiveAnimatedSegmentPosition
import com.mi.onextbox.ui.settings.Material3ExpressiveInferredSegmentPosition
import com.mi.onextbox.ui.settings.Material3ExpressiveSegmentContentCard
import com.mi.onextbox.ui.settings.Material3ExpressiveSegmentPosition
import com.mi.onextbox.ui.settings.settingsInteractiveRowHighlight
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.math.roundToInt

@Composable
internal fun FeatureSettingsSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    enabled: Boolean,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    modifier: Modifier = Modifier,
) {
    if (LocalAppUiStyle.current == AppUiStyle.Material3Expressive) {
        MaterialSlider(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            modifier = modifier,
        )
    } else {
        ColorOs17SettingsSlider(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            modifier = modifier,
        )
    }
}

@Composable
internal fun ColorOsSettingsSliderRow(
    title: String,
    enabled: Boolean,
    hasDividerAbove: Boolean,
    hasDividerBelow: Boolean,
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
                    MaterialText(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    )
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
            // Slider title inset inside a settings card.
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = title,
            style = COUITheme.textStyles.title3,
            color = COUITheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            modifier = Modifier
                .padding(top = 10.dp)
                .graphicsLayer { alpha = titleAlpha },
        )
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
