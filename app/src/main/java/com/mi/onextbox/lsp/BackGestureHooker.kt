package com.mi.onextbox.lsp

import android.content.Context
import android.graphics.Point
import android.os.SystemClock
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.WeakHashMap

internal object BackGestureHooker {
    private const val TAG = "ONextBox-BackGesture"
    private class Session(val settings: BackGestureSettings.State, val manager: Any) {
        var accepted = false
        var side = 0
    }
    private data class Frame(val detector: Any, val event: MotionEvent, val session: Session)
    private data class Feedback(val view: View, val scheme: Int, val aosp: Boolean)
    private val frames = ThreadLocal<Frame>()
    private val feedback = ThreadLocal<Feedback>()
    private val emitting = ThreadLocal<Boolean>()
    private var pointState: Method? = null
    private var pointSide: Method? = null

    fun hook(loader: ClassLoader) {
        val stateKey = "$TAG:installed"
        if (!BackGestureSettings.xposed().active && HotReloadLifecycle.restoredState(stateKey) != true) return
        HotReloadLifecycle.register(stateKey, capture = { true })
        fun adapter(name: String, block: () -> Unit) = runCatching(block)
            .onFailure { HookLog.w(TAG, "$name unavailable; native behavior retained", it) }
        adapter("Gesture routing") { gestures(loader) }
        adapter("Haptic schemes") { haptics(loader) }
        adapter("Predictive animation") { BackGesturePrediction.hook(loader) }
    }

    private fun install(method: Method, hook: (XposedInterface.Chain) -> Any?) {
        ModernHookRegistry.installFast("$TAG:${method.toGenericString()}", method, XposedInterface.Hooker(hook))
        runCatching { ModernHookRuntime.requireModule().deoptimize(method) }
    }

    private fun field(type: Class<*>, name: String): Field = type.getDeclaredField(name).apply { isAccessible = true }

    private fun gestures(loader: ClassLoader) {
        val detector = loader.loadClass("com.oplus.systemui.navigationbar.gesture.sidegesture.SideGestureDetector")
        val manager = loader.loadClass("com.oplus.systemui.navigationbar.gesture.sidegesture.view.SideGestureViewManager")
        val config = loader.loadClass("com.oplus.systemui.navigationbar.gesture.sidegesture.SideGestureConfiguration")
        val point = loader.loadClass("com.android.systemui.navigationbar.GesturePointContainerEx")
        val owner = field(detector, "mSideGestureViewManager")
        val configuration = field(detector, "mSideGestureConfiguration")
        val size = field(detector, "mDisplaySize")
        val allowed = field(detector, "mAllowGesture")
        val input = detector.getDeclaredMethod("onMotionEventImpl", MotionEvent::class.java, Map::class.java)
        val finished = manager.getDeclaredMethod("onGestureFinished", point)
        pointState = point.getDeclaredMethod("getState")
        pointSide = point.getDeclaredMethod("getSide")
        val sessions = WeakHashMap<Any, Session>()
        val lock = Any()
        var lastInput = 0L
        for ((name, left) in listOf("getSideGestureAreaWidthLeft" to true, "getSideGestureAreaWidthRight" to false)) {
            val getter = config.getDeclaredMethod(name)
            require(getter.returnType == Int::class.javaPrimitiveType)
            install(getter) { chain ->
                val frame = frames.get()
                if (frame == null || !frame.session.settings.area || frame.event.actionMasked != MotionEvent.ACTION_DOWN ||
                    configuration.get(frame.detector) !== chain.thisObject) return@install chain.proceed()
                val bounds = size.get(frame.detector) as Point
                val state = frame.session.settings
                if (BackGestureRules.areaAllows(frame.event.y, bounds.y,
                    if (left) state.leftHeight else state.rightHeight,
                    if (left) state.leftPosition else state.rightPosition)) chain.proceed() else -1
            }
        }
        install(finished) { chain ->
            recordFinished(chain.thisObject, chain.args.firstOrNull())
            chain.proceed()
        }
        install(input) { chain ->
            val host = chain.thisObject ?: return@install chain.proceed()
            val event = chain.args.firstOrNull() as? MotionEvent ?: return@install chain.proceed()
            lastInput = SystemClock.uptimeMillis()
            if (event.actionMasked == MotionEvent.ACTION_DOWN) synchronized(lock) {
                sessions.remove(host)
                if (event.pointerCount == 1 && event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN) &&
                    event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER) {
                    owner.get(host)?.let { sessions[host] = Session(BackGestureSettings.xposed(), it) }
                }
            }
            val session = synchronized(lock) { sessions[host] } ?: return@install chain.proceed()
            val previous = frames.get()
            frames.set(Frame(host, event, session))
            var completed = false
            try {
                chain.proceed().also { completed = true }
            } finally {
                try {
                    if (completed && BackGestureRules.releaseHaptic(session.settings.release, event.actionMasked,
                        session.accepted, event.pointerCount)) {
                        val view = field(manager, if (session.side == 0) "navLeftView" else "navRightView").get(session.manager) as View
                        emit(view, session.settings.selectedHaptics, release = true)
                    }
                } catch (error: Exception) { HookLog.w(TAG, "Release feedback unavailable", error) }
                finally {
                    if (event.actionMasked in listOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL) || event.pointerCount != 1)
                        synchronized(lock) { sessions.remove(host) }
                    if (previous == null) frames.remove() else frames.set(previous)
                }
            }
        }
        HotReloadLifecycle.register("$TAG:gestures",
            canReload = { synchronized(lock) {
                sessions.keys.none { runCatching { allowed.getBoolean(it) }.getOrDefault(true) } &&
                    SystemClock.uptimeMillis() - lastInput >= 700
            } }, detach = { synchronized(lock) { sessions.clear() } })
        HookLog.i(TAG, "Native gesture area and release adapters installed")
    }

    fun recordFinished(manager: Any?, point: Any?) {
        val frame = frames.get() ?: return
        if (point == null || frame.session.manager !== manager || frame.event.actionMasked != MotionEvent.ACTION_UP) return
        runCatching {
            frame.session.accepted = pointState?.invoke(point) == 2
            frame.session.side = (pointSide?.invoke(point) as? Int ?: 0).coerceIn(0, 1)
        }.onFailure { frame.session.accepted = false }
    }

    private fun withFeedback(view: View, nativeAosp: Boolean, action: () -> Any?): Any? {
        val previous = feedback.get()
        val state = frames.get()?.session?.settings ?: BackGestureSettings.xposed()
        val scheme = BackGestureRules.haptics(state.haptics, nativeAosp)
        feedback.set(Feedback(view, scheme, nativeAosp))
        return try { action() } finally { if (previous == null) feedback.remove() else feedback.set(previous) }
    }

    private var nativeHelper: Class<*>? = null

    private fun haptics(loader: ClassLoader) {
        val helper = loader.loadClass("com.oplus.systemui.navigationbar.gesture.VibrationHelper")
        nativeHelper = helper
        val side = loader.loadClass("com.oplus.systemui.navigationbar.gesture.sidegesture.view.SideGestureNavView")
        val point = loader.loadClass("com.android.systemui.navigationbar.GesturePointContainerEx")
        install(side.getDeclaredMethod("onUpdateGestureView", point)) { chain ->
            withFeedback(chain.thisObject as View, false) { chain.proceed() }
        }
        val panel = loader.loadClass("com.android.systemui.navigationbar.gestural.BackPanelController")
        val panelView = panel.declaredMethods.single { it.name.startsWith("getBackPanelView\$") && it.parameterCount == 0 }
            .apply { isAccessible = true }
        val state = field(panel, "currentState").type
        install(panel.getDeclaredMethod("updateArrowState", state, Boolean::class.javaPrimitiveType)) { chain ->
            withFeedback(panelView.invoke(chain.thisObject) as View, true) { chain.proceed() }
        }
        val perform = View::class.java.getDeclaredMethod("performHapticFeedback", Int::class.javaPrimitiveType)
        install(perform) { chain ->
            val current = feedback.get()
            val effect = chain.args[0] as Int
            if (emitting.get() == true || current == null || current.view !== chain.thisObject ||
                !current.aosp || current.scheme != 1 || effect !in 23..24) return@install chain.proceed()
            if (effect == 24 || emit(current.view, 1, false)) true else chain.proceed()
        }
        for (method in listOf(helper.getDeclaredMethod("doVibrateCustomized", Context::class.java,
            Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType), helper.getDeclaredMethod("performKeyboardTouchFeedback"))) {
            install(method) { chain ->
                val current = feedback.get()
                if (emitting.get() == true || current == null || current.aosp || current.scheme != 0) chain.proceed()
                else if (emit(current.view, 0, false)) null else chain.proceed()
            }
        }
        HookLog.i(TAG, "Native AOSP and ColorOS haptic adapters installed")
    }

    private fun emit(view: View, scheme: Int, release: Boolean): Boolean {
        if (!view.isAttachedToWindow || !view.isHapticFeedbackEnabled ||
            Settings.System.getInt(view.context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0) return false
        val previous = emitting.get()
        emitting.set(true)
        return try {
            if (scheme == 0) view.performHapticFeedback(if (release) HapticFeedbackConstants.GESTURE_END else 23)
            else nativeHelper?.let { type ->
                val instance = type.getDeclaredMethod("getInstance", Context::class.java).invoke(null, view.context)
                type.getDeclaredMethod("onPerformHapticFeedback").invoke(instance)
                true
            } ?: false
        } catch (error: Exception) {
            HookLog.w(TAG, "Native haptic feedback unavailable", error)
            false
        } finally { if (previous == null) emitting.remove() else emitting.set(previous) }
    }

}
