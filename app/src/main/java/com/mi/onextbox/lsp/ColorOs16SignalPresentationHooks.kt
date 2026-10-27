package com.mi.onextbox.lsp

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernMethodHook
import java.util.WeakHashMap
import java.lang.ref.WeakReference

/** C16 mobile binders render named Views; C17's Os17Binding/Compose contracts do not apply. */
internal object ColorOs16SignalPresentationHooks {
    fun hookRoaming(loader: ClassLoader) = install(loader, "roaming",
        setOf("mobile_roaming", "mobile_roaming_large", "mobile_roaming_space"),
        LspConfig::isSystemUiHideMobileRoamingIndicatorEnabledXposed)

    fun hookActivity(loader: ClassLoader) = install(loader, "activity",
        setOf("data_inout", "data_inout_alone", "mobile_in", "mobile_out"),
        LspConfig::isSystemUiHideNetworkActivityIndicatorEnabledXposed)

    private fun install(loader: ClassLoader, key: String, names: Set<String>, enabled: () -> Boolean) {
        val binder = Class.forName("com.android.systemui.statusbar.pipeline.mobile.ui.binder.MobileIconBinder", false, loader)
        val bind = binder.declaredMethods.single { it.name == "bind" && it.parameterCount == 4 &&
            it.parameterTypes[0] == ViewGroup::class.java }
        val guards = WeakHashMap<View, Guard>()
        fun watch(root: View) {
            if (!guards.containsKey(root)) guards[root] = Guard(root, names, enabled).also { it.start() }
        }
        val stateKey = "c16:mobile-$key-views"
        HotReloadLifecycle.register(stateKey,
            capture = { guards.keys.toTypedArray() },
            detach = { guards.values.toList().forEach(Guard::close); guards.clear() },
            restore = { (it as? Array<*>)?.filterIsInstance<View>()?.forEach(::watch) })
        ModernHookRegistry.installCompat("$stateKey:bind", bind, object : ModernMethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (param.throwable == null) (param.args[0] as? View)?.let(::watch)
            }
        })
    }

    private class Guard(root: View, private val names: Set<String>, private val enabled: () -> Boolean) :
        View.OnAttachStateChangeListener, ViewTreeObserver.OnPreDrawListener {
        private val rootRef = WeakReference(root)
        private val originals = WeakHashMap<View, Int>()
        private var observer: ViewTreeObserver? = null

        fun start() { rootRef.get()?.addOnAttachStateChangeListener(this); attach(); apply() }
        private fun attach() {
            val root = rootRef.get() ?: return
            val current = root.viewTreeObserver
            if (current !== observer) {
                detachObserver()
                observer = current
                current.addOnPreDrawListener(this)
            }
        }
        override fun onViewAttachedToWindow(view: View) { attach(); apply() }
        override fun onViewDetachedFromWindow(view: View) = detachObserver()
        override fun onPreDraw(): Boolean { apply(); return true }

        private fun apply() {
            if (HotReloadLifecycle.isRetiring || !enabled()) { restore(); return }
            fun visit(view: View) {
                val target = runCatching { view.resources.getResourcePackageName(view.id) == "com.android.systemui" &&
                    view.resources.getResourceTypeName(view.id) == "id" &&
                    view.resources.getResourceEntryName(view.id) in names }.getOrDefault(false)
                if (target) {
                    if (!originals.containsKey(view)) originals[view] = view.visibility
                    if (view.visibility != View.GONE) view.visibility = View.GONE
                } else if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
            }
            rootRef.get()?.let(::visit)
        }
        private fun restore() {
            originals.forEach { (view, visibility) -> if (view.visibility == View.GONE) view.visibility = visibility }
            originals.clear()
        }
        private fun detachObserver() {
            observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(this)
            observer = null
        }
        fun close() { rootRef.get()?.removeOnAttachStateChangeListener(this); detachObserver(); restore() }
    }
}
