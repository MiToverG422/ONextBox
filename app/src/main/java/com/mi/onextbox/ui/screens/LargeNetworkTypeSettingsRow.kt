package com.mi.onextbox.ui.screens

import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LargeNetworkTypeSettings
import com.mi.onextbox.ui.settings.SettingsToggleRow
import com.mi.onextbox.ui.settings.SettingsWindowDropdownPreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun LargeNetworkTypeSettingsRow() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(LargeNetworkTypeSettings.enabled(context)) }
    var followFont by remember { mutableStateOf(LargeNetworkTypeSettings.followsSystemFont(context)) }
    var bold by remember { mutableStateOf(LargeNetworkTypeSettings.usesBold(context)) }
    var artwork by remember { mutableStateOf(LargeNetworkTypeSettings.usesArtwork(context)) }
    var lte by remember { mutableStateOf(LargeNetworkTypeSettings.usesLte(context)) }
    var position by remember { mutableIntStateOf(LargeNetworkTypeSettings.position(context)) }
    var saving by remember { mutableStateOf(false) }
    DisposableEffect(context, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !saving) {
                enabled = LargeNetworkTypeSettings.enabled(context)
                followFont = LargeNetworkTypeSettings.followsSystemFont(context)
                bold = LargeNetworkTypeSettings.usesBold(context)
                artwork = LargeNetworkTypeSettings.usesArtwork(context)
                lte = LargeNetworkTypeSettings.usesLte(context)
                position = LargeNetworkTypeSettings.position(context)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val rows = largeNetworkRows(enabled, artwork, followFont)
    FeatureAnimatedRows {
        // Register hidden rows too: their stable transition state must survive
        // until the shrink animation finishes, and start hidden before entry.
        LargeNetworkRow.entries.forEach { row ->
            row(row, visible = row in rows) { above, below ->
                val content: @Composable () -> Unit = {
                    when (row) {
                        LargeNetworkRow.Main -> SettingsToggleRow(
                            title = stringResource(R.string.feature_large_network_type_title),
                            summary = stringResource(R.string.feature_large_network_type_summary),
                            checked = enabled,
                            interactionEnabled = !saving,
                            hasDividerAbove = above,
                            hasDividerBelow = below,
                            onCheckedChange = { checked ->
                                if (!saving) {
                                    saving = true
                                    enabled = checked
                                    scope.launch {
                                        try { withContext(Dispatchers.IO) { LargeNetworkTypeSettings.set(context, checked) } }
                                        catch (cancel: CancellationException) { throw cancel }
                                        catch (_: Exception) { Toast.makeText(context, R.string.feature_large_network_type_save_failed, Toast.LENGTH_LONG).show() }
                                        finally { enabled = LargeNetworkTypeSettings.enabled(context); saving = false }
                                    }
                                }
                            },
                        )
                        LargeNetworkRow.Position -> SettingsWindowDropdownPreference(
                            title = stringResource(R.string.feature_large_network_position_title),
                            items = listOf(stringResource(R.string.feature_large_network_position_left),
                                stringResource(R.string.feature_large_network_position_right)),
                            selectedIndex = position,
                            interactionEnabled = !saving && enabled,
                            onSelectedIndexChange = { selected ->
                                if (!saving && selected != position) {
                                    saving = true
                                    position = selected
                                    scope.launch {
                                        try { withContext(Dispatchers.IO) { LargeNetworkTypeSettings.setPosition(context, selected) } }
                                        catch (cancel: CancellationException) { throw cancel }
                                        catch (_: Exception) { Toast.makeText(context, R.string.feature_large_network_type_save_failed, Toast.LENGTH_LONG).show() }
                                        finally { position = LargeNetworkTypeSettings.position(context); saving = false }
                                    }
                                }
                            },
                        )
                        LargeNetworkRow.Artwork -> SettingsToggleRow(
                            title = stringResource(R.string.feature_large_network_artwork_title),
                            summary = stringResource(R.string.feature_large_network_artwork_summary),
                            checked = artwork,
                            interactionEnabled = !saving && enabled,
                            hasDividerAbove = above,
                            hasDividerBelow = below,
                            onCheckedChange = { checked ->
                                if (!saving) {
                                    saving = true
                                    artwork = checked
                                    scope.launch {
                                        try { withContext(Dispatchers.IO) { LargeNetworkTypeSettings.setArtwork(context, checked) } }
                                        catch (cancel: CancellationException) { throw cancel }
                                        catch (_: Exception) { Toast.makeText(context, R.string.feature_large_network_type_save_failed, Toast.LENGTH_LONG).show() }
                                        finally { artwork = LargeNetworkTypeSettings.usesArtwork(context); saving = false }
                                    }
                                }
                            },
                        )
                        LargeNetworkRow.Lte -> SettingsToggleRow(
                            title = stringResource(R.string.feature_large_network_lte_title),
                            summary = stringResource(R.string.feature_large_network_lte_summary),
                            checked = lte,
                            interactionEnabled = !saving && enabled && artwork,
                            hasDividerAbove = above,
                            hasDividerBelow = below,
                            onCheckedChange = { checked ->
                                if (!saving) {
                                    saving = true
                                    lte = checked
                                    scope.launch {
                                        try { withContext(Dispatchers.IO) { LargeNetworkTypeSettings.setLte(context, checked) } }
                                        catch (cancel: CancellationException) { throw cancel }
                                        catch (_: Exception) { Toast.makeText(context, R.string.feature_large_network_type_save_failed, Toast.LENGTH_LONG).show() }
                                        finally { lte = LargeNetworkTypeSettings.usesLte(context); saving = false }
                                    }
                                }
                            },
                        )
                        LargeNetworkRow.Font -> SettingsToggleRow(
                            title = stringResource(R.string.feature_large_network_font_title),
                            summary = stringResource(R.string.feature_large_network_font_summary),
                            checked = followFont,
                            interactionEnabled = !saving && enabled && !artwork,
                            hasDividerAbove = above,
                            hasDividerBelow = below,
                            onCheckedChange = { checked ->
                                if (!saving) {
                                    saving = true
                                    followFont = checked
                                    scope.launch {
                                        try { withContext(Dispatchers.IO) { LargeNetworkTypeSettings.setSystemFont(context, checked) } }
                                        catch (cancel: CancellationException) { throw cancel }
                                        catch (_: Exception) { Toast.makeText(context, R.string.feature_large_network_type_save_failed, Toast.LENGTH_LONG).show() }
                                        finally { followFont = LargeNetworkTypeSettings.followsSystemFont(context); saving = false }
                                    }
                                }
                            },
                        )
                        LargeNetworkRow.Bold -> SettingsToggleRow(
                            title = stringResource(R.string.feature_large_network_bold_title),
                            summary = stringResource(R.string.feature_large_network_bold_summary),
                            checked = bold,
                            interactionEnabled = !saving && enabled && !artwork && followFont,
                            hasDividerAbove = above,
                            hasDividerBelow = below,
                            onCheckedChange = { checked ->
                                if (!saving) {
                                    saving = true
                                    bold = checked
                                    scope.launch {
                                        try { withContext(Dispatchers.IO) { LargeNetworkTypeSettings.setBold(context, checked) } }
                                        catch (cancel: CancellationException) { throw cancel }
                                        catch (_: Exception) { Toast.makeText(context, R.string.feature_large_network_type_save_failed, Toast.LENGTH_LONG).show() }
                                        finally { bold = LargeNetworkTypeSettings.usesBold(context); saving = false }
                                    }
                                }
                            },
                        )
                    }
                }
                content()
            }
        }
    }
}

internal enum class LargeNetworkRow { Main, Position, Artwork, Lte, Font, Bold }

internal fun largeNetworkRows(enabled: Boolean, artwork: Boolean, followFont: Boolean) = buildList {
    add(LargeNetworkRow.Main)
    if (enabled) {
        add(LargeNetworkRow.Position)
        add(LargeNetworkRow.Artwork)
        if (artwork) add(LargeNetworkRow.Lte)
        else {
            add(LargeNetworkRow.Font)
            if (followFont) add(LargeNetworkRow.Bold)
        }
    }
}
