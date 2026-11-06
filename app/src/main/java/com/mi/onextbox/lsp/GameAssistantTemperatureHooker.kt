package com.mi.onextbox.lsp

import android.view.View
import android.widget.TextView
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import io.github.libxposed.api.XposedInterface
import org.luckypray.dexkit.DexKitBridge
import java.util.concurrent.atomic.AtomicBoolean

internal object GameAssistantTemperatureHooker {
    fun hook(loader: ClassLoader, dex: DexKitBridge) {
        val init = dex.findMethod { matcher {
            declaredClass = "business.toolpanel.layout.PerfModeBigPanelLayout"
            usingStrings("initData."); paramTypes(); returnType = "void"
        } }.single().getMethodInstance(loader)
        val panel = init.declaringClass
        val model = loader.loadClass("business.edgepanel.PerfPanelViewModel")
        val getter = dex.findMethod { matcher {
            usingStrings(" isHighTemperature ")
            returnType = "boolean"; paramTypes("java.lang.String", "java.lang.String")
        } }.flatMap { guard ->
            guard.invokes.filter {
                it.className == guard.className && it.paramCount == 0 && it.returnTypeName == "float"
            }
        }.distinctBy { it.descriptor }.single().getMethodInstance(loader)
        val targets = GameAssistantTemperatureTargets(model, getter)
        val lists = panel.declaredFields.filter { List::class.java.isAssignableFrom(it.type) }
            .onEach { it.isAccessible = true }
        val maps = panel.declaredFields.filter { Map::class.java.isAssignableFrom(it.type) }
            .onEach { it.isAccessible = true }
        require(lists.size >= 2 && maps.isNotEmpty())
        val failed = AtomicBoolean()

        fun render(view: View) {
            runCatching {
                val state = targets.readLabels() ?: return
                val original = state.battery
                val thermal = state.temperature
                val enabled = GameAssistantSettings.enabledXposed(GameAssistantSettings.TEMPERATURE)
                val labels = if (enabled) thermal else original
                val value = if (enabled) targets.readCelsius() else null
                lists.forEach { field ->
                    val text = (field.get(view) as? List<*>)?.getOrNull(state.index) as? TextView
                        ?: return@forEach
                    when (text.text.toString()) {
                        original[0], thermal[0] -> text.text = labels[0]
                        original[1], thermal[1] -> text.text = labels[1]
                    }
                }
                if (value != null) {
                    val display = if (value.isFinite() && value in 0f..100f) value.toInt().toString() else "--"
                    maps.forEach { field ->
                        ((field.get(view) as? Map<*, *>)?.get("battery") as? TextView)?.text = display
                    }
                }
            }.onFailure {
                if (failed.compareAndSet(false, true)) {
                    HookLog.w("ONextBox-GameAssistant", "Temperature display update unavailable", it)
                }
            }
        }
        val update = panel.getDeclaredMethod("onUpdate", String::class.java, String::class.java,
            Boolean::class.javaPrimitiveType)
        listOf(init, update).forEach { method ->
            ModernHookRegistry.installFast("game-temperature:${method.toGenericString()}", method,
                XposedInterface.Hooker { chain ->
                    val result = chain.proceed()
                    val view = chain.thisObject as? View
                    if (view != null && (method == init || chain.args[0] == "battery")) {
                        view.post { HotReloadLifecycle.withHook(fallback = {}, action = { render(view) }) }
                    }
                    result
                })
        }
        HookLog.i("ONextBox-GameAssistant", "Deferred temperature adapter installed: ${getter.declaringClass.name}.${getter.name}")
    }
}
