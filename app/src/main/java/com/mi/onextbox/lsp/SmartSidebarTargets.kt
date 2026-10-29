package com.mi.onextbox.lsp

import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object SmartSidebarTargets {
    const val SCHEMA = "smart-sidebar-apps:v1"

    fun resolve(dex: DexKitBridge, loader: ClassLoader): Map<String, Array<Method>> {
        val groups = linkedMapOf(SCHEMA to emptyArray<Method>())
        fun method(owner: String, name: String, result: String, vararg parameters: String): MethodData =
            dex.findMethod {
                matcher { declaredClass = owner; this.name = name; returnType = result; paramTypes(*parameters) }
            }.single()
        fun group(name: String, targets: () -> List<MethodData>) {
            runCatching {
                val selected = targets()
                val direct = selected.flatMap { it.callers }
                val callers = (direct + direct.flatMap { it.callers }).filter { it.isMethod }
                    .distinctBy { it.descriptor }.mapNotNull { runCatching { it.getMethodInstance(loader) }.getOrNull() }
                groups[name] = (selected.map { it.getMethodInstance(loader) } + callers).distinct().toTypedArray()
            }.onFailure { HookLog.w("ONextBox-Sidebar", "$name interfaces unavailable; native behavior retained", it) }
        }
        group("apps") {
            val owner = "com.oplus.smartsidebar.panelview.edgepanel.data.entrybeans.proxies.LauncherAppProxy"
            val available = method(owner, "isActivityAvailable", "boolean", "android.content.pm.LauncherActivityInfo")
            require(!Modifier.isStatic(available.getMethodInstance(loader).modifiers))
            require(available.invokes.any { it.className == "android.content.pm.LauncherActivityInfo" && it.methodName == "getLabel" })
            val hidden = method(owner, "isHiddenFromLauncherIconList", "boolean", "java.lang.String")
            require(Modifier.isStatic(hidden.getMethodInstance(loader).modifiers))
            val load = method(owner, "loadAllAppIfNeed", "void")
            listOf(available, hidden, load)
        }
        group("display") {
            val owner = "com.coloros.edgepanel.helpers.AppSwitchHelper"
            listOf(method(owner, "needExit", "boolean"), method(owner, "needMinimize", "boolean")).also { methods ->
                require(methods.none { Modifier.isStatic(it.getMethodInstance(loader).modifiers) })
            }
        }
        return groups
    }
}
