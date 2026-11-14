package com.mi.onextbox.lsp

import android.view.View
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
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
        val hookKey = "$TAG@${System.identityHashCode(loader)}"
        val keys = listOf("$hookKey:layout", "$hookKey:margin")
        var owner: NativeButton? = null
        runCatching {
            val targets = saved?.get(0) as? Array<*> ?: resolve(loader)
            val native = NativeButton(targets).also { owner = it }
            (targets[4] as Array<*>).filterIsInstance<Method>().forEach {
                ModernHookRuntime.requireModule().deoptimize(it)
            }
            val callbacks = (keys.indices.map { keys[it] to (targets[it] as Method) } +
                (targets[0] as Method).declaringClass.declaredMethods
                    .filter { it.name in setOf("setVisible", "updateVisibility", "onStateChanged", "bindViews", "onHeightUpdated") }
                    .mapIndexed { index, method -> "$hookKey:visible:$index" to method })
            for ((key, method) in callbacks) {
                ModernHookRegistry.installCompat(key, method, object : ModernMethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.throwable == null) param.thisObject?.let(native::refresh)
                    }
                })
            }
            HotReloadLifecycle.register(stateKey,
                capture = { arrayOf(targets, native.snapshot()) },
                detach = { native.stop() },
                restore = { state ->
                    native.restoreSnapshot((state as? Array<*>)?.get(1))
                    native.start()
                },
            )
            native.start()
            HookLog.i(TAG, "Native round notification clear button hook installed")
        }.onFailure {
            owner?.stop()
            ModernHookRegistry.unhookPrefix(hookKey)
            HookLog.w(TAG, "Notification clear button hook unavailable, stock behavior retained", it)
        }
    }

    private class NativeButton(targets: Array<*>) {
        private val button = (targets[2] as Field).apply { isAccessible = true }
        private val stockWidth = (targets[3] as Field).apply { isAccessible = true }
        private val stockHeight = stockWidth.declaringClass.getDeclaredField("iconHeight").apply { isAccessible = true }
        private val resized = WeakHashMap<View, IntArray>()
        private val known = WeakHashMap<View, IntArray>()
        private val handler = Handler(Looper.getMainLooper())
        private val preferences = ModernHookRuntime.remotePreferences("lsp_features")
        private val refreshTask = Runnable {
            known.toMap().forEach { (view, native) -> apply(view, native[0], native[1]) }
        }
        private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key.startsWith("notification_clear_") ||
                key == "systemui_round_notification_clear_button") {
                handler.removeCallbacks(refreshTask)
                handler.post(refreshTask)
            }
        }

        fun start() {
            preferences?.registerOnSharedPreferenceChangeListener(listener)
            handler.post(refreshTask)
        }

        fun stop() {
            preferences?.unregisterOnSharedPreferenceChangeListener(listener)
            handler.removeCallbacks(refreshTask)
            restoreAll()
            known.clear()
        }

        fun refresh(controller: Any) {
            val view = button.get(controller) as? View ?: return
            val width = stockWidth.getInt(controller)
            val height = stockHeight.getInt(controller)
            known[view] = intArrayOf(width, height)
            apply(view, width, height)
        }

        private fun padding(view: View) = intArrayOf(view.paddingLeft, view.paddingTop,
            view.paddingRight, view.paddingBottom)

        private fun apply(view: View, width: Int, nativeHeight: Int? = null) {
            val params = view.layoutParams ?: return
            val round = LspConfig.isRoundNotificationClearButtonEnabledXposed()
            val custom = SystemCustomizationSettings.active(SystemCustomizationSettings.Feature.NotificationClearSize)
            if (!round && !custom) {
                restore(view, width)
                return
            }
            val previous = resized[view]
            val height = nativeHeight ?: if (previous != null && params.height == previous[3]) previous[1] else params.height
            if (width <= 0 || height <= 0) return
            val percent = if (custom) SystemCustomizationSettings.value(SystemCustomizationSettings.clearPercent) else 100
            val desiredWidth = SystemCustomizationRules.scaled(NotificationClearButtonRules.width(round, width, height), percent)
            val desiredHeight = SystemCustomizationRules.scaled(height, percent)
            val currentPadding = padding(view)
            val nativePadding = if (previous != null && previous.size >= 12 &&
                currentPadding.contentEquals(previous.copyOfRange(8, 12))) previous.copyOfRange(4, 8) else currentPadding
            val desiredPadding = nativePadding.map { SystemCustomizationRules.scaled(it, percent) }.toIntArray()
            resized[view] = intArrayOf(width, height, desiredWidth, desiredHeight) + nativePadding + desiredPadding
            if (!currentPadding.contentEquals(desiredPadding)) {
                view.setPadding(desiredPadding[0], desiredPadding[1], desiredPadding[2], desiredPadding[3])
            }
            if (params.width != desiredWidth || params.height != desiredHeight) {
                params.width = desiredWidth
                params.height = desiredHeight
                view.layoutParams = params
            }
        }

        fun snapshot(): Array<Array<Any>> = known.entries.map { (view, native) ->
            val sizes = resized[view] ?: (intArrayOf(native[0], native[1],
                view.layoutParams?.width ?: native[0], view.layoutParams?.height ?: native[1]) +
                padding(view) + padding(view))
            arrayOf<Any>(view, *sizes.toTypedArray())
        }.toTypedArray()

        private fun restore(view: View, width: Int? = null) {
            val sizes = resized.remove(view) ?: return
            val params = view.layoutParams ?: return
            val desired = NotificationClearButtonRules.restoreWidth(params.width, width ?: sizes[0], sizes[2])
            val height = NotificationClearButtonRules.restoreWidth(params.height, sizes[1], sizes[3])
            if (sizes.size >= 12 && padding(view).contentEquals(sizes.copyOfRange(8, 12))) {
                view.setPadding(sizes[4], sizes[5], sizes[6], sizes[7])
            }
            if (params.width != desired || params.height != height) {
                params.width = desired
                params.height = height
                view.layoutParams = params
            }
        }

        fun restoreAll() = resized.keys.toList().forEach { restore(it) }

        fun restoreSnapshot(state: Any?) {
            (state as? Array<*>)?.forEach { entry ->
                val row = entry as? Array<*> ?: return@forEach
                val view = row.getOrNull(0) as? View ?: return@forEach
                val width = row.getOrNull(1) as? Int ?: return@forEach
                val third = row.getOrNull(2) as? Int ?: return@forEach
                val sizes = if (row.size >= 5) intArrayOf(width, third,
                    row[3] as Int, row[4] as Int) else intArrayOf(width, view.layoutParams.height,
                    third, view.layoutParams.height)
                val savedPadding = row.drop(5).take(8).filterIsInstance<Int>().toIntArray()
                known[view] = intArrayOf(sizes[0], sizes[1])
                resized[view] = if (savedPadding.size == 8) sizes + savedPadding else sizes
                apply(view, width, sizes[1])
            }
        }
    }

    internal fun resolve(loader: ClassLoader): Array<Any> {
        val controller = Class.forName("com.oplus.systemui.notification.clearall.ClearAllController", false, loader)
        val button = controller.getDeclaredField("clearAll")
        check(button.type.name == "com.oplus.systemui.notification.clearall.OplusClearAllButton")
        check(View::class.java.isAssignableFrom(button.type))
        val width = controller.getDeclaredField("iconWidth")
        check(width.type == Int::class.javaPrimitiveType)
        check(controller.getDeclaredField("iconHeight").type == Int::class.javaPrimitiveType)
        val layout = try {
            controller.getDeclaredMethod("updateLayoutParams", String::class.java, Boolean::class.javaPrimitiveType)
        } catch (_: NoSuchMethodException) {
            controller.getDeclaredMethod("updateLayoutParams", String::class.java)
        }
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
