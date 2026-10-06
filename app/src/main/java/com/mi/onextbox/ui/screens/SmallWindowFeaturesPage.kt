package com.mi.onextbox.ui.screens

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
import com.mi.onextbox.lsp.LspConfig
import com.mi.onextbox.lsp.LspConfig.SmallWindowFeature as Feature
import com.mi.onextbox.ui.settings.SettingsDivider
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsSection
import com.mi.onextbox.ui.settings.SettingsToggleRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Composable
internal fun SmallWindowFeaturesPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val writeLock = remember { Mutex() }
    var values by remember {
        mutableStateOf(Feature.entries.associateWith { LspConfig.isSmallWindowFeatureEnabled(context, it) })
    }
    DisposableEffect(context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                values = Feature.entries.associateWith { LspConfig.isSmallWindowFeatureEnabled(context, it) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    fun save(feature: Feature, enabled: Boolean) {
        values = values + (feature to enabled)
        scope.launch {
            writeLock.withLock {
                withContext(Dispatchers.IO) {
                    LspConfig.setSmallWindowFeatureEnabled(context, feature, enabled)
                }
            }
        }
    }

    val groups = listOf(
        R.string.small_window_group_behavior to listOf(
            Triple(Feature.HideRecents, R.string.small_window_hide_recents, R.string.small_window_hide_recents_summary),
            Triple(Feature.MuteStashed, R.string.small_window_mute_stashed, R.string.small_window_mute_stashed_summary),
        ),
        R.string.small_window_group_performance to listOf(
            Triple(Feature.UnlimitedCount, R.string.small_window_unlimited_count, R.string.small_window_unlimited_count_summary),
            Triple(Feature.UnlimitedFrameRate, R.string.small_window_unlimited_frame_rate, R.string.small_window_unlimited_frame_rate_summary),
            Triple(Feature.KeepRunning, R.string.small_window_keep_running, R.string.small_window_keep_running_summary),
        ),
        R.string.small_window_group_edge to listOf(
            Triple(Feature.WhiteBar, R.string.small_window_white_bar, R.string.small_window_white_bar_summary),
            Triple(Feature.SafeEdgeInset, R.string.small_window_safe_inset, R.string.small_window_safe_inset_summary),
        ),
        R.string.small_window_group_layout to listOf(
            Triple(Feature.LandscapeRatio, R.string.small_window_landscape_ratio, R.string.small_window_landscape_ratio_summary),
            Triple(Feature.LargerSize, R.string.small_window_larger_size, R.string.small_window_larger_size_summary),
            Triple(Feature.CompactCaption, R.string.small_window_compact_caption, R.string.small_window_compact_caption_summary),
        ),
    )
    groups.forEach { (groupTitle, options) ->
        SettingsSection(stringResource(groupTitle))
        SettingsGroup {
            options.forEachIndexed { index, (feature, title, summary) ->
                if (index > 0) SettingsDivider()
                SettingsToggleRow(
                    title = stringResource(title),
                    summary = stringResource(summary),
                    checked = values[feature] == true,
                    onCheckedChange = { save(feature, it) },
                    hasDividerAbove = index > 0,
                    hasDividerBelow = index < options.lastIndex,
                )
            }
        }
    }
}
