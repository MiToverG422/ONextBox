package com.mi.onextbox.lsp

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Application
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.database.ContentObserver
import android.os.Bundle
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Process
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import com.mi.onextbox.lsp.compat.ModernHookBridge as XposedBridge
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernMethodHook as XC_MethodHook
import com.mi.onextbox.lsp.compat.ModernReflect as XposedHelpers
import java.lang.reflect.Method
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("StaticFieldLeak")
object AssistantHooker {
    private const val TAG = "ONextBox-Assistant"
    private const val SYSTEM_PACKAGE = "system"
    private const val ANDROID_PACKAGE = "android"
    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    private const val LAUNCHER_PACKAGE = "com.android.launcher"
    private const val SETTINGS_PACKAGE = "com.android.settings"
    private const val GOOGLE_APP_PACKAGE = "com.google.android.googlequicksearchbox"
    private const val GOOGLE_ASSISTANT_APP_FEATURE =
        "com.android.systemui.google_assistant_supported"
    private const val CONTEXTUAL_SEARCH_SYSTEM_FEATURE =
        "com.google.android.feature.CONTEXTUAL_SEARCH"
    private const val BREENO_SPEECH_FEATURE = "oplus.software.speech_assist_for_breeno"
    private const val LONG_PRESS_POWER_SHUTDOWN_FEATURE =
        "oplus.software.long_press_powerkey_shutdown"
    private const val SHORT_PRESS_POWER_SHUTDOWN_FEATURE =
        "oplus.software.short_press_powerkey_shutdown"
    private const val SECURE_ASSISTANT = "assistant"
    private const val SECURE_VOICE_INTERACTION_SERVICE = "voice_interaction_service"
    private const val SECURE_DISABLE_GOOGLE_ASSIST_POWER_WAKEUP =
        "disable_google_asssist_power_wakeup"
    private const val SYSTEM_QUICK_TURN_ON_VOICE_ASSISTANT =
        "quick_turn_on_voice_assistant"
    private const val SECURE_ASSISTANT_TUTORIAL_STARTED = "op_assist_started"
    private const val SECURE_GESTURE_CIRCLE_TO_SEARCH_ENABLED =
        "circle_to_search_enable_navi"
    private const val SECURE_GESTURE_CORNER_ASSISTANT_ENABLED =
        "circle_to_search_corner_assist_enable_navi"
    private const val ASSISTANT_ROLE = "android.app.role.ASSISTANT"
    private const val VOICE_INTERACTION_SERVICE_ACTION =
        "android.service.voice.VoiceInteractionService"
    private const val BIND_VOICE_INTERACTION_PERMISSION =
        "android.permission.BIND_VOICE_INTERACTION"
    private const val MSG_POWER_LONG_PRESS_FOR_SPEECH = 0x3F3
    private const val DEBOUNCE_WINDOW_MS = 1_000L
    private const val WARMUP_TIMEOUT_MS = 600L
    private const val WARMUP_TIMEOUT_AGGRESSIVE_MS = 1_000L
    private const val POST_CONNECT_SETTLE_MS = 120L
    private const val POST_CONNECT_SETTLE_AGGRESSIVE_MS = 250L
    private const val SHOW_SESSION_RETRY_DELAY_MS = 80L
    private const val FORCE_STOP_SETTLE_MS = 100L
    private const val SHOW_SOURCE_ASSIST_GESTURE = 4
    private const val INVOCATION_TYPE_GESTURE_CORNER_ASSISTANT = 1
    private const val INVOCATION_TYPE_POWER_BUTTON_LONG_PRESS = 6
    private const val INVOCATION_TYPE_CIRCLE_TO_SEARCH = 8
    private const val INVOCATION_TYPE_CIRCLE_TO_SEARCH_ALTERNATE = 1000
    private const val KEYBOARD_DEVICE_ID_SYSTEM = -1

    private val installedHooks = ConcurrentHashMap.newKeySet<String>()
    private val assistantExecutor by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "ONextBox-Assistant").apply { isDaemon = true }
        }
    }
    private val assistantLaunchInProgress = AtomicBoolean(false)
    private val nativeAssistantRouteDepth = ThreadLocal<Int>()
    private val nativeAssistantRouteFrames = ThreadLocal<ArrayDeque<Boolean>>()

    @Volatile
    private var powerSettingBridgeObservers: List<ContentObserver>? = null

    @Volatile
    private var voiceInteractionManagerStub: Any? = null

    @Volatile
    private var lastObservedPowerAssistantChoice: Boolean? = null

    @Volatile
    private var systemContext: Context? = null

    @Volatile
    private var lastPowerInterceptAt = 0L

    @Volatile
    private var contextualSearchConfigId: Int? = null

    fun hook(packageName: String, classLoader: ClassLoader) {
        when (packageName) {
            SYSTEM_PACKAGE, ANDROID_PACKAGE -> hookSystem(packageName, classLoader)
            SYSTEM_UI_PACKAGE -> hookSystemUi(classLoader)
            LAUNCHER_PACKAGE -> hookLauncher(classLoader)
            SETTINGS_PACKAGE -> hookSettings(classLoader)
        }
    }

    private fun hookSystem(packageName: String, classLoader: ClassLoader) {
        val legacyPowerEnabled =
            LspConfig.getAssistantPowerModeXposed() != LspConfig.ASSISTANT_POWER_MODE_NONE
        val nativePowerEnabled = LspConfig.isAssistantNativePowerEnabledXposed()
        val circleEnabled = isAnyGestureCircleEnabled()
        if (!legacyPowerEnabled && !nativePowerEnabled && !circleEnabled) return
        hookSystemContext(packageName, classLoader)
        if (circleEnabled) hookContextualSearch(packageName, classLoader)
        if (nativePowerEnabled) {
            hookNativeAssistantFeatureRoute(packageName, classLoader)
            hookInternationalPowerStrategy(packageName, classLoader)
            hookNativePowerLongPress(packageName, classLoader)
            hookVoiceInteractionServiceRepair(packageName, classLoader)
        }
        if (!nativePowerEnabled && LspConfig.isAssistantNativeCircleEnabledXposed()) {
            XposedHelpers.findClassIfExists(
                "com.android.server.voiceinteraction.VoiceInteractionManagerService\$" +
                    "VoiceInteractionManagerServiceStub",
                classLoader,
            )?.let { hookNativeAssistantSessionRecovery(packageName, it, classLoader) }
        }
        if (legacyPowerEnabled && !nativePowerEnabled) {
            hookPowerLongPress(packageName, classLoader)
            hookOriginalAssistantStart(packageName, classLoader)
        }
    }

    private fun hookSystemUi(classLoader: ClassLoader) {
        val nativePowerEnabled = LspConfig.isAssistantNativePowerEnabledXposed()
        val nativeCircleEnabled = LspConfig.isAssistantNativeCircleEnabledXposed()
        val legacyCircleEnabled = isLegacyGestureCircleEnabled()
        if (!nativePowerEnabled && !nativeCircleEnabled && !legacyCircleEnabled) return
        hookSystemUiAttachRetry()
        hookSystemUiExperimentalRoutes(classLoader)
        if (legacyCircleEnabled && !nativeCircleEnabled) {
            hookGestureBar(classLoader)
        }
    }

    /**
     * ColorOS 17 moved gesture-bar Circle to Search into the launcher's
     * OplusCuiInputConsumer. The consumer already sends the correct native
     * invocation types (8/1000); it only takes the Breeno branch on domestic
     * builds because this launcher-local feature gate is false.
     */
    private fun hookLauncher(classLoader: ClassLoader) {
        if (!LspConfig.isAssistantNativeCircleEnabledXposed()) return
        hookContextualSearchSystemFeature(LAUNCHER_PACKAGE, classLoader)
        hookLauncherAssistantAvailability(classLoader)

        val featureClass = XposedHelpers.findClassIfExists(
            "com.android.common.config.FeatureOption",
            classLoader,
        ) ?: return
        val key = "launcher:native-circle-feature-option"
        if (!installedHooks.add(key)) return
        runCatching {
            listOf("isSupportCircleToSearch", "isSupportGemini").forEach { methodName ->
                XposedBridge.hookAllMethods(
                    featureClass,
                    methodName,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (LspConfig.isAssistantNativeCircleEnabledXposed()) {
                                param.result = true
                            }
                        }
                    },
                )
            }
            log("Enabled the ColorOS 17 launcher Circle to Search and Gemini feature gates")
        }.onFailure {
            installedHooks.remove(key)
            log("Failed to enable the ColorOS 17 launcher Circle to Search route", it)
        }
        hookLauncherNativeCircleEntry(classLoader)
    }

    /**
     * International SystemUI reports the selected assistant as available through OverviewProxy.
     * The domestic build can report false even with a valid Assistant Role, which prevents the
     * stock AssistantInputConsumer from being created before invocation_type=1 is ever sent.
     */
    private fun hookLauncherAssistantAvailability(classLoader: ClassLoader) {
        val serviceClass = XposedHelpers.findClassIfExists(
            "com.android.quickstep.TouchInteractionService",
            classLoader,
        ) ?: return
        sequenceOf(serviceClass, *serviceClass.declaredClasses)
            .flatMap { it.declaredMethods.asSequence() }
            .filter { method ->
                method.name == "onAssistantAvailable" &&
                    method.parameterTypes.size == 2 &&
                    method.parameterTypes[0] == java.lang.Boolean.TYPE
            }
            .forEach { method ->
                val key = "launcher:assistant-available:${method.toGenericString()}"
                if (!installedHooks.add(key)) return@forEach
                runCatching {
                    XposedBridge.hookMethod(
                        method,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                if (!LspConfig.isAssistantNativeCircleEnabledXposed()) return
                                val context = findContext(param.thisObject) ?: return
                                if (!isGestureCornerAssistantEnabled(context)) return
                                if (resolveDefaultAssistant(context) == null) return
                                param.args[0] = true
                            }
                        },
                    )
                    log("Restored the C17 launcher corner-assistant availability signal")
                }.onFailure {
                    installedHooks.remove(key)
                    log("Failed to restore launcher assistant availability", it)
                }
            }
    }

    /**
     * Some C17 domestic launcher builds resolve the lazy feature value before our package-ready
     * callback and keep the Breeno branch in the compiled companion entry. Intercept that final
     * entry as a compatibility fallback and execute the same SystemUiProxy.startAssistant route
     * used by the international branch. Gesture recognition and its press animation stay owned
     * by OplusCuiInputConsumer; only the destination selected after recognition is replaced.
     */
    private fun hookLauncherNativeCircleEntry(classLoader: ClassLoader) {
        val companionClass = XposedHelpers.findClassIfExists(
            "com.android.quickstep.inputconsumers.OplusCuiInputConsumer\$Companion",
            classLoader,
        ) ?: return
        companionClass.declaredMethods
            .filter { method ->
                method.name == "e" &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 2 &&
                    method.parameterTypes[0] == Integer.TYPE &&
                    Context::class.java.isAssignableFrom(method.parameterTypes[1])
            }
            .forEach { method ->
                val key = "launcher:native-circle-entry:${method.toGenericString()}"
                if (!installedHooks.add(key)) return@forEach
                runCatching {
                    XposedBridge.hookMethod(
                        method,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                if (!LspConfig.isAssistantNativeCircleEnabledXposed()) return
                                val startType = param.args.getOrNull(0) as? Int ?: return
                                val context = param.args.getOrNull(1) as? Context ?: return
                                if (!isGestureCircleToSearchEnabled(context)) return
                                val invocationType =
                                    if (startType == 93) {
                                        INVOCATION_TYPE_CIRCLE_TO_SEARCH
                                    } else {
                                        INVOCATION_TYPE_CIRCLE_TO_SEARCH_ALTERNATE
                                    }
                                dispatchLauncherNativeAssistant(
                                    context = context,
                                    classLoader = classLoader,
                                    invocationType = invocationType,
                                )
                                param.result = null
                            }
                        },
                    )
                    log("Hooked the ColorOS 17 launcher native assistant entry")
                }.onFailure {
                    installedHooks.remove(key)
                    log("Failed to hook the ColorOS 17 launcher native assistant entry", it)
                }
            }
    }

    private fun dispatchLauncherNativeAssistant(
        context: Context,
        classLoader: ClassLoader,
        invocationType: Int,
    ) {
        val launch = Runnable {
            runCatching {
                val proxyClass = XposedHelpers.findClass(
                    "com.android.quickstep.pb",
                    classLoader,
                )
                val holder = readStaticField(proxyClass, "T")
                    ?: error("SystemUiProxy holder is unavailable")
                val proxy = XposedHelpers.callMethod(holder, "get", context)
                    ?: error("SystemUiProxy is unavailable")
                val bundle = Bundle().apply {
                    putInt("invocation_type", invocationType)
                }
                XposedHelpers.callMethod(proxy, "startAssistant", bundle)
                log("Forwarded launcher assistant invocation type $invocationType to SystemUI")
            }.onFailure {
                log("Failed to forward the launcher assistant invocation to SystemUI", it)
            }
        }

        // Match C17's own recents-animation hand-off. If an OEM field changes in a later build,
        // launching immediately is safer than falling back to the domestic Breeno service.
        val queued = runCatching {
            val stateClass = XposedHelpers.findClass("com.android.quickstep.s5", classLoader)
            val recentsAnimationFinished =
                readStaticField(stateClass, "f19468r") as? Boolean ?: true
            if (recentsAnimationFinished) return@runCatching false

            val serviceClass = XposedHelpers.findClass(
                "com.android.quickstep.OplusBaseTouchInteractionService",
                classLoader,
            )
            val service = readStaticField(serviceClass, "f18246c0")
                ?: return@runCatching false
            val gestureState = XposedHelpers.callMethod(service, "t")
                ?: return@runCatching false
            XposedHelpers.callMethod(gestureState, "x", launch)
            true
        }.getOrDefault(false)
        if (!queued) launch.run()
    }

    private fun readStaticField(targetClass: Class<*>, fieldName: String): Any? {
        val field = targetClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(null)
    }

    /**
     * ColorOS 17's navigation settings no longer infer this UI from the selected assistant.
     * It removes the whole Circle to Search category unless its process can see the Google
     * contextual-search system feature. The stock domestic Breeno feature is cached in this
     * process as well, so mirror the narrowly-scoped ROM XML changes here instead of changing
     * Settings' global CN/EXP identity.
     */
    private fun hookSettings(classLoader: ClassLoader) {
        val nativePowerEnabled = LspConfig.isAssistantNativePowerEnabledXposed()
        val nativeCircleEnabled = LspConfig.isAssistantNativeCircleEnabledXposed()
        if (!nativePowerEnabled && !nativeCircleEnabled) return

        if (nativeCircleEnabled) {
            hookContextualSearchSystemFeature(SETTINGS_PACKAGE, classLoader)
            hookSettingsCircleToSearchFeature(classLoader)
        }
        hookNativeAssistantFeatureRoute(SETTINGS_PACKAGE, classLoader)
        hookSettingsBreenoSpeechFeature(classLoader)
        if (nativePowerEnabled) {
            hookSettingsPowerMenuDetails(classLoader)
        }
    }

    private fun hookSettingsPowerMenuDetails(classLoader: ClassLoader) {
        val controllerClass = XposedHelpers.findClassIfExists(
            "com.oplus.settings.feature.convenient.PowerMenuPreferenceController",
            classLoader,
        )
        controllerClass?.declaredMethods
            ?.filter { it.name == "lambda\$displayPreference\$0" }
            ?.forEach { method ->
                val key = "settings:power-menu-details:${method.toGenericString()}"
                if (!installedHooks.add(key)) return@forEach
                runCatching {
                    XposedBridge.hookMethod(
                        method,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                if (!LspConfig.isAssistantNativePowerEnabledXposed()) return
                                val context = findContext(param.thisObject) ?: return
                                openNativePowerMenuSettings(context)
                                param.result = null
                            }
                        },
                    )
                }.onFailure {
                    installedHooks.remove(key)
                    log("Failed to connect the power-menu details page", it)
                }
            }

        val utilsClass = XposedHelpers.findClassIfExists(
            "com.android.settings.gestures.PowerMenuSettingsUtils",
            classLoader,
        ) ?: return
        listOf(
            "isLongPressPowerSettingAvailable",
            "isLongPressPowerForAssistantEnabled",
            "setLongPressPowerForAssistant",
            "setLongPressPowerForPowerMenu",
        ).forEach { methodName ->
            val key = "settings:power-menu-utils:$methodName"
            if (!installedHooks.add(key)) return@forEach
            runCatching {
                XposedBridge.hookAllMethods(
                    utilsClass,
                    methodName,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (!LspConfig.isAssistantNativePowerEnabledXposed()) return
                            val context = param.args.firstOrNull() as? Context ?: return
                            when (methodName) {
                                "isLongPressPowerSettingAvailable" -> param.result = true
                                "isLongPressPowerForAssistantEnabled" ->
                                    param.result = isPowerAssistantEnabled(context)

                                "setLongPressPowerForAssistant" -> {
                                    writeNativePowerChoice(context, assistant = true)
                                    param.result = true
                                }

                                "setLongPressPowerForPowerMenu" -> {
                                    writeNativePowerChoice(context, assistant = false)
                                    param.result = true
                                }
                            }
                        }
                    },
                )
            }.onFailure {
                installedHooks.remove(key)
                log("Failed to bridge $methodName to the C17 power setting", it)
            }
        }
        log("Connected the inert C17 power-menu details entry to its native fragment")
    }

    private fun openNativePowerMenuSettings(context: Context) {
        val intent = Intent().apply {
            component = ComponentName(SETTINGS_PACKAGE, "com.android.settings.SubSettings")
            putExtra(
                ":settings:show_fragment",
                "com.android.settings.gestures.PowerMenuSettings",
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun writeNativePowerChoice(context: Context, assistant: Boolean) {
        val resolver = context.contentResolver
        Settings.System.putInt(
            resolver,
            SYSTEM_QUICK_TURN_ON_VOICE_ASSISTANT,
            if (assistant) 1 else 0,
        )
        Settings.Secure.putInt(
            resolver,
            SECURE_DISABLE_GOOGLE_ASSIST_POWER_WAKEUP,
            if (assistant) 0 else 1,
        )
        Settings.Global.putInt(
            resolver,
            "power_button_long_press",
            if (assistant) 5 else 1,
        )
        if (assistant) {
            Settings.Global.putInt(resolver, "key_chord_power_volume_up", 2)
        }
    }

    private fun hookSettingsCircleToSearchFeature(classLoader: ClassLoader) {
        val featureClass = XposedHelpers.findClassIfExists(
            "com.oplus.settings.utils.CustomizeFeatureUtils",
            classLoader,
        ) ?: return
        val key = "settings:native-circle-feature"
        if (!installedHooks.add(key)) return
        runCatching {
            XposedBridge.hookAllMethods(
                featureClass,
                "isSupportCircleToSearch",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (LspConfig.isAssistantNativeCircleEnabledXposed()) {
                            param.result = true
                        }
                    }
                },
            )
            log("Enabled the ColorOS 17 Circle to Search settings category")
        }.onFailure {
            installedHooks.remove(key)
            log("Failed to enable the ColorOS 17 Circle to Search settings category", it)
        }
    }

    private fun hookSettingsBreenoSpeechFeature(classLoader: ClassLoader) {
        val featureClass = XposedHelpers.findClassIfExists(
            "com.oplus.settings.utils.FeatureUtils",
            classLoader,
        ) ?: return
        val key = "settings:disable-breeno-speech-feature"
        if (!installedHooks.add(key)) return
        runCatching {
            XposedBridge.hookAllMethods(
                featureClass,
                "isSupportSpeechAssist",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isAnyNativeAssistantEnabled()) {
                            param.result = false
                        }
                    }
                },
            )
            log("Disabled the conflicting Breeno navigation assistant setting")
        }.onFailure {
            installedHooks.remove(key)
            log("Failed to disable the Breeno navigation assistant setting", it)
        }
    }

    /**
     * Runtime equivalent of the two ROM XML edits used by ColorOS.EU. These hooks only replace
     * the exact assistant feature keys; they deliberately do not turn the whole CN SystemUI into
     * an EXP build.
     */
    private fun hookSystemUiExperimentalRoutes(classLoader: ClassLoader) {
        val nativePowerEnabled = LspConfig.isAssistantNativePowerEnabledXposed()
        val nativeCircleEnabled = LspConfig.isAssistantNativeCircleEnabledXposed()
        if (nativePowerEnabled) {
            hookNativeAssistantFeatureRoute(SYSTEM_UI_PACKAGE, classLoader)
            hookSystemUiBreenoAssistantFeature(classLoader)
            hookGoogleAssistantAppFeature(classLoader)
            hookSystemUiAssistantTutorialState(classLoader)
        }
        if (nativeCircleEnabled) {
            hookContextualSearchSystemFeature(SYSTEM_UI_PACKAGE, classLoader)
            hookNativeCircleSystemUi(classLoader)
            hookSystemUiCircleToSearchInterceptor(classLoader)
        }
        if (nativePowerEnabled || nativeCircleEnabled) {
            hookNativeAssistantSystemUiGate(classLoader)
        }
    }

    /** Keep the international one-time tutorial complete instead of resetting it on every radio change. */
    private fun hookSystemUiAssistantTutorialState(classLoader: ClassLoader) {
        val observerClass = XposedHelpers.findClassIfExists(
            "com.oplus.systemui.shutdown.observer.AssistSettingObserver",
            classLoader,
        )
        if (observerClass != null) {
            val key = "systemui:assistant-tutorial-observer"
            if (installedHooks.add(key)) {
                runCatching {
                    XposedBridge.hookAllMethods(
                        observerClass,
                        "onChange",
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                if (LspConfig.isAssistantNativePowerEnabledXposed()) {
                                    param.result = null
                                }
                            }
                        },
                    )
                }.onFailure {
                    installedHooks.remove(key)
                    log("Failed to suppress the domestic tutorial reset", it)
                }
            }
        }

        val managerClass = XposedHelpers.findClassIfExists(
            "com.oplus.systemui.shutdown.AssistManagerImpl",
            classLoader,
        ) ?: return
        val key = "systemui:assistant-tutorial-migration"
        if (!installedHooks.add(key)) return
        runCatching {
            XposedBridge.hookAllConstructors(
                managerClass,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!LspConfig.isAssistantNativePowerEnabledXposed()) return
                        val context = param.args.firstOrNull() as? Context
                            ?: findContext(param.thisObject)
                            ?: return
                        val resolver = context.contentResolver
                        val stored = Settings.Secure.getString(
                            resolver,
                            SECURE_ASSISTANT_TUTORIAL_STARTED,
                        )
                        if (stored == "0" &&
                            Settings.System.getInt(
                                resolver,
                                SYSTEM_QUICK_TURN_ON_VOICE_ASSISTANT,
                                1,
                            ) != 0 &&
                            resolveDefaultAssistant(context) != null
                        ) {
                            Settings.Secure.putInt(
                                resolver,
                                SECURE_ASSISTANT_TUTORIAL_STARTED,
                                1,
                            )
                            log("Migrated the already-seen C17 assistant tutorial state")
                        }
                    }
                },
            )
        }.onFailure {
            installedHooks.remove(key)
            log("Failed to migrate the assistant tutorial state", it)
        }
    }

    /**
     * ShutdownFeatureOption stores the Oplus feature result in a Kotlin Lazy inside SystemUI.
     * Removing the ROM XML makes this false from process start; hook the public accessor too so
     * a value cached before package-ready cannot keep the domestic power-button route alive.
     */
    private fun hookSystemUiBreenoAssistantFeature(classLoader: ClassLoader) {
        val featureClass = XposedHelpers.findClassIfExists(
            "com.oplusos.systemui.common.feature.ShutdownFeatureOption",
            classLoader,
        ) ?: return
        val key = "systemui:disable-breeno-power-assistant"
        if (!installedHooks.add(key)) return
        runCatching {
            XposedBridge.hookAllMethods(
                featureClass,
                "isBreenoAsVoiceAssistant",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (LspConfig.isAssistantNativePowerEnabledXposed()) {
                            param.result = false
                        }
                    }
                },
            )
            log("Disabled the cached Breeno power-button assistant route in SystemUI")
        }.onFailure {
            installedHooks.remove(key)
            log("Failed to disable the Breeno power-button assistant route in SystemUI", it)
        }
    }

    /**
     * C17's AssistManager rejects every normal assistant request on a domestic build before it
     * reaches the selected Google assistant. Scope the EXP-region result to the exact native
     * invocation currently being handled, rather than changing SystemUI's region globally.
     */
    private fun hookNativeAssistantSystemUiGate(classLoader: ClassLoader) {
        val assistManagerClass = XposedHelpers.findClassIfExists(
            "com.android.systemui.assist.AssistManager",
            classLoader,
        )
        if (assistManagerClass != null) {
            val key = "systemui:native-assistant-route-scope"
            if (installedHooks.add(key)) {
                runCatching {
                    XposedBridge.hookAllMethods(
                        assistManagerClass,
                        "startAssist\$1",
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                val enabled = isEnabledNativeInvocation(param.args.firstOrNull())
                                val frames = nativeAssistantRouteFrames.get()
                                    ?: ArrayDeque<Boolean>().also(nativeAssistantRouteFrames::set)
                                frames.addLast(enabled)
                                if (!enabled) return
                                val invocationType =
                                    (param.args.firstOrNull() as? Bundle)
                                        ?.getInt("invocation_type", 0)
                                log("SystemUI received native assistant invocation type $invocationType")
                                nativeAssistantRouteDepth.set(
                                    (nativeAssistantRouteDepth.get() ?: 0) + 1,
                                )
                            }

                            override fun afterHookedMethod(param: MethodHookParam) {
                                val frames = nativeAssistantRouteFrames.get() ?: return
                                val enabled = frames.pollLast() ?: false
                                if (frames.isEmpty()) nativeAssistantRouteFrames.remove()
                                if (!enabled) return
                                val nextDepth = (nativeAssistantRouteDepth.get() ?: 1) - 1
                                if (nextDepth > 0) {
                                    nativeAssistantRouteDepth.set(nextDepth)
                                } else {
                                    nativeAssistantRouteDepth.remove()
                                }
                            }
                        },
                    )
                    log("Hooked the ColorOS 17 SystemUI native assistant route")
                }.onFailure {
                    installedHooks.remove(key)
                    log("Failed to scope the ColorOS 17 native assistant route", it)
                }
            }
        }

        val featureClass = XposedHelpers.findClassIfExists(
            "com.oplusos.systemui.common.feature.FeatureOption",
            classLoader,
        ) ?: return
        val key = "systemui:native-assistant-exp-gate"
        if (!installedHooks.add(key)) return
        runCatching {
            XposedBridge.hookAllMethods(
                featureClass,
                "isExpRegion",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if ((nativeAssistantRouteDepth.get() ?: 0) > 0) {
                            param.result = true
                        }
                    }
                },
            )
        }.onFailure {
            installedHooks.remove(key)
            log("Failed to pass the ColorOS 17 assistant region gate", it)
        }
    }

    private fun isEnabledNativeInvocation(argument: Any?): Boolean {
        val invocationType = (argument as? Bundle)?.getInt("invocation_type", 0) ?: return false
        val context = systemContext ?: findSystemContext()
        return when (invocationType) {
            INVOCATION_TYPE_POWER_BUTTON_LONG_PRESS ->
                LspConfig.isAssistantNativePowerEnabledXposed() &&
                    isPowerAssistantEnabled(context)

            INVOCATION_TYPE_GESTURE_CORNER_ASSISTANT ->
                LspConfig.isAssistantNativeCircleEnabledXposed() &&
                    isGestureCornerAssistantEnabled(context)

            INVOCATION_TYPE_CIRCLE_TO_SEARCH,
            INVOCATION_TYPE_CIRCLE_TO_SEARCH_ALTERNATE,
            -> LspConfig.isAssistantNativeCircleEnabledXposed() &&
                isGestureCircleToSearchEnabled(context)

            else -> false
        }
    }

    /**
     * The C17 domestic SystemUI ships an empty OplusCircleToSearchManagerEx implementation,
     * whereas the international path consumes invocation types 8/1000 here and forwards them
     * to Android 17's contextual-search service. Restore only that missing interception layer;
     * ordinary assistant invocations (including power-button type 6) keep the stock route.
     */
    private fun hookSystemUiCircleToSearchInterceptor(classLoader: ClassLoader) {
        val managerClass = XposedHelpers.findClassIfExists(
            "com.android.systemui.navigationbar.otherbusiness.circlesearch." +
                "OplusCircleToSearchManagerEx",
            classLoader,
        ) ?: return
        val key = "systemui:native-circle-interceptor"
        if (!installedHooks.add(key)) return
        runCatching {
            XposedBridge.hookAllMethods(
                managerClass,
                "interceptStartAssistInternal",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!LspConfig.isAssistantNativeCircleEnabledXposed()) return
                        val context = findContext(param.thisObject) ?: systemContext
                        if (!isGestureCircleToSearchEnabled(context)) return
                        val bundle = param.args.firstOrNull() as? Bundle ?: return
                        val invocationType = bundle.getInt("invocation_type", 0)
                        if (invocationType != INVOCATION_TYPE_CIRCLE_TO_SEARCH &&
                            invocationType != INVOCATION_TYPE_CIRCLE_TO_SEARCH_ALTERNATE
                        ) return

                        val triggered = triggerCircleToSearchC17()
                        log(
                            "C17 contextual-search interception for invocation type " +
                                "$invocationType: $triggered",
                        )
                        if (triggered) {
                            param.result = true
                        }
                    }
                },
            )
            log("Hooked the C17 SystemUI contextual-search interceptor")
        }.onFailure {
            installedHooks.remove(key)
            log("Failed to hook the C17 SystemUI contextual-search interceptor", it)
        }
    }

    private fun hookNativeAssistantFeatureRoute(packageName: String, classLoader: ClassLoader) {
        val classNames = listOf(
            "com.oplus.content.OplusFeatureConfigManager",
            "com.android.server.content.OplusFeatureConfigManagerService",
        )
        classNames.forEach { className ->
            val cls = XposedHelpers.findClassIfExists(className, classLoader) ?: return@forEach
            cls.declaredMethods
                .filter { method ->
                    method.name in setOf("hasFeature", "hasFeatureMap") &&
                        method.returnType == java.lang.Boolean.TYPE &&
                        method.parameterTypes.firstOrNull() == String::class.java
                }
                .forEach { method ->
                    val key = "$packageName:native-assistant-feature:${method.toGenericString()}"
                    if (!installedHooks.add(key)) return@forEach
                    runCatching {
                        XposedBridge.hookMethod(
                            method,
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam) {
                                    val featureName = param.args.firstOrNull() as? String ?: return
                                    if (shouldSuppressBreenoSpeechFeature(packageName) &&
                                        featureName == BREENO_SPEECH_FEATURE
                                    ) {
                                        param.result = false
                                    }
                                    if ((packageName == SYSTEM_PACKAGE ||
                                            packageName == ANDROID_PACKAGE) &&
                                        LspConfig.isAssistantNativePowerEnabledXposed() &&
                                        LspConfig.isAssistantInternationalPowerChordEnabledXposed() &&
                                        featureName in setOf(
                                            LONG_PRESS_POWER_SHUTDOWN_FEATURE,
                                            SHORT_PRESS_POWER_SHUTDOWN_FEATURE,
                                        )
                                    ) {
                                        // This is the exact feature split used by StrategyShutdown:
                                        // both absent selects POWER_VOLUMEUP (immediate) / POWER.
                                        param.result = false
                                    }
                                }
                            },
                        )
                    }.onFailure {
                        installedHooks.remove(key)
                        log("Failed to override $BREENO_SPEECH_FEATURE in $className", it)
                    }
                }
        }
    }

    private fun shouldSuppressBreenoSpeechFeature(packageName: String): Boolean {
        return LspConfig.isAssistantNativePowerEnabledXposed() ||
            (packageName == SETTINGS_PACKAGE &&
                LspConfig.isAssistantNativeCircleEnabledXposed())
    }

    private fun isAnyNativeAssistantEnabled(): Boolean =
        LspConfig.isAssistantNativePowerEnabledXposed() ||
            LspConfig.isAssistantNativeCircleEnabledXposed()

    private fun hookGoogleAssistantAppFeature(classLoader: ClassLoader) {
        val providerClass = XposedHelpers.findClassIfExists(
            "com.oplus.coreapp.appfeature.AppFeatureProviderUtils",
            classLoader,
        )
        providerClass?.declaredMethods
            ?.filter { method ->
                method.returnType == java.lang.Boolean.TYPE &&
                    method.name in setOf("getBoolean", "isFeatureSupport") &&
                    method.parameterTypes.any { it == String::class.java }
            }
            ?.forEach { method ->
                val key = "systemui:native-assistant-app-feature:${method.toGenericString()}"
                if (!installedHooks.add(key)) return@forEach
                runCatching {
                    XposedBridge.hookMethod(
                        method,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                if (!LspConfig.isAssistantNativePowerEnabledXposed()) return
                                if (param.args.any { it == GOOGLE_ASSISTANT_APP_FEATURE }) {
                                    param.result = true
                                }
                            }
                        },
                    )
                }.onFailure {
                    installedHooks.remove(key)
                    log("Failed to override Google Assistant app feature", it)
                }
            }

        val optionClass = XposedHelpers.findClassIfExists(
            "com.oplusos.systemui.common.feature.QSFeatureOption",
            classLoader,
        ) ?: return
        forceStaticBoolean(optionClass, "isGoogleAssistantSupport", true)
        val key = "systemui:native-assistant-option"
        if (!installedHooks.add(key)) return
        runCatching {
            XposedBridge.hookAllMethods(
                optionClass,
                "loadAppFeature",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (LspConfig.isAssistantNativePowerEnabledXposed()) {
                            forceStaticBoolean(optionClass, "isGoogleAssistantSupport", true)
                        }
                    }
                },
            )
        }.onFailure {
            installedHooks.remove(key)
            log("Failed to keep Google Assistant SystemUI option enabled", it)
        }
    }

    private fun hookContextualSearchSystemFeature(
        packageName: String,
        classLoader: ClassLoader,
    ) {
        val classNames = buildList {
            add("android.app.ApplicationPackageManager")
            if (packageName == SYSTEM_PACKAGE || packageName == ANDROID_PACKAGE) {
                // The service-side hook makes the synthetic XML feature visible to unscoped
                // clients such as the Google app too, matching a real permissions XML entry.
                add("com.android.server.pm.PackageManagerService")
                add("com.android.server.pm.PackageManagerService\$IPackageManagerImpl")
                add("com.android.server.pm.ComputerEngine")
            }
        }
        classNames.forEach { className ->
            val cls = XposedHelpers.findClassIfExists(className, classLoader) ?: return@forEach
            cls.declaredMethods
                .filter { method ->
                    method.name == "hasSystemFeature" &&
                        method.returnType == java.lang.Boolean.TYPE &&
                        method.parameterTypes.firstOrNull() == String::class.java
                }
                .forEach { method ->
                    val key =
                        "$packageName:contextual-search-system-feature:${method.toGenericString()}"
                    if (!installedHooks.add(key)) return@forEach
                    runCatching {
                        XposedBridge.hookMethod(
                            method,
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam) {
                                    if (!LspConfig.isAssistantNativeCircleEnabledXposed()) return
                                    if (param.args.firstOrNull() == CONTEXTUAL_SEARCH_SYSTEM_FEATURE) {
                                        param.result = true
                                    }
                                }
                            },
                        )
                    }.onFailure {
                        installedHooks.remove(key)
                        log("Failed to expose contextual-search system feature in $className", it)
                    }
                }
        }
    }

    private fun hookNativeCircleSystemUi(classLoader: ClassLoader) {
        val customizeClass = XposedHelpers.findClassIfExists(
            "com.oplusos.systemui.common.feature.CustomizeFeatureOption",
            classLoader,
        )
        val featureClass = XposedHelpers.findClassIfExists(
            "com.oplusos.systemui.common.feature.FeatureOption",
            classLoader,
        )
        if (customizeClass != null) {
            forceStaticBoolean(customizeClass, "sIsSupportCircleToSearch", true)
        }
        if (featureClass != null && customizeClass != null) {
            val key = "systemui:native-circle-feature-option"
            if (installedHooks.add(key)) {
                runCatching {
                    XposedBridge.hookAllMethods(
                        featureClass,
                        "init",
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                if (LspConfig.isAssistantNativeCircleEnabledXposed()) {
                                    forceStaticBoolean(
                                        customizeClass,
                                        "sIsSupportCircleToSearch",
                                        true,
                                    )
                                }
                            }
                        },
                    )
                }.onFailure {
                    installedHooks.remove(key)
                    log("Failed to keep native Circle to Search enabled", it)
                }
            }
        }

        // On a phone with the normal visible gesture handle, C17's launcher CUI consumer only
        // owns touch interception/animation. SystemUI's HomeHandleTouchEvent still owns the
        // actual long-press recognition. Keep that stock recognizer alive and replace only its
        // domestic Breeno destination with the same invocation_type=8 AssistManager route used
        // by the international launcher branch. Either business can be selected by C17 at
        // runtime depending on the OCR compatibility metadata, so cover both implementations.
        listOf(
            "com.oplus.systemui.navigationbar.gesture.otherbusiness.SpeedChassistMainBusiness",
            "com.oplus.systemui.navigationbar.ocrscreen.OplusOcrScreenBusiness",
        ).forEach { className ->
            val businessClass = XposedHelpers.findClassIfExists(className, classLoader)
                ?: return@forEach
            hookNativeCircleSystemUiBusiness(businessClass, classLoader)
        }
    }

    private fun hookNativeCircleSystemUiBusiness(
        businessClass: Class<*>,
        classLoader: ClassLoader,
    ) {
        val key = "systemui:native-circle-business:${businessClass.name}"
        if (!installedHooks.add(key)) return
        runCatching {
            XposedBridge.hookAllMethods(
                businessClass,
                "onLongPressed",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!LspConfig.isAssistantNativeCircleEnabledXposed()) return
                        val context = findContext(param.thisObject) ?: systemContext
                        if (!isGestureCircleToSearchEnabled(context)) return
                        if (dispatchSystemUiNativeAssistant(
                                classLoader = classLoader,
                                invocationType = INVOCATION_TYPE_CIRCLE_TO_SEARCH,
                            )
                        ) {
                            // Recognition, pointer pilfering and the launcher's press animation
                            // have already run in C17. Suppress only the final Breeno/OCR action.
                            param.result = null
                        }
                    }
                },
            )
            log("Hooked C17 gesture-bar recognition: ${businessClass.name}")
        }.onFailure {
            installedHooks.remove(key)
            log("Failed to hook C17 gesture-bar recognition: ${businessClass.name}", it)
        }
    }

    private fun dispatchSystemUiNativeAssistant(
        classLoader: ClassLoader,
        invocationType: Int,
    ): Boolean {
        return runCatching {
            val dependencyClass = XposedHelpers.findClass(
                "com.android.systemui.Dependency",
                classLoader,
            )
            val assistManagerClass = XposedHelpers.findClass(
                "com.android.systemui.assist.AssistManager",
                classLoader,
            )
            val dependency = readStaticField(dependencyClass, "sDependency")
                ?: error("SystemUI Dependency is unavailable")
            val assistManager = XposedHelpers.callMethod(
                dependency,
                "getDependencyInner",
                assistManagerClass,
            ) ?: error("SystemUI AssistManager is unavailable")
            val bundle = Bundle().apply {
                putInt("invocation_type", invocationType)
            }
            XposedHelpers.callMethod(assistManager, "startAssist\$1", bundle)
            log("Forwarded gesture-bar invocation type $invocationType inside SystemUI")
            true
        }.onFailure {
            log("Failed to forward the gesture-bar invocation inside SystemUI", it)
        }.getOrDefault(false)
    }

    private fun forceStaticBoolean(cls: Class<*>, fieldName: String, value: Boolean) {
        runCatching {
            cls.getDeclaredField(fieldName).apply { isAccessible = true }.setBoolean(null, value)
        }.onFailure {
            log("Failed to set ${cls.name}#$fieldName", it)
        }
    }

    private fun hookGestureBar(classLoader: ClassLoader) {
        val classNames = buildList {
            add("com.oplus.systemui.navigationbar.ocrscreen.OplusOcrScreenBusiness")
            if (LspConfig.isAssistantGestureCircleC17EnabledXposed()) {
                add("com.oplus.systemui.navigationbar.gesture.otherbusiness.SpeedChassistMainBusiness")
            }
        }
        classNames.forEach { className ->
            val cls = XposedHelpers.findClassIfExists(className, classLoader) ?: return@forEach
            hookGestureBarClass(cls)
        }
    }

    private fun hookGestureBarClass(cls: Class<*>) {
        val key = "systemui:gesture:${cls.name}"
        if (!installedHooks.add(key)) return

        runCatching {
            XposedBridge.hookAllMethods(
                cls,
                "onLongPressed",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!isLegacyGestureCircleEnabled() ||
                            LspConfig.isAssistantNativeCircleEnabledXposed()
                        ) return
                        findContext(param.thisObject)?.let(::performHapticFeedback)
                        val triggered = if (LspConfig.isAssistantGestureCircleC17EnabledXposed()) {
                            triggerCircleToSearchC17()
                        } else {
                            triggerCircleToSearchLegacy()
                        }
                        if (triggered) {
                            param.result = null
                        }
                    }
                }
            )
            log("Gesture bar long press hooked: ${cls.name}")
        }.onFailure {
            installedHooks.remove(key)
            log("Failed to hook gesture bar long press", it)
        }
    }

    private fun hookSystemUiAttachRetry() {
        val key = "systemui:application-attach"
        if (!installedHooks.add(key)) return
        runCatching {
            XposedHelpers.findAndHookMethod(
                Application::class.java,
                "attach",
                Context::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val context = param.args.firstOrNull() as? Context ?: return
                        if (systemContext == null) {
                            systemContext = context.applicationContext ?: context
                        }
                        sequenceOf(
                            context.classLoader,
                            param.thisObject?.javaClass?.classLoader,
                            Thread.currentThread().contextClassLoader
                        ).filterNotNull().distinct().forEach { classLoader ->
                            hookSystemUiExperimentalRoutes(classLoader)
                            if (isLegacyGestureCircleEnabled() &&
                                !LspConfig.isAssistantNativeCircleEnabledXposed()
                            ) {
                                hookGestureBar(classLoader)
                            }
                        }
                    }
                }
            )
        }.onFailure {
            installedHooks.remove(key)
            log("Failed to install SystemUI assistant retry", it)
        }
    }

    private fun hookSystemContext(packageName: String, classLoader: ClassLoader) {
        val key = "$packageName:system-context"
        val cls = XposedHelpers.findClassIfExists("com.android.server.SystemService", classLoader) ?: return
        if (!installedHooks.add(key)) return
        runCatching {
            cls.declaredMethods
                .filter { method -> method.name == "getContext" }
                .forEach { method ->
                    val handleKey = "assistant:system-context:${method.toGenericString()}"
                    ModernHookRegistry.installCompat(
                        handleKey,
                        method,
                        object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        (param.result as? Context)?.let {
                            systemContext = it
                            sequenceOf(
                                param.thisObject?.javaClass?.classLoader,
                                it.classLoader,
                                Thread.currentThread().contextClassLoader
                            ).filterNotNull().distinct().forEach { runtimeClassLoader ->
                                retrySystemHooks(packageName, runtimeClassLoader)
                            }
                            // SystemService#getContext is common during boot. It is needed only to
                            // capture the first valid context and retry late OEM targets once.
                            ModernHookRegistry.unhookPrefix("assistant:system-context:")
                        }
                    }
                        },
                    )
                }
        }.onFailure {
            installedHooks.remove(key)
            log("Failed to hook SystemService.getContext", it)
        }
    }

    private fun retrySystemHooks(packageName: String, classLoader: ClassLoader) {
        if (isAnyGestureCircleEnabled()) {
            hookContextualSearch(packageName, classLoader)
        }
        if (LspConfig.isAssistantNativePowerEnabledXposed()) {
            hookNativeAssistantFeatureRoute(packageName, classLoader)
            hookInternationalPowerStrategy(packageName, classLoader)
            hookNativePowerLongPress(packageName, classLoader)
            hookVoiceInteractionServiceRepair(packageName, classLoader)
        } else if (LspConfig.getAssistantPowerModeXposed() != LspConfig.ASSISTANT_POWER_MODE_NONE) {
            hookPowerLongPress(packageName, classLoader)
            hookOriginalAssistantStart(packageName, classLoader)
        }
    }

    /** Select C17's built-in EXP shutdown styles without replacing its key-combination rule. */
    private fun hookInternationalPowerStrategy(packageName: String, classLoader: ClassLoader) {
        val cls = XposedHelpers.findClassIfExists(
            "com.android.server.policy.StrategyShutdown",
            classLoader,
        ) ?: return
        val key = "$packageName:international-power-strategy"
        if (!installedHooks.add(key)) return
        runCatching {
            XposedBridge.hookAllMethods(
                cls,
                "refreshShutdownStyle",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!LspConfig.isAssistantNativePowerEnabledXposed() ||
                            !LspConfig.isAssistantInternationalPowerChordEnabledXposed()
                        ) return
                        val source = param.thisObject ?: return
                        XposedHelpers.setObjectField(source, "mShutdownByPowerEnabled", false)
                        XposedHelpers.setObjectField(
                            source,
                            "mShutDownByLongPressPowerEnabled",
                            false,
                        )
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!LspConfig.isAssistantNativePowerEnabledXposed() ||
                            !LspConfig.isAssistantInternationalPowerChordEnabledXposed()
                        ) return
                        val source = param.thisObject ?: return
                        val style = runCatching {
                            XposedHelpers.getObjectField(
                                source,
                                "mCurrentShutdownStyle",
                            )
                        }.getOrNull()
                        log("Selected native C17 international shutdown style: $style")
                    }
                },
            )
            log("Hooked the native C17 international power-key strategy")
        }.onFailure {
            installedHooks.remove(key)
            log("Failed to select the international power-key strategy", it)
        }
    }

    /**
     * C17 keeps both destinations in PhoneWindowManagerExtImpl.startSpeech(): the domestic
     * feature value starts Breeno directly, while the international value calls the framework's
     * native type-6 assist route. Correct only the cached routing state immediately before that
     * stock decision and let C17 retain its own haptic, deduplication and launch bundle.
     */
    private fun hookNativePowerLongPress(packageName: String, classLoader: ClassLoader) {
        val cls = XposedHelpers.findClassIfExists(
            "com.android.server.policy.PhoneWindowManagerExtImpl",
            classLoader,
        ) ?: return
        val key = "$packageName:native-power-long-press:${cls.name}"
        if (!installedHooks.add(key)) return
        runCatching {
            val targets = cls.declaredMethods
                .filter { method ->
                    method.name == "startSpeech" &&
                        method.parameterTypes.contentEquals(
                            arrayOf(
                                Integer.TYPE,
                                Integer.TYPE,
                                java.lang.Long.TYPE,
                            ),
                        )
                }
            check(targets.isNotEmpty()) { "C17 startSpeech(int, int, long) was not found" }
            targets.forEach { method ->
                    XposedBridge.hookMethod(
                        method,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                if (!LspConfig.isAssistantNativePowerEnabledXposed()) return
                                val source = param.thisObject ?: return
                                val context = findContext(source) ?: return
                                applyC17NativePowerConfiguration(source, context)
                            }
                        },
                    )
                }
            XposedBridge.hookAllMethods(
                cls,
                "isSpeechDisabled",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!LspConfig.isAssistantNativePowerEnabledXposed()) return
                        val context = findContext(param.thisObject) ?: systemContext ?: return
                        param.result = !isPowerAssistantEnabled(context)
                    }
                },
            )
            XposedBridge.hookAllMethods(
                cls,
                "registerSettingsForOplusLocked",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!LspConfig.isAssistantNativePowerEnabledXposed()) return
                        val source = param.thisObject ?: return
                        val context = findContext(source) ?: systemContext ?: return
                        registerPowerSettingBridge(source, context)
                        applyC17NativePowerConfiguration(source, context)
                    }
                },
            )
            log("Hooked the ColorOS 17 native power-button assistant entry")
        }.onFailure {
            installedHooks.remove(key)
            log("Failed to hook the ColorOS 17 native power-button assistant entry", it)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun applyC17NativePowerConfiguration(source: Any, context: Context) {
        runCatching {
            XposedHelpers.setObjectField(source, "mSpeechAsssistForBreeno", false)
            XposedHelpers.setObjectField(
                source,
                "mhasGoogleAssistant",
                resolveDefaultAssistant(context) != null,
            )

            // C17 EXP stores 0=enabled, 1=disabled and -1=not configured/show guide.
            // Keep those values unchanged so the stock startSpeech() state machine remains intact.
            val internationalPowerState = Settings.Secure.getInt(
                context.contentResolver,
                SECURE_DISABLE_GOOGLE_ASSIST_POWER_WAKEUP,
                -1,
            )
            val currentUser = XposedHelpers.getObjectField(source, "mCurrentUserId") as? Int ?: 0
            val enabledMap = XposedHelpers.getObjectField(source, "mSpeechEnabledMap")
                as? MutableMap<Any?, Any?>
            enabledMap?.set(currentUser, internationalPowerState)
            log("Selected the ColorOS 17 native type-6 power assistant route")
        }.onFailure {
            log("Failed to select the ColorOS 17 native power assistant route", it)
        }
    }

    /**
     * C17 international uses Assistant Role as the source of truth, then writes both secure
     * components. VIMS observes voice_interaction_service and owns the third, live binding layer.
     */
    private fun hookVoiceInteractionServiceRepair(
        packageName: String,
        classLoader: ClassLoader,
    ) {
        val cls = XposedHelpers.findClassIfExists(
            "com.android.server.voiceinteraction.VoiceInteractionManagerService\$" +
                "VoiceInteractionManagerServiceStub",
            classLoader,
        ) ?: return
        hookNativeAssistantSessionRecovery(packageName, cls, classLoader)
        val key = "$packageName:voice-interaction-service-repair:${cls.name}"
        if (!installedHooks.add(key)) return
        runCatching {
            XposedBridge.hookAllMethods(
                cls,
                "systemRunning",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        voiceInteractionManagerStub = param.thisObject
                        val context = systemContext ?: findContext(param.thisObject) ?: return
                        val userId = resolveCurrentUserId()
                        reconcileAssistantRoleState(context, userId)
                        scheduleAssistantRoleReconciliation(context, userId)
                    }
                },
            )
            log("Hooked C17 voice-interaction service restoration")
        }.onFailure {
            installedHooks.remove(key)
            log("Failed to hook C17 voice-interaction service restoration", it)
        }

        val serviceClass = XposedHelpers.findClassIfExists(
            "com.android.server.voiceinteraction.VoiceInteractionManagerService",
            classLoader,
        ) ?: return
        val unlockKey = "$packageName:voice-interaction-service-user-unlock"
        if (!installedHooks.add(unlockKey)) return
        runCatching {
            XposedBridge.hookAllMethods(
                serviceClass,
                "onUserUnlocking",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val context = systemContext ?: findContext(param.thisObject) ?: return
                        val userId = extractUserId(param.args.firstOrNull())
                            ?: resolveCurrentUserId()
                        reconcileAssistantRoleState(context, userId)
                        scheduleAssistantRoleReconciliation(context, userId)
                    }
                },
            )
        }.onFailure {
            installedHooks.remove(unlockKey)
            log("Failed to restore the voice-interaction service after user unlock", it)
        }

        val roleObserverClass = XposedHelpers.findClassIfExists(
            "com.android.server.voiceinteraction.VoiceInteractionManagerService\$" +
                "VoiceInteractionManagerServiceStub\$RoleObserver",
            classLoader,
        ) ?: return
        val roleKey = "$packageName:assistant-role-reconciliation"
        if (!installedHooks.add(roleKey)) return
        runCatching {
            XposedBridge.hookAllMethods(
                roleObserverClass,
                "onRoleHoldersChanged",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.args.firstOrNull() != ASSISTANT_ROLE) return
                        val context = systemContext ?: findContext(param.thisObject) ?: return
                        val userId = extractUserId(param.args.getOrNull(1))
                            ?: resolveCurrentUserId()
                        reconcileAssistantRoleState(context, userId)
                        scheduleAssistantRoleReconciliation(context, userId)
                    }
                },
            )
            log("Hooked the C17 Assistant Role state reconciliation")
        }.onFailure {
            installedHooks.remove(roleKey)
            log("Failed to hook the C17 Assistant Role state reconciliation", it)
        }
    }

    private fun hookNativeAssistantSessionRecovery(
        packageName: String,
        cls: Class<*>,
        classLoader: ClassLoader,
    ) {
        val key = "$packageName:native-assistant-session-recovery"
        if (!installedHooks.add(key)) return
        runCatching {
            val showMethods = cls.declaredMethods.filter {
                it.name == "showSessionForActiveService" && it.parameterTypes.size == 5
            }
            check(showMethods.isNotEmpty()) { "Native assistant show method not found" }
            showMethods.forEach { method ->
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val stub = param.thisObject ?: return
                        NativeAssistantSessionRecovery.prepare(stub, method, param.args)
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        NativeAssistantSessionRecovery.finishRequest(
                            param.args.getOrNull(3),
                            param.result == true,
                            param.throwable,
                        )
                    }
                })
            }
            val implClass = XposedHelpers.findClass(
                "com.android.server.voiceinteraction.VoiceInteractionManagerServiceImpl",
                classLoader,
            )
            XposedBridge.hookAllMethods(implClass, "showSessionLocked", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val impl = param.thisObject ?: return
                    NativeAssistantSessionRecovery.prepareCornerSession(
                        impl,
                        param.args.firstOrNull() as? Bundle,
                    )
                }
            })
            listOf("hideCurrentSession", "hideSessionFromSession", "finish").forEach { name ->
                XposedBridge.hookAllMethods(cls, name, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (name == "hideCurrentSession") {
                            NativeAssistantSessionRecovery.cancel()
                        } else {
                            val stub = param.thisObject ?: return
                            NativeAssistantSessionRecovery.cancelSession(stub, param.args.firstOrNull())
                        }
                    }
                })
            }
            log("Hooked native Google assistant session timeout recovery")
        }.onFailure {
            installedHooks.remove(key)
            log("Failed to hook native assistant session recovery", it)
        }
    }

    private fun reconcileAssistantRoleState(context: Context, userId: Int): Boolean {
        if (!LspConfig.isAssistantNativePowerEnabledXposed()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val token = Binder.clearCallingIdentity()
        return runCatching {
            val userHandleClass = Class.forName("android.os.UserHandle")
            val user = userHandleClass.getMethod("of", Integer.TYPE).invoke(null, userId)
            val userContext = Context::class.java
                .getMethod("createContextAsUser", userHandleClass, Integer.TYPE)
                .invoke(context, user, 0) as Context
            val roleManager = context.getSystemService(RoleManager::class.java)
                ?: return@runCatching false
            if (!roleManager.isRoleAvailable(ASSISTANT_ROLE)) return@runCatching false

            @Suppress("UNCHECKED_CAST")
            val holders = roleManager.javaClass
                .getMethod("getRoleHoldersAsUser", String::class.java, userHandleClass)
                .invoke(roleManager, ASSISTANT_ROLE, user) as? List<String>
                ?: emptyList()
            val target = if (holders.isEmpty()) {
                AssistantSecureState("", "")
            } else {
                resolveRoleAssistantState(userContext, holders.first())
                    ?: return@runCatching false
            }

            val resolver = userContext.contentResolver
            val oldAssistant = Settings.Secure.getString(resolver, SECURE_ASSISTANT).orEmpty()
            val oldVoiceService = Settings.Secure.getString(
                resolver,
                SECURE_VOICE_INTERACTION_SERVICE,
            ).orEmpty()
            val assistantWritten = oldAssistant == target.assistant || Settings.Secure.putString(
                resolver,
                SECURE_ASSISTANT,
                target.assistant,
            )
            val voiceWritten = oldVoiceService == target.voiceInteractionService ||
                Settings.Secure.putString(
                    resolver,
                    SECURE_VOICE_INTERACTION_SERVICE,
                    target.voiceInteractionService,
                )
            if (oldAssistant != target.assistant || oldVoiceService != target.voiceInteractionService) {
                log(
                    "Reconciled Assistant Role for user $userId: assistant=${target.assistant}, " +
                        "voiceInteractionService=${target.voiceInteractionService}",
                )
            }
            assistantWritten && voiceWritten
        }.onFailure {
            log("Failed to reconcile the C17 Assistant Role state for user $userId", it)
        }.also {
            Binder.restoreCallingIdentity(token)
        }.getOrDefault(false)
    }

    private fun scheduleAssistantRoleReconciliation(context: Context, userId: Int) {
        val handler = Handler(Looper.getMainLooper())
        listOf(1_000L, 5_000L, 15_000L, 30_000L).forEach { delayMs ->
            handler.postDelayed(
                { reconcileAssistantRoleState(context, userId) },
                delayMs,
            )
        }
    }

    private fun resolveRoleAssistantState(
        userContext: Context,
        packageName: String,
    ): AssistantSecureState? {
        val packageManager = userContext.packageManager
        val serviceQueryFlags = 0x000C0080 // GET_META_DATA + both direct-boot modes.
        val services = packageManager.queryIntentServices(
            Intent(VOICE_INTERACTION_SERVICE_ACTION).setPackage(packageName),
            serviceQueryFlags,
        )
        services.forEach { resolveInfo ->
            val serviceInfo = resolveInfo.serviceInfo ?: return@forEach
            if (serviceInfo.permission != BIND_VOICE_INTERACTION_PERMISSION) return@forEach
            val capability = inspectVoiceInteractionService(packageManager, serviceInfo)
                ?: return@forEach
            if (!capability.supportsAssist) return@forEach

            // Match C17/AOSP RoleObserver exactly: a supports-assist service without a declared
            // RecognitionService is cleared to avoid a boot loop on older service implementations.
            val component = if (capability.hasRecognitionService) {
                ComponentName(serviceInfo.packageName, serviceInfo.name).flattenToShortString()
            } else {
                ""
            }
            return AssistantSecureState(component, component)
        }

        val activity = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_ASSIST).setPackage(packageName),
            0x000D0000, // MATCH_DEFAULT_ONLY + both direct-boot modes.
        ).firstOrNull()?.activityInfo ?: return null
        return AssistantSecureState(
            assistant = ComponentName(activity.packageName, activity.name).flattenToShortString(),
            voiceInteractionService = "",
        )
    }

    private fun inspectVoiceInteractionService(
        packageManager: android.content.pm.PackageManager,
        serviceInfo: android.content.pm.ServiceInfo,
    ): VoiceInteractionCapability? {
        return runCatching {
            val infoClass = Class.forName("android.service.voice.VoiceInteractionServiceInfo")
            val info = infoClass
                .getConstructor(
                    android.content.pm.PackageManager::class.java,
                    android.content.pm.ServiceInfo::class.java,
                )
                .newInstance(packageManager, serviceInfo)
            VoiceInteractionCapability(
                supportsAssist = infoClass.getMethod("getSupportsAssist").invoke(info) == true,
                hasRecognitionService =
                    infoClass.getMethod("getRecognitionService").invoke(info) != null,
            )
        }.onFailure {
            log("Failed to inspect ${serviceInfo.packageName}/${serviceInfo.name}", it)
        }.getOrNull()
    }

    private fun registerPowerSettingBridge(source: Any, context: Context) {
        if (powerSettingBridgeObservers != null) return
        synchronized(this) {
            if (powerSettingBridgeObservers != null) return
            val resolver = context.contentResolver
            val handler = Handler(Looper.getMainLooper())
            val domesticObserver = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    syncPowerChoiceFromDomesticSetting(source, context)
                }
            }
            val internationalObserver = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    syncPowerChoiceFromInternationalSetting(source, context)
                }
            }
            resolver.registerContentObserver(
                Settings.System.getUriFor(SYSTEM_QUICK_TURN_ON_VOICE_ASSISTANT),
                false,
                domesticObserver,
            )
            resolver.registerContentObserver(
                Settings.Secure.getUriFor(SECURE_DISABLE_GOOGLE_ASSIST_POWER_WAKEUP),
                false,
                internationalObserver,
            )
            powerSettingBridgeObservers = listOf(domesticObserver, internationalObserver)
            lastObservedPowerAssistantChoice = Settings.System.getInt(
                resolver,
                SYSTEM_QUICK_TURN_ON_VOICE_ASSISTANT,
                1,
            ) != 0

            if (Settings.Secure.getInt(
                    resolver,
                    SECURE_DISABLE_GOOGLE_ASSIST_POWER_WAKEUP,
                    -1,
                ) == -1
            ) {
                syncPowerChoiceFromDomesticSetting(source, context)
            } else {
                syncPowerChoiceFromInternationalSetting(source, context)
            }
            log("Connected the ColorOS power-button UI to the international assistant setting")
        }
    }

    private fun syncPowerChoiceFromDomesticSetting(source: Any, context: Context) {
        val resolver = context.contentResolver
        val domesticValue = Settings.System.getInt(
            resolver,
            SYSTEM_QUICK_TURN_ON_VOICE_ASSISTANT,
            1,
        )
        val assistantEnabled = domesticValue != 0
        val previousAssistantEnabled = lastObservedPowerAssistantChoice
        lastObservedPowerAssistantChoice = assistantEnabled
        val targetInternationalValue = if (assistantEnabled) 0 else 1
        val currentInternationalValue = Settings.Secure.getInt(
            resolver,
            SECURE_DISABLE_GOOGLE_ASSIST_POWER_WAKEUP,
            -1,
        )
        if (currentInternationalValue != targetInternationalValue) {
            Settings.Secure.putInt(
                resolver,
                SECURE_DISABLE_GOOGLE_ASSIST_POWER_WAKEUP,
                targetInternationalValue,
            )
        }
        applyC17NativePowerConfiguration(source, context)
        if (assistantEnabled && previousAssistantEnabled == false) {
            refreshLiveAssistantBinding(context, resolveCurrentUserId())
        }
    }

    /**
     * Re-selecting an Assistant Role on international C17 does more than rewrite its two secure
     * component strings: VIMS tears down and recreates the active implementation, which also
     * republishes assistant availability to SystemUI and the launcher. The domestic power-menu
     * switch never touches the Role, so perform that same live refresh only on the off -> on edge.
     */
    private fun refreshLiveAssistantBinding(context: Context, userId: Int) {
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            reconcileAssistantRoleState(context, userId)
            val stub = voiceInteractionManagerStub
            if (stub == null) {
                log("Skipped live Assistant Role refresh because VIMS is not ready")
                return@post
            }
            runCatching {
                XposedHelpers.callMethod(stub, "switchImplementationIfNeeded", true)
                log("Refreshed the live C17 Assistant Role binding for user $userId")
            }.onFailure {
                log("Failed to refresh the live C17 Assistant Role binding for user $userId", it)
            }
        }
    }

    private fun syncPowerChoiceFromInternationalSetting(source: Any, context: Context) {
        val resolver = context.contentResolver
        val internationalValue = Settings.Secure.getInt(
            resolver,
            SECURE_DISABLE_GOOGLE_ASSIST_POWER_WAKEUP,
            -1,
        )
        if (internationalValue == -1) return
        val targetDomesticValue = if (internationalValue == 1) 0 else 1
        if (Settings.System.getInt(
                resolver,
                SYSTEM_QUICK_TURN_ON_VOICE_ASSISTANT,
                1,
            ) != targetDomesticValue
        ) {
            Settings.System.putInt(
                resolver,
                SYSTEM_QUICK_TURN_ON_VOICE_ASSISTANT,
                targetDomesticValue,
            )
        }
        applyC17NativePowerConfiguration(source, context)
    }

    private fun isPowerAssistantEnabled(context: Context?): Boolean {
        context ?: return false
        val resolver = context.contentResolver
        return when (Settings.Secure.getInt(
            resolver,
            SECURE_DISABLE_GOOGLE_ASSIST_POWER_WAKEUP,
            -1,
        )) {
            1 -> false
            0 -> true
            else -> Settings.System.getInt(
                resolver,
                SYSTEM_QUICK_TURN_ON_VOICE_ASSISTANT,
                1,
            ) != 0
        }
    }

    private fun isGestureCircleToSearchEnabled(context: Context?): Boolean {
        context ?: return false
        return Settings.Secure.getInt(
            context.contentResolver,
            SECURE_GESTURE_CIRCLE_TO_SEARCH_ENABLED,
            1,
        ) == 1
    }

    private fun isGestureCornerAssistantEnabled(context: Context?): Boolean {
        context ?: return false
        return Settings.Secure.getInt(
            context.contentResolver,
            SECURE_GESTURE_CORNER_ASSISTANT_ENABLED,
            1,
        ) == 1
    }

    private fun resolveCurrentUserId(): Int {
        return runCatching {
            ActivityManager::class.java.getDeclaredMethod("getCurrentUser").apply {
                isAccessible = true
            }.invoke(null) as Int
        }.getOrDefault(Process.myUid() / 100_000)
    }

    private fun extractUserId(value: Any?): Int? {
        if (value == null) return null
        listOf("getUserIdentifier", "getIdentifier").forEach { methodName ->
            val result = runCatching {
                value.javaClass.getMethod(methodName).invoke(value) as? Int
            }.getOrNull()
            if (result != null) return result
        }
        return runCatching {
            val userHandle = value.javaClass.getMethod("getUserHandle").invoke(value)
                ?: return@runCatching null
            userHandle.javaClass.getMethod("getIdentifier").invoke(userHandle) as? Int
        }.getOrNull()
    }

    private data class AssistantSecureState(
        val assistant: String,
        val voiceInteractionService: String,
    )

    private data class VoiceInteractionCapability(
        val supportsAssist: Boolean,
        val hasRecognitionService: Boolean,
    )

    private fun hookPowerLongPress(packageName: String, classLoader: ClassLoader) {
        XposedHelpers.findClassIfExists(
            "com.android.server.policy.PhoneWindowManagerExtImpl\$OplusSpeechHandler",
            classLoader
        )?.let { cls ->
            val key = "$packageName:power-long-press:${cls.name}"
            if (!installedHooks.add(key)) return@let
            runCatching {
                XposedHelpers.findAndHookMethod(
                    cls,
                    "handleMessage",
                    Message::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val message = param.args.firstOrNull() as? Message ?: return
                            if (message.what != MSG_POWER_LONG_PRESS_FOR_SPEECH) return
                            if (interceptPowerLongPress(param.thisObject)) {
                                param.result = null
                            }
                        }
                    }
                )
                log("OPlus speech handler hooked")
            }.onFailure {
                installedHooks.remove(key)
                log("Failed to hook OPlus speech handler", it)
            }
        }

        XposedHelpers.findClassIfExists("com.android.server.policy.PhoneWindowManager", classLoader)
            ?.let { cls ->
                val key = "$packageName:power-long-press:${cls.name}"
                if (!installedHooks.add(key)) return@let
                runCatching {
                    XposedBridge.hookAllMethods(
                        cls,
                        "powerLongPress",
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                if (interceptPowerLongPress(param.thisObject)) {
                                    param.result = null
                                }
                            }
                        }
                    )
                    log("PhoneWindowManager.powerLongPress hooked")
                }.onFailure {
                    installedHooks.remove(key)
                    log("Failed to hook PhoneWindowManager.powerLongPress", it)
                }
            }
    }

    private fun hookContextualSearch(packageName: String, classLoader: ClassLoader) {
        if (LspConfig.isAssistantNativeCircleEnabledXposed()) {
            hookContextualSearchSystemFeature(packageName, classLoader)
        }
        if (contextualSearchConfigId == null) {
            contextualSearchConfigId = findContextualSearchConfigId(classLoader)
        }

        XposedHelpers.findClassIfExists("com.android.server.SystemServer", classLoader)?.let { cls ->
            val key = "$packageName:contextual-search-config:${cls.name}"
            if (!installedHooks.add(key)) return@let
            runCatching {
                XposedHelpers.findAndHookMethod(
                    cls,
                    "deviceHasConfigString",
                    Context::class.java,
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (!isCircleFeatureEnabled()) return
                            val resId = param.args.getOrNull(1) as? Int ?: return
                            if (resId == contextualSearchConfigId) {
                                param.result = true
                            }
                        }
                    }
                )
            }.onFailure {
                installedHooks.remove(key)
                log("Failed to hook contextual search config check", it)
            }
        }

        XposedHelpers.findClassIfExists(
            "com.android.server.contextualsearch.ContextualSearchManagerService",
            classLoader
        )?.let { cls ->
            hookContextualSearchService(cls)
            cls.declaredClasses.forEach(::hookContextualSearchService)
        }
    }

    private fun hookContextualSearchService(cls: Class<*>) {
        val key = "contextual:${cls.name}"
        if (!installedHooks.add(key)) return
        runCatching {
            cls.declaredMethods
                .filter { it.name == "getContextualSearchPackageName" }
                .forEach { method ->
                    XposedBridge.hookMethod(
                        method,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                if (isCircleFeatureEnabled()) {
                                    param.result = GOOGLE_APP_PACKAGE
                                }
                            }
                        }
                    )
                }
            cls.declaredMethods
                .filter { it.name == "enforcePermission" }
                .forEach { method ->
                    XposedBridge.hookMethod(
                        method,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                if (isCircleFeatureEnabled() &&
                                    isTrustedContextualSearchCaller()
                                ) {
                                    param.result = null
                                }
                            }
                        }
                    )
                }
            cls.declaredMethods
                .filter { it.name == "startContextualSearch" }
                .forEach { method ->
                    XposedBridge.hookMethod(method, ContextualSearchIdentityHook)
                }
        }.onFailure {
            installedHooks.remove(key)
            log("Failed to hook contextual search service ${cls.name}", it)
        }
    }

    private object ContextualSearchIdentityHook : XC_MethodHook() {
        private val identity = ThreadLocal<Long?>()

        override fun beforeHookedMethod(param: MethodHookParam) {
            if (!isCircleFeatureEnabled() || !isTrustedContextualSearchCaller()) return
            identity.set(Binder.clearCallingIdentity())
        }

        override fun afterHookedMethod(param: MethodHookParam) {
            identity.get()?.let(Binder::restoreCallingIdentity)
            identity.remove()
        }
    }

    private fun hookOriginalAssistantStart(packageName: String, classLoader: ClassLoader) {
        val classNames = listOf(
            "com.oplus.voiceassistant.service.BrenoServiceProxy",
            "com.oplus.voiceassistant.BrenoService",
            "com.heytap.voiceassistant.service.VoiceAssistantService"
        )
        classNames.forEach { className ->
            val cls = XposedHelpers.findClassIfExists(className, classLoader) ?: return@forEach
            val key = "$packageName:original-assistant-block:${cls.name}"
            if (!installedHooks.add(key)) return@forEach
            runCatching {
                cls.declaredMethods
                    .filter { it.name.startsWith("start", ignoreCase = true) }
                    .forEach { method ->
                        XposedBridge.hookMethod(
                            method,
                            object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam) {
                                    if (!LspConfig.isAssistantNativePowerEnabledXposed() &&
                                        LspConfig.getAssistantPowerModeXposed() !=
                                        LspConfig.ASSISTANT_POWER_MODE_NONE
                                    ) {
                                        param.result = null
                                    }
                                }
                            }
                        )
                    }
            }.onFailure {
                installedHooks.remove(key)
                log("Failed to hook original assistant class $className", it)
            }
        }
    }

    private fun interceptPowerLongPress(source: Any?): Boolean {
        if (LspConfig.isAssistantNativePowerEnabledXposed()) return false
        val mode = LspConfig.getAssistantPowerModeXposed()
        if (mode == LspConfig.ASSISTANT_POWER_MODE_NONE) return false

        val now = SystemClock.elapsedRealtime()
        if (now - lastPowerInterceptAt < DEBOUNCE_WINDOW_MS) return true
        lastPowerInterceptAt = now

        val context = findContext(source) ?: systemContext ?: findSystemContext()
        context?.let(::performHapticFeedback)
        return when (mode) {
            LspConfig.ASSISTANT_POWER_MODE_SYSTEM_DEFAULT -> {
                launchDefaultAssistantAsync(context)
                true
            }
            else -> false
        }
    }

    /**
     * Power-key hooks run on a critical system_server policy thread. All binding, retries and
     * shell fallbacks must happen away from that callback so a slow Google service cannot stall
     * input dispatch or system policy handling.
     */
    private fun launchDefaultAssistantAsync(context: Context?) {
        if (!assistantLaunchInProgress.compareAndSet(false, true)) return
        val safeContext = context?.applicationContext ?: context
        runCatching {
            assistantExecutor.execute {
                try {
                    if (safeContext != null) {
                        triggerDefaultAssistant(safeContext)
                    } else {
                        log("Unable to resolve a system context for the default assistant")
                    }
                } finally {
                    assistantLaunchInProgress.set(false)
                }
            }
        }.onFailure { error ->
            assistantLaunchInProgress.set(false)
            log("Failed to schedule the default assistant", error)
        }
    }

    private fun triggerDefaultAssistant(context: Context): Boolean {
        val selection = resolveDefaultAssistant(context) ?: run {
            log("No system default assistant is selected")
            return false
        }
        log(
            "Launching system default assistant: " +
                selection.assistComponent.flattenToShortString(),
        )
        return if (
            selection.isVoiceInteractionService &&
            selection.assistComponent.packageName == GOOGLE_APP_PACKAGE
        ) {
            triggerGoogleAssistant(context, selection.assistComponent)
        } else {
            triggerSelectedAssistant(context, selection)
        }
    }

    private fun triggerGoogleAssistant(
        context: Context,
        voiceInteractionService: ComponentName,
    ): Boolean {
        val token = Binder.clearCallingIdentity()
        return runCatching {
            warmUpVoiceInteractionService(
                context,
                voiceInteractionService,
                aggressive = false,
            )
            if (tryShowSessionViaVims(attempt = 1)) return@runCatching true

            forceStopGoogleApp(context)
            sleepQuietly(SHOW_SESSION_RETRY_DELAY_MS)
            warmUpVoiceInteractionService(
                context,
                voiceInteractionService,
                aggressive = true,
            )
            if (tryShowSessionViaVims(attempt = 2)) return@runCatching true

            val voiceCommand = Intent(Intent.ACTION_VOICE_COMMAND)
                .setPackage(GOOGLE_APP_PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (startActivity(context, voiceCommand)) return@runCatching true

            triggerGeminiFallbackByShell()
            true
        }.onFailure {
            log("Failed to trigger Gemini", it)
        }.also {
            Binder.restoreCallingIdentity(token)
        }.getOrDefault(false)
    }

    private fun triggerSelectedAssistant(
        context: Context,
        selection: DefaultAssistantSelection,
    ): Boolean {
        val token = Binder.clearCallingIdentity()
        return runCatching {
            if (selection.isVoiceInteractionService) {
                warmUpVoiceInteractionService(
                    context,
                    selection.assistComponent,
                    aggressive = false,
                )
                if (tryShowSessionViaVims(attempt = 1)) return@runCatching true
                warmUpVoiceInteractionService(
                    context,
                    selection.assistComponent,
                    aggressive = true,
                )
                if (tryShowSessionViaVims(attempt = 2)) return@runCatching true
            } else {
                val explicitAssist = Intent(Intent.ACTION_ASSIST)
                    .setComponent(selection.assistComponent)
                    .putExtras(newAssistantInvocationBundle())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (startActivity(context, explicitAssist)) return@runCatching true
            }

            // Some assistants expose only an implicit assist/voice-command activity even when
            // their picker entry points at a VoiceInteractionService.
            val packageName = selection.assistComponent.packageName
            val packageAssist = Intent(Intent.ACTION_ASSIST)
                .setPackage(packageName)
                .putExtras(newAssistantInvocationBundle())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (startActivity(context, packageAssist)) return@runCatching true

            val voiceCommand = Intent(Intent.ACTION_VOICE_COMMAND)
                .setPackage(packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(context, voiceCommand)
        }.onFailure {
            log("Failed to trigger the selected assistant", it)
        }.also {
            Binder.restoreCallingIdentity(token)
        }.getOrDefault(false)
    }

    private fun resolveDefaultAssistant(context: Context): DefaultAssistantSelection? {
        val resolver = context.contentResolver
        val assistComponent = Settings.Secure.getString(resolver, SECURE_ASSISTANT)
            ?.takeIf(String::isNotBlank)
            ?.let(ComponentName::unflattenFromString)
        val voiceInteractionComponent = Settings.Secure.getString(
            resolver,
            SECURE_VOICE_INTERACTION_SERVICE,
        )
            ?.takeIf(String::isNotBlank)
            ?.let(ComponentName::unflattenFromString)
        val selected = assistComponent ?: voiceInteractionComponent ?: return null
        return DefaultAssistantSelection(
            assistComponent = selected,
            isVoiceInteractionService = selected == voiceInteractionComponent,
        )
    }

    private fun warmUpVoiceInteractionService(
        context: Context,
        component: ComponentName,
        aggressive: Boolean,
    ) {
        val intent = Intent("android.service.voice.VoiceInteractionService").setComponent(component)
        val latch = CountDownLatch(1)
        val connected = AtomicBoolean(false)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: android.content.ComponentName, service: IBinder) {
                connected.set(true)
                latch.countDown()
            }

            override fun onServiceDisconnected(name: android.content.ComponentName) = Unit

            override fun onBindingDied(name: android.content.ComponentName) {
                latch.countDown()
            }

            override fun onNullBinding(name: android.content.ComponentName) {
                connected.set(true)
                latch.countDown()
            }
        }

        val flags = Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT
        val bound = runCatching {
            context.bindService(intent, connection, flags)
        }.getOrDefault(false)
        if (!bound) return

        try {
            val timeoutMs = if (aggressive) WARMUP_TIMEOUT_AGGRESSIVE_MS else WARMUP_TIMEOUT_MS
            val settleMs = if (aggressive) POST_CONNECT_SETTLE_AGGRESSIVE_MS else POST_CONNECT_SETTLE_MS
            val arrived = latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            if (arrived && connected.get()) {
                sleepQuietly(settleMs)
            }
        } finally {
            runCatching { context.unbindService(connection) }
        }
    }

    private fun tryShowSessionViaVims(attempt: Int): Boolean {
        return runCatching {
            val binder = getService("voiceinteraction") ?: return@runCatching false
            val stubClass = Class.forName("com.android.internal.app.IVoiceInteractionManagerService\$Stub")
            val service = stubClass
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, binder) ?: return@runCatching false
            val method = service.javaClass.methods.firstOrNull { it.name == "showSessionForActiveService" }
                ?: service.javaClass.methods.firstOrNull { it.name == "showSessionFromSession" }
                ?: return@runCatching false
            method.isAccessible = true
            val bundle = newAssistantInvocationBundle()
            val args = method.parameterTypes.map { type ->
                when {
                    IBinder::class.java.isAssignableFrom(type) -> null
                    type == Bundle::class.java -> bundle
                    type == Integer.TYPE -> SHOW_SOURCE_ASSIST_GESTURE
                    type == java.lang.Boolean.TYPE -> true
                    type == String::class.java -> null
                    else -> null
                }
            }.toTypedArray()
            val result = method.invoke(service, *args)
            val ok = when {
                method.returnType == Void.TYPE -> true
                method.returnType == java.lang.Boolean.TYPE -> result == true
                else -> result != java.lang.Boolean.FALSE
            }
            if (!ok) log("VIMS showSession returned false on attempt $attempt")
            ok
        }.onFailure {
            log("Failed to show Gemini session on attempt $attempt", it)
        }.getOrDefault(false)
    }

    private fun newAssistantInvocationBundle(): Bundle {
        return Bundle().apply {
            putInt("invocation_type", INVOCATION_TYPE_POWER_BUTTON_LONG_PRESS)
            putLong("invocation_time_ms", SystemClock.uptimeMillis())
            putInt("invocation_phone_state", 0)
            putLong(Intent.EXTRA_TIME, SystemClock.uptimeMillis())
            putInt(Intent.EXTRA_ASSIST_INPUT_DEVICE_ID, KEYBOARD_DEVICE_ID_SYSTEM)
            putBoolean("xiaobu_trigger", true)
        }
    }

    private fun forceStopGoogleApp(context: Context) {
        runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return
            val method = ActivityManager::class.java.getDeclaredMethod(
                "forceStopPackage",
                String::class.java
            )
            method.isAccessible = true
            method.invoke(am, GOOGLE_APP_PACKAGE)
            sleepQuietly(FORCE_STOP_SETTLE_MS)
        }.onFailure {
            log("Failed to force stop Google app", it)
        }
    }

    private fun triggerGeminiFallbackByShell() {
        runCatching {
            log("Gemini shell fallback command start")
            Runtime.getRuntime().exec(
                arrayOf(
                    "am",
                    "start",
                    "-a",
                    Intent.ACTION_VOICE_COMMAND,
                    "-p",
                    GOOGLE_APP_PACKAGE
                )
            )
            log("Gemini shell fallback command issued")
        }.onFailure {
            log("Gemini shell fallback failed", it)
        }
    }

    private fun startActivity(context: Context, intent: Intent): Boolean {
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private data class DefaultAssistantSelection(
        val assistComponent: ComponentName,
        val isVoiceInteractionService: Boolean,
    )

    private fun triggerCircleToSearchLegacy(): Boolean {
        val token = Binder.clearCallingIdentity()
        return runCatching {
            val binder = getService("contextual_search") ?: return@runCatching false
            val stubClass = Class.forName("android.app.contextualsearch.IContextualSearchManager\$Stub")
            val service = stubClass
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, binder)
                ?: return@runCatching false
            val method = service.javaClass.methods.firstOrNull {
                it.name == "startContextualSearch" && it.parameterTypes.size == 1
            } ?: return@runCatching false
            method.invoke(service, 1)
            true
        }.onFailure {
            log("Failed to trigger Circle to Search through the ColorOS 16 API", it)
        }.also {
            Binder.restoreCallingIdentity(token)
        }.getOrDefault(false)
    }

    private fun triggerCircleToSearchC17(): Boolean {
        val token = Binder.clearCallingIdentity()
        return runCatching {
            val binder = getService("contextual_search") ?: return@runCatching false
            val stubClass = Class.forName("android.app.contextualsearch.IContextualSearchManager\$Stub")
            val service = stubClass
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, binder)
                ?: return@runCatching false
            val method = service.javaClass.methods.firstOrNull {
                it.name == "startContextualSearch" && it.parameterTypes.size == 2
            } ?: return@runCatching false
            // Android 17 added ContextualSearchConfig as the second Binder argument. A null
            // config intentionally selects the framework's DEFAULT_CONFIG, matching the public
            // one-argument manager overload while keeping the ColorOS 16 path untouched.
            method.invoke(service, 1, null)
            true
        }.onFailure {
            log("Failed to trigger Circle to Search through the ColorOS 17 API", it)
        }.also {
            Binder.restoreCallingIdentity(token)
        }.getOrDefault(false)
    }

    private fun getService(name: String): IBinder? {
        return runCatching {
            Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, name) as? IBinder
        }.getOrNull()
    }

    private fun findContextualSearchConfigId(classLoader: ClassLoader): Int? {
        return runCatching {
            val cls = XposedHelpers.findClass("com.android.internal.R\$string", classLoader)
            XposedHelpers.getStaticIntField(cls, "config_defaultContextualSearchPackageName")
        }.getOrNull()
    }

    private fun isCircleFeatureEnabled(): Boolean {
        return isAnyGestureCircleEnabled()
    }

    private fun isLegacyGestureCircleEnabled(): Boolean =
        LspConfig.isAssistantGestureCircleEnabledXposed() ||
            LspConfig.isAssistantGestureCircleC17EnabledXposed()

    private fun isAnyGestureCircleEnabled(): Boolean =
        isLegacyGestureCircleEnabled() || LspConfig.isAssistantNativeCircleEnabledXposed()

    private fun isTrustedContextualSearchCaller(): Boolean {
        val uid = Binder.getCallingUid()
        if (uid == Process.SYSTEM_UID) return true
        return runCatching {
            val context = systemContext ?: findSystemContext() ?: return@runCatching false
            context.packageManager.getPackagesForUid(uid)
                ?.contains(SYSTEM_UI_PACKAGE) == true
        }.getOrDefault(false)
    }

    private fun findContext(source: Any?): Context? {
        val direct = source as? Context
        if (direct != null) return direct
        return findFieldValue(source, Context::class.java) ?: findSystemContext()
    }

    private fun <T> findFieldValue(source: Any?, type: Class<T>, depth: Int = 0): T? {
        if (source == null || depth > 2) return null
        val cls = source.javaClass
        var current: Class<*>? = cls
        while (current != null) {
            for (field in current.declaredFields) {
                val value = runCatching {
                    field.isAccessible = true
                    field.get(source)
                }.getOrNull()
                if (type.isInstance(value)) return type.cast(value)
                if (field.name == "this$0") {
                    findFieldValue(value, type, depth + 1)?.let { return it }
                }
            }
            current = current.superclass
        }
        return null
    }

    private fun findSystemContext(): Context? {
        systemContext?.let { return it }
        return runCatching {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val thread = activityThreadClass
                .getMethod("currentActivityThread")
                .invoke(null) ?: return@runCatching null
            activityThreadClass
                .getMethod("getSystemContext")
                .invoke(thread) as? Context
        }.onSuccess { context ->
            if (context != null) systemContext = context
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun performHapticFeedback(context: Context) {
        runCatching {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                vibrator.vibrate(20L)
            }
        }
    }

    private fun performHapticFeedback(source: Any) {
        runCatching {
            (source as? View)?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                ?: findContext(source)?.let(::performHapticFeedback)
        }
    }

    private fun sleepQuietly(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun log(message: String, throwable: Throwable? = null) {
        HookLog.i(TAG, message, throwable)
    }
}
