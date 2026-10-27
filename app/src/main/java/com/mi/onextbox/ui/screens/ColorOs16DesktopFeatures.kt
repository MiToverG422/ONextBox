package com.mi.onextbox.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspConfig
import com.mi.onextbox.lsp.ConfigSyncStatus
import com.mi.onextbox.ui.settings.FeatureSettingWriteQueue
import com.mi.onextbox.ui.settings.SettingsCardRow
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsSection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal fun isColorOs16FeaturePageAvailable(mode: FeaturePageMode): Boolean =
    ColorOs16FeaturePolicy.isPageAvailable(mode)

@Composable
internal fun ColorOs16FeatureEntry(onOpen: (FeaturePageMode) -> Unit) {
    ColorOs16FeatureCategories(FeaturePageMode.Main, onOpen)
}

@Composable
internal fun ColorOs16DesktopFeatures(mode: FeaturePageMode, onOpen: (FeaturePageMode) -> Unit) {
    if (mode == FeaturePageMode.Desktop) {
        SettingsGroup {
            SettingsCardRow(
                title = stringResource(R.string.desktop_category_search),
                summary = "",
                showArrow = true,
                onClick = { onOpen(FeaturePageMode.DesktopLayout) },
            )
        }
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedMode by rememberSaveable {
        mutableIntStateOf(LspConfig.getLauncherSearchBarMode(context))
    }
    var saving by remember { mutableStateOf(false) }
    SettingsSection(title = stringResource(R.string.desktop_category_search))
    SettingsGroup {
        LauncherSearchBarModeRow(
            title = stringResource(R.string.feature_launcher_taskbar_search_box_title),
            summary = stringResource(R.string.feature_launcher_taskbar_search_box_summary),
            selectedMode = selectedMode,
            onModeChange = { next ->
                if (!saving) {
                    selectedMode = next
                    saving = true
                    val pending = FeatureSettingWriteQueue.application.enqueue {
                        LspConfig.setLauncherSearchBarMode(context, next)
                    }
                    scope.launch {
                        try {
                            pending.await()
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (_: Exception) {
                            ConfigSyncStatus.failed("launcher search mode")
                        } finally {
                            selectedMode = LspConfig.getLauncherSearchBarMode(context)
                            saving = false
                        }
                    }
                }
            },
            hasDividerAbove = false,
            hasDividerBelow = false,
            interactionEnabled = !saving,
        )
    }
}
