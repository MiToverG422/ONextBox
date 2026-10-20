package com.mi.onextbox.lsp

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import com.mi.onextbox.lsp.compat.*
import java.util.WeakHashMap
import com.mi.onextbox.lsp.compat.ModernReflect as Reflect

internal object AppLockDelayMenuHooker {
    fun hook(loader: ClassLoader) = runCatching { Session(loader).install() }
        .onFailure { HookLog.w("ONextBox-AppLock", "Native lock-time menu unavailable", it) }.let { }

    private class Session(private val loader: ClassLoader) {
        private data class Row(val entries: Array<CharSequence>, val values: Array<CharSequence>, val groups: IntArray?)
        private val rows = WeakHashMap<Any, Row>()
        private val main = Handler(Looper.getMainLooper())
        private var prefs: SharedPreferences? = null
        private var updating = false
        private var retired = false
        private val prefix = "ONextBox-AppLock:menu@${System.identityHashCode(loader)}:"
        private val refresh = Runnable { HotReloadLifecycle.withHook({}, { if (!retired) rows.keys.toList().forEach(::refresh) }) }
        private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key in setOf(AppLockDelaySettings.ENABLED, MODERN_PREFERENCES_READY_KEY,
                SystemAdaptationSettings.KEY, SystemAdaptationSettings.ALLOW_HYPER_OS)) {
                if (!retired) { main.removeCallbacks(refresh); main.post(refresh) }
            }
        }
        private fun matches(row: Any?) = row != null && Reflect.callMethod(row, "getKey") == AppLockDelaySettings.ROW
        private fun context(row: Any) = Reflect.callMethod(row, "getContext") as Context
        @Suppress("UNCHECKED_CAST")
        private fun remember(row: Any): Row? {
            rows[row]?.let { return it }
            val entries = Reflect.callMethod(row, "getEntries") as? Array<CharSequence> ?: return null
            val values = Reflect.callMethod(row, "getEntryValues") as? Array<CharSequence> ?: return null
            if (entries.size != 2 || values.size != 2 || Reflect.callMethod(row, "isPersistent") != true) return null
            return Row(entries.copyOf(), values.copyOf(), (Reflect.getObjectField(row, "mGroupIds") as? IntArray)?.copyOf()).also { rows[row] = it }
        }
        private fun selected(row: Any, state: Row): String {
            val context = context(row)
            val delay = if (!retired && AppLockDelaySettings.active()) AppLockDelaySettings.selected(context) else 0L
            return if (delay != 0L) AppLockDelaySettings.token(delay)
            else state.values[if (Settings.Secure.getInt(context.contentResolver, AppLockDelaySettings.NATIVE_MODE, 1) == 1) 1 else 0].toString()
        }
        private fun refresh(row: Any) {
            if (updating) return
            runCatching {
                val state = remember(row) ?: return
                val enabled = !retired && AppLockDelaySettings.active()
                val options = AppLockDelayMenuOptions.build(state.entries, state.values, enabled) { AppLockDelaySettings.label(context(row), it) }
                val entries = options.entries
                val values = options.values
                updating = true
                try {
                    val groups = state.groups?.let { original -> IntArray(entries.size) { index -> original.getOrElse(index) { -1 } } }
                    Reflect.setObjectField(row, "mGroupIds", groups)
                    // COUI rebuilds popup titles in both setters; labels must be applied last.
                    Reflect.callMethod(row, "setEntryValues", values)
                    Reflect.callMethod(row, "setEntries", entries)
                    val value = selected(row, state)
                    Reflect.callMethod(row, "setValue", value)
                    val index = values.indexOfFirst { it.toString() == value }.coerceAtLeast(0)
                    Reflect.callMethod(row, "setAssignment", entries[index])
                } finally { updating = false }
            }.onFailure { HookLog.w("ONextBox-AppLock", "Lock-time menu refresh failed", it) }
        }

        fun install() {
            val menu = Reflect.findClass("com.coui.appcompat.preference.COUIMenuPreference", loader)
            val preference = Reflect.findClass("androidx.preference.Preference", loader)
            val entries = menu.getDeclaredMethod("setEntryValues", Array<CharSequence>::class.java)
            val value = menu.getDeclaredMethod("setValue", String::class.java)
            val change = preference.getDeclaredMethod("callChangeListener", Any::class.java)
            val persist = preference.getDeclaredMethod("persistString", String::class.java)
            menu.getDeclaredField("mGroupIds")
            ModernHookRegistry.installCompat(prefix + "entries", entries, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.throwable == null && !updating && !retired) runCatching { if (matches(param.thisObject)) refresh(param.thisObject!!) }
                }
            })
            ModernHookRegistry.installCompat(prefix + "value", value, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (updating || retired) return
                    runCatching {
                        val row = param.thisObject ?: return
                        if (!matches(row)) return
                        val state = remember(row) ?: return
                        if (AppLockDelaySettings.active() || AppLockDelaySettings.fromToken(param.args[0]) != null) {
                            param.args[0] = selected(row, state)
                        }
                    }
                }
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.throwable == null && !updating && !retired) runCatching { if (matches(param.thisObject)) refresh(param.thisObject!!) }
                }
            })
            // Never persist extension tokens into native preferences: uninstall must remain safe.
            ModernHookRegistry.installCompat(prefix + "persist-stock-value", persist, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (AppLockDelaySettings.fromToken(param.args[0]) == null) return
                    runCatching {
                        val row = param.thisObject ?: return
                        if (matches(row)) {
                            val stock = remember(row)?.values?.firstOrNull()?.toString()
                            if (stock == null) param.result = false
                            else param.args[0] = AppLockDelayMenuOptions.persist(param.args[0] as String, stock)
                        }
                    }.onFailure { param.result = false }
                }
            })
            ModernHookRegistry.installCompat(prefix + "choice", change, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (retired || !AppLockDelaySettings.active()) return
                    runCatching {
                        val row = param.thisObject ?: return
                        if (!menu.isInstance(row) || !matches(row) || remember(row) == null) return
                        val context = context(row)
                        val delay = AppLockDelaySettings.fromToken(param.args[0])
                        if (delay == null) {
                            check(Settings.Secure.putLong(context.contentResolver, AppLockDelaySettings.DELAY, 0L))
                        } else {
                            // All custom choices retain the stock immediate mode as the fallback.
                            check(Settings.Secure.putLong(context.contentResolver, AppLockDelaySettings.DELAY, 0L))
                            check(Settings.Secure.putInt(context.contentResolver, AppLockDelaySettings.NATIVE_MODE, 0))
                            check(Settings.Secure.putLong(context.contentResolver, AppLockDelaySettings.DELAY, delay))
                            param.result = true
                        }
                    }.onFailure {
                        param.result = false
                        runCatching { (param.thisObject?.let(::context))?.let { context ->
                            Toast.makeText(context, AppLockDelaySettings.saveError(context), Toast.LENGTH_LONG).show()
                            refresh(param.thisObject!!)
                        } }
                        HookLog.w("ONextBox-AppLock", "Lock-time selection was not saved", it)
                    }
                }
            })
            listOf(entries, value, change, persist).forEach { runCatching { ModernHookRuntime.requireModule().deoptimize(it) } }
            HotReloadLifecycle.register("applock:delay-menu", capture = { rows.keys.toTypedArray() },
                detach = {
                    retired = true
                    main.removeCallbacksAndMessages(null)
                    prefs?.unregisterOnSharedPreferenceChangeListener(listener); prefs = null
                    rows.keys.toList().forEach(::refresh)
                    rows.clear()
                },
                restore = { state -> retired = false; import(state); observe() },
                resumeAfterRejection = { observe(); rows.keys.toList().forEach(::refresh) },
            )
            HotReloadLifecycle.afterCommit { import(HotReloadLifecycle.restoredState("applock:delay-menu")); observe() }
        }
        private fun import(state: Any?) { (state as? Array<*>)?.filterNotNull()?.forEach(::refresh) }
        private fun observe() { if (prefs == null) ModernHookRuntime.remotePreferences("lsp_features")?.let {
            it.registerOnSharedPreferenceChangeListener(listener); prefs = it
        } }
    }
}
