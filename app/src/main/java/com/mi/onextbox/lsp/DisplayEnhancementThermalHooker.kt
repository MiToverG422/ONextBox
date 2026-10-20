package com.mi.onextbox.lsp

import android.content.Context
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.MODERN_PREFERENCES_READY_KEY
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.util.WeakHashMap

internal object DisplayEnhancementThermalHooker {
    private const val TAG = "DisplayEnhancementThermal"
    private const val MEMC = "com.android.server.display.feature.vrr.memc.OplusFeatureMEMC"
    private const val COLOR = "com.android.server.display.color.feature.OplusFeatureVideoColorEnhance"
    private const val STATE = "display-enhancement-thermal"

    fun hookSettings(loader: ClassLoader) {
        val prefix = "$TAG:settings:"
        runCatching {
            val owner = loader.loadClass("com.oplus.settings.utils.Iris5SwitchAndTemperatureControlObserver")
            mapOf("isPowerTemperatureControlVideosr" to DisplayEnhancementThermalSettings.SUPER_RESOLUTION,
                "isPowerTemperatureControlOsie" to DisplayEnhancementThermalSettings.VIDEO_COLOR).forEach { (name, key) ->
                val method = owner.getDeclaredMethod(name, Context::class.java)
                ModernHookRegistry.installFast(prefix + name, method, XposedInterface.Hooker { chain ->
                    if (DisplayEnhancementThermalSettings.enabledXposed(key)) false else chain.proceed()
                })
            }
            // The two static one-line predicates may already have been inlined by ART.
            listOf("com.oplus.settings.feature.display.video.Iris5SwitchPanelFragment",
                "com.oplus.settings.feature.display.controller.Iris5VideoSuperResolutionController",
                "com.oplus.settings.feature.display.controller.Iris5VideoDisplayEnhancementController",
                "com.oplus.settings.feature.homepage.messageentry.MessageEntryController", owner.name)
                .forEach { name -> deoptimize(loader, name) }
            HookLog.i(TAG, "Settings enhancement thermal predicates installed")
        }.onFailure {
            ModernHookRegistry.unhookPrefix(prefix)
            HookLog.w(TAG, "Settings enhancement predicates unavailable; stock behavior retained", it)
        }
    }

    fun hookSystemServer(loader: ClassLoader) {
        val session = Session(loader)
        session.install()
        HotReloadLifecycle.register(STATE, capture = session::capture, detach = session::detach,
            restore = session::restore, resumeAfterRejection = session::schedule)
        HotReloadLifecycle.afterCommit { session.restore(HotReloadLifecycle.restoredState(STATE)) }
    }

    private class Native(
        val type: Class<*>, val key: String, val handlerField: String,
        val refresh: Method, val decision: DisplayEnhancementThermalOverride,
        val thermalRead: Method? = null,
    )

    private class Session(private val loader: ClassLoader) {
        private val main = Handler(Looper.getMainLooper())
        private val targets = mutableListOf<Native>()
        private val owners = WeakHashMap<Any, Handler>()
        private val applied = WeakHashMap<Any, Boolean>()
        private var preferences: SharedPreferences? = null
        @Volatile private var detached = false
        private val refresh = Runnable {
            HotReloadLifecycle.withHook({}, {
                if (!detached && !HotReloadLifecycle.isRetiring) {
                    discover()
                    val live = synchronized(owners) { owners.entries.map { it.key to it.value } }
                    live.forEach { (owner, handler) ->
                        val native = targets.firstOrNull { it.type.isInstance(owner) } ?: return@forEach
                        handler.removeCallbacksAndMessages(null)
                        val enabled = DisplayEnhancementThermalSettings.enabledXposed(native.key)
                        val previous = synchronized(owners) { applied[owner] }
                        if (previous == enabled) return@forEach
                        if (previous == null && !enabled) {
                            synchronized(owners) { applied[owner] = false }
                            return@forEach
                        }
                        handler.post {
                            HotReloadLifecycle.withHook({}, {
                                if (!detached && !HotReloadLifecycle.isRetiring) runCatching {
                                    if (native.key == DisplayEnhancementThermalSettings.SUPER_RESOLUTION &&
                                        !canBypassSuperResolution(owner)) return@runCatching
                                    native.refresh.invoke(owner)
                                }.onFailure { HookLog.w(TAG, "Native enhancement refresh failed", it) }
                            })
                        }
                    }
                }
            })
        }
        private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key in DisplayEnhancementThermalSettings.keys || key == MODERN_PREFERENCES_READY_KEY) schedule()
        }

        fun install() {
            installColor()
            installSuperResolution()
        }

        private fun installColor() {
            val prefix = "$TAG:color:"
            runCatching {
                val type = loader.loadClass(COLOR)
                val native = Native(type, DisplayEnhancementThermalSettings.VIDEO_COLOR, "mColorHandler",
                    type.getDeclaredMethod("sendVideoColorChangedMsg").accessible(),
                    DisplayEnhancementThermalOverride(type.getDeclaredField("mTemperatureTooHigh")))
                val notify = type.getDeclaredMethod("notifySFChanged")
                type.getDeclaredField(native.handlerField)
                ModernHookRegistry.installFast(prefix + "decision", notify, XposedInterface.Hooker { chain ->
                    val owner = chain.thisObject ?: return@Hooker chain.proceed()
                    remember(owner, native)
                    decide(native, owner) { chain.proceed() }
                })
                captureSetUp(native, prefix)
                deoptimize(loader, "$COLOR\$VideoColorEnhanceHandler")
                targets += native
                HookLog.i(TAG, "Native video color thermal decision installed")
            }.onFailure {
                ModernHookRegistry.unhookPrefix(prefix)
                HookLog.w(TAG, "Video color contract unavailable; stock behavior retained", it)
            }
        }

        private fun installSuperResolution() {
            val prefix = "$TAG:sr:"
            runCatching {
                val type = loader.loadClass(MEMC)
                listOf("mOledVideoX7P_RM", "mScreenOn", "mLowPowerMode", "mSplitMode", "mNeedClosePWFunctions")
                    .forEach { check(type.getDeclaredField(it).type == Boolean::class.javaPrimitiveType) }
                val read = type.getDeclaredMethod("getPowerTemperatureFromSettings").accessible()
                val native = Native(type, DisplayEnhancementThermalSettings.SUPER_RESOLUTION, "mHandler",
                    type.getDeclaredMethod("handlePowerTemperatureChange").accessible(),
                    DisplayEnhancementThermalOverride(type.getDeclaredField("mHighTemperatureEnable")), read)
                type.getDeclaredField(native.handlerField)
                val names = setOf("handleAppEnterX7PMode", "shouldSkipActivityEnter", "checkTopActivity", "handlePowerTemperatureChange")
                val decisions = names.map { name -> type.declaredMethods.single { it.name == name } }
                ModernHookRegistry.installFast(prefix + "read", read, XposedInterface.Hooker { chain ->
                    val genuine = chain.proceed() as Boolean
                    native.decision.observed(chain.thisObject, genuine)
                })
                decisions.forEach { method ->
                    ModernHookRegistry.installFast(prefix + method.name, method, XposedInterface.Hooker { chain ->
                        val owner = chain.thisObject ?: return@Hooker chain.proceed()
                        remember(owner, native)
                        decide(native, owner, refreshGenuine = method.name == "handlePowerTemperatureChange") { chain.proceed() }
                    })
                }
                captureSetUp(native, prefix)
                deoptimize(loader, MEMC)
                deoptimize(loader, "$MEMC\$MemcHandler")
                targets += native
                HookLog.i(TAG, "Native super resolution thermal decisions installed")
            }.onFailure {
                ModernHookRegistry.unhookPrefix(prefix)
                HookLog.w(TAG, "Super resolution contract unavailable; stock behavior retained", it)
            }
        }

        private fun captureSetUp(native: Native, prefix: String) {
            val method = native.type.getDeclaredMethod("onSetUp", Context::class.java, Int::class.javaPrimitiveType)
            ModernHookRegistry.installFast(prefix + "setup", method, XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                chain.thisObject?.let { remember(it, native) }
                result
            })
        }

        private fun remember(owner: Any, native: Native) {
            if (detached || HotReloadLifecycle.isRetiring) return
            if (native.key == DisplayEnhancementThermalSettings.SUPER_RESOLUTION && field(owner, "mOledVideoX7P_RM") != true) return
            synchronized(owners) {
                if (owners.containsKey(owner)) return
                val original = native.type.getDeclaredField(native.handlerField).apply { isAccessible = true }.get(owner) as? Handler ?: return
                // Own a separate Handler on the OEM looper; never remove OEM messages.
                owners[owner] = Handler(original.looper)
            }
        }

        private fun <T> decide(native: Native, owner: Any, refreshGenuine: Boolean = false, action: () -> T): T {
            val enabled = DisplayEnhancementThermalSettings.enabledXposed(native.key) &&
                (native.key != DisplayEnhancementThermalSettings.SUPER_RESOLUTION || canBypassSuperResolution(owner))
            val readGenuine: (() -> Boolean)? = if (refreshGenuine) {
                { checkNotNull(native.thermalRead).invoke(owner) as Boolean }
            } else null
            val result = native.decision.decide(owner, enabled, genuineAfter = readGenuine, action = action)
            synchronized(owners) { if (owners.containsKey(owner)) applied[owner] = enabled }
            return result
        }

        private fun canBypassSuperResolution(owner: Any): Boolean = DisplayEnhancementThermalRules.canBypassSuperResolution(
            supported = field(owner, "mOledVideoX7P_RM") == true,
            screenOn = field(owner, "mScreenOn") == true,
            lowPower = field(owner, "mLowPowerMode") == true,
            split = field(owner, "mSplitMode") == true,
            throttled = field(owner, "mNeedClosePWFunctions") == true,
        )

        // This executes inside system_server, with its own LocalServices and class loader.
        @SuppressLint("BlockedPrivateApi")
        private fun discover() {
            // Read existing owners only; never instantiate unsupported hardware features.
            targets.firstOrNull { it.type.name == MEMC }?.let { native ->
                runCatching { native.type.getDeclaredField("sInstance").apply { isAccessible = true }.get(null) }
                    .getOrNull()?.let { remember(it, native) }
            }
            runCatching {
                val local = loader.loadClass("com.android.server.LocalServices")
                val internalType = loader.loadClass("com.android.server.display.color.ColorDisplayService\$ColorDisplayServiceInternal")
                val internal = local.getDeclaredMethod("getService", Class::class.java).invoke(null, internalType) ?: return@runCatching
                val service = field(internal, "this\$0") ?: return@runCatching
                val extension = field(service, "mCdsExt") ?: return@runCatching
                val manager = field(extension, "mDisplayColorFeatureManager") ?: return@runCatching
                (field(manager, "mFeatures") as? List<*>)?.filterNotNull()?.forEach { owner ->
                    targets.firstOrNull { it.type.isInstance(owner) }?.let { remember(owner, it) }
                }
            }.onFailure { HookLog.w(TAG, "Existing enhancement owner discovery unavailable", it) }
        }

        fun schedule() {
            HotReloadLifecycle.afterCommit {
                if (!detached && !HotReloadLifecycle.isRetiring) {
                    main.removeCallbacks(refresh)
                    main.post(refresh)
                }
            }
        }

        fun capture(): Array<Array<Any?>> = synchronized(owners) {
            owners.keys.map { arrayOf(it, applied[it]) }.toTypedArray()
        }

        fun detach() {
            preferences?.unregisterOnSharedPreferenceChangeListener(listener)
            preferences = null
            detached = true
            main.removeCallbacks(refresh)
            synchronized(owners) {
                owners.values.forEach { it.removeCallbacksAndMessages(null) }
                owners.clear()
                applied.clear()
            }
        }

        fun restore(saved: Any?) {
            detached = false
            (saved as? Array<*>)?.forEach { row ->
                val values = row as? Array<*> ?: return@forEach
                val owner = values.getOrNull(0) ?: return@forEach
                val native = checkNotNull(targets.firstOrNull { it.type.isInstance(owner) }) {
                    "An existing enhancement owner has no compatible replacement"
                }
                remember(owner, native)
                (values.getOrNull(1) as? Boolean)?.let { enabled -> synchronized(owners) { applied[owner] = enabled } }
            }
            if (preferences == null) ModernHookRuntime.remotePreferences("lsp_features")?.let {
                it.registerOnSharedPreferenceChangeListener(listener)
                preferences = it
            }
            schedule()
        }
    }

    private fun Method.accessible(): Method = apply { isAccessible = true }

    private fun field(owner: Any, name: String): Any? =
        owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)

    private fun deoptimize(loader: ClassLoader, name: String) {
        runCatching { loader.loadClass(name).declaredMethods.forEach { ModernHookRuntime.requireModule().deoptimize(it) } }
            .onFailure { HookLog.w(TAG, "Enhancement caller deoptimization unavailable: $name", it) }
    }
}
