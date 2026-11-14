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
        LauncherDockSearchTargets(loader)
        true
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
        val targets = LauncherDockSearchTargets(loader)
        val manager = targets.manager
        val proxy = targets.proxy
        val selected = if (international) targets.export else targets.domestic
        val support = targets.support(selected)
        val utils = targets.utils
        val init = loader.loadClass("com.android.common.util.LauncherSharedPrefs")
            .getDeclaredMethod("initOtaVersionUpdate", Context::class.java)
        val region = targets.region
        fun selectProxy(context: Context) {
            if (selected.isInstance(proxy.get(null))) return
            val instance = allocate(selected)
            val mutexField = targets.mutex(selected)
            val mutex = mutexField.type.getDeclaredConstructor().newInstance()
            mutexField.set(instance, mutex)
            if (international) {
                targets.cached(true).setInt(instance, -1)
                selected.getField("s").set(instance, selected.getField("w").get(null))
                selected.getField("u").set(instance, "ExportBottomSearch")
                val key = "selected_search_engine_aga"
                Reflect.callMethod(selected.getField("v").get(null), "i", Settings.Secure.getString(context.contentResolver, key))
                context.contentResolver.registerContentObserver(Settings.Secure.getUriFor(key), true,
                    targets.exportObserver().get(null) as ContentObserver)
            } else {
                val interpolator = targets.domesticInterpolator()
                interpolator.set(instance, interpolator.type.getDeclaredConstructor().newInstance())
                targets.cached(false).setInt(instance, -1)
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
            val probe = targets.exportProbe
            val cached = targets.cached(true)
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
            hook(targets.saveSwitch) { param ->
                if (param.args[2] == true && !region.getBoolean(null)) {
                    val launcher = Reflect.callStaticMethod(loader.loadClass("com.android.launcher3.taskbar.TaskbarUtils"), "getOplusLauncher")
                    val impl = proxy.get(null)
                    if (launcher != null && selected.isInstance(impl)) Reflect.callMethod(impl, "l", launcher, false)
                }
            }
        } else {
            val providerSupported = targets.domesticProbe
            val cached = targets.cached(false)
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
                .declaredConstructors.single { it.parameterCount == 4 &&
                    it.parameterTypes.take(3) == listOf(selected, baseLauncher, java.lang.Boolean.TYPE) }
                .apply { isAccessible = true }
            val scope = targets.coroutineScope
            val dispatch = targets.dispatch
            hook(selected.getDeclaredMethod("d", launcher), before = true) { param ->
                param.result = support.invoke(param.thisObject, param.args[0])
            }
            hook(targets.domesticSchedule, before = true) { param ->
                val activity = param.args[0] as Context
                if (param.args[1] == true || support.invoke(param.thisObject, activity) == true) {
                    val lifecycle = Reflect.callMethod(activity, "getLifecycle")
                    val block = job.newInstance(param.thisObject, activity, param.args[1], null)
                    dispatch.invoke(null, scope.invoke(null, lifecycle), null, null, block, 3)
                }
                param.result = null
            }
        }
        runCatching { installSettings(loader, international, targets) }
            .onFailure { HookLog.w(TAG, "Native search settings adapter unavailable", it) }
    }

    private fun installSettings(loader: ClassLoader, international: Boolean, targets: LauncherDockSearchTargets) {
        val fragment = loader.loadClass("com.android.launcher.settings.c")
        val category = fragment.getField("K")
        val domestic = fragment.getField("C")
        val global = fragment.getField("D")
        val nativeListener = if (international) targets.nativeListener else null
        val retained = java.util.WeakHashMap<Any, Boolean>()
        fun update(owner: Any) {
            val group = category.get(owner) ?: return
            val entry = (if (international) global else domestic).get(owner) ?: return
            retained[entry] = true
            val other = (if (international) domestic else global).get(owner)
            if (other != null && Reflect.callMethod(other, "getParent") === group) Reflect.callMethod(group, "j", other)
            if (Reflect.callMethod(entry, "getParent") !== group) Reflect.callMethod(group, "g", entry)
            if (nativeListener != null && Reflect.callMethod(entry, "getOnPreferenceClickListener") == null) {
                val listener = allocate(nativeListener)
                nativeListener.declaredFields.single {
                    !java.lang.reflect.Modifier.isStatic(it.modifiers) && (it.type == fragment || it.type == Any::class.java)
                }.apply { isAccessible = true }.set(listener, owner)
                Reflect.callMethod(entry, "setOnPreferenceClickListener", listener)
            }
        }
        for (method in listOf("onCreatePreferences", "onResume")) {
            hook(fragment.getDeclaredMethod(method)) { param -> param.thisObject?.let(::update) }
        }
        // Layout compatibility callbacks can remove the entry after onResume.
        val group = category.type
        val preference = loader.loadClass("androidx.preference.Preference")
        val remove = group.methods.single {
            it.name in setOf("removePreference", "j") && it.returnType == java.lang.Boolean.TYPE &&
                it.parameterTypes.contentEquals(arrayOf(preference))
        }
        hook(remove, before = true) { param ->
            if (retained[param.args[0]] == true) param.result = false
        }
    }
}
