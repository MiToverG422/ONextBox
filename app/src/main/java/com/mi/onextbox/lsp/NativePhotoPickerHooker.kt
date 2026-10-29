package com.mi.onextbox.lsp

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import com.mi.onextbox.lsp.compat.ModernHookRegistry

internal object NativePhotoPickerHooker {
    private const val TAG = "NativePhotoPicker"
    fun hook(loader: ClassLoader) {
        runCatching {
            val method = loader.loadClass("com.android.photopicker.MainActivity").getDeclaredMethod("onCreate", Bundle::class.java)
            ModernHookRegistry.installFast("$TAG:onCreate", method) { chain ->
                val result = chain.proceed()
                val activity = chain.thisObject as Activity
                if (SystemExtrasSettings.active(SystemExtrasSettings.NATIVE_PICKER) && !activity.isFinishing) {
                    val target = NativePhotoPickerRules.target(activity.intent.action)
                    if (target != null) runCatching {
                        val component = ComponentName("com.android.providers.media.module", "com.android.providers.media.photopicker.$target")
                        @Suppress("DEPRECATION")
                        val info = activity.packageManager.getActivityInfo(component, 0)
                        check(info.enabled && info.exported && info.applicationInfo.enabled)
                        val intent = Intent(activity.intent).apply {
                            setPackage(component.packageName)
                            this.component = component
                            addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
                        }
                        activity.startActivity(intent)
                        activity.finish()
                        activity.overridePendingTransition(0, 0)
                        HookLog.i(TAG, "Forwarded ${intent.action} to native picker")
                    }.onFailure { HookLog.w(TAG, "Native picker unavailable; original retained", it) }
                }
                result
            }
            HookLog.i(TAG, "Photo picker result forwarding installed")
        }.onFailure { HookLog.w(TAG, "Photo picker entry unavailable", it) }
    }
}

internal object NativePhotoPickerRules {
    fun target(action: String?): String? = when (action) {
        "android.provider.action.PICK_IMAGES" -> "PhotoPickerActivity"
        "android.intent.action.GET_CONTENT" -> "PhotoPickerGetContentActivity"
        "android.provider.action.USER_SELECT_IMAGES_FOR_APP" -> "PhotoPickerUserSelectActivity"
        else -> null
    }
}
