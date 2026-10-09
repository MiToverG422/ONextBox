package com.mi.onextbox.lsp

internal object TrafficManagementRules {
    const val PACKAGE_NAME = "com.oplus.trafficmonitor"
    val hiddenPackages = setOf("com.nearme.deamon", "com.redteamobile.roaming.deamon")

    // Network policies apply to the entire UID. Exclude system, isolated and SDK sandbox UIDs.
    fun isApplicationUid(uid: Int): Boolean = uid >= 0 && uid % 100_000 in 10_000..19_999

    fun roamingMode(saved: Int?, backgroundRestricted: Boolean): Int =
        saved?.takeIf { it in 0..2 } ?: if (backgroundRestricted) 1 else 0

    fun isRoamingChoiceAdapter(adapterClass: String, items: List<String?>, labels: List<String>): Boolean =
        adapterClass.startsWith("androidx.appcompat.app.AlertController\$") &&
            labels.size == 3 && labels.all { it.isNotBlank() } && labels.distinct().size == 3 && items == labels
}
