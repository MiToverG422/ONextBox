package com.mi.onextbox.lsp

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import com.mi.onextbox.lsp.DisplayDensitySettings.DEFAULT
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime

internal object DisplayDensityHooker {
    private const val TAG = "DisplayDensity"
    private const val HOOK = "system:display-density"

    fun hook(loader: ClassLoader) {
        runCatching {
            val preferences = checkNotNull(ModernHookRuntime.remotePreferences("lsp_features"))
            val window = loader.loadClass("com.android.server.wm.WindowManagerService")
            val session = Session(window, preferences)
            val ready = window.getDeclaredMethod("systemReady")
            ModernHookRegistry.installFast("$HOOK:ready", ready) { chain ->
                val result = chain.proceed()
                chain.thisObject?.let(session::windowReady)
                result
            }
            // The default display is reassigned during a foreground-user switch.
            val userSwitch = window.declaredMethods.firstOrNull {
                it.name == "switchUserInternal" && it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
            } ?: window.declaredMethods.firstOrNull {
                it.name == "setCurrentUser" && it.parameterTypes.firstOrNull() == Int::class.javaPrimitiveType
            }
            userSwitch?.let { method ->
                ModernHookRegistry.installFast("$HOOK:user", method) { chain ->
                    val result = chain.proceed()
                    session.schedule()
                    result
                }
            }
            HotReloadLifecycle.register(HOOK, detach = session::detach, restore = { session.attach() })
            HotReloadLifecycle.afterCommit(session::attach)
        }.onFailure { HookLog.w(TAG, "WindowManager density hook unavailable", it) }
    }

    private class Session(private val windowType: Class<*>, private val preferences: SharedPreferences) {
        private val handler = Handler(Looper.getMainLooper())
        private val systemReady = windowType.getDeclaredField("mSystemReady").apply { isAccessible = true }
        private val currentUser = windowType.getDeclaredField("mCurrentUserId").apply { isAccessible = true }
        private val contextField = windowType.getDeclaredField("mContext").apply { isAccessible = true }
        private val int = Int::class.javaPrimitiveType
        private val getInitialDensity = windowType.getMethod("getInitialDisplayDensity", int)
        private val getBaseDensity = windowType.getMethod("getBaseDisplayDensity", int)
        private val setDensity = windowType.getMethod("setForcedDisplayDensityForUser", int, int, int)
        private val clearDensity = windowType.getMethod("clearForcedDisplayDensityForUser", int, int)
        private val checkService = Class.forName("android.os.ServiceManager").getMethod("checkService", String::class.java)
        private val getProperty = Class.forName("android.os.SystemProperties").getMethod("get", String::class.java)
        private var waited = 0
        private var readyAt = 0L
        @Volatile private var attached = false
        @Volatile private var window: Any? = null
        private val reconcile = Runnable {
            HotReloadLifecycle.withHook<Unit>(fallback = {}) {
                if (attached) {
                    runCatching(::reconcileDensity)
                        .onFailure { HookLog.w(TAG, "Unable to reconcile display density", it) }
                }
            }
        }
        private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == DisplayDensitySettings.KEY) schedule()
        }

        fun attach() {
            if (attached) return
            preferences.registerOnSharedPreferenceChangeListener(listener)
            attached = true
            schedule()
        }

        fun detach() {
            attached = false
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
            handler.removeCallbacks(reconcile)
        }

        fun windowReady(instance: Any) {
            window = instance
            schedule()
        }

        fun schedule() {
            if (!attached) return
            handler.removeCallbacks(reconcile)
            handler.post(reconcile)
        }

        private fun reconcileDensity() {
            val wm = window ?: checkService.invoke(null, "window")?.takeIf(windowType::isInstance)
            if (wm == null || !systemReady.getBoolean(wm) || getProperty.invoke(null, "sys.boot_completed") != "1") {
                if (waited++ < 180) handler.postDelayed(reconcile, 1_000)
                return
            }
            // Leave time for the ROM to finish applying display defaults before the boot check.
            val now = SystemClock.uptimeMillis()
            if (readyAt == 0L) readyAt = now + 3_000
            if (now < readyAt) {
                handler.postAtTime(reconcile, readyAt)
                return
            }
            val resolver = (contextField.get(wm) as Context).contentResolver
            val user = currentUser.getInt(wm)
            val boot = Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT)
            val key = DisplayDensitySettings.resultKey(user)
            val previous = DisplayDensitySettings.result(Settings.Global.getString(resolver, key))
            val encoded = preferences.getString(DisplayDensitySettings.KEY, null)
            val request = DisplayDensitySettings.request(encoded)?.takeIf { it.user == user }
            if (request == null) {
                if (previous?.boot != boot || previous.id.isNotEmpty()) {
                    check(Settings.Global.putString(resolver, key, DisplayDensitySettings.Result("", boot, false, true).encode()))
                }
                return
            }

            val physical = getInitialDensity.invoke(wm, Display.DEFAULT_DISPLAY) as Int
            val before = getBaseDensity.invoke(wm, Display.DEFAULT_DISPLAY) as Int
            check(physical > 0 && before > 0)
            val override = before.takeIf { it != physical }
            val normalized = request.copy(
                original = DisplayDensitySettings.overrideFor(request.original, physical),
                target = DisplayDensitySettings.overrideFor(request.target, physical),
            )
            val change = DisplayDensitySettings.decide(normalized, boot, previous, override) ?: return
            if (currentUser.getInt(wm) != user || preferences.getString(DisplayDensitySettings.KEY, null) != encoded) return

            try {
                change.write?.let { writeOverride(wm, user, it) }
                val after = getBaseDensity.invoke(wm, Display.DEFAULT_DISPLAY) as Int
                val expected = if (change.write == DEFAULT) physical else change.write
                check(expected == null || after == expected) { "Expected $expected DPI, got $after" }
                val result = DisplayDensitySettings.Result(request.id, boot, change.retain, true)
                check(Settings.Global.putString(resolver, key, result.encode()))
                HookLog.i(TAG, "source=lsp boot=$boot action=${request.action} before=$before after=$after keep=${request.keep}")
            } catch (error: Exception) {
                // Restore only a value that this request may have written.
                runCatching {
                    val attempted = if (change.write == DEFAULT) physical else change.write
                    if (attempted != null && currentUser.getInt(wm) == user &&
                        getBaseDensity.invoke(wm, Display.DEFAULT_DISPLAY) == attempted) {
                        writeOverride(wm, user, override ?: DEFAULT)
                    }
                    val failure = DisplayDensitySettings.Result(request.id, boot, false, false)
                    Settings.Global.putString(resolver, key, failure.encode())
                }.onFailure(error::addSuppressed)
                throw error
            }
        }

        private fun writeOverride(wm: Any, user: Int, dpi: Int) {
            if (dpi == DEFAULT) clearDensity.invoke(wm, Display.DEFAULT_DISPLAY, user)
            else setDensity.invoke(wm, Display.DEFAULT_DISPLAY, dpi, user)
        }
    }
}
