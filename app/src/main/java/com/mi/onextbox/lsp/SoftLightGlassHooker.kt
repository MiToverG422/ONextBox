package com.mi.onextbox.lsp

import android.app.Application
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.graphics.Canvas
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.LruCache
import android.view.View
import com.mi.onextbox.diagnostics.SafeModePolicy
import com.mi.onextbox.lsp.SoftLightGlassSettings.Parameter
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernReflect
import com.mi.onextbox.lsp.compat.SafeModeRuntime
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

internal object SoftLightGlassHooker {
    private const val STATE = "soft-light:host-programs"
    private const val TAG = "ONextBox-SoftLight"
    private var session: Session? = null

    fun hook(packageName: String, processName: String, loader: ClassLoader, sourceDir: String) {
        if (processName.substringBefore(':') != packageName || packageName == "android" ||
            packageName == "com.mi.onextbox") return
        if (sourceDir.startsWith("/data/") && !isUpdatedSystemApp(packageName)) return
        val build = materialBuilder(loader)
        val coui = runCatching { SoftLightCouiPass.findSetter(loader) }.getOrNull()
        val nativeScope = packageName in SoftLightGlassSettings.mainPackages
        if (build == null && coui == null && !nativeScope) return
        synchronized(this) {
            val current = session ?: Session(packageName).also {
                session = it
                HotReloadLifecycle.register(STATE, capture = it::capture, detach = it::detach, restore = it::restore)
                HotReloadLifecycle.afterCommit { it.restore(HotReloadLifecycle.restoredState(STATE)) }
            }
            if (build != null) {
                current.installFramework(loader)
                current.installMaterial(build, loader)
            }
            if (packageName == "com.android.systemui") current.watchKeyguardPlugins(loader)
            if (coui != null) current.coui.install(coui)
            if (nativeScope || (build == null && coui != null)) current.installNative(loader)
        }
    }

    private fun materialBuilder(loader: ClassLoader): Method? =
        listOf("com.oplus.posteffect.agsl.BlurDrawableShaderBaseStringKt", "com.oplus.posteffect.agsl.a").firstNotNullOfOrNull { name ->
            ModernReflect.findClassIfExists(name, loader)?.declaredMethods?.singleOrNull {
                val p = it.parameterTypes
                Modifier.isStatic(it.modifiers) && it.returnType == Void.TYPE && p.size == 6 &&
                    p[0] == StringBuilder::class.java && p[1] == Integer.TYPE && p[2] == Integer.TYPE &&
                    p[4] == java.lang.Boolean.TYPE && p[5] == List::class.java &&
                    (name.endsWith("Kt") || p[3].name == "com.oplus.posteffect.params.CornerType")
            }
        }

    private fun isUpdatedSystemApp(packageName: String): Boolean = runCatching {
        val threadType = Class.forName("android.app.ActivityThread")
        val thread = threadType.getMethod("currentActivityThread").invoke(null) ?: return false
        val bound = ModernReflect.getObjectField(thread, "mBoundApplication") ?: return false
        val info = ModernReflect.getObjectField(bound, "appInfo") as? ApplicationInfo ?: return false
        info.packageName == packageName &&
            info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }.getOrDefault(false)

    private class Session(private val packageName: String) {
        private val main = Handler(Looper.getMainLooper())
        private val lock = Any()
        private val programs = WeakHashMap<RuntimeShader, Boolean>()
        private val directional = WeakHashMap<RuntimeShader, Boolean>()
        private val dimmable = WeakHashMap<RuntimeShader, Boolean>()
        private val aodPrograms = WeakHashMap<RuntimeShader, Boolean>()
        private val effects = WeakHashMap<RenderEffect, RuntimeShader>()
        private val nodes = WeakHashMap<RenderNode, NodeBinding>()
        private val drawables = WeakHashMap<Drawable, Boolean>()
        private val sources = LruCache<String, String>(24)
        private val validating = ThreadLocal<Boolean>()
        private val reported = ConcurrentHashMap.newKeySet<String>()
        private val materialOwners = ConcurrentHashMap.newKeySet<Class<*>>()
        private val keyguardLoaders = ConcurrentHashMap.newKeySet<ClassLoader>()
        private val currentApp by lazy { Class.forName("android.app.ActivityThread").getMethod("currentApplication") }
        @Volatile private var application: Application? = null
        @Volatile private var running = true
        private var preferences: SharedPreferences? = null
        private var workspaceGenerationChanged = false
        @Volatile private var profile = SoftLightGlassSettings.Profile(packageName)
        @Volatile private var followTilt = false
        @Volatile private var lastDraw = 0L
        private var tiltX = 0f
        private var tiltY = 0f
        private val tilt = SoftLightTilt(main, ::host, {
            followTilt && active() && (SystemClock.uptimeMillis()-lastDraw < 1000 ||
                synchronized(lock) { drawables.keys.any(::visibleSurface) })
        }, ::moveLight)

        private fun visibleSurface(drawable: Drawable): Boolean {
            if (!drawable.isVisible) return false
            var owner = drawable.callback
            repeat(8) {
                val callback = owner
                if (callback is View) return callback.isShown && callback.windowVisibility == View.VISIBLE
                owner = (callback as? Drawable)?.callback ?: return false
            }
            return false
        }

        private fun aim(shader: RuntimeShader) {
            val angle = Math.toRadians(profile[Parameter.ANGLE].toDouble()*100.0)
            val c = kotlin.math.cos(angle).toFloat()
            val s = kotlin.math.sin(angle).toFloat()
            val gain = if (followTilt) profile[Parameter.RESPONSE] else 0f
            shader.setFloatUniform("onbLampOffset", .4f*(1f-c)+.6f*s+gain*tiltX,
                .6f*(1f-c)-.4f*s+gain*tiltY)
        }

        private fun moveLight(x: Float, y: Float) {
            val bindings: List<Pair<RenderNode, NodeBinding>>
            val surfaces: List<Drawable>
            synchronized(lock) {
                tiltX = x
                tiltY = y
                if (!followTilt || !running || directional.isEmpty()) return
                directional.keys.forEach(::aim)
                bindings = nodes.filterValues { it.program in directional }.toList()
                surfaces = drawables.keys.filter { it.isVisible }
            }
            bindings.forEach { (node, binding) -> rebind(node, binding) }
            if (packageName == "com.android.launcher") runCatching { launcher.refresh(surfaces, false) }
                .onFailure { logOnce("workspace light direction", it) }
            surfaces.forEach { it.invalidateSelf() }
        }
        private val selection = SoftLightSelection { profile.keepWhite && active() }
        val coui = SoftLightCouiPass({ active() && profile.wholeProcess }, { profile }) { logOnce("native COUI blur and tint") }
        private val nativeBlur = SoftLightNativeBlurPass(packageName, ::active, { profile }, ::host, { logOnce(it) })
        fun installNative(loader: ClassLoader) = runCatching { nativeBlur.install(loader) }
            .onFailure { logOnce("native blur adapter unavailable", it) }
        private val launcher = SoftLightLauncherPass(
            { shader -> synchronized(lock) { programs.containsKey(shader) } },
            { effect -> synchronized(lock) { effects[effect] } },
            { effect, shader -> synchronized(lock) { effects[effect] = shader } },
            ::active, ::logOnce,
        )
        private val panels = if (packageName in setOf("com.android.systemui","com.coloros.smartsidebar",
            "com.coloros.assistantscreen","com.heytap.quicksearchbox","com.oplus.aod")) SoftLightPanelPass(packageName, ::preserveControlWhite, {
                profile.keepWhite && profile.selected(SoftLightGlassSettings.Scene.VOLUME) &&
                    profile[SoftLightGlassSettings.Parameter.STRENGTH] > 0f && active()
            }) { logOnce(it) } else null

        private class NodeBinding(val program: RuntimeShader, val setter: Method, val mode: Int?)

        private fun host() = application ?: (runCatching { currentApp.invoke(null) as? Application }.getOrNull())
            ?.takeIf { it.packageName == packageName && CouiDividerHooker.isSystemContext(it) }?.also { application = it }

        private fun preserveControlWhite() = profile.keepWhite && profile.selected(SoftLightGlassSettings.Scene.CONTROL) &&
            profile[Parameter.STRENGTH] > 0f && active()

        private fun active() = running && !SafeModeRuntime.blocked && SoftLightGlassSettings.enabledXposed()
        private val refresh = Runnable { refreshMaterials() }
        private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == SoftLightGlassSettings.ENABLED || key in SoftLightGlassSettings.keys || key == SafeModePolicy.MANUAL || key == SafeModePolicy.AUTOMATIC) {
                main.removeCallbacks(refresh)
                main.post(refresh)
            }
        }

        private fun logOnce(key: String, error: Throwable? = null) {
            if (!reported.add(key)) return
            if (error == null) HookLog.i(TAG, "$packageName: $key") else HookLog.w(TAG, "$packageName: $key", error)
        }

        private fun install(key: String, target: Executable, hooker: XposedInterface.Hooker) {
            ModernHookRegistry.installFast("$STATE:$key", target, hooker)
        }

        fun watchKeyguardPlugins(loader: ClassLoader) {
            HotReloadLifecycle.registerReplay("$STATE:keyguard-loaders", capture = { keyguardLoaders.toTypedArray() },
                replay = { saved -> (saved as? Array<*>)?.filterIsInstance<ClassLoader>()?.forEach(::installKeyguardMaterial) })
            val plugin = ModernReflect.findClassIfExists("com.oplus.keyguard.plugin.AbstractSubPlugin", loader) ?: return
            plugin.declaredMethods.filter { method ->
                method.name == "getView" && method.parameterTypes.contentEquals(arrayOf(Integer.TYPE)) ||
                    method.parameterTypes.singleOrNull()?.name == "com.oplus.keyguard.IOplusStyleBase"
            }.forEach { method ->
                install("keyguard-plugin:${method.toGenericString()}", method, XposedInterface.Hooker { chain ->
                    runCatching { chain.args.firstOrNull()?.javaClass?.classLoader?.let(::installKeyguardMaterial) }
                        .onFailure { logOnce("keyguard plugin material unavailable", it) }
                    val result = chain.proceed()
                    runCatching { (result as? View)?.javaClass?.classLoader?.let(::installKeyguardMaterial) }
                        .onFailure { logOnce("keyguard view material unavailable", it) }
                    result
                })
                ModernHookRuntime.requireModule().deoptimize(method)
            }
        }

        private fun installKeyguardMaterial(loader: ClassLoader) {
            if (loader in keyguardLoaders) return
            val build = materialBuilder(loader) ?: return
            if (!keyguardLoaders.add(loader)) return
            installFramework(loader)
            installMaterial(build, loader, lockscreen = true)
        }

        fun installMaterial(build: Method, loader: ClassLoader, lockscreen: Boolean = false) {
            if (!materialOwners.add(build.declaringClass)) return
            if (lockscreen) runCatching { installExistingKeyguardShaders(loader) }
                .onFailure { logOnce("keyguard cached shader adapter unavailable", it) }
            if (packageName == "com.android.systemui") runCatching { selection.installTiles(loader, ::preserveControlWhite, ::logOnce) }
                .onFailure { logOnce("tile selection metadata unavailable", it) }
            panels?.let { pass -> runCatching { pass.installSources(loader) }.onFailure { logOnce("surface roles", it) } }
            if (packageName == "com.android.launcher") runCatching { launcher.install(loader) }
                .onFailure { logOnce("workspace material adapter unavailable", it) }
            val id = System.identityHashCode(build.declaringClass)
            install("source:$id", build, XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                val buffer = chain.getArg(0) as StringBuilder
                if (!running || host() == null || buffer.isEmpty()) return@Hooker result
                val original = buffer.toString()
                val key = if (lockscreen) "keyguard:$original" else original
                val extension = sources.get(key) ?: compile(original, lockscreen).also { sources.put(key, it) }
                if (extension.isNotEmpty()) { buffer.setLength(0); buffer.append(extension) }
                result
            })
            if (lockscreen) logOnce("keyguard plugin material adapter installed")
            for (name in listOf("com.oplus.posteffect.drawable.BaseDrawable",
                "com.android.launcher3.uioverrides.states.blurdrawable.RenderEffectDrawable",
                "com.android.launcher3.uioverrides.states.blurdrawable.LayerBlurDrawable")) {
                val type = ModernReflect.findClassIfExists(name, loader) ?: continue
                val draw = runCatching { type.getDeclaredMethod("draw", Canvas::class.java) }.getOrNull() ?: continue
                val tagPanel = panels?.drawableAdapter(type, { shader -> synchronized(lock) { programs.containsKey(shader) } }, ::host)
                val tagSelection = selection.adapter(type, { shader -> synchronized(lock) { programs[shader] == true } }) { shader ->
                    val bound = synchronized(lock) { nodes.filterValues { it.program === shader }.toList() }
                    bound.forEach { (node, binding) -> rebind(node, binding) }
                }
                install("drawable:$name:${System.identityHashCode(type)}", draw, XposedInterface.Hooker { chain ->
                    val drawable = chain.thisObject as Drawable
                    synchronized(lock) { drawables[drawable] = true }
                    if (followTilt && drawable.isVisible) {
                        lastDraw = SystemClock.uptimeMillis()
                        tilt.request()
                    }
                    if (tagPanel != null) runCatching { tagPanel(drawable) }.onFailure { logOnce("panel material lookup", it) }
                    if (tagSelection != null) runCatching { tagSelection(drawable) }.onFailure { logOnce("selection lookup", it) }
                    chain.proceed()
                })
            }
        }

        private fun installExistingKeyguardShaders(loader: ClassLoader) {
            val shaderType = ModernReflect.findClassIfExists("com.oplus.posteffect.agsl.DrawableShader", loader) ?: return
            val getter = shaderType.getDeclaredMethod("getShaderOrNull")
            val rebuild = shaderType.getDeclaredMethod("rebuildShader").also { it.isAccessible = true }
            val shaderField = shaderType.declaredFields.single { it.type == RuntimeShader::class.java }.also { it.isAccessible = true }
            val attempted = WeakHashMap<RuntimeShader, Boolean>()
            install("keyguard-existing:${System.identityHashCode(shaderType)}", getter, XposedInterface.Hooker { chain ->
                val result = chain.proceed() as? RuntimeShader ?: return@Hooker null
                if (synchronized(lock) { programs.containsKey(result) }) return@Hooker result
                if (synchronized(attempted) { attempted.put(result, true) } != null) return@Hooker result
                runCatching {
                    rebuild.invoke(chain.thisObject)
                    shaderField.get(chain.thisObject) ?: result
                }.onFailure { logOnce("keyguard cached material refresh", it) }.getOrDefault(result)
            })
        }

        private fun compile(original: String, lockscreen: Boolean): String {
            val modified = SoftLightGlassShader.extend(original, packageName == "com.android.launcher", panels != null, lockscreen) ?: return ""
            // Validate this vendor variant before handing it back to the host's own shader cache.
            validating.set(true)
            return try {
                RuntimeShader(modified)
                modified
            } catch (e: RuntimeException) {
                logOnce("unsupported material shader", e)
                ""
            } finally { validating.remove() }
        }

        fun installFramework(loader: ClassLoader) {
            val ctor = RuntimeShader::class.java.getDeclaredConstructor(String::class.java)
            install("program", ctor, XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                val source = chain.getArg(0) as String
                if (validating.get() != true && SoftLightGlassShader.isMaterial(source))
                    (chain.thisObject as? RuntimeShader)?.let { register(it, source) }
                result
            })
            val cache = ModernReflect.findClassIfExists("com.oplus.graphics.OplusRuntimeShader", loader)
            cache?.declaredMethods?.filter {
                it.name == "createRuntimeShader" && it.parameterTypes.firstOrNull() == String::class.java &&
                    it.returnType == RuntimeShader::class.java
            }?.forEach { method ->
                install("cached:${method.toGenericString()}", method, XposedInterface.Hooker { chain ->
                    val result = chain.proceed()
                    val source = chain.getArg(0) as String
                    if (result is RuntimeShader && SoftLightGlassShader.isMaterial(source)) register(result, source)
                    result
                })
            }
            val factory = RenderEffect::class.java.getMethod("createRuntimeShaderEffect", RuntimeShader::class.java, String::class.java)
            install("bind", factory, XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                if (result is RenderEffect && chain.getArg(1) == SoftLightGlassShader.INPUT) {
                    val shader = chain.getArg(0) as RuntimeShader
                    val known = synchronized(lock) {
                        if (programs.containsKey(shader)) { effects[result] = shader; true } else false
                    }
                    if (known && active()) logOnce("native backdrop bound")
                }
                result
            })
            RenderNode::class.java.declaredMethods.filter {
                it.name == "setBackdropRenderEffect" &&
                    (it.parameterTypes.contentEquals(arrayOf(RenderEffect::class.java)) ||
                        it.parameterTypes.contentEquals(arrayOf(RenderEffect::class.java, Integer.TYPE)))
            }.forEach { method ->
                install("node:${method.parameterCount}", method, XposedInterface.Hooker { chain ->
                    val result = chain.proceed()
                    val node = chain.thisObject as RenderNode
                    val effect = chain.getArg(0) as? RenderEffect
                    synchronized(lock) {
                        val program = effect?.let(effects::get)
                        if (program == null) nodes.remove(node)
                        else nodes[node] = NodeBinding(program, method, chain.args.getOrNull(1) as? Int)
                    }
                    result
                })
            }
        }

        private fun configure(shader: RuntimeShader, enabled: Boolean, customizable: Boolean) {
            val metrics = host()?.resources?.displayMetrics ?: return
            val p = profile
            shader.setFloatUniform(SoftLightGlassShader.UNIFORM, if (enabled && customizable) metrics.density else 0f,
                metrics.widthPixels.toFloat(), metrics.heightPixels.toFloat())
            if (!customizable) return
            shader.setFloatUniform("onbScenes", p.channels)
            shader.setFloatUniform("onbOptics", p[Parameter.STRENGTH], p[Parameter.BLUR], p[Parameter.WIDTH], p[Parameter.SHINE])
            shader.setFloatUniform("onbToneControls", p[Parameter.REFRACTION], p[Parameter.SATURATION], p[Parameter.EXPOSURE])
            shader.setFloatUniform("onbKeepWhite", if (p.keepWhite) 1f else 0f)
            synchronized(lock) {
                if (shader in aodPrograms) shader.setFloatUniform("onbAodScene", if (p.selected(SoftLightGlassSettings.Scene.AOD)) 1f else 0f)
                if (shader in dimmable) shader.setFloatUniform("onbDimming", p[Parameter.DIMMING])
                if (shader in directional) {
                    shader.setFloatUniform("onbTint", p[Parameter.WARMTH], p[Parameter.CONTRAST])
                    aim(shader)
                }
            }
        }

        private fun register(shader: RuntimeShader, source: String) {
            val enabled = active()
            synchronized(lock) {
                if (programs.containsKey(shader)) return
                val customizable = "uniform float onbKeepWhite;" in source
                if ("uniform float onbDimming;" in source) dimmable[shader] = true
                if ("uniform float2 onbLampOffset;" in source) directional[shader] = true
                if ("uniform float onbAodScene;" in source) aodPrograms[shader] = true
                configure(shader, enabled, customizable)
                programs[shader] = customizable
            }
            val panelPass = panels
            if (panelPass != null && "uniform float2 onbPanelSurface;" in source) {
                runCatching { host()?.let { panelPass.created(shader, it,
                    lockscreenPlugin = "const bool onbLockscreenPlugin = true;" in source,
                    supportsAod = "uniform float onbAodScene;" in source) } }.onFailure { logOnce("panel material setup", it) }
            }
            if (enabled) logOnce("native material shader configured")
        }

        private fun refreshMaterials(rebindWorkspace: Boolean = true) {
            val enabled = active()
            profile = SoftLightGlassSettings.Profile(packageName)
            followTilt = enabled && profile.gyro && profile.channels.any { it > 0f } &&
                profile[Parameter.RESPONSE] > 0f && profile[Parameter.SHINE] > 0f &&
                profile[Parameter.WIDTH] > 0f && profile[Parameter.STRENGTH] > 0f
            if (!followTilt) tilt.stop()
            val shaders: List<Pair<RuntimeShader, Boolean>>
            val bound: List<Pair<RenderNode, NodeBinding>>
            val surfaces: List<Drawable>
            synchronized(lock) {
                shaders = programs.map { it.key to it.value }
                bound = nodes.map { it.key to it.value }
                surfaces = drawables.keys.toList()
            }
            shaders.forEach { (shader, customizable) -> runCatching { configure(shader, enabled, customizable) }.onFailure { e -> logOnce("material uniform refresh", e) } }
            // RenderEffect snapshots uniforms. Rebind the native program through its original mode.
            bound.forEach { (node, binding) -> rebind(node, binding) }
            if (rebindWorkspace && packageName == "com.android.launcher") {
                runCatching { launcher.refresh(surfaces, workspaceGenerationChanged) }
                    .onFailure { logOnce("workspace material refresh", it) }
                workspaceGenerationChanged = false
            }
            surfaces.forEach { it.invalidateSelf() }
            coui.refresh()
            nativeBlur.refresh()
        }

        private fun rebind(node: RenderNode, binding: NodeBinding) {
            runCatching {
                val effect = RenderEffect.createRuntimeShaderEffect(binding.program, SoftLightGlassShader.INPUT)
                if (binding.mode == null) binding.setter.invoke(node, effect)
                else binding.setter.invoke(node, effect, binding.mode)
            }.onFailure { logOnce("native backdrop refresh", it) }
        }

        fun capture(): Any = synchronized(lock) {
            arrayOf(programs.keys.toTypedArray(), effects.map { arrayOf(it.key, it.value) }.toTypedArray(),
                nodes.map { (node, b) -> arrayOf(node, b.program, b.setter, b.mode) }.toTypedArray(), drawables.keys.toTypedArray(),
                panels?.capture(), coui.capture(), programs.filterValues { it }.keys.toTypedArray(),
                directional.keys.toTypedArray(), dimmable.keys.toTypedArray(), aodPrograms.keys.toTypedArray())
        }

        fun detach() {
            running = false
            preferences?.unregisterOnSharedPreferenceChangeListener(listener)
            preferences = null
            main.removeCallbacks(refresh)
            // Keep the launcher's own snapshots addressable by the incoming generation.
            refreshMaterials(rebindWorkspace = false)
            sources.evictAll()
        }

        fun restore(saved: Any?) {
            running = true
            val parts = saved as? Array<*>
            if (parts != null && parts.size >= 4) synchronized(lock) {
                val customizable = (parts.getOrNull(6) as? Array<*>)?.toSet().orEmpty()
                (parts.getOrNull(7) as? Array<*>)?.filterIsInstance<RuntimeShader>()?.forEach { directional[it] = true }
                (parts.getOrNull(8) as? Array<*>)?.filterIsInstance<RuntimeShader>()?.forEach { dimmable[it] = true }
                (parts.getOrNull(9) as? Array<*>)?.filterIsInstance<RuntimeShader>()?.forEach { aodPrograms[it] = true }
                (parts[0] as? Array<*>)?.filterIsInstance<RuntimeShader>()?.forEach { programs[it] = it in customizable }
                (parts[1] as? Array<*>)?.filterIsInstance<Array<*>>()?.forEach { effects[it[0] as RenderEffect] = it[1] as RuntimeShader }
                (parts[2] as? Array<*>)?.filterIsInstance<Array<*>>()?.forEach {
                    nodes[it[0] as RenderNode] = NodeBinding(it[1] as RuntimeShader, it[2] as Method, it[3] as? Int)
                }
                (parts[3] as? Array<*>)?.filterIsInstance<Drawable>()?.forEach { drawables[it] = true }
                panels?.restore(parts.getOrNull(4))
                coui.restore(parts.getOrNull(5))
                workspaceGenerationChanged = true
            }
            if (preferences == null) ModernHookRuntime.remotePreferences("lsp_features")?.let {
                it.registerOnSharedPreferenceChangeListener(listener)
                preferences = it
            }
            main.removeCallbacks(refresh)
            main.post(refresh)
        }
    }
}
