package com.mi.onextbox.lsp

import android.content.Intent
import android.content.pm.ResolveInfo
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect

internal object NativeFilePickerHooker {
    private const val TAG = "ONextBox-NativeFilePicker"

    fun hook(classLoader: ClassLoader) {
        runCatching {
            val policy = ModernReflect.findClassIfExists(
                "com.android.server.pm.OplusDefaultAppPolicyManager", classLoader,
            )
            val method = policy?.declaredMethods?.singleOrNull {
                NativeFilePickerRules.isRedirectMethod(
                    it.name, it.returnType.name, it.parameterTypes.map { type -> type.name },
                )
            }
            if (method == null) {
                HookLog.w(TAG, "File picker routing unavailable")
                return
            }
            ModernHookRegistry.installCompat(
                key = "$TAG@${System.identityHashCode(classLoader)}:routing",
                executable = method,
                callback = object : ModernMethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.throwable != null) return
                        val selected = (param.result as? ResolveInfo)?.activityInfo ?: return
                        if (selected.packageName != NativeFilePickerRules.FILE_MANAGER_PACKAGE ||
                            selected.name != NativeFilePickerRules.FILE_MANAGER_PICKER
                        ) return
                        val intent = param.args.firstOrNull() as? Intent ?: return
                        val query = param.args.getOrNull(1) as? List<*> ?: return
                        val first = (query.firstOrNull() as? ResolveInfo)?.activityInfo ?: return
                        if (NativeFilePickerRules.shouldRestore(
                                enabled = LspConfig.isFileManagerNativePickerEnabledXposed(),
                                action = intent.action,
                                explicitlyTargeted = intent.component != null || intent.`package` != null ||
                                    intent.selector != null,
                                selectedPackage = selected.packageName,
                                selectedActivity = selected.name,
                                firstPackage = first.packageName,
                                firstAvailable = first.enabled && first.exported &&
                                    first.applicationInfo?.enabled == true,
                            )
                        ) {
                            // Skip picker replacement and retain system resolution and URI grants.
                            param.result = null
                        }
                    }
                },
            )
            HookLog.i(TAG, "Native file picker routing installed")
        }.onFailure { HookLog.w(TAG, "File picker routing hook failed", it) }
    }
}
