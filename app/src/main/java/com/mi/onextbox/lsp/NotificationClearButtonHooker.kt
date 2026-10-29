package com.mi.onextbox.lsp

import android.view.View
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernMethodHook
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.WeakHashMap

internal object NotificationClearButtonHooker {
    private const val TAG = "ONextBox-NotificationClear"

    fun hook(loader: ClassLoader) {
        val stateKey = "$TAG:state@${System.identityHashCode(loader)}"
        val saved = DexTargetCache.restoredState(stateKey) as? Array<*>
        if (!LspConfig.isRoundNotificationClearButtonEnabledXposed() && saved == null) return
        val hookKey = "$TAG@${System.identityHashCode(loader)}"
        val keys = listOf("$hookKey:layout", "$hookKey:margin")
        var owner: NativeButton? = null
        runCatching {
            val targets = saved?.get(0) as? Array<*> ?: resolve(loader)
            val native = NativeButton(targets).also { owner = it }
            (targets[4] as Array<*>).filterIsInstance<Method>().forEach {
                ModernHookRuntime.requireModule().deoptimize(it)
            }
            for (index in keys.indices) {
                ModernHookRegistry.installCompat(keys[index], targets[index] as Method, object : ModernMethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.throwable == null) param.thisObject?.let(native::refresh)
                    }
                })
            }
            HotReloadLifecycle.register(stateKey,
                capture = { arrayOf(targets, native.snapshot()) },
                detach = { native.restoreAll() },
                restore = { state -> native.restoreSnapshot((state as? Array<*>)?.get(1)) },
            )
            HookLog.i(TAG, "Native round notification clear button hook installed")
        }.onFailure {
            owner?.restoreAll()
            keys.forEach(ModernHookRegistry::unhook)
            HookLog.w(TAG, "Notification clear button hook unavailable, stock behavior retained", it)
        }
    }

    private class NativeButton(targets: Array<*>) {
        private val button = (targets[2] as Field).apply { isAccessible = true }
        private val stockWidth = (targets[3] as Field).apply { isAccessible = true }
        private val resized = WeakHashMap<View, IntArray>()

        fun refresh(controller: Any) {
            val view = button.get(controller) as? View ?: return
            apply(view, stockWidth.getInt(controller))
        }

        private fun apply(view: View, width: Int) {
            val params = view.layoutParams ?: return
            val desired = NotificationClearButtonRules.width(
                LspConfig.isRoundNotificationClearButtonEnabledXposed(), width, params.height,
            )
            if (desired == width) {
                restore(view, width)
                return
            }
            if (params.width != desired || resized.containsKey(view)) {
                resized[view] = intArrayOf(width, desired)
            }
            if (params.width != desired) {
                params.width = desired
                view.layoutParams = params
            }
        }

        fun snapshot(): Array<Array<Any>> = resized.entries.map { (view, sizes) ->
            arrayOf<Any>(view, sizes[0], sizes[1])
        }.toTypedArray()

        private fun restore(view: View, width: Int? = null) {
            val sizes = resized.remove(view) ?: return
            val params = view.layoutParams ?: return
            val desired = NotificationClearButtonRules.restoreWidth(params.width, width ?: sizes[0], sizes[1])
            if (params.width != desired) {
                params.width = desired
                view.layoutParams = params
            }
        }

        fun restoreAll() = resized.keys.toList().forEach { restore(it) }

        fun restoreSnapshot(state: Any?) {
            (state as? Array<*>)?.forEach { entry ->
                val row = entry as? Array<*> ?: return@forEach
                val view = row.getOrNull(0) as? View ?: return@forEach
                val width = row.getOrNull(1) as? Int ?: return@forEach
                val applied = row.getOrNull(2) as? Int ?: return@forEach
                resized[view] = intArrayOf(width, applied)
                apply(view, width)
            }
        }
    }

    private fun resolve(loader: ClassLoader): Array<Any> {
        val controller = Class.forName("com.oplus.systemui.notification.clearall.ClearAllController", false, loader)
        val button = controller.getDeclaredField("clearAll")
        check(button.type.name == "com.oplus.systemui.notification.clearall.OplusClearAllButton")
        check(View::class.java.isAssignableFrom(button.type))
        val width = controller.getDeclaredField("iconWidth")
        check(width.type == Int::class.javaPrimitiveType)
        val layout = controller.getDeclaredMethod("updateLayoutParams", String::class.java)
        val margin = controller.getDeclaredMethod("updateLayoutParams", Int::class.javaPrimitiveType)
        check(listOf(layout, margin).all { it.returnType == Void.TYPE })
        val callers = controller.declaredMethods.filter {
            it.name in setOf("updateResource", "onConfigChanged", "bindViews")
        }.toTypedArray()
        check(callers.any { it.name == "updateResource" })
        check(callers.any { it.name == "onConfigChanged" })
        val marginCallers = runCatching {
            Class.forName("com.oplus.systemui.shade.NotificationPanelViewControllerExImp", false, loader)
                .declaredMethods.filter { it.name == "updateClearAllButtonLayoutParams" }.toTypedArray()
        }.getOrDefault(emptyArray())
        return arrayOf(layout, margin, button, width, callers + marginCallers)
    }
}
