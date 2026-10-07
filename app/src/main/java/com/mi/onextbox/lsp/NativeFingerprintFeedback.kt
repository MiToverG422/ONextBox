package com.mi.onextbox.lsp
import android.os.Build

import android.content.Context
import android.graphics.Rect
import android.graphics.PixelFormat
import android.graphics.drawable.AnimationDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import com.mi.onextbox.lsp.compat.ModernReflect as Reflect

/** Native fingerprint animation, isolated from authentication. */
internal class NativeFingerprintFeedback {
    private val main = Handler(Looper.getMainLooper())
    private var layer: ImageView? = null
    private var window: FrameLayout? = null
    private var windowManager: WindowManager? = null
    private var animation: AnimationDrawable? = null
    private var waterRenderer: Any? = null
    private var waterAnimHandler: Handler? = null
    private var startedAt = 0L
    private var committed = false
    private var lastDiagnostic: String? = null
    private val cleanup = Runnable { cancel() }

    fun release(host: FrameLayout, bounds: Rect, mech: Any): Boolean = safely {
        cancel()
        start(host, bounds, mech)
    }

    fun commitDelay(): Long = FaceTapFeedbackRules.commitDelay(SystemClock.uptimeMillis() - startedAt)

    fun allowFinish() { if (animation != null) committed = true }

    fun cancelUncommitted() {
        if (FaceTapFeedbackRules.cancelForHide(committed, hard = false)) cancel()
    }

    fun cancel() {
        main.removeCallbacks(cleanup)
        val old = animation
        animation = null
        committed = false
        // Only stop our own renderer, never the shared HAL-driven VFX controller
        if (old?.javaClass?.name == FaceTapFeedbackRules.HY_WATER) {
            // This COE renderer is ours, never borrowed from the HAL-driven drawable
            runCatching {
                Reflect.getObjectField(old, "mCoeSysEffect")?.let {
                    Reflect.callMethod(it, "stopAllAnim")
                    Reflect.callMethod(it, "seekAnimToEnd", "animator1")
                }
            }
        }
        val ownWater = waterRenderer
        val ownHandler = waterAnimHandler
        waterRenderer = null
        waterAnimHandler = null
        if (ownWater != null && ownHandler != null) {
            // Serialize with the native renderer's queued start, avoid recreating a released surface
            ownHandler.post {
                runCatching { Reflect.callMethod(ownWater, "stopVFX", "URA") }
                runCatching { Reflect.callMethod(ownWater, "releaseVfxSurface") }
            }
        }
        runCatching { old?.stop() }
        layer?.let {
            it.setImageDrawable(null)
            (it.parent as? FrameLayout)?.removeView(it)
        }
        layer = null
        window?.let { view -> runCatching { windowManager?.removeViewImmediate(view) } }
        window = null
        windowManager = null
        startedAt = 0L
    }

    private fun start(host: FrameLayout, bounds: Rect, mech: Any): Boolean {
        if (!host.isAttachedToWindow || !host.isShown || bounds.isEmpty) return false
        val native = Reflect.getObjectField(mech, "pressedAnimDrawable") as? AnimationDrawable ?: return false
        // Never share a live HAL-driven animation or take over its callback/window
        if (native.isRunning) return false
        val className = native.javaClass.name
        val options = Reflect.callMethod(native, "getOptions") ?: return false
        val frames = Reflect.callMethod(options, "getFrames") as Int
        val name = Reflect.callMethod(options, "getName") as? String
        if (!FaceTapFeedbackRules.supported(className, frames, name)) {
            diagnose("Selected fingerprint animation is none or unsupported, tap unlock retained")
            return false
        }
        if (className == FaceTapFeedbackRules.WATER &&
            Reflect.getObjectField(Reflect.getObjectField(native, "mKeyguardVFXController") ?: return false, "mVFXEnable") != true) return false
        val duration = Reflect.callMethod(options, "getDuration") as Int
        val speed = FaceTapFeedbackRules.speedForDuration(duration) ?: return false
        val scale = Reflect.callMethod(options, "getScaleRate") as Float
        if (!scale.isFinite() || scale <= 0f) return false
        val copiedOptions = Reflect.newInstance(options.javaClass, frames, speed, name,
            Reflect.getObjectField(options, "mIsReverse") as Boolean)
        Reflect.callMethod(copiedOptions, "setNecessaryScale", false)
        Reflect.callMethod(copiedOptions, "setScaleRate", scale)
        Reflect.callMethod(copiedOptions, "setOnlyOneFrame", Reflect.getObjectField(options, "mOnlyOneFrame") as Boolean)
        // Use the selected drawable's user/overlay context, not the module's resources
        val decorator = Reflect.getObjectField(native, "mDecorator") ?: return false
        val context = Reflect.getObjectField(decorator, "mContext") as? Context ?: return false
        val nativeIcon = Reflect.getObjectField(mech, "fpIcon") as? ImageView ?: return false
        if (nativeIcon.measuredWidth <= 0 || nativeIcon.measuredHeight <= 0 ||
            !nativeIcon.scaleX.isFinite() || !nativeIcon.scaleY.isFinite() ||
            nativeIcon.scaleX <= 0f || nativeIcon.scaleY <= 0f) return false
        // Water's subclass invokes the shared keyguard controller, use the same icon frames with
        // an independent instance of its OEM VFX engine so stock hide cannot cut off our tail
        val drawableClass = if (className == FaceTapFeedbackRules.WATER)
            Reflect.findClass(FaceTapFeedbackRules.BASE, mech.javaClass.classLoader) else native.javaClass
        val fresh = Reflect.newInstance(drawableClass, context, copiedOptions) as? AnimationDrawable ?: return false
        // Track before further reflection so any unsupported interface also cleans the decoder
        animation = fresh
        if (fresh.numberOfFrames <= 0) { cancel(); return false }
        if (className == FaceTapFeedbackRules.HY_WATER) {
            // C17 initializes the HY renderer in loadAnimDrawables, not in its constructor
            val nativeRenderer = requireNotNull(Reflect.getObjectField(native, "mCoeSysEffect"))
            val ownRenderer = Reflect.newInstance(nativeRenderer.javaClass, context)
            Reflect.setObjectField(fresh, "mCoeSysEffect", ownRenderer)
            Reflect.setObjectField(fresh, "mContext", context)
            Reflect.setObjectField(fresh, "mCenter", FloatArray(2))
            Reflect.callMethod(ownRenderer, "load", "WaterRippleAnimation.coz", false, false)
            Reflect.callMethod(ownRenderer, "setDensity", context.resources.displayMetrics.density)
        }
        if (className == FaceTapFeedbackRules.HY_WATER) {
            val center = Reflect.getObjectField(fresh, "mCenter") as FloatArray
            require(center.size == 2)
            center[0] = bounds.exactCenterX()
            center[1] = bounds.exactCenterY()
            val helper = Reflect.findClass("com.oplus.systemui.keyguard.helper.ScreenSizeHelper", mech.javaClass.classLoader)
            val companion = helper.getDeclaredField("Companion").get(null)
            val size = requireNotNull(Reflect.callMethod(companion, "getInstance", context))
            val width = Reflect.callMethod(size, "getBaseDisplayWidthSize") as Int
            val height = Reflect.callMethod(size, "getBaseDisplayHeightSize") as Int
            require(width > 0 && height > 0)
            center[0] /= width.toFloat()
            center[1] /= height.toFloat()
        }
        val view = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER
            scaleX = nativeIcon.scaleX
            scaleY = nativeIcon.scaleY
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setPadding(0, 0, 0, 0)
            setImageDrawable(fresh)
        }
        val params = FrameLayout.LayoutParams(nativeIcon.measuredWidth, nativeIcon.measuredHeight, Gravity.TOP or Gravity.LEFT).apply {
            leftMargin = bounds.centerX() - width / 2
            topMargin = bounds.centerY() - height / 2
        }
        layer = view
        val manager = context.getSystemService(WindowManager::class.java) ?: return false
        // Borrow only the native window type, never its HBM/brightness flags or HAL layer name
        val nativeWindow = nativeIcon.rootView.layoutParams as? WindowManager.LayoutParams ?: return false
        if (nativeWindow.type !in WindowManager.LayoutParams.FIRST_SYSTEM_WINDOW..WindowManager.LayoutParams.LAST_SYSTEM_WINDOW) return false
        val root = FrameLayout(context).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            addView(view, params)
        }
        val windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            nativeWindow.type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            setTitle("ONextBoxFaceTapFeedback")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setFitInsetsTypes(0)
        }
        // The visual-only System UI layer must not cause untrusted-overlay touch blocking on desktop
        val trustedFlag = Reflect.getStaticIntField(WindowManager.LayoutParams::class.java, "PRIVATE_FLAG_TRUSTED_OVERLAY")
        Reflect.setObjectField(windowParams, "privateFlags", trustedFlag)
        window = root
        windowManager = manager
        manager.addView(root, windowParams)
        if (className == FaceTapFeedbackRules.HY_WATER) Reflect.callMethod(fresh, "setTargetView", view)
        native.colorFilter?.let(fresh::setColorFilter)
        // A style change or real fingerprint press may arrive on the optical UI Looper
        if (Reflect.getObjectField(mech, "pressedAnimDrawable") !== native || native.isRunning) {
            cancel()
            return false
        }
        if (className == FaceTapFeedbackRules.WATER) {
            val thread = Reflect.findClass("com.oplusos.keyguard.utils.KeyguardThreadUtil", mech.javaClass.classLoader)
            waterAnimHandler = Reflect.callStaticMethod(thread, "getAnimHandler") as Handler
            val own = Reflect.newInstance(Reflect.findClass("com.oplus.keyguard.VFXController", mech.javaClass.classLoader), context)
            waterRenderer = own
        }
        val totalDuration = (0 until fresh.numberOfFrames).sumOf { fresh.getDuration(it).toLong() }
        val nativeRipple = className == FaceTapFeedbackRules.WATER || className == FaceTapFeedbackRules.HY_WATER
        // HY retries only once when no surface exists, starting before the first traversal loses it
        // Start both native frames and native renderers only when this owned surface is ready
        root.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                root.viewTreeObserver.removeOnPreDrawListener(this)
                if (window !== root || animation !== fresh) return true
                runCatching {
                    fresh.start()
                    if (className == FaceTapFeedbackRules.WATER) {
                        val own = requireNotNull(waterRenderer)
                        val viewRoot = Reflect.callMethod(root, "getViewRootImpl") ?: return@runCatching
                        val surface = Reflect.callMethod(viewRoot, "getSurfaceControl") ?: return@runCatching
                        // C17 URA update callbacks read this field rather than the startVFX argument
                        // It references only our window, never the wallpaper/official fingerprint surface
                        Reflect.setObjectField(own, "mVfxSurfaceControl", surface)
                        val density = context.resources.displayMetrics.density
                        Reflect.callMethod(own, "startVFX", "URA", 0, 0, surface, 321,
                            floatArrayOf(bounds.exactCenterX(), bounds.exactCenterY()),
                            (60f * density).toInt().toFloat(), (117f * density).toInt().toFloat(), true, true)
                    }
                    startedAt = SystemClock.uptimeMillis()
                    main.removeCallbacks(cleanup)
                    main.postDelayed(cleanup, FaceTapFeedbackRules.cleanupDelay(totalDuration, nativeRipple))
                }.onFailure {
                    runCatching { cancel() }
                    diagnose("OEM animation surface unavailable (${it.javaClass.simpleName}), tap unlock retained")
                }
                return true
            }
        })
        startedAt = SystemClock.uptimeMillis()
        // Bounded cleanup even if a surface is never produced, no overlay can remain indefinitely
        main.postDelayed(cleanup, 5000L)
        diagnose("Selected OEM fingerprint animation prepared: ${native.javaClass.simpleName}")
        return true
    }

    private fun safely(action: () -> Boolean): Boolean = runCatching(action).getOrElse {
        runCatching { cancel() }
        diagnose("OEM fingerprint animation unavailable (${it.javaClass.simpleName}), tap unlock retained")
        false
    }

    private fun diagnose(message: String) {
        if (message != lastDiagnostic) {
            lastDiagnostic = message
            HookLog.i("ONextBox-FaceTap", message)
        }
    }
}
