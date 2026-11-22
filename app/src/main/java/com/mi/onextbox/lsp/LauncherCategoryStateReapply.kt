package com.mi.onextbox.lsp

internal class LauncherCategoryStateReapply(owner: Class<*>) {
    private val boolean = Boolean::class.javaPrimitiveType!!
    private val int = Int::class.javaPrimitiveType!!
    private val methods = owner.declaredMethods.filter { it.name == "reapplyState" && it.returnType == Void.TYPE }
    val method = methods.firstOrNull { it.parameterCount == 0 }
        ?: methods.firstOrNull { it.parameterTypes.contentEquals(arrayOf(int, boolean)) }
        ?: methods.firstOrNull { it.parameterTypes.contentEquals(arrayOf(boolean)) }
        ?: methods.single { it.parameterTypes.contentEquals(arrayOf(boolean, int)) }
    private val defaults = Array<Any>(method.parameterCount) { if (method.parameterTypes[it] == boolean) false else 0 }

    fun invoke(manager: Any) {
        method.invoke(manager, *defaults)
    }
}
