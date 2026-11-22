package com.mi.onextbox.lsp

import android.view.MotionEvent
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface

/** API 102-native AOD hooks kept outside the main SystemUI feature dispatcher. */
internal object SystemUiAodHooker {
    private const val TAG = "ONextBox-LSP"
    private const val INIT_DARK_THRESHOLD = 40
    private const val BLACK_SCREEN_GESTURE_EVENT =
        "com.android.systemui.keyguard.OplusBlackScreenGestureEvent"
    private const val BRIGHTNESS_MIN = 0
    private const val BRIGHTNESS_MAX = 255
    private const val DOZE_SENSOR_SINGLE_TAP = 9

    private val brightnessArgs = ThreadLocal.withInitial<Array<Any?>> { arrayOfNulls(2) }
    private val singleClickCallbackClassNames = listOf(
        "com.oplus.systemui.aod.scene.AodViewSingleClickWakeUpHolder\$AodSingleClickWakeUpCallback",
        // ColorOS 16 panoramic AOD path.
        "com.oplus.systemui.aod.scene.PanoramicAodSingleClickWakeUpController\$PanoramicAodSingleClickWakeUpCallback",
        // ColorOS 17 moved panoramic gestures into a shared gesture controller.
        "com.oplus.systemui.aod.scene.PanoramicAodGestureController\$PanoramicAodGestureCallback",
        // ColorOS 17 normal AOD also routes single-tap wake through this controller.
        "com.oplus.systemui.aod.display.OplusWakeUpController\$AodSingleClickWakeUpCallback",
    )

    fun hook(classLoader: ClassLoader?, packageName: String): Int {
        var installed = 0

        val dozeServiceClass = ModernReflect.findClassIfExists(
            "com.oplus.systemui.aod.OplusDozeServiceExImpl",
            classLoader,
        )
        val dozeServiceMethods = dozeServiceClass
            ?.declaredMethods
            ?.filter { method ->
                method.name == "setBrightnessBeforeDozing" &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == Integer.TYPE
            }
            .orEmpty()
        dozeServiceMethods.forEach { method ->
            val key = "aod:init:${method.declaringClass.name}#${method.name}"
            ModernHookRegistry.installFast(
                key,
                method,
                XposedInterface.Hooker { chain ->
                    val originalResult = chain.proceed()
                    if (!LspConfig.isAodEnhanceEnabledXposed()) {
                        originalResult
                    } else {
                        val brightness = originalResult as? Int
                        when {
                            brightness == null -> originalResult
                            // ColorOS 17 returns -1 for panoramic AOD to tell DozeService not to
                            // replace the panel-controlled brightness. Treating it as a dark
                            // ambient value breaks the panoramic transition.
                            brightness < BRIGHTNESS_MIN -> originalResult
                            else -> FeatureSliderRules.initBrightness(
                                brightness,
                                if (brightness < INIT_DARK_THRESHOLD) LspConfig.getAodInitDarkBrightnessXposed()
                                else LspConfig.getAodInitBrightBrightnessXposed(),
                            )
                        }
                    }
                },
            )
            installed++
        }

        val displayUtilClass = ModernReflect.findClassIfExists(
            "com.oplus.systemui.aod.display.BaseDisplayUtil",
            classLoader,
        )
        val runningMethods = displayUtilClass
            ?.declaredMethods
            ?.filter { method ->
                method.name == "setDozeScreenBrightness" &&
                    method.parameterTypes.contentEquals(
                        arrayOf(java.lang.Float.TYPE, Integer.TYPE),
                    )
            }
            .orEmpty()
        runningMethods.forEach { method ->
            val key = "aod:running:${method.declaringClass.name}#${method.name}"
            ModernHookRegistry.installFast(
                key,
                method,
                XposedInterface.Hooker { chain ->
                    if (!LspConfig.isAodEnhanceEnabledXposed()) {
                        chain.proceed()
                    } else {
                        val originalNit = chain.getArg(0) as? Float
                        val originalBrightness = chain.getArg(1) as? Int
                        if (
                            originalNit == null ||
                            originalBrightness == null ||
                            originalBrightness < BRIGHTNESS_MIN ||
                            !originalNit.isFinite()
                        ) {
                            chain.proceed()
                        } else {
                            val multiplier = LspConfig.getAodRunningBrightnessMultiplierXposed()
                            if (multiplier == FeatureSliderRules.SYSTEM_DEFAULT_MULTIPLIER) return@Hooker chain.proceed()
                            val args = brightnessArgs.get()!!
                            args[0] = originalNit * multiplier
                            args[1] = (originalBrightness * multiplier)
                                .toInt()
                                .coerceIn(BRIGHTNESS_MIN, BRIGHTNESS_MAX)
                            try {
                                chain.proceed(args)
                            } finally {
                                args[0] = null
                                args[1] = null
                            }
                        }
                    }
                },
            )
            installed++
        }

        val smoothController = ModernReflect.findClassIfExists(
            "com.oplus.systemui.aod.display.SmoothTransitionController", classLoader,
        )
        val allDaySupport = smoothController?.let { type ->
            AodAllDaySupport(type) { message -> HookLog.i(TAG, message) }
        }
        val smoothControllerCompanion = ModernReflect.findClassIfExists(
            "com.oplus.systemui.aod.display.SmoothTransitionController\$Companion",
            classLoader,
        )
        val panoramicMethods = smoothControllerCompanion
            ?.declaredMethods
            ?.filter { method -> method.name == "getInstance" && method.returnType == smoothController }
            .orEmpty()
        panoramicMethods.forEach { method ->
            val key = "aod:panoramic:${method.declaringClass.name}#${method.name}"
            ModernHookRegistry.installFast(
                key,
                method,
                XposedInterface.Hooker { chain ->
                    val instance = chain.proceed()
                    allDaySupport?.apply(instance, LspConfig.isAodAllDayUnlockEnabledXposed())
                    instance
                },
            )
            installed++
        }

        // getInstance precedes native initialization. Reapply after the native panel
        // capability calculation, and on subsequent lookups if the system resets it.
        val panoramicInitMethods = smoothController?.declaredMethods.orEmpty().filter {
            it.name == "initSmoothTransitionState" && it.parameterTypes.isEmpty() &&
                it.returnType == java.lang.Void.TYPE
        }
        panoramicInitMethods.forEach { method ->
            runCatching {
                ModernHookRegistry.installFast(
                    "aod:panoramic-init:${method.declaringClass.name}#${method.name}", method,
                    XposedInterface.Hooker { chain ->
                        val result = chain.proceed()
                        allDaySupport?.apply(chain.thisObject, LspConfig.isAodAllDayUnlockEnabledXposed())
                        result
                    },
                )
                installed++
            }.onFailure { HookLog.i(TAG, "AOD all-day initialization hook failed: $it") }
        }
        if (panoramicInitMethods.isEmpty()) {
            HookLog.i(TAG, "AOD all-day initialization hook not matched; using controller lookup fallback")
        }

        val singleClickCallbackClasses = singleClickCallbackClassNames.mapNotNull { className ->
            ModernReflect.findClassIfExists(className, classLoader)
        }
        var singleClickMethods = 0
        singleClickCallbackClasses.forEach { callbackClass ->
            callbackClass.declaredMethods
                .filter { method ->
                    method.name == "onClick" &&
                        method.parameterTypes.size == 1 &&
                        method.parameterTypes[0].name == BLACK_SCREEN_GESTURE_EVENT &&
                        method.returnType == java.lang.Void.TYPE
                }
                .forEach { method ->
                    val key = "aod:single-click-action:${method.declaringClass.name}#${method.name}"
                    ModernHookRegistry.installFast(
                        key,
                        method,
                        XposedInterface.Hooker { chain ->
                            // Gesture 16 must remain supported: the native dispatcher combines
                            // two single-tap events into gesture 1 before dispatching double-tap.
                            if (LspConfig.isAodSingleClickBlockEnabledXposed()) null
                            else chain.proceed()
                        },
                    )
                    installed++
                    singleClickMethods++
                }
        }

        // Panoramic AOD normally wakes on the first tap and may not opt into double-tap
        // compatibility. When that wake action is blocked, let the native dispatcher route
        // the second tap to its existing double-tap callback, respecting the user's setting.
        val doubleTapCompatibilityMethods = singleClickCallbackClasses.mapNotNull { callbackClass ->
            runCatching { callbackClass.getMethod("isDoubleTapCompatible") }.getOrNull()
                ?.takeIf { method ->
                    method.parameterTypes.isEmpty() && method.returnType == java.lang.Boolean.TYPE
                }
        }.distinct()
        doubleTapCompatibilityMethods.forEach { method ->
            val key = "aod:double-tap-compat:${method.declaringClass.name}#${method.name}"
            ModernHookRegistry.installFast(
                key,
                method,
                XposedInterface.Hooker { chain ->
                    if (
                        LspConfig.isAodSingleClickBlockEnabledXposed() &&
                        singleClickCallbackClasses.any { it.isInstance(chain.thisObject) }
                    ) true else chain.proceed()
                },
            )
            installed++
        }

        val transitionMethods = hookSingleTapWakePaths(classLoader)
        installed += transitionMethods
        HookLog.i(
            TAG,
            if (installed > 0) {
                "SystemUI AOD API102 hooks installed in $packageName: " +
                    "init=${dozeServiceMethods.size}, running=${runningMethods.size}, " +
                    "panoramic=${panoramicMethods.size}, panoramicInit=${panoramicInitMethods.size}, singleClick=$singleClickMethods, " +
                    "doubleTapCompat=${doubleTapCompatibilityMethods.size}, transitionTap=$transitionMethods"
            } else {
                "SystemUI AOD hooks not matched in $packageName"
            },
        )
        return installed
    }

    private fun hookSingleTapWakePaths(loader: ClassLoader?): Int {
        var count = 0
        fun install(owner: String, name: String, result: Class<*>, vararg args: Class<*>, hook: XposedInterface.Hooker) {
            val type = ModernReflect.findClassIfExists(owner, loader) ?: return
            val method = type.declaredMethods.firstOrNull {
                it.name == name && it.returnType == result && it.parameterTypes.contentEquals(args)
            } ?: return
            runCatching { ModernHookRegistry.installFast("aod:tap:$owner#$name", method, hook) }
                .onSuccess { count++ }
                .onFailure { HookLog.w(TAG, "Cannot hook $owner#$name", it) }
        }

        val gesture = "com.oplus.systemui.keyguard.gesture.OplusDoubleClickSleep"
        val noninteractive = ModernReflect.findClassIfExists(gesture, loader)?.declaredMethods?.firstOrNull {
            it.name == "isNoninteractive" && it.parameterCount == 0 && it.returnType == Boolean::class.javaPrimitiveType &&
                java.lang.reflect.Modifier.isStatic(it.modifiers)
        }
        if (noninteractive != null) {
            install("$gesture\$OnDoubleClickListener", "onSingleTapConfirmed", java.lang.Boolean.TYPE, MotionEvent::class.java) { chain ->
                val event = chain.getArg(0) as? MotionEvent
                if (event != null && event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER &&
                    LspConfig.isAodSingleClickBlockEnabledXposed() &&
                    runCatching { noninteractive.invoke(null) == true }.getOrDefault(false)
                ) true else chain.proceed()
            }
        }
        install("com.android.systemui.power.domain.interactor.PowerInteractor", "wakeUpIfDozing", Void.TYPE,
            String::class.java, Integer.TYPE) { chain ->
            if (chain.getArg(0) == "PULSING_SINGLE_TAP" && LspConfig.isAodSingleClickBlockEnabledXposed()) null
            else chain.proceed()
        }
        install("com.android.systemui.doze.DozeTriggers", "gentleWakeUp", Void.TYPE, Integer.TYPE) { chain ->
            if (chain.getArg(0) == DOZE_SENSOR_SINGLE_TAP && LspConfig.isAodSingleClickBlockEnabledXposed()) null
            else chain.proceed()
        }
        return count
    }
}
