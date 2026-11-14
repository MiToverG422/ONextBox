package com.mi.onextbox.lsp

import android.os.CountDownTimer
import android.widget.Button
import android.content.res.Resources
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import java.lang.reflect.Method
import org.luckypray.dexkit.DexKitBridge

internal object GalleryDeleteCountdownHooker {
    private const val TAG = "GalleryDeleteCountdown"

    internal fun targets(dex: DexKitBridge, loader: ClassLoader): Array<Method> {
        val dialog = dex.findMethod {
            matcher { usingStrings("showActionDialog: center dialog is showing"); returnType = "void" }
        }.single()
        val timer = dialog.invokes.filter { it.isConstructor &&
            it.paramTypeNames == listOf(Button::class.java.name, String::class.java.name, Resources::class.java.name)
        }.map { Class.forName(it.className, false, loader) }
            .single { CountDownTimer::class.java.isAssignableFrom(it) }
        return arrayOf(timer.getDeclaredMethod("onFinish"), dialog.getMethodInstance(loader))
    }

    fun hook(loader: ClassLoader, apkPath: String) {
        val key = "$TAG@${System.identityHashCode(loader)}"
        runCatching {
            val methods = DexTargetCache.restoredState(key) as? Array<*>
                ?: ApkDexSession.withSession(apkPath) { targets(it(), loader) }
            val finish = (methods[0] as Method).apply { isAccessible = true }
            var reported = false
            ModernHookRegistry.installFast("$key:start", CountDownTimer::class.java.getDeclaredMethod("start")) { chain ->
                if (SystemCustomizationSettings.active(SystemCustomizationSettings.Feature.GalleryDeleteCountdown) &&
                    finish.declaringClass.isInstance(chain.thisObject)) {
                    finish.invoke(chain.thisObject)
                    if (!reported) { reported = true; HookLog.i(TAG, "Native deletion confirmation enabled without countdown") }
                    chain.thisObject
                } else chain.proceed()
            }
            ModernHookRuntime.requireModule().deoptimize(methods[1] as Method)
            HotReloadLifecycle.register(key, capture = { methods })
            HookLog.i(TAG, "Native deletion confirmation timer adapter installed")
        }.onFailure {
            ModernHookRegistry.unhookPrefix(key)
            HookLog.w(TAG, "Delete confirmation timer unavailable; native dialog retained", it)
        }
    }
}
