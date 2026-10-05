package com.mi.onextbox.lsp

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.mi.onextbox.lsp.LspConfig.InstallerFeature as Feature
import com.mi.onextbox.lsp.compat.ModernHookBridge
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

internal object InstallerRedirectHooker {
    private const val TAG = "ONextBox-Installer"
    private val installed = ConcurrentHashMap.newKeySet<ClassLoader>()
    private val sessionRead = ThreadLocal<Boolean>()

    private fun field(type: Class<*>, name: String): Field {
        var current: Class<*>? = type
        while (current != null) {
            runCatching { current!!.getDeclaredField(name) }.getOrNull()?.let {
                it.isAccessible = true
                return it
            }
            current = current.superclass
        }
        error("Missing field $name")
    }

    fun hook(loader: ClassLoader) {
        if (!installed.add(loader)) return
        runCatching {
            val starter = loader.loadClass("com.android.server.wm.ActivityStarter")
            val requestField = field(starter, "mRequest")
            val serviceField = field(starter, "mService")
            ModernHookBridge.hookMethodFast(starter.getDeclaredMethod("execute")) { chain ->
                if (LspConfig.isInstallerFeatureEnabledXposed(Feature.Enabled)) runCatching {
                    val owner = chain.thisObject!!
                    val request = requestField.get(owner)!!
                    val intentField = field(request.javaClass, "intent")
                    val original = intentField.get(request) as? Intent ?: return@runCatching
                    if (!InstallerRoutingPolicy.shouldRedirect(
                            original.action, original.type, original.dataString, original.component?.packageName,
                            LspConfig.isInstallerFeatureEnabledXposed(Feature.Uninstall),
                            LspConfig.isInstallerFeatureEnabledXposed(Feature.Session),
                        )) return@runCatching
                    val target = InstallerRoutingPolicy.target(
                        original.action,
                        LspConfig.installerTextXposed(LspConfig.INSTALLER_PACKAGE),
                        LspConfig.installerTextXposed(LspConfig.UNINSTALLER_PACKAGE),
                        LspConfig.isInstallerFeatureEnabledXposed(Feature.FollowUninstall),
                    )
                    if (target.isBlank() || original.component?.packageName == target || original.`package` == target) return@runCatching
                    val candidate = Intent(original).apply {
                        selector = null
                        component = null
                        `package` = target
                        if (action == InstallerRoutingPolicy.INSTALL) action = Intent.ACTION_VIEW
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val service = serviceField.get(owner)!!
                    val context = field(service.javaClass, "mContext").get(service) as Context
                    val user = field(request.javaClass, "userId").getInt(request)
                    val resolve = context.packageManager.javaClass.getMethod(
                        "resolveActivityAsUser", Intent::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                    )
                    if (resolve.invoke(context.packageManager, candidate, PackageManager.MATCH_DEFAULT_ONLY, user) != null) {
                        // All fields are obtained before mutation. The framework then resolves the target and URI grants normally.
                        val activityInfo = field(request.javaClass, "activityInfo")
                        val resolveInfo = field(request.javaClass, "resolveInfo")
                        intentField.set(request, candidate)
                        activityInfo.set(request, null)
                        resolveInfo.set(request, null)
                    }
                }.onFailure { HookLog.w(TAG, "Installer redirect skipped", it) }
                chain.proceed()
            }
            HookLog.i(TAG, "Installer routing installed")
        }.onFailure { HookLog.w(TAG, "Installer routing unavailable", it) }
        runCatching {
            val session = loader.loadClass("com.android.server.pm.PackageInstallerSession")
            session.declaredMethods.filter { it.name == "generateInfoInternal" }.forEach { method ->
                ModernHookBridge.hookMethodFast(method) { chain ->
                    val previous = sessionRead.get()
                    sessionRead.set(LspConfig.isInstallerFeatureEnabledXposed(Feature.Enabled) &&
                        LspConfig.isInstallerFeatureEnabledXposed(Feature.FixPermissions))
                    try { chain.proceed() } finally {
                        if (previous == null) sessionRead.remove() else sessionRead.set(previous)
                    }
                }
            }
            val check = loader.loadClass("android.app.ContextImpl").getDeclaredMethod("checkCallingOrSelfPermission", String::class.java)
            ModernHookBridge.hookMethodFast(check) { chain ->
                if (sessionRead.get() == true && chain.args[0] == "android.permission.READ_INSTALLED_SESSION_PATHS")
                    PackageManager.PERMISSION_GRANTED else chain.proceed()
            }
        }.onFailure { HookLog.w(TAG, "Installer session paths unavailable", it) }
    }
}
