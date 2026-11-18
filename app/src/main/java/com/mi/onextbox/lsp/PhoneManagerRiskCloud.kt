package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.HookConfigSnapshot
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean

internal object PhoneManagerRiskCloudSettings {
    const val KEY = "phonemanager_hide_risk_app_cloud"
    fun enabled(context: Context) = LspPreferenceStore.prefs(context).getBoolean(KEY, false)
    fun enabledXposed() = HookConfigSnapshot.boolean(KEY, false) == true
    fun set(context: Context, enabled: Boolean) {
        check(LspPreferenceStore.prefs(context).edit().putBoolean(KEY, enabled).commit())
        LspConfig.syncReadableState(context)
    }
}

internal object PhoneManagerRiskCloudHooker {
    private const val TAG = "ONextBox-PhoneManagerRiskCloud"
    private const val OWNER = "com.oplus.phonemanager.virusdetect.riskapptips.RiskAppTipsCardHelperKt"
    fun hook(loader: ClassLoader, apkPath: String) {
        val key = "$TAG@${System.identityHashCode(loader)}"
        runCatching {
            val owner = HostClassLookup.load(OWNER, false, loader)
            val visual = owner.getDeclaredMethod("getVisualContext", Context::class.java)
            require(Modifier.isStatic(visual.modifiers) && visual.returnType == Context::class.java)
            // Verify the native ordinary-notification fallback exists before changing its input.
            val callers = HostDexMetadata.methods(loader, apkPath, "phone-manager-risk-cloud", "v1") { dex ->
                val methods = dex.findMethod {
                    matcher {
                        usingStrings("showRiskTips: no display context, fallback to notification")
                        name = "invokeSuspend"
                        paramTypes("java.lang.Object")
                        returnType = "java.lang.Object"
                    }
                }.filter { it.className.startsWith(OWNER + "\$showRiskTips\$") }
                    .filter { candidate -> candidate.invokes.any { it.className == OWNER && it.name == "getVisualContext" } }
                    .map { it.getMethodInstance(loader) }.also { require(it.isNotEmpty()) }
                mapOf("callers" to methods.toTypedArray())
            }.getValue("callers").toList()
            // Do not hook showRiskTips, sendNotification, dismissRiskTips, or shared Seedling APIs.
            (callers + visual).forEach { ModernHookRuntime.requireModule().deoptimize(it) }
            val reached = AtomicBoolean()
            ModernHookRegistry.installFast(key, visual, XposedInterface.Hooker { chain ->
                if (!PhoneManagerRiskCloudSettings.enabledXposed()) chain.proceed()
                else {
                    if (reached.compareAndSet(false, true)) HookLog.i(TAG, "Risk-app cloud redirected to native ordinary notification")
                    null
                }
            })
            HookLog.i(TAG, "Risk-app cloud fallback hook installed")
        }.onFailure {
            ModernHookRegistry.unhook(key)
            HookLog.w(TAG, "Risk-app cloud adapter unavailable; stock behavior retained", it)
        }
    }
}
