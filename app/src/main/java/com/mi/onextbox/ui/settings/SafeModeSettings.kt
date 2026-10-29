package com.mi.onextbox.ui.settings

import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Column
import com.mi.onextbox.ui.screens.FeatureExpandableVisibility
import com.mi.onextbox.diagnostics.DiagnosticRepository
import com.mi.onextbox.diagnostics.SafeModePolicy
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.lsp.ModernXposedPreferenceSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable internal fun SafeModeSettings() {
    val context = LocalContext.current
    val preferences = remember { LspPreferenceStore.prefs(context) }
    var manual by remember { mutableStateOf(preferences.getBoolean(SafeModePolicy.MANUAL, false)) }
    var automatic by remember { mutableStateOf(preferences.getBoolean(SafeModePolicy.AUTOMATIC, true)) }
    var busy by remember { mutableStateOf(false) }
    var records by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val scope = rememberCoroutineScope()
    fun refresh() = scope.launch {
        records = withContext(Dispatchers.IO) {
            runCatching { DiagnosticRepository.events(context).filter { it.kind == "safe_mode" }
                .associate { it.name to it.detail } }.getOrDefault(emptyMap())
        }
    }
    fun change(key: String, value: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { ModernXposedPreferenceSync.setBooleanAndSync(context, key, value) } }
                .onSuccess {
                    manual = preferences.getBoolean(SafeModePolicy.MANUAL, false)
                    automatic = preferences.getBoolean(SafeModePolicy.AUTOMATIC, true)
                    Toast.makeText(context, "已保存；重启作用域或手机以完整应用", Toast.LENGTH_LONG).show()
                }.onFailure { Toast.makeText(context, "保存失败：${it.cause?.message ?: it.message}", Toast.LENGTH_LONG).show() }
            busy = false
        }
    }
    LaunchedEffect(automatic) { if (automatic) refresh() }
    SettingsSection(title = "安全模式")
    SettingsGroup {
        SettingsToggleRow(title = "安全模式", summary = "暂停所有作用域的模块修改，保留功能配置。已有界面和内存修改需重启作用域或手机后完全恢复；关闭后同样需重启恢复功能。",
            checked = manual, onCheckedChange = { change(SafeModePolicy.MANUAL, it) })
        SettingsDivider()
        SettingsToggleRow(title = "高风险作用域崩溃保护", summary = "系统界面、系统桌面、系统框架在 2 分钟内发生 3 次未捕获 Java 崩溃时，仅暂停对应作用域。重启手机自动解除；不会清空配置或自动重启。",
            checked = automatic, onCheckedChange = { change(SafeModePolicy.AUTOMATIC, it) })
        FeatureExpandableVisibility(visible = automatic) {
            Column {
                SettingsDivider()
                SettingsCardRow("保护记录", "点击刷新最近记录；无记录不代表未注入。原生崩溃、ANR 和强制终止不计入此保护。", onClick = { refresh() })
                listOf("com.android.systemui" to "系统界面", "com.android.launcher" to "系统桌面", "system" to "系统框架").forEach { (name, title) ->
                    SettingsDivider()
                    val record = records[name] ?: if (name == "com.android.launcher") records.entries.firstOrNull {
                        SafeModePolicy.scope(it.key, false)?.contains("launcher") == true
                    }?.value else null
                    val summary = when {
                        record == null -> "尚无可读取的保护记录"
                        record.startsWith("Protection unavailable") -> "保护初始化失败，请查看诊断日志"
                        "blocked=true" in record || record.startsWith("Automatic protection active") -> "最近记录：已进入单独安全模式，重启手机解除"
                        record.startsWith("count=") -> "最近记录：已记录崩溃，尚未达到保护阈值"
                        else -> "最近记录：崩溃监测已就绪"
                    }
                    SettingsCardRow(title, summary, onClick = { refresh() })
                }
            }
        }
    }
}
