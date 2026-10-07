package com.mi.onextbox.lsp

import android.app.Application
import android.app.Instrumentation
import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.os.Process
import androidx.core.content.ContextCompat
import com.mi.onextbox.lsp.compat.ModernHookBridge
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.push.MCS_PACKAGE
import com.mi.onextbox.push.PushMonitorCodec
import com.mi.onextbox.push.PushMonitorProvider as Bridge
import com.mi.onextbox.push.PushMonitorRules
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Read-only MCS registration and notification-enqueue observers. */
internal object OppoPushMonitorHooker {
    private const val TAG = "ONextBox-PushMonitor"
    private val installed = AtomicBoolean()
    private val initialized = AtomicBoolean()
    private val refreshPending = AtomicBoolean()
    private val pushPending = AtomicBoolean()
    private val warned = AtomicBoolean()
    private val requestId = AtomicReference("")
    private val queuedPushes = linkedSetOf<String>()
    private val worker = ScheduledThreadPoolExecutor(1) { runnable ->
        Thread(runnable, "ONextBox-McsMonitor").apply { isDaemon = true }
    }.apply { removeOnCancelPolicy = true }
    @Volatile private var appContext: Context? = null

    fun hook(loader: ClassLoader) {
        if (!installed.compareAndSet(false, true)) return
        runCatching {
            val method = Instrumentation::class.java.getDeclaredMethod("callApplicationOnCreate", Application::class.java)
            ModernHookBridge.hookMethod(method, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.throwable != null) return
                    val app = param.args.firstOrNull() as? Application ?: return
                    if (app.packageName == MCS_PACKAGE) initialize(app)
                }
            })
        }.onFailure { warn() }
        runCatching {
            val proxy = Class.forName("android.app.INotificationManager\$Stub\$Proxy", false, loader)
            val signature = arrayOf(String::class.java, String::class.java, String::class.java,
                Int::class.javaPrimitiveType, Notification::class.java, Int::class.javaPrimitiveType)
            val method = proxy.declaredMethods.single {
                it.name == "enqueueNotificationWithTag" && it.parameterTypes.contentEquals(signature)
            }
            ModernHookBridge.hookMethod(method, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.throwable != null || appContext == null) return
                    if (param.args.getOrNull(5) != Process.myUid() / 100_000) return
                    val pkg = param.args.firstOrNull() as? String ?: return
                    if (!PushMonitorRules.validPackage(pkg) || pkg == MCS_PACKAGE) return
                    queuePush(pkg)
                }
            })
        }.onFailure { warn() }
    }

    private fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        appContext = context.applicationContext
        runCatching {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action != Bridge.ACTION) return
                    val id = intent.getStringExtra("requestId") ?: ""
                    if (id.length <= 64) refresh(id)
                }
            }
            val filter = IntentFilter(Bridge.ACTION)
            ContextCompat.registerReceiver(context, receiver, filter, Bridge.PERMISSION, null, ContextCompat.RECEIVER_EXPORTED)
            refresh("")
        }.onFailure { warn() }
    }

    private fun refresh(id: String) {
        requestId.set(id)
        if (!refreshPending.compareAndSet(false, true)) return
        worker.execute {
            val currentRequest = requestId.get()
            try {
                val ctx = appContext ?: return@execute
                val (rows, complete) = readRegistrations(ctx)
                val boundedRows = PushMonitorRules.boundedRows(rows)
                ctx.contentResolver.call(Bridge.URI, "snapshot", null, Bundle().apply {
                    putString("rows", PushMonitorCodec.writeRows(boundedRows).toString())
                    putBoolean("complete", complete && boundedRows.size == rows.size)
                    putString("requestId", currentRequest)
                })
            } catch (_: Exception) { warn() }
            finally {
                refreshPending.set(false)
                if (requestId.get() != currentRequest) refresh(requestId.get())
            }
        }
    }

    private fun readRegistrations(context: Context): Pair<Map<String, Boolean>, Boolean> {
        val rows = linkedMapOf<String, Boolean>()
        var complete = true
        val userId = (Process.myUid() / 100_000).toString()
        val locations = listOf(context.createDeviceProtectedStorageContext(), context)
        for ((database, credential) in listOf(
            "com_heytap_mcs_app_service" to "registerID", "com_heytap_onepush_app_service" to "token",
        )) {
            val paths = locations.map { it.getDatabasePath(database) }.distinctBy { it.path }.filter { it.isFile }
            if (paths.isEmpty()) complete = false
            paths.forEach { path ->
                runCatching {
                    SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                        // Convert the stored credential to a boolean inside SQLite.
                        db.rawQuery("SELECT appPackage, CASE WHEN length($credential)>0 THEN 1 ELSE 0 END " +
                            "FROM app_register WHERE userId=? LIMIT ${PushMonitorRules.MAX_APPS + 1}", arrayOf(userId)).use { cursor ->
                            while (cursor.moveToNext()) {
                                val pkg = cursor.getString(0)
                                if (pkg == null || !PushMonitorRules.validPackage(pkg)) {
                                    complete = false
                                    continue
                                }
                                if (pkg !in rows && rows.size >= PushMonitorRules.MAX_APPS) {
                                    complete = false
                                    break
                                }
                                rows[pkg] = rows[pkg] == true || cursor.getInt(1) == 1
                            }
                            if (cursor.count > PushMonitorRules.MAX_APPS) complete = false
                        }
                    }
                }.onFailure { complete = false }
            }
        }
        return rows to complete
    }

    private fun queuePush(pkg: String) {
        synchronized(queuedPushes) {
            if (queuedPushes.size < PushMonitorRules.MAX_APPS) queuedPushes.add(pkg)
        }
        if (!pushPending.compareAndSet(false, true)) return
        worker.schedule({
            try {
                val packages = synchronized(queuedPushes) { queuedPushes.toList().also { queuedPushes.clear() } }
                val ctx = appContext ?: return@schedule
                if (packages.isNotEmpty()) {
                    ctx.contentResolver.call(Bridge.URI, "pushes", null, Bundle().apply {
                        putStringArrayList("packages", ArrayList(packages))
                    })
                }
            } catch (_: Exception) { warn() }
            finally {
                pushPending.set(false)
                // A notification may arrive while the previous batch is being saved.
                val next = synchronized(queuedPushes) { queuedPushes.firstOrNull() }
                if (next != null) queuePush(next)
            }
        }, 500, TimeUnit.MILLISECONDS)
    }

    private fun warn() {
        if (warned.compareAndSet(false, true)) HookLog.w(TAG, "MCS monitor interface unavailable")
    }
}
