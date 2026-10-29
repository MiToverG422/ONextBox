package com.mi.onextbox

import android.app.Application
import com.mi.onextbox.lsp.ModernXposedPreferenceSync
import com.mi.onextbox.ui.settings.AppUpdatePreferences

class ONextBoxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (getProcessName().endsWith(":diagnostics")) return
        com.mi.onextbox.diagnostics.RuntimeDiagnostics.event("startup_begin", "Application")
        com.mi.onextbox.diagnostics.DeveloperDiagnostics.install(this)
        com.mi.onextbox.ui.common.AppLogStore.initialize(this)
        AppUpdatePreferences.getBetaPlanEnabled(applicationContext)
        ModernXposedPreferenceSync.initialize(applicationContext)
    }
}
