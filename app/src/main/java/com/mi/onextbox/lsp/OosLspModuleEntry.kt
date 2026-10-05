package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.HookConfigSnapshot
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.util.concurrent.ConcurrentHashMap

/** Single modern API 102 entry for all ONextBox scopes. */
class OosLspModuleEntry : XposedModule() {
    private val dispatchedPackages = ConcurrentHashMap.newKeySet<String>()

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        ModernHookRuntime.attach(this)
        runCatching { getRemotePreferences(PREFERENCES_GROUP) }
            .onSuccess {
                ModernHookRuntime.attachRemotePreferences(PREFERENCES_GROUP, it)
                HookConfigSnapshot.attach(it)
            }
            .onFailure {
                HookLog.w(
                    TAG,
                    "Remote preferences unavailable in ${param.processName}; synced system state will be used",
                    it,
                )
            }
        HookLog.i(
            TAG,
            "Modern API $apiVersion attached: ${param.processName} " +
                "(${frameworkName} ${frameworkVersion})",
        )
    }

    override fun onSystemServerStarting(
        param: XposedModuleInterface.SystemServerStartingParam,
    ) {
        ModernHookRuntime.attach(this)
        val loader = param.classLoader
        FrameworkHooker.hook(packageName = PACKAGE_SYSTEM, classLoader = loader)
        PermissionSystemHooker.hook(loader)
        NotificationRemovalHooker.hookSystemServer(loader)
        InstallerRedirectHooker.hook(loader)
        SettingsGlobalExtremeRefreshRateHooker.hook(classLoader = loader)
        AssistantHooker.hook(packageName = PACKAGE_SYSTEM, classLoader = loader)
        GmsRegionRestrictionHooker.hook(classLoader = loader)
        AthenaPolicyHooker.hook(classLoader = loader)
        OkGoogleHotwordCompatibilityHooker.hookSystemServer(classLoader = loader)
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        ModernHookRuntime.attach(this)
        val packageName = param.packageName

        // API 102 reports every package that becomes ready in a process. SystemUI can later load
        // the framework package "android" via a package context, but framework hooks belong only
        // to onSystemServerStarting. Keep later named packages available for legitimate shared
        // processes and localizer scopes while rejecting this ambiguous framework callback.
        if (packageName == PACKAGE_ANDROID) return

        val classLoader = param.classLoader
        val dispatchKey = "$packageName@${System.identityHashCode(classLoader)}"
        if (!dispatchedPackages.add(dispatchKey)) return

        when (packageName) {
            PACKAGE_SYSTEM_UI -> {
                AssistantHooker.hook(packageName, classLoader)
                SystemUiHooker.hook(packageName, classLoader)
                SystemUiRedOneHooker.hook(classLoader)
                NotificationRemovalHooker.hookSystemUi(classLoader)
            }

            PACKAGE_SETTINGS -> {
                AssistantHooker.hook(packageName, classLoader)
                SettingsHooker.hook(
                    packageName = packageName,
                    classLoader = classLoader,
                )
            }

            PACKAGE_LAUNCHER -> {
                FrameworkHooker.hook(packageName, classLoader)
                AssistantHooker.hook(packageName, classLoader)
                LauncherHooker.hook(classLoader)
                SystemUiRedOneHooker.hookLauncherClock()
            }

            PACKAGE_OPLUS_AOD -> FrameworkHooker.hook(packageName, classLoader)

            PACKAGE_ATHENA -> AthenaPolicyHooker.hook(classLoader)

            PACKAGE_OPLUS_EUICC -> {
                EuiccRegionRestrictionHooker.hook(classLoader)
                EuiccDiagnosticsHooker.hook(classLoader)
            }

            PACKAGE_WALLPAPERS -> WallpapersRedOneEntryHooker.hook(classLoader)

            PACKAGE_PHONE -> MobileNetworkHooker.hook(classLoader)

            PACKAGE_APP_MARKET -> AppMarketHooker.hook(classLoader)

            "com.oplus.securitypermission" -> SecurityPermissionHooker.hook(classLoader)

            "com.android.permissioncontroller", "com.google.android.permissioncontroller" ->
                PermissionUiStyleHooker.hookPermissionController(classLoader)

            PACKAGE_GOOGLE_APP -> OkGoogleHotwordCompatibilityHooker.hookGoogleApp(classLoader)

        }

        if (packageName in OosLocalizerHooker.supportedPackageNames) {
            OosLocalizerHooker.hook(packageName, classLoader)
        }
    }

    override fun onHotReloading(param: XposedModuleInterface.HotReloadingParam): Boolean {
        // Stateful OEM hooks are installed only at process start. Refuse partial hot reloads.
        return false
    }

    private companion object {
        const val TAG = "ONextBox-Entry"
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
    }
}
