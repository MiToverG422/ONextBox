package com.mi.onextbox.push

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import org.json.JSONObject

/** Private reads and sanitized updates from the MCS UID. */
class PushMonitorProvider : ContentProvider() {
    override fun onCreate() = true

    @Synchronized
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val ctx = requireNotNull(context)
        val caller = Binder.getCallingUid()
        val prefs = ctx.createDeviceProtectedStorageContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (method == "read" || method == "clear") {
            if (!PushMonitorRules.canRead(caller, Process.myUid())) throw SecurityException("Private monitor operation")
            if (method == "clear") {
                check(prefs.edit().clear().commit())
                return Bundle.EMPTY
            }
            return Bundle().apply {
                putString("snapshot", prefs.getString("snapshot", null))
                putString("times", prefs.getString("times", null))
            }
        }
        val mcsUid = runCatching { ctx.packageManager.getApplicationInfo(MCS_PACKAGE, 0).uid }.getOrNull()
        if (!PushMonitorRules.canWrite(caller, Process.myUid(), mcsUid)) throw SecurityException("MCS UID required")
        require(method == "snapshot" || method == "pushes") { "Unknown monitor operation" }
        when (method) {
            "snapshot" -> {
                val payload = requireNotNull(extras)
                val inputRows = PushMonitorCodec.readRows(requireNotNull(payload.getString("rows")))
                val rows = PushMonitorRules.boundedRows(inputRows)
                val requestId = payload.getString("requestId", "")
                require(requestId.length <= 64)
                val snapshot = JSONObject().put("time", System.currentTimeMillis())
                    .put("requestId", requestId).put("complete", payload.getBoolean("complete", false) && rows.size == inputRows.size)
                    .put("rows", PushMonitorCodec.writeRows(rows))
                check(prefs.edit().putString("snapshot", snapshot.toString()).commit())
            }
            "pushes" -> {
                val packages = requireNotNull(extras?.getStringArrayList("packages"))
                val times = PushMonitorRules.recordPushes(
                    PushMonitorCodec.readTimes(prefs.getString("times", null)), packages, System.currentTimeMillis(),
                )
                check(prefs.edit().putString("times", JSONObject(times).toString()).commit())
            }
        }
        return Bundle.EMPTY
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        const val AUTHORITY = "com.mi.onextbox.pushmonitor"
        const val ACTION = "com.mi.onextbox.REFRESH_PUSH_MONITOR"
        const val PERMISSION = "com.mi.onextbox.permission.PUSH_MONITOR"
        const val PREFS_NAME = "push_monitor"
        val URI: Uri = Uri.parse("content://$AUTHORITY")
    }
}
