package com.mi.onextbox.lsp

import android.view.View
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernMethodHook
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal object ColorOs16SettingsHooks {
    private const val TAG = "ONextBox-C16Settings"
    private const val CN_FEATURE = "com.android.settings.cn_version"
    private val loaders = ConcurrentHashMap.newKeySet<ClassLoader>()
    private val internationalApplied = AtomicBoolean(false)

    fun hook(classLoader: ClassLoader) {
        if (!loaders.add(classLoader)) return
        val loaderPrefix = "c16-settings@${System.identityHashCode(classLoader)}:"
        ColorOs16SettingsTargets.installCompatible(
            resolveClass = { Class.forName(it, false, classLoader) },
            install = { targets ->
                val prefix = "$loaderPrefix${targets.feature}:"
                try {
                    install(targets, prefix)
                    HookLog.i(TAG, "${targets.feature}: ${targets.methods.size} verified hooks installed")
                } catch (error: Throwable) {
                    runCatching { ModernHookRegistry.unhookPrefix(prefix) }
                        .onFailure { HookLog.w(TAG, "${targets.feature}: hook cleanup failed", it) }
                    throw error
                }
            },
            failed = { feature, error ->
                HookLog.w(TAG, "$feature: incompatible target; stock behavior retained", error)
            },
        )
    }

    private fun install(targets: ColorOs16SettingsTargets, prefix: String) {
        val callbacks = when (targets.feature) {
            ColorOs16SettingsFeature.GoogleEntry -> listOf(
                constant(LspConfig::isSettingsForceGoogleEntryEnabledXposed, 0),
            )
            ColorOs16SettingsFeature.AppOpenButton -> {
                val button = checkNotNull(targets.recoveryButton).also { it.isAccessible = true }
                listOf(
                    constant(LspConfig::isSettingsRestoreAppOpenButtonEnabledXposed, false),
                    object : ModernMethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (!LspConfig.isSettingsRestoreAppOpenButtonEnabledXposed()) return
                            val feature = param.thisObject ?: return
                            (button.get(feature) as? View)?.visibility = View.GONE
                        }
                    },
                )
            }
            ColorOs16SettingsFeature.InternationalSettings -> targets.methods.map { method ->
                object : ModernMethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!LspConfig.isSettingsInternationalEnabledXposed()) return
                        if (method.name == "isExpVersion") {
                            param.result = !SettingsDomesticAuxiliaryHooker.isDomesticScopeActive()
                        } else {
                            if (param.args.none { it == CN_FEATURE }) return
                            param.result = SettingsDomesticAuxiliaryHooker.isDomesticScopeActive()
                        }
                        if (internationalApplied.compareAndSet(false, true)) {
                            HookLog.i(TAG, "Settings switched to international mode")
                        }
                    }
                }
            }
            ColorOs16SettingsFeature.AppAutoStart -> targets.methods.map {
                constant(LspConfig::isSettingsForceAppAutoStartEnabledXposed, 0)
            }
        }
        check(callbacks.size == targets.methods.size)
        targets.methods.zip(callbacks).forEachIndexed { index, (method, callback) ->
            ModernHookRegistry.installCompat("$prefix$index", method, callback)
        }
    }

    private fun constant(enabled: () -> Boolean, result: Any) = object : ModernMethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            if (enabled()) param.result = result
        }
    }
}
