package com.mi.onextbox.lsp

import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object MediaUnlockTargets {
    const val CAMERA_CONFIG = "com.oplus.camera.configure.CameraConfig"
    const val SCHEMA = "media-eligibility-v5"
    private const val GALLERY_FUNCTIONS = "com.oplus.gallery.basebiz.helper.function.FunctionSwitchManager"

    /** Only boolean readers; never existence checks, numeric values or native configuration writes. */
    fun cameraReader(method: Method): Boolean {
        if (method.declaringClass.name != CAMERA_CONFIG || !Modifier.isStatic(method.modifiers) ||
            Modifier.isNative(method.modifiers) || method.returnType != Boolean::class.javaPrimitiveType) return false
        val args = method.parameterTypes.toList()
        return (method.name == "getConfigBooleanValue" && args == listOf(String::class.java)) ||
            (method.name == "c" && args == listOf(String::class.java, Boolean::class.javaPrimitiveType)) ||
            (method.name == "d" && args == listOf(String::class.java, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType))
    }

    fun camera(dex: DexKitBridge, loader: ClassLoader): Map<String, Array<Method>> {
        val type = loader.loadClass(CAMERA_CONFIG)
        val data = checkNotNull(dex.getClassData(CAMERA_CONFIG))
        val valueReaders = data.methods.filter { method ->
            method.isMethod && method.returnTypeName == "boolean" &&
                method.paramTypeNames == listOf(String::class.java.name, "boolean", "boolean") &&
                method.invokes.any { it.className == "java.lang.Boolean" && it.name == "parseBoolean" }
        }
        val delegates = valueReaders.flatMap { it.callers }.filter { method ->
            method.isMethod && method.className == CAMERA_CONFIG && method.returnTypeName == "boolean" &&
                method.paramTypeNames == listOf(String::class.java.name, "boolean")
        }
        val readers = (type.declaredMethods.filter { it.name == "getConfigBooleanValue" && cameraReader(it) } +
            (valueReaders + delegates).mapNotNull { runCatching { it.getMethodInstance(loader) }.getOrNull() }
                .filter { Modifier.isStatic(it.modifiers) && !Modifier.isNative(it.modifiers) }).distinct()
        check(readers.any { it.name == "getConfigBooleanValue" }) { "No supported CameraConfig reader" }
        // New GR/Vibe/filter gates call the default-value reader directly.
        val keys = MediaUnlockCatalog.gatesByApp.getValue(MediaUnlockCatalog.App.Camera).keys
        val callers = data.methods.filter { it.isMethod && it.name in readers.map(Method::getName) }
            .flatMap { it.callers }.distinctBy { it.toString() }
            .filter { it.isMethod && it.usingStrings.any(keys::contains) }
        return callerGroups(callers, keys, loader) + mapOf(SCHEMA to emptyArray(), "readers" to readers.toTypedArray())
    }

    fun gallery(dex: DexKitBridge, loader: ClassLoader): Map<String, Array<Method>> {
        val config = dex.findMethod {
            matcher { declaredClass = GalleryAiRetouchRules.STORAGE; usingStrings(GalleryAiRetouchRules.CONFIG_KEY) }
        }.filter { it.isMethod }.mapNotNull { data ->
            runCatching { data.getMethodInstance(loader) }.getOrNull()
                ?.takeIf(GalleryAiRetouchRules::configMethod)?.let { data to it }
        }.single()
        val getter = config.first.invokes.filter { it.isMethod }.mapNotNull {
            runCatching { it.getMethodInstance(loader) }.getOrNull()
        }.filter { GalleryAiRetouchRules.keyGetter(it, config.second.parameterTypes[0]) }.distinct().single()
        getter.isAccessible = true
        val callers = config.first.callers.filter { it.isMethod }.mapNotNull {
            runCatching { it.getMethodInstance(loader) }.getOrNull()
        }.distinct()
        // Some features (notably AI matting) use the common string-key facade rather
        // than OtherSystemStorage. Resolve its real boolean call from a known client.
        val facade = dex.findMethod {
            matcher { usingStrings("feature_is_support_ai_matting"); paramTypes(); returnType = "java.lang.Object" }
        }.filter { it.isMethod }.flatMap { it.invokes }.filter { it.isMethod }.mapNotNull { data ->
            runCatching { data.getMethodInstance(loader) }.getOrNull()
                ?.takeIf(::galleryKeyReader)?.let { data to it }
        }.distinctBy { it.second }.single()
        // Watermark pages also call the underlying (String, boolean, boolean)
        // reader directly. Hooking only Kotlin's default-argument wrapper misses
        // those reads, including the visible inspiration/film category path.
        val facadeReaders = (listOf(facade) + facade.first.invokes.filter { it.isMethod }.mapNotNull { data ->
            runCatching { data.getMethodInstance(loader) }.getOrNull()
                ?.takeIf { it.declaringClass == facade.second.declaringClass && galleryKeyReader(it) }?.let { data to it }
        }).distinctBy { it.second }
        val keys = MediaUnlockCatalog.gatesByApp.getValue(MediaUnlockCatalog.App.Gallery).keys
        val facadeCallers = facadeReaders.flatMap { it.first.callers }
            .filter { it.isMethod && it.usingStrings.any(keys::contains) }.distinctBy { it.toString() }
        val functionType = runCatching { loader.loadClass(GALLERY_FUNCTIONS) }.getOrNull()
        val functionReaders = functionType?.declaredMethods.orEmpty().filter {
            Modifier.isStatic(it.modifiers) && it.returnType == Boolean::class.javaPrimitiveType &&
                it.parameterTypes.contentEquals(arrayOf(String::class.java))
        }.takeIf { it.size == 1 }.orEmpty()
        val functionCallers = if (functionReaders.isEmpty()) emptyList() else dex.getClassData(GALLERY_FUNCTIONS)
            ?.methods.orEmpty().filter { it.isMethod && it.name == functionReaders.single().name }
            .flatMap { it.callers }.filter { it.isMethod && it.usingStrings.any(keys::contains) }
        val groups = callerGroups(facadeCallers + functionCallers, keys, loader)
        return groups + mapOf(SCHEMA to emptyArray(), "dispatcher" to arrayOf(config.second),
            "key" to arrayOf(getter), "stringReaders" to facadeReaders.map { it.second }.toTypedArray(),
            "functionReaders" to functionReaders.toTypedArray(),
            "commonCallers" to callers.toTypedArray(),
            "callers" to (callers + groups.getValue("callers").toList()).distinct().toTypedArray())
    }

    private fun callerGroups(data: Collection<org.luckypray.dexkit.result.MethodData>, keys: Set<String>, loader: ClassLoader): Map<String, Array<Method>> {
        val groups = linkedMapOf<String, MutableList<Method>>()
        data.forEach { target ->
            val method = runCatching { target.getMethodInstance(loader) }.getOrNull() ?: return@forEach
            target.usingStrings.filter(keys::contains).forEach { key -> groups.getOrPut("gate:$key") { mutableListOf() }.add(method) }
        }
        return groups.mapValues { it.value.distinct().toTypedArray() } +
            ("callers" to groups.values.flatten().distinct().toTypedArray())
    }

    /** Do not deoptimize render or editing paths belonging only to disabled packs. */
    fun callersFor(targets: Map<String, Array<Method>>, app: MediaUnlockCatalog.App, enabled: (String) -> Boolean): List<Method> =
        (targets["commonCallers"].orEmpty().toList() + MediaUnlockCatalog.byApp.getValue(app)
            .filter { enabled(it.key) }.flatMap { it.overrides.keys }
            .flatMap { targets["gate:$it"].orEmpty().toList() } +
            if (app == MediaUnlockCatalog.App.Camera && MediaUnlockCatalog.sharedStyleOptions.any { enabled(it.key) })
                targets["gate:${MediaUnlockCatalog.CAMERA_MASTER_GATE}"].orEmpty().toList() else emptyList()).distinct()

    fun galleryKeyReader(method: Method): Boolean {
        if (!Modifier.isStatic(method.modifiers) || Modifier.isNative(method.modifiers) ||
            method.returnType != Boolean::class.javaPrimitiveType) return false
        val args = method.parameterTypes.toList()
        // In the same facade (String) -> boolean is contains(), not a value read.
        return args == listOf(Int::class.javaPrimitiveType, String::class.java, Boolean::class.javaPrimitiveType) ||
            args == listOf(String::class.java, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
    }
}
