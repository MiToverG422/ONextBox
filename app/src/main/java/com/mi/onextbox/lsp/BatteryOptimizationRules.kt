package com.mi.onextbox.lsp

internal object BatteryOptimizationRules {
    const val PACKAGE_NAME = "com.oplus.battery"
    const val RESTRICT_APPLICATION = "com.oplus.battery.restrictdynamicfeature.RestrictApplication"

    fun isRestrictLoader(returnType: String, parameters: List<String>, isStatic: Boolean): Boolean =
        returnType == "void" && parameters.isEmpty() && !isStatic

    /** Build off to the side so a failed OEM extension never leaves the caller's list half-written. */
    fun defaultWhitelist(
        defaults: List<String>,
        addCustomize: (ArrayList<String>) -> Unit,
        addFelica: (ArrayList<String>) -> Unit,
    ): ArrayList<String> = ArrayList(defaults).apply {
        addCustomize(this)
        addFelica(this)
    }
}
