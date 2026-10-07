package com.mi.onextbox.lsp

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import com.mi.onextbox.lsp.LspConfig.SmallWindowFeature as Feature
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect as Reflect
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Small-window appearance and safe positioning, independent of task, audio and freeze policies. */
internal object SmallWindowHooker {
    private const val TAG = "ONextBox-SmallWindow"
    private val styles = WeakHashMap<FrameLayout, AnimatedStyle>()
    private val nativeDepths = WeakHashMap<View, Int>()
    private val observedLayouts = WeakHashMap<FrameLayout, Boolean>()
    private val pendingStyles = WeakHashMap<View, Boolean>()
    private val visualFailed = AtomicBoolean(false)

    private enum class Variant(val viewClass: String, val controllerClass: String) {
        Zoom(
            "com.oplus.zoom.ui.floathandle.FloatHandleView",
            "com.oplus.zoom.ui.floathandle.FloatHandleStatus",
        ),
        Flexible(
            "com.android.server.wm.floathandle.FloatHandleView",
            "com.android.server.wm.FloatHandleController",
        ),
    }

    fun hookSystemUi(loader: ClassLoader) = install(loader, Variant.Zoom)
    fun hookSystemServer(loader: ClassLoader) {
        install(loader, Variant.Flexible)
        SmallWindowPolicyHooker.install(loader)
    }

    private fun enabled(feature: Feature): Boolean = LspConfig.isSmallWindowFeatureEnabledXposed(feature)

    private fun install(loader: ClassLoader, variant: Variant) {
        if (!enabled(Feature.WhiteBar) && !enabled(Feature.SafeEdgeInset)) return
        val target = Reflect.findClassIfExists(variant.viewClass, loader) ?: return
        val controller = Reflect.findClassIfExists(variant.controllerClass, loader) ?: return
        runCatching { installSafeInset(controller, variant) }
            .onFailure { HookLog.w(TAG, "Safe-inset interface not matched (${variant.name}); stock behavior kept", it) }
        runCatching { installWhiteBar(target, loader, variant) }
            .onFailure { HookLog.w(TAG, "Handle interface not matched (${variant.name}); stock appearance kept", it) }
    }

    private fun installSafeInset(target: Class<*>, variant: Variant) {
        // Validate all dependencies before installing a callback in a critical system process.
        target.getDeclaredField("mContext")
        Reflect.findDeclaredMethodExact(target, "getScreenHeight")
        Reflect.findDeclaredMethodExact(target, "getContainerHeight")
        val method = Reflect.findDeclaredMethodExact(target, "getMovingEdgeLimit")
        val failed = AtomicBoolean(false)
        ModernHookRegistry.installCompat("small-window:${variant.name}:inset", method, object : ModernMethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (param.throwable != null || failed.get() || !enabled(Feature.SafeEdgeInset)) return
                val receiver = param.thisObject ?: return
                val original = param.result as? Int ?: return
                runCatching {
                    val context = Reflect.getObjectField(receiver, "mContext") as? Context ?: return@runCatching
                    if (!isPhone(context)) return@runCatching
                    val density = context.resources.displayMetrics.density
                    if (!density.isFinite() || density <= 0f) return@runCatching
                    param.result = SmallWindowRules.safeInset(
                        original = original,
                        requested = (48f * density).toInt(),
                        screenHeight = Reflect.callMethod(receiver, "getScreenHeight") as Int,
                        handleHeight = Reflect.callMethod(receiver, "getContainerHeight") as Int,
                    )
                }.onFailure {
                    if (failed.compareAndSet(false, true)) HookLog.w(TAG, "Safe-inset callback disabled; stock result retained", it)
                    param.result = original
                }
            }
        })
        HookLog.i(TAG, "${variant.name} safe-inset hook installed")
    }

    private fun installWhiteBar(target: Class<*>, loader: ClassLoader, variant: Variant) {
        val integer = Int::class.javaPrimitiveType!!
        val boolean = Boolean::class.javaPrimitiveType!!
        val fields = if (variant == Variant.Flexible) listOf(
            "mFloatHandleController", "mFloatIconListAnimationLayout", "mChangingToNextMode",
            "mTouching", "mInLongPressDragging", "mInGlobalDragMoving", "mIsEventUpAnimating",
            "mInMultiIconIncreaseAnimating", "mRemoving", "mCurrentMode", "mCurrentSide",
        ) else listOf(
            "mStatus", "mIconView", "mIconSwapView", "mArrowView", "mFloatHandleView",
            "mTouchHandler", "mPendingMode", "mCurrentMode", "mCurrentSide", "mRemoving",
            "mIconTransactionAnimator", "mPanAndReboundAnimator",
        )
        (fields + "mFloatHandleViewContainer").forEach(target::getDeclaredField)
        if (variant == Variant.Flexible) {
            Reflect.findDeclaredMethodExact(target, "isEditMode")
            Reflect.findDeclaredMethodExact(target, "getFloatHandleInfoList")
        } else {
            Reflect.findDeclaredMethodExact(target.getDeclaredField("mTouchHandler").type, "isTouching")
        }
        Reflect.findDeclaredMethodExact(Reflect.findClass(variant.controllerClass, loader), "getMaxDistanceToScreenInHalfHidden")
        val info = Reflect.findClass(
            if (variant == Variant.Flexible) "com.android.server.wm.floathandle.FloatHandleInfo"
            else "com.oplus.zoom.ui.floathandle.FloatHandleInfo", loader,
        )
        val methods = if (variant == Variant.Flexible) listOf(
            Reflect.findDeclaredMethodExact(target, "initView", Context::class.java, integer),
            Reflect.findDeclaredMethodExact(target, "initState", info, integer, integer),
            Reflect.findDeclaredMethodExact(target, "setCurrentMode", integer),
            Reflect.findDeclaredMethodExact(target, "setChangingToNextMode", integer),
            Reflect.findDeclaredMethodExact(target, "updateViewLayoutParams", boolean, boolean),
            Reflect.findDeclaredMethodExact(target, "exeRefreshOption"),
            Reflect.findDeclaredMethodExact(target, "onLongPressUpAnimEnd"),
            Reflect.findDeclaredMethodExact(target, "onTouch", View::class.java, MotionEvent::class.java),
            // Restore before native list mutations so icon animation setup never sees our alpha 0.
            Reflect.findDeclaredMethodExact(target, "updateForMultiIconIncrease", info),
            Reflect.findDeclaredMethodExact(target, "updateForMultiIconReduce", boolean, boolean, info),
            Reflect.findDeclaredMethodExact(target, "updateViewForIncrease", info),
            Reflect.findDeclaredMethodExact(target, "resetToFullMode", boolean),
            Reflect.findDeclaredMethodExact(target, "setLaunchFlag", integer),
        ) else listOf(
            Reflect.findDeclaredMethodExact(target, "initState", info, integer, integer),
            Reflect.findDeclaredMethodExact(target, "updateArrowStyle", boolean),
            Reflect.findDeclaredMethodExact(target, "updateIconStyle", String::class.java, integer),
            Reflect.findDeclaredMethodExact(target, "refreshFloatHandleView", String::class.java, integer),
            Reflect.findDeclaredMethodExact(target, "relayout"),
            Reflect.findDeclaredMethodExact(target, "onTouch", View::class.java, MotionEvent::class.java),
        )
        val detach = Reflect.findDeclaredMethodExact(target, "onDetachedFromWindow")
        // Resolve animator/transition signatures before registering any of this hook group.
        val transition = if (variant == Variant.Flexible) Reflect.findDeclaredMethodExact(
            target, "startSwitchModeAnimation", integer, integer,
            Reflect.findClass("com.android.server.wm.floathandle.SwitchModeAnimCallback", loader),
        ) else Reflect.findDeclaredMethodExact(target, "createAnimatorForPanAndRebound", integer, integer)
        val iconTransition = if (variant == Variant.Zoom) Reflect.findDeclaredMethodExact(target, "startAnimationForIconTransaction") else null
        methods.forEach { method ->
            ModernHookRegistry.installCompat("small-window:${variant.name}:${method.name}", method, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    guarded { beginNative(param.thisObject) }
                }
                override fun afterHookedMethod(param: MethodHookParam) {
                    guarded {
                        endNative(param.thisObject, variant, failed = param.throwable != null)
                        if (param.throwable == null) scheduleStyle(param.thisObject, variant)
                    }
                }
            })
        }
        ModernHookRegistry.installCompat("small-window:${variant.name}:detach", detach, object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) { guarded { restore(param.thisObject) } }
        })
        ModernHookRegistry.installCompat("small-window:${variant.name}:transition", transition, object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) { guarded { beginNative(param.thisObject) } }
            override fun afterHookedMethod(param: MethodHookParam) {
                guarded {
                    // Begin the cross-fade with the native slide rather than after it has stopped.
                    endNative(param.thisObject, variant, failed = param.throwable != null,
                        forceNative = param.args[1] != 4)
                }
                // The old Zoom path writes mode fields directly in Animator listeners. Reapply
                // after native listeners finish, without cancelling/replacing any native animation.
                if (variant != Variant.Zoom || param.throwable != null) return
                val animator = param.result as? Animator ?: return
                observeAnimationEnd(animator, param.thisObject, variant)
            }
        })
        iconTransition?.let { method ->
            ModernHookRegistry.installCompat("small-window:${variant.name}:icon-transition", method, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) { guarded { beginNative(param.thisObject) } }
                override fun afterHookedMethod(param: MethodHookParam) {
                    guarded {
                        val host = param.thisObject
                        val animator = host?.let { Reflect.getObjectField(it, "mIconTransactionAnimator") as? Animator }
                        endNative(host, variant, failed = param.throwable != null,
                            forceNative = animator?.isRunning == true)
                        if (param.throwable != null) return@guarded
                        if (animator != null && animator.isRunning) observeAnimationEnd(animator, host, variant)
                        else scheduleStyle(host, variant)
                    }
                }
            })
        }
        HookLog.i(TAG, "${variant.name} white-bar hook installed")
    }

    private fun observeAnimationEnd(animator: Animator, receiver: Any?, variant: Variant) {
        val host = WeakReference(receiver as? View ?: return)
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                host.get()?.post { guarded { applyStyle(host.get(), variant) } }
                animation.removeListener(this)
            }
        })
    }

    private fun scheduleStyle(receiver: Any?, variant: Variant) {
        val view = receiver as? View ?: return
        if (synchronized(pendingStyles) { pendingStyles.put(view, true) != null }) return
        val weak = WeakReference(view)
        view.post {
            val host = weak.get() ?: return@post
            synchronized(pendingStyles) { pendingStyles.remove(host) }
            guarded { applyStyle(host, variant) }
        }
    }

    private fun isPhone(context: Context): Boolean = context.resources.configuration.smallestScreenWidthDp in 1..599

    private fun beginNative(receiver: Any?) {
        val view = receiver as? View ?: return
        val first = synchronized(nativeDepths) {
            val depth = nativeDepths[view] ?: 0
            nativeDepths[view] = depth + 1
            depth == 0
        }
        if (first) currentStyle(receiver)?.let(::suspendStyle)
    }

    private fun endNative(receiver: Any?, variant: Variant, failed: Boolean, forceNative: Boolean = false) {
        val view = receiver as? View ?: return
        val outermost = synchronized(nativeDepths) {
            val depth = (nativeDepths[view] ?: 1) - 1
            if (depth <= 0) nativeDepths.remove(view) else nativeDepths[view] = depth
            depth <= 0
        }
        // Nested native methods must never see our partially faded alpha as their own starting value.
        if (!outermost) return
        if (failed) restore(receiver) else applyStyle(receiver, variant, forceNative)
    }

    private fun currentStyle(receiver: Any): AnimatedStyle? {
        val container = Reflect.getObjectField(receiver, "mFloatHandleViewContainer") as? FrameLayout ?: return null
        return synchronized(styles) { styles[container] }
    }

    private fun applyStyle(receiver: Any?, variant: Variant, forceNative: Boolean = false) {
        if (receiver == null) return
        if (visualFailed.get()) { restore(receiver); return }
        if (receiver is View && synchronized(nativeDepths) { (nativeDepths[receiver] ?: 0) > 0 }) return
        val container = Reflect.getObjectField(receiver, "mFloatHandleViewContainer") as? FrameLayout ?: return
        if (!isPhone(container.context)) { restore(receiver); return }
        val observeLayout = synchronized(observedLayouts) { observedLayouts.put(container, true) == null }
        if (observeLayout) {
            val host = WeakReference(receiver)
            container.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                guarded { applyStyle(host.get(), variant) }
            }
        }
        if (!container.isAttachedToWindow) { restore(receiver); return }
        val mode = Reflect.getObjectField(receiver, "mCurrentMode") as Int
        val nextMode = Reflect.getObjectField(receiver, if (variant == Variant.Flexible) "mChangingToNextMode" else "mPendingMode") as Int
        val controller = Reflect.getObjectField(receiver, if (variant == Variant.Flexible) "mFloatHandleController" else "mStatus") ?: return
        val interacting: Boolean
        val windowCount: Int
        val children: List<View>
        val backgrounds: List<View>
        val leftSide: Boolean
        if (variant == Variant.Flexible) {
            interacting = listOf("mTouching", "mInLongPressDragging", "mInGlobalDragMoving", "mIsEventUpAnimating", "mInMultiIconIncreaseAnimating", "mRemoving")
                .any { Reflect.getObjectField(receiver, it) == true } || Reflect.callMethod(receiver, "isEditMode") == true
            windowCount = (Reflect.callMethod(receiver, "getFloatHandleInfoList") as? List<*>)?.size ?: 0
            children = listOf(Reflect.getObjectField(receiver, "mFloatIconListAnimationLayout") as View)
            backgrounds = listOf(container)
            leftSide = Reflect.getObjectField(receiver, "mCurrentSide") == 0
        } else {
            val touch = Reflect.getObjectField(receiver, "mTouchHandler") ?: return
            interacting = Reflect.callMethod(touch, "isTouching") == true || Reflect.getObjectField(receiver, "mRemoving") == true ||
                listOf("mIconTransactionAnimator", "mPanAndReboundAnimator").any {
                    (Reflect.getObjectField(receiver, it) as? Animator)?.isRunning == true
                }
            windowCount = 1 // Old Zoom owns one handle per side, never a stacked icon list.
            children = listOf("mIconView", "mIconSwapView", "mArrowView").map { Reflect.getObjectField(receiver, it) as View }
            backgrounds = listOf(container, Reflect.getObjectField(receiver, "mFloatHandleView") as View).distinct()
            leftSide = Reflect.getObjectField(receiver, "mCurrentSide") == 1
        }
        // The C17 stack shares one handle. The mode-2 picker still displays its original icons;
        // a collapse toward mode 4 can cross-fade at the same time as the native spring translation.
        val showBar = !forceNative && SmallWindowRules.showWhiteBar(
            enabled(Feature.WhiteBar), mode, nextMode, interacting, windowCount)
        val previous = synchronized(styles) { styles[container] }
        if (previous == null && !showBar) return
        val hidden = Reflect.callMethod(controller, "getMaxDistanceToScreenInHalfHidden") as Int
        val density = container.resources.displayMetrics.density
        if (SmallWindowRules.barBounds(container.width.toFloat(), container.height.toFloat(), hidden.toFloat(), density, leftSide) == null) {
            restore(receiver)
            return
        }
        // Temporarily expose real native properties for sampling, but retain the in-flight progress.
        // This happens in one UI callback, with no frame drawn between suspend and reapplication.
        previous?.let(::suspendStyle)
        val native = NativeStyle(children.map { WeakReference(it) to it.alpha },
            backgrounds.map { WeakReference(it) to it.background },
            container.foreground, container.foregroundGravity, container.elevation)
        val style = previous ?: AnimatedStyle(WeakReference(container), native)
        style.native = native
        style.density = density
        style.hidden = hidden.toFloat()
        style.leftSide = leftSide
        style.suspended = false
        synchronized(styles) { styles[container] = style }
        try {
            render(style)
            retarget(style, if (showBar) 1f else 0f)
        } catch (error: Throwable) {
            restore(receiver)
            throw error
        }
    }

    private data class NativeStyle(
        val alphas: List<Pair<WeakReference<View>, Float>>,
        val backgrounds: List<Pair<WeakReference<View>, Drawable?>>,
        val foreground: Drawable?,
        val gravity: Int,
        val elevation: Float,
    )

    private class AnimatedStyle(val container: WeakReference<FrameLayout>, var native: NativeStyle) {
        var progress = 0f
        var target = 0f
        var animator: ValueAnimator? = null
        var suspended = false
        var density = 1f
        var hidden = 0f
        var leftSide = false
    }

    private fun suspendStyle(style: AnimatedStyle) {
        if (style.suspended) return
        style.suspended = true
        restoreNative(style)
    }

    private fun restoreNative(style: AnimatedStyle) {
        val container = style.container.get() ?: return
        val native = style.native
        native.alphas.forEach { (view, alpha) -> view.get()?.alpha = alpha }
        native.backgrounds.forEach { (view, background) -> view.get()?.background = background }
        container.foreground = native.foreground
        container.foregroundGravity = native.gravity
        container.elevation = native.elevation
        container.invalidate()
    }

    private fun render(style: AnimatedStyle) {
        if (style.suspended) return
        val container = style.container.get() ?: return
        val nativeWeight = 1f - SmallWindowRules.barProgress(style.progress)
        style.native.alphas.forEach { (view, alpha) -> view.get()?.alpha = alpha * nativeWeight }
        style.native.backgrounds.forEach { (weak, background) ->
            val view = weak.get() ?: return@forEach
            if (background == null) { view.background = null; return@forEach }
            val wrapper = view.background as? FadingBackground
            if (wrapper?.style !== style || wrapper.original !== background) {
                view.background = FadingBackground(background, style)
            } else wrapper.invalidateSelf()
        }
        container.elevation = style.native.elevation * nativeWeight
        container.foregroundGravity = Gravity.FILL
        val foreground = container.foreground as? WhiteBarDrawable
        if (foreground?.style !== style) container.foreground = WhiteBarDrawable(style)
        else foreground.invalidateSelf()
        container.invalidate()
    }

    private fun retarget(style: AnimatedStyle, target: Float) {
        if (style.target == target && style.animator != null) return
        if (style.animator == null && style.progress == target) {
            if (target == 0f) finishStyle(style)
            return
        }
        // Clear identity before cancel: the old animator's end callback cannot tear down a reversal.
        val old = style.animator
        style.animator = null
        old?.cancel()
        style.target = target
        val duration = SmallWindowRules.barTransitionDuration(style.progress, target)
        val container = style.container.get()
        if (duration == 0L || container?.isAttachedToWindow != true || Looper.myLooper() == null || !ValueAnimator.areAnimatorsEnabled()) {
            style.progress = target
            render(style)
            if (target == 0f) finishStyle(style)
            return
        }
        val weak = WeakReference(style)
        val animator = ValueAnimator.ofFloat(style.progress, target).apply {
            this.duration = duration
            interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
            addUpdateListener { running ->
                val current = weak.get() ?: return@addUpdateListener
                if (current.animator !== running) return@addUpdateListener
                guarded {
                    if (current.container.get()?.isAttachedToWindow != true) finishStyle(current)
                    else {
                        current.progress = SmallWindowRules.barProgress(running.animatedValue as Float)
                        render(current)
                    }
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    val current = weak.get() ?: return
                    if (current.animator !== animation) return
                    current.animator = null
                    guarded {
                        current.progress = target
                        render(current)
                        if (target == 0f) finishStyle(current)
                    }
                }
            })
        }
        style.animator = animator
        animator.start()
    }

    private fun finishStyle(style: AnimatedStyle) {
        val old = style.animator
        style.animator = null
        old?.cancel()
        style.container.get()?.let { container ->
            synchronized(styles) { if (styles[container] === style) styles.remove(container) }
        }
        // Remove the weak-map value before native drawables acquire View callbacks again.
        style.suspended = true
        restoreNative(style)
    }

    private fun restore(receiver: Any?) {
        if (receiver == null) return
        val container = Reflect.getObjectField(receiver, "mFloatHandleViewContainer") as? FrameLayout ?: return
        val style = synchronized(styles) { styles.remove(container) } ?: return
        finishStyle(style)
    }

    private inline fun guarded(block: () -> Unit) {
        runCatching(block).onFailure {
            if (visualFailed.compareAndSet(false, true)) {
                val active = synchronized(styles) { styles.values.toList().also { styles.clear() } }
                active.forEach { style -> runCatching { finishStyle(style) } }
                HookLog.w(TAG, "White-bar callback disabled; native views retained", it)
            }
        }
    }

    /** Draw originals through a layer; never mutate a shared drawable's alpha or its callback. */
    private class FadingBackground(val original: Drawable, val style: AnimatedStyle) : Drawable() {
        private var opacity = 255
        override fun draw(canvas: Canvas) {
            val alpha = (opacity * (1f - SmallWindowRules.barProgress(style.progress))).toInt()
            drawFaded(canvas, original, alpha)
        }
        override fun onBoundsChange(bounds: Rect) { original.bounds = bounds }
        override fun getIntrinsicWidth() = original.intrinsicWidth
        override fun getIntrinsicHeight() = original.intrinsicHeight
        override fun getMinimumWidth() = original.minimumWidth
        override fun getMinimumHeight() = original.minimumHeight
        override fun getPadding(padding: Rect) = original.getPadding(padding)
        override fun isStateful() = original.isStateful
        override fun onStateChange(state: IntArray) = original.setState(state)
        override fun getOutline(outline: Outline) {
            original.getOutline(outline)
            outline.alpha *= 1f - SmallWindowRules.barProgress(style.progress)
        }
        override fun setAlpha(alpha: Int) { opacity = alpha.coerceIn(0, 255); invalidateSelf() }
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit
        @Deprecated("Required by Drawable")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private fun drawFaded(canvas: Canvas, drawable: Drawable?, alpha: Int) {
        if (drawable == null || alpha <= 0) return
        val b = drawable.bounds
        if (b.isEmpty) return
        val layer = canvas.saveLayerAlpha(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), alpha.coerceAtMost(255))
        try { drawable.draw(canvas) } finally { canvas.restoreToCount(layer) }
    }

    private class WhiteBarDrawable(val style: AnimatedStyle) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private var opacity = 255
        override fun draw(canvas: Canvas) {
            val progress = SmallWindowRules.barProgress(style.progress)
            drawFaded(canvas, style.native.foreground, (opacity * (1f - progress)).toInt())
            if (progress <= 0f) return
            val rect = SmallWindowRules.barBounds(bounds.width().toFloat(), bounds.height().toFloat(), style.hidden, style.density, style.leftSide) ?: return
            // A gentle length change complements the fade without altering hit areas or layout.
            val halfHeight = (rect.bottom - rect.top) * (0.7f + 0.3f * progress) / 2f
            val center = (rect.top + rect.bottom) / 2f
            paint.alpha = (opacity * progress).toInt()
            canvas.drawRoundRect(bounds.left + rect.left, bounds.top + center - halfHeight,
                bounds.left + rect.right, bounds.top + center + halfHeight, 2f * style.density, 2f * style.density, paint)
        }
        override fun setAlpha(alpha: Int) { opacity = alpha.coerceIn(0, 255); invalidateSelf() }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
        @Deprecated("Required by Drawable")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
