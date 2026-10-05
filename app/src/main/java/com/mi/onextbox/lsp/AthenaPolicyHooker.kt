package com.mi.onextbox.lsp

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Pair
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** ColorOS 17 Athena swipe-up protection for active VPN and foreground-service apps. */
internal object AthenaPolicyHooker {
    private const val TAG = "ONextBox-Athena"
    private const val ATHENA_PACKAGE = "com.oplus.athena"
    private const val ATHENA_C17_VERSION_CODE = 700L
    private const val ACTIVITY_THREAD_CLASS = "android.app.ActivityThread"
    private const val PARSER_CLASS = "com.oplus.athena.common.parser.a"
    private const val C17_CONFIG_HOLDER_CLASS = "com.oplus.athena.common.parser.athena.q"
    private const val C17_CLEAR_POLICY_CLASS = "u0.a"
    private const val C17_SWIPE_UP_CLEAR_ACTION_CLASS =
        "com.oplus.athena.systemservice.action.prockill.clear.v"
    private const val C17_DYNAMIC_CLEAR_FILTER_CLASS =
        "com.oplus.athena.systemservice.action.prockill.clear.i"
    private const val PROC_DETAIL_INFO_CLASS = "com.oplus.app.athena.ProcDetailInfo"
    private const val METHOD_GET_XML_STRING = "getXmlString"
    private const val FIELD_LOCAL_FILE_NAME = "mLocalFileName"
    private const val FIELD_CONTEXT = "mContext"
    private const val FIELD_CLEAR_POLICY_NAME = "a"
    private const val FIELD_SKIP_VPN = "z"
    private const val FIELD_SKIP_FOREGROUND_SERVICE = "E"
    private const val KEEP_REASON_VPN = 36
    private const val KEEP_REASON_FOREGROUND_SERVICE = 41
    private const val SWIPE_UP_CLEAR_POLICY = "swipeup_forcestop_clear"
    private const val ATHENA_CONFIG_FILE = "sys_athena_config_list.xml"

    private val swipeUpClearConfigRegex = Regex(
        """<clear_config\b[^>]*>(?:(?!</clear_config>)[\s\S])*?""" +
            """<cc_name>\s*$SWIPE_UP_CLEAR_POLICY\s*</cc_name>""" +
            """(?:(?!</clear_config>)[\s\S])*?</clear_config>""",
    )

    private val installedParserClasses = ConcurrentHashMap.newKeySet<String>()
    private val installedClassLoaderBridges = ConcurrentHashMap.newKeySet<String>()
    private val installedRuntimePolicyClassLoaders = ConcurrentHashMap.newKeySet<String>()
    private val installedSwipeUpKillGuards = ConcurrentHashMap.newKeySet<String>()
    private val loggedClassLoaderBridge = AtomicBoolean(false)
    private val loggedXmlProtection = AtomicBoolean(false)
    private val loggedRuntimeProtection = AtomicBoolean(false)
    private val loggedSwipeUpKillGuard = AtomicBoolean(false)
    private val loggedSwipeUpKillGuardFailure = AtomicBoolean(false)
    private val loggedPatchFailure = AtomicBoolean(false)
    private val loggedUnsupportedVersion = AtomicBoolean(false)

    @Volatile
    private var verifiedC17Version: Boolean? = null

    fun hook(classLoader: ClassLoader?): Boolean {
        if (!isFeatureEnabled()) return false

        val classLoaderBridgeInstalled = installAthenaClassLoaderBridge(classLoader)
        val runtimePolicyInstalled = installRuntimePolicyHook(classLoader)
        val swipeUpKillGuardInstalled = installSwipeUpKillGuard(classLoader)
        val parserInstalled = findClass(PARSER_CLASS, classLoader)
            ?.let(::installParserHook)
            ?: false
        return classLoaderBridgeInstalled || runtimePolicyInstalled ||
            swipeUpKillGuardInstalled || parserInstalled
    }

    /** Athena's system-server classes arrive through a package LoadedApk after boot hooks run. */
    private fun installAthenaClassLoaderBridge(classLoader: ClassLoader?): Boolean {
        val activityThreadClass = findClass(ACTIVITY_THREAD_CLASS, classLoader) ?: return false
        val bridgeKey = loaderKey(activityThreadClass.classLoader)
        if (!installedClassLoaderBridges.add(bridgeKey)) return true

        val candidates = activityThreadClass.declaredMethods.filter { method ->
            method.name in setOf("getPackageInfoNoCheck", "getPackageInfo") &&
                method.parameterTypes.any { ApplicationInfo::class.java.isAssignableFrom(it) }
        }
        if (candidates.isEmpty()) {
            installedClassLoaderBridges.remove(bridgeKey)
            HookLog.w(TAG, "ActivityThread package-loader bridge is unavailable")
            return false
        }

        var installed = 0
        candidates.forEach { method ->
            val succeeded = runCatching {
                ModernHookRegistry.installCompat(
                    key = "athena-c17:loader-bridge:$bridgeKey:${method.toGenericString()}",
                    executable = method,
                    callback = object : ModernMethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val applicationInfo = param.args
                                .filterIsInstance<ApplicationInfo>()
                                .firstOrNull()
                                ?: return
                            if (applicationInfo.packageName != ATHENA_PACKAGE) return
                            val athenaLoader = findLoadedApkClassLoader(param.result) ?: return
                            if (loggedClassLoaderBridge.compareAndSet(false, true)) {
                                HookLog.i(TAG, "Resolved Athena package class loader in system process")
                            }
                            installAthenaHooks(athenaLoader)
                        }
                    },
                )
                true
            }.onFailure {
                HookLog.w(TAG, "Failed to install Athena package-loader bridge", it)
            }.getOrDefault(false)
            if (succeeded) installed++
        }
        if (installed == 0) installedClassLoaderBridges.remove(bridgeKey)
        return installed > 0
    }

    private fun installAthenaHooks(classLoader: ClassLoader) {
        if (!isFeatureEnabled()) return
        installRuntimePolicyHook(classLoader)
        installSwipeUpKillGuard(classLoader)
        findClass(PARSER_CLASS, classLoader)?.let(::installParserHook)
    }

    /** Patch both insertion into and retrieval from Athena's live clear-policy map. */
    private fun installRuntimePolicyHook(classLoader: ClassLoader?): Boolean {
        val holderClass = findClass(C17_CONFIG_HOLDER_CLASS, classLoader) ?: return false
        val policyClass = findClass(C17_CLEAR_POLICY_CLASS, holderClass.classLoader) ?: return false
        val classLoaderKey = loaderKey(holderClass.classLoader)
        if (!installedRuntimePolicyClassLoaders.add(classLoaderKey)) return true

        val setMethod = holderClass.declaredMethods.firstOrNull { method ->
            method.name == "setClearConfig" &&
                method.parameterTypes.contentEquals(arrayOf(policyClass))
        }
        val getMethod = holderClass.declaredMethods.firstOrNull { method ->
            method.name == "getClearConfig" &&
                method.parameterTypes.contentEquals(arrayOf(String::class.java)) &&
                policyClass.isAssignableFrom(method.returnType)
        }

        var installed = 0
        if (setMethod != null && installRuntimePolicyMethodHook(
                key = "athena-c17:set-policy:$classLoaderKey:${setMethod.toGenericString()}",
                executable = setMethod,
                policyProvider = { param -> param.args.getOrNull(0) },
            )
        ) installed++
        if (getMethod != null && installRuntimePolicyMethodHook(
                key = "athena-c17:get-policy:$classLoaderKey:${getMethod.toGenericString()}",
                executable = getMethod,
                policyProvider = { param -> param.result },
            )
        ) installed++

        if (installed == 0) {
            installedRuntimePolicyClassLoaders.remove(classLoaderKey)
            HookLog.w(TAG, "Athena 7.0 live clear-policy methods are unavailable")
        }
        return installed > 0
    }

    private fun installRuntimePolicyMethodHook(
        key: String,
        executable: java.lang.reflect.Executable,
        policyProvider: (ModernMethodHook.MethodHookParam) -> Any?,
    ): Boolean = runCatching {
        ModernHookRegistry.installCompat(
            key = key,
            executable = executable,
            callback = object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (executable !is java.lang.reflect.Method ||
                        executable.name != "setClearConfig"
                    ) return
                    applyRuntimeProtection(policyProvider(param))
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    if (executable !is java.lang.reflect.Method ||
                        executable.name != "getClearConfig"
                    ) return
                    applyRuntimeProtection(policyProvider(param))
                }
            },
        )
        true
    }.onFailure {
        HookLog.w(TAG, "Failed to hook Athena 7.0 live clear policy", it)
    }.getOrDefault(false)

    private fun applyRuntimeProtection(policy: Any?) {
        policy ?: return
        if (!isFeatureEnabled()) return
        if (readStringField(policy, FIELD_CLEAR_POLICY_NAME) != SWIPE_UP_CLEAR_POLICY) return

        val vpnUpdated = writeBooleanField(policy, FIELD_SKIP_VPN, true)
        val foregroundServiceUpdated = writeBooleanField(
            policy,
            FIELD_SKIP_FOREGROUND_SERVICE,
            true,
        )
        if (vpnUpdated && foregroundServiceUpdated &&
            loggedRuntimeProtection.compareAndSet(false, true)
        ) {
            HookLog.i(TAG, "Applied C17 swipe-up background protection to the live policy")
        }
    }

    /**
     * C17's stopType != 0 branch omits keep reasons 36 and 41. Guard its final kill decision
     * with Athena's own dynamic filter while leaving normal recent-task removal untouched.
     */
    private fun installSwipeUpKillGuard(classLoader: ClassLoader?): Boolean {
        val actionClass = findClass(C17_SWIPE_UP_CLEAR_ACTION_CLASS, classLoader) ?: return false
        val actualClassLoader = actionClass.classLoader ?: classLoader
        val dynamicFilterClass = findClass(C17_DYNAMIC_CLEAR_FILTER_CLASS, actualClassLoader)
            ?: return false
        val procDetailClass = findClass(PROC_DETAIL_INFO_CLASS, actualClassLoader) ?: return false
        val classLoaderKey = loaderKey(actualClassLoader)
        if (!installedSwipeUpKillGuards.add(classLoaderKey)) return true

        val killDecisionMethod = actionClass.declaredMethods.firstOrNull { method ->
            method.name == "I0" &&
                method.parameterCount == 5 &&
                method.returnType == Void.TYPE &&
                dynamicFilterClass.isAssignableFrom(method.parameterTypes[1]) &&
                procDetailClass.isAssignableFrom(method.parameterTypes[4])
        }
        val dynamicReasonMethod = dynamicFilterClass.declaredMethods.firstOrNull { method ->
            method.name == "x" &&
                method.parameterCount == 3 &&
                Collection::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                procDetailClass.isAssignableFrom(method.parameterTypes[1]) &&
                method.parameterTypes[2] == Boolean::class.javaPrimitiveType &&
                method.returnType == Int::class.javaPrimitiveType
        }
        if (killDecisionMethod == null || dynamicReasonMethod == null) {
            installedSwipeUpKillGuards.remove(classLoaderKey)
            HookLog.w(TAG, "Athena 7.0 swipe-up kill-decision signatures are unavailable")
            return false
        }

        return runCatching {
            dynamicReasonMethod.isAccessible = true
            ModernHookRegistry.installCompat(
                key = "athena-c17:kill-guard:$classLoaderKey:${killDecisionMethod.toGenericString()}",
                executable = killDecisionMethod,
                callback = object : ModernMethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!isFeatureEnabled()) return
                        val dynamicFilter = param.args.getOrNull(1) ?: return
                        val procDetail = param.args.getOrNull(4) ?: return
                        val keepReason = runCatching {
                            (dynamicReasonMethod.invoke(
                                dynamicFilter,
                                listOf(KEEP_REASON_VPN, KEEP_REASON_FOREGROUND_SERVICE),
                                procDetail,
                                true,
                            ) as? Number)?.toInt() ?: -1
                        }.onFailure {
                            if (loggedSwipeUpKillGuardFailure.compareAndSet(false, true)) {
                                HookLog.w(TAG, "Failed to query Athena 7.0 live keep reason", it)
                            }
                        }.getOrDefault(-1)
                        if (keepReason != KEEP_REASON_VPN &&
                            keepReason != KEEP_REASON_FOREGROUND_SERVICE
                        ) return

                        param.result = null
                        if (loggedSwipeUpKillGuard.compareAndSet(false, true)) {
                            val packageName = readStringField(procDetail, "pkgName") ?: "unknown"
                            HookLog.i(
                                TAG,
                                "Protected $packageName from C17 swipe-up kill (reason=$keepReason)",
                            )
                        }
                    }
                },
            )
            true
        }.onFailure {
            installedSwipeUpKillGuards.remove(classLoaderKey)
            HookLog.w(TAG, "Failed to hook Athena 7.0 swipe-up kill decision", it)
        }.getOrDefault(false)
    }

    /** Preserve Athena's source and RUS precedence while changing only the target policy block. */
    private fun installParserHook(parserClass: Class<*>): Boolean {
        val classKey = "${parserClass.name}@${System.identityHashCode(parserClass.classLoader)}"
        if (!installedParserClasses.add(classKey)) return true

        val method = parserClass.declaredMethods.firstOrNull {
            it.name == METHOD_GET_XML_STRING &&
                it.parameterCount == 0 &&
                Pair::class.java.isAssignableFrom(it.returnType)
        } ?: run {
            installedParserClasses.remove(classKey)
            HookLog.w(TAG, "Athena parser signature is unsupported")
            return false
        }

        return runCatching {
            ModernHookRegistry.installCompat(
                key = "athena-c17:parser:$classKey:${method.toGenericString()}",
                executable = method,
                callback = object : ModernMethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val parser = param.thisObject ?: return
                        if (!shouldApply(parser)) return
                        if (readStringField(parser, FIELD_LOCAL_FILE_NAME) != ATHENA_CONFIG_FILE) {
                            return
                        }

                        val original = param.result as? Pair<*, *> ?: return
                        val originalXml = original.first as? String ?: return
                        val patchedXml = patchSwipeUpProtection(originalXml)
                        if (patchedXml == null) {
                            if (loggedPatchFailure.compareAndSet(false, true)) {
                                HookLog.w(TAG, "C17 swipe-up policy block is unavailable")
                            }
                            return
                        }

                        param.result = Pair.create(patchedXml, original.second)
                        if (loggedXmlProtection.compareAndSet(false, true)) {
                            HookLog.i(TAG, "Applied C17 VPN and foreground-service policy flags")
                        }
                    }
                },
            )
            true
        }.onFailure {
            installedParserClasses.remove(classKey)
            HookLog.w(TAG, "Failed to hook Athena policy parser", it)
        }.getOrDefault(false)
    }

    private fun shouldApply(parser: Any): Boolean {
        if (!isFeatureEnabled()) return false
        val context = readContextField(parser) ?: return verifiedC17Version == true
        return isSupportedC17Version(context)
    }

    private fun isSupportedC17Version(context: Context): Boolean {
        verifiedC17Version?.let { return it }
        val versionCode = runCatching {
            context.packageManager.getPackageInfo(ATHENA_PACKAGE, 0).longVersionCode
        }.getOrNull() ?: return false
        val supported = versionCode == ATHENA_C17_VERSION_CODE
        verifiedC17Version = supported
        if (!supported && loggedUnsupportedVersion.compareAndSet(false, true)) {
            HookLog.w(TAG, "Athena version $versionCode is unsupported; expected 700")
        }
        return supported
    }

    private fun isFeatureEnabled(): Boolean = runCatching {
        LspConfig.isAthenaC17SwipeUpProtectionEnabledXposed()
    }.getOrDefault(false)

    private fun patchSwipeUpProtection(xml: String): String? {
        val clearConfigMatch = swipeUpClearConfigRegex.find(xml) ?: return null
        var clearConfig = clearConfigMatch.value
        clearConfig = forceBooleanTagTrue(clearConfig, "cc_skip_vpn") ?: return null
        clearConfig = forceBooleanTagTrue(clearConfig, "cc_skip_foreground_service") ?: return null
        return xml.replaceRange(clearConfigMatch.range, clearConfig)
    }

    private fun forceBooleanTagTrue(xml: String, tagName: String): String? {
        val tagRegex = Regex(
            """(<${Regex.escape(tagName)}>\s*)[^<]*(\s*</${Regex.escape(tagName)}>)""",
        )
        if (!tagRegex.containsMatchIn(xml)) return null
        return tagRegex.replace(xml) { match ->
            "${match.groupValues[1]}true${match.groupValues[2]}"
        }
    }

    private fun readContextField(instance: Any): Context? {
        (readField(instance, FIELD_CONTEXT) as? Context)?.let { return it }
        var targetClass: Class<*>? = instance.javaClass
        while (targetClass != null) {
            val contextField = targetClass.declaredFields.firstOrNull {
                Context::class.java.isAssignableFrom(it.type)
            }
            if (contextField != null) {
                return runCatching {
                    contextField.isAccessible = true
                    contextField.get(instance) as? Context
                }.getOrNull()
            }
            targetClass = targetClass.superclass
        }
        return null
    }

    private fun readStringField(instance: Any, fieldName: String): String? =
        readField(instance, fieldName) as? String

    private fun writeBooleanField(instance: Any, fieldName: String, value: Boolean): Boolean {
        var targetClass: Class<*>? = instance.javaClass
        while (targetClass != null) {
            val field = runCatching {
                targetClass.getDeclaredField(fieldName).apply { isAccessible = true }
            }.getOrNull()
            if (field != null) {
                return runCatching {
                    field.set(instance, value)
                    true
                }.getOrDefault(false)
            }
            targetClass = targetClass.superclass
        }
        return false
    }

    private fun readField(instance: Any, fieldName: String): Any? {
        var targetClass: Class<*>? = instance.javaClass
        while (targetClass != null) {
            val field = runCatching {
                targetClass.getDeclaredField(fieldName).apply { isAccessible = true }
            }.getOrNull()
            if (field != null) return runCatching { field.get(instance) }.getOrNull()
            targetClass = targetClass.superclass
        }
        return null
    }

    private fun findLoadedApkClassLoader(loadedApk: Any?): ClassLoader? {
        loadedApk ?: return null
        var targetClass: Class<*>? = loadedApk.javaClass
        while (targetClass != null) {
            val method = runCatching {
                targetClass.getDeclaredMethod("getClassLoader").apply { isAccessible = true }
            }.getOrNull()
            if (method != null) {
                return runCatching { method.invoke(loadedApk) as? ClassLoader }.getOrNull()
            }
            targetClass = targetClass.superclass
        }
        return readField(loadedApk, "mClassLoader") as? ClassLoader
    }

    private fun findClass(className: String, classLoader: ClassLoader?): Class<*>? {
        listOf(classLoader, null, ClassLoader.getSystemClassLoader())
            .distinct()
            .forEach { loader ->
                ModernReflect.findClassIfExists(className, loader)?.let { return it }
            }
        return null
    }

    private fun loaderKey(classLoader: ClassLoader?): String =
        "${classLoader?.javaClass?.name ?: "boot"}@${System.identityHashCode(classLoader)}"
}
