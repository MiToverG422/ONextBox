package com.mi.onextbox.lsp

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Display
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Field
import java.lang.reflect.Method

/** OEM TouchIdle can override SurfaceFlinger's fixed framework range after the policy vote. */
internal object SettingsFixedRefreshIdleBridge {
    private const val TAG = "FixedRefreshIdle"
    private const val OWNER = "fixed-refresh-native-idle"
    private const val PREFIX = "settings-fixed-refresh-idle:"
    private const val MANAGER = "com.oplus.vrr.OPlusExternalRefreshRateManager"
    private const val SERVICE = "com.oplus.vrr.OPlusRefreshRateService"
    private const val CONTROLLER = "com.oplus.vrr.OPlusSurfaceFlingerController"

    private class Access(loader: ClassLoader) {
        val manager = ModernReflect.findClass(MANAGER, loader)
        val service = ModernReflect.findClass(SERVICE, loader)
        val controllerType = ModernReflect.findClass(CONTROLLER, loader)
        val bean = ModernReflect.findClass("com.oplus.vrr.bean.TouchIdleBean", loader)
        val config = SettingsFixedRefreshIdleConfig(bean)
        val defaultService = method(service, "getDefault")
        val managerField = field(service, "mExternalRefreshRateManager")
        val screen = field(manager, "mScreenState")
        val nativePause = field(manager, "mTouchIdleShouldPause")
        val replayConfig = method(manager, "handleTouchIdleConfig", Boolean::class.javaPrimitiveType!!)
        val sendConfig = method(controllerType, "setTouchIdleConfig", bean)
        val sendPause = method(controllerType, "setTouchIdleShouldPause", Boolean::class.javaPrimitiveType!!)
        val screenChanged = method(manager, "screenStateChange", Int::class.javaPrimitiveType!!)
        val publish = method(service, "publish", Context::class.java)
        val controller = checkNotNull(method(controllerType, "getInstance").invoke(null))
    }

    @Volatile private var requested = false
    @Volatile private var manager: Any? = null
    @Volatile private var access: Access? = null
    private var replay: SettingsRefreshRatePolicyReplay? = null

    fun hook(loader: ClassLoader): Int {
        val installed = ArrayList<String>()
        return runCatching {
            val fields = Access(loader)
            fun install(method: Method, name: String, hooker: XposedInterface.Hooker) {
                val key = PREFIX + name
                ModernHookRegistry.installFast(key, method, hooker)
                installed += key
            }
            access = fields
            install(fields.sendConfig, "outgoing-config", XposedInterface.Hooker { chain ->
                val original: Any? = chain.getArg(0)
                if (chain.thisObject !== fields.controller || original == null || !blocksIdle()) chain.proceed()
                else chain.proceed(arrayOf(fields.config.outgoing(original, true)))
            })
            install(fields.sendPause, "outgoing-pause", XposedInterface.Hooker { chain ->
                if (chain.thisObject === fields.controller && blocksIdle()) chain.proceed(arrayOf<Any?>(true))
                else chain.proceed()
            })
            install(fields.screenChanged, "screen-state", XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                if (chain.thisObject === manager) schedule()
                result
            })
            install(fields.publish, "service-published", XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                ensureBound()
                result
            })
            HotReloadLifecycle.register(OWNER,
                capture = { manager },
                detach = ::detach,
                restore = { saved -> bind(saved) },
                resumeAfterRejection = ::ensureBound,
            )
            HookLog.i(TAG, "Native TouchIdle output hooks installed: ${installed.size}")
            installed.size
        }.getOrElse {
            installed.forEach(ModernHookRegistry::unhook)
            access = null
            HookLog.w(TAG, "Native TouchIdle contract unavailable; leaving vendor idle policy unchanged", it)
            0
        }
    }

    /** Called without taking any vendor lock from window-policy hooks. */
    fun setRequested(enabled: Boolean) {
        if (requested == enabled) return
        requested = enabled
        ensureBound()
    }

    fun ensureBound() {
        if (access == null || HotReloadLifecycle.isRetiring) return
        HotReloadLifecycle.afterCommit {
            if (manager == null) bind(HotReloadLifecycle.restoredState(OWNER)) else schedule()
        }
    }

    private fun bind(saved: Any?) {
        if (HotReloadLifecycle.isRetiring) return
        val fields = access ?: return
        val native = if (saved != null && fields.manager.isInstance(saved)) saved else {
            val service = fields.defaultService.invoke(null)
            // Only use the local system-server owner, never a remote service proxy.
            if (!fields.service.isInstance(service)) return
            fields.managerField.get(service) ?: return
        }
        if (manager !== native || replay == null) {
            replay?.detach()
            manager = native
            val handler = Handler(Looper.getMainLooper())
            replay = SettingsRefreshRatePolicyReplay(HotReloadLifecycle,
                post = { handler.post(it) }, remove = { handler.removeCallbacks(it) }, reapply = ::reapply)
        }
        schedule()
    }

    private fun schedule() {
        if (!HotReloadLifecycle.isRetiring) replay?.schedule()
    }

    private fun blocksIdle(): Boolean {
        val fields = access ?: return false
        val owner = manager ?: return false
        return SettingsFixedRefreshRateRules.shouldBlockIdle(requested,
            SettingsFixedRefreshRateHooker.hasFixedSelection(), fields.screen.getInt(owner) == Display.STATE_ON)
    }

    private fun reapply() {
        val fields = access ?: return
        val owner = manager ?: return
        runCatching {
            // Pause first to exit an already-entered 60 Hz idle mode; rebuild from native
            // configuration, then send the pause again in case the config resets its output.
            fields.sendPause.invoke(fields.controller, fields.nativePause.getBoolean(owner))
            fields.replayConfig.invoke(owner, false)
            fields.sendPause.invoke(fields.controller, fields.nativePause.getBoolean(owner))
            HookLog.i(TAG, "Native TouchIdle output reapplied: blocked=${blocksIdle()}")
        }.onFailure { HookLog.w(TAG, "Native TouchIdle output refresh failed", it) }
    }

    private fun detach() {
        replay?.detach()
        replay = null
        requested = false
        // The originals are still owned by the OEM manager, including changes made while
        // fixed mode was active. Do not leave an overridden SF configuration after retirement.
        reapply()
        manager = null
    }

    private fun field(type: Class<*>, name: String): Field = type.getDeclaredField(name).apply { isAccessible = true }
    private fun method(type: Class<*>, name: String, vararg parameters: Class<*>): Method =
        type.getDeclaredMethod(name, *parameters).apply { isAccessible = true }
}
