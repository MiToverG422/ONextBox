package com.mi.onextbox.lsp

import android.app.Application
import android.content.res.Resources
import com.mi.onextbox.BuildConfig
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile
import org.json.JSONObject

internal object GalleryWatermarkCatalogHooker {
    fun targets(loader: ClassLoader): List<Method> {
        val type = loader.loadClass(GalleryWatermarkCatalogRules.OWNER)
        return GalleryWatermarkCatalogRules.methods.map { (name, count) ->
            type.getDeclaredMethod(name, *Array(count) { Boolean::class.javaPrimitiveType!! }).also {
                check(it.returnType == List::class.java)
            }
        }
    }

    fun visibleTarget(loader: ClassLoader): Method = loader.loadClass(GalleryWatermarkCatalogRules.VIEW_MODEL)
        .declaredMethods.single { method ->
            method.parameterTypes.toList() == listOf(ArrayList::class.java, IntArray::class.java, Int::class.javaPrimitiveType) &&
                method.returnType == ArrayList::class.java && !Modifier.isStatic(method.modifiers)
        }

    fun preview(resources: Resources, packageName: String, id: String, camera: Boolean, landscape: Boolean): Int {
        val name = GalleryWatermarkCatalogRules.previewName(id, camera, landscape) ?: return 0
        val resource = resources.getIdentifier(name, "drawable", packageName)
        // Some OEM builds keep resource names as empty placeholders.
        return resource.takeIf { it != 0 && runCatching {
            val value = android.util.TypedValue()
            resources.getValue(it, value, true)
            value.string?.toString()?.startsWith("res/") == true
        }.getOrDefault(false) } ?: 0
    }

    fun available(path: String): Set<String> = ZipFile(path).use { apk ->
        GalleryWatermarkCatalogRules.options.flatMap { it.styles }.distinct().filterTo(linkedSetOf()) { id ->
            val entry = apk.getEntry("assets/${GalleryWatermarkCatalogRules.ASSET_ROOT}$id.json")
            entry != null && entry.size in 1..1_048_576 && runCatching {
                apk.getInputStream(entry).bufferedReader().use { JSONObject(it.readText()).optString("styleId") == id }
            }.getOrDefault(false)
        }
    }

    fun hook(loader: ClassLoader, path: String) {
        if (GalleryWatermarkCatalogRules.keys.none(MediaUnlockSettings::enabledXposed)) return
        val tag = "ONextBox-WatermarkPacks"
        val prefix = "$tag@${System.identityHashCode(loader)}:"
        runCatching {
            val methods = targets(loader)
            val visible = visibleTarget(loader)
            val available = available(path)
            val pair = loader.loadClass("kotlin.Pair")
            val constructor = pair.getConstructor(Any::class.java, Any::class.java)
            val first = pair.getMethod("getFirst")
            val reached = ConcurrentHashMap.newKeySet<String>()
            val failed = AtomicBoolean()
            methods.forEach { method ->
                ModernHookRegistry.installFast("$prefix${method.name}", method, XposedInterface.Hooker { chain ->
                    val original = chain.proceed()
                    if (original !is List<*>) return@Hooker original
                    runCatching {
                        val selected = GalleryWatermarkCatalogRules.selected(method.name, available, MediaUnlockSettings::enabledXposed)
                        val result = GalleryWatermarkCatalogRules.merge(original, selected,
                            id = { if (pair.isInstance(it)) first.invoke(it) as? String else null },
                            create = { constructor.newInstance(it, "${GalleryWatermarkCatalogRules.ASSET_ROOT}$it.json") })
                        if (result !== original && reached.add(method.name))
                            HookLog.i(tag, "Native template factory ${method.name}: ${original.size} -> ${result.size}")
                        result
                    }.getOrElse {
                        if (failed.compareAndSet(false, true)) HookLog.w(tag, "Native watermark catalog retained after incompatible data", it)
                        original
                    }
                })
            }
            val metadata = HostDexMetadata.methods(loader, path, "watermark-pack-callers", "native-visible-style-v2") { dex ->
                val data = checkNotNull(dex.getClassData(GalleryWatermarkCatalogRules.OWNER))
                val vm = checkNotNull(dex.getClassData(GalleryWatermarkCatalogRules.VIEW_MODEL))
                val builder = vm.methods.single { method ->
                    method.isMethod && runCatching { method.getMethodInstance(loader) == visible }.getOrDefault(false)
                }
                val cameraContext = builder.invokes.filter { it.isMethod }.mapNotNull {
                    runCatching { it.getMethodInstance(loader) }.getOrNull()
                }.distinct().single { it.declaringClass == visible.declaringClass && it.parameterCount == 0 &&
                    it.returnType == Boolean::class.javaPrimitiveType }
                mapOf("context" to arrayOf(cameraContext), "callers" to
                    (data.methods.filter { it.name in GalleryWatermarkCatalogRules.methods }.flatMap { it.callers } + builder.callers)
                    .filter { it.isMethod }.mapNotNull {
                        runCatching { it.getMethodInstance(loader) }.getOrNull()
                    }.distinct().toTypedArray())
            }
            val application = generateSequence(visible.declaringClass as Class<*>?) { it.superclass }
                .flatMap { it.declaredFields.asSequence() }.single {
                    it.type == Application::class.java && !Modifier.isStatic(it.modifiers)
                }.apply { isAccessible = true }
            val cameraContext = metadata.getValue("context").single().apply { isAccessible = true }
            val previews = ConcurrentHashMap<String, Int>()
            ModernHookRegistry.installFast("${prefix}visible", visible, XposedInterface.Hooker { chain ->
                val ids = (chain.getArg(0) as? ArrayList<*>)?.filterIsInstance<String>()
                val original = chain.getArg(1) as? IntArray
                val arguments = if (ids != null && original != null && ids.size > original.size) runCatching {
                    val app = application.get(chain.thisObject) as Application
                    val camera = cameraContext.invoke(chain.thisObject) == true
                    val landscape = !camera && original.firstOrNull()?.let {
                        app.resources.getResourceEntryName(it).contains("_land")
                    } == true
                    val completed = GalleryWatermarkCatalogRules.completeChoices(ids, original) { id ->
                        previews.getOrPut("$camera:$landscape:$id") { preview(app.resources, app.packageName, id, camera, landscape) }
                    }
                    if (completed.ids.size < ids.size) {
                        if (reached.add("missing:$camera:$landscape"))
                            HookLog.w(tag, "Omitted ${ids.size - completed.ids.size} extra native templates with missing previews")
                    }
                    chain.args.toTypedArray().apply {
                        this[0] = ArrayList(completed.ids)
                        this[1] = completed.previews
                    }.also {
                        if (reached.add("visible:$camera:$landscape:${completed.ids.size}"))
                            HookLog.i(tag, "Native visible watermark choices: ${original.size} -> ${completed.ids.size}; camera=$camera; previews=${completed.previews.size}")
                    }
                }.getOrElse {
                    if (failed.compareAndSet(false, true)) HookLog.w(tag, "Native chooser retained after incompatible thumbnail data", it)
                    null
                } else null
                if (arguments != null) chain.proceed(arguments) else chain.proceed()
            })
            if (BuildConfig.DEBUG) traceAdapter(loader, prefix, tag, reached)
            metadata.getValue("callers").forEach { runCatching { ModernHookRuntime.requireModule().deoptimize(it) }
                .onFailure { error -> HookLog.w(tag, "Native style caller deoptimization unavailable", error) } }
            HotReloadLifecycle.register("${prefix}targets", capture = { (methods + visible).toTypedArray() })
            HookLog.i(tag, "Native watermark factory and visible chooser hooks installed; available templates=${available.size}")
        }.onFailure {
            ModernHookRegistry.unhookPrefix(prefix)
            HookLog.w(tag, "Unsupported watermark catalog; stock templates retained", it)
        }
    }

    /** Temporary debug-only evidence at the final native adapter, never photo contents. */
    private fun traceAdapter(loader: ClassLoader, prefix: String, tag: String, reached: MutableSet<String>) {
        val adapter = loader.loadClass("com.oplus.gallery.photoeditor.editingvvm.watermarkmaster.adapter.WatermarkMasterStyleAdapter")
        val row = loader.loadClass("com.oplus.aiunit.vision.byg0")
        val styleId = row.getDeclaredField("a").apply { isAccessible = true }
        adapter.declaredConstructors.filter { it.parameterTypes.count { t -> t == List::class.java } == 1 }.forEach { constructor ->
            val index = constructor.parameterTypes.indexOf(List::class.java)
            ModernHookRegistry.installFast("${prefix}adapter:${constructor.parameterCount}", constructor, XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                runCatching {
                    val ids = (chain.getArg(index) as? List<*>)?.mapNotNull {
                        if (row.isInstance(it)) styleId.get(it) as? String else null
                    }.orEmpty()
                    val extras = ids.filter { it.startsWith("personalize_film_realme_") }
                    if (extras.isNotEmpty() && reached.add("adapter:${ids.joinToString()}"))
                        HookLog.i(tag, "Native adapter received ${ids.size} choices; added IDs=${extras.joinToString()}")
                }
                result
            })
        }
    }
}
