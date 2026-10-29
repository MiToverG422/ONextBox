package com.mi.onextbox.lsp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.view.View
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object CouiDividerTargets {
    data class Target(val method: Method, val itemView: Field? = null)

    fun resolve(couiRecycler: Class<*>, recycler: Class<*>): List<Target> =
        couiRecycler.declaredClasses.flatMap { resolveDecoration(it, recycler) }.distinctBy { it.method }

    fun isDecoration(type: Class<*>, recycler: Class<*>): Boolean =
        type.declaredMethods.any { method ->
            val args = method.parameterTypes
            method.returnType == Void.TYPE && args.size == 4 &&
                args[0] == android.graphics.Rect::class.java && args[1] == View::class.java && args[2] == recycler
        } || type.declaredMethods.any { method ->
            val args = method.parameterTypes
            method.returnType == Void.TYPE && args.size in 2..3 && args[0] == Canvas::class.java &&
                args[1] == recycler && (args.size == 2 || args[2].name.startsWith("${recycler.name}\$") ||
                    args[2].packageName == recycler.packageName)
        }

    fun resolvePreference(fragment: Class<*>, recycler: Class<*>): List<Target> =
        (fragment.declaredClasses.toList() + fragment.declaredFields.map { it.type }).distinct().flatMap { type ->
            val fields = type.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }
            if (type.superclass?.let { isDecoration(it, recycler) } != true ||
                fields.count { it.type == Drawable::class.java } != 1 ||
                fields.any { it.type == Paint::class.java } ||
                fields.none { it.type == Int::class.javaPrimitiveType } ||
                fields.none { it.type == Boolean::class.javaPrimitiveType }) emptyList()
            else drawingMethods(type, recycler)
        }

    fun resolveDecoration(candidate: Class<*>, recycler: Class<*>): List<Target> {
        val hierarchy = generateSequence(candidate) { it.superclass }.toList()
        val base = hierarchy.firstOrNull { type ->
            val fields = type.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }
            val parent = type.superclass
            val mergedPreference = fields.count { it.type.name == "androidx.preference.PreferenceScreen" } == 1 &&
                fields.count { it.type == IntArray::class.java } == 2
            parent?.let { isDecoration(it, recycler) } == true &&
                fields.count { it.type == Paint::class.java } == 1 &&
                (fields.count { it.type == Drawable::class.java } == 1 ||
                    (
                        fields.count { it.type == Int::class.javaPrimitiveType } >= 5 &&
                        fields.all { it.type == Paint::class.java || it.type == Int::class.javaPrimitiveType ||
                            (mergedPreference && (it.type == IntArray::class.java || it.type.name == "androidx.preference.PreferenceScreen")) })) &&
                fields.count { it.type == Int::class.javaPrimitiveType } >= 3 &&
                type.declaredConstructors.any {
                    val args = it.parameterTypes
                    args.contentEquals(arrayOf(Context::class.java)) ||
                        (mergedPreference && args.size == 2 && args[0] == Context::class.java && args[1].name == "androidx.preference.PreferenceScreen")
                }
        } ?: return emptyList()
        return hierarchy.takeWhile { it != base.superclass }.flatMap { drawingMethods(it, recycler) }
    }

    private fun drawingMethods(type: Class<*>, recycler: Class<*>): List<Target> {
        val methods = type.declaredMethods.filter {
            it.returnType == Void.TYPE && !Modifier.isStatic(it.modifiers) && !Modifier.isAbstract(it.modifiers)
        }
        val draw = methods.filter { method ->
            val args = method.parameterTypes
            args.size in 2..3 && args[0] == Canvas::class.java && args[1] == recycler &&
                generateSequence(type.superclass) { it.superclass }.any { parent -> parent.declaredMethods.any {
                    it.name == method.name && it.parameterTypes.contentEquals(args) && it.returnType == Void.TYPE
                } }
        }

        return draw.map(::Target) + methods.mapNotNull { method ->
            val args = method.parameterTypes
            if (args.size != 2 || args[0] != Canvas::class.java || args[1] == recycler ||
                !(args[1].name.startsWith("${recycler.name}\$") ||
                    args[1].packageName == recycler.packageName)) return@mapNotNull null
            val views = args[1].fields.filter {
                it.type == View::class.java && !Modifier.isStatic(it.modifiers) && Modifier.isFinal(it.modifiers)
            }
            val itemView = views.singleOrNull { it.name == "itemView" } ?: views.singleOrNull()
                ?: return@mapNotNull null
            Target(method, itemView)
        }
    }
}
