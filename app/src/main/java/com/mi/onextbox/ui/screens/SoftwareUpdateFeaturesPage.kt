package com.mi.onextbox.ui.screens

import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.lsp.SoftwareUpdateFeature
import com.mi.onextbox.lsp.SoftwareUpdateSettings
import com.mi.onextbox.ui.settings.FeatureSettingWriteQueue
import com.mi.onextbox.ui.settings.SettingsDivider
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsSection
import com.mi.onextbox.ui.settings.SettingsToggleRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal object SoftwareUpdateFeatureItems {
    data class Item(val feature: SoftwareUpdateFeature, val title: Int, val summary: Int)
    val checks = listOf(
        Item(SoftwareUpdateFeature.HideRoot, R.string.ota_hide_root_title, R.string.ota_hide_root_summary),
        Item(SoftwareUpdateFeature.HideBootloader, R.string.ota_hide_bootloader_title, R.string.ota_hide_bootloader_summary),
        Item(SoftwareUpdateFeature.ShowLocalInstall, R.string.ota_local_install_title, R.string.ota_local_install_summary),
    )
    val blocking = listOf(
        Item(SoftwareUpdateFeature.BlockSystemUpdate, R.string.ota_block_system_title, R.string.ota_block_system_summary),
        Item(SoftwareUpdateFeature.BlockSystemFiles, R.string.ota_block_files_title, R.string.ota_block_files_summary),
    )
    val automatic = listOf(
        Item(SoftwareUpdateFeature.DisableAutoDownload, R.string.ota_disable_download_title, R.string.ota_disable_download_summary),
        Item(SoftwareUpdateFeature.DisableNightInstall, R.string.ota_disable_night_title, R.string.ota_disable_night_summary),
        Item(SoftwareUpdateFeature.HideRecommendations, R.string.ota_hide_recommendations_title, R.string.ota_hide_recommendations_summary),
        Item(SoftwareUpdateFeature.HideUpdatePopup, R.string.ota_hide_update_popup_title, R.string.ota_hide_update_popup_summary),
    )
    val information = listOf(
        Item(SoftwareUpdateFeature.PackageDetails, R.string.ota_package_details_title, R.string.ota_package_details_summary),
    )
    val all = checks + blocking + automatic + information
}

@Composable
internal fun SoftwareUpdateFeaturesPage() {
    val context = LocalContext.current.applicationContext
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    fun read() = SoftwareUpdateFeature.entries.associateWith { SoftwareUpdateSettings.enabled(context, it) }
    var values by remember(context) { mutableStateOf(read()) }
    var applying by remember { mutableStateOf(emptySet<SoftwareUpdateFeature>()) }

    fun refresh() {
        values = read().mapValues { (feature, value) -> if (feature in applying) values[feature] == true else value }
    }
    DisposableEffect(context, owner) {
        val prefs = LspPreferenceStore.prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || SoftwareUpdateFeature.entries.any { it.key == key }) refresh()
        }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        owner.lifecycle.addObserver(observer)
        refresh()
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            owner.lifecycle.removeObserver(observer)
        }
    }

    listOf(
        R.string.ota_checks_group to SoftwareUpdateFeatureItems.checks,
        R.string.ota_blocking_group to SoftwareUpdateFeatureItems.blocking,
        R.string.ota_automatic_group to SoftwareUpdateFeatureItems.automatic,
        R.string.ota_information_group to SoftwareUpdateFeatureItems.information,
    ).forEach { (heading, items) ->
        SettingsSection(title = stringResource(heading))
        SettingsGroup {
            items.forEachIndexed { index, item ->
                if (index > 0) SettingsDivider()
                SettingsToggleRow(
                    title = stringResource(item.title),
                    summary = stringResource(item.summary),
                    checked = values[item.feature] == true,
                    interactionEnabled = item.feature !in applying,
                    hasDividerAbove = index > 0,
                    hasDividerBelow = index < items.lastIndex,
                    onCheckedChange = { checked ->
                        if (item.feature !in applying) {
                            applying = applying + item.feature
                            values = values + (item.feature to checked)
                            val pending = FeatureSettingWriteQueue.application.enqueue {
                                SoftwareUpdateSettings.setEnabled(context, item.feature, checked)
                            }
                            scope.launch {
                                try {
                                    pending.await()
                                } catch (cancel: CancellationException) {
                                    throw cancel
                                } catch (_: Exception) {
                                    Toast.makeText(context, R.string.ota_save_failed, Toast.LENGTH_LONG).show()
                                } finally {
                                    applying = applying - item.feature
                                    refresh()
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}
