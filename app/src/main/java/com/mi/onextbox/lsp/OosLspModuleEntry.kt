package com.mi.onextbox.lsp

import android.util.Log
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.HookConfigSnapshot
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.HotReloadDispatchMailbox
import com.mi.onextbox.lsp.compat.ModernHotReloadSession
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
        HotReloadLifecycle.withHook(
            fallback = { deferLifecycle(row) },
            action = { dispatchLifecycle(readTarget(row)) },
        )
    }
    private var processName = ""
    private var systemServer = false

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
        return runCatching {
            val preferences = getRemotePreferences(PREFERENCES_GROUP)
            ModernHookRuntime.attachRemotePreferences(PREFERENCES_GROUP, preferences)
            check(HookConfigSnapshot.attach(preferences)) { "Preference listener is unavailable" }
            HotReloadLifecycle.register(
                key = "core.preferences",
                detach = { check(HookConfigSnapshot.detach()) { "Preference listener could not be removed" } },
                restore = { check(HookConfigSnapshot.attach(preferences)) { "Preference listener could not be restored" } },
            )
        }
            .onFailure {
                HookLog.w(
                    TAG,
                    "Remote preferences unavailable in $processName; synced system state will be used",
                    it,
                )
            }.isSuccess
    }

    override fun onSystemServerStarting(
        param: XposedModuleInterface.SystemServerStartingParam,
    ) {
        HotReloadDispatchMailbox.submit(
            reloadMailbox, ReloadTarget(PACKAGE_SYSTEM, param.classLoader, "", system = true).export(),
        )
    }

    private fun dispatchSystemServer(loader: ClassLoader) {
        if (LspConfig.isImmersiveNavigationEnabledXposed()) {
            ImmersiveNavigationHooker.hook()
        }
        FrameworkHooker.hook(packageName = PACKAGE_SYSTEM, classLoader = loader)
        SideKeyHooker.hookSystemServer(loader)
        AppErrorTrackingHooker.hookSystemServer(loader)
        StatusBarScrollToTopHooker.hookSystemServer(loader)
        BatteryOptimizationHooker.hookSystemServer(loader)
        SystemRootDetectionHooker.hookSystemServer(loader)
        PermissionSystemHooker.hook(loader)
        NotificationRemovalHooker.hookSystemServer(loader)
        SmallWindowHooker.hookSystemServer(loader)
        KeyguardCaptureHooker.hookSystemServer(loader)
        ScreenCaptureHooker.hookSystemServer(loader)
        InstallerRedirectHooker.hook(loader)
        NativeFilePickerHooker.hook(loader)
        SettingsGlobalExtremeRefreshRateHooker.hook(classLoader = loader)
        AssistantHooker.hook(packageName = PACKAGE_SYSTEM, classLoader = loader)
        GmsRegionRestrictionHooker.hook(classLoader = loader)
        AthenaPolicyHooker.hook(classLoader = loader)
        OkGoogleHotwordCompatibilityHooker.hookSystemServer(classLoader = loader)
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
        ModernHookRuntime.attach(this)
        val dispatchKey = "${target.packageName}@${System.identityHashCode(target.classLoader)}"
        if (!dispatchedPackages.add(dispatchKey)) return
        reloadTargets += target
        if (target.system) dispatchSystemServer(target.classLoader) else dispatchPackage(target)
    }

    private fun dispatchPackage(target: ReloadTarget) {
        val packageName = target.packageName
        val classLoader = target.classLoader

        InstallerRedirectHooker.hookApp()

        when (packageName) {
            PACKAGE_SYSTEM_UI -> {
                LargeNetworkTypeHooker.hook(classLoader)
                DataSimSignalHooker.hook(classLoader)
                StatusBarLayoutHooker.hook(classLoader)
                CarrierNameHooker.hook(classLoader)
                runCatching { QsTileCornerHooker.hook(classLoader) }
                    .onFailure { HookLog.w("ONextBox-QsTileCorner", "Tile corner adapter unavailable", it) }
                SideKeySystemUiHooker.hook(classLoader)
                ImmersiveNavigationHooker.hook(classLoader)
                AospBackPanelHooker.hook(classLoader)
                AospVolumeHooker.hook(classLoader)
                C16StackedSignalHooker.hook(classLoader)
                AssistantHooker.hook(packageName, classLoader)
                SystemUiHooker.hook(packageName, classLoader)
                DisconnectedBluetoothIconHooker.hook(classLoader)
                StatusBarScrollToTopHooker.hookSystemUi(classLoader)
                FluidCloudBatteryHooker.hook(classLoader)
                FluidCloudMaterialHooker.hook(classLoader)
                PermanentClockSecondsHooker.hookSystemUi(classLoader)
                SystemUiRedOneHooker.hook(classLoader)
                NotificationRemovalHooker.hookSystemUi(classLoader)
                NotificationClearButtonHooker.hook(classLoader)
                SmallWindowHooker.hookSystemUi(classLoader)
                FaceTapUnlockHooker.hook(classLoader)
                KeyguardCaptureHooker.hookSystemUi(classLoader)
                PasswordDialHighlightHooker.hook(classLoader)
                FluidCloudMediaBlacklistHooker.hook(classLoader)
            }

            PACKAGE_SETTINGS -> {
                AssistantHooker.hook(packageName, classLoader)
                SettingsHooker.hook(
                    packageName = packageName,
                    classLoader = classLoader,
                )
                SettingsTitleCollapseHooker.hook(classLoader)
                SettingsAppInfoCardHooker.hook(classLoader)
                PermanentClockSecondsHooker.hookSettings(classLoader)
            }

            PACKAGE_LAUNCHER -> {
                FrameworkHooker.hook(packageName, classLoader)
                AssistantHooker.hook(packageName, classLoader)
                LauncherHooker.hook(classLoader)
                LauncherFeaturesHooker.hook(classLoader, target.sourceDir)
                LauncherGestureHooker.hook(classLoader)
                LauncherNotificationDotColorHooker.hook(classLoader)
                LauncherNotificationDotShadowHooker.hook(classLoader)
                ExpressCardHooker.hook(packageName, classLoader)
                SystemUiRedOneHooker.hookLauncherClock()
            }

            PACKAGE_OPLUS_AOD -> FrameworkHooker.hook(packageName, classLoader)

            PACKAGE_ATHENA -> AthenaPolicyHooker.hook(classLoader)

            PACKAGE_OPLUS_EUICC -> {
                EuiccRegionRestrictionHooker.hook(classLoader)
                EuiccDiagnosticsHooker.hook(classLoader)
            }

            PACKAGE_WALLPAPERS -> {
                WallpapersRedOneEntryHooker.hook(classLoader)
                WallpaperMotionHooker.hook(classLoader)
            }
            WallpaperMotionSettings.ENGINE_PACKAGE -> {
                WallpaperMotionHooker.hook(classLoader)
            }

            PACKAGE_PHONE -> {
                MobileNetworkHooker.hook(classLoader)
                NetworkIdentityHooker.hook(classLoader, processName)
            }

            PACKAGE_APP_MARKET -> AppMarketHooker.hook(classLoader)
            TrafficManagementRules.PACKAGE_NAME ->
                TrafficManagementHooker.hook(classLoader, target.sourceDir)
            KeyboardMaterialRules.PACKAGE_NAME -> {
                KeyboardMaterialHooker.hook(classLoader, target.sourceDir)
                KeyboardFlagEmojiHooker.hook(classLoader, target.sourceDir)
                KeyboardBottomLiftHooker.hook(classLoader, target.sourceDir)
                KeyboardDismissButtonHooker.hook(classLoader)
            }
            "com.oplus.battery" -> {
                BatteryCycleCountHooker.hook(classLoader)
                BatteryOptimizationHooker.hookBattery(classLoader, target.sourceDir)
            }

            FileManagerCardRules.PACKAGE_NAME -> FileManagerHooker.hook(classLoader)

            "com.oplus.securitypermission" -> SecurityPermissionHooker.hook(classLoader)

            PhoneManagerSettings.PACKAGE_NAME ->
                PhoneManagerCountdownHooker.hook(classLoader, target.sourceDir)

            OShareSettings.PACKAGE_NAME -> OShareVisibilityHooker.hook(classLoader)
            ThemeStoreSettings.PACKAGE_NAME -> ThemeStoreHooker.hook(classLoader)
            SecurePaySettings.PACKAGE_NAME -> SecurePayHooker.hook(classLoader, target.sourceDir)
            GameAssistantSettings.PACKAGE_NAME -> GameAssistantHooker.hook(classLoader, target.sourceDir)

            SoftwareUpdateSettings.PACKAGE_NAME ->
                SoftwareUpdateHooker.hook(classLoader, target.sourceDir)

            SoftwareUpdateSettings.SYSTEM_FILES_PACKAGE_NAME ->
                SystemFileUpdateHooker.hook(classLoader, target.sourceDir)

            FluidCloudBrowserSettings.PACKAGE_NAME,
            FluidCloudBrowserSettings.METIS_PACKAGE_NAME -> FluidCloudBrowserHooker.hook(classLoader)

            "com.android.permissioncontroller" ->
                PermissionUiStyleHooker.hookPermissionController(classLoader)

            PACKAGE_GOOGLE_APP -> {
                OkGoogleHotwordCompatibilityHooker.hookGoogleApp(classLoader)
                GoogleLensAiHooker.hookGoogleApp(classLoader)
            }
            PACKAGE_GOOGLE_MESSAGES -> GoogleMessagesHooker.hook(classLoader)
            "com.heytap.mcs" -> OppoPushMonitorHooker.hook(classLoader)
            "com.oplus.screenshot" -> AodScreenshotHooker.hook(classLoader)
            "com.oplus.screenrecorder" -> KeyguardCaptureHooker.hookRecorder(classLoader)
            ExpressCardRules.PLATFORM, ExpressCardRules.ASSISTANT -> ExpressCardHooker.hook(packageName, classLoader)
        }

    }

    override fun onHotReloading(param: XposedModuleInterface.HotReloadingParam): Boolean {
        val session = ModernHotReloadSession.current
        val currentTargets = reloadTargets.toList()
        // Read the framework-owned preference at the decision point, not an APK-bundled flag.
        val enabled = runCatching {
            getRemotePreferences(PREFERENCES_GROUP)
                .getBoolean(ModuleHotReloadSettings.PREFERENCE_KEY, false)
        }.getOrDefault(false)
        if (!ModuleHotReloadPolicy.canReload(
                enabled, processName, systemServer, currentTargets.map { it.packageName }.toSet(),
                session?.healthy == true && currentTargets.isNotEmpty() && HookConfigSnapshot.isListening,
            )) {
            if (enabled) reloadStatus("Hot reload deferred for $processName; restart this scope. " +
                "healthy=${session?.healthy}, preferences=${HookConfigSnapshot.isListening}, " +
                "packages=${currentTargets.map { it.packageName }.distinct()}")
            return false
        }
        var resourceState: Map<String, Any?>? = null
        return runCatching {
            resourceState = HotReloadLifecycle.prepare()
            val targets = reloadTargets.toList()
            check(ModuleHotReloadPolicy.canReload(
                enabled, processName, systemServer, targets.map { it.packageName }.toSet(), session?.healthy == true,
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
                true, processName, systemServer, targets.map { it.packageName }.toSet(), targets.isNotEmpty(),
            )) { "Unsupported lifecycle records" }
            @Suppress("UNCHECKED_CAST")
            val resources = state[4] as? Map<String, Any?> ?: error("Missing resource ownership state")
            @Suppress("UNCHECKED_CAST")
            val mailbox = state[5] as? Array<Any?> ?: error("Missing lifecycle handover")
            reloadMailbox = mailbox
            HotReloadLifecycle.begin(resources)
            session = ModernHotReloadSession.begin(staging = true)
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
