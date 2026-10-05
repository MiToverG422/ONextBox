package com.mi.onextbox.lsp

import android.content.pm.FeatureInfo
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect
import java.lang.reflect.Array as ReflectArray
import java.lang.reflect.Method

/**
 * LSP equivalent of removing the CN-GMS feature declarations from system permission XML files.
 *
 * The original Magisk module removes these feature entries before system_server reads them. We
 * cannot replace mounted partitions here, so only expose the equivalent PackageManager view:
 * callers see neither CN-GMS feature once the option was enabled before system_server started.
 */
internal object GmsRegionRestrictionHooker {
    private const val TAG = "ONextBox-GmsRegion"
    private const val CLASS_SYSTEM_CONFIG = "com.android.server.SystemConfig"
    private const val CLASS_PACKAGE_MANAGER_SERVICE = "com.android.server.pm.PackageManagerService"
    private const val CLASS_IPACKAGE_MANAGER_IMPL =
        "com.android.server.pm.PackageManagerService\$IPackageManagerImpl"
    private const val METHOD_HAS_SYSTEM_FEATURE = "hasSystemFeature"
    private const val METHOD_GET_SYSTEM_AVAILABLE_FEATURES = "getSystemAvailableFeatures"
    private const val METHOD_GET_AVAILABLE_FEATURES = "getAvailableFeatures"

    /** Exact feature names removed by unlock-cn-gms; do not broaden this list. */
    private val blockedFeatures = setOf(
        "cn.google.services",
        "com.google.android.feature.services_updater",
    )

    fun hook(classLoader: ClassLoader?): Boolean {
        val enabledAtStart = runCatching {
            LspConfig.isGmsRegionRestrictionBypassEnabledXposed()
        }.getOrDefault(false)
        if (!enabledAtStart) return false

        var hookedMethods = 0
        hookedMethods += hookSystemConfig(classLoader)
        hookedMethods += hookPackageManager(classLoader)

        if (hookedMethods == 0) {
            HookLog.w(TAG, "No compatible PackageManager target found; CN-GMS bypass was not applied")
        } else {
            HookLog.i(TAG, "CN-GMS features masked through $hookedMethods system hook(s)")
        }
        return hookedMethods > 0
    }

    @Suppress("UNCHECKED_CAST")
    private fun hookSystemConfig(classLoader: ClassLoader?): Int {
        val targetClass = findClass(CLASS_SYSTEM_CONFIG, classLoader) ?: return 0
        return targetClass.declaredMethods
            .asSequence()
            .filter { method ->
                method.name == METHOD_GET_AVAILABLE_FEATURES && method.parameterCount == 0
            }
            .count { method ->
                install(method) { param ->
                    // SystemConfig normally returns an ArrayMap. Mutating that original map keeps
                    // the declared return type intact and lets PackageManager inherit the removal.
                    val map = param.result as? MutableMap<Any?, Any?> ?: return@install
                    blockedFeatures.forEach(map::remove)
                }
            }
    }

    private fun hookPackageManager(classLoader: ClassLoader?): Int {
        return listOf(CLASS_PACKAGE_MANAGER_SERVICE, CLASS_IPACKAGE_MANAGER_IMPL)
            .sumOf { className ->
                val targetClass = findClass(className, classLoader) ?: return@sumOf 0
                hookHasSystemFeature(targetClass) + hookAvailableFeatures(targetClass)
            }
    }

    private fun hookHasSystemFeature(targetClass: Class<*>): Int {
        return targetClass.declaredMethods
            .asSequence()
            .filter { method ->
                method.name == METHOD_HAS_SYSTEM_FEATURE &&
                    method.parameterTypes.firstOrNull() == String::class.java &&
                    (method.returnType == Boolean::class.javaPrimitiveType ||
                        method.returnType == Boolean::class.javaObjectType)
            }
            .count { method ->
                install(method) { param ->
                    val featureName = param.args.firstOrNull() as? String ?: return@install
                    if (featureName in blockedFeatures) {
                        param.result = false
                    }
                }
            }
    }

    private fun hookAvailableFeatures(targetClass: Class<*>): Int {
        return targetClass.declaredMethods
            .asSequence()
            .filter { method ->
                method.name == METHOD_GET_SYSTEM_AVAILABLE_FEATURES && method.parameterCount == 0
            }
            .count { method ->
                install(method) { param ->
                    param.result = filterFeatureArray(param.result)
                }
            }
    }

    private fun install(method: Method, after: (ModernMethodHook.MethodHookParam) -> Unit): Boolean {
        val key = "gms-region:${method.declaringClass.name}:${method.toGenericString()}"
        return runCatching {
            ModernHookRegistry.installCompat(
                key = key,
                executable = method,
                callback = object : ModernMethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) = after(param)
                },
            )
        }.isSuccess
    }

    private fun filterFeatureArray(value: Any?): Any? {
        if (value !is kotlin.Array<*>) return value
        val retained = value.filterNot(::isBlockedFeature)
        if (retained.size == value.size) return value

        val componentType = value.javaClass.componentType ?: return value
        val copy = ReflectArray.newInstance(componentType, retained.size)
        retained.forEachIndexed { index, feature -> ReflectArray.set(copy, index, feature) }
        return copy
    }

    private fun isBlockedFeature(value: Any?): Boolean =
        (value as? FeatureInfo)?.name in blockedFeatures

    private fun findClass(className: String, classLoader: ClassLoader?): Class<*>? {
        listOf(classLoader, null, ClassLoader.getSystemClassLoader())
            .distinct()
            .forEach { loader ->
                ModernReflect.findClassIfExists(className, loader)?.let { return it }
            }
        return null
    }
}
