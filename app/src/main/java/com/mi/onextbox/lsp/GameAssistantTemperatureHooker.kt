package com.mi.onextbox.lsp

import android.view.View
import android.widget.TextView
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import io.github.libxposed.api.XposedInterface
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Modifier

internal object GameAssistantTemperatureHooker {
    fun hook(loader: ClassLoader, dex: DexKitBridge) {
        val init = dex.findMethod { matcher {
            declaredClass = "business.toolpanel.layout.PerfModeBigPanelLayout"
            usingStrings("initData."); paramTypes(); returnType = "void"
        } }.single().getMethodInstance(loader)
        val panel = init.declaringClass
        val model = loader.loadClass("business.edgepanel.PerfPanelViewModel")
        val instance = model.getField("INSTANCE").get(null)
        val status = model.getMethod("getStatusList")
        val battery = model.getMethod("getBatteryStatus")
        val temperature = model.getMethod("getSmallTemperatureStatus")
        val helper = loader.loadClass("com.coloros.gamespaceui.utils.TemperatureControlHelper")
        val getter = helper.declaredMethods.single {
            it.parameterCount == 0 && it.returnType == Float::class.javaPrimitiveType
        }.apply { isAccessible = true }
        val companionField = helper.declaredFields.single { field ->
            Modifier.isStatic(field.modifiers) && field.type.declaredMethods.any {
                it.parameterCount == 0 && it.returnType == helper
            }
        }.apply { isAccessible = true }
        val factory = companionField.type.declaredMethods.single {
            it.parameterCount == 0 && it.returnType == helper
        }.apply { isAccessible = true }
        val lists = panel.declaredFields.filter { List::class.java.isAssignableFrom(it.type) }
            .onEach { it.isAccessible = true }
        val maps = panel.declaredFields.filter { Map::class.java.isAssignableFrom(it.type) }
            .onEach { it.isAccessible = true }
        require(lists.size >= 2 && maps.isNotEmpty())

        fun render(view: View) {
            runCatching {
                val index = (status.invoke(instance) as List<*>).indexOf("battery")
                if (index < 0) return
                val original = battery.invoke(instance) as Array<*>
                val thermal = temperature.invoke(instance) as Array<*>
                val enabled = GameAssistantSettings.enabledXposed(GameAssistantSettings.TEMPERATURE)
                val labels = if (enabled) thermal else original
                lists.forEach { field ->
                    val text = (field.get(view) as? List<*>)?.getOrNull(index) as? TextView
                        ?: return@forEach
                    when (text.text.toString()) {
                        original[0], thermal[0] -> text.text = labels[0] as String
                        original[1], thermal[1] -> text.text = labels[1] as String
                    }
                }
                if (enabled) {
                    val value = (getter.invoke(factory.invoke(companionField.get(null))) as Float)
                    val display = if (value.isFinite() && value in 0f..100f) value.toInt().toString() else "--"
                    maps.forEach { field ->
                        ((field.get(view) as? Map<*, *>)?.get("battery") as? TextView)?.text = display
                    }
                }
            }.onFailure { HookLog.w("ONextBox-GameAssistant", "Temperature display update unavailable", it) }
        }
        val update = panel.getDeclaredMethod("onUpdate", String::class.java, String::class.java,
            Boolean::class.javaPrimitiveType)
        listOf(init, update).forEach { method ->
            ModernHookRegistry.installFast("game-temperature:${method.toGenericString()}", method,
                XposedInterface.Hooker { chain ->
                    val result = chain.proceed()
                    val view = chain.thisObject as? View
                    if (view != null && (method == init || chain.args[0] == "battery")) {
                        view.post { render(view) }
                    }
                    result
                })
        }
    }
}
