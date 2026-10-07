package com.mi.onextbox.lsp

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Google hotword permission-check compatibility, without persistent grants or package changes, excluding MANAGE_ASSISTANT_AUDIO. */
internal object OkGoogleHotwordCompatibilityHooker {
    private const val TAG = "ONextBox-OkGoogle"
    private const val GOOGLE_APP_PACKAGE = "com.google.android.googlequicksearchbox"
    private const val PERMISSION_GRANTED = 0

    private val hotwordPermissions = setOf(
        "android.permission.MANAGE_VOICE_KEYPHRASES",
        "android.permission.MANAGE_SOUND_TRIGGER",
        "android.permission.CAPTURE_AUDIO_HOTWORD",
        "android.permission.MANAGE_HOTWORD_DETECTION",
    )

    private val permissionManagerClasses = listOf(
        "com.android.server.pm.permission.PermissionManagerService",
        "com.android.server.pm.permission.PermissionManagerServiceImpl",
        "com.android.server.pm.permission.PermissionManagerService\$PermissionCheckerService",
    )
    private val installedClasses = ConcurrentHashMap.newKeySet<String>()
    private val googleUidCache = ConcurrentHashMap<Int, Boolean>()
    private val loggedFirstPackageBypass = AtomicBoolean(false)
    private val loggedFirstUidBypass = AtomicBoolean(false)
    private val loggedIdentityPresentation = AtomicBoolean(false)

    @Volatile
    private var systemContext: Context? = null

    /** Called in system_server. The preference is intentionally latched until the next reboot. */
    fun hookSystemServer(classLoader: ClassLoader?) {
        if (!isEnabledAtProcessStart()) return

        var installedChecks = 0
        permissionManagerClasses.forEach { className ->
            val targetClass = ModernReflect.findClassIfExists(className, classLoader) ?: return@forEach
            val classKey = "${targetClass.name}@${System.identityHashCode(targetClass.classLoader)}"
            if (!installedClasses.add(classKey)) return@forEach

            targetClass.declaredMethods
                .asSequence()
                .filter(::isPermissionCheckMethod)
                .forEach { method ->
                    if (installPermissionCheckHook(classKey, method)) installedChecks++
                }
        }

        if (installedChecks > 0) {
            HookLog.i(
                TAG,
                "Installed $installedChecks precise hotword permission checks; " +
                    "MANAGE_ASSISTANT_AUDIO remains blocked",
            )
        } else {
            HookLog.w(TAG, "No supported permission-manager check was found")
        }
    }

/** Google-process view of its own FLAG_SYSTEM bit, without changing package-manager state. */
    fun hookGoogleApp(classLoader: ClassLoader?) {
        if (!isEnabledAtProcessStart()) return

        val packageManagerClass = ModernReflect.findClassIfExists(
            "android.app.ApplicationPackageManager",
            classLoader,
        ) ?: return
        val classKey = "${packageManagerClass.name}@${System.identityHashCode(packageManagerClass.classLoader)}"
        if (!installedClasses.add(classKey)) return

        var installedChecks = 0
        packageManagerClass.declaredMethods
            .asSequence()
            .filter { method ->
                method.name in setOf("getApplicationInfo", "getPackageInfo") &&
                    method.parameterTypes.firstOrNull() == String::class.java &&
                    (ApplicationInfo::class.java.isAssignableFrom(method.returnType) ||
                        PackageInfo::class.java.isAssignableFrom(method.returnType))
            }
            .forEach { method ->
                val key = "ok-google-identity:$classKey:${method.toGenericString()}"
                val installed = runCatching {
                    ModernHookRegistry.installCompat(
                        key = key,
                        executable = method,
                        callback = object : ModernMethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                if (param.args.firstOrNull() != GOOGLE_APP_PACKAGE) return
                                when (val result = param.result) {
                                    is ApplicationInfo -> result.flags = result.flags or ApplicationInfo.FLAG_SYSTEM
                                    is PackageInfo -> result.applicationInfo?.let { info ->
                                        info.flags = info.flags or ApplicationInfo.FLAG_SYSTEM
                                    }
                                    else -> return
                                }
                                if (loggedIdentityPresentation.compareAndSet(false, true)) {
                                    HookLog.i(TAG, "Presented system-app identity only to Google App self-check")
                                }
                            }
                        },
                    )
                }.onFailure {
                    HookLog.w(TAG, "Failed to install Google App identity compatibility", it)
                }.isSuccess
                if (installed) installedChecks++
            }

        if (installedChecks == 0) {
            HookLog.w(TAG, "Google App package-manager identity methods were not found")
        }
    }

    private fun isPermissionCheckMethod(method: Method): Boolean {
        if (method.returnType != Int::class.javaPrimitiveType) return false
        if (method.name !in setOf("checkPermission", "checkUidPermission")) return false
        return method.parameterTypes.any { it == String::class.java }
    }

    private fun installPermissionCheckHook(classKey: String, method: Method): Boolean {
        val key = "ok-google-permission:$classKey:${method.toGenericString()}"
        return runCatching {
            ModernHookRegistry.installCompat(
                key = key,
                executable = method,
                callback = object : ModernMethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val permission = param.args
                            .filterIsInstance<String>()
                            .firstOrNull { it in hotwordPermissions }
                            ?: return

                        rememberSystemContext(param.thisObject)
                        when {
                            param.args.any { it == GOOGLE_APP_PACKAGE } ||
                                param.args.any(::hasGooglePackageName) -> {
                                param.result = PERMISSION_GRANTED
                                logFirstPackageBypass(permission)
                            }

                            param.args
                                .filterIsInstance<Int>()
                                .any(::isGoogleUid) -> {
                                param.result = PERMISSION_GRANTED
                                logFirstUidBypass(permission)
                            }
                        }
                    }
                },
            )
            true
        }.onFailure {
            HookLog.w(TAG, "Failed to hook ${method.declaringClass.name}#${method.name}", it)
        }.getOrDefault(false)
    }

    private fun logFirstPackageBypass(permission: String) {
        if (loggedFirstPackageBypass.compareAndSet(false, true)) {
            HookLog.i(TAG, "Active: allowed verified Google hotword check $permission")
        }
    }

    private fun logFirstUidBypass(permission: String) {
        if (loggedFirstUidBypass.compareAndSet(false, true)) {
            HookLog.i(TAG, "Active: allowed verified Google hotword UID check $permission")
        }
    }

    private fun rememberSystemContext(owner: Any?) {
        if (systemContext != null || owner == null) return
        var current: Class<*>? = owner.javaClass
        while (current != null) {
            val context = runCatching {
                current.getDeclaredField("mContext").also { it.isAccessible = true }.get(owner)
            }.getOrNull() as? Context
            if (context != null) {
                systemContext = context.applicationContext ?: context
                return
            }
            current = current.superclass
        }
    }

    private fun isGoogleUid(uid: Int): Boolean {
        if (uid <= 0) return false
        return googleUidCache.getOrPut(uid) {
            val context = systemContext ?: return@getOrPut false
            context.packageManager.getPackagesForUid(uid)?.contains(GOOGLE_APP_PACKAGE) == true
        }
    }

    private fun hasGooglePackageName(value: Any?): Boolean {
        if (value == null || value is String) return false
        runCatching {
            value.javaClass.methods
                .firstOrNull { it.name == "getPackageName" && it.parameterCount == 0 }
                ?.invoke(value) as? String
        }.getOrNull()?.let { return it == GOOGLE_APP_PACKAGE }

        var current: Class<*>? = value.javaClass
        while (current != null) {
            val packageName = runCatching {
                current.getDeclaredField("packageName").also { it.isAccessible = true }.get(value)
            }.getOrNull() as? String
            if (packageName != null) return packageName == GOOGLE_APP_PACKAGE
            current = current.superclass
        }
        return false
    }

    private fun isEnabledAtProcessStart(): Boolean = runCatching {
        LspConfig.isOkGoogleHotwordCompatibilityEnabledXposed()
    }.getOrDefault(false)
}
