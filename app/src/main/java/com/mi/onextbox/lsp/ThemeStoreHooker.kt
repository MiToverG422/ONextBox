package com.mi.onextbox.lsp

import android.content.Context
import android.content.Intent
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

internal object ThemeStoreHookRules {
    private val expireActions = setOf(
        "THEME_TRIAL_EXPIRE", "FONT_TRIAL_EXPIRE", "LIVE_WP_TRIAL_EXPIRE",
        "SKU_SYSTEMUI_TRIAL_EXPIRE", "SKU_LOCKSCREEN_TRIAL_EXPIRE", "RESOURCE_EXPIRE", "VIP_EXPIRE",
    ).mapTo(hashSetOf()) { "com.nearme.themespace.action.$it" }
    fun skipExpiry(enabled: Boolean, action: String?) = enabled && action in expireActions
    fun numericValue(type: Class<*>, value: Int): Any? = when (type) {
        Int::class.javaPrimitiveType, Int::class.javaObjectType -> value
        Long::class.javaPrimitiveType, Long::class.javaObjectType -> value.toLong()
        Float::class.javaPrimitiveType, Float::class.javaObjectType -> value.toFloat()
        Double::class.javaPrimitiveType, Double::class.javaObjectType -> value.toDouble()
        else -> null
    }
}

internal object ThemeStoreHooker {
    private const val TAG = "ONextBox-ThemeStore"
    private val delivered = AtomicBoolean()
    private fun report() {
        if (delivered.compareAndSet(false, true)) HookLog.i(TAG, "Theme-store client VIP hooks reached")
    }

    fun hook(loader: ClassLoader, colorOs16: Boolean = false) {
        var installed = 0
        val targets = mapOf(
            "com.oppo.cdo.card.theme.dto.vip.VipUserDto" to mapOf("getVipStatus" to 1, "getVipDays" to 999),
            "com.oppo.cdo.card.theme.dto.page.WeatherPageResponseDto" to mapOf("getVipStatus" to 1),
            "com.oppo.cdo.theme.domain.dto.response.ResourceItemDto" to mapOf("getIsVip" to 1, "getIsVipAvailable" to 1),
            "com.oppo.cdo.theme.domain.dto.response.PublishProductItemDto" to mapOf("getPrice" to 0, "getIsVipAvailable" to 1),
        )
        targets.forEach { (name, getters) ->
            val type = ModernReflect.findClassIfExists(name, loader) ?: return@forEach
            getters.forEach { (getter, value) ->
                runCatching {
                    val method = type.getDeclaredMethod(getter)
                    val replacement = ThemeStoreHookRules.numericValue(method.returnType, value)
                        ?: error("Unsupported return type: ${method.returnType.name}")
                    install(method) { replacement }
                    installed++
                }.onFailure { HookLog.w(TAG, "VIP getter unavailable: $name.$getter", it) }
            }
        }
        if (!colorOs16) ThemeStoreAdsHooker.hook(loader)
        runCatching {
            val receiver = ModernReflect.findClassIfExists("com.nearme.themespace.trial.ThemeTrialExpireReceiver", loader)
                ?: return@runCatching
            val method = receiver.getDeclaredMethod("onReceive", Context::class.java, Intent::class.java)
            ModernHookRegistry.installFast("$TAG:expiry", method, XposedInterface.Hooker { chain ->
                if (ThemeStoreHookRules.skipExpiry(ThemeStoreSettings.enabledXposed(), (chain.getArg(1) as? Intent)?.action)) {
                    report()
                    null
                } else chain.proceed()
            })
            installed++
        }.onFailure { HookLog.w(TAG, "Trial expiry adapter unavailable", it) }
        HookLog.i(TAG, "Theme-store hooks installed: $installed endpoints")
    }

    private fun install(method: Method, replacement: () -> Any?) {
        ModernHookRegistry.installFast("$TAG:${method.toGenericString()}", method, XposedInterface.Hooker { chain ->
            if (ThemeStoreSettings.enabledXposed()) {
                report()
                replacement()
            } else chain.proceed()
        })
    }
}
