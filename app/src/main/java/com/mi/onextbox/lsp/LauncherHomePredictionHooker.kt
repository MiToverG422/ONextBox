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
    private data class Frame(val owner: Any, val progress: Float, val edge: Int, val touchY: Float)

    fun hook(loader: ClassLoader) {
        if (BackGestureSettings.xposed().predictive != 1 && HotReloadLifecycle.restoredState(KEY) != true) return
        runCatching { install(loader) }
            .onFailure { HookLog.w(TAG, "Launcher predictive preview unavailable; native animation retained", it) }
    }

    internal data class Playback(val type: Class<*>, val inlined: Boolean, val resolvedUpdate: Method?,
        val animator: Class<*>, val motion: Class<*>, val begin: Method?, val callback: Class<*>)

    internal fun playback(loader: ClassLoader): Playback {
        val legacy = runCatching { loader.loadClass("com.android.quickstep.LauncherBackAnimationController") }.getOrNull()
        val inlined = legacy == null
        val type = legacy ?: LauncherDexResolver.type(loader, "home-controller") { dex ->
            dex.findMethod {
                matcher { usingStrings("onBackProgressed touchX to progress: ")
                    paramTypes("android.window.BackMotionEvent"); returnType = "void" }
            }.single().className
        }
        val resolvedUpdate = if (inlined) LauncherDexResolver.method(loader, "home-progress") { dex ->
            dex.findMethod {
                matcher { usingStrings("updateBackProgress, illegal appWindowToHomeMap")
                    paramTypes("float"); returnType = "void" }
            }.single()
        } else null
        val animator = resolvedUpdate?.declaringClass ?: loader.loadClass("com.android.quickstep.i4")
        val motion = loader.loadClass("android.window.BackMotionEvent")
        val begin = if (inlined) animator.declaredMethods.single {
            it.returnType == Void.TYPE && it.parameterCount == 2 && it.parameterTypes[0] == motion
        } else null
        val callback = begin?.parameterTypes?.get(1) ?: loader.loadClass("com.android.quickstep.o3")
        return Playback(type, inlined, resolvedUpdate, animator, motion, begin, callback)
    }

    private fun install(loader: ClassLoader) {
        val (type, inlined, resolvedUpdate, animator, motion, begin, callback) = playback(loader)
        val event = if (inlined) motion else loader.loadClass("android.window.BackEvent")
        fun field(owner: Class<*>, name: String) = owner.getDeclaredField(name).apply { isAccessible = true }
        fun controllerField(old: String, current: String) = field(type, if (inlined) current else old)
        val callbackOwner = callback.declaredFields.single {
            !java.lang.reflect.Modifier.isStatic(it.modifiers) && (it.type == type || it.type == Any::class.java)
        }.apply { isAccessible = true }
        val listenerField = if (inlined) animator.declaredFields.single { it.type == callback }
            .apply { isAccessible = true } else null
        val motionField = if (inlined) animator.declaredFields.single { it.type == motion }
            .apply { isAccessible = true } else null
        val bounds = controllerField("c", "r")
        val initialTouch = controllerField("z", "F")
        val current = controllerField("s", "y")
        val crop = controllerField("o", "u")
        val session = controllerField("q", "w")
        val sessionProgress = field(session.type, "d")
        val initialRadius = controllerField("y", "E")
        val currentRadius = controllerField("d0", "j0")
        val launcherReference = controllerField("w", "C")
        val endRadius = type.getDeclaredMethod(if (inlined) "k" else "v").apply { isAccessible = true }
        val start = begin ?: animator.getDeclaredMethod("b", motion, callback)
        val update = if (inlined) requireNotNull(resolvedUpdate)
            else callback.getDeclaredMethod("a", event)
        val transform = type.declaredMethods.single { it.returnType == Void.TYPE &&
            it.parameterTypes.contentEquals(arrayOf(RectF::class.java, java.lang.Float.TYPE, Rect::class.java,
                java.lang.Float.TYPE, java.lang.Float.TYPE, java.lang.Boolean.TYPE)) }
        val finish = if (inlined) animator.getDeclaredMethod("c") else type.getDeclaredMethod("s")
        val edge = event.getDeclaredMethod("getSwipeEdge")
        val startEdge = motion.getDeclaredMethod("getSwipeEdge")
        val progress = event.getDeclaredMethod("getProgress")
        val touchY = event.getDeclaredMethod("getTouchY")
        require((callbackOwner.type == type || callbackOwner.type == Any::class.java) && bounds.type == Rect::class.java &&
            initialTouch.type == PointF::class.java && current.type == RectF::class.java && crop.type == Rect::class.java)
        require(listOf(sessionProgress, initialRadius, currentRadius).all { it.type == Float::class.javaPrimitiveType })
        require(WeakReference::class.java.isAssignableFrom(launcherReference.type))
        val gestures = WeakHashMap<Any, Boolean>()
        val frame = ThreadLocal<Frame>()
        var logged = false
        fun owner(listener: Any?): Any? = listener?.takeIf(callback::isInstance)
            ?.let(callbackOwner::get)?.takeIf(type::isInstance)

        fun hook(method: Method, action: (XposedInterface.Chain) -> Any?) {
            ModernHookRegistry.installFast("$KEY:${method.toGenericString()}", method, XposedInterface.Hooker(action))
            runCatching { ModernHookRuntime.requireModule().deoptimize(method) }
        }
        hook(finish) { chain ->
            val owner = if (inlined) owner(listenerField?.get(chain.thisObject)) else chain.thisObject
            try { chain.proceed() } finally { synchronized(gestures) { gestures.remove(owner) } }
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
                active.progress, active.edge,
                active.touchY - initial.y, margin) ?: return@hook chain.proceed()
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
                if (!logged && active.progress > 0f) {
                    logged = true
                    HookLog.i(TAG, "AOSP launcher predictive frame applied")
                }
            }
        }
        hook(update) { chain ->
            val listener = if (inlined) listenerField?.get(chain.thisObject) else chain.thisObject
            val owner = owner(listener) ?: return@hook chain.proceed()
            if (synchronized(gestures) { gestures[owner] } != true) return@hook chain.proceed()
            val previous = frame.get()
            val sample = (if (inlined) motionField?.get(chain.thisObject) else chain.args[0]) ?: return@hook chain.proceed()
            val fraction = if (inlined) (chain.args[0] as Float) / 100f else progress.invoke(sample) as Float
            frame.set(Frame(owner, fraction, edge.invoke(sample) as Int, touchY.invoke(sample) as Float))
            try { chain.proceed() }
            finally { if (previous == null) frame.remove() else frame.set(previous) }
        }
        hook(start) { chain ->
            val listener = chain.args.getOrNull(1) ?: return@hook chain.proceed()
            val owner = owner(listener) ?: return@hook chain.proceed()
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
