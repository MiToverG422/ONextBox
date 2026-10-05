package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.ModernHookBridge
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernReflect
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Restores the CN branches of SpecialFeatureFragment (辅助功能), not AccessibilitySettings. */
internal object SettingsDomesticAuxiliaryHooker {
    private const val TAG = "ONextBox-AuxiliarySettings"
    private const val FRAGMENT = "com.oplus.settings.feature.spfunction.SpecialFeatureFragment"
    private val scopeDepth = ThreadLocal<Int>()
    private val controllers = Collections.synchronizedMap(WeakHashMap<Any, Boolean>())
    private val hookedMethods = ConcurrentHashMap.newKeySet<Method>()
    private val reported = AtomicBoolean(false)
    private val controllerMethods = setOf(
        "getAvailabilityStatus", "isAvailable", "displayPreference", "updateState",
        "onResume", "handlePreferenceTreeClick",
    )
    private val internationalPowerKeys = setOf("power_button", "shut_down_settings", "google_asssist")

    fun isDomesticScopeActive(): Boolean = (scopeDepth.get() ?: 0) > 0

    fun hook(loader: ClassLoader) {
        val fragment = ModernReflect.findClassIfExists(FRAGMENT, loader) ?: return
        fragment.declaredMethods.filter {
            it.name in setOf("onCreate", "createPreferenceControllers", "displayTile")
        }.forEach { method ->
            installScope(method, fragmentMethod = true)
        }

        // C17 caches this CN/EXP decision in Kotlin Lazy. Re-evaluate only the region
        // part inside our page; the official primary-user restriction still applies.
        val autofill = ModernReflect.findClassIfExists(
            "com.oplus.settings.feature.spfunction.AutofillPreferenceController\$Companion", loader,
        )
        val multiUser = ModernReflect.findClassIfExists("com.oplus.settings.multiuser.MultiUserUtils", loader)
        if (autofill != null && multiUser != null) {
            runCatching {
                ModernHookBridge.hookAllMethods(autofill, "isHideAutofill", object : ModernMethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!isDomesticScopeActive()) return
                        param.result = ModernReflect.callStaticMethod(multiUser, "isNotSystemUser")
                    }
                })
            }.onFailure { HookLog.w(TAG, "Unable to restore cached autofill availability", it) }
        }

        // isExpVersion can be inlined into these helpers, even with the caller deoptimized.
        mapOf(
            "com.oplus.settings.feature.convenient.controller.RedEnvelopeController" to setOf("isSupport"),
            "com.oplus.settings.feature.convenient.controller.RedEnvelopeController\$Companion" to setOf("isSupport"),
            "com.oplus.settings.feature.spfunction.AutofillPreferenceController" to setOf("isHideAutofill"),
            "com.oplus.settings.feature.spfunction.AutofillPreferenceController\$Companion" to setOf("isHideAutofill"),
            "com.oplus.settings.feature.convenient.controller.PowerButtonPreferenceController\$Companion" to
                setOf("isPowerButtonSupport"),
            "com.oplus.settings.feature.othersettings.aipower.AiPowerSettingPreferenceController\$Companion" to
                setOf("isNotSupportAiPower"),
            "com.oplus.settings.utils.SettingsUtils" to setOf("isGoogleAsssistSupport", "isExportUpgradeProject"),
        ).forEach { (name, names) ->
            ModernReflect.findClassIfExists(name, loader)?.declaredMethods?.filter {
                it.name in names
            }?.forEach { method ->
                runCatching { ModernHookRuntime.requireModule().deoptimize(method) }
                    .onFailure { HookLog.w(TAG, "Unable to deoptimize $name#${method.name}", it) }
            }
        }
        HookLog.i(TAG, "Domestic auxiliary page hooks registered")
    }

    private fun rememberControllers(result: Any?) {
        (result as? Iterable<*>)?.filterNotNull()?.forEach { controller ->
            val key = runCatching {
                ModernReflect.callMethod(controller, "getPreferenceKey") as? String
            }.getOrNull()
            controllers[controller] = key !in internationalPowerKeys
            // The actual page supplies its controller list, so additions in later Settings
            // versions retain their original availability rules without a forced list of entries.
            var type: Class<*>? = controller.javaClass
            while (type != null && type != Any::class.java) {
                type.declaredMethods.filter {
                    it.name in controllerMethods && !Modifier.isAbstract(it.modifiers)
                }.forEach { method -> installScope(method, fragmentMethod = false) }
                type = type.superclass
            }
        }
    }

    private fun installScope(method: Method, fragmentMethod: Boolean) {
        if (!hookedMethods.add(method)) return
        runCatching {
            if (!ModernHookRuntime.requireModule().deoptimize(method)) {
                HookLog.w(TAG, "Caller deoptimization unavailable: ${method.declaringClass.name}#${method.name}")
            }
            ModernHookBridge.hookMethodFast(method) { chain ->
                val domestic = if (fragmentMethod) true else controllers[chain.thisObject]
                val selected = domestic != null &&
                    LspConfig.isSettingsRestoreDomesticAuxiliaryFunctionsEnabledXposed()
                val previous = scopeDepth.get()
                if (selected) {
                    // Reset the enclosing fragment's CN scope for power entries. Simply
                    // excluding these controllers would still inherit onCreate's CN scope.
                    scopeDepth.set(if (domestic == true) (previous ?: 0) + 1 else 0)
                    if (domestic == true && reported.compareAndSet(false, true)) {
                        HookLog.i(TAG, "Restoring CN auxiliary options")
                    }
                }
                try {
                    val result = chain.proceed()
                    if (fragmentMethod && method.name == "createPreferenceControllers") {
                        runCatching { rememberControllers(result) }
                            .onFailure { HookLog.w(TAG, "Unable to register auxiliary controllers", it) }
                    }
                    result
                } finally {
                    if (selected) {
                        if (previous == null) scopeDepth.remove() else scopeDepth.set(previous)
                    }
                }
            }
        }.onFailure {
            hookedMethods.remove(method)
            HookLog.w(TAG, "Unable to scope ${method.declaringClass.name}#${method.name}", it)
        }
    }
}
