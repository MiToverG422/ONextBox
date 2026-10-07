package com.mi.onextbox.push

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

internal data class PushMonitorView(val snapshot: PushSnapshot, val apps: List<PushApp>, val mcsInstalled: Boolean)

internal object PushMonitorRepository {
    val serviceActions = listOf(
        "com.heytap.mcs.action.RECEIVE_MCS_MESSAGE",
        "com.heytap.msp.push.RECEIVE_MCS_MESSAGE",
        "com.coloros.mcs.action.RECEIVE_MCS_MESSAGE",
    )

    fun requestRefresh(context: Context, requestId: String) {
        context.sendBroadcast(Intent(PushMonitorProvider.ACTION).setPackage(MCS_PACKAGE).putExtra("requestId", requestId))
    }

    fun read(context: Context): PushMonitorView {
        val data = context.contentResolver.call(PushMonitorProvider.URI, "read", null, null)
            ?: error("Monitor storage unavailable")
        val snapshot = PushMonitorCodec.readSnapshot(data.getString("snapshot"), data.getString("times"))
        val pm = context.packageManager
        val installed = runCatching { pm.getApplicationInfo(MCS_PACKAGE, 0) }.isSuccess
        val supported = serviceActions.flatMap { action ->
            pm.queryIntentServices(Intent(action), PackageManager.MATCH_DISABLED_COMPONENTS)
        }.map { it.serviceInfo.packageName }.filter(PushMonitorRules::validPackage).toSet()
        val packages = supported + snapshot.rows.keys + snapshot.times.keys + if (installed) setOf(MCS_PACKAGE) else emptySet()
        val now = System.currentTimeMillis()
        val times = PushMonitorRules.recordPushes(snapshot.times, emptyList(), now)
        val apps = packages.map { pkg ->
            val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
            PushApp(pkg, label, PushMonitorRules.registration(pkg, snapshot), times[pkg] ?: 0)
        }
        return PushMonitorView(snapshot, apps, installed)
    }
}
