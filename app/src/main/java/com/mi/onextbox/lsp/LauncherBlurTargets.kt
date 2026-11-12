package com.mi.onextbox.lsp

import android.content.Context
import android.view.SurfaceControl
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Resolve obfuscated tier helpers by their native consumers, never by short class names. */
internal object LauncherBlurTargets {
    private const val FEATURES = "com.android.common.util.AppFeatureUtils"
    private const val BLUR = "com.android.launcher3.uioverrides.states.blurdrawable.BlurUtils"
    private const val MATERIAL = "com.coui.appcompat.COUIMaterialStateManager"
    private const val LEVEL = "com.coui.appcompat.uiutil.AnimLevel"

    data class Platform(
        val unavailable: Method,
        val dynamic: Method,
        val support: Method,
        val tier: Method,
        val blurQueries: List<Method>,
        val animationFlags: Map<Method, Boolean>,
        val callers: List<Executable>,
    )

    data class Menu(val builder: Method, val tier: Method, val materialTier: Method, val callers: List<Executable>)

    fun open(apkPath: String): DexKitBridge {
        System.loadLibrary("dexkit")
        return DexKitBridge.create(apkPath).also { it.setThreadNum(1) }
    }

    fun platform(dex: DexKitBridge, loader: ClassLoader): Platform {
        // Resolve, but do not initialize or invoke, the vendor rendering API.
        // A tier override cannot supply an engine removed from the firmware.
        val blurParam = Class.forName("com.oplus.graphics.OplusBlurParam", false, loader)
        Class.forName("com.oplus.graphics.OplusBlurManager", false, loader).getDeclaredMethod(
            "setBackgroundBlurRadius", SurfaceControl.Transaction::class.java, SurfaceControl::class.java,
            Int::class.javaPrimitiveType, blurParam,
        )
        val gaussian = dex.findMethod {
            matcher { usingStrings("Fail to getGaussianLevel, "); paramTypes(Context::class.java.name); returnType = "int" }
        }.single()
        val platform = gaussian.className
        val dynamicFlag = dex.findMethod {
            matcher { declaredClass = FEATURES; name = "isSupportDynamicBlur"; paramTypes(); returnType = "boolean" }
        }.singleOrNull()
        val dynamic = dynamicFlag?.callers?.single {
            it.className == platform && it.returnTypeName == "boolean" && it.paramTypeNames == listOf(Context::class.java.name)
        } ?: dex.findMethod {
            matcher { declaredClass = platform; paramTypes(Context::class.java.name); returnType = "boolean" }
        }.single { method -> method.invokes.any { it.name == "access\$getMSysAnimBlurSwitchEnable\$cp" } }
        val unavailable = dex.findMethod {
            matcher { declaredClass = "com.android.launcher3.popup.PopupBlurView"; name = "<init>" }
        }.flatMap { it.invokes }.distinctBy { it.descriptor }.single {
            it.className == platform && it.returnTypeName == "boolean" && it.paramTypeNames == listOf(Context::class.java.name)
        }
        val levelEnabled = dex.findMethod {
            matcher { declaredClass = FEATURES; name = "isAnimationLevelDisable"; paramTypes(); returnType = "boolean" }
        }.singleOrNull()?.callers?.single { it.className == platform && it.returnTypeName == "boolean" && it.paramCount == 0 }
        val support = dynamic.invokes.distinctBy { it.descriptor }.single {
            it.className == platform && it.returnTypeName == "boolean" && it.paramCount == 0 &&
                it.descriptor != levelEnabled?.descriptor && (levelEnabled != null || it.invokes.any { nested ->
                    nested.className == platform && nested.paramTypeNames == listOf("int") && nested.returnTypeName == "boolean"
                })
        }
        val tier = dex.findMethod {
            matcher { declaredClass = platform; paramTypes("int"); returnType = "boolean" }
        }.single()
        val queries = listOf("isMaterialBlurJumpSupported", "isSupportDrawerBlur", "isSupportDrawerBtnBlur")
            .mapNotNull { name -> dex.findMethod {
                matcher { declaredClass = BLUR; this.name = name; paramTypes(); returnType = "boolean" }
            }.singleOrNull() }
        val flags = linkedMapOf<MethodData, Boolean>()
        if (dynamicFlag != null) flags[dynamicFlag] = true
        listOf("getAllowLauncherBlur" to true,
            "isAllAppsDisableBlur" to false, "isRecentsDisableBlur" to false,
            "isSpringLoadedDisableBlur" to false, "isStartAppDisableBlur" to false,
            "isToggleBarDisableBlur" to false, "isPagePreviewDisableBlur" to false,
            "isIconBlurDisabled" to false, "isLowGaussianBlur" to false,
        ).forEach { (name, value) ->
            dex.findMethod {
                matcher { declaredClass = FEATURES; this.name = name; paramTypes(); returnType = "boolean" }
            }.singleOrNull()?.let { flags[it] = value }
        }
        val all = listOf(unavailable, dynamic, support, tier) + queries + flags.keys
        fun MethodData.method() = getMethodInstance(loader).apply { isAccessible = true }
        val result = Platform(unavailable.method(), dynamic.method(), support.method(), tier.method(),
            queries.map { it.method() }, flags.mapKeys { it.key.method() }, callers(all, loader))
        require(listOf(result.unavailable, result.dynamic, result.support, result.tier).all { Modifier.isStatic(it.modifiers) })
        return result
    }

    fun menu(dex: DexKitBridge, loader: ClassLoader): Menu {
        val builder = dex.findMethod {
            matcher {
                usingStrings("Machines below V do not support setting blurred backgrounds or current animLevel is too low or is in third party theme")
                paramTypes("boolean", LEVEL); returnType = "void"
            }
        }.single()
        val tier = builder.invokes.distinctBy { it.descriptor }.single {
            it.returnTypeName == "boolean" && it.paramTypeNames == listOf(LEVEL)
        }
        val materialOwners = builder.invokes.map { it.className }.filter { it.startsWith("com.coui.appcompat.") }.toSet() + MATERIAL
        val readLevel = dex.findMethod {
            matcher { usingStrings("getAnimLevelVersion e: "); paramTypes(); returnType = "int" }
        }.single { it.className in materialOwners }
        val materialTier = readLevel.callers.single {
            it.className == readLevel.className && it.returnTypeName == "boolean" && it.paramCount == 0
        }
        require(Modifier.isStatic(tier.modifiers) && Modifier.isStatic(materialTier.modifiers))
        return Menu(builder.getMethodInstance(loader).apply { isAccessible = true },
            tier.getMethodInstance(loader).apply { isAccessible = true },
            materialTier.getMethodInstance(loader).apply { isAccessible = true },
            callers(listOf(builder, tier, materialTier), loader))
    }

    private fun callers(targets: List<MethodData>, loader: ClassLoader): List<Executable> {
        // ART may have inlined both a tier check and its small wrapper. Deoptimize
        // only APK-owned callers; no framework-wide property or performance hooks.
        val direct = targets.flatMap { it.callers }.distinctBy { it.descriptor }
        return (targets + direct + direct.flatMap { it.callers }).distinctBy { it.descriptor }
            .mapNotNull { data -> runCatching {
                when {
                    data.isConstructor -> data.getConstructorInstance(loader)
                    data.isMethod -> data.getMethodInstance(loader)
                    else -> null
                }?.apply { isAccessible = true }
            }.getOrNull() }
    }
}
