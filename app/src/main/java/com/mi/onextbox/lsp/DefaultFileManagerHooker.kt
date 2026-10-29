package com.mi.onextbox.lsp

import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.UserHandle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect
import java.lang.reflect.Field

internal object DefaultFileManagerHooker {
    private const val TAG = "ONextBox-DefaultFiles"

    fun hook(loader: ClassLoader) {
        val prefix = "$TAG@${System.identityHashCode(loader)}"
        runCatching {
            val starter = ModernReflect.findClass("com.android.server.wm.ActivityStarter", loader)
            val requestClass = ModernReflect.findClass("com.android.server.wm.ActivityStarter\$Request", loader)
            val resolve = requestClass.declaredMethods.single {
                it.name == "resolveActivity" && it.returnType == Void.TYPE &&
                    it.parameterTypes.map { type -> type.name } == listOf("com.android.server.wm.ActivityTaskSupervisor")
            }
            val fields = RequestFields(requestClass)
            val execute = starter.getDeclaredMethod("execute")
            val requestField = starter.getDeclaredField("mRequest").apply { isAccessible = true }
            val serviceField = starter.getDeclaredField("mService").apply { isAccessible = true }
            ModernHookRegistry.installCompat("$prefix:execute", execute, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val owner = param.thisObject ?: return
                    val request = requestField.get(owner) ?: return
                    redirect(request, fields) { serviceField.get(owner) }
                }
            })
            ModernHookRegistry.installCompat("$prefix:resolve", resolve, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val request = param.thisObject ?: return
                    val supervisor = param.args.firstOrNull() ?: return
                    redirect(request, fields) { ModernReflect.getObjectField(supervisor, "mService") }
                }
            })
            runCatching { ModernHookRuntime.requireModule().deoptimize(execute) }
            HookLog.i(TAG, "Default file manager routing installed for resolved and unresolved requests")
        }.onFailure {
            ModernHookRegistry.unhookPrefix(prefix)
            HookLog.w(TAG, "Default file manager routing unavailable", it)
        }
    }

    private fun redirect(request: Any, fields: RequestFields, service: () -> Any?) {
        val selected = DefaultFileManagerSettings.selectedXposed()
        if (selected.isBlank()) return
        val original = fields.intent.get(request) as? Intent ?: return
        if (!DefaultFileManagerRouting.mayRoute(original)) return
        runCatching {
            val owner = service() ?: return
            val context = ModernReflect.getObjectField(owner, "mContext") as? Context ?: return
            val user = fields.user.getInt(request)
            if (user < 0) return
            // Look up installed handlers as system, then restore the caller before activity security checks.
            val token = Binder.clearCallingIdentity()
            val routed = try {
                val userContext = ModernReflect.callMethod(context, "createContextAsUser",
                    UserHandle.getUserHandleForUid(user * 100000), 0) as? Context ?: return
                DefaultFileManagerRouting.redirect(userContext.packageManager, original,
                    fields.type.get(request) as? String, selected, fields.caller.get(request) as? String)
            } finally { Binder.restoreCallingIdentity(token) } ?: return
            fields.intent.set(request, routed)
            fields.type.set(request, routed.type)
            fields.activity.set(request, null)
            fields.resolve.set(request, null)
            fields.grants.set(request, null)
            HookLog.d(TAG, "Routed ${original.action} to ${routed.component?.flattenToShortString()}")
        }.onFailure { HookLog.w(TAG, "File request retained", it) }
    }

    private class RequestFields(private val owner: Class<*>) {
        private fun field(name: String): Field = owner.getDeclaredField(name).apply { isAccessible = true }
        val intent = field("intent")
        val user = field("userId")
        val type = field("resolvedType")
        val caller = field("callingPackage")
        val activity = field("activityInfo")
        val resolve = field("resolveInfo")
        val grants = field("intentGrants")
    }
}
