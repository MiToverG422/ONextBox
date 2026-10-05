package com.mi.onextbox.lsp

import android.content.Context
import android.database.ContentObserver
import android.graphics.Point
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernReflect as XposedHelpers
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import kotlin.math.abs

/** Fills ColorOS's unconfigured extreme-mode slot with the selected, physical display rate. */
internal object SettingsGlobalExtremeRefreshRateHooker {
    private const val TAG = "GlobalExtremeRefresh"
    private const val CONFIGS_CLASS = "com.android.server.wm.OplusRefreshRateConfigs"
    private const val PREFERRED_DATA_CLASS =
        "com.android.server.wm.OplusRefreshRateConstants\$PreferredRefreshRateData"
    private const val POLICY_CLASS = "com.android.server.wm.OplusRefreshRatePolicyImpl"
    private const val PICK_DATA_CLASS =
        "com.android.server.wm.OplusRefreshRatePolicyImpl\$PickRefreshRateData"
    private const val RATE_UTILS_CLASS = "com.android.server.wm.OplusRefreshRateUtils"
    private const val TOGGLE_KEY = "oost_settings_force_global_extreme_refresh_rate"
    private const val PICK_MODE_KEY = "oplus_customize_screen_refresh_rate"
    private const val CHOICE_RATE_KEY = "choice_mode_rate"
    private const val RESOLUTION_KEY = "oplus_customize_screen_resolution_adjust"
    private const val EXTREME_PICK_MODE = 8
    private const val MIN_GLOBAL_EXTREME_RATE = 120f

    private data class Target(
        val rateId: Int = 0,
        val hertz: Float = 0f,
        val lowerHighRateIds: Set<Int> = emptySet(),
    )

    @Volatile private var target = Target()
    @Volatile private var extremeSettingIndex = 0
    @Volatile private var lastLoggedKeyguardRateId = 0
    private var policy: Any? = null
    private var rateIdByRate: Method? = null
    private var rateById: Method? = null
    private var settingModeIndexMethod: Method? = null
    private var changeObserver: ContentObserver? = null

    fun hook(classLoader: ClassLoader?): Int {
        val loader = classLoader ?: return 0
        val configs = XposedHelpers.findClassIfExists(CONFIGS_CLASS, loader) ?: return 0
        val preferredData = XposedHelpers.findClassIfExists(PREFERRED_DATA_CLASS, loader) ?: return 0
        val policyClass = XposedHelpers.findClassIfExists(POLICY_CLASS, loader) ?: return 0
        val pickDataClass = XposedHelpers.findClassIfExists(PICK_DATA_CLASS, loader)
        val utils = XposedHelpers.findClassIfExists(RATE_UTILS_CLASS, loader) ?: return 0
        val defaultRate = configs.declaredMethods.singleOrNull {
            it.name == "getDefaultRateId" && it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
        } ?: return 0
        val defaultMax = configs.declaredMethods.singleOrNull {
            it.name == "getDefaultMaxRate" && it.parameterCount == 0
        } ?: return 0
        val preferredRates = preferredData.declaredMethods.filter {
            it.name == "getPreferredRefreshRateId" &&
                (it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType)) ||
                    it.parameterTypes.contentEquals(
                        arrayOf(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType),
                    ))
        }
        val onSystemReady = policyClass.declaredMethods.singleOrNull {
            it.name == "onSystemReady" && it.parameterCount == 0
        } ?: return 0
        val keyguardPick = pickDataClass?.declaredMethods?.singleOrNull {
            it.name == "getNormalModePickPreferredId" &&
                it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType)) &&
                it.returnType == Int::class.javaPrimitiveType
        }
        val idByRate = utils.declaredMethods.singleOrNull {
            it.name == "getRefreshRateIdByRate" &&
                it.parameterTypes.contentEquals(arrayOf(Float::class.javaPrimitiveType))
        } ?: return 0
        val byId = utils.declaredMethods.singleOrNull {
            it.name == "getRefreshRateById" &&
                it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
        } ?: return 0
        val settingModeIndex = utils.declaredMethods.singleOrNull {
            it.name == "getSettingModeIndex" &&
                it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
        } ?: return 0
        if (preferredRates.size != 2) return 0
        idByRate.isAccessible = true
        byId.isAccessible = true
        settingModeIndex.isAccessible = true
        rateIdByRate = idByRate
        rateById = byId
        settingModeIndexMethod = settingModeIndex

        val installedKeys = ArrayList<String>()
        return runCatching {
            fun install(method: Method, name: String, hooker: XposedInterface.Hooker) {
                val key = "global-extreme-refresh:$name"
                ModernHookRegistry.installFast(key, method, hooker)
                installedKeys.add(key)
            }

            install(defaultRate, "default-rate", XposedInterface.Hooker { chain ->
                val original = chain.proceed()
                val selected = target
                if (chain.getArg(0) == EXTREME_PICK_MODE &&
                    original is Int && original in selected.lowerHighRateIds &&
                    selected.rateId != 0
                ) selected.rateId else original
            })
            install(defaultMax, "default-max", XposedInterface.Hooker { chain ->
                val original = chain.proceed()
                val selected = target
                if (selected.rateId != 0 && original is Float && original < selected.hertz) {
                    selected.hertz
                } else original
            })
            preferredRates.forEach { method ->
                install(method, "preferred-${method.parameterCount}", XposedInterface.Hooker { chain ->
                    val original = chain.proceed()
                    val modeIndex = chain.getArg(method.parameterCount - 1)
                    val selected = target
                    if (modeIndex == extremeSettingIndex &&
                        (method.parameterCount == 1 || chain.getArg(0) == 0) &&
                        original is Int && original in selected.lowerHighRateIds &&
                        selected.rateId != 0
                    ) selected.rateId else original
                })
            }
            // This is ColorOS's final keyguard choice, after its optional SystemUI
            // package rule. The tiny getKeyguardRefreshRateId() may be ART-inlined.
            if (keyguardPick != null) {
                install(keyguardPick, "keyguard-pick", XposedInterface.Hooker { chain ->
                    val original = chain.proceed()
                    liftedKeyguardPick(chain.thisObject, original)
                })
            }
            install(onSystemReady, "system-ready", XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                runCatching { bindPolicy(chain.thisObject) }
                    .onFailure { HookLog.w(TAG, "OEM policy observer setup failed", it) }
                result
            })
            HookLog.i(TAG, "OEM extreme-mode config hooks installed: ${installedKeys.joinToString()}")
            installedKeys.size
        }.getOrElse { error ->
            installedKeys.forEach(ModernHookRegistry::unhook)
            HookLog.w(TAG, "OEM extreme-mode config hook registration failed", error)
            0
        }
    }

    private fun liftedKeyguardPick(pickData: Any?, original: Any?): Any? {
        val selected = target
        if (selected.rateId == 0 || original !is Int ||
            original !in selected.lowerHighRateIds || pickData == null ||
            field(pickData, "mFocusFromKeyguard") != true
        ) return original

        val policy = field(pickData, "this\$0") ?: return original
        val displayInfo = field(policy, "mDisplayInfo") ?: return original
        if (field(displayInfo, "state") != Display.STATE_ON) return original

        // Keep the OEM DC limit, AOD vote, and thermal policy downstream of this pick.
        runCatching {
            pickData.javaClass.getDeclaredField("mLastPickPreferredId")
                .apply { isAccessible = true }.setInt(pickData, selected.rateId)
        }.getOrElse { return original }
        if (lastLoggedKeyguardRateId != selected.rateId) {
            lastLoggedKeyguardRateId = selected.rateId
            HookLog.i(TAG, "OEM visible keyguard rate ID $original -> ${selected.rateId}")
        }
        return selected.rateId
    }

    private fun bindPolicy(oemPolicy: Any?) {
        if (oemPolicy == null || policy != null) return
        if (field(oemPolicy, "mDisplayId") != 0) return
        val oemContext = field(oemPolicy, "mContext") as? Context ?: return
        val configs = field(oemPolicy, "mConfigs") ?: return
        val listener = method(configs.javaClass, "getConfigListChangeListener")
            ?.invoke(configs) ?: return
        val callback = method(listener.javaClass, "onConfigListChange") ?: return
        val index = runCatching {
            settingModeIndexMethod?.invoke(null, EXTREME_PICK_MODE) as? Int
        }.getOrNull()?.takeIf { it in 1..7 } ?: return
        extremeSettingIndex = index
        policy = oemPolicy

        val resolver = oemContext.contentResolver
        val handler = Handler(Looper.getMainLooper())
        fun refreshTarget() {
            val next = resolveTarget(oemPolicy, oemContext)
            if (next == target) return
            target = next
            runCatching { callback.invoke(listener) }
                .onFailure { HookLog.w(TAG, "OEM config refresh failed", it) }
            HookLog.i(TAG, "OEM global extreme target: ${next.hertz} Hz (ID ${next.rateId})")
        }
        val resolutionUri = Settings.Secure.getUriFor(RESOLUTION_KEY)
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                refreshTarget()
                // The setting can change before the display service finishes switching.
                if (uri == resolutionUri) handler.postDelayed({ refreshTarget() }, 1500L)
            }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(TOGGLE_KEY), false, observer)
        resolver.registerContentObserver(Settings.Secure.getUriFor(PICK_MODE_KEY), false, observer)
        resolver.registerContentObserver(Settings.Secure.getUriFor(CHOICE_RATE_KEY), false, observer)
        resolver.registerContentObserver(resolutionUri, false, observer)
        changeObserver = observer

        val initial = resolveTarget(oemPolicy, oemContext)
        target = initial
        if (initial.rateId != 0) {
            callback.invoke(listener)
            HookLog.i(TAG, "OEM global extreme target at startup: ${initial.hertz} Hz (ID ${initial.rateId})")
        }
    }

    private fun resolveTarget(oemPolicy: Any, oemContext: Context): Target {
        val resolver = oemContext.contentResolver
        if (field(oemPolicy, "mDisplayId") != 0) return Target()
        if (Settings.Global.getInt(resolver, TOGGLE_KEY, 0) != 1 ||
            Settings.Secure.getInt(resolver, PICK_MODE_KEY, 0) != EXTREME_PICK_MODE
        ) return Target()
        val chosenHertz = Settings.Secure.getInt(resolver, CHOICE_RATE_KEY, 0)
        if (chosenHertz <= 120) return Target()

        val displayInfo = field(oemPolicy, "mDisplayInfo") ?: return Target()
        val displayService = field(oemPolicy, "sService") ?: return Target()
        val resolution = runCatching {
            method(displayService.javaClass, "getCurrentResolution")
                ?.invoke(displayService) as? Point
        }.getOrNull() ?: return Target()
        val modes = runCatching {
            displayInfo.javaClass.getField("supportedModes").get(displayInfo) as? Array<*>
        }.getOrNull() ?: return Target()
        if (modes.none { mode ->
                mode is Display.Mode &&
                    mode.physicalWidth == resolution.x &&
                    mode.physicalHeight == resolution.y &&
                    abs(mode.refreshRate - chosenHertz) < 0.5f
            }
        ) return Target()

        val rateId = runCatching {
            rateIdByRate?.invoke(null, chosenHertz.toFloat()) as? Int
        }.getOrNull() ?: return Target()
        val officialHertz = runCatching { rateById?.invoke(null, rateId) as? Float }
            .getOrNull() ?: return Target()
        if (officialHertz <= MIN_GLOBAL_EXTREME_RATE ||
            abs(officialHertz - chosenHertz) >= 1f
        ) {
            return Target()
        }
        val lowerHighRateIds = (1..8).filterTo(mutableSetOf()) { configuredId ->
            val configuredHertz = runCatching {
                rateById?.invoke(null, configuredId) as? Float
            }.getOrNull() ?: return@filterTo false
            configuredHertz >= MIN_GLOBAL_EXTREME_RATE &&
                configuredHertz + 0.5f < officialHertz
        }
        return Target(rateId, officialHertz, lowerHighRateIds)
    }

    private fun field(instance: Any, name: String): Any? = runCatching {
        instance.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(instance)
    }.getOrNull()

    private fun method(clazz: Class<*>, name: String): Method? = runCatching {
        clazz.getDeclaredMethod(name).apply { isAccessible = true }
    }.getOrNull()
}
