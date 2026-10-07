package com.mi.onextbox.ui.layout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mi.onextbox.ui.common.ColorOsBottomBarMaterialView
import com.mi.onextbox.ui.common.ColorOsTopBarSpotlightView
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import io.github.suqi8.coui.kmp.basic.NavigationItem
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign

@Composable
internal fun ColorOs17BottomNavigationBar(
    items: List<NavigationItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selectedIcons: List<ImageVector>? = null,
    unselectedIcons: List<ImageVector>? = null,
) {
    if (items.isEmpty()) return

    val colors = COUITheme.colorScheme
    val darkTheme = colors.background.luminance() < 0.5f
    val fallbackColor = if (darkTheme) Color(0xFF333333) else Color(0xFFF0F0F0)
    val selectedColor = if (darkTheme) Color(0xE6FFFFFF) else Color(0xE6000000)
    val unselectedColor = if (darkTheme) Color(0xB8FFFFFF) else Color(0xB8000000)
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val exactTitleSize = with(density) { 10.dp.toSp() }
    val currentOnItemSelected by rememberUpdatedState(onItemSelected)
    val currentSelectedIndex by rememberUpdatedState(selectedIndex.coerceIn(items.indices))

    var pressed by remember { mutableStateOf(false) }
    var previewIndex by remember {
        mutableIntStateOf(selectedIndex.coerceIn(items.indices))
    }
    val pressScale = remember { Animatable(1f) }
    val touchMotion = rememberColorOs17BottomBarTouchMotion()
    val spotlightHolder = remember { BottomBarSpotlightHolder() }

    LaunchedEffect(selectedIndex, pressed, items.size) {
        if (!pressed) {
            previewIndex = selectedIndex.coerceIn(items.indices)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            spotlightHolder.view?.cancelSpotlight()
            spotlightHolder.view = null
            touchMotion.cancel()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(BottomBarHostHeight),
        contentAlignment = Alignment.Center,
    ) {
        val desiredWidth = BottomBarHorizontalPadding * 2 +
            BottomBarItemWidth * items.size +
            BottomBarItemSpacing * (items.size - 1)
        val maximumWidth = maxWidth - BottomBarScreenPadding * 2
        val barWidth = minOf(desiredWidth, maximumWidth.coerceAtLeast(0.dp))
        val itemsAvailableWidth = (
            barWidth - BottomBarHorizontalPadding * 2 -
                BottomBarItemSpacing * (items.size - 1)
            ).coerceAtLeast(0.dp)
        val itemWidth = minOf(BottomBarItemWidth, itemsAvailableWidth / items.size)
        val pressedTargetScale = with(density) {
            colorOsBottomBarPressedScale(
                widthPx = barWidth.toPx(),
                heightPx = BottomBarHeight.toPx(),
            )
        }
        val candyMotion = barWidth.value / BottomBarHeight.value > 2f

        LaunchedEffect(pressed, pressedTargetScale, candyMotion) {
            if (pressed) {
                pressScale.animateTo(
                    targetValue = pressedTargetScale,
                    animationSpec = spring(
                        dampingRatio = 1f,
                        stiffness = if (candyMotion) 438.649f else 631.655f,
                        visibilityThreshold = 0.0001f,
                    ),
                )
            } else {
                pressScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = if (candyMotion) 0.5f else 0.4f,
                        stiffness = 157.914f,
                        visibilityThreshold = 0.0001f,
                    ),
                )
            }
        }

        Box(
            modifier = Modifier
                .width(barWidth)
                .height(BottomBarHeight)
                .graphicsLayer {
                    translationX = touchMotion.translationX
                    translationY = touchMotion.translationY
                    // The native compose-motion aggregator adds each unit's delta from 1.
                    scaleX = pressScale.value + touchMotion.scaleX - 1f
                    scaleY = pressScale.value + touchMotion.scaleY - 1f
                    clip = false
                }
                .pointerInput(items.size, itemWidth, layoutDirection) {
                    val horizontalPaddingPx = BottomBarHorizontalPadding.toPx()
                    val itemSpacingPx = BottomBarItemSpacing.toPx()
                    val itemWidthPx = itemWidth.toPx()
                    val itemTopPx = BottomBarItemTop.toPx()
                    val itemBottomPx = itemTopPx + BottomBarItemHeight.toPx()
                    val maxStretchDistancePx = BottomBarMaxStretchDistance.toPx()
                    val maxDeformDistancePx = BottomBarMaxDeformDistance.toPx()

                    fun itemIndexAt(position: Offset): Int? {
                        if (position.y < itemTopPx || position.y > itemBottomPx) return null
                        val logicalX = if (layoutDirection == LayoutDirection.Ltr) {
                            position.x
                        } else {
                            size.width - position.x
                        }
                        val contentX = logicalX - horizontalPaddingPx
                        if (contentX < 0f) return null
                        val stride = itemWidthPx + itemSpacingPx
                        if (stride <= 0f) return null
                        val index = (contentX / stride).toInt()
                        if (index !in items.indices) return null
                        val xWithinSlot = contentX - index * stride
                        return index.takeIf { xWithinSlot <= itemWidthPx }
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        // This pointer node is inside the animated layer, matching Android View's
                        // inverse-transformed local MotionEvent coordinates used by C17.
                        val downPosition = down.position
                        val downIndex = itemIndexAt(downPosition)
                        var gesturePreview = currentSelectedIndex
                        var lastPosition = down.position
                        var moved = false
                        var released = false
                        val velocityTracker = VelocityTracker().apply {
                            addPosition(down.uptimeMillis, down.position)
                        }

                        touchMotion.beginDrag(maxStretchDistancePx)
                        pressed = true
                        spotlightHolder.view?.spotlightDown(downPosition.x, downPosition.y)

                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null) break
                                lastPosition = change.position
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                val localPosition = lastPosition
                                val rawDrag = Offset(
                                    x = touchMotion.correctedDeltaX(
                                        localPosition.x - downPosition.x,
                                    ),
                                    y = touchMotion.correctedDeltaY(
                                        localPosition.y - downPosition.y,
                                    ),
                                )
                                if (!moved && (lastPosition - down.position).getDistance() > viewConfiguration.touchSlop) {
                                    moved = true
                                }
                                if (change.pressed) {
                                    spotlightHolder.view?.spotlightMove(localPosition.x, localPosition.y)
                                    // Native C17 changes the preview as soon as the pointer enters
                                    // another child. Its 5px dead zone only gates rubber/deform.
                                    itemIndexAt(localPosition)?.let { index ->
                                        gesturePreview = index
                                        previewIndex = index
                                    }
                                    if (
                                        abs(rawDrag.x) >= BottomBarMotionDeadZonePx ||
                                        abs(rawDrag.y) >= BottomBarMotionDeadZonePx
                                    ) {
                                        val deform = colorOsBottomBarDeform(
                                            rawDrag = rawDrag,
                                            widthPx = size.width.toFloat(),
                                            heightPx = size.height.toFloat(),
                                            maxDistancePx = maxDeformDistancePx,
                                        )
                                        touchMotion.dragTo(
                                            target = BottomBarMotionTarget(
                                                translationX = colorOsRubberBand(
                                                    offset = rawDrag.x,
                                                    curveRatio = BottomBarRubberCurveRatio,
                                                    maxDistancePx = maxStretchDistancePx,
                                                ),
                                                translationY = colorOsRubberBand(
                                                    offset = rawDrag.y,
                                                    curveRatio = BottomBarRubberCurveRatio,
                                                    maxDistancePx = maxStretchDistancePx,
                                                ),
                                                scaleX = deform.scaleX,
                                                scaleY = deform.scaleY,
                                            ),
                                        )
                                    }
                                } else {
                                    released = true
                                    break
                                }
                            }
                        } finally {
                            val localPosition = lastPosition
                            if (released) {
                                spotlightHolder.view?.spotlightUp(localPosition.x, localPosition.y)
                            } else {
                                spotlightHolder.view?.cancelSpotlight()
                            }
                            val releaseVelocity = if (released) {
                                runCatching { velocityTracker.calculateVelocity() }.getOrNull()
                            } else {
                                null
                            }
                            touchMotion.release(
                                velocityX = releaseVelocity?.x ?: 0f,
                                velocityY = releaseVelocity?.y ?: 0f,
                                maximumStretchDistance = maxStretchDistancePx,
                            )
                            pressed = false
                        }

                        if (released) {
                            val upPosition = lastPosition
                            val targetIndex = if (moved) {
                                gesturePreview
                            } else {
                                itemIndexAt(upPosition) ?: downIndex
                            }
                            previewIndex = targetIndex ?: currentSelectedIndex
                            targetIndex?.let(currentOnItemSelected)
                        } else {
                            previewIndex = currentSelectedIndex
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { context ->
                    ColorOsBottomBarMaterialView(
                        context = context,
                        darkTheme = darkTheme,
                        fallbackColor = fallbackColor.toArgb(),
                    )
                },
                update = { view ->
                    view.updateAppearance(darkTheme, fallbackColor.toArgb())
                },
                modifier = Modifier.fillMaxSize(),
            )

            // BaseBottomFloatingMenuView draws the spotlight after its material edge but before
            // dispatching child icons/text, so the content stays crisp above the moving light.
            AndroidView(
                factory = { context ->
                    ColorOsTopBarSpotlightView(
                        context = context,
                        darkTheme = darkTheme,
                    ).also { spotlightHolder.view = it }
                },
                update = { view ->
                    view.updateAppearance(darkTheme)
                    spotlightHolder.view = view
                },
                modifier = Modifier.fillMaxSize(),
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = BottomBarHorizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(BottomBarItemSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { index, item ->
                    val itemSelected = index == previewIndex
                    val itemColor = if (itemSelected) selectedColor else unselectedColor
                    Column(
                        modifier = Modifier
                            .width(itemWidth)
                            .height(BottomBarItemHeight)
                            .clearAndSetSemantics {
                                selected = itemSelected
                                role = Role.Tab
                                contentDescription = item.label
                                onClick(label = item.label) {
                                    currentOnItemSelected(index)
                                    true
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(modifier = Modifier.height(BottomBarIconTopMargin))
                        Image(
                            imageVector = if (itemSelected) {
                                selectedIcons?.getOrNull(index) ?: item.icon
                            } else {
                                unselectedIcons?.getOrNull(index) ?: item.icon
                            },
                            contentDescription = null,
                            modifier = Modifier.size(BottomBarIconSize),
                            colorFilter = ColorFilter.tint(itemColor),
                        )
                        Spacer(modifier = Modifier.height(BottomBarIconTitleGap))
                        BasicText(
                            text = item.label,
                            modifier = Modifier.fillMaxWidth(),
                            style = TextStyle(
                                color = itemColor,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Medium,
                                fontSize = exactTitleSize,
                                textAlign = TextAlign.Center,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberColorOs17BottomBarTouchMotion(): ColorOs17BottomBarTouchMotion {
    return remember { ColorOs17BottomBarTouchMotion() }
}

/** Retargetable bottom-bar spring for drag translation and deformation. */
private class ColorOs17BottomBarTouchMotion {
    var translationX by mutableFloatStateOf(0f)
        private set
    var translationY by mutableFloatStateOf(0f)
        private set
    var scaleX by mutableFloatStateOf(1f)
        private set
    var scaleY by mutableFloatStateOf(1f)
        private set

    private val translationXAnimation = SpringAnimation(FloatValueHolder(0f))
    private val translationYAnimation = SpringAnimation(FloatValueHolder(0f))
    private val scaleXAnimation = SpringAnimation(FloatValueHolder(1f))
    private val scaleYAnimation = SpringAnimation(FloatValueHolder(1f))
    private val animations = listOf(
        translationXAnimation,
        translationYAnimation,
        scaleXAnimation,
        scaleYAnimation,
    )
    private var baseDistanceX = 0f
    private var baseDistanceY = 0f

    init {
        attach(
            animation = translationXAnimation,
            finalPosition = 0f,
            minimumVisibleChange = BottomBarRubberVisibilityThreshold,
            onUpdate = { translationX = it },
        )
        attach(
            animation = translationYAnimation,
            finalPosition = 0f,
            minimumVisibleChange = BottomBarRubberVisibilityThreshold,
            onUpdate = { translationY = it },
        )
        attach(
            animation = scaleXAnimation,
            finalPosition = 1f,
            minimumVisibleChange = BottomBarDeformVisibilityThreshold,
            onUpdate = { scaleX = it },
        )
        attach(
            animation = scaleYAnimation,
            finalPosition = 1f,
            minimumVisibleChange = BottomBarDeformVisibilityThreshold,
            onUpdate = { scaleY = it },
        )
    }

    private fun attach(
        animation: SpringAnimation,
        finalPosition: Float,
        minimumVisibleChange: Float,
        onUpdate: (Float) -> Unit,
    ) {
        animation.spring = SpringForce(finalPosition)
        animation.minimumVisibleChange = minimumVisibleChange
        animation.addUpdateListener { _, value, _ -> onUpdate(value) }
    }

    private fun configureSprings(stiffness: Float, dampingRatio: Float) {
        animations.forEach { animation ->
            animation.spring.stiffness = stiffness
            animation.spring.dampingRatio = dampingRatio
        }
    }

    fun beginDrag(maximumStretchDistance: Float) {
        animations.forEach { it.cancel() }
        configureSprings(BottomBarDragStiffness, BottomBarDragDampingRatio)
        baseDistanceX = colorOsInverseRubberBand(
            result = translationX,
            curveRatio = BottomBarRubberCurveRatio,
            maxDistancePx = maximumStretchDistance,
        )
        baseDistanceY = colorOsInverseRubberBand(
            result = translationY,
            curveRatio = BottomBarRubberCurveRatio,
            maxDistancePx = maximumStretchDistance,
        )
    }

    fun correctedDeltaX(rawDelta: Float): Float = baseDistanceX + rawDelta

    fun correctedDeltaY(rawDelta: Float): Float = baseDistanceY + rawDelta

    fun dragTo(target: BottomBarMotionTarget) {
        translationXAnimation.animateToFinalPosition(target.translationX)
        translationYAnimation.animateToFinalPosition(target.translationY)
        scaleXAnimation.animateToFinalPosition(target.scaleX)
        scaleYAnimation.animateToFinalPosition(target.scaleY)
    }

    fun release(
        velocityX: Float,
        velocityY: Float,
        maximumStretchDistance: Float,
    ) {
        val currentTranslationX = translationX
        val currentTranslationY = translationY
        val currentScaleX = scaleX
        val currentScaleY = scaleY

        animations.forEach { it.cancel() }
        configureSprings(BottomBarHandUpStiffness, BottomBarHandUpDampingRatio)
        startRelease(
            animation = translationXAnimation,
            startValue = currentTranslationX,
            finalPosition = 0f,
            initialVelocity = colorOsHandUpVelocity(
                gestureVelocity = velocityX,
                maximumStretchDistance = maximumStretchDistance,
                restDistance = abs(currentTranslationX),
            ),
        )
        startRelease(
            animation = translationYAnimation,
            startValue = currentTranslationY,
            finalPosition = 0f,
            initialVelocity = colorOsHandUpVelocity(
                gestureVelocity = velocityY,
                maximumStretchDistance = maximumStretchDistance,
                restDistance = abs(currentTranslationY),
            ),
        )
        startRelease(
            animation = scaleXAnimation,
            startValue = currentScaleX,
            finalPosition = 1f,
            initialVelocity = colorOsHandUpVelocity(
                gestureVelocity = velocityX,
                maximumStretchDistance = maximumStretchDistance,
                restDistance = abs(currentScaleX - 1f),
            ),
        )
        startRelease(
            animation = scaleYAnimation,
            startValue = currentScaleY,
            finalPosition = 1f,
            initialVelocity = colorOsHandUpVelocity(
                gestureVelocity = velocityY,
                maximumStretchDistance = maximumStretchDistance,
                restDistance = abs(currentScaleY - 1f),
            ),
        )
    }

    private fun startRelease(
        animation: SpringAnimation,
        startValue: Float,
        finalPosition: Float,
        initialVelocity: Float,
    ) {
        animation.setStartValue(startValue)
        animation.setStartVelocity(initialVelocity)
        animation.animateToFinalPosition(finalPosition)
    }

    fun cancel() {
        animations.forEach { it.cancel() }
    }
}

private data class BottomBarMotionTarget(
    val translationX: Float,
    val translationY: Float,
    val scaleX: Float,
    val scaleY: Float,
) {
    companion object {
        val Rest = BottomBarMotionTarget(
            translationX = 0f,
            translationY = 0f,
            scaleX = 1f,
            scaleY = 1f,
        )
    }
}

private data class BottomBarDeformTarget(
    val scaleX: Float,
    val scaleY: Float,
)

/** C17 TouchMotionRubberBand: M * (1 - 1 / (ratio * |offset| / M + 1)). */
private fun colorOsRubberBand(
    offset: Float,
    curveRatio: Float,
    maxDistancePx: Float,
): Float {
    if (maxDistancePx <= 0f) return 0f
    return (1f - 1f / (curveRatio * abs(offset) / maxDistancePx + 1f)) *
        maxDistancePx * sign(offset)
}

private fun colorOsInverseRubberBand(
    result: Float,
    curveRatio: Float,
    maxDistancePx: Float,
): Float {
    if (maxDistancePx <= 0f) return result
    val magnitude = abs(result)
    if (magnitude >= maxDistancePx) return maxDistancePx * sign(result)
    val remaining = maxDistancePx - magnitude
    if (curveRatio <= 0f || remaining <= 0f) return result
    return maxDistancePx * magnitude / (curveRatio * remaining) * sign(result)
}

/** C17 tracks px/ms, applies a 0.3 gain, then normalizes it by the return distance. */
private fun colorOsHandUpVelocity(
    gestureVelocity: Float,
    maximumStretchDistance: Float,
    restDistance: Float,
): Float {
    if (maximumStretchDistance <= 0f) return 0f
    val trackedVelocity = gestureVelocity / 1_000f * BottomBarVelocityTrackerScale
    return (trackedVelocity / maximumStretchDistance * restDistance).coerceIn(
        -BottomBarMaximumHandUpVelocity,
        BottomBarMaximumHandUpVelocity,
    )
}

private fun colorOsBottomBarDeform(
    rawDrag: Offset,
    widthPx: Float,
    heightPx: Float,
    maxDistancePx: Float,
): BottomBarDeformTarget {
    if (maxDistancePx <= 0f) return BottomBarDeformTarget(1f, 1f)

    val maxScale = colorOsBottomBarMaxDeformScale(widthPx, heightPx)

    fun axisScale(distance: Float): Float {
        val rubberDistance = colorOsRubberBand(
            offset = abs(distance),
            curveRatio = BottomBarDeformCurveRatio,
            maxDistancePx = maxDistancePx,
        )
        return 1f + rubberDistance / maxDistancePx * (maxScale - 1f)
    }

    val absX = abs(rawDrag.x)
    val absY = abs(rawDrag.y)
    val horizontalStretch = axisScale(absX)
    val verticalStretch = axisScale(absY)
    val horizontal = BottomBarDeformTarget(
        scaleX = horizontalStretch,
        scaleY = 1f / horizontalStretch,
    )
    val vertical = BottomBarDeformTarget(
        scaleX = 1f / verticalStretch,
        scaleY = verticalStretch,
    )
    val total = absX + absY
    if (total <= 0f) return BottomBarDeformTarget(1f, 1f)
    if (absY <= 0f) return horizontal
    if (absX <= 0f) return vertical

    val horizontalWeight = absX / total
    val verticalWeight = absY / total
    return BottomBarDeformTarget(
        scaleX = exp(
            ln(horizontal.scaleX) * horizontalWeight +
                ln(vertical.scaleX) * verticalWeight,
        ),
        scaleY = exp(
            ln(horizontal.scaleY) * horizontalWeight +
                ln(vertical.scaleY) * verticalWeight,
        ),
    )
}

private fun colorOsBottomBarMaxDeformScale(widthPx: Float, heightPx: Float): Float {
    if (widthPx <= 0f || heightPx <= 0f) return 1f
    val area = widthPx * heightPx
    if (area <= BottomBarDeformReferenceAreaPx) return BottomBarMaxDeformScale

    val aspectRatio = max(widthPx, heightPx) / min(widthPx, heightPx)
    val decay = (
        BottomBarDeformAreaDecay * ln(area / BottomBarDeformReferenceAreaPx) +
            BottomBarDeformAspectDecay * (aspectRatio - 1f)
        ).coerceAtMost(1f)
    return max(
        (BottomBarMaxDeformScale - BottomBarMinDeformScale) * (1f - decay) +
            BottomBarMinDeformScale,
        BottomBarMinDeformScale,
    )
}

/** ColorOS PressPhysicsEngine defaults, including its cubic (0,0,0,1) size mapping. */
private fun colorOsBottomBarPressedScale(widthPx: Float, heightPx: Float): Float {
    if (widthPx <= 0f || heightPx <= 0f) return 1f

    fun colorOsSizeInterpolation(value: Float): Float {
        val input = value.coerceIn(0f, 1f)
        val t = input.toDouble().pow(1.0 / 3.0).toFloat()
        return 3f * t * t - 2f * t * t * t
    }

    val area = widthPx * heightPx
    val areaProgress = ((area.coerceIn(20f * 20f, 150f * 150f) - 20f * 20f) /
        (150f * 150f - 20f * 20f))
    val areaScale = 0.85f + colorOsSizeInterpolation(areaProgress) * (0.97f - 0.85f)

    val ratio = max(widthPx, heightPx) / min(widthPx, heightPx)
    if (ratio <= 2f) return areaScale.coerceIn(0.85f, 0.97f)
    val aspectProgress = ((ratio - 2f) / (15f - 2f)).coerceIn(0f, 1f)
    val aspectScale = 0.85f + colorOsSizeInterpolation(aspectProgress) * (1f - 0.85f)
    return ((areaScale + aspectScale) / 2f).coerceIn(0.85f, 0.97f)
}

private class BottomBarSpotlightHolder {
    var view: ColorOsTopBarSpotlightView? = null
}

private val BottomBarHostHeight = 88.dp
private val BottomBarHeight = 56.dp
private val BottomBarScreenPadding = 16.dp
private val BottomBarHorizontalPadding = 12.dp
private val BottomBarItemHeight = 48.dp
private val BottomBarItemTop = 4.dp
private val BottomBarItemWidth = 70.dp
private val BottomBarItemSpacing = 8.dp
private val BottomBarIconSize = 24.dp
private val BottomBarIconTopMargin = 4.dp
private val BottomBarIconTitleGap = 2.dp
private val BottomBarMaxStretchDistance = 28.dp
private val BottomBarMaxDeformDistance = 150.dp

private const val BottomBarMotionDeadZonePx = 5f
private const val BottomBarRubberCurveRatio = 0.05f
private const val BottomBarDeformCurveRatio = 0.55f
private const val BottomBarVelocityTrackerScale = 0.3f
private const val BottomBarMaximumHandUpVelocity = 1_000f
private const val BottomBarMaxDeformScale = 1.2f
private const val BottomBarMinDeformScale = 1.03f
private const val BottomBarDeformReferenceAreaPx = 38_416f
private const val BottomBarDeformAreaDecay = 0.35f
private const val BottomBarDeformAspectDecay = 0.015f
private const val BottomBarRubberVisibilityThreshold = 0.5f
private const val BottomBarDeformVisibilityThreshold = 0.0005f

// stiffness=(2pi/response)^2 and dampingRatio=1-bounce, from C17's spring adapter.
private val BottomBarDragStiffness = ((2.0 * PI / 0.15).pow(2.0)).toFloat()
private const val BottomBarDragDampingRatio = 0.85f
private val BottomBarHandUpStiffness = ((2.0 * PI / 0.45).pow(2.0)).toFloat()
private const val BottomBarHandUpDampingRatio = 0.35f
