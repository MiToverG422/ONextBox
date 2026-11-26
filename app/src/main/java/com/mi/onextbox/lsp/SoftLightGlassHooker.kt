package com.mi.onextbox.lsp

import android.app.Application
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.HardwareRenderer
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
import com.mi.onextbox.lsp.SoftLightGlassShader.PanelProgram
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import com.mi.onextbox.lsp.compat.ModernReflect
import com.mi.onextbox.lsp.compat.SafeModeRuntime
import io.github.libxposed.api.XposedInterface
import java.io.File
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

internal object SoftLightGlassHooker {
    private const val STATE = "soft-light:host-programs"
    private const val TAG = "ONextBox-SoftLight"
    private const val MATERIAL_REQUEST = "// onbMaterial\n"
    private const val KEYGUARD_REQUEST = "// onbKeyguardMaterial\n"
    private var session: Session? = null

    fun hook(packageName: String, processName: String, loader: ClassLoader, sourceDir: String) {
        if (processName.substringBefore(':') != packageName || packageName == "android" ||
            packageName == "com.mi.onextbox") return
        if (sourceDir.startsWith("/data/") && !isUpdatedSystemApp(packageName)) return
        if (packageName == "com.android.systemui") {
            SystemUiHookActivation.install(STATE, setOf(SoftLightGlassSettings.ENABLED), SoftLightGlassSettings::enabledXposed) { late ->
                val saved = HotReloadLifecycle.restoredState(STATE) as? Array<*>
                install(packageName, loader, adoptExistingShaders = late || saved?.getOrNull(15) == true)
            }
        } else {
            install(packageName, loader)
        }
    }

    private fun install(packageName: String, loader: ClassLoader, adoptExistingShaders: Boolean = false) {
        val shelf = packageName == SoftLightShelfPass.PACKAGE
        val build = if (shelf) null else materialBuilder(loader)
        val coui = if (shelf) null else runCatching { SoftLightCouiPass.findSetter(loader) }.getOrNull()
        val nativeScope = packageName in SoftLightGlassSettings.mainPackages
        if (build == null && coui == null && !nativeScope) return
        synchronized(this) {
            val current = session ?: Session(packageName, adoptExistingShaders).also {
                session = it
                HotReloadLifecycle.register(STATE, capture = it::capture, detach = it::detach, restore = it::restore)
                HotReloadLifecycle.afterCommit { it.restore(HotReloadLifecycle.restoredState(STATE)) }
            }
            if (shelf) {
                current.installShelf(loader)
                return
            }
            if (build != null) {
                current.installFramework(loader)
                current.installMaterial(build, loader)
            }
            if (packageName == "com.android.systemui") {
                current.installPanelCacheCheck()
                current.watchKeyguardPlugins(loader)
            }
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
        val threadType = HostClassLookup.load("android.app.ActivityThread")
        val thread = threadType.getMethod("currentActivityThread").invoke(null) ?: return false
        val bound = ModernReflect.getObjectField(thread, "mBoundApplication") ?: return false
        val info = ModernReflect.getObjectField(bound, "appInfo") as? ApplicationInfo ?: return false
        info.packageName == packageName &&
            info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }.getOrDefault(false)

    private class Session(private val packageName: String, private val adoptExistingShaders: Boolean) {
        private val systemUi = packageName == "com.android.systemui"
        private val main = Handler(Looper.getMainLooper())
        private val lock = Any()
        private val programs = WeakHashMap<RuntimeShader, Boolean>()
        private val nativePrograms = WeakHashMap<RuntimeShader, Boolean>()
        private val directional = WeakHashMap<RuntimeShader, Boolean>()
        private val dimmable = WeakHashMap<RuntimeShader, Boolean>()
        private val aodPrograms = WeakHashMap<RuntimeShader, Boolean>()
        private val stylePrograms = WeakHashMap<RuntimeShader, Boolean>()
        private val pluginPrograms = WeakHashMap<RuntimeShader, Boolean>()
        private val panelModes = WeakHashMap<RuntimeShader, PanelProgram>()
        private val panelOwners = WeakHashMap<Any, Boolean>()
        private val buildingPanel = ThreadLocal<PanelProgram>()
        @Volatile private var hasLegacyPrograms = false
        private val cachedMaterialReaders = mutableListOf<(List<Drawable>) -> Unit>()
        private val effects = WeakHashMap<RenderEffect, RuntimeShader>()
        private val nodes = WeakHashMap<RenderNode, NodeBinding>()
        private val drawables = WeakHashMap<Drawable, Boolean>()
        private data class SourceKey(val source: String, val mode: PanelProgram?)
        private val sources = LruCache<SourceKey, GlassShaderVariant>(24)
        private val validating = ThreadLocal<Boolean>()
        private val reported = ConcurrentHashMap.newKeySet<String>()
        private val materialOwners = ConcurrentHashMap.newKeySet<Class<*>>()
        private val keyguardLoaders = ConcurrentHashMap.newKeySet<ClassLoader>()
        private val currentApp by lazy { HostClassLookup.load("android.app.ActivityThread").getMethod("currentApplication") }
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
        private val selection = SoftLightSelection {
            (profile.keepWhite || (packageName == "com.android.systemui" && profile.selected(SoftLightGlassSettings.Scene.CONTROL))) && active()
        }
        val coui = SoftLightCouiPass({ active() && profile.wholeProcess }, { profile }) { logOnce("native COUI blur and tint") }
        private val nativeBlur = SoftLightNativeBlurPass(packageName, ::active, { profile }, ::host, { logOnce(it) })
        fun installNative(loader: ClassLoader) = runCatching { nativeBlur.install(loader) }
            .onFailure { logOnce("native blur adapter unavailable", it) }
        private val shelf = if (packageName == SoftLightShelfPass.PACKAGE)
            SoftLightShelfPass(::active, { profile }, ::logOnce) else null
        fun installShelf(loader: ClassLoader) = runCatching { shelf?.install(loader) }
            .onFailure { logOnce("shelf card adapter unavailable", it) }
        private val launcher = SoftLightLauncherPass(
            { shader -> synchronized(lock) { programs.containsKey(shader) } },
            { effect -> synchronized(lock) { effects[effect] } },
            { effect, shader -> synchronized(lock) { effects[effect] = shader } },
            { profile }, ::active, ::logOnce,
        )
        private val panels = if (packageName in setOf("com.android.systemui","com.coloros.smartsidebar",
            "com.heytap.quicksearchbox","com.oplus.aod")) SoftLightPanelPass(packageName, ::preserveControlWhite, {
                profile.keepWhite && profile.selected(SoftLightGlassSettings.Scene.VOLUME) &&
                    profile[SoftLightGlassSettings.Parameter.STRENGTH] > 0f && active()
            }, { profile.plugin != null }, ::active) { logOnce(it) } else null

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

        // SystemUI hook target. The application UI never invokes this hidden framework method.
        @android.annotation.SuppressLint("BlockedPrivateApi")
        fun installPanelCacheCheck() {
            if (packageName != "com.android.systemui") return
            val method = runCatching { HardwareRenderer::class.java.getDeclaredMethod("setupDiskCache", File::class.java) }
                .getOrNull() ?: return
            install("panel-disk-cache", method, XposedInterface.Hooker { chain ->
                val directory = chain.getArg(0) as? File
                if (directory?.name == "cache" && directory.parentFile?.name == packageName && active() &&
                    profile.selected(SoftLightGlassSettings.Scene.CONTROL)) runCatching {
                    val relative = "$packageName/oplus_precache_configs.json"
                    val override = File("/data/oplus/multimedia/precache", relative)
                    val config = if (override.exists()) override else File("/odm/etc/precache", relative)
                    if (config.isFile && config.length() in 1..65536) {
                        val policy = JSONObject(config.readText())
                        if (policy.optString("name") == packageName && policy.optInt("enable") == 1) {
                            android.os.Trace.beginSection("ONextBox QS pipeline cache")
                            try {
                                if (QsPipelineCache.prepare(directory, policy.optLong("maxValueSize")))
                                    logOnce("retained previous shader cache and requested a fresh Vulkan pipeline cache")
                            } finally {
                                android.os.Trace.endSection()
                            }
                        }
                    }
                }.onFailure { logOnce("panel shader cache check unavailable", it) }
                chain.proceed()
            })
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
            val upgradeExistingDrawable = runCatching { installCachedShaderUpgrade(loader, lockscreen) }
                .onFailure { logOnce("cached shader adapter unavailable", it) }.getOrNull()
            if (packageName == "com.android.systemui") runCatching { selection.installTiles(loader, ::preserveControlWhite, ::logOnce) }
                .onFailure { logOnce("tile selection metadata unavailable", it) }
            if (packageName == "com.android.systemui") runCatching { selection.installDeviceCards(loader, ::preserveControlWhite, ::logOnce) }
                .onFailure { logOnce("device selection metadata unavailable", it) }
            panels?.let { pass -> runCatching { pass.installSources(loader, lockscreen) }.onFailure { logOnce("surface roles", it) } }
            if (packageName == "com.android.launcher") runCatching { launcher.install(loader) }
                .onFailure { logOnce("workspace material adapter unavailable", it) }
            val id = System.identityHashCode(build.declaringClass)
            install("source:$id", build, XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                val buffer = chain.getArg(0) as StringBuilder
                if (!running || host() == null || buffer.isEmpty()) return@Hooker result
                // Keep the host buffer compact. Unhooked consumers can render this source as-is.
                if (!buffer.startsWith(MATERIAL_REQUEST) && !buffer.startsWith(KEYGUARD_REQUEST) &&
                    !SoftLightGlassShader.isMaterial(buffer)) {
                    buffer.insert(0, if (lockscreen) KEYGUARD_REQUEST else MATERIAL_REQUEST)
                }
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
                    val firstDraw = synchronized(lock) { drawables.put(drawable, true) == null }
                    if (systemUi && !active()) return@Hooker chain.proceed()
                    if (firstDraw && adoptExistingShaders && upgradeExistingDrawable != null) {
                        runCatching { upgradeExistingDrawable(drawable) }
                            .onFailure { logOnce("existing panel material refresh", it) }
                    }
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

        private fun installCachedShaderUpgrade(loader: ClassLoader, lockscreen: Boolean): ((Drawable) -> Unit)? {
            val shaderType = ModernReflect.findClassIfExists("com.oplus.posteffect.agsl.DrawableShader", loader) ?: return null
            val getters = shaderType.declaredMethods.filter { it.returnType == RuntimeShader::class.java && it.parameterCount == 0 &&
                it.name in setOf("getShaderOrNull", "getShaderDirect", "s") }
            val forceUpdate = shaderType.declaredMethods.firstOrNull { it.name in setOf("updateShaderBlendParams", "T") &&
                it.parameterTypes.contentEquals(arrayOf(java.lang.Boolean.TYPE)) && it.returnType == Void.TYPE }
            val rebuild = forceUpdate ?: shaderType.declaredMethods.firstOrNull {
                it.name == "rebuildShader" && it.parameterCount == 0 && it.returnType == Void.TYPE
            } ?: return null
            if (getters.isEmpty()) return null
            rebuild.isAccessible = true
            val shaderField = shaderType.declaredFields.singleOrNull { it.type == RuntimeShader::class.java }?.also { it.isAccessible = true } ?: return null
            val panel = systemUi && !lockscreen
            if (panel) {
                val wrap = XposedInterface.Hooker { chain ->
                    val previous = buildingPanel.get()
                    buildingPanel.set(profile.panelProgram)
                    try {
                        chain.proceed().also {
                            chain.thisObject?.let { owner -> synchronized(lock) { panelOwners[owner] = true } }
                        }
                    } finally {
                        if (previous == null) buildingPanel.remove() else buildingPanel.set(previous)
                    }
                }
                val entryPoints = shaderType.declaredMethods.filter {
                    it.name == "rebuildShader" && it.parameterCount == 0 && it.returnType == Void.TYPE
                } + rebuild
                for (entry in shaderType.declaredConstructors.toList() + entryPoints.distinct()) {
                    install("panel-program:${System.identityHashCode(shaderType)}:$entry", entry, wrap)
                }
            }
            val attempted = WeakHashMap<RuntimeShader, Int>()
            fun upgrade(owner: Any, result: RuntimeShader): RuntimeShader {
                val desired = profile.panelProgram
                if (!active()) return result
                if (synchronized(lock) {
                    (result in pluginPrograms && (!panel || panelModes[result] == desired)) ||
                        (!lockscreen && !adoptExistingShaders && result !in programs && result !in nativePrograms) ||
                        (!panel && !profile.needsCurrentShader && result in programs)
                }) return result
                val attempt = if (panel) desired.ordinal else -1
                if (synchronized(attempted) { attempted.put(result, attempt) } == attempt) return result
                return runCatching {
                    if (forceUpdate != null) rebuild.invoke(owner, true) else rebuild.invoke(owner)
                    val next = shaderField.get(owner) as? RuntimeShader ?: return result
                    val rebound = synchronized(lock) {
                        if (next !in pluginPrograms) return result
                        nodes.filterValues { it.program === result }.map { (node, binding) ->
                            node to NodeBinding(next, binding.setter, binding.mode)
                        }
                    }
                    if (panel) {
                        host()?.let { panels?.copyRole(result, next, it) }
                        selection.copyState(result, next)
                    }
                    rebound.forEach { (node, binding) -> rebind(node, binding) }
                    logOnce("cached native material upgraded")
                    next
                }.onFailure { logOnce("cached material refresh", it) }.getOrDefault(result)
            }
            for (getter in getters) {
                val key = if (getter.name == "getShaderOrNull") "keyguard-existing" else "cached-material:${getter.name}"
                install("$key:${System.identityHashCode(shaderType)}", getter, XposedInterface.Hooker { chain ->
                    val result = chain.proceed() as? RuntimeShader ?: return@Hooker null
                    if (!active()) return@Hooker result
                    if (panel && buildingPanel.get() != null) return@Hooker result
                    if ((!hasLegacyPrograms || (!panel && !profile.needsCurrentShader)) && !lockscreen && !adoptExistingShaders)
                        return@Hooker result
                    upgrade(chain.thisObject ?: return@Hooker result, result)
                })
            }
            if (panel) synchronized(lock) {
                cachedMaterialReaders += {
                    val owners = synchronized(lock) { panelOwners.keys.filter(shaderType::isInstance) }
                    for (owner in owners) (shaderField.get(owner) as? RuntimeShader)?.let { upgrade(owner, it) }
                }
            }
            val drawable = ModernReflect.findClassIfExists("com.oplus.posteffect.drawable.BaseDrawable", loader) ?: return null
            val material = drawable.declaredFields.singleOrNull { it.type == shaderType }?.also { it.isAccessible = true } ?: return null
            val refresh: (Drawable) -> Unit = refresh@{ surface ->
                if (!drawable.isInstance(surface)) return@refresh
                val owner = material.get(surface) ?: return@refresh
                val program = shaderField.get(owner) as? RuntimeShader ?: return@refresh
                if (upgrade(owner, program) !== program) surface.invalidateSelf()
            }
            synchronized(lock) {
                cachedMaterialReaders += { surfaces -> surfaces.forEach(refresh) }
            }
            return refresh
        }

        private fun materialMarker(source: String): String? = when {
            source.startsWith(MATERIAL_REQUEST) -> MATERIAL_REQUEST
            source.startsWith(KEYGUARD_REQUEST) -> KEYGUARD_REQUEST
            else -> null
        }

        private fun variant(source: String, marker: String): GlassShaderVariant {
            val key = SourceKey(source, if (marker == MATERIAL_REQUEST) buildingPanel.get() else null)
            return synchronized(sources) {
                sources.get(key) ?: run {
                    val original = source.substring(marker.length)
                    GlassShaderVariant(original, SoftLightGlassShader.extend(original, packageName == "com.android.launcher",
                        panels != null, marker == KEYGUARD_REQUEST, key.mode)).also { sources.put(key, it) }
                }
            }
        }

        private fun rememberNative(shader: RuntimeShader) = synchronized(lock) {
            // Keep the native effect eligible for replacement if glass is enabled in this process.
            nativePrograms[shader] = true
            hasLegacyPrograms = true
        }

        private fun validate(source: String) {
            validating.set(true)
            try { RuntimeShader(source) } finally { validating.remove() }
        }

        fun installFramework(loader: ClassLoader) {
            val ctor = RuntimeShader::class.java.getDeclaredConstructor(String::class.java)
            install("program", ctor, XposedInterface.Hooker { chain ->
                val input = chain.getArg(0) as? String ?: return@Hooker chain.proceed()
                val marker = materialMarker(input)
                val useNative = marker != null && systemUi && !active()
                val source = when {
                    marker == null -> input
                    useNative -> input.substring(marker.length)
                    else -> variant(input, marker).constructorSource(::validate) { logOnce("unsupported material shader", it) }
                }
                val result = if (source === input) chain.proceed() else chain.proceed(arrayOf(source))
                if (useNative) (chain.thisObject as? RuntimeShader)?.let(::rememberNative)
                else if (SoftLightGlassShader.isMaterial(source) && validating.get() != true)
                    (chain.thisObject as? RuntimeShader)?.let { register(it, source) }
                result
            })
            val cache = ModernReflect.findClassIfExists("com.oplus.graphics.OplusRuntimeShader", loader)
            cache?.declaredMethods?.filter {
                it.name == "createRuntimeShader" && it.parameterTypes.firstOrNull() == String::class.java &&
                    it.returnType == RuntimeShader::class.java
            }?.forEach { method ->
                install("cached:${method.toGenericString()}", method, XposedInterface.Hooker { chain ->
                    var source = chain.getArg(0) as? String ?: return@Hooker chain.proceed()
                    val marker = materialMarker(source)
                    if (marker != null && systemUi && !active()) {
                        val args = chain.args.toTypedArray()
                        args[0] = source.substring(marker.length)
                        return@Hooker chain.proceed(args).also { if (it is RuntimeShader) rememberNative(it) }
                    }
                    val result = if (marker == null) chain.proceed() else variant(source, marker).createCached({ candidate ->
                        source = candidate
                        val args = chain.args.toTypedArray()
                        args[0] = candidate
                        chain.proceed(args) as? RuntimeShader
                    }, { logOnce("unsupported cached material shader", it) })
                    if (result is RuntimeShader && SoftLightGlassShader.isMaterial(source)) register(result, source)
                    result
                })
            }
            val factory = RenderEffect::class.java.getMethod("createRuntimeShaderEffect", RuntimeShader::class.java, String::class.java)
            install("bind", factory, XposedInterface.Hooker { chain ->
                val shader = chain.getArg(0) as? RuntimeShader
                if (shader == null || chain.getArg(1) != SoftLightGlassShader.INPUT) return@Hooker chain.proceed()
                val known = synchronized(lock) { shader in programs || shader in nativePrograms }
                if (!known) return@Hooker chain.proceed()
                val result = chain.proceed()
                if (result is RenderEffect) {
                    synchronized(lock) { effects[result] = shader }
                    if (active()) logOnce("native backdrop bound")
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
            val resources = host()?.resources ?: return
            val metrics = resources.displayMetrics
            val p = profile
            val mode = synchronized(lock) { panelModes[shader] }
            shader.setFloatUniform(SoftLightGlassShader.UNIFORM, if (enabled && customizable && (mode == null || mode == p.panelProgram)) metrics.density else 0f,
                metrics.widthPixels.toFloat(), metrics.heightPixels.toFloat())
            if (!customizable) return
            val dock = packageName == "com.android.launcher" && launcher.isDock(shader)
            shader.setFloatUniform("onbScenes", if (dock) p.dockChannels else p.channels)
            shader.setFloatUniform("onbOptics", p[Parameter.STRENGTH], p[Parameter.BLUR], p[Parameter.WIDTH], p[Parameter.SHINE])
            shader.setFloatUniform("onbToneControls", p[Parameter.REFRACTION], p[Parameter.SATURATION], p[Parameter.EXPOSURE])
            shader.setFloatUniform("onbKeepWhite", if (p.keepWhite) 1f else 0f)
            synchronized(lock) {
                if (shader in stylePrograms) {
                    val dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                    val supported = shader in pluginPrograms
                    if (supported) {
                        shader.setFloatUniform("onbLowPower", if (p.lowPower) 1f else 0f)
                        p.plugin?.bind(shader)
                    }
                    val style = if (!supported && p.plugin != null) 0 else p.materialStyle
                    shader.setFloatUniform(SoftLightGlassShader.STYLE_UNIFORM, style.toFloat(),
                        if (dark) 18f/255f else 250f/255f)
                    if (enabled && supported && p.plugin != null) logOnce("LiquidGlass optical material configured")
                }
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
                PanelProgram.inSource(source)?.let {
                    panelModes[shader] = it
                    if (enabled) logOnce("panel shader: ${it.name}")
                }
                val customizable = "uniform float onbKeepWhite;" in source
                if (SoftLightGlassShader.STYLE_DECLARATION in source) stylePrograms[shader] = true
                if (LiquidGlassShader.DECLARATION in source) pluginPrograms[shader] = true else hasLegacyPrograms = true
                if ("uniform float onbDimming;" in source) dimmable[shader] = true
                if ("uniform float2 onbLampOffset;" in source) directional[shader] = true
                if ("uniform float onbAodScene;" in source) aodPrograms[shader] = true
                configure(shader, enabled, customizable)
                if (customizable) shader.setFloatUniform("onbSelectionState", selection.currentControlState().toFloat())
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
            val previousMode = profile.panelProgram
            profile = SoftLightGlassSettings.Profile(packageName)
            shelf?.let { it.refresh(); return }
            followTilt = enabled && profile.gyro && profile.anyScene &&
                profile[Parameter.RESPONSE] > 0f && profile[Parameter.SHINE] > 0f &&
                profile[Parameter.WIDTH] > 0f && profile[Parameter.STRENGTH] > 0f
            if (!followTilt) tilt.stop()
            if (systemUi && previousMode != profile.panelProgram) hasLegacyPrograms = true
            if (enabled && (hasLegacyPrograms || adoptExistingShaders) && (systemUi || profile.needsCurrentShader)) {
                val (readers, surfaces) = synchronized(lock) { cachedMaterialReaders.toList() to drawables.keys.toList() }
                readers.forEach { reader -> runCatching { reader(surfaces) }.onFailure { logOnce("cached drawable refresh", it) } }
                hasLegacyPrograms = synchronized(lock) {
                    nativePrograms.isNotEmpty() || programs.keys.any { it !in pluginPrograms } ||
                        panelModes.values.any { it != profile.panelProgram }
                }
            }
            val shaders: List<Pair<RuntimeShader, Boolean>>
            val bound: List<Pair<RenderNode, NodeBinding>>
            val surfaces: List<Drawable>
            synchronized(lock) {
                shaders = programs.map { it.key to it.value }
                bound = nodes.mapNotNull { (node, binding) -> if (binding.program in programs) node to binding else null }
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
            selection.refresh()
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
                directional.keys.toTypedArray(), dimmable.keys.toTypedArray(), aodPrograms.keys.toTypedArray(), stylePrograms.keys.toTypedArray(),
                arrayOf(LiquidGlassShader.DECLARATION, pluginPrograms.keys.toTypedArray()),
                panelModes.map { (shader, mode) -> arrayOf(shader, mode.ordinal) }.toTypedArray(), panelOwners.keys.toTypedArray(),
                nativePrograms.keys.toTypedArray(), adoptExistingShaders)
        }

        fun detach() {
            running = false
            preferences?.unregisterOnSharedPreferenceChangeListener(listener)
            preferences = null
            main.removeCallbacks(refresh)
            // Keep the launcher's own snapshots addressable by the incoming generation.
            refreshMaterials(rebindWorkspace = false)
            shelf?.detach()
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
                (parts.getOrNull(10) as? Array<*>)?.filterIsInstance<RuntimeShader>()?.forEach { stylePrograms[it] = true }
                val savedLens = parts.getOrNull(11) as? Array<*>
                if (savedLens?.getOrNull(0) == LiquidGlassShader.DECLARATION) {
                    (savedLens.getOrNull(1) as? Array<*>)?.filterIsInstance<RuntimeShader>()?.forEach { pluginPrograms[it] = true }
                }
                (parts[0] as? Array<*>)?.filterIsInstance<RuntimeShader>()?.forEach { programs[it] = it in customizable }
                (parts.getOrNull(12) as? Array<*>)?.filterIsInstance<Array<*>>()?.forEach {
                    panelModes[it[0] as RuntimeShader] = PanelProgram.entries[it[1] as Int]
                }
                (parts.getOrNull(13) as? Array<*>)?.filterNotNull()?.forEach { panelOwners[it] = true }
                (parts.getOrNull(14) as? Array<*>)?.filterIsInstance<RuntimeShader>()?.forEach { nativePrograms[it] = true }
                hasLegacyPrograms = nativePrograms.isNotEmpty() || programs.keys.any { it !in pluginPrograms } ||
                    (systemUi && (parts.size <= 12 || panelModes.values.any { it != profile.panelProgram }))
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
