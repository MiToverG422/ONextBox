@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.mi.onextbox.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LsposedScopeRequester
import com.mi.onextbox.ui.common.AppLocale
import com.mi.onextbox.ui.common.AppIcons
import com.mi.onextbox.ui.common.AppUiTokens
import com.mi.onextbox.ui.common.AppUiStyle
import com.mi.onextbox.ui.common.LocalAppUiStyle
import com.mi.onextbox.ui.common.LocalColorOsDialogContent
import com.mi.onextbox.ui.common.ColorOsDialogChoice
import com.mi.onextbox.ui.common.rememberHapticClick
import com.mi.onextbox.ui.common.bottomTabs
import com.mi.onextbox.ui.common.readCachedRootAccessInfo
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.SmallTitle
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.basic.ListPopup
import io.github.suqi8.coui.kmp.basic.NavigationBar
import io.github.suqi8.coui.kmp.basic.NavigationItem
import io.github.suqi8.coui.kmp.basic.PopupPositionProvider
import io.github.suqi8.coui.kmp.basic.Switch
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.icon.extended.Ok
import io.github.suqi8.coui.kmp.icon.extended.Back
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.darkColorScheme
import io.github.suqi8.coui.kmp.theme.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface as MaterialSurface
import androidx.compose.material3.Text as MaterialText
import io.github.suqi8.coui.kmp.utils.COUIPopupUtils.Companion.COUIPopupHost
import io.github.suqi8.coui.kmp.utils.PressFeedbackType
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.max

internal val LocalMaterial3ExpressiveSegmentShapes = compositionLocalOf<ListItemShapes?> { null }

@Composable
internal fun Material3ExpressiveSegmentPosition(
    index: Int,
    count: Int,
    content: @Composable () -> Unit,
) {
    val defaults = ListItemDefaults.segmentedShapes(index, count)
    val shapes = if (count == 1) defaults.copy(shape = MaterialTheme.shapes.large) else defaults
    CompositionLocalProvider(LocalMaterial3ExpressiveSegmentShapes provides shapes, content = content)
}

@Composable
internal fun Material3ExpressiveInferredSegmentPosition(
    hasDividerAbove: Boolean,
    hasDividerBelow: Boolean,
    content: @Composable () -> Unit,
) {
    if (LocalMaterial3ExpressiveSegmentShapes.current != null) {
        content()
        return
    }
    val index = if (hasDividerAbove) 1 else 0
    val count = 1 + (if (hasDividerAbove) 1 else 0) + (if (hasDividerBelow) 1 else 0)
    Material3ExpressiveSegmentPosition(index, count, content)
}

@Composable
@Suppress("UNUSED_PARAMETER")
internal fun Material3ExpressiveAnimatedSegmentPosition(
    index: Int,
    count: Int,
    durationMillis: Int,
    content: @Composable () -> Unit,
) {
    val target = ListItemDefaults.segmentedShapes(index, count)
    val cornerSpring = spring<Dp>(dampingRatio = 0.9f, stiffness = 800f)
    val topCorner by animateDpAsState(
        targetValue = if (index == 0) 16.dp else 4.dp,
        animationSpec = cornerSpring,
        label = "material3ExpressiveSegmentTopCorner",
    )
    val bottomCorner by animateDpAsState(
        targetValue = if (index == count - 1) 16.dp else 4.dp,
        animationSpec = cornerSpring,
        label = "material3ExpressiveSegmentBottomCorner",
    )
    val shape = RoundedCornerShape(
        topStart = topCorner,
        topEnd = topCorner,
        bottomStart = bottomCorner,
        bottomEnd = bottomCorner,
    )
    CompositionLocalProvider(
        LocalMaterial3ExpressiveSegmentShapes provides target.copy(shape = shape),
        content = content,
    )
}

@Composable
internal fun Material3ExpressiveSegmentContentCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    MaterialSurface(
        modifier = modifier.fillMaxWidth(),
        shape = LocalMaterial3ExpressiveSegmentShapes.current?.shape ?: MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceBright,
        content = content,
    )
}

@Composable
fun SettingsSection(title: String) {
    if (LocalAppUiStyle.current == AppUiStyle.Material3Expressive) {
        MaterialText(
            text = title,
            modifier = Modifier
                .padding(horizontal = 32.dp, vertical = 8.dp)
                .heightIn(min = 16.dp),
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = MaterialTheme.colorScheme.primary,
        )
        return
    }
    SmallTitle(text = title)
}

@Composable
fun SettingsGroup(
    bottomPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalAppUiStyle.current == AppUiStyle.Material3Expressive) {
        val expressiveBottomPadding = if (bottomPadding == 16.dp) 13.dp else bottomPadding
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = expressiveBottomPadding),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
        return
    }
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
    // C17 support_preference_text_content_padding_(top|bottom) and the card's
    // 32dp absolute title inset (16dp card margin + 16dp row inset).
    val RowInsideMargin: PaddingValues
        @Composable get() = PaddingValues(
            horizontal = if (LocalColorOsDialogContent.current) 0.dp else 16.dp,
            vertical = 10.dp,
        )
    // Press highlight inset around dividers.
    val DividerVerticalPadding = 6.dp
    // C17 coui_list_divider_height.
    val DividerThickness = 0.33.dp
    // C17 coui_preference_divider_default_horizontal_padding is 32dp from
    // the screen edge, leaving 16dp inside a card that already has a 16dp margin.
    val DividerCardInset = 16.dp
    // Horizontal overflow for custom pressed-row highlight backgrounds.
    val RowHighlightHorizontalOverflow = 16.dp
    // Extra pressed-highlight overflow at card group top/bottom edges.
    val RowHighlightGroupEdgeOverflow = 28.dp
}

@Composable
fun SettingsDivider() {
    if (LocalAppUiStyle.current == AppUiStyle.Material3Expressive) {
        return
    }
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
internal fun Material3ExpressivePreferenceRow(
    title: String,
    summary: String?,
    modifier: Modifier = Modifier,
    startAction: (@Composable () -> Unit)? = null,
    endActions: (@Composable RowScope.() -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    role: Role? = null,
) {
    val shape = LocalMaterial3ExpressiveSegmentShapes.current?.shape ?: MaterialTheme.shapes.large
    val row: @Composable () -> Unit = {
        SegmentedListItem(
            onClick = onClick ?: {},
            modifier = modifier.fillMaxWidth(),
            enabled = enabled,
            shapes = LocalMaterial3ExpressiveSegmentShapes.current
                ?: ListItemDefaults.segmentedShapes(0, 1).copy(shape = shape),
            colors = ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceBright,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceBright,
                supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            leadingContent = startAction,
            supportingContent = summary?.takeIf { it.isNotBlank() }?.let { supporting ->
                { MaterialText(supporting) }
            },
            trailingContent = endActions?.let { trailing ->
                { Row(verticalAlignment = Alignment.CenterVertically) { trailing() } }
            },
            verticalAlignment = Alignment.CenterVertically,
        ) { MaterialText(title) }
    }
    if (bottomAction == null) {
        row()
    } else {
        MaterialSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceBright,
        ) {
            Column {
                row()
                bottomAction()
            }
        }
    }
}

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
    if (LocalAppUiStyle.current == AppUiStyle.Material3Expressive) {
        Material3ExpressiveInferredSegmentPosition(hasDividerAbove, hasDividerBelow) {
        Material3ExpressivePreferenceRow(
            title = title,
            summary = summary,
            startAction = leadingContent,
            onClick = onClick,
            endActions = {
                trailing?.let {
                    MaterialText(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (showArrow || showExpandArrow) {
                    MaterialIcon(
                        imageVector = if (showExpandArrow) {
                            if (expandArrowExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore
                        } else {
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight
                        },
                        contentDescription = null,
                        modifier = Modifier.padding(start = 8.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
        }
        return
    }

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
            endActions = { Text(text = trailing, color = COUITheme.colorScheme.onSurfaceVariantActions) },
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

/**
 * C17 coui_line_arrow in its native 24dp widget cell. The platform morphs the
 * 16x24dp down/up glyph over 350ms; scaling through its centre reproduces that
 * line-collapse transition while keeping the two endpoint shapes pixel-identical.
 */
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
    if (LocalAppUiStyle.current == AppUiStyle.Material3Expressive) {
        MaterialText(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
        if (summary != null) {
            MaterialText(
                text = summary,
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f),
            )
        }
        return
    }
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
