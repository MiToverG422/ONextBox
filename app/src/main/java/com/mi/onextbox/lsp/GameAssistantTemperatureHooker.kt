package com.mi.onextbox.lsp

import android.view.View
import android.widget.TextView
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.MethodData
import java.util.concurrent.atomic.AtomicBoolean

internal object GameAssistantTemperatureHooker {
    private const val TAG = "ONextBox-GameAssistant"
    private const val BIG_PANEL = "business.toolpanel.layout.PerfModeBigPanelLayout"
    private const val SMALL_TOOLBAR = "business.toolpanel.layout.PerfModeSmallToolbarLayout"

    fun hook(loader: ClassLoader, dex: DexKitBridge) {
        val model = loader.loadClass("business.edgepanel.PerfPanelViewModel")
        val getter = dex.findMethod { matcher {
            usingStrings(" isHighTemperature ")
            returnType = "boolean"
        } }.flatMap { guard ->
            guard.invokes.filter {
                it.className == guard.className && it.paramCount == 0 && it.returnTypeName == "float"
            }
        }.distinctBy { it.descriptor }.single().getMethodInstance(loader)
        val targets = GameAssistantTemperatureTargets(model, getter)
        val failed = AtomicBoolean()
        fun failure(error: Throwable) {
            if (failed.compareAndSet(false, true)) {
                HookLog.w(TAG, "Temperature display update unavailable", error)
            }
        }

        var installed = 0
        fun adapter(name: String, block: () -> Unit) {
            runCatching(block).onSuccess { installed++ }
                .onFailure { HookLog.w(TAG, "$name temperature display unavailable; native display retained", it) }
        }
        adapter("Performance panel") { hookBigPanel(loader, dex, targets, ::failure) }
        adapter("Tools toolbar") { hookSmallToolbar(loader, dex, targets, ::failure) }
        check(installed > 0) { "No compatible temperature display" }
        HookLog.i(TAG, "Deferred temperature adapter installed: ${getter.declaringClass.name}.${getter.name}; panels=$installed")
    }

    private fun enabled(key: String) = GameAssistantSettings.enabledXposed(key)

    private fun hookBigPanel(
        loader: ClassLoader,
        dex: DexKitBridge,
        targets: GameAssistantTemperatureTargets,
        failure: (Throwable) -> Unit,
    ) {
        val initData = dex.findMethod { matcher {
            declaredClass = BIG_PANEL
            usingStrings("initData."); paramTypes(); returnType = "void"
        } }.single()
        val init = initData.getMethodInstance(loader)
        val panel = init.declaringClass
        val lists = panel.declaredFields.filter { List::class.java.isAssignableFrom(it.type) }
            .onEach { it.isAccessible = true }
        val maps = panel.declaredFields.filter { Map::class.java.isAssignableFrom(it.type) }
            .onEach { it.isAccessible = true }
        require(lists.size >= 2 && maps.isNotEmpty())
        val update = panel.getDeclaredMethod("onUpdate", String::class.java, String::class.java,
            Boolean::class.javaPrimitiveType)

        fun render(view: View) {
            runCatching {
                val state = targets.readLabels() ?: return
                val original = state.battery
                val thermal = state.temperature
                val active = enabled(GameAssistantSettings.TEMPERATURE)
                val labels = if (active) thermal else original
                val value = if (active) targets.readDisplayValue() else null
                lists.forEach { field ->
                    val text = (field.get(view) as? List<*>)?.getOrNull(state.index) as? TextView
                        ?: return@forEach
                    when (text.text.toString()) {
                        original[0], thermal[0] -> text.text = labels[0]
                        original[1], thermal[1] -> text.text = labels[1]
                    }
                }
                if (value != null) {
                    maps.forEach { field ->
                        ((field.get(view) as? Map<*, *>)?.get("battery") as? TextView)?.text = value
                    }
                }
            }.onFailure(failure)
        }
        listOf(init, update).forEach { method ->
            ModernHookRegistry.installFast("game-temperature:${method.toGenericString()}", method,
                XposedInterface.Hooker { chain ->
                    val batteryUpdate = method == update && chain.args[0] == "battery"
                    val args = if (batteryUpdate) runCatching {
                        targets.updateArguments(chain.args, enabled(GameAssistantSettings.TEMPERATURE))
                    }.onFailure(failure).getOrNull() else null
                    // The host queues the value; change it before enqueueing, not only after drawing.
                    val result = if (args != null) chain.proceed(args) else chain.proceed()
                    val view = chain.thisObject as? View
                    if (view != null && (method == init || batteryUpdate)) {
                        view.post { HotReloadLifecycle.withHook(fallback = {}, action = { render(view) }) }
                    }
                    result
                })
        }
        deoptCallers(initData, loader)
    }

    private fun hookSmallToolbar(
        loader: ClassLoader,
        dex: DexKitBridge,
        targets: GameAssistantTemperatureTargets,
        failure: (Throwable) -> Unit,
    ) {
        val formats = dex.findMethod { matcher {
            declaredClass = SMALL_TOOLBAR
            returnType = "android.text.SpannableStringBuilder"
            paramCount = 5
        } }.mapNotNull { data ->
            val method = data.getMethodInstance(loader)
            GameAssistantTemperatureTargets.toolbarValueIndex(method.parameterTypes)?.let {
                Triple(data, method, it)
            }
        }
        val (formatData, format, valueIndex) = formats.single()
        val updateData = dex.findMethod { matcher {
            declaredClass = SMALL_TOOLBAR
            usingStrings("onUpdate: key = ", " , value = ")
            paramTypes("java.lang.String", "java.lang.String", "boolean"); returnType = "void"
        } }.single()
        val update = updateData.getMethodInstance(loader)

        ModernHookRegistry.installFast("game-temperature:${format.toGenericString()}", format,
            XposedInterface.Hooker { chain ->
                val args = runCatching {
                    targets.toolbarArguments(chain.args, valueIndex, enabled(GameAssistantSettings.TOOLBAR_TEMPERATURE))
                }.onFailure(failure).getOrNull()
                if (args != null) chain.proceed(args) else chain.proceed()
            })
        ModernHookRegistry.installFast("game-temperature:${update.toGenericString()}", update,
            XposedInterface.Hooker { chain ->
                if (enabled(GameAssistantSettings.TOOLBAR_TEMPERATURE) && chain.args[0] == "battery") {
                    // Battery warnings must not tint a temperature reading as a low percentage.
                    chain.proceed(chain.args.toTypedArray().also { it[2] = false })
                } else chain.proceed()
            })
        deoptCallers(formatData, loader)
        deoptCallers(updateData, loader)
    }

    private fun deoptCallers(data: MethodData, loader: ClassLoader) {
        val callers = data.callers
        (callers + callers.flatMap { it.callers }).distinctBy { it.descriptor }.forEach { caller ->
            val method = runCatching { caller.getMethodInstance(loader) }.getOrNull() ?: return@forEach
            runCatching { ModernHookRuntime.requireModule().deoptimize(method) }
                .onFailure { HookLog.w(TAG, "Temperature caller deoptimization unavailable: ${method.name}", it) }
        }
    }
}
