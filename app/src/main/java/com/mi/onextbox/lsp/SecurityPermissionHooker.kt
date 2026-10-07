package com.mi.onextbox.lsp
import android.os.Build

import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.UserHandle
import com.mi.onextbox.lsp.LspConfig.PermissionFeature
import com.mi.onextbox.lsp.compat.ModernHookBridge as XposedBridge
import com.mi.onextbox.lsp.compat.ModernMethodHook as XC_MethodHook
import com.mi.onextbox.lsp.compat.ModernReflect as XposedHelpers
import java.util.concurrent.ConcurrentHashMap

internal object SecurityPermissionHooker {
    private const val TAG = "ONextBox-PermissionManager"
    private const val PACKAGE = "com.oplus.securitypermission"
    private val loaders = ConcurrentHashMap.newKeySet<Int>()

    fun hook(classLoader: ClassLoader) {
        if (!loaders.add(System.identityHashCode(classLoader))) return
        install(TAG, "old app-start dialog") { hookOldDialog(classLoader) }
        install(TAG, "always-allow button") { hookAlwaysAllowButton(classLoader) }
        install(TAG, "permanent start whitelist") { hookWhitelist(classLoader) }
        install(TAG, "restricted settings") { hookRestrictedSettings(classLoader) }
    }

    private fun oldDialogEnabled() =
        LspConfig.isPermissionFeatureEnabledXposed(PermissionFeature.OldAppStartDialog)

    private fun alwaysAllowEnabled() = !oldDialogEnabled() &&
        LspConfig.isPermissionFeatureEnabledXposed(PermissionFeature.AlwaysAllowAppStart)

    private fun hookOldDialog(loader: ClassLoader): Int {
        val target = XposedHelpers.findClassIfExists(
            "com.oplusos.securitypermission.permission.ui.AppStartConfirmDialogActivity", loader,
        ) ?: return 0
        return listOf("onCreate", "onNewIntent").sumOf { methodName ->
            XposedBridge.hookAllMethods(target, methodName, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!oldDialogEnabled()) return
                    val activity = param.thisObject as? Activity ?: return
                    val intent = param.args.firstOrNull() as? Intent ?: activity.intent ?: return
                    intent.putExtra("activity_start_confirm_version", 0)
                }
            }).size
        }
    }

    private fun hookAlwaysAllowButton(loader: ClassLoader): Int {
        // C17 has obfuscated COUIAlertDialogBuilder as 'e'. Only the neutral-button signature
        // and this specific resource ID are changed; unrelated system dialogs stay untouched.
        return listOf("com.coui.appcompat.dialog.COUIAlertDialogBuilder", "com.coui.appcompat.dialog.e")
            .mapNotNull { XposedHelpers.findClassIfExists(it, loader) }
            .distinct()
            .sumOf { target ->
                target.declaredMethods.filter { method ->
                    method.parameterTypes.contentEquals(arrayOf(
                        Int::class.javaPrimitiveType, DialogInterface.OnClickListener::class.java,
                        Boolean::class.javaPrimitiveType,
                    ))
                }.onEach { method ->
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (!alwaysAllowEnabled()) return
                            val builder = param.thisObject ?: return
                            val context = XposedHelpers.callMethod(builder, "getContext") as? Context ?: return
                            if (context.packageName != PACKAGE) return
                            val res = context.resources
                            val allow30 = res.getIdentifier("app_start_dialog_allow_30", "string", PACKAGE)
                            if (allow30 == 0 || param.args[0] != allow30) return
                            val always = res.getIdentifier("app_start_dialog_always_allow", "string", PACKAGE)
                            if (always == 0) return
                            param.args[0] = always
                        }
                    })
                }.size
            }
    }

    private fun hookWhitelist(loader: ClassLoader): Int {
        val targets = listOf(
            "android.content.pm.OplusPermissionManager",
            "com.oplusos.securitypermission.permission.utils.OplusPermissionManager",
            "s9.d", // verified C17 wrapper around ISecurityPermissionService
        ).mapNotNull { XposedHelpers.findClassIfExists(it, loader) }.distinct()
        return targets.sumOf { target ->
            target.declaredMethods.filter { method ->
                method.parameterTypes.contentEquals(arrayOf(Bundle::class.java)) &&
                    (method.name == "putActivityStartWhiteList" ||
                        (target.name == "s9.d" && method.name == "d" &&
                            target.declaredFields.any { it.type.name == "android.os.ISecurityPermissionService" }))
            }.onEach { method ->
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!alwaysAllowEnabled()) return
                        val original = param.args[0] as? Bundle ?: return
                        // A dismissal uses ignored_activity, not src_and_dst. Only the user's
                        // 30-day allow choice receives a permanent lifetime.
                        if (original.getStringArrayList("src_and_dst")?.size != 2 ||
                            !original.containsKey("valid_time")) return
                        param.args[0] = Bundle(original).apply { remove("valid_time") }
                        HookLog.i(TAG, "Converted selected 30-day allow choice to permanent allow")
                    }
                })
            }.size
        }
    }

    private fun hookRestrictedSettings(loader: ClassLoader): Int {
        val target = XposedHelpers.findClassIfExists(
            "com.oplusos.securitypermission.permission.PermissionGroupsActivity", loader,
        ) ?: return 0
        return XposedBridge.hookAllMethods(target, "onCreate", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!LspConfig.isPermissionFeatureEnabledXposed(PermissionFeature.AutoUnlockRestrictedSettings)) return
                val activity = param.thisObject as? Activity ?: return
                val intent = activity.intent ?: return
                val packageName = intent.getStringExtra("packageName")
                    ?: intent.getStringExtra("mPackageName") ?: return
                if (packageName.isBlank()) return
                runCatching {
                    @Suppress("DEPRECATION")
                    val user = intent.getParcelableExtra<UserHandle>(Intent.EXTRA_USER)
                    val context = if (user == null) activity else
                        XposedHelpers.callMethod(activity, "createContextAsUser", user, 0) as Context
                    unlockRestriction(context, packageName, loader)
                }.onFailure { HookLog.w(TAG, "Unable to clear target package restriction", it) }
            }
        }).size
    }

    private fun unlockRestriction(context: Context, packageName: String, loader: ClassLoader) {
        // ECM's setting gate changed on Android 16. Use the platform flag and service, matching
        // PermissionGroupsActivity, instead of assuming an installed system version.
        val flags = XposedHelpers.findClassIfExists(
            "com.android.internal.hidden_from_bootclasspath.android.permission.flags.Flags", loader,
        )
        val ecmEnabled = flags != null && runCatching {
            XposedHelpers.callStaticMethod(flags, "enhancedConfirmationModeApisEnabled") == true
        }.getOrDefault(false)
        if (ecmEnabled) {
            val manager = context.getSystemService("ecm_enhanced_confirmation") ?: return
            val restricted = XposedHelpers.callMethod(manager, "isRestricted", packageName,
                "android:bind_accessibility_service") == true
            val clearAllowed = XposedHelpers.callMethod(manager, "isClearRestrictionAllowed", packageName) == true
            if (!restricted && !clearAllowed) return
            if (!clearAllowed) XposedHelpers.callMethod(manager, "setClearRestrictionAllowed", packageName)
            XposedHelpers.callMethod(manager, "clearRestriction", packageName)
        } else {
            val appOps = context.getSystemService(AppOpsManager::class.java) ?: return
            @Suppress("DEPRECATION")
            val uid = context.packageManager.getApplicationInfo(packageName, 0).uid
            val op = XposedHelpers.getStaticIntField(AppOpsManager::class.java, "OP_ACCESS_RESTRICTED_SETTINGS")
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow("android:access_restricted_settings", uid, packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow("android:access_restricted_settings", uid, packageName)
            }
            if (mode == AppOpsManager.MODE_ALLOWED || mode == AppOpsManager.MODE_DEFAULT) return
            XposedHelpers.callMethod(appOps, "setMode", op, uid, packageName, AppOpsManager.MODE_ALLOWED)
        }
        HookLog.i(TAG, "Cleared restriction for the package opened in Permission Manager")
    }

    private inline fun install(tag: String, name: String, block: () -> Int) {
        runCatching(block).onSuccess { count ->
            if (count == 0) HookLog.w(tag, "$name: no compatible target")
            else HookLog.i(tag, "$name: $count hooks installed")
        }.onFailure { HookLog.w(tag, "$name: registration failed", it) }
    }
}
