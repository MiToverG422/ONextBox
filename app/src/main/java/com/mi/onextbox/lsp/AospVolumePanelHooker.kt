package com.mi.onextbox.lsp

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.View
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface

/** C17 has a real Settings volume panel, but no active AOSP Compose panel navigator. */
internal object AospVolumePanelHooker {
    private const val MODEL = "com.android.systemui.volume.dialog.settings.ui.viewmodel.VolumeDialogSettingsButtonViewModel"
    fun hook(loader: ClassLoader) = runCatching {
        val model = Class.forName(MODEL, false, loader)
        val activityStarter = Class.forName("com.android.systemui.plugins.ActivityStarter", false, loader)
            .getMethod("startActivity", Intent::class.java, Boolean::class.javaPrimitiveType)
        ModernHookRegistry.installFast("aosp-volume:expanded-panel", model.getDeclaredMethod("onButtonClicked"),
            XposedInterface.Hooker { chain ->
                if (!AospVolumeSettings.enabledXposed()) return@Hooker chain.proceed()
                val host = requireNotNull(chain.thisObject)
                val opened = runCatching {
                    val context = field(host, "context") as Context
                    val intent = Intent(Settings.Panel.ACTION_VOLUME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    intent.component = intent.resolveActivity(context.packageManager) ?: return@runCatching false
                    val interactor = field(host, "interactor")
                    val globalState = field(interactor, "volumePanelGlobalStateInteractor")
                    activityStarter.invoke(field(globalState, "activityStarter"), intent, true)
                    // Preserve the native dismiss reason after the system accepts the panel route.
                    val visibility = field(interactor, "visibilityInteractor")
                    runCatching { visibility.javaClass.getMethod("dismissDialog", Int::class.javaPrimitiveType).invoke(visibility, 5) }
                    true
                }.getOrElse { HookLog.i("ONextBox-AospVolume", "Expanded panel route failed: $it"); false }
                if (opened) null else chain.proceed()
            })
        // The native click listener is small enough for ART to inline the ViewModel call.
        Class.forName("com.android.systemui.volume.dialog.settings.ui.binder.VolumeDialogSettingsButtonViewBinder\$bind\$4", false, loader)
            .getDeclaredMethod("onClick", View::class.java).let { ModernHookRuntime.requireModule().deoptimize(it) }
    }.onFailure { HookLog.i("ONextBox-AospVolume", "Expanded panel adapter unavailable: $it") }

    private fun field(host: Any, name: String): Any = requireNotNull(host.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(host))
}
