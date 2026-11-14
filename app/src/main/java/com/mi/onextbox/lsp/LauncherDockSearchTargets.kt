package com.mi.onextbox.lsp

import android.content.Context
import android.database.ContentObserver
import android.view.animation.Interpolator
import java.lang.reflect.Modifier

internal class LauncherDockSearchTargets(val loader: ClassLoader) {
    val domesticProbe = LauncherDexResolver.method(loader, "search-domestic-provider") { dex ->
        dex.findMethod {
            matcher { usingStrings("readSupportBottomSearchFromBrowser: mBrowserSupportBottomSearch: ")
                paramTypes(Context::class.java.name); returnType = "boolean" }
        }.single()
    }
    val domestic = domesticProbe.declaringClass
    val exportProbe = LauncherDexResolver.method(loader, "search-google-provider") { dex ->
        dex.findMethod {
            matcher { usingStrings("updateSupportQsbWidgetFlag: mSupportQsbWidgetFlag=")
                paramTypes(Context::class.java.name); returnType = "void" }
        }.single()
    }
    val export = exportProbe.declaringClass
    val base = domestic.superclass.also { require(it == export.superclass && it != Any::class.java) }
    val manager = LauncherDexResolver.type(loader, "search-manager") { dex ->
        dex.findMethod {
            matcher { usingStrings("BottomSearchManager"); paramTypes(); returnType = "java.lang.String" }
        }.single { it.name == "getLogTag" }.className
    }
    val proxy = manager.declaredFields.single { Modifier.isStatic(it.modifiers) && it.type == base }
        .apply { require(!Modifier.isFinal(modifiers)); isAccessible = true }
    val saveSwitch = LauncherDexResolver.method(loader, "search-save-switch") { dex ->
        dex.findMethod {
            matcher { usingStrings("putInt LAUNCHER_SEARCH_SWITCH value: ")
                paramTypes(Context::class.java.name, "int", "boolean"); returnType = "void" }
        }.single()
    }
    val utils = saveSwitch.declaringClass
    val region = LauncherDexResolver.field(loader, "search-region") { dex ->
        requireNotNull(dex.getMethodData(saveSwitch)).usingFields.map { it.field }.distinctBy { it.descriptor }.single {
            it.typeName == "boolean" && Modifier.isStatic(it.modifiers) && it.className != utils.name
        }
    }
    val domesticSchedule = LauncherDexResolver.method(loader, "search-domestic-schedule") { dex ->
        dex.findMethod {
            matcher { declaredClass = domestic.name; usingStrings("interruptAddOrDelete: rus force disabled, interrupt=true")
                paramTypes("com.android.launcher3.Launcher", "boolean"); returnType = "void" }
        }.single()
    }
    val jumpSettings = LauncherDexResolver.method(loader, "search-google-settings") { dex ->
        dex.findMethod { matcher { usingStrings("jumpToTaskBarSearchPage: error="); returnType = "void" } }.single()
    }
    val nativeListener = LauncherDexResolver.type(loader, "search-settings-listener") { dex ->
        requireNotNull(dex.getMethodData(jumpSettings)).callers.single {
            it.returnTypeName == "boolean" && it.paramTypeNames == listOf("androidx.preference.Preference") &&
                it.usingFields.any { reference -> reference.field.className == "com.android.launcher.settings.c" }
        }.className
    }

    fun support(type: Class<*>) = type.declaredMethods.single {
        !Modifier.isStatic(it.modifiers) && it.returnType == java.lang.Boolean.TYPE &&
            it.parameterTypes.contentEquals(arrayOf(Context::class.java))
    }.apply { isAccessible = true }

    fun cached(international: Boolean) = (if (international) export else domestic).declaredFields.single {
        !Modifier.isStatic(it.modifiers) && it.type == Integer.TYPE
    }.apply { isAccessible = true }

    fun mutex(type: Class<*>) = type.declaredFields.single {
        !Modifier.isStatic(it.modifiers) && it.type.name.startsWith("kotlinx.coroutines.sync.")
    }.apply { isAccessible = true }

    fun domesticInterpolator() = domestic.declaredFields.single {
        !Modifier.isStatic(it.modifiers) && Interpolator::class.java.isAssignableFrom(it.type)
    }.apply { isAccessible = true }

    fun exportObserver() = export.declaredFields.single {
        Modifier.isStatic(it.modifiers) && ContentObserver::class.java.isAssignableFrom(it.type)
    }.apply { isAccessible = true }

    val dispatch by lazy { LauncherDexResolver.method(loader, "search-coroutine-dispatch") { dex ->
        requireNotNull(dex.getMethodData(domesticSchedule)).invokes.single {
            it.paramCount == 5 && it.paramTypeNames.takeLast(3) ==
                listOf("kotlinx.coroutines.CoroutineStart", "kotlin.jvm.functions.Function2", "int")
        }
    } }
    val coroutineScope by lazy { LauncherDexResolver.method(loader, "search-lifecycle-scope") { dex ->
        requireNotNull(dex.getMethodData(domesticSchedule)).invokes.single {
            it.paramCount == 1 && it.className.startsWith("androidx.lifecycle.") &&
                dispatch.parameterTypes[0].isAssignableFrom(Class.forName(it.returnTypeName, false, loader))
        }
    } }
}
