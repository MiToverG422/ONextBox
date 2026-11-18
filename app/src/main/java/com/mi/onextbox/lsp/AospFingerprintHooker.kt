package com.mi.onextbox.lsp

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.widget.ImageView
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookBridge
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect
import java.util.concurrent.atomic.AtomicBoolean

internal object AospFingerprintHooker {
    fun hook(loader: ClassLoader) = runCatching {
        val type = HostClassLookup.load("com.oplus.systemui.biometrics.finger.udfps.OnScreenFingerprintUiMech", false, loader)
        val applied = AtomicBoolean(false)
        ModernHookBridge.hookAllMethods(type, "loadAnimDrawables", object : ModernMethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!AospUiSettings.enabledXposed(AospUiSettings.FINGERPRINT)) return
                val owner = param.thisObject ?: return
                val context = ModernReflect.getObjectField(owner, "context") as Context
                val id = context.resources.getIdentifier("ic_fingerprint", "drawable", "com.android.systemui")
                if (id == 0) return
                val original = ModernReflect.getObjectField(owner, "imMobileDrawable") as? Drawable ?: return
                val previous = ModernReflect.getObjectField(owner, "imMobileDrawableHY")
                val size = original.intrinsicWidth.coerceAtLeast((32 * context.resources.displayMetrics.density).toInt())
                val glyph = SizedGlyph(context.getDrawable(id)!!.mutate(), size)
                ModernReflect.setObjectField(owner, "imMobileDrawable", glyph)
                ModernReflect.setObjectField(owner, "imMobileDrawableHY", glyph)
                val icon = ModernReflect.getObjectField(owner, "fpIcon") as? ImageView
                if (icon?.drawable === original || icon?.drawable === previous) icon?.setImageDrawable(glyph)
                if (applied.compareAndSet(false, true)) HookLog.i("ONextBox-AospFingerprint", "Native fingerprint glyph applied to the OEM sensor view")
            }
        })
        HotReloadLifecycle.register("aosp-fingerprint", canPrepare = { !applied.get() }, canReload = { !applied.get() })
    }.onFailure { HookLog.w("ONextBox-AospFingerprint", "Native glyph adapter unavailable", it) }

    private class SizedGlyph(drawable: Drawable, private val size: Int) : DrawableWrapper(drawable) {
        override fun getIntrinsicWidth() = size
        override fun getIntrinsicHeight() = size
    }
}
