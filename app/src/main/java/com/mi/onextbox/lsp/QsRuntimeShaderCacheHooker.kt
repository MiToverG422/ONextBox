package com.mi.onextbox.lsp

import android.annotation.SuppressLint
import android.graphics.RuntimeShader
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import java.lang.reflect.Modifier

/** Reuse the compiled base shader for native SystemUI post-effect drawables. */
internal object QsRuntimeShaderCacheHooker {
    private const val KEY = "systemui:post-effect-base-shader"

    // This runs inside the LSPosed SystemUI scope and is skipped when its native methods are unavailable.
    @SuppressLint("BlockedPrivateApi")
    fun hook(loader: ClassLoader) {
        runCatching {
            val owner = loader.loadClass("com.oplus.posteffect.agsl.DrawableShader")
            val constructor = owner.getDeclaredConstructor(Int::class.javaPrimitiveType)
            val sourceField = owner.getDeclaredField("shaderStringBuilder").apply { isAccessible = true }
            check(sourceField.type == StringBuilder::class.java && !Modifier.isStatic(sourceField.modifiers))
            val supportsCache = owner.getDeclaredField("supportsCachedRuntimeShader").apply { isAccessible = true }
            val usesCache = owner.getDeclaredField("USE_RUNTIME_SHADER_CACHED").apply { isAccessible = true }
            check(listOf(supportsCache, usesCache).all {
                it.type == Boolean::class.javaPrimitiveType && Modifier.isStatic(it.modifiers)
            })

            val shader = RuntimeShader::class.java
            val create = shader.getDeclaredMethod("nativeCreateBuilder", String::class.java)
            val createCached = shader.getDeclaredMethod(
                "nativeCreateBuilderUseCached", String::class.java, Boolean::class.javaPrimitiveType,
            ).apply { isAccessible = true }
            check(listOf(create, createCached).all {
                it.returnType == Long::class.javaPrimitiveType && Modifier.isStatic(it.modifiers) && Modifier.isNative(it.modifiers)
            })

            val constructing = ThreadLocal<Any>()
            ModernHookRegistry.installFast("$KEY:builder", create) { chain ->
                val drawable = constructing.get() ?: return@installFast chain.proceed()
                val source = chain.getArg(0) as? String ?: return@installFast chain.proceed()
                val builder = sourceField.get(drawable) as? StringBuilder ?: return@installFast chain.proceed()
                if (!source.contentEquals(builder) || !supportsCache.getBoolean(null) || !usesCache.getBoolean(null)) {
                    return@installFast chain.proceed()
                }

                // Like the native style-rebuild path, share only the compiled effect. HWUI
                // allocates a new builder so this drawable keeps independent uniform state.
                constructing.remove()
                runCatching { createCached.invoke(null, source, true) as? Long }
                    .getOrNull()?.takeIf { it != 0L } ?: chain.proceed()
            }
            ModernHookRegistry.installFast("$KEY:constructor", constructor) { chain ->
                val instance = chain.thisObject ?: return@installFast chain.proceed()
                val previous = constructing.get()
                constructing.set(instance)
                try {
                    chain.proceed()
                } finally {
                    if (previous == null) constructing.remove() else constructing.set(previous)
                }
            }
        }.onFailure {
            ModernHookRegistry.unhookPrefix("$KEY:")
            HookLog.w("ONextBox-QS", "Native post-effect shader cache unavailable", it)
        }
    }
}
