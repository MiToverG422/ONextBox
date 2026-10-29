package com.mi.onextbox.lsp

import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernMethodHook
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.WeakHashMap

internal object KeyboardDismissButtonHooker {
    private const val TAG = "ONextBox-KeyboardDismiss"

    fun hook(loader: ClassLoader) {
        val stateKey = "$TAG:state@${System.identityHashCode(loader)}"
        val saved = DexTargetCache.restoredState(stateKey) as? Array<*>
        if (!LspConfig.isKeyboardDismissHiddenXposed() && saved == null) return
        val hookKey = "$TAG@${System.identityHashCode(loader)}"
        val keys = listOf("$hookKey:icons", "$hookKey:shown", "$hookKey:detached")
        var owner: NativeButton? = null
        runCatching {
            val targets = saved?.get(0) as? Array<*> ?: resolve(loader)
            val native = NativeButton(targets).also { owner = it }
            (targets[7] as Array<*>).filterIsInstance<Method>().forEach {
                ModernHookRuntime.requireModule().deoptimize(it)
            }
            ModernHookRegistry.installCompat(keys[0], targets[0] as Method, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.throwable == null) (param.thisObject as? View)?.let {
                        native.refresh(it, nativeUpdated = true)
                    }
                }
            })
            ModernHookRegistry.installCompat(keys[1], targets[2] as Method, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.throwable == null) native.navigationView(param.thisObject)?.let {
                        native.refresh(it)
                    }
                }
            })
            ModernHookRegistry.installCompat(keys[2], targets[1] as Method, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    (param.thisObject as? View)?.let(native::restore)
                }
            })
            HotReloadLifecycle.register(stateKey,
                capture = { arrayOf(targets, native.snapshot()) },
                detach = { native.restoreAll() },
                restore = { state -> native.restoreSnapshot((state as? Array<*>)?.get(1)) },
            )
            HookLog.i(TAG, "Native IME dismiss button hook installed")
        }.onFailure {
            owner?.restoreAll()
            keys.forEach(ModernHookRegistry::unhook)
            HookLog.w(TAG, "IME dismiss button hook unavailable, stock behavior retained", it)
        }
    }

    private class NativeButton(targets: Array<*>) {
        private val frame = (targets[3] as Field).apply { isAccessible = true }
        private val getBackButton = (targets[4] as Method).apply { isAccessible = true }
        private val setVisibility = (targets[5] as Method).apply { isAccessible = true }
        private val getVisibility = (targets[6] as Method).apply { isAccessible = true }
        private val hidden = WeakHashMap<View, Int>()

        fun navigationView(controller: Any?): View? {
            fun find(view: View): View? {
                if (getBackButton.declaringClass.isInstance(view)) return view
                if (view is ViewGroup) for (index in 0 until view.childCount) {
                    find(view.getChildAt(index))?.let { return it }
                }
                return null
            }
            return (frame.get(controller) as? View)?.let(::find)
        }

        fun refresh(view: View, nativeUpdated: Boolean = false, restoredStock: Int? = null) {
            val button = getBackButton.invoke(view) ?: return
            val current = getVisibility.invoke(button) as Int
            val stock = KeyboardDismissButtonRules.stockVisibility(current, restoredStock ?: hidden[view], nativeUpdated)
            val visibility = KeyboardDismissButtonRules.visibility(
                LspConfig.isKeyboardDismissHiddenXposed(), view.context.packageName,
                Settings.Secure.getInt(view.context.contentResolver, "navigation_mode", 0) == 2, stock,
            )
            if (visibility != stock) hidden[view] = stock else hidden.remove(view)
            if (visibility != current) setVisibility.invoke(button, visibility)
        }

        fun snapshot(): Array<Array<Any>> = hidden.entries.map { (view, stock) ->
            arrayOf<Any>(view, stock)
        }.toTypedArray()

        fun restore(view: View) {
            val stock = hidden.remove(view) ?: return
            val button = getBackButton.invoke(view) ?: return
            if (getVisibility.invoke(button) == View.INVISIBLE) setVisibility.invoke(button, stock)
        }

        fun restoreAll() = hidden.keys.toList().forEach(::restore)

        fun restoreSnapshot(state: Any?) {
            (state as? Array<*>)?.forEach { entry ->
                val row = entry as? Array<*> ?: return@forEach
                val view = row.getOrNull(0) as? View ?: return@forEach
                val stock = row.getOrNull(1) as? Int ?: return@forEach
                refresh(view, restoredStock = stock)
            }
        }
    }

    private fun resolve(loader: ClassLoader): Array<Any> {
        val view = Class.forName("android.inputmethodservice.navigationbar.NavigationBarView", false, loader)
        val controller = Class.forName("android.inputmethodservice.NavigationBarController", false, loader)
        check(ViewGroup::class.java.isAssignableFrom(view))
        val back = view.getDeclaredMethod("getBackButton")
        val dispatcher = back.returnType
        check(dispatcher.name == "android.inputmethodservice.navigationbar.ButtonDispatcher")
        val update = view.getDeclaredMethod("updateNavButtonIcons")
        val detach = view.getDeclaredMethod("onDetachedFromWindow")
        val shown = controller.getDeclaredMethod("onWindowShown")
        val frame = controller.getDeclaredField("mNavigationBarFrame")
        check(ViewGroup::class.java.isAssignableFrom(frame.type))
        val setVisibility = dispatcher.getDeclaredMethod("setVisibility", Int::class.javaPrimitiveType)
        val getVisibility = dispatcher.getDeclaredMethod("getVisibility")
        check(getVisibility.returnType == Int::class.javaPrimitiveType)
        check(listOf(update, detach, shown, setVisibility).all { it.returnType == Void.TYPE })
        val callers = view.declaredMethods.filter {
            it.name in setOf("setNavbarFlags", "reorient", "onConfigurationChanged", "onAttachedToWindow")
        }.toTypedArray()
        check(callers.size == 4)
        return arrayOf(update, detach, shown, frame, back, setVisibility, getVisibility, callers)
    }
}
