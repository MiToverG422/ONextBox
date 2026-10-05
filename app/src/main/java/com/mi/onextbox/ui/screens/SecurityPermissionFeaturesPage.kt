package com.mi.onextbox.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspConfig
import com.mi.onextbox.lsp.LspConfig.PermissionFeature
import com.mi.onextbox.lsp.PermissionStartAllowList
import com.mi.onextbox.ui.common.CouiConfirmDialog
import com.mi.onextbox.ui.settings.SettingsCardRow
import com.mi.onextbox.ui.settings.SettingsDivider
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsSection
import com.mi.onextbox.ui.settings.SettingsToggleRow
import com.mi.onextbox.ui.settings.Material3ExpressiveSegmentPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import androidx.compose.runtime.remember

@Composable
internal fun SecurityPermissionFeaturesPage() {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val writeLock = remember { Mutex() }
    var oldDialog by rememberSaveable {
        mutableStateOf(LspConfig.isPermissionFeatureEnabled(context, PermissionFeature.OldAppStartDialog))
    }
    var alwaysAllow by rememberSaveable {
        mutableStateOf(LspConfig.isPermissionFeatureEnabled(context, PermissionFeature.AlwaysAllowAppStart))
    }
    var unlockRestricted by rememberSaveable {
        mutableStateOf(LspConfig.isPermissionFeatureEnabled(context, PermissionFeature.AutoUnlockRestrictedSettings))
    }
    var disableMalicious by rememberSaveable {
        mutableStateOf(LspConfig.isPermissionFeatureEnabled(context, PermissionFeature.DisableMaliciousAppIntercept))
    }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var allUsers by rememberSaveable { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }

    fun save(feature: PermissionFeature, enabled: Boolean) {
        scope.launch {
            withContext(Dispatchers.IO) {
                writeLock.withLock { LspConfig.setPermissionFeatureEnabled(context, feature, enabled) }
            }
        }
    }

    SettingsSection(title = stringResource(R.string.feature_permission_ui_group))
    SettingsGroup {
        val options = listOf(
            Triple(PermissionFeature.ExportPermissionPages,
                R.string.feature_permission_export_pages_title, R.string.feature_permission_export_pages_summary),
            Triple(PermissionFeature.NativePermissionDialogs,
                R.string.feature_permission_native_dialogs_title, R.string.feature_permission_native_dialogs_summary),
        )
        options.forEachIndexed { index, (feature, title, summary) ->
            var checked by rememberSaveable(feature.key) {
                mutableStateOf(LspConfig.isPermissionFeatureEnabled(context, feature))
            }
            if (index > 0) SettingsDivider()
            Material3ExpressiveSegmentPosition(index = index, count = options.size) {
                SettingsToggleRow(
                    title = stringResource(title),
                    summary = stringResource(summary),
                    checked = checked,
                    onCheckedChange = { checked = it; save(feature, it) },
                    hasDividerAbove = index > 0,
                    hasDividerBelow = index < options.lastIndex,
                )
            }
        }
    }

    SettingsSection(title = stringResource(R.string.feature_permission_start_group))
    SettingsGroup {
        FeatureAnimatedSegmentPosition(index = 0, count = if (alwaysAllow) 3 else 2) {
            SettingsToggleRow(
                title = stringResource(R.string.feature_permission_old_dialog_title),
                summary = stringResource(R.string.feature_permission_old_dialog_summary),
                checked = oldDialog,
                onCheckedChange = { oldDialog = it; save(PermissionFeature.OldAppStartDialog, it) },
                hasDividerBelow = true,
            )
        }
        SettingsDivider()
        FeatureAnimatedSegmentPosition(index = 1, count = if (alwaysAllow) 3 else 2) {
            SettingsToggleRow(
                title = stringResource(R.string.feature_permission_always_allow_title),
                summary = stringResource(if (oldDialog) R.string.feature_permission_always_allow_old_summary
                    else R.string.feature_permission_always_allow_summary),
                checked = alwaysAllow,
                enabled = !oldDialog,
                onCheckedChange = { alwaysAllow = it; save(PermissionFeature.AlwaysAllowAppStart, it) },
                hasDividerAbove = true,
                hasDividerBelow = alwaysAllow,
            )
        }
        FeatureExpandableVisibility(visible = alwaysAllow) {
            Column {
                SettingsDivider()
                Material3ExpressiveSegmentPosition(index = 2, count = 3) {
                    SettingsCardRow(
                        title = stringResource(R.string.feature_permission_clear_allow_title),
                        summary = stringResource(R.string.feature_permission_clear_allow_summary),
                        onClick = if (clearing) null else ({ confirmClear = true }),
                        showArrow = true,
                        hasDividerAbove = true,
                    )
                }
            }
        }
    }

    SettingsSection(title = stringResource(R.string.feature_permission_access_group))
    SettingsGroup {
        SettingsToggleRow(
            title = stringResource(R.string.feature_permission_unlock_restricted_title),
            summary = stringResource(R.string.feature_permission_unlock_restricted_summary),
            checked = unlockRestricted,
            onCheckedChange = { unlockRestricted = it; save(PermissionFeature.AutoUnlockRestrictedSettings, it) },
            hasDividerBelow = true,
        )
        SettingsDivider()
        SettingsToggleRow(
            title = stringResource(R.string.feature_permission_disable_malicious_title),
            summary = stringResource(R.string.feature_permission_disable_malicious_summary),
            checked = disableMalicious,
            onCheckedChange = { disableMalicious = it; save(PermissionFeature.DisableMaliciousAppIntercept, it) },
            hasDividerAbove = true,
        )
    }

    CouiConfirmDialog(
        show = confirmClear,
        title = stringResource(R.string.feature_permission_clear_allow_title),
        summary = stringResource(R.string.feature_permission_clear_allow_confirm),
        negativeText = stringResource(R.string.config_clear_confirm_cancel),
        positiveText = stringResource(R.string.config_clear_confirm_action),
        onDismissRequest = { confirmClear = false },
        onPositive = {
            val clearAll = allUsers
            confirmClear = false
            clearing = true
            scope.launch {
                val success = PermissionStartAllowList.clear(context, clearAll)
                clearing = false
                Toast.makeText(context, resources.getString(
                    if (success) R.string.feature_permission_clear_allow_done
                    else R.string.feature_permission_clear_allow_failed,
                ), Toast.LENGTH_LONG).show()
            }
        },
        content = {
            SettingsToggleRow(
                title = stringResource(R.string.feature_permission_clear_all_users_title),
                summary = stringResource(R.string.feature_permission_clear_all_users_summary),
                checked = allUsers,
                onCheckedChange = { allUsers = it },
            )
        },
    )
}
