package com.mi.onextbox.lsp

import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object BatteryWhitelistRestoreTargets {
    fun resolve(dex: DexKitBridge, loader: ClassLoader): Array<Method> {
        val data = dex.findMethod {
            matcher {
                usingStrings("restoreDftDozeWhiteList: listDftDozeWL=", "restoreDftDozeWhiteList: isDftWL=")
                returnType = "void"
                paramTypes("android.os.IDeviceIdleController", "java.lang.String", "android.content.Context")
            }
        }.single()
        val restore = data.getMethodInstance(loader)
        require(BatteryOptimizationRules.isWhitelistRestore(restore.returnType.name,
            restore.parameterTypes.map { it.name }, Modifier.isStatic(restore.modifiers), Modifier.isAbstract(restore.modifiers)))
        val callers = (data.callers + data.callers.flatMap { it.callers }).distinctBy { it.descriptor }
            .mapNotNull { runCatching { it.getMethodInstance(loader) }.getOrNull() }
        return (listOf(restore) + callers).distinct().toTypedArray()
    }
}
