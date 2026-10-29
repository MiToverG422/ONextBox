package com.mi.onextbox.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import com.mi.onextbox.ui.RootNavDisplay
import com.mi.onextbox.ui.layout.rememberChromeBlurBackdrop
import com.mi.onextbox.ui.screens.FeatureIcon
import com.mi.onextbox.lsp.LspLoadedTarget
import androidx.core.view.WindowCompat
import com.mi.onextbox.MainActivity
import com.mi.onextbox.R
import com.mi.onextbox.lsp.LspFrameworkService
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.lsp.ModernXposedPreferenceSync
import com.mi.onextbox.ui.common.AppLocale
import com.mi.onextbox.ui.common.*
import com.mi.onextbox.ui.common.CouiConfirmDialog
import com.mi.onextbox.ui.settings.*
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.ThemeController
import io.github.suqi8.coui.kmp.theme.ThemePaletteStyle
import io.github.suqi8.coui.kmp.theme.ThemeColorSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class DiagnosticActivity : ComponentActivity() {
    override fun attachBaseContext(base: Context) = super.attachBaseContext(AppLocale.wrapContext(base))
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val mode = remember { AppThemeMode.get(this) }
            val keyColor = remember { AppThemeKeyColor.get(this) }
            val seed = rememberMonetSeedColor(mode.isMonet, keyColor)
            val palette = remember { AppThemePaletteStyle.get(this) }
            val spec = remember { AppThemeColorSpec.get(this) }
            val controller = remember(mode, seed) { ThemeController(
                colorSchemeMode = mode.toColorSchemeMode(), keyColor = seed,
                paletteStyle = ThemePaletteStyle.entries.getOrNull(palette) ?: ThemePaletteStyle.TonalSpot,
                colorSpec = ThemeColorSpec.entries.getOrNull(spec) ?: ThemeColorSpec.Spec2021,
            ) }
            val dark = when (mode) {
                AppThemeMode.MonetDark -> true
                AppThemeMode.MonetLight -> false
                else -> isSystemInDarkTheme()
            }
            val monetColors = remember(mode, seed, spec, dark) {
                if (mode.isMonet && spec == CouiColorSpec.Spec2026.ordinal && seed != null)
                    coui2026Colors(seed, dark) else null
            }
            COUITheme(controller = controller) {
              ColorOs17AppTheme(nativeAccent = !mode.isMonet, monetColors = monetColors) {
                val light = COUITheme.colorScheme.surface.luminance() > .5f
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = light
                        isAppearanceLightNavigationBars = light
                    }
                }
                AppUiStyleAppTheme(AppUiStyle.ColorOs, mode, keyColor, palette, spec) {
                DiagnosticHost(intent.getStringExtra("page") ?: "reports", intent.getStringExtra("report"))
                }
              }
            }
        }
    }
}

class CrashReportActivity : DiagnosticActivity()

@Composable private fun DiagnosticHost(initialPage: String, initialReport: String?) {
    val activity = LocalActivity.current
    var selectedRoute by rememberSaveable { mutableStateOf<String?>(null) }
    val keys = listOf(initialPage) + listOfNotNull(selectedRoute)
    fun back() { if (selectedRoute != null) selectedRoute = null else activity?.finish() }
    BackHandler(enabled = selectedRoute == null) { back() }
    val entries = rememberDecoratedNavEntries(
        backStack = keys,
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
        entryProvider = { key -> NavEntry(key) {
            DiagnosticPage(
                key.substringBefore(':'),
                if (':' in key) key.substringAfter(':') else initialReport,
                onReport = { selectedRoute = "report:$it" }, onNavigate = { selectedRoute = it.takeIf(String::isNotEmpty) }, onBack = ::back,
            )
        } },
    )
    RootNavDisplay(entries = entries, onBack = ::back, modifier = Modifier.fillMaxSize())
}

@Composable private fun DiagnosticPage(page: String, reportName: String?,
    onReport: (String) -> Unit, onNavigate: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    var content by remember { mutableStateOf("") }
    var files by remember { mutableStateOf(emptyList<java.io.File>()) }
    var targets by remember { mutableStateOf<List<LspLoadedTarget>>(emptyList()) }
    var labels by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                checkNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(content) }
            } }.onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
        }
    }
    LaunchedEffect(page, reportName, revision, configuration) {
        busy = true
        content = ""
        try {
            withContext(Dispatchers.IO) {
                when (page) {
                    "report" -> content = DeveloperDiagnostics.report(context, reportName.orEmpty())
                        ?.let(DeveloperDiagnostics::details) ?: resources.getString(R.string.diagnostics_no_report)
                    "reports" -> files = DeveloperDiagnostics.reports(context)
                    "hooks" -> {
                        ModernXposedPreferenceSync.initialize(context)
                        val snapshot = LspFrameworkService.readSnapshot()
                        targets = snapshot?.runningTargets.orEmpty().sortedBy { it.processName }
                        labels = targets.associate { target ->
                            val pkg = target.processName.substringBefore(':')
                            pkg to runCatching {
                                context.packageManager.getApplicationInfo(pkg, 0).loadLabel(context.packageManager).toString()
                            }.getOrDefault(pkg)
                        }
                        content = buildString {
                            appendLine(snapshot?.frameworkVersionText ?: resources.getString(R.string.diagnostics_framework_unavailable))
                            appendLine(resources.getString(R.string.diagnostics_hook_notice))
                            snapshot?.runningTargets?.sortedBy { it.processName }?.forEach {
                                appendLine("\n${it.processName}\nUID ${it.uid} · ${it.loadedVersionCode}")
                                appendLine(resources.getString(when {
                                    it.reloadFailed -> R.string.diagnostics_hook_failed
                                    it.current -> R.string.diagnostics_hook_current
                                    else -> R.string.diagnostics_hook_old
                                }))
                            }
                            if (snapshot?.runningTargets == null) appendLine(resources.getString(R.string.diagnostics_targets_unavailable))
                            appendLine("\n" + resources.getString(R.string.diagnostics_scopes))
                            appendLine(snapshot?.scopes?.sorted()?.joinToString("\n") ?: "—")
                        }
                    }
                }
            }
        } catch (error: Exception) { content = error.toString() }
        finally { busy = false }
    }
    val title = if (page in developerToolPages) developerToolTitle(page) else stringResource(when (page) {
        "permissions" -> R.string.diagnostics_permissions
        "cache" -> R.string.diagnostics_cache
        "hooks" -> R.string.diagnostics_hooks
        "report" -> R.string.diagnostics_report
        else -> R.string.diagnostics_reports
    })
    val blurEnabled = remember { context.getSharedPreferences("onextbox_prefs", Context.MODE_PRIVATE)
        .getBoolean("blur_effect_enabled", true) }
    val backdrop = rememberChromeBlurBackdrop(blurEnabled)
    SettingsPageSurface(title, modifier = Modifier.fillMaxSize(), showBack = true, onBack = onBack,
        blurBackdrop = backdrop) {
        if (page in developerToolPages) {
            DeveloperToolsContent(page, reportName, onNavigate)
        } else if (page == "permissions") {
            DiagnosticPermissions()
        } else if (page == "cache") {
            SettingsGroup {
                SettingsCardRow(stringResource(R.string.diagnostics_cache), stringResource(R.string.diagnostics_cache_summary))
                SettingsDivider()
                SettingsCardRow(stringResource(R.string.diagnostics_cache_clear),
                    stringResource(R.string.diagnostics_cache_generation, LspPreferenceStore.prefs(context)
                        .getInt(DeveloperDiagnostics.CACHE_GENERATION, 0)), onClick = { if (!busy) confirmClear = true })
            }
        } else if (page == "reports") {
            SettingsGroup {
                SettingsCardRow(stringResource(R.string.diagnostics_refresh), "", onClick = { revision++ })
                SettingsDivider()
                SettingsCardRow(stringResource(R.string.diagnostics_delete_reports), "", onClick = { confirmClear = true })
            }
            if (files.isEmpty()) SettingsGroup { SettingsCardRow(stringResource(R.string.diagnostics_no_report), "") }
            files.forEach { file -> SettingsGroup {
                SettingsCardRow(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(file.name.substringBefore('-').toLongOrNull() ?: file.lastModified())), file.name.substringAfter('-').removeSuffix(".json"), showArrow = true,
                    onClick = { onReport(file.name) })
            } }
        } else {
            SettingsGroup {
                SettingsCardRow(stringResource(R.string.diagnostics_copy), "", onClick = {
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(title, content))
                })
                SettingsDivider()
                SettingsCardRow(stringResource(R.string.diagnostics_export), "", onClick = { export.launch("ONextBox-diagnostics.txt") })
                SettingsDivider()
                if (page == "hooks") SettingsCardRow(stringResource(R.string.diagnostics_refresh), "", onClick = { revision++ })
                if (page == "report") SettingsCardRow(stringResource(R.string.diagnostics_restart), "", onClick = {
                    context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                    activity?.finish()
                })
            }
            if (page == "hooks") {
                SettingsGroup {
                    if (targets.isEmpty()) SettingsCardRow(stringResource(R.string.diagnostics_targets_unavailable), "")
                    targets.forEachIndexed { index, target ->
                        if (index > 0) SettingsDivider()
                        val pkg = target.processName.substringBefore(':')
                        val status = stringResource(when {
                            target.reloadFailed -> R.string.diagnostics_hook_failed
                            target.current -> R.string.diagnostics_hook_current
                            else -> R.string.diagnostics_hook_old
                        })
                        SettingsCardRow(labels[pkg] ?: pkg,
                            status + "\n" + target.processName + "\nUID " + target.uid + " · " + target.loadedVersionCode,
                            leadingContent = { FeatureIcon(packageNames = listOf(if (pkg == "system") "android" else pkg)) })
                    }
                }
            }
            SettingsGroup {
                SelectionContainer { Text(if (busy) stringResource(R.string.diagnostics_loading) else if (page == "hooks") content.lineSequence().take(2).joinToString("\n") else content,
                    modifier = Modifier.padding(16.dp), fontSize = 13.sp, lineHeight = 19.sp,
                    color = COUITheme.colorScheme.onSurface) }
            }
        }
    }
    CouiConfirmDialog(show = confirmClear, title = title,
        summary = stringResource(if (page == "cache") R.string.diagnostics_cache_summary else R.string.diagnostics_delete_confirm),
        negativeText = stringResource(android.R.string.cancel), positiveText = stringResource(android.R.string.ok),
        onDismissRequest = { confirmClear = false }, onPositive = {
            confirmClear = false
            scope.launch {
                busy = true
                runCatching { withContext(Dispatchers.IO) {
                    if (page == "cache") {
                        val next = LspPreferenceStore.prefs(context).getInt(DeveloperDiagnostics.CACHE_GENERATION, 0) + 1
                        ModernXposedPreferenceSync.setIntAndSync(context, DeveloperDiagnostics.CACHE_GENERATION, next)
                    } else DeveloperDiagnostics.reports(context).forEach { check(it.delete()) }
                } }.onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                busy = false
                revision++
            }
        })
}
