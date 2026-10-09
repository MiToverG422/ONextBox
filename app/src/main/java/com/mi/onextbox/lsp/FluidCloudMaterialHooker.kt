package com.mi.onextbox.lsp

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.View
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernReflect as Reflect
import io.github.libxposed.api.XposedInterface
import java.util.concurrent.ConcurrentHashMap

/** Changes expanded-card backgrounds only; native layout, controls and transitions stay intact. */
internal object FluidCloudMaterialHooker {
    private const val TAG = "ONextBox-FluidMaterial"
    private val installed = ConcurrentHashMap.newKeySet<ClassLoader>()
    private val diagnosed = ConcurrentHashMap.newKeySet<String>()
    private val applied = ConcurrentHashMap.newKeySet<String>()
    private lateinit var material: FluidCloudNotificationMaterial
    private lateinit var expandedMaterial: FluidCloudExpandedMaterialHooks

    fun hook(loader: ClassLoader) {
        material = FluidCloudNotificationMaterial(loader, ::diagnose)
        expandedMaterial = FluidCloudExpandedMaterialHooks(::enabled, ::logged, ::diagnose)
        installPanelMaterial(loader)
        installFlashlightMaterial(loader)
        runCatching {
            val factory = Reflect.findClass("com.android.systemui.shared.plugins.PluginInstance\$PluginFactory", loader)
            for (name in listOf("createClassLoader", "createPluginContext")) {
                val method = factory.getDeclaredMethod(name)
                ModernHookRegistry.installFast("$TAG:factory:$name", method, XposedInterface.Hooker { chain ->
                    val result = chain.proceed()
                    runCatching {
                        val info = Reflect.getObjectField(requireNotNull(chain.thisObject), "pluginAppInfo") as? ApplicationInfo
                        if (info?.packageName == FluidCloudMaterialRules.PLUGIN_PACKAGE) {
                            val pluginLoader = (result as? ClassLoader) ?: (result as? Context)?.classLoader
                            pluginLoader?.let(::installPlugin)
                        }
                    }.onFailure { diagnose("Plugin material hook unavailable", it) }
                    result
                })
            }
            HookLog.i(TAG, "Watching native Fluid Cloud plugin loader")
        }.onFailure { diagnose("Cannot observe Fluid Cloud plugin", it) }
    }

    private fun installPanelMaterial(loader: ClassLoader) {
        // Expanded cards do not participate in ViewBlurManager's native row refresh list.
        // Propagate resource/motion changes and animated BP tint changes without rebuilding
        // the proxy or recalculating geometry on every frame.
        for ((className, methodName, geometry) in listOf(
            Triple("com.oplus.systemui.notification.blur.ViewBlurManager", "updateResource", true),
            Triple("com.oplus.systemui.notification.blur.ViewBlurManager", "updateRowsBlur", true),
            Triple("com.oplus.systemui.notification.color.MaterialColorManager", "setCurrentMaterialColor", false),
        )) runCatching {
            val target = Reflect.findClass(className, loader)
            target.declaredMethods.filter { it.name == methodName }.forEach { method ->
                ModernHookRegistry.installFast("$TAG:style:$methodName", method, XposedInterface.Hooker { chain ->
                    val result = chain.proceed()
                    if (enabled()) {
                        if (geometry) material.invalidateStyle() else material.invalidateColors()
                    }
                    result
                })
                ModernHookRuntime.requireModule().deoptimize(method)
            }
        }.onFailure { diagnose("Notification style refresh unavailable: $methodName", it) }
        // CUSTOM is the native notification row type used by Seedling/Fluid Cloud templates.
        for ((className, names) in listOf(
            "com.oplus.systemui.notification.blur.ViewBlurManager" to listOf("getMixConfig"),
            "com.oplus.systemui.notification.blur.NotificationPlatFormBlurParamsManager" to
                listOf("getGradientStrokeLineParams", "getInnerShadowParams"),
        )) runCatching {
            val target = Reflect.findClass(className, loader)
            val notification = Reflect.findClass("com.oplus.systemui.notification.blur.ViewBlurManager\$CardType", loader)
            for (name in names) {
                val method = target.declaredMethods.single { it.name == name && it.parameterCount == 2 }
                ModernHookRegistry.installFast("$TAG:panel:$name", method, XposedInterface.Hooker { chain ->
                    if (name == "getMixConfig") chain.thisObject?.let(material::observeManager)
                    val type = chain.getArg(0) as? Enum<*>
                    if (!FluidCloudMaterialRules.shouldUseNotificationMaterial(enabled(), type?.name)) return@Hooker chain.proceed()
                    val args = chain.args.toTypedArray()
                    args[0] = notification.getField(FluidCloudMaterialRules.NOTIFICATION_CARD).get(null)
                    logged("panel", "Panel Fluid Cloud cards use ordinary notification material")
                    chain.proceed(args)
                })
            }
            // These small helpers can be inlined into native proxy creation/update.
            target.declaredMethods.filter { it.name in listOf("requireBlurProxyForView", "updatePlatformBlurParams") }
                .forEach { runCatching { ModernHookRuntime.requireModule().deoptimize(it) } }
        }.onFailure { diagnose("Panel notification material hook unavailable: $className", it) }

        runCatching {
            val handler = Reflect.findClass("com.oplus.systemui.notification.row.NotificationBackgroundCustomRowHandler", loader)
            val method = handler.declaredMethods.single { it.name == "getContentBgColor" }
            ModernHookRegistry.installFast("$TAG:panel:contentColor", method, XposedInterface.Hooker { chain ->
                // Let the native handler clear its custom MetaBall color instead of applying
                // a branded solid/gradient layer on top of the normal notification shader.
                if (enabled()) null else chain.proceed()
            })
            handler.declaredMethods.filter { it.name in listOf("updateDrawableMetaColor", "needDrawBgColorByMetaBall") }
                .forEach { runCatching { ModernHookRuntime.requireModule().deoptimize(it) } }
        }.onFailure { diagnose("Panel template tint hook unavailable", it) }

        runCatching {
            val manager = Reflect.findClass("com.oplus.systemui.notification.blur.ViewBlurManager", loader)
            val method = manager.getDeclaredMethod("customCardPlatformMixConfig")
            ModernHookRegistry.installFast("$TAG:panel:immersiveMix", method, XposedInterface.Hooker { chain ->
                // OplusCustomRow bypasses getMixConfig during every immersive update. Returning
                // the ordinary shader also prevents its legacy BlurMixMulti color interpolation
                // from writing the dark custom recipe back into the native proxy.
                val replacement = if (enabled()) runCatching {
                    material.panelMix(requireNotNull(chain.thisObject))
                }.onFailure { diagnose("Cannot replace immersive custom mix", it) }.getOrNull() else null
                replacement ?: chain.proceed()
            })
            val row = Reflect.findClass("com.oplus.systemui.statusbar.notification.customcard.OplusCustomRow", loader)
            val progress = row.getDeclaredMethod("setImmersiveProgress", Float::class.javaPrimitiveType)
            ModernHookRegistry.installFast("$TAG:panel:immersiveMask", progress, XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                if (enabled()) runCatching {
                    val view = Reflect.getObjectField(requireNotNull(chain.thisObject), "mBackgroundNormal") as? View
                        ?: return@runCatching
                    if (!material.available(view)) return@runCatching
                    val extension = Reflect.callMethod(view, "getExt") ?: return@runCatching
                    val proxy = Reflect.callMethod(extension, "getViewBlurProxy") ?: return@runCatching
                    if (!material.isPanelMaterialActive(proxy)) return@runCatching
                    // Only remove the redundant solid immersive mask when a real notification
                    // renderer is active. Native fallback and parent/gesture alpha stay untouched.
                    Reflect.callMethod(extension, "setTempDrawable", null)
                    (Reflect.callMethod(view, "getCustomBackground") as? Drawable)?.alpha = 255
                    logged("immersive", "Panel immersive updates preserve notification shader; extra dark mask removed")
                }.onFailure { diagnose("Cannot clear immersive custom mask", it) }
                result
            })
            ModernHookRuntime.requireModule().deoptimize(progress)
        }.onFailure { diagnose("Immersive card material hook unavailable", it) }

        runCatching {
            val config = Reflect.findClass("com.oplusos.systemui.common.blurability.BlurConfig", loader)
            val custom = Reflect.findClass("com.oplus.systemui.notification.blur.ViewBlurManager\$CardType", loader)
                .getField(FluidCloudMaterialRules.CUSTOM_CARD).get(null) as Enum<*>
            val setter = config.declaredMethods.single { it.name == "setPlatformMixConfig" && it.parameterCount == 1 }
            ModernHookRegistry.installFast("$TAG:panel:preserveMix", setter, XposedInterface.Hooker { chain ->
                val replacement = if (enabled()) runCatching {
                    if (Reflect.callMethod(chain.thisObject, "getCardType") != custom.ordinal) null else material.panelMix()
                }.onFailure { diagnose("Cannot preserve ordinary notification mix", it) }.getOrNull() else null
                if (replacement == null) chain.proceed() else {
                    val args = chain.args.toTypedArray()
                    args[0] = replacement
                    chain.proceed(args)
                }
            })
            Reflect.findClass("com.oplus.systemui.notification.blur.ViewBlurManager", loader).declaredMethods
                .filter { it.name == "updatePlatformBlurParams" }
                .forEach { ModernHookRuntime.requireModule().deoptimize(it) }
        }.onFailure { diagnose("Notification mix write protection unavailable", it) }

        runCatching {
            val gate = FluidCloudPanelDrawGate()
            val extension = Reflect.findClass("com.oplus.systemui.statusbar.notification.row.NotificationBackgroundViewExtImp", loader)
            val row = Reflect.findClass("com.oplus.systemui.statusbar.notification.customcard.OplusCustomRow", loader)
            val draw = extension.getDeclaredMethod("draw", Canvas::class.java, Drawable::class.java)
            val policy = Reflect.findClass("com.oplusos.systemui.common.util.ScrimUtil", loader)
                .getDeclaredMethod("isBPAnimLevel")
            ModernHookRegistry.installFast("$TAG:panel:drawDispatch", policy, XposedInterface.Hooker { chain ->
                // Only the first dispatch check in this exact native draw chooses the CUSTOM
                // solid/MetaBall shortcut. Later animation, stacking and AOD checks remain native.
                if (gate.consumeCustomBackgroundBypass()) false else chain.proceed()
            })
            ModernHookRegistry.installFast("$TAG:panel:draw", draw, XposedInterface.Hooker { chain ->
                var extension: Any? = null
                var proxy: Any? = null
                val use = if (enabled()) runCatching {
                    val receiver = requireNotNull(chain.thisObject)
                    val view = Reflect.callMethod(receiver, "getBgView") as? View ?: return@runCatching false
                    if (!row.isInstance(view.parent) || !material.available(view)) return@runCatching false
                    val nativeProxy = Reflect.callMethod(receiver, "getViewBlurProxy") ?: return@runCatching false
                    if (!material.isPanelMaterialActive(nativeProxy)) return@runCatching false
                    material.preparePanel(view, nativeProxy)
                    extension = receiver
                    proxy = nativeProxy
                    true
                }.onFailure { diagnose("Cannot select panel notification draw path", it) }.getOrDefault(false) else false
                if (use) logged("draw", "Fluid Cloud panel background uses the ordinary notification draw path")
                val result = gate.duringDraw(use) { chain.proceed() }
                if (use) runCatching {
                    material.drawPanelColor(chain.getArg(0) as Canvas, requireNotNull(extension), requireNotNull(proxy))
                }.onFailure { diagnose("Cannot draw native notification material color", it) }
                result
            })
            ModernHookRuntime.requireModule().deoptimize(draw)
        }.onFailure { diagnose("Panel background dispatch hook unavailable", it) }
    }

    private fun installFlashlightMaterial(loader: ClassLoader) = runCatching {
        val target = Reflect.findClass("com.oplus.systemui.qs.detail.flashlight.FlashlightCardBgDrawableUtils", loader)
        val method = target.getDeclaredMethod("create", View::class.java)
        ModernHookRegistry.installFast("$TAG:qs:flashlight", method, XposedInterface.Hooker { chain ->
            val original = chain.proceed()
            val view = chain.getArg(0) as? View ?: return@Hooker original
            if (!enabled()) {
                material.release(view)
                return@Hooker original
            }
            runCatching {
                val context = view.context
                val radius = dimension(context, "qs_detail_bg_corner_radius", "com.android.systemui")
                val round = Reflect.callStaticMethod(
                    Reflect.findClass("com.oplusos.systemui.common.util.OplusQsSmoothRoundUtil", loader),
                    "getRoundParams", context, radius,
                )
                val actualRadius = (round?.let { Reflect.callMethod(it, "getRadius") } as? Number)?.toFloat() ?: radius
                val weight = (round?.let { Reflect.callMethod(it, "getWeight") } as? Number)?.toFloat() ?: 0f
                material.background(view, actualRadius, weight, original as? Drawable)?.also {
                    logged("qs", "Control-center flashlight material and black mask replaced")
                } ?: original
            }.onFailure { diagnose("Cannot update control-center card", it) }.getOrDefault(original)
        })
        val caller = Reflect.findClass("com.oplus.systemui.qs.detail.flashlight.OplusQSFlashlightDetailView", loader)
        caller.declaredMethods.filter { it.name in listOf("inflateSecondaryLayout", "onConfigurationChanged") }
            .forEach { runCatching { ModernHookRuntime.requireModule().deoptimize(it) } }
    }.onFailure { diagnose("Control-center flashlight material hook unavailable", it) }

    private fun installPlugin(loader: ClassLoader) {
        if (!installed.add(loader)) return
        val prefix = "$TAG:plugin:${System.identityHashCode(loader)}"
        // Do not clear native template layers if this plugin's replacement inputs failed.
        if (expandedMaterial.install(loader, prefix).isFailure) return
        installTemplates(loader, prefix)
        FluidCloudTemplateMaterialHooks.install(loader, prefix, material, ::enabled, ::logged, ::diagnose)
    }

    private fun installTemplates(loader: ClassLoader, prefix: String) {
        runCatching {
            val target = Reflect.findClass(FluidCloudMaterialRules.SINGLE_BACKGROUND, loader)
            val method = target.getDeclaredMethod("invokeSuspend", Any::class.java)
            ModernHookRegistry.installFast("$prefix:single", method, XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                runCatching {
                    val page = Reflect.getObjectField(requireNotNull(chain.thisObject), "this\$0")!!
                    clearTemplate(Reflect.getObjectField(page, "k") as View)
                }.onFailure { diagnose("Cannot clear single-card template background", it) }
                result
            })
        }.onFailure { diagnose("Single-card template hook unavailable", it) }
        runCatching {
            val target = Reflect.findClass(FluidCloudMaterialRules.MINI_BACKGROUND, loader)
            val method = target.declaredMethods.single { it.name == "a" && it.parameterCount == 2 }
            ModernHookRegistry.installFast("$prefix:mini", method, XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                runCatching {
                    val receiver = requireNotNull(chain.thisObject)
                    val branch = Reflect.getObjectField(receiver, "b") as Int
                    if (FluidCloudMaterialRules.shouldClearMiniBackground(enabled(), branch)) {
                        val page = Reflect.getObjectField(receiver, "c")!!
                        clearTemplate(Reflect.getObjectField(page, "k") as View)
                    }
                }.onFailure { diagnose("Cannot clear mini-card template background", it) }
                result
            })
        }.onFailure { diagnose("Mini-card template hook unavailable", it) }
    }

    private fun clearTemplate(view: View) {
        if (expandedMaterial.retainBackground(view)) return
        if (enabled() && material.available(view)) material.clearTemplate(view)
    }

    @Suppress("DiscouragedApi")
    private fun dimension(context: Context, name: String, packageName: String): Float {
        val id = context.resources.getIdentifier(name, "dimen", packageName)
        require(id != 0) { "Missing native dimension $name" }
        return context.resources.getDimension(id)
    }

    private fun enabled() = LspConfig.isFluidCloudMaterialEnabledXposed()
    private fun logged(key: String, message: String) {
        if (applied.add(key)) HookLog.i(TAG, message)
    }
    private fun diagnose(message: String, error: Throwable) {
        if (diagnosed.add(message)) HookLog.w(TAG, message, error)
    }
}
