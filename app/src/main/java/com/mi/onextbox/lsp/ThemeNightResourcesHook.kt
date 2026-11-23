package com.mi.onextbox.lsp

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.Process
import com.mi.onextbox.lsp.compat.HookConfigSnapshot
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import java.util.concurrent.atomic.AtomicBoolean

internal object ThemeNightResourcesHook {
    private const val KEY = "theme-store:night-resources"
    private var installed = false
    private fun enabled() = HookConfigSnapshot.boolean(ThemeStoreSettings.NIGHT_RESOURCES, false) == true

    @Synchronized
    fun install(loader: ClassLoader) {
        if (installed) return
        runCatching {
            val base = Class.forName("android.content.res.OplusBaseFile", false, loader)
            val ext = Class.forName("android.content.res.IResourcesImplExt", false, loader)
            val reject = base.getDeclaredMethod("rejectTheme", ext, String::class.java)
            val checkUpdate = Class.forName("android.content.res.OplusThemeResources", false, loader)
                .getDeclaredMethod("checkUpdate")
            val defaultTheme = Class.forName("com.oplus.theme.OplusThirdPartUtil", false, loader)
                .getDeclaredMethod("getDefaultTheme", Int::class.javaPrimitiveType)
            if (reject.returnType != Boolean::class.javaPrimitiveType || defaultTheme.returnType != reject.returnType) return
            val userId = Process.myUid() / 100000
            ModernHookRegistry.installFast(KEY, reject) { chain ->
                val rejected = chain.proceed()
                if (rejected == true && enabled() &&
                    runCatching { defaultTheme.invoke(null, userId) == false }.getOrDefault(false)) false
                else rejected
            }
            val prepared = AtomicBoolean()
            val main = Handler(Looper.getMainLooper())
            val prepare = Runnable {
                if (!HotReloadLifecycle.isRetiring && enabled() && prepared.compareAndSet(false, true)) {
                    runCatching { ModernHookRuntime.requireModule().deoptimize(checkUpdate) }
                        .onFailure { HookLog.w("ONextBox-Theme", "Unable to prepare theme updates", it) }
                }
            }
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == null || key == ThemeStoreSettings.NIGHT_RESOURCES) main.post(prepare)
            }
            var prefs: SharedPreferences? = null
            fun activate() {
                if (prefs == null) prefs = ModernHookRuntime.remotePreferences("lsp_features")?.also {
                    it.registerOnSharedPreferenceChangeListener(listener)
                }
                prepare.run()
            }
            HotReloadLifecycle.register(KEY, detach = {
                prefs?.unregisterOnSharedPreferenceChangeListener(listener)
                prefs = null
                main.removeCallbacks(prepare)
            }, resumeAfterRejection = ::activate)
            prepare.run()
            HotReloadLifecycle.afterCommit(::activate)
            installed = true
        }.onFailure { HookLog.w("ONextBox-Theme", "Night theme resource hook unavailable", it) }
    }
}
