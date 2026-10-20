package com.mi.onextbox.lsp.compat

import com.mi.onextbox.lsp.SystemAdaptationSettings
import com.mi.onextbox.lsp.SystemAdaptationVersion

internal object AdaptationRuntimePolicy {
    fun selected(values: Map<String, Any?>?): SystemAdaptationVersion =
        if (values?.get(SystemAdaptationSettings.ALLOW_HYPER_OS) == true &&
            (values[SystemAdaptationSettings.KEY] as? Number)?.toInt() == SystemAdaptationVersion.HyperOs3.value)
            SystemAdaptationVersion.HyperOs3 else SystemAdaptationVersion.ColorOs17

    fun allowsKey(key: String, selected: SystemAdaptationVersion): Boolean = when {
        key == SystemAdaptationSettings.KEY || key == SystemAdaptationSettings.ALLOW_HYPER_OS -> true
        key.startsWith("hyperos3_") -> selected == SystemAdaptationVersion.HyperOs3
        else -> selected == SystemAdaptationVersion.ColorOs17
    }

    fun allowsHook(owner: SystemAdaptationVersion, selected: SystemAdaptationVersion): Boolean = owner == selected
}

/** Bind a generation to one profile; later package callbacks must not mix two implementations. */
internal object AdaptationHookGate {
    @Volatile private var owner: SystemAdaptationVersion? = null

    @Synchronized
    fun bind(): SystemAdaptationVersion = owner ?: HookConfigSnapshot.adaptationVersion().also { owner = it }

    fun isActive(profile: SystemAdaptationVersion): Boolean =
        AdaptationRuntimePolicy.allowsHook(profile, HookConfigSnapshot.adaptationVersion())

    fun isBoundProfileActive(): Boolean = owner?.let(::isActive) ?: true

    @JvmStatic
    fun isHyperOsActive(): Boolean = isActive(SystemAdaptationVersion.HyperOs3)
}
