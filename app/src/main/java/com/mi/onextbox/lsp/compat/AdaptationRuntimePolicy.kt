package com.mi.onextbox.lsp.compat

import com.mi.onextbox.lsp.SystemAdaptationVersion

internal object AdaptationRuntimePolicy {
    fun allowsHook(owner: SystemAdaptationVersion, selected: SystemAdaptationVersion): Boolean =
        owner == selected

    fun allowsSystemServer(version: SystemAdaptationVersion): Boolean =
        version == SystemAdaptationVersion.ColorOs17 || "system" in ColorOs16Capabilities.packages

    fun allowsPackage(version: SystemAdaptationVersion, packageName: String): Boolean =
        version == SystemAdaptationVersion.ColorOs17 || packageName in ColorOs16Capabilities.packages

    fun allowsFeatureBoolean(version: SystemAdaptationVersion, key: String): Boolean =
        allowsFeatureValue(version, key)

    fun allowsFeatureValue(version: SystemAdaptationVersion, key: String): Boolean =
        version == SystemAdaptationVersion.ColorOs17 || key in ColorOs16Capabilities.featureKeys
}

/** One module generation must never mix hooks belonging to different system versions. */
internal open class AdaptationProfileGate(
    private val enabled: () -> Boolean = { true },
    private val selectedVersion: () -> SystemAdaptationVersion,
) {
    @Volatile private var owner: SystemAdaptationVersion? = null

    @Synchronized
    fun bind(): SystemAdaptationVersion = owner ?: selectedVersion().also { owner = it }

    fun isActive(profile: SystemAdaptationVersion): Boolean =
        enabled() && AdaptationRuntimePolicy.allowsHook(profile, selectedVersion())

    fun isBoundProfileActive(): Boolean = enabled() && (owner?.let(::isActive) ?: true)
}

internal object AdaptationHookGate : AdaptationProfileGate({ !SafeModeRuntime.blocked }, HookConfigSnapshot::adaptationVersion)
