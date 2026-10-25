package com.mi.onextbox.lsp

import android.os.Build

/** Read-only device detection, shared by the app and injected processes. */
internal object SystemAdaptationDetector {
    private val detected by lazy {
        SystemAdaptationDetection.detect(
            LspPreferenceStore::readSystemPropertyValue,
            runCatching { Build.DISPLAY }.getOrNull(),
        )
    }
    @Volatile private var processVersion: SystemAdaptationVersion? = null

    fun current(): SystemAdaptationVersion = processVersion ?: detected ?: SystemAdaptationVersion.ColorOs17

    /** Resolve before installing hooks; never switch adapters after a generation has started. */
    @Synchronized
    fun initialize(packageName: String, loader: ClassLoader): SystemAdaptationVersion {
        processVersion?.let { return it }
        val version = SystemAdaptationDetection.resolve(detected) { candidate ->
            packageName == "com.android.launcher" && LauncherSearchTargets.forVersion(candidate).isAvailable(loader)
        }
        processVersion = version
        HookLog.i("ONextBox-Adaptation", "${version.label}: " +
            if (detected != null) "detected from system version" else "API probe/default for $packageName")
        return version
    }
}
