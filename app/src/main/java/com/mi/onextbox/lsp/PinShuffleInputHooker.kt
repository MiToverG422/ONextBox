package com.mi.onextbox.lsp

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.security.SecureRandom
import java.util.IdentityHashMap
import java.util.WeakHashMap

internal object PinShuffleInputHooker {
    private const val TAG = "ONextBox-PinShuffle"
    private const val DIAL = "com.coui.appcompat.passworddial.COUIPasswordDial"
    private const val MATRIX = "com.coui.appcompat.passworddial.key.PasswordDialMatrixLayout"
    private const val KEY = "com.coui.appcompat.passworddial.key.PasswordDialKeyView"
    private val hosts = setOf("com.oplus.keyguard.security.view.OplusKeyguardPinView",
        "com.oplus.keyguard.security.view.OplusKeyguardFixedPinView")
    private val random by lazy { SecureRandom() }
    private class Active(val matrix: ViewGroup, val positions: PinKeyPositions, var ending: Boolean = false)
    private val active = IdentityHashMap<ViewGroup, Active>()
    private val previous = WeakHashMap<ViewGroup, IntArray>()

    internal class Targets(loader: ClassLoader) {
        val dial = ModernReflect.findClass(DIAL, loader)
        private val matrix = ModernReflect.findClass(MATRIX, loader)
        val matrixField: Field = dial.declaredFields.single { it.type == matrix }.apply { isAccessible = true }
        val gridField: Field = matrix.declaredFields.single {
            !Modifier.isStatic(it.modifiers) && Map::class.java.isAssignableFrom(it.type)
        }.apply { isAccessible = true }
        val attach: Method = dial.getDeclaredMethod("onAttachedToWindow")
        val detach: Method = dial.getDeclaredMethod("onDetachedFromWindow")
        val visibility: Method = dial.getDeclaredMethod("onVisibilityChanged", View::class.java, Int::class.javaPrimitiveType)
        val enter: Method = dial.getDeclaredMethod("playEnterAnimIfNeeded")
        val disappear: Method = dial.getDeclaredMethod("prepareForHostDisappear")
    }

    fun hook(loader: ClassLoader) {
        runCatching {
            val targets = Targets(loader)
            fun shown(value: Any?) {
                val dial = value as? ViewGroup ?: return
                if (dial.javaClass != targets.dial) return
                if (dial.isAttachedToWindow && dial.isShown && dial.windowVisibility == View.VISIBLE) begin(dial, targets)
                else restore(dial)
            }
            after("attach", targets.attach, ::shown)
            after("visibility", targets.visibility, ::shown)
            after("enter", targets.enter, ::shown)
            after("window", View::class.java.getDeclaredMethod("onWindowVisibilityChanged", Int::class.javaPrimitiveType), ::shown)
            after("detach", targets.detach) { (it as? ViewGroup)?.let(::restore) }
            after("disappear", targets.disappear) { (it as? ViewGroup)?.let { dial -> active[dial]?.ending = true } }
            HotReloadLifecycle.register(TAG,
                canReload = { active.keys.none { it.isShown && it.windowVisibility == View.VISIBLE } },
                detach = { active.keys.toList().forEach(::restore); previous.clear() })
            HookLog.i(TAG, "Installed native PIN key-position shuffling")
        }.onFailure {
            ModernHookRegistry.unhookPrefix(TAG)
            active.keys.toList().forEach(::restore)
            HookLog.w(TAG, "PIN key-position grid unavailable; native layout retained", it)
        }
    }

    private fun after(key: String, method: Method, action: (Any?) -> Unit) {
        ModernHookRegistry.installCompat("$TAG:$key", method, object : ModernMethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) = action(param.thisObject)
        })
    }

    private fun begin(dial: ViewGroup, targets: Targets) {
        if (active[dial]?.ending == false) return
        restore(dial)
        if (!LspConfig.isKeyguardFeatureEnabledXposed(LspConfig.KeyguardFeature.ShufflePinKeys)) return
        var host = dial.parent
        while (host is View && host.javaClass.name !in hosts) host = host.parent
        if (host !is View) return
        runCatching {
            val matrix = targets.matrixField.get(dial) as ViewGroup
            val numbers = (0 until matrix.childCount).map { matrix.getChildAt(it) }
                .filter { it.javaClass.name == KEY }.filterIsInstance<TextView>()
                .map { key ->
                    val label = key.text.toString().trim()
                    require(label.length == 1)
                    Character.digit(label[0], 10) to key.id
                }.sortedBy { it.first }
            require(numbers.map { it.first } == (0..9).toList())
            val positions = PinKeyPositions.capture(targets.gridField.get(matrix) as Map<*, *>, numbers.map { it.second })
            val order = PinKeyPositions.randomOrder(random, previous[dial])
            active[dial] = Active(matrix, positions)
            positions.apply(order)
            previous[dial] = order
            matrix.requestLayout()
        }.onFailure {
            restore(dial)
            HookLog.w(TAG, "PIN shuffle skipped for an unrecognized number-key layout", it)
        }
    }

    private fun restore(dial: ViewGroup) {
        active.remove(dial)?.let { state ->
            state.positions.restore()
            state.matrix.requestLayout()
        }
    }
}
