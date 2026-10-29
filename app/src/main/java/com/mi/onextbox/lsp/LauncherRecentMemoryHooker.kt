package com.mi.onextbox.lsp

import android.app.Activity
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.View
import com.mi.onextbox.lsp.LspConfig.LauncherFeature
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.MODERN_PREFERENCES_READY_KEY
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.util.WeakHashMap

internal object LauncherRecentMemoryHooker {
    private const val TAG = "LauncherRecentMemory"
    private const val PREFERENCES = "lsp_features"

    fun hook(loader: ClassLoader, apkPath: String) {
        val stateKey = "$TAG:state@${System.identityHashCode(loader)}"
        val saved = DexTargetCache.restoredState(stateKey) as? Array<*>
        val session = Session(loader, apkPath)
        HotReloadLifecycle.register(stateKey,
            capture = session::capture,
            detach = session::detach,
            restore = session::restore,
            resumeAfterRejection = session::schedule,
        )
        session.restoreTargets(saved)
        HotReloadLifecycle.afterCommit {
            if (saved == null) session.restore(null)
        }
    }

    private fun enabled() = LspConfig.isLauncherFeatureEnabledXposed(LauncherFeature.RecentMemory)

    private class Session(private val loader: ClassLoader, private val apkPath: String) {
        private val prefix = "$TAG@${System.identityHashCode(loader)}:"
        private val main = Handler(Looper.getMainLooper())
        @Volatile private var targets: LauncherMemoryTargets? = null
        private var memory: LauncherMemoryState? = null
        private val panels = WeakHashMap<View, Boolean>()
        private var preferences: SharedPreferences? = null
        private var reportedUpdateFailure = false
        private val update = Runnable {
            HotReloadLifecycle.withHook<Unit>({}) {
                runCatching {
                    if (targets == null && enabled()) installTargets(null)
                    reconcile(discover = true)
                }.onFailure { HookLog.w(TAG, "Native memory display update unavailable", it) }
            }
        }
        private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == LauncherFeature.RecentMemory.key || key == MODERN_PREFERENCES_READY_KEY) schedule()
        }

        fun restoreTargets(saved: Array<*>?) {
            val values = saved?.getOrNull(0) as? Array<*>
            if (values != null || enabled()) installTargets(values)
        }

        private fun installTargets(values: Array<*>?) {
            if (targets != null || HotReloadLifecycle.isRetiring) return
            runCatching {
                val native = values?.let(::LauncherMemoryTargets) ?: LauncherMemoryTargets.resolve(loader, apkPath)
                val state = LauncherMemoryState(native.allowed, native.detail)
                fun synchronize(owner: Any?) {
                    if (owner == null) return
                    if (enabled()) state.force(owner)
                    else if (state.restoreAllowed(owner)) native.refresh.invoke(owner)
                }
                after(native.constructor) { synchronize(it.thisObject) }
                hook(native.refresh) { chain ->
                    val owner = chain.thisObject!!
                    if (!enabled()) state.restoreAllowed(owner)
                    val result = chain.proceed()
                    if (enabled()) state.force(owner)
                    result
                }
                native.gates.forEach { gate ->
                    hook(gate) { chain ->
                        synchronize(chain.thisObject)
                        if (enabled()) true else chain.proceed()
                    }
                }
                after(native.singleton) { chain -> synchronize(chain.result) }
                hook(native.format) { chain ->
                    synchronize(chain.thisObject)
                    chain.proceed()
                }
                hook(native.updatePanel) { chain ->
                    (chain.thisObject as? View)?.let { panel ->
                        panels[panel] = true
                        native.singleton.invoke(null, panel.context)
                    }
                    chain.proceed()
                }
                hook(native.bindPanel) { chain ->
                    val owner = chain.args.first { native.allowed.declaringClass.isInstance(it) }!!
                    synchronize(owner)
                    if (!enabled() && !native.detail.getBoolean(owner)) {
                        val panel = chain.args.first { native.panelType.isInstance(it) }
                        native.updatePanel.invoke(panel, false)
                        null
                    } else chain.proceed()
                }
                after(native.inflatePanel) { chain ->
                    (chain.thisObject as? View)?.let { panels[it] = true }
                }
                native.callers.forEach { method ->
                    ModernHookRuntime.requireModule().deoptimize(method)
                }
                memory = state
                targets = native
                HookLog.i(TAG, "Native memory display hooks installed: ${native.allowed.declaringClass.name}")
            }.onFailure {
                ModernHookRegistry.unhookPrefix(prefix)
                HookLog.w(TAG, "Memory display targets unavailable, stock behavior retained", it)
            }
        }

        fun schedule() {
            if (HotReloadLifecycle.isRetiring) return
            main.removeCallbacks(update)
            main.postDelayed(update, 80L)
        }

        fun capture(): Array<Any?> = arrayOf(targets?.values, memory?.export(), panels.keys.toTypedArray())

        fun detach() {
            main.removeCallbacks(update)
            preferences?.let { it.unregisterOnSharedPreferenceChangeListener(listener) }
            preferences = null
            releaseMemory()
        }

        fun restore(saved: Any?) {
            val rows = saved as? Array<*>
            (rows?.getOrNull(1) as? Array<*>)?.let { memory?.import(it) }
            (rows?.getOrNull(2) as? Array<*>)?.filterIsInstance<View>()?.forEach { panels[it] = true }
            if (preferences == null) {
                ModernHookRuntime.remotePreferences(PREFERENCES)?.let {
                    it.registerOnSharedPreferenceChangeListener(listener)
                    preferences = it
                }
            }
            reconcile(discover = false)
        }

        private fun releaseMemory() {
            val native = targets ?: return
            val state = memory ?: return
            state.export().forEach { row ->
                val owner = row[0]
                if (state.restoreAllowed(owner)) native.refresh.invoke(owner)
            }
        }

        private fun reconcile(discover: Boolean) {
            val native = targets ?: return
            val state = memory ?: return
            if (enabled()) {
                state.export().forEach { state.force(it[0]) }
            } else releaseMemory()
            if (discover) {
                val thread = ModernReflect.findClass("android.app.ActivityThread", loader)
                val app = ModernReflect.callStaticMethod(thread, "currentApplication")
                if (app != null) {
                    val owner = native.singletonField.get(null)
                    if (owner != null) {
                        if (enabled()) state.force(owner)
                        existingPanel(native)?.let { panels[it] = true }
                    }
                }
            }
            panels.keys.toList().filter { it.isAttachedToWindow }.forEach { panel ->
                runCatching {
                    native.updatePanel.invoke(panel, false)
                    panel.requestLayout()
                }.onFailure { reportUpdateFailure(it) }
            }
        }

        private fun existingPanel(native: LauncherMemoryTargets): View? = runCatching {
            val launcher = ModernReflect.findClass("com.android.launcher3.Launcher", loader)
            val tracker = launcher.getDeclaredField("ACTIVITY_TRACKER").get(null) ?: return@runCatching null
            val activity = ModernReflect.callMethod(tracker, "getCreatedActivity") as? Activity ?: return@runCatching null
            if (activity.isDestroyed) return@runCatching null
            val id = activity.resources.getIdentifier("tv_memory_info", "id", "com.android.launcher")
            if (id == 0) return@runCatching null
            val text = activity.window.decorView.findViewById<View>(id) ?: return@runCatching null
            generateSequence(text as View?) { it.parent as? View }.firstOrNull { native.panelType.isInstance(it) }
        }.getOrNull()

        private fun reportUpdateFailure(error: Throwable) {
            if (reportedUpdateFailure) return
            reportedUpdateFailure = true
            HookLog.w(TAG, "Native memory display refresh failed", error)
        }

        private fun hook(target: Executable, body: (XposedInterface.Chain) -> Any?) {
            ModernHookRegistry.installFast("$prefix${target.toGenericString()}", target,
                XposedInterface.Hooker { chain ->
                    if (targets == null) chain.proceed() else body(chain)
                },
            )
        }

        private data class CompletedCall(val thisObject: Any?, val result: Any?)

        private fun after(target: Executable, body: (CompletedCall) -> Unit) {
            hook(target) { chain ->
                val result = chain.proceed()
                runCatching { body(CompletedCall(chain.thisObject, result)) }.onFailure(::reportUpdateFailure)
                result
            }
        }
    }
}
