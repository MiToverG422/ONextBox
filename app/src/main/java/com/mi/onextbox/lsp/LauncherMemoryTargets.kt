package com.mi.onextbox.lsp

import android.content.Context
import android.view.View
import com.mi.onextbox.lsp.compat.ModernReflect
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.zip.ZipFile

internal class LauncherMemoryTargets(val values: Array<*>) {
    val constructor = values[0] as Constructor<*>
    val refresh = values[1] as Method
    val allowed = values[2] as Field
    val detail = values[3] as Field
    val gates = (values[4] as Array<*>).filterIsInstance<Method>()
    val singleton = values[5] as Method
    val singletonField = values[6] as Field
    val format = values[7] as Method
    val updatePanel = values[8] as Method
    val inflatePanel = values[9] as Method
    val callers = (values[10] as Array<*>).filterIsInstance<Method>()
    val bindPanel = values[11] as Method
    val panelType: Class<*> get() = updatePanel.declaringClass

    init {
        values.filterIsInstance<java.lang.reflect.AccessibleObject>().forEach { it.isAccessible = true }
        gates.forEach { it.isAccessible = true }
    }

    companion object {
        private const val ALLOW_KEY = "allow_memory_info_display"
        private const val UPDATE_ANCHOR = "updateMemoDisplaySwitch(), e="

        fun resolve(loader: ClassLoader, apkPath: String): LauncherMemoryTargets {
            if (SystemAdaptationDetector.current() == SystemAdaptationVersion.ColorOs16) return resolveColorOs16(loader)
            System.loadLibrary("dexkit")
            val bytes = ZipFile(apkPath).use { apk ->
                apk.entries().asSequence().filter { it.name.matches(Regex("classes\\d*\\.dex")) }
                    .map { entry -> apk.getInputStream(entry).use { it.readBytes() } }
                    .first { dex ->
                        val strings = String(dex, Charsets.ISO_8859_1)
                        strings.contains(ALLOW_KEY) && strings.contains(UPDATE_ANCHOR)
                    }
            }
            return DexKitBridge.create(arrayOf(bytes)).use { dex ->
                dex.setThreadNum(1)
                val init = dex.findMethod {
                    matcher { usingStrings(ALLOW_KEY); paramTypes(Context::class.java.name); returnType = "void" }
                }.single { it.isConstructor }
                val refresh = dex.findMethod {
                    matcher { declaredClass = init.className; usingStrings(UPDATE_ANCHOR); paramTypes(); returnType = "void" }
                }.single()
                val booleanFields = refresh.usingFields.map { it.field }.distinctBy { it.descriptor }
                    .filter { it.typeName == "boolean" && !Modifier.isStatic(it.modifiers) }
                val allowed = booleanFields.single { Modifier.isFinal(it.modifiers) }
                val detail = booleanFields.single { !Modifier.isFinal(it.modifiers) }
                fun getter(field: FieldData): MethodData = field.readers.single {
                    it.className == init.className && it.returnTypeName == "boolean" &&
                        it.paramTypeNames.isEmpty() && !Modifier.isStatic(it.modifiers)
                }
                val gates = listOf(getter(allowed), getter(detail))
                val type = ModernReflect.findClass(init.className, loader)
                val singleton = type.declaredMethods.single {
                    Modifier.isStatic(it.modifiers) && it.returnType == type &&
                        it.parameterTypes.contentEquals(arrayOf(Context::class.java))
                }
                val singletonField = type.declaredFields.single {
                    Modifier.isStatic(it.modifiers) && it.type == type
                }
                val format = type.declaredMethods.single {
                    !Modifier.isStatic(it.modifiers) && it.parameterTypes.contentEquals(arrayOf(Boolean::class.javaPrimitiveType)) &&
                        it.returnType.declaredConstructors.any { constructor ->
                            constructor.parameterTypes.contentEquals(arrayOf(String::class.java, Int::class.javaPrimitiveType))
                        }
                }
                val panel = ModernReflect.findClass("com.oplus.quickstep.views.OplusClearAllPanelView", loader)
                check(View::class.java.isAssignableFrom(panel))
                val update = gates[1].callers.single {
                    it.className == panel.name && it.returnTypeName == "void" && it.paramTypeNames == listOf("boolean")
                }
                val bind = panel.declaredMethods.single {
                    Modifier.isStatic(it.modifiers) && it.returnType == Void.TYPE && it.parameterCount == 4 &&
                        it.parameterTypes.toSet() == setOf(panel, type, format.returnType, Boolean::class.javaPrimitiveType)
                }
                val direct = gates.flatMap { it.callers }
                val callers = (direct + direct.flatMap { it.callers } + listOf(refresh, update))
                    .filter { it.isMethod }.distinctBy { it.descriptor }.map { it.getMethodInstance(loader) }
                LauncherMemoryTargets(arrayOf(
                    init.getConstructorInstance(loader), refresh.getMethodInstance(loader),
                    allowed.getFieldInstance(loader), detail.getFieldInstance(loader),
                    gates.map { it.getMethodInstance(loader) }.toTypedArray(), singleton, singletonField, format,
                    update.getMethodInstance(loader), panel.getDeclaredMethod("onFinishInflate"), callers.toTypedArray(), bind,
                ))
            }
        }

        private fun resolveColorOs16(loader: ClassLoader): LauncherMemoryTargets {
            val type = ModernReflect.findClass("com.oplus.quickstep.memory.MemoryInfoManager", loader)
            val panel = ModernReflect.findClass("com.oplus.quickstep.views.OplusClearAllPanelView", loader)
            val wrapper = ModernReflect.findClass("${type.name}\$MemoryInfoWrapper", loader)
            check(View::class.java.isAssignableFrom(panel))
            wrapper.getDeclaredConstructor(String::class.java, Int::class.javaPrimitiveType)
            val gates = listOf("isAllowMemoryInfoDisplay", "needMemoryDetail").map { name ->
                type.getDeclaredMethod(name).also { require(it.returnType == Boolean::class.javaPrimitiveType) }
            }
            val format = type.getDeclaredMethod("getMemoryInfo", Boolean::class.javaPrimitiveType)
            require(format.returnType == wrapper)
            val refresh = type.getDeclaredMethod("updateMemoDisplaySwitch")
            val update = panel.getDeclaredMethod("updateMeminfo", Boolean::class.javaPrimitiveType)
            val bind = panel.getDeclaredMethod("b", panel, wrapper, Boolean::class.javaPrimitiveType, type)
            require(listOf(refresh, update, bind).all { it.returnType == Void.TYPE })
            // C16's constructor delegates the capability test, and its allowance field is mutable.
            // Keep the same read/force/restore lifecycle instead of matching C17's final-field shape.
            return LauncherMemoryTargets(arrayOf(
                type.getDeclaredConstructor(Context::class.java), refresh,
                type.getDeclaredField("mAllowMemoryInfoDisplay"), type.getDeclaredField("mNeedMemoryDetail"),
                gates.toTypedArray(), type.getDeclaredMethod("getInstance", Context::class.java),
                type.getDeclaredField("sInstance"), format, update, panel.getDeclaredMethod("onFinishInflate"),
                (type.declaredMethods.toList() + panel.declaredMethods).toTypedArray(), bind,
            ))
        }
    }
}
