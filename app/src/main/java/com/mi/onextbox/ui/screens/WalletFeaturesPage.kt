package com.mi.onextbox.ui.screens

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import android.content.SharedPreferences
import com.mi.onextbox.R
import com.mi.onextbox.lsp.WalletCardCatalog
import com.mi.onextbox.lsp.LspFrameworkService
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.lsp.ArtworkTarget
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.lsp.WalletOptions
import com.mi.onextbox.ui.settings.*

private val walletKeys = WalletOptions.entries.mapTo(linkedSetOf()) { it.key }

@Composable
internal fun WalletFeaturesPage() {
    val context = LocalContext.current.applicationContext
    val editor = rememberCustomization(walletKeys) { ctx ->
        val prefs = LspPreferenceStore.prefs(ctx)
        walletKeys.associateWith { prefs.getBoolean(it, false) }
    }
    WalletArtworkSection()
    for (options in listOf(WalletOptions.entries.take(3), WalletOptions.entries.drop(3))) {
        FeatureAnimatedGroup {
            for (option in options) row(option.key) { above, below ->
                SettingsToggleRow(title = stringResource(option.title),
                    summary = if (option.summary != 0) stringResource(option.summary) else "",
                    checked = editor.state[option.key] == true, interactionEnabled = editor.savingKey == null,
                    hasDividerAbove = above, hasDividerBelow = below, onCheckedChange = { enabled ->
                        editor.save(option.key, editor.state + (option.key to enabled)) {
                            option.set(context, enabled)
                        }
                    })
            }
        }
    }
}

@Composable
private fun WalletArtworkSection() {
    val context = LocalContext.current
    val prefs = remember(context) { WalletCardCatalog.prefs(context) }
    fun cards() = prefs.all.mapNotNull { (id, value) -> (value as? String)?.let { id to it } }
        .sortedWith(compareBy({ it.second }, { it.first }))
    var choices by remember(prefs) { mutableStateOf(cards()) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> choices = cards() }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        choices = cards()
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val index = choices.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    SettingsSection(stringResource(R.string.wallet_art))
    SettingsGroup {
        if (choices.isEmpty()) {
            SettingsCardRow(stringResource(R.string.wallet_open_cards), stringResource(R.string.wallet_cards_hint),
                showArrow = true, onClick = {
                    LspFrameworkService.requestScopes(setOf(WalletOptions.PACKAGE))
                    context.packageManager.getLaunchIntentForPackage(WalletOptions.PACKAGE)?.let(context::startActivity)
                })
        } else {
            val duplicates = choices.groupingBy { it.second }.eachCount()
            SettingsWindowDropdownPreference(title = stringResource(R.string.wallet_select_card),
                selectedIndex = index, items = choices.map { (id, label) ->
                    if (duplicates.getValue(label) > 1) "$label · ${id.take(6)}" else label
                }, onSelectedIndexChange = { selected = choices[it].first })
        }
    }
    choices.getOrNull(index)?.let { (id, _) ->
        key(id) { OtaArtworkSettings(ArtworkTarget.WALLET, WalletCardCatalog.PREFIX + id) }
    }
}
