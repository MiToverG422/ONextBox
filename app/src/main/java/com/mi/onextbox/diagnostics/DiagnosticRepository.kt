package com.mi.onextbox.diagnostics

import android.content.Context
import com.mi.onextbox.lsp.LspPreferenceStore
import com.mi.onextbox.lsp.LspFrameworkService
import com.mi.onextbox.ui.common.AppLogStore
import com.mi.onextbox.ui.common.RootAccessState
import com.mi.onextbox.ui.common.readCachedRootAccessInfo
import com.topjohnwu.superuser.Shell
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal data class DiagnosticEvent(val time: Long, val generation: Long, val process: String, val pid: Int,
    val kind: String, val name: String, val detail: String, val ms: Long)

internal object DiagnosticRepository {
    fun events(context: Context): List<DiagnosticEvent> {
        val lines = if (readCachedRootAccessInfo(context)?.state == RootAccessState.Granted) {
            val result = Shell.cmd("logcat -d -v raw -t 3000 -s ONextBox-Diag:I '*:S'").exec()
            check(result.isSuccess) { "无法读取诊断记录" }
            result.out
        } else RuntimeDiagnostics.localEvents.toList()
        val rows = lines.mapNotNull { line -> runCatching {
            val j = JSONObject(line)
            DiagnosticEvent(j.getLong("time"), j.getLong("generation"), j.getString("process"), j.getInt("pid"),
                j.getString("kind"), j.getString("name"), j.optString("detail"), j.optLong("ms"))
        }.getOrNull() }
        return rows.sortedBy { it.time }.takeLast(2000)
    }
    fun currentEvents(rows: List<DiagnosticEvent>): List<DiagnosticEvent> {
        val latest = rows.groupBy { it.process }.mapValues { (_, v) -> v.maxOf { it.generation } }
        return rows.filter { latest[it.process] == it.generation }
    }
    private fun snapshots(context: Context) = File(context.noBackupFilesDir, "config-snapshots").apply { mkdirs() }
    fun list(context: Context) = snapshots(context).listFiles()?.filter { it.extension == "json" }?.sortedByDescending { it.name }.orEmpty()
    fun encode(values: Map<String, *>): JSONObject = JSONObject().apply {
        values.toSortedMap().forEach { (key, value) ->
            val type = when (value) { is Boolean -> "boolean"; is Int -> "int"; is Long -> "long"; is Float -> "float"; is String -> "string"; is Set<*> -> "set"; else -> error("Unsupported value: $key") }
            put(key, JSONObject().put("type", type).put("value", if (value is Set<*>) JSONArray(value.toList()) else value))
        }
    }
    fun decode(json: JSONObject): Map<String, Any> = json.keys().asSequence().associateWith { key ->
        val row = json.getJSONObject(key)
        when (row.getString("type")) {
            "boolean" -> row.getBoolean("value")
            "int" -> row.getInt("value")
            "long" -> row.getLong("value")
            "float" -> row.getDouble("value").toFloat().also { require(it.isFinite()) }
            "string" -> row.getString("value")
            "set" -> row.getJSONArray("value").let { array -> (0 until array.length()).map { array.getString(it) }.toSet() }
            else -> error("Unknown configuration type")
        }
    }
    fun saveSnapshot(context: Context): File {
        val values = DiagnosticRules.featureValues(LspPreferenceStore.prefs(context).all)
        val file = File(snapshots(context), "${System.currentTimeMillis()}.json")
        val pending = File(file.path + ".tmp")
        pending.writeText(JSONObject().put("schema", 1).put("values", encode(values)).toString())
        check(pending.renameTo(file))
        list(context).drop(10).forEach { it.delete() }
        return file
    }
    fun load(context: Context, name: String): Map<String, Any> {
        val file = checkNotNull(list(context).firstOrNull { it.name == name }) { "快照不存在" }
        val j = JSONObject(file.readText())
        require(j.getInt("schema") == 1)
        return decode(j.getJSONObject("values")).also { values ->
            require(values.keys.none { it.startsWith("__") || it.startsWith("developer_") })
        }
    }
    fun diff(context: Context, name: String): String {
        val saved = load(context, name)
        val current = DiagnosticRules.featureValues(LspPreferenceStore.prefs(context).all)
        return (saved.keys + current.keys).sorted().filter { saved[it] != current[it] }.joinToString("\n\n") {
            "$it\n当前：${current[it] ?: "默认"}\n快照：${saved[it] ?: "默认"}"
        }.ifEmpty { "没有配置差异" }
    }
    fun bundle(context: Context, output: OutputStream) {
        ZipOutputStream(output.buffered()).use { zip ->
            fun entry(name: String, value: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(DiagnosticRules.redact(value).toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
            entry("README.txt", "ONextBox diagnostics\n${java.time.Instant.now()}\n常见敏感字段已过滤；请检查内容后再分享。进程记录来自有容量限制的系统日志，缺少记录不代表 Hook 失败。")
            entry("environment.txt", runCatching { File(context.filesDir, "diagnostic-environment.txt").readText() }.getOrDefault("环境快照暂不可用"))
            entry("framework.txt", LspFrameworkService.readSnapshot().toString())
            entry("app-log.txt", AppLogStore.entries.value.joinToString("\n") { it.toLine() })
            val safeConfig = DiagnosticRules.featureValues(LspPreferenceStore.prefs(context).all).mapValues { (_, value) ->
                if (value is String || value is Set<*>) "[TEXT VALUE REDACTED]" else value
            }
            entry("configuration.json", encode(safeConfig).toString(2))
            entry("hook-events.txt", runCatching { events(context).joinToString("\n") }.getOrElse { it.toString() })
            DeveloperDiagnostics.reports(context).forEachIndexed { index, file ->
                entry("crashes/report-$index.txt", runCatching { DeveloperDiagnostics.details(file) }.getOrDefault("报告读取失败"))
            }
        }
    }
}
