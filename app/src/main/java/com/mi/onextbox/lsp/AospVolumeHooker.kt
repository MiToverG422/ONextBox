package com.mi.onextbox.lsp

import android.os.Handler
import android.os.Looper
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Proxy

internal object AospVolumeHooker {
    private const val TAG = "ONextBox-AospVolume"
    private val bypass = ThreadLocal.withInitial { false }

    fun hook(loader: ClassLoader) {
        runCatching {
            AospVolumeCallbackBridge.registerHotReload(loader)
            val type = Class.forName("com.oplus.systemui.dagger.DaggerOplusReferenceGlobalRootComponent\$OplusReferenceSysUIComponentImpl", false, loader)
            val method = type.getDeclaredMethod("volumeDialog")
            ModernHookRegistry.installFast("aosp-volume:factory", method, XposedInterface.Hooker { chain ->
                if (bypass.get() == true || !AospVolumeSettings.enabledXposed()) return@Hooker chain.proceed()
                val host = requireNotNull(chain.thisObject)
                try {
                    AospVolumeCallbackBridge.install(loader)
                    val native = AospVolumeGraph.create(host)
                    val replacement = Session(native) {
                        bypass.set(true)
                        try { requireNotNull(method.invoke(host)) } finally { bypass.remove() }
                    }
                    replacement.proxy()
                } catch (error: Throwable) {
                    HookLog.i(TAG, "Native volume unavailable; retaining stock: $error")
                    chain.proceed()
                }
            })
        }.onFailure { HookLog.i(TAG, "Native volume factory not compatible: $it") }
    }

    private class Session(private val native: Any, private val stockFactory: () -> Any) {
        private val loader = native.javaClass.classLoader!!
        private val dialogType = Class.forName("com.android.systemui.plugins.VolumeDialog", false, loader)
        private val main = Handler(Looper.getMainLooper())
        private var stock: Any? = null
        private var failed = false
        private var destroyed = false
        private var initialized = false
        private var initArgs: Array<out Any?>? = null
        private var ownedScope: Any? = null

        fun proxy(): Any {
            installIsolatedScope()
            return Proxy.newProxyInstance(loader, arrayOf(dialogType)) { proxy, method, args ->
                when (method.name) {
                    "toString" -> "ONextBox Native Volume"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.getOrNull(0)
                    "init" -> {
                        if (!destroyed && !initialized) {
                            initialized = true
                            initArgs = args ?: emptyArray()
                            try {
                                method.invoke(stock ?: native, *initArgs!!)
                                HookLog.i(TAG, "Native volume initialization dispatched")
                            }
                            catch (error: Exception) { fallback(error) }
                        }
                        null
                    }
                    "destroy" -> {
                        destroyed = true
                        runCatching { dialogType.getMethod("destroy").invoke(stock ?: native) }
                        cancelScope()
                        null
                    }
                    else -> method.invoke(stock ?: native, *(args ?: emptyArray()))
                }
            }
        }

        private fun fallback(error: Throwable) {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                main.post { fallback(error) }
                return
            }
            if (failed || destroyed) return
            failed = true
            HookLog.i(TAG, "Native volume failed; restoring stock: $error")
            runCatching { dialogType.getMethod("destroy").invoke(native) }
            cancelScope()
            runCatching {
                stock = stockFactory()
                initArgs?.let { args -> dialogType.methods.single { it.name == "init" }.invoke(stock, *args) }
            }.onFailure { HookLog.i(TAG, "Stock volume restoration failed: $it") }
        }

        private fun installIsolatedScope() {
            fun type(name: String) = Class.forName(name, false, loader)
            val scopeType = type("kotlinx.coroutines.CoroutineScope")
            val contextType = type("kotlin.coroutines.CoroutineContext")
            val functionType = type("kotlin.jvm.functions.Function2")
            val original = AospVolumeGraph.field(native, "applicationCoroutineScope")
            val originalContext = scopeType.getMethod("getCoroutineContext").invoke(original)
            val callback = Proxy.newProxyInstance(loader, arrayOf(functionType)) { proxy, method, args ->
                when (method.name) {
                    "invoke" -> {
                        (args?.getOrNull(1) as? Throwable)?.let { error -> main.post { fallback(error) } }
                        type("kotlin.Unit").getField("INSTANCE").get(null)
                    }
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.getOrNull(0)
                    else -> "ONextBox Volume Exception Handler"
                }
            }
            val handler = type("kotlinx.coroutines.CoroutineExceptionHandlerKt")
                .getMethod("CoroutineExceptionHandler", functionType).invoke(null, callback)
            val job = type("kotlinx.coroutines.SupervisorKt")
                .getMethod("SupervisorJob", type("kotlinx.coroutines.Job")).invoke(null, null)
            val plus = contextType.getMethod("plus", contextType)
            val context = plus.invoke(plus.invoke(originalContext, job), handler)
            ownedScope = type("kotlinx.coroutines.CoroutineScopeKt").getMethod("CoroutineScope", contextType)
                .invoke(null, context)
            native.javaClass.getDeclaredField("applicationCoroutineScope").apply { isAccessible = true }
                .set(native, ownedScope)
        }

        private fun cancelScope() {
            ownedScope?.let { scope ->
                runCatching {
                    Class.forName("kotlinx.coroutines.CoroutineScopeKt", false, loader)
                        .getMethod("cancel", Class.forName("kotlinx.coroutines.CoroutineScope", false, loader),
                            java.util.concurrent.CancellationException::class.java)
                        .invoke(null, scope, null)
                }
            }
            ownedScope = null
        }
    }
}
