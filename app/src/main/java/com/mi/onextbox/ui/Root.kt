package com.mi.onextbox.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.NavDisplayTransitionEffects
import androidx.navigation3.ui.defaultPopTransitionSpec
import androidx.navigation3.ui.defaultTransitionSpec
import androidx.savedstate.serialization.SavedStateConfiguration
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LsposedScopeRequester
import com.mi.onextbox.ui.layout.BottomNavigationBar
import com.mi.onextbox.ui.layout.Material3ExpressiveBottomNavigationBar
import com.mi.onextbox.ui.layout.BlurredChromeBar
import com.mi.onextbox.ui.layout.LocalChromeNativeBlurEnabled
import com.mi.onextbox.ui.layout.LiquidGlassBottomNavigationBar
import com.mi.onextbox.ui.layout.Page
import com.mi.onextbox.ui.layout.rememberChromeBlurBackdrop
import com.mi.onextbox.ui.platform.findActivity
import com.mi.onextbox.ui.screens.FeatureMainRoute
import com.mi.onextbox.ui.screens.ToolsMainRoute
import com.mi.onextbox.ui.screens.FeatureLaunchIcon
import com.mi.onextbox.ui.screens.FeatureLaunchOrigin
import com.mi.onextbox.ui.screens.FeaturePageAuroraBackground
import com.mi.onextbox.ui.screens.FeaturePageVideoBackground
import com.mi.onextbox.ui.screens.FeaturePageMode
import com.mi.onextbox.ui.screens.FeatureSubRoute
import com.mi.onextbox.ui.screens.rememberFeatureBackgroundPlaybackState
import com.mi.onextbox.ui.settings.AboutMainRoute
import com.mi.onextbox.ui.settings.AboutPageMode
import com.mi.onextbox.ui.settings.AboutSubRoute
import com.mi.onextbox.ui.settings.SoftwareUpdateUiState
import com.mi.onextbox.ui.settings.rememberSoftwareUpdateUiState
import com.mi.onextbox.ui.common.AppLocale
import com.mi.onextbox.ui.common.AppThemeMode
import com.mi.onextbox.ui.common.AppUiStyle
import com.mi.onextbox.ui.common.Material3ExpressiveAppTheme
import com.mi.onextbox.ui.common.ColorOs17AppTheme
import com.mi.onextbox.ui.common.LocalAppUiStyle
import com.mi.onextbox.ui.common.LocalMaterialSwitchIconsEnabled
import com.mi.onextbox.ui.common.isMonet
import com.mi.onextbox.ui.common.AppIcons
import com.mi.onextbox.ui.common.AssistantScreenOption
import com.mi.onextbox.ui.common.BottomTab
import com.mi.onextbox.ui.common.FpsMonitorOverlay
import com.mi.onextbox.ui.common.bottomTabs
import com.mi.onextbox.ui.common.readCachedRootAccessInfo
import com.mi.onextbox.ui.common.rememberHapticClick
import com.kyant.backdrop.backdrops.LayerBackdrop as LiquidLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop as liquidLayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop as rememberLiquidLayerBackdrop
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.basic.ListPopup
import io.github.suqi8.coui.kmp.basic.NavigationBar
import io.github.suqi8.coui.kmp.basic.NavigationBarDisplayMode
import io.github.suqi8.coui.kmp.basic.NavigationItem
import io.github.suqi8.coui.kmp.basic.PopupPositionProvider
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.Switch
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronForward
import io.github.suqi8.coui.kmp.icon.extended.Ok
import io.github.suqi8.coui.kmp.icon.extended.Back
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.ThemeColorSpec
import io.github.suqi8.coui.kmp.theme.ThemeController
import io.github.suqi8.coui.kmp.theme.ThemePaletteStyle
import io.github.suqi8.coui.kmp.utils.COUIPopupUtils.Companion.COUIPopupHost
import io.github.suqi8.coui.kmp.utils.PressFeedbackType
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

// ColorOS 17.3.12 desktop, phone profile, AppLaunchAnimSpeedHandler level 1.
// These are the DynamicAnimation spring channels used by LauncherAnimEngine's
// CustomRectFSpringAnim path. Retargeting the same Animatable preserves the live
// value and velocity, matching C17's performReverse() behavior.
private const val C17_OPEN_GEOMETRY_STIFFNESS = 420f
private const val C17_OPEN_GEOMETRY_DAMPING = 1.12f
private const val C17_OPEN_RADIUS_STIFFNESS = 400f
private const val C17_OPEN_RADIUS_DAMPING = 1f
private const val C17_OPEN_WORKSPACE_STIFFNESS = 420f
private const val C17_OPEN_WORKSPACE_DAMPING = 1f
private const val C17_OPEN_BLUR_STIFFNESS = 240f
private const val C17_OPEN_BLUR_DAMPING = 1f

private const val C17_CLOSE_GEOMETRY_STIFFNESS = 160f
private const val C17_CLOSE_GEOMETRY_DAMPING = 0.84f
private const val C17_CLOSE_RADIUS_STIFFNESS = 300f
private const val C17_CLOSE_RADIUS_DAMPING = 1f
private const val C17_CLOSE_WORKSPACE_STIFFNESS = 200f
private const val C17_CLOSE_WORKSPACE_DAMPING = 1f
private const val C17_CLOSE_BLUR_STIFFNESS = 300f
private const val C17_CLOSE_BLUR_DAMPING = 1f
// The 0.84 close spring is intentionally under-damped. Keep C17's small
// icon-end overshoot, but cap velocity-amplified rapid-interruption outliers.
private const val C17_CLOSE_MIN_RENDER_PROGRESS = -0.012f

private const val C17_SOURCE_SCALE = 0.9f
private const val C17_SOURCE_BLUR_DP = 18f
private const val C17_WINDOW_CORNER_DP = 30f
private const val C17_ICON_CORNER_DP = 16f
private const val C17_PREDICTIVE_MIN_WINDOW_SCALE = 0.4f
private const val C17_PREDICTIVE_VERTICAL_MARGIN_DP = 10f
private const val C17_PREDICTIVE_RESET_STIFFNESS = 400f
private const val C17_PREDICTIVE_RESET_DAMPING = 1f

private enum class FeatureLaunchDirection {
    Opening,
    Closing,
}

private data class FeatureLaunchSession(
    val mode: FeaturePageMode,
    val origin: FeatureLaunchOrigin,
    val pressToken: Long? = null,
)

private data class FeatureLaunchAnimation(
    val session: FeatureLaunchSession,
    val direction: FeatureLaunchDirection,
    val motion: FeatureLaunchMotion,
    val returnOrigin: FeatureLaunchOrigin? = null,
    val interrupted: Boolean = false,
    val preserveWorkspace: Boolean = false,
    val initialVelocity: FeatureLaunchVelocity? = null,
    val initialWorkspaceVelocity: FeatureWorkspaceVelocity? = null,
    val predictiveBackMotion: FeaturePredictiveBackMotion? = null,
)

private data class FeatureLaunchVelocity(
    val geometry: Float,
    val radius: Float,
)

private data class FeatureWorkspaceVelocity(
    val scale: Float,
    val alpha: Float,
    val blur: Float,
)

/**
 * One leash owns its geometry and radius progress for its whole lifetime.
 *
 * Keeping the channels on the leash (rather than in two global "active" and
 * "parallel" slots) is important for C17-style interruption: an opening leash
 * can become a closing leash, or be promoted back from the closing set, without
 * snapping its current value.
 */
private class FeatureLaunchMotion(
    initialGeometry: Float,
    initialRadius: Float = initialGeometry,
    val alphaDirection: FeatureLaunchDirection,
    val useReturnOrigin: Boolean,
) {
    val geometry = Animatable(initialGeometry)
    val radius = Animatable(initialRadius)

    fun velocitySnapshot(): FeatureLaunchVelocity = FeatureLaunchVelocity(
        geometry = geometry.velocity,
        radius = radius.velocity,
    )
}

/**
 * Gesture-owned transform which sits outside the normal icon/window leash.
 *
 * C17 first moves the full task surface with the finger, then hands the exact
 * current surface to its close spring. Keeping this object on the animation
 * record lets the transform survive close -> parallel and close -> reopen
 * ownership changes without a one-frame reset.
 */
@Stable
private class FeaturePredictiveBackMotion {
    val progress = Animatable(0f)
    var startTouchY by mutableFloatStateOf(Float.NaN)
    var touchY by mutableFloatStateOf(Float.NaN)
    var swipeEdge by mutableIntStateOf(BackEventCompat.EDGE_LEFT)
    var progressJob: Job? = null

    fun updatePointer(event: BackEventCompat) {
        if (startTouchY.isNaN()) {
            startTouchY = event.touchY
        }
        touchY = event.touchY
        swipeEdge = event.swipeEdge
    }
}

private data class C17PredictiveBackTransform(
    val scale: Float,
    val translationX: Float,
    val translationY: Float,
)

/** Exact normalized OSpringInterpolator used by C17 Launcher. */
private fun c17SpringInterpolation(
    input: Float,
    stiffness: Double,
    dampingRatio: Double,
): Float {
    fun origin(value: Double): Double {
        val undampedFrequency = Math.sqrt(stiffness)
        val decay = Math.exp(-dampingRatio * undampedFrequency * value)
        return if (dampingRatio < 1.0) {
            val angularFrequency = Math.sqrt(1.0 - dampingRatio * dampingRatio) *
                undampedFrequency
            val coefficient = dampingRatio * undampedFrequency / angularFrequency
            1.0 - (
                Math.sin(angularFrequency * value) * coefficient +
                    Math.cos(angularFrequency * value)
                ) * decay
        } else if (dampingRatio == 1.0) {
            1.0 - (1.0 + undampedFrequency * value) * decay
        } else {
            val angularFrequency = Math.sqrt(dampingRatio * dampingRatio - 1.0) *
                undampedFrequency
            1.0 - (
                Math.cosh(angularFrequency * value) * angularFrequency +
                    Math.sinh(angularFrequency * value) *
                    (dampingRatio * undampedFrequency)
                ) * (decay / angularFrequency)
        }
    }

    val boundedInput = input.coerceIn(0f, 1f).toDouble()
    val finalValue = origin(1.0)
    return if (abs(finalValue) < 1e-6) {
        boundedInput.toFloat()
    } else {
        (origin(boundedInput) / finalValue).toFloat()
    }
}

private fun FeaturePredictiveBackMotion.c17Progress(): Float =
    c17SpringInterpolation(
        input = progress.value,
        stiffness = 100.0,
        dampingRatio = 4.0,
    ).coerceIn(0f, 1f)

private fun FeaturePredictiveBackMotion.c17WindowScale(): Float = lerpFloat(
    1f,
    C17_PREDICTIVE_MIN_WINDOW_SCALE,
    c17Progress(),
)

private fun FeaturePredictiveBackMotion.toC17Transform(
    viewportBounds: Rect,
    density: Float,
): C17PredictiveBackTransform {
    val progress = c17Progress()
    val scale = c17WindowScale()
    val viewportWidth = viewportBounds.width.coerceAtLeast(1f)
    val viewportHeight = viewportBounds.height.coerceAtLeast(1f)
    val scaledWidth = viewportWidth * scale
    val scaledHeight = viewportHeight * scale
    val targetCenterX = when (swipeEdge) {
        BackEventCompat.EDGE_LEFT -> viewportWidth
        BackEventCompat.EDGE_RIGHT -> 0f
        else -> viewportWidth / 2f
    }
    val centerX = lerpFloat(viewportWidth / 2f, targetCenterX, progress)
    val horizontalOffset = centerX - scaledWidth / 2f

    val touchDelta = if (startTouchY.isNaN() || touchY.isNaN()) {
        0f
    } else {
        touchY - startTouchY
    }
    val verticalInput = (
        abs(touchDelta) / (viewportHeight / 2f).coerceAtLeast(1f)
        ).coerceIn(0f, 1f)
    val verticalProgress = c17SpringInterpolation(
        input = verticalInput,
        stiffness = 50.0,
        dampingRatio = 5.0,
    ).coerceIn(0f, 1f)
    val centeredTop = (viewportHeight - scaledHeight) / 2f
    val verticalMargin = C17_PREDICTIVE_VERTICAL_MARGIN_DP * density
    // The touch delta is gesture-owned and intentionally remains frozen once
    // the finger lifts. Its edge margin must still decay with the predictive
    // progress, otherwise the outer leash reaches scale=1 with a non-zero Y
    // translation and the icon stays displaced until that leash is disposed.
    val animatedVerticalMargin = verticalMargin * progress
    val directionalTop = when {
        touchDelta > 0f ->
            viewportHeight - scaledHeight - animatedVerticalMargin * verticalProgress
        touchDelta < 0f -> animatedVerticalMargin * verticalProgress
        else -> centeredTop
    }
    val verticalOffset = lerpFloat(centeredTop, directionalTop, verticalProgress)

    return C17PredictiveBackTransform(
        scale = scale,
        translationX = horizontalOffset,
        translationY = verticalOffset,
    )
}

private fun lerpFloat(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction

private fun FeatureLaunchOrigin.toLiveVisualOrigin(
    pressScale: Float,
    workspaceScale: Float,
    viewportBounds: Rect,
): FeatureLaunchOrigin {
    val boundedPressScale = pressScale.coerceIn(0.85f, 1f)
    val boundedWorkspaceScale = workspaceScale.coerceIn(C17_SOURCE_SCALE, 1f)
    val itemCenterX = hitLeft + hitWidth / 2f
    val itemCenterY = hitTop + hitHeight / 2f
    val pressedLeft = itemCenterX + (restingLeft - itemCenterX) * boundedPressScale
    val pressedTop = itemCenterY + (restingTop - itemCenterY) * boundedPressScale
    val pressedWidth = restingWidth * boundedPressScale
    val pressedHeight = restingHeight * boundedPressScale
    val workspaceCenterX = viewportBounds.left + viewportBounds.width / 2f
    val workspaceCenterY = viewportBounds.top + viewportBounds.height / 2f
    return copy(
        left = workspaceCenterX + (pressedLeft - workspaceCenterX) * boundedWorkspaceScale,
        top = workspaceCenterY + (pressedTop - workspaceCenterY) * boundedWorkspaceScale,
        width = pressedWidth * boundedWorkspaceScale,
        height = pressedHeight * boundedWorkspaceScale,
        pressScale = boundedPressScale,
    )
}

private fun FeatureLaunchOrigin.toReturnOrigin(
    workspaceScale: Float,
    viewportBounds: Rect,
): FeatureLaunchOrigin {
    val boundedWorkspaceScale = workspaceScale.coerceIn(C17_SOURCE_SCALE, 1f)
    val workspaceCenterX = viewportBounds.left + viewportBounds.width / 2f
    val workspaceCenterY = viewportBounds.top + viewportBounds.height / 2f
    return copy(
        left = workspaceCenterX + (restingLeft - workspaceCenterX) * boundedWorkspaceScale,
        top = workspaceCenterY + (restingTop - workspaceCenterY) * boundedWorkspaceScale,
        width = restingWidth * boundedWorkspaceScale,
        height = restingHeight * boundedWorkspaceScale,
        pressScale = 1f,
    )
}

private fun c17TaskSurfaceAlpha(
    direction: FeatureLaunchDirection,
    geometryProgress: Float,
): Float {
    val progress = geometryProgress.coerceIn(0f, 1f)
    return when (direction) {
        FeatureLaunchDirection.Opening -> ((progress - 0.2f) / 0.3f).coerceIn(0f, 1f)
        FeatureLaunchDirection.Closing -> ((progress - 0.3f) / 0.3f).coerceIn(0f, 1f)
    }
}

private fun instantRootContentTransform(): ContentTransform = ContentTransform(
    targetContentEnter = EnterTransition.None,
    initialContentExit = ExitTransition.None,
)

private fun Modifier.c17LauncherSourceTransform(
    scaleProgress: Float,
    alphaProgress: Float,
    blurProgress: Float,
): Modifier {
    val boundedScaleProgress = scaleProgress.coerceIn(0f, 1f)
    val boundedAlphaProgress = alphaProgress.coerceIn(0f, 1f)
    val boundedBlurProgress = blurProgress.coerceIn(0f, 1f)
    return this
        .graphicsLayer {
            val scale = lerpFloat(1f, C17_SOURCE_SCALE, boundedScaleProgress)
            scaleX = scale
            scaleY = scale
            alpha = 1f - boundedAlphaProgress
            transformOrigin = TransformOrigin.Center
        }
        .blur((C17_SOURCE_BLUR_DP * boundedBlurProgress).dp)
}

@Composable
private fun C17StableBaseSceneLayer(
    transformActive: Boolean,
    workspaceScaleProgress: Float,
    workspaceAlphaProgress: Float,
    workspaceBlurProgress: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val sourceModifier = if (transformActive) {
        Modifier.c17LauncherSourceTransform(
            scaleProgress = workspaceScaleProgress,
            alphaProgress = workspaceAlphaProgress,
            blurProgress = workspaceBlurProgress,
        )
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(sourceModifier),
    ) {
        content()
    }
}

@Composable
private fun C17FeatureWindowLayer(
    session: FeatureLaunchSession,
    direction: FeatureLaunchDirection,
    motion: FeatureLaunchMotion?,
    predictiveBackMotion: FeaturePredictiveBackMotion? = null,
    returnOrigin: FeatureLaunchOrigin? = null,
    viewportBounds: Rect,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (viewportBounds.width <= 0f || viewportBounds.height <= 0f) return
    val density = LocalDensity.current
    val predictiveTransform = predictiveBackMotion?.toC17Transform(
        viewportBounds = viewportBounds,
        density = density.density,
    )
    val geometryProgress = motion?.geometry?.value ?: 1f
    // Keep the scalar path used by the original feature-page animation. This
    // is also the normalized width progress used by C17's normal-app alpha.
    // Keep C17's under-damped icon-end rebound. The endpoint itself is now the
    // canonical 1x source rect, so this is a continuous spring rebound rather
    // than the old 0.85x -> 1x handoff reset. Only extreme inherited velocity
    // from rapid interruption is bounded.
    val physicalProgress = if (direction == FeatureLaunchDirection.Closing) {
        geometryProgress.coerceAtLeast(C17_CLOSE_MIN_RENDER_PROGRESS)
    } else {
        geometryProgress
    }
    val radiusProgress = (motion?.radius?.value ?: 1f).coerceIn(0f, 1f)
    val sourceOrigin = if (returnOrigin != null) {
        returnOrigin
    } else if (motion?.useReturnOrigin == true) {
        session.origin.toReturnOrigin(
            workspaceScale = 1f,
            viewportBounds = viewportBounds,
        )
    } else {
        session.origin
    }
    val sourceLeft = (sourceOrigin.left - viewportBounds.left)
        .coerceIn(0f, viewportBounds.width)
    val sourceTop = (sourceOrigin.top - viewportBounds.top)
        .coerceIn(0f, viewportBounds.height)
    val sourceWidth = sourceOrigin.width.coerceIn(1f, viewportBounds.width)
    val sourceHeight = sourceOrigin.height.coerceIn(1f, viewportBounds.height)
    val sourceCenterX = sourceLeft + sourceWidth / 2f
    val targetCenterX = viewportBounds.width / 2f
    val animatedCenterX = lerpFloat(sourceCenterX, targetCenterX, physicalProgress)
    val animatedTop = lerpFloat(sourceTop, 0f, physicalProgress)
    val animatedWidth = lerpFloat(
        sourceWidth,
        viewportBounds.width,
        physicalProgress,
    ).coerceAtLeast(1f)
    val sourceAspectRatio = sourceHeight / sourceWidth
    val targetAspectRatio = viewportBounds.height / viewportBounds.width
    val animatedAspectRatio = lerpFloat(
        sourceAspectRatio,
        targetAspectRatio,
        physicalProgress,
    ).coerceAtLeast(0.01f)
    val animatedHeight = (animatedWidth * animatedAspectRatio).coerceAtLeast(1f)
    val animatedLeft = animatedCenterX - animatedWidth / 2f
    val cornerRadius = lerpFloat(
        C17_ICON_CORNER_DP,
        C17_WINDOW_CORNER_DP,
        radiusProgress,
    ).dp
    val boundedTaskSurfaceAlpha = c17TaskSurfaceAlpha(
        direction = motion?.alphaDirection ?: direction,
        geometryProgress = geometryProgress,
    )
    val iconSurfaceVisible = boundedTaskSurfaceAlpha < 1f
    val animatedIconSize = animatedWidth
    // IconDrawableView's extended surface keeps the square icon at the top of
    // curRect; the remaining surface height is edge-filled, not centre-aligned.
    val animatedIconTop = animatedTop

    val predictiveModifier = if (predictiveTransform != null) {
        Modifier.graphicsLayer {
            scaleX = predictiveTransform.scale
            scaleY = predictiveTransform.scale
            translationX = predictiveTransform.translationX
            translationY = predictiveTransform.translationY
            transformOrigin = TransformOrigin(0f, 0f)
            shape = RoundedCornerShape(C17_WINDOW_CORNER_DP.dp)
            clip = true
        }
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(predictiveModifier),
    ) {
        // The extended icon surface is a launcher child surface below the task
        // leash. The task alpha therefore reveals the page over the icon
        // continuously; drawing this after the task causes the icon to cover
        // the fade and then disappear in one visibly flashing frame.
        val animatedIconSizeDp = with(density) { animatedIconSize.toDp() }
        // The native extended-icon buffer never becomes shorter than its square
        // icon, which avoids a late-frame squash near the endpoint.
        val animatedIconSurfaceHeightDp = with(density) {
            max(animatedHeight, animatedIconSize).toDp()
        }
        // C17 keeps one IconSurface alive for the complete transition and only
        // changes its alpha while the opaque task is on top. Keeping this node
        // mounted prevents close from having to recreate the icon layer on the
        // first reveal frame, which otherwise looks like a sudden reset.
        FeatureLaunchIcon(
            pageMode = session.mode,
            visualSize = animatedIconSizeDp,
            surfaceHeight = animatedIconSurfaceHeightDp,
            modifier = Modifier
                .offset {
                    IntOffset(
                        animatedLeft.roundToInt(),
                        animatedIconTop.roundToInt(),
                    )
                }
                .size(
                    width = animatedIconSizeDp,
                    height = animatedIconSurfaceHeightDp,
                )
                .graphicsLayer {
                    alpha = if (iconSurfaceVisible) 1f else 0f
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                },
        )
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(animatedLeft.roundToInt(), animatedTop.roundToInt())
                }
                .size(
                    width = with(density) { animatedWidth.toDp() },
                    height = with(density) { animatedHeight.toDp() },
                )
                .graphicsLayer {
                    alpha = boundedTaskSurfaceAlpha
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
                .clip(RoundedCornerShape(cornerRadius))
                .background(COUITheme.colorScheme.surface),
        ) {
            Layout(
                content = {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // The OPlus background RenderEffect samples the physical window. During
                        // the launcher task transform it would escape this layer's alpha/crop,
                        // leaving an opaque header over the shrinking page. Draw the toolbar
                        // blur into the movable task surface for both open and close instead.
                        CompositionLocalProvider(LocalChromeNativeBlurEnabled provides false) {
                            content()
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) { measurables, constraints ->
                val viewportWidth = viewportBounds.width.roundToInt().coerceAtLeast(1)
                val viewportHeight = viewportBounds.height.roundToInt().coerceAtLeast(1)
                // C17 never independently scales the task buffer's X/Y axes.
                // Its RectTransformHelper applies one uniform scale selected
                // from the active clip axis, then changes the Surface crop as
                // the spring rect morphs from icon to window. This feature
                // transition is the portrait/vertical-clip path, so width is
                // the driving axis and excess height is clipped from the
                // bottom while the page remains top-aligned.
                val taskSurfaceScale = animatedWidth / viewportBounds.width
                val placeable = measurables.single().measure(
                    Constraints.fixed(viewportWidth, viewportHeight),
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.placeWithLayer(0, 0) {
                        scaleX = taskSurfaceScale
                        scaleY = taskSurfaceScale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                }
            }
        }
    }
}

private suspend fun animateFeatureLaunchMotion(
    motion: FeatureLaunchMotion,
    targetValue: Float,
    initialVelocity: FeatureLaunchVelocity? = null,
    geometryStiffness: Float,
    geometryDamping: Float,
    radiusStiffness: Float,
    radiusDamping: Float,
) = coroutineScope {
    val radiusJob = launch {
        motion.radius.animateTo(
            targetValue = targetValue,
            initialVelocity = initialVelocity?.radius ?: motion.radius.velocity,
            animationSpec = spring(
                dampingRatio = radiusDamping,
                stiffness = radiusStiffness,
                visibilityThreshold = 0.001f,
            ),
        )
    }
    try {
        motion.geometry.animateTo(
            targetValue = targetValue,
            initialVelocity = initialVelocity?.geometry ?: motion.geometry.velocity,
            animationSpec = spring(
                dampingRatio = geometryDamping,
                stiffness = geometryStiffness,
                visibilityThreshold = 0.0005f,
            ),
        )
    } finally {
        radiusJob.cancel()
    }
}

@Composable
private fun C17ClosingFeatureWindowLayer(
    animation: FeatureLaunchAnimation,
    viewportBounds: Rect,
    workspaceScale: Float,
    onFinished: suspend (FeatureLaunchAnimation) -> Unit,
    content: @Composable () -> Unit,
) {
    val latestOnFinished by rememberUpdatedState(onFinished)
    LaunchedEffect(animation, viewportBounds) {
        if (viewportBounds.width <= 0f || viewportBounds.height <= 0f) {
            return@LaunchedEffect
        }
        animateFeatureLaunchMotion(
            motion = animation.motion,
            targetValue = 0f,
            initialVelocity = animation.initialVelocity,
            geometryStiffness = C17_CLOSE_GEOMETRY_STIFFNESS,
            geometryDamping = C17_CLOSE_GEOMETRY_DAMPING,
            radiusStiffness = C17_CLOSE_RADIUS_STIFFNESS,
            radiusDamping = C17_CLOSE_RADIUS_DAMPING,
        )
        latestOnFinished(animation)
    }
    C17FeatureWindowLayer(
        session = animation.session,
        direction = FeatureLaunchDirection.Closing,
        motion = animation.motion,
        predictiveBackMotion = animation.predictiveBackMotion,
        // C17 stores an immutable icon target per record, then applies the
        // launcher's live workspace delta in the same surface transaction.
        // Following the current workspace scale prevents an old parallel leash
        // from ending at stale 0.9x coordinates while Main is restoring to 1x.
        returnOrigin = animation.returnOrigin?.toReturnOrigin(
            workspaceScale = workspaceScale,
            viewportBounds = viewportBounds,
        ),
        viewportBounds = viewportBounds,
        content = content,
    )
}

@Composable
private fun FeatureLaunchQueueHitLayer(
    origins: Map<FeaturePageMode, FeatureLaunchOrigin>,
    viewportBounds: Rect,
    onPressStart: (FeaturePageMode) -> Long,
    onPressEnd: (FeaturePageMode, Long) -> Unit,
    onOpen: (FeaturePageMode, FeatureLaunchOrigin?, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (viewportBounds.width <= 0f || viewportBounds.height <= 0f) return
    val density = LocalDensity.current
    val latestOnPressStart by rememberUpdatedState(onPressStart)
    val latestOnPressEnd by rememberUpdatedState(onPressEnd)
    val latestOnOpen by rememberUpdatedState(onOpen)
    Box(modifier = modifier.fillMaxSize()) {
        origins.forEach { (mode, origin) ->
            val latestOrigin by rememberUpdatedState(origin)
            val restingHitWidth = origin.hitWidth.coerceAtLeast(origin.restingWidth)
            val restingHitHeight = origin.hitHeight.coerceAtLeast(origin.restingHeight)
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (origin.hitLeft - viewportBounds.left).roundToInt(),
                            y = (origin.hitTop - viewportBounds.top).roundToInt(),
                        )
                    }
                    .size(
                        width = with(density) { restingHitWidth.toDp() },
                        height = with(density) { restingHitHeight.toDp() },
                    )
                    // Keep the canonical View hit rectangle fixed for the whole
                    // gesture. Moving/shrinking this node with the workspace can
                    // put an unchanged finger outside it between DOWN and UP,
                    // which makes detectTapGestures cancel rapid taps.
                    .pointerInput(mode) {
                        detectTapGestures(
                            onPress = {
                                val pressToken = latestOnPressStart(mode)
                                try {
                                    if (tryAwaitRelease()) {
                                        latestOnOpen(mode, latestOrigin, pressToken)
                                    }
                                } finally {
                                    latestOnPressEnd(mode, pressToken)
                                }
                            },
                        )
                    },
            )
        }
    }
}

@Stable
private class RootMainPagerState(
    val pagerState: PagerState,
    private val coroutineScope: CoroutineScope,
    scrollResetGenerationState: MutableIntState,
) {
    var selectedPage by mutableIntStateOf(pagerState.currentPage)
        private set

    var isNavigating by mutableStateOf(false)
        private set
    var scrollResetGeneration by scrollResetGenerationState
        private set
    private var navigationJob: Job? = null

    fun animateToPage(targetIndex: Int) {
        if (
            targetIndex == selectedPage &&
            targetIndex == pagerState.currentPage &&
            abs(pagerState.currentPageOffsetFraction) < 0.001f
        ) {
            return
        }
        navigationJob?.cancel()
        selectedPage = targetIndex
        isNavigating = true
        val distance = abs(targetIndex - pagerState.currentPage).coerceAtLeast(2)
        val duration = 100 * distance + 100
        val layoutInfo = pagerState.layoutInfo
        val pageSize = layoutInfo.pageSize + layoutInfo.pageSpacing
        val currentDistanceInPages =
            targetIndex - pagerState.currentPage - pagerState.currentPageOffsetFraction
        val scrollPixels = currentDistanceInPages * pageSize
        navigationJob = coroutineScope.launch {
            val myJob = coroutineContext[Job]
            try {
                if (pageSize == 0) {
                    pagerState.scrollToPage(targetIndex)
                } else {
                    pagerState.animateScrollBy(
                        value = scrollPixels,
                        animationSpec = tween(easing = EaseInOut, durationMillis = duration),
                    )
                }
            } finally {
                if (navigationJob == myJob) {
                    if (pagerState.currentPage != targetIndex) {
                        selectedPage = pagerState.currentPage
                    }
                    isNavigating = false
                    scrollResetGeneration += 1
                }
            }
        }
    }

    fun syncPage() {
        if (!isNavigating && selectedPage != pagerState.currentPage) {
            selectedPage = pagerState.currentPage
        }
    }
}

@Composable
private fun rememberRootMainPagerState(pagerState: PagerState): RootMainPagerState {
    val coroutineScope = rememberCoroutineScope()
    val scrollResetGeneration = rememberSaveable { mutableIntStateOf(0) }
    return remember(pagerState, coroutineScope, scrollResetGeneration) {
        RootMainPagerState(pagerState, coroutineScope, scrollResetGeneration)
    }
}

@Composable
private fun MainScreenBackHandler(
    mainPagerState: RootMainPagerState,
    navigator: RootNavigator,
    onTabChange: (Int) -> Unit,
) {
    val isPagerBackHandlerEnabled by remember {
        derivedStateOf {
            navigator.current() is RootRoute.Main &&
                    navigator.backStackSize() == 1 &&
                    mainPagerState.selectedPage != 0
        }
    }
    val mainBackEventState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = mainBackEventState,
        isBackEnabled = isPagerBackHandlerEnabled,
        onBackCompleted = {
            mainPagerState.animateToPage(0)
            onTabChange(0)
        },
    )
}

@Serializable
private sealed interface RootRoute : NavKey {
    @Serializable
    data object Main : RootRoute

    @Serializable
    data object FeatureDesktop : RootRoute

    @Serializable
    data object FeatureSystemUi : RootRoute

    @Serializable
    data object FeatureSystemUiNative : RootRoute

    @Serializable
    data object FeatureSystemUiDynamicColor : RootRoute

    @Serializable
    data object FeatureSystemUiStatusBar : RootRoute

    @Serializable
    data object FeatureSystemUiNotificationCenter : RootRoute

    @Serializable
    data object FeatureSystemUiControlCenter : RootRoute

    @Serializable
    data object FeatureNotificationRemoval : RootRoute

    @Serializable
    data object FeatureMobileNetwork : RootRoute

    @Serializable
    data object FeatureAndroidSystem : RootRoute

    @Serializable
    data object FeatureInstaller : RootRoute

    @Serializable
    data object FeatureEsim : RootRoute

    @Serializable
    data object FeatureEsimDiagnostics : RootRoute

    @Serializable
    data object FeatureAppMarket : RootRoute

    @Serializable
    data object FeatureGoogleMessages : RootRoute

    @Serializable
    data object FeatureAthena : RootRoute

    @Serializable
    data object FeatureSettings : RootRoute

    @Serializable
    data object FeatureSettingsRegion : RootRoute

    @Serializable
    data object FeatureSecurityPermission : RootRoute

    @Serializable
    data object FeatureTouchSampling : RootRoute

    @Serializable
    data object FeatureRefreshRate : RootRoute

    @Serializable
    data object FeatureWallpapers : RootRoute

    @Serializable
    data object FeatureAod : RootRoute

    @Serializable
    data object FeatureAssistant : RootRoute

    @Serializable
    data object FeatureOPlusLocalizer : RootRoute

    @Serializable
    data object FeatureOPlusLocalizerProperties : RootRoute

    @Serializable
    data object FeatureOPlusLocalizerScope : RootRoute

    @Serializable
    data object AppSettings : RootRoute

    @Serializable
    data object DeveloperOptions : RootRoute

    @Serializable
    data object SoftwareUpdate : RootRoute

    @Serializable
    data object SoftwareUpdateSettings : RootRoute

    @Serializable
    data object SoftwareUpdateReleaseNotes : RootRoute

    @Serializable
    data object Contributors : RootRoute

    @Serializable
    data object References : RootRoute
}

@Stable
private class RootNavigator(
    private val backStack: MutableList<NavKey>,
) {
    fun push(key: NavKey) {
        backStack.add(key)
    }

    fun pop() {
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        }
    }

    fun popUntil(predicate: (NavKey) -> Boolean) {
        while (backStack.size > 1 && !predicate(backStack.last())) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    fun current(): NavKey? = backStack.lastOrNull()

    fun backStackSize(): Int = backStack.size
}

private fun FeaturePageMode.toRootRoute(): RootRoute? = when (this) {
    FeaturePageMode.Main -> null
    FeaturePageMode.Desktop -> RootRoute.FeatureDesktop
    FeaturePageMode.SystemUi -> RootRoute.FeatureSystemUi
    FeaturePageMode.SystemUiNative -> RootRoute.FeatureSystemUiNative
    FeaturePageMode.SystemUiDynamicColor -> RootRoute.FeatureSystemUiDynamicColor
    FeaturePageMode.SystemUiStatusBar -> RootRoute.FeatureSystemUiStatusBar
    FeaturePageMode.SystemUiNotificationCenter -> RootRoute.FeatureSystemUiNotificationCenter
    FeaturePageMode.SystemUiControlCenter -> RootRoute.FeatureSystemUiControlCenter
    FeaturePageMode.NotificationRemoval -> RootRoute.FeatureNotificationRemoval
    FeaturePageMode.MobileNetwork -> RootRoute.FeatureMobileNetwork
    FeaturePageMode.AndroidSystem -> RootRoute.FeatureAndroidSystem
    FeaturePageMode.Installer -> RootRoute.FeatureInstaller
    FeaturePageMode.Esim -> RootRoute.FeatureEsim
    FeaturePageMode.EsimDiagnostics -> RootRoute.FeatureEsimDiagnostics
    FeaturePageMode.AppMarket -> RootRoute.FeatureAppMarket
    FeaturePageMode.GoogleMessages -> RootRoute.FeatureGoogleMessages
    FeaturePageMode.Athena -> RootRoute.FeatureAthena
    FeaturePageMode.Settings -> RootRoute.FeatureSettings
    FeaturePageMode.SettingsRegion -> RootRoute.FeatureSettingsRegion
    FeaturePageMode.SecurityPermission -> RootRoute.FeatureSecurityPermission
    FeaturePageMode.TouchSampling -> RootRoute.FeatureTouchSampling
    FeaturePageMode.RefreshRate -> RootRoute.FeatureRefreshRate
    FeaturePageMode.Wallpapers -> RootRoute.FeatureWallpapers
    FeaturePageMode.Aod -> RootRoute.FeatureAod
    FeaturePageMode.Assistant -> RootRoute.FeatureAssistant
    FeaturePageMode.OPlusLocalizer -> RootRoute.FeatureOPlusLocalizer
    FeaturePageMode.OPlusLocalizerProperties -> RootRoute.FeatureOPlusLocalizerProperties
    FeaturePageMode.OPlusLocalizerScope -> RootRoute.FeatureOPlusLocalizerScope
}

private fun AboutPageMode.toRootRoute(): RootRoute? = when (this) {
    AboutPageMode.Main -> null
    AboutPageMode.AppSettings -> RootRoute.AppSettings
    AboutPageMode.DeveloperOptions -> RootRoute.DeveloperOptions
    AboutPageMode.Update -> RootRoute.SoftwareUpdate
    AboutPageMode.UpdateSettings -> RootRoute.SoftwareUpdateSettings
    AboutPageMode.UpdateReleaseNotes -> RootRoute.SoftwareUpdateReleaseNotes
    AboutPageMode.Contributors -> RootRoute.Contributors
    AboutPageMode.References -> RootRoute.References
}

@Stable
private data class RootUiState(
    val currentTab: Int,
    val rootGranted: Boolean,
    val blurEffectEnabled: Boolean,
    val featurePageNewStyleEnabled: Boolean,
    val featurePageVideoHidden: Boolean,
    val featurePageVideoActive: Boolean,
    val hiddenFeatureSourceModes: Set<FeaturePageMode>,
    val featureExternalIconScales: Map<FeaturePageMode, () -> Float>,
    val popDirectionFollowsSwipeEdge: Boolean,
    val showFpsMonitor: Boolean,
    val liquidGlassBottomBarEnabled: Boolean,
    val appLanguageTag: String,
    val appThemeMode: AppThemeMode,
    val appUiStyle: AppUiStyle,
    val materialFloatingBottomBarEnabled: Boolean,
    val materialHapticsEnabled: Boolean,
    val materialSwitchIconsEnabled: Boolean,
    val appThemeKeyColor: Long?,
    val appThemePaletteStyle: Int,
    val appThemeColorSpec: Int,
    val permissionMonitorVisible: Boolean,
    val nativeNotifyIconEnabled: Boolean,
    val nativeNotificationBubblesEnabled: Boolean,
    val systemUiInternationalNetworkDisplayEnabled: Boolean,
    val systemUiHideMobileRoamingIndicatorEnabled: Boolean,
    val systemUiInternationalNotificationStyleEnabled: Boolean,
    val systemUiHideQsEditEnabled: Boolean,
    val systemUiHideQsSettingsEnabled: Boolean,
    val systemUiHideQsTopCarrierEnabled: Boolean,
    val systemUiHideQsMoreEnabled: Boolean,
    val systemUiForceNativeClipboardOverlayEnabled: Boolean,
    val settingsForceGoogleEntryEnabled: Boolean,
    val gmsRegionRestrictionBypassEnabled: Boolean,
    val extremeRefresh165Enabled: Boolean,
    val launcherLayoutUnlocked: Boolean,
    val assistantScreenOption: AssistantScreenOption,
    val recentTaskRadiusEnabled: Boolean,
    val recentTaskRadiusDp: Int,
    val aodEnhanceEnabled: Boolean,
    val aodInitDarkBrightness: Int,
    val aodInitBrightBrightness: Int,
    val aodRunningBrightnessMultiplier: Float,
    val aodPanoramicSupportEnabled: Boolean,
    val aodSettingsSwitchEnabled: Boolean,
    val aodSingleClickBlockEnabled: Boolean,
    val oosLocalizerEnabled: Boolean,
    val oosLocalizerConfigMode: Int,
    val oosLocalizerRegion: String,
    val oosLocalizerLocale: String,
    val oosLocalizerModel: String,
    val assistantPowerMode: Int,
    val assistantGestureCircleEnabled: Boolean,
    val assistantGestureCircleC17Enabled: Boolean,
    val assistantNativePowerEnabled: Boolean,
    val assistantNativeCircleEnabled: Boolean,
    val visibleTabs: List<BottomTab>,
    val selectedIndex: Int,
    val bottomNavigationHeight: Dp,
    val blurBackdrop: LayerBackdrop?,
    val liquidBackdrop: LiquidLayerBackdrop?,
    val softwareUpdateState: SoftwareUpdateUiState,
)

@Stable
private data class RootActions(
    val onTabChange: (Int) -> Unit,
    val onBlurEffectEnabledChange: (Boolean) -> Unit,
    val onFeaturePageNewStyleEnabledChange: (Boolean) -> Unit,
    val onFeaturePageVideoHiddenChange: (Boolean) -> Unit,
    val onPopDirectionFollowsSwipeEdgeChange: (Boolean) -> Unit,
    val onShowFpsMonitorChange: (Boolean) -> Unit,
    val onLiquidGlassBottomBarEnabledChange: (Boolean) -> Unit,
    val onAppLanguageChange: (String) -> Unit,
    val onAppThemeModeChange: (AppThemeMode) -> Unit,
    val onAppUiStyleChange: (AppUiStyle) -> Unit,
    val onMaterialFloatingBottomBarEnabledChange: (Boolean) -> Unit,
    val onMaterialHapticsEnabledChange: (Boolean) -> Unit,
    val onMaterialSwitchIconsEnabledChange: (Boolean) -> Unit,
    val onAppThemeKeyColorChange: (Long?) -> Unit,
    val onAppThemePaletteStyleChange: (Int) -> Unit,
    val onAppThemeColorSpecChange: (Int) -> Unit,
    val onPermissionMonitorVisibleChange: (Boolean) -> Unit,
    val onNativeNotifyIconEnabledChange: (Boolean) -> Unit,
    val onNativeNotificationBubblesEnabledChange: (Boolean) -> Unit,
    val onSystemUiInternationalNetworkDisplayEnabledChange: (Boolean) -> Unit,
    val onSystemUiHideMobileRoamingIndicatorEnabledChange: (Boolean) -> Unit,
    val onSystemUiInternationalNotificationStyleEnabledChange: (Boolean) -> Unit,
    val onSystemUiHideQsEditEnabledChange: (Boolean) -> Unit,
    val onSystemUiHideQsSettingsEnabledChange: (Boolean) -> Unit,
    val onSystemUiHideQsTopCarrierEnabledChange: (Boolean) -> Unit,
    val onSystemUiHideQsMoreEnabledChange: (Boolean) -> Unit,
    val onSystemUiForceNativeClipboardOverlayEnabledChange: (Boolean) -> Unit,
    val onSettingsForceGoogleEntryEnabledChange: (Boolean) -> Unit,
    val onGmsRegionRestrictionBypassEnabledChange: (Boolean) -> Unit,
    val onExtremeRefresh165EnabledChange: (Boolean) -> Unit,
    val onLauncherLayoutUnlockedChange: (Boolean) -> Unit,
    val onAssistantScreenOptionChange: (AssistantScreenOption) -> Unit,
    val onRecentTaskRadiusEnabledChange: (Boolean) -> Unit,
    val onRecentTaskRadiusDpChange: (Int) -> Unit,
    val onAodEnhanceEnabledChange: (Boolean) -> Unit,
    val onAodInitDarkBrightnessChange: (Int) -> Unit,
    val onAodInitBrightBrightnessChange: (Int) -> Unit,
    val onAodRunningBrightnessMultiplierChange: (Float) -> Unit,
    val onAodPanoramicSupportEnabledChange: (Boolean) -> Unit,
    val onAodSettingsSwitchEnabledChange: (Boolean) -> Unit,
    val onAodSingleClickBlockEnabledChange: (Boolean) -> Unit,
    val onOosLocalizerEnabledChange: (Boolean) -> Unit,
    val onOosLocalizerConfigModeChange: (Int) -> Unit,
    val onOosLocalizerRegionChange: (String) -> Unit,
    val onOosLocalizerLocaleChange: (String) -> Unit,
    val onOosLocalizerModelChange: (String) -> Unit,
    val onAssistantPowerModeChange: (Int) -> Unit,
    val onAssistantGestureCircleEnabledChange: (Boolean) -> Unit,
    val onAssistantGestureCircleC17EnabledChange: (Boolean) -> Unit,
    val onAssistantNativePowerEnabledChange: (Boolean) -> Unit,
    val onAssistantNativeCircleEnabledChange: (Boolean) -> Unit,
    val onBottomNavigationHeightChange: (Int) -> Unit,
    val onHapticClick: () -> Unit,
    val popRootRoute: () -> Unit,
    val popToMainRoute: () -> Unit,
    val openFeatureSubPage: (FeaturePageMode) -> Unit,
    val openFeatureSubPageFromMain: (FeaturePageMode, FeatureLaunchOrigin?) -> Unit,
    val openAboutSubPage: (AboutPageMode) -> Unit,
    val requestAboutAutoCheckUpdate: () -> Unit,
)

private val LocalRootUiState = compositionLocalOf<RootUiState> {
    error("No RootUiState provided")
}

private val LocalRootActions = staticCompositionLocalOf<RootActions> {
    error("No RootActions provided")
}

private val LocalRootNavigator = staticCompositionLocalOf<RootNavigator> {
    error("No RootNavigator provided")
}

private val LocalMainPagerState = staticCompositionLocalOf<RootMainPagerState> {
    error("No RootMainPagerState provided")
}

@Composable
private fun RootFeatureEntry(
    pageMode: FeaturePageMode,
    transitionScrollState: ScrollState,
) {
    val ui = LocalRootUiState.current
    val actions = LocalRootActions.current
    // NavDisplay keeps both entries composed during predictive back. Each entry must
    // own its capture layer; sharing the main-page backdrop lets the two scenes
    // overwrite one another and leaves rectangular holes in the toolbar blur.
    val entryBlurBackdrop = rememberChromeBlurBackdrop(ui.blurEffectEnabled)
    FeatureSubRoute(
        modifier = Modifier.fillMaxSize(),
        pageMode = pageMode,
        oosLocalizerEnabled = ui.oosLocalizerEnabled,
        onOosLocalizerEnabledChange = actions.onOosLocalizerEnabledChange,
        oosLocalizerConfigMode = ui.oosLocalizerConfigMode,
        onOosLocalizerConfigModeChange = actions.onOosLocalizerConfigModeChange,
        oosLocalizerRegion = ui.oosLocalizerRegion,
        onOosLocalizerRegionChange = actions.onOosLocalizerRegionChange,
        oosLocalizerLocale = ui.oosLocalizerLocale,
        onOosLocalizerLocaleChange = actions.onOosLocalizerLocaleChange,
        oosLocalizerModel = ui.oosLocalizerModel,
        onOosLocalizerModelChange = actions.onOosLocalizerModelChange,
        permissionMonitorVisible = ui.permissionMonitorVisible,
        onPermissionMonitorVisibleChange = actions.onPermissionMonitorVisibleChange,
        nativeNotifyIconEnabled = ui.nativeNotifyIconEnabled,
        onNativeNotifyIconEnabledChange = actions.onNativeNotifyIconEnabledChange,
        nativeNotificationBubblesEnabled = ui.nativeNotificationBubblesEnabled,
        onNativeNotificationBubblesEnabledChange = actions.onNativeNotificationBubblesEnabledChange,
        systemUiInternationalNetworkDisplayEnabled =
            ui.systemUiInternationalNetworkDisplayEnabled,
        onSystemUiInternationalNetworkDisplayEnabledChange =
            actions.onSystemUiInternationalNetworkDisplayEnabledChange,
        systemUiHideMobileRoamingIndicatorEnabled =
            ui.systemUiHideMobileRoamingIndicatorEnabled,
        onSystemUiHideMobileRoamingIndicatorEnabledChange =
            actions.onSystemUiHideMobileRoamingIndicatorEnabledChange,
        systemUiInternationalNotificationStyleEnabled =
            ui.systemUiInternationalNotificationStyleEnabled,
        onSystemUiInternationalNotificationStyleEnabledChange =
            actions.onSystemUiInternationalNotificationStyleEnabledChange,
        systemUiHideQsEditEnabled = ui.systemUiHideQsEditEnabled,
        onSystemUiHideQsEditEnabledChange = actions.onSystemUiHideQsEditEnabledChange,
        systemUiHideQsSettingsEnabled = ui.systemUiHideQsSettingsEnabled,
        onSystemUiHideQsSettingsEnabledChange = actions.onSystemUiHideQsSettingsEnabledChange,
        systemUiHideQsTopCarrierEnabled = ui.systemUiHideQsTopCarrierEnabled,
        onSystemUiHideQsTopCarrierEnabledChange = actions.onSystemUiHideQsTopCarrierEnabledChange,
        systemUiHideQsMoreEnabled = ui.systemUiHideQsMoreEnabled,
        onSystemUiHideQsMoreEnabledChange = actions.onSystemUiHideQsMoreEnabledChange,
        systemUiForceNativeClipboardOverlayEnabled = ui.systemUiForceNativeClipboardOverlayEnabled,
        onSystemUiForceNativeClipboardOverlayEnabledChange = actions.onSystemUiForceNativeClipboardOverlayEnabledChange,
        settingsForceGoogleEntryEnabled = ui.settingsForceGoogleEntryEnabled,
        onSettingsForceGoogleEntryEnabledChange = actions.onSettingsForceGoogleEntryEnabledChange,
        gmsRegionRestrictionBypassEnabled = ui.gmsRegionRestrictionBypassEnabled,
        onGmsRegionRestrictionBypassEnabledChange = actions.onGmsRegionRestrictionBypassEnabledChange,
        extremeRefresh165Enabled = ui.extremeRefresh165Enabled,
        onExtremeRefresh165EnabledChange = actions.onExtremeRefresh165EnabledChange,
        launcherLayoutUnlocked = ui.launcherLayoutUnlocked,
        onLauncherLayoutUnlockedChange = actions.onLauncherLayoutUnlockedChange,
        assistantScreenOption = ui.assistantScreenOption,
        onAssistantScreenOptionChange = actions.onAssistantScreenOptionChange,
        recentTaskRadiusEnabled = ui.recentTaskRadiusEnabled,
        onRecentTaskRadiusEnabledChange = actions.onRecentTaskRadiusEnabledChange,
        recentTaskRadiusDp = ui.recentTaskRadiusDp,
        onRecentTaskRadiusDpChange = actions.onRecentTaskRadiusDpChange,
        aodEnhanceEnabled = ui.aodEnhanceEnabled,
        onAodEnhanceEnabledChange = actions.onAodEnhanceEnabledChange,
        aodInitDarkBrightness = ui.aodInitDarkBrightness,
        onAodInitDarkBrightnessChange = actions.onAodInitDarkBrightnessChange,
        aodInitBrightBrightness = ui.aodInitBrightBrightness,
        onAodInitBrightBrightnessChange = actions.onAodInitBrightBrightnessChange,
        aodRunningBrightnessMultiplier = ui.aodRunningBrightnessMultiplier,
        onAodRunningBrightnessMultiplierChange = actions.onAodRunningBrightnessMultiplierChange,
        aodPanoramicSupportEnabled = ui.aodPanoramicSupportEnabled,
        onAodPanoramicSupportEnabledChange = actions.onAodPanoramicSupportEnabledChange,
        aodSettingsSwitchEnabled = ui.aodSettingsSwitchEnabled,
        onAodSettingsSwitchEnabledChange = actions.onAodSettingsSwitchEnabledChange,
        aodSingleClickBlockEnabled = ui.aodSingleClickBlockEnabled,
        onAodSingleClickBlockEnabledChange = actions.onAodSingleClickBlockEnabledChange,
        assistantPowerMode = ui.assistantPowerMode,
        onAssistantPowerModeChange = actions.onAssistantPowerModeChange,
        assistantGestureCircleEnabled = ui.assistantGestureCircleEnabled,
        onAssistantGestureCircleEnabledChange = actions.onAssistantGestureCircleEnabledChange,
        assistantGestureCircleC17Enabled = ui.assistantGestureCircleC17Enabled,
        onAssistantGestureCircleC17EnabledChange =
            actions.onAssistantGestureCircleC17EnabledChange,
        assistantNativePowerEnabled = ui.assistantNativePowerEnabled,
        onAssistantNativePowerEnabledChange = actions.onAssistantNativePowerEnabledChange,
        assistantNativeCircleEnabled = ui.assistantNativeCircleEnabled,
        onAssistantNativeCircleEnabledChange = actions.onAssistantNativeCircleEnabledChange,
        subPageBottomExtension = ui.bottomNavigationHeight,
        blurBackdrop = entryBlurBackdrop,
        transitionScrollState = transitionScrollState,
        onBack = actions.popRootRoute,
        onOpenSubPage = actions.openFeatureSubPage,
    )
}

@Composable
private fun RootAboutEntry(pageMode: AboutPageMode) {
    val ui = LocalRootUiState.current
    val actions = LocalRootActions.current
    val entryBlurBackdrop = rememberChromeBlurBackdrop(ui.blurEffectEnabled)
    AboutSubRoute(
        modifier = Modifier.fillMaxSize(),
        pageMode = pageMode,
        softwareUpdateState = ui.softwareUpdateState,
        blurEffectEnabled = ui.blurEffectEnabled,
        onBlurEffectEnabledChange = actions.onBlurEffectEnabledChange,
        featurePageNewStyleEnabled = ui.featurePageNewStyleEnabled,
        onFeaturePageNewStyleEnabledChange = actions.onFeaturePageNewStyleEnabledChange,
        featurePageVideoHidden = ui.featurePageVideoHidden,
        onFeaturePageVideoHiddenChange = actions.onFeaturePageVideoHiddenChange,
        popDirectionFollowsSwipeEdge = ui.popDirectionFollowsSwipeEdge,
        onPopDirectionFollowsSwipeEdgeChange = actions.onPopDirectionFollowsSwipeEdgeChange,
        showFpsMonitor = ui.showFpsMonitor,
        onShowFpsMonitorChange = actions.onShowFpsMonitorChange,
        liquidGlassBottomBarEnabled = ui.liquidGlassBottomBarEnabled,
        onLiquidGlassBottomBarEnabledChange = actions.onLiquidGlassBottomBarEnabledChange,
        appLanguageTag = ui.appLanguageTag,
        onAppLanguageChange = actions.onAppLanguageChange,
        appThemeMode = ui.appThemeMode,
        onAppThemeModeChange = actions.onAppThemeModeChange,
        appUiStyle = ui.appUiStyle,
        onAppUiStyleChange = actions.onAppUiStyleChange,
        materialFloatingBottomBarEnabled = ui.materialFloatingBottomBarEnabled,
        onMaterialFloatingBottomBarEnabledChange = actions.onMaterialFloatingBottomBarEnabledChange,
        materialHapticsEnabled = ui.materialHapticsEnabled,
        onMaterialHapticsEnabledChange = actions.onMaterialHapticsEnabledChange,
        materialSwitchIconsEnabled = ui.materialSwitchIconsEnabled,
        onMaterialSwitchIconsEnabledChange = actions.onMaterialSwitchIconsEnabledChange,
        appThemeKeyColor = ui.appThemeKeyColor,
        onAppThemeKeyColorChange = actions.onAppThemeKeyColorChange,
        appThemePaletteStyle = ui.appThemePaletteStyle,
        onAppThemePaletteStyleChange = actions.onAppThemePaletteStyleChange,
        appThemeColorSpec = ui.appThemeColorSpec,
        onAppThemeColorSpecChange = actions.onAppThemeColorSpecChange,
        bottomContentPadding = ui.bottomNavigationHeight,
        subPageBottomExtension = ui.bottomNavigationHeight,
        blurBackdrop = entryBlurBackdrop,
        onBack = actions.popRootRoute,
        onOpenDeveloperOptions = { actions.openAboutSubPage(AboutPageMode.DeveloperOptions) },
        onOpenUpdateSettings = { actions.openAboutSubPage(AboutPageMode.UpdateSettings) },
        onOpenUpdateReleaseNotes = { actions.openAboutSubPage(AboutPageMode.UpdateReleaseNotes) },
    )
}

@Composable
fun Root(
    openSoftwareUpdateRequest: Int = 0,
    currentTab: Int,
    onTabChange: (Int) -> Unit,
    rootGranted: Boolean,
    blurEffectEnabled: Boolean,
    onBlurEffectEnabledChange: (Boolean) -> Unit,
    featurePageNewStyleEnabled: Boolean,
    onFeaturePageNewStyleEnabledChange: (Boolean) -> Unit,
    featurePageVideoHidden: Boolean,
    onFeaturePageVideoHiddenChange: (Boolean) -> Unit,
    popDirectionFollowsSwipeEdge: Boolean,
    onPopDirectionFollowsSwipeEdgeChange: (Boolean) -> Unit,
    showFpsMonitor: Boolean,
    onShowFpsMonitorChange: (Boolean) -> Unit,
    liquidGlassBottomBarEnabled: Boolean,
    onLiquidGlassBottomBarEnabledChange: (Boolean) -> Unit,
    appLanguageTag: String,
    onAppLanguageChange: (String) -> Unit,
    appThemeMode: AppThemeMode,
    onAppThemeModeChange: (AppThemeMode) -> Unit,
    appUiStyle: AppUiStyle,
    onAppUiStyleChange: (AppUiStyle) -> Unit,
    materialFloatingBottomBarEnabled: Boolean,
    onMaterialFloatingBottomBarEnabledChange: (Boolean) -> Unit,
    materialHapticsEnabled: Boolean,
    onMaterialHapticsEnabledChange: (Boolean) -> Unit,
    materialSwitchIconsEnabled: Boolean,
    onMaterialSwitchIconsEnabledChange: (Boolean) -> Unit,
    appThemeKeyColor: Long?,
    onAppThemeKeyColorChange: (Long?) -> Unit,
    appThemePaletteStyle: Int,
    onAppThemePaletteStyleChange: (Int) -> Unit,
    appThemeColorSpec: Int,
    onAppThemeColorSpecChange: (Int) -> Unit,
    permissionMonitorVisible: Boolean,
    onPermissionMonitorVisibleChange: (Boolean) -> Unit,
    nativeNotifyIconEnabled: Boolean,
    onNativeNotifyIconEnabledChange: (Boolean) -> Unit,
    nativeNotificationBubblesEnabled: Boolean,
    onNativeNotificationBubblesEnabledChange: (Boolean) -> Unit,
    systemUiInternationalNetworkDisplayEnabled: Boolean,
    onSystemUiInternationalNetworkDisplayEnabledChange: (Boolean) -> Unit,
    systemUiHideMobileRoamingIndicatorEnabled: Boolean,
    onSystemUiHideMobileRoamingIndicatorEnabledChange: (Boolean) -> Unit,
    systemUiInternationalNotificationStyleEnabled: Boolean,
    onSystemUiInternationalNotificationStyleEnabledChange: (Boolean) -> Unit,
    systemUiHideQsEditEnabled: Boolean,
    onSystemUiHideQsEditEnabledChange: (Boolean) -> Unit,
    systemUiHideQsSettingsEnabled: Boolean,
    onSystemUiHideQsSettingsEnabledChange: (Boolean) -> Unit,
    systemUiHideQsTopCarrierEnabled: Boolean,
    onSystemUiHideQsTopCarrierEnabledChange: (Boolean) -> Unit,
    systemUiHideQsMoreEnabled: Boolean,
    onSystemUiHideQsMoreEnabledChange: (Boolean) -> Unit,
    systemUiForceNativeClipboardOverlayEnabled: Boolean,
    onSystemUiForceNativeClipboardOverlayEnabledChange: (Boolean) -> Unit,
    settingsForceGoogleEntryEnabled: Boolean,
    onSettingsForceGoogleEntryEnabledChange: (Boolean) -> Unit,
    gmsRegionRestrictionBypassEnabled: Boolean,
    onGmsRegionRestrictionBypassEnabledChange: (Boolean) -> Unit,
    extremeRefresh165Enabled: Boolean,
    onExtremeRefresh165EnabledChange: (Boolean) -> Unit,
    launcherLayoutUnlocked: Boolean,
    onLauncherLayoutUnlockedChange: (Boolean) -> Unit,
    assistantScreenOption: AssistantScreenOption,
    onAssistantScreenOptionChange: (AssistantScreenOption) -> Unit,
    recentTaskRadiusEnabled: Boolean,
    onRecentTaskRadiusEnabledChange: (Boolean) -> Unit,
    recentTaskRadiusDp: Int,
    onRecentTaskRadiusDpChange: (Int) -> Unit,
    aodEnhanceEnabled: Boolean,
    onAodEnhanceEnabledChange: (Boolean) -> Unit,
    aodInitDarkBrightness: Int,
    onAodInitDarkBrightnessChange: (Int) -> Unit,
    aodInitBrightBrightness: Int,
    onAodInitBrightBrightnessChange: (Int) -> Unit,
    aodRunningBrightnessMultiplier: Float,
    onAodRunningBrightnessMultiplierChange: (Float) -> Unit,
    aodPanoramicSupportEnabled: Boolean,
    onAodPanoramicSupportEnabledChange: (Boolean) -> Unit,
    aodSettingsSwitchEnabled: Boolean,
    onAodSettingsSwitchEnabledChange: (Boolean) -> Unit,
    aodSingleClickBlockEnabled: Boolean,
    onAodSingleClickBlockEnabledChange: (Boolean) -> Unit,
    oosLocalizerEnabled: Boolean,
    onOosLocalizerEnabledChange: (Boolean) -> Unit,
    oosLocalizerConfigMode: Int,
    onOosLocalizerConfigModeChange: (Int) -> Unit,
    oosLocalizerRegion: String,
    onOosLocalizerRegionChange: (String) -> Unit,
    oosLocalizerLocale: String,
    onOosLocalizerLocaleChange: (String) -> Unit,
    oosLocalizerModel: String,
    onOosLocalizerModelChange: (String) -> Unit,
    assistantPowerMode: Int,
    onAssistantPowerModeChange: (Int) -> Unit,
    assistantGestureCircleEnabled: Boolean,
    onAssistantGestureCircleEnabledChange: (Boolean) -> Unit,
    assistantGestureCircleC17Enabled: Boolean,
    onAssistantGestureCircleC17EnabledChange: (Boolean) -> Unit,
    assistantNativePowerEnabled: Boolean,
    onAssistantNativePowerEnabledChange: (Boolean) -> Unit,
    assistantNativeCircleEnabled: Boolean,
    onAssistantNativeCircleEnabledChange: (Boolean) -> Unit,
) {
    val systemInDarkTheme = isSystemInDarkTheme()
    val themeController = remember(appThemeMode, appThemeKeyColor, appThemePaletteStyle, appThemeColorSpec) {
        val paletteStyle = ThemePaletteStyle.entries.getOrNull(appThemePaletteStyle)
            ?: ThemePaletteStyle.TonalSpot
        val colorSpec = ThemeColorSpec.entries.getOrNull(appThemeColorSpec)
            ?: ThemeColorSpec.Spec2021
        ThemeController(
            colorSchemeMode = appThemeMode.toColorSchemeMode(),
            keyColor = if (appThemeMode.isMonet) appThemeKeyColor?.let { Color(it) } else null,
            paletteStyle = paletteStyle,
            colorSpec = colorSpec,
        )
    }
    COUITheme(controller = themeController) {
        ColorOs17AppTheme(nativeAccent = appUiStyle == AppUiStyle.ColorOs && !appThemeMode.isMonet) {
        CompositionLocalProvider(
            LocalAppUiStyle provides appUiStyle,
            LocalMaterialSwitchIconsEnabled provides materialSwitchIconsEnabled,
        ) {
            Material3ExpressiveAppTheme(mode = appThemeMode, keyColor = appThemeKeyColor) {
        val colors = COUITheme.colorScheme
        val blurBackdrop = rememberChromeBlurBackdrop(blurEffectEnabled)
        val liquidBottomBarSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val liquidBackdrop = if (liquidBottomBarSupported && liquidGlassBottomBarEnabled) {
            rememberLiquidLayerBackdrop()
        } else {
            null
        }
        val context = LocalContext.current
        val activity = context.findActivity()
        val statusBarDarkIcons = colors.surface.luminance() > 0.5f
        val navigationBarSurface = if (blurBackdrop != null) colors.surface else colors.background
        val navigationBarDarkIcons = navigationBarSurface.luminance() > 0.5f
        DisposableEffect(activity, statusBarDarkIcons, navigationBarDarkIcons) {
            activity?.window?.let { window ->
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    isAppearanceLightStatusBars = statusBarDarkIcons
                    isAppearanceLightNavigationBars = navigationBarDarkIcons
                }
            }
            onDispose {}
        }
        val hapticClick = rememberHapticClick()
        val visibleTabs = bottomTabs
        // An activity restored from the former four-tab layout may still hold tab 4.
        // Move that selection to Features, where Refresh Rate now lives.
        val selectedPagerIndex = visibleTabs.indexOfFirst {
            it.screenIndex == if (currentTab == 4) 1 else currentTab
        }.coerceAtLeast(0)
        val pagerState = rememberPagerState(
            initialPage = selectedPagerIndex,
            pageCount = { visibleTabs.size },
        )
        val mainPagerState = rememberRootMainPagerState(pagerState)
        LaunchedEffect(mainPagerState.pagerState.currentPage) {
            mainPagerState.syncPage()
        }
        LaunchedEffect(selectedPagerIndex) {
            if (mainPagerState.selectedPage != selectedPagerIndex) {
                mainPagerState.animateToPage(selectedPagerIndex)
            }
        }
        LaunchedEffect(currentTab) {
            if (currentTab == 4) onTabChange(1)
        }
        val selectedIndex = mainPagerState.selectedPage
            .coerceIn(0, (visibleTabs.size - 1).coerceAtLeast(0))
        var aboutAutoCheckUpdateRequest by remember { mutableIntStateOf(0) }
        val serializersModule = remember {
            SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(RootRoute.Main::class)
                    subclass(RootRoute.FeatureDesktop::class)
                    subclass(RootRoute.FeatureSystemUi::class)
                    subclass(RootRoute.FeatureSystemUiNative::class)
                    subclass(RootRoute.FeatureSystemUiDynamicColor::class)
                    subclass(RootRoute.FeatureSystemUiStatusBar::class)
                    subclass(RootRoute.FeatureSystemUiNotificationCenter::class)
                    subclass(RootRoute.FeatureSystemUiControlCenter::class)
                    subclass(RootRoute.FeatureNotificationRemoval::class)
                    subclass(RootRoute.FeatureMobileNetwork::class)
                    subclass(RootRoute.FeatureAndroidSystem::class)
                    subclass(RootRoute.FeatureInstaller::class)
                    subclass(RootRoute.FeatureEsim::class)
                    subclass(RootRoute.FeatureEsimDiagnostics::class)
                    subclass(RootRoute.FeatureAppMarket::class)
                    subclass(RootRoute.FeatureGoogleMessages::class)
                    subclass(RootRoute.FeatureAthena::class)
                    subclass(RootRoute.FeatureSettings::class)
                    subclass(RootRoute.FeatureSettingsRegion::class)
                    subclass(RootRoute.FeatureSecurityPermission::class)
                    subclass(RootRoute.FeatureTouchSampling::class)
                    subclass(RootRoute.FeatureRefreshRate::class)
                    subclass(RootRoute.FeatureWallpapers::class)
                    subclass(RootRoute.FeatureAod::class)
                    subclass(RootRoute.FeatureAssistant::class)
                    subclass(RootRoute.FeatureOPlusLocalizer::class)
                    subclass(RootRoute.FeatureOPlusLocalizerProperties::class)
                    subclass(RootRoute.FeatureOPlusLocalizerScope::class)
                    subclass(RootRoute.AppSettings::class)
                    subclass(RootRoute.DeveloperOptions::class)
                    subclass(RootRoute.SoftwareUpdate::class)
                    subclass(RootRoute.SoftwareUpdateSettings::class)
                    subclass(RootRoute.SoftwareUpdateReleaseNotes::class)
                    subclass(RootRoute.Contributors::class)
                    subclass(RootRoute.References::class)
                }
            }
        }
        val savedStateConfig = remember(serializersModule) {
            SavedStateConfiguration {
                this.serializersModule = serializersModule
            }
        }
        val rootBackStack = rememberNavBackStack(
            configuration = savedStateConfig,
            RootRoute.Main,
        )
        val navigator = remember(rootBackStack) { RootNavigator(rootBackStack) }
        val featureWorkspaceScaleProgress = remember { Animatable(0f) }
        val featureWorkspaceAlphaProgress = remember { Animatable(0f) }
        val featureWorkspaceBlurProgress = remember { Animatable(0f) }
        val featureQueuePressScales = remember {
            FeaturePageMode.entries.associateWith { Animatable(1f) }
        }
        val featureTransitionScope = rememberCoroutineScope()
        var featureLaunchSession by remember { mutableStateOf<FeatureLaunchSession?>(null) }
        var parkedFeatureLaunchSession by remember { mutableStateOf<FeatureLaunchSession?>(null) }
        var featureLaunchAnimation by remember { mutableStateOf<FeatureLaunchAnimation?>(null) }
        var featurePredictiveBackMotion by remember {
            mutableStateOf<FeaturePredictiveBackMotion?>(null)
        }
        val outgoingFeatureAnimations = remember { mutableStateListOf<FeatureLaunchAnimation>() }
        val featureQueuePressTokens = remember { mutableStateMapOf<FeaturePageMode, Long>() }
        val featureQueueHeldTokens = remember { mutableStateMapOf<FeaturePageMode, Long>() }
        val featureExternalIconScaleProviders = remember {
            FeaturePageMode.entries.associateWith { mode ->
                {
                    if (mode in featureQueuePressTokens) {
                        featureQueuePressScales.getValue(mode).value
                    } else {
                        1f
                    }
                }
            }
        }
        var nextFeatureQueuePressToken by remember { mutableStateOf(0L) }
        var featureRouteReadySession by remember { mutableStateOf<FeatureLaunchSession?>(null) }
        val featureLauncherOrigins = remember { mutableStateMapOf<FeaturePageMode, FeatureLaunchOrigin>() }
        var suppressRootTransition by remember { mutableStateOf(false) }
        val movableFeatureEntries = remember {
            FeaturePageMode.entries.associateWith { mode ->
                movableContentOf {
                    val transitionScrollState = rememberScrollState()
                    RootFeatureEntry(mode, transitionScrollState)
                }
            }
        }
        var rootViewportBounds by remember { mutableStateOf(Rect.Zero) }
        val subPageActive = rootBackStack.size > 1
        val softwareUpdateState = rememberSoftwareUpdateUiState(aboutAutoCheckUpdateRequest)
        val currentRootRoute = navigator.current() as? RootRoute ?: RootRoute.Main
        val featurePagerIndex = visibleTabs.indexOfFirst { it.screenIndex == 1 }
        val featureBackgroundPlaybackState = rememberFeatureBackgroundPlaybackState()
        var previousTopLevelTab by remember { mutableIntStateOf(currentTab) }
        LaunchedEffect(currentTab) {
            if (currentTab == 1 && previousTopLevelTab != 1) {
                // A new top-level visit is the only event that restarts the one-shot
                // background. Child routes deliberately keep the same generation.
                featureBackgroundPlaybackState.restart()
            }
            previousTopLevelTab = currentTab
        }
        var featurePageVideoActive by remember(featurePagerIndex) {
            mutableStateOf(featurePagerIndex >= 0 && selectedPagerIndex == featurePagerIndex)
        }
        // Child routes intentionally do not participate in this state. The stable base-scene
        // TextureView must remain mounted behind them so its decoder and completed frame survive.
        LaunchedEffect(
            selectedPagerIndex,
            mainPagerState.isNavigating,
            featurePagerIndex,
        ) {
            featurePageVideoActive = when {
                featurePagerIndex < 0 -> false
                selectedPagerIndex == featurePagerIndex -> true
                mainPagerState.isNavigating -> featurePageVideoActive
                else -> false
            }
        }
        val featurePageIsDark =
            appThemeMode == AppThemeMode.Dark ||
                (appThemeMode == AppThemeMode.System && systemInDarkTheme)
        val featurePageVideoVisible =
            appUiStyle == AppUiStyle.ColorOs &&
            featurePageNewStyleEnabled &&
                !appThemeMode.isMonet &&
                featurePageIsDark &&
                !featurePageVideoHidden &&
                featurePageVideoActive
        val featurePageAuroraVisible =
            appUiStyle == AppUiStyle.ColorOs &&
            featurePageNewStyleEnabled &&
                !appThemeMode.isMonet &&
                (!featurePageIsDark || featurePageVideoHidden) &&
                featurePageVideoActive
        val featurePageDynamicBackgroundVisible =
            featurePageVideoVisible || featurePageAuroraVisible
        var bottomNavigationHeightPx by remember { mutableIntStateOf(0) }
        val density = LocalDensity.current
        val bottomNavigationHeight = with(density) {
            if (bottomNavigationHeightPx > 0) bottomNavigationHeightPx.toDp() else 96.dp
        }

        LaunchedEffect(currentRootRoute) {
            if (
                currentRootRoute == RootRoute.SoftwareUpdate
            ) {
                softwareUpdateState.onCheckUpdates()
            }
        }

        LaunchedEffect(currentRootRoute, featureLaunchAnimation, featureLaunchSession) {
            val session = featureLaunchSession
            val route = session?.mode?.toRootRoute()
            if (
                suppressRootTransition &&
                featureLaunchAnimation == null &&
                route != null &&
                currentRootRoute == route
            ) {
                // NavDisplay must observe the instant spec for the ownership
                // handoff frame. Release it on the next vsync without keeping
                // feature input blocked or rebuilding the destination page.
                withFrameNanos { }
                if (
                    featureLaunchAnimation == null &&
                    featureLaunchSession === session &&
                    navigator.current() == route
                ) {
                    suppressRootTransition = false
                }
            }
        }

        fun resetFeatureIconFeedback() {
            featureQueuePressTokens.clear()
            featureQueueHeldTokens.clear()
            featureTransitionScope.launch {
                coroutineScope {
                    featureQueuePressScales.values.forEach { scale ->
                        launch { scale.snapTo(1f) }
                    }
                }
            }
        }

        fun releaseIconPress(mode: FeaturePageMode, pressToken: Long) {
            if (
                featureQueuePressTokens[mode] != pressToken ||
                featureQueueHeldTokens[mode] == pressToken
            ) {
                return
            }
            featureTransitionScope.launch {
                // Match IconPressAnimManager: ACTION_UP immediately restores
                // the real source view. The floating leash keeps its own frozen
                // pressed geometry, while the hidden source is already back at
                // 1x before it is ever handed back.
                if (
                    featureQueuePressTokens[mode] != pressToken ||
                    featureQueueHeldTokens[mode] == pressToken
                ) {
                    return@launch
                }
                val pressScale = featureQueuePressScales.getValue(mode)
                val remainingDuration = (
                    200f * (1f - pressScale.value).coerceIn(0f, 0.15f) / 0.15f
                ).roundToInt()
                pressScale.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = remainingDuration,
                        easing = FastOutSlowInEasing,
                    ),
                )
                if (
                    featureQueuePressTokens[mode] == pressToken &&
                    featureQueueHeldTokens[mode] != pressToken
                ) {
                    featureQueuePressTokens.remove(mode)
                }
            }
        }

        suspend fun prepareFeatureIconHandoff(session: FeatureLaunchSession) {
            val pressToken = session.pressToken ?: return
            if (
                featureQueuePressTokens[session.mode] != pressToken ||
                featureQueueHeldTokens[session.mode] == pressToken
            ) {
                return
            }
            featureQueuePressScales.getValue(session.mode).snapTo(1f)
            if (
                featureQueuePressTokens[session.mode] == pressToken &&
                featureQueueHeldTokens[session.mode] != pressToken
            ) {
                featureQueuePressTokens.remove(session.mode)
            }
        }

        fun releaseCompletedIconPress(session: FeatureLaunchSession) {
            val pressToken = session.pressToken ?: return
            releaseIconPress(session.mode, pressToken)
        }

        fun currentFeatureWorkspaceVelocity() = FeatureWorkspaceVelocity(
            scale = featureWorkspaceScaleProgress.velocity,
            alpha = featureWorkspaceAlphaProgress.velocity,
            blur = featureWorkspaceBlurProgress.velocity,
        )

        fun beginFeatureClose(
            session: FeatureLaunchSession,
            interrupted: Boolean,
            predictiveBackMotion: FeaturePredictiveBackMotion? = null,
        ) {
            // First transfer the one movable detail tree to the floating leash.
            // The animation effect retires the Nav entry on a following frame,
            // after its old scene has replaced the content with a placeholder.
            suppressRootTransition = true
            val runningAnimation = featureLaunchAnimation
            val returnOrigin = featureLauncherOrigins[session.mode]
                ?.toReturnOrigin(
                    workspaceScale = 1f,
                    viewportBounds = rootViewportBounds,
                )
                ?: session.origin.toReturnOrigin(
                    workspaceScale = 1f,
                    viewportBounds = rootViewportBounds,
                )
            featureLaunchAnimation = if (runningAnimation?.session === session) {
                runningAnimation.copy(
                    direction = FeatureLaunchDirection.Closing,
                    returnOrigin = returnOrigin,
                    interrupted = true,
                    preserveWorkspace = runningAnimation.preserveWorkspace,
                    initialVelocity = runningAnimation.motion.velocitySnapshot(),
                    initialWorkspaceVelocity = currentFeatureWorkspaceVelocity(),
                    predictiveBackMotion = predictiveBackMotion
                        ?: runningAnimation.predictiveBackMotion,
                )
            } else {
                FeatureLaunchAnimation(
                    session = session,
                    direction = FeatureLaunchDirection.Closing,
                    motion = FeatureLaunchMotion(
                        initialGeometry = 1f,
                        alphaDirection = FeatureLaunchDirection.Closing,
                        useReturnOrigin = true,
                    ),
                    returnOrigin = returnOrigin,
                    interrupted = interrupted,
                    predictiveBackMotion = predictiveBackMotion,
                )
            }
        }

        fun startFeaturePredictiveBack(): FeaturePredictiveBackMotion? {
            val session = featureLaunchSession ?: parkedFeatureLaunchSession ?: return null
            val route = session.mode.toRootRoute() ?: return null
            if (
                !featurePageNewStyleEnabled ||
                featureLaunchAnimation != null ||
                navigator.current() != route
            ) {
                return null
            }
            if (featureLaunchSession == null) {
                featureLaunchSession = session
                parkedFeatureLaunchSession = null
            }
            featurePredictiveBackMotion?.progressJob?.cancel()
            val motion = FeaturePredictiveBackMotion()
            // Keep the real detail tree on the floating leash and expose Main
            // beneath it before the first non-zero progress frame. This is the
            // single-activity equivalent of C17 receiving the closing app leash
            // while Launcher is already prepared behind it.
            suppressRootTransition = true
            featurePredictiveBackMotion = motion
            navigator.pop()
            return motion
        }

        suspend fun updateFeaturePredictiveBack(
            motion: FeaturePredictiveBackMotion,
            event: BackEventCompat,
        ) {
            if (featurePredictiveBackMotion !== motion) return
            motion.updatePointer(event)
            motion.progressJob?.cancel()
            // BackEvent is already frame-paced by the system compositor. C17's
            // native DynamicAnimation can retarget a SurfaceControl in-place;
            // restarting Compose coroutines for every sample adds a full-frame
            // queue and feels detached from the finger. Drive the sample
            // directly here and reserve springs for cancel/commit only.
            motion.progress.snapTo(event.progress.coerceIn(0f, 1f))
        }

        fun cancelFeaturePredictiveBack(motion: FeaturePredictiveBackMotion) {
            motion.progressJob?.cancel()
            motion.progressJob = featureTransitionScope.launch {
                val initialVelocity = motion.progress.velocity
                motion.progress.stop()
                motion.progress.animateTo(
                    targetValue = 0f,
                    initialVelocity = initialVelocity,
                    animationSpec = spring(
                        dampingRatio = C17_PREDICTIVE_RESET_DAMPING,
                        stiffness = C17_PREDICTIVE_RESET_STIFFNESS,
                        visibilityThreshold = 0.0005f,
                    ),
                )
                if (
                    featurePredictiveBackMotion === motion &&
                    featureLaunchAnimation == null
                ) {
                    val session = featureLaunchSession
                    val route = session?.mode?.toRootRoute()
                    if (route != null && navigator.current() != route) {
                        navigator.push(route)
                    }
                    featurePredictiveBackMotion = null
                }
            }
        }

        suspend fun commitFeaturePredictiveBack(motion: FeaturePredictiveBackMotion) {
            if (featurePredictiveBackMotion !== motion) return
            motion.progressJob?.cancel()
            val initialVelocity = motion.progress.velocity
            motion.progress.stop()
            val session = featureLaunchSession ?: return
            // C17's Launcher content begins the continuation from the exact
            // amount already revealed by the hand-follow window.
            val workspaceStart = motion.c17WindowScale()
            coroutineScope {
                launch { featureWorkspaceScaleProgress.snapTo(workspaceStart) }
                launch { featureWorkspaceAlphaProgress.snapTo(workspaceStart) }
                launch { featureWorkspaceBlurProgress.snapTo(workspaceStart) }
            }
            beginFeatureClose(
                session = session,
                interrupted = true,
                predictiveBackMotion = motion,
            )
            featurePredictiveBackMotion = null
            // The inner close leash now travels to the source icon. Remove the
            // hand-follow coordinate space in parallel, preserving its current
            // value and velocity so the ownership handoff has no reset frame.
            motion.progressJob = featureTransitionScope.launch {
                motion.progress.animateTo(
                    targetValue = 0f,
                    initialVelocity = initialVelocity,
                    animationSpec = spring(
                        dampingRatio = C17_PREDICTIVE_RESET_DAMPING,
                        stiffness = C17_PREDICTIVE_RESET_STIFFNESS,
                        visibilityThreshold = 0.0005f,
                    ),
                )
            }
        }

        fun popRootRoute() {
            when (featureLaunchAnimation?.direction) {
                FeatureLaunchDirection.Opening -> {
                    val animation = featureLaunchAnimation ?: return
                    beginFeatureClose(animation.session, interrupted = true)
                    return
                }
                FeatureLaunchDirection.Closing -> return
                null -> Unit
            }
            val session = featureLaunchSession ?: parkedFeatureLaunchSession
            val sessionRoute = session?.mode?.toRootRoute()
            if (
                featurePageNewStyleEnabled &&
                featureLaunchAnimation == null &&
                session != null &&
                sessionRoute != null &&
                navigator.current() == sessionRoute
            ) {
                if (featureLaunchSession == null) {
                    featureLaunchSession = session
                    parkedFeatureLaunchSession = null
                }
                beginFeatureClose(session, interrupted = false)
                return
            }
            navigator.pop()
        }

        fun popToMainRoute() {
            featurePredictiveBackMotion?.progressJob?.cancel()
            featurePredictiveBackMotion = null
            featureLaunchAnimation = null
            featureLaunchSession = null
            parkedFeatureLaunchSession = null
            outgoingFeatureAnimations.forEach {
                it.predictiveBackMotion?.progressJob?.cancel()
            }
            outgoingFeatureAnimations.clear()
            featureRouteReadySession = null
            suppressRootTransition = false
            resetFeatureIconFeedback()
            navigator.popUntil { it is RootRoute.Main }
        }

        fun openFeatureSubPage(mode: FeaturePageMode) {
            val route = mode.toRootRoute() ?: return
            if (featureLaunchAnimation != null) return
            if (navigator.current() == route) return
            if (mode.isNestedPage) {
                // Nested feature pages must use NavDisplay for both directions.
                // Park the launcher-style leash until the parent itself goes back to
                // the feature grid; otherwise it covers the nested transition.
                parkedFeatureLaunchSession = featureLaunchSession ?: parkedFeatureLaunchSession
                featureLaunchSession = null
                featureRouteReadySession = null
                suppressRootTransition = false
            }
            navigator.push(route)
        }

        fun openFeatureSubPageFromMain(
            mode: FeaturePageMode,
            origin: FeatureLaunchOrigin?,
            pressToken: Long?,
        ) {
            val route = mode.toRootRoute() ?: return
            // The real launcher item owns its own ACTION_UP feedback. Only the
            // transparent rapid-tap layer needs an external press token.
            val launchPressToken = pressToken
            val runningAnimation = featureLaunchAnimation
            if (runningAnimation?.direction == FeatureLaunchDirection.Opening) {
                // C17's OplusDragLayer intercepts launcher input while an app
                // opening record owns the transition. Do not let either the
                // real source grid or a stale queued gesture launch through
                // the foreground task surface.
                return
            }
            if (
                launchPressToken != null &&
                runningAnimation?.direction != FeatureLaunchDirection.Closing
            ) {
                // Queue tokens belong only to the launcher-visible close /
                // reverse phase. A token completing after that ownership has
                // changed is stale and must only run its press cleanup.
                return
            }
            val stableSession = featureLaunchSession?.takeIf { session ->
                runningAnimation == null && navigator.current() == session.mode.toRootRoute()
            }
            val activeAnimation = runningAnimation ?: stableSession?.let { session ->
                FeatureLaunchAnimation(
                    session = session,
                    direction = FeatureLaunchDirection.Opening,
                    motion = FeatureLaunchMotion(
                        initialGeometry = 1f,
                        alphaDirection = FeatureLaunchDirection.Closing,
                        useReturnOrigin = true,
                    ),
                    interrupted = true,
                    preserveWorkspace = true,
                )
            }

            if (activeAnimation != null && featurePageNewStyleEnabled && origin != null) {
                if (mode == activeAnimation.session.mode) {
                    // C17 reverses the same running leash in place. Reusing the
                    // motion object preserves its exact current frame; there is
                    // no close-end queue or restart from either endpoint.
                    if (runningAnimation?.direction == FeatureLaunchDirection.Closing) {
                        val resumedSession = runningAnimation.session.copy(
                            pressToken = launchPressToken,
                        )
                        featureLaunchSession = resumedSession
                        featureLaunchAnimation = runningAnimation.copy(
                            session = resumedSession,
                            direction = FeatureLaunchDirection.Opening,
                            interrupted = true,
                            preserveWorkspace = true,
                            initialVelocity = runningAnimation.motion.velocitySnapshot(),
                            initialWorkspaceVelocity = currentFeatureWorkspaceVelocity(),
                        )
                    }
                    return
                }

                // AnimationRegistry in C17 keeps every running close record.
                // Transfer the foreground leash to that registry immediately;
                // a newer tap never waits for an older close to finish.
                val oldRoute = activeAnimation.session.mode.toRootRoute()
                if (oldRoute != null && navigator.current() == oldRoute) {
                    suppressRootTransition = true
                    navigator.pop()
                }
                val replacedOutgoingAnimations = outgoingFeatureAnimations.filter {
                    it.session.mode == activeAnimation.session.mode
                }
                outgoingFeatureAnimations.removeAll(replacedOutgoingAnimations)
                replacedOutgoingAnimations.forEach { replaced ->
                    releaseCompletedIconPress(replaced.session)
                }
                val outgoingReturnOrigin = activeAnimation.returnOrigin
                    ?: featureLauncherOrigins[activeAnimation.session.mode]
                        ?.toReturnOrigin(
                            workspaceScale = 1f,
                            viewportBounds = rootViewportBounds,
                        )
                    ?: activeAnimation.session.origin.toReturnOrigin(
                        workspaceScale = 1f,
                        viewportBounds = rootViewportBounds,
                    )
                outgoingFeatureAnimations += activeAnimation.copy(
                    direction = FeatureLaunchDirection.Closing,
                    returnOrigin = outgoingReturnOrigin,
                    interrupted = true,
                    preserveWorkspace = true,
                    initialVelocity = activeAnimation.motion.velocitySnapshot(),
                )

                // If this icon already owns an outgoing close, promote that
                // exact leash back to foreground and reverse it. This also keeps
                // one movable content tree per mode, avoiding duplicate/ghost
                // icons during A -> B -> A and longer rapid sequences.
                val resumedAnimation = outgoingFeatureAnimations
                    .lastOrNull { it.session.mode == mode }
                    ?.also(outgoingFeatureAnimations::remove)
                val nextSession = resumedAnimation?.session?.copy(
                    // A running leash owns an immutable geometric target. A
                    // later A -> B -> A tap may replace only gesture ownership;
                    // retargeting its origin makes the same scalar jump.
                    pressToken = launchPressToken,
                ) ?: FeatureLaunchSession(
                    mode = mode,
                    origin = origin,
                    pressToken = launchPressToken,
                )
                featureLaunchSession = nextSession
                featureRouteReadySession = null
                featureLaunchAnimation = FeatureLaunchAnimation(
                    session = nextSession,
                    direction = FeatureLaunchDirection.Opening,
                    motion = resumedAnimation?.motion ?: FeatureLaunchMotion(
                        initialGeometry = 0f,
                        alphaDirection = FeatureLaunchDirection.Opening,
                        useReturnOrigin = false,
                    ),
                    returnOrigin = resumedAnimation?.returnOrigin,
                    interrupted = resumedAnimation != null,
                    preserveWorkspace = true,
                    initialVelocity = resumedAnimation?.motion?.velocitySnapshot(),
                    initialWorkspaceVelocity = currentFeatureWorkspaceVelocity(),
                )
                return
            }

            if (featurePageNewStyleEnabled && origin != null) {
                // The foreground close can finish before an older parallel
                // leash. A tap on that older icon must still promote and
                // reverse its existing leash; creating a second owner for the
                // same movableContent produces the intermittent missing/wrong
                // icon seen in A -> B -> C -> A stress taps.
                val resumedAnimation = outgoingFeatureAnimations
                    .lastOrNull { it.session.mode == mode }
                if (resumedAnimation != null) {
                    outgoingFeatureAnimations.remove(resumedAnimation)
                    val resumedSession = resumedAnimation.session.copy(
                        pressToken = launchPressToken,
                    )
                    featureLaunchSession = resumedSession
                    featureRouteReadySession = null
                    featureLaunchAnimation = resumedAnimation.copy(
                        session = resumedSession,
                        direction = FeatureLaunchDirection.Opening,
                        interrupted = true,
                        preserveWorkspace = true,
                        initialVelocity = resumedAnimation.motion.velocitySnapshot(),
                        initialWorkspaceVelocity = currentFeatureWorkspaceVelocity(),
                    )
                    return
                }
            }

            if (runningAnimation != null) return
            if (navigator.current() == route) return
            if (
                featurePageNewStyleEnabled &&
                origin != null &&
                rootViewportBounds.width > 0f &&
                rootViewportBounds.height > 0f
            ) {
                val session = FeatureLaunchSession(
                    mode = mode,
                    origin = origin,
                    pressToken = launchPressToken,
                )
                featureLaunchSession = session
                featureLaunchAnimation = FeatureLaunchAnimation(
                    session = session,
                    direction = FeatureLaunchDirection.Opening,
                    motion = FeatureLaunchMotion(
                        initialGeometry = 0f,
                        alphaDirection = FeatureLaunchDirection.Opening,
                        useReturnOrigin = false,
                    ),
                )
            } else {
                featureLaunchSession = null
                navigator.push(route)
            }
        }

        fun openAboutSubPage(mode: AboutPageMode) {
            val route = mode.toRootRoute() ?: return
            if (navigator.current() == route) return
            featurePredictiveBackMotion?.progressJob?.cancel()
            featurePredictiveBackMotion = null
            featureLaunchAnimation = null
            featureLaunchSession = null
            parkedFeatureLaunchSession = null
            outgoingFeatureAnimations.forEach {
                it.predictiveBackMotion?.progressJob?.cancel()
            }
            outgoingFeatureAnimations.clear()
            featureRouteReadySession = null
            suppressRootTransition = false
            resetFeatureIconFeedback()
            navigator.push(route)
        }

        LaunchedEffect(featureLaunchAnimation) {
            val animation = featureLaunchAnimation ?: return@LaunchedEffect
            val initialValue = when (animation.direction) {
                FeatureLaunchDirection.Opening -> 0f
                FeatureLaunchDirection.Closing -> 1f
            }
            val targetValue = when (animation.direction) {
                FeatureLaunchDirection.Opening -> 1f
                FeatureLaunchDirection.Closing -> 0f
            }
            if (!animation.interrupted) {
                if (!animation.preserveWorkspace) {
                    featureWorkspaceScaleProgress.snapTo(initialValue)
                    featureWorkspaceAlphaProgress.snapTo(initialValue)
                    featureWorkspaceBlurProgress.snapTo(initialValue)
                }
            }
            if (animation.direction == FeatureLaunchDirection.Closing) {
                val sessionRoute = animation.session.mode.toRootRoute()
                if (sessionRoute != null && navigator.current() == sessionRoute) {
                    navigator.pop()
                }
                // The task stays fully opaque through the first part of the
                // close, so NavDisplay can prepare Main underneath in parallel.
                // C17 starts its rect spring immediately instead of inserting a
                // fixed two-frame hold here.
            }
            val opening = animation.direction == FeatureLaunchDirection.Opening
            val geometryStiffness = if (opening) {
                C17_OPEN_GEOMETRY_STIFFNESS
            } else {
                C17_CLOSE_GEOMETRY_STIFFNESS
            }
            val geometryDamping = if (opening) {
                C17_OPEN_GEOMETRY_DAMPING
            } else {
                C17_CLOSE_GEOMETRY_DAMPING
            }
            val radiusStiffness = if (opening) {
                C17_OPEN_RADIUS_STIFFNESS
            } else {
                C17_CLOSE_RADIUS_STIFFNESS
            }
            val radiusDamping = if (opening) {
                C17_OPEN_RADIUS_DAMPING
            } else {
                C17_CLOSE_RADIUS_DAMPING
            }
            val workspaceStiffness = if (opening) {
                C17_OPEN_WORKSPACE_STIFFNESS
            } else {
                C17_CLOSE_WORKSPACE_STIFFNESS
            }
            val workspaceDamping = if (opening) {
                C17_OPEN_WORKSPACE_DAMPING
            } else {
                C17_CLOSE_WORKSPACE_DAMPING
            }
            val blurStiffness = if (opening) {
                C17_OPEN_BLUR_STIFFNESS
            } else {
                C17_CLOSE_BLUR_STIFFNESS
            }
            val blurDamping = if (opening) {
                C17_OPEN_BLUR_DAMPING
            } else {
                C17_CLOSE_BLUR_DAMPING
            }
            // Geometry is the authoritative lifecycle channel. Workspace
            // effects follow in parallel but never keep an already-finished
            // window registered and its source icon hidden.
            coroutineScope {
                val workspaceScaleJob = launch {
                    featureWorkspaceScaleProgress.animateTo(
                        targetValue = targetValue,
                        initialVelocity = animation.initialWorkspaceVelocity?.scale
                            ?: featureWorkspaceScaleProgress.velocity,
                        animationSpec = spring(
                            dampingRatio = workspaceDamping,
                            stiffness = workspaceStiffness,
                            visibilityThreshold = 0.001f,
                        ),
                    )
                }
                val workspaceAlphaJob = launch {
                    featureWorkspaceAlphaProgress.animateTo(
                        targetValue = targetValue,
                        initialVelocity = animation.initialWorkspaceVelocity?.alpha
                            ?: featureWorkspaceAlphaProgress.velocity,
                        animationSpec = spring(
                            dampingRatio = workspaceDamping,
                            stiffness = workspaceStiffness,
                            visibilityThreshold = 0.001f,
                        ),
                    )
                }
                val workspaceBlurJob = launch {
                    featureWorkspaceBlurProgress.animateTo(
                        targetValue = targetValue,
                        initialVelocity = animation.initialWorkspaceVelocity?.blur
                            ?: featureWorkspaceBlurProgress.velocity,
                        animationSpec = spring(
                            dampingRatio = blurDamping,
                            stiffness = blurStiffness,
                            visibilityThreshold = 0.001f,
                        ),
                    )
                }
                try {
                    animateFeatureLaunchMotion(
                        motion = animation.motion,
                        targetValue = targetValue,
                        initialVelocity = animation.initialVelocity,
                        geometryStiffness = geometryStiffness,
                        geometryDamping = geometryDamping,
                        radiusStiffness = radiusStiffness,
                        radiusDamping = radiusDamping,
                    )
                } finally {
                    workspaceScaleJob.cancel()
                    workspaceAlphaJob.cancel()
                    workspaceBlurJob.cancel()
                }
            }

            if (featureLaunchAnimation !== animation) return@LaunchedEffect
            val route = animation.session.mode.toRootRoute()
            when (animation.direction) {
                FeatureLaunchDirection.Opening -> {
                    suppressRootTransition = true
                    featureRouteReadySession = null
                    if (route != null && navigator.current() != route) {
                        navigator.push(route)
                    }
                    if (route != null) {
                        // NavDisplay switches targets asynchronously. Keep the
                        // opaque leash until the destination placeholder has
                        // actually been placed, then move the same content tree
                        // into it in one composition with no Main/blank flash.
                        withTimeoutOrNull(750L) {
                            snapshotFlow { featureRouteReadySession }
                                .first { it === animation.session }
                        }
                    }
                    if (featureLaunchAnimation !== animation) return@LaunchedEffect
                    animation.predictiveBackMotion?.progressJob?.cancel()
                    featureLaunchAnimation = null
                }
                FeatureLaunchDirection.Closing -> {
                    // Reset the hidden source before dropping the leash owner;
                    // otherwise Compose exposes a stale 0.85x icon for a frame
                    // and then visibly grows it back to 1x.
                    prepareFeatureIconHandoff(animation.session)
                    // Once the foreground (last-clicked) app has completed its
                    // full C17 close and Main is being handed back, no older
                    // parallel leash may remain above the now-visible launcher.
                    // C17 suppresses those stale icon surfaces and restores each
                    // original icon view; letting their springs settle offscreen
                    // here caused the grid to stay at mixed sizes for ~1 second.
                    outgoingFeatureAnimations.toList().forEach { outgoing ->
                        prepareFeatureIconHandoff(outgoing.session)
                        outgoing.predictiveBackMotion?.progressJob?.cancel()
                    }
                    outgoingFeatureAnimations.clear()
                    featureRouteReadySession = null
                    animation.predictiveBackMotion?.progressJob?.cancel()
                    featureLaunchAnimation = null
                    featureLaunchSession = null
                    suppressRootTransition = false
                }
            }
        }

        LaunchedEffect(openSoftwareUpdateRequest) {
            if (openSoftwareUpdateRequest > 0) {
                featurePredictiveBackMotion?.progressJob?.cancel()
                featurePredictiveBackMotion = null
                featureLaunchAnimation = null
                featureLaunchSession = null
                outgoingFeatureAnimations.forEach {
                    it.predictiveBackMotion?.progressJob?.cancel()
                }
                outgoingFeatureAnimations.clear()
                featureRouteReadySession = null
                suppressRootTransition = false
                resetFeatureIconFeedback()
                navigator.popUntil { it is RootRoute.Main }
                navigator.push(RootRoute.SoftwareUpdate)
            }
        }

        val rootUiState = RootUiState(
            currentTab = currentTab,
            rootGranted = rootGranted,
            blurEffectEnabled = blurEffectEnabled,
            featurePageNewStyleEnabled = featurePageNewStyleEnabled,
            featurePageVideoHidden = featurePageVideoHidden,
            featurePageVideoActive = featurePageDynamicBackgroundVisible,
            hiddenFeatureSourceModes = buildSet {
                featureLaunchSession?.mode?.let(::add)
                outgoingFeatureAnimations.forEach { add(it.session.mode) }
            },
            featureExternalIconScales = featureExternalIconScaleProviders,
            popDirectionFollowsSwipeEdge = popDirectionFollowsSwipeEdge,
            showFpsMonitor = showFpsMonitor,
            liquidGlassBottomBarEnabled = liquidGlassBottomBarEnabled && liquidBackdrop != null,
            appLanguageTag = appLanguageTag,
            appThemeMode = appThemeMode,
            appUiStyle = appUiStyle,
            materialFloatingBottomBarEnabled = materialFloatingBottomBarEnabled,
            materialHapticsEnabled = materialHapticsEnabled,
            materialSwitchIconsEnabled = materialSwitchIconsEnabled,
            appThemeKeyColor = appThemeKeyColor,
            appThemePaletteStyle = appThemePaletteStyle,
            appThemeColorSpec = appThemeColorSpec,
            permissionMonitorVisible = permissionMonitorVisible,
            nativeNotifyIconEnabled = nativeNotifyIconEnabled,
            nativeNotificationBubblesEnabled = nativeNotificationBubblesEnabled,
            systemUiInternationalNetworkDisplayEnabled =
                systemUiInternationalNetworkDisplayEnabled,
            systemUiHideMobileRoamingIndicatorEnabled =
                systemUiHideMobileRoamingIndicatorEnabled,
            systemUiInternationalNotificationStyleEnabled =
                systemUiInternationalNotificationStyleEnabled,
            systemUiHideQsEditEnabled = systemUiHideQsEditEnabled,
            systemUiHideQsSettingsEnabled = systemUiHideQsSettingsEnabled,
            systemUiHideQsTopCarrierEnabled = systemUiHideQsTopCarrierEnabled,
            systemUiHideQsMoreEnabled = systemUiHideQsMoreEnabled,
            systemUiForceNativeClipboardOverlayEnabled = systemUiForceNativeClipboardOverlayEnabled,
            settingsForceGoogleEntryEnabled = settingsForceGoogleEntryEnabled,
            gmsRegionRestrictionBypassEnabled = gmsRegionRestrictionBypassEnabled,
            extremeRefresh165Enabled = extremeRefresh165Enabled,
            launcherLayoutUnlocked = launcherLayoutUnlocked,
            assistantScreenOption = assistantScreenOption,
            recentTaskRadiusEnabled = recentTaskRadiusEnabled,
            recentTaskRadiusDp = recentTaskRadiusDp,
            aodEnhanceEnabled = aodEnhanceEnabled,
            aodInitDarkBrightness = aodInitDarkBrightness,
            aodInitBrightBrightness = aodInitBrightBrightness,
            aodRunningBrightnessMultiplier = aodRunningBrightnessMultiplier,
            aodPanoramicSupportEnabled = aodPanoramicSupportEnabled,
            aodSettingsSwitchEnabled = aodSettingsSwitchEnabled,
            aodSingleClickBlockEnabled = aodSingleClickBlockEnabled,
            oosLocalizerEnabled = oosLocalizerEnabled,
            oosLocalizerConfigMode = oosLocalizerConfigMode,
            oosLocalizerRegion = oosLocalizerRegion,
            oosLocalizerLocale = oosLocalizerLocale,
            oosLocalizerModel = oosLocalizerModel,
            assistantPowerMode = assistantPowerMode,
            assistantGestureCircleEnabled = assistantGestureCircleEnabled,
            assistantGestureCircleC17Enabled = assistantGestureCircleC17Enabled,
            assistantNativePowerEnabled = assistantNativePowerEnabled,
            assistantNativeCircleEnabled = assistantNativeCircleEnabled,
            visibleTabs = visibleTabs,
            selectedIndex = selectedIndex,
            bottomNavigationHeight = bottomNavigationHeight,
            blurBackdrop = blurBackdrop,
            liquidBackdrop = liquidBackdrop,
            softwareUpdateState = softwareUpdateState,
        )
        val rootActions = RootActions(
            onTabChange = onTabChange,
            onBlurEffectEnabledChange = onBlurEffectEnabledChange,
            onFeaturePageNewStyleEnabledChange = onFeaturePageNewStyleEnabledChange,
            onFeaturePageVideoHiddenChange = onFeaturePageVideoHiddenChange,
            onPopDirectionFollowsSwipeEdgeChange = onPopDirectionFollowsSwipeEdgeChange,
            onShowFpsMonitorChange = onShowFpsMonitorChange,
            onLiquidGlassBottomBarEnabledChange = onLiquidGlassBottomBarEnabledChange,
            onAppLanguageChange = onAppLanguageChange,
            onAppThemeModeChange = onAppThemeModeChange,
            onAppUiStyleChange = onAppUiStyleChange,
            onMaterialFloatingBottomBarEnabledChange = onMaterialFloatingBottomBarEnabledChange,
            onMaterialHapticsEnabledChange = onMaterialHapticsEnabledChange,
            onMaterialSwitchIconsEnabledChange = onMaterialSwitchIconsEnabledChange,
            onAppThemeKeyColorChange = onAppThemeKeyColorChange,
            onAppThemePaletteStyleChange = onAppThemePaletteStyleChange,
            onAppThemeColorSpecChange = onAppThemeColorSpecChange,
            onPermissionMonitorVisibleChange = onPermissionMonitorVisibleChange,
            onNativeNotifyIconEnabledChange = onNativeNotifyIconEnabledChange,
            onNativeNotificationBubblesEnabledChange = onNativeNotificationBubblesEnabledChange,
            onSystemUiInternationalNetworkDisplayEnabledChange =
                onSystemUiInternationalNetworkDisplayEnabledChange,
            onSystemUiHideMobileRoamingIndicatorEnabledChange =
                onSystemUiHideMobileRoamingIndicatorEnabledChange,
            onSystemUiInternationalNotificationStyleEnabledChange =
                onSystemUiInternationalNotificationStyleEnabledChange,
            onSystemUiHideQsEditEnabledChange = onSystemUiHideQsEditEnabledChange,
            onSystemUiHideQsSettingsEnabledChange = onSystemUiHideQsSettingsEnabledChange,
            onSystemUiHideQsTopCarrierEnabledChange = onSystemUiHideQsTopCarrierEnabledChange,
            onSystemUiHideQsMoreEnabledChange = onSystemUiHideQsMoreEnabledChange,
            onSystemUiForceNativeClipboardOverlayEnabledChange = onSystemUiForceNativeClipboardOverlayEnabledChange,
            onSettingsForceGoogleEntryEnabledChange = onSettingsForceGoogleEntryEnabledChange,
            onGmsRegionRestrictionBypassEnabledChange = onGmsRegionRestrictionBypassEnabledChange,
            onExtremeRefresh165EnabledChange = onExtremeRefresh165EnabledChange,
            onLauncherLayoutUnlockedChange = onLauncherLayoutUnlockedChange,
            onAssistantScreenOptionChange = onAssistantScreenOptionChange,
            onRecentTaskRadiusEnabledChange = onRecentTaskRadiusEnabledChange,
            onRecentTaskRadiusDpChange = onRecentTaskRadiusDpChange,
            onAodEnhanceEnabledChange = onAodEnhanceEnabledChange,
            onAodInitDarkBrightnessChange = onAodInitDarkBrightnessChange,
            onAodInitBrightBrightnessChange = onAodInitBrightBrightnessChange,
            onAodRunningBrightnessMultiplierChange = onAodRunningBrightnessMultiplierChange,
            onAodPanoramicSupportEnabledChange = onAodPanoramicSupportEnabledChange,
            onAodSettingsSwitchEnabledChange = onAodSettingsSwitchEnabledChange,
            onAodSingleClickBlockEnabledChange = onAodSingleClickBlockEnabledChange,
            onOosLocalizerEnabledChange = onOosLocalizerEnabledChange,
            onOosLocalizerConfigModeChange = onOosLocalizerConfigModeChange,
            onOosLocalizerRegionChange = onOosLocalizerRegionChange,
            onOosLocalizerLocaleChange = onOosLocalizerLocaleChange,
            onOosLocalizerModelChange = onOosLocalizerModelChange,
            onAssistantPowerModeChange = onAssistantPowerModeChange,
            onAssistantGestureCircleEnabledChange = onAssistantGestureCircleEnabledChange,
            onAssistantGestureCircleC17EnabledChange =
                onAssistantGestureCircleC17EnabledChange,
            onAssistantNativePowerEnabledChange = onAssistantNativePowerEnabledChange,
            onAssistantNativeCircleEnabledChange = onAssistantNativeCircleEnabledChange,
            onBottomNavigationHeightChange = { bottomNavigationHeightPx = it },
            onHapticClick = hapticClick,
            popRootRoute = ::popRootRoute,
            popToMainRoute = ::popToMainRoute,
            openFeatureSubPage = ::openFeatureSubPage,
            openFeatureSubPageFromMain = { mode, origin ->
                openFeatureSubPageFromMain(mode, origin, pressToken = null)
            },
            openAboutSubPage = ::openAboutSubPage,
            requestAboutAutoCheckUpdate = { aboutAutoCheckUpdateRequest += 1 },
        )

        val bottomChrome: @Composable (Boolean, Boolean) -> Unit = { visible, liquidOnly ->
        val ui = LocalRootUiState.current
        val actions = LocalRootActions.current
        val mainPagerState = LocalMainPagerState.current
            val liquidBackdrop = ui.liquidBackdrop
            val liquidGlassActive = ui.appUiStyle == AppUiStyle.ColorOs &&
                ui.liquidGlassBottomBarEnabled && liquidBackdrop != null
            val liquidGlassVisible = visible && liquidOnly && liquidGlassActive
            AnimatedVisibility(
                visible = liquidGlassVisible,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
            ) {
                liquidBackdrop?.let { backdrop ->
                    LiquidGlassBottomNavigationBar(
                        tabs = ui.visibleTabs,
                        selectedIndex = ui.selectedIndex,
                        pagerState = mainPagerState.pagerState,
                        backdrop = backdrop,
                        onTabSelected = { index ->
                            actions.onHapticClick()
                            actions.popToMainRoute()
                            mainPagerState.animateToPage(index)
                            actions.onTabChange(ui.visibleTabs[index].screenIndex)
                        },
                        modifier = Modifier
                            .onGloballyPositioned { coordinates ->
                                actions.onBottomNavigationHeightChange(coordinates.size.height)
                            },
                    )
                }
            }
            AnimatedVisibility(
                visible = visible && !liquidOnly && !liquidGlassActive,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (ui.appUiStyle == AppUiStyle.Material3Expressive) {
                            Material3ExpressiveBottomNavigationBar(
                                tabs = ui.visibleTabs,
                                selectedIndex = ui.selectedIndex,
                                floating = ui.materialFloatingBottomBarEnabled,
                                onTabSelected = { index ->
                                    if (ui.materialHapticsEnabled) actions.onHapticClick()
                                    actions.popToMainRoute()
                                    mainPagerState.animateToPage(index)
                                    actions.onTabChange(ui.visibleTabs[index].screenIndex)
                                },
                                modifier = Modifier.onGloballyPositioned { coordinates ->
                                    actions.onBottomNavigationHeightChange(coordinates.size.height)
                                },
                            )
                        } else BottomNavigationBar(
                            items = ui.visibleTabs.mapIndexed { index, tab ->
                                NavigationItem(
                                    label = stringResource(tab.titleRes),
                                    icon = if (index == ui.selectedIndex) tab.selectedIcon else tab.unselectedIcon,
                                )
                            },
                            selectedIndex = ui.selectedIndex,
                            onItemSelected = { index ->
                                actions.onHapticClick()
                                actions.popToMainRoute()
                                mainPagerState.animateToPage(index)
                                actions.onTabChange(ui.visibleTabs[index].screenIndex)
                            },
                            color = Color.Transparent,
                            showDivider = false,
                            mode = NavigationBarDisplayMode.IconAndText,
                            selectedIcons = ui.visibleTabs.map { it.selectedIcon },
                            unselectedIcons = ui.visibleTabs.map { it.unselectedIcon },
                            modifier = Modifier
                                .onGloballyPositioned { coordinates ->
                                    actions.onBottomNavigationHeightChange(coordinates.size.height)
                                },
                        )
                    }
                }
            }
        }
        val featureEntryContent: @Composable (FeaturePageMode) -> Unit = { mode ->
            val animation = featureLaunchAnimation
            val session = featureLaunchSession
            val ownedByFloatingLeash = session?.mode == mode ||
                outgoingFeatureAnimations.any { it.session.mode == mode }
            if (ownedByFloatingLeash) {
                // While the floating leash owns the real movable tree, the Nav
                // scene gets only a readiness marker. This prevents Compose from
                // invoking the same movable content at two positions and lets us
                // wait until NavDisplay has actually placed its async target.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned {
                            if (
                                session?.mode == mode &&
                                animation?.direction == FeatureLaunchDirection.Opening &&
                                navigator.current() == mode.toRootRoute()
                            ) {
                                featureRouteReadySession = session
                            }
                        },
                )
            } else {
                movableFeatureEntries.getValue(mode).invoke()
            }
        }
        val rootEntryProvider = remember(rootBackStack) {
            entryProvider<NavKey> {
            entry<RootRoute.Main> {
                        val ui = LocalRootUiState.current
                        val actions = LocalRootActions.current
                        val mainPagerState = LocalMainPagerState.current
                        val mainSceneContainerColor = if (
                            ui.appUiStyle == AppUiStyle.ColorOs &&
                            ui.featurePageNewStyleEnabled && ui.featurePageVideoActive
                        ) {
                            Color.Transparent
                        } else if (ui.appUiStyle == AppUiStyle.Material3Expressive) {
                            androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer
                        } else {
                            COUITheme.colorScheme.surface
                        }
                        Scaffold(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(mainSceneContainerColor),
                            containerColor = mainSceneContainerColor,
                            popupHost = {},
                            bottomBar = { bottomChrome(true, false) },
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                HorizontalPager(
                                    state = mainPagerState.pagerState,
                                    userScrollEnabled = false,
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.fillMaxSize(),
                                ) { pageIndex ->
                                    val targetTab = ui.visibleTabs.getOrNull(pageIndex)?.screenIndex ?: ui.currentTab
                                    when (targetTab) {
                                        1 -> FeatureMainRoute(
                                            modifier = Modifier.fillMaxSize(),
                                            subPageBottomExtension = ui.bottomNavigationHeight,
                                            blurBackdrop = if (ui.appUiStyle == AppUiStyle.Material3Expressive) null else ui.blurBackdrop,
                                            newStyleEnabled = ui.appUiStyle == AppUiStyle.ColorOs &&
                                                ui.featurePageNewStyleEnabled,
                                            scrollResetKey = mainPagerState.scrollResetGeneration,
                                            hiddenSourceModes = ui.hiddenFeatureSourceModes,
                                            externalIconScales = ui.featureExternalIconScales,
                                            onLaunchOriginChanged = { mode, origin ->
                                                // boundsInWindow includes the
                                                // workspace graphics transform.
                                                // Freeze canonical 1x source
                                                // bounds while C17 is scaling
                                                // the workspace; otherwise a
                                                // parallel tap applies 0.9x a
                                                // second time and closes toward
                                                // the wrong icon position.
                                                if (
                                                    featureLaunchAnimation == null &&
                                                    outgoingFeatureAnimations.isEmpty() &&
                                                    featureLauncherOrigins[mode] != origin
                                                ) {
                                                    featureLauncherOrigins[mode] = origin
                                                }
                                            },
                                            onOpen = { mode, origin ->
                                                if (ui.appUiStyle == AppUiStyle.Material3Expressive) {
                                                    actions.openFeatureSubPage(mode)
                                                } else {
                                                    actions.openFeatureSubPageFromMain(mode, origin)
                                                }
                                            },
                                        )
                                        2 -> ToolsMainRoute(
                                            modifier = Modifier.fillMaxSize(),
                                            bottomContentPadding = ui.bottomNavigationHeight,
                                            blurBackdrop = if (ui.appUiStyle == AppUiStyle.Material3Expressive) null else ui.blurBackdrop,
                                            scrollResetKey = mainPagerState.scrollResetGeneration,
                                            onOpen = actions.openFeatureSubPage,
                                        )
                                        3 -> AboutMainRoute(
                                            modifier = Modifier.fillMaxSize(),
                                            blurBackdrop = if (ui.appUiStyle == AppUiStyle.Material3Expressive) null else ui.blurBackdrop,
                                            bottomContentPadding = ui.bottomNavigationHeight,
                                            scrollResetKey = mainPagerState.scrollResetGeneration,
                                            onOpenAppSettings = { actions.openAboutSubPage(AboutPageMode.AppSettings) },
                                            onOpenSoftwareUpdate = { actions.openAboutSubPage(AboutPageMode.Update) },
                                            onOpenContributors = { actions.openAboutSubPage(AboutPageMode.Contributors) },
                                            onOpenReferences = { actions.openAboutSubPage(AboutPageMode.References) },
                                        )
                                        else -> Page(
                                            modifier = Modifier.fillMaxSize(),
                                            currentTab = targetTab,
                                            rootGranted = ui.rootGranted,
                                            bottomChromePadding = ui.bottomNavigationHeight,
                                            onHomeHeroLongPress = {
                                                actions.onTabChange(3)
                                                mainPagerState.animateToPage(
                                                    ui.visibleTabs.indexOfFirst { it.screenIndex == 3 }.coerceAtLeast(0),
                                                )
                                                actions.openAboutSubPage(AboutPageMode.Update)
                                                actions.requestAboutAutoCheckUpdate()
                                            },
                                            blurBackdrop = if (ui.appUiStyle == AppUiStyle.Material3Expressive) null else ui.blurBackdrop,
                                            scrollResetKey = mainPagerState.scrollResetGeneration,
                                        )
                                    }
                                }
                            }
                            COUIPopupHost()
                        }
            }
            entry<RootRoute.FeatureDesktop> {
                featureEntryContent(FeaturePageMode.Desktop)
            }
            entry<RootRoute.FeatureSystemUi> {
                featureEntryContent(FeaturePageMode.SystemUi)
            }
            entry<RootRoute.FeatureSystemUiNative> {
                featureEntryContent(FeaturePageMode.SystemUiNative)
            }
            entry<RootRoute.FeatureSystemUiDynamicColor> {
                featureEntryContent(FeaturePageMode.SystemUiDynamicColor)
            }
            entry<RootRoute.FeatureSystemUiStatusBar> {
                featureEntryContent(FeaturePageMode.SystemUiStatusBar)
            }
            entry<RootRoute.FeatureSystemUiNotificationCenter> {
                featureEntryContent(FeaturePageMode.SystemUiNotificationCenter)
            }
            entry<RootRoute.FeatureSystemUiControlCenter> {
                featureEntryContent(FeaturePageMode.SystemUiControlCenter)
            }
            entry<RootRoute.FeatureNotificationRemoval> {
                featureEntryContent(FeaturePageMode.NotificationRemoval)
            }
            entry<RootRoute.FeatureInstaller> {
                featureEntryContent(FeaturePageMode.Installer)
            }
            entry<RootRoute.FeatureMobileNetwork> {
                featureEntryContent(FeaturePageMode.MobileNetwork)
            }
            entry<RootRoute.FeatureAndroidSystem> {
                featureEntryContent(FeaturePageMode.AndroidSystem)
            }
            entry<RootRoute.FeatureEsim> {
                featureEntryContent(FeaturePageMode.Esim)
            }
            entry<RootRoute.FeatureEsimDiagnostics> {
                featureEntryContent(FeaturePageMode.EsimDiagnostics)
            }
            entry<RootRoute.FeatureAppMarket> {
                featureEntryContent(FeaturePageMode.AppMarket)
            }
            entry<RootRoute.FeatureGoogleMessages> {
                featureEntryContent(FeaturePageMode.GoogleMessages)
            }
            entry<RootRoute.FeatureAthena> {
                featureEntryContent(FeaturePageMode.Athena)
            }
            entry<RootRoute.FeatureSettings> {
                featureEntryContent(FeaturePageMode.Settings)
            }
            entry<RootRoute.FeatureSettingsRegion> {
                featureEntryContent(FeaturePageMode.SettingsRegion)
            }
            entry<RootRoute.FeatureSecurityPermission> {
                featureEntryContent(FeaturePageMode.SecurityPermission)
            }
            entry<RootRoute.FeatureTouchSampling> {
                featureEntryContent(FeaturePageMode.TouchSampling)
            }
            entry<RootRoute.FeatureRefreshRate> {
                featureEntryContent(FeaturePageMode.RefreshRate)
            }
            entry<RootRoute.FeatureWallpapers> {
                featureEntryContent(FeaturePageMode.Wallpapers)
            }
            entry<RootRoute.FeatureAod> {
                featureEntryContent(FeaturePageMode.Aod)
            }
            entry<RootRoute.FeatureAssistant> {
                featureEntryContent(FeaturePageMode.Assistant)
            }
            entry<RootRoute.FeatureOPlusLocalizer> {
                featureEntryContent(FeaturePageMode.OPlusLocalizer)
            }
            entry<RootRoute.FeatureOPlusLocalizerProperties> {
                featureEntryContent(FeaturePageMode.OPlusLocalizerProperties)
            }
            entry<RootRoute.FeatureOPlusLocalizerScope> {
                featureEntryContent(FeaturePageMode.OPlusLocalizerScope)
            }
            entry<RootRoute.AppSettings> { RootAboutEntry(AboutPageMode.AppSettings) }
            entry<RootRoute.DeveloperOptions> { RootAboutEntry(AboutPageMode.DeveloperOptions) }
            entry<RootRoute.SoftwareUpdate> { RootAboutEntry(AboutPageMode.Update) }
            entry<RootRoute.SoftwareUpdateSettings> { RootAboutEntry(AboutPageMode.UpdateSettings) }
            entry<RootRoute.SoftwareUpdateReleaseNotes> {
                RootAboutEntry(AboutPageMode.UpdateReleaseNotes)
            }
            entry<RootRoute.Contributors> { RootAboutEntry(AboutPageMode.Contributors) }
            entry<RootRoute.References> { RootAboutEntry(AboutPageMode.References) }
            }
        }

        val rootEntries = rememberDecoratedNavEntries(
            backStack = rootBackStack,
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
            entryProvider = rootEntryProvider,
        )

        val rootTransitionEffects = remember(popDirectionFollowsSwipeEdge, appUiStyle) {
            NavDisplayTransitionEffects(
                // COUI detail pages draw through the status bar. Clipping the
                // entire navigation scene rounds off and cuts that top strip
                // while a nested page slides in or out.
                enableCornerClip = appUiStyle == AppUiStyle.Material3Expressive,
                dimAmount = 0.5f,
                blockInputDuringTransition = true,
                popDirectionFollowsSwipeEdge = false,
            )
        }
        MainScreenBackHandler(
            mainPagerState = mainPagerState,
            navigator = navigator,
            onTabChange = onTabChange,
        )

        CompositionLocalProvider(
            LocalRootNavigator provides navigator,
            LocalMainPagerState provides mainPagerState,
            LocalRootUiState provides rootUiState,
            LocalRootActions provides rootActions,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        rootViewportBounds = coordinates.boundsInWindow()
                    },
            ) {
                val activeAnimation = featureLaunchAnimation
                val activeSession = featureLaunchSession
                val handFollowMotion = featurePredictiveBackMotion
                val handFollowWorkspaceProgress = handFollowMotion?.c17WindowScale()
                val launchWorkspaceScaleProgress = handFollowWorkspaceProgress
                    ?: featureWorkspaceScaleProgress.value
                val launchWorkspaceAlphaProgress = handFollowWorkspaceProgress
                    ?: featureWorkspaceAlphaProgress.value
                val launchWorkspaceBlurProgress = handFollowWorkspaceProgress
                    ?: featureWorkspaceBlurProgress.value
                val workspaceVisualScale = lerpFloat(
                    1f,
                    C17_SOURCE_SCALE,
                    launchWorkspaceScaleProgress.coerceIn(0f, 1f),
                )
                val baseScene: @Composable () -> Unit = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.surface),
                    ) {
                        // This sibling is outside NavDisplay, so replacing Main with a child entry
                        // cannot dispose its TextureView. Top-level pager visibility is still
                        // allowed to remove it because the next visit starts a new generation.
                        if (featurePageAuroraVisible) {
                            FeaturePageAuroraBackground(
                                isDarkTheme = featurePageIsDark,
                                surfaceColor = colors.surface,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else if (featurePageVideoVisible) {
                            FeaturePageVideoBackground(
                                playbackState = featureBackgroundPlaybackState,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        if (
                            currentRootRoute != RootRoute.Main &&
                            featureLaunchSession == null &&
                            featurePageDynamicBackgroundVisible
                        ) {
                            // Nested feature pages use NavDisplay's slide transition.
                            // Cover the main feature page's aurora/video where both
                            // transitioning pages leave a transparent edge.
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(colors.surface),
                            )
                        }
                        NavDisplay(
                            entries = rootEntries,
                            modifier = Modifier
                                .fillMaxSize()
                                .imePadding()
                                .then(
                                    if (
                                        rootUiState.liquidGlassBottomBarEnabled &&
                                        rootUiState.liquidBackdrop != null
                                    ) {
                                        Modifier.liquidLayerBackdrop(rootUiState.liquidBackdrop)
                                    } else {
                                        Modifier
                                    },
                                ),
                            onBack = ::popRootRoute,
                            transitionSpec = if (suppressRootTransition) {
                                { instantRootContentTransform() }
                            } else {
                                defaultTransitionSpec()
                            },
                            popTransitionSpec = if (suppressRootTransition) {
                                { instantRootContentTransform() }
                            } else {
                                defaultPopTransitionSpec()
                            },
                            transitionEffects = rootTransitionEffects,
                        )
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            bottomChrome(currentRootRoute == RootRoute.Main, true)
                        }
                    }
                }

                C17StableBaseSceneLayer(
                    transformActive = activeAnimation != null || handFollowMotion != null,
                    workspaceScaleProgress = launchWorkspaceScaleProgress,
                    workspaceAlphaProgress = launchWorkspaceAlphaProgress,
                    workspaceBlurProgress = launchWorkspaceBlurProgress,
                ) {
                    baseScene()
                }

                outgoingFeatureAnimations.toList().forEach { outgoingAnimation ->
                    key(outgoingAnimation) {
                        C17ClosingFeatureWindowLayer(
                            animation = outgoingAnimation,
                            viewportBounds = rootViewportBounds,
                            workspaceScale = workspaceVisualScale,
                            onFinished = onFinished@{ finished ->
                                if (outgoingFeatureAnimations.none { it === finished }) {
                                    return@onFinished
                                }
                                prepareFeatureIconHandoff(finished.session)
                                finished.predictiveBackMotion?.progressJob?.cancel()
                                outgoingFeatureAnimations.remove(finished)
                            },
                        ) {
                            movableFeatureEntries
                                .getValue(outgoingAnimation.session.mode)
                                .invoke()
                        }
                    }
                }

                if (activeSession != null) {
                    C17FeatureWindowLayer(
                        session = activeSession,
                        direction = activeAnimation?.direction
                            ?: FeatureLaunchDirection.Opening,
                        motion = activeAnimation?.motion,
                        predictiveBackMotion = activeAnimation?.predictiveBackMotion
                            ?: handFollowMotion,
                        returnOrigin = activeAnimation?.returnOrigin?.toReturnOrigin(
                            workspaceScale = workspaceVisualScale,
                            viewportBounds = rootViewportBounds,
                        ),
                        viewportBounds = rootViewportBounds,
                    ) {
                        movableFeatureEntries
                            .getValue(activeSession.mode)
                            .invoke()
                    }
                }

                val keepLauncherHitTargets =
                    activeAnimation?.direction == FeatureLaunchDirection.Closing ||
                        featureQueueHeldTokens.isNotEmpty()
                if (keepLauncherHitTargets) {
                    FeatureLaunchQueueHitLayer(
                        origins = featureLauncherOrigins,
                        viewportBounds = rootViewportBounds,
                        onPressStart = { mode ->
                            // Monotonic ownership prevents a completed old leash
                            // from mistaking a newer press for its own token.
                            nextFeatureQueuePressToken += 1L
                            val pressToken = nextFeatureQueuePressToken
                            featureQueuePressTokens[mode] = pressToken
                            featureQueueHeldTokens[mode] = pressToken
                            featureTransitionScope.launch {
                                if (
                                    featureQueuePressTokens[mode] != pressToken ||
                                    featureQueueHeldTokens[mode] != pressToken
                                ) {
                                    return@launch
                                }
                                featureQueuePressScales.getValue(mode).animateTo(
                                    targetValue = 0.85f,
                                    animationSpec = tween(
                                        durationMillis = 200,
                                        easing = FastOutSlowInEasing,
                                    ),
                                )
                            }
                            pressToken
                        },
                        onPressEnd = { mode, pressToken ->
                            if (featureQueueHeldTokens[mode] == pressToken) {
                                featureQueueHeldTokens.remove(mode)
                            }
                            // An accepted launch owns this scale until the close
                            // leash reaches the icon. Releasing it here made old
                            // icons randomly resize underneath parallel windows.
                            releaseIconPress(mode, pressToken)
                        },
                        onOpen = { mode, origin, pressToken ->
                            openFeatureSubPageFromMain(
                                mode,
                                origin?.toLiveVisualOrigin(
                                    pressScale = featureQueuePressScales.getValue(mode).value,
                                    workspaceScale = workspaceVisualScale,
                                    viewportBounds = rootViewportBounds,
                                ),
                                pressToken = pressToken,
                            )
                        },
                    )
                }

                if (
                    activeAnimation?.direction == FeatureLaunchDirection.Opening ||
                    handFollowMotion != null
                ) {
                    // Launcher input is blocked for the whole OPEN / MULTI_OPEN
                    // state in C17. This full-window consumer also prevents an
                    // uncovered part of the transforming page from falling
                    // through to the still-mounted Main scene underneath.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = { tryAwaitRelease() },
                                )
                            },
                    )
                }

                AnimatedVisibility(
                    visible = rootUiState.showFpsMonitor,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    FpsMonitorOverlay(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 44.dp),
                    )
                }
            }
            val featureSessionRoute =
                (featureLaunchSession ?: parkedFeatureLaunchSession)?.mode?.toRootRoute()
            BackHandler(
                enabled = featureLaunchAnimation != null,
            ) {
                popRootRoute()
            }
            PredictiveBackHandler(
                enabled = featurePageNewStyleEnabled &&
                    featureLaunchAnimation == null &&
                    featureSessionRoute != null &&
                    (
                        navigator.current() == featureSessionRoute ||
                            featurePredictiveBackMotion != null
                        ),
            ) { progressEvents ->
                var gestureMotion: FeaturePredictiveBackMotion? = null
                try {
                    progressEvents.collect { event ->
                        val motion = gestureMotion ?: startFeaturePredictiveBack()
                            ?.also { gestureMotion = it }
                            ?: return@collect
                        updateFeaturePredictiveBack(motion, event)
                    }
                    val motion = gestureMotion
                    if (motion == null) {
                        // Three-button and hardware back have no progress
                        // stream; retain the normal full C17 close path.
                        popRootRoute()
                    } else {
                        commitFeaturePredictiveBack(motion)
                    }
                } catch (cancellation: CancellationException) {
                    gestureMotion?.let(::cancelFeaturePredictiveBack)
                    throw cancellation
                }
            }
        }
            }
        }
    }
    }
}
