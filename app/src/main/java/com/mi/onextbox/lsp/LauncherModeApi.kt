package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.ModernReflect
import java.lang.reflect.Modifier

internal class LauncherModeApi(private val loader: ClassLoader?) {
    private val named = ModernReflect.findClassIfExists("com.android.launcher.mode.LauncherModeManager", loader)
    private val owner = named ?: LauncherDexResolver.type(requireNotNull(loader), "mode-manager") { dex ->
        dex.findMethod {
            matcher { usingStrings("change but idp is null"); paramTypes(); returnType = "void" }
        }.single().className
    }
    val instance = owner.declaredMethods.single {
        it.parameterCount == 0 && it.returnType == owner && Modifier.isStatic(it.modifiers)
    }.apply {
        require(returnType == owner && Modifier.isStatic(modifiers))
        isAccessible = true
    }
    val simple by lazy { predicate("isInSimpleMode", "Simple") }
    val standard by lazy { predicate("isStandardMode", "Standard") }
    val drawer by lazy { predicate("isInDrawerMode", "Drawer") }

    private fun predicate(name: String, mode: String): java.lang.reflect.Method {
        val method = if (named != null) owner.getDeclaredMethod(name) else {
            val enum = Class.forName("com.android.launcher.mode.LauncherMode", false, loader)
            val constant = enum.declaredFields.single {
                it.isEnumConstant && (it.get(null) as Enum<*>).name.equals(mode, ignoreCase = true)
            }
            LauncherDexResolver.method(requireNotNull(loader), "mode-$mode") { dex ->
                requireNotNull(dex.getFieldData(constant)).readers.single {
                    it.className == owner.name && it.returnTypeName == "boolean" && it.paramCount == 0
                }
            }
        }
        return method.apply {
            require(returnType == Boolean::class.javaPrimitiveType && !Modifier.isStatic(modifiers))
            isAccessible = true
        }
    }
}
