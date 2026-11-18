package com.mi.onextbox.lsp

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.UserHandle
import android.view.KeyEvent
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import io.github.libxposed.api.XposedInterface

internal object SideKeyHooker {
    private const val TAG = "ONextBox-SideKey"
    private const val TARGET = "com.android.server.policy.StrategyActionButtonKeyLaunchApp"

    fun hookSystemServer(loader: ClassLoader) {
        if (!SideKeyFeaturePolicy.available) return
        runCatching {
            val type = HostClassLookup.load(TARGET, false, loader)
            val method = type.getDeclaredMethod("actionInterceptKeyBeforeQueueing", KeyEvent::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            check(method.returnType == Int::class.javaPrimitiveType)
            val owner = Owner(type)
            ModernHookRegistry.installFast("$TAG:raw", method, XposedInterface.Hooker { chain ->
                val event = chain.getArg(0) as? KeyEvent ?: return@Hooker chain.proceed()
                if (event.keyCode != SideKeyRules.RAW_KEY) return@Hooker chain.proceed()
                val strategy = chain.thisObject ?: return@Hooker chain.proceed()
                val consumed = runCatching { owner.handle(strategy, event) }.onFailure {
                    owner.abort()
                    HookLog.w(TAG, "Side key handler failed, native dispatch retained", it)
                }.getOrDefault(false)
                if (consumed) 0 else chain.proceed()
            })
            HotReloadLifecycle.register("$TAG:gestures", detach = owner::abort,
                canReload = { owner.idle() }, resumeAfterRejection = owner::resume)
            HookLog.i(TAG, "Dedicated side key hook installed")
        }.onFailure { HookLog.w(TAG, "Dedicated side key unavailable, native keys retained", it) }
    }

    private class Owner(type: Class<*>) {
        private val getContext = type.getMethod("getContext")
        private val checkConditions = type.getDeclaredMethod("checkInterceptConditions", Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }
        private val nativePressed = type.getDeclaredField("mHasProcessedDownEvent").apply { isAccessible = true }
        private val inject = type.getMethod("injectActionButtonPressKeyEvent", KeyEvent::class.java)
        private val currentUser = HostClassLookup.load("android.app.ActivityManager").getMethod("getCurrentUser")
        private val userOf = UserHandle::class.java.getMethod("of", Int::class.javaPrimitiveType)
        private val broadcast = Context::class.java.getMethod("sendBroadcastAsUser",
            Intent::class.java, UserHandle::class.java, String::class.java)
        private val handler = Handler(Looper.getMainLooper())
        private val machine = SideKeyGestureMachine()
        private var context: Context? = null
        private var wakeLock: PowerManager.WakeLock? = null
        private var stockLong = false
        private var stockDownAt = 0L
        private var suppressUp = false
        private var pressExpiresAt: Long? = null
        private val timer = Runnable {
            HotReloadLifecycle.withHook({}, {
                synchronized(this) {
                    runCatching {
                        val now = SystemClock.uptimeMillis()
                        if (SideKeySettings.enabledXposed() && (pressExpiresAt == null || now < pressExpiresAt!!))
                            emit(machine.advance(now))
                        else {
                            suppressUp = machine.isPressed
                            emit(machine.up(now, cancelled = true))
                            machine.reset()
                            pressExpiresAt = null
                        }
                        schedule()
                    }.onFailure { abort(); HookLog.w(TAG, "Side key timer failed", it) }
                }
            })
        }

        @Synchronized fun idle() = machine.isIdle && !stockLong && !suppressUp

        @Synchronized fun resume() { schedule() }

        @Synchronized fun handle(strategy: Any, event: KeyEvent): Boolean {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) suppressUp = false
            if (event.action == KeyEvent.ACTION_UP && suppressUp) {
                suppressUp = false
                return true
            }
            if (nativePressed.getBoolean(strategy)) return false
            if (!machine.isPressed && !SideKeySettings.enabledXposed()) return false
            if (event.repeatCount != 0) return true
            val now = SystemClock.uptimeMillis()
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (machine.isPressed) return true
                    val ctx = getContext.invoke(strategy) as? Context ?: return false
                    if (checkConditions.invoke(strategy, true) != null) return true
                    context = ctx
                    if (wakeLock == null) wakeLock = ctx.getSystemService(PowerManager::class.java)
                        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ONextBox:SideKey")
                    if (wakeLock?.isHeld != true) wakeLock?.acquire(5000)
                    pressExpiresAt = now + 5000
                    emit(machine.down(now, SideKeySettings.configXposed()))
                }
                KeyEvent.ACTION_UP -> {
                    if (!machine.isPressed) return true
                    emit(machine.up(now, event.isCanceled || !SideKeySettings.enabledXposed()))
                    pressExpiresAt = null
                }
                else -> return false
            }
            schedule()
            return true
        }

        private fun schedule() {
            handler.removeCallbacks(timer)
            listOfNotNull(machine.nextDeadline, pressExpiresAt).minOrNull()?.let { handler.postAtTime(timer, it) }
            if (machine.isIdle && wakeLock?.isHeld == true) wakeLock?.release()
        }

        private fun emit(commands: List<SideKeyCommand>) {
            commands.forEach { command ->
                when (command) {
                    SideKeyCommand.StockTap -> {
                        nativeEvent(SideKeyRules.TAP_KEY, KeyEvent.ACTION_DOWN)
                        nativeEvent(SideKeyRules.TAP_KEY, KeyEvent.ACTION_UP)
                    }
                    SideKeyCommand.StockLongDown -> {
                        stockDownAt = SystemClock.uptimeMillis()
                        stockLong = true
                        nativeEvent(SideKeyRules.LONG_KEY, KeyEvent.ACTION_DOWN)
                    }
                    SideKeyCommand.StockLongUp -> {
                        nativeEvent(SideKeyRules.LONG_KEY, KeyEvent.ACTION_UP)
                        stockLong = false
                    }
                    is SideKeyCommand.Run -> {
                        val ctx = context ?: return@forEach
                        val user = userOf.invoke(null, currentUser.invoke(null)) as UserHandle
                        val intent = Intent(SideKeyRules.BROADCAST).setPackage("com.android.systemui")
                            .putExtra("action", command.binding.action.id)
                            .putExtra("package", command.binding.packageName)
                            .putExtra("timestamp", SystemClock.uptimeMillis())
                        broadcast.invoke(ctx, intent, user, SideKeyRules.PERMISSION)
                        HookLog.d(TAG, "Dispatched ${command.binding.action.id}")
                    }
                }
            }
        }

        private fun nativeEvent(code: Int, action: Int) {
            val now = SystemClock.uptimeMillis()
            inject.invoke(null, KeyEvent(if (code == SideKeyRules.LONG_KEY) stockDownAt else now,
                now, action, code, 0, 0, -1, 0, 0, 257))
        }

        @Synchronized fun abort() {
            handler.removeCallbacks(timer)
            if (stockLong) runCatching { nativeEvent(SideKeyRules.LONG_KEY, KeyEvent.ACTION_UP) }
            stockLong = false
            machine.reset()
            pressExpiresAt = null
            suppressUp = false
            if (wakeLock?.isHeld == true) runCatching { wakeLock?.release() }
        }
    }
}
