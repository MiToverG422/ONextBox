package com.mi.onextbox.lsp

import android.widget.FrameLayout
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import java.util.WeakHashMap

internal class NetworkSpeedVisibility(viewClass: Class<*>, stateClass: Class<*>) {
    private val copy = stateClass.getDeclaredMethod("copy")
    private val speed = stateClass.getDeclaredMethod("getSpeedText")
    private val visible = stateClass.getDeclaredMethod("getVisible")
    private val setVisible = stateClass.getDeclaredMethod("setVisible", Boolean::class.javaPrimitiveType)
    private val currentState = viewClass.getDeclaredField("mState").apply { isAccessible = true }
    private val nativeApply = ModernHookRuntime.requireModule().getInvoker(
        viewClass.getDeclaredMethod("applyNetworkState", stateClass),
    ).setType(XposedInterface.Invoker.Type.ORIGIN)
    private val hiddenStates = WeakHashMap<FrameLayout, Any>()

    init {
        require(copy.returnType == stateClass && currentState.type == stateClass)
        require(speed.returnType == Long::class.javaPrimitiveType && visible.returnType == Boolean::class.javaPrimitiveType)
    }

    fun filter(view: FrameLayout, state: Any?): Any? {
        if (state == null) {
            hiddenStates.remove(view)
            return null
        }
        val settings = NetworkSpeedSettings.snapshot()
        if (visible.invoke(state) == true && NetworkSpeedRules.hideLowSpeed(
                speed.invoke(state) as Long, settings.hideLowSpeed, settings.lowSpeedThresholdKbps,
            )) {
            val original = copy.invoke(state)!!
            val filtered = copy.invoke(state)!!
            setVisible.invoke(filtered, false)
            hiddenStates[view] = original
            return filtered
        }
        hiddenStates.remove(view)
        return state
    }

    fun refresh(view: FrameLayout) {
        val original = hiddenStates[view] ?: currentState.get(view) ?: return
        val filtered = filter(view, original)
        nativeApply.invokeSpecial(view, filtered)
    }

    fun restore(view: FrameLayout) {
        val original = hiddenStates[view] ?: return
        nativeApply.invokeSpecial(view, original)
        hiddenStates.remove(view)
    }

    fun restoreAll() = hiddenStates.keys.toList().forEach(::restore)
}
