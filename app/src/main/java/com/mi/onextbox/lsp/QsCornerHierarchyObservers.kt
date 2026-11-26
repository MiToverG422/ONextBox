package com.mi.onextbox.lsp

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Discover lazy/plugin children on layout events, never by walking every frame. */
internal class QsCornerHierarchyObservers(key: String, private val found: (View) -> Unit) {
    private val roots = WeakHashMap<ViewGroup, Guard>()
    init {
        HotReloadLifecycle.register(key,
            detach = { synchronized(roots) { roots.values.toList() }.forEach { it.dispose() } })
        registerRoots(key)
    }
    // Older generations tracked device cards with a second observer on the same QS root.
    fun adoptPreviousRoots(key: String) {
        HotReloadLifecycle.register(key)
        registerRoots(key)
    }
    private fun registerRoots(key: String) {
        HotReloadLifecycle.registerReplay("$key:roots",
            capture = { synchronized(roots) { roots.keys.toTypedArray() } },
            replay = { saved -> (saved as? Array<*>)?.filterIsInstance<ViewGroup>()?.forEach(::watch) })
    }
    fun watch(root: ViewGroup) {
        val guard = synchronized(roots) { roots.getOrPut(root) { Guard(root) } }
        if (!HotReloadLifecycle.isStaging && QsTileCornerSettings.enabledXposed()) guard.attach()
    }
    fun refresh() {
        val enabled = QsTileCornerSettings.enabledXposed()
        synchronized(roots) { roots.values.toList() }.forEach { guard ->
            if (enabled) guard.attach() else guard.dispose()
        }
    }
    private inner class Guard(root: ViewGroup) : View.OnAttachStateChangeListener, ViewTreeObserver.OnGlobalLayoutListener {
        private val target = WeakReference(root)
        private var tree: WeakReference<ViewTreeObserver>? = null
        private var listening = false
        private val discovered = WeakHashMap<View, Boolean>()
        private fun scan(view: View) {
            // Layout of an unrelated QS child must not reapply every known card.
            if (discovered.put(view, true) == null) found(view)
            if (view is ViewGroup) for (index in 0 until view.childCount) scan(view.getChildAt(index))
        }
        fun attach() {
            val root = target.get() ?: return
            if (listening) return
            listening = true
            root.addOnAttachStateChangeListener(this)
            if (root.isAttachedToWindow) arm(root)
            onGlobalLayout()
        }
        private fun arm(root: ViewGroup) {
            disarm()
            root.viewTreeObserver.takeIf { it.isAlive }?.let {
                it.addOnGlobalLayoutListener(this)
                tree = WeakReference(it)
            }
        }
        private fun disarm() {
            tree?.get()?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(this)
            tree = null
        }
        fun dispose() {
            disarm()
            target.get()?.removeOnAttachStateChangeListener(this)
            listening = false
            discovered.clear()
        }
        override fun onViewAttachedToWindow(view: View) { (view as? ViewGroup)?.let(::arm); onGlobalLayout() }
        override fun onViewDetachedFromWindow(view: View) { disarm() }
        override fun onGlobalLayout() = HotReloadLifecycle.withHook(fallback = {}) {
            if (!HotReloadLifecycle.isRetiring && QsTileCornerSettings.enabledXposed()) target.get()?.let(::scan)
            Unit
        }
    }
}
