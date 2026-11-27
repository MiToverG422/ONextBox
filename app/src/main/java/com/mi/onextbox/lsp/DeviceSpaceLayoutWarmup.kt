package com.mi.onextbox.lsp

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.MessageQueue
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.HookConfigSnapshot
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import java.lang.ref.WeakReference

/** Prepare the fixed Device Space layout while the bound control center tile is idle. */
internal object DeviceSpaceLayoutWarmup {
    private const val KEY = "systemui:device-space-warmup"
    private const val PACKAGE = "com.oplus.deviceplugin.sdk"
    private const val HOST = "com.android.systemui"
    private const val MAX_PREPARED_AGE_MS = 15_000L

    fun hook(loader: ClassLoader) {
        runCatching { Owner(loader).install() }.onFailure {
            ModernHookRegistry.unhookPrefix("$KEY:")
            HookLog.w("ONextBox-DeviceSpace", "Device Space layout warmup unavailable", it)
        }
    }

    private class Owner(loader: ClassLoader) {
        private val holderType = loader.loadClass("com.oplus.systemui.plugins.qs.customize.view.viewholder.EditablePluginViewHolder")
        private val serviceType = loader.loadClass("$PACKAGE.SysUiPluginService")
        private val panelType = loader.loadClass("$PACKAGE.ui.panel.m")
        private val layoutType = loader.loadClass("$PACKAGE.ui.view.CustomConstraintLayout")
        private val bind = holderType.getDeclaredMethod("bindData", loader.loadClass("com.oplusos.systemui.common.viewholder.GridData"))
        private val plugin = holderType.getDeclaredField("panelPlugin").apply { isAccessible = true }
        private val attached = holderType.getDeclaredField("isPluginViewAttached").apply { isAccessible = true }
        private val listening = holderType.getDeclaredField("isListening").apply { isAccessible = true }
        private val pluginContext = serviceType.getDeclaredField("_pluginContext").apply { isAccessible = true }
        private val created = serviceType.getDeclaredField("_isCreated").apply { isAccessible = true }
        private val separate = serviceType.getDeclaredField("_isSeparateMode").apply { isAccessible = true }
        private val create = serviceType.getDeclaredMethod("onCreateView", ViewGroup::class.java,
            loader.loadClass("com.android.systemui.plugins.qs.SpanSizeInPlugin"), Bundle::class.java)
        private val applyTheme = loader.loadClass("$PACKAGE.utils.ThemeUtils")
            .getDeclaredMethod("applyThemeOverlays", Context::class.java).apply { isAccessible = true }
        private val inflate = LayoutInflater::class.java.getDeclaredMethod("inflate", Int::class.javaPrimitiveType, ViewGroup::class.java)
        private val main = Handler(Looper.getMainLooper())
        private var holder: WeakReference<Any>? = null
        private var prepared: Layout? = null
        private val expire = Runnable { prepared = null }
        private val using = ThreadLocal<Layout>()
        private var idleQueued = false
        private val idle = MessageQueue.IdleHandler {
            idleQueued = false
            HotReloadLifecycle.withHook(fallback = {}) { prepare() }
            false
        }

        private class Layout(val service: Any, val base: Context, val themed: Context,
            val config: Configuration, val settings: Any?, val started: Long, val id: Int, val view: View)

        fun install() {
            // The native two-argument inflate attaches one child and returns this FrameLayout.
            check(panelType.superclass == FrameLayout::class.java && panelType.declaredMethods.none {
                it.name in setOf("addView", "generateLayoutParams", "checkLayoutParams")
            })
            ModernHookRegistry.installFast("$KEY:bind", bind) { chain ->
                val result = chain.proceed()
                if (Looper.myLooper() == main.looper) runCatching {
                    val target = chain.thisObject ?: return@runCatching
                    if (eligible(target) != null) {
                        holder = WeakReference(target)
                        enqueue()
                    }
                }
                result
            }
            ModernHookRegistry.installFast("$KEY:create", create) { chain ->
                if (Looper.myLooper() != main.looper) return@installFast chain.proceed()
                val layout = prepared ?: return@installFast chain.proceed()
                if (layout.service !== chain.thisObject || (chain.getArg(2) as? Bundle)?.getInt("device_mode_key") != 1) {
                    return@installFast chain.proceed()
                }
                prepared = null
                main.removeCallbacks(expire)
                if (pluginContext.get(layout.service) !== layout.base ||
                    layout.base.resources.configuration != layout.config || layout.view.parent != null ||
                    HookConfigSnapshot.identity !== layout.settings ||
                    SystemClock.uptimeMillis() - layout.started >= MAX_PREPARED_AGE_MS) {
                    return@installFast chain.proceed()
                }
                using.set(layout)
                try { chain.proceed() } finally { using.remove() }
            }
            ModernHookRegistry.installFast("$KEY:theme", applyTheme) { chain ->
                val layout = using.get()
                if (layout != null && chain.getArg(0) === layout.base) layout.themed else chain.proceed()
            }
            ModernHookRegistry.installFast("$KEY:inflate", inflate) { chain ->
                val layout = using.get() ?: return@installFast chain.proceed()
                if (chain.getArg(0) != layout.id) return@installFast chain.proceed()
                val parent = chain.getArg(1) as? FrameLayout ?: return@installFast chain.proceed()
                if (parent.javaClass !== panelType || parent.context !== layout.themed || parent.childCount != 0 ||
                    parent.isAttachedToWindow || layout.view.parent != null) return@installFast chain.proceed()
                runCatching { parent.addView(layout.view, layout.view.layoutParams) }.onFailure {
                    if (layout.view.parent === parent) parent.removeView(layout.view)
                    HookLog.w("ONextBox-DeviceSpace", "Native Device Space inflation used after attach failure", it)
                    return@installFast chain.proceed()
                }
                HookLog.i("ONextBox-DeviceSpace", "Reused the prepared Device Space layout")
                parent
            }
            HotReloadLifecycle.register(KEY,
                capture = { holder?.get() },
                detach = {
                    main.looper.queue.removeIdleHandler(idle)
                    main.removeCallbacks(expire)
                    idleQueued = false
                    prepared = null
                },
                restore = { holder = it?.let { target -> WeakReference(target) } },
                resumeAfterRejection = ::enqueue,
            )
            HotReloadLifecycle.afterCommit(::enqueue)
        }

        private fun eligible(target: Any): Any? {
            if (attached.getBoolean(target) || listening.getBoolean(target)) return null
            val service = plugin.get(target) ?: return null
            return service.takeIf { serviceType.isInstance(it) && created.getBoolean(it) && separate.getBoolean(it) }
        }

        private fun enqueue() {
            if (!idleQueued && holder?.get() != null && prepared == null) {
                idleQueued = true
                main.looper.queue.addIdleHandler(idle)
            }
        }

        private fun prepare() {
            val target = holder?.get() ?: return
            runCatching {
                val service = eligible(target) ?: return
                val base = pluginContext.get(service) as? Context ?: return
                val id = base.resources.getIdentifier("fixed_separate_main_layout", "layout", HOST)
                if (id == 0) return
                val config = Configuration(base.resources.configuration)
                val themed = applyTheme.invoke(null, base) as? Context ?: return
                val stagingParent = FrameLayout(themed)
                val view = LayoutInflater.from(themed).inflate(id, stagingParent, false)
                if (view.javaClass === layoutType && view.layoutParams is FrameLayout.LayoutParams && view.parent == null) {
                    prepared = Layout(service, base, themed, config, HookConfigSnapshot.identity,
                        SystemClock.uptimeMillis(), id, view)
                    main.postDelayed(expire, MAX_PREPARED_AGE_MS)
                    HookLog.i("ONextBox-DeviceSpace", "Prepared the Device Space layout while idle")
                }
            }.onFailure { HookLog.w("ONextBox-DeviceSpace", "Cannot prepare the Device Space layout", it) }
        }
    }
}
