package com.mi.onextbox.lsp

import android.content.ComponentName
import android.content.Context
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.IInterface
import android.os.Looper
import android.os.Parcel
import android.os.SystemClock
import android.provider.Settings
import com.mi.onextbox.lsp.compat.ModernReflect
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Watches the native show callback without replacing a successful assistant invocation. */
internal object NativeAssistantSessionRecovery {
    private const val TAG = "ONextBox-Assistant"
    private const val GOOGLE_PACKAGE = "com.google.android.googlequicksearchbox"
    private const val CALLBACK_DESCRIPTOR =
        "com.android.internal.app.IVoiceInteractionSessionShowCallback"
    private const val SHOW_TIMEOUT_MS = 5_000L
    private const val BIND_TIMEOUT_MS = 2_000L
    private const val BIND_POLL_MS = 100L
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val tasks by lazy {
        NativeAssistantReloadTasks(
            dispatch = { task, delay -> handler.postDelayed(task, delay) },
            cancelDispatch = handler::removeCallbacks,
            runWhenActive = { action, deferred ->
                HotReloadLifecycle.withHook(fallback = deferred, action = action)
            },
        )
    }
    private val replaying = ThreadLocal<Boolean>()
    private var pending: ShowAttempt? = null
    private val outstandingCallbacks = AtomicInteger()

    fun registerHotReload() {
        HotReloadLifecycle.register("native-assistant-session",
            // The callback can reside in another process; do not pretend cancelling a timer revokes it.
            canReload = { pending == null && outstandingCallbacks.get() == 0 && tasks.isIdle() },
            detach = {
                check(pending == null && outstandingCallbacks.get() == 0 && tasks.isIdle()) { "Assistant show callback is still active" }
                handler.removeCallbacksAndMessages(null)
            },
            resumeAfterRejection = { tasks.resumeDeferred() },
        )
    }

    fun prepare(stub: Any, method: Method, args: Array<Any?>) {
        if (HotReloadLifecycle.isRetiring || replaying.get() == true) return
        cancel()
        if (args.size != 5 || args[3] != null) return
        val bundle = args[0] as? Bundle ?: return
        val invocationType = bundle.getInt("invocation_type", 0)
        if (!enabled(invocationType)) return
        val component = currentComponent(stub) ?: return
        if (component.packageName != GOOGLE_PACKAGE) return
        val userId = ModernReflect.getObjectField(stub, "mCurUser") as? Int ?: return
        val savedArgs = args.copyOf().also { it[0] = Bundle(bundle) }
        val attempt = ShowAttempt(stub, method, savedArgs, component, userId, invocationType, 0)
        args[3] = attempt.callback
        tasks.post { arm(attempt) }
    }

    fun finishRequest(callback: Any?, accepted: Boolean, error: Throwable?) {
        if (HotReloadLifecycle.isRetiring) return
        tasks.post {
            if (HotReloadLifecycle.isRetiring) return@post
            val attempt = pending ?: return@post
            if (attempt.callback !== callback) return@post
            if (error != null) {
                attempt.releaseCallback()
                attempt.cancel()
                pending = null
            } else if (!accepted) {
                attempt.releaseCallback()
                attempt.complete(shown = false)
            }
        }
    }

    fun prepareCornerSession(impl: Any, bundle: Bundle?) {
        if (HotReloadLifecycle.isRetiring) return
        if (bundle?.getInt("invocation_type", 0) != 1 || !enabled(1)) return
        val component = ModernReflect.getObjectField(impl, "mComponent") as? ComponentName ?: return
        if (component.packageName != GOOGLE_PACKAGE) return
        val stub = ModernReflect.getObjectField(impl, "mServiceStub") ?: return
        synchronized(stub) {
            val session = ModernReflect.getObjectField(impl, "mActiveSession") ?: return
            val token = ModernReflect.getObjectField(session, "mToken") as? IBinder ?: return
            // A new corner invocation must not reuse Google's unresponsive cached session.
            // Keep the selected voice service bound and replace only its session connection.
            ModernReflect.callMethod(impl, "finishLocked", token, false)
            HookLog.i(TAG, "Released the cached Google corner session before native show")
        }
    }

    fun cancel() {
        if (HotReloadLifecycle.isRetiring) return
        tasks.post {
            if (HotReloadLifecycle.isRetiring) return@post
            pending?.cancel()
            pending = null
        }
    }

    fun cancelSession(stub: Any, token: Any?) {
        if (token == null) return
        val matches = runCatching {
            synchronized(stub) {
                val impl = ModernReflect.getObjectField(stub, "mImpl") ?: return@synchronized false
                val session = ModernReflect.getObjectField(impl, "mActiveSession")
                    ?: return@synchronized false
                ModernReflect.getObjectField(session, "mToken") == token
            }
        }.getOrDefault(false)
        if (matches) cancel()
    }

    private fun enabled(invocationType: Int): Boolean = when (invocationType) {
        6 -> LspConfig.isAssistantNativePowerEnabledXposed()
        1 -> LspConfig.isAssistantNativeCircleEnabledXposed()
        else -> false
    }

    private fun currentComponent(stub: Any): ComponentName? = runCatching {
        val impl = ModernReflect.getObjectField(stub, "mImpl") ?: return@runCatching null
        ModernReflect.getObjectField(impl, "mComponent") as? ComponentName
    }.getOrNull()

    private fun arm(attempt: ShowAttempt) {
        if (HotReloadLifecycle.isRetiring) return
        pending?.cancel()
        pending = attempt
        if (!attempt.completed.get()) tasks.post(attempt.timeout, SHOW_TIMEOUT_MS)
    }

    private class ShowAttempt(
        val stub: Any,
        val method: Method,
        val args: Array<Any?>,
        val component: ComponentName,
        val userId: Int,
        val invocationType: Int,
        val retry: Int,
    ) {
        private val startedAt = SystemClock.uptimeMillis()
        val completed = AtomicBoolean(false)
        private val callbackReleased = AtomicBoolean(false)
        val timeout = Runnable { complete(shown = false) }
        val callback: Any = createCallback(method.parameterTypes[3]) {
            // Preserve a Binder result arriving during preparation. Its main-thread task
            // keeps this generation busy and resumes if another resource vetoes reload.
            try { complete(it) } finally { releaseCallback() }
        }.also { outstandingCallbacks.incrementAndGet() }

        fun releaseCallback() {
            if (callbackReleased.compareAndSet(false, true)) outstandingCallbacks.decrementAndGet()
        }

        fun cancel() {
            completed.set(true)
            tasks.remove(timeout)
        }

        fun complete(shown: Boolean) {
            if (!completed.compareAndSet(false, true)) return
            tasks.post {
                if (HotReloadLifecycle.isRetiring) return@post
                tasks.remove(timeout)
                if (pending !== this) return@post
                if (shown) {
                    pending = null
                    HookLog.i(
                        TAG,
                        "Native Google session shown: type=$invocationType, " +
                            "elapsed=${SystemClock.uptimeMillis() - startedAt}ms, retry=$retry",
                    )
                    if (retry > 0) HookLog.i(TAG, "Native Google assistant session recovered")
                } else if (retry == 0) {
                    recover()
                } else {
                    pending = null
                    HookLog.w(TAG, "Google assistant did not show after one binding recovery")
                }
            }
        }

        private fun stillSelected(): Boolean = runCatching {
            synchronized(stub) {
                val impl = ModernReflect.getObjectField(stub, "mImpl") ?: return@synchronized false
                val context = ModernReflect.getObjectField(impl, "mContext") as? Context
                    ?: return@synchronized false
                val selected = ModernReflect.callStaticMethod(
                    Settings.Secure::class.java,
                    "getStringForUser",
                    context.contentResolver,
                    "voice_interaction_service",
                    userId,
                ) as? String
                enabled(invocationType) &&
                    ModernReflect.getObjectField(stub, "mCurUser") == userId &&
                    currentComponent(stub) == component &&
                    selected?.let(ComponentName::unflattenFromString) == component &&
                    ModernReflect.getObjectField(stub, "mTemporarilyDisabled") != true
            }
        }.getOrDefault(false)

        private fun recover() {
            if (!stillSelected()) {
                pending = null
                return
            }
            val identity = Binder.clearCallingIdentity()
            try {
                // Recreate the same selected implementation; never change the Assistant Role
                // or force-stop the Google app and its unrelated services.
                synchronized(stub) {
                    ModernReflect.callMethod(stub, "switchImplementationIfNeeded", true)
                }
                HookLog.i(TAG, "Native Google show callback timed out/failed; rebuilding its binding")
                waitForBinding(SystemClock.uptimeMillis() + BIND_TIMEOUT_MS)
            } catch (error: Throwable) {
                pending = null
                HookLog.w(TAG, "Failed to recover the native Google assistant binding", error)
            } finally {
                Binder.restoreCallingIdentity(identity)
            }
        }

        private fun waitForBinding(deadline: Long) {
            if (HotReloadLifecycle.isRetiring || pending !== this) return
            if (!stillSelected()) {
                pending = null
                return
            }
            val connected = runCatching {
                synchronized(stub) {
                    val impl = ModernReflect.getObjectField(stub, "mImpl")
                        ?: return@synchronized false
                    ModernReflect.getObjectField(impl, "mService") != null
                }
            }.getOrDefault(false)
            if (!connected) {
                if (SystemClock.uptimeMillis() < deadline) {
                    tasks.post(Runnable { waitForBinding(deadline) }, BIND_POLL_MS)
                } else {
                    pending = null
                    HookLog.w(TAG, "Native Google assistant did not reconnect before the deadline")
                }
                return
            }
            val retryArgs = args.copyOf().also {
                it[0] = Bundle(args[0] as Bundle).apply {
                    putLong("invocation_time_ms", SystemClock.elapsedRealtime())
                }
            }
            val next = ShowAttempt(stub, method, retryArgs, component, userId, invocationType, 1)
            retryArgs[3] = next.callback
            arm(next)
            val identity = Binder.clearCallingIdentity()
            replaying.set(true)
            try {
                method.isAccessible = true
                if (method.invoke(stub, *retryArgs) == false) {
                    next.releaseCallback()
                    next.complete(shown = false)
                }
            } catch (error: Throwable) {
                next.releaseCallback()
                next.cancel()
                pending = null
                HookLog.w(TAG, "Failed to retry the native assistant session", error)
            } finally {
                replaying.remove()
                Binder.restoreCallingIdentity(identity)
            }
        }
    }

    private fun createCallback(callbackClass: Class<*>, onComplete: (Boolean) -> Unit): Any {
        val stubClass = HostClassLookup.load("$CALLBACK_DESCRIPTOR\$Stub", false, callbackClass.classLoader)
        fun transaction(name: String): Int = stubClass.getDeclaredField("TRANSACTION_$name")
            .apply { isAccessible = true }.getInt(null)
        val shownCode = transaction("onShown")
        val failedCode = transaction("onFailed")
        val binder = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code == IBinder.INTERFACE_TRANSACTION) {
                    reply?.writeString(CALLBACK_DESCRIPTOR)
                    return true
                }
                if (code == shownCode || code == failedCode) {
                    data.enforceInterface(CALLBACK_DESCRIPTOR)
                    onComplete(code == shownCode)
                    return true
                }
                return super.onTransact(code, data, reply, flags)
            }
        }
        return Proxy.newProxyInstance(callbackClass.classLoader, arrayOf(callbackClass)) { proxy, method, args ->
            when (method.name) {
                "asBinder" -> binder
                "onShown" -> onComplete(true)
                "onFailed" -> onComplete(false)
                "toString" -> "ONextBox-NativeAssistantShowCallback"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> null
            }
        }.also { binder.attachInterface(it as IInterface, CALLBACK_DESCRIPTOR) }
    }
}

/** Retains main-thread work across a rejected reload without exporting any old-generation task. */
internal class NativeAssistantReloadTasks(
    private val dispatch: (Runnable, Long) -> Boolean,
    private val cancelDispatch: (Runnable) -> Unit,
    private val runWhenActive: (action: () -> Unit, deferred: () -> Unit) -> Unit,
) {
    private val lock = Any()
    private val queued = linkedSetOf<Task>()

    private inner class Task(val action: Runnable) : Runnable {
        var canceled = false
        var deferred = false

        override fun run() {
            if (synchronized(lock) { canceled }) return
            runWhenActive({
                try {
                    if (!synchronized(lock) { canceled }) action.run()
                } finally {
                    synchronized(lock) { queued.remove(this) }
                }
            }, {
                synchronized(lock) { if (!canceled) deferred = true }
            })
        }
    }

    fun isIdle(): Boolean = synchronized(lock) { queued.isEmpty() }

    fun post(action: Runnable, delay: Long = 0L) {
        val task = Task(action)
        synchronized(lock) { queued.add(task) }
        enqueue(task, delay)
    }

    fun post(action: () -> Unit) = post(Runnable(action))

    fun remove(action: Runnable) {
        val removed = synchronized(lock) {
            queued.filter { it.action === action }.onEach { it.canceled = true; queued.remove(it) }
        }
        removed.forEach(cancelDispatch)
    }

    fun resumeDeferred() {
        val resumed = synchronized(lock) {
            queued.filter { it.deferred && !it.canceled }.onEach { it.deferred = false }
        }
        resumed.forEach { enqueue(it, 0L) }
    }

    private fun enqueue(task: Task, delay: Long) {
        try {
            check(dispatch(task, delay)) { "Assistant recovery main thread is unavailable" }
        } catch (error: Throwable) {
            synchronized(lock) { task.canceled = true; queued.remove(task) }
            throw error
        }
    }
}
