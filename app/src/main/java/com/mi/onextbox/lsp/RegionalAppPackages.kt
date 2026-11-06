package com.mi.onextbox.lsp

import android.content.Context

internal object RegionalAppPackages {
    const val THEME_GLOBAL = "com.oplus.themestore"
    const val ESIM_GLOBAL = "com.google.android.euicc"
    const val INSTALLER_GLOBAL = "com.google.android.packageinstaller"
    const val APP_INSTALLER_GLOBAL = "com.oplus.stdsp"
    const val PICKER_GLOBAL = "com.google.android.photopicker"
    val themes = listOf(ThemeStoreSettings.PACKAGE_NAME, THEME_GLOBAL)
    val esim = listOf("com.oplus.euicc", ESIM_GLOBAL)
    val installers = listOf(InstallerCleanupSettings.PACKAGE_INSTALLER, INSTALLER_GLOBAL)
    val appInstallers = listOf(InstallerCleanupSettings.APP_DETAIL, APP_INSTALLER_GLOBAL)
    val pickers = listOf(SystemExtrasSettings.PICKER, PICKER_GLOBAL)
    fun installed(context: Context, packages: List<String>): List<String> = packages.filter {
        runCatching { context.packageManager.getApplicationInfo(it, 0).enabled }.getOrDefault(false)
    }
    fun googleEsim(context: Context): Boolean = installed(context, esim).let {
        ESIM_GLOBAL in it && "com.oplus.euicc" !in it
    }
    fun stdspInstaller(context: Context): Boolean = installed(context, appInstallers).let {
        APP_INSTALLER_GLOBAL in it && InstallerCleanupSettings.APP_DETAIL !in it
    }
    fun googleInstaller(context: Context): Boolean = installed(context, installers).let {
        INSTALLER_GLOBAL in it && InstallerCleanupSettings.PACKAGE_INSTALLER !in it
    }

}
