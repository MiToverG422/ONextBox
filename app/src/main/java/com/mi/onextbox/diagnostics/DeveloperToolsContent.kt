package com.mi.onextbox.diagnostics

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.lsp.ModernXposedPreferenceSync
import com.mi.onextbox.ui.common.CouiConfirmDialog
import com.mi.onextbox.ui.common.readCachedRootAccessInfo
import com.mi.onextbox.ui.common.RootAccessState
import com.mi.onextbox.ui.settings.*
import com.mi.onextbox.ui.screens.FeatureExpandableVisibility
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal val developerToolPages = setOf("bundle", "hookdetails", "sync", "snapshots", "snapshot", "performance", "trace")
internal fun developerToolTitle(page: String) = when (page) {
    "bundle" -> "诊断包导出"
    "hookdetails" -> "单项 Hook 状态"
    "sync" -> "配置同步状态"
    "snapshots", "snapshot" -> "功能配置快照"
    "performance" -> "性能诊断"
    else -> "临时详细日志"
}

@Composable private fun DiagnosticText(text: String) {
    SelectionContainer { Text(text, modifier = Modifier.padding(16.dp), fontSize = 13.sp,
        lineHeight = 19.sp, color = COUITheme.colorScheme.onSurface) }
}

@Composable internal fun DeveloperToolsContent(page: String, detail: String?, navigate: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var events by remember { mutableStateOf<List<DiagnosticEvent>>(emptyList()) }
    var files by remember { mutableStateOf<List<java.io.File>>(emptyList()) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var process by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var minutes by remember { mutableIntStateOf(5) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var tags by remember { mutableStateOf(setOf("*")) }
    var showTags by remember { mutableStateOf(false) }
    var until by remember { mutableLongStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    fun runWork(refresh: Boolean = true, block: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            try { withContext(Dispatchers.IO) { block() }; if (refresh) revision++ }
            catch (error: Exception) { Toast.makeText(context, error.cause?.message ?: error.message, Toast.LENGTH_LONG).show() }
            finally { busy = false }
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) runWork {
            checkNotNull(context.contentResolver.openOutputStream(uri)).use { DiagnosticRepository.bundle(context, it) }
            message = "诊断包已导出"
        }
    }
    LaunchedEffect(page, detail, revision) {
        busy = true
        try {
            withContext(Dispatchers.IO) {
                if (page in setOf("hookdetails", "performance", "sync", "trace")) events = DiagnosticRepository.events(context)
                when (page) {
                    "snapshots" -> files = DiagnosticRepository.list(context)
                    "snapshot" -> message = DiagnosticRepository.diff(context, detail.orEmpty())
                    "sync" -> {
                        val (local, remote) = ModernXposedPreferenceSync.diagnosticReadback(context)
                        val hash = DiagnosticRules.fingerprint(local)
                        val remoteHash = remote?.let(DiagnosticRules::fingerprint)
                        val ready = remote?.get("__onextbox_api102_snapshot_ready") == true
                        message = "本地持久配置：$hash\n框架回读：${remoteHash ?: "不可用"}\n" +
                            "同步状态：${if (!ready) "框架未就绪" else if (hash == remoteHash) "一致" else "不一致"}\n" +
                            "最近完整同步：${ModernXposedPreferenceSync.lastDiagnosticWrite.takeIf { it > 0 }?.let(::diagnosticTime) ?: "本次运行暂无记录"}\n" +
                            "最近错误：${ModernXposedPreferenceSync.lastDiagnosticError ?: "无已记录错误"}\n\n目标进程最近回执（历史记录，不代表进程仍存活）：\n" +
                            DiagnosticRepository.currentEvents(events).filter { it.kind == "config" }.groupBy { it.process }
                                .values.joinToString("\n\n") { rows -> rows.last().let {
                                    "${it.process} · PID ${it.pid}\n${diagnosticTime(it.time)}\n${if (it.detail == hash) "与本地一致" else "与本地不同"} · ${it.detail}"
                                } }.ifEmpty { "暂无回执，不能判定目标进程已读取" }
                    }
                    "trace" -> {
                        val prefs = LspPreferenceStore.prefs(context)
                        until = prefs.getLong(RuntimeDiagnostics.UNTIL, 0)
                    }
                }
            }
        } catch (error: Exception) { message = error.cause?.message ?: error.toString() }
        finally { busy = false }
    }
    LaunchedEffect(page) {
        if (page == "trace") tags = LspPreferenceStore.prefs(context)
            .getStringSet(RuntimeDiagnostics.TAGS, setOf("*")).orEmpty().toSet()
    }
    LaunchedEffect(page) { if (page == "trace") while (true) { now = System.currentTimeMillis(); delay(1000) } }
    if (page != "bundle") SettingsGroup {
        SettingsCardRow(if (busy) "正在读取…" else "刷新", "", onClick = { if (!busy) revision++ })
    }
    when (page) {
        "bundle" -> {
            SettingsGroup { DiagnosticText("包含设备环境、框架与作用域、功能配置、应用日志、Hook 诊断记录和崩溃报告。\n\n过滤常见账号、令牌、手机号、邮件、地址和私人路径；配置文本值不导出。不包含快照原文件。\n\n记录可能包含未识别的个人内容，分享前请检查；不会自动上传。") }
            SettingsGroup { SettingsCardRow(if (busy) "正在导出…" else "导出 ZIP 诊断包", message,
                showArrow = true, onClick = { if (!busy) export.launch("ONextBox-diagnostics-${System.currentTimeMillis()}.zip") }) }
        }
        "sync" -> {
            SettingsGroup { DiagnosticText(message) }
            SettingsGroup { SettingsCardRow("重新同步", "同步当前本地配置到框架", onClick = {
                if (!busy) { ModernXposedPreferenceSync.syncNow(context); revision++ }
            }) }
        }
        "snapshots" -> {
            SettingsGroup { SettingsCardRow("保存当前配置", "仅保存功能配置，本地最多保留 10 份", onClick = {
                runWork { DiagnosticRepository.saveSnapshot(context) }
            }) }
            if (files.isEmpty()) SettingsGroup { DiagnosticText("暂无配置快照") }
            files.forEach { file -> SettingsGroup {
                SettingsCardRow(diagnosticTime(file.nameWithoutExtension.toLongOrNull() ?: file.lastModified()),
                    "查看差异、恢复或删除", showArrow = true, onClick = { navigate("snapshot:${file.name}") })
            } }
        }
        "snapshot" -> {
            SettingsSection("配置差异")
            SettingsGroup { DiagnosticText(message) }
            SettingsGroup {
                SettingsCardRow("恢复此快照", "先备份当前配置，再恢复并同步到框架；应用界面重新打开后读取", onClick = { if (!busy) confirm = "restore" })
                SettingsDivider()
                SettingsCardRow("与上一份快照比较", "", onClick = { runWork(refresh = false) {
                    val list = DiagnosticRepository.list(context)
                    val index = list.indexOfFirst { it.name == detail }
                    val other = list.getOrNull(index + 1) ?: error("没有更早的快照")
                    val a = DiagnosticRepository.load(context, detail.orEmpty())
                    val b = DiagnosticRepository.load(context, other.name)
                    message = "对比：${diagnosticTime(other.nameWithoutExtension.toLong())}\n\n" +
                        (a.keys + b.keys).sorted().filter { a[it] != b[it] }.joinToString("\n\n") { "$it\n本份：${a[it]}\n上一份：${b[it]}" }.ifEmpty { "没有配置差异" }
                } })
                SettingsDivider()
                SettingsCardRow("删除此快照", "", onClick = { if (!busy) confirm = "delete" })
            }
        }
        "trace" -> {
            SettingsGroup {
                SettingsCardRow("记录状态", if (until > now) "剩余 ${(until - now) / 1000} 秒；到期自动停止" else "已停止")
                SettingsDivider()
                SettingsCardRow("记录时长", "$minutes 分钟", onClick = { minutes = when (minutes) { 5 -> 10; 10 -> 15; else -> 5 } })
                SettingsDivider()
                SettingsCardRow("开始记录", "仅开启所选日志标签。全局调试模式开启时仍会记录其他标签。", onClick = { runWork {
                    check(tags.isNotEmpty()) { "请至少选择一个标签" }
                    ModernXposedPreferenceSync.replaceDiagnosticValues(context, mapOf(RuntimeDiagnostics.UNTIL to System.currentTimeMillis() + minutes * 60_000L,
                        RuntimeDiagnostics.TAGS to tags), featuresOnly = false)
                } })
                SettingsDivider()
                SettingsCardRow("立即停止", "", onClick = { runWork {
                    ModernXposedPreferenceSync.replaceDiagnosticValues(context, mapOf(RuntimeDiagnostics.UNTIL to 0L), featuresOnly = false)
                } })
            }
            SettingsSection("日志标签")
            val choices = (listOf("*", "App") + diagnosticLogTags + events.filter { it.kind == "log" || it.kind == "hook_log" }.map { it.name }).distinct()
            SettingsGroup { SettingsCardRow("选择日志标签", if ("*" in tags) "全部标签" else "已选择 ${tags.size} 个标签",
                showExpandArrow = true, expandArrowExpanded = showTags, onClick = { showTags = !showTags }) }
            FeatureExpandableVisibility(visible = showTags) {
                SettingsGroup { choices.forEachIndexed { i, tag ->
                    if (i > 0) SettingsDivider()
                    SettingsToggleRow(title = if (tag == "*") "全部标签" else tag, summary = "", checked = tag in tags,
                        onCheckedChange = { enabled -> tags = if (enabled) (if (tag == "*") emptySet() else tags - "*") + tag else tags - tag })
                } }
            }
        }
        else -> {
            SettingsGroup { DiagnosticText("以下为已记录的诊断事件。重启或日志缓冲区轮转后可能缺失；未记录不等于失败。" +
                if (readCachedRootAccessInfo(context)?.state != RootAccessState.Granted) "\n当前无已授权 Root，仅显示本 App 记录。" else "\n按进程最近记录的模块代次显示；历史记录不保证进程仍存活。") }
            val current = DiagnosticRepository.currentEvents(events)
            val processes = current.map { it.process }.distinct()
            SettingsGroup { SettingsCardRow("进程", process ?: "全部", onClick = {
                val options = listOf<String?>(null) + processes
                process = options[(options.indexOf(process) + 1) % options.size]
            }) }
            val rows = current.filter { process == null || it.process == process }.filter {
                if (page == "performance") it.kind in setOf("startup", "dex", "initialization")
                else it.kind in setOf("initialization", "installed", "staged", "failure", "callback_failure", "hook_log", "committed")
            }
            val groups = rows.groupBy { it.process + "\n" + it.name }.entries
                .sortedByDescending { if (page == "performance") it.value.maxOf { r -> r.ms } else it.value.maxOf { r -> r.time } }.take(100)
            if (groups.isEmpty()) SettingsGroup { DiagnosticText(message.ifBlank { "暂无记录；需要目标作用域加载新模块后才会产生记录。" }) }
            groups.forEach { (key, records) -> SettingsGroup {
                val recent = records.last()
                SettingsCardRow(recent.name, recent.process + "\n" + if (page == "performance") "最近 ${recent.ms} ms · 峰值 ${records.maxOf { it.ms }} ms" else
                    "${diagnosticTime(recent.time)} · ${when (recent.kind) { "installed" -> "目标已安装"; "staged" -> "目标已暂存，等待热加载提交"; "failure", "callback_failure" -> "发生异常"; "initialization" -> "初始化结束（零目标可能为功能关闭或未命中）"; "committed" -> "热加载已提交"; else -> "诊断日志" }}",
                    showExpandArrow = true, expandArrowExpanded = expanded == key, onClick = { expanded = if (expanded == key) null else key })
                if (expanded == key) { SettingsDivider(); DiagnosticText(records.takeLast(10).joinToString("\n\n") {
                    "${diagnosticTime(it.time)} · ${it.kind} · ${it.ms} ms\n${it.detail}"
                }) }
            } }
        }
    }
    CouiConfirmDialog(show = confirm != null, title = if (confirm == "restore") "恢复配置快照" else "删除配置快照",
        summary = if (confirm == "restore") "将自动保存当前配置，再覆盖功能开关及参数。框架不可用时不会恢复。" else "此操作无法撤销。",
        negativeText = "取消", positiveText = "确定", onDismissRequest = { confirm = null }, onPositive = {
            val action = confirm; confirm = null
            runWork {
                if (action == "restore") {
                    val values = DiagnosticRepository.load(context, detail.orEmpty())
                    DiagnosticRepository.saveSnapshot(context)
                    ModernXposedPreferenceSync.replaceDiagnosticValues(context, values, featuresOnly = true)
                } else {
                    check(DiagnosticRepository.list(context).firstOrNull { it.name == detail }?.delete() == true)
                    navigate("")
                }
            }
        })
}

private fun diagnosticTime(time: Long): String = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(time))
