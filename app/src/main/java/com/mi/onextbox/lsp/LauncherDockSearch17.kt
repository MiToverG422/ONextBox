package com.mi.onextbox.lsp

import android.content.Context
import android.database.ContentObserver
import android.os.Bundle
import android.provider.Settings
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect as Reflect
import java.lang.reflect.Method

/** Launcher builds with bottom-search initialization inlined into LauncherSharedPrefs. */
internal object LauncherDockSearch17 {
    private const val PACKAGE = "com.android.launcher.bottomsearch."
    private const val TAG = "ONextBox-Launcher"

    fun matches(loader: ClassLoader): Boolean = runCatching {
        val base = loader.loadClass("yt0")
        loader.loadClass("cu0").getField("c").type == base &&
            loader.loadClass(PACKAGE + "e").superclass == base && loader.loadClass(PACKAGE + "c").superclass == base
    }.getOrDefault(false)

    private fun hook(method: Method, before: Boolean = false, body: (ModernMethodHook.MethodHookParam) -> Unit) {
        ModernHookRegistry.installCompat("dockSearch:" + method.toGenericString(), method, object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) { if (before) body(param) }
            override fun afterHookedMethod(param: MethodHookParam) { if (!before && param.throwable == null) body(param) }
        })
    }

    // R8 inlined the constructors into their callers. Run the same Object constructor
    // and populate all non-default fields before publishing the proxy to the launcher.
    private fun allocate(type: Class<*>): Any = ModernHookRuntime.requireModule()
        .getInvoker(Any::class.java.getDeclaredConstructor()).newInstanceSpecial(type)

    fun install(loader: ClassLoader, mode: Int) {
        val international = mode == LspConfig.LAUNCHER_SEARCH_BAR_MODE_INTERNATIONAL
        val manager = loader.loadClass("cu0")
        val proxy = manager.getField("c")
        val selected = loader.loadClass(PACKAGE + if (international) "e" else "c")
        val support = selected.getDeclaredMethod("c", Context::class.java)
        val utils = loader.loadClass(PACKAGE + "a")
        val init = loader.loadClass("com.android.common.util.LauncherSharedPrefs")
            .getDeclaredMethod("initOtaVersionUpdate", Context::class.java)
        val region = loader.loadClass("rp3").getField("N")
        fun selectProxy(context: Context) {
            if (selected.isInstance(proxy.get(null))) return
            val instance = allocate(selected)
            val mutex = loader.loadClass("kotlinx.coroutines.sync.a").getDeclaredConstructor().newInstance()
            if (international) {
                selected.getField("q").set(instance, mutex)
                selected.getField("r").setInt(instance, -1)
                selected.getField("s").set(instance, selected.getField("w").get(null))
                selected.getField("u").set(instance, "ExportBottomSearch")
                val key = "selected_search_engine_aga"
                Reflect.callMethod(selected.getField("v").get(null), "i", Settings.Secure.getString(context.contentResolver, key))
                context.contentResolver.registerContentObserver(Settings.Secure.getUriFor(key), true,
                    selected.getField("D").get(null) as ContentObserver)
            } else {
                selected.getField("r").set(instance, mutex)
                selected.getField("s").set(instance, loader.loadClass("wf1").getDeclaredConstructor().newInstance())
                selected.getField("t").setInt(instance, -1)
            }
            proxy.set(null, instance)
        }
        hook(init, before = true) { param ->
            if (region.getBoolean(null) != international) selectProxy(param.args[0] as Context)
        }
        val launcher = loader.loadClass("com.android.launcher.Launcher")
        hook(launcher.getDeclaredMethod("onCreate", Bundle::class.java), before = true) { param ->
            selectProxy(param.thisObject as Context)
            HookLog.i(TAG, "Dock search mode=$mode, active=" + proxy.get(null)?.javaClass?.name)
        }
        if (international) {
            val probe = selected.getDeclaredMethod("t", Context::class.java)
            val cached = selected.getField("r")
            hook(support, before = true) { param ->
                val context = param.args[0] as? Context ?: return@hook
                if (cached.getInt(param.thisObject) == -1) probe.invoke(param.thisObject, context)
                param.result = cached.getInt(param.thisObject) == 1
            }
            hook(manager.getDeclaredMethod("m", Context::class.java), before = true) { param ->
                val impl = proxy.get(null) ?: return@hook
                param.result = support.invoke(impl, param.args[0]) == true &&
                    Reflect.callStaticMethod(utils, "n", param.args[0]) == true
            }
            hook(utils.getDeclaredMethod("q", Context::class.java, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)) { param ->
                if (param.args[2] == true && !region.getBoolean(null)) {
                    val launcher = Reflect.callStaticMethod(loader.loadClass("com.android.launcher3.taskbar.TaskbarUtils"), "getOplusLauncher")
                    val impl = proxy.get(null)
                    if (launcher != null && selected.isInstance(impl)) Reflect.callMethod(impl, "l", launcher, false)
                }
            }
        } else {
            val providerSupported = selected.getDeclaredMethod("p", Context::class.java)
            val cached = selected.getField("t")
            hook(support, before = true) { param ->
                val context = param.args[0] as? Context ?: loader.loadClass("com.android.common.LauncherApplication")
                    .getField("q").get(null) as? Context ?: return@hook
                if (cached.getInt(param.thisObject) == -1) providerSupported.invoke(param.thisObject, context)
                param.result = cached.getInt(param.thisObject) == 1
            }
            // The same product filter was also inlined in the widget scheduling path.
            // Dispatch the launcher's own job; it owns the switch, widget host and removal.
            val baseLauncher = loader.loadClass("com.android.launcher3.Launcher")
            val job = loader.loadClass(PACKAGE + "DomesticBottomSearch\$addOrDeleteBottomSearchWidget\$2")
                .getDeclaredConstructor(selected, baseLauncher, Boolean::class.javaPrimitiveType, loader.loadClass("dg2"))
                .apply { isAccessible = true }
            val scope = loader.loadClass("androidx.lifecycle.b").getDeclaredMethod("a", loader.loadClass("tx5"))
            val dispatch = loader.loadClass("n92").getDeclaredMethod("E", loader.loadClass("ih2"),
                loader.loadClass("vg2"), loader.loadClass("kotlinx.coroutines.CoroutineStart"),
                loader.loadClass("kotlin.jvm.functions.Function2"), Int::class.javaPrimitiveType)
            hook(selected.getDeclaredMethod("d", launcher), before = true) { param ->
                param.result = support.invoke(param.thisObject, param.args[0])
            }
            hook(selected.getDeclaredMethod("h", baseLauncher, Boolean::class.javaPrimitiveType), before = true) { param ->
                val activity = param.args[0] as Context
                if (param.args[1] == true || support.invoke(param.thisObject, activity) == true) {
                    val lifecycle = Reflect.callMethod(activity, "getLifecycle")
                    val block = job.newInstance(param.thisObject, activity, param.args[1], null)
                    dispatch.invoke(null, scope.invoke(null, lifecycle), null, null, block, 3)
                }
                param.result = null
            }
        }
        installSettings(loader, international)
    }

    private fun installSettings(loader: ClassLoader, international: Boolean) {
        val fragment = loader.loadClass("com.android.launcher.settings.c")
        val category = fragment.getField("K")
        val domestic = fragment.getField("C")
        val global = fragment.getField("D")
        val nativeListener = if (international) loader.loadClass("vq5") else null
        fun update(owner: Any) {
            val group = category.get(owner) ?: return
            val entry = (if (international) global else domestic).get(owner) ?: return
            val other = (if (international) domestic else global).get(owner)
            if (other != null && Reflect.callMethod(other, "getParent") === group) Reflect.callMethod(group, "j", other)
            if (Reflect.callMethod(entry, "getParent") !== group) Reflect.callMethod(group, "g", entry)
            if (nativeListener != null && Reflect.callMethod(entry, "getOnPreferenceClickListener") == null) {
                val listener = allocate(nativeListener)
                nativeListener.getField("c").set(listener, owner)
                Reflect.callMethod(entry, "setOnPreferenceClickListener", listener)
            }
        }
        for (method in listOf("onCreatePreferences", "onResume")) {
            hook(fragment.getDeclaredMethod(method)) { param -> param.thisObject?.let(::update) }
        }
        // The native layout compatibility callback can change the entries after onResume.
        val task = loader.loadClass("b02")
        val taskOwner = task.getField("r")
        hook(task.getDeclaredMethod("run")) { param ->
            val owner = taskOwner.get(param.thisObject)
            if (owner != null && fragment.isInstance(owner)) update(owner)
        }
    }
}
