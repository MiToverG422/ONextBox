package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.HotReloadResourceCoordinator
import java.lang.reflect.Executable

/** Inert owners also cover an enabled old generation when the replacement is disabled. */
internal object NativeFiveGaPhoneResources {
    private val sessionOwners = setOf(
        "com.oplus.internal.telephony.OplusServiceStateTrackerImpl",
        "com.oplus.internal.telephony.OplusServiceStateTrackerImpl\$3",
        "com.oplus.internal.telephony.OplusNetworkManagerImpl",
        "com.android.internal.telephony.DisplayInfoController",
        "com.oplus.internal.telephony.localbus.LocalCarrierConfigManager",
    )
    fun isSessionOwner(name: String) = name in sessionOwners
    fun inactiveTargets(previous: Collection<Executable>): List<Executable> =
        previous.filter { isSessionOwner(it.declaringClass.name) }

    // Old live method hooks also require coverage. Do not detach an installed session when
    // its disabled replacement intentionally installs no telephony hooks or communication.
    fun canReplaceInstalledSession(enabled: Boolean, ownsNativeState: Boolean) = enabled && !ownsNativeState

    fun registerInactive(lifecycle: HotReloadResourceCoordinator, sessionLoader: ClassLoader? = null) {
        lifecycle.registerReplay("phone:native-five-ga-loader", capture = { sessionLoader }, replay = {})
        lifecycle.register("phone:native-five-ga-discovery")
        lifecycle.register("phone:native-five-ga")
    }
}
