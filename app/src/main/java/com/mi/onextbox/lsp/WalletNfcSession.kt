package com.mi.onextbox.lsp

import android.app.Activity
import android.content.ContentProviderClient
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import com.mi.onextbox.BuildConfig
import com.topjohnwu.superuser.Shell
import java.util.IdentityHashMap
import java.util.concurrent.Executors

internal object WalletNfcSession {
    private val io = Executors.newSingleThreadExecutor()
    private val sessions = IdentityHashMap<Activity, IBinder>() // activity callbacks run on main
    private val clients = mutableMapOf<IBinder, ContentProviderClient>() // io thread only

    fun enter(activity: Activity) {
        if (sessions.containsKey(activity)) return
        val token = Binder()
        sessions[activity] = token
        val resolver = activity.applicationContext.contentResolver
        io.execute {
            runCatching {
                val client = resolver.acquireContentProviderClient(BuildConfig.APPLICATION_ID + ArtworkTarget.WALLET.authority)
                    ?: return@execute
                try {
                    client.call("enterSwipe", null, Bundle().apply { putBinder("session", token) })
                    clients[token] = client
                } finally {
                    if (token !in clients) client.close()
                }
            }.onFailure { HookLog.w("ONextBox-LSP", "Wallet NFC session unavailable", it) }
        }
    }

    fun leave(activity: Activity) {
        val token = sessions.remove(activity) ?: return
        io.execute {
            clients.remove(token)?.use { client ->
                runCatching { client.call("leaveSwipe", null, Bundle().apply { putBinder("session", token) }) }
            }
        }
    }
}

/** Runs in ONextBox. A wallet process that dies without onStop releases its lease through Binder. */
internal object WalletNfcLease {
    private val owners = mutableMapOf<IBinder, IBinder.DeathRecipient>()
    private var enabledHere = false

    @Synchronized fun enter(token: IBinder) {
        if (token in owners) return
        val death = IBinder.DeathRecipient { leave(token) }
        token.linkToDeath(death, 0)
        owners[token] = death
        if (owners.size == 1 && !enabledHere) {
            val status = Shell.cmd("cmd nfc status").exec()
            if (status.isSuccess && status.out.any { it.trim().equals("Nfc is disabled", ignoreCase = true) }) {
                enabledHere = Shell.cmd("cmd nfc enable-nfc").exec().isSuccess
            }
        }
    }

    @Synchronized fun leave(token: IBinder) {
        val recipient = owners.remove(token) ?: return
        runCatching { token.unlinkToDeath(recipient, 0) }
        if (owners.isEmpty() && enabledHere) {
            enabledHere = !Shell.cmd("cmd nfc disable-nfc").exec().isSuccess
        }
    }
}
