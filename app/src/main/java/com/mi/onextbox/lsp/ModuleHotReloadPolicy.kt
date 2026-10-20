package com.mi.onextbox.lsp

/** Scope eligibility is separate from the resource owners' runtime readiness checks. */
internal object ModuleHotReloadPolicy {
    val supportedPackages: Set<String> = setOf(
        "com.xiaomi.touchservice", "com.miui.securitycenter",
        "system", "com.android.systemui", "com.oplus.screenshot", "com.oplus.screenrecorder",
        "com.android.settings", "com.oplus.securitypermission", "com.oplus.safecenter", "com.android.permissioncontroller",
        "com.coloros.phonemanager", "com.coloros.colordirectservice", "com.oplus.metis", "com.oplus.ota", "com.oplus.sau",
        "com.android.launcher", "com.oplus.aod",
        "com.oplus.athena", "com.oplus.euicc", "com.oplus.wallpapers", "com.android.phone",
        "com.google.android.googlequicksearchbox", "com.google.android.apps.messaging",
        "com.heytap.market", "com.oplus.battery", "com.oplus.trafficmonitor", "com.oplus.keyboard",
        "com.coloros.filemanager", "com.heytap.mcs", "com.nearme.instant.platform",
        "com.coloros.assistantscreen", "com.coloros.oshare", "com.heytap.colorfulengine",
        "com.heytap.themestore", "com.coloros.securepay", "com.oplus.games", "com.coloros.gallery3d",
    )

    fun supportsProcess(processName: String, isSystemServer: Boolean): Boolean =
        if (isSystemServer) processName == "system" || processName == "system_server"
        else processName.substringBefore(':').let { it != "system" && it in supportedPackages }

    fun canReload(
        processName: String,
        isSystemServer: Boolean,
        loadedPackages: Set<String>,
        healthy: Boolean,
    ): Boolean = healthy && supportsProcess(processName, isSystemServer) &&
        // Package contexts can belong to themes, resource overlays or host plugins outside the
        // scope list. They are replayable companions, not evidence of another primary process.
        loadedPackages.none { it.isBlank() || it == "android" || it == "system_server" } &&
        if (isSystemServer) "system" in loadedPackages
        else processName.substringBefore(':') in loadedPackages && "system" !in loadedPackages
}
