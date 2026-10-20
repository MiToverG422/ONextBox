package com.mi.onextbox.ui.screens

import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mi.onextbox.R
import com.mi.onextbox.lsp.GalleryAiRetouchSettings
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.ui.settings.FeatureSettingWriteQueue
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsToggleRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun GalleryFeaturesPage() {
    val app = LocalContext.current.applicationContext
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var enabled by remember(app) { mutableStateOf(GalleryAiRetouchSettings.enabled(app)) }
    var saving by remember { mutableStateOf(false) }
    DisposableEffect(app, owner) {
        val prefs = LspPreferenceStore.prefs(app)
        fun refresh() { if (!saving) enabled = GalleryAiRetouchSettings.enabled(app) }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == GalleryAiRetouchSettings.KEY) refresh()
        }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        owner.lifecycle.addObserver(observer)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            owner.lifecycle.removeObserver(observer)
        }
    }
    SettingsGroup {
        SettingsToggleRow(title = stringResource(R.string.gallery_ai_retouch_title),
            summary = stringResource(R.string.gallery_ai_retouch_summary), checked = enabled, interactionEnabled = !saving,
            onCheckedChange = { next ->
                if (!saving) {
                    saving = true
                    enabled = next
                    val pending = FeatureSettingWriteQueue.application.enqueue { GalleryAiRetouchSettings.set(app, next) }
                    scope.launch {
                        try { pending.await() }
                        catch (cancel: CancellationException) { throw cancel }
                        catch (_: Exception) { Toast.makeText(app, R.string.gallery_save_failed, Toast.LENGTH_LONG).show() }
                        finally { enabled = GalleryAiRetouchSettings.enabled(app); saving = false }
                    }
                }
            })
    }
}
