package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.ModernReflect

internal object ColorOs16FrameworkHooks {
    private const val TAG = "ONextBox-C16-Framework"

    fun hook(loader: ClassLoader) {
        install(loader, "corepatch", "com.android.server.pm.PackageManagerServiceUtils" to "checkDowngrade") {
            CorePatchIntegration.hook(loader)
        }
        install(loader, "notifications", "com.android.server.notification.OplusNotificationFixHelper" to "fixSmallIcon",
            "com.android.server.notification.BubbleExtractor" to "canPresentAsBubble") {
            FrameworkHooker.hook("system", loader, colorOs16 = true)
        }
        install(loader, "navigation", "android.content.res.Resources" to "getDimensionPixelSize") {
            if (LspConfig.isImmersiveNavigationEnabledXposed()) ImmersiveNavigationHooker.hook()
        }
        install(loader, "errors", "com.android.server.am.ActivityManagerService" to "addErrorToDropBox") {
            AppErrorTrackingHooker.hookSystemServer(loader)
        }
        install(loader, "scroll-to-top", "com.android.server.OplusScrollToTopRusHelper" to "isInWhiteList") {
            StatusBarScrollToTopHooker.hookSystemServer(loader)
        }
        install(loader, "battery-whitelist", "com.android.server.OplusDeviceIdleHelper" to "getNewWhiteList") {
            BatteryOptimizationHooker.hookSystemServer(loader)
        }
        install(loader, "root-detection", "com.android.server.oplus.heimdall.service.RootService" to "isRoot",
            "com.android.server.oplus.heimdall.root.RootDetector" to "checkDeviceRootStatus") {
            SystemRootDetectionHooker.hookSystemServer(loader)
        }
        install(loader, "permission", "com.android.server.wm.OplusAppStartConfirmManager" to "checkMaliciousIntercept",
            "com.android.server.am.OplusActivityStartController" to "init") { PermissionSystemHooker.hook(loader) }
        install(loader, "app-lock", "com.android.server.wm.OplusAccessControlManagerService" to "clearAccessControlPassPackages") {
            AppLockDelaySystemHooker.hook(loader, colorOs16 = true)
        }
        install(loader, "notification-removal", "com.android.server.notification.NotificationManagerService" to "enqueueNotificationInternal") {
            NotificationRemovalHooker.hookSystemServer(loader)
        }
        install(loader, "small-window", "com.android.server.wm.FlexibleTaskController" to "fillFlexibleTaskInfo") {
            SmallWindowHooker.hookSystemServer(loader, colorOs16 = true)
        }
        install(loader, "capture", "com.android.server.policy.OplusAODScreenshotManager" to "init",
            "com.android.server.media.projection.MediaProjectionStopController" to "isStartForbidden") {
            KeyguardCaptureHooker.hookSystemServer(loader)
        }
        install(loader, "secure-capture", "com.android.server.wm.WindowState" to "isSecureLocked") {
            ScreenCaptureHooker.hookSystemServer(loader)
        }
        install(loader, "installer", "com.android.server.wm.ActivityStarter" to "execute") {
            InstallerRedirectHooker.hook(loader)
        }
        install(loader, "file-picker", "com.android.server.pm.OplusDefaultAppPolicyManager" to "interceptPickerIntent") {
            NativeFilePickerHooker.hook(loader)
        }
        install(loader, "default-file-manager", "com.android.server.wm.ActivityStarter\$Request" to "resolveActivity") {
            DefaultFileManagerHooker.hook(loader)
        }
        install(loader, "gms-region", "com.android.server.SystemConfig" to "getAvailableFeatures") {
            GmsRegionRestrictionHooker.hook(loader)
        }
        install(loader, "fixed-refresh", "com.android.server.wm.OplusRefreshRatePolicyImpl" to "onSystemReady") {
            SettingsFixedRefreshRateHooker.hookColorOs16(loader)
        }
        install(loader, "assistant-power", "com.android.server.policy.PhoneWindowManagerExtImpl" to "startSpeech") {
            AssistantHooker.hook("system", loader)
        }
        install(loader, "athena-loader", "android.app.ActivityThread" to "getPackageInfoNoCheck") {
            AthenaPolicyHooker.hookColorOs16(loader)
        }
    }

    fun hookApp() = InstallerRedirectHooker.hookApp()

    private inline fun install(loader: ClassLoader, name: String, vararg targets: Pair<String, String>, action: () -> Unit) {
        runCatching {
            targets.forEach { (owner, method) ->
                val type = ModernReflect.findClass(owner, loader)
                check(type.declaredMethods.any { it.name == method }) { "Missing $owner.$method" }
            }
            action()
        }.onFailure { HookLog.w(TAG, "$name unavailable; stock behavior retained", it) }
    }
}
