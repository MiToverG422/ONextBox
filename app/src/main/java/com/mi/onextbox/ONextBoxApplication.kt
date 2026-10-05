package com.mi.onextbox

import android.app.Application
import com.mi.onextbox.lsp.ModernXposedPreferenceSync

class ONextBoxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ModernXposedPreferenceSync.initialize(applicationContext)
    }
}
