package com.mi.onextbox.lsp

import android.content.res.Configuration
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowInsets
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernMethodHook
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.IdentityHashMap
import java.util.zip.ZipFile

internal object KeyboardBottomLiftHooker {
    private const val TAG = "ONextBox-KeyboardLift"

    fun hook(loader: ClassLoader, apkPath: String) {
        val stateKey = "$TAG:state@${System.identityHashCode(loader)}"
        val saved = DexTargetCache.restoredState(stateKey) as? Array<*>
        if (!LspConfig.isKeyboardBottomLiftFixEnabledXposed() && saved == null) return
        val hookKey = "$TAG@${System.identityHashCode(loader)}"
        val keys = listOf("$hookKey:insets", "$hookKey:shown", "$hookKey:detached")
        var refresh: NativeRefresh? = null
        runCatching {
            val targets = saved?.get(0) as? Array<*> ?: resolve(loader, apkPath)
            val native = NativeRefresh(targets).also { refresh = it }
            ModernHookRegistry.installCompat(keys[0], native.updateInsets, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (param.args[0] != null) return
                    val root = native.ownerRoot(param.thisObject) ?: return
                    if (!shouldRefresh(root)) return
                    native.windowInsets(root)?.let { param.args[0] = it }
                }
            })
            ModernHookRegistry.installCompat(keys[1], targets[0] as Method, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.throwable != null) return
                    val root = native.serviceRoot.get(param.thisObject) as? View ?: return
                    native.schedule(root)
                }
            })
            ModernHookRegistry.installCompat(keys[2], targets[1] as Method, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    (param.thisObject as? View)?.let(native::cancel)
                }
            })
            HotReloadLifecycle.register(stateKey,
                capture = { arrayOf(targets, native.pendingViews()) },
                detach = { native.cancelAll() },
                restore = { state ->
                    ((state as? Array<*>)?.get(1) as? Array<*>)?.filterIsInstance<View>()
                        ?.forEach(native::schedule)
                },
            )
            HookLog.i(TAG, "Native keyboard bottom lift initialization fix installed")
        }.onFailure {
            refresh?.cancelAll()
            keys.forEach(ModernHookRegistry::unhook)
            HookLog.w(TAG, "Bottom lift fix unavailable, stock behavior retained", it)
        }
    }

    private fun shouldRefresh(view: View, requireReady: Boolean = true): Boolean = KeyboardBottomLiftRules.shouldRefresh(
        LspConfig.isKeyboardBottomLiftFixEnabledXposed(),
        Settings.Secure.getInt(view.context.contentResolver, "keyboard_position", 0) == 1,
        Settings.Secure.getInt(view.context.contentResolver, "navigation_mode", 0) == 2,
        view.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT,
        !requireReady || view.isAttachedToWindow, !requireReady || view.isShown,
    )

    private class NativeRefresh(targets: Array<*>) {
        val serviceRoot = (targets[2] as Field).apply { isAccessible = true }
        val delegate = (targets[3] as Field).apply { isAccessible = true }
        private val ownerFields = (targets[4] as Array<*>).filterIsInstance<Field>()
            .onEach { it.isAccessible = true }
        val updateInsets = (targets[5] as Method).apply { isAccessible = true }
        private val wrapInsets = (targets[6] as Method).apply { isAccessible = true }
        private val adjustHelper = (targets[7] as Method).apply { isAccessible = true }
        private val refreshMargin = (targets[8] as Method).apply { isAccessible = true }
        private val keyboardLayout = (targets[9] as Method).apply { isAccessible = true }
        private val pending = IdentityHashMap<View, Pending>()

        private data class Pending(val observer: ViewTreeObserver, val listener: ViewTreeObserver.OnPreDrawListener)

        fun ownerRoot(instance: Any?): View? = ownerFields.asSequence()
            .mapNotNull { it.get(instance) as? View }
            .firstOrNull { serviceRoot.type.isInstance(it) }

        fun windowInsets(view: View): Any? = view.rootWindowInsets?.let { insets ->
            val args = wrapInsets.parameterTypes.map { if (it == View::class.java) view else insets }
            wrapInsets.invoke(null, *args.toTypedArray())
        }

        fun schedule(view: View) {
            cancel(view)
            if (!shouldRefresh(view, requireReady = false)) return
            val observer = view.viewTreeObserver
            if (!observer.isAlive) return
            val listener = ViewTreeObserver.OnPreDrawListener {
                HotReloadLifecycle.withHook({ true }) {
                    cancel(view)
                    if (!shouldRefresh(view)) return@withHook true
                    runCatching {
                        val insets = windowInsets(view) ?: return@runCatching true
                        val layout = keyboardLayout.invoke(view) as? View ?: return@runCatching true
                        val before = (layout.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin
                        updateInsets.invoke(delegate.get(view), insets)
                        refreshMargin.invoke(adjustHelper.invoke(view))
                        val after = (layout.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin
                        if (before != after) {
                            HookLog.i(TAG, "Initial bottom margin synchronized: $before -> $after")
                            false
                        } else true
                    }.getOrElse {
                        HookLog.w(TAG, "Initial height synchronization skipped", it)
                        true
                    }
                }
            }
            pending[view] = Pending(observer, listener)
            observer.addOnPreDrawListener(listener)
            view.requestApplyInsets()
            view.invalidate()
        }

        fun pendingViews(): Array<View> = pending.keys.toTypedArray()

        fun cancel(view: View) {
            val task = pending.remove(view) ?: return
            if (task.observer.isAlive) task.observer.removeOnPreDrawListener(task.listener)
            else if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnPreDrawListener(task.listener)
        }

        fun cancelAll() = pendingViews().forEach(::cancel)
    }

    private fun resolve(loader: ClassLoader, apkPath: String): Array<Any> {
        System.loadLibrary("dexkit")
        val relevantDex = ZipFile(apkPath).use { apk ->
            apk.entries().asSequence().filter { it.name.matches(Regex("classes\\d*\\.dex")) }
                .map { entry -> apk.getInputStream(entry).use { it.readBytes() } }
                .first { KeyboardBottomLiftRules.containsHeightAnchors(it) }
        }
        return DexKitBridge.create(arrayOf(relevantDex)).use { dex ->
            dex.setThreadNum(1)
            val attached = dex.findMethod {
                matcher {
                    name = "onAttachedToWindow"
                    usingStrings("registerKbPositionRaiseObserver failed")
                    returnType = "void"
                    paramTypes()
                }
            }.single().getMethodInstance(loader)
            val container = attached.declaringClass
            check(View::class.java.isAssignableFrom(container))
            val getter = container.declaredMethods.single {
                it.name.startsWith("getBlurDelegateAdjustHelper") && it.parameterCount == 0
            }
            val observer = dex.findMethod {
                matcher {
                    usingStrings("kbPositionRaiseObserver onChange:")
                    returnType = "void"
                    paramTypes("boolean")
                }
            }.single()
            val margin = observer.invokes.single {
                it.className == getter.returnType.name && it.returnTypeName == "void" &&
                    it.paramTypeNames.isEmpty()
            }.getMethodInstance(loader)
            val delegates = container.declaredFields.mapNotNull { field ->
                if (Modifier.isStatic(field.modifiers) || field.type.isPrimitive) return@mapNotNull null
                if (field.type.declaredConstructors.none {
                        it.parameterTypes.contentEquals(arrayOf(container))
                    }) return@mapNotNull null
                val candidates = field.type.declaredMethods.mapNotNull methods@{ method ->
                    if (Modifier.isStatic(method.modifiers) || method.returnType != Void.TYPE ||
                        method.parameterCount != 1) return@methods null
                    val compat = method.parameterTypes[0]
                    val wrap = compat.declaredMethods.singleOrNull {
                        Modifier.isStatic(it.modifiers) && it.returnType == compat &&
                            it.parameterCount == 2 && it.parameterTypes.toSet() ==
                            setOf(View::class.java, WindowInsets::class.java)
                    } ?: return@methods null
                    method to wrap
                }
                candidates.singleOrNull()?.let { Triple(field, it.first, it.second) }
            }.single()
            val ownerFields = delegates.first.type.declaredFields.filter {
                !Modifier.isStatic(it.modifiers) &&
                    (it.type == Any::class.java || container.isAssignableFrom(it.type))
            }.toTypedArray()
            check(ownerFields.isNotEmpty())
            val service = Class.forName("com.oplus.keyboard.service.ImeService", false, loader)
            val serviceRoot = service.declaredFields.single {
                !Modifier.isStatic(it.modifiers) && it.type == container
            }
            arrayOf(service.getDeclaredMethod("onWindowShown"),
                container.getDeclaredMethod("onDetachedFromWindow"), serviceRoot,
                delegates.first, ownerFields, delegates.second, delegates.third, getter, margin,
                container.getDeclaredMethod("getKeyboardLayout"))
        }
    }
}
