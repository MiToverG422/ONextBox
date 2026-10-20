package com.mi.onextbox.lsp

import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect
import java.util.IdentityHashMap

internal object PinSwipeInputHooker {
    private const val TAG = "ONextBox-PinSwipe"
    private const val DIAL = "com.coui.appcompat.passworddial.COUIPasswordDial"
    private const val KEY = "com.coui.appcompat.passworddial.key.PasswordDialKeyView"
    private val hosts = setOf("com.oplus.keyguard.security.view.OplusKeyguardPinView",
        "com.oplus.keyguard.security.view.OplusKeyguardFixedPinView")
    private class Gesture(val dial: ViewGroup, val keys: List<View>, val x: Float, val y: Float,
        val slop: Int, first: Int) {
        val state = PinSwipeGesture(first)
        var cancelled = false
    }
    private val gestures = IdentityHashMap<View, Gesture>()
    private var dispatchingCancel = false
    private fun enabled() = LspConfig.isKeyguardFeatureEnabledXposed(LspConfig.KeyguardFeature.SwipePinInput)

    fun hook(loader: ClassLoader) {
        runCatching {
            val keyClass = ModernReflect.findClass(KEY, loader)
            ModernHookRegistry.installCompat(TAG, keyClass.getDeclaredMethod("dispatchTouchEvent", MotionEvent::class.java),
                object : ModernMethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (dispatchingCancel) return
                        val key = param.thisObject as? View ?: return
                        val event = param.args[0] as? MotionEvent ?: return
                        if (handle(key, event)) param.result = true
                    }
                })
            val dialClass = ModernReflect.findClass(DIAL, loader)
            ModernHookRegistry.installCompat("$TAG:detach", dialClass.getDeclaredMethod("onDetachedFromWindow"),
                object : ModernMethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        gestures.entries.removeAll { it.value.dial === param.thisObject }
                    }
                })
            HotReloadLifecycle.register(TAG,
                capture = { null },
                detach = { gestures.forEach { (key, _) -> key.parent?.requestDisallowInterceptTouchEvent(false) }; gestures.clear() },
                restore = { },
            )
        }.onFailure { HookLog.w(TAG, "PIN swipe unavailable; native input retained", it) }
    }

    private fun dialFor(key: View): ViewGroup? {
        var parent = key.parent
        var dial: ViewGroup? = null
        while (parent is View) {
            if (parent.javaClass.name == DIAL) dial = parent as? ViewGroup
            if (parent.javaClass.name in hosts) return dial
            parent = parent.parent
        }
        return null
    }

    private fun keysIn(root: ViewGroup): List<View> = buildList {
        fun visit(view: View) {
            if (view.javaClass.name == KEY) add(view)
            else if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
        }
        visit(root)
    }

    private fun available(view: View): Boolean {
        if (!view.isAttachedToWindow || !view.isShown || !view.isClickable) return false
        var current: View? = view
        while (current != null) {
            if (!current.isEnabled || current.alpha <= 0f) return false
            current = current.parent as? View
        }
        return true
    }
    private fun cancelNative(key: View, event: MotionEvent) {
        val cancel = MotionEvent.obtain(event)
        cancel.action = MotionEvent.ACTION_CANCEL
        dispatchingCancel = true
        try { key.dispatchTouchEvent(cancel) } finally { dispatchingCancel = false; cancel.recycle() }
    }

    private fun handle(key: View, event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            gestures.remove(key)
            if (!enabled() || !available(key) || event.pointerCount != 1 ||
                key.context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true) return false
            val dial = dialFor(key) ?: return false
            val keys = keysIn(dial)
            if (keys.size != 10 || keys.any { !available(it) }) return false
            gestures[key] = Gesture(dial, keys, event.rawX, event.rawY,
                ViewConfiguration.get(key.context).scaledTouchSlop, keys.indexOf(key))
            key.parent?.requestDisallowInterceptTouchEvent(true)
            return false
        }
        val gesture = gestures[key] ?: return false
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            gestures.remove(key)
            key.parent?.requestDisallowInterceptTouchEvent(false)
            return gesture.state.sliding || gesture.cancelled
        }
        if (gesture.cancelled) return true
        if (!enabled() || event.pointerCount != 1 || !gesture.dial.isShown || !available(key)) {
            cancelNative(key, event)
            gesture.cancelled = true
            return true
        }
        if (action != MotionEvent.ACTION_MOVE) return gesture.state.sliding
        val bounds = Rect()
        fun sample(x: Float, y: Float) {
            val hit = gesture.keys.indexOfFirst { candidate ->
                available(candidate) && candidate.getGlobalVisibleRect(bounds) && bounds.contains(x.toInt(), y.toInt())
            }
            val dx = x - gesture.x
            val dy = y - gesture.y
            val wasSliding = gesture.state.sliding
            val clicks = gesture.state.move(hit, dx * dx + dy * dy > gesture.slop * gesture.slop)
            if (!wasSliding && gesture.state.sliding) {
                cancelNative(key, event)
                key.parent?.requestDisallowInterceptTouchEvent(true)
            }
            for (index in clicks) {
                val target = gesture.keys[index]
                if (!enabled() || !gesture.dial.isShown || !available(target)) break
                target.performClick()
            }
        }
        for (i in 0 until event.historySize) {
            sample(event.getHistoricalX(i) + event.rawX - event.x,
                event.getHistoricalY(i) + event.rawY - event.y)
        }
        sample(event.rawX, event.rawY)
        return gesture.state.sliding
    }
}
