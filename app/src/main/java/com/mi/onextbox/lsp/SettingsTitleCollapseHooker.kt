package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface

// Initial collapsed title state in system settings.
internal object SettingsTitleCollapseHooker {
    private const val TAG = "ONextBox-SettingsTitle"

    fun hook(classLoader: ClassLoader) {
        if (!LspConfig.isSettingsTitleCollapsedEnabledXposed()) return
        runCatching {
            val appBar = ModernReflect.findClassIfExists(
                "com.google.android.material.appbar.SettingsCollapsableAppBarLayout", classLoader,
            ) ?: return
            val attached = appBar.getDeclaredMethod("onAttachedToWindow")
            val expanded = ModernReflect.findMethodExact(
                appBar, "setExpanded", arrayOf(java.lang.Boolean.TYPE, java.lang.Boolean.TYPE),
            )
            ModernHookRegistry.installFast(
                "$TAG@${System.identityHashCode(classLoader)}", attached,
                XposedInterface.Hooker { chain ->
                    val result = chain.proceed()
                    if (LspConfig.isSettingsTitleCollapsedEnabledXposed()) {
                        runCatching { expanded.invoke(chain.thisObject, false, false) }
                            .onFailure { HookLog.w(TAG, "Unable to collapse title", it) }
                    }
                    result
                },
            )
            HookLog.i(TAG, "Title collapse hook installed")
        }.onFailure { HookLog.w(TAG, "Title collapse unavailable", it) }
    }
}
