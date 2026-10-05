package com.mi.onextbox.lsp.compat

import android.content.SharedPreferences
import android.util.Log
import io.github.libxposed.api.XposedModule
import java.util.concurrent.ConcurrentHashMap

/** Process-local access to the API 102 module instance and framework-owned config. */
internal object ModernHookRuntime {
    private val remotePreferences = ConcurrentHashMap<String, SharedPreferences>()

    @Volatile
    private var module: XposedModule? = null

    fun attach(module: XposedModule) {
        this.module = module
    }

    fun attachRemotePreferences(group: String, preferences: SharedPreferences) {
        remotePreferences[group] = preferences
    }

    fun remotePreferences(group: String): SharedPreferences? = remotePreferences[group]

    fun requireModule(): XposedModule = checkNotNull(module) {
        "Modern Xposed runtime is not attached"
    }

    fun log(priority: Int, tag: String, message: String, throwable: Throwable? = null) {
        val activeModule = module
        if (activeModule != null) {
            runCatching { activeModule.log(priority, tag, message, throwable) }
                .onSuccess { return }
        }
        when (priority) {
            Log.DEBUG -> Log.d(tag, message, throwable)
            Log.WARN -> Log.w(tag, message, throwable)
            Log.ERROR -> Log.e(tag, message, throwable)
            else -> Log.i(tag, message, throwable)
        }
    }
}
