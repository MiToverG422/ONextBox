@file:Suppress("DEPRECATION")

package com.mi.onextbox.ui.screens

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import io.github.suqi8.coui.kmp.basic.Text as CouiText
import io.github.suqi8.coui.kmp.theme.COUITheme
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspConfig
import com.mi.onextbox.lsp.InstallerRoutingPolicy
import com.mi.onextbox.lsp.LspConfig.InstallerFeature as Feature
import com.mi.onextbox.ui.common.AppUiStyle
import com.mi.onextbox.ui.common.LocalAppUiStyle
import com.mi.onextbox.ui.common.CouiConfirmDialog
import com.mi.onextbox.ui.settings.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class InstallerChoice(val packageName: String, val label: String)

@Composable
internal fun InstallerPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var flags by remember { mutableStateOf(Feature.entries.associateWith { LspConfig.isInstallerFeatureEnabled(context, it) }) }
    var installer by remember { mutableStateOf(LspConfig.installerText(context, LspConfig.INSTALLER_PACKAGE)) }
    var choices by remember { mutableStateOf<List<InstallerChoice>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var showChooser by rememberSaveable { mutableStateOf(false) }
    val errorText = stringResource(R.string.installer_save_error)
    val systemDefault = stringResource(R.string.installer_system_default)
    var selectedLabel by remember { mutableStateOf(installer) }

    LaunchedEffect(installer) {
        selectedLabel = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationInfo(installer, 0)
                .loadLabel(context.packageManager).toString() }.getOrDefault(installer)
        }
    }
    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val manager = context.packageManager
                val intent = Intent(Intent.ACTION_VIEW).setDataAndType(
                    Uri.parse("content://onextbox/choice.apk"), InstallerRoutingPolicy.APK_TYPE)
                val resolved = manager.queryIntentActivities(intent, PackageManager.MATCH_ALL or PackageManager.MATCH_DEFAULT_ONLY)
                    .filter { it.activityInfo.exported && it.activityInfo.enabled && it.activityInfo.applicationInfo.enabled }
                val systemPackage = resolved.firstOrNull {
                    it.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
                }?.activityInfo?.packageName.orEmpty()
                if (LspConfig.installerText(context, LspConfig.INSTALLER_SYSTEM_PACKAGE) != systemPackage) {
                    LspConfig.setInstallerText(context, LspConfig.INSTALLER_SYSTEM_PACKAGE, systemPackage)
                }
                resolved.map { InstallerChoice(it.activityInfo.packageName,
                    it.activityInfo.applicationInfo.loadLabel(manager).toString()) }
                    .distinctBy { it.packageName }.sortedBy { it.label }
            }
        }
        result.onSuccess { choices = it }
            .onFailure { Toast.makeText(context, errorText, Toast.LENGTH_SHORT).show() }
        loading = false
    }

    fun saveTarget(value: String) {
        if (saving) return
        saving = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { LspConfig.setInstallerText(context, LspConfig.INSTALLER_PACKAGE, value) }
            }
            result.onSuccess { installer = value; showChooser = false }
                .onFailure { Toast.makeText(context, errorText, Toast.LENGTH_SHORT).show() }
            saving = false
        }
    }

    SettingsSection(stringResource(R.string.installer_targets))
    SettingsGroup {
        SettingsCardRow(
            title = stringResource(R.string.installer_target),
            summary = selectedLabel.ifEmpty { systemDefault },
            onClick = { showChooser = true }, showArrow = true,
        )
    }
    val groups = listOf(
        R.string.installer_group to listOf(
            Triple(Feature.Enabled, R.string.installer_enable, R.string.installer_enable_summary),
            Triple(Feature.Uninstall, R.string.installer_uninstall, R.string.installer_shared_uninstall_summary),
            Triple(Feature.Session, R.string.installer_session, R.string.installer_session_summary),
            Triple(Feature.InterceptSystem, R.string.installer_intercept_system, R.string.installer_intercept_system_summary),
            Triple(Feature.RemoveDefaultAppPolicy, R.string.installer_remove_default_policy, R.string.installer_remove_default_policy_summary),
        ),
    )
    groups.forEach { (groupTitle, options) ->
        SettingsSection(stringResource(groupTitle))
        SettingsGroup {
            options.forEachIndexed { index, (feature, title, summary) ->
                if (index > 0) SettingsDivider()
                Material3ExpressiveSegmentPosition(index, options.size) {
                    SettingsToggleRow(
                        title = stringResource(title), summary = stringResource(summary),
                        checked = flags[feature] == true,
                        enabled = !saving && (feature == Feature.Enabled || flags[Feature.Enabled] == true),
                        onCheckedChange = { value ->
                            if (!saving) {
                                saving = true
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching { LspConfig.setInstallerFeatureEnabled(context, feature, value) }
                                    }
                                    flags = Feature.entries.associateWith { LspConfig.isInstallerFeatureEnabled(context, it) }
                                    result.onFailure { Toast.makeText(context, errorText, Toast.LENGTH_SHORT).show() }
                                    saving = false
                                }
                            }
                        },
                        hasDividerAbove = index > 0, hasDividerBelow = index < options.lastIndex,
                    )
                }
            }
        }
    }
    val restartHint = stringResource(R.string.installer_restart_hint)
    val hintModifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp)
    if (LocalAppUiStyle.current == AppUiStyle.Material3Expressive) {
        Text(restartHint, modifier = hintModifier, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        CouiText(restartHint, modifier = hintModifier,
            style = COUITheme.textStyles.footnote1.copy(fontSize = 12.sp, lineHeight = 18.sp),
            color = COUITheme.colorScheme.onSurfaceVariantSummary)
    }
    CouiConfirmDialog(
        show = showChooser && !loading,
        title = stringResource(R.string.installer_target), summary = null,
        negativeText = stringResource(R.string.config_clear_confirm_cancel), positiveText = systemDefault,
        onDismissRequest = { if (!saving) showChooser = false },
        onPositive = { saveTarget("") },
        content = {
            val material3 = LocalAppUiStyle.current == AppUiStyle.Material3Expressive
            Column(
                modifier = Modifier.fillMaxWidth().then(
                    if (material3) Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()) else Modifier),
                verticalArrangement = Arrangement.spacedBy(if (material3) 2.dp else 0.dp),
            ) {
                if (choices.isEmpty()) SettingsCardRow(title = stringResource(R.string.installer_no_choices), summary = "")
                choices.forEachIndexed { index, choice ->
                    Material3ExpressiveSegmentPosition(index, choices.size) {
                        SettingsCardRow(title = choice.label, summary = choice.packageName,
                            onClick = { saveTarget(choice.packageName) })
                    }
                }
            }
        },
    )
}
