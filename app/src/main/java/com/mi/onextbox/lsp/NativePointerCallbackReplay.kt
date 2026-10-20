package com.mi.onextbox.lsp

import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Resolve callback classes only; authentication and press state never cross generations. */
internal object NativePointerCallbackReplay {
    fun owners(methods: Iterable<Executable>, callbackBase: Class<*>): List<Class<*>> =
        methods.filterIsInstance<Method>().filter {
            callbackBase.isAssignableFrom(it.declaringClass) && !Modifier.isStatic(it.modifiers) &&
                it.name in setOf("onUdfpsPointerDown", "onUdfpsPointerUp") &&
                it.returnType == Void.TYPE &&
                it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType!!))
        }.map { it.declaringClass }.distinct()
}
