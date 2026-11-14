package com.mi.onextbox.lsp

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object EuiccProfileLimitTargets {
    class Download(val values: Array<*>) {
        val check = values[0] as Method
        val callers = (values[1] as Array<*>).filterIsInstance<Method>()
    }

    class AddEntry(val values: Array<*>) {
        val click = values[0] as Method
        val controller = values[1] as Field
        val profileCount = values[2] as Field
        val preferenceKey = click.parameterTypes.single().getMethod("getKey")
        val context = click.declaringClass.getMethod("getContext")
    }

    fun download(loader: ClassLoader, apkPath: String): Download = ApkDexSession.withSession(apkPath) { open ->
        val dex = open()
        val check = dex.findMethod { matcher {
            usingStrings("profileLimitReached Profile count now: ")
            returnType = "boolean"; paramTypes()
        } }.single { !Modifier.isStatic(it.modifiers) }
        val callers = check.callers.filter { it.isMethod && it.className == check.className }
            .map { it.getMethodInstance(loader).apply { isAccessible = true } }.toTypedArray()
        Download(arrayOf(check.getMethodInstance(loader).apply { isAccessible = true }, callers))
    }

    fun addEntry(loader: ClassLoader, apkPath: String): AddEntry = ApkDexSession.withSession(apkPath) { open ->
        val click = open().findMethod { matcher {
            usingStrings("add_esim_profile", "eSIM count exceeds maximum limit")
            returnType = "boolean"; paramTypes("androidx.preference.Preference")
        } }.single { !Modifier.isStatic(it.modifiers) }
        val fields = click.usingFields.map { it.field }.distinctBy { it.descriptor }
        val controller = fields.single {
            it.className == click.className && !Modifier.isStatic(it.modifiers) &&
                it.typeName == "com.android.phone.esim.settings.EsimSettingsControl"
        }
        val count = fields.single {
            it.className == controller.typeName && it.typeName == "int" && !Modifier.isStatic(it.modifiers)
        }
        AddEntry(arrayOf<Any>(
            click.getMethodInstance(loader).apply { isAccessible = true },
            controller.getFieldInstance(loader).apply { isAccessible = true },
            count.getFieldInstance(loader).apply { isAccessible = true },
        ))
    }
}
