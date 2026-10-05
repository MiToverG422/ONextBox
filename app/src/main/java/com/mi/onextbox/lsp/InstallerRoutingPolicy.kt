package com.mi.onextbox.lsp

internal object InstallerRoutingPolicy {
    const val APK_TYPE = "application/vnd.android.package-archive"
    const val VIEW = "android.intent.action.VIEW"
    const val INSTALL = "android.intent.action.INSTALL_PACKAGE"
    const val DELETE = "android.intent.action.DELETE"
    const val UNINSTALL = "android.intent.action.UNINSTALL_PACKAGE"
    const val CONFIRM_INSTALL = "android.content.pm.action.CONFIRM_INSTALL"
    const val CONFIRM_PERMISSIONS = "android.content.pm.action.CONFIRM_PERMISSIONS"

    fun isUninstall(action: String?) = action == DELETE || action == UNINSTALL

    fun validPackage(value: String): Boolean = value.isEmpty() ||
        (value.length <= 8192 && value.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")))

    fun shouldRedirect(
        action: String?, type: String?, uri: String?, componentPackage: String?,
        uninstallEnabled: Boolean, sessionEnabled: Boolean,
    ): Boolean {
        if (action !in setOf(VIEW, INSTALL, DELETE, UNINSTALL, CONFIRM_INSTALL, CONFIRM_PERMISSIONS)) return false
        if (componentPackage != null) return false
        if (isUninstall(action)) return uninstallEnabled && uri?.startsWith("package:") == true
        if (action == CONFIRM_INSTALL || action == CONFIRM_PERMISSIONS) return sessionEnabled
        if (action == INSTALL || type == APK_TYPE) return true
        val path = uri.orEmpty().substringBefore('?').substringBefore('#').lowercase()
        return (path.startsWith("file://") || path.startsWith("content://")) &&
            listOf(".apk", ".apks", ".apk.1").any(path::endsWith)
    }

    fun target(action: String?, installer: String, uninstaller: String, follow: Boolean): String =
        if (isUninstall(action) && !follow) uninstaller else installer
}
