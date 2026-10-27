package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.ModernHookRegistry
import android.graphics.Typeface
import android.widget.TextView
import java.util.concurrent.atomic.AtomicBoolean
import io.github.libxposed.api.XposedInterface

/** Select the ROM's complete big-type layout (including 3G/4G), not fake network data. */
internal object LargeNetworkTypeHooker {
    internal const val FEATURE = "com.android.systemui.statusbar_show_big_5g"
    internal fun shouldEnable(enabled: Boolean, feature: String?) = enabled && feature == FEATURE
    internal fun shouldFollowFont(enabled: Boolean, follow: Boolean, width: Int) = enabled && follow && width == 25
    internal fun fontWeight(enabled: Boolean, follow: Boolean, bold: Boolean, weight: Int) =
        if (enabled && follow) { if (bold) 700 else 400 } else weight

    fun hook(loader: ClassLoader) {
        LargeNetworkArtworkHooker.hook(loader)
        hookFont(loader)
        hookNativeFeature(loader)
    }

    fun hookColorOs16(loader: ClassLoader) {
        // C16 uses drawable network labels, with no C17 text typeface helper or Compose ABI.
        LargeNetworkArtworkHooker.hookColorOs16()
        hookNativeFeature(loader)
    }

    private fun hookNativeFeature(loader: ClassLoader) {
        runCatching {
            val provider = Class.forName("com.oplus.coreapp.appfeature.AppFeatureProviderUtils", false, loader)
            val methods = provider.declaredMethods.filter {
                it.name == "isFeatureSupport" && it.returnType == Boolean::class.javaPrimitiveType &&
                    it.parameterTypes.count { type -> type == String::class.java } == 1
            }
            check(methods.isNotEmpty()) { "Missing app-feature support query" }
            methods.forEach { method ->
                val index = method.parameterTypes.indexOf(String::class.java)
                ModernHookRegistry.installFast("large-network-type:${method.toGenericString()}", method,
                    XposedInterface.Hooker { chain ->
                        if (shouldEnable(LargeNetworkTypeSettings.enabledXposed(), chain.getArg(index) as? String)) true
                        else chain.proceed()
                    })
            }
            // CustomizeFeatureOption caches this at startup. A SystemUI restart applies
            // both enabling and disabling consistently to View and Compose pipelines.
            HookLog.i("ONextBox-LargeNetwork", "Native big network-type feature query installed")
        }.onFailure { HookLog.w("ONextBox-LargeNetwork", "Big network-type layout unavailable", it) }
    }

    private fun hookFont(loader: ClassLoader) {
        runCatching {
            val helper = Class.forName("com.oplusos.systemui.common.typeface.SystemTypefaceHelper", false, loader)
            val defaultFont = helper.getDeclaredMethod("getDefaultTypeface", Int::class.javaPrimitiveType)
            fun weight(value: Int) = fontWeight(LargeNetworkTypeSettings.enabledXposed(),
                LargeNetworkTypeSettings.followsSystemFontXposed(), LargeNetworkTypeSettings.usesBoldXposed(), value)
            fun systemFont(receiver: Any?, value: Int): Typeface {
                val targetWeight = weight(value)
                val original = defaultFont.invoke(receiver, targetWeight) as Typeface
                // The host may return a themed SANS_SERIF regardless of requested weight.
                // Normalize both states; otherwise "off" can retain a bold system face.
                return Typeface.create(original, targetWeight, original.isItalic)
            }
            val fontDelivered = AtomicBoolean()
            fun active() = LargeNetworkTypeSettings.enabledXposed() && LargeNetworkTypeSettings.followsSystemFontXposed() && !LargeNetworkTypeSettings.usesArtworkXposed()
            fun delivered() {
                if (fontDelivered.compareAndSet(false, true)) HookLog.i("ONextBox-LargeNetwork", "System font applied to mobile network text")
            }
            val networkFont = helper.getDeclaredMethod("getNetworkIconTextTypeface", Typeface::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            // Both Os17Binding and getLargeNetworkTypeComposeFontFamily use width 25.
            // Width 70 is shared with Wi-Fi and normal network text: leave it untouched.
            ModernHookRegistry.installFast("large-network-type:font", networkFont, XposedInterface.Hooker { chain ->
                if (!LargeNetworkTypeSettings.usesArtworkXposed() && shouldFollowFont(LargeNetworkTypeSettings.enabledXposed(),
                        LargeNetworkTypeSettings.followsSystemFontXposed(), chain.getArg(2) as Int)) {
                    val font = runCatching { systemFont(chain.thisObject, chain.getArg(1) as Int) }.getOrNull()
                    font ?: chain.proceed()
                } else chain.proceed()
            })
            // FourG/ThreeG.isLarge() is false even inside the big-type layout, so the
            // width-25 helper above alone cannot cover those labels. Override the final
            // mobile TextView assignment, not the shared Wi-Fi/font callback.
            val binding = Class.forName("com.oplus.systemui.statusbar.pipeline.mobile.ui.view.OplusStatusBarMobileViewBinder\$Os17Binding", false, loader)
            val text = binding.getDeclaredField("mobileTypeText").apply { isAccessible = true }
            val fonts = binding.getDeclaredField("systemTypefaceHelper").apply { isAccessible = true }
            val assign = binding.getDeclaredMethod("access\$setMobileTypeTextTypeface", binding,
                Typeface::class.java, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            ModernHookRegistry.installFast("large-network-type:font-view", assign, XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                if (active()) runCatching {
                    val target = chain.getArg(0)
                    val font = systemFont(fonts.get(target), chain.getArg(2) as Int)
                    (text.get(target) as TextView).typeface = font
                    delivered()
                }.onFailure { HookLog.w("ONextBox-LargeNetwork", "Mobile font assignment failed", it) }
                result
            })
            val normalCompose = helper.getDeclaredMethod("getNetworkTypeComposeFontFamily", Typeface::class.java, Int::class.javaPrimitiveType)
            val largeCompose = helper.getDeclaredMethod("getLargeNetworkTypeComposeFontFamily", Typeface::class.java, Int::class.javaPrimitiveType)
            ModernHookRegistry.installFast("large-network-type:font-compose", normalCompose, XposedInterface.Hooker { chain ->
                if (!active()) return@Hooker chain.proceed()
                val font = runCatching { systemFont(chain.thisObject, chain.getArg(1) as Int) }.getOrNull()
                    ?: return@Hooker chain.proceed()
                val args = chain.args.toTypedArray()
                args[0] = font
                args[1] = weight(chain.getArg(1) as Int)
                chain.proceed(args).also { delivered() }
            })
            ModernHookRegistry.installFast("large-network-type:font-compose-large", largeCompose, XposedInterface.Hooker { chain ->
                if (!active()) return@Hooker chain.proceed()
                // Reuse the system's FontFamily conversion without selecting its special font.
                runCatching { normalCompose.invoke(chain.thisObject, chain.getArg(0), chain.getArg(1)) }
                    .getOrElse { chain.proceed() }
            })
            HookLog.i("ONextBox-LargeNetwork", "Mobile font View/Compose hooks ready")
        }.onFailure { HookLog.w("ONextBox-LargeNetwork", "System font override unavailable; native font retained", it) }
    }

}
