package com.mi.onextbox.lsp

internal object InstallerRoutingPolicy {
    const val APK_TYPE = "application/vnd.android.package-archive"
    const val VIEW = "android.intent.action.VIEW"
    const val INSTALL = "android.intent.action.INSTALL_PACKAGE"
    const val DELETE = "android.intent.action.DELETE"
    const val UNINSTALL = "android.intent.action.UNINSTALL_PACKAGE"
    const val CONFIRM_INSTALL = "android.content.pm.action.CONFIRM_INSTALL"
    private val actions = setOf(VIEW, INSTALL, DELETE, UNINSTALL, CONFIRM_INSTALL)
    private val packagePattern = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")
    private val numberedApk = Regex(".*\\.apk\\.\\d+$", RegexOption.IGNORE_CASE)

    fun isUninstall(action: String?) = action == DELETE || action == UNINSTALL
    fun validPackage(value: String) = value.isEmpty() ||
        (value.length <= 8192 && value.matches(packagePattern))

    fun isArchiveName(name: String?): Boolean {
        val path = name.orEmpty().substringBefore('?').substringBefore('#')
        return listOf(".apk", ".apks", ".xapk", ".apkm").any { path.endsWith(it, ignoreCase = true) } ||
            numberedApk.matches(path)
    }

    fun shouldRedirect(
        action: String?, type: String?, uri: String?, componentPackage: String?,
        uninstallEnabled: Boolean, sessionEnabled: Boolean,
        systemInstaller: String = "", interceptSystem: Boolean = false,
        displayName: String? = null,
    ): Boolean {
        if (action !in actions) return false
        if (componentPackage != null &&
            (!interceptSystem || systemInstaller.isBlank() || componentPackage != systemInstaller)) return false
        if (isUninstall(action)) return uninstallEnabled && uri?.startsWith("package:") == true
        if (action == CONFIRM_INSTALL) return sessionEnabled
        if (action == INSTALL) return true
        if (uri != null && !uri.startsWith("file://") && !uri.startsWith("content://")) return false
        return type == APK_TYPE || isArchiveName(uri) ||
            (uri?.startsWith("content://") == true && isArchiveName(displayName))
    }
}
