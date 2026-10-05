package com.mi.onextbox.ui.common

import android.graphics.Path
import android.graphics.RectF
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.window.WindowDialog

internal val LocalColorOsDialogContent = staticCompositionLocalOf { false }

private object DialogSpacing {
    val ContentInset = 24.dp
    val CustomVerticalInset = 8.dp
    val TitleTop = 24.dp
    val TitleBottom = 6.dp
    val TitleMaxHeight = 80.dp
    val ButtonInset = 16.dp
    val ButtonGap = 8.dp
    val ButtonTop = 4.dp
    val ButtonHeight = 44.dp
    val MultilineButtonHeight = 56.dp
    val ChoiceHighlightOutset = 8.dp
    val ChoiceHighlightVerticalInset = 2.dp
    val ChoiceHighlightRadius = 28.dp
}

private data class DialogPresentation(
    val title: String?,
    val summary: String?,
    val negativeText: String,
    val positiveText: String,
    val content: (@Composable () -> Unit)?,
)

internal object ColorOsDialogShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val rect = RectF(0f, 0f, size.width, size.height)
        val radius = with(density) { 45.dp.toPx() }.coerceAtMost(size.height / 2f)
        if (!addOplusSmoothRoundRect(path, rect, radius, weight = 3f)) {
            path.addRoundRect(rect, radius, radius, Path.Direction.CCW)
        }
        return Outline.Generic(path.asComposePath())
    }
}

@Composable
internal fun ColorOs17ConfirmDialog(
    show: Boolean,
    title: String?,
    summary: String?,
    negativeText: String,
    positiveText: String,
    onDismissRequest: () -> Unit,
    onNegative: () -> Unit,
    onPositive: () -> Unit,
    onDismissFinished: (() -> Unit)?,
    content: (@Composable () -> Unit)?,
) {
    val darkTheme = COUITheme.colorScheme.background.luminance() < 0.5f
    val density = LocalDensity.current
    val dimProgress by animateFloatAsState(if (show) 1f else 0f, tween(200), label = "C17DialogDim")
    val currentPresentation = DialogPresentation(title, summary, negativeText, positiveText, content)
    var lastVisiblePresentation by remember { mutableStateOf(currentPresentation) }
    SideEffect {
        if (show) lastVisiblePresentation = currentPresentation
    }
    // Dismissal can clear the caller's selection before the window finishes sliding out.
    val presentation = if (show) currentPresentation else lastVisiblePresentation
    // Keep the window host alive until its exit and predictive-back transitions finish.
    COUITheme(colors = COUITheme.colorScheme.copy(windowDimming = Color.Black.copy(alpha = .2f))) {
        WindowDialog(
            show = show,
            enableWindowDim = false,
            backgroundColor = Color.Transparent,
            cornerRadius = 0.dp,
            maxWidth = 392.dp,
            outsideMargin = DpSize(16.dp, 32.dp),
            onDismissRequest = onDismissRequest,
            onDismissFinished = onDismissFinished,
        ) {
            ColorOsDialogSystemBars(darkTheme, dimProgress)
            Box(Modifier.fillMaxWidth().graphicsLayer { shape = ColorOsDialogShape; clip = true }) {
                key(darkTheme, density.density) {
                    AndroidView(
                        factory = { ColorOsDialogMaterialView(it, darkTheme, ColorOsDialogMaterialView.Kind.Panel) },
                        modifier = Modifier.matchParentSize(),
                    )
                }
                Column(Modifier.fillMaxWidth().heightIn(max = 632.dp)) {
                    presentation.title?.takeIf { it.isNotBlank() }?.let {
                        Box(
                            Modifier.fillMaxWidth().padding(
                                start = DialogSpacing.ContentInset, end = DialogSpacing.ContentInset,
                                top = DialogSpacing.TitleTop, bottom = DialogSpacing.TitleBottom,
                            ),
                        ) {
                            BasicText(
                                text = it,
                                modifier = Modifier.fillMaxWidth().heightIn(max = DialogSpacing.TitleMaxHeight)
                                    .verticalScroll(rememberScrollState()),
                                style = dialogTextStyle(18, if (darkTheme) Color(0xE6FFFFFF) else Color(0xE6000000), FontWeight(550), TextAlign.Center, 1.36f),
                            )
                        }
                    }
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        presentation.summary?.let {
                            var multiline by remember(it) { mutableStateOf(false) }
                            BasicText(
                                text = it,
                                modifier = Modifier.fillMaxWidth().padding(
                                    start = DialogSpacing.ContentInset, end = DialogSpacing.ContentInset,
                                    top = if (presentation.title.isNullOrBlank()) 22.dp else 0.dp,
                                    bottom = if (presentation.content == null) 16.dp else 4.dp,
                                ),
                                style = dialogTextStyle(13, if (darkTheme) Color(0x8AFFFFFF) else Color(0x8A000000), FontWeight.Normal, if (multiline) TextAlign.Start else TextAlign.Center, 1.29f),
                                onTextLayout = { multiline = it.lineCount > 1 },
                            )
                        }
                        presentation.content?.let { customContent ->
                            Column(
                                Modifier.fillMaxWidth().padding(
                                    horizontal = DialogSpacing.ContentInset,
                                    vertical = DialogSpacing.CustomVerticalInset,
                                ).then(
                                    if (presentation.title.isNullOrBlank() && presentation.summary == null) Modifier.padding(top = 16.dp)
                                    else Modifier,
                                ),
                            ) {
                                CompositionLocalProvider(LocalColorOsDialogContent provides true) {
                                    customContent()
                                }
                            }
                        }
                    }
                    ColorOsDialogButtonBar(presentation, darkTheme, onNegative, onPositive)
                }
            }
        }
    }
}

@Composable
private fun ColorOsDialogButtonBar(
    presentation: DialogPresentation,
    darkTheme: Boolean,
    onNegative: () -> Unit,
    onPositive: () -> Unit,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val style = dialogTextStyle(16, Color.White, FontWeight.Medium, TextAlign.Center, 1.29f)
    BoxWithConstraints(
        Modifier.fillMaxWidth().padding(
            start = DialogSpacing.ButtonInset, end = DialogSpacing.ButtonInset,
            top = if (presentation.summary == null && presentation.content == null) 16.dp else DialogSpacing.ButtonTop,
            bottom = DialogSpacing.ButtonInset,
        ),
    ) {
        val labelWidths = listOf(presentation.negativeText, presentation.positiveText).map {
            textMeasurer.measure(it, style, softWrap = false).size.width
        }
        val horizontalTextWidth = with(density) { ((maxWidth - DialogSpacing.ButtonGap) / 2 - 24.dp).roundToPx() }
        if (labelWidths.any { it > horizontalTextWidth }) {
            val fullTextWidth = with(density) { (maxWidth - 24.dp).roundToPx() }
            Column(verticalArrangement = Arrangement.spacedBy(DialogSpacing.ButtonGap)) {
                ColorOsDialogButton(
                    presentation.positiveText, true, darkTheme, onPositive, Modifier.fillMaxWidth(),
                    if (labelWidths[1] > fullTextWidth) DialogSpacing.MultilineButtonHeight else DialogSpacing.ButtonHeight,
                )
                ColorOsDialogButton(
                    presentation.negativeText, false, darkTheme, onNegative, Modifier.fillMaxWidth(),
                    if (labelWidths[0] > fullTextWidth) DialogSpacing.MultilineButtonHeight else DialogSpacing.ButtonHeight,
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(DialogSpacing.ButtonGap)) {
                ColorOsDialogButton(presentation.negativeText, false, darkTheme, onNegative, Modifier.weight(1f))
                ColorOsDialogButton(presentation.positiveText, true, darkTheme, onPositive, Modifier.weight(1f))
            }
        }
    }
}

private fun dialogTextStyle(size: Int, color: Color, weight: FontWeight, alignment: TextAlign, lineHeight: Float) = TextStyle(
    color = color,
    fontSize = size.sp,
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    textAlign = alignment,
    lineHeight = lineHeight.em,
    platformStyle = PlatformTextStyle(includeFontPadding = true),
)

@Composable
internal fun ColorOsDialogChoice(
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    val darkTheme = COUITheme.colorScheme.background.luminance() < 0.5f
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val highlight = remember { Animatable(0f) }
    LaunchedEffect(pressed) {
        highlight.animateTo(if (pressed) 1f else 0f,
            tween(if (pressed) 180 else 360, easing = LinearEasing))
    }
    val pressColor = if (darkTheme) Color(0x26FFFFFF) else Color(0x14000000)
    Box(
        Modifier.fillMaxWidth()
            .drawWithCache {
                val outset = DialogSpacing.ChoiceHighlightOutset.toPx()
                val verticalInset = DialogSpacing.ChoiceHighlightVerticalInset.toPx()
                val rect = RectF(-outset, verticalInset, size.width + outset,
                    (size.height - verticalInset).coerceAtLeast(verticalInset))
                val radius = DialogSpacing.ChoiceHighlightRadius.toPx()
                    .coerceAtMost(rect.height() / 2f)
                val nativePath = Path()
                if (!addOplusSmoothRoundRect(nativePath, rect, radius, weight = 3f)) {
                    nativePath.addRoundRect(rect, radius, radius, Path.Direction.CCW)
                }
                val highlightPath = nativePath.asComposePath()
                onDrawBehind {
                    drawPath(highlightPath, pressColor.copy(alpha = pressColor.alpha * highlight.value))
                }
            }
            .clickable(interactionSource = interactions, indication = null, onClick = onClick)
            .heightIn(min = 48.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            BasicText(
                text = title,
                modifier = Modifier.fillMaxWidth().heightIn(min = 22.dp),
                style = dialogTextStyle(16, if (darkTheme) Color(0xE6FFFFFF) else Color(0xE6000000),
                    FontWeight.Medium, TextAlign.Start, 1.36f),
            )
            if (summary.isNotBlank()) {
                BasicText(
                    text = summary,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp).heightIn(min = 20.dp),
                    style = dialogTextStyle(14, if (darkTheme) Color(0x8AFFFFFF) else Color(0x8A000000),
                        FontWeight.Normal, TextAlign.Start, 1.29f),
                )
            }
        }
    }
}

@Composable
private fun ColorOsDialogButton(
    text: String, primary: Boolean, darkTheme: Boolean, onClick: () -> Unit, modifier: Modifier,
    minimumHeight: Dp = DialogSpacing.ButtonHeight,
) {
    val density = LocalDensity.current
    val accent = COUITheme.colorScheme.primary.toArgb()
    val holder = remember { DialogButtonMaterialHolder() }
    val motion = remember(density.density) { ColorOsDialogButtonMotion(density.density) }
    DisposableEffect(motion) {
        onDispose { motion.dispose() }
    }
    // Keep gesture coordinates outside the moving layer to avoid feedback while dragging.
    Box(
        modifier.onSizeChanged { holder.size = it }.pointerInput(holder, motion) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, down.position)
                var lastPosition = down.position
                var dragged = false
                var releasedNormally = false
                motion.begin()
                val localDown = motion.localSpotlightPosition(lastPosition, holder.size)
                holder.view?.spotlightDown(localDown.x, localDown.y)
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        lastPosition = change.position
                        tracker.addPosition(change.uptimeMillis, lastPosition)
                        if (!change.pressed || event.changes.size > 1) {
                            releasedNormally = !change.pressed && event.changes.size == 1
                            if (dragged) change.consume()
                            break
                        }
                        val delta = lastPosition - down.position
                        if (delta.getDistance() > viewConfiguration.touchSlop) dragged = true
                        if (dragged) change.consume()
                        motion.drag(delta, holder.size)
                        val local = motion.localSpotlightPosition(lastPosition, holder.size)
                        holder.view?.spotlightMove(local.x, local.y)
                    }
                } finally {
                    val velocity = if (releasedNormally) tracker.calculateVelocity() else null
                    motion.release(holder.size, velocity?.let { Offset(it.x, it.y) } ?: Offset.Zero)
                    val local = motion.localSpotlightPosition(lastPosition, holder.size)
                    holder.view?.spotlightUp(local.x, local.y)
                }
            }
        },
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().graphicsLayer {
                translationX = motion.translationX
                translationY = motion.translationY
                scaleX = motion.scaleX
                scaleY = motion.scaleY
            },
            insideMargin = PaddingValues(0.dp),
            minWidth = 0.dp,
            minHeight = minimumHeight,
            cornerRadius = 1000.dp,
            colors = ButtonDefaults.buttonColors(color = Color.Transparent, contentColor = Color.White),
        ) {
            Box(Modifier.fillMaxWidth().heightIn(min = minimumHeight).graphicsLayer { shape = CircleShape; clip = true }, contentAlignment = Alignment.Center) {
                key(darkTheme, density.density, accent, primary) {
                    AndroidView(
                        factory = {
                            ColorOsDialogMaterialView(it, darkTheme, if (primary) ColorOsDialogMaterialView.Kind.Primary else ColorOsDialogMaterialView.Kind.Secondary, accent).also { holder.view = it }
                        },
                        modifier = Modifier.matchParentSize(),
                    )
                }
                BasicText(
                    text = text,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = dialogTextStyle(16, if (primary || darkTheme) Color(0xE6FFFFFF) else Color(0xE6000000), FontWeight.Medium, TextAlign.Center, 1.29f),
                )
            }
        }
    }
}

private class DialogButtonMaterialHolder {
    var view: ColorOsDialogMaterialView? = null
    var size: IntSize = IntSize.Zero
}
