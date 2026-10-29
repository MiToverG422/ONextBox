package com.mi.onextbox.lsp

import android.content.Context
import android.content.Intent
import android.app.Activity
import android.provider.Settings
import android.view.View
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.io.FileDescriptor

internal object InstallerCleanupHooker {
    private const val TAG = "ONextBox-InstallerCleanup"
    private val application by lazy { Class.forName("android.app.ActivityThread").getDeclaredMethod("currentApplication") }
    private fun context() = runCatching { application.invoke(null) as? Context }.getOrNull()
    private fun maySkip(context: Context? = context()): Boolean = context != null && runCatching {
        InstallerCleanupRules.maySkip(
            Settings.Global.getInt(context.contentResolver, "children_mode_on", 0) == 1,
            Settings.Secure.getInt(context.contentResolver, "minors_mode", 0) == 1 &&
                Settings.Secure.getInt(context.contentResolver, "minors_mode_enabled", 0) == 1)
    }.getOrDefault(false)
    private fun scan(context: Context? = context()) = InstallerCleanupSettings.active(InstallerCleanupSettings.APP_SKIP_SCAN) && maySkip(context)

    fun hook(loader: ClassLoader, apkPath: String, packageName: String) {
        if (packageName == InstallerCleanupSettings.PACKAGE_INSTALLER) NativeInstallerThemeHooker.hook()
        val key = "$TAG@${System.identityHashCode(loader)}:$packageName"
        val stateKey = "$key:targets-v1"
        runCatching {
            val saved = (DexTargetCache.restoredState(stateKey) as? Map<*, *>)?.mapNotNull { (name, value) ->
                val methods = (value as? Array<*>)?.filterIsInstance<Method>()?.toTypedArray()
                if (name is String && methods != null) name to methods else null
            }?.toMap()
            val targets = saved?.takeIf { InstallerCleanupTargets.SCHEMA in it }
                ?: ApkDexSession.withSession(apkPath) { open -> InstallerCleanupTargets.resolve(open(), loader, packageName) }
            fun install(group: String, count: Int, action: (Array<Method>) -> Unit) {
                val methods = targets[group] ?: return
                runCatching {
                    require(methods.size >= count)
                    action(methods)
                    methods.drop(count).forEach { runCatching { ModernHookRuntime.requireModule().deoptimize(it) } }
                    HookLog.i(TAG, "$packageName $group hook installed")
                }.onFailure {
                    ModernHookRegistry.unhookPrefix("$key:$group:")
                    HookLog.w(TAG, "$group unavailable; native behavior retained", it)
                }
            }
            fun method(group: String, index: Int, target: Method, action: XposedInterface.Hooker) {
                ModernHookRegistry.installFast("$key:$group:$index", target, action)
            }
            install("market-card", 2) { methods ->
                method("market-card", 0, methods[0], XposedInterface.Hooker { chain ->
                    val view = chain.thisObject as? View
                    if (InstallerCleanupSettings.active(InstallerCleanupSettings.APP_REMOVE_ADS) && view != null && InstallerPromotionCards.matches(view)) {
                        val args = chain.args.toTypedArray(); args[0] = View.GONE
                        chain.proceed(args)
                    } else chain.proceed()
                })
                method("market-card", 1, methods[1], XposedInterface.Hooker { chain ->
                    val result = chain.proceed()
                    if (InstallerCleanupSettings.active(InstallerCleanupSettings.APP_REMOVE_ADS)) {
                        val activity = chain.thisObject as? Activity
                        if (activity?.packageName == InstallerCleanupSettings.APP_DETAIL) {
                            activity.window?.decorView?.let(InstallerPromotionCards::hide)
                        }
                    }
                    result
                })
                HotReloadLifecycle.afterCommit {
                    if (InstallerCleanupSettings.active(InstallerCleanupSettings.APP_REMOVE_ADS)) {
                        runCatching { InstallerPromotionCards.refreshOwnedActivities() }
                            .onSuccess { HookLog.i(TAG, "Existing market promotion cards hidden: $it") }
                            .onFailure { HookLog.w(TAG, "Existing promotion refresh unavailable", it) }
                    }
                }
            }
            install("network", 1) { methods ->
                methods.forEachIndexed { index, target ->
                    method("network", index, target, XposedInterface.Hooker { chain ->
                        InstallerNetworkAccess.check(target.name, chain.args.firstOrNull() as? FileDescriptor,
                            InstallerCleanupSettings.active(InstallerCleanupSettings.APP_BLOCK_NETWORK))
                        chain.proceed()
                    })
                }
            }
            install("fast-prepare", 2) { methods ->
                val fallback = methods[1].declaringClass.declaredConstructors.single { it.parameterCount == 3 &&
                    it.parameterTypes[1].name == "com.heytap.cdo.security.domain.expgray.ConfigResult" &&
                    it.parameterTypes[2] == String::class.java }
                method("fast-prepare", 0, methods[0], XposedInterface.Hooker { chain ->
                    if (InstallerCleanupSettings.active(InstallerCleanupSettings.APP_BLOCK_NETWORK) && maySkip()) {
                        runCatching { fallback.newInstance(null, null, "") }.getOrNull() ?: chain.proceed()
                    } else chain.proceed()
                })
            }
            install("scan-card", 1) { methods ->
                method("scan-card", 0, methods[0], XposedInterface.Hooker { chain ->
                    val view = chain.thisObject as? View ?: return@Hooker chain.proceed()
                    if (scan(view.context)) {
                        view.visibility = View.GONE
                        null
                    } else {
                        view.visibility = View.VISIBLE
                        chain.proceed()
                    }
                })
            }
            install("collection-notice", 2) { methods ->
                val factory = methods[1].let { it.declaringClass.name to it.name }
                method("collection-notice", 0, methods[0], XposedInterface.Hooker { chain ->
                    if (InstallerCleanupSettings.active(InstallerCleanupSettings.APP_HIDE_COLLECTION_NOTICE) && maySkip() &&
                        InstallerCleanupRules.noticeCaller(Thread.currentThread().stackTrace.toList(), factory)) true
                    else chain.proceed()
                })
                ModernHookRuntime.requireModule().deoptimize(methods[1])
            }
            install("route", 2) { methods ->
                val factory = methods[1].let { it.declaringClass.name to it.name }
                method("route", 0, methods[0], XposedInterface.Hooker { chain ->
                    if (scan(chain.getArg(0) as? Context) && InstallerCleanupRules.routeCaller(Thread.currentThread().stackTrace.toList(), factory)) false
                    else chain.proceed()
                })
                ModernHookRuntime.requireModule().deoptimize(methods[1])
            }
            install("plain", 2) { methods ->
                methods.take(2).forEachIndexed { index, target ->
                    method("plain", index, target, XposedInterface.Hooker { chain ->
                        if (scan()) { if (index == 0) 0 else "0" } else chain.proceed()
                    })
                }
            }
            install("recommend", 3) { methods ->
                methods.take(2).forEachIndexed { index, target ->
                    method("recommend", index, target, XposedInterface.Hooker { chain ->
                        if (InstallerCleanupSettings.active(InstallerCleanupSettings.APP_REMOVE_ADS)) null else chain.proceed()
                    })
                }
                method("recommend", 2, methods[2], XposedInterface.Hooker { chain ->
                    if (!InstallerCleanupSettings.active(InstallerCleanupSettings.APP_REMOVE_ADS)) chain.proceed() else {
                        val args = chain.args.toTypedArray(); args[0] = null
                        chain.proceed(args)
                    }
                })
            }
            install("cancel", 2) { methods ->
                methods.take(2).forEachIndexed { index, target ->
                    method("cancel", index, target, XposedInterface.Hooker { chain ->
                        if (InstallerCleanupSettings.active(InstallerCleanupSettings.APP_REMOVE_ADS)) false else chain.proceed()
                    })
                }
            }
            install("risk-ad", 1) { methods ->
                method("risk-ad", 0, methods[0], XposedInterface.Hooker { chain ->
                    if (InstallerCleanupSettings.active(InstallerCleanupSettings.APP_REMOVE_ADS)) null else chain.proceed()
                })
            }
            install("native", 4) { methods ->
                method("native", 0, methods[0], XposedInterface.Hooker { chain ->
                    if (InstallerCleanupSettings.active(InstallerCleanupSettings.NATIVE_UI) && maySkip()) true else chain.proceed()
                })
                method("native", 1, methods[3], XposedInterface.Hooker { chain ->
                    if (InstallerCleanupRules.legacyFile((chain.getArg(0) as? Activity)?.intent?.data?.scheme,
                            InstallerCleanupSettings.active(InstallerCleanupSettings.NATIVE_UI)) && maySkip()) false else chain.proceed()
                })
                val legacy = loader.loadClass("com.android.packageinstaller.PackageInstallerActivity")
                val setClass = Intent::class.java.getDeclaredMethod("setClass", Context::class.java, Class::class.java)
                method("native", 2, setClass, XposedInterface.Hooker { chain ->
                    if (InstallerCleanupSettings.active(InstallerCleanupSettings.NATIVE_UI) && maySkip() &&
                        (chain.getArg(1) as? Class<*>)?.name == "com.android.packageinstaller.oplus.OPlusPackageInstallerActivity") {
                        val args = chain.args.toTypedArray(); args[1] = legacy
                        chain.proceed(args)
                    } else chain.proceed()
                })
                methods.take(3).drop(1).forEach { ModernHookRuntime.requireModule().deoptimize(it) }
            }
            HotReloadLifecycle.register(stateKey, capture = { targets.toMap() })
        }.onFailure { HookLog.w(TAG, "$packageName installer hooks unavailable", it) }
    }
}
