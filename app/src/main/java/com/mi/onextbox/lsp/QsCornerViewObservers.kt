package com.mi.onextbox.lsp

import android.view.View
import android.view.ViewTreeObserver
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Observe only known QS views, including frames whose child RenderNodes are cached. */
internal class QsCornerViewObservers(
    key: String,
    private val onLayout: ((View) -> Boolean)? = null,
    private val content: (View) -> List<Any?>? = { emptyList() },
    private val configurationToken: () -> Int? = QsTileCornerSettings::configurationTokenXposed,
    private val update: (View) -> Boolean,
) {
    private val guards = WeakHashMap<View, Guard>()
    init {
        HotReloadLifecycle.register(key,
            detach = { synchronized(guards) { guards.values.toList() }.forEach { it.dispose() } },
            restore = { synchronized(guards) { guards.values.toList() }.forEach { it.attach() } })
        HotReloadLifecycle.registerReplay("$key:views",
            capture = { synchronized(guards) { guards.keys.toTypedArray() } },
            replay = { saved -> (saved as? Array<*>)?.filterIsInstance<View>()?.forEach(::watch) })
    }
    fun watch(view: View) {
        val guard = synchronized(guards) { guards.getOrPut(view) { Guard(view) } }
        guard.attach()
    }
    fun invalidate(view: View) {
        watch(view)
        synchronized(guards) { guards[view] }?.markDirty()
    }
    private inner class Guard(view: View) : View.OnAttachStateChangeListener, ViewTreeObserver.OnPreDrawListener, View.OnLayoutChangeListener {
        private val target = WeakReference(view)
        private var tree: WeakReference<ViewTreeObserver>? = null
        private var listening = false
        private val gate = QsCornerRefreshGate()
        private val schedule = QsCornerProbeSchedule()
        fun markDirty() {
            schedule.invalidate()
            gate.invalidate()
            retryAt = 0L
            retries = 0
            target.get()?.invalidate()
        }
        private var layoutPending = true
        private var retryAt = 0L
        private var retries = 0
        private val retry = Runnable {
            schedule.invalidate()
            target.get()?.takeIf { it.isAttachedToWindow && it.isShown }?.invalidate()
        }
        private fun defer(view: View, now: Long) {
            retryAt = now + 250L
            // Request another frame even when QS becomes idle after its first layout.
            // Bound autonomous retries for unsupported/unavailable plugin components.
            if (retries < 8) {
                retries++
                view.removeCallbacks(retry)
                view.postDelayed(retry, 250L)
            }
        }
        fun attach() {
            val view = target.get() ?: return
            if (!listening) {
                listening = true
                gate.invalidate()
                schedule.invalidate()
                layoutPending = true
                retryAt = 0L
                retries = 0
                view.addOnAttachStateChangeListener(this)
                view.addOnLayoutChangeListener(this)
                if (view.isAttachedToWindow) arm(view)
                view.invalidate()
            }
        }
        private fun arm(view: View) {
            val next = view.viewTreeObserver
            if (tree?.get() === next && next.isAlive) return
            disarm()
            if (next.isAlive) {
                next.addOnPreDrawListener(this)
                tree = WeakReference(next)
            }
        }
        private fun disarm() {
            tree?.get()?.takeIf { it.isAlive }?.let {
                it.removeOnPreDrawListener(this)
            }
            tree = null
        }
        fun dispose() {
            disarm()
            target.get()?.removeCallbacks(retry)
            target.get()?.removeOnAttachStateChangeListener(this)
            target.get()?.removeOnLayoutChangeListener(this)
            listening = false
        }
        override fun onViewAttachedToWindow(view: View) { gate.invalidate(); schedule.invalidate(); layoutPending = true; retryAt = 0L; retries = 0; arm(view) }
        override fun onViewDetachedFromWindow(view: View) { view.removeCallbacks(retry); disarm() }
        override fun onLayoutChange(view: View, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) {
            if (r - l != or - ol || b - t != ob - ot) { layoutPending = true; retryAt = 0L; retries = 0; gate.invalidate(); schedule.invalidate() }
        }
        override fun onPreDraw(): Boolean = HotReloadLifecycle.withHook(fallback = { true }) {
            if (!HotReloadLifecycle.isRetiring) target.get()?.let { view ->
                val now = android.os.SystemClock.uptimeMillis()
                if (now < retryAt) return@let
                val dp = configurationToken()
                if (!schedule.shouldProbe(now, view.isShown, dp, view.width, view.height,
                        System.identityHashCode(view.background), System.identityHashCode(view.parent),
                        view.resources.displayMetrics.density, view.layoutDirection)) return@let
                val enabled = dp != null
                // Probe the actual child/material, not merely the usually empty outer background.
                val snapshot = runCatching { if (enabled) content(view) else emptyList() }.getOrNull()
                if (snapshot == null) {
                    defer(view, now)
                    return@let
                }
                val state = QsCornerRefreshState(dp, view.width, view.height,
                    view.resources.displayMetrics.density, System.identityHashCode(view.background),
                    System.identityHashCode(view.parent), view.isShown, view.layoutDirection, snapshot)
                if (gate.changed(state)) {
                    val success = runCatching {
                        if (layoutPending && onLayout != null) onLayout.invoke(view) else update(view)
                    }.getOrDefault(false)
                    if (success) {
                        // Our own provider/clip replacement changes the probe; cache only its post-state.
                        val after = runCatching { if (enabled) content(view) else emptyList() }.getOrNull()
                        if (after != null) {
                            gate.applied(state.copy(content = after))
                            layoutPending = false
                            retries = 0
                            view.removeCallbacks(retry)
                        } else defer(view, now)
                    } else defer(view, now)
                }
            }
            true
        }
    }
}
