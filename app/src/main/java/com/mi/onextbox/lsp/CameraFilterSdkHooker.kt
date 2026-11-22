package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

internal object CameraFilterSdkHooker {
    private const val OWNER = "com.oplus.ocs.camera.ipusdk.processunit.filter.list.FilterGroupManager"
    private const val COMMON = "com.oplus.ocs.camera.ipusdk.processunit.filter.list.CommonUtil"
    val options = MediaUnlockCatalog.byApp.getValue(MediaUnlockCatalog.App.Camera)
        .filter { it.group == MediaUnlockCatalog.Group.Filters }
    private val keys = options.flatMap { it.overrides.keys }.toSet()

    fun targets(loader: ClassLoader): List<Method> = listOf(OWNER, COMMON).flatMap { owner ->
        loader.loadClass(owner).declaredMethods.filter { method ->
            Modifier.isStatic(method.modifiers) && !Modifier.isNative(method.modifiers) &&
                method.returnType == Boolean::class.javaPrimitiveType &&
                ((method.name == "getConfigBooleanValue" && (method.parameterTypes.contentEquals(arrayOf(String::class.java)) ||
                    method.parameterTypes.toList() == listOf(String::class.java, Boolean::class.javaPrimitiveType))) ||
                    (method.name == "getConfig" && method.parameterTypes.toList() == listOf(String::class.java, Boolean::class.javaPrimitiveType)))
        }
    }.also { check(it.any { method -> method.declaringClass.name == OWNER }) }

    fun hook(loader: ClassLoader, appPath: String) {
        val tag = "ONextBox-CameraFilters"
        val prefix = "$tag@${System.identityHashCode(loader)}:sdk:"
        if (options.none { MediaUnlockSettings.enabledXposed(it.key) } && HotReloadLifecycle.restoredState("${prefix}targets") == null) return
        runCatching {
            val methods = targets(loader)
            val reached = ConcurrentHashMap.newKeySet<String>()
            methods.forEachIndexed { index, method ->
                ModernHookRegistry.installFast("$prefix$index", method, XposedInterface.Hooker { chain ->
                    val key = chain.getArg(0) as? String
                    val value = key?.takeIf { it in keys }?.let { MediaUnlockSettings.overrideValue(MediaUnlockCatalog.App.Camera, it) }
                    if (value == null) chain.proceed() else {
                        if (reached.add(key!!)) HookLog.i(tag, "Native SDK filter gate reached: $key=$value")
                        value
                    }
                })
            }
            runCatching { CameraFilterSdkGroups.install(loader, prefix, appPath) }.onFailure {
                ModernHookRegistry.unhookPrefix("${prefix}groups:")
                HookLog.w(tag, "Extended filter groups unavailable; native configuration hooks retained", it)
            }
            HostDexMetadata.methods(loader, appPath, "camera-filter-sdk-host", "native-groups-v2") { dex ->
                mapOf("callers" to CameraFilterSdkGroups.appCallers(dex, loader).toTypedArray())
            }.getValue("callers").forEach {
                runCatching { ModernHookRuntime.requireModule().deoptimize(it) }
                    .onFailure { error -> HookLog.w(tag, "Native filter SDK entry deoptimization unavailable", error) }
            }
            val sdkPath = listOf("/product/framework", "/system_ext/framework", "/system/framework")
                .map { File(it, "com.oplus.camera.unit.sdk.jar") }.firstOrNull { it.isFile && it.canRead() }?.path
            if (sdkPath != null) {
                val schema = "filter-sdk-v3:${ApkDexSession.fingerprint(sdkPath)}"
                val deoptTargets = methods
                val callers = HostDexMetadata.methods(loader, appPath, "camera-filter-sdk", schema) {
                    ApkDexSession.withSession(sdkPath) { open ->
                        val dex = open()
                        mapOf("callers" to deoptTargets.map { it.declaringClass.name }.distinct().flatMap { owner ->
                            checkNotNull(dex.getClassData(owner)).methods.filter { data ->
                                data.isMethod && runCatching { data.getMethodInstance(loader) in deoptTargets }.getOrDefault(false)
                            }.flatMap { it.callers }
                        }.filter { it.isMethod }.mapNotNull {
                            runCatching { it.getMethodInstance(loader) }.getOrNull()
                        }.distinct().toTypedArray())
                    }
                }.getValue("callers")
                callers.forEach { runCatching { ModernHookRuntime.requireModule().deoptimize(it) }
                    .onFailure { error -> HookLog.w(tag, "Native SDK filter caller deoptimization unavailable", error) } }
            }
            HotReloadLifecycle.register("${prefix}targets", capture = { methods.toTypedArray() })
            HookLog.i(tag, "Selected filter gates installed in the native SDK")
        }.onFailure {
            ModernHookRegistry.unhookPrefix(prefix)
            HookLog.w(tag, "Unsupported native SDK filter configuration; native behavior retained", it)
        }
    }
}
