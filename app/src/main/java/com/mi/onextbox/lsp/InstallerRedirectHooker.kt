package com.mi.onextbox.lsp

import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.provider.OpenableColumns
import com.mi.onextbox.lsp.LspConfig.InstallerFeature as Feature
import com.mi.onextbox.lsp.compat.ModernHookBridge
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

internal object InstallerRedirectHooker {
    private const val TAG = "ONextBox-Installer"
    private val installed = ConcurrentHashMap.newKeySet<ClassLoader>()
    private val appHooks = ConcurrentHashMap.newKeySet<Method>()

    private fun field(type: Class<*>, name: String): Field {
        var current: Class<*>? = type
        while (current != null) {
            val owner = current
            runCatching { owner.getDeclaredField(name) }.getOrNull()?.let {
                it.isAccessible = true
                return it
            }
            current = owner.superclass
        }
        error("Missing field $name")
    }

    fun hook(loader: ClassLoader) {
        if (!installed.add(loader)) return
        runCatching {
            val starter = loader.loadClass("com.android.server.wm.ActivityStarter")
            val requestField = field(starter, "mRequest")
            ModernHookBridge.hookMethodFast(starter.getDeclaredMethod("execute")) { chain ->
                runCatching {
                    val request = requestField.get(chain.thisObject) ?: return@runCatching
                    val intentField = field(request.javaClass, "intent")
                    val original = intentField.get(request) as? Intent ?: return@runCatching
                    val resolvedType = field(request.javaClass, "resolvedType")
                    val candidate = redirect(original, resolvedType.get(request) as? String) ?: return@runCatching
                    // Resolve the replacement with the original caller and URI grants.
                    val activityInfo = field(request.javaClass, "activityInfo")
                    val resolveInfo = field(request.javaClass, "resolveInfo")
                    val intentGrants = field(request.javaClass, "intentGrants")
                    intentField.set(request, candidate)
                    resolvedType.set(request, candidate.type)
                    activityInfo.set(request, null)
                    resolveInfo.set(request, null)
                    intentGrants.set(request, null)
                }.onFailure { HookLog.w(TAG, "Installer redirect skipped", it) }
                chain.proceed()
            }
            HookLog.i(TAG, "Installer routing installed (phone 1.6.2 behavior)")
        }.onFailure { HookLog.w(TAG, "Installer routing unavailable", it) }
        hookOplusSwitch(loader, "com.android.server.pm.OplusDefaultAppPolicyManager",
            "isDefaultAppEnabled", Feature.RemoveDefaultAppPolicy)
    }

    private fun hookOplusSwitch(
        loader: ClassLoader, className: String, methodName: String, feature: Feature,
    ) {
        val type = runCatching { loader.loadClass(className) }.getOrNull() ?: return
        val methods = type.declaredMethods.filter {
            it.name == methodName && it.returnType == Boolean::class.javaPrimitiveType
        }
        if (methods.isEmpty()) HookLog.w(TAG, "Unavailable Oplus method: $methodName")
        methods.forEach { method ->
            runCatching {
                ModernHookBridge.hookMethodFast(method) { chain ->
                    if (LspConfig.isInstallerFeatureEnabledXposed(Feature.Enabled) &&
                        LspConfig.isInstallerFeatureEnabledXposed(feature)) false else chain.proceed()
                }
            }.onFailure { HookLog.w(TAG, "Oplus hook unavailable: $methodName", it) }
        }
    }

    fun hookApp() {
        Instrumentation::class.java.declaredMethods.filter {
            it.name == "execStartActivity" && Intent::class.java in it.parameterTypes
        }.forEach { method ->
            if (!appHooks.add(method)) return@forEach
            val index = method.parameterTypes.indexOf(Intent::class.java)
            runCatching {
                ModernHookBridge.hookMethodFast(method) { chain ->
                    val candidate = runCatching {
                        val original = chain.args[index] as? Intent ?: return@runCatching null
                        redirect(original, context = chain.args.firstOrNull() as? Context)
                    }.onFailure { HookLog.w(TAG, "App installer redirect skipped", it) }.getOrNull()
                    if (candidate == null) chain.proceed() else {
                        val args = chain.args.toTypedArray()
                        args[index] = candidate
                        chain.proceed(args)
                    }
                }
            }.onFailure {
                appHooks.remove(method)
                HookLog.w(TAG, "App installer routing unavailable", it)
            }
        }
    }

    private fun redirect(original: Intent, resolvedType: String? = null, context: Context? = null): Intent? {
        if (!LspConfig.isInstallerFeatureEnabledXposed(Feature.Enabled)) return null
        val target = LspConfig.installerTextXposed(LspConfig.INSTALLER_PACKAGE)
        if (target.isBlank() || !InstallerRoutingPolicy.validPackage(target) ||
            original.component?.packageName == target || original.`package` == target) return null
        val uninstall = LspConfig.isInstallerFeatureEnabledXposed(Feature.Uninstall)
        val session = LspConfig.isInstallerFeatureEnabledXposed(Feature.Session)
        val system = LspConfig.installerTextXposed(LspConfig.INSTALLER_SYSTEM_PACKAGE)
        val intercept = LspConfig.isInstallerFeatureEnabledXposed(Feature.InterceptSystem)
        fun accepts(name: String? = null) = InstallerRoutingPolicy.shouldRedirect(
            original.action, original.type ?: resolvedType, original.dataString,
            original.component?.packageName, uninstall, session, system, intercept, name,
        )
        if (!accepts()) {
            // Provider metadata is queried only in the calling app, never under the system activity lock.
            if (context == null || original.action != Intent.ACTION_VIEW || original.data?.scheme != "content" ||
                (original.component != null && (!intercept || original.component?.packageName != system))) return null
            val name = runCatching {
                context.contentResolver.query(original.data!!, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    val column = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (column >= 0 && it.moveToFirst()) it.getString(column) else null
                }
            }.getOrNull()
            if (!accepts(name)) return null
        }
        return Intent(original).apply {
            selector = null
            component = null
            `package` = target
            if (type == null && resolvedType != null) setDataAndType(data, resolvedType)
            if (action == InstallerRoutingPolicy.INSTALL) action = Intent.ACTION_VIEW
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (!InstallerRoutingPolicy.isUninstall(action)) addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
