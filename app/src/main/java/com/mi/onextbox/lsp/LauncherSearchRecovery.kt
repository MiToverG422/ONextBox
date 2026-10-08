package com.mi.onextbox.lsp

/** Runs the device probe once the launcher context is ready. */
internal class LauncherSearchBootstrap(private val installRecovery: () -> Unit) {
    private var started = false

    fun onApplicationReady(contextReady: Boolean) {
        if (!contextReady || started) return
        started = true
        installRecovery()
    }
}

internal data class LauncherSearchLayout(
    val folded: Boolean,
    val width: Int,
    val height: Int,
    val containerId: Int,
    val profileId: Int,
    val landscape: Boolean = false,
)

/** Search-box layout stability during a display switch. */
internal class LauncherSearchRecovery {
    private var previous: LauncherSearchLayout? = null

    fun isStable(layout: LauncherSearchLayout?): Boolean {
        val valid = layout?.takeIf { it.width > 0 && it.height > 0 }
        val stable = valid != null && valid == previous
        previous = valid
        return stable
    }
}

internal object LauncherSearchRecoveryRules {
    fun isEnabled(mode: Int, requested: Boolean): Boolean = requested &&
        (mode == LspConfig.LAUNCHER_SEARCH_BAR_MODE_CHINA ||
            mode == LspConfig.LAUNCHER_SEARCH_BAR_MODE_INTERNATIONAL)

    fun supportsInternationalDevice(
        foldScreen: Boolean,
        tablet: Boolean,
        folded: Boolean,
    ): Boolean = tablet || (foldScreen && folded)

    fun supportsTabletLayout(
        tablet: Boolean,
        landscape: Boolean,
        drawerOrStandard: Boolean,
    ): Boolean = tablet && landscape && drawerOrStandard

    fun canRestore(
        nativeSwitchEnabled: Boolean,
        providerSupported: Boolean,
        layoutSupported: Boolean,
    ): Boolean = nativeSwitchEnabled && providerSupported && layoutSupported
}
