package com.mi.onextbox.lsp

import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Retain native classes, never task instances or a previous generation's hook callbacks. */
internal class RecentTaskHookReplay(
    private val base: Class<*>,
    private val install: (Class<*>) -> Unit,
) {
    private val installed = linkedSetOf<Class<*>>()

    @Synchronized
    fun discover(concrete: Class<*>) {
        check(base.isAssignableFrom(concrete)) { "Unrelated recent-task implementation" }
        if (concrete in installed) return
        install(concrete)
        installed += concrete
    }

    @Synchronized
    fun capture(): Array<Class<*>> = installed.toTypedArray()

    fun restore(saved: Any?, previousTargets: Collection<Executable> = emptyList()) {
        if (saved != null) {
            val classes = saved as? Array<*> ?: error("Invalid recent-task replay state")
            classes.forEach { discover(it as? Class<*> ?: error("Invalid recent-task class")) }
        }
        // Older versions did not export this replay state. Their executable owners still identify
        // already-constructed implementations; no native constructor needs to run again.
        previousTargets.filterIsInstance<Method>().filter(::isPresentationMethod)
            .map { it.declaringClass }.distinct().forEach(::discover)
    }

    private fun isPresentationMethod(method: Method): Boolean {
        if (!base.isAssignableFrom(method.declaringClass) || method.isBridge ||
            Modifier.isStatic(method.modifiers)) return false
        val signature = method.returnType == ArrayList::class.java &&
            method.parameterTypes.contentEquals(arrayOf(ArrayList::class.java)) ||
            method.returnType == List::class.java && method.parameterCount == 0
        return signature && base.declaredMethods.any {
            it.name == method.name && it.returnType == method.returnType &&
                it.parameterTypes.contentEquals(method.parameterTypes)
        }
    }
}
