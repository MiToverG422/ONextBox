package com.mi.onextbox.lsp

import android.content.Context
import android.view.View
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernReflect as Reflect
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Stream

/** Restore the retained native action; widget discovery, UI, binding and dragging stay native. */
internal object LauncherWidgetMenuHooker {
    private const val TAG = "ONextBox-LauncherWidgets"
    private val reported = ConcurrentHashMap.newKeySet<String>()

    fun hook(loader: ClassLoader) {
        val launcher = Reflect.findClass("com.android.launcher.Launcher", loader)
        val item = Reflect.findClass("com.android.launcher3.model.data.ItemInfo", loader)
        val workspaceItem = Reflect.findClass("com.android.launcher3.model.data.WorkspaceItemInfo", loader)
        val shortcut = Reflect.findClass("com.android.launcher3.popup.SystemShortcut", loader)
        val widgets = requireNotNull(shortcut.getDeclaredField("WIDGETS").apply { isAccessible = true }.get(null))
        val factoryMethod = widgets.javaClass.getDeclaredMethod("getShortcut", Context::class.java, item, View::class.java)
        val itemType = item.getDeclaredField("itemType").apply { isAccessible = true }
        val disabled = item.getMethod("isDisabled")
        val promise = try {
            workspaceItem.getMethod("isPromise")
        } catch (_: NoSuchMethodException) {
            workspaceItem.getMethod("hasPromiseIconUi")
        }
        // The archive/install additions are absent on some older launcher builds.
        val archived = runCatching { item.getMethod("getIsArchived") }.getOrNull()
        val nonInstalled = runCatching { item.getMethod("isNonInstalledApp") }.getOrNull()
        val supported = launcher.getDeclaredMethod("getSupportedShortcuts")
        check(Stream::class.java.isAssignableFrom(supported.returnType))
        val sheets = LauncherWidgetSheetAdapter(loader, ::report, ::diagnose).also { it.install() }

        ModernHookRegistry.installFast("$TAG:eligible", factoryMethod, XposedInterface.Hooker { chain ->
            if (!enabled()) return@Hooker chain.proceed()
            val allowed = runCatching {
                val info = chain.getArg(1) ?: return@runCatching false
                if (!item.isInstance(info) || chain.getArg(2) !is View) return@runCatching false
                LauncherWidgetMenuRules.eligible(itemType.getInt(info), disabled.invoke(info) == true,
                    workspaceItem.isInstance(info) && promise.invoke(info) == true,
                    archived?.invoke(info) == true, nonInstalled?.invoke(info) == true)
            }.onFailure { diagnose("App icon eligibility", it) }.getOrDefault(false)
            if (!allowed) return@Hooker null
            // The native factory checks the target package + UserHandle and returns
            // null when it has no widgets. Never force a fake provider or modify its data.
            chain.proceed().also { if (it != null) { sheets.recordAction(it); report("Native per-app widget action created") } }
        })
        ModernHookRegistry.installFast("$TAG:factories", supported, XposedInterface.Hooker { chain ->
            val original = chain.proceed()
            if (!enabled()) return@Hooker original
            val stream = original as? Stream<*> ?: return@Hooker original
            // Only factory discovery is evaluated here, never individual getShortcut
            // calls. Return a new stream with all original entries in their native order.
            val factories = stream.use { it.toArray().toList() }
            LauncherWidgetMenuRules.appendFactory(factories, widgets).stream()
        })
        deoptimize(supported)
        deoptimize(factoryMethod)
        listOf(
            "com.android.launcher3.popup.model.ShortcutDataBuilder" to setOf("collectDefaultSystemShortcuts", "buildPopupData", "a", "b"),
            "com.android.launcher3.popup.presenter.Launcher3PopupPresenter" to setOf("showForLauncherIcon"),
            "com.android.launcher3.popup.OplusPopupContainerWithArrow" to setOf("showForIcon"),
        ).forEach { (name, methods) ->
            Reflect.findClassIfExists(name, loader)?.declaredMethods?.filter { it.name in methods }?.forEach(::deoptimize)
        }
        HookLog.i(TAG, "Native app-widget menu adapter installed")
    }

    private fun enabled() = !HotReloadLifecycle.isRetiring && LauncherGestureSettings.widgetsXposed()
    private fun report(message: String) { if (reported.add(message)) HookLog.i(TAG, message) }
    private fun diagnose(stage: String, error: Throwable) {
        if (reported.add(stage)) HookLog.w(TAG, "$stage unavailable; widget shortcut omitted", error)
    }
    private fun deoptimize(method: Method) {
        runCatching { ModernHookRuntime.requireModule().deoptimize(method) }
            .onFailure { diagnose("Native caller deoptimization: ${method.name}", it) }
    }
}
