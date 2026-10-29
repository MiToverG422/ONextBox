package com.mi.onextbox.lsp

import android.content.SharedPreferences
import android.util.Log
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.AdaptationHookGate
import com.mi.onextbox.lsp.compat.AdaptationRuntimePolicy
import com.mi.onextbox.lsp.compat.HookConfigSnapshot
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.HotReloadDispatchMailbox
import com.mi.onextbox.lsp.compat.ModernHotReloadSession
import com.mi.onextbox.lsp.compat.MODERN_PREFERENCES_READY_KEY
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer

/** Single modern API 102 entry for all ONextBox scopes. */
class OosLspModuleEntry : XposedModule() {
    private val dispatchedPackages = ConcurrentHashMap.newKeySet<String>()
    private data class ReloadTarget(
        val packageName: String,
        val classLoader: ClassLoader,
        val sourceDir: String,
        val system: Boolean = false,
    ) {
        fun export(): Array<Any> = arrayOf(packageName, classLoader, sourceDir, system)
    }
    private val reloadTargets = CopyOnWriteArrayList<ReloadTarget>()
    private var reloadMailbox = HotReloadDispatchMailbox.create()
    private val lifecycleReceiver = Consumer<Array<Any>> { row ->
        HotReloadLifecycle.withInfrastructureHook(
            fallback = { deferLifecycle(row) },
            action = { dispatchLifecycle(readTarget(row)) },
        )
    }
    private var processName = ""
    private var systemServer = false
    @Volatile private var observedPreferences: SharedPreferences? = null
    private val preferenceReadinessListener = SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
        if (key == null || key == MODERN_PREFERENCES_READY_KEY) replayWhenPreferencesReady(preferences)
    }

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        processName = param.processName
        systemServer = param.isSystemServer
        HotReloadLifecycle.begin()
        ModernHotReloadSession.begin(staging = false)
        attachRuntime()
        HotReloadDispatchMailbox.bind(reloadMailbox, lifecycleReceiver)
        HookLog.i(
            TAG,
            "Modern API $apiVersion attached: $processName " +
                "(${frameworkName} ${frameworkVersion})",
        )
    }

    private fun attachRuntime(): Boolean {
        ModernHookRuntime.attach(this)
        com.mi.onextbox.lsp.compat.SafeModeRuntime.initialize(processName, systemServer)
        com.mi.onextbox.diagnostics.RuntimeDiagnostics.event("module", "Attached", processName)
        return runCatching {
            val preferences = getRemotePreferences(PREFERENCES_GROUP)
            ModernHookRuntime.attachRemotePreferences(PREFERENCES_GROUP, preferences)
            fun observePreferences() {
                check(HookConfigSnapshot.attach(preferences)) { "Preference listener is unavailable" }
                preferences.registerOnSharedPreferenceChangeListener(preferenceReadinessListener)
                observedPreferences = preferences
                // Close the registration race without relying on framework listener ordering.
                replayWhenPreferencesReady(preferences)
            }
            observePreferences()
            HotReloadLifecycle.register(
                key = "core.preferences",
                detach = {
                    preferences.unregisterOnSharedPreferenceChangeListener(preferenceReadinessListener)
                    observedPreferences = null
                    check(HookConfigSnapshot.detach()) { "Preference listener could not be removed" }
                },
                restore = { observePreferences() },
            )
        }
            .onFailure {
                HookLog.w(
                    TAG,
                    "Remote preferences unavailable in $processName; feature dispatch waits for synchronized configuration",
                    it,
                )
            }.isSuccess
    }

    private fun replayWhenPreferencesReady(preferences: SharedPreferences) {
        HotReloadLifecycle.withInfrastructureHook({}) {
            if (observedPreferences !== preferences) return@withInfrastructureHook
            runCatching {
                check(HookConfigSnapshot.attach(preferences)) { "Preference listener is unavailable" }
                if (HookConfigSnapshot.isAvailable) HotReloadLifecycle.afterCommit {
                    HotReloadLifecycle.withInfrastructureHook({}) {
                        if (observedPreferences === preferences && HookConfigSnapshot.isAvailable) {
                            HotReloadDispatchMailbox.bind(reloadMailbox, lifecycleReceiver)
                        }
                    }
                }
            }.onFailure { HookLog.w(TAG, "Unable to resume pending lifecycle events in $processName", it) }
        }
    }

    override fun onSystemServerStarting(
        param: XposedModuleInterface.SystemServerStartingParam,
    ) {
        HotReloadDispatchMailbox.submit(
            reloadMailbox, ReloadTarget(PACKAGE_SYSTEM, param.classLoader, "", system = true).export(),
        )
    }

    private fun dispatchSystemServer(loader: ClassLoader) {
        SystemAdaptationDetector.initialize(PACKAGE_SYSTEM, loader)
        val profile = AdaptationHookGate.bind()
        if (!AdaptationHookGate.isActive(profile) || !AdaptationRuntimePolicy.allowsSystemServer(profile)) return
        if (profile == SystemAdaptationVersion.ColorOs16) {
            com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("ColorOs16FrameworkHooks.hook") { ColorOs16FrameworkHooks.hook(loader) }
            return
        }
        com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("CorePatchIntegration.hook") { CorePatchIntegration.hook(loader) }
        if (LspConfig.isImmersiveNavigationEnabledXposed()) {
            com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("ImmersiveNavigationHooker.hook") { ImmersiveNavigationHooker.hook() }
        }
        com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("FrameworkHooker.hook") { FrameworkHooker.hook(packageName = PACKAGE_SYSTEM, classLoader = loader) }
        com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("SideKeyHooker.hookSystemServer") { SideKeyHooker.hookSystemServer(loader) }
        com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("AppErrorTrackingHooker.hookSystemServer") { AppErrorTrackingHooker.hookSystemServer(loader) }
        com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("StatusBarScrollToTopHooker.hookSystemServer") { StatusBarScrollToTopHooker.hookSystemServer(loader) }
        com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("BatteryOptimizationHooker.hookSystemServer") { BatteryOptimizationHooker.hookSystemServer(loader) }
        com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("SystemRootDetectionHooker.hookSystemServer") { SystemRootDetectionHooker.hookSystemServer(loader) }
        com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("PermissionSystemHooker.hook") { PermissionSystemHooker.hook(loader) }
        com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("AppLockDelaySystemHooker.hook") { AppLockDelaySystemHooker.hook(loader) }
        com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("NotificationRemovalHooker.hookSystemServer") { NotificationRemovalHooker.hookSystemServer(loader) }
        com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("SmallWindowHooker.hookSystemServer") { SmallWindowHooker.hookSystemServer(loader) }
        com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("KeyguardCaptureHooker.hookSystemServer") { KeyguardCaptureHooker.hookSystemServer(loader) }
        com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("ScreenCaptureHooker.hookSystemServer") { ScreenCaptureHooker.hookSystemServer(loader) }
        com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("InstallerRedirectHooker.hook") { InstallerRedirectHooker.hook(loader) }
        com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("NativeFilePickerHooker.hook") { NativeFilePickerHooker.hook(loader) }
        com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("DefaultFileManagerHooker.hook") { DefaultFileManagerHooker.hook(loader) }
        com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("SettingsGlobalExtremeRefreshRateHooker.hook") { SettingsGlobalExtremeRefreshRateHooker.hook(classLoader = loader) }
        com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("DisplayEnhancementThermalHooker.hookSystemServer") { DisplayEnhancementThermalHooker.hookSystemServer(loader) }
        com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("AssistantHooker.hook") { AssistantHooker.hook(packageName = PACKAGE_SYSTEM, classLoader = loader) }
        com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("GmsRegionRestrictionHooker.hook") { GmsRegionRestrictionHooker.hook(classLoader = loader) }
        com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("AthenaPolicyHooker.hook") { AthenaPolicyHooker.hook(classLoader = loader) }
        com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("OkGoogleHotwordCompatibilityHooker.hookSystemServer") { OkGoogleHotwordCompatibilityHooker.hookSystemServer(classLoader = loader) }
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        val packageName = param.packageName

        // API 102 reports every package that becomes ready in a process. SystemUI can later load
        // the framework package "android" via a package context, but framework hooks belong only
        // to onSystemServerStarting. Keep later named packages available for legitimate shared
        // processes while rejecting this ambiguous framework callback.
        if (packageName == PACKAGE_ANDROID) return

        HotReloadDispatchMailbox.submit(reloadMailbox,
            ReloadTarget(packageName, param.classLoader, param.applicationInfo.sourceDir.orEmpty()).export())
    }

    private fun deferLifecycle(row: Array<Any>) {
        HotReloadDispatchMailbox.defer(reloadMailbox, lifecycleReceiver, row)
    }

    private fun readTarget(row: Any?): ReloadTarget {
        val fields = row as? Array<*> ?: error("Invalid lifecycle record")
        check(fields.size == 4) { "Invalid lifecycle record size" }
        return ReloadTarget(fields[0] as String, fields[1] as ClassLoader, fields[2] as String, fields[3] as Boolean)
    }

    private fun dispatchLifecycle(target: ReloadTarget) {
        if (!HookConfigSnapshot.isAvailable) {
            deferLifecycle(target.export())
            // READY may have drained the mailbox between the check and this enqueue.
            observedPreferences?.let(::replayWhenPreferencesReady)
            return
        }
        ModernHookRuntime.attach(this)
        com.mi.onextbox.diagnostics.RuntimeDiagnostics.event("module", "Attached", processName)
        val dispatchKey = "${target.packageName}@${System.identityHashCode(target.classLoader)}"
        if (!dispatchedPackages.add(dispatchKey)) return
        reloadTargets += target
        if (target.system) dispatchSystemServer(target.classLoader) else dispatchPackage(target)
    }

    private fun dispatchPackage(target: ReloadTarget) {
        val packageName = target.packageName
        val classLoader = target.classLoader

        // Auxiliary package callbacks must not bind the launcher's adapter using another APK's loader.
        if (processName.substringBefore(':') == PACKAGE_LAUNCHER && packageName != PACKAGE_LAUNCHER) return
        SystemAdaptationDetector.initialize(packageName, classLoader)
        val profile = AdaptationHookGate.bind()
        if (!AdaptationHookGate.isActive(profile)) return
        com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("CouiDividerHooker.hook") { CouiDividerHooker.hook(packageName, classLoader, target.sourceDir) }
        if (!AdaptationRuntimePolicy.allowsPackage(profile, packageName)) return
        if (profile == SystemAdaptationVersion.ColorOs16) {
            com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("ColorOs16FrameworkHooks.hookApp") { ColorOs16FrameworkHooks.hookApp() }
            com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("ColorOs16AppHooks.hook") { ColorOs16AppHooks.hook(packageName, classLoader, target.sourceDir, processName) }
            when (packageName) {
                PACKAGE_LAUNCHER -> com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("ColorOs16SettingsLauncherHooks.hookLauncher") { ColorOs16SettingsLauncherHooks.hookLauncher(classLoader, target.sourceDir) }
                PACKAGE_SYSTEM_UI -> com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("ColorOs16SystemUiHooks.hook") { ColorOs16SystemUiHooks.hook(classLoader) }
                PACKAGE_SETTINGS -> com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("ColorOs16SettingsLauncherHooks.hookSettings") { ColorOs16SettingsLauncherHooks.hookSettings(classLoader) }
                PACKAGE_OPLUS_AOD -> com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("ColorOs16SettingsLauncherHooks.hookAod") { ColorOs16SettingsLauncherHooks.hookAod(classLoader) }
                PACKAGE_WALLPAPERS, WallpaperMotionSettings.ENGINE_PACKAGE ->
                    com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("ColorOs16SettingsLauncherHooks.hookWallpaperMotion") { ColorOs16SettingsLauncherHooks.hookWallpaperMotion(classLoader) }
                ExpressCardRules.PLATFORM, ExpressCardRules.ASSISTANT ->
                    com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("ColorOs16SettingsLauncherHooks.hookExpress") { ColorOs16SettingsLauncherHooks.hookExpress(packageName, classLoader) }
                "com.oplus.screenshot" -> com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("ColorOs16SystemUiFeatureHooks.hookScreenshot") { ColorOs16SystemUiFeatureHooks.hookScreenshot(classLoader) }
                "com.oplus.screenrecorder" -> com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("ColorOs16SystemUiFeatureHooks.hookRecorder") { ColorOs16SystemUiFeatureHooks.hookRecorder(classLoader) }
            }
            return
        }

        com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("InstallerRedirectHooker.hookApp") { InstallerRedirectHooker.hookApp() }

        when (packageName) {
            PACKAGE_SYSTEM_UI -> {
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("PictorialCustomizationHooker.hookSystemUi") { PictorialCustomizationHooker.hookSystemUi(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("LargeNetworkTypeHooker.hook") { LargeNetworkTypeHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("DataSimSignalHooker.hook") { DataSimSignalHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("StatusBarLayoutHooker.hook") { StatusBarLayoutHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("PowerIndicatorHooker.hook") { PowerIndicatorHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("RotationSuggestionHooker.hook") { RotationSuggestionHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("CarrierNameHooker.hook") { CarrierNameHooker.hook(classLoader) }
                runCatching { com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("QsTileCornerHooker.hook") { QsTileCornerHooker.hook(classLoader) } }
                    .onFailure { HookLog.w("ONextBox-QsTileCorner", "Tile corner adapter unavailable", it) }
                runCatching { com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("QsSliderPercentageHooker.hook") { QsSliderPercentageHooker.hook(classLoader) } }
                    .onFailure { HookLog.w("ONextBox-QsPercentage", "Slider percentage adapter unavailable", it) }
                runCatching { com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("SideVolumePercentageHooker.hook") { SideVolumePercentageHooker.hook(classLoader) } }
                    .onFailure { HookLog.w("ONextBox-SideVolumePercentage", "Volume popup percentage adapter unavailable", it) }
                runCatching { com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("SideVolumeAppearanceHooker.hook") { SideVolumeAppearanceHooker.hook(classLoader) } }
                    .onFailure { HookLog.w("ONextBox-SideVolumeAppearance", "Volume popup appearance adapter unavailable", it) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("SideKeySystemUiHooker.hook") { SideKeySystemUiHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("ImmersiveNavigationHooker.hook") { ImmersiveNavigationHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("AospBackPanelHooker.hook") { AospBackPanelHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("BackGestureHooker.hook") { BackGestureHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("AospVolumeHooker.hook") { AospVolumeHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("VolumeSafetyWarningHooker.hook") { VolumeSafetyWarningHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("NativeLiveUpdateHooker.hook") { NativeLiveUpdateHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("C16StackedSignalHooker.hook") { C16StackedSignalHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("AssistantHooker.hook") { AssistantHooker.hook(packageName, classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("SystemUiHooker.hook") { SystemUiHooker.hook(packageName, classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("DisconnectedBluetoothIconHooker.hook") { DisconnectedBluetoothIconHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("StatusBarScrollToTopHooker.hookSystemUi") { StatusBarScrollToTopHooker.hookSystemUi(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("FluidCloudBatteryHooker.hook") { FluidCloudBatteryHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("FluidCloudMaterialHooker.hook") { FluidCloudMaterialHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("PermanentClockSecondsHooker.hookSystemUi") { PermanentClockSecondsHooker.hookSystemUi(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("ClockPeriodHooker.hook") { ClockPeriodHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("ClockTabularDigitsHooker.hook") { ClockTabularDigitsHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("ClockSpacingHooker.hook") { ClockSpacingHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("SystemUiRedOneHooker.hook") { SystemUiRedOneHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("NotificationRemovalHooker.hookSystemUi") { NotificationRemovalHooker.hookSystemUi(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("NotificationClearButtonHooker.hook") { NotificationClearButtonHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("HideNotificationClearHooker.hook") { HideNotificationClearHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("SmallWindowHooker.hookSystemUi") { SmallWindowHooker.hookSystemUi(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("NetworkSpeedHooker.hook") { NetworkSpeedHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("FaceTapUnlockHooker.hook") { FaceTapUnlockHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("KeyguardCaptureHooker.hookSystemUi") { KeyguardCaptureHooker.hookSystemUi(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("PasswordDialHighlightHooker.hook") { PasswordDialHighlightHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("PinSwipeInputHooker.hook") { PinSwipeInputHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("FluidCloudMediaBlacklistHooker.hook") { FluidCloudMediaBlacklistHooker.hook(classLoader) }
            }

            PACKAGE_SETTINGS -> {
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("AssistantHooker.hook") { AssistantHooker.hook(packageName, classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("SettingsHooker.hook") { SettingsHooker.hook(
                    packageName = packageName,
                    classLoader = classLoader,
                ) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("SettingsTitleCollapseHooker.hook") { SettingsTitleCollapseHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("SettingsAccountCardSizeHooker.hook") { SettingsAccountCardSizeHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("SettingsHomeEntryHooker.hook") { SettingsHomeEntryHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("SettingsHomeCleanupHooker.hook") { SettingsHomeCleanupHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("SettingsProcessManager.hook") { SettingsProcessManager.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("SettingsRealmeFeatures.hook") { SettingsRealmeFeatures.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("DisplayEnhancementThermalHooker.hookSettings") { DisplayEnhancementThermalHooker.hookSettings(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("SettingsAppInfoCardHooker.hook") { SettingsAppInfoCardHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("PermanentClockSecondsHooker.hookSettings") { PermanentClockSecondsHooker.hookSettings(classLoader) }
            }

            PACKAGE_LAUNCHER -> {
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("FrameworkHooker.hook") { FrameworkHooker.hook(packageName, classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("AssistantHooker.hook") { AssistantHooker.hook(packageName, classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("LauncherHooker.hook") { LauncherHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("LauncherFeaturesHooker.hook") { LauncherFeaturesHooker.hook(classLoader, target.sourceDir) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("LauncherGestureHooker.hook") { LauncherGestureHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("LauncherHomePredictionHooker.hook") { LauncherHomePredictionHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("HideRecentAppsHooker.hook") { HideRecentAppsHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("LauncherIconLabelSpacingHooker.hook") { LauncherIconLabelSpacingHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("LauncherNotificationDotColorHooker.hook") { LauncherNotificationDotColorHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("LauncherNotificationDotShadowHooker.hook") { LauncherNotificationDotShadowHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("ExpressCardHooker.hook") { ExpressCardHooker.hook(packageName, classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("SystemUiRedOneHooker.hookLauncherClock") { SystemUiRedOneHooker.hookLauncherClock() }
            }

            PACKAGE_OPLUS_AOD -> com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("FrameworkHooker.hook") { FrameworkHooker.hook(packageName, classLoader) }

            PACKAGE_ATHENA -> com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("AthenaPolicyHooker.hook") { AthenaPolicyHooker.hook(classLoader) }

            PACKAGE_OPLUS_EUICC -> {
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("EuiccRegionRestrictionHooker.hook") { EuiccRegionRestrictionHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("EuiccDiagnosticsHooker.hook") { EuiccDiagnosticsHooker.hook(classLoader) }
            }

            PACKAGE_WALLPAPERS -> {
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("WallpapersRedOneEntryHooker.hook") { WallpapersRedOneEntryHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("WallpaperMotionHooker.hook") { WallpaperMotionHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("Wallpaper3DForceHooker.hook") { Wallpaper3DForceHooker.hook(classLoader) }
            }
            WallpaperMotionSettings.ENGINE_PACKAGE -> {
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("WallpaperMotionHooker.hook") { WallpaperMotionHooker.hook(classLoader) }
            }

            PACKAGE_PHONE -> {
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("MobileNetworkHooker.hook") { MobileNetworkHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("NetworkIdentityHooker.hook") { NetworkIdentityHooker.hook(classLoader, processName) }
            }

            PACKAGE_APP_MARKET -> com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("AppMarketHooker.hook") { AppMarketHooker.hook(classLoader) }
            TrafficManagementRules.PACKAGE_NAME ->
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("TrafficManagementHooker.hook") { TrafficManagementHooker.hook(classLoader, target.sourceDir) }
            KeyboardPictorialSettings.PICTORIAL -> {
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("PictorialCustomizationHooker.hookApp") { PictorialCustomizationHooker.hookApp() }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("PictorialDownloadHooker.hook") { PictorialDownloadHooker.hook(classLoader) }
            }
            BrowserSettings.PACKAGE_NAME -> {
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("BrowserCleanupHooker.hook") { BrowserCleanupHooker.hook(classLoader, target.sourceDir) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("BrowserPurifyHooker.hook") { BrowserPurifyHooker.hook(classLoader) }
            }
            SystemExtrasSettings.WEATHER -> WeatherHooker.hook(classLoader)
            SystemExtrasSettings.PICKER -> NativePhotoPickerHooker.hook(classLoader)
            SystemExtrasSettings.ENGINEER -> EngineerModeHooker.hook(classLoader, target.sourceDir)
            KeyboardMaterialRules.PACKAGE_NAME -> {
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("KeyboardAppearanceHooker.hook") { KeyboardAppearanceHooker.hook(classLoader, target.sourceDir) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("KeyboardMaterialHooker.hook") { KeyboardMaterialHooker.hook(classLoader, target.sourceDir) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("KeyboardFlagEmojiHooker.hook") { KeyboardFlagEmojiHooker.hook(classLoader, target.sourceDir) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("KeyboardBottomLiftHooker.hook") { KeyboardBottomLiftHooker.hook(classLoader, target.sourceDir) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("KeyboardDismissButtonHooker.hook") { KeyboardDismissButtonHooker.hook(classLoader) }
            }
            "com.oplus.battery" -> {
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("BatteryAiAssistantHooker.hook") { BatteryAiAssistantHooker.hook(classLoader, target.sourceDir) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("BatteryCycleCountHooker.hook") { BatteryCycleCountHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("BatteryOptimizationHooker.hookBattery") { BatteryOptimizationHooker.hookBattery(classLoader, target.sourceDir) }
            }

            CameraGallerySettings.PACKAGE_NAME -> com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("CameraGalleryHooker.hook") { CameraGalleryHooker.hook(classLoader, target.sourceDir) }
            ScreenTranslationSettings.PACKAGE_NAME -> com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("ScreenTranslationHooker.hook") { ScreenTranslationHooker.hook(classLoader, target.sourceDir) }
            SmartSidebarSettings.PACKAGE_NAME -> com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("SmartSidebarHooker.hook") { SmartSidebarHooker.hook(classLoader, target.sourceDir) }
            InstallerCleanupSettings.APP_DETAIL, InstallerCleanupSettings.PACKAGE_INSTALLER ->
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("InstallerCleanupHooker.hook") { InstallerCleanupHooker.hook(classLoader, target.sourceDir, packageName) }

            FileManagerCardRules.PACKAGE_NAME -> com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("FileManagerHooker.hook") { FileManagerHooker.hook(classLoader) }

            "com.oplus.securitypermission" -> com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("SecurityPermissionHooker.hook") { SecurityPermissionHooker.hook(classLoader) }
            AppLockDelaySettings.PACKAGE -> com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("AppLockDelayMenuHooker.hook") { AppLockDelayMenuHooker.hook(classLoader) }

            PhoneManagerSettings.PACKAGE_NAME -> {
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("PhoneManagerCountdownHooker.hook") { PhoneManagerCountdownHooker.hook(classLoader, target.sourceDir) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("PhoneManagerRiskCloudHooker.hook") { PhoneManagerRiskCloudHooker.hook(classLoader, target.sourceDir) }
            }

            OShareSettings.PACKAGE_NAME -> com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("OShareVisibilityHooker.hook") { OShareVisibilityHooker.hook(classLoader) }
            ThemeStoreSettings.PACKAGE_NAME -> com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("ThemeStoreHooker.hook") { ThemeStoreHooker.hook(classLoader) }
            GalleryAiRetouchSettings.PACKAGE_NAME -> {
                if (processName == GalleryAiRetouchSettings.PACKAGE_NAME)
                    com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("GalleryAiRetouchHooker.hook") { GalleryAiRetouchHooker.hook(classLoader, target.sourceDir) }
            }
            SecurePaySettings.PACKAGE_NAME -> com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("SecurePayHooker.hook") { SecurePayHooker.hook(classLoader, target.sourceDir) }
            GameAssistantSettings.PACKAGE_NAME -> com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("GameAssistantHooker.hook") { GameAssistantHooker.hook(classLoader, target.sourceDir) }

            SoftwareUpdateSettings.PACKAGE_NAME ->
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("SoftwareUpdateHooker.hook") { SoftwareUpdateHooker.hook(classLoader, target.sourceDir) }

            SoftwareUpdateSettings.SYSTEM_FILES_PACKAGE_NAME ->
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("SystemFileUpdateHooker.hook") { SystemFileUpdateHooker.hook(classLoader, target.sourceDir) }

            FluidCloudBrowserSettings.PACKAGE_NAME,
            FluidCloudBrowserSettings.METIS_PACKAGE_NAME -> com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("FluidCloudBrowserHooker.hook") { FluidCloudBrowserHooker.hook(classLoader) }

            "com.android.permissioncontroller" ->
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("PermissionUiStyleHooker.hookPermissionController") { PermissionUiStyleHooker.hookPermissionController(classLoader) }

            PACKAGE_GOOGLE_APP -> {
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("OkGoogleHotwordCompatibilityHooker.hookGoogleApp") { OkGoogleHotwordCompatibilityHooker.hookGoogleApp(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("GoogleLensAiHooker.hookGoogleApp") { GoogleLensAiHooker.hookGoogleApp(classLoader) }
            }
            PACKAGE_GOOGLE_MESSAGES -> com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("GoogleMessagesHooker.hook") { GoogleMessagesHooker.hook(classLoader) }
            "com.heytap.mcs" -> com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("OppoPushMonitorHooker.hook") { OppoPushMonitorHooker.hook(classLoader) }
            "com.oplus.screenshot" -> {
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("AodScreenshotHooker.hook") { AodScreenshotHooker.hook(classLoader) }
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("HideScreenshotFloatingHooker.hook") { HideScreenshotFloatingHooker.hook(classLoader) }
            }
            "com.oplus.screenrecorder" -> com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("KeyguardCaptureHooker.hookRecorder") { KeyguardCaptureHooker.hookRecorder(classLoader) }
            ExpressCardRules.PLATFORM, ExpressCardRules.ASSISTANT -> com.mi.onextbox.diagnostics.RuntimeDiagnostics.measure("ExpressCardHooker.hook") { ExpressCardHooker.hook(packageName, classLoader) }
        }

    }

    override fun onHotReloading(param: XposedModuleInterface.HotReloadingParam): Boolean {
        if (com.mi.onextbox.lsp.compat.SafeModeRuntime.blocked) return false
        val session = ModernHotReloadSession.current
        val currentTargets = reloadTargets.toList()
        if (!ModuleHotReloadPolicy.canReload(
                processName, systemServer, currentTargets.map { it.packageName }.toSet(),
                session?.healthy == true && currentTargets.isNotEmpty() &&
                    !HotReloadLifecycle.isRetiring && HookConfigSnapshot.isListening && HookConfigSnapshot.isAvailable,
            )) {
            reloadStatus("Hot reload deferred for $processName; restart this scope. " +
                "healthy=${session?.healthy}, preferences=${HookConfigSnapshot.isListening}, " +
                "snapshot=${HookConfigSnapshot.isAvailable}, retiring=${HotReloadLifecycle.isRetiring}, " +
                "packages=${currentTargets.map { it.packageName }.distinct()}")
            return false
        }
        var resourceState: Map<String, Any?>? = null
        return runCatching {
            resourceState = HotReloadLifecycle.prepare()
            val targets = reloadTargets.toList()
            check(ModuleHotReloadPolicy.canReload(
                processName, systemServer, targets.map { it.packageName }.toSet(), session?.healthy == true,
            )) { "Lifecycle changed while preparing hot reload" }
            val mailbox = HotReloadDispatchMailbox.retire(reloadMailbox)
            // Only platform values and the host's loader cross the module-classloader boundary.
            param.setSavedInstanceState(arrayOf(
                RELOAD_PROTOCOL, processName, systemServer, targets.map { it.export() }.toTypedArray(), resourceState,
                mailbox,
            ))
            true
        }.onFailure {
            runCatching {
                resourceState?.let { state -> HotReloadLifecycle.cancelPreparation(state) }
                if (!HotReloadLifecycle.isRetiring) HotReloadDispatchMailbox.bind(reloadMailbox, lifecycleReceiver)
            }.onFailure { recoveryError ->
                session?.markFailed()
                if (recoveryError !== it) it.addSuppressed(recoveryError)
            }
            if (HotReloadLifecycle.isRetiring) session?.markFailed()
            reloadStatus("Hot reload preparation rejected; restart $processName", it)
        }
            .getOrDefault(false)
    }

    override fun onHotReloaded(param: XposedModuleInterface.HotReloadedParam) {
        var session: ModernHotReloadSession.Session? = null
        try {
            val state = param.savedInstanceState as? Array<*> ?: error("Missing hot-reload state")
            check(state.size == 6 && state[0] == RELOAD_PROTOCOL) { "Unsupported hot-reload state" }
            processName = param.processName
            systemServer = param.isSystemServer
            check(state[1] == processName && state[2] == systemServer &&
                ModuleHotReloadPolicy.supportsProcess(processName, systemServer)) {
                "Process is not supported for hot reload"
            }
            val rows = state[3] as? Array<*> ?: error("Missing target lifecycle records")
            val targets = rows.map(::readTarget)
            check(ModuleHotReloadPolicy.canReload(
                processName, systemServer, targets.map { it.packageName }.toSet(), targets.isNotEmpty(),
            )) { "Unsupported lifecycle records" }
            @Suppress("UNCHECKED_CAST")
            val resources = state[4] as? Map<String, Any?> ?: error("Missing resource ownership state")
            @Suppress("UNCHECKED_CAST")
            val mailbox = state[5] as? Array<Any?> ?: error("Missing lifecycle handover")
            reloadMailbox = mailbox
            HotReloadLifecycle.begin(resources)
            session = ModernHotReloadSession.begin(
                staging = true, previousTargets = param.oldHookHandles.map { it.executable },
            )
            check(attachRuntime() && HookConfigSnapshot.isAvailable) { "Remote preferences are not ready" }

            // API 102 does not replay initial callbacks. Preserve the exact dispatch order and paths.
            targets.forEach { target ->
                if (target.system) dispatchSystemServer(target.classLoader) else dispatchPackage(target)
                val dispatchKey = "${target.packageName}@${System.identityHashCode(target.classLoader)}"
                dispatchedPackages.add(dispatchKey)
                reloadTargets += target
            }
            HotReloadLifecycle.replaySavedState()
            session.commit(param.oldHookHandles)
            HotReloadLifecycle.complete()
            HotReloadDispatchMailbox.bind(reloadMailbox, lifecycleReceiver)
            reloadStatus("Hot reload completed for $processName")
        } catch (error: Throwable) {
            session?.markFailed()
            runCatching { HotReloadLifecycle.fail() }
            if (session?.hasCommittedHooks != true) HookConfigSnapshot.detach()
            reloadStatus("Hot reload failed; restart $processName", error)
            // Do not call the default callback: it would remove all previous hooks on failure.
            throw IllegalStateException("Hot reload failed; restart $processName", error)
        }
    }

    private fun reloadStatus(message: String, error: Throwable? = null) {
        // Keep a concise local outcome even when the framework rate-limits hook-install logs.
        if (error == null) {
            Log.i(TAG, message)
            HookLog.i(TAG, message)
        } else {
            Log.w(TAG, message, error)
            HookLog.w(TAG, message, error)
        }
    }

    private companion object {
        const val TAG = "ONextBox-Entry"
        const val RELOAD_PROTOCOL = "onextbox.reload.lifecycle.v3"
        const val PREFERENCES_GROUP = "lsp_features"
        const val PACKAGE_SYSTEM = "system"
        const val PACKAGE_ANDROID = "android"
        const val PACKAGE_SYSTEM_UI = "com.android.systemui"
        const val PACKAGE_SETTINGS = "com.android.settings"
        const val PACKAGE_LAUNCHER = "com.android.launcher"
        const val PACKAGE_OPLUS_AOD = "com.oplus.aod"
        const val PACKAGE_ATHENA = "com.oplus.athena"
        const val PACKAGE_OPLUS_EUICC = "com.oplus.euicc"
        const val PACKAGE_WALLPAPERS = "com.oplus.wallpapers"
        const val PACKAGE_PHONE = "com.android.phone"
        const val PACKAGE_APP_MARKET = "com.heytap.market"
        const val PACKAGE_GOOGLE_APP = "com.google.android.googlequicksearchbox"
        const val PACKAGE_GOOGLE_MESSAGES = "com.google.android.apps.messaging"
    }
}
