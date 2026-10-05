package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

/** API 102-native OPlus extreme refresh hooks, isolated from other framework features. */
internal object RefreshRateHooker {
    private const val TAG = "ONextBox-LSP"
    private const val EXTREME_RATE_ID = 7
    private const val FORCED_DISPLAY_MODE_ID = 5
    private const val CLS_CONFIGS = "com.android.server.wm.OplusRefreshRateConfigs"
    private const val CLS_CORE = "com.android.server.wm.OplusRefreshRateCore"
    private const val CLS_VOTE = "com.android.server.wm.OplusRefreshRateCore\$Vote"
    private const val CLS_POLICY = "com.android.server.wm.OplusRefreshRatePolicyImpl"
    private const val CLS_PICK_DATA = "com.android.server.wm.OplusRefreshRatePolicyImpl\$PickRefreshRateData"
    private const val CLS_SETTINGS = "com.android.server.wm.OplusRefreshRatePolicyImpl\$SettingsObserver"

    private val rewriteInProgress = ThreadLocal.withInitial { false }
    private val voteArgs = ThreadLocal.withInitial<Array<Any?>> { arrayOfNulls(2) }
    private val accessorMethods = ConcurrentHashMap<Class<*>, Method>()
    private val mutationMethods = ConcurrentHashMap<Class<*>, PreferredDataMethods>()
    private val rewrittenData = Collections.synchronizedMap(WeakHashMap<Any, Boolean>())

    private data class PreferredDataMethods(
        val setAppReqFirst: Method,
        val enableWinOverride: Method,
        val setDisableViewOverride: Method,
        val setLowFeqMode: Method,
        val setBrightnessBlock: Method,
        val setAppLowRefreshRate: Method,
        val putUsrOverrideRefreshRateId: Method,
    )

    fun hook(classLoader: ClassLoader?, packageName: String): Int {
        var installed = 0
        if (hookFast(CLS_CONFIGS, classLoader, "isExtremeHighEnable") { true }) installed++
        if (hookFast(CLS_SETTINGS, classLoader, "isExtremeHighSettingOn") { true }) installed++
        if (
            hookFast(
                CLS_POLICY,
                classLoader,
                "getFinalDisplayModeIdLocked",
                arrayOf("android.view.DisplayInfo", "android.graphics.Point"),
            ) { FORCED_DISPLAY_MODE_ID }
        ) installed++
        if (hookFast(CLS_PICK_DATA, classLoader, "isKeyguardShown") { false }) installed++

        val extremeVote = createExtremeVote(classLoader)
        if (extremeVote != null) {
            if (
                hookFast(
                    CLS_CORE,
                    classLoader,
                    "updateVoteLocked",
                    arrayOf(Integer.TYPE, CLS_VOTE),
                ) { chain ->
                    if (chain.args.size < 2 || chain.getArg(1) === extremeVote) {
                        chain.proceed()
                    } else {
                        val args = voteArgs.get()!!
                        args[0] = chain.getArg(0)
                        args[1] = extremeVote
                        try {
                            chain.proceed(args)
                        } finally {
                            args[0] = null
                            args[1] = null
                        }
                    }
                }
            ) installed++
        }

        if (
            hookFast(
                CLS_CONFIGS,
                classLoader,
                "getPreferredRefreshRateData",
                arrayOf(String::class.java, String::class.java),
                ::rewritePreferredData,
            )
        ) installed++

        log(
            if (installed > 0) {
                "Framework 165Hz API102 hooks installed in $packageName: methods=$installed"
            } else {
                "Framework 165Hz hooks not matched in $packageName"
            }
        )
        return installed
    }

    private fun createExtremeVote(classLoader: ClassLoader?): Any? {
        candidateLoaders(classLoader).forEach { loader ->
            val vote = runCatching {
                val voteClass = ModernReflect.findClass(CLS_VOTE, loader)
                val factory = ModernReflect.findMethodExact(
                    voteClass,
                    "forRefreshRate",
                    arrayOf(Integer.TYPE),
                )
                factory.invoke(null, EXTREME_RATE_ID)
            }.getOrNull()
            if (vote != null) return vote
        }
        return null
    }

    private fun rewritePreferredData(chain: XposedInterface.Chain): Any? {
        val original = chain.proceed()
        if (rewriteInProgress.get() == true) return original
        rewriteInProgress.set(true)
        return try {
            val receiver = chain.thisObject ?: return original
            val key = chain.getArg(0) as? String ?: return original
            val data = accessorMethod(receiver.javaClass).invoke(receiver, key) ?: return original
            if (rewrittenData[data] == true) return data

            val methods = dataMethods(data.javaClass)
            methods.setAppReqFirst.invoke(data, false)
            methods.enableWinOverride.invoke(data, false)
            methods.setDisableViewOverride.invoke(data, true)
            methods.setLowFeqMode.invoke(data, false)
            methods.setBrightnessBlock.invoke(data, false)
            methods.setAppLowRefreshRate.invoke(data, 0)
            for (index in 0..3) {
                methods.putUsrOverrideRefreshRateId.invoke(data, index, EXTREME_RATE_ID)
            }
            rewrittenData[data] = true
            data
        } catch (error: Throwable) {
            log("Framework 165Hz preferred-data rewrite failed: ${error.javaClass.simpleName}")
            original
        } finally {
            rewriteInProgress.set(false)
        }
    }

    private fun accessorMethod(targetClass: Class<*>): Method {
        accessorMethods[targetClass]?.let { return it }
        val method = ModernReflect.findMethodExact(
            targetClass,
            "getOrCreatePreferredRefreshRateData",
            arrayOf(String::class.java),
        )
        return accessorMethods.putIfAbsent(targetClass, method) ?: method
    }

    private fun dataMethods(targetClass: Class<*>): PreferredDataMethods {
        mutationMethods[targetClass]?.let { return it }
        fun method(name: String, vararg types: Class<*>): Method =
            ModernReflect.findMethodExact(targetClass, name, types)
        val methods = PreferredDataMethods(
            setAppReqFirst = method("setAppReqFirst", java.lang.Boolean.TYPE),
            enableWinOverride = method("enableWinOverride", java.lang.Boolean.TYPE),
            setDisableViewOverride = method("setDisableViewOverride", java.lang.Boolean.TYPE),
            setLowFeqMode = method("setLowFeqMode", java.lang.Boolean.TYPE),
            setBrightnessBlock = method("setBrightnessBlock", java.lang.Boolean.TYPE),
            setAppLowRefreshRate = method("setAppLowRefreshRate", Integer.TYPE),
            putUsrOverrideRefreshRateId = method(
                "putUsrOverrideRefreshRateId",
                Integer.TYPE,
                Integer.TYPE,
            ),
        )
        return mutationMethods.putIfAbsent(targetClass, methods) ?: methods
    }

    private fun hookFast(
        className: String,
        classLoader: ClassLoader?,
        methodName: String,
        parameters: Array<Any> = emptyArray(),
        intercept: (XposedInterface.Chain) -> Any?,
    ): Boolean {
        val signature = parameters.joinToString(",") { value ->
            if (value is Class<*>) value.name else value.toString()
        }
        val key = "refresh:$className#$methodName($signature)"
        candidateLoaders(classLoader).forEach { loader ->
            val success = runCatching {
                val targetClass = ModernReflect.findClass(className, loader)
                val types = parameters.map { value ->
                    when (value) {
                        is Class<*> -> value
                        is String -> ModernReflect.findClass(value, loader)
                        else -> error("Unsupported parameter type: $value")
                    }
                }.toTypedArray()
                val method = ModernReflect.findMethodExact(targetClass, methodName, types)
                ModernHookRegistry.installFast(key, method, XposedInterface.Hooker(intercept))
            }.isSuccess
            if (success) return true
        }
        return false
    }

    private fun candidateLoaders(primary: ClassLoader?): List<ClassLoader?> =
        listOf(primary, null, ClassLoader.getSystemClassLoader()).distinct()

    private fun log(message: String) = HookLog.i(TAG, message)
}
