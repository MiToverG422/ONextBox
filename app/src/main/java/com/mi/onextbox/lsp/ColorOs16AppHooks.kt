package com.mi.onextbox.lsp

import android.content.ContentResolver
import android.content.Context
import android.content.DialogInterface
import android.provider.Settings
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface

/** App adapters checked against the PMA110 16.0.10.501 package capture. */
internal object ColorOs16AppHooks {
    fun hook(packageName: String, loader: ClassLoader, apkPath: String, processName: String) {
        when (packageName) {
            "com.oplus.athena" -> AthenaPolicyHooker.hookColorOs16(loader)
            "com.android.phone" -> {
                MobileNetworkHooker.hook(loader, colorOs16 = true)
                NetworkIdentityHooker.hook(loader, processName)
            }
            "com.heytap.market" -> AppMarketHooker.hook(loader)
            TrafficManagementRules.PACKAGE_NAME -> TrafficManagementHooker.hook(loader, apkPath, colorOs16 = true)
            "com.oplus.battery" -> {
                BatteryCycleCountHooker.hook(loader)
                BatteryOptimizationHooker.hookBattery(loader, apkPath)
            }
            "com.oplus.securitypermission" -> SecurityPermissionHooker.hook(loader, colorOs16 = true)
            AppLockDelaySettings.PACKAGE -> AppLockDelayMenuHooker.hook(loader)
            PhoneManagerSettings.PACKAGE_NAME -> {
                PhoneManagerCountdownHooker.hook(loader, apkPath)
                PhoneManagerRiskCloudHooker.hook(loader, apkPath)
            }
            OShareSettings.PACKAGE_NAME -> OShareVisibilityHooker.hookColorOs16(loader)
            ThemeStoreSettings.PACKAGE_NAME -> ThemeStoreHooker.hook(loader, colorOs16 = true)
            SecurePaySettings.PACKAGE_NAME -> SecurePayHooker.hook(loader, apkPath)
            GameAssistantSettings.PACKAGE_NAME -> GameAssistantHooker.hook(loader, apkPath, colorOs16 = true)
            SoftwareUpdateSettings.PACKAGE_NAME -> {
                SoftwareUpdateHooker.hook(loader, apkPath)
                hookUpdaterRecommendations(loader)
            }
            SoftwareUpdateSettings.SYSTEM_FILES_PACKAGE_NAME -> SystemFileUpdateHooker.hook(loader, apkPath)
            FluidCloudBrowserSettings.PACKAGE_NAME,
            FluidCloudBrowserSettings.METIS_PACKAGE_NAME -> FluidCloudBrowserHooker.hook(loader)
            "com.android.permissioncontroller" -> PermissionUiStyleHooker.hookPermissionController(loader, colorOs16 = true)
            "com.heytap.mcs" -> OppoPushMonitorHooker.hook(loader)
        }
    }

    /** C16 combines dialog dismissal/bookkeeping with the recommendation; keep those actions. */
    private fun hookUpdaterRecommendations(loader: ClassLoader) {
        val prefix = "ColorOs16Updater:recommendations@${System.identityHashCode(loader)}"
        val scope = ColorOs16UpdaterRecommendationScope()
        runCatching {
            val owner = loader.loadClass("w7.n")
            val callbacks = listOf("i", "k").map { name ->
                owner.getDeclaredMethod(name, Context::class.java, DialogInterface::class.java).also {
                    check(it.returnType == Void.TYPE)
                }
            }
            val read = Settings.Global::class.java.getDeclaredMethod("getInt", ContentResolver::class.java,
                String::class.java, Int::class.javaPrimitiveType)
            ModernHookRegistry.installFast("$prefix:read", read, XposedInterface.Hooker { chain ->
                scope.replacement(chain.args.getOrNull(1) as? String,
                    SoftwareUpdateSettings.enabledXposed(SoftwareUpdateFeature.HideRecommendations)) ?: chain.proceed()
            })
            callbacks.forEach { callback ->
                ModernHookRegistry.installFast("$prefix:${callback.name}", callback, XposedInterface.Hooker { chain ->
                    scope.enter { chain.proceed() }
                })
                ModernHookRuntime.requireModule().deoptimize(callback)
            }
        }.onFailure {
            ModernHookRegistry.unhookPrefix(prefix)
            HookLog.w("ColorOs16Updater", "Recommendation adapter unavailable; stock behavior retained", it)
        }
    }
}
