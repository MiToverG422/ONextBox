package com.mi.onextbox.lsp

import android.util.SparseArray
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.util.WeakHashMap

internal object BackGesturePredictionRoutes {
    private const val TAG = "ONextBox-BackGesture"
    private const val PACKAGE = "com.android.wm.shell.back."
    private data class Retained(var activity: Any? = null, var task: Any? = null,
        val activitySlot: BackGestureRouteSlot<Any> = BackGestureRouteSlot(),
        val taskSlot: BackGestureRouteSlot<Any> = BackGestureRouteSlot())
    private val registries = WeakHashMap<Any, Retained>()

    private fun install(target: Method, callback: (XposedInterface.Chain) -> Any?) {
        ModernHookRegistry.installFast("$TAG:route:${target.toGenericString()}", target, XposedInterface.Hooker(callback))
        runCatching { ModernHookRuntime.requireModule().deoptimize(target) }
    }

    fun hook(loader: ClassLoader, activitySupported: Boolean, taskSupported: Boolean) {
        val registry = loader.loadClass(PACKAGE + "ShellBackAnimationRegistry")
        val controller = loader.loadClass(PACKAGE + "BackAnimationController")
        val runner = loader.loadClass(PACKAGE + "BackAnimationRunner")
        val animation = loader.loadClass(PACKAGE + "ShellBackAnimation")
        val info = loader.loadClass("android.window.BackNavigationInfo")
        fun field(type: Class<*>, name: String) = type.getDeclaredField(name).apply { isAccessible = true }
        val defaultActivity = field(registry, "mDefaultCrossActivityAnimation")
        val defaultTask = field(registry, "mCrossTaskAnimation")
        val definitions = field(registry, "mAnimationDefinition")
        val tracker = field(controller, "mCurrentTracker")
        val controllerRegistry = field(controller, "mShellBackAnimationRegistry")
        val edge = field(tracker.type, "mSwipeEdge")
        val prepare = animation.getDeclaredMethod("prepareNextAnimation", loader.loadClass("android.window.BackNavigationInfo\$CustomAnimationInfo"), Int::class.javaPrimitiveType)
        val getRunner = animation.getDeclaredMethod("getRunner")
        val getType = info.getDeclaredMethod("getType")
        val getColor = info.getDeclaredMethod("getLetterboxColor")
        val resetWaiting = runner.getDeclaredMethod("resetWaitingAnimation")
        val startGesture = runner.getDeclaredMethod("startGesture")
        val isCancelled = runner.getDeclaredMethod("isAnimationCancelled")
        val select = registry.getDeclaredMethod("getAnimationRunnerAndInit", info)
        val register = registry.getDeclaredMethod("registerAnimation", Int::class.javaPrimitiveType, runner)
        val start = controller.getDeclaredMethod("startSystemAnimation").apply { isAccessible = true }
        val finish = controller.getDeclaredMethod("finishBackNavigation", Boolean::class.javaPrimitiveType)
        val request = ThreadLocal<Pair<Int, Int>>()
        val logged = hashSetOf<Int>()

        fun retain(owner: Any): Retained = synchronized(registries) {
            registries.getOrPut(owner) { Retained() }.also {
                defaultActivity.get(owner)?.let { native -> it.activity = native }
                defaultTask.get(owner)?.let { native -> it.task = native }
            }
        }
        // External providers can replace the registry entry and clear its default animation field.
        install(register) { chain ->
            chain.thisObject?.let(::retain)
            chain.proceed()
        }
        install(finish) { chain ->
            val owner = chain.thisObject?.let { controllerRegistry.get(it) }
            val retained = synchronized(registries) { registries[owner] }
            if (owner != null && retained != null) {
                @Suppress("UNCHECKED_CAST") val map = definitions.get(owner) as SparseArray<Any>
                for ((type, slot) in listOf(2 to retained.activitySlot, 3 to retained.taskSlot)) {
                    val current = map[type]
                    val restored = slot.restore(current)
                    if (restored !== current) {
                        current?.let { resetWaiting.invoke(it) }
                        if (restored == null) map.remove(type) else map.put(type, restored)
                    }
                }
            }
            // Restore before the host resets its default entry, rather than undoing that reset.
            chain.proceed()
        }
        install(start) { chain ->
            val previous = request.get()
            val owner = chain.thisObject ?: return@install chain.proceed()
            request.set(BackGestureSettings.xposed().predictive to edge.getInt(tracker.get(owner)))
            try { chain.proceed() }
            finally { if (previous == null) request.remove() else request.set(previous) }
        }
        install(select) { chain ->
            val owner = chain.thisObject ?: return@install chain.proceed()
            val navigation = chain.args.firstOrNull() ?: return@install chain.proceed()
            val type = getType.invoke(navigation) as Int
            if (type !in 2..3) return@install chain.proceed()
            val retained = retain(owner)
            val slot = if (type == 2) retained.activitySlot else retained.taskSlot
            @Suppress("UNCHECKED_CAST") val map = definitions.get(owner) as SparseArray<Any>
            val current = map[type]
            val restored = slot.restore(current)
            if (restored !== current) {
                current?.let { resetWaiting.invoke(it) }
                if (restored == null) map.remove(type) else map.put(type, restored)
                // The previous selected runner may have received this gesture's waiting flag.
                restored?.let { startGesture.invoke(it) }
            }
            val original = chain.proceed() ?: return@install null
            val choice = request.get()
            if (!BackGestureRules.predictiveRoute(choice?.first ?: 0, choice?.second ?: -1, type,
                    isCancelled.invoke(original) == true)) return@install original
            val candidate = if (type == 2 && activitySupported) retained.activity
                else if (type == 3 && taskSupported) retained.task else null
            if (candidate == null) {
                if (logged.add(-type)) HookLog.w(TAG, "AOSP predictive runner missing for type=$type; native route retained")
                return@install original
            }
            val selected = runCatching {
                prepare.invoke(candidate, null, getColor.invoke(navigation))
                getRunner.invoke(candidate)
            }.onFailure { HookLog.w(TAG, "AOSP predictive runner preparation unavailable", it) }.getOrNull()
                ?: return@install original
            if (selected !== original) {
                resetWaiting.invoke(original)
                startGesture.invoke(selected)
                map.put(type, slot.replace(original, selected))
            }
            if (logged.add(type)) HookLog.i(TAG, "AOSP predictive runner selected type=$type")
            selected
        }
        val stateKey = "$TAG:predictionRoutes"
        fun restore(snapshot: Any?) {
            synchronized(registries) {
                (snapshot as? Array<*>)?.forEach { value ->
                    val row = value as? Array<*> ?: return@forEach
                    val owner = row.getOrNull(0) ?: return@forEach
                    registries[owner] = Retained(row.getOrNull(1), row.getOrNull(2))
                }
            }
        }
        restore(HotReloadLifecycle.restoredState(stateKey))
        HotReloadLifecycle.register(stateKey,
            capture = { synchronized(registries) {
                registries.entries.map { arrayOf(it.key, it.value.activity, it.value.task) }.toTypedArray()
            } }, detach = { synchronized(registries) { registries.clear() } }, restore = ::restore,
            canReload = { synchronized(registries) { registries.values.none { it.activitySlot.active || it.taskSlot.active } } })
        (registry.declaredMethods.toList() + controller.declaredMethods).forEach {
            runCatching { ModernHookRuntime.requireModule().deoptimize(it) }
        }
    }
}
