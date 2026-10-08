
package com.mi.onextbox.lsp

import android.app.Notification
import android.content.Context
import android.content.res.Resources
import android.util.TypedValue
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernReflect as XposedHelpers
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method

object FrameworkHooker {
    private const val TAG = "ONextBox-LSP"
    private const val CLS_BUBBLE_EXTRACTOR = "com.android.server.notification.BubbleExtractor"
    private const val CLS_NOTIFICATION_RECORD = "com.android.server.notification.NotificationRecord"
    private const val CLS_PREFERENCES_HELPER = "com.android.server.notification.PreferencesHelper"
    private const val CLS_NOTIFICATION_CHANNEL = "android.app.NotificationChannel"

    private const val M_GET_DIMENSION_PIXEL_SIZE = "getDimensionPixelSize"
    private const val M_GET_KEY_AOD_ALL_DAY_SUPPORT_SETTINGS = "getKeyAodAllDaySupportSettings"
    private const val M_PROCESS = "process"
    private const val M_CAN_PRESENT_AS_BUBBLE = "canPresentAsBubble"
    private const val M_SET_ALLOW_BUBBLE = "setAllowBubble"
    private const val M_CAN_BUBBLE = "canBubble"
    private const val M_IS_CONVERSATION = "isConversation"
    private const val M_BUBBLES_ENABLED = "bubblesEnabled"
    private const val M_GET_BUBBLE_PREFERENCE = "getBubblePreference"
    private const val M_GET_ALLOW_BUBBLES = "getAllowBubbles"

    private const val PACKAGE_SYSTEM = "system"
    private const val PACKAGE_LAUNCHER = "com.android.launcher"
    private const val PACKAGE_OPLUS_AOD = "com.oplus.aod"

    private val recentTaskDimenNames = FeatureSliderRules.recentRadiusResourceNames
    @Volatile
    private var cachedRecentTaskDimenIds: Set<Int> = emptySet()

    private val helperClassNames = listOf(
        "com.android.server.notification.OplusNotificationFixHelper",
        "com.android.server.notification.OplusNotificationManagerExtImpl",
        "com.android.server.notification.NotificationManagerServiceExtImpl",
        "com.android.server.notification.OplusNotificationManagerServiceExtImpl"
    )

    private val installedHookKeys = HashSet<String>()

    @Volatile
    private var nativeNotificationBubblesEnabledAtStart = false

    fun hook(packageName: String, classLoader: ClassLoader?): Boolean {
        var totalHooks = 0
        when (packageName) {
            PACKAGE_SYSTEM -> {
                val featureState = readFrameworkFeatureState()
                nativeNotificationBubblesEnabledAtStart = featureState.nativeNotificationBubbles

                if (featureState.nativeNotifyIcon) {
                    totalHooks += installHookGroup("native notification icon") {
                        hookNativeNotifyIcon(classLoader = classLoader, packageName = packageName)
                    }
                }
                if (featureState.nativeNotificationBubbles) {
                    totalHooks += installHookGroup("native notification bubbles") {
                        hookNativeNotificationBubbles(classLoader = classLoader, packageName = packageName)
                    }
                }
                if (featureState.extremeRefresh) {
                    totalHooks += installHookGroup("165Hz") {
                        RefreshRateHooker.hook(classLoader = classLoader, packageName = packageName)
                    }
                }
                log(
                    "Framework feature snapshot: nativeIcon=${featureState.nativeNotifyIcon}, " +
                        "bubbles=${featureState.nativeNotificationBubbles}, " +
                        "165Hz=${featureState.extremeRefresh}"
                )
            }

            PACKAGE_LAUNCHER -> {
                if (LspConfig.isRecentTaskRadiusEnabledXposed()) {
                    totalHooks += hookRecentTaskViewRadius(
                        classLoader = classLoader,
                        packageName = packageName
                    )
                }
            }

            PACKAGE_OPLUS_AOD -> {
                if (LspConfig.isAodSettingsSwitchEnabledXposed()) {
                    totalHooks += hookAodSettingsSupport(
                        classLoader = classLoader,
                        packageName = packageName
                    )
                }
            }
        }
        return totalHooks > 0
    }

/** Isolated feature installation, one incompatible Hook cannot block the remaining groups. */
    private inline fun installHookGroup(name: String, block: () -> Int): Int {
        return runCatching(block)
            .onFailure { error ->
                log(
                    "Framework $name hook installation failed: " +
                        "${error.javaClass.simpleName}: ${error.message.orEmpty()}"
                )
            }
            .getOrDefault(0)
    }

    private data class FrameworkFeatureState(
        val nativeNotifyIcon: Boolean,
        val nativeNotificationBubbles: Boolean,
        val extremeRefresh: Boolean,
    )

    private fun readFrameworkFeatureState(): FrameworkFeatureState {
        return FrameworkFeatureState(
            nativeNotifyIcon = runCatching {
                LspConfig.isNativeNotifyIconEnabledXposed()
            }.getOrDefault(true),
            nativeNotificationBubbles = runCatching {
                LspConfig.isNativeNotificationBubblesEnabledXposed()
            }.getOrDefault(false),
            extremeRefresh = runCatching {
                LspConfig.isExtremeRefresh165EnabledXposed()
            }.getOrDefault(false),
        )
    }

    private fun hookNativeNotifyIcon(classLoader: ClassLoader?, packageName: String): Int {
        var hookedMethods = 0
        helperClassNames.forEach { className ->
            val hookClass = findClassAnyLoader(className, classLoader) ?: return@forEach
            val targetMethods = hookClass.declaredMethods.filter { method ->
                method.name.equals("fixSmallIcon", ignoreCase = true) &&
                    method.parameterTypes.size == 4 &&
                    method.parameterTypes[0] == Notification::class.java
            }
            targetMethods.forEach { method ->
                if (hookFixSmallIcon(method)) {
                    hookedMethods++
                }
            }
            if (targetMethods.isNotEmpty()) {
                log("Framework native-icon hook: $className (${targetMethods.size}) in $packageName")
            }
        }
        return hookedMethods
    }

    private fun hookRecentTaskViewRadius(classLoader: ClassLoader?, packageName: String): Int {
        val hookKey = "recent_task_radius|resources|getDimensionPixelSize"
        if (!addHookKeyIfAbsent(hookKey)) return 1

        val resourcesClass = findClassAnyLoader("android.content.res.Resources", classLoader)
        if (resourcesClass == null) {
            removeHookKey(hookKey)
            log("Recent task radius hook miss: Resources class not found in $packageName")
            return 0
        }

        val targetMethods = resourcesClass.declaredMethods.filter { method ->
            method.name == M_GET_DIMENSION_PIXEL_SIZE &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == Integer.TYPE &&
                method.returnType == Integer.TYPE
        }
        if (targetMethods.isEmpty()) {
            removeHookKey(hookKey)
            log("Recent task radius hook miss: Resources#getDimensionPixelSize not found in $packageName")
            return 0
        }

        targetMethods.forEach { method ->
            ModernHookRegistry.installFast(
                key = "framework:recent-task-radius:${method.toGenericString()}",
                executable = method,
                hooker = XposedInterface.Hooker { chain ->
                    val originalResult = chain.proceed()
                    if (!LspConfig.isRecentTaskRadiusEnabledXposed()) return@Hooker originalResult

                    val requestId = chain.getArg(0) as? Int ?: return@Hooker originalResult
                    val resources = chain.thisObject as? Resources ?: return@Hooker originalResult
                    val matchedIds = resolveRecentTaskDimenIds(resources)
                    if (matchedIds.isEmpty() || requestId !in matchedIds) return@Hooker originalResult
                    val recentTaskRadiusDp = LspConfig.getRecentTaskRadiusDpXposed()
                    if (recentTaskRadiusDp == FeatureSliderRules.SYSTEM_DEFAULT.toFloat()) return@Hooker originalResult

                    TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        recentTaskRadiusDp,
                        resources.displayMetrics
                    ).toInt()
                },
            )
        }
        log("Recent task radius hooks installed in $packageName: methods=${targetMethods.size}")
        return targetMethods.size
    }

    private fun hookAodSettingsSupport(classLoader: ClassLoader?, packageName: String): Int {
        val installed = if (
            tryHookFastFromJadx(
                className = "com.oplus.aod.util.SettingsUtils",
                classLoader = classLoader,
                methodName = M_GET_KEY_AOD_ALL_DAY_SUPPORT_SETTINGS,
                parameters = arrayOf(
                    Context::class.java,
                    Integer.TYPE
                ),
            ) { 1 }
        ) {
            1
        } else {
            0
        }
        if (installed > 0) {
            log("AOD settings support hook installed in $packageName")
        }
        return installed
    }

    private fun hookNativeNotificationBubbles(classLoader: ClassLoader?, packageName: String): Int {
        var installed = 0

        if (
            tryHookFastFromJadx(
                className = CLS_PREFERENCES_HELPER,
                classLoader = classLoader,
                methodName = M_BUBBLES_ENABLED,
                parameters = arrayOf("android.os.UserHandle"),
            ) { true }
        ) installed++

        if (
            tryHookFastFromJadx(
                className = CLS_PREFERENCES_HELPER,
                classLoader = classLoader,
                methodName = M_GET_BUBBLE_PREFERENCE,
                parameters = arrayOf(String::class.java, Integer.TYPE),
            ) { chain ->
                val original = chain.proceed()
                if ((original as? Int ?: 0) == 0) 1 else original
            }
        ) installed++

        if (
            tryHookFastFromJadx(
                className = CLS_BUBBLE_EXTRACTOR,
                classLoader = classLoader,
                methodName = M_CAN_PRESENT_AS_BUBBLE,
                parameters = arrayOf(CLS_NOTIFICATION_RECORD),
            ) { chain ->
                val record = chain.getArg(0)
                if (shouldForceBubbleRecord(record)) true else chain.proceed()
            }
        ) installed++

        if (
            tryHookFastFromJadx(
                className = CLS_BUBBLE_EXTRACTOR,
                classLoader = classLoader,
                methodName = M_PROCESS,
                parameters = arrayOf(CLS_NOTIFICATION_RECORD),
            ) { chain ->
                val record = chain.getArg(0)
                if (shouldForceBubbleRecord(record)) {
                        forceBubbleRecord(record)
                    null
                } else {
                    chain.proceed()
                }
            }
        ) installed++

        if (
            tryHookFastFromJadx(
                className = CLS_NOTIFICATION_RECORD,
                classLoader = classLoader,
                methodName = M_SET_ALLOW_BUBBLE,
                parameters = arrayOf(java.lang.Boolean.TYPE),
            ) { chain ->
                if (shouldForceBubbleRecord(chain.thisObject)) {
                    chain.proceed(arrayOf(true))
                } else {
                    chain.proceed()
                }
            }
        ) installed++

        if (
            tryHookFastFromJadx(
                className = CLS_NOTIFICATION_RECORD,
                classLoader = classLoader,
                methodName = M_CAN_BUBBLE,
            ) { chain ->
                if (shouldForceBubbleRecord(chain.thisObject)) true else chain.proceed()
            }
        ) installed++

        if (
            tryHookFastFromJadx(
                className = CLS_NOTIFICATION_RECORD,
                classLoader = classLoader,
                methodName = M_IS_CONVERSATION,
            ) { chain ->
                if (shouldForceBubbleRecord(chain.thisObject)) true else chain.proceed()
            }
        ) installed++

        if (
            tryHookFastFromJadx(
                className = CLS_NOTIFICATION_CHANNEL,
                classLoader = classLoader,
                methodName = M_CAN_BUBBLE,
            ) { true }
        ) installed++

        if (
            tryHookFastFromJadx(
                className = CLS_NOTIFICATION_CHANNEL,
                classLoader = classLoader,
                methodName = M_GET_ALLOW_BUBBLES,
            ) { chain ->
                val original = chain.proceed()
                if ((original as? Int ?: 0) == 0) 1 else original
            }
        ) installed++

        if (installed > 0) {
            log("Framework native notification bubbles hooks installed in $packageName: methods=$installed")
        } else {
            log("Framework native notification bubbles hooks not matched in $packageName")
        }
        return installed
    }

    private fun tryHookFastFromJadx(
        className: String,
        classLoader: ClassLoader?,
        methodName: String,
        parameters: Array<Any> = emptyArray(),
        intercept: (XposedInterface.Chain) -> Any?,
    ): Boolean {
        return installResolvedHook(
            className = className,
            classLoader = classLoader,
            methodName = methodName,
            parameters = parameters,
        ) { hookKey, method ->
            ModernHookRegistry.installFast(
                "framework:$hookKey",
                method,
                XposedInterface.Hooker(intercept),
            )
        }
    }

    private inline fun installResolvedHook(
        className: String,
        classLoader: ClassLoader?,
        methodName: String,
        parameters: Array<Any>,
        install: (String, Method) -> Unit,
    ): Boolean {
        val paramsKey = parameters.joinToString(",") { param ->
            when (param) {
                is Class<*> -> param.name
                is String -> param
                else -> param.toString()
            }
        }
        val hookKey = "method|$className|$methodName|$paramsKey"
        if (!addHookKeyIfAbsent(hookKey)) return true

        candidateLoaders(classLoader).forEach { loader ->
            val success = runCatching {
                val targetClass = XposedHelpers.findClass(className, loader)
                val parameterTypes = parameters.map { parameter ->
                    when (parameter) {
                        is Class<*> -> parameter
                        is String -> XposedHelpers.findClass(parameter, loader)
                        else -> error("Unsupported parameter type: $parameter")
                    }
                }.toTypedArray()
                val method = XposedHelpers.findMethodExact(targetClass, methodName, parameterTypes)
                install(hookKey, method)
            }.isSuccess
            if (success) return true
        }

        removeHookKey(hookKey)
        return false
    }

    private fun hookFixSmallIcon(method: Method): Boolean {
        val key = "native_notify|${method.toGenericString()}"
        if (!addHookKeyIfAbsent(key)) return false
        ModernHookRegistry.installFast(
            key = "framework:$key",
            executable = method,
            hooker = XposedInterface.Hooker { chain ->
                if (!LspConfig.isNativeNotifyIconEnabledXposed()) {
                    chain.proceed()
                } else {
                    NativeNotifyIconRules.buildSupplementResultForArgsXposed(
                        args = chain.args,
                        returnType = method.returnType,
                    ) ?: defaultResult(method.returnType)
                }
            },
        )
        return true
    }

    private fun shouldForceBubbleRecord(record: Any?): Boolean {
        if (!isNativeNotificationBubblesEnabledForHook()) return false
        if (record == null) return false
        if (isForegroundServiceOrUserInitiatedJob(record)) return false
        return getBubbleMetadataFromRecord(record) != null
    }

    private fun forceBubbleRecordIfEligible(record: Any?) {
        if (!shouldForceBubbleRecord(record)) return
        forceBubbleRecord(record)
    }

    private fun forceBubbleRecord(record: Any?) {
        runCatching {
            XposedHelpers.callMethod(record, M_SET_ALLOW_BUBBLE, true)
        }.onFailure { error ->
            log("Force native bubble setAllowBubble failed: ${error.javaClass.simpleName}")
        }
        setNotificationBubbleFlag(record)
    }

    private fun getNotificationFromRecord(record: Any?): Any? {
        if (record == null) return null
        return runCatching {
            XposedHelpers.callMethod(record, "getNotification")
        }.getOrNull()
    }

    private fun getBubbleMetadataFromRecord(record: Any?): Any? {
        val notification = getNotificationFromRecord(record) ?: return null
        return runCatching {
            XposedHelpers.callMethod(notification, "getBubbleMetadata")
        }.getOrNull()
    }

    private fun isForegroundServiceOrUserInitiatedJob(record: Any?): Boolean {
        val notification = getNotificationFromRecord(record) ?: return false
        return runCatching {
            XposedHelpers.callMethod(notification, "isFgsOrUij") as? Boolean == true
        }.getOrDefault(false)
    }

    private fun setNotificationBubbleFlag(record: Any?) {
        val notification = getNotificationFromRecord(record) as? Notification ?: return
        notification.flags = notification.flags or Notification.FLAG_BUBBLE
    }

    private fun isNativeNotificationBubblesEnabledForHook(): Boolean {
        return nativeNotificationBubblesEnabledAtStart
    }

    private fun addHookKeyIfAbsent(key: String): Boolean {
        synchronized(installedHookKeys) {
            if (installedHookKeys.contains(key)) return false
            installedHookKeys.add(key)
            return true
        }
    }

    private fun removeHookKey(key: String) {
        synchronized(installedHookKeys) {
            installedHookKeys.remove(key)
        }
    }

    private fun findClassAnyLoader(className: String, classLoader: ClassLoader?): Class<*>? {
        candidateLoaders(classLoader).forEach { loader ->
            val klass = XposedHelpers.findClassIfExists(className, loader)
            if (klass != null) return klass
        }
        return null
    }

    private fun resolveRecentTaskDimenIds(resources: Resources): Set<Int> {
        val cached = cachedRecentTaskDimenIds
        if (cached.isNotEmpty()) return cached

        val ids = recentTaskDimenNames.mapNotNull { name ->
            val id = resources.getIdentifier(name, "dimen", PACKAGE_LAUNCHER)
            if (id != 0) id else null
        }.toSet()
        if (ids.isNotEmpty()) {
            cachedRecentTaskDimenIds = ids
        }
        return ids
    }

    private fun candidateLoaders(primary: ClassLoader?): List<ClassLoader?> {
        val list = ArrayList<ClassLoader?>()
        list.add(primary)
        list.add(null)
        list.add(ClassLoader.getSystemClassLoader())
        return list.distinct()
    }

    private fun defaultResult(returnType: Class<*>): Any? {
        return when (returnType) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Character.TYPE -> 0.toChar()
            java.lang.Void.TYPE -> null
            else -> null
        }
    }

    private fun log(message: String) {
        HookLog.i(TAG, message)
    }
}
