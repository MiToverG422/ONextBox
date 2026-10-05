package com.mi.onextbox.lsp

import android.database.MatrixCursor
import android.os.Build
import com.mi.onextbox.lsp.compat.ModernMethodHook as XC_MethodHook
import com.mi.onextbox.lsp.compat.ModernReflect as XposedHelpers

/** Exposes the bundled OnePlus red-one card without changing the official red-one switch. */
object WallpapersRedOneEntryHooker {
    private const val TAG = "ONextBox-RedOneEntry"
    private const val MORE_VIEW_MODEL =
        "com.oplus.wallpapers.business.personalize.viewmodel.MoreNewPersonalViewModel"
    private val SEARCH_PROVIDERS = arrayOf(
        "com.oplus.wallpapers.WallpapersSearchIndexablesProvider",
        "com.oplus.wallpapers.business.personalize.PersonalSearchIndexProvider",
    )

    fun hook(classLoader: ClassLoader?) {
        val viewModel = XposedHelpers.findClassIfExists(MORE_VIEW_MODEL, classLoader)
        if (viewModel == null) {
            HookLog.w(TAG, "More personalization view model unavailable")
            return
        }
        runCatching {
            // In ColorOS 17 this private predicate is used only to filter the
            // `oplus_red_one` item from the bundled moreParam.json card list.
            XposedHelpers.findAndHookMethod(viewModel, "n", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (LspConfig.isWallpapersRedOneEntryEnabledXposed()) {
                        param.result = true
                    }
                }
            })
        }.onSuccess {
            HookLog.i(TAG, "Native red-one card gate hooked")
        }.onFailure {
            HookLog.w(TAG, "Native red-one card gate unavailable", it)
        }

        SEARCH_PROVIDERS.forEach { className ->
            val provider = XposedHelpers.findClassIfExists(className, classLoader)
                ?: return@forEach
            runCatching {
                val addItem = provider.getDeclaredMethod(
                    "addRedOneClockItem",
                    MatrixCursor::class.java,
                ).apply { isAccessible = true }
                XposedHelpers.findAndHookMethod(
                    provider,
                    "queryRawData",
                    Array<String>::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (!LspConfig.isWallpapersRedOneEntryEnabledXposed()) return
                            if (Build.BRAND.equals("oneplus", ignoreCase = true)) return
                            val cursor = param.result as? MatrixCursor ?: return
                            runCatching { addItem.invoke(param.thisObject, cursor) }
                                .onFailure { HookLog.w(TAG, "Red-one search index failed", it) }
                        }
                    },
                )
            }.onFailure { HookLog.w(TAG, "Search provider hook unavailable: $className", it) }
        }
    }
}
