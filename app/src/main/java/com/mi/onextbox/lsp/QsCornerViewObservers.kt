package com.mi.onextbox.lsp

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import com.mi.onextbox.diagnostics.SafeModePolicy
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Observe only known QS views, including frames whose child RenderNodes are cached. */
internal class QsCornerViewObservers(
    key: String,
    private val onLayout: ((View) -> Boolean)? = null,
    private val content: (View) -> List<Any?>? = { emptyList() },
    private val configurationToken: (View) -> Int? = { QsTileCornerSettings.configurationTokenXposed() },
    settingsKeys: Set<String> = QsTileCornerSettings.keys,
    private val update: (View) -> Boolean,
) {
    private val guards = WeakHashMap<View, Guard>()
    private val main = Handler(Looper.getMainLooper())
    private var preferences: SharedPreferences? = null
    private val refresh = Runnable {
        if (!HotReloadLifecycle.isRetiring) {
            synchronized(guards) { guards.values.toList() }.forEach {
                it.attach()
                it.markDirty()
            }
        }
    }
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key in settingsKeys || key == SafeModePolicy.MANUAL || key == SafeModePolicy.AUTOMATIC) {
            main.removeCallbacks(refresh)
            main.post(refresh)
        }
    }

    init {
        HotReloadLifecycle.register(key, detach = {
            preferences?.unregisterOnSharedPreferenceChangeListener(listener)
            preferences = null
            main.removeCallbacks(refresh)
            synchronized(guards) { guards.values.toList() }.forEach { it.dispose() }
        }, resumeAfterRejection = ::activate)
        HotReloadLifecycle.registerReplay("$key:views",
            capture = { synchronized(guards) { guards.keys.toTypedArray() } },
            replay = { saved -> (saved as? Array<*>)?.filterIsInstance<View>()?.forEach(::watch) })
        HotReloadLifecycle.afterCommit(::activate)
    }

    private fun activate() {
        if (preferences == null) preferences = ModernHookRuntime.remotePreferences("lsp_features")?.also {
            it.registerOnSharedPreferenceChangeListener(listener)
        }
        refresh.run()
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
        private fun resetProbe() {
            schedule.invalidate()
            gate.invalidate()
            retryAt = 0L
            retries = 0
        }
        fun markDirty() {
            if (!listening) return
            resetProbe()
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
            if (!listening && configurationToken(view) != null) {
                listening = true
                resetProbe()
                layoutPending = true
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
        override fun onViewAttachedToWindow(view: View) {
            resetProbe()
            layoutPending = true
            arm(view)
        }
        override fun onViewDetachedFromWindow(view: View) { view.removeCallbacks(retry); disarm() }
        override fun onLayoutChange(view: View, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) {
            if (r - l != or - ol || b - t != ob - ot) {
                resetProbe()
                layoutPending = true
            }
        }
        override fun onPreDraw(): Boolean = HotReloadLifecycle.withHook(fallback = { true }) {
            if (!HotReloadLifecycle.isRetiring) target.get()?.let { view ->
                val dp = configurationToken(view)
                val now = android.os.SystemClock.uptimeMillis()
                if (now < retryAt) return@let
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
                            if (!enabled) dispose()
                        } else defer(view, now)
                    } else defer(view, now)
                }
            }
            true
        }
    }
}
