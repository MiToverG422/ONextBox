package com.mi.onextbox.ui.screens

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspConfig
import com.mi.onextbox.lsp.LspConfig.LauncherFeature
import com.mi.onextbox.ui.settings.SettingsDivider
import com.mi.onextbox.ui.settings.SettingsToggleRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class LauncherFeatureItem(
    val feature: LauncherFeature,
    @param:StringRes val title: Int,
    @param:StringRes val summary: Int = 0,
)

internal object LauncherFeatureItems {
    val pages = listOf(
        LauncherFeatureItem(LauncherFeature.RightmostCategories, R.string.launcher_rightmost_categories, R.string.launcher_rightmost_categories_summary),
    )
    val recent = listOf(
        LauncherFeatureItem(LauncherFeature.RecentMemory, R.string.launcher_recent_memory),
        LauncherFeatureItem(LauncherFeature.DisablePreviousTaskAutoFocus, R.string.launcher_disable_previous_task_auto_focus),
        LauncherFeatureItem(LauncherFeature.RecentIconAppDetails, R.string.launcher_recent_icon_app_details),
        LauncherFeatureItem(LauncherFeature.OldClearButton, R.string.launcher_old_clear_button),
        LauncherFeatureItem(LauncherFeature.HideClearButton, R.string.launcher_hide_clear_button),
    )
    val badges = listOf(
        LauncherFeatureItem(LauncherFeature.HideShortcutBadge, R.string.launcher_hide_shortcut_badge, R.string.launcher_hide_shortcut_badge_summary),
        LauncherFeatureItem(LauncherFeature.HideWorkBadge, R.string.launcher_hide_work_badge, R.string.launcher_hide_work_badge_summary),
        LauncherFeatureItem(LauncherFeature.HideCloneBadge, R.string.launcher_hide_clone_badge, R.string.launcher_hide_clone_badge_summary),
        LauncherFeatureItem(LauncherFeature.HideUpdateDot, R.string.launcher_hide_update_dot, R.string.launcher_hide_update_dot_summary),
    )
    val folder = listOf(
        LauncherFeatureItem(LauncherFeature.UnlimitedFolderInput, R.string.launcher_unlimited_folder_input, R.string.launcher_unlimited_folder_input_summary),
    )
    val dock = listOf(
        LauncherFeatureItem(LauncherFeature.Dock, R.string.launcher_dock, R.string.launcher_dock_summary),
        LauncherFeatureItem(LauncherFeature.DockBlur, R.string.launcher_dock_blur, R.string.launcher_dock_blur_summary),
    )
    val all = pages + recent + badges + folder + dock
}

@Composable
internal fun LauncherFeatureRows(items: List<LauncherFeatureItem>, hasDividerBelow: Boolean = false) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val writeLock = remember { Mutex() }
    val states = remember(context, items) {
        mutableStateMapOf<LauncherFeature, Boolean>().apply {
            items.forEach { put(it.feature, LspConfig.isLauncherFeatureEnabled(context, it.feature)) }
        }
    }
    DisposableEffect(context, owner, items) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                items.forEach { states[it.feature] = LspConfig.isLauncherFeatureEnabled(context, it.feature) }
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    items.forEachIndexed { index, item ->
        val rowEnabled = item.feature != LauncherFeature.DockBlur || states[LauncherFeature.Dock] == true
        SettingsToggleRow(
            title = stringResource(item.title),
            summary = if (item.summary != 0) stringResource(item.summary) else "",
            checked = states[item.feature] == true,
            enabled = rowEnabled,
            onCheckedChange = { enabled ->
                if (rowEnabled) {
                    states[item.feature] = enabled
                    scope.launch {
                        writeLock.withLock {
                            withContext(Dispatchers.IO) {
                                LspConfig.setLauncherFeatureEnabled(context, item.feature, enabled)
                            }
                        }
                    }
                }
            },
            hasDividerAbove = index > 0,
            hasDividerBelow = index < items.lastIndex || hasDividerBelow,
        )
        if (index < items.lastIndex || hasDividerBelow) SettingsDivider()
    }
}
