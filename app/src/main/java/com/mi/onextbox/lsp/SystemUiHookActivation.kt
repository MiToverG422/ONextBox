package com.mi.onextbox.lsp

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.mi.onextbox.diagnostics.SafeModePolicy
import com.mi.onextbox.lsp.compat.HookConfigSnapshot
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.MODERN_PREFERENCES_READY_KEY
import com.mi.onextbox.lsp.compat.MODERN_PREFERENCES_REVISION_KEY
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.SafeModeRuntime

/** Install SystemUI rendering hooks when their settings first require them. */
internal object SystemUiHookActivation {
    fun install(
        stateKey: String,
        keys: Set<String>,
        enabled: () -> Boolean,
        installHooks: (late: Boolean) -> Unit,
    ) {
        val observer = PendingActivation(stateKey, keys, enabled, installHooks)
        HotReloadLifecycle.register("systemui:activation:$stateKey",
            detach = observer::detach, resumeAfterRejection = observer::observe)
        // A replacement generation must still own the previous generation's
        // callbacks and captured views, even if the user has since turned it off.
        if (enabled() || HotReloadLifecycle.restoredState(stateKey) != null) {
            observer.install(late = false)
        } else {
            HotReloadLifecycle.afterCommit(observer::observe)
        }
    }

    private class PendingActivation(
        private val stateKey: String,
        private val keys: Set<String>,
        private val enabled: () -> Boolean,
        private val installHooks: (Boolean) -> Unit,
    ) {
        private val main by lazy { Handler(Looper.getMainLooper()) }
        @Volatile private var preferences: SharedPreferences? = null
        private var installed = false
        private val activate = Runnable {
            HotReloadLifecycle.withHook(fallback = {}) {
                if (!installed && HookConfigSnapshot.isAvailable && !SafeModeRuntime.blocked && enabled()) {
                    install(late = true)
                }
            }
        }
        private val listener = SharedPreferences.OnSharedPreferenceChangeListener { store, key ->
            if (store === preferences && (key == null || key in keys || key == MODERN_PREFERENCES_READY_KEY ||
                key == MODERN_PREFERENCES_REVISION_KEY || key == SafeModePolicy.MANUAL ||
                key == SafeModePolicy.AUTOMATIC)
            ) schedule()
        }

        fun observe() {
            if (installed) return
            if (preferences == null) {
                preferences = ModernHookRuntime.remotePreferences("lsp_features")?.also {
                    it.registerOnSharedPreferenceChangeListener(listener)
                }
            }
            schedule()
        }

        private fun schedule() {
            main.removeCallbacks(activate)
            main.post(activate)
        }

        fun install(late: Boolean) {
            installed = true
            detach()
            runCatching { installHooks(late) }.onFailure {
                HookLog.w("ONextBox-SystemUI", "Hook installation unavailable for $stateKey", it)
            }
        }

        fun detach() {
            val store = preferences ?: return
            store.unregisterOnSharedPreferenceChangeListener(listener)
            preferences = null
            main.removeCallbacks(activate)
        }
    }
}
