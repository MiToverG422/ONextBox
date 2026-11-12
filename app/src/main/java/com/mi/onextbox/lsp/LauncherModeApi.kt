package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.ModernReflect
import java.lang.reflect.Modifier

internal class LauncherModeApi(loader: ClassLoader?) {
    private val named = ModernReflect.findClassIfExists("com.android.launcher.mode.LauncherModeManager", loader)
    private val owner = named ?: ModernReflect.findClass("pp5", loader)
    val instance = owner.getDeclaredMethod(if (named != null) "getInstance" else "c").apply {
        require(returnType == owner && Modifier.isStatic(modifiers))
        isAccessible = true
    }
    val simple by lazy { predicate("isInSimpleMode", "m") }
    val standard by lazy { predicate("isStandardMode", "o") }
    val drawer by lazy { predicate("isInDrawerMode", "l") }

    private fun predicate(name: String, compact: String) =
        owner.getDeclaredMethod(if (named != null) name else compact).apply {
            require(returnType == Boolean::class.javaPrimitiveType && !Modifier.isStatic(modifiers))
            isAccessible = true
        }
}
