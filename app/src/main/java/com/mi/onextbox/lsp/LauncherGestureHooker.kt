package com.mi.onextbox.lsp

import android.content.Context
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

internal object LauncherGestureHooker {
    private const val TAG = "ONextBox-LauncherGesture"
    private val reported = ConcurrentHashMap.newKeySet<String>()
    private fun report(stage: String) { if (reported.add(stage)) HookLog.i(TAG, "Native launcher adapter reached: $stage") }
    fun hook(loader: ClassLoader) {
        runCatching { hookMenu(loader) }.onFailure { HookLog.w(TAG, "Secondary-menu adapter unavailable", it) }
        runCatching { LauncherWidgetMenuHooker.hook(loader) }.onFailure { HookLog.w(TAG, "Widget-menu adapter unavailable", it) }
        runCatching { hookTop(loader) }.onFailure { HookLog.w(TAG, "Swipe-top adapter unavailable", it) }
    }
    private fun deopt(type: Class<*>, names: Set<String>) {
        type.declaredMethods.filter { it.name in names }.forEach { method ->
            runCatching { ModernHookRuntime.requireModule().deoptimize(method) }
                .onFailure { if (reported.add("deopt:${type.name}")) HookLog.w(TAG, "Native caller deoptimization unavailable: ${type.name}", it) }
        }
    }
    private fun hookMenu(loader: ClassLoader) {
        // Clear only the native secondary-menu grouping flag, not the actual app shortcuts.
        // C17 writes it before querying notification keys, then uses it to choose More functions
        // versus direct app-info/share/edit entries. Actual shortcut counts/lists stay unchanged.
        val item = ModernReflect.findClass("com.android.launcher3.model.data.ItemInfo", loader)
        val count = item.getDeclaredField("mAddShortcutCount").apply { isAccessible = true }
        check(count.type == Int::class.javaPrimitiveType)
        val provider = ModernReflect.findClass("com.android.launcher3.popup.PopupDataProvider", loader)
        val method = provider.getDeclaredMethod("getNotificationKeysForItem", item)
        check(List::class.java.isAssignableFrom(method.returnType))
        ModernHookRegistry.installFast("$TAG:secondary-menu:${method.toGenericString()}", method, XposedInterface.Hooker { chain ->
            if (LauncherGestureSettings.menuXposed()) {
                val info = chain.args.firstOrNull()
                if (item.isInstance(info)) {
                    val previous = count.getInt(info)
                    count.setInt(info, LauncherGestureRules.secondaryMenuGroupingCount(true, previous))
                    report("secondary-menu-flattened grouping=$previous->0")
                }
            }
            // Preserve notification content, deep shortcuts and native popup creation.
            chain.proceed()
        })
        if (LauncherGestureSettings.menuXposed()) {
            // Keep compiled callers from bypassing the notification-key hook.
            listOf(
                "com.android.launcher3.popup.model.ShortcutDataBuilder" to setOf("buildPopupData"),
                "com.android.launcher3.popup.presenter.Launcher3PopupPresenter" to setOf("showForLauncherIcon"),
                "com.android.launcher3.popup.OplusPopupContainerWithArrow" to setOf("showForIcon"),
            ).forEach { (name, methods) ->
                ModernReflect.findClassIfExists(name, loader)?.let { caller ->
                    deopt(caller, methods)
                }
            }
        }
        HookLog.i(TAG, "Native secondary-menu grouping hook installed")
    }
    private fun hookTop(loader: ClassLoader) {
        val c16 = SystemAdaptationDetector.current() == SystemAdaptationVersion.ColorOs16
        val prefix = "com.oplus.quickstep.rapidreaction."
        val base = ModernReflect.findClass("${prefix}panelparams.TriggerPanelParams", loader)
        val options = ModernReflect.findClass("${base.name}\$SelectionOptions", loader)
        val enums = requireNotNull(options.enumConstants).associateBy { (it as Enum<*>).name }
        val floating = requireNotNull(enums["FLOATING_WINDOW"])
        val none = requireNotNull(enums["NONE"])
        val axisFor = base.getDeclaredMethod(if (c16) "getEntranceAxisCode" else "c", options).apply { isAccessible = true }
        val widths = WeakHashMap<Any, Int>()
        fun restoreWidths(state: Any?) {
            (state as? List<*>)?.forEach { entry ->
                val pair = entry as? List<*> ?: return@forEach
                val owner = pair.getOrNull(0) ?: return@forEach
                val width = pair.getOrNull(1) as? Int ?: return@forEach
                if (base.isInstance(owner) && width > 0) synchronized(widths) { widths[owner] = width }
            }
        }
        val ownerKey = "$TAG:panel-widths"
        HotReloadLifecycle.register(ownerKey,
            capture = { synchronized(widths) { widths.entries.map { listOf(it.key, it.value) } } },
            detach = { synchronized(widths) { widths.clear() } }, restore = ::restoreWidths)
        HotReloadLifecycle.restoredState(ownerKey)?.let(::restoreWidths)
        (if (c16) listOf("SplitFloatParams", "SplitCapsuleFloatParams") else listOf("a", "b")).forEach { name ->
            val type = ModernReflect.findClass("${prefix}panelparams.$name", loader)
            check(base.isAssignableFrom(type))
            val update = type.getDeclaredMethod(if (c16) "updateParams" else "l", Context::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            val select = if (c16) type.getDeclaredMethod("calculateSelectArea", Float::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            else type.getDeclaredMethod("a", Float::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            val decode = type.getDeclaredMethod(if (c16) "getEntranceType" else "d", Int::class.javaPrimitiveType)
            ModernHookRegistry.installCompat("$TAG:bounds:$name", update, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val owner = param.thisObject ?: return
                    val context = param.args[0] as Context
                    synchronized(widths) { widths[owner] = context.resources.displayMetrics.widthPixels }
                }
            })
            ModernHookRegistry.installCompat("$TAG:axis:$name", select, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.throwable != null || !LauncherGestureSettings.topXposed()) return
                    val owner = param.thisObject ?: return
                    val original = param.result as? Int ?: return
                    val floatingAxis = axisFor.invoke(owner, floating) as Int
                    val next = LauncherGestureRules.selectAxis(original, floatingAxis, param.args[1] as Int,
                        synchronized(widths) { widths[owner] } ?: 0, param.args[2] as Int, LauncherGestureSettings.widthXposed())
                    param.result = next
                    if (next != original) report("floating-hit-area")
                }
            })
            ModernHookRegistry.installCompat("$TAG:split:$name", decode, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.throwable == null && LauncherGestureRules.blockSplit(LauncherGestureSettings.topXposed(),
                        (param.result as? Enum<*>)?.name)) {
                        param.result = none
                        report("swipe-split-disabled")
                    }
                }
            })
            deopt(type, if (c16) setOf("calculateSelectArea", "getEntranceAxisCode", "getEntranceType", "updateParams")
                else setOf("a", "c", "d", "l"))
        }
        val panel = ModernReflect.findClass("${prefix}widget.MultiTriggerPanelView", loader)
        // Suppress ONLY this panel's child rendering, not visibility/alpha/progress or
        // animation callbacks used by native gesture recognition and window creation.
        val draw = ViewGroup::class.java.getDeclaredMethod("dispatchDraw", Canvas::class.java)
        ModernHookRegistry.installFast("$TAG:panel-draw", draw, XposedInterface.Hooker { chain ->
            if (panel.isInstance(chain.thisObject) && LauncherGestureSettings.topXposed()) {
                report("swipe-panel-hidden")
                null
            } else chain.proceed()
        })
        deopt(panel, setOf("i", "k", "onLayout"))
        deopt(ModernReflect.findClass("${prefix}MultiTriggerAnimController", loader), setOf("f", "g", "i", "e", "d"))
        HookLog.i(TAG, "Native swipe-top selection and rendering hooks installed")
    }
}
