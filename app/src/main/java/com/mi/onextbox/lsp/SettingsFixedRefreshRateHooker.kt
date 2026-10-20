package com.mi.onextbox.lsp

import android.content.Context
import android.graphics.Point
import android.provider.Settings
import android.util.SparseArray
import android.view.Display
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Field
import java.lang.reflect.Method

/** Pins the native physical range and disables OEM idle adaptation, not panel hardware support. */
internal object SettingsFixedRefreshRateHooker {
    private const val TAG = "FixedRefreshRate"
    private const val PREFIX = "settings-fixed-refresh:"
    private const val POLICY = "com.android.server.wm.OplusRefreshRatePolicyImpl"
    private const val CORE = "com.android.server.wm.OplusRefreshRateCore"
    private val safetyPriorities = intArrayOf(50, 60, 67, 70, 80, 110, 120)
    private val nativeScenePriorities = intArrayOf(121, 130, 140, 150)

    private data class State(val policy: Any, val core: Any, val lock: Any,
                             val selected: Float, val supported: List<Float>) {
        val rates = supported.toFloatArray()
    }

    private class Access(policy: Class<*>, core: Class<*>, vote: Class<*>, info: Class<*>,
                         settings: Class<*>, dc: Class<*>) {
        val displayId = field(policy, "mDisplayId")
        val displayInfo = field(policy, "mDisplayInfo")
        val displayState = field(info, "state")
        val supportedModes = field(info, "supportedModes")
        val coreInstance = field(policy, "mCore")
        val lock = field(policy, "mLock")
        val service = field(policy, "sService")
        val votes = field(core, "mActiveVotes")
        val voteRate = field(vote, "mRefreshRate")
        val adfr = optionalField(policy, "mAdfrV32Controller")
        val observer = field(policy, "mSettingsObserver")
        val pwm = field(settings, "mDisplayPwmStatus")
        val pencil = field(settings, "mIpePencilState")
        val dcController = dc.getDeclaredMethod("getInstance").apply { isAccessible = true }.invoke(null)
        val dcLimit = field(dc, "mLimitMaxRefrashRateId")
    }

    @Volatile private var state: State? = null
    private var access: Access? = null
    private val pickingPrimaryDisplay = ThreadLocal<Boolean>()

    fun hook(loader: ClassLoader): Int {
        val installed = ArrayList<String>()
        return runCatching {
            val policy = ModernReflect.findClass(POLICY, loader)
            val core = ModernReflect.findClass(CORE, loader)
            val vote = ModernReflect.findClass("$CORE\$Vote", loader)
            val info = ModernReflect.findClass("android.view.DisplayInfo", loader)
            val settings = ModernReflect.findClass("$POLICY\$SettingsObserver", loader)
            val dc = ModernReflect.findClass("com.android.server.wm.OplusRefreshRateDCController", loader)
            val fields = Access(policy, core, vote, info, settings, dc)
            val perfect = core.getDeclaredMethod("getPerfectRefreshRate", SparseArray::class.java,
                Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            val min = policy.getDeclaredMethod("getPreferredMinRefreshRate", Float::class.javaPrimitiveType)
            val max = policy.getDeclaredMethod("getPreferredMaxRefreshRate", Float::class.javaPrimitiveType)
            val pick = policy.getDeclaredMethod("getPreferredModeId", Float::class.javaPrimitiveType,
                Int::class.javaPrimitiveType)
            check(perfect.returnType == Float::class.javaPrimitiveType && min.returnType == perfect.returnType &&
                max.returnType == perfect.returnType)
            access = fields

            fun install(method: Method, name: String, hooker: XposedInterface.Hooker) {
                val key = PREFIX + name
                ModernHookRegistry.installFast(key, method, hooker)
                installed += key
            }

            install(pick, "primary-display-scope", XposedInterface.Hooker { chain ->
                val current = state
                if (current == null || chain.thisObject !== current.policy) chain.proceed() else {
                    val previous = pickingPrimaryDisplay.get()
                    pickingPrimaryDisplay.set(true)
                    try { chain.proceed() } finally {
                        if (previous == null) pickingPrimaryDisplay.remove() else pickingPrimaryDisplay.set(previous)
                    }
                }
            })
            install(perfect, "physical-pick", XposedInterface.Hooker { chain ->
                val original = chain.proceed()
                val current = state
                if (current == null || pickingPrimaryDisplay.get() != true || chain.thisObject !== current.core) original else {
                    @Suppress("UNCHECKED_CAST")
                    val votes = chain.getArg(0) as? SparseArray<Any>
                    fixedRate(current, votes).takeIf { it > 0f } ?: original
                }
            })
            for ((name, method) in listOf("minimum" to min, "maximum" to max)) {
                install(method, name, XposedInterface.Hooker { chain ->
                    val original = chain.proceed()
                    val current = state
                    if (current == null || chain.thisObject !== current.policy) original
                    else fixedRate(current).takeIf { it > 0f } ?: original
                })
            }
            // Keep the OEM scene transition, low-frequency cleanup and thermal callbacks.
            policy.declaredMethods.singleOrNull { it.name == "applyVrrScene" &&
                it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType, Display.Mode::class.java,
                    Boolean::class.javaPrimitiveType)) }?.let { method ->
                install(method, "disable-vrr-adaptation", XposedInterface.Hooker { chain ->
                    val current = state
                    if (current == null || chain.thisObject !== current.policy || fixedRate(current) <= 0f) {
                        chain.proceed()
                    } else chain.proceed(chain.args.toTypedArray().also { it[2] = true })
                })
            }
            ModernReflect.findClassIfExists("com.android.server.wm.OplusAdfrV32Controller", loader)?.let { adfr ->
                adfr.declaredMethods.singleOrNull { it.name == "setAdfrDirectorState" &&
                    it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType)) }?.let { method ->
                    install(method, "adfr-director", XposedInterface.Hooker { chain ->
                        if (ownsActiveAdfr(chain.thisObject)) chain.proceed(arrayOf<Any?>(1)) else chain.proceed()
                    })
                }
                adfr.declaredMethods.singleOrNull { it.name == "adjustFrtcModeIfNeed" &&
                    it.parameterTypes.contentEquals(arrayOf(Display.Mode::class.java,
                        Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType)) }?.let { method ->
                    install(method, "disable-frtc-adaptation", XposedInterface.Hooker { chain ->
                        // Native standard-mode handling also clears an existing FRTC request.
                        if (ownsActiveAdfr(chain.thisObject)) {
                            chain.proceed(chain.args.toTypedArray().also { it[2] = 2 })
                        } else chain.proceed()
                    })
                }
            }
            HookLog.i(TAG, "Native fixed-rate hooks installed: ${installed.size}")
            installed.size + SettingsFixedRefreshIdleBridge.hook(loader)
        }.getOrElse {
            installed.forEach(ModernHookRegistry::unhook)
            access = null
            state = null
            HookLog.w(TAG, "Native fixed-rate contract unavailable; keeping OEM policy", it)
            0
        }
    }

    /** Called by the existing policy owner on selection, resolution, toggle and power changes. */
    fun updatePolicy(policy: Any, context: Context): Boolean {
        val previous = state
        state = runCatching {
            val fields = access ?: return@runCatching null
            val resolver = context.contentResolver
            if (fields.displayId.getInt(policy) != Display.DEFAULT_DISPLAY ||
                Settings.Global.getInt(resolver, SettingsFixedRefreshRateRules.TOGGLE_KEY, 0) != 1 ||
                Settings.Global.getInt(resolver, "low_power", 0) != 0) return@runCatching null
            val rate = SettingsFixedRefreshRateRules.selectedHertz(
                Settings.Secure.getInt(resolver, "oplus_customize_screen_refresh_rate", -1),
                Settings.Secure.getInt(resolver, "choice_mode_rate", -1))
            val info = fields.displayInfo.get(policy) ?: return@runCatching null
            val service = fields.service.get(policy) ?: return@runCatching null
            val size = service.javaClass.getDeclaredMethod("getCurrentResolution")
                .apply { isAccessible = true }.invoke(service) as? Point ?: return@runCatching null
            val modes = fields.supportedModes.get(info) as? Array<*> ?: return@runCatching null
            val rates = modes.filterIsInstance<Display.Mode>()
                .filter { it.physicalWidth == size.x && it.physicalHeight == size.y }
                .map { it.refreshRate }.distinct().sorted()
            if (!SettingsFixedRefreshRateRules.isSupported(rate, rates.toFloatArray())) return@runCatching null
            State(policy, checkNotNull(fields.coreInstance.get(policy)), checkNotNull(fields.lock.get(policy)), rate, rates)
        }.onFailure { HookLog.w(TAG, "Could not resolve selected physical refresh rate", it) }.getOrNull()
        SettingsFixedRefreshIdleBridge.ensureBound()
        if (state == null) SettingsFixedRefreshIdleBridge.setRequested(false)
        if (state != previous) HookLog.i(TAG, "Fixed refresh target: ${state?.selected ?: 0f} Hz")
        return state != previous
    }

    fun clear() {
        state = null
        SettingsFixedRefreshIdleBridge.setRequested(false)
    }

    fun hasFixedSelection(): Boolean = state != null

    private fun ownsActiveAdfr(controller: Any?): Boolean {
        val current = state ?: return false
        return controller != null && access?.adfr?.get(current.policy) === controller && fixedRate(current) > 0f
    }

    private fun fixedRate(current: State, source: SparseArray<Any>? = null): Float {
        val fields = access ?: return 0f
        val result = synchronized(current.lock) {
            val info = fields.displayInfo.get(current.policy) ?: return@synchronized 0f
            if (fields.displayState.getInt(info) != Display.STATE_ON) return@synchronized 0f
            val observer = fields.observer.get(current.policy) ?: return@synchronized 0f
            if (fields.dcLimit.getInt(fields.dcController) > 0 || fields.pwm.getInt(observer) == 1 ||
                fields.pencil.getInt(observer) == 1) return@synchronized 0f
            @Suppress("UNCHECKED_CAST")
            val votes = source ?: (fields.votes.get(current.core) as? SparseArray<Any>) ?: return@synchronized 0f
            // AOD, fingerprint illumination and special display tests must retain exact native timing.
            if (nativeScenePriorities.any { votes[it] != null }) return@synchronized 0f
            var cap = Float.POSITIVE_INFINITY
            for (priority in safetyPriorities) {
                val vote = votes[priority] ?: continue
                val rate = fields.voteRate.getFloat(vote)
                if (!rate.isFinite() || rate <= 0f) return@synchronized 0f
                cap = minOf(cap, rate)
            }
            SettingsFixedRefreshRateRules.effectiveHertz(current.selected, current.rates, true, false, cap)
        }
        // Do not acquire the vendor state lock under the window-policy lock. Only publish
        // the effective request; the main-queue replay performs the native output transition.
        SettingsFixedRefreshIdleBridge.setRequested(result > 0f)
        return result
    }

    private fun field(type: Class<*>, name: String): Field = type.getDeclaredField(name).apply { isAccessible = true }
    private fun optionalField(type: Class<*>, name: String): Field? = runCatching { field(type, name) }.getOrNull()
}
