package com.mi.onextbox.lsp

import android.content.Context
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.FutureTask
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal object NativeFiveGaBridge {
    private val worker by lazy { Handler(HandlerThread("native-5ga-bridge").apply { start() }.looper) }
    private val io = ThreadPoolExecutor(0, 2, 30, TimeUnit.SECONDS, SynchronousQueue(), { task ->
        Thread(task, "native-5ga-ipc").apply { isDaemon = true }
    })
    @Volatile private var publisher: Publisher? = null
    private var publisherSource: String? = null
    private val publisherRetired = AtomicBoolean(false)

    fun publish(context: Context, snapshot: NativeFiveGaSnapshot) {
        check(Process.myUid() == NativeFiveGaProtocol.PHONE_UID)
        worker.post {
            if (publisherRetired.get()) return@post
            if (publisherSource == null) publisherSource = snapshot.source
            if (publisherSource != snapshot.source) return@post
            var current = publisher
            if (current == null) {
                current = Publisher(context.applicationContext ?: context)
                publisher = current
            }
            current.update(snapshot)
        }
    }

    fun retirePublisher() {
        publisherRetired.set(true)
        publisher?.invalidate()
        worker.post { publisher?.close(); publisher = null }
    }

    fun subscribe(context: Context, listener: (NativeFiveGaSnapshot?) -> Unit): AutoCloseable {
        val consumer = Consumer(context.applicationContext ?: context, listener)
        worker.post(consumer.connect)
        return AutoCloseable { if (consumer.closed.compareAndSet(false, true)) worker.post { consumer.close() } }
    }

    private fun <T> bounded(action: () -> T): T {
        val future = FutureTask(action)
        io.execute(future)
        return try { future.get(1500, TimeUnit.MILLISECONDS) }
        catch (error: Exception) { future.cancel(false); throw error }
    }

    private fun call(context: Context, method: String, extras: Bundle): Bundle? = bounded {
        context.contentResolver.acquireUnstableContentProviderClient(NativeFiveGaProtocol.AUTHORITY)?.use {
            it.call(method, null, extras)
        }
    }

    private fun moduleUid(context: Context) = context.packageManager
        .getApplicationInfo(NativeFiveGaProtocol.PACKAGE, 0).uid

    private class Publisher(private val context: Context) : Binder() {
        @Volatile var snapshot: NativeFiveGaSnapshot? = null
            private set
        @Volatile private var retired = false
        private var discovery: IBinder? = null
        private val targets = linkedMapOf<IBinder, IBinder.DeathRecipient>()
        private val ownModuleUid = moduleUid(context)
        private val systemUiUid = NativeFiveGaProtocol.systemUiUid(context)
        private val incarnation = SystemClock.elapsedRealtimeNanos()
        private val discoveryDeath = IBinder.DeathRecipient {
            worker.post { discovery = null; if (!retired) worker.postDelayed(publish, 1000) }
        }
        private val publish: Runnable = object : Runnable {
            override fun run() {
                if (!retired && discovery == null) {
                    runCatching {
                        val response = call(context, "publish", NativeFiveGaProtocol.envelope().apply {
                            putBinder("endpoint", this@Publisher)
                            putLong("incarnation", incarnation)
                        })
                        check(NativeFiveGaProtocol.compatible(response))
                        check(response?.getBinder("endpoint") == this@Publisher)
                        requireNotNull(response?.getBinder("discovery")).also {
                            it.linkToDeath(discoveryDeath, 0)
                            discovery = it
                        }
                    }.onFailure { if (!retired) worker.postDelayed(this, 3000) }
                }
            }
        }

        fun update(value: NativeFiveGaSnapshot) {
            synchronized(this) {
                if (retired) return
                val previous = snapshot
                if (previous != null && value.sequence <= previous.sequence) return
                check(NativeFiveGaProtocol.decode(NativeFiveGaProtocol.encode(value)) != null)
                snapshot = value
            }
            if (discovery == null) worker.post(publish)
            notifyTargets(value)
        }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code !in NativeFiveGaProtocol.READ..NativeFiveGaProtocol.UNREGISTER) return super.onTransact(code, data, reply, flags)
            if (!NativeFiveGaProtocol.canRead(Binder.getCallingUid(), ownModuleUid, systemUiUid)) throw SecurityException("Private native network endpoint")
            data.enforceInterface(NativeFiveGaProtocol.DESCRIPTOR)
            val callback = if (code != NativeFiveGaProtocol.READ) data.readStrongBinder() else null
            data.enforceNoDataAvail()
            synchronized(targets) {
                if (code == NativeFiveGaProtocol.REGISTER && callback != null && callback !in targets && !retired) {
                    require(targets.size < 32)
                    val death = IBinder.DeathRecipient { remove(callback) }
                    callback.linkToDeath(death, 0)
                    targets[callback] = death
                } else if (code == NativeFiveGaProtocol.UNREGISTER && callback != null) remove(callback)
            }
            reply?.writeNoException()
            reply?.writeBundle(NativeFiveGaProtocol.encode(if (retired) null else snapshot))
            return true
        }

        private fun remove(callback: IBinder) = synchronized(targets) {
            targets.remove(callback)?.let { callback.unlinkToDeath(it, 0) }
            Unit
        }

        private fun notifyTargets(value: NativeFiveGaSnapshot?) {
            val callbacks = synchronized(targets) { targets.keys.toList() }
            callbacks.forEach { callback ->
                try {
                    bounded {
                        val data = Parcel.obtain()
                        try {
                            data.writeInterfaceToken(NativeFiveGaProtocol.DESCRIPTOR)
                            data.writeBundle(NativeFiveGaProtocol.encode(value))
                            check(callback.transact(NativeFiveGaProtocol.CHANGED, data, null, IBinder.FLAG_ONEWAY))
                        } finally { data.recycle() }
                    }
                } catch (_: Exception) { remove(callback) }
            }
        }

        fun close() {
            invalidate()
            worker.removeCallbacks(publish)
            notifyTargets(null)
            runCatching { call(context, "retire", NativeFiveGaProtocol.envelope().apply {
                putBinder("endpoint", this@Publisher)
                putLong("incarnation", incarnation)
            }) }
            discovery?.let { runCatching { it.unlinkToDeath(discoveryDeath, 0) } }
            discovery = null
            synchronized(targets) { targets.keys.toList() }.forEach(::remove)
        }

        @Synchronized fun invalidate() { retired = true; snapshot = null }
    }

    private class Consumer(private val context: Context, private val listener: (NativeFiveGaSnapshot?) -> Unit) {
        val closed = AtomicBoolean(false)
        private val stateLock = Any()
        private val ownModuleUid = moduleUid(context)
        private var endpoint: IBinder? = null
        private var discovery: IBinder? = null
        private var callback: IBinder? = null
        private var receipt = NativeFiveGaReceipt()
        private var connection = 0L
        private var endpointDeath: IBinder.DeathRecipient? = null
        private var discoveryDeath: IBinder.DeathRecipient? = null
        private val observer = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code != NativeFiveGaProtocol.INVALIDATED) return super.onTransact(code, data, reply, flags)
                if (Binder.getCallingUid() != ownModuleUid) throw SecurityException("Discovery UID required")
                data.enforceInterface(NativeFiveGaProtocol.DESCRIPTOR)
                val broker = data.readStrongBinder()
                data.enforceNoDataAvail()
                if (broker != null) invalidate(expectedDiscovery = broker)
                return true
            }
        }
        val connect = Runnable { connectNow() }
        private val health: Runnable = object : Runnable {
            override fun run() {
                if (closed.get()) return
                val remote = endpoint ?: return
                val receiver = callback ?: return
                val generation = synchronized(stateLock) { connection }
                // Re-register idempotently so a dropped callback recovers without restarting a scope.
                runCatching { accept(transact(remote, NativeFiveGaProtocol.REGISTER, receiver), generation, remote) }
                    .onFailure { invalidate(expectedEndpoint = remote) }
                if (!closed.get() && endpoint == remote) worker.postDelayed(this, 5000)
            }
        }

        private fun invalidate(expectedEndpoint: IBinder? = null, expectedDiscovery: IBinder? = null) {
            val invalidated = synchronized(stateLock) {
                if (closed.get() || expectedEndpoint != null && endpoint != expectedEndpoint ||
                    expectedDiscovery != null && discovery != null && discovery != expectedDiscovery) return
                ++connection
                runCatching { listener(null) }
                connection
            }
            worker.post {
                if (synchronized(stateLock) { !closed.get() && connection == invalidated }) {
                    disconnect()
                    worker.removeCallbacks(connect)
                    worker.postDelayed(connect, 500)
                }
            }
        }

        private fun connectNow() {
            val ticket = synchronized(stateLock) {
                if (closed.get()) return
                connection
            }
            runCatching {
                val response = requireNotNull(call(context, "read", NativeFiveGaProtocol.envelope().apply { putBinder("observer", observer) }))
                check(NativeFiveGaProtocol.compatible(response))
                val broker = requireNotNull(response.getBinder("discovery"))
                val remote = response.getBinder("endpoint")
                val generation = synchronized(stateLock) {
                    if (closed.get() || ticket != connection) return@runCatching
                    if (discovery != broker) {
                        discoveryDeath?.let { previous -> discovery?.let { runCatching { it.unlinkToDeath(previous, 0) } } }
                        val brokerDeath = IBinder.DeathRecipient {
                            worker.post {
                                if (discovery == broker) {
                                    discovery = null
                                    discoveryDeath = null
                                    // Package replacement kills discovery, not the phone-owned endpoint.
                                    // Its authenticated updates and health checks remain authoritative.
                                    if (endpoint?.isBinderAlive != true) invalidate()
                                    else {
                                        worker.removeCallbacks(connect)
                                        worker.postDelayed(connect, 500)
                                    }
                                }
                            }
                        }
                        discovery = broker
                        discoveryDeath = brokerDeath
                        broker.linkToDeath(brokerDeath, 0)
                    }
                    if (remote == null) {
                        // Broker replacement can precede the live phone endpoint's re-publication.
                        if (endpoint != null) {
                            worker.removeCallbacks(connect)
                            worker.postDelayed(connect, 1000)
                        }
                        return@runCatching
                    }
                    if (remote == endpoint && callback != null) return@runCatching
                    if (endpoint != null) {
                        invalidate(expectedEndpoint = endpoint)
                        return@runCatching
                    }
                    val remoteDeath = IBinder.DeathRecipient { invalidate(expectedEndpoint = remote) }
                    endpoint = remote
                    endpointDeath = remoteDeath
                    remote.linkToDeath(remoteDeath, 0)
                    receipt = NativeFiveGaReceipt()
                    ++connection
                }
                requireNotNull(remote)
                val receiver = object : Binder() {
                    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                        if (code != NativeFiveGaProtocol.CHANGED) return super.onTransact(code, data, reply, flags)
                        if (Binder.getCallingUid() != NativeFiveGaProtocol.PHONE_UID) throw SecurityException("Phone UID required")
                        data.enforceInterface(NativeFiveGaProtocol.DESCRIPTOR)
                        val value = NativeFiveGaProtocol.decode(data.readBundle(Bundle::class.java.classLoader))
                        data.enforceNoDataAvail()
                        worker.post { accept(value, generation, remote) }
                        return true
                    }
                }
                callback = receiver
                accept(transact(remote, NativeFiveGaProtocol.REGISTER, receiver), generation, remote)
                worker.removeCallbacks(health)
                worker.postDelayed(health, 5000)
            }.onFailure { disconnect() }
            if (endpoint == null && !closed.get()) worker.postDelayed(connect, 3000)
        }

        private fun accept(value: NativeFiveGaSnapshot?, generation: Long, remote: IBinder) = synchronized(stateLock) {
            if (closed.get() || generation != connection || endpoint != remote) return@synchronized
            when (receipt.receive(value?.source, value?.sequence ?: -1)) {
                NativeFiveGaReceipt.Result.ACCEPT -> runCatching { listener(value) }
                NativeFiveGaReceipt.Result.IGNORE -> Unit
                NativeFiveGaReceipt.Result.INVALID -> {
                    runCatching { listener(null) }
                    invalidate()
                }
            }
        }

        private fun disconnect() {
            worker.removeCallbacks(health)
            val (remote, receiver, previousDeath) = synchronized(stateLock) {
                ++connection
                val result = Triple(endpoint, callback, endpointDeath)
                endpoint = null
                callback = null
                endpointDeath = null
                runCatching { listener(null) }
                result
            }
            if (remote != null) {
                previousDeath?.let { runCatching { remote.unlinkToDeath(it, 0) } }
                if (receiver != null) runCatching { transact(remote, NativeFiveGaProtocol.UNREGISTER, receiver) }
            }
        }

        fun close() {
            worker.removeCallbacks(connect)
            disconnect()
            discoveryDeath?.let { previous -> discovery?.let { runCatching { it.unlinkToDeath(previous, 0) } } }
            discovery = null
            discoveryDeath = null
            runCatching { call(context, "unwatch", NativeFiveGaProtocol.envelope().apply { putBinder("observer", observer) }) }
        }
    }

    private fun transact(remote: IBinder, code: Int, callback: IBinder? = null): NativeFiveGaSnapshot? = bounded {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(NativeFiveGaProtocol.DESCRIPTOR)
            if (code != NativeFiveGaProtocol.READ) data.writeStrongBinder(callback)
            check(remote.transact(code, data, reply, 0))
            reply.readException()
            NativeFiveGaProtocol.decode(reply.readBundle(Bundle::class.java.classLoader))
        } finally { data.recycle(); reply.recycle() }
    }
}
