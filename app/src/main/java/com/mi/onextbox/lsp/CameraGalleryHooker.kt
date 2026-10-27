package com.mi.onextbox.lsp

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method

internal object CameraGalleryHooker {
    private const val TAG = "ONextBox-Camera"

    fun hook(loader: ClassLoader, apkPath: String) {
        val key = "$TAG@${System.identityHashCode(loader)}"
        val stateKey = "$key:preview-v1"
        runCatching {
            val saved = HotReloadLifecycle.restoredState(stateKey) as? Map<*, *>
            val groups = saved?.takeIf { it.containsKey(CameraGalleryTargets.SCHEMA) }?.mapNotNull { (name, value) ->
                (name as? String)?.let { it to ((value as? Array<*>)?.filterIsInstance<Method>()?.toTypedArray() ?: emptyArray()) }
            }?.toMap() ?: ApkDexSession.withSession(apkPath) { open -> CameraGalleryTargets.resolveAll(open(), loader) }
            val targets = groups.getValue("ordinary")
            require(targets.size >= 6)
            val preview = targets[0]
            val launch = targets[1]
            val secure = targets[2]
            val leaving = targets[3]
            val reset = targets[4]
            val alternate = targets[5]
            val helper = preview.declaringClass
            val activityField = helper.declaredFields.single { it.type == Activity::class.java }.apply { isAccessible = true }
            val entryField = helper.declaredFields.single { it.type == secure.declaringClass }.apply { isAccessible = true }
            val mediaField = preview.parameterTypes[1].declaredFields.single { it.type == Uri::class.java }.apply { isAccessible = true }
            listOf(secure, leaving, reset).forEach { it.isAccessible = true }

            ModernHookRegistry.installFast("$key:preview", preview, XposedInterface.Hooker { chain ->
                if (!CameraGallerySettings.active()) return@Hooker chain.proceed()
                val owner = chain.thisObject ?: return@Hooker chain.proceed()
                val opened = runCatching {
                    val entry = entryField.get(owner)
                    val activity = activityField.get(owner) as? Activity ?: return@runCatching false
                    val media = chain.getArg(1) ?: return@runCatching false
                    CameraGalleryIntents.tryOpen(activity, mediaField.get(media) as? Uri, secure.invoke(entry) == true, "ordinary")
                }.onFailure { HookLog.w(TAG, "Ordinary preview routing failed; native preview retained", it) }.getOrDefault(false)
                if (!opened) chain.proceed() else {
                    runCatching { reset.invoke(owner) }.onFailure { HookLog.w(TAG, "Preview transition reset failed", it) }
                    if (chain.getArg(2) == true) runCatching { leaving.invoke(entryField.get(owner), true) }
                    true
                }
            })
            ModernHookRegistry.installFast("$key:launch", launch, XposedInterface.Hooker { chain ->
                if (!CameraGallerySettings.active()) return@Hooker chain.proceed()
                if (Thread.currentThread().stackTrace.none {
                        it.className == alternate.declaringClass.name && it.methodName == alternate.name
                    }) return@Hooker chain.proceed()
                val owner = chain.thisObject ?: return@Hooker chain.proceed()
                val opened = runCatching {
                    val activity = activityField.get(owner) as? Activity ?: return@runCatching false
                    val intent = chain.getArg(0) as? Intent ?: return@runCatching false
                    CameraGalleryIntents.tryOpen(activity, intent.data,
                        chain.getArg(1) == true || intent.getBooleanExtra("IsSecureCamera", false) ||
                            secure.invoke(entryField.get(owner)) == true, "alternate")
                }.onFailure { HookLog.w(TAG, "Alternate preview routing failed; native preview retained", it) }.getOrDefault(false)
                if (opened) {
                    runCatching { reset.invoke(owner) }
                    null
                } else chain.proceed()
            })
            (listOf(preview, launch, alternate) + targets.drop(6)).distinct().forEach {
                runCatching { ModernHookRuntime.requireModule().deoptimize(it) }
            }
            groups["embedded"]?.let { embedded ->
                runCatching {
                    val entryGetter = embedded[1].apply { isAccessible = true }
                    val controllerField = CameraGalleryTargets.instanceField(embedded[0].declaringClass, entryGetter.declaringClass)
                    val entryActivity = CameraGalleryTargets.instanceField(secure.declaringClass, Activity::class.java)
                    ModernHookRegistry.installFast("$key:embedded", embedded[0], XposedInterface.Hooker { chain ->
                        if (!CameraGallerySettings.active()) return@Hooker chain.proceed()
                        val opened = runCatching {
                            val owner = chain.thisObject ?: return@runCatching false
                            val entry = entryGetter.invoke(controllerField.get(owner)) ?: return@runCatching false
                            val activity = entryActivity.get(entry) as? Activity ?: return@runCatching false
                            CameraGalleryIntents.tryOpen(activity, chain.getArg(1) as? Uri, secure.invoke(entry) == true, "embedded")
                        }.onFailure { HookLog.w(TAG, "Embedded preview routing failed; native preview retained", it) }.getOrDefault(false)
                        if (opened) null else chain.proceed()
                    })
                    (listOf(embedded[0]) + embedded.drop(2)).distinct().forEach {
                        runCatching { ModernHookRuntime.requireModule().deoptimize(it) }
                    }
                    HookLog.i(TAG, "Embedded gallery photo preview hook installed")
                }.onFailure {
                    ModernHookRegistry.unhook("$key:embedded")
                    HookLog.w(TAG, "Embedded preview hook unavailable; ordinary preview retained", it)
                }
            }
            HotReloadLifecycle.register(stateKey, capture = { groups })
            HookLog.i(TAG, "Default gallery photo preview hook installed")
        }.onFailure {
            ModernHookRegistry.unhookPrefix("$key:")
            HookLog.w(TAG, "Camera preview targets unavailable; native gallery retained", it)
        }
    }
}
