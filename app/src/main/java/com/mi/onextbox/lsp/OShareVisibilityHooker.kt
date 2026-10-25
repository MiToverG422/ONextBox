package com.mi.onextbox.lsp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.content.res.TypedArray
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Modifier

internal object OShareVisibilityHooker {
    private const val TAG = "ONextBox-OShare"

    fun hook(loader: ClassLoader) = install(loader, colorOs16 = false)

    fun hookColorOs16(loader: ClassLoader) = install(loader, colorOs16 = true)

    private fun install(loader: ClassLoader, colorOs16: Boolean) {
        val key = "$TAG:visibility-timeout@${System.identityHashCode(loader)}"
        val keys = mutableListOf<String>()
        runCatching {
            val receiver = Class.forName("com.oplus.oshare.device.TimeoutReceiver", false, loader)
            require(BroadcastReceiver::class.java.isAssignableFrom(receiver))
            val method = receiver.getDeclaredMethod("onReceive", Context::class.java, Intent::class.java)
            require(method.returnType == Void.TYPE && !Modifier.isStatic(method.modifiers))
            val countdowns = listOf(
                resolveCountdown(loader, "com.oplus.oshare.ui.ShareSettingsFragment",
                    if (colorOs16) "e" else "d", if (colorOs16) "Z" else "Y"),
                resolveCountdown(loader, "com.oplus.oshare.OShareSysUiInlineViewService", "f",
                    if (colorOs16) "P" else "Q"),
            )
            keys += key
            ModernHookRegistry.installFast(key, method, XposedInterface.Hooker { chain ->
                val action = (chain.args[1] as? Intent)?.action
                if (OShareVisibilityRules.suppress(OShareSettings.isEnabledXposed(), action)) {
                    HookLog.d(TAG, "Everyone visibility timeout skipped")
                    null
                } else {
                    chain.proceed()
                }
            })
            countdowns.forEachIndexed { index, countdown ->
                val timerKey = "$key:ui:$index"
                keys += timerKey
                ModernHookRegistry.installFast(timerKey, countdown.run, XposedInterface.Hooker { chain ->
                    OShareVisibilityRules.runCountdown(
                        enabled = OShareSettings.isEnabledXposed(),
                        clear = { countdown.clear.invoke(countdown.owner.get(chain.thisObject)) },
                        original = { chain.proceed() },
                        failure = { HookLog.w(TAG, "Unable to clear visibility countdown", it) },
                    )
                })
            }
            HookLog.i(TAG, "Everyone visibility hooks installed: alarm, settings countdown, inline countdown")
            installLabelHook(loader, key, colorOs16)
        }.onFailure {
            keys.forEach(ModernHookRegistry::unhook)
            HookLog.w(TAG, "Visibility timeout hook unavailable; stock behavior retained", it)
        }
    }

    private data class Countdown(
        val run: java.lang.reflect.Method,
        val owner: java.lang.reflect.Field,
        val clear: java.lang.reflect.Method,
    )

    private fun resolveCountdown(loader: ClassLoader, ownerName: String, suffix: String, clearName: String): Countdown {
        val owner = Class.forName(ownerName, false, loader)
        val timer = Class.forName("$ownerName\$$suffix", false, loader)
        require(Runnable::class.java.isAssignableFrom(timer))
        val ownerField = timer.declaredFields.single { it.type == owner && !Modifier.isStatic(it.modifiers) }
            .apply { isAccessible = true }
        val run = timer.getDeclaredMethod("run")
        val clear = owner.getDeclaredMethod(clearName).apply { isAccessible = true }
        require(run.returnType == Void.TYPE && clear.returnType == Void.TYPE &&
            !Modifier.isStatic(run.modifiers) && !Modifier.isStatic(clear.modifiers))
        return Countdown(run, ownerField, clear)
    }

    private fun installLabelHook(loader: ClassLoader, key: String, colorOs16: Boolean) {
        val labelKeys = mutableListOf<String>()
        runCatching {
            val strings = Class.forName(if (colorOs16) "com.oplus.oshare.f0" else "com.oplus.oshare.x", false, loader)
            val allId = strings.getDeclaredField("reception_range_all").getInt(null)
            labelKeys += "$key:label"
            ModernHookRegistry.installFast("$key:label", Resources::class.java.getDeclaredMethod("getText", Int::class.javaPrimitiveType),
                XposedInterface.Hooker { chain ->
                    val result = chain.proceed()
                    if (OShareSettings.isEnabledXposed() && chain.args[0] == allId &&
                        ownsLabel(chain.thisObject as Resources, allId)) {
                        (result as? CharSequence)?.let { OShareVisibilityRules.unlimitedLabel(it.toString()) } ?: result
                    } else result
                })
            for (name in listOf("getText", "getString")) {
                val labelKey = "$key:label:xml:$name"
                labelKeys += labelKey
                ModernHookRegistry.installFast(labelKey, TypedArray::class.java.getDeclaredMethod(name, Int::class.javaPrimitiveType),
                    XposedInterface.Hooker { chain ->
                        val result = chain.proceed()
                        if (!OShareSettings.isEnabledXposed()) return@Hooker result
                        val array = chain.thisObject as TypedArray
                        val index = chain.args[0] as Int
                        if (array.getResourceId(index, 0) == allId && ownsLabel(array.resources, allId)) {
                            (result as? CharSequence)?.let { OShareVisibilityRules.unlimitedLabel(it.toString()) } ?: result
                        } else result
                    })
            }
        }.onFailure {
            labelKeys.forEach(ModernHookRegistry::unhook)
            HookLog.w(TAG, "Visibility label unchanged", it)
        }
    }

    private fun ownsLabel(resources: Resources, id: Int): Boolean =
        runCatching { resources.getResourcePackageName(id) == OShareSettings.PACKAGE_NAME }.getOrDefault(false)
}
