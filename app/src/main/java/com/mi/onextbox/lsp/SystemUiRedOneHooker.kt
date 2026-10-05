package com.mi.onextbox.lsp

import android.app.Application
import android.os.Build
import android.os.UserHandle
import android.content.Context
import android.provider.Settings
import android.view.View
import android.widget.TextView
import com.mi.onextbox.lsp.compat.ModernMethodHook as XC_MethodHook
import com.mi.onextbox.lsp.compat.ModernReflect as XposedHelpers
import com.mi.onextbox.lsp.compat.ModernHookBridge
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Lets the native SystemUI clock observe the official red-one setting on non-OnePlus devices. */
object SystemUiRedOneHooker {
    private const val TAG = "ONextBox-RedOneClock"
    private const val CLOCK_CLASS = "com.android.systemui.statusbar.policy.Clock"
    private const val OBSERVER_CLASS = "com.android.systemui.statusbar.policy.RedOneStyleObserver"
    private const val SINGLE_CLOCK_CLASS = "com.oplus.systemui.shared.clocks.SingleClockView"
    private const val KEYGUARD_UTILS_CLASS = "com.oplus.keyguard.utils.KeyguardUtils"
    private const val PERSONALITY_CLOCK_PACKAGE = "com.oplus.keyguard.personality.clocks"
    private const val BASE_CLOCK_IMPL_CLASS = "com.oplus.keyguard.clock.base.BaseClockImpl"
    private const val DIGITAL_CLOCK_IMPL_CLASS = "com.oplus.keyguard.clock.digital.DigitalClockImpl"
    private const val DIGITAL_COLOR_COORDINATOR_CLASS = "com.oplus.keyguard.clock.digital.ui.controller.DigitalClockColorCoordinator"
    private const val DIGITAL_TEXT_VIEW_CLASS = "com.oplus.keyguard.clock.digital.widget.MyCustomizedTextView"
    private const val DIGITAL_VIEW_ROOT_CLASS = "com.oplus.keyguard.clock.digital.ui.view.ClockViewRoot"
    private const val DIGITAL_VIEW_STATE_CLASS = "com.oplus.keyguard.clock.digital.domain.state.ClockViewRootState"
    private const val GLASS_REGION_CLASS = "com.oplus.keyguard.clock.common.view.livecontent.effect.shader.glass.protocol.GlassRegion"
    private const val PLUGIN_OBSERVER_CLASS = "com.oplus.keyguard.clock.common.observer.OnePlusRedOneSwitchObserver"
    private const val PLUGIN_COMMON_UTILS_CLASS = "com.oplus.keyguard.clock.common.util.CommonUtils"
    private const val OFFICIAL_SETTING = "oneplus_red_one_enable"
    private val registeredClocks = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val registeredPluginClocks = Collections.synchronizedMap(WeakHashMap<Any, Boolean>())
    private val hookedPluginClasses = Collections.synchronizedSet(HashSet<Class<*>>())
    private val lockClockAppliedLogged = AtomicBoolean(false)
    private val tickColorAppliedLogged = AtomicBoolean(false)
    private val tickDiagnostics = AtomicInteger()
    private val sceneColorAppliedLogged = AtomicBoolean(false)
    private val glassTargetAppliedLogged = AtomicBoolean(false)

    fun hook(classLoader: ClassLoader?) {
        if (!LspConfig.isWallpapersRedOneEntryEnabledXposed()) return
        if (Build.BRAND.equals("oneplus", ignoreCase = true)) return

        val clockClass = XposedHelpers.findClassIfExists(CLOCK_CLASS, classLoader)
        val observerClass = XposedHelpers.findClassIfExists(OBSERVER_CLASS, classLoader)
        if (clockClass != null && observerClass != null) {
            hookPanelClock(clockClass, observerClass)
        } else {
            HookLog.w(TAG, "Native SystemUI panel clock classes unavailable")
        }
        hookLockClock(classLoader)
        hookPersonalityClockImplementations()
        hookHostClockPlugin(classLoader)
    }

    fun hookLauncherClock() {
        if (!LspConfig.isWallpapersRedOneEntryEnabledXposed()) return
        if (Build.BRAND.equals("oneplus", ignoreCase = true)) return
        hookPersonalityClockImplementations()
    }

    private fun hookHostClockPlugin(classLoader: ClassLoader?) {
        runCatching {
            val hostClass = XposedHelpers.findClassIfExists("com.oplus.keyguard.plugin.AbstractSubPlugin", classLoader)
                ?: return@runCatching
            XposedHelpers.findAndHookMethod(hostClass, "getView", Int::class.javaPrimitiveType!!, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val view = param.result as? View ?: return
                    if (view.javaClass.name != DIGITAL_VIEW_ROOT_CLASS) return
                    val loader = view.javaClass.classLoader ?: return
                    if (hookedPluginClasses.contains(view.javaClass)) return
                    HookLog.i(TAG, "Actual lock clock root observed: loader=$loader")
                    installPersonalityClockHooks(loader)
                    hookedPluginClasses.add(view.javaClass)
                }
            })
        }.onFailure { HookLog.w(TAG, "Could not observe host lock clock root", it) }
    }

    private fun hookPersonalityClockImplementations() {
        runCatching {
            // The host loads the plugin for the current user, which can use a different loader
            // from an eagerly created package context. Observe the context it actually returns.
            val contextImpl = XposedHelpers.findClass("android.app.ContextImpl", null)
            XposedHelpers.findAndHookMethod(
                contextImpl, "createPackageContextAsUser", String::class.java,
                Int::class.javaPrimitiveType!!, UserHandle::class.java, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.throwable != null || param.args[0] != PERSONALITY_CLOCK_PACKAGE) return
                        val context = param.result as? Context ?: return
                        installPersonalityClockHooks(context.classLoader)
                    }
                },
            )
            XposedHelpers.findAndHookMethod(Application::class.java, "attach", Context::class.java, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.throwable != null) return
                    val application = param.thisObject as? Context ?: return
                    installPersonalityClockImplementationHooks(application)
                }
            })
            HookLog.i(TAG, "Waiting for SystemUI context to observe personality clock implementations")
        }.onFailure { HookLog.w(TAG, "Could not watch SystemUI application context", it) }
    }

    private fun installPersonalityClockImplementationHooks(application: Context) {
        runCatching {
            val pluginContext = application.createPackageContext(
                PERSONALITY_CLOCK_PACKAGE,
                Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
            )
            installPersonalityClockHooks(pluginContext.classLoader)
        }.onFailure { HookLog.w(TAG, "Could not observe native clock implementations", it) }
    }

    private fun installPersonalityClockHooks(classLoader: ClassLoader) {
        runCatching {
            var hooks = 0
            for (name in arrayOf(BASE_CLOCK_IMPL_CLASS, DIGITAL_CLOCK_IMPL_CLASS)) {
                val clockClass = XposedHelpers.findClassIfExists(name, classLoader) ?: continue
                if (!hookedPluginClasses.add(clockClass)) continue
                ModernHookBridge.hookAllConstructors(clockClass, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.throwable == null) attachPluginClock(param.thisObject)
                    }
                })
                XposedHelpers.findAndHookMethod(clockClass, "getView", Int::class.javaPrimitiveType!!, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.throwable == null) attachPluginClock(param.thisObject)
                    }
                })
                hooks++
            }
            if (hooks > 0) {
                hookDigitalRedOneColorLayers(classLoader)
                hookDigitalRedOneTimeUpdates(classLoader)
                HookLog.i(TAG, "Native personality clock implementations observed: $hooks, loader=$classLoader")
            }
        }.onFailure {
            HookLog.w(TAG, "Could not observe native clock implementations", it)
        }
    }

    private fun hookDigitalRedOneColorLayers(classLoader: ClassLoader) {
        runCatching {
            val coordinatorClass = XposedHelpers.findClassIfExists(DIGITAL_COLOR_COORDINATOR_CLASS, classLoader)
                ?: return@runCatching
            val textClass = XposedHelpers.findClassIfExists(DIGITAL_TEXT_VIEW_CLASS, classLoader)
                ?: return@runCatching
            val regionClass = XposedHelpers.findClassIfExists(GLASS_REGION_CLASS, classLoader)
                ?: return@runCatching
            val sceneConfigClass = XposedHelpers.findClassIfExists("$DIGITAL_COLOR_COORDINATOR_CLASS\$SceneColorConfig", classLoader)
                ?: return@runCatching
            for (method in arrayOf("setKeyguardColorConfig", "setLauncherColorConfig")) {
                XposedHelpers.findAndHookMethod(coordinatorClass, method, sceneConfigClass, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        runCatching {
                            val root = XposedHelpers.getObjectField(param.thisObject!!, "viewRoot") as? View
                                ?: return@runCatching
                            val config = param.args[0] ?: return@runCatching
                            val enabled = isOfficialRedOneEnabled(root.context)
                            if (XposedHelpers.callMethod(config, "isRedOneEnable") == enabled) return@runCatching
                            param.args[0] = XposedHelpers.callMethod(
                                config, "copy", XposedHelpers.callMethod(config, "getBlendMode"),
                                XposedHelpers.callMethod(config, "isMixColor"), enabled,
                            )
                            if (sceneColorAppliedLogged.compareAndSet(false, true)) {
                                HookLog.i(TAG, "Native peer scene red-one configuration corrected: $enabled")
                            }
                        }.onFailure { HookLog.w(TAG, "Could not synchronize native peer red-one configuration", it) }
                    }
                })
            }
            for (method in arrayOf("getColorTarget", "buildColorTargetsFromMaskMap")) {
                ModernHookBridge.hookAllMethods(coordinatorClass, method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val targets = param.result as? List<*> ?: return
                        runCatching {
                            var changed = false
                            val updated = targets.map { target ->
                                if (target == null) return@map target
                                val digit = XposedHelpers.callMethod(target, "getView") as? TextView
                                    ?: return@map target
                                val glass = XposedHelpers.callMethod(target, "getGlass") ?: return@map target
                                val region = XposedHelpers.callMethod(glass, "getRegion") as? Enum<*>
                                    ?: return@map target
                                if (region.name != "HOUR_TENS" && region.name != "HOUR_ONES") return@map target
                                val enabled = digit.text?.toString() == "1" && isOfficialRedOneEnabled(digit.context)
                                if (XposedHelpers.callMethod(glass, "getUseOnePlusRedLayer") == enabled) return@map target
                                changed = true
                                val correctedGlass = XposedHelpers.callMethod(glass, "copy", region, enabled)
                                XposedHelpers.callMethod(
                                    target, "copy", digit, XposedHelpers.callMethod(target, "getMaskColor"),
                                    XposedHelpers.callMethod(target, "getKey"), correctedGlass,
                                )
                            }
                            if (changed) {
                                param.result = updated
                                if (glassTargetAppliedLogged.compareAndSet(false, true)) {
                                    HookLog.i(TAG, "Native clock glass red-one targets corrected")
                                }
                            }
                        }.onFailure { HookLog.w(TAG, "Could not correct native glass red-one targets", it) }
                    }
                })
            }
            XposedHelpers.findAndHookMethod(coordinatorClass, "isRedOneHourMask", textClass, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.throwable != null || param.result == true) return
                    val digit = param.args[0] as? TextView ?: return
                    if (digit.text?.toString() != "1" || !isOfficialRedOneEnabled(digit.context)) return
                    val eligible = XposedHelpers.callMethod(digit, "shouldDeferMixRenderEffectToRedOneHour") == true
                    if (eligible) param.result = true
                }
            })
            XposedHelpers.findAndHookMethod(
                coordinatorClass, "resolveUseOnePlusRedLayer", View::class.java,
                Boolean::class.javaPrimitiveType!!, regionClass, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.throwable != null || param.result == true || param.args[1] != true) return
                        val digit = param.args[0] as? TextView ?: return
                        val region = param.args[2] as? Enum<*> ?: return
                        if (region.name != "HOUR_TENS" && region.name != "HOUR_ONES") return
                        if (digit.text?.toString() != "1" || !isOfficialRedOneEnabled(digit.context)) return
                        if (XposedHelpers.callMethod(digit, "isHourText") == true) param.result = true
                    }
                },
            )
            HookLog.i(TAG, "Native digital red-one color layers enabled for OPPO")
        }.onFailure { HookLog.w(TAG, "Could not enable digital red-one color layers", it) }
    }

    private fun isOfficialRedOneEnabled(context: Context): Boolean =
        Settings.System.getInt(context.contentResolver, OFFICIAL_SETTING, 0) == 1

    private fun hookDigitalRedOneTimeUpdates(classLoader: ClassLoader) {
        runCatching {
            val rootClass = XposedHelpers.findClassIfExists(DIGITAL_VIEW_ROOT_CLASS, classLoader)
                ?: return@runCatching
            val stateClass = XposedHelpers.findClassIfExists(DIGITAL_VIEW_STATE_CLASS, classLoader)
                ?: return@runCatching
            XposedHelpers.findAndHookMethod(
                rootClass, "tryApplyLightweightTimeUpdate", stateClass, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.throwable != null) return
                        val root = param.thisObject as? View ?: return
                        val state = param.args[0] ?: return
                        runCatching {
                            if (tickDiagnostics.getAndIncrement() < 4) {
                                HookLog.i(TAG, "Native clock time update: lightweight=${param.result}, official=${isOfficialRedOneEnabled(root.context)}, stateRedOne=${XposedHelpers.callMethod(state, "isRedOneEnable")}, oneShot=${XposedHelpers.callMethod(state, "isOneShotAnim")}")
                            }
                            if (param.result != true) return@runCatching
                            if (!isOfficialRedOneEnabled(root.context) ||
                                XposedHelpers.callMethod(state, "isRedOneEnable") != true) return@runCatching
                            val coordinator = XposedHelpers.callMethod(
                                root, "getDigitalClockColorCoordinator\$KeyguardPersonalityClocks_release",
                            ) ?: return@runCatching
                            // The lightweight tick commits new text without running the color pipeline.
                            // Reuse its native scene configuration and glass/color refresh after that commit.
                            XposedHelpers.callMethod(coordinator, "syncRedOneEnableFromColorConfig", true)
                            XposedHelpers.callMethod(coordinator, "requestRefreshColorIfNeed", state, true)
                            if (tickColorAppliedLogged.compareAndSet(false, true)) {
                                HookLog.i(TAG, "Native red-one color refresh applied after lightweight time update")
                            }
                        }.onFailure { HookLog.w(TAG, "Could not refresh native red-one color after time update", it) }
                    }
                },
            )
            HookLog.i(TAG, "Native digital red-one time update path observed")
        }.onFailure { HookLog.w(TAG, "Could not observe digital red-one time updates", it) }
    }

    private fun attachPluginClock(clock: Any?) {
        if (clock == null) return
        runCatching {
            if (registeredPluginClocks.containsKey(clock)) return@runCatching
            val listener = XposedHelpers.getObjectField(clock, "onePlusRedOneSwitchListener")
                ?: return@runCatching
            val context = XposedHelpers.getObjectField(clock, "pluginContext") as? Context
                ?: return@runCatching
            val pluginLoader = clock.javaClass.classLoader
            val observerClass = XposedHelpers.findClassIfExists(PLUGIN_OBSERVER_CLASS, pluginLoader)
                ?: return@runCatching
            val commonUtilsClass = XposedHelpers.findClassIfExists(PLUGIN_COMMON_UTILS_CLASS, pluginLoader)
                ?: return@runCatching
            val commonUtils = commonUtilsClass.getDeclaredField("INSTANCE").get(null)
                ?: return@runCatching
            val userId = XposedHelpers.callMethod(commonUtils, "getUserId") as? Int
                ?: return@runCatching
            XposedHelpers.callStaticMethod(observerClass, "addListener", listener, context, userId)
            XposedHelpers.callMethod(listener, "accept", isOfficialRedOneEnabled(context))
            registeredPluginClocks[clock] = true
            HookLog.i(TAG, "Native red-one listener attached to ${clock.javaClass.simpleName}")
        }.onFailure { HookLog.w(TAG, "Could not attach native personality clock listener", it) }
    }

    private fun hookPanelClock(clockClass: Class<*>, observerClass: Class<*>) {
        runCatching {
            XposedHelpers.findAndHookMethod(clockClass, "onAttachedToWindow", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.throwable != null) return
                    val clock = param.thisObject as? View ?: return
                    if (registeredClocks.containsKey(clock)) return
                    runCatching {
                        val observer = XposedHelpers.callStaticMethod(observerClass, "getInstance")
                            ?: error("Red-one observer missing")
                        val listener = XposedHelpers.getObjectField(clock, "mRedOneStyleObserver")
                            ?: error("Red-one clock listener missing")
                        XposedHelpers.callMethod(observer, "addListener", clock.context, listener)
                        registeredClocks[clock] = true
                        // The OEM registers this listener only on OnePlus. Refresh it now so the
                        // current Settings.System value applies without waiting for another toggle.
                        XposedHelpers.callMethod(observer, "onChange", false)
                    }.onFailure { HookLog.w(TAG, "Could not attach native red-one clock listener", it) }
                }
            })
            XposedHelpers.findAndHookMethod(clockClass, "onDetachedFromWindow", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val clock = param.thisObject as? View ?: return
                    if (registeredClocks.remove(clock) == null) return
                    runCatching {
                        val observer = XposedHelpers.callStaticMethod(observerClass, "getInstance")
                            ?: return@runCatching
                        val listener = XposedHelpers.getObjectField(clock, "mRedOneStyleObserver")
                            ?: return@runCatching
                        XposedHelpers.callMethod(observer, "removeListener", clock.context, listener)
                    }.onFailure { HookLog.w(TAG, "Could not detach native red-one clock listener", it) }
                }
            })
        }.onSuccess {
            HookLog.i(TAG, "Native red-one clock listener enabled for non-OnePlus SystemUI")
        }.onFailure {
            HookLog.w(TAG, "Native red-one clock listener hook unavailable", it)
        }
    }

    private fun hookLockClock(classLoader: ClassLoader?) {
        val clockClass = XposedHelpers.findClassIfExists(SINGLE_CLOCK_CLASS, classLoader)
        val utilsClass = XposedHelpers.findClassIfExists(KEYGUARD_UTILS_CLASS, classLoader)
        if (clockClass == null || utilsClass == null) {
            HookLog.w(TAG, "Native lock clock classes unavailable")
            return
        }
        val companion = runCatching {
            utilsClass.getDeclaredField("Companion").get(null)
        }.getOrNull()
        if (companion == null) {
            HookLog.w(TAG, "Native lock clock color helper unavailable")
            return
        }

        runCatching {
            XposedHelpers.findAndHookMethod(clockClass, "updateTime", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.throwable != null) return
                    val clock = param.thisObject as? View ?: return
                    runCatching {
                        if (XposedHelpers.getObjectField(clock, "mClockMode") != 0) return@runCatching
                        val hour = XposedHelpers.getObjectField(clock, "mHour") as? String
                            ?: return@runCatching
                        if ('1' !in hour) return@runCatching
                        if (Settings.System.getInt(clock.context.contentResolver, OFFICIAL_SETTING, 0) != 1) {
                            return@runCatching
                        }
                        val hourView = XposedHelpers.getObjectField(clock, "mTimeHour") as? TextView
                            ?: return@runCatching
                        val nativeText = XposedHelpers.callMethod(
                            companion, "getSpannedHourString", clock.context, hour,
                        ) as? CharSequence ?: return@runCatching
                        hourView.text = nativeText
                        if (lockClockAppliedLogged.compareAndSet(false, true)) {
                            HookLog.i(TAG, "Native red-one coloring applied to lock clock")
                        }
                    }.onFailure { HookLog.w(TAG, "Could not color native lock clock", it) }
                }
            })
        }.onSuccess {
            HookLog.i(TAG, "Native lock clock red-one path enabled")
        }.onFailure {
            HookLog.w(TAG, "Native lock clock red-one hook unavailable", it)
        }
    }
}
