package com.mi.onextbox.lsp

import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.View
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object KeyboardRenderTargets {
    fun background(loader: ClassLoader, apkPath: String): Method = ApkDexSession.withSession(apkPath) { open ->
        open().findMethod { matcher {
            usingStrings("bgKeyNormalBlur", "bgKeyNormal")
            returnType = "android.graphics.drawable.Drawable"
            paramCount = 2
        } }.mapNotNull { data -> runCatching {
            data.getMethodInstance(loader).takeIf { method ->
                View::class.java.isAssignableFrom(method.declaringClass) &&
                    method.parameterTypes[1] == Boolean::class.javaPrimitiveType &&
                    method.declaringClass.declaredMethods.any {
                        it.parameterTypes.contentEquals(arrayOf(Canvas::class.java, method.parameterTypes[0])) &&
                            it.returnType == Void.TYPE
                    }
            }
        }.getOrNull() }.single().apply { isAccessible = true }
    }

    fun effects(loader: ClassLoader, apkPath: String, renderer: Class<*>): List<Method> =
        ApkDexSession.withSession(apkPath) { open ->
            open().findMethod { matcher {
                returnType = "void"
                paramTypes("android.graphics.Canvas", "java.util.List", "boolean")
            } }.filter { data -> data.callers.any { it.className == renderer.name } }
                .map { it.getMethodInstance(loader) }.filter { Modifier.isStatic(it.modifiers) }
                .distinct().onEach { it.isAccessible = true }
        }

    fun labelMaps(loader: ClassLoader, apkPath: String, renderer: Class<*>): List<Map<*, *>> =
        ApkDexSession.withSession(apkPath) { open ->
            open().findMethod { matcher { declaredClass = renderer.name } }
                .filter { "android.graphics.Canvas" in it.paramTypeNames }
                .flatMap { it.usingFields }.map { it.field }.distinctBy { it.descriptor }
                .filter { Modifier.isStatic(it.modifiers) }
                .mapNotNull { data -> runCatching {
                    val field = data.getFieldInstance(loader)
                    if (!Map::class.java.isAssignableFrom(field.type)) null
                    else field.apply { isAccessible = true }.get(null) as? Map<*, *>
                }.getOrNull() }
        }

    fun isEdge(method: Method) = method.declaringClass.declaredFields.any {
        Modifier.isStatic(it.modifiers) && Drawable::class.java.isAssignableFrom(it.type) &&
            it.type.methods.any { candidate -> candidate.name == "setEdgeEnable" }
    }
}
