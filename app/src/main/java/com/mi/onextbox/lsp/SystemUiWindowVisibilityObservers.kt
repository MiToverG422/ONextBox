package com.mi.onextbox.lsp

import android.view.View
import android.view.ViewTreeObserver
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHotReloadSession
import java.lang.ref.WeakReference
import java.util.WeakHashMap

internal class SystemUiWindowVisibilityObservers(
    private val observeLayout: Boolean = false,
    private val changed: (View) -> Unit,
) {
    private class Observation(
        tree: ViewTreeObserver,
        private val windowListener: ViewTreeObserver.OnWindowVisibilityChangeListener,
        private val layoutListener: ViewTreeObserver.OnGlobalLayoutListener?,
    ) {
        private val observer = WeakReference(tree)

        fun remove() {
            val tree = observer.get()?.takeIf { it.isAlive } ?: return
            tree.removeOnWindowVisibilityChangeListener(windowListener)
            layoutListener?.let(tree::removeOnGlobalLayoutListener)
        }
    }

    private val observations = WeakHashMap<View, Observation>()

    fun watch(view: View) {
        if (!view.isAttachedToWindow || observations.containsKey(view)) return
        val tree = view.viewTreeObserver.takeIf { it.isAlive } ?: return
        val target = WeakReference(view)
        fun notifyVisibility() {
            HotReloadLifecycle.withHook(fallback = {}) { target.get()?.let(changed); Unit }
        }
        val windowListener = ViewTreeObserver.OnWindowVisibilityChangeListener { notifyVisibility() }
        val layoutListener = if (observeLayout) ViewTreeObserver.OnGlobalLayoutListener(::notifyVisibility) else null
        tree.addOnWindowVisibilityChangeListener(windowListener)
        layoutListener?.let(tree::addOnGlobalLayoutListener)
        observations[view] = Observation(tree, windowListener, layoutListener)
    }

    fun unwatch(view: View) {
        observations.remove(view)?.remove()
    }

    fun clear() {
        observations.values.forEach(Observation::remove)
        observations.clear()
    }

    companion object {
        fun retireLegacyHooks() {
            if (!HotReloadLifecycle.isStaging) return
            val session = ModernHotReloadSession.current ?: return
            session.previousTargets.distinct().filter { it.declaringClass == View::class.java }.forEach { target ->
                when (target.name) {
                    "onVisibilityChanged" -> session.retireLegacyFirstSlot(target)
                    "onWindowVisibilityChanged" -> session.retireLegacySlots(target, 2)
                }
            }
        }
    }
}
