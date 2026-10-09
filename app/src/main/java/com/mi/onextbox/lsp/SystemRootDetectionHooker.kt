package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface

internal object SystemRootDetectionHooker {
    private const val TAG = "ONextBox-SystemRoot"

    fun hookSystemServer(loader: ClassLoader) {
        val key = "$TAG@${System.identityHashCode(loader)}:"
        runCatching {
            // A restart ensures the system service's cached status starts from the new policy.
            if (!LspConfig.isSystemRootDetectionBlockedXposed()) return
            val root = ModernReflect.findClass(SystemRootDetectionRules.ROOT_SERVICE, loader)
            val detector = ModernReflect.findClass(SystemRootDetectionRules.DETECTOR, loader)
            val callback = ModernReflect.findClass(SystemRootDetectionRules.CALLBACK, loader)
            val query = root.getDeclaredMethod("isRoot", String::class.java)
            val check = detector.getDeclaredMethod("checkDeviceRootStatus", callback)
            val notify = callback.getDeclaredMethod("notifyRootStatus", Boolean::class.javaPrimitiveType!!)
                .apply { isAccessible = true }
            require(SystemRootDetectionRules.isInstanceMethod(query, Boolean::class.javaPrimitiveType!!, String::class.java)) {
                "Unsupported RootService query signature"
            }
            require(SystemRootDetectionRules.isInstanceMethod(check, Void.TYPE, callback))
            require(callback.isInterface && notify.returnType == Void.TYPE)

            // Older releases lack this facade; probe its contract instead of an OS version code.
            val facade = ModernReflect.findClassIfExists(SystemRootDetectionRules.SERVICE, loader)
            val facadeQuery = facade?.declaredMethods?.singleOrNull {
                it.name == "isRootEnable" &&
                    SystemRootDetectionRules.isInstanceMethod(it, Boolean::class.javaPrimitiveType!!)
            }
            for (target in listOfNotNull(query, facadeQuery)) {
                ModernHookRegistry.installFast("$key${target.toGenericString()}", target, XposedInterface.Hooker { chain ->
                    SystemRootDetectionRules.query(LspConfig.isSystemRootDetectionBlockedXposed()) { chain.proceed() }
                })
            }
            ModernHookRegistry.installFast("${key}check", check, XposedInterface.Hooker { chain ->
                SystemRootDetectionRules.check(
                    enabled = LspConfig.isSystemRootDetectionBlockedXposed(),
                    reportStatus = { rooted ->
                        // RootService initialization and forced refresh both await this callback.
                        // Keep Binder authorization unchanged; only the result is replaced.
                        val receiver = chain.getArg(0)
                        if (receiver != null) runCatching { notify.invoke(receiver, rooted) }
                            .onFailure { HookLog.w(TAG, "Unable to deliver not-rooted status", it) }
                    },
                    original = { chain.proceed() },
                )
            })
            HookLog.i(TAG, "System Root hooks installed: facade=${facadeQuery != null}, callback preserved")
        }.onFailure {
            ModernHookRegistry.unhookPrefix(key)
            HookLog.w(TAG, "System Root hooks unavailable; stock behavior retained", it)
        }
    }
}
