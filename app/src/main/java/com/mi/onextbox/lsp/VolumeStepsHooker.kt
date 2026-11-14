package com.mi.onextbox.lsp

import android.annotation.SuppressLint
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import java.lang.reflect.Modifier

internal object VolumeStepsHooker {
    private const val TAG = "VolumeSteps"

    internal fun target(loader: ClassLoader) =
        Class.forName("com.android.server.audio.AudioService", false, loader)
            .getDeclaredMethod("createStreamStates").apply { require(returnType == Void.TYPE) }

    // Runs only in system_server via Xposed, using the host's AudioSystem fields.
    // Unsupported field layouts are caught below and retain the system defaults.
    @SuppressLint("SoonBlockedPrivateApi")
    fun hook(loader: ClassLoader) {
        runCatching {
            val create = target(loader)
            val owner = create.declaringClass
            val maximum = owner.getDeclaredField("MAX_STREAM_VOLUME").apply {
                require(type == IntArray::class.java && Modifier.isStatic(modifiers)); isAccessible = true
            }
            val minimum = owner.getDeclaredField("MIN_STREAM_VOLUME").apply { isAccessible = true }
            val defaults = Class.forName("android.media.AudioSystem", false, loader)
                .getDeclaredField("DEFAULT_STREAM_VOLUME").apply { isAccessible = true }
            ModernHookRegistry.installFast("$TAG:create", create) { chain ->
                if (SystemCustomizationSettings.active(SystemCustomizationSettings.Feature.VolumeSteps)) {
                    val max = maximum.get(null) as IntArray
                    val min = minimum.get(null) as IntArray
                    val default = defaults.get(null) as IntArray
                    for ((stream, setting) in SystemCustomizationSettings.streamSteps) {
                        if (stream !in max.indices || stream !in min.indices) continue
                        val before = max[stream]
                        val next = SystemCustomizationRules.volumeMaximum(SystemCustomizationSettings.value(setting), min[stream], before)
                        if (next == before) continue
                        if (stream in default.indices && default[stream] >= 0) {
                            default[stream] = SystemCustomizationRules.rescaleVolume(default[stream], before, next)
                                .coerceIn(min[stream], next)
                        }
                        max[stream] = next
                        HookLog.i(TAG, "Native volume maximum initialized: stream=$stream; $before -> $next")
                    }
                }
                chain.proceed()
            }
            HookLog.i(TAG, "Native volume initialization adapter installed")
        }.onFailure { HookLog.w(TAG, "Native volume ranges unavailable; system steps retained", it) }
    }
}
