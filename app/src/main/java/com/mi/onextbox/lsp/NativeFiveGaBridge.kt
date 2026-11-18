package com.mi.onextbox.lsp

import android.content.ContentProviderClient
import android.content.Context
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import android.os.SystemClock
import java.util.concurrent.FutureTask
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal object NativeFiveGaBridge {
    private val worker by lazy { Handler(HandlerThread("native-5ga-bridge").apply { start() }.looper) }
    private val io = ThreadPoolExecutor(0, 2, 30, TimeUnit.SECONDS, SynchronousQueue(), { task ->
        Thread(task, "native-5ga-ipc").apply { isDaemon = true }
    })
    // Cleanup cannot queue behind discovery/notification calls. Reservations bound both live
    // provider leases and queued cleanup work; exhausted capacity stops further acquisition.
    private val cleanup by lazy {
        val executor = ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS,
            ArrayBlockingQueue<Runnable>(NativeFiveGaCleanup.CAPACITY), { task ->
                Thread(task, "native-5ga-cleanup").apply { isDaemon = true }
            }).apply { allowCoreThreadTimeOut(true) }
        NativeFiveGaCleanup(executor)
    }
    @Volatile private var publisher: Publisher? = null
    private var publisherSource: String? = null
    private val publisherRetired = AtomicBoolean(false)

    fun publish(context: Context, snapshot: NativeFiveGaSnapshot, enabled: () -> Boolean) {
        check(Process.myUid() == NativeFiveGaProtocol.PHONE_UID)
        if (!enabled()) { pausePublisher(); return }
        worker.post {
            if (publisherRetired.get() || !enabled()) return@post
            if (publisherSource == null) publisherSource = snapshot.source
            if (publisherSource != snapshot.source) return@post
            runCatching {
                val current = publisher ?: Publisher(context.applicationContext ?: context, enabled).also { publisher = it }
                current.update(snapshot)
            }.onFailure {
                retirePublisher()
                com.mi.onextbox.diagnostics.RuntimeDiagnostics.event("failure", "native_5ga:publisher",
                    "Publication paused: ${it.javaClass.simpleName}")
            }
        }
    }

    /** Pause is reversible; retiring an old module generation is permanent. Neither starts a provider. */
    fun pausePublisher() {
        val previous = publisher ?: return
        previous.invalidate()
        worker.post {
            if (publisher === previous) { publisher = null; publisherSource = null }
            previous.close()
        }
    }

    fun retirePublisher() {
        publisherRetired.set(true)
        pausePublisher()
    }

    fun subscribe(context: Context, enabled: () -> Boolean, listener: (NativeFiveGaSnapshot?) -> Unit): AutoCloseable {
        if (!enabled()) {
            runCatching { listener(null) }
            return AutoCloseable {}
        }
        val consumer = Consumer(context.applicationContext ?: context, enabled, listener)
        worker.post { consumer.start() }
        return AutoCloseable { if (consumer.closed.compareAndSet(false, true)) worker.post { consumer.close() } }
    }

    private fun <T> bounded(action: () -> T): T {
        val future = FutureTask(action)
        io.execute(future)
        return try { future.get(1500, TimeUnit.MILLISECONDS) }
        catch (error: Exception) { future.cancel(false); throw error }
    }

    /** Keep the already-acquired client so cleanup cannot cold-start the module again. */
    private class Discovery(
        private val client: ContentProviderClient,
        private val cleanupMethod: String,
        private val cleanupExtras: Bundle,
        private val reservation: NativeFiveGaCleanup.Reservation,
    ) : AutoCloseable {
        var response: Bundle? = null
        private val closed = AtomicBoolean(false)

        fun read(method: String, extras: Bundle) { response = client.call(method, null, extras) }

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            try {
                reservation.finish {
                    try { runCatching { client.call(cleanupMethod, null, cleanupExtras) }.onFailure(::cleanupFailure) }
                    finally { runCatching { client.close() }.onFailure(::cleanupFailure) }
                }
            } catch (error: Exception) { cleanupFailure(error); runCatching { client.close() } }
        }
    }

    private fun discover(context: Context, method: String, extras: Bundle, enabled: () -> Boolean): Discovery {
        val lease = NativeFiveGaIpcLease<Discovery>()
        return try {
            bounded {
                check(enabled()) { "Native network bridge is inactive" }
                val reservation = cleanup.reserve()
                val client = try { requireNotNull(context.contentResolver
                    .acquireUnstableContentProviderClient(NativeFiveGaProtocol.AUTHORITY)) }
                catch (error: Exception) { reservation.cancel(); throw error }
                val next = Discovery(client, if (method == "publish") "retire" else "unwatch", Bundle(extras), reservation)
                try { next.read(method, extras); lease.offer(next) }
                catch (error: Exception) { next.close(); throw error }
            }
            requireNotNull(lease.take())
        } catch (error: Exception) { lease.abandon(); throw error }
    }

    private fun cleanupFailure(error: Throwable) {
        com.mi.onextbox.diagnostics.RuntimeDiagnostics.event("failure", "native_5ga:cleanup",
            "Registration cleanup failed: ${error.javaClass.simpleName}")
    }

    private fun retry(enabled: () -> Boolean, role: String, attempt: () -> Boolean) = NativeFiveGaConnectionRetry(
        enabled = enabled,
        post = { task, delay -> worker.postDelayed(task, delay) },
        remove = { worker.removeCallbacks(it) },
        attempt = attempt,
        suspended = {
            com.mi.onextbox.diagnostics.RuntimeDiagnostics.event("bridge", "native_5ga:$role",
                "Connection suspended after ${NativeFiveGaConnectionRetry.MAX_ATTEMPTS} failed attempts")
        },
    )

    private fun moduleUid(context: Context) = context.packageManager
        .getApplicationInfo(NativeFiveGaProtocol.PACKAGE, 0).uid

    private class Publisher(private val context: Context, private val enabled: () -> Boolean) : Binder() {
        @Volatile var snapshot: NativeFiveGaSnapshot? = null
            private set
        @Volatile private var retired = false
        private var released = false
        private var discovery: IBinder? = null
        private var discoveryClient: Discovery? = null
        private val targets = linkedMapOf<IBinder, IBinder.DeathRecipient>()
        private val ownModuleUid by lazy { moduleUid(context) }
        private val systemUiUid by lazy { NativeFiveGaProtocol.systemUiUid(context) }
        private fun active() = !retired && enabled()
        private val connection = retry(::active, "publisher", ::connect)
        private val discoveryDeath = IBinder.DeathRecipient {
            worker.post {
                discoveryClient?.close(); discoveryClient = null; discovery = null
                if (active()) connection.reconnect(1000)
            }
        }

        private fun connect(): Boolean {
            if (!active()) return false
            require(ownModuleUid >= 0) { "Module UID unavailable" }
            // A timed-out registration may already have been retired by its late cleanup.
            val incarnation = SystemClock.elapsedRealtimeNanos()
            val next = discover(context, "publish", NativeFiveGaProtocol.envelope().apply {
                putBinder("endpoint", this@Publisher)
                putLong("incarnation", incarnation)
            }, ::active)
            return try {
                check(active())
                check(NativeFiveGaProtocol.compatible(next.response))
                check(next.response?.getBinder("endpoint") == this@Publisher)
                val broker = requireNotNull(next.response?.getBinder("discovery"))
                broker.linkToDeath(discoveryDeath, 0)
                discoveryClient = next
                discovery = broker
                true
            } catch (error: Exception) { next.close(); throw error }
        }

        fun update(value: NativeFiveGaSnapshot) {
            synchronized(this) {
                if (!active()) return
                val previous = snapshot
                if (previous != null && value.sequence <= previous.sequence) return
                check(NativeFiveGaProtocol.decode(NativeFiveGaProtocol.encode(value)) != null)
                snapshot = value
            }
            if (discovery == null) connection.request()
            notifyTargets(value)
        }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code !in NativeFiveGaProtocol.READ..NativeFiveGaProtocol.UNREGISTER) return super.onTransact(code, data, reply, flags)
            if (!NativeFiveGaProtocol.canRead(Binder.getCallingUid(), ownModuleUid, systemUiUid)) throw SecurityException("Private native network endpoint")
            data.enforceInterface(NativeFiveGaProtocol.DESCRIPTOR)
            val callback = if (code != NativeFiveGaProtocol.READ) data.readStrongBinder() else null
            data.enforceNoDataAvail()
            synchronized(targets) {
                if (code == NativeFiveGaProtocol.REGISTER && callback != null && callback !in targets && active()) {
                    require(targets.size < 32)
                    val death = IBinder.DeathRecipient { remove(callback) }
                    callback.linkToDeath(death, 0)
                    targets[callback] = death
                } else if (code == NativeFiveGaProtocol.UNREGISTER && callback != null) remove(callback)
            }
            reply?.writeNoException()
            reply?.writeBundle(NativeFiveGaProtocol.encode(if (active()) snapshot else null))
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
            if (released) return
            released = true
            invalidate()
            connection.close()
            notifyTargets(null)
            discovery?.let { runCatching { it.unlinkToDeath(discoveryDeath, 0) } }
            discovery = null
            discoveryClient?.close(); discoveryClient = null
            synchronized(targets) { targets.keys.toList() }.forEach(::remove)
        }

        @Synchronized fun invalidate() { retired = true; snapshot = null }
    }

    private class Consumer(
        private val context: Context,
        private val enabled: () -> Boolean,
        private val listener: (NativeFiveGaSnapshot?) -> Unit,
    ) {
        val closed = AtomicBoolean(false)
        private var released = false
        private val stateLock = Any()
        private val ownModuleUid by lazy { moduleUid(context) }
        private var endpoint: IBinder? = null
        @Volatile private var discovery: IBinder? = null
        private var discoveryClient: Discovery? = null
        private var discoveryObserver: IBinder? = null
        private var callback: IBinder? = null
        private var receipt = NativeFiveGaReceipt()
        private var generation = 0L
        private var endpointDeath: IBinder.DeathRecipient? = null
        private var discoveryDeath: IBinder.DeathRecipient? = null
        private fun active() = !closed.get() && enabled()
        private val connection = retry(::active, "consumer", ::connect)
        private val health: Runnable = object : Runnable {
            override fun run() {
                if (!active()) { close(); return }
                val remote = endpoint ?: return
                val receiver = callback ?: return
                val ticket = synchronized(stateLock) { generation }
                runCatching { accept(transact(remote, NativeFiveGaProtocol.REGISTER, receiver), ticket, remote) }
                    .onFailure { invalidate(expectedEndpoint = remote) }
                if (active() && endpoint == remote) worker.postDelayed(this, 5000)
            }
        }

        fun start() { if (active()) connection.request() else close() }

        private fun invalidate(expectedEndpoint: IBinder? = null, expectedDiscovery: IBinder? = null) {
            val ticket = synchronized(stateLock) {
                if (closed.get() || expectedEndpoint != null && endpoint != expectedEndpoint ||
                    expectedDiscovery != null && discovery != expectedDiscovery) return
                ++generation
                runCatching { listener(null) }
                generation
            }
            worker.post {
                if (synchronized(stateLock) { !closed.get() && generation == ticket }) {
                    disconnect()
                    dropDiscovery()
                    if (active()) connection.reconnect(500) else close()
                }
            }
        }

        private fun connect(): Boolean {
            if (!active()) { close(); return false }
            require(ownModuleUid >= 0) { "Module UID unavailable" }
            val observer = object : Binder() {
                override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                    if (code != NativeFiveGaProtocol.INVALIDATED) return super.onTransact(code, data, reply, flags)
                    if (Binder.getCallingUid() != ownModuleUid) throw SecurityException("Discovery UID required")
                    data.enforceInterface(NativeFiveGaProtocol.DESCRIPTOR)
                    val broker = data.readStrongBinder()
                    data.enforceNoDataAvail()
                    worker.post { if (discoveryObserver === this && broker != null) invalidate(expectedDiscovery = broker) }
                    return true
                }
            }
            val next = discover(context, "read", NativeFiveGaProtocol.envelope().apply { putBinder("observer", observer) }, ::active)
            return try {
                check(active())
                val response = requireNotNull(next.response)
                check(NativeFiveGaProtocol.compatible(response))
                val broker = requireNotNull(response.getBinder("discovery"))
                val brokerDeath = IBinder.DeathRecipient { invalidate(expectedDiscovery = broker) }
                broker.linkToDeath(brokerDeath, 0)
                discovery = broker; discoveryDeath = brokerDeath
                discoveryClient = next; discoveryObserver = observer
                val remote = response.getBinder("endpoint")
                // The registered observer will notify us when the phone publishes. No polling is needed.
                if (remote == null) return true
                val remoteDeath = IBinder.DeathRecipient { invalidate(expectedEndpoint = remote) }
                remote.linkToDeath(remoteDeath, 0)
                val ticket = synchronized(stateLock) {
                    endpoint = remote; endpointDeath = remoteDeath
                    receipt = NativeFiveGaReceipt()
                    ++generation
                }
                val receiver = object : Binder() {
                    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                        if (code != NativeFiveGaProtocol.CHANGED) return super.onTransact(code, data, reply, flags)
                        if (Binder.getCallingUid() != NativeFiveGaProtocol.PHONE_UID) throw SecurityException("Phone UID required")
                        data.enforceInterface(NativeFiveGaProtocol.DESCRIPTOR)
                        val value = NativeFiveGaProtocol.decode(data.readBundle(Bundle::class.java.classLoader))
                        data.enforceNoDataAvail()
                        worker.post { accept(value, ticket, remote) }
                        return true
                    }
                }
                callback = receiver
                check(accept(transact(remote, NativeFiveGaProtocol.REGISTER, receiver), ticket, remote, false))
                worker.removeCallbacks(health)
                worker.postDelayed(health, 5000)
                true
            } catch (error: Exception) {
                disconnect(); dropDiscovery(); next.close()
                throw error
            }
        }

        private fun accept(value: NativeFiveGaSnapshot?, ticket: Long, remote: IBinder, reconnectInvalid: Boolean = true): Boolean = synchronized(stateLock) {
            if (!active() || ticket != generation || endpoint != remote) return@synchronized false
            when (receipt.receive(value?.source, value?.sequence ?: -1)) {
                NativeFiveGaReceipt.Result.ACCEPT -> { runCatching { listener(value) }; true }
                NativeFiveGaReceipt.Result.IGNORE -> true
                NativeFiveGaReceipt.Result.INVALID -> {
                    runCatching { listener(null) }
                    if (reconnectInvalid) invalidate(expectedEndpoint = remote)
                    false
                }
            }
        }

        private fun disconnect() {
            worker.removeCallbacks(health)
            val (remote, receiver, previousDeath) = synchronized(stateLock) {
                ++generation
                val result = Triple(endpoint, callback, endpointDeath)
                endpoint = null; callback = null; endpointDeath = null
                runCatching { listener(null) }
                result
            }
            if (remote != null) {
                previousDeath?.let { runCatching { remote.unlinkToDeath(it, 0) } }
                if (receiver != null) runCatching { transact(remote, NativeFiveGaProtocol.UNREGISTER, receiver) }
            }
        }

        private fun dropDiscovery() {
            discoveryDeath?.let { previous -> discovery?.let { runCatching { it.unlinkToDeath(previous, 0) } } }
            discovery = null; discoveryDeath = null; discoveryObserver = null
            discoveryClient?.close(); discoveryClient = null
        }

        fun close() {
            closed.set(true)
            if (released) return
            released = true
            connection.close()
            disconnect()
            dropDiscovery()
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
