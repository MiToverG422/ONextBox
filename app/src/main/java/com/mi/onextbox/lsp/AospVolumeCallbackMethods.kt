package com.mi.onextbox.lsp

import java.lang.reflect.Method

/** OEM versions add flags to the callback while the AOSP subscriber implements only State. */
internal object AospVolumeCallbackMethods {
    fun bridgeTargets(callbacks: Class<*>, state: Class<*>, producer: Class<*>, dispatcher: Class<*>): List<Method> =
        dispatcher.declaredMethods.filter { method ->
            val args = method.parameterTypes
            method.name == "onStateChanged" && method.returnType == Void.TYPE &&
                args.firstOrNull() == state &&
                (args.drop(1) == listOf(Int::class.javaPrimitiveType) ||
                    args.drop(1) == listOf(Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)) &&
                runCatching { producer.getMethod(method.name, *args).declaringClass == callbacks }.getOrDefault(false)
        }.onEach { it.isAccessible = true }
}
