package com.mi.onextbox.lsp

import android.content.Intent
import android.os.Handler
import com.mi.onextbox.lsp.compat.HookConfigSnapshot
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Modifier

internal object LowBatteryAutoSwitch {
    const val KEY = "battery_block_low_level_auto_super"

    fun hook(loader: ClassLoader, colorOs16: Boolean = false) {
        runCatching {
            val center = loader.loadClass(if (colorOs16) "e8.e" else "ba.e")
            val threshold = center.getDeclaredMethod("z")
            val evaluate = center.getDeclaredMethod("v")
            require(Modifier.isPrivate(threshold.modifiers) && threshold.returnType == Boolean::class.javaPrimitiveType)
            require(evaluate.returnType == Void.TYPE &&
                center.getDeclaredMethod("w", Handler::class.java).returnType == Void.TYPE &&
                center.getDeclaredMethod(if (colorOs16) "f" else "d", Int::class.javaPrimitiveType, Intent::class.java).returnType == Void.TYPE)
            ModernHookRegistry.installFast("battery:auto-super:threshold", threshold, XposedInterface.Hooker { chain ->
                if (HookConfigSnapshot.boolean(KEY, false) == true) false else chain.proceed()
            })
            ModernHookRuntime.requireModule().deoptimize(evaluate)
        }.onFailure { HookLog.w("BatteryAutoSwitch", "Automatic low-level transition unavailable", it) }
    }
}
