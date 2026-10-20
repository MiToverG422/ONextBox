package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.hyperos.Force185

internal object HyperOsPerformanceHooker {
    private var runtime: Force185? = null
    private var initialized = false

    @Synchronized
    fun hook(packageName: String, loader: ClassLoader) {
        if (!Force185.supported()) return
        if (!initialized) {
            initialized = true
            val displayScope = packageName == "system"
            val refresh = displayScope && HyperOsPerformanceSettings.enabledXposed(HyperOsPerformanceSettings.REFRESH)
            val touch = HyperOsPerformanceSettings.enabledXposed(HyperOsPerformanceSettings.TOUCH)
            val idle = displayScope && HyperOsPerformanceSettings.enabledXposed(HyperOsPerformanceSettings.IDLE)
            // These vendor locks have no verified live reset. Keep the owner until reboot.
            HotReloadLifecycle.register(
                key = "hyperos.performance.rebootRequired",
                canReload = {
                    !refresh && !touch && !idle &&
                        !(displayScope && HyperOsPerformanceSettings.enabledXposed(HyperOsPerformanceSettings.IDLE)) &&
                        !(displayScope && HyperOsPerformanceSettings.enabledXposed(HyperOsPerformanceSettings.REFRESH)) &&
                        !HyperOsPerformanceSettings.enabledXposed(HyperOsPerformanceSettings.TOUCH)
                },
            )
            if (refresh || touch || idle) runtime = Force185(refresh, touch, idle)
        }
        val active = runtime ?: return
        if (packageName == "system") active.installSystem(loader)
        else active.installPackage(packageName, loader)
        HotReloadLifecycle.afterCommit { active.startTouch() }
    }
}
