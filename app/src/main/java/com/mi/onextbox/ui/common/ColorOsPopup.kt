package com.mi.onextbox.ui.common

import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

// ColorOS 17 (OS version > 37): coui_popup_round_frame_layout_corner_radius.
private val ColorOsPopupCornerRadius = 28.dp
private const val ColorOsPopupSmoothWeight = 2.5f
private val ColorOsPopupEnterSpring = spring<Float>(
    dampingRatio = 0.8f,
    stiffness = 322.27f,
    visibilityThreshold = 0.0001f,
)
private val ColorOsPopupExitScaleSpring = spring<Float>(
    dampingRatio = 1f,
    stiffness = 438.65f,
    visibilityThreshold = 0.0001f,
)
private val ColorOsPopupExitAlphaSpring = spring<Float>(
    dampingRatio = 1f,
    stiffness = 631.65f,
    visibilityThreshold = 0.002f,
)
// COUIPopupTouchMotionAnimator MAX tier. SpringData converts with
// dampingRatio = 1 - bounce * .5 and stiffness = 39.47 / response^2.
private const val ColorOsPopupDragDampingRatio = 0.925f
private const val ColorOsPopupDragStiffness = 1_754.22f
private const val ColorOsPopupReleaseDampingRatio = 0.675f
private const val ColorOsPopupReleaseStiffness = 194.91f
private const val ColorOsPopupRubberCurveRatio = 0.05f
private const val ColorOsPopupDeformCurveRatio = 0.55f
private const val ColorOsPopupMaximumScale = 1.2f
private const val ColorOsPopupScaleAreaThreshold = 38_416f

private class PopupTransformOrigin {
    var x: Float = 1f
    var y: Float = 0f
}

private data class PopupTouchTarget(
    val translationX: Float,
    val translationY: Float,
    val scaleX: Float,
    val scaleY: Float,
)

private fun rubberBand(distance: Float, ratio: Float, maximumDistance: Float): Float {
    if (distance == 0f || maximumDistance <= 0f) return 0f
    return sign(distance) *
        (1f - 1f / (abs(distance) * ratio / maximumDistance + 1f)) *
        maximumDistance
}

private fun inverseRubberBand(value: Float, ratio: Float, maximumDistance: Float): Float {
    val absoluteValue = abs(value).coerceAtMost(maximumDistance - 0.001f)
    if (absoluteValue == 0f || maximumDistance <= 0f || ratio <= 0f) return 0f
    return sign(value) * absoluteValue * maximumDistance /
        (ratio * (maximumDistance - absoluteValue))
}

private fun popupMaximumDeformScale(width: Float, height: Float): Float {
    val minimumSide = min(width, height)
    val area = width * height
    if (minimumSide <= 0f || area <= ColorOsPopupScaleAreaThreshold) {
        return ColorOsPopupMaximumScale
    }
    val aspectRatio = max(width, height) / minimumSide
    val attenuation = min(
        0.35f * ln(area / ColorOsPopupScaleAreaThreshold) + 0.015f * (aspectRatio - 1f),
        1f,
    )
    return max(
        (ColorOsPopupMaximumScale - 1.03f) * (1f - attenuation) + 1.03f,
        1.03f,
    )
}

private fun popupTouchTarget(
    deltaX: Float,
    deltaY: Float,
    width: Float,
    height: Float,
    maximumStretchDistance: Float,
    maximumDeformDistance: Float,
): PopupTouchTarget {
    val x = deltaX
    val y = deltaY
    val translationX = rubberBand(x, ColorOsPopupRubberCurveRatio, maximumStretchDistance)
    val translationY = rubberBand(y, ColorOsPopupRubberCurveRatio, maximumStretchDistance)
    val maximumScale = popupMaximumDeformScale(width, height)
    val xRubber = abs(rubberBand(x, ColorOsPopupDeformCurveRatio, maximumDeformDistance))
    val yRubber = abs(rubberBand(y, ColorOsPopupDeformCurveRatio, maximumDeformDistance))
    val xAxisScale = 1f + xRubber / maximumDeformDistance * (maximumScale - 1f)
    val yAxisScale = 1f + yRubber / maximumDeformDistance * (maximumScale - 1f)
    val distanceSum = abs(x) + abs(y)
    if (distanceSum == 0f) {
        return PopupTouchTarget(translationX, translationY, 1f, 1f)
    }

    // C17 logarithmically blends the X/Y deformation pairs so their area stays constant.
    val xWeight = abs(x) / distanceSum
    val yWeight = abs(y) / distanceSum
    val scaleX = exp(ln(xAxisScale) * xWeight + ln(1f / yAxisScale) * yWeight)
    val scaleY = exp(ln(1f / xAxisScale) * xWeight + ln(yAxisScale) * yWeight)
    return PopupTouchTarget(translationX, translationY, scaleX, scaleY)
}

private class PopupTouchMotionState {
    var translationX by mutableFloatStateOf(0f)
        private set
    var translationY by mutableFloatStateOf(0f)
        private set
    var scaleX by mutableFloatStateOf(1f)
        private set
    var scaleY by mutableFloatStateOf(1f)
        private set
    var active by mutableStateOf(false)
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
    private var dragging = false
    private var moved = false
    private var baseDistanceX = 0f
    private var baseDistanceY = 0f

    init {
        attach(
            animation = translationXAnimation,
            finalPosition = 0f,
            minimumVisibleChange = 0.5f,
            onUpdate = { translationX = it },
        )
        attach(
            animation = translationYAnimation,
            finalPosition = 0f,
            minimumVisibleChange = 0.5f,
            onUpdate = { translationY = it },
        )
        attach(
            animation = scaleXAnimation,
            finalPosition = 1f,
            minimumVisibleChange = 0.0005f,
            onUpdate = { scaleX = it },
        )
        attach(
            animation = scaleYAnimation,
            finalPosition = 1f,
            minimumVisibleChange = 0.0005f,
            onUpdate = { scaleY = it },
        )
        animations.forEach { animation ->
            animation.addEndListener { _, canceled, _, _ ->
                if (!canceled && !dragging && animations.none { it.isRunning }) {
                    active = false
                }
            }
        }
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
        configureSprings(ColorOsPopupDragStiffness, ColorOsPopupDragDampingRatio)
        baseDistanceX = inverseRubberBand(
            translationX,
            ColorOsPopupRubberCurveRatio,
            maximumStretchDistance,
        )
        baseDistanceY = inverseRubberBand(
            translationY,
            ColorOsPopupRubberCurveRatio,
            maximumStretchDistance,
        )
        dragging = true
        moved = false
        active = true
    }

    fun dragTo(target: PopupTouchTarget) {
        moved = true
        translationXAnimation.animateToFinalPosition(target.translationX)
        translationYAnimation.animateToFinalPosition(target.translationY)
        scaleXAnimation.animateToFinalPosition(target.scaleX)
        scaleYAnimation.animateToFinalPosition(target.scaleY)
    }

    fun correctedDeltaX(rawDelta: Float): Float = baseDistanceX + rawDelta

    fun correctedDeltaY(rawDelta: Float): Float = baseDistanceY + rawDelta

    fun release() {
        if (!active || !dragging) return
        dragging = false
        configureSprings(ColorOsPopupReleaseStiffness, ColorOsPopupReleaseDampingRatio)
        val alreadyAtRest = !moved &&
            abs(translationX) < 0.5f && abs(translationY) < 0.5f &&
            abs(scaleX - 1f) < 0.0005f && abs(scaleY - 1f) < 0.0005f
        if (alreadyAtRest) {
            active = false
            return
        }
        translationXAnimation.animateToFinalPosition(0f)
        translationYAnimation.animateToFinalPosition(0f)
        scaleXAnimation.animateToFinalPosition(1f)
        scaleYAnimation.animateToFinalPosition(1f)
    }

    fun dispose() {
        dragging = false
        animations.forEach { it.cancel() }
        active = false
    }
}

@Composable
fun ColorOsPopup(
    show: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    minWidth: Dp = 168.dp,
    maxWidth: Dp = 256.dp,
    maxHeight: Dp? = null,
    anchorCenterX: Float? = null,
    content: @Composable () -> Unit,
) {
    var popupMounted by remember { mutableStateOf(show) }
    val scaleProgress = remember { Animatable(0f) }
    val alphaProgress = remember { Animatable(0f) }

    LaunchedEffect(show) {
        if (show) {
            popupMounted = true
            coroutineScope {
                launch { scaleProgress.animateTo(1f, ColorOsPopupEnterSpring) }
                launch { alphaProgress.animateTo(1f, ColorOsPopupEnterSpring) }
            }
        } else if (popupMounted) {
            coroutineScope {
                launch { scaleProgress.animateTo(0f, ColorOsPopupExitScaleSpring) }
                launch { alphaProgress.animateTo(0f, ColorOsPopupExitAlphaSpring) }
            }
            popupMounted = false
        }
    }

    if (!popupMounted) return

    val density = LocalDensity.current
    val transformOrigin = remember { PopupTransformOrigin() }
    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    var menuPosition by remember { mutableStateOf(IntOffset.Zero) }
    val positionProvider = remember(density, transformOrigin, anchorCenterX, menuSize) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset = with(density) {
                val horizontalMargin = 16.dp.roundToPx()
                val verticalGap = 8.dp.roundToPx()
                // PreciseClickHelper records the DOWN point. COUIPopupListWindow collapses the
                // anchor to that point, centers the popup on it, then clamps at the window edges.
                // Settings dropdowns retain their row-based vertical anchor and only use the
                // precise horizontal coordinate.
                val horizontalAnchor = anchorCenterX ?: (anchorBounds.left + anchorBounds.width / 2f)
                val unclampedX = (horizontalAnchor - menuSize.width / 2f).toInt()
                val maxX = (windowSize.width - menuSize.width - horizontalMargin)
                    .coerceAtLeast(horizontalMargin)
                val x = unclampedX.coerceIn(horizontalMargin, maxX)
                val below = anchorBounds.bottom + verticalGap
                val above = anchorBounds.top - menuSize.height - verticalGap
                val y = if (below + menuSize.height <= windowSize.height - horizontalMargin) {
                    below
                } else {
                    above.coerceAtLeast(horizontalMargin)
                }
                // ColorOS PopupMenuDomain uses the anchor centre, clamped into the popup, as the
                // horizontal scale pivot. The vertical pivot is the edge nearest the anchor.
                transformOrigin.x = if (menuSize.width > 0) {
                    ((horizontalAnchor - x) / menuSize.width)
                        .coerceIn(0f, 1f)
                } else {
                    if (layoutDirection == LayoutDirection.Ltr) 1f else 0f
                }
                transformOrigin.y = if (y >= anchorBounds.bottom) 0f else 1f
                menuPosition = IntOffset(x, y)
                // COUIPopupListWindow occupies the application window; only its menu is offset.
                // The transparent host leaves room for the native elevation shadow.
                IntOffset.Zero
            }
        }
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            clippingEnabled = false,
        ),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.matchParentSize().pointerInput(onDismissRequest) {
                detectTapGestures { onDismissRequest() }
            })
            ColorOsPopupSurface(
            scaleProgress = scaleProgress.value,
            alphaProgress = alphaProgress.value,
            transformOrigin = transformOrigin,
            modifier = modifier
                .offset { menuPosition }
                .width(IntrinsicSize.Max)
                .widthIn(min = minWidth, max = maxWidth)
                .onSizeChanged { menuSize = it }
                .then(if (maxHeight != null) Modifier.heightIn(max = maxHeight) else Modifier),
            content = content,
        )
        }
    }
}

/** Reuses the native popup material and edge shader inside the clipped activation preview. */
@Composable
internal fun ColorOsPopupMiniatureMaterial(
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    key(darkTheme, density.density) {
        AndroidView(
            factory = { context ->
                ColorOsPopupMaterialView(
                    context = context,
                    darkTheme = darkTheme,
                    cornerRadiusDp = 19f,
                ).also { view ->
                    view.post {
                        applyOplusPopupBlur(
                            view = view,
                            darkTheme = darkTheme,
                            density = density.density,
                            cornerRadiusDp = 19f,
                        )
                    }
                }
            },
            modifier = modifier,
        )
    }
}

@Composable
private fun ColorOsPopupSurface(
    scaleProgress: Float,
    alphaProgress: Float,
    transformOrigin: PopupTransformOrigin,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val darkTheme = COUITheme.colorScheme.background.luminance() < 0.5f
    val touchMotion = remember { PopupTouchMotionState() }
    val blurViewHolder = remember { PopupBlurViewHolder() }

    DisposableEffect(darkTheme, density.density) {
        onDispose {
            touchMotion.dispose()
            blurViewHolder.view?.cancelSpotlight()
            blurViewHolder.view?.background = null
            blurViewHolder.view = null
        }
    }

    val fallbackColor = if (darkTheme) Color(0xFF1E1E1E) else Color.White
    val nativeShadowSupported = remember {
        runCatching { Class.forName("com.oplus.view.OplusView") }.isSuccess
    }
    val nativeShadow = nativeShadowSupported
    val renderedScaleX = scaleProgress * touchMotion.scaleX
    val renderedScaleY = scaleProgress * touchMotion.scaleY
    val renderedOrigin = if (touchMotion.active) {
        TransformOrigin.Center
    } else {
        TransformOrigin(transformOrigin.x, transformOrigin.y)
    }
    val contentMotion = Modifier.graphicsLayer {
        alpha = alphaProgress.coerceIn(0f, 1f)
    }
    fun updateMaterialMotion(view: ColorOsPopupMaterialView) {
        view.updateTransition(
            progress = alphaProgress,
            scaleX = renderedScaleX,
            scaleY = renderedScaleY,
        )
    }
    val interactionModifier = modifier
            .pointerInput(density.density) {
                val maximumStretchDistance = 28.dp.toPx()
                val maximumDeformDistance = 150.dp.toPx()
                val deadZone = 5.dp.toPx()
                fun spotlightLocalPosition(position: Offset): Offset {
                    // pointerInput wraps graphicsLayer, while Android's transformed child receives
                    // MotionEvents through the inverse of its current matrix. Recreate that mapping
                    // so the light remains exactly under the finger while the card stretches.
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    val renderedScaleX = (scaleProgress * touchMotion.scaleX).coerceAtLeast(0.001f)
                    val renderedScaleY = (scaleProgress * touchMotion.scaleY).coerceAtLeast(0.001f)
                    return Offset(
                        x = centerX +
                            (position.x - touchMotion.translationX - centerX) / renderedScaleX,
                        y = centerY +
                            (position.y - touchMotion.translationY - centerY) / renderedScaleY,
                    )
                }
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        val startPosition = down.position
                        var lastPosition = startPosition
                        touchMotion.beginDrag(maximumStretchDistance)
                        spotlightLocalPosition(startPosition).let { position ->
                            blurViewHolder.view?.spotlightDown(position.x, position.y)
                        }

                        try {
                            var tracking = true
                            while (tracking) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change != null) {
                                    lastPosition = change.position
                                }
                                if (change == null || event.changes.size > 1 || !change.pressed) {
                                    tracking = false
                                    continue
                                }

                                // Spotlight receives every MOVE, including the deformation
                                // deadzone, just like RoundFrameLayout.dispatchTouchEvent().
                                spotlightLocalPosition(change.position).let { position ->
                                    blurViewHolder.view?.spotlightMove(position.x, position.y)
                                }

                                val rawDeltaX = change.position.x - startPosition.x
                                val rawDeltaY = change.position.y - startPosition.y
                                // C17 ignores the MOVE only while both axes remain in the
                                // deadzone. Once crossed it forwards the complete raw distance.
                                if (abs(rawDeltaX) < deadZone && abs(rawDeltaY) < deadZone) {
                                    continue
                                }
                                touchMotion.dragTo(
                                    popupTouchTarget(
                                        deltaX = touchMotion.correctedDeltaX(rawDeltaX),
                                        deltaY = touchMotion.correctedDeltaY(rawDeltaY),
                                        width = size.width.toFloat(),
                                        height = size.height.toFloat(),
                                        maximumStretchDistance = maximumStretchDistance,
                                        maximumDeformDistance = maximumDeformDistance,
                                    ),
                                )
                            }
                        } finally {
                            spotlightLocalPosition(lastPosition).let { position ->
                                blurViewHolder.view?.spotlightUp(position.x, position.y)
                            }
                            touchMotion.release()
                        }
                    }
                }
            }

    Box(
        // Keep the native blur and elevation in the same transform as the content,
        // without an offscreen alpha buffer around the AndroidView.
        modifier = interactionModifier.graphicsLayer {
            scaleX = renderedScaleX
            scaleY = renderedScaleY
            translationX = touchMotion.translationX
            translationY = touchMotion.translationY
            this.transformOrigin = renderedOrigin
        },
    ) {
        Box(Modifier.matchParentSize().then(contentMotion).shadow(
            elevation = if (nativeShadow) 0.dp else if (darkTheme) 12.dp else 30.dp,
            shape = ColorOsPopupSmoothShape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = if (darkTheme) .30f else 48f / 255f),
            spotColor = Color.Black.copy(alpha = if (darkTheme) .42f else 128f / 255f),
        ).graphicsLayer {
            shape = ColorOsPopupSmoothShape
            clip = true
        }.background(fallbackColor))
        // BackgroundBlurDrawable must stay out of Compose's offscreen alpha layer.
        // Its drawable, outline and edge alpha are synchronized separately.
        key(darkTheme, density.density) {
            AndroidView(
                factory = { context ->
                    ColorOsPopupMaterialView(context, darkTheme, nativeShadow = nativeShadow).also { blurView ->
                        updateMaterialMotion(blurView)
                        blurViewHolder.view = blurView
                        blurView.post {
                            applyOplusPopupBlur(
                                view = blurView,
                                darkTheme = darkTheme,
                                density = density.density,
                            )
                        }
                    }
                },
                update = { view -> updateMaterialMotion(view) },
                modifier = Modifier.matchParentSize(),
            )
        }
        Box(contentMotion.graphicsLayer {
            shape = ColorOsPopupSmoothShape
            clip = true
        }) {
            content()
        }
    }
}

private class PopupBlurViewHolder {
    var view: ColorOsPopupMaterialView? = null
}

private fun applyOplusPopupBlur(
    view: View,
    darkTheme: Boolean,
    density: Float,
    cornerRadiusDp: Float = ColorOsPopupCornerRadius.value,
): Boolean = runCatching {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    if (Settings.System.getInt(view.context.contentResolver, "system_material_blur_enable", 0) != 1) {
        return false
    }
    val windowManager = view.context.getSystemService(WindowManager::class.java)
    if (!windowManager.isCrossWindowBlurEnabled) return false

    val managerClass = Class.forName("com.oplus.view.ViewRootManager")
    val blurParamClass = Class.forName("com.oplus.graphics.OplusBlurParam")
    val manager = managerClass.getConstructor(View::class.java).newInstance(view)
    val blurParam = blurParamClass.getConstructor().newInstance()

    blurParamClass.getMethod("setBlurType", Int::class.javaPrimitiveType).invoke(blurParam, 2)
    val hdrMaterial = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        runCatching { view.display?.hdrSdrRatio ?: 1f }.getOrDefault(1f) > 1f
    val materialMode: Int
    val blendColor: Int
    val mixColor: Int
    if (hdrMaterial) {
        materialMode = if (darkTheme) 2 else 3
        blendColor = if (darkTheme) 0x990F0F0F.toInt() else 0xC8868686.toInt()
        mixColor = if (darkTheme) 0x50262626.toInt() else 0xDC7A7A7A.toInt()
    } else {
        // RoundFrameLayout.applyBlurColorParams() uses material mode 4 on SDR. Modes 2/3
        // are HDR-only and render a visible rectangular material patch on an SDR display.
        materialMode = 4
        blendColor = if (darkTheme) 0x99262626.toInt() else 0xBFEEEEEE.toInt()
        mixColor = if (darkTheme) 0x99262626.toInt() else 0x99CDCDCD.toInt()
    }
    blurParamClass.getMethod(
        "setMaterialParams",
        Int::class.javaPrimitiveType,
        FloatArray::class.java,
        FloatArray::class.java,
    ).invoke(blurParam, materialMode, colorToFloats(blendColor), colorToFloats(mixColor))
    runCatching {
        blurParamClass.getMethod("setSmoothCornerWeight", Float::class.javaPrimitiveType)
            .invoke(blurParam, ColorOsPopupSmoothWeight)
    }
    runCatching {
        blurParamClass.getMethod("setSmoothCornerType", Int::class.javaPrimitiveType)
            .invoke(blurParam, 1)
    }

    managerClass.getMethod("setBlurParams", blurParamClass).invoke(manager, blurParam)
    managerClass.getMethod("setBlurRadius", Int::class.javaPrimitiveType)
        // ColorOS 17 RoundFrameLayout.DEFAULT_BLUR_RADIUS_PX is a literal pixel value.
        .invoke(manager, 160)
    val radius = cornerRadiusDp * density
    val setCornerRadius = managerClass.getMethod(
        "setCornerRadius",
        Float::class.javaPrimitiveType,
        Float::class.javaPrimitiveType,
        Float::class.javaPrimitiveType,
        Float::class.javaPrimitiveType,
    )
    setCornerRadius.invoke(manager, radius, radius, radius, radius)
    managerClass.getMethod("setColor", Int::class.javaPrimitiveType)
        .invoke(manager, Color.Transparent.toArgb())

    val blurDrawable = managerClass.getMethod("getBackgroundBlurDrawable").invoke(manager) as? Drawable
        ?: return false
    blurDrawable.alpha = 255
    view.background = blurDrawable
    if (view is ColorOsPopupMaterialView) {
        view.syncTransitionAlpha()
        view.bindBlurCornerUpdater { scaledRadius ->
            setCornerRadius.invoke(manager, scaledRadius, scaledRadius, scaledRadius, scaledRadius)
        }
    }
    view.invalidate()
    true
}.getOrDefault(false)

private fun colorToFloats(color: Int): FloatArray = floatArrayOf(
    ((color shr 16) and 0xFF) / 255f,
    ((color shr 8) and 0xFF) / 255f,
    (color and 0xFF) / 255f,
    ((color ushr 24) and 0xFF) / 255f,
)
