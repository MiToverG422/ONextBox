package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHotReloadSession
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import io.github.libxposed.api.XposedInterface
import android.os.Handler
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

/** Deliver OEM state changes on the native subscriber's own handler. */
internal object AospVolumeCallbackBridge {
    @Volatile private var installed = false
    private const val PRODUCER = "com.android.systemui.volume.dialog.domain.interactor.VolumeDialogCallbacksInteractor\$VolumeDialogEventModelProducer"

    fun registerHotReload(loader: ClassLoader, producerName: String = PRODUCER) {
        HotReloadLifecycle.registerReplay("aosp-volume:callback-bridge@${System.identityHashCode(loader)}",
            capture = { installed }, replay = { if (it == true) install(loader, producerName) })
        val previous = ModernHotReloadSession.current?.previousTargets.orEmpty()
        if (previous.any { it is Method &&
                it.declaringClass.name == "com.android.systemui.volume.VolumeDialogControllerImpl\$C" &&
                it.name == "onStateChanged" && it.returnType == Void.TYPE &&
                it.parameterTypes.firstOrNull()?.name ==
                    "com.android.systemui.plugins.VolumeDialogController\$State" }) {
            install(loader, producerName)
        }
    }

    @Synchronized
    fun install(loader: ClassLoader, producerName: String = PRODUCER) {
        if (installed) return
        val callbacks = Class.forName("com.android.systemui.plugins.VolumeDialogController\$Callbacks", false, loader)
        val state = Class.forName("com.android.systemui.plugins.VolumeDialogController\$State", false, loader)
        val producer = Class.forName(producerName, false, loader)
        val update = producer.getMethod("onStateChanged", state).apply { isAccessible = true }
        // Do not intercept inherited interface defaults: ART can dispatch to a copied
        // default implementation instead. Hook the concrete OEM dispatcher instead.
        val dispatcher = Class.forName("com.android.systemui.volume.VolumeDialogControllerImpl\$C", false, loader)
        val methods = AospVolumeCallbackMethods.bridgeTargets(callbacks, state, producer, dispatcher)
        if (methods.isEmpty()) {
            installed = true
            HookLog.i("ONextBox-AospVolume", "Native volume callbacks already compatible")
            return
        }
        val callbackMap = dispatcher.getDeclaredField("mCallbackMap").apply { isAccessible = true }
        val copy = state.getMethod("copy")
        val delivered = AtomicBoolean()
        val failed = AtomicBoolean()
        fun reportFailure(error: Throwable) {
            if (failed.compareAndSet(false, true)) {
                HookLog.i("ONextBox-AospVolume", "Native volume state delivery failed: $error")
            }
        }
        methods.forEach { method ->
            ModernHookRegistry.installFast("aosp-volume:dispatcher:state:${method.parameterCount}", method,
                XposedInterface.Hooker { chain ->
                    val result = chain.proceed()
                    runCatching {
                        val listeners = callbackMap.get(chain.thisObject) as Map<*, *>
                        val source = chain.getArg(0)
                        val targets = listeners.entries.filter { shouldForward(producer.isInstance(it.key), source != null) }
                        if (targets.isNotEmpty()) {
                            val snapshot = copy.invoke(source)
                            targets.forEach { entry ->
                                val target = entry.key
                                val handler = entry.value as Handler
                                handler.post {
                                    HotReloadLifecycle.withHook(fallback = {}, action = {
                                        // destroy() may unregister the subscriber before this runs.
                                        if (listeners[target] === handler) {
                                            runCatching {
                                                update.invoke(target, snapshot)
                                                if (delivered.compareAndSet(false, true)) {
                                                    HookLog.i("ONextBox-AospVolume", "Native volume live state delivered")
                                                }
                                            }.onFailure(::reportFailure)
                                        }
                                    })
                                }
                            }
                        }
                    }.onFailure(::reportFailure)
                    result
                })
        }
        installed = true
        // Initial-state delivery already invokes the one-argument callback on this ROM.
        HookLog.i("ONextBox-AospVolume", "Native volume dispatcher bridge ready: ${methods.map { it.parameterCount }} arguments")
    }

    internal fun shouldForward(nativeProducer: Boolean, hasState: Boolean) = nativeProducer && hasState
}
