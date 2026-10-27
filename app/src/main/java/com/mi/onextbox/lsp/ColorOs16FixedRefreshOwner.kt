package com.mi.onextbox.lsp

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Field
import java.lang.reflect.Method

/** Owns only C16's native fixed-rate selection, independently of any extreme-rate mode. */
internal object ColorOs16FixedRefreshOwner {
    private const val TAG = "C16FixedRefresh"
    private const val OWNER = "c16-fixed-refresh-owner"
    private const val MODE = "oplus_customize_screen_refresh_rate"
    private const val RESOLUTION = "oplus_customize_screen_resolution_adjust"

    fun hook(loader: ClassLoader): Int = runCatching {
        val policyType = ModernReflect.findClass("com.android.server.wm.OplusRefreshRatePolicyImpl", loader)
        val ready = policyType.getDeclaredMethod("onSystemReady")
        check(ready.returnType == Void.TYPE)
        val context = field(policyType, "mContext")
        val display = field(policyType, "mDisplayId")
        val configs = field(policyType, "mConfigs")
        val listener = configs.type.getDeclaredMethod("getConfigListChangeListener").apply { isAccessible = true }
        val refresh = listener.returnType.getDeclaredMethod("onConfigListChange").apply { isAccessible = true }
        check(context.type == Context::class.java && display.type == Int::class.javaPrimitiveType &&
            refresh.returnType == Void.TYPE)
        val session = Session(context, display, configs, listener, refresh)
        val backend = SettingsFixedRefreshRateHooker.hook(loader)
        if (backend == 0) return@runCatching 0
        ModernHookRegistry.installFast("settings-fixed-refresh:c16-system-ready", ready,
            XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                runCatching { session.bind(chain.thisObject) }
                    .onFailure { HookLog.w(TAG, "Native fixed-rate owner unavailable", it) }
                result
            })
        HotReloadLifecycle.register(OWNER,
            capture = session::capture,
            detach = session::detach,
            restore = session::bind,
            resumeAfterRejection = session::schedule,
        )
        HotReloadLifecycle.afterCommit { session.bind(HotReloadLifecycle.restoredState(OWNER)) }
        HookLog.i(TAG, "Independent native fixed-rate owner installed")
        backend + 1
    }.onFailure { HookLog.w(TAG, "C16 fixed-rate initialization unavailable", it) }.getOrDefault(0)

    private class Session(
        private val contextField: Field,
        private val displayField: Field,
        private val configsField: Field,
        private val listenerMethod: Method,
        private val refreshMethod: Method,
    ) {
        private val handler = Handler(Looper.getMainLooper())
        private var policy: Any? = null
        private var context: Context? = null
        private var listener: Any? = null
        private var observer: ContentObserver? = null
        private var replay: SettingsRefreshRatePolicyReplay? = null
        private val resolutionUri = Settings.Secure.getUriFor(RESOLUTION)
        private val delayedResolution = Runnable {
            HotReloadLifecycle.withHook({}, { refresh(force = false) })
        }

        fun capture(): Any? = policy

        fun bind(owner: Any?) {
            if (owner == null || HotReloadLifecycle.isRetiring || policy != null || displayField.getInt(owner) != 0) return
            val currentContext = contextField.get(owner) as? Context ?: return
            val configs = configsField.get(owner) ?: return
            val callback = listenerMethod.invoke(configs) ?: return
            policy = owner
            context = currentContext
            listener = callback
            val changed = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    HotReloadLifecycle.withHook({}, {
                        refresh(force = false)
                        if (uri == resolutionUri) {
                            handler.removeCallbacks(delayedResolution)
                            handler.postDelayed(delayedResolution, 1500L)
                        }
                    })
                }
            }
            observer = changed
            val resolver = currentContext.contentResolver
            try {
                resolver.registerContentObserver(Settings.Global.getUriFor(SettingsFixedRefreshRateRules.TOGGLE_KEY), false, changed)
                resolver.registerContentObserver(Settings.Global.getUriFor("low_power"), false, changed)
                resolver.registerContentObserver(Settings.Secure.getUriFor(MODE), false, changed)
                resolver.registerContentObserver(resolutionUri, false, changed)
                replay = SettingsRefreshRatePolicyReplay(HotReloadLifecycle,
                    post = { handler.post(it) }, remove = { handler.removeCallbacks(it) },
                    reapply = { refresh(force = true) })
                schedule()
            } catch (error: Throwable) {
                detach()
                throw error
            }
        }

        private fun refresh(force: Boolean) {
            if (HotReloadLifecycle.isRetiring) return
            val owner = policy ?: return
            val currentContext = context ?: return
            runCatching {
                val changed = SettingsFixedRefreshRateHooker.updatePolicy(owner, currentContext, colorOs16 = true)
                if (force || changed) refreshMethod.invoke(listener)
            }.onFailure { HookLog.w(TAG, "Native fixed-rate policy refresh failed", it) }
        }

        fun schedule() { replay?.schedule() }

        fun detach() {
            observer?.let { context?.contentResolver?.unregisterContentObserver(it) }
            replay?.detach()
            handler.removeCallbacksAndMessages(null)
            SettingsFixedRefreshRateHooker.clear()
            replay = null
            observer = null
            listener = null
            policy = null
            context = null
        }
    }

    private fun field(type: Class<*>, name: String): Field = type.getDeclaredField(name).apply { isAccessible = true }
}
