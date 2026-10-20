package com.mi.onextbox.ui.common

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.lsp.SystemAdaptationSettings
import com.mi.onextbox.lsp.SystemAdaptationVersion

@Composable
internal fun rememberSystemAdaptationVersion(): SystemAdaptationVersion {
    val app = LocalContext.current.applicationContext
    val owner = LocalLifecycleOwner.current
    var version by remember(app) { mutableStateOf(SystemAdaptationSettings.selectedVersion(app)) }
    DisposableEffect(app, owner) {
        val prefs = LspPreferenceStore.prefs(app)
        fun refresh() { version = SystemAdaptationSettings.selectedVersion(app) }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == SystemAdaptationSettings.KEY || key == SystemAdaptationSettings.ALLOW_HYPER_OS) refresh()
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        owner.lifecycle.addObserver(observer)
        refresh()
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            owner.lifecycle.removeObserver(observer)
        }
    }
    return version
}
