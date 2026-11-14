package com.mi.onextbox.lsp

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Camera
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.view.WindowInsets
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import kotlin.math.roundToLong

internal class FaceAuthCapsuleView(context: Context) : View(context) {
    private companion object { var rendererReported = false }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val colorEvaluator = android.animation.ArgbEvaluator()
    private val geometryInterpolator = PathInterpolator(0.3f, 0f, 0.1f, 1f)
    private val contentInterpolator = PathInterpolator(0.33f, 0f, 0.67f, 1f)
    private val curves = FaceAuthCapsuleMotion.Curves(
        geometryInterpolator::getInterpolation, contentInterpolator::getInterpolation,
    )
    private val path = Path()
    private val shackleCamera = Camera()
    private val shackleMatrix = Matrix()
    private val box = RectF()
    private val lockBody = RectF(-9f, 0f, 9f, 15f)
    private val capsuleClip = Path()
    private var requested = FaceAuthCapsuleState.Phase.Hidden
    private var opacity = 0f
    private var expansion = 0f
    private var retraction = 0f
    private var unlock = 0f
    private var contentOpacity = 1f
    private var contentScale = 1f
    private var shapeStretch = 1f
    private var pulse = 0f
    private var motion: ValueAnimator? = null
    private var scan: ValueAnimator? = null
    private val density get() = resources.displayMetrics.density
    private var pluginResources = loadPluginResources()
    private fun loadPluginResources() = runCatching {
        context.createPackageContext("com.oplus.systemui.plugins", 0).resources
    }.getOrNull()
    private val nativeIconInset get() = dimension("capsule_padding_start_end", 5f) + dimension("capsule_icon_size", 20f) / 2f
    private var compactLayout: FaceAuthCapsuleNativeLayout? = null
    private var hasNativeLayout = false
    private var placement: FaceAuthCapsuleLayout? = null
    private var reservedBounds: FaceAuthCapsuleBounds? = null
    var nativeLayout: (() -> FaceAuthCapsuleNativeLayout?)? = null
    var protectedContentTop: (() -> Float?)? = null
    var reserveIcons: ((FaceAuthCapsuleBounds) -> Boolean)? = null
    var onRenderFailure: ((Throwable) -> Unit)? = null
    var onTransitionFinished: ((FaceAuthCapsuleState.Phase) -> Unit)? = null
    private val staticSuccessFinished = Runnable {
        if (requested == FaceAuthCapsuleState.Phase.Success) onTransitionFinished?.invoke(requested)
    }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = INVISIBLE
    }

    fun show(next: FaceAuthCapsuleState.Phase) {
        if (requested == next) {
            // The native plugin may finish laying out after the idle lock's first frame.
            if (next != FaceAuthCapsuleState.Phase.Hidden) invalidate()
            return
        }
        if (requested == FaceAuthCapsuleState.Phase.Hidden && next != FaceAuthCapsuleState.Phase.Hidden) {
            resetPosition()
        }
        requested = next
        removeCallbacks(staticSuccessFinished)
        motion?.cancel(); scan?.cancel(); scan = null
        visibility = VISIBLE
        val animated = ValueAnimator.areAnimatorsEnabled()
        when (next) {
            FaceAuthCapsuleState.Phase.Success -> {
                // Lock-screen content can settle while the idle lock is visible.
                placement = null
                hasNativeLayout = false
                val initialOpacity = opacity
                motion = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = FaceAuthCapsuleMotion.SUCCESS_DURATION
                    interpolator = LinearInterpolator()
                    addUpdateListener {
                        val elapsed = (it.animatedFraction * FaceAuthCapsuleMotion.SUCCESS_DURATION).roundToLong()
                        val frame = FaceAuthCapsuleMotion.success(elapsed, curves, initialOpacity)
                        opacity = frame.opacity; expansion = frame.expansion; retraction = frame.retraction
                        unlock = frame.unlock; contentOpacity = frame.contentOpacity
                        contentScale = frame.contentScale; shapeStretch = frame.shapeStretch
                        invalidate()
                    }
                    addListener(completionListener(next))
                    if (animated) start() else {
                        opacity = 1f; expansion = 1f; retraction = 0f; unlock = 1f
                        contentOpacity = 1f; contentScale = 1f; shapeStretch = 1f
                        postDelayed(staticSuccessFinished, 600L)
                    }
                }
            }
            FaceAuthCapsuleState.Phase.Hidden -> {
                if (opacity <= 0f || !animated) {
                    opacity = 0f; visibility = INVISIBLE
                    onTransitionFinished?.invoke(next)
                    return
                }
                val initialOpacity = opacity
                val initialRetraction = retraction
                val initialContent = contentOpacity
                motion = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = FaceAuthCapsuleMotion.HIDE_DURATION
                    interpolator = geometryInterpolator
                    addUpdateListener {
                        val progress = it.animatedValue as Float
                        opacity = initialOpacity * (1f - progress)
                        retraction = initialRetraction + (1f - initialRetraction) * progress
                        contentOpacity = initialContent * (1f - progress)
                        if (opacity <= 0f) visibility = INVISIBLE
                        invalidate()
                    }
                    addListener(completionListener(next))
                    start()
                }
            }
            else -> {
                expansion = 0f; retraction = 0f; unlock = 0f
                contentOpacity = 1f; contentScale = 1f; shapeStretch = 1f
                motion = ValueAnimator.ofFloat(opacity, 1f).apply {
                    duration = 160L
                    interpolator = contentInterpolator
                    addUpdateListener { opacity = it.animatedValue as Float; invalidate() }
                    if (animated) start() else opacity = 1f
                }
                if (animated && next == FaceAuthCapsuleState.Phase.Scanning) {
                    scan = ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = 950L
                        interpolator = contentInterpolator
                        repeatCount = ValueAnimator.INFINITE
                        repeatMode = ValueAnimator.REVERSE
                        addUpdateListener { pulse = it.animatedValue as Float; invalidate() }
                        start()
                    }
                }
            }
        }
        invalidate()
    }

    private fun completionListener(phase: FaceAuthCapsuleState.Phase) = object : AnimatorListenerAdapter() {
        private var cancelled = false
        override fun onAnimationCancel(animation: Animator) { cancelled = true }
        override fun onAnimationEnd(animation: Animator) {
            if (!cancelled && requested == phase) onTransitionFinished?.invoke(phase)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val save = canvas.save()
        try { drawCapsule(canvas) }
        catch (error: Throwable) { onRenderFailure?.invoke(error) }
        finally { canvas.restoreToCount(save) }
    }

    private fun resetPosition() {
        compactLayout = null; hasNativeLayout = false; placement = null; reservedBounds = null
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resetPosition()
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        resetPosition()
        invalidate()
        return super.onApplyWindowInsets(insets)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        pluginResources = loadPluginResources()
        resetPosition()
        invalidate()
    }

    private fun dimension(name: String, fallback: Float): Float {
        val res = pluginResources ?: return fallback * density
        val id = res.getIdentifier(name, "dimen", "com.oplus.systemui.plugins")
        return if (id != 0) res.getDimension(id) else fallback * density
    }

    private fun drawCapsule(canvas: Canvas) {
        if (opacity <= 0f) return
        if (!rendererReported && context.packageName == "com.android.systemui") {
            rendererReported = true
            HookLog.i("ONextBox-FaceCapsule", "Face capsule renderer: " +
                if (canvas.isHardwareAccelerated) "hardware" else "software")
        }
        val d = density
        val insets = rootWindowInsets
        val cutout = insets?.displayCutout
        // boundingRectTop may include the empty area above a punch hole. The path gives
        // the physical camera's vertical center, which is what the OEM aligns against.
        val cameraPath = RectF()
        cutout?.cutoutPath?.computeBounds(cameraPath, true)
        val camera = if (!cameraPath.isEmpty && cameraPath.top >= 0f &&
            cameraPath.bottom <= maxOf(cutout?.safeInsetTop ?: 0, (80 * d).toInt())) {
            FaceAuthCapsuleBounds(cameraPath.left, cameraPath.top, cameraPath.right, cameraPath.bottom)
        } else cutout?.boundingRectTop?.takeUnless { it.isEmpty }?.let {
            FaceAuthCapsuleBounds(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat())
        }
        val barId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusHeight = maxOf(if (barId != 0) resources.getDimension(barId) else 36f * d,
            (insets?.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars())?.top ?: 0).toFloat())
        val position = IntArray(2).also(::getLocationOnScreen)
        val transitioning = requested == FaceAuthCapsuleState.Phase.Success || requested == FaceAuthCapsuleState.Phase.Hidden
        val native = if (hasNativeLayout && transitioning) null else nativeLayout?.invoke()
            ?.offset(-position[0].toFloat(), -position[1].toFloat())
            ?.takeIf { it.top >= 0f && it.top + it.height <= height }
        val resolved = native ?: compactLayout ?: FaceAuthCapsulePosition.fallback(
            width.toFloat(), statusHeight,
            dimension(if (statusHeight > dimension("status_bar_height_slop", 32f))
                "capsule_layout_max_height" else "capsule_layout_max_height_small", 30f),
            dimension("capsule_view_min_width", 100f), dimension("capsule_corner_radius", 20f), camera,
            dimension("default_hole_width", 20f), dimension("capsule_hole_safe_height_gap", 4f),
        )
        if (native != null) hasNativeLayout = true
        if (compactLayout != resolved) {
            compactLayout = resolved; placement = null; reservedBounds = null
        }
        val compactHeight = resolved.height
        val top = resolved.top
        val cx = resolved.centerX ?: width / 2f
        val compactWidth = resolved.minWidth
        val compactRadius = minOf(resolved.radius, compactHeight / 2f)
        val hole = resolved.hole ?: run {
            val size = dimension("capsule_appear_init_width_height", 20f)
            val centerY = top + compactHeight / 2f
            FaceAuthCapsuleBounds(cx - size / 2, centerY - size / 2, cx + size / 2, centerY + size / 2)
        }
        val layout = placement ?: FaceAuthCapsuleLayout.resolve(
            top, compactHeight, d, protectedContentTop?.invoke()?.minus(position[1]), height.toFloat(),
        ).also { placement = it }
        if (reservedBounds == null) {
            val half = (compactWidth + 32f * d) * 1.025f / 2f
            val reserved = FaceAuthCapsuleBounds(cx - half, 0f, cx + half, maxOf(statusHeight, top + layout.expandedHeight))
            check(reserveIcons?.invoke(reserved.offset(position[0].toFloat(), position[1].toFloat())) != false) {
                "Native capsule icon avoidance is not ready"
            }
            reservedBounds = reserved
        }
        val w = (compactWidth + 32f * d * expansion) * shapeStretch
        val h = (compactHeight + (layout.expandedHeight - compactHeight) * expansion) *
            if (layout.vertical) shapeStretch else 1f
        val bounds = FaceAuthCapsuleBounds(cx - w / 2, top, cx + w / 2, top + h).toward(hole, retraction)
        box.set(bounds.left, bounds.top, bounds.right, bounds.bottom)
        val expandedRadius = if (layout.vertical) compactRadius + (30f * d - compactRadius) * expansion else compactRadius
        val radius = expandedRadius + (minOf(hole.width, hole.height) / 2f - expandedRadius) * retraction
        paint.style = Paint.Style.FILL
        paint.color = Color.BLACK
        paint.alpha = (255 * opacity).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(box, radius, radius, paint)
        capsuleClip.reset()
        capsuleClip.addRoundRect(box, radius, radius, Path.Direction.CW)
        canvas.clipPath(capsuleClip)

        val scanScale = ((compactHeight / d - 12f) / 30f).coerceIn(0.35f, 0.65f)
        val iconScale = scanScale + (layout.iconScale - scanScale) * expansion
        val edgeX = minOf(cx - w / 2f + nativeIconInset,
            hole.left - 10.1f * d * iconScale - 5f * d)
        val glyphX = if (layout.vertical) edgeX + (cx - edgeX) * expansion else edgeX
        val glyphY = top + compactHeight / 2f +
            (layout.faceCenterY - top - compactHeight / 2f) * expansion
        canvas.translate(glyphX + (hole.centerX - glyphX) * retraction,
            glyphY + (hole.centerY - glyphY) * retraction)
        canvas.scale(d * contentScale * iconScale, d * contentScale * iconScale)
        canvas.translate(0f, -2.75f)
        val color = if (requested == FaceAuthCapsuleState.Phase.Failed) 0xFFFF8B86.toInt()
            else colorEvaluator.evaluate(unlock, 0xFFE7ECE9.toInt(), 0xFF73F3B0.toInt()) as Int
        paint.color = color
        val breath = if (requested == FaceAuthCapsuleState.Phase.Scanning) 0.84f + 0.16f * pulse else 1f
        paint.alpha = (255 * opacity * contentOpacity * breath).toInt().coerceIn(0, 255)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.2f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        canvas.drawRoundRect(lockBody, 3f, 3f, paint)
        canvas.drawCircle(0f, 6f, 1f, paint)
        canvas.drawLine(0f, 7f, 0f, 10f, paint)
        val shackle = canvas.save()
        shackleCamera.save()
        shackleCamera.rotateY(180f * unlock)
        shackleCamera.getMatrix(shackleMatrix)
        shackleCamera.restore()
        shackleMatrix.preTranslate(-5.5f, 0f)
        shackleMatrix.postTranslate(5.5f, 0f)
        canvas.concat(shackleMatrix)
        path.reset()
        path.moveTo(-5.5f, 0f)
        path.lineTo(-5.5f, -5f)
        path.cubicTo(-5.5f, -11f, 5.5f, -11f, 5.5f, -5f)
        path.lineTo(5.5f, 0f)
        canvas.drawPath(path, paint)
        canvas.restoreToCount(shackle)
    }

    fun dispose() {
        onTransitionFinished = null
        removeCallbacks(staticSuccessFinished)
        motion?.cancel(); motion = null
        scan?.cancel(); scan = null
        requested = FaceAuthCapsuleState.Phase.Hidden
        opacity = 0f; expansion = 0f; retraction = 0f; unlock = 0f
        resetPosition()
        nativeLayout = null; protectedContentTop = null; reserveIcons = null; onRenderFailure = null
        visibility = INVISIBLE
    }

    override fun onDetachedFromWindow() { dispose(); super.onDetachedFromWindow() }
}
