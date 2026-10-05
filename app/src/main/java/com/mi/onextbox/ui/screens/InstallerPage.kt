@file:Suppress("DEPRECATION")

package com.mi.onextbox.ui.screens

import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspConfig
import com.mi.onextbox.lsp.LspConfig.InstallerFeature as Feature
import com.mi.onextbox.ui.common.AppUiStyle
import com.mi.onextbox.ui.common.LocalAppUiStyle
import com.mi.onextbox.ui.common.CouiConfirmDialog
import com.mi.onextbox.ui.settings.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private data class InstallerChoice(val packageName: String, val label: String)

private fun loadInstallerChoices(context: Context, key: String): List<InstallerChoice> = runCatching {
    val intents = if (key == LspConfig.UNINSTALLER_PACKAGE) listOf(
        Intent(Intent.ACTION_DELETE, Uri.parse("package:com.mi.onextbox")),
        Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:com.mi.onextbox")),
    ) else listOf(
        Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://onextbox/choice.apk"), "application/vnd.android.package-archive"),
        Intent("android.content.pm.action.CONFIRM_INSTALL"),
    )
    intents.flatMap { context.packageManager.queryIntentActivities(it, PackageManager.MATCH_ALL or PackageManager.MATCH_DEFAULT_ONLY) }
        .filter { it.activityInfo.exported && it.activityInfo.enabled && it.activityInfo.applicationInfo.enabled }
        .map { InstallerChoice(it.activityInfo.packageName, it.activityInfo.applicationInfo.loadLabel(context.packageManager).toString()) }
        .distinctBy { it.packageName }.sortedBy { it.label }
}.getOrDefault(emptyList())

@Composable
internal fun InstallerPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lock = remember { Mutex() }
    var flags by remember { mutableStateOf(Feature.entries.associateWith { LspConfig.isInstallerFeatureEnabled(context, it) }) }
    var installer by rememberSaveable { mutableStateOf(LspConfig.installerText(context, LspConfig.INSTALLER_PACKAGE)) }
    var uninstaller by rememberSaveable { mutableStateOf(LspConfig.installerText(context, LspConfig.UNINSTALLER_PACKAGE)) }
    var chooserKey by rememberSaveable { mutableStateOf<String?>(null) }
    var choiceSets by remember { mutableStateOf<Map<String, List<InstallerChoice>>>(emptyMap()) }
    var choices by remember { mutableStateOf<List<InstallerChoice>>(emptyList()) }
    var choicesReady by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val errorText = stringResource(R.string.installer_save_error)
    val systemDefault = stringResource(R.string.installer_system_default)
    val follow = flags[Feature.FollowUninstall] == true
    val selectedUninstaller = if (follow) installer else uninstaller
    fun openChooser(key: String) {
        choices = choiceSets[key].orEmpty()
        chooserKey = key
    }
    var labels by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(installer, selectedUninstaller) {
        labels = withContext(Dispatchers.IO) {
            listOf(installer, selectedUninstaller).filter(String::isNotEmpty).associateWith { pkg ->
                runCatching { context.packageManager.getApplicationInfo(pkg, 0).loadLabel(context.packageManager).toString() }.getOrDefault(pkg)
            }
        }
    }

    fun saveTarget(key: String, value: String) {
        if (saving) return
        saving = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                lock.withLock { runCatching { LspConfig.setInstallerText(context, key, value) } }
            }
            result.onSuccess {
                when (key) {
                    LspConfig.INSTALLER_PACKAGE -> installer = value
                    LspConfig.UNINSTALLER_PACKAGE -> {
                        uninstaller = value
                        flags = flags + (Feature.FollowUninstall to false)
                    }
                }
                chooserKey = null
            }.onFailure { Toast.makeText(context, errorText, Toast.LENGTH_SHORT).show() }
            saving = false
        }
    }

    LaunchedEffect(Unit) {
        choiceSets = withContext(Dispatchers.IO) {
            listOf(LspConfig.INSTALLER_PACKAGE, LspConfig.UNINSTALLER_PACKAGE)
                .associateWith { loadInstallerChoices(context, it) }
        }
        chooserKey?.let { choices = choiceSets[it].orEmpty() }
        choicesReady = true
    }

    SettingsSection(stringResource(R.string.installer_targets))
    SettingsGroup {
        SettingsCardRow(
            title = stringResource(R.string.installer_target),
            summary = labels[installer] ?: installer.ifEmpty { systemDefault },
            onClick = { openChooser(LspConfig.INSTALLER_PACKAGE) }, showArrow = true, hasDividerBelow = true,
        )
        SettingsDivider()
        SettingsCardRow(
            title = stringResource(R.string.installer_uninstaller_target),
            summary = if (follow) stringResource(R.string.installer_follow_current, labels[selectedUninstaller] ?: selectedUninstaller.ifEmpty { systemDefault })
                else labels[uninstaller] ?: uninstaller.ifEmpty { systemDefault },
            onClick = { openChooser(LspConfig.UNINSTALLER_PACKAGE) },
            showArrow = true, hasDividerAbove = true,
        )
    }

    SettingsSection(stringResource(R.string.installer_group))
    val options = listOf(
        Triple(Feature.Enabled, R.string.installer_enable, R.string.installer_enable_summary),
        Triple(Feature.Uninstall, R.string.installer_uninstall, R.string.installer_uninstall_summary),
        Triple(Feature.Session, R.string.installer_session, R.string.installer_session_summary),
        Triple(Feature.FixPermissions, R.string.installer_paths, R.string.installer_paths_summary),
        Triple(Feature.FollowUninstall, R.string.installer_follow, R.string.installer_follow_summary),
    )
    SettingsGroup {
        options.forEachIndexed { index, (feature, title, summary) ->
            if (index > 0) SettingsDivider()
            Material3ExpressiveSegmentPosition(index, options.size) {
                SettingsToggleRow(
                    title = stringResource(title), summary = stringResource(summary),
                    checked = flags[feature] == true,
                    onCheckedChange = { value ->
                        flags = flags + (feature to value)
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                lock.withLock { runCatching { LspConfig.setInstallerFeatureEnabled(context, feature, value) } }
                            }
                            if (result.isFailure) {
                                flags = flags + (feature to LspConfig.isInstallerFeatureEnabled(context, feature))
                                Toast.makeText(context, errorText, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    hasDividerAbove = index > 0, hasDividerBelow = index < options.lastIndex,
                )
            }
        }
    }
    CouiConfirmDialog(
        show = chooserKey != null && choicesReady,
        title = stringResource(if (chooserKey == LspConfig.UNINSTALLER_PACKAGE) R.string.installer_uninstaller_target else R.string.installer_target),
        summary = null,
        negativeText = stringResource(R.string.config_clear_confirm_cancel),
        positiveText = systemDefault,
        onDismissRequest = { if (!saving) chooserKey = null },
        onPositive = {
            chooserKey?.let { saveTarget(it, "") }
        },
        content = {
            val material3 = LocalAppUiStyle.current == AppUiStyle.Material3Expressive
            Column(
                modifier = Modifier.fillMaxWidth().then(
                    if (material3) Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())
                    else Modifier,
                ),
                verticalArrangement = Arrangement.spacedBy(if (material3) 2.dp else 0.dp),
            ) {
                if (choices.isEmpty()) SettingsCardRow(
                    title = stringResource(R.string.installer_no_choices), summary = "",
                )
                choices.forEachIndexed { index, choice ->
                    Material3ExpressiveSegmentPosition(index, choices.size) {
                        SettingsCardRow(title = choice.label, summary = choice.packageName,
                            onClick = { chooserKey?.let { saveTarget(it, choice.packageName) } })
                    }
                }
            }
        },
    )
}
