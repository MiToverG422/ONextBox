package com.mi.onextbox.lsp

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface

internal object StdspInstallHooker {
    fun hook(loader: ClassLoader) {
        runCatching {
            val target = Class.forName("com.oplus.cloud.cloudscan.stdspcore.PackageVerifyReceiver", false, loader)
                .getDeclaredMethod("handleNeedsVerification", Context::class.java, Intent::class.java, Long::class.javaPrimitiveType)
            val verify = PackageManager::class.java.getMethod("verifyPendingInstall", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            check(target.returnType == Void.TYPE)
            ModernHookRegistry.installFast("StdspInstall:${System.identityHashCode(loader)}", target, XposedInterface.Hooker { chain ->
                val context = chain.args[0] as? Context
                val intent = chain.args[1] as? Intent
                val handled = runCatching {
                    val id = intent?.getIntExtra("android.content.pm.extra.VERIFICATION_ID", -1) ?: -1
                    if (context == null || !StdspInstallRules.mayAllow(
                            InstallerCleanupSettings.active(InstallerCleanupSettings.STDSP_ALLOW_INSTALL),
                            context.packageName, intent?.action, id, intent?.data != null,
                        )) false
                    else if (!InstallerCleanupRules.maySkip(
                            Settings.Global.getInt(context.contentResolver, "children_mode_on", 0) == 1,
                            Settings.Secure.getInt(context.contentResolver, "minors_mode", 0) == 1 &&
                                Settings.Secure.getInt(context.contentResolver, "minors_mode_enabled", 0) == 1,
                        )) false
                    else {
                        verify.invoke(context.packageManager, id, 1)
                        HookLog.i("StdspInstall", "Installation verification released by module preference")
                        true
                    }
                }.getOrElse {
                    HookLog.w("StdspInstall", "Release failed; retaining native verification", it)
                    false
                }
                if (handled) null else chain.proceed()
            })
            ModernHookRuntime.requireModule().deoptimize(target)
            HookLog.i("StdspInstall", "Installation verification hook installed")
        }.onFailure { HookLog.w("StdspInstall", "Unsupported verification entry; native behavior retained", it) }
    }
}

internal object StdspInstallRules {
    fun mayAllow(enabled: Boolean, host: String?, action: String?, verificationId: Int, hasData: Boolean) =
        enabled && host == "com.oplus.stdsp" && action == "android.intent.action.PACKAGE_NEEDS_VERIFICATION" &&
            verificationId >= 0 && hasData
}
