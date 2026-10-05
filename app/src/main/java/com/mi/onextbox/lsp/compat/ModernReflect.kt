package com.mi.onextbox.lsp.compat

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/** Reflection helpers required by ONextBox hook targets, with no legacy API dependency. */
internal object ModernReflect {
    private data class FieldKey(val targetClass: Class<*>, val fieldName: String)

    private val methodTableCache = ConcurrentHashMap<Class<*>, List<Method>>()
    private val constructorTableCache = ConcurrentHashMap<Class<*>, List<Constructor<*>>>()
    private val fieldCache = ConcurrentHashMap<FieldKey, Field>()

    fun findClassIfExists(name: String, classLoader: ClassLoader?): Class<*>? =
        runCatching { findClass(name, classLoader) }.getOrNull()

    fun findClass(name: String, classLoader: ClassLoader?): Class<*> =
        Class.forName(name, false, classLoader)

    fun getObjectField(target: Any, fieldName: String): Any? =
        findField(target.javaClass, fieldName).read(target)

    fun setObjectField(target: Any, fieldName: String, value: Any?) {
        findField(target.javaClass, fieldName).write(target, value)
    }

    fun getStaticIntField(targetClass: Class<*>, fieldName: String): Int =
        findField(targetClass, fieldName).read(null) as Int

    fun callMethod(target: Any?, methodName: String, vararg args: Any?): Any? {
        val receiver = requireNotNull(target) { "Receiver is null for $methodName" }
        val method = findBestMethod(receiver.javaClass, methodName, args, requireStatic = false)
        return method.invoke(receiver, *args)
    }

    fun callStaticMethod(targetClass: Class<*>, methodName: String, vararg args: Any?): Any? {
        val method = findBestMethod(targetClass, methodName, args, requireStatic = true)
        return method.invoke(null, *args)
    }

    fun newInstance(targetClass: Class<*>, vararg args: Any?): Any {
        val constructor = allConstructors(targetClass)
            .mapNotNull { constructor ->
                compatibilityScore(constructor.parameterTypes, args)?.let { constructor to it }
            }
            .minByOrNull { it.second }
            ?.first
            ?: throw NoSuchMethodException(
                "No compatible constructor for ${targetClass.name}(${argumentTypes(args)})"
            )
        constructor.isAccessible = true
        return constructor.newInstance(*args)
    }

    fun findAndHookMethod(
        targetClass: Class<*>,
        methodName: String,
        vararg parameterTypesAndCallback: Any?,
    ): XposedInterfaceHandle {
        val request = parseHookRequest(parameterTypesAndCallback, targetClass.classLoader)
        val method = findMethodExact(targetClass, methodName, request.parameterTypes)
        return XposedInterfaceHandle(ModernHookBridge.hookMethod(method, request.callback))
    }

    fun findAndHookMethod(
        className: String,
        classLoader: ClassLoader?,
        methodName: String,
        vararg parameterTypesAndCallback: Any?,
    ): XposedInterfaceHandle {
        val targetClass = findClass(className, classLoader)
        val request = parseHookRequest(parameterTypesAndCallback, classLoader)
        val method = findMethodExact(targetClass, methodName, request.parameterTypes)
        return XposedInterfaceHandle(ModernHookBridge.hookMethod(method, request.callback))
    }

    /** Keeps the framework handle opaque to feature code. */
    internal class XposedInterfaceHandle internal constructor(internal val value: Any)

    private data class HookRequest(
        val parameterTypes: Array<Class<*>>,
        val callback: ModernMethodHook,
    )

    private fun parseHookRequest(values: Array<out Any?>, loader: ClassLoader?): HookRequest {
        require(values.isNotEmpty()) { "Hook callback is missing" }
        val callback = values.last() as? ModernMethodHook
            ?: throw IllegalArgumentException("Last hook argument must be ModernMethodHook")
        val parameterTypes = values.dropLast(1).map { value ->
            when (value) {
                is Class<*> -> value
                is String -> findClass(value, loader)
                else -> throw IllegalArgumentException("Unsupported parameter type: $value")
            }
        }.toTypedArray()
        return HookRequest(parameterTypes, callback)
    }

    internal fun findMethodExact(
        targetClass: Class<*>,
        methodName: String,
        parameterTypes: Array<out Class<*>>,
    ): Method {
        var current: Class<*>? = targetClass
        while (current != null) {
            runCatching { current.getDeclaredMethod(methodName, *parameterTypes) }
                .getOrNull()
                ?.let { method ->
                    method.isAccessible = true
                    return method
                }
            current = current.superclass
        }
        throw NoSuchMethodException(
            "${targetClass.name}#$methodName(${parameterTypes.joinToString { it.name }})"
        )
    }

    private fun findBestMethod(
        targetClass: Class<*>,
        methodName: String,
        args: Array<out Any?>,
        requireStatic: Boolean,
    ): Method {
        val method = allMethods(targetClass)
            .asSequence()
            .filter { it.name == methodName }
            .filter { !requireStatic || Modifier.isStatic(it.modifiers) }
            .mapNotNull { candidate ->
                compatibilityScore(candidate.parameterTypes, args)?.let { candidate to it }
            }
            .minByOrNull { it.second }
            ?.first
            ?: throw NoSuchMethodException(
                "${targetClass.name}#$methodName(${argumentTypes(args)})"
            )
        method.isAccessible = true
        return method
    }

    private fun allMethods(targetClass: Class<*>): List<Method> {
        methodTableCache[targetClass]?.let { return it }
        val methods = LinkedHashMap<String, Method>()
        var current: Class<*>? = targetClass
        while (current != null) {
            current.declaredMethods.forEach { method ->
                val key = method.name + method.parameterTypes.joinToString(prefix = "(") { it.name }
                methods.putIfAbsent(key, method)
            }
            current = current.superclass
        }
        targetClass.interfaces.forEach { intf ->
            intf.methods.forEach { method ->
                val key = method.name + method.parameterTypes.joinToString(prefix = "(") { it.name }
                methods.putIfAbsent(key, method)
            }
        }
        val resolved = methods.values.toList()
        return methodTableCache.putIfAbsent(targetClass, resolved) ?: resolved
    }

    private fun allConstructors(targetClass: Class<*>): List<Constructor<*>> {
        constructorTableCache[targetClass]?.let { return it }
        val resolved = targetClass.declaredConstructors.toList()
        return constructorTableCache.putIfAbsent(targetClass, resolved) ?: resolved
    }

    private fun findField(targetClass: Class<*>, fieldName: String): Field {
        val key = FieldKey(targetClass, fieldName)
        fieldCache[key]?.let { return it }
        var current: Class<*>? = targetClass
        while (current != null) {
            runCatching { current.getDeclaredField(fieldName) }
                .getOrNull()
                ?.let { field ->
                    field.isAccessible = true
                    return fieldCache.putIfAbsent(key, field) ?: field
                }
            current = current.superclass
        }
        throw NoSuchFieldException("${targetClass.name}#$fieldName")
    }

    private fun Field.read(receiver: Any?): Any? {
        isAccessible = true
        return get(receiver)
    }

    private fun Field.write(receiver: Any?, value: Any?) {
        isAccessible = true
        set(receiver, value)
    }

    private fun compatibilityScore(
        parameterTypes: Array<Class<*>>,
        args: Array<out Any?>,
    ): Int? {
        if (parameterTypes.size != args.size) return null
        var score = 0
        parameterTypes.forEachIndexed { index, parameterType ->
            val argument = args[index]
            if (argument == null) {
                if (parameterType.isPrimitive) return null
                score += 4
                return@forEachIndexed
            }
            val argumentClass = argument.javaClass
            val boxedParameter = parameterType.boxed()
            when {
                boxedParameter == argumentClass -> Unit
                boxedParameter.isAssignableFrom(argumentClass) -> score += inheritanceDistance(argumentClass, boxedParameter)
                else -> return null
            }
        }
        return score
    }

    private fun Class<*>.boxed(): Class<*> = when (this) {
        java.lang.Boolean.TYPE -> Boolean::class.javaObjectType
        java.lang.Byte.TYPE -> Byte::class.javaObjectType
        java.lang.Character.TYPE -> Char::class.javaObjectType
        java.lang.Short.TYPE -> Short::class.javaObjectType
        java.lang.Integer.TYPE -> Int::class.javaObjectType
        java.lang.Long.TYPE -> Long::class.javaObjectType
        java.lang.Float.TYPE -> Float::class.javaObjectType
        java.lang.Double.TYPE -> Double::class.javaObjectType
        java.lang.Void.TYPE -> java.lang.Void::class.java
        else -> this
    }

    private fun inheritanceDistance(child: Class<*>, parent: Class<*>): Int {
        if (parent.isInterface) return 1
        var distance = 0
        var current: Class<*>? = child
        while (current != null && current != parent) {
            distance++
            current = current.superclass
        }
        return distance
    }

    private fun argumentTypes(args: Array<out Any?>): String =
        args.joinToString { it?.javaClass?.name ?: "null" }
}
