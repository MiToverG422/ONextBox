package com.mi.onextbox.lsp

import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Resolves members without initializing host singletons before Application.onCreate. */
internal class GameAssistantTemperatureTargets(model: Class<*>, private val getter: Method) {
    private val instance = model.getField("INSTANCE").apply {
        require(Modifier.isStatic(modifiers) && type == model)
        isAccessible = true
    }
    private val status = model.getMethod("getStatusList").apply {
        require(List::class.java.isAssignableFrom(returnType))
        isAccessible = true
    }
    private val battery = model.getMethod("getBatteryStatus").apply {
        require(returnType == Array<String>::class.java)
        isAccessible = true
    }
    private val temperature = model.getMethod("getSmallTemperatureStatus").apply {
        require(returnType == Array<String>::class.java)
        isAccessible = true
    }
    private val receiver: () -> Any?

    init {
        require(getter.parameterCount == 0 && getter.returnType == Float::class.javaPrimitiveType)
        getter.isAccessible = true
        val helper = getter.declaringClass
        receiver = if (Modifier.isStatic(getter.modifiers)) {
            { null }
        } else {
            val singleton = helper.declaredFields.singleOrNull {
                Modifier.isStatic(it.modifiers) && it.type == helper
            }
            if (singleton != null) {
                singleton.isAccessible = true
                ({ singleton.get(null) })
            } else {
                val factories = helper.declaredFields.filter { Modifier.isStatic(it.modifiers) }
                    .flatMap { field ->
                        field.type.declaredMethods.filter {
                            Modifier.isPublic(it.modifiers) && !Modifier.isStatic(it.modifiers) &&
                                !it.isSynthetic && it.parameterCount == 0 && it.returnType == helper
                        }.map { field to it }
                    }
                val (companion, factory) = factories.single()
                companion.isAccessible = true
                factory.isAccessible = true
                ({ factory.invoke(companion.get(null)) })
            }
        }
    }

    data class Labels(val index: Int, val battery: List<String>, val temperature: List<String>)

    // Called only after the native panel initialization/update has returned successfully.
    fun readLabels(): Labels? {
        val owner = instance.get(null)
        val index = (status.invoke(owner) as List<*>).indexOf("battery")
        if (index < 0) return null
        val original = (battery.invoke(owner) as Array<*>).map { it as String }
        val thermal = (temperature.invoke(owner) as Array<*>).map { it as String }
        require(original.size >= 2 && thermal.size >= 2)
        return Labels(index, original, thermal)
    }

    fun readCelsius(): Float = getter.invoke(receiver()) as Float
}
