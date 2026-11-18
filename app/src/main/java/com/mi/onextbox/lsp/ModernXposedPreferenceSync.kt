package com.mi.onextbox.lsp

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import com.mi.onextbox.lsp.compat.MODERN_PREFERENCES_READY_KEY
import com.mi.onextbox.lsp.compat.MODERN_PREFERENCES_REVISION_KEY
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicLong

/** Mirrors the app's feature preferences into framework-owned modern preferences. */
object ModernXposedPreferenceSync : XposedServiceHelper.OnServiceListener {
    @Volatile var lastDiagnosticWrite: Long = 0L
        private set
    @Volatile var lastDiagnosticError: String? = null
        private set

    fun diagnosticReadback(context: Context): Pair<Map<String, *>, Map<String, *>?> {
        initialize(context)
        return initializer.submit<Pair<Map<String, *>, Map<String, *>?>> {
            val local = checkNotNull(localPreferences).all
            val binding = remoteBinding.get()
            local to binding?.takeIf { LspFrameworkService.isCurrent(it.service) }?.preferences?.all
        }.get()
    }

    fun replaceDiagnosticValues(context: Context, values: Map<String, *>, featuresOnly: Boolean) {
        initialize(context)
        initializer.submit {
            val local = checkNotNull(localPreferences)
            val bound = checkNotNull(remoteBinding.get()) { "Xposed service is not connected" }
            check(LspFrameworkService.isCurrent(bound.service)) { "Xposed service changed" }
            val before = local.all
            val editor = local.edit()
            if (featuresOnly) before.keys.filter { !it.startsWith("developer_") && !it.startsWith("__") }.forEach(editor::remove)
            values.forEach { (key, value) -> putValue(editor, key, value) }
            try {
                com.mi.onextbox.diagnostics.diagnosticTransaction(
                    write = { check(editor.commit()) { "Local configuration write failed" } },
                    publish = {
                        copyAll(local, bound)
                        check(LspFrameworkService.isCurrent(bound.service)) { "Xposed service changed during synchronization" }
                    },
                    rollback = {
                        val rollback = local.edit().clear()
                        before.forEach { (key, value) -> putValue(rollback, key, value) }
                        check(rollback.commit()) { "Local rollback failed" }
                    },
                    republish = { copyAll(local, bound) },
                )
            } catch (error: Throwable) {
                lastDiagnosticError = error.toString()
                throw error
            }
        }.get()
    }
    private const val TAG = "ONextBox-XposedService"
    private const val GROUP = "lsp_features"

    private val initialized = AtomicBoolean(false)
    private val listenerRegistered = AtomicBoolean(false)
    private val initializer = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "ONextBox-XposedInit").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var localPreferences: SharedPreferences? = null

    private data class RemoteBinding(val service: XposedService, val preferences: SharedPreferences,
        var mirrored: MutableMap<String, Any?>? = null)
    private val remoteBinding = AtomicReference<RemoteBinding?>(null)

    private val syncQueued = AtomicBoolean(false)
    private val pendingChanges = PreferenceSyncBatch()
    private val publicationRevision = AtomicLong(System.currentTimeMillis())
    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key -> scheduleSync(key) }

    fun syncNow(context: Context) {
        initialize(context)
        scheduleSync()
    }

    /** Call off the main thread. Consent is accepted only after framework persistence succeeds. */
    fun setBooleanAndSync(context: Context, key: String, value: Boolean) {
        val started = System.nanoTime()
        initialize(context)
        // Serialize with every full mirror copy so an older copy cannot overwrite this choice.
        initializer.submit {
            try {
                val local = checkNotNull(localPreferences) { "Local preferences are not ready" }
                val bound = checkNotNull(remoteBinding.get()) { "Xposed service is not connected" }
                commitConfirmedBooleanPreference(local, bound.preferences, key, value, ::stampPublication) {
                    LspFrameworkService.isCurrent(bound.service)
                }
                bound.mirrored?.set(key, value)
                lastDiagnosticWrite = System.currentTimeMillis()
                lastDiagnosticError = null
                recordConfirmation(key, started)
            } catch (error: Throwable) {
                remoteBinding.get()?.mirrored = null
                lastDiagnosticError = error.toString()
                ConfigSyncStatus.failed("framework preferences")
                throw error
            }
        }.get()
    }

    /** Persist enum selections with the same serialized confirmation as Boolean preferences. */
    fun setIntAndSync(context: Context, key: String, value: Int) {
        val started = System.nanoTime()
        initialize(context)
        initializer.submit {
            try {
                val local = checkNotNull(localPreferences) { "Local preferences are not ready" }
                val bound = checkNotNull(remoteBinding.get()) { "Xposed service is not connected" }
                commitConfirmedIntPreference(local, bound.preferences, key, value, ::stampPublication) {
                    LspFrameworkService.isCurrent(bound.service)
                }
                bound.mirrored?.set(key, value)
                lastDiagnosticWrite = System.currentTimeMillis()
                lastDiagnosticError = null
                recordConfirmation(key, started)
            } catch (error: Throwable) {
                remoteBinding.get()?.mirrored = null
                lastDiagnosticError = error.toString()
                ConfigSyncStatus.failed("framework preferences")
                throw error
            }
        }.get()
    }

    fun setStringAndSync(context: Context, key: String, value: String) {
        val started = System.nanoTime()
        initialize(context)
        initializer.submit {
            try {
                val local = checkNotNull(localPreferences) { "Local preferences are not ready" }
                val bound = checkNotNull(remoteBinding.get()) { "Xposed service is not connected" }
                commitConfirmedStringPreference(local, bound.preferences, key, value, ::stampPublication) {
                    LspFrameworkService.isCurrent(bound.service)
                }
                bound.mirrored?.set(key, value)
                lastDiagnosticWrite = System.currentTimeMillis()
                lastDiagnosticError = null
                recordConfirmation(key, started)
            } catch (error: Throwable) {
                remoteBinding.get()?.mirrored = null
                lastDiagnosticError = error.toString()
                ConfigSyncStatus.failed("framework preferences")
                throw error
            }
        }.get()
    }

    private fun scheduleSync(key: String? = null) {
        pendingChanges.add(key)
        scheduleDrain()
    }

    private fun scheduleDrain() {
        if (!syncQueued.compareAndSet(false, true)) return
        initializer.schedule({
            val changes = pendingChanges.drain()
            try {
                val local = localPreferences ?: return@schedule
                val bound = remoteBinding.get() ?: return@schedule
                if (!LspFrameworkService.isCurrent(bound.service)) return@schedule
                runCatching { copyAll(local, bound, if (changes.full) null else changes.keys) }.onFailure {
                    bound.mirrored = null
                    Log.w(TAG, "Preference sync failed", it)
                    ConfigSyncStatus.failed("framework preferences")
                }
            } finally {
                syncQueued.set(false)
                if (pendingChanges.isPending()) scheduleDrain()
            }
        }, 16, TimeUnit.MILLISECONDS)
    }

    fun initialize(context: Context) {
        LsposedScopeRequester.initialize(context)
        if (!initialized.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        initializer.execute {
            runCatching {
                localPreferences =
                    LspPreferenceStore.prefs(appContext).also {
                        SideKeySettings.enforceDisabled(it)
                        AospUiSettings.enforcePaused(it)
                        GoogleMessagesConfig.removeRetiredPreferences(it)
                        if (it.contains("mcs_push_monitor_enabled")) {
                            check(it.edit().remove("mcs_push_monitor_enabled").commit())
                        }
                        it.registerOnSharedPreferenceChangeListener(preferenceListener)
                    }
            }.onSuccess {
                scheduleSync()
            }.onFailure {
                Log.w(TAG, "Unable to prepare local preferences", it)
                initialized.set(false)
                ConfigSyncStatus.failed("local preferences")
            }
            // The framework connection does not depend on local preference initialization.
            if (listenerRegistered.compareAndSet(false, true)) {
                mainHandler.post { XposedServiceHelper.registerListener(this) }
            }
        }
    }

    override fun onServiceBind(service: XposedService) {
        LspFrameworkService.bind(service)
        initializer.execute {
            if (!LspFrameworkService.isCurrent(service)) return@execute
            runCatching {
                val remote = service.getRemotePreferences(GROUP)
                if (!LspFrameworkService.publishIfCurrent(service) {
                        remoteBinding.set(RemoteBinding(service, remote))
                    }) return@execute
                if (LspFrameworkService.isCurrent(service)) {
                    localPreferences?.let { copyAll(it, checkNotNull(remoteBinding.get())) }
                }
                Log.i(TAG, "Connected to ${service.frameworkName} API ${service.apiVersion}")
            }.onFailure {
                clearRemoteBinding(service)
                Log.w(TAG, "Unable to initialize remote preferences", it)
                ConfigSyncStatus.failed("framework preferences")
            }
        }
    }

    override fun onServiceDied(service: XposedService) {
        val wasCurrent = LspFrameworkService.unbind(service)
        clearRemoteBinding(service)
        if (!wasCurrent) return
        Log.w(TAG, "Xposed service disconnected")
    }

    private fun clearRemoteBinding(service: XposedService) {
        while (true) {
            val bound = remoteBinding.get() ?: return
            if (bound.service !== service || remoteBinding.compareAndSet(bound, null)) return
        }
    }

    private fun copyAll(source: SharedPreferences, bound: RemoteBinding, keys: Set<String>? = null) {
        val started = System.nanoTime()
        check(LspFrameworkService.isCurrent(bound.service)) { "Xposed service changed before synchronization" }
        SideKeySettings.enforceDisabled(source)
        val target = bound.preferences
        val desired = frameworkPreferenceValues(source.all, SideKeyFeaturePolicy.available)
        val previous = bound.mirrored ?: target.all.filterKeys {
            it != MODERN_PREFERENCES_READY_KEY && it != MODERN_PREFERENCES_REVISION_KEY
        }
        val changed = preferenceDelta(previous, desired, if (bound.mirrored == null) null else keys)
        try {
            val ready = target.getBoolean(MODERN_PREFERENCES_READY_KEY, false)
            val publish = changed.isNotEmpty() || !ready || !target.contains(MODERN_PREFERENCES_REVISION_KEY)
            if (publish) {
                val editor = target.edit()
                if (!ready) editor.putBoolean(MODERN_PREFERENCES_READY_KEY, true)
                changed.forEach { (key, value) -> putValue(editor, key, value) }
                stampPublication(editor)
                check(LspFrameworkService.isCurrent(bound.service)) { "Xposed service changed before publication" }
                check(editor.commit()) { "Framework preference commit failed" }
                check(LspFrameworkService.isCurrent(bound.service)) { "Xposed service changed during publication" }
                lastDiagnosticWrite = System.currentTimeMillis()
            }
            bound.mirrored = previous.toMutableMap().apply {
                changed.forEach { (key, value) -> if (value == null) remove(key) else put(key, value) }
            }
            lastDiagnosticError = null
            if (publish) com.mi.onextbox.diagnostics.RuntimeDiagnostics.event("config_sync", "Preference mirror",
                "${changed.size} changed keys; full=${keys == null}", (System.nanoTime() - started) / 1_000_000)
        } catch (error: Throwable) {
            lastDiagnosticError = error.toString()
            throw error
        }
    }

    private fun stampPublication(editor: SharedPreferences.Editor) {
        editor.putLong(MODERN_PREFERENCES_REVISION_KEY, publicationRevision.updateAndGet {
            maxOf(it + 1, System.currentTimeMillis())
        })
    }

    private fun recordConfirmation(key: String, started: Long) {
        com.mi.onextbox.diagnostics.RuntimeDiagnostics.event("config_sync", "Confirmed setting", key,
            (System.nanoTime() - started) / 1_000_000)
    }

    private fun putValue(editor: SharedPreferences.Editor, key: String, value: Any?) {
        when (value) {
            null -> editor.remove(key)
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is String -> editor.putString(key, value)
            is Set<*> -> editor.putStringSet(key, frameworkStringSet(value))
            else -> Log.w(TAG, "Unsupported preference type for $key: ${value.javaClass.name}")
        }
    }
}
