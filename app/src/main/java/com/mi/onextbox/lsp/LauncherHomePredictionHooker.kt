package com.mi.onextbox.lsp

import android.content.Context
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.WeakHashMap

internal object LauncherHomePredictionHooker {
    private const val TAG = "ONextBox-BackGesture"
    private const val KEY = "$TAG:launcherHome"
    private data class Frame(val owner: Any, val event: Any)

    fun hook(loader: ClassLoader) {
        if (BackGestureSettings.xposed().predictive != 1 && HotReloadLifecycle.restoredState(KEY) != true) return
        runCatching { install(loader) }
            .onFailure { HookLog.w(TAG, "Launcher predictive preview unavailable; native animation retained", it) }
    }

    private fun install(loader: ClassLoader) {
        val type = loader.loadClass("com.android.quickstep.LauncherBackAnimationController")
        val callback = loader.loadClass("com.android.quickstep.o3")
        val animator = loader.loadClass("com.android.quickstep.i4")
        val motion = loader.loadClass("android.window.BackMotionEvent")
        val event = loader.loadClass("android.window.BackEvent")
        fun field(owner: Class<*>, name: String) = owner.getDeclaredField(name).apply { isAccessible = true }
        val callbackOwner = field(callback, "a")
        val bounds = field(type, "c")
        val initialTouch = field(type, "z")
        val current = field(type, "s")
        val crop = field(type, "o")
        val session = field(type, "q")
        val sessionProgress = field(session.type, "d")
        val initialRadius = field(type, "y")
        val currentRadius = field(type, "d0")
        val launcherReference = field(type, "w")
        val endRadius = type.getDeclaredMethod("v").apply { isAccessible = true }
        val begin = animator.getDeclaredMethod("b", motion, callback)
        val update = callback.getDeclaredMethod("a", event)
        val transform = type.getDeclaredMethod("l", RectF::class.java, Float::class.javaPrimitiveType,
            Rect::class.java, Float::class.javaPrimitiveType, Float::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
        val finish = type.getDeclaredMethod("s")
        val edge = event.getDeclaredMethod("getSwipeEdge")
        val startEdge = motion.getDeclaredMethod("getSwipeEdge")
        val progress = event.getDeclaredMethod("getProgress")
        val touchY = event.getDeclaredMethod("getTouchY")
        require(callbackOwner.type == type && bounds.type == Rect::class.java &&
            initialTouch.type == PointF::class.java && current.type == RectF::class.java && crop.type == Rect::class.java)
        require(listOf(sessionProgress, initialRadius, currentRadius).all { it.type == Float::class.javaPrimitiveType })
        require(WeakReference::class.java.isAssignableFrom(launcherReference.type))
        val gestures = WeakHashMap<Any, Boolean>()
        val frame = ThreadLocal<Frame>()
        var logged = false

        fun hook(method: Method, action: (XposedInterface.Chain) -> Any?) {
            ModernHookRegistry.installFast("$KEY:${method.toGenericString()}", method, XposedInterface.Hooker(action))
            runCatching { ModernHookRuntime.requireModule().deoptimize(method) }
        }
        hook(finish) { chain ->
            try { chain.proceed() } finally { synchronized(gestures) { gestures.remove(chain.thisObject) } }
        }
        hook(transform) { chain ->
            val active = frame.get()
            if (active == null || active.owner !== chain.thisObject) return@hook chain.proceed()
            val owner = active.owner
            val screen = bounds.get(owner) as Rect
            val initial = initialTouch.get(owner) as PointF
            val context = (launcherReference.get(owner) as? WeakReference<*>)?.get() as? Context
                ?: return@hook chain.proceed()
            val resources = context.resources
            val id = resources.getIdentifier("swipe_back_window_scale_x_margin", "dimen", context.packageName)
            val margin = if (id != 0) resources.getDimension(id) else 8f * resources.displayMetrics.density
            val projected = BackGestureRules.homePreview(screen.width().toFloat(), screen.height().toFloat(),
                progress.invoke(active.event) as Float, edge.invoke(active.event) as Int,
                (touchY.invoke(active.event) as Float) - initial.y, margin) ?: return@hook chain.proceed()
            val rect = RectF(screen.left + projected.left, screen.top + projected.top,
                screen.left + projected.left + projected.width, screen.top + projected.top + projected.height)
            val radius = initialRadius.getFloat(owner).let { start ->
                start + ((endRadius.invoke(owner) as Float) - start) * projected.progress
            }
            (current.get(owner) as RectF).set(rect)
            (chain.args[0] as RectF).set(rect)
            sessionProgress.setFloat(session.get(owner), projected.progress)
            currentRadius.setFloat(owner, radius)
            // Native cancellation and icon handoff read these same rectangles and crop bounds.
            val args = chain.args.toTypedArray()
            args[1] = radius
            args[2] = crop.get(owner)
            args[3] = 1f
            args[4] = projected.progress
            chain.proceed(args).also {
                if (!logged) {
                    logged = true
                    HookLog.i(TAG, "AOSP launcher predictive frame applied")
                }
            }
        }
        hook(update) { chain ->
            val owner = callbackOwner.get(chain.thisObject) ?: return@hook chain.proceed()
            if (synchronized(gestures) { gestures[owner] } != true) return@hook chain.proceed()
            val previous = frame.get()
            frame.set(Frame(owner, chain.args[0] ?: return@hook chain.proceed()))
            try { chain.proceed() }
            finally { if (previous == null) frame.remove() else frame.set(previous) }
        }
        hook(begin) { chain ->
            val listener = chain.args.getOrNull(1) ?: return@hook chain.proceed()
            val owner = callbackOwner.get(listener) ?: return@hook chain.proceed()
            synchronized(gestures) {
                gestures[owner] = BackGestureSettings.xposed().predictive == 1 && startEdge.invoke(chain.args[0]) in listOf(0, 1)
            }
            chain.proceed()
        }
        HotReloadLifecycle.register(KEY, capture = { true },
            canReload = { synchronized(gestures) { gestures.values.none { it } } },
            detach = { synchronized(gestures) { gestures.clear() } })
        listOf(type, callback, animator).flatMap {
            it.declaredMethods.toList()
        }.forEach { runCatching { ModernHookRuntime.requireModule().deoptimize(it) } }
        HookLog.i(TAG, "Native launcher predictive preview adapter installed")
    }
}
