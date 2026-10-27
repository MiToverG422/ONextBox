package com.mi.onextbox.lsp

import android.app.role.RoleManager
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Modifier

internal object FluidCloudBrowserHooker {
    private const val TAG = "FluidCloudBrowser"
    private const val TEXT_INTENT = "com.oplus.textintent.distribution.bean.TextIntent"
    private val reported = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private fun reportOnce(message: String) {
        if (reported.add(message)) HookLog.i(TAG, message)
    }

    fun hook(loader: ClassLoader) {
        val prefix = "$TAG:launch@${System.identityHashCode(loader)}:"
        runCatching {
            val type = loader.loadClass(TEXT_INTENT)
            val invoke = type.getDeclaredMethod("processInvoke", Context::class.java, Intent::class.java)
            check(invoke.returnType == Void.TYPE && !Modifier.isStatic(invoke.modifiers))
            val launchers = mutableListOf(invoke)
            type.declaredMethods.singleOrNull {
                it.name == "startMiniFloat" && it.returnType == Void.TYPE &&
                    !Modifier.isStatic(it.modifiers) && it.parameterTypes.contentEquals(
                        arrayOf(Context::class.java, Intent::class.java, Bundle::class.java),
                    )
            }?.let(launchers::add)
            val packageName = type.getDeclaredField("packageName").apply {
                check(this.type == String::class.java && !Modifier.isStatic(modifiers))
                isAccessible = true
            }
            val textType = type.getDeclaredField("textType").apply {
                check(this.type == Int::class.javaObjectType && !Modifier.isStatic(modifiers))
                isAccessible = true
            }
            val intentType = type.getDeclaredField("intentType").apply {
                check(this.type == Int::class.javaPrimitiveType && !Modifier.isStatic(modifiers))
                isAccessible = true
            }
            val redirect = XposedInterface.Hooker { chain ->
                if (!FluidCloudBrowserSettings.isEnabledXposed()) return@Hooker chain.proceed()
                val owner = chain.thisObject ?: return@Hooker chain.proceed()
                val context = chain.args.getOrNull(0) as? Context ?: return@Hooker chain.proceed()
                val original = chain.args.getOrNull(1) as? Intent ?: return@Hooker chain.proceed()
                val prepared = runCatching {
                    val oldPackage = packageName.get(owner) as? String
                    val typeCode = textType.get(owner) as? Int
                    val url = FluidCloudBrowserRules.launchUrl(
                        original.action, original.dataString,
                        if (original.action == Intent.ACTION_WEB_SEARCH)
                            original.getStringExtra(SearchManager.QUERY) else null,
                        typeCode,
                    ) ?: return@runCatching null
                    val webUri = Uri.parse(url)
                    if (!FluidCloudBrowserRules.accepts(
                            enabled = true,
                            textType = typeCode,
                            intentType = intentType.getInt(owner),
                            packageName = oldPackage,
                            action = original.action,
                            scheme = webUri.scheme,
                            host = webUri.host,
                            explicitPackage = original.`package`,
                            componentPackage = original.component?.packageName,
                            hasSelector = original.selector != null,
                        )) return@runCatching null
                    reportOnce("Recognized web link launch intercepted")
                    val target = defaultBrowser(context) ?: run {
                        reportOnce("No default browser resolved; original route retained")
                        return@runCatching null
                    }
                    if (target == FluidCloudBrowserRules.STOCK_BROWSER) return@runCatching null
                    val redirected = FluidCloudBrowserIntents.redirect(original, url, target)
                    val activity = redirected.resolveActivityInfo(context.packageManager, PackageManager.MATCH_DEFAULT_ONLY)
                        ?: run {
                            reportOnce("Default browser cannot resolve web intent; original route retained")
                            return@runCatching null
                        }
                    if (activity.packageName != target || !activity.enabled || !activity.exported ||
                        !activity.applicationInfo.enabled) return@runCatching null
                    Triple(oldPackage, target, redirected)
                }.onFailure { HookLog.w(TAG, "Browser routing unavailable, stock behavior retained") }.getOrNull()
                    ?: return@Hooker chain.proceed()
                val args = chain.args.toTypedArray()
                args[1] = prepared.third
                reportOnce("Routing recognized web link to default browser")
                FluidCloudBrowserRules.withTarget(
                    prepared.first, prepared.second, { packageName.set(owner, it) },
                ) { chain.proceed(args) }
            }
            launchers.forEach { method ->
                ModernHookRegistry.installFast("$prefix${method.name}", method, redirect)
            }
            type.declaredMethods.filter {
                it.name == "invoke" || it.name == "processInvoke" ||
                    it.name == "access\$processInvoke" || it.name == "startMiniFloat\$default"
            }.forEach { ModernHookRuntime.requireModule().deoptimize(it) }
            runCatching { loader.loadClass("$TEXT_INTENT\$invoke\$1\$1\$1") }.getOrNull()
                ?.declaredMethods?.filter { it.name == "invokeSuspend" }
                ?.forEach { ModernHookRuntime.requireModule().deoptimize(it) }
            // These card/panel callers can contain an inlined invoke/processInvoke on OEM builds.
            // Deoptimizing only TextIntent.invoke does not invalidate that outer compiled code.
            listOf(
                "com.oplus.textintent.distribution.PilotIntent",
                "com.oplus.textintent.distribution.ui.MultiAppOpenablePanel",
                "com.oplus.textintent.distribution.ui.MultiAppInstallPanel",
                "com.oplus.textintent.distribution.ui.MediaOpenPanel",
            ).forEach { name ->
                runCatching {
                    val caller = loader.loadClass(name)
                    (listOf(caller) + caller.declaredClasses.toList()).forEach { callerType ->
                        callerType.declaredMethods.filterNot {
                            Modifier.isAbstract(it.modifiers) || Modifier.isNative(it.modifiers)
                        }.forEach { ModernHookRuntime.requireModule().deoptimize(it) }
                    }
                }.onFailure { reportOnce("Optional web link caller deoptimization unavailable: $name") }
            }
            HookLog.i(TAG, "Recognized web link routing installed")
        }.onFailure {
            ModernHookRegistry.unhookPrefix(prefix)
            HookLog.w(TAG, "Web link target unavailable, stock behavior retained", it)
        }
    }

    private fun defaultBrowser(context: Context): String? {
        val pm = context.packageManager
        return FluidCloudBrowserRules.firstAvailableBrowser(
            // Query the browser role in the current user's context on every click (no stale cache).
            {
                val manager = context.getSystemService(RoleManager::class.java)
                @Suppress("UNCHECKED_CAST")
                val holders = manager?.javaClass?.getMethod("getRoleHolders", String::class.java)
                    ?.invoke(manager, RoleManager.ROLE_BROWSER) as? List<String>
                holders?.singleOrNull()
            },
            {
                val user = Context::class.java.getMethod("getUserId").invoke(context) as Int
                pm.javaClass.getMethod("getDefaultBrowserPackageNameAsUser", Int::class.javaPrimitiveType)
                    .invoke(pm, user) as? String
            },
            { resolveBrowser(context) },
        )
    }

    private fun resolveBrowser(context: Context): String? {
        val pm = context.packageManager
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://onextbox.invalid/"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        val resolved = pm.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY) ?: return null
        val handlesWeb = runCatching {
            resolved.javaClass.getField("handleAllWebDataURI").getBoolean(resolved)
        }.getOrDefault(false)
        return resolved.activityInfo?.packageName?.takeIf {
            handlesWeb && it != "android" && resolved.activityInfo.enabled &&
                resolved.activityInfo.exported && resolved.activityInfo.applicationInfo.enabled
        }
    }
}
