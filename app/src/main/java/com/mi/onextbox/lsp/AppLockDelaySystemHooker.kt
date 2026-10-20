package com.mi.onextbox.lsp

import android.content.Context
import android.content.SharedPreferences
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import com.mi.onextbox.lsp.compat.*
import io.github.libxposed.api.XposedInterface
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.function.Consumer
import com.mi.onextbox.lsp.compat.ModernReflect as Reflect

internal object AppLockDelaySystemHooker {
    fun hook(loader: ClassLoader) = runCatching { Session(loader).install() }
        .onFailure { HookLog.w("ONextBox-AppLock", "Native delay backend unavailable", it) }.let { }

    private class Session(private val loader: ClassLoader) {
        private val policy = AppLockDelayPolicy()
        private val main = Handler(Looper.getMainLooper())
        private val tasks = hashMapOf<AppLockDelayPolicy.Key, Runnable>()
        private val clearing = ThreadLocal<Any?>()
        private val bypass = ThreadLocal<Boolean>()
        private var service = WeakReference<Any>(null)
        private var prefs: SharedPreferences? = null
        private var observedContext: Context? = null
        private var observerFailed = false
        @Volatile private var retired = false
        private lateinit var nativeClear: Method
        private val prefix = "ONextBox-AppLock:system@${System.identityHashCode(loader)}:"
        private val secureObserver = object : ContentObserver(main) {
            override fun onChange(selfChange: Boolean, uri: Uri?) { requestDrain() }
        }
        private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key in setOf(AppLockDelaySettings.ENABLED, MODERN_PREFERENCES_READY_KEY,
                SystemAdaptationSettings.KEY, SystemAdaptationSettings.ALLOW_HYPER_OS)) requestDrain()
        }
        private val drain = Runnable { HotReloadLifecycle.withHook({}, { if (!retired) service.get()?.let(::drain) }) }
        private fun requestDrain() { if (!retired) { main.removeCallbacks(drain); main.post(drain) } }

        @Synchronized private fun remember(owner: Any) {
            if (retired) return
            if (service.get() !== owner) service = WeakReference(owner)
            if (!AppLockDelaySettings.active() || observedContext != null || observerFailed) return
            val context = Reflect.getObjectField(owner, "mContext") as Context
            runCatching {
                for (key in listOf(AppLockDelaySettings.DELAY, AppLockDelaySettings.NATIVE_MODE)) {
                    Reflect.callMethod(context.contentResolver, "registerContentObserver", Settings.Secure.getUriFor(key), false, secureObserver, -1)
                }
                observedContext = context
            }.onFailure {
                runCatching { context.contentResolver.unregisterContentObserver(secureObserver) }
                observerFailed = true
                HookLog.w("ONextBox-AppLock", "Delay observer unavailable; retaining immediate relock", it)
            }
        }

        private fun delay(owner: Any, user: Int): Long {
            if (!AppLockDelaySettings.active() || retired || observedContext == null) return 0L
            val context = Reflect.getObjectField(owner, "mContext") as Context
            val userState = Reflect.callMethod(owner, "changeUserState", user) ?: return 0L
            val ownerId = Reflect.getIntField(userState, "mUserHandle")
            val mode = Reflect.callStaticMethod(Settings.Secure::class.java, "getIntForUser", context.contentResolver, AppLockDelaySettings.NATIVE_MODE, 1, ownerId)
            if (mode != 0) return 0L
            val millis = Reflect.callStaticMethod(Settings.Secure::class.java, "getLongForUser", context.contentResolver, AppLockDelaySettings.DELAY, 0L, ownerId) as? Number ?: return 0L
            return AppLockDelaySettings.valid(millis.toLong())
        }

        private fun authenticated(owner: Any, key: AppLockDelayPolicy.Key): Boolean {
            val state = Reflect.callMethod(owner, "getUserStateLocked", key.user) ?: return false
            return (Reflect.getObjectField(state, "mAccessControlPassPackages") as? Map<*, *>)?.containsKey(key.pkg) == true
        }
        private fun cancel(key: AppLockDelayPolicy.Key) {
            policy.cancel(key)
            tasks.remove(key)?.let(main::removeCallbacks)
        }
        private fun revoke(owner: Any, key: AppLockDelayPolicy.Key) {
            bypass.set(true)
            try { nativeClear.invoke(owner, key.pkg, key.user) } finally { bypass.remove() }
            cancel(key)
        }
        private fun visible(owner: Any, key: AppLockDelayPolicy.Key): Boolean {
            var found = false
            val atms = Reflect.getObjectField(owner, "mAtms") ?: return false
            val root = Reflect.getObjectField(atms, "mRootWindowContainer") ?: return false
            Reflect.callMethod(root, "forAllTasks", Consumer<Any> { task ->
                if (!found && Reflect.callMethod(task, "shouldBeVisible", null) == true) {
                    Reflect.callMethod(task, "topRunningActivity", true)?.let {
                        found = Reflect.getObjectField(it, "packageName") == key.pkg && Reflect.getIntField(it, "mUserId") == key.user
                    }
                }
            })
            return found
        }
        private fun withLocks(owner: Any, action: () -> Unit) {
            val atms = checkNotNull(Reflect.getObjectField(owner, "mAtms"))
            val global = checkNotNull(Reflect.getObjectField(atms, "mGlobalLock"))
            synchronized(global) { synchronized(owner) { action() } }
        }
        private fun schedule(owner: Any, key: AppLockDelayPolicy.Key, lease: AppLockDelayPolicy.Lease): Boolean {
            if (tasks.containsKey(key)) return true
            val task = Runnable {
                HotReloadLifecycle.withHook({}, {
                    if (!retired) runCatching {
                        withLocks(owner) {
                            if (policy.current(key) != lease) return@withLocks
                            if (runCatching { delay(owner, key.user) }.getOrDefault(0L) != lease.duration) revoke(owner, key)
                            else if (runCatching { visible(owner, key) }.getOrDefault(false)) cancel(key)
                            else revoke(owner, key)
                        }
                    }.onFailure { HookLog.w("ONextBox-AppLock", "Could not expire native grant; next access will require verification", it) }
                })
            }
            tasks[key] = task
            if (main.postDelayed(task, (lease.deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0))) return true
            tasks.remove(key)
            policy.cancel(key)
            return false
        }
        private fun drain(owner: Any) = withLocks(owner) { policy.snapshot().keys.forEach { revoke(owner, it) } }

        fun install() {
            val host = Reflect.findClass("com.android.server.wm.OplusAccessControlManagerService", loader)
            val activity = Reflect.findClass("com.android.server.wm.ActivityRecord", loader)
            val task = Reflect.findClass("com.android.server.wm.Task", loader)
            val int = Int::class.javaPrimitiveType!!
            val clear = host.getDeclaredMethod("clearAccessControlPassPackages", task, String::class.java, int, String::class.java, activity)
            nativeClear = host.getDeclaredMethod("clearPassPackages", String::class.java, int).apply { isAccessible = true }
            val access = host.getDeclaredMethod("isEncryptPass", String::class.java, int)
            val resumed = host.getDeclaredMethod("interceptResumeActivity", activity, int)
            val visible = activity.getDeclaredMethod("setVisibilityInner", Boolean::class.javaPrimitiveType)
            val setVisibility = activity.getDeclaredMethod("setVisibility", Boolean::class.javaPrimitiveType)
            val add = host.getDeclaredMethod("addEncryptPass", String::class.java, int, int)
            val remove = host.getDeclaredMethod("removeAccessControlPassAsUser", String::class.java, int, Boolean::class.javaPrimitiveType)
            ModernHookRegistry.installFast(prefix + "exit", clear, XposedInterface.Hooker { chain ->
                val owner = chain.thisObject ?: return@Hooker chain.proceed()
                runCatching { remember(owner) }
                val previous = clearing.get()
                clearing.set(if (AppLockDelayPolicy.isVisibilityExit(chain.getArg(3))) owner else null)
                try { chain.proceed() } finally { if (previous == null) clearing.remove() else clearing.set(previous) }
            })
            ModernHookRegistry.installFast(prefix + "defer-native-clear", nativeClear, XposedInterface.Hooker { chain ->
                val owner = chain.thisObject ?: return@Hooker chain.proceed()
                if (retired || bypass.get() == true || clearing.get() !== owner) return@Hooker chain.proceed()
                val pkg = chain.getArg(0) as? String ?: return@Hooker chain.proceed()
                val key = AppLockDelayPolicy.Key(chain.getArg(1) as Int, pkg)
                val deferred = runCatching {
                    if (retired || bypass.get() == true || clearing.get() !== owner) false
                    else synchronized(owner) {
                        val millis = delay(owner, key.user)
                        val lease = policy.defer(key, SystemClock.elapsedRealtime(), millis, authenticated(owner, key))
                        if (lease == null) { cancel(key); false } else schedule(owner, key, lease)
                    }
                }.getOrDefault(false)
                if (deferred) null else chain.proceed()
            })
            ModernHookRegistry.installCompat(prefix + "access", access, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val owner = param.thisObject ?: return
                    runCatching { remember(owner) }
                    if (!policy.hasPending()) return
                    val key = AppLockDelayPolicy.Key(param.args[1] as Int, param.args[0] as String? ?: return)
                    synchronized(owner) {
                        val lease = policy.current(key) ?: return
                        if (lease.requiresRelock(SystemClock.elapsedRealtime(), runCatching { delay(owner, key.user) }.getOrDefault(0L))) {
                            runCatching { revoke(owner, key) }.onFailure { param.result = false }
                        }
                    }
                }
            })
            ModernHookRegistry.installCompat(prefix + "resume", resumed, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (retired || !policy.hasPending()) return
                    runCatching {
                        val owner = param.thisObject ?: return
                        val record = param.args[0] ?: return
                        val key = AppLockDelayPolicy.Key(Reflect.getIntField(record, "mUserId"), Reflect.getObjectField(record, "packageName") as? String ?: return)
                        synchronized(owner) {
                            val lease = policy.current(key) ?: return
                            if (lease.requiresRelock(SystemClock.elapsedRealtime(), runCatching { delay(owner, key.user) }.getOrDefault(0L))) revoke(owner, key)
                        }
                    }
                }
            })
            // A permitted resume attempt is not proof that the app actually became visible.
            ModernHookRegistry.installCompat(prefix + "visible", visible, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (retired || param.throwable != null || param.args[0] != true || !policy.hasPending()) return
                    runCatching {
                        val owner = service.get() ?: return
                        val record = param.thisObject ?: return
                        if (Reflect.getObjectField(record, "mVisibleRequested") != true) return
                        val pkg = Reflect.getObjectField(record, "packageName") as? String ?: return
                        synchronized(owner) {
                            val key = AppLockDelayPolicy.Key(Reflect.getIntField(record, "mUserId"), pkg)
                            val lease = policy.current(key) ?: return
                            if (runCatching { delay(owner, key.user) }.getOrDefault(0L) != lease.duration) revoke(owner, key)
                            else if (!lease.expired(SystemClock.elapsedRealtime()) && authenticated(owner, key)) cancel(key)
                        }
                    }
                }
            })
            ModernHookRegistry.installCompat(prefix + "authenticated", add, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.throwable != null) return
                    val owner = param.thisObject ?: return
                    synchronized(owner) { cancel(AppLockDelayPolicy.Key(param.args[2] as Int, param.args[0] as String? ?: return)) }
                }
            })
            ModernHookRegistry.installCompat(prefix + "native-revoke", remove, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.throwable != null) return
                    val owner = param.thisObject ?: return
                    synchronized(owner) {
                        val user = param.args[1] as Int
                        val pkg = param.args[0] as? String
                        val all = param.args[2] == true
                        policy.snapshot().keys.filter { all || (it.user == user && it.pkg == pkg) }.forEach(::cancel)
                    }
                }
            })
            listOf(clear, nativeClear, access, resumed, visible, setVisibility, add, remove).forEach { runCatching { ModernHookRuntime.requireModule().deoptimize(it) } }
            HotReloadLifecycle.register("applock:delay-system", capture = { service.get() },
                detach = {
                    retired = true
                    main.removeCallbacksAndMessages(null)
                    service.get()?.let(::drain)
                    prefs?.unregisterOnSharedPreferenceChangeListener(listener); prefs = null
                    observedContext?.contentResolver?.unregisterContentObserver(secureObserver); observedContext = null
                },
                restore = { retired = false; observe(); reschedule() },
                resumeAfterRejection = { retired = false; observe(); reschedule() },
            )
            HotReloadLifecycle.afterCommit {
                HotReloadLifecycle.restoredState("applock:delay-system")?.let { service = WeakReference(it) }
                observe()
            }
        }
        private fun observe() {
            if (prefs == null) ModernHookRuntime.remotePreferences("lsp_features")?.let {
                it.registerOnSharedPreferenceChangeListener(listener); prefs = it
            }
        }
        private fun reschedule() { service.get()?.let { owner -> synchronized(owner) {
            tasks.clear()
            policy.snapshot().forEach { (key, lease) -> schedule(owner, key, lease) }
        } } }
    }
}
