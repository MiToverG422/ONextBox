package com.mi.onextbox.lsp

import android.content.Context
import android.widget.FrameLayout
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect
import java.lang.reflect.Method

internal object FileManagerHooker {
    private const val TAG = "ONextBox-FileManager"

    fun hook(classLoader: ClassLoader) {
        runCatching {
            val helper = ModernReflect.findClassIfExists("in.d", classLoader)
            val card = ModernReflect.findClassIfExists("jn.d", classLoader)
            if (helper == null || card == null || !FrameLayout::class.java.isAssignableFrom(card)) {
                HookLog.w(TAG, "Secure access card unavailable")
                return
            }
            val methods = helper.declaredMethods
            val predicate = methods.singleOrNull {
                FileManagerCardRules.isDisplayPredicate(it.name, it.returnType.name, it.parameterNames())
            }
            val factory = methods.singleOrNull {
                FileManagerCardRules.isCardFactory(it.name, it.returnType.name, it.parameterNames())
            }
            if (predicate == null || factory?.returnType != card) {
                HookLog.w(TAG, "Unsupported secure access card signature")
                return
            }
            ModernHookRegistry.installCompat(
                key = "$TAG@${System.identityHashCode(classLoader)}:secure-access-card",
                executable = predicate,
                callback = object : ModernMethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val context = param.args.firstOrNull() as? Context ?: return
                        // The native false branch removes the card and resets its top inset.
                        if (FileManagerCardRules.shouldHide(
                                LspConfig.isFileManagerHideSecureAccessTipEnabledXposed(),
                                context.packageName,
                            )
                        ) {
                            param.result = false
                        }
                    }
                },
            )
            HookLog.i(TAG, "Secure access card display hook installed")
        }.onFailure { HookLog.w(TAG, "Secure access card hook failed", it) }
    }

    private fun Method.parameterNames(): List<String> = parameterTypes.map { it.name }
}
