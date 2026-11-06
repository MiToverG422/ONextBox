package com.mi.onextbox.lsp

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import android.os.SystemClock

class NativeFiveGaProvider : ContentProvider() {
    private val lock = Any()
    private val token = Binder()
    private val systemUiUid by lazy { NativeFiveGaProtocol.systemUiUid(requireNotNull(context)) }
    private var endpoint: IBinder? = null
    private var endpointDeath: IBinder.DeathRecipient? = null
    private var incarnation = -1L
    private var incarnationRetired = false
    private val observers = linkedMapOf<IBinder, IBinder.DeathRecipient>()
    private val worker by lazy {
        Handler(HandlerThread("native-5ga-discovery").apply { start() }.looper)
    }

    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val caller = Binder.getCallingUid()
        val ownUid = Process.myUid()
        val writer = caller == NativeFiveGaProtocol.PHONE_UID
        if (method == "publish" || method == "retire") {
            if (!writer) throw SecurityException("Phone UID required")
        } else if (!writer && !NativeFiveGaProtocol.canRead(caller, ownUid, systemUiUid)) {
            throw SecurityException("System or module UID required")
        }
        require(NativeFiveGaProtocol.compatible(extras)) { "Incompatible native network session" }
        synchronized(lock) {
            when (method) {
                "publish" -> {
                    val next = requireNotNull(extras?.getBinder("endpoint"))
                    val born = extras.getLong("incarnation", -1)
                    require(born in 0..SystemClock.elapsedRealtimeNanos())
                    require(next.isBinderAlive)
                    if (born > incarnation || born == incarnation && !incarnationRetired && next == endpoint) {
                        val death = IBinder.DeathRecipient { clearEndpoint(next) }
                        next.linkToDeath(death, 0)
                        endpointDeath?.let { old -> endpoint?.unlinkToDeath(old, 0) }
                        endpoint = next
                        endpointDeath = death
                        incarnation = born
                        incarnationRetired = false
                        notifyObservers()
                    }
                }
                "retire" -> {
                    val born = extras?.getLong("incarnation", -1) ?: -1
                    require(born in 0..SystemClock.elapsedRealtimeNanos())
                    if (born >= incarnation) {
                        incarnation = born
                        incarnationRetired = true
                        clearEndpoint(endpoint)
                    }
                }
                "read" -> extras?.getBinder("observer")?.let { observer ->
                    if (observer !in observers) {
                        require(observers.size < 32) { "Too many observers" }
                        val death = IBinder.DeathRecipient { removeObserver(observer) }
                        observer.linkToDeath(death, 0)
                        observers[observer] = death
                    }
                }
                "unwatch" -> extras?.getBinder("observer")?.let(::removeObserver)
                else -> throw IllegalArgumentException("Unknown discovery operation")
            }
            return NativeFiveGaProtocol.response(extras).apply {
                putBinder("discovery", token)
                putBinder("endpoint", endpoint)
            }
        }
    }

    private fun clearEndpoint(expected: IBinder?) = synchronized(lock) {
        if (expected != null && endpoint == expected) {
            endpointDeath?.let { endpoint?.unlinkToDeath(it, 0) }
            endpoint = null
            endpointDeath = null
            notifyObservers()
        }
    }

    private fun removeObserver(observer: IBinder) = synchronized(lock) {
        observers.remove(observer)?.let { observer.unlinkToDeath(it, 0) }
        Unit
    }

    private fun notifyObservers() {
        val targets = observers.keys.toList()
        worker.post {
            targets.forEach { observer ->
                val parcel = Parcel.obtain()
                try {
                    parcel.writeInterfaceToken(NativeFiveGaProtocol.DESCRIPTOR)
                    parcel.writeStrongBinder(token)
                    observer.transact(NativeFiveGaProtocol.INVALIDATED, parcel, null, IBinder.FLAG_ONEWAY)
                } catch (_: Exception) {
                    removeObserver(observer)
                } finally {
                    parcel.recycle()
                }
            }
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
