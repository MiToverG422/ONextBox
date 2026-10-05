package com.mi.onextbox.lsp

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import com.mi.onextbox.lsp.compat.MODERN_PREFERENCES_READY_KEY
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Mirrors the app's feature preferences into framework-owned modern preferences. */
object ModernXposedPreferenceSync : XposedServiceHelper.OnServiceListener {
    private const val TAG = "ONextBox-XposedService"
    private const val GROUP = "lsp_features"
    private const val SERVICE_REGISTRATION_DELAY_MS = 800L

    private val initialized = AtomicBoolean(false)
    private val initializer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ONextBox-XposedInit").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var localPreferences: SharedPreferences? = null

    @Volatile
    private var remotePreferences: SharedPreferences? = null

    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
            val remote = remotePreferences ?: return@OnSharedPreferenceChangeListener
            runCatching {
                if (key == null) copyAll(preferences, remote) else copyKey(preferences, remote, key)
            }.onFailure { Log.w(TAG, "Preference sync failed", it) }
        }

    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        initializer.execute {
            runCatching {
                val deviceContext = appContext.createDeviceProtectedStorageContext()
                val devicePreferencesFile = File(
                    "/data/user_de/0/${appContext.packageName}/shared_prefs",
                    "$GROUP.xml",
                )
                if (!devicePreferencesFile.exists()) {
                    runCatching { deviceContext.moveSharedPreferencesFrom(appContext, GROUP) }
                }
                localPreferences =
                    deviceContext.getSharedPreferences(GROUP, Context.MODE_PRIVATE).also {
                        it.registerOnSharedPreferenceChangeListener(preferenceListener)
                    }
            }.onSuccess {
                // The bridge is irrelevant to the first frame. Register once startup rendering has
                // settled so a framework bind/copy cannot stall the cold-launch animation.
                mainHandler.postDelayed(
                    { XposedServiceHelper.registerListener(this) },
                    SERVICE_REGISTRATION_DELAY_MS,
                )
            }.onFailure {
                Log.w(TAG, "Unable to prepare local preferences", it)
            }
        }
    }

    override fun onServiceBind(service: XposedService) {
        initializer.execute {
            runCatching {
                val remote = service.getRemotePreferences(GROUP)
                remotePreferences = remote
                localPreferences?.let { copyAll(it, remote) }
                Log.i(TAG, "Connected to ${service.frameworkName} API ${service.apiVersion}")
            }.onFailure {
                remotePreferences = null
                Log.w(TAG, "Unable to initialize remote preferences", it)
            }
        }
    }

    override fun onServiceDied(service: XposedService) {
        remotePreferences = null
        Log.w(TAG, "Xposed service disconnected")
    }

    private fun copyAll(source: SharedPreferences, target: SharedPreferences) {
        val editor = target.edit()
            .clear()
            .putBoolean(MODERN_PREFERENCES_READY_KEY, true)
        source.all.forEach { (key, value) -> putValue(editor, key, value) }
        editor.apply()
    }

    private fun copyKey(source: SharedPreferences, target: SharedPreferences, key: String) {
        val editor = target.edit()
        if (!source.contains(key)) {
            editor.remove(key)
        } else {
            putValue(editor, key, source.all[key])
        }
        editor.apply()
    }

    private fun putValue(editor: SharedPreferences.Editor, key: String, value: Any?) {
        when (value) {
            null -> editor.remove(key)
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is String -> editor.putString(key, value)
            is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            else -> Log.w(TAG, "Unsupported preference type for $key: ${value.javaClass.name}")
        }
    }
}
