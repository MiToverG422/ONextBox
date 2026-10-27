package com.mi.onextbox.lsp

import android.app.Activity
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.MODERN_PREFERENCES_READY_KEY
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

internal object SettingsTitleCollapseHooker {
    private const val TAG = "ONextBox-SettingsTitle"
    private const val STATE = "settings:title-collapse"
    private const val TYPE = "com.google.android.material.appbar.SettingsCollapsableAppBarLayout"
    private const val MODE_STATE = "MODE_STATE_KEY"

    fun hook(loader: ClassLoader) {
        val session = Session(loader)
        if (!session.install()) return
        HotReloadLifecycle.register(STATE, capture = session::capture, detach = session::detach,
            restore = session::restore, resumeAfterRejection = session::schedule)
        HotReloadLifecycle.afterCommit { session.restore(HotReloadLifecycle.restoredState(STATE)) }
    }

    private class Session(private val loader: ClassLoader) {
        private val main = Handler(Looper.getMainLooper())
        private val bars = Collections.synchronizedMap(WeakHashMap<View, SettingsTitleModeState>())
        private val writing = ThreadLocal<View?>()
        private lateinit var type: Class<*>
        private lateinit var getMode: Method
        private lateinit var setMode: Method
        private lateinit var expanded: Method
        private lateinit var collapsed: Method
        private var preferences: SharedPreferences? = null
        private var detached = false
        private var previousCollapsed = LspConfig.isSettingsTitleCollapsedEnabledXposed()
        private val active get() = !detached && !HotReloadLifecycle.isRetiring
        private val prefix = "$TAG@${System.identityHashCode(loader)}:"
        private val refresh = Runnable {
            HotReloadLifecycle.withHook({}, {
                if (active) {
                    val enabled = LspConfig.isSettingsTitleCollapsedEnabledXposed()
                    val initial = enabled && !previousCollapsed
                    previousCollapsed = enabled
                    snapshot().forEach { (bar, state) -> guarded { apply(bar, state, initial) } }
                }
            })
        }
        private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == SettingsAppearanceSettings.TITLE_COLLAPSED ||
                key == SettingsAppearanceSettings.TITLE_LOCK || key == MODERN_PREFERENCES_READY_KEY) schedule()
        }

        fun install(): Boolean = runCatching {
            type = loader.loadClass(TYPE)
            getMode = type.getDeclaredMethod("getMode").apply { isAccessible = true }
            setMode = type.getDeclaredMethod("setMode", Int::class.javaPrimitiveType).apply { isAccessible = true }
            collapsed = type.getDeclaredMethod("isCollapsed").apply { isAccessible = true }
            expanded = ModernReflect.findMethodExact(type, "setExpanded",
                arrayOf(java.lang.Boolean.TYPE, java.lang.Boolean.TYPE)).apply { isAccessible = true }
            require(getMode.returnType == Int::class.javaPrimitiveType && setMode.returnType == Void.TYPE &&
                collapsed.returnType == Boolean::class.javaPrimitiveType && expanded.returnType == Void.TYPE)
            val attached = type.getDeclaredMethod("onAttachedToWindow")
            val save = type.getDeclaredMethod("onSaveInstanceState")
            ModernHookRegistry.installFast(prefix + "mode", setMode, XposedInterface.Hooker { chain ->
                val bar = chain.thisObject as? View
                if (!active || bar == null || writing.get() === bar) return@Hooker chain.proceed()
                val state = state(bar)
                val requested = chain.getArg(0) as Int
                state.nativeMode = requested
                val desired = SettingsAppearanceRules.titleMode(requested, locked())
                state.overriding = desired != requested
                if (desired == requested) chain.proceed()
                else write(bar) { chain.proceed(arrayOf<Any?>(desired)) }
            })
            ModernHookRegistry.installFast(prefix + "expanded", expanded, XposedInterface.Hooker { chain ->
                val bar = chain.thisObject as? View
                if (!active || bar == null || !type.isInstance(bar) || writing.get() === bar) return@Hooker chain.proceed()
                val state = state(bar)
                val request = chain.getArg(0) as Boolean
                state.nativeExpanded = request
                if (locked() && state.nativeMode != 2 && request) chain.proceed(arrayOf<Any?>(false, false))
                else chain.proceed()
            })
            ModernHookRegistry.installFast(prefix + "attached", attached, XposedInterface.Hooker { chain ->
                val bar = chain.thisObject as? View
                // Attachment replays mMode; a forced mode must not replace the native mode we restore.
                val result = if (active && bar != null && bars[bar]?.overriding == true) {
                    write(bar) { chain.proceed() }
                } else chain.proceed()
                if (active && bar != null) guarded { apply(bar, state(bar), true) }
                result
            })
            ModernHookRegistry.installFast(prefix + "save", save, XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                val state = (chain.thisObject as? View)?.let { bars[it] }
                if (active && state?.overriding == true && result is Bundle && result.containsKey(MODE_STATE)) {
                    result.putInt(MODE_STATE, state.nativeMode)
                }
                result
            })
            val resumed = Activity::class.java.getDeclaredMethod("onPostResume")
            ModernHookRegistry.installFast(prefix + "resumed", resumed, XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                if (active) (chain.thisObject as? Activity)?.takeIf { it.packageName == "com.android.settings" }?.let { activity ->
                    guarded { visit(activity.window.decorView) }
                }
                result
            })
            listOf(type, expanded.declaringClass).distinct().forEach { owner -> owner.declaredMethods.forEach { method ->
                runCatching { ModernHookRuntime.requireModule().deoptimize(method) }
            } }
            HookLog.i(TAG, "Native Settings title-collapse and fixed-mode hooks installed")
        }.onFailure {
            ModernHookRegistry.unhookPrefix(prefix)
            HookLog.w(TAG, "Settings title contract unavailable; native behavior retained", it)
        }.isSuccess

        private fun locked(): Boolean {
            val settings = SettingsAppearanceSettings.stateXposed()
            return settings.titleCollapsed && settings.titleLocked
        }

        private fun state(bar: View) = bars.getOrPut(bar) {
            SettingsTitleModeState(getMode.invoke(bar) as Int, collapsed.invoke(bar) != true)
        }

        private fun visit(view: View) {
            if (type.isInstance(view)) {
                val initial = !bars.containsKey(view)
                apply(view, state(view), initial)
            }
            if (view is ViewGroup) (0 until view.childCount).forEach { visit(view.getChildAt(it)) }
        }

        private fun apply(bar: View, state: SettingsTitleModeState, initial: Boolean) {
            val enabled = LspConfig.isSettingsTitleCollapsedEnabledXposed()
            val desired = SettingsAppearanceRules.titleMode(state.nativeMode, locked())
            if (desired != state.nativeMode) {
                state.overriding = true
                write(bar) {
                    if (getMode.invoke(bar) != desired) setMode.invoke(bar, desired)
                    expanded.invoke(bar, false, false)
                }
            } else {
                if (state.overriding) {
                    state.overriding = false
                    write(bar) {
                        setMode.invoke(bar, state.nativeMode)
                        if (enabled && state.nativeMode != 2) expanded.invoke(bar, false, false)
                    }
                }
                if (initial && enabled && state.nativeMode != 2) write(bar) { expanded.invoke(bar, false, false) }
            }
        }

        private fun <T> write(bar: View, action: () -> T): T {
            val previous = writing.get()
            writing.set(bar)
            return try { action() } finally { writing.set(previous) }
        }

        private fun snapshot() = synchronized(bars) { bars.entries.map { it.key to it.value } }
        fun capture(): Array<Array<Any>> = snapshot().map { (bar, state) ->
            arrayOf<Any>(bar, state.nativeMode, state.nativeExpanded)
        }.toTypedArray()

        fun schedule() = HotReloadLifecycle.afterCommit { if (active) { main.removeCallbacks(refresh); main.post(refresh) } }

        fun detach() {
            detached = true
            preferences?.unregisterOnSharedPreferenceChangeListener(listener)
            preferences = null
            main.removeCallbacksAndMessages(null)
            snapshot().forEach { (bar, state) -> if (state.overriding) guarded {
                write(bar) { setMode.invoke(bar, state.nativeMode); expanded.invoke(bar, state.nativeExpanded, false) }
            } }
            bars.clear()
        }

        fun restore(saved: Any?) {
            detached = false
            (saved as? Array<*>)?.filterIsInstance<Array<*>>()?.forEach { entry ->
                val bar = entry.getOrNull(0) as? View ?: return@forEach
                val mode = entry.getOrNull(1) as? Int ?: return@forEach
                val wasExpanded = entry.getOrNull(2) as? Boolean ?: return@forEach
                if (type.isInstance(bar)) bars[bar] = SettingsTitleModeState(mode, wasExpanded)
            }
            if (preferences == null) ModernHookRuntime.remotePreferences("lsp_features")?.let {
                it.registerOnSharedPreferenceChangeListener(listener)
                preferences = it
            }
            schedule()
        }

        private fun guarded(action: () -> Unit) {
            runCatching(action).onFailure { HookLog.w(TAG, "Title mode refresh failed; native content retained", it) }
        }
    }
}
