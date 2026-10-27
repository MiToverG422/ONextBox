package com.mi.onextbox.lsp

import android.content.Context
import android.graphics.Rect
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect
import java.util.concurrent.atomic.AtomicBoolean

internal object StatusBarLayoutHooker {
    private const val TAG = "ONextBox-StatusBarLayout"
    fun hook(loader: ClassLoader) {
        hook(loader, "status_bar_icon_container_height")
    }

    fun hookColorOs16(loader: ClassLoader) {
        hook(loader, "status_bar_system_icons_height")
    }

    private fun hook(loader: ClassLoader, iconHeightResource: String) {
        runCatching { hookOrder(loader) }.onFailure { HookLog.w(TAG, "Icon-order adapter unavailable", it) }
        runCatching { hookMargins(loader, iconHeightResource) }.onFailure { HookLog.w(TAG, "Margin adapter unavailable", it) }
    }

    private fun hookOrder(loader: ClassLoader) {
        val type = ModernReflect.findClass("com.android.systemui.statusbar.phone.ui.StatusBarIconList", loader)
        val slots = type.getDeclaredField("mSlots").apply { isAccessible = true }
        val slot = ModernReflect.findClass("${type.name}\$Slot", loader)
        val name = slot.getMethod("getName")
        val constructor = type.getDeclaredConstructor(Array<String>::class.java)
        val reached = AtomicBoolean()
        ModernHookRegistry.installCompat("$TAG:order", constructor, object : ModernMethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (param.throwable != null || !StatusBarLayoutSettings.orderEnabledXposed()) return
                // Run after construction so explicit custom order wins over the legacy C16 preset.
                val list = slots.get(param.thisObject) as? MutableList<*> ?: return
                val names = list.map { name.invoke(it) as String }
                val sortedNames = StatusBarLayoutRules.reorder(names, StatusBarLayoutSettings.orderXposed())
                val buckets = list.groupBy { name.invoke(it) as String }.mapValues { it.value.toMutableList() }
                val sorted = sortedNames.map { buckets.getValue(it).removeAt(0) }
                @Suppress("UNCHECKED_CAST")
                (list as MutableList<Any?>).apply { clear(); addAll(sorted) }
                if (reached.compareAndSet(false, true)) HookLog.i(TAG, "Native status-bar slots reordered")
            }
        })
        HookLog.i(TAG, "Native icon-order hook installed")
    }

    private fun hookMargins(loader: ClassLoader, iconHeightResource: String) {
        val type = ModernReflect.findClass("com.oplus.systemui.statusbar.phone.StatusBarPaddingProvider", loader)
        val pair = ModernReflect.findClass("kotlin.Pair", loader)
        val method = type.getDeclaredMethod("getHomeStatusBarLeftRightPadding", String::class.java, pair, pair)
        val context = type.getDeclaredField("context").apply { isAccessible = true }
        val first = pair.getMethod("getFirst")
        val second = pair.getMethod("getSecond")
        val reached = AtomicBoolean()
        val verticalReached = AtomicBoolean()
        val verticalFailure = AtomicBoolean()
        ModernHookRegistry.installCompat("$TAG:margins", method, object : ModernMethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (param.throwable != null) return
                val offsets = StatusBarLayoutSettings.marginsXposed()
                val vertical = StatusBarLayoutSettings.verticalMarginsXposed()
                if (offsets == null && vertical == null) return
                val original = param.result as? Rect ?: return
                val ownerContext = context.get(param.thisObject) as Context
                val density = ownerContext.resources.displayMetrics.density
                // Always start with native geometry. Neither axis accumulates offsets
                // on a View, and the separate keyguard padding path is untouched.
                val result = Rect(original)
                if (offsets != null) {
                    val cutouts = param.args[2]
                    if (cutouts != null) {
                        val leftSafe = (first.invoke(cutouts) as Number).toInt()
                        val rightSafe = (second.invoke(cutouts) as Number).toInt()
                        result.left = StatusBarLayoutRules.padding(original.left, offsets.first, density, leftSafe)
                        result.right = StatusBarLayoutRules.padding(original.right, offsets.second, density, rightSafe)
                        if (reached.compareAndSet(false, true)) HookLog.i(TAG, "Native home status-bar side margins adjusted")
                    }
                }
                if (vertical != null) runCatching {
                    // The provider refreshes this height for the current rotation and
                    // display before returning the Rect. Retain the native icon height.
                    val height = (ModernReflect.getObjectField(requireNotNull(param.thisObject), "statusBarHeight") as Number).toInt()
                    val resources = ownerContext.resources
                    val iconId = resources.getIdentifier(iconHeightResource, "dimen", "com.android.systemui")
                    if (iconId == 0) return@runCatching
                    val padding = StatusBarLayoutRules.verticalPadding(original.top, original.bottom,
                        vertical.first, vertical.second, density, height, resources.getDimensionPixelSize(iconId))
                    result.top = padding.first
                    result.bottom = padding.second
                    if (verticalReached.compareAndSet(false, true)) HookLog.i(TAG, "Native home status-bar vertical margins adjusted")
                }.onFailure {
                    if (verticalFailure.compareAndSet(false, true)) HookLog.w(TAG, "Vertical margins unavailable; native geometry retained", it)
                }
                param.result = result
            }
        })
        ModernReflect.findClassIfExists("com.oplus.systemui.statusbar.phone.PhoneStatusBarViewExImpl", loader)
            ?.declaredMethods?.filter { it.name == "updateContentsPadding" }?.forEach {
                runCatching { ModernHookRuntime.requireModule().deoptimize(it) }
                    .onFailure { error -> HookLog.w(TAG, "Padding caller deoptimization unavailable", error) }
            }
        HookLog.i(TAG, "Native side/vertical-margin hook installed")
    }
}
