package com.mi.onextbox.ui.screens

import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.lsp.NetworkIdentityCatalog
import com.mi.onextbox.lsp.NetworkIdentityRules
import com.mi.onextbox.lsp.NetworkIdentitySettings
import com.mi.onextbox.ui.common.CouiConfirmDialog
import com.mi.onextbox.ui.settings.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun NetworkIdentitySettingsGroup() {
    val context = LocalContext.current.applicationContext
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(NetworkIdentitySettings.state(context)) }
    var saving by remember { mutableStateOf(false) }
    var allSlots by remember { mutableStateOf(setOf<Int>()) }
    var confirming by remember { mutableStateOf(false) }
    var catalog by remember { mutableStateOf(emptyList<NetworkIdentityCatalog.Entry>()) }
    LaunchedEffect(context) {
        catalog = withContext(Dispatchers.IO) { NetworkIdentityCatalog.load(context) }
    }
    fun refresh() { if (!saving) state = NetworkIdentitySettings.state(context) }
    DisposableEffect(context, owner) {
        val prefs = LspPreferenceStore.prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key in NetworkIdentitySettings.keys) refresh()
        }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        owner.lifecycle.addObserver(observer)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener); owner.lifecycle.removeObserver(observer) }
    }
    fun save(next: NetworkIdentitySettings.State) {
        if (saving) return
        saving = true
        state = next
        val pending = FeatureSettingWriteQueue.application.enqueue { NetworkIdentitySettings.save(context, next) }
        scope.launch {
            try { pending.await() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { Toast.makeText(context, R.string.enabled_features_save_failed, Toast.LENGTH_LONG).show() }
            finally { saving = false; refresh() }
        }
    }
    val choicesBySlot = remember(catalog, state, allSlots) { (0..1).associateWith { index ->
        val slot = state.slot(index)
        catalog.filter { it.region == slot.region && (index in allSlots || it.numeric in NetworkIdentityCatalog.popular || it.numeric == slot.numeric) }
            .sortedWith(compareBy<NetworkIdentityCatalog.Entry> {
                NetworkIdentityCatalog.popular.indexOf(it.numeric).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE
            }.thenBy { it.numeric })
    } }
    SettingsSection(title = stringResource(R.string.network_identity_group))
    FeatureAnimatedGroup(animateChanges = true) {
        row(NetworkIdentitySettings.ENABLED) { above, below ->
            SettingsToggleRow(title = stringResource(R.string.network_identity_title),
                summary = stringResource(R.string.network_identity_summary), checked = state.enabled,
                interactionEnabled = !saving, hasDividerAbove = above, hasDividerBelow = below,
                onCheckedChange = { if (it) confirming = true else save(state.copy(enabled = false)) })
        }
        (0..1).forEach { index ->
        val slot = state.slot(index)
        val choices = choicesBySlot.getValue(index)
        row("identity-sim-$index", state.enabled) { above, below ->
            SettingsToggleRow(title = "SIM ${index + 1}", checked = slot.enabled,
                summary = if (slot.enabled) "${slot.name} · ${slot.numeric}" else stringResource(R.string.network_identity_original),
                interactionEnabled = !saving, hasDividerAbove = above, hasDividerBelow = below,
                onCheckedChange = { save(state.withSlot(index, slot.copy(enabled = it))) })
        }
        row("identity-region-$index", state.enabled && slot.enabled) { _, _ ->
            SettingsWindowDropdownPreference(title = stringResource(R.string.network_identity_region),
                items = listOf("TW", "HK", "CN", "US"),
                selectedIndex = NetworkIdentityRules.regions.indexOf(slot.region).coerceAtLeast(0), enabled = catalog.isNotEmpty(), interactionEnabled = !saving,
                onSelectedIndexChange = { regionIndex ->
                    val region = NetworkIdentityRules.regions[regionIndex]
                    if (region != slot.region) catalog.firstOrNull { it.region == region && it.numeric in NetworkIdentityCatalog.popular }
                        ?.let { save(state.withSlot(index, it.selection(slot.enabled))) }
                })
        }
        row("identity-list-$index", state.enabled && slot.enabled) { _, _ ->
            SettingsWindowDropdownPreference(title = stringResource(R.string.network_identity_list),
                items = listOf(stringResource(R.string.network_identity_popular), stringResource(R.string.network_identity_all)),
                selectedIndex = if (index in allSlots) 1 else 0,
                onSelectedIndexChange = { allSlots = if (it == 1) allSlots + index else allSlots - index })
        }
        row("identity-operator-$index", state.enabled && slot.enabled) { _, _ ->
            SettingsWindowDropdownPreference(title = stringResource(R.string.network_identity_operator),
                items = choices.map { "${it.title} · ${it.numeric}" },
                selectedIndex = choices.indexOfFirst { it.numeric == slot.numeric }, enabled = choices.isNotEmpty(), interactionEnabled = !saving,
                maxHeight = 360.dp, onSelectedIndexChange = { choices.getOrNull(it)?.let { entry ->
                    save(state.withSlot(index, entry.selection(slot.enabled)))
                } })
        }
        }
    }
    CouiConfirmDialog(show = confirming, title = stringResource(R.string.network_identity_title),
        summary = stringResource(R.string.network_identity_notice),
        negativeText = stringResource(android.R.string.cancel), positiveText = stringResource(android.R.string.ok),
        onDismissRequest = { confirming = false }, onPositive = { confirming = false; save(state.copy(enabled = true)) })
}
