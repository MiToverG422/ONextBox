package com.mi.onextbox.lsp

import android.media.AudioManager
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface

internal object VolumeSafetyWarningHooker {
    private const val TAG = "ONextBox-VolumeSafety"
    private const val CALLBACK_CLASS =
        "com.oplus.systemui.volume.repository.OplusVolumeRepository\$mControllerCallbackH\$1"

    fun hook(loader: ClassLoader) {
        runCatching {
            val type = Class.forName(CALLBACK_CLASS, false, loader)
            val method = type.getDeclaredMethod(
                "onShowSafetyWarning",
                Int::class.javaPrimitiveType,
            )
            ModernHookRegistry.installFast(
                "volume-safety-warning:${method.toGenericString()}",
                method,
                XposedInterface.Hooker { chain ->
                    if (!VolumeSafetyWarningSettings.enabledXposed()) {
                        return@Hooker chain.proceed()
                    }
                    try {
                        val repository = requireNotNull(
                            ModernReflect.getObjectField(
                                requireNotNull(chain.thisObject),
                                "this\$0",
                            ),
                        )
                        val volumeDialog = requireNotNull(
                            ModernReflect.getObjectField(
                                repository,
                                "mOplusVolumeDialogInterface",
                            ),
                        )
                        val controller = requireNotNull(
                            ModernReflect.getObjectField(volumeDialog, "mController"),
                        )
                        val audioManager = ModernReflect.callMethod(
                            controller,
                            "getAudioManager",
                        ) as? AudioManager ?: error("VolumeDialogController returned no AudioManager")
                        ModernReflect.callMethod(audioManager, "disableSafeMediaVolume")
                        null
                    } catch (error: Throwable) {
                        HookLog.w(
                            TAG,
                            "Unable to suppress the hearing safety reminder; stock warning retained",
                            error,
                        )
                        chain.proceed()
                    }
                },
            )
            HookLog.i(TAG, "Hearing safety warning hook installed")
        }.onFailure { error ->
            HookLog.w(TAG, "Hearing safety warning hook unavailable", error)
        }
    }
}
