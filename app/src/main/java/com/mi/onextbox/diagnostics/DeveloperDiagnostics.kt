package com.mi.onextbox.diagnostics

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import com.mi.onextbox.BuildConfig
import com.mi.onextbox.lsp.LspFrameworkService
import com.mi.onextbox.ui.common.AppLogStore
import com.mi.onextbox.ui.common.readCachedRootAccessInfo
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal object DeveloperDiagnostics {
    const val DEBUG = "developer_debug_logging"
    const val CACHE_GENERATION = "developer_dex_cache_generation"
    private const val PREFS = "developer_diagnostics"
    private val handling = AtomicBoolean(false)
    @Volatile var debugEnabled = false
    private var appContext: Context? = null
    fun logEnabled(tag: String): Boolean {
        if (debugEnabled) return true
        val context = appContext ?: return false
        val config = com.mi.onextbox.lsp.LspPreferenceStore.prefs(context)
        return DiagnosticRules.temporaryLogging(config.getLong(RuntimeDiagnostics.UNTIL, 0),
            System.currentTimeMillis(), config.getStringSet(RuntimeDiagnostics.TAGS, emptySet()).orEmpty(), tag)
    }
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "ONextBox-Diagnostics").apply { isDaemon = true } }

    fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun install(app: Application) {
        appContext = app
        if (Application.getProcessName().endsWith(":diagnostics")) return
        debugEnabled = prefs(app).getBoolean("debug", false)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            if (!prefs(app).getBoolean("intercept", true) || !handling.compareAndSet(false, true)) {
                previous?.uncaughtException(thread, error)
            } else {
                val report = runCatching { save(app, error, thread, false) }
                    .recoverCatching { save(app, error, thread, false, extended = false) }.getOrNull()
                val opened = report != null && runCatching {
                    app.startActivity(Intent(app, CrashReportActivity::class.java)
                        .putExtra("page", "report").putExtra("report", report.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                }.isSuccess
                if (!opened) previous?.uncaughtException(thread, error)
            }
            Process.killProcess(Process.myPid())
            kotlin.system.exitProcess(10)
        }
        refreshEnvironment(app)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ refreshEnvironment(app) }, 5000L)
    }

    fun refreshEnvironment(context: Context) {
        val app = context.applicationContext
        worker.execute {
            runCatching {
                val root = readCachedRootAccessInfo(app)
                val framework = LspFrameworkService.readSnapshot()
                val text = buildString {
                    appendLine("Captured: ${java.time.Instant.now()}")
                    appendLine("Root (cached): ${root?.state ?: "unknown"}; ${root?.managerVersion ?: "unknown"}")
                    appendLine("Root detail: ${root?.detail.orEmpty()}")
                    appendLine("Framework: ${framework?.frameworkVersionText ?: "unavailable"}")
                    appendLine("Framework API: ${framework?.apiVersion ?: "unknown"}")
                    appendLine("Scopes: ${framework?.scopes?.sorted()?.joinToString() ?: "unavailable"}")
                    appendLine("Loaded processes: ${framework?.runningTargets?.joinToString { "${it.processName}: current=${it.current}, failed=${it.reloadFailed}, version=${it.loadedVersionCode}" } ?: "unavailable"}")
                    val property = runCatching { Class.forName("android.os.SystemProperties").getMethod("get", String::class.java) }.getOrNull()
                    listOf("ro.build.version.oplusrom", "ro.build.version.opporom", "ro.build.display.id",
                        "ro.build.version.ota", "ro.oplus.regionmark", "ro.vendor.oplus.regionmark",
                        "ro.oplus.market.name", "ro.vendor.oplus.market.name", "ro.product.name",
                        "ro.boot.hardware", "ro.build.oplus_nv_id", "ro.build.version.security_patch").forEach {
                        appendLine("$it=${runCatching { property?.invoke(null, it) ?: "unavailable" }.getOrDefault("unavailable")}")
                    }
                }
                atomicWrite(File(app.filesDir, "diagnostic-environment.txt"), text)
            }
        }
    }

    fun directory(context: Context) = File(context.noBackupFilesDir, "crash-reports").apply { mkdirs() }
    fun reports(context: Context): List<File> = directory(context).listFiles()
        ?.filter { it.isFile && it.name.endsWith(".json") }?.sortedByDescending { it.name }.orEmpty()
    fun report(context: Context, name: String): File? = reports(context).firstOrNull { it.name == name }

    @Synchronized fun save(context: Context, error: Throwable, thread: Thread, demo: Boolean, extended: Boolean = true): File {
        val stack = CrashReportText.stack(error)
        val fingerprint = CrashReportText.fingerprint(stack)
        val time = System.currentTimeMillis()
        val details = buildString {
            appendLine("ONextBox ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Report: $fingerprint\nTime: ${java.time.Instant.ofEpochMilli(time)}\nDemo: $demo")
            appendLine("Process: ${Application.getProcessName()} (${Process.myPid()})")
            appendLine("Thread: ${thread.name} (${thread.id})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.BRAND} ${Build.MODEL}")
            appendLine("Product: ${Build.PRODUCT}; device=${Build.DEVICE}; board=${Build.BOARD}; hardware=${Build.HARDWARE}")
            appendLine("Android: ${Build.VERSION.RELEASE}; API=${Build.VERSION.SDK_INT}; patch=${Build.VERSION.SECURITY_PATCH}")
            appendLine("Build: ${Build.DISPLAY}\nFingerprint: ${Build.FINGERPRINT}\nABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("Kernel: ${System.getProperty("os.version")}")
            appendLine("Memory: free=${Runtime.getRuntime().freeMemory()}, total=${Runtime.getRuntime().totalMemory()}, max=${Runtime.getRuntime().maxMemory()}")
            appendLine("\nException / causes / suppressed:\n$stack")
            if (!extended) appendLine("Extended diagnostics failed; basic exception report retained.")
            if (extended && prefs(context).getBoolean("collect", true)) {
                val root = readCachedRootAccessInfo(context)
                appendLine("\nRoot at crash (cached): ${root?.state}; ${root?.managerVersion}; ${root?.detail}")
                appendLine("\nEnvironment snapshot:\n" + runCatching {
                    File(context.filesDir, "diagnostic-environment.txt").readText()
                }.getOrDefault("Not captured yet"))
                appendLine("\nApp logs (latest 500 entries):")
                AppLogStore.entries.value.forEach { appendLine(it.toLine()) }
                appendLine("\nThread stacks:")
                Thread.getAllStackTraces().forEach { (t, trace) ->
                    appendLine("${t.name} (${t.id}) ${t.state}")
                    trace.forEach { appendLine("  at $it") }
                }
            }
        }
        val json = JSONObject().put("time", time).put("fingerprint", fingerprint).put("demo", demo)
            .put("exception", error.toString()).put("details", details)
        val file = File(directory(context), "$time-$fingerprint.json")
        atomicWrite(file, json.toString())
        reports(context).drop(20).forEach { it.delete() }
        return file
    }

    fun details(file: File): String = JSONObject(file.readText()).getString("details")
    private fun atomicWrite(file: File, content: String) {
        val pending = File(file.parentFile, file.name + ".tmp")
        pending.outputStream().use { stream -> stream.write(content.toByteArray()); stream.fd.sync() }
        check(pending.renameTo(file)) { "Cannot save diagnostic report" }
    }
}
