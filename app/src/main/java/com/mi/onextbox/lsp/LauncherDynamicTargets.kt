package com.mi.onextbox.lsp

import android.content.Context
import android.graphics.drawable.Drawable
import com.mi.onextbox.lsp.compat.ModernReflect
import java.lang.reflect.Modifier

internal object LauncherDynamicTargets {
    fun largeDisplay(loader: ClassLoader): java.lang.reflect.Method {
        ModernReflect.findClassIfExists("com.android.common.util.ScreenUtils", loader)?.let {
            return it.getDeclaredMethod("hasLargeDisplayFeatures")
        }
        return LauncherDexResolver.method(loader, "large-display") { dex ->
            val owner = dex.findMethod {
                matcher { usingStrings("ScreenUtils#setCurrentDisplayRotation rotation="); paramTypes("int"); returnType = "void" }
            }.single().className
            val hotseat = loader.loadClass("com.android.launcher3.OplusHotseat").getDeclaredMethod("setDockerBackground")
            requireNotNull(dex.getMethodData(hotseat)).invokes.single {
                it.className == owner && it.returnTypeName == "boolean" && it.paramCount == 0
            }
        }
    }

    fun focusPage(loader: ClassLoader) = LauncherDexResolver.method(loader, "recent-focus-page") { dex ->
        dex.findMethod {
            matcher { usingStrings("getToRecentsFocusPage: "); paramTypes(); returnType = "int" }
        }.single()
    }

    fun autoFocusGates(loader: ClassLoader): List<java.lang.reflect.Method> {
        val featureUtils = ModernReflect.findClassIfExists("com.android.common.util.AppFeatureUtils", loader)
        val named = featureUtils?.declaredMethods.orEmpty().filter {
            it.name == "isSupportAutoFocusToNextPageInOverviewState" && Modifier.isStatic(it.modifiers) &&
                it.returnType == Boolean::class.javaPrimitiveType &&
                (it.parameterCount == 0 || it.parameterTypes.contentEquals(arrayOf(Boolean::class.javaPrimitiveType)))
        }
        return named.ifEmpty {
            listOf(LauncherDexResolver.method(loader, "recent-auto-focus") { dex ->
                requireNotNull(dex.getMethodData(focusPage(loader))).invokes.single {
                    it.returnTypeName == "boolean" && it.paramTypeNames == listOf("boolean") && Modifier.isStatic(it.modifiers)
                }
            })
        }
    }

    fun workFlag(loader: ClassLoader) = LauncherDexResolver.method(loader, "icon-work-flag") { dex ->
        dex.findMethod {
            matcher { name = "setWorkFlag"; paramTypes("boolean", "int"); returnType = "void" }
        }.single { candidate ->
            !Modifier.isAbstract(candidate.modifiers) &&
                Drawable::class.java.isAssignableFrom(candidate.getMethodInstance(loader).declaringClass)
        }
    }

    fun assets(loader: ClassLoader) = LauncherDexResolver.type(loader, "category-assets") { dex ->
        dex.findMethod {
            matcher { usingStrings("launcher_initAssetData"); paramTypes(Context::class.java.name); returnType = "void" }
        }.single { it.isConstructor }.className
    }

    fun updateAssets(loader: ClassLoader) = LauncherDexResolver.method(loader, "category-assets-update") { dex ->
        dex.findMethod {
            matcher { declaredClass = assets(loader).name; usingStrings(""); paramTypes("java.util.ArrayList"); returnType = "void" }
        }.single { !Modifier.isStatic(it.modifiers) }
    }

    fun executor(loader: ClassLoader): java.lang.reflect.Field {
        val legacy = ModernReflect.findClassIfExists("ej.a", loader)?.declaredFields?.singleOrNull {
            it.name == "a" && Modifier.isStatic(it.modifiers) &&
                java.util.concurrent.ExecutorService::class.java.isAssignableFrom(it.type)
        }
        if (legacy != null) return legacy.apply { isAccessible = true }
        return LauncherDexResolver.field(loader, "category-executor-v2") { dex ->
            val initializer = dex.findMethod {
                matcher { usingStrings("THREAD_POOL_EXECUTOR"); name = "<clinit>" }
            }.single { it.className.indexOf('.') < 0 }
            val executors = initializer.usingFields.map { it.field }.distinctBy { it.descriptor }.filter {
                it.className == initializer.className && Modifier.isStatic(it.modifiers) &&
                    java.util.concurrent.ExecutorService::class.java.isAssignableFrom(it.getFieldInstance(loader).type)
            }
            executors.singleOrNull { it.typeName == "com.oplus.dispatch.OCDExecutorService" } ?: executors.single()
        }
    }
}
