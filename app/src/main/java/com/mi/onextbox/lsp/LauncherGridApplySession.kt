package com.mi.onextbox.lsp

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.MODERN_PREFERENCES_READY_KEY
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface

internal class LauncherGridApplySession(private val loader: ClassLoader, private val apkPath: String) {
    private val prefix = "LauncherGrid@${System.identityHashCode(loader)}:apply-"
    private val main = Handler(Looper.getMainLooper())
    private var targets: LauncherGridTargets? = null
    private var menus: LauncherGridMenus? = null
    private var native: LauncherGridApplyTargets? = null
    private var preferences: SharedPreferences? = null
    private val queue = LauncherGridApplyQueue()
    private var starting = false
    private var task: Any? = null
    private var activity: Activity? = null
    private var installationFailed = false
    private val update = Runnable {
        HotReloadLifecycle.withHook<Unit>({}) {
            runCatching { reconcile() }.onFailure {
                HookLog.w(TAG, "Direct layout update unavailable", it)
            }
        }
    }
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == LauncherGridSettings.CONFIG || key == LauncherGridSettings.REQUEST ||
            key == MODERN_PREFERENCES_READY_KEY) schedule()
    }

    fun restoreTargets(saved: Array<*>?) {
        if (!LauncherGridSettings.stateXposed().enabled && saved?.getOrNull(0) == null) return
        install(saved?.getOrNull(0) as? Array<*>, saved?.getOrNull(1), saved?.getOrNull(2) as? Array<*>)
    }

    private fun install(menuValues: Array<*>? = null, menuState: Any? = null, applyValues: Array<*>? = null) {
        if (installationFailed || HotReloadLifecycle.isRetiring) return
        runCatching {
            if (targets == null) {
                val installed = LauncherGridHooker.installMenuHooks(loader, apkPath, menuValues, menuState)
                targets = installed.first
                menus = installed.second
            }
            if (native != null) return@runCatching
            val resolved = applyValues?.takeIf { it.size == LauncherGridApplyTargets.SCHEMA_SIZE }
                ?.let(::LauncherGridApplyTargets) ?: LauncherGridApplyTargets.resolve(loader, apkPath)
            ModernHookRegistry.installFast(prefix + "task", resolved.taskConstructor, XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                if (starting && queue.running != null) task = chain.thisObject
                result
            })
            ModernHookRegistry.installFast(prefix + "complete", resolved.completed, XposedInterface.Hooker { chain ->
                try { chain.proceed() } finally {
                    if (chain.thisObject === task && task != null) {
                        runCatching { completed(chain.getArg(0) == true) }
                            .onFailure { finish(); HookLog.w(TAG, "Unable to verify native layout completion", it) }
                    }
                }
            })
            native = resolved
            HookLog.i(TAG, "Native direct layout migration hooks installed")
        }.onFailure {
            ModernHookRegistry.unhookPrefix(prefix)
            installationFailed = true
            HookLog.w(TAG, "Direct custom layout unavailable, native layout retained", it)
        }
    }

    fun schedule() = scheduleAfter(300L)

    private fun scheduleAfter(delay: Long) {
        if (HotReloadLifecycle.isRetiring) return
        main.removeCallbacks(update)
        main.postDelayed(update, delay)
    }

    fun capture(): Array<Any?> = arrayOf(targets?.values, menus?.capture(), native?.values, queue.capture())

    fun canReload() = queue.running == null && menus?.canReload() != false

    fun detach() {
        main.removeCallbacks(update)
        preferences?.unregisterOnSharedPreferenceChangeListener(listener)
        preferences = null
        menus?.clear()
        task = null
        activity = null
    }

    fun restore(saved: Any?) {
        val rows = saved as? Array<*>
        menus?.restore(rows?.getOrNull(1))
        queue.restore(rows?.getOrNull(3))
        if (preferences == null) {
            preferences = ModernHookRuntime.remotePreferences("lsp_features")?.also {
                it.registerOnSharedPreferenceChangeListener(listener)
            }
        }
        schedule()
    }

    private fun reconcile() {
        val values = preferences?.all ?: return
        if (values[MODERN_PREFERENCES_READY_KEY] != true) return
        val state = LauncherGridRules.parse(values[LauncherGridSettings.CONFIG] as? String)
        val request = LauncherGridApplyRequest.parse(values[LauncherGridSettings.REQUEST] as? String)
            ?.takeIf { it.isCurrent(SystemClock.elapsedRealtime(), state) }
        queue.offer(request)
        if (!state.enabled) return
        install()
        if (queue.running != null || queue.pending == null) return
        val target = native
        val menu = targets
        if (target == null || menu == null) {
            queue.consume()
            return
        }
        val host = target.activity()
        if (host == null) {
            scheduleAfter(500L)
            return
        }
        val pending = queue.pending!!
        val acknowledgements = host.getSharedPreferences("onextbox_grid_control", Context.MODE_PRIVATE)
        if (acknowledgements.getString("last_request", null) == pending.token) {
            queue.consume()
            return
        }
        if (!menu.allowed()) {
            queue.consume()
            failed(host, "Current desktop mode does not support a custom layout")
            return
        }
        if (menus?.canReload() == false || !target.ready(host)) {
            scheduleAfter(500L)
            return
        }
        val original = currentGrid(host)
        if (original == pending.grid) {
            queue.consume()
            acknowledgements.edit().putString("last_request", pending.token).commit()
            HookLog.i(TAG, "Requested layout is already active: ${pending.grid}")
            return
        }
        if (original == null) {
            queue.consume()
            failed(host, "Native current layout unavailable")
            return
        }
        val applying = queue.start() ?: return
        if (!acknowledgements.edit().putString("last_request", applying.token).commit()) {
            finish()
            failed(host, "Unable to persist layout request acknowledgement")
            return
        }
        activity = host
        starting = true
        try {
            target.apply.invoke(null, host, applying.grid.columns, applying.grid.rows, false)
            check(task != null) { "Native layout migration task was not captured" }
            HookLog.i(TAG, "Applying custom grid using native migration: ${applying.grid}")
        } catch (error: Throwable) {
            if (!target.started(task) && menu.idle()) {
                runCatching { target.restorePreview(host, original) }
                    .onFailure { HookLog.w(TAG, "Unable to restore native workspace preview", it) }
                finish()
            }
            failed(host, "Unable to start native layout migration", error)
        } finally {
            starting = false
        }
    }

    private fun currentGrid(host: Activity): LauncherGrid? =
        (targets?.currentGrid?.invoke(null, host) as? IntArray)?.takeIf { it.size == 2 }
            ?.let { LauncherGrid(it[0], it[1]) }

    private fun completed(success: Boolean) {
        val request = queue.running ?: return
        val host = activity
        if (success && host != null && currentGrid(host) == request.grid) {
            HookLog.i(TAG, "Custom grid applied: ${request.grid}")
        } else if (host != null) failed(host, "Native layout migration did not apply the requested grid")
        finish()
    }

    private fun finish() {
        queue.finish()
        task = null
        activity = null
        schedule()
    }

    private fun failed(host: Activity, message: String, error: Throwable? = null) {
        HookLog.w(TAG, message, error)
        runCatching {
            val module = host.createPackageContext("com.mi.onextbox", 0)
            Toast.makeText(host, ModuleStrings.get(module, "launcher_custom_grid_apply_failed"), Toast.LENGTH_SHORT).show()
        }
    }

    companion object { private const val TAG = "LauncherGrid" }
}
