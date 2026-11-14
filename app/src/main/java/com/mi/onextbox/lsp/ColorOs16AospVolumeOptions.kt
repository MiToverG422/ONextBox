package com.mi.onextbox.lsp

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import io.github.libxposed.api.XposedInterface
import java.lang.ref.WeakReference
import java.util.WeakHashMap

internal object ColorOs16AospVolumeOptions {
    private data class Original(val context: Context, val percent: Int)
    private val dialogs = WeakHashMap<Any, Original>()
    private var shadeState = WeakReference<Any>(null)

    fun hook(loader: ClassLoader) {
        val native = Class.forName(ColorOs16AospVolumeHooker.NATIVE, false, loader)
        val contextField = native.getDeclaredField("mContext").apply { isAccessible = true }
        val changed = native.getDeclaredField("mConfigChanged").apply { isAccessible = true }
        val showing = native.getDeclaredField("mShowing").apply { isAccessible = true }
        val dismiss = native.getDeclaredMethod("dismissH", Int::class.javaPrimitiveType).apply { isAccessible = true }
        fun hide(host: Any) { if (showing.getBoolean(host)) dismiss.invoke(host, 2) }
        fun hidden() = AospVolumeSettings.hideInQsXposed() && AospVolumeQsState.visible(shadeState.get())
        HotReloadLifecycle.register("aosp-volume:c16-options",
            canPrepare = { dialogs.keys.none { showing.getBoolean(it) } },
            canReload = { dialogs.keys.none { showing.getBoolean(it) } },
            capture = { arrayOf(shadeState.get(), dialogs.map { (host, original) -> arrayOf(host, original.context, original.percent) }.toTypedArray()) },
            detach = { dialogs.forEach { (host, original) -> contextField.set(host, original.context); changed.setBoolean(host, true) } },
            restore = { saved ->
                val values = saved as? Array<*>
                shadeState = WeakReference(values?.getOrNull(0))
                (values?.getOrNull(1) as? Array<*>)?.forEach { item ->
                    val row = item as Array<*>
                    val host = requireNotNull(row[0]); val base = row[1] as Context; val percent = row[2] as Int
                    dialogs[host] = Original(base, percent)
                    contextField.set(host, AospVolumeDensityContext.create(base, percent))
                    changed.setBoolean(host, true)
                }
            })
        // Imported native objects have no module callbacks. Rebuild them at the next native show.
        (HotReloadLifecycle.restoredState("aosp-volume:c16-options") as? Array<*>)?.let { values ->
            shadeState = WeakReference(values.getOrNull(0))
            (values.getOrNull(1) as? Array<*>)?.forEach { item ->
                val row = item as Array<*>
                dialogs[requireNotNull(row[0])] = Original(row[1] as Context, 100)
            }
        }
        ModernHookRegistry.installFast("aosp-volume:c16-layout", native.getDeclaredMethod("initDialog", Int::class.javaPrimitiveType),
            XposedInterface.Hooker { chain ->
                val host = requireNotNull(chain.thisObject)
                val base = dialogs[host]?.context ?: contextField.get(host) as Context
                val percent = AospVolumeSettings.scaleXposed()
                contextField.set(host, AospVolumeDensityContext.create(base, percent))
                dialogs[host] = Original(base, percent)
                chain.proceed()
            })
        val callback = Class.forName(ColorOs16AospVolumeHooker.CALLBACK, false, loader)
        val owner = callback.getDeclaredField("this\$0").apply { isAccessible = true }
        ModernHookRegistry.installFast("aosp-volume:c16-show", callback.getDeclaredMethod("onShowRequested",
            Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType), XposedInterface.Hooker { chain ->
            val host = owner.get(chain.thisObject)
            if (hidden()) { hide(host); null } else {
                if (dialogs[host]?.percent != AospVolumeSettings.scaleXposed()) changed.setBoolean(host, true)
                chain.proceed()
            }
        })
        val shade = Class.forName("com.android.systemui.shade.NotificationShadeWindowControllerImpl", false, loader)
        val apply = shade.declaredMethods.single { it.name == "apply" && it.parameterTypes.singleOrNull()?.simpleName == "NotificationShadeWindowState" }
        val main = Handler(Looper.getMainLooper())
        var wasQsVisible = AospVolumeQsState.visible(shadeState.get())
        ModernHookRegistry.installFast("aosp-volume:c16-qs", apply, XposedInterface.Hooker { chain ->
            shadeState = WeakReference(chain.getArg(0))
            val result = chain.proceed()
            val visible = AospVolumeQsState.visible(shadeState.get())
            val justOpened = visible && !wasQsVisible
            wasQsVisible = visible
            if (justOpened && hidden()) main.post {
                HotReloadLifecycle.withHook(fallback = {}, action = {
                    if (hidden()) dialogs.keys.toList().forEach { runCatching { hide(it) } }
                })
            }
            result
        })
        val click = Class.forName("${ColorOs16AospVolumeHooker.NATIVE}\$\$ExternalSyntheticLambda11", false, loader)
        val clickOwner = click.getDeclaredField("f\$0").apply { isAccessible = true }
        val settingsIcon = native.getDeclaredField("mSettingsIcon").apply { isAccessible = true }
        val navigator = native.getDeclaredField("mVolumeNavigator").apply { isAccessible = true }
        val navigatorType = navigator.type
        val starter = navigatorType.getDeclaredField("activityStarter").apply { isAccessible = true }
        val start = Class.forName("com.android.systemui.plugins.ActivityStarter", false, loader)
            .getMethod("startActivity", Intent::class.java, Boolean::class.javaPrimitiveType)
        ModernHookRegistry.installFast("aosp-volume:c16-panel", click.getDeclaredMethod("onClick", View::class.java), XposedInterface.Hooker { chain ->
            val host = clickOwner.get(chain.thisObject)
            if (!AospVolumeSettings.enabledXposed() || settingsIcon.get(host) !== chain.getArg(0)) return@Hooker chain.proceed()
            val opened = runCatching {
                val context = contextField.get(host) as Context
                val intent = Intent(Settings.Panel.ACTION_VOLUME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.component = intent.resolveActivity(context.packageManager) ?: return@runCatching false
                start.invoke(starter.get(navigator.get(host)), intent, true)
                true
            }.getOrElse { HookLog.i("ONextBox-AospVolume", "C16 expanded panel route failed: $it"); false }
            if (opened) { runCatching { dismiss.invoke(host, 5) }; null } else chain.proceed()
        })
    }
}
