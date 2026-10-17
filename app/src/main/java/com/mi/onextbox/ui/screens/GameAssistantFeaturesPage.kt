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
import com.mi.onextbox.lsp.GameAssistantRules
import com.mi.onextbox.lsp.GameAssistantSettings
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.ui.settings.FeatureSettingWriteQueue
import com.mi.onextbox.ui.settings.SettingsToggleRow
import com.mi.onextbox.ui.settings.SettingsWindowDropdownPreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun GameAssistantFeaturesPage() {
    val context = LocalContext.current.applicationContext
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var states by remember(context) { mutableStateOf(GameAssistantSettings.toggles.associateWith { GameAssistantSettings.enabled(context, it) }) }
    var skin by remember(context) { mutableStateOf(GameAssistantSettings.skin(context)) }
    var savingKey by remember { mutableStateOf<String?>(null) }
    fun refresh() {
        states = GameAssistantSettings.toggles.associateWith { GameAssistantSettings.enabled(context, it) }
        skin = GameAssistantSettings.skin(context)
    }
    DisposableEffect(context, owner) {
        val prefs = LspPreferenceStore.prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (savingKey == null && (key == null || key in GameAssistantSettings.keys)) refresh()
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && savingKey == null) refresh()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        owner.lifecycle.addObserver(observer)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener); owner.lifecycle.removeObserver(observer) }
    }
    fun save(key: String, enabled: Boolean? = null, selectedSkin: String? = null) {
        if (savingKey != null) return
        savingKey = key
        if (enabled != null) states = states + (key to enabled)
        if (selectedSkin != null) skin = selectedSkin
        val write = FeatureSettingWriteQueue.application.enqueue {
            if (key == GameAssistantSettings.SKIN) GameAssistantSettings.setSkin(context, requireNotNull(selectedSkin))
            else GameAssistantSettings.set(context, key, requireNotNull(enabled))
        }
        scope.launch {
            try { write.await() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { Toast.makeText(context, R.string.enabled_features_save_failed, Toast.LENGTH_LONG).show() }
            finally { refresh(); savingKey = null }
        }
    }
    val options = listOf(
        Triple(GameAssistantSettings.PROMOTIONS, R.string.game_assistant_promotions_title, R.string.game_assistant_promotions_summary),
        Triple(GameAssistantSettings.REALME_STARTUP_ANIMATION, R.string.game_assistant_realme_startup_title, R.string.game_assistant_realme_startup_summary),
        Triple(GameAssistantSettings.UNLIMITED, R.string.game_assistant_unlimited_title, R.string.game_assistant_unlimited_summary),
        Triple(GameAssistantSettings.MUSIC, R.string.game_assistant_music_title, R.string.game_assistant_music_summary),
        Triple(GameAssistantSettings.BLOCK_ROOT, R.string.game_assistant_root_title, R.string.game_assistant_root_summary),
        Triple(GameAssistantSettings.DEVELOPER, R.string.game_assistant_developer_title, R.string.game_assistant_developer_summary),
        Triple(GameAssistantSettings.SKINS, R.string.game_assistant_skins_title, 0),
    )
    val names = listOf(R.string.game_assistant_skin_default, R.string.game_assistant_skin_cyber,
        R.string.game_assistant_skin_snipe, R.string.game_assistant_skin_hutao, R.string.game_assistant_skin_xiangling,
        R.string.game_assistant_skin_keqing, R.string.game_assistant_skin_ayaka, R.string.game_assistant_skin_naruto)
        .map { stringResource(it) }
    FeatureAnimatedGroup(animateChanges = true) {
        options.forEach { (key, title, summary) -> row(key) { above, below ->
            SettingsToggleRow(title = stringResource(title), summary = if (summary == 0) "" else stringResource(summary),
                checked = states[key] == true, switchEnabled = savingKey != key,
                hasDividerAbove = above, hasDividerBelow = below,
                onCheckedChange = { save(key, enabled = it) })
        } }
        row(GameAssistantSettings.SKIN, states[GameAssistantSettings.SKINS] == true) { _, _ ->
            // Reuse the exact popup selector used by UI style and app language.
            // The surrounding animated group supplies joined-card positioning.
            SettingsWindowDropdownPreference(
                title = stringResource(R.string.game_assistant_skin_title),
                items = names,
                selectedIndex = GameAssistantRules.skins.indexOf(skin).coerceAtLeast(0),
                enabled = states[GameAssistantSettings.SKINS] == true,
                onSelectedIndexChange = { index ->
                    GameAssistantRules.skins.getOrNull(index)?.let { id ->
                        if (skin != id) save(GameAssistantSettings.SKIN, selectedSkin = id)
                    }
                },
            )
        }
    }
}
