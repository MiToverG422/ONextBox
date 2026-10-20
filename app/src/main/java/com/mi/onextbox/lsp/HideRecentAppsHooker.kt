package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernHotReloadSession
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/** Filter presentation copies, never the task cache or ActivityTaskManager records. */
internal object HideRecentAppsHooker {
    private const val TAG = "ONextBox-HideRecentApps"
    private const val TASK = "com.android.systemui.shared.recents.model.Task"

    fun hook(loader: ClassLoader) {
        runCatching {
            val base = ModernReflect.findClass("com.android.quickstep.RecentTasksList", loader)
            val task = ModernReflect.findClass(TASK, loader)
            val packageGetter = task.getDeclaredMethod("getPackageName")
            check(packageGetter.returnType == String::class.java)
            installRunningCardGate(loader, task, packageGetter)
            val replay = RecentTaskHookReplay(base) { concrete -> install(concrete, base, task, packageGetter) }
            HotReloadLifecycle.registerReplay("$TAG:types@${System.identityHashCode(loader)}",
                capture = replay::capture, replay = { replay.restore(it) })
            replay.restore(null, ModernHotReloadSession.current?.previousTargets.orEmpty())
            // The concrete ColorOS subclass is obfuscated; discover it from construction.
            base.declaredConstructors.forEach { constructor ->
                ModernHookRegistry.installCompat("$TAG:ctor:${constructor.parameterCount}", constructor,
                    object : ModernMethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (param.throwable != null) return
                            val concrete = param.thisObject?.javaClass ?: return
                            runCatching { replay.discover(concrete) }
                                .onFailure { HookLog.w(TAG, "Unsupported recent-task presentation; stock list retained", it) }
                        }
                    })
            }
        }.onFailure { HookLog.w(TAG, "Recent-task filtering unavailable", it) }
    }

    private fun installRunningCardGate(loader: ClassLoader, task: Class<*>, packageGetter: java.lang.reflect.Method) {
        // Native ColorOS already rejects blacklisted apps here. Use the same gate so a filtered
        // app is not reinserted as a temporary running-task card when entering overview.
        val taskArray = java.lang.reflect.Array.newInstance(task, 0).javaClass
        listOf("com.android.quickstep.views.RecentsView",
            "com.android.quickstep.views.OplusRecentsViewImpl").forEach { name ->
            runCatching {
                val owner = ModernReflect.findClass(name, loader)
                val gate = owner.getDeclaredMethod("shouldAddStubTaskView", taskArray)
                check(gate.returnType == Boolean::class.javaPrimitiveType)
                ModernHookRegistry.installCompat("$TAG:running:$name", gate, object : ModernMethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val hidden = HideRecentAppsSettings.hiddenXposed()
                        if (hidden.isEmpty()) return
                        val tasks = param.args[0] as? Array<*> ?: return
                        val packages = tasks.map { value ->
                            value?.let { runCatching { packageGetter.invoke(it) as? String }.getOrNull() }
                        }
                        if (HideRecentAppsRules.hideGroup(packages, hidden)) param.result = false
                    }
                })
                (owner.declaredMethods.filter { it.name == "showCurrentTask" } + gate).forEach {
                    ModernHookRuntime.requireModule().deoptimize(it)
                }
            }.onFailure { HookLog.w(TAG, "Running-task gate unavailable for $name", it) }
        }
    }

    private fun install(concrete: Class<*>, base: Class<*>, task: Class<*>, packageGetter: java.lang.reflect.Method) {
        // C17: copy(ArrayList) and the panorama presentation getter List(). Only override paths
        // declared by RecentTasksList qualify; unrelated list getters must never be filtered.
        val copies = concrete.declaredMethods.filter { method ->
            !Modifier.isStatic(method.modifiers) && !method.isBridge &&
                method.returnType == ArrayList::class.java &&
                method.parameterTypes.contentEquals(arrayOf(ArrayList::class.java)) &&
                base.declaredMethods.any { it.name == method.name && it.parameterTypes.contentEquals(method.parameterTypes) }
        }
        check(copies.size == 1) { "Ambiguous recent-task copy method" }
        val panorama = concrete.declaredMethods.filter { method ->
            !Modifier.isStatic(method.modifiers) && !method.isBridge && method.parameterCount == 0 &&
                method.returnType == List::class.java && base.declaredMethods.any {
                    it.name == method.name && it.parameterCount == 0 && it.returnType == List::class.java
                }
        }
        check(panorama.size <= 1) { "Ambiguous panorama task getter" }
        val fields = ConcurrentHashMap<Class<*>, List<java.lang.reflect.Field>>()
        (copies + panorama).forEach { method ->
            ModernHookRegistry.installCompat("$TAG:${concrete.name}:${method.name}", method,
                object : ModernMethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.throwable != null) return
                        val hidden = HideRecentAppsSettings.hiddenXposed()
                        if (hidden.isEmpty()) return
                        val original = param.result as? List<*> ?: return
                        val filtered = original.filterNot { group ->
                            if (group == null) false else runCatching {
                                val taskFields = fields.getOrPut(group.javaClass) {
                                    group.javaClass.declaredFields.filter {
                                        !Modifier.isStatic(it.modifiers) && it.type == task
                                    }.onEach { it.isAccessible = true }
                                }
                                val packages = taskFields.map { field ->
                                    field.get(group)?.let { packageGetter.invoke(it) as? String }
                                }
                                HideRecentAppsRules.hideGroup(packages, hidden)
                            }.getOrDefault(false)
                        }
                        if (filtered.size != original.size) param.result = ArrayList(filtered)
                    }
                })
        }
        // Inlined callers of the presentation-copy method must also be invalidated.
        (base.declaredMethods + concrete.declaredMethods).forEach {
            if (!Modifier.isAbstract(it.modifiers)) ModernHookRuntime.requireModule().deoptimize(it)
        }
    }
}
