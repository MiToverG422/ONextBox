package com.mi.onextbox.lsp

import android.content.Context
import android.os.Process
import android.provider.Settings
import com.mi.onextbox.ui.common.ShellLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.UUID

/** One-shot root request; the official system controller updates both memory and disk. */
internal object PermissionStartAllowList {
    const val REQUEST_KEY = "oost_permission_start_allow_clear_request"
    const val RESULT_KEY = "oost_permission_start_allow_clear_result"

    suspend fun clear(context: Context, allUsers: Boolean): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val ids = if (allUsers) {
                val result = ShellLogger.exec("Permission Manager users", "pm list users")
                check(result.isSuccess)
                result.out.mapNotNull { Regex("UserInfo\\{(\\d+):").find(it)?.groupValues?.get(1)?.toInt() }
                    .distinct().also { check(it.isNotEmpty()) }
            } else listOf(Process.myUid() / 100_000)
            val id = UUID.randomUUID().toString()
            val request = "$id|${ids.joinToString(",")}" // validated numeric IDs and generated UUID only
            val result = ShellLogger.exec("Permission Manager clear allow records",
                "settings put global $REQUEST_KEY '$request'")
            check(result.isSuccess)
            repeat(24) {
                delay(150L)
                val reply = Settings.Global.getString(context.contentResolver, RESULT_KEY)
                if (reply == "$id:ok") return@withContext true
                if (reply == "$id:failed") return@withContext false
            }
            false
        }.getOrDefault(false)
    }
}
