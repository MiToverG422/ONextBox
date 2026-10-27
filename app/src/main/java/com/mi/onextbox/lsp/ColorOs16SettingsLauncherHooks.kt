package com.mi.onextbox.lsp

/** App-process adapters whose C16 native entry points were checked independently of C17. */
internal object ColorOs16SettingsLauncherHooks {
    private const val TAG = "ONextBox-C16Apps"

    fun hookSettings(loader: ClassLoader) {
        group("settings-core") { ColorOs16SettingsHooks.hook(loader) }
        group("settings-entries") { SettingsHooker.hookColorOs16VerifiedEntries(loader) }
        group("settings-title") { SettingsTitleCollapseHooker.hook(loader) }
        group("settings-account-size") { SettingsAccountCardSizeHooker.hook(loader) }
        group("settings-home-entry") { SettingsHomeEntryHooker.hook(loader) }
        group("settings-home-cleanup") { SettingsHomeCleanupHooker.hook(loader) }
        group("settings-process-manager") { SettingsProcessManager.hook(loader) }
        group("settings-realme") { SettingsRealmeFeatures.hook(loader) }
        group("settings-app-info") { SettingsAppInfoCardHooker.hook(loader) }
        group("settings-permission-pages") { PermissionUiStyleHooker.hookSettings(loader) }
        group("settings-native-power") { AssistantHooker.hook("com.android.settings", loader) }
    }

    fun hookLauncher(loader: ClassLoader, apkPath: String) {
        group("recent-radius") { FrameworkHooker.hook("com.android.launcher", loader) }
        group("launcher-labels-search") { LauncherHooker.hook(loader) }
        group("launcher-features") { LauncherFeaturesHooker.hookColorOs16(loader, apkPath) }
        group("launcher-gestures") { LauncherGestureHooker.hook(loader) }
        group("launcher-hidden-recents") { HideRecentAppsHooker.hook(loader) }
        group("launcher-label-spacing") { LauncherIconLabelSpacingHooker.hook(loader) }
        group("launcher-dot-color") { LauncherNotificationDotColorHooker.hook(loader) }
        group("launcher-dot-shadow") { LauncherNotificationDotShadowHooker.hook(loader) }
        group("launcher-express") { ExpressCardHooker.hook(ExpressCardRules.LAUNCHER, loader) }
    }

    fun hookAod(loader: ClassLoader) {
        group("aod-all-day") { FrameworkHooker.hook("com.oplus.aod", loader) }
    }

    fun hookWallpaperMotion(loader: ClassLoader) {
        group("wallpaper-motion") { WallpaperMotionHooker.hook(loader) }
    }

    fun hookExpress(packageName: String, loader: ClassLoader) {
        group("express:$packageName") { ExpressCardHooker.hook(packageName, loader) }
    }

    private inline fun group(name: String, action: () -> Unit) {
        runCatching(action).onFailure { HookLog.w(TAG, "$name: C16 adapter unavailable", it) }
    }
}
