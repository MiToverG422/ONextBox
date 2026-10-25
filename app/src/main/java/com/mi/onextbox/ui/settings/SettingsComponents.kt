@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.mi.onextbox.ui.settings

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mi.onextbox.ui.common.AppUiTokens
import com.mi.onextbox.ui.common.LocalColorOsDialogContent
import com.mi.onextbox.ui.common.ColorOsDialogChoice
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.SmallTitle
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.max



@Composable
fun SettingsSection(title: String) {


    SmallTitle(text = title)
}

@Composable
fun SettingsGroup(
    bottomPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {


    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = bottomPadding),
        cornerRadius = AppUiTokens.CardCornerRadius,
    ) {
        content()
    }
}

internal object SettingsTokens {
    // Settings row padding inside a card.
    val RowInsideMargin: PaddingValues
        @Composable get() = PaddingValues(
            horizontal = if (LocalColorOsDialogContent.current) 0.dp else 16.dp,
            vertical = 10.dp,
        )
    // Press highlight inset around dividers.
    val DividerVerticalPadding = 6.dp
    // Settings divider thickness.
    val DividerThickness = 0.33.dp
    // Divider inset from the card edge.
    val DividerCardInset = 16.dp
    // Horizontal overflow for custom pressed-row highlight backgrounds.
    val RowHighlightHorizontalOverflow = 16.dp
    // Extra pressed-highlight overflow at card group top/bottom edges.
    val RowHighlightGroupEdgeOverflow = 28.dp
}

@Composable
fun SettingsDivider() {


    val dividerColor = if (COUITheme.colorScheme.background.luminance() < 0.5f) {
        Color(0x33FFFFFF)
    } else {
        Color(0x1A000000)
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = SettingsTokens.DividerCardInset),
        thickness = SettingsTokens.DividerThickness,
        color = dividerColor,
    )
}

@Composable


private fun Modifier.settingsRowHighlight(
    progress: Float,
    color: Color,
    hasDividerAbove: Boolean,
    hasDividerBelow: Boolean,
): Modifier = drawBehind {
    val alpha = progress.coerceIn(0f, 1f)
    if (alpha <= 0f) return@drawBehind

    val horizontalOverflow = SettingsTokens.RowHighlightHorizontalOverflow.toPx()
    val dividerLimit = SettingsTokens.DividerVerticalPadding.toPx()
    val groupEdgeOverflow = SettingsTokens.RowHighlightGroupEdgeOverflow.toPx()
    val topOverflow = if (hasDividerAbove) dividerLimit else groupEdgeOverflow
    val bottomOverflow = if (hasDividerBelow) dividerLimit else groupEdgeOverflow

    drawRect(
        color = color.copy(alpha = color.alpha * alpha),
        topLeft = Offset(-horizontalOverflow, -topOverflow),
        size = Size(
            width = size.width + horizontalOverflow * 2f,
            height = size.height + topOverflow + bottomOverflow,
        ),
    )
}

@Composable
fun Modifier.settingsInteractiveRowHighlight(
    interactionSource: MutableInteractionSource,
    color: Color,
    hasDividerAbove: Boolean,
    hasDividerBelow: Boolean,
    persistentProgress: Float = 0f,
): Modifier {
    val clickProgress = rememberSettingsRowClickHighlightProgress(interactionSource)
    return settingsRowHighlight(
        progress = max(clickProgress, persistentProgress),
        color = color,
        hasDividerAbove = hasDividerAbove,
        hasDividerBelow = hasDividerBelow,
    )
}

@Composable
private fun rememberSettingsRowClickHighlightProgress(
    interactionSource: MutableInteractionSource,
): Float {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(interactionSource) {
        var animationJob: Job? = null
        interactionSource.interactions.collect { interaction ->
            animationJob?.cancel()
            when (interaction) {
                is PressInteraction.Press -> {
                    animationJob = launch {
                        progress.stop()
                        progress.snapTo(0f)
                        progress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = 80),
                        )
                    }
                }
                is PressInteraction.Release,
                is PressInteraction.Cancel -> {
                    animationJob = launch {
                        progress.stop()
                        progress.animateTo(
                            targetValue = 0.55f,
                            animationSpec = tween(durationMillis = 60),
                        )
                        progress.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(durationMillis = 180),
                        )
                    }
                }
            }
        }
    }
    return progress.value
}

@Composable
fun SettingsCardRow(
    title: String,
    summary: String,
    trailing: String? = null,
    onClick: (() -> Unit)? = null,
    showArrow: Boolean = false,
    showExpandArrow: Boolean = false,
    expandArrowExpanded: Boolean = false,
    hasDividerAbove: Boolean = false,
    hasDividerBelow: Boolean = false,
    leadingContent: (@Composable () -> Unit)? = null,
) {



    val rowSummary = summary.takeIf { it.isNotBlank() }
    if (LocalColorOsDialogContent.current && onClick != null && trailing == null &&
        !showArrow && !showExpandArrow && leadingContent == null) {
        ColorOsDialogChoice(title, summary, onClick)
        return
    }
    val startAction = leadingContent?.let { content: @Composable () -> Unit ->
        @Composable { content() }
    }

    when {
        trailing != null -> BasicComponent(
            title = title,
            summary = rowSummary,
            startAction = startAction,
            insideMargin = SettingsTokens.RowInsideMargin,
            endActions = {
                Text(text = trailing, color = COUITheme.colorScheme.onSurfaceVariantActions)
                if (showExpandArrow || showArrow) {
                    Spacer(Modifier.width(8.dp))
                    if (showExpandArrow) SettingsExpandArrow(expanded = expandArrowExpanded) else SettingsJumpArrow()
                }
            },
            onClick = onClick,
        )
        showArrow -> BasicComponent(
            title = title,
            summary = rowSummary,
            startAction = startAction,
            insideMargin = SettingsTokens.RowInsideMargin,
            endActions = { SettingsJumpArrow() },
            onClick = onClick,
        )
        showExpandArrow -> BasicComponent(
            title = title,
            summary = rowSummary,
            startAction = startAction,
            insideMargin = SettingsTokens.RowInsideMargin,
            endActions = {
                SettingsExpandArrow(expanded = expandArrowExpanded)
            },
            onClick = onClick,
        )
        else -> BasicComponent(
            title = title,
            summary = rowSummary,
            startAction = startAction,
            insideMargin = SettingsTokens.RowInsideMargin,
            onClick = onClick,
        )
    }
}

@Composable
private fun SettingsJumpArrow() {
    val direction = LocalLayoutDirection.current
    val tint = COUITheme.colorScheme.onSurfaceVariantActions
    Canvas(
        modifier = Modifier
            .size(width = 12.dp, height = 24.dp)
            .graphicsLayer(scaleX = if (direction == LayoutDirection.Rtl) -1f else 1f),
    ) {
        val scaleX = size.width / 12f
        val scaleY = size.height / 24f
        val path = Path().apply {
            moveTo(5f * scaleX, 6f * scaleY)
            lineTo(10.646f * scaleX, 11.646f * scaleY)
            cubicTo(
                10.842f * scaleX,
                11.842f * scaleY,
                10.842f * scaleX,
                12.158f * scaleY,
                10.646f * scaleX,
                12.354f * scaleY,
            )
            lineTo(5f * scaleX, 18f * scaleY)
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(
                width = 1.4.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

/** C17 coui_pop_up_next: the 14x24dp up/down mark used by expandable choices. */
@Composable
internal fun SettingsPopupArrow(
    tint: Color = COUITheme.colorScheme.onSurfaceVariantActions,
) {
    Canvas(modifier = Modifier.size(width = 14.dp, height = 24.dp)) {
        val scaleX = size.width / 14f
        val scaleY = size.height / 24f
        val stroke = Stroke(
            width = 1.4.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        val upper = Path().apply {
            moveTo(4.199f * scaleX, 10f * scaleY)
            lineTo(8.328f * scaleX, 5.413f * scaleY)
            cubicTo(
                8.526f * scaleX,
                5.192f * scaleY,
                8.872f * scaleX,
                5.192f * scaleY,
                9.071f * scaleX,
                5.413f * scaleY,
            )
            lineTo(13.199f * scaleX, 10f * scaleY)
        }
        val lower = Path().apply {
            moveTo(13.199f * scaleX, 14f * scaleY)
            lineTo(9.071f * scaleX, 18.587f * scaleY)
            cubicTo(
                8.872f * scaleX,
                18.808f * scaleY,
                8.526f * scaleX,
                18.808f * scaleY,
                8.328f * scaleX,
                18.587f * scaleY,
            )
            lineTo(4.199f * scaleX, 14f * scaleY)
        }
        drawPath(upper, color = tint, style = stroke)
        drawPath(lower, color = tint, style = stroke)
    }
}

/** Animated expand arrow for settings rows. */
@Composable
internal fun SettingsExpandArrow(
    expanded: Boolean,
) {
    val verticalScale by animateFloatAsState(
        targetValue = if (expanded) -1f else 1f,
        animationSpec = tween(
            durationMillis = 350,
            easing = CubicBezierEasing(0.3f, 0f, 0.1f, 1f),
        ),
        label = "settingsExpandArrowMorph",
    )
    val tint = COUITheme.colorScheme.onSurfaceSecondary
    Canvas(modifier = Modifier.size(24.dp)) {
        val intrinsicOffsetX = 4.dp.toPx()
        val scaleX = 1.dp.toPx()
        val scaleY = verticalScale * 1.dp.toPx()
        val centreY = size.height / 2f
        val path = Path().apply {
            fun y(value: Float): Float = centreY + (value - 12f) * scaleY
            moveTo(intrinsicOffsetX + 8.595f * scaleX, y(13.743f))
            cubicTo(
                intrinsicOffsetX + 8.751f * scaleX, y(13.901f),
                intrinsicOffsetX + 8.829f * scaleX, y(13.981f),
                intrinsicOffsetX + 8.921f * scaleX, y(14.003f),
            )
            cubicTo(
                intrinsicOffsetX + 8.973f * scaleX, y(14.016f),
                intrinsicOffsetX + 9.027f * scaleX, y(14.016f),
                intrinsicOffsetX + 9.079f * scaleX, y(14.003f),
            )
            cubicTo(
                intrinsicOffsetX + 9.171f * scaleX, y(13.981f),
                intrinsicOffsetX + 9.249f * scaleX, y(13.901f),
                intrinsicOffsetX + 9.405f * scaleX, y(13.743f),
            )
            lineTo(intrinsicOffsetX + 15.035f * scaleX, y(8f))
            lineTo(intrinsicOffsetX + 16f * scaleX, y(8.975f))
            lineTo(intrinsicOffsetX + 9.795f * scaleX, y(15.202f))
            cubicTo(
                intrinsicOffsetX + 9.489f * scaleX, y(15.509f),
                intrinsicOffsetX + 9.336f * scaleX, y(15.663f),
                intrinsicOffsetX + 9.154f * scaleX, y(15.707f),
            )
            cubicTo(
                intrinsicOffsetX + 9.053f * scaleX, y(15.731f),
                intrinsicOffsetX + 8.947f * scaleX, y(15.731f),
                intrinsicOffsetX + 8.846f * scaleX, y(15.707f),
            )
            cubicTo(
                intrinsicOffsetX + 8.664f * scaleX, y(15.663f),
                intrinsicOffsetX + 8.511f * scaleX, y(15.509f),
                intrinsicOffsetX + 8.205f * scaleX, y(15.202f),
            )
            lineTo(intrinsicOffsetX + 2f * scaleX, y(8.975f))
            lineTo(intrinsicOffsetX + 2.966f * scaleX, y(8f))
            close()
        }
        drawPath(path = path, color = tint)
    }
}

@Composable
internal fun SettingsRowTextContent(
    title: String,
    summary: String?,
    enabled: Boolean = true,
) {

    Text(
        text = title,
        fontSize = COUITheme.textStyles.headline1.fontSize,
        fontWeight = FontWeight.Medium,
        color = if (enabled) {
            COUITheme.colorScheme.onBackground
        } else {
            COUITheme.colorScheme.disabledOnSecondaryVariant
        },
    )
    if (summary != null) {
        Text(
            text = summary,
            modifier = Modifier.padding(top = 2.dp),
            fontSize = COUITheme.textStyles.body2.fontSize,
            color = if (enabled) {
                COUITheme.colorScheme.onSurfaceVariantSummary
            } else {
                COUITheme.colorScheme.disabledOnSecondaryVariant
            },
        )
    }
}
