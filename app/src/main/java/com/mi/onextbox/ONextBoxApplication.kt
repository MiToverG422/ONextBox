package com.mi.onextbox

import android.app.Application
import com.mi.onextbox.boot.BootSyncReceiver
import com.mi.onextbox.lsp.ModernXposedPreferenceSync

class ONextBoxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (getProcessName().endsWith(":diagnostics") || getProcessName().endsWith(":native_5ga_bridge") ||
            getProcessName().endsWith(":guide_haptics")) return
        com.mi.onextbox.diagnostics.RuntimeDiagnostics.event("startup_begin", "Application")
        com.mi.onextbox.diagnostics.DeveloperDiagnostics.install(this)
        com.mi.onextbox.ui.common.AppLogStore.initialize(this)
        ModernXposedPreferenceSync.initialize(applicationContext)
        BootSyncReceiver.migrateDisplayDensity(this)
    }
}
