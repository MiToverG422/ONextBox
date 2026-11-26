package com.mi.onextbox.lsp

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.mi.onextbox.diagnostics.SafeModePolicy
import com.mi.onextbox.lsp.compat.HookConfigSnapshot
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.MODERN_PREFERENCES_READY_KEY
import com.mi.onextbox.lsp.compat.MODERN_PREFERENCES_REVISION_KEY
import com.mi.onextbox.lsp.compat.ModernHotReloadSession
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.SafeModeRuntime
import java.lang.reflect.Executable

/** Install optional SystemUI hooks when their settings first require them. */
internal object SystemUiHookActivation {
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val waiting = linkedSetOf<PendingActivation>()
    private var preferences: SharedPreferences? = null
    private var refreshPosted = false
    private val refresh = Runnable {
        val pending = synchronized(waiting) {
            refreshPosted = false
            waiting.toList()
        }
        HotReloadLifecycle.withHook(fallback = {}) {
            if (HookConfigSnapshot.isAvailable && !SafeModeRuntime.blocked) pending.forEach { it.tryInstall() }
        }
    }
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { store, key ->
        synchronized(waiting) {
            if (store === preferences && (key == null || key == MODERN_PREFERENCES_READY_KEY ||
                key == MODERN_PREFERENCES_REVISION_KEY || key == SafeModePolicy.MANUAL ||
                key == SafeModePolicy.AUTOMATIC || waiting.any { key in it.keys })
            ) scheduleRefresh()
        }
    }

    fun install(
        stateKey: String,
        keys: Set<String>,
        enabled: () -> Boolean,
        legacyTarget: ((Executable) -> Boolean)? = null,
        installHooks: (late: Boolean) -> Unit,
    ) {
        val observer = PendingActivation(stateKey, keys, enabled, installHooks)
        val activationKey = "systemui:activation:$stateKey"
        HotReloadLifecycle.register(activationKey, capture = { observer.installed },
            detach = observer::detach, resumeAfterRejection = observer::observe)
        // A replacement generation must still own the previous generation's
        // callbacks and captured views, even if the user has since turned it off.
        val previous = HotReloadLifecycle.restoredState(activationKey) as? Boolean
        val hadHooks = previous ?: (HotReloadLifecycle.hasRestoredState(stateKey) ||
            (legacyTarget != null && ModernHotReloadSession.current?.previousTargets?.any(legacyTarget) == true))
        // With no previous handles, use the live-enable path after replacement.
        // It can attach to existing views once the callback gate is open.
        if (hadHooks || (!HotReloadLifecycle.isStaging && enabled())) {
            observer.install(late = false)
        } else {
            HotReloadLifecycle.afterCommit(observer::observe)
        }
    }

    private fun scheduleRefresh() {
        if (!refreshPosted) refreshPosted = main.post(refresh)
    }

    private class PendingActivation(
        private val stateKey: String,
        val keys: Set<String>,
        private val enabled: () -> Boolean,
        private val installHooks: (Boolean) -> Unit,
    ) {
        var installed = false
            private set

        fun observe() {
            if (installed) return
            synchronized(waiting) {
                waiting.add(this)
                if (preferences == null) {
                    preferences = ModernHookRuntime.remotePreferences("lsp_features")?.also {
                        it.registerOnSharedPreferenceChangeListener(listener)
                    }
                }
                scheduleRefresh()
            }
        }

        fun tryInstall() {
            if (!installed && enabled()) install(late = true)
        }

        fun install(late: Boolean) {
            runCatching {
                detach()
                installed = true
                installHooks(late)
            }.onFailure {
                HookLog.w("ONextBox-SystemUI", "Hook installation unavailable for $stateKey", it)
            }
        }

        fun detach() {
            synchronized(waiting) {
                if (this !in waiting) return
                if (waiting.size == 1) {
                    preferences?.unregisterOnSharedPreferenceChangeListener(listener)
                    preferences = null
                    main.removeCallbacks(refresh)
                    refreshPosted = false
                }
                waiting.remove(this)
            }
        }
    }
}
