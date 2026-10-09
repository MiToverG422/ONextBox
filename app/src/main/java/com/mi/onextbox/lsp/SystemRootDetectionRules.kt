package com.mi.onextbox.lsp

import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object SystemRootDetectionRules {
    private const val HEIMDALL = "com.android.server.oplus.heimdall"
    const val SERVICE = "$HEIMDALL.HeimdallService"
    const val ROOT_SERVICE = "$HEIMDALL.service.RootService"
    const val DETECTOR = "$HEIMDALL.root.RootDetector"
    const val CALLBACK = "$HEIMDALL.root.ICheckRootCallback"

    fun isInstanceMethod(method: Method, returnType: Class<*>, vararg parameters: Class<*>): Boolean =
        !Modifier.isStatic(method.modifiers) && !Modifier.isAbstract(method.modifiers) &&
            method.returnType == returnType && method.parameterTypes.contentEquals(parameters)

    inline fun query(enabled: Boolean, original: () -> Any?): Any? =
        if (enabled) false else original()

    inline fun check(enabled: Boolean, reportStatus: (Boolean) -> Unit, original: () -> Any?): Any? {
        if (!enabled) return original()
        // Preserve completion semantics even though the actual probes are skipped.
        reportStatus(false)
        return null
    }
}
