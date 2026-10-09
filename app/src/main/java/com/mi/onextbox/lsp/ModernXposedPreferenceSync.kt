package com.mi.onextbox.lsp

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import com.mi.onextbox.lsp.compat.MODERN_PREFERENCES_READY_KEY
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Mirrors the app's feature preferences into framework-owned modern preferences. */
object ModernXposedPreferenceSync : XposedServiceHelper.OnServiceListener {
    private const val TAG = "ONextBox-XposedService"
    private const val GROUP = "lsp_features"

    private val initialized = AtomicBoolean(false)
    private val listenerRegistered = AtomicBoolean(false)
    private val initializer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ONextBox-XposedInit").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var localPreferences: SharedPreferences? = null

    private data class RemoteBinding(val service: XposedService, val preferences: SharedPreferences)
    private val remoteBinding = AtomicReference<RemoteBinding?>(null)

    private val syncQueued = AtomicBoolean(false)
    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> scheduleSync() }

    fun syncNow(context: Context) {
        initialize(context)
        scheduleSync()
    }

    private fun scheduleSync() {
        if (!syncQueued.compareAndSet(false, true)) return
        initializer.execute {
            syncQueued.set(false)
            val local = localPreferences ?: return@execute
            val bound = remoteBinding.get() ?: return@execute
            if (!LspFrameworkService.isCurrent(bound.service)) return@execute
            runCatching { copyAll(local, bound.preferences) }.onFailure {
                Log.w(TAG, "Preference sync failed", it)
                ConfigSyncStatus.failed("framework preferences")
            }
        }
    }

    fun initialize(context: Context) {
        LsposedScopeRequester.initialize(context)
        if (!initialized.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        initializer.execute {
            runCatching {
                localPreferences =
                    LspPreferenceStore.prefs(appContext).also {
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
                    localPreferences?.let { copyAll(it, remote) }
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

    private fun copyAll(source: SharedPreferences, target: SharedPreferences) {
        val editor = target.edit()
            .clear()
            .putBoolean(MODERN_PREFERENCES_READY_KEY, true)
        source.all.forEach { (key, value) -> putValue(editor, key, value) }
        check(editor.commit()) { "Framework preference commit failed" }
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
