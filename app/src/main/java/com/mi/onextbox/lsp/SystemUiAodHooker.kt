package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface
import java.util.Collections
import java.util.WeakHashMap

/** API 102-native AOD hooks kept outside the main SystemUI feature dispatcher. */
internal object SystemUiAodHooker {
    private const val TAG = "ONextBox-LSP"
    private const val INIT_DARK_THRESHOLD = 40
    private const val GESTURE_SINGLE_CLICK = 16
    private const val BRIGHTNESS_MIN = 0
    private const val BRIGHTNESS_MAX = 255

    private val brightnessArgs = ThreadLocal.withInitial<Array<Any?>> { arrayOfNulls(2) }
    private val configuredPanoramicInstances =
        Collections.synchronizedMap(WeakHashMap<Any, Boolean>())
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
                            brightness < INIT_DARK_THRESHOLD ->
                                LspConfig.getAodInitDarkBrightnessXposed()
                            else -> LspConfig.getAodInitBrightBrightnessXposed()
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

        val smoothControllerCompanion = ModernReflect.findClassIfExists(
            "com.oplus.systemui.aod.display.SmoothTransitionController\$Companion",
            classLoader,
        )
        val panoramicMethods = smoothControllerCompanion
            ?.declaredMethods
            ?.filter { method -> method.name == "getInstance" }
            .orEmpty()
        panoramicMethods.forEach { method ->
            val key = "aod:panoramic:${method.declaringClass.name}#${method.name}"
            ModernHookRegistry.installFast(
                key,
                method,
                XposedInterface.Hooker { chain ->
                    val instance = chain.proceed()
                    if (
                        instance != null &&
                        LspConfig.isAodPanoramicSupportEnabledXposed() &&
                        configuredPanoramicInstances.put(instance, true) != true
                    ) {
                        setBooleanField(instance, "isSupportPanoramicAllDay")
                        setBooleanField(instance, "isSupportPanoramicAllDayByPanelFeature")
                    }
                    instance
                },
            )
            installed++
        }

        var singleClickMethods = 0
        singleClickCallbackClassNames.forEach { className ->
            val callbackClass = ModernReflect.findClassIfExists(className, classLoader)
                ?: return@forEach
            callbackClass.declaredMethods
                .filter { method ->
                    method.name == "isSupportGesture" &&
                        method.parameterTypes.contentEquals(arrayOf(Integer.TYPE)) &&
                        method.returnType == java.lang.Boolean.TYPE
                }
                .forEach { method ->
                    val key = "aod:single-click:${method.declaringClass.name}#${method.name}"
                    ModernHookRegistry.installFast(
                        key,
                        method,
                        XposedInterface.Hooker { chain ->
                            val block = LspConfig.isAodSingleClickBlockEnabledXposed() &&
                                chain.getArg(0) == GESTURE_SINGLE_CLICK
                            if (block) false else chain.proceed()
                        },
                    )
                    installed++
                    singleClickMethods++
                }
        }

        HookLog.i(
            TAG,
            if (installed > 0) {
                "SystemUI AOD API102 hooks installed in $packageName: " +
                    "init=${dozeServiceMethods.size}, running=${runningMethods.size}, " +
                    "panoramic=${panoramicMethods.size}, gesture=$singleClickMethods"
            } else {
                "SystemUI AOD hooks not matched in $packageName"
            },
        )
        return installed
    }

    private fun setBooleanField(instance: Any, fieldName: String) {
        runCatching {
            instance.javaClass.getDeclaredField(fieldName).apply {
                isAccessible = true
                setBoolean(instance, true)
            }
        }
    }
}
