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

    fun autoFocus(loader: ClassLoader) = LauncherDexResolver.method(loader, "recent-auto-focus") { dex ->
        requireNotNull(dex.getMethodData(focusPage(loader))).invokes.single {
            it.returnTypeName == "boolean" && it.paramTypeNames == listOf("boolean") && Modifier.isStatic(it.modifiers)
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

    fun executor(loader: ClassLoader): java.lang.reflect.Field = LauncherDexResolver.field(loader, "category-executor") { dex ->
        val owner = dex.findMethod {
            matcher { usingStrings("THREAD_POOL_EXECUTOR"); name = "<clinit>" }
        }.single { it.className.indexOf('.') < 0 }.className
        val type = Class.forName(owner, false, loader)
        val field = type.declaredFields.single {
            Modifier.isStatic(it.modifiers) && java.util.concurrent.ExecutorService::class.java.isAssignableFrom(it.type)
        }
        requireNotNull(dex.getFieldData(field))
    }
}
