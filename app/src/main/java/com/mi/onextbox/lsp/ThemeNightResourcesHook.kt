package com.mi.onextbox.lsp

import android.os.Process
import com.mi.onextbox.lsp.compat.HookConfigSnapshot
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime

internal object ThemeNightResourcesHook {
    fun install(loader: ClassLoader) {
        runCatching {
            val base = Class.forName("android.content.res.OplusBaseFile", false, loader)
            val ext = Class.forName("android.content.res.IResourcesImplExt", false, loader)
            val reject = base.getDeclaredMethod("rejectTheme", ext, String::class.java)
            val checkUpdate = Class.forName("android.content.res.OplusThemeResources", false, loader)
                .getDeclaredMethod("checkUpdate")
            val defaultTheme = Class.forName("com.oplus.theme.OplusThirdPartUtil", false, loader)
                .getDeclaredMethod("getDefaultTheme", Int::class.javaPrimitiveType)
            if (reject.returnType != Boolean::class.javaPrimitiveType || defaultTheme.returnType != reject.returnType) return
            val userId = Process.myUid() / 100000
            ModernHookRegistry.installFast("theme-store:night-resources", reject) { chain ->
                val rejected = chain.proceed()
                if (rejected == true && HookConfigSnapshot.boolean(ThemeStoreSettings.NIGHT_RESOURCES, false) == true &&
                    runCatching { defaultTheme.invoke(null, userId) == false }.getOrDefault(false)) false
                else rejected
            }
            ModernHookRuntime.requireModule().deoptimize(checkUpdate)
        }.onFailure { HookLog.w("ONextBox-Theme", "Night theme resource hook unavailable", it) }
    }
}
