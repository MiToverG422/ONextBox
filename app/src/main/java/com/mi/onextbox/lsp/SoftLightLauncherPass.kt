package com.mi.onextbox.lsp

import android.graphics.Canvas
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.drawable.Drawable
import android.view.View
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernReflect
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Field
import java.util.Collections
import java.util.WeakHashMap

/** Identifies workspace backgrounds before the launcher snapshots their shader uniforms. */
internal class SoftLightLauncherPass(
    private val owns: (RuntimeShader) -> Boolean,
    private val snapshotProgram: (RenderEffect) -> RuntimeShader?,
    private val rememberSnapshot: (RenderEffect, RuntimeShader) -> Unit,
    private val active: () -> Boolean,
    private val report: (String) -> Unit,
) {
    private val role = WeakHashMap<RuntimeShader, Int>()
    private val installed = mutableSetOf<Class<*>>()
    private val refreshers = mutableListOf<(List<Drawable>, Boolean) -> Unit>()

    private fun field(type: Class<*>, test: (Field) -> Boolean): Field? =
        generateSequence(type) { it.superclass }.flatMap { it.declaredFields.asSequence() }
            .firstOrNull(test)?.apply { isAccessible = true }

    private fun mark(program: RuntimeShader?, kind: Int): Boolean {
        if (program == null || !owns(program)) return false
        synchronized(role) {
            if (role[program] != kind) {
                if (runCatching { program.setFloatUniform("onbPreviewFolder", kind.toFloat()) }.isFailure) return false
                role[program] = kind
            }
        }
        return true
    }

    fun install(loader: ClassLoader) {
        val path = "com.android.launcher3.uioverrides.states.blurdrawable."
        for (name in listOf("LayerBlurDrawable", "RenderEffectDrawable")) {
            val type = ModernReflect.findClassIfExists(path+name, loader) ?: continue
            if (type in installed) continue
            val node = name == "RenderEffectDrawable"
            val material = field(type) { it.name == if (node) "mUniRenderDrawable" else "mBlurDrawable" } ?: continue
            val shader = field(material.type) { it.type.name in setOf("com.oplus.posteffect.agsl.DrawableShader", "com.oplus.posteffect.agsl.b") } ?: continue
            val runtime = field(shader.type) { it.type == RuntimeShader::class.java } ?: continue
            val rebuild = shader.type.declaredMethods.singleOrNull {
                it.name in setOf("updateShaderBlendParams", "n", "m") && it.returnType == Void.TYPE &&
                    it.parameterTypes.contentEquals(arrayOf(java.lang.Boolean.TYPE))
            }?.apply { isAccessible = true }
            val isFolder = field(type) { it.name == "mIsFolder" } ?: continue
            val materialType = field(type) { it.name in setOf("type", "mType") && it.type == Integer.TYPE }
            fun kind(owner: Any, folder: Boolean = isFolder.getBoolean(owner)): Int =
                if (folder) FOLDER else when (materialType?.getInt(owner)) {
                    5, 6, 8 -> CARD
                    7 -> WIDGET
                    3, 4 -> WORKSPACE_BAR
                    else -> OTHER
                }
            fun reportKind(kind: Int) {
                if (!active()) return
                when (kind) {
                    FOLDER -> report("closed folder preview / $name")
                    CARD -> report("workspace card material / $name")
                    WIDGET -> report("workspace widget material / $name")
                    WORKSPACE_BAR -> report("workspace bar material / $name")
                }
            }
            fun program(source: Any?): RuntimeShader? = source?.let(shader::get)?.let(runtime::get) as? RuntimeShader
            val prefix = "soft-light:folder:$name:${System.identityHashCode(type)}"

            if (!node) {
                val draw = type.getDeclaredMethod("draw", Canvas::class.java)
                ModernHookRegistry.installFast("$prefix:bitmap", draw, XposedInterface.Hooker { chain ->
                    val owner = chain.thisObject ?: return@Hooker chain.proceed()
                    val selected = kind(owner)
                    if (mark(program(material.get(owner)), selected)) reportKind(selected)
                    chain.proceed()
                })
                refreshers += { drawables, newGeneration ->
                    if (newGeneration && rebuild != null) for (owner in drawables) {
                        if (!type.isInstance(owner) || kind(owner) == OTHER) continue
                        val source = material.get(owner) ?: continue
                        if (program(source)?.let(owns) == true) rebuild.invoke(shader.get(source), true)
                    }
                }
                installed += type
                continue
            }

            val factory = material.type.declaredMethods.singleOrNull {
                it.returnType == RenderEffect::class.java && it.parameterCount == 0
            } ?: continue
            val set = type.getDeclaredMethod("setBackdropRenderEffect", RenderEffect::class.java, Integer.TYPE)
            val current = field(type) { it.name == "mRenderEffect" } ?: continue
            val mode = field(type) { it.name == "mBackdropMode" } ?: continue
            val view = field(type) { it.name == "view" && View::class.java.isAssignableFrom(it.type) }
            val radius = type.declaredMethods.singleOrNull { it.name == "setCornerRadius" &&
                it.parameterCount == 3 && it.parameterTypes[1] == java.lang.Boolean.TYPE } ?: continue
            val sources = Collections.synchronizedMap(WeakHashMap<Any, Int>())
            ModernHookRegistry.installFast("$prefix:radius", radius, XposedInterface.Hooker { chain ->
                val owner = chain.thisObject ?: return@Hooker chain.proceed()
                material.get(owner)?.let { sources[it] = kind(owner, chain.getArg(1) as Boolean) }
                chain.proceed()
            })
            for (method in type.declaredMethods.filter { it.name == "refreshEffectDrawableParam" ||
                it.name == "refreshAllCategoryEffectDrawableParam" || it.name == "clearDefaultBlendColor" }) {
                runCatching { ModernHookRuntime.requireModule().deoptimize(method) }
                ModernHookRegistry.installFast("$prefix:${method.name}", method, XposedInterface.Hooker { chain ->
                    val owner = chain.thisObject ?: return@Hooker chain.proceed()
                    material.get(owner)?.let { sources[it] = kind(owner) }
                    chain.proceed()
                })
            }
            ModernHookRegistry.installFast("$prefix:create", factory, XposedInterface.Hooker { chain ->
                val source = chain.thisObject
                val live = program(source)
                val selected = sources[source] ?: OTHER
                val known = mark(live, selected)
                val effect = chain.proceed()
                if (known && effect is RenderEffect && live != null) {
                    rememberSnapshot(effect, live)
                    reportKind(selected)
                }
                effect
            })
            refreshers += { drawables, newGeneration ->
                for (owner in drawables) {
                    if (!type.isInstance(owner)) continue
                    val source = material.get(owner) ?: continue
                    val selected = kind(owner)
                    sources[source] = selected
                    val effect = current.get(owner) as? RenderEffect ?: continue
                    val before = program(source) ?: continue
                    // Drag previews may borrow a different owner's snapshot and transform.
                    val recorded = snapshotProgram(effect)
                    val attachedBackground = newGeneration && recorded == null && (view?.get(owner) as? View)?.background === owner
                    if (selected == OTHER || (recorded !== before && !attachedBackground) || !owns(before)) continue
                    if (newGeneration && rebuild != null) rebuild.invoke(shader.get(source), true)
                    val live = program(source) ?: continue
                    if (!mark(live, selected)) continue
                    val fresh = factory.invoke(source) as? RenderEffect ?: continue
                    rememberSnapshot(fresh, live)
                    set.invoke(owner, fresh, mode.getInt(owner))
                    owner.invalidateSelf()
                    reportKind(selected)
                    if (active()) report("workspace material rebound")
                }
            }
            installed += type
        }
    }

    fun refresh(drawables: List<Drawable>, newGeneration: Boolean) =
        refreshers.forEach { reload -> reload(drawables, newGeneration) }

    private companion object {
        const val OTHER = 0
        const val FOLDER = 1
        const val CARD = 2
        const val WIDGET = 3
        const val WORKSPACE_BAR = 4
    }
}
