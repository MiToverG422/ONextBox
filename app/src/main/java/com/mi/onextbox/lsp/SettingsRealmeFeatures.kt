package com.mi.onextbox.lsp

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.HookConfigSnapshot
import com.mi.onextbox.lsp.compat.MODERN_PREFERENCES_READY_KEY
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect as Reflect
import java.lang.reflect.Method
import java.util.WeakHashMap

internal enum class SettingsRealmeFeature(val key: String) {
    ProcessManager(SettingsProcessManager.KEY),
    AppVolume("settings_realme_app_volume"),
    DirectionalSound("settings_realme_directional_sound"),
    BrightnessRange("settings_realme_brightness_range"),
    MultipleFaces("settings_realme_multiple_faces"),
    SleepCapsule("settings_realme_sleep_capsule"),
    HeartRate("settings_realme_heart_rate"),
    Laboratory("settings_realme_laboratory"),
    DcDimming("settings_realme_dc_dimming"),
    DualMusic("settings_realme_dual_music"),
}

internal object SettingsRealmeFeatures {
    fun enabled(context: Context, feature: SettingsRealmeFeature): Boolean {
        val prefs = LspPreferenceStore.prefs(context)
        return SettingsRealmeLaboratory.selected(feature) { item, default -> prefs.getBoolean(item.key, default) }
    }
    fun effectiveEnabled(context: Context, feature: SettingsRealmeFeature): Boolean {
        val prefs = LspPreferenceStore.prefs(context)
        return SettingsRealmeLaboratory.effective(feature) { item, default -> prefs.getBoolean(item.key, default) }
    }
    fun set(context: Context, feature: SettingsRealmeFeature, enabled: Boolean) {
        check(!enabled || SettingsRealmeDependencies.available(context, feature)) { "Required Realme application is unavailable" }
        if (feature in SettingsRealmeLaboratory.children) {
            val prefs = LspPreferenceStore.prefs(context)
            if (!prefs.contains(SettingsRealmeFeature.Laboratory.key)) {
                // Freeze the legacy entrance before editing the last enabled child.
                ModernXposedPreferenceSync.setBooleanAndSync(context, SettingsRealmeFeature.Laboratory.key,
                    SettingsRealmeLaboratory.selected(SettingsRealmeFeature.Laboratory) { item, default -> prefs.getBoolean(item.key, default) })
            }
        }
        ModernXposedPreferenceSync.setBooleanAndSync(context, feature.key, enabled)
    }
    private fun active(feature: SettingsRealmeFeature) = SettingsRealmeLaboratory.effective(feature) { item, default ->
        HookConfigSnapshot.boolean(item.key, default) == true
    }
    fun availability(primaryUser: Boolean) = if (primaryUser) 0 else 3
    fun brightnessAvailability(enabled: Boolean, nativeAvailability: () -> Boolean) = enabled || nativeAvailability()

    fun hook(loader: ClassLoader) {
        Session(loader).install()
    }

    private class Session(private val loader: ClassLoader) {
        private val main = Handler(Looper.getMainLooper())
        private val fragments = WeakHashMap<Any, Boolean>()
        private val rawBrightnessCheck = ThreadLocal<Boolean>()
        private val faceMode = SettingsRealmeFaceMode()
        private var preferences: SharedPreferences? = null
        private var retired = false
        private val prefix = "ONextBox-Realme@${System.identityHashCode(loader)}:"
        private val classes = mapOf(
            "feature.sound.SoundSettingsFragment" to listOf(
                "feature.sound.multiappvolume.MultiAppVolumeAdjustmentPreferenceController" to "multi_app_volume",
                "feature.sound.multiappvolume.AppVolumeSingleAdjustPreferenceController" to "app_volume_single_adjust",
                "feature.sound.controller.DirectionalTransmitController" to "directional_transmit",
            ),
            "feature.display.DisplaySettingsFragment" to listOf(
                "feature.display.controller.IncreaseBrightnessRangePreferenceController" to "increase_brightness_switch",
            ),
            "feature.homepage.OplusTopLevelSettings" to listOf(
                "feature.homepage.TopLevelLaboratoryPreferenceController" to "laboratory_key",
            ),
            "feature.laboratory.LaboratorySettingsFragment" to listOf(
                "doubleears.controller.DoubleEarPageJumpController" to "lab_double_ear",
            ),
        )
        private val refresh = Runnable {
            HotReloadLifecycle.withHook({}, {
                if (!retired) fragments.keys.toList().forEach(::refreshFragment)
            })
        }
        private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == MODERN_PREFERENCES_READY_KEY || SettingsRealmeFeature.entries.any { it.key == key }) schedule()
        }
        private fun schedule() {
            if (retired) return
            main.removeCallbacks(refresh)
            main.post(refresh)
        }
        private fun type(name: String) = Reflect.findClass("com.oplus.settings.$name", loader)
        private fun primaryUser() = Reflect.callStaticMethod(Class.forName("android.app.ActivityManager"), "getCurrentUser") == 0
        private fun rawBrightness(): Boolean {
            rawBrightnessCheck.set(true)
            return try {
                Reflect.callStaticMethod(type("feature.display.controller.IncreaseBrightnessRangePreferenceController"), "isPreferenceAvailable") == true
            } finally { rawBrightnessCheck.remove() }
        }

        private fun labAvailable(context: Context): Boolean = primaryUser() &&
            Reflect.callStaticMethod(type("custom.SettingsCustomUtils"), "isWellBeingDisableTimeChange", context) != true &&
            active(SettingsRealmeFeature.Laboratory)

        fun install() {
            fun install(feature: SettingsRealmeFeature, name: String, methodName: String, result: () -> Any?) {
                runCatching {
                    val method = type(name).getDeclaredMethod(methodName)
                    ModernHookRegistry.installCompat(prefix + "${feature.name}:$name:$methodName", method, object : ModernMethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (retired || !active(feature) || rawBrightnessCheck.get() == true) return
                            runCatching { result() }.onFailure {
                                HookLog.w("ONextBox-Realme", "Could not resolve ${feature.name} entry", it)
                            }.getOrNull()?.let { param.result = it }
                        }
                    })
                    deoptimize(method)
                }.onFailure { HookLog.w("ONextBox-Realme", "Entry unavailable: ${feature.name}", it) }
            }
            install(SettingsRealmeFeature.AppVolume, "feature.sound.multiappvolume.MultiAppVolumeAdjustmentPreferenceController", "getAvailabilityStatus") {
                availability(primaryUser())
            }
            install(SettingsRealmeFeature.AppVolume, "feature.sound.multiappvolume.AppVolumeSingleAdjustPreferenceController", "getAvailabilityStatus") {
                // The Realme page edits the same setting; avoid two competing controls.
                if (primaryUser()) 3 else null
            }
            runCatching {
                val sound = type("feature.sound.SoundSettingsFragment")
                val remove = Reflect.findClass("com.android.settings.SettingsPreferenceFragment", loader)
                    .getDeclaredMethod("removePreference", String::class.java)
                ModernHookRegistry.installCompat(prefix + "app-volume:retain-group", remove, object : ModernMethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (retired || !sound.isInstance(param.thisObject) || param.args[0] != SettingsAppVolumeGroup.KEY ||
                            !active(SettingsRealmeFeature.AppVolume)) return
                        if (primaryUser()) param.result = false
                    }
                })
                deoptimize(remove)
            }.onFailure { HookLog.w("ONextBox-Realme", "App-volume parent-group retention unavailable", it) }
            install(SettingsRealmeFeature.DirectionalSound, "feature.sound.controller.DirectionalTransmitController", "isSupportDirectionalTransmit") {
                true
            }
            install(SettingsRealmeFeature.DirectionalSound, "feature.sound.controller.DirectionalTransmitController", "getAvailabilityStatus") { 0 }
            install(SettingsRealmeFeature.BrightnessRange, "feature.display.controller.IncreaseBrightnessRangePreferenceController", "isPreferenceAvailable") {
                true
            }
            install(SettingsRealmeFeature.DcDimming, "feature.display.controller.DcDimmingModePreferenceController", "getAvailabilityStatus") { availability(primaryUser()) }
            install(SettingsRealmeFeature.DualMusic, "doubleears.controller.DoubleEarPageJumpController", "getAvailabilityStatus") { availability(primaryUser()) }
            installLaboratory()
            installMultipleFaces()
            // isAvailable caches the old support result in a final field; resolve it live instead.
            runCatching {
                val brightness = type("feature.display.controller.IncreaseBrightnessRangePreferenceController")
                val available = brightness.getDeclaredMethod("isAvailable")
                ModernHookRegistry.installCompat(prefix + "brightness:available", available, object : ModernMethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (retired) return
                        runCatching { brightnessAvailability(active(SettingsRealmeFeature.BrightnessRange), ::rawBrightness) }
                            .onSuccess { param.result = it }
                            .onFailure { HookLog.w("ONextBox-Realme", "Brightness visibility refresh failed", it) }
                    }
                })
                deoptimize(available)
                // A switch change can make the stock onDestroy support check false after registration.
                ModernHookRegistry.installCompat(prefix + "brightness:destroy", brightness.getDeclaredMethod("onDestroy"), object : ModernMethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        runCatching {
                            val owner = param.thisObject ?: return@runCatching
                            Reflect.getObjectField(owner, "mIncreaseBrightnessRangeObserverRegisterHelper")?.let {
                                Reflect.callMethod(it, "detach")
                            }
                        }
                    }
                })
            }.onFailure { HookLog.w("ONextBox-Realme", "Brightness cache adaptation unavailable", it) }
            classes.forEach { (name, _) ->
                runCatching {
                    val fragment = type(name)
                    ModernHookRegistry.installCompat(prefix + name + ":resume", fragment.getDeclaredMethod("onResume"), object : ModernMethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (param.throwable != null || retired) return
                            param.thisObject?.let { fragments[it] = true; refreshFragment(it) }
                        }
                    })
                    fragment.declaredMethods.filter { it.name in setOf("onCreate", "onResume", "createPreferenceControllers", "initIncreaseBrightnessRangePreference") }.forEach(::deoptimize)
                }.onFailure { HookLog.w("ONextBox-Realme", "Page refresh unavailable: $name", it) }
            }
            for (name in (classes.values.flatten().map { it.first } + "feature.display.SettingsBrightnessPreference")) {
                runCatching { type(name).declaredMethods.forEach(::deoptimize) }
            }
            runCatching {
                Reflect.findClass("com.android.settings.core.BasePreferenceController", loader).declaredMethods
                    .filter { it.name == "isAvailable" }.forEach(::deoptimize)
            }
            HotReloadLifecycle.register("settings:realme-entries",
                capture = { fragments.keys.toTypedArray() },
                detach = {
                    retired = true
                    main.removeCallbacksAndMessages(null)
                    preferences?.unregisterOnSharedPreferenceChangeListener(listener)
                    preferences = null
                    fragments.keys.toList().forEach { fragment ->
                        runCatching { SettingsRealmeLabEntries.removeOwnedGroup(Reflect.callMethod(fragment, "getPreferenceScreen")) }
                    }
                    fragments.clear()
                },
                restore = { state ->
                    retired = false
                    (state as? Array<*>)?.filterNotNull()?.forEach { fragments[it] = true }
                    observe()
                    schedule()
                },
                resumeAfterRejection = { observe(); schedule() },
            )
            HotReloadLifecycle.afterCommit {
                (HotReloadLifecycle.restoredState("settings:realme-entries") as? Array<*>)?.filterNotNull()?.forEach { fragments[it] = true }
                observe()
                schedule()
            }
        }

        private fun observe() {
            if (preferences == null) ModernHookRuntime.remotePreferences("lsp_features")?.let {
                it.registerOnSharedPreferenceChangeListener(listener)
                preferences = it
            }
        }
        private fun refreshFragment(fragment: Any) {
            runCatching {
                val targets = classes.entries.firstOrNull { type(it.key).isInstance(fragment) }?.value ?: return
                val screen = Reflect.callMethod(fragment, "getPreferenceScreen") ?: return
                if (type("feature.laboratory.LaboratorySettingsFragment").isInstance(fragment)) {
                    // Native onResume hides DC based on the unmodified panel capability.
                    Reflect.callMethod(fragment, "checkRmDC")
                    SettingsRealmeLabEntries.refresh(loader, fragment) { feature -> primaryUser() && active(feature) }
                }
                if (targets.any { it.second == "multi_app_volume" }) {
                    val volumeMode = Reflect.callMethod(fragment, "use", type("feature.sound.controller.VolumeModePreferenceController"))
                    if (volumeMode != null) SettingsAppVolumeGroup.reconcile(
                        screen = screen,
                        volumeModeController = volumeMode,
                        forced = active(SettingsRealmeFeature.AppVolume) && primaryUser(),
                        nativeRealme = Reflect.callStaticMethod(type("utils.CustomizeFeatureUtils"), "isRLMDevice") == true,
                    )
                }
                for ((name, key) in targets) {
                    val controller = Reflect.callMethod(fragment, "use", type(name)) ?: continue
                    Reflect.callMethod(controller, "displayPreference", screen)
                    if (Reflect.callMethod(controller, "isAvailable") == true) {
                        Reflect.callMethod(screen, "findPreference", key)?.let { Reflect.callMethod(controller, "updateState", it) }
                    }
                    if (key == "lab_double_ear") Reflect.callMethod(controller, "checkEnable")
                }
            }.onFailure { HookLog.w("ONextBox-Realme", "Native entry refresh failed", it) }
        }
        private fun deoptimize(method: Method) {
            runCatching { ModernHookRuntime.requireModule().deoptimize(method) }
        }

        private fun installLaboratory() {
            runCatching {
                val controller = type("feature.homepage.TopLevelLaboratoryPreferenceController")
                val status = controller.getDeclaredMethod("getAvailabilityStatus")
                ModernHookRegistry.installCompat(prefix + "lab:available", status, object : ModernMethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (retired) return
                        runCatching {
                            val context = Reflect.getObjectField(param.thisObject ?: return, "mContext") as? Context ?: return
                            if (labAvailable(context)) param.result = 0
                        }.onFailure { HookLog.w("ONextBox-Realme", "Laboratory visibility unavailable", it) }
                    }
                })
                val support = controller.getDeclaredMethod("getAvailabilitySupport", Context::class.java)
                ModernHookRegistry.installCompat(prefix + "lab:support", support, object : ModernMethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (retired) return
                        runCatching { if (labAvailable(param.args[0] as Context)) param.result = true }
                    }
                })
                // The native destroy path checks live availability, which may have changed since registration.
                ModernHookRegistry.installCompat(prefix + "lab:observer-cleanup", controller.getDeclaredMethod("onDestroy"), object : ModernMethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        runCatching {
                            val owner = param.thisObject ?: return
                            val context = Reflect.getObjectField(owner, "mContext") as Context
                            val observer = Reflect.getObjectField(owner, "mWellBeingObserver") as android.database.ContentObserver
                            context.contentResolver.unregisterContentObserver(observer)
                        }
                    }
                })
                deoptimize(status)
                deoptimize(support)
                controller.declaredMethods.forEach(::deoptimize)
            }.onFailure { HookLog.w("ONextBox-Realme", "Laboratory adaptation unavailable", it) }
        }

        private fun installMultipleFaces() {
            runCatching {
                fun forceMode(): Boolean {
                    if (retired || !faceMode.forced(active(SettingsRealmeFeature.MultipleFaces))) return false
                    val context = Reflect.callStaticMethod(type("SettingsApplicationEx"), "getAppContext") as? Context
                    return context?.packageManager?.hasSystemFeature("android.hardware.biometrics.face") == true
                }
                val fragment = type("feature.face.FaceSettings\$FaceSettingsFragment")
                fun acquire(owner: Any) = faceMode.acquire(owner, active(SettingsRealmeFeature.MultipleFaces))
                for (method in fragment.declaredMethods.filter { it.name in setOf("onCreate", "createPreferenceControllers") }) {
                    ModernHookRegistry.installCompat(prefix + "face:pin:" + method.name, method, object : ModernMethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (!retired) param.thisObject?.let { acquire(it) }
                        }
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (param.throwable != null) param.thisObject?.let(faceMode::release)
                        }
                    })
                }
                // A page may predate this hook generation. Infer its existing UI before resuming it.
                for (name in listOf("onResume", "onPause")) {
                    ModernHookRegistry.installCompat(prefix + "face:existing:" + name, fragment.getDeclaredMethod(name), object : ModernMethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (retired) return
                            val owner = param.thisObject ?: return
                            runCatching {
                                faceMode.acquireExisting(owner) {
                                    val screen = Reflect.callMethod(owner, "getPreferenceScreen")
                                    if (screen == null) active(SettingsRealmeFeature.MultipleFaces)
                                    else Reflect.callMethod(screen, "findPreference", "multiple_face_usage_category") != null
                                }
                            }
                        }
                    })
                }
                ModernHookRegistry.installCompat(prefix + "face:release", fragment.getDeclaredMethod("onDestroy"), object : ModernMethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) { param.thisObject?.let(faceMode::release) }
                })
                val support = type("feature.face.multiple.MultiFaceUtil").getDeclaredMethod("isSupportMultipleFaces")
                ModernHookRegistry.installCompat(prefix + "face:support", support, object : ModernMethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        runCatching { if (forceMode()) param.result = true }
                    }
                })
                // Alternate-face UI removes the native multi-face category; the two modes are exclusive.
                val alternate = type("feature.face.FaceUtils").getDeclaredMethod("isSupportAlternateFace")
                ModernHookRegistry.installCompat(prefix + "face:alternate", alternate, object : ModernMethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        runCatching { if (forceMode()) param.result = false }
                    }
                })
                deoptimize(alternate)
                for (name in listOf("FaceSettings\$FaceSettingsFragment", "FaceUtils", "FaceEnrollNewActivity",
                    "FaceSettingsKeyguardUnlockController", "FaceSettingsAppLockPreferenceController",
                    "FaceSettingsAppHiddenPreferenceController", "FaceSettingsPrivateSafePreferenceController", "studycenter.StudyFaceUtils")) {
                    runCatching {
                        val caller = type("feature.face.$name")
                        caller.declaredMethods.forEach(::deoptimize)
                        caller.declaredClasses.forEach { it.declaredMethods.forEach(::deoptimize) }
                    }
                }
                deoptimize(support)
                HotReloadLifecycle.register("settings:realme-face-mode",
                    capture = faceMode::capture,
                    detach = faceMode::clear,
                    restore = faceMode::restore,
                )
                HotReloadLifecycle.afterCommit { faceMode.restore(HotReloadLifecycle.restoredState("settings:realme-face-mode")) }
            }.onFailure { HookLog.w("ONextBox-Realme", "Multiple-face adaptation unavailable", it) }
        }
    }
}
