package com.mi.onextbox.lsp

import android.os.Bundle
import android.view.View
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernReflect as Reflect
import io.github.libxposed.api.XposedInterface
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Uses the plugin's content-background controls, including embedded Seedling renderers. */
internal object FluidCloudTemplateMaterialHooks {
    private const val CONTENT = "disableContentBgColor"
    private const val BACKGROUND = "disableBgColor"

    private class State(val original: Bundle) {
        var applied = false
        var view: WeakReference<View>? = null
    }

    fun install(
        loader: ClassLoader,
        prefix: String,
        material: FluidCloudNotificationMaterial,
        enabled: () -> Boolean,
        logged: (String, String) -> Unit,
        report: (String, Throwable) -> Unit,
    ) {
        val states = WeakHashMap<Any, State>()
        // Seedling.p/t/u obtain their view through f() again. Never reenter configuration updates.
        val dispatching = ThreadLocal<Boolean>()
        fun expanded(wrapper: Any): Boolean = FluidCloudMaterialRules.isExpandedTemplate(
            (Reflect.callMethod(wrapper, "d") as? Enum<*>)?.name,
        )
        fun state(wrapper: Any): State = states.getOrPut(wrapper) {
            val native = runCatching { Reflect.getObjectField(wrapper, "f") as? Bundle }.getOrNull()
            val ui = runCatching {
                Reflect.callMethod(Reflect.getObjectField(Reflect.getObjectField(wrapper, "c")!!, "e"), "getValue")
            }.getOrNull()
            State(Bundle().apply {
                putInt(CONTENT, native?.getInt(CONTENT, 0) ?: 0)
                putBoolean(BACKGROUND, ui?.let { Reflect.getObjectField(it, "F") as? Boolean }
                    ?: native?.getBoolean(BACKGROUND, false) ?: false)
            })
        }
        fun unified(original: Bundle): Bundle = Bundle(original).apply {
            putInt(CONTENT, 1)
            putBoolean(BACKGROUND, true)
            // Keep native light flags intact: they also control the card's edge stroke.
        }
        fun remember(original: Bundle, incoming: Bundle) {
            if (incoming.containsKey(CONTENT)) original.putInt(CONTENT, incoming.getInt(CONTENT))
            if (incoming.containsKey(BACKGROUND)) original.putBoolean(BACKGROUND, incoming.getBoolean(BACKGROUND))
        }

        fun apply(wrapper: Any, view: View, cardContainer: Boolean = false) {
            if (dispatching.get() == true || (!cardContainer && !expanded(wrapper))) return
            val current = state(wrapper)
            val use = enabled() && material.available(view)
            if (current.applied == use && (!use || current.view?.get() === view)) return
            dispatching.set(true)
            try {
                val config = if (use) unified(current.original) else Bundle(current.original)
                Reflect.callMethod(wrapper, "p", config)
                if (wrapper.javaClass.name.endsWith(".seedling.origin.F")) {
                    Reflect.callMethod(wrapper, "t", config.getInt(CONTENT))
                } else {
                    // DefaultViewWrapper.t is empty. This is the native UIConfig transform.
                    val transform = Reflect.newInstance(
                        Reflect.findClass("com.oplus.systemui.livealert.classic.origin.g", loader),
                        10, config.getBoolean(BACKGROUND),
                    )
                    Reflect.callMethod(Reflect.getObjectField(wrapper, "c"), "p", transform)
                }
                current.applied = use
                current.view = WeakReference(view)
                if (use) logged("content:${wrapper.javaClass.name}",
                    "Content-background override applied to ${wrapper.javaClass.simpleName}; native edge/light flags preserved")
            } finally {
                dispatching.remove()
            }
        }

        // The container path also covers built-in templates whose wrapper f() has been
        // inlined. Unlike enum size alone, these hosts definitively identify expanded cards.
        for ((hostName, methodName, itemField, viewField, repoField) in listOf(
            listOf("com.oplus.systemui.plugins.seedling.card.ui.view.CardView", "h", "h", "c", "d"),
            listOf("com.oplus.systemui.plugins.seedling.card.ui.view.InstantContainer", "u", "n", "d", "e"),
        )) runCatching {
            val host = Reflect.findClass(hostName, loader)
            val method = host.declaredMethods.single { it.name == methodName }
            ModernHookRegistry.installFast("$prefix:contentHost:$hostName", method, XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                runCatching {
                    val item = Reflect.getObjectField(requireNotNull(chain.thisObject), itemField) ?: return@runCatching
                    val view = Reflect.getObjectField(item, viewField) as? View ?: return@runCatching
                    val repo = Reflect.getObjectField(item, repoField) ?: return@runCatching
                    val interactor = Reflect.getObjectField(repo, "a") ?: return@runCatching
                    val wrapper = Reflect.callMethod(interactor, "F", view) ?: return@runCatching
                    apply(wrapper, view, cardContainer = true)
                }.onFailure { report("Cannot configure expanded content host: $hostName", it) }
                result
            })
            ModernHookRuntime.requireModule().deoptimize(method)
        }.onFailure { report("Expanded content host unavailable: $hostName", it) }

        for (name in listOf(
            "com.oplus.systemui.livealert.seedling.origin.F",
            "com.oplus.systemui.livealert.classic.origin.o",
            "com.oplus.systemui.livealert.notification.origin.e",
        )) runCatching {
            val target = Reflect.findClass(name, loader)
            val seedling = name.endsWith(".F")
            val getView = target.getDeclaredMethod("f", Boolean::class.javaPrimitiveType)
            ModernHookRegistry.installFast("$prefix:content:$name", getView, XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                val view = result as? View ?: return@Hooker result
                val wrapper = chain.thisObject ?: return@Hooker result
                if (dispatching.get() == true) return@Hooker result
                runCatching {
                    apply(wrapper, view)
                }.onFailure { report("Cannot update native template background: $name", it) }
                result
            })
            val update = target.getDeclaredMethod("p", Bundle::class.java)
            ModernHookRegistry.installFast("$prefix:contentConfig:$name", update, XposedInterface.Hooker { chain ->
                val wrapper = chain.thisObject ?: return@Hooker chain.proceed()
                if (dispatching.get() == true) return@Hooker chain.proceed()
                val args = runCatching {
                    if (!expanded(wrapper)) return@runCatching null
                    val incoming = chain.getArg(0) as Bundle
                    remember(state(wrapper).original, incoming)
                    if (!enabled() || !material.ready()) return@runCatching null
                    chain.args.toTypedArray().also { it[0] = unified(incoming) }
                }.onFailure { report("Cannot preserve template background config: $name", it) }.getOrNull()
                if (args == null) chain.proceed() else chain.proceed(args)
            })
            target.declaredMethods.firstOrNull {
                it.name == "t" && it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
            }?.let { method ->
                ModernHookRegistry.installFast("$prefix:contentFlag:$name:t", method, XposedInterface.Hooker { chain ->
                    val wrapper = chain.thisObject ?: return@Hooker chain.proceed()
                    if (dispatching.get() == true) return@Hooker chain.proceed()
                    val args = runCatching {
                        if (!expanded(wrapper)) return@runCatching null
                        val original = state(wrapper).original
                        val incoming = chain.getArg(0) as Int
                        original.putInt(CONTENT, incoming)
                        if (!seedling) original.putBoolean(BACKGROUND, incoming != 0)
                        if (!enabled() || !material.ready()) return@runCatching null
                        chain.args.toTypedArray().also { it[0] = 1 }
                    }.onFailure { report("Cannot preserve template background flag: $name:t", it) }.getOrNull()
                    if (args == null) chain.proceed() else chain.proceed(args)
                })
            }
            // The obfuscated wrappers inline these helpers in rendering/configuration callers.
            target.declaredMethods.filter { it.name in listOf("f", "p", "J", "t") }.forEach {
                runCatching { ModernHookRuntime.requireModule().deoptimize(it) }
            }
        }.onFailure { report("Native content-background hook unavailable: $name", it) }
    }
}
