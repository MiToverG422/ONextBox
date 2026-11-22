package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Field
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean
import org.luckypray.dexkit.DexKitBridge

internal object CameraXpanFilterHooker {
    fun targets(dex: DexKitBridge, loader: ClassLoader): Map<String, List<Member>> {
        val getter = dex.findMethod { matcher {
            usingStrings("XPanPresenter", "getCurrentFilterType, index out of range")
            paramTypes(); returnType = String::class.java.name
        } }.single()
        val key = getter.usingFields.map { it.field.getFieldInstance(loader) }.distinct()
            .single { it.type.name == "com.oplus.camera.data.DataKey" }.apply { isAccessible = true }
        val setter = checkNotNull(dex.getClassData(getter.className)).methods.single { method ->
            method.isMethod && method.paramTypeNames == listOf(String::class.java.name) && method.returnTypeName == "void" &&
                method.usingFields.any { it.field.getFieldInstance(loader) == key } && method.invokes.any { it.name == "indexOf" }
        }
        val reader = getter.invokes.single { it.isMethod && it.className == "com.oplus.camera.data.DataManager" && it.paramCount == 2 }
            .getMethodInstance(loader)
        val writer = setter.invokes.single { it.isMethod && it.className == reader.declaringClass.name && it.paramCount == 3 }
            .getMethodInstance(loader)
        val instance = reader.declaringClass.getDeclaredMethod("getInstance")
        val callers = (getter.callers + setter.callers).filter { it.isMethod }
            .mapNotNull { runCatching { it.getMethodInstance(loader) }.getOrNull() }.distinct()
        val output = mutableMapOf<String, List<Member>>("getter" to listOf(getter.getMethodInstance(loader)),
            "setter" to listOf(setter.getMethodInstance(loader)), "key" to listOf(key),
            "read" to listOf(reader), "write" to listOf(writer), "instance" to listOf(instance), "callers" to callers)
        val panel = dex.getClassData(getter.className)?.methods?.singleOrNull { it.paramCount == 0 &&
            it.returnTypeName == ArrayList::class.java.name && it.usingFields.map { f -> f.field.name }
                .containsAll(listOf("mBackTypeList", "mBackNameIdList")) && it.invokes.any { call -> call.name == "getString" } }
        if (panel != null) output["panel"] = listOf(panel.getMethodInstance(loader))
        val capture = dex.getClassData(getter.className)?.methods?.singleOrNull {
            it.returnTypeName == "boolean" && it.paramCount == 1 && "aps_algo_filter" in it.usingStrings &&
                it.invokes.any { call -> call.name == "getApsVersion" }
        }
        if (capture != null) runCatching {
            val flag = capture.usingFields.map { it.field.getFieldInstance(loader) }.distinct()
                .single { it.declaringClass == getter.getMethodInstance(loader).declaringClass && it.type == Array<String>::class.java }
            val version = capture.invokes.single { it.name == "getApsVersion" }.getMethodInstance(loader)
            val none = capture.usingFields.map { it.field.getFieldInstance(loader) }.distinct().single { it.name == "sNoneIndex" }
            output["capture"] = listOf(capture.getMethodInstance(loader), flag, version, none)
            output["callers"] = callers + capture.callers.filter { it.isMethod }
                .mapNotNull { runCatching { it.getMethodInstance(loader) }.getOrNull() }
        }
        runCatching {
            val legacy = dex.findMethod { matcher {
                usingStrings(CameraXpanFilterRules.LEGACY_MONO,
                    "getCameraSwitcherButtonClickEvent isAllowSwitchCamera, false, blurAnimation is running")
                paramTypes(); returnType = "void"
            } }.single()
            val receiver = legacy.usingFields.map { it.field.getFieldInstance(loader) }.distinct()
                .single { it.declaringClass.name == legacy.className && it.type == Any::class.java }
            val legacyWriter = legacy.invokes.filter { it.isMethod && it.className == reader.declaringClass.name &&
                it.paramTypeNames == writer.parameterTypes.take(2).map(Class<*>::getName) && it.returnTypeName == "void" }
                .map { it.getMethodInstance(loader) }.distinct().single()
            val preview = legacy.invokes.filter { it.isMethod && it.name == "setCurrFilterType" &&
                it.paramTypeNames == listOf(String::class.java.name) }.map { it.getMethodInstance(loader) }.distinct().single()
            output["legacy"] = listOf(legacy.getMethodInstance(loader))
            output["receiver"] = listOf(receiver)
            output["legacyWrite"] = listOf(legacyWriter)
            output["preview"] = listOf(preview)
            output["callers"] = output.getValue("callers") + legacy.getMethodInstance(loader)
        }
        return output
    }

    fun hook(loader: ClassLoader, path: String) {
        val tag = "ONextBox-XpanFilter"
        val prefix = "$tag@${System.identityHashCode(loader)}:"
        runCatching {
            val metadata = HostDexMetadata.members(loader, path, "camera-xpan-filter", "capture-eligibility-v4") { targets(it, loader) }
            fun method(name: String) = (metadata.getValue(name).single() as Method).apply { isAccessible = true }
            val getter = method("getter"); val setter = method("setter")
            val reader = method("read"); val writer = method("write"); val instance = method("instance")
            val keyField = (metadata.getValue("key").single() as Field).apply { isAccessible = true }
            val group = loader.loadClass("com.oplus.camera.filter.FilterGroupManager").getDeclaredField("sHasselbladXpanFilterGroup")
            val types = group.type.getDeclaredField("mBackTypeList")
            val names = group.type.getDeclaredField("mBackNameIdList")
            fun available(): List<String> {
                val current = group.get(null) ?: return emptyList()
                val entries = types.get(current) as? List<*> ?: return emptyList()
                return if (CameraXpanFilterRules.hasValidEntries(entries, names.get(current) as? List<*>))
                    entries.filterIsInstance<String>() else emptyList()
            }
            if (metadata.containsKey("panel")) ModernHookRegistry.installFast("${prefix}panel", method("panel"), XposedInterface.Hooker { chain ->
                if (available().isEmpty()) return@Hooker ArrayList<Any>()
                try { chain.proceed() } catch (_: android.content.res.Resources.NotFoundException) { ArrayList<Any>() }
            })
            val repaired = AtomicBoolean()
            val mapped = AtomicBoolean()
            fun mappedLegacy() {
                if (mapped.compareAndSet(false, true)) HookLog.i(tag, "Mapped an unavailable legacy XPAN filter to a native supported filter")
            }
            val capture = metadata["capture"]
            val flags = (capture?.get(1) as? Field)?.apply { isAccessible = true }
            val version = (capture?.get(2) as? Method)?.apply { isAccessible = true }
            val noneField = capture?.get(3) as? Field
            fun allowsFilter(presenter: Any?, capturing: Boolean = false): Boolean {
                if (presenter == null || flags == null || version == null) return true
                return CameraXpanFilterRules.canApply(flags.get(presenter) as? Array<*>, (version.invoke(null) as Number).toInt(), capturing)
            }
            val legacyOwner = ThreadLocal<Any>()
            if (metadata.containsKey("legacy")) {
                val receiver = (metadata.getValue("receiver").single() as Field).apply { isAccessible = true }
                ModernHookRegistry.installFast("${prefix}legacy", method("legacy"), XposedInterface.Hooker { chain ->
                    if (!getter.declaringClass.isInstance(receiver.get(chain.thisObject))) return@Hooker chain.proceed()
                    val previousOwner = legacyOwner.get()
                    legacyOwner.set(receiver.get(chain.thisObject))
                    try { chain.proceed() } finally {
                        if (previousOwner == null) legacyOwner.remove() else legacyOwner.set(previousOwner)
                    }
                })
                ModernHookRegistry.installFast("${prefix}legacy-preview", method("preview"), XposedInterface.Hooker { chain ->
                    if (legacyOwner.get() == null || chain.getArg(0) != CameraXpanFilterRules.LEGACY_MONO) return@Hooker chain.proceed()
                    val list = available()
                    val selected = if (allowsFilter(legacyOwner.get())) CameraXpanFilterRules.resolve(CameraXpanFilterRules.LEGACY_MONO, list) else "default"
                    if (selected == CameraXpanFilterRules.LEGACY_MONO) chain.proceed() else {
                        mappedLegacy()
                        chain.proceed(chain.args.toTypedArray().apply { this[0] = selected })
                    }
                })
                ModernHookRegistry.installFast("${prefix}legacy-write", method("legacyWrite"), XposedInterface.Hooker { chain ->
                    if (legacyOwner.get() == null || chain.getArg(0) != keyField.get(null)) return@Hooker chain.proceed()
                    val index = (chain.getArg(1) as? Number)?.toInt() ?: return@Hooker chain.proceed()
                    val list = available()
                    if ((list.isEmpty() || !allowsFilter(legacyOwner.get())) && noneField != null) return@Hooker chain.proceed(chain.args.toTypedArray().apply { this[1] = noneField.getInt(null) })
                    if (index in list.indices || list.isEmpty()) return@Hooker chain.proceed()
                    val selected = CameraXpanFilterRules.selected(index, list, true)
                    val corrected = list.indexOf(selected)
                    if (corrected < 0) chain.proceed() else {
                        mappedLegacy()
                        chain.proceed(chain.args.toTypedArray().apply { this[1] = corrected })
                    }
                })
            }
            if (capture != null && noneField != null) {
                ModernHookRegistry.installFast("${prefix}capture", capture[0] as Method, XposedInterface.Hooker { chain ->
                    if (!allowsFilter(chain.thisObject, capturing = true) || available().isEmpty()) {
                        val manager = instance.invoke(null)
                        val key = keyField.get(null)
                        val none = noneField.getInt(null)
                        if (reader.invoke(manager, key, none) != none) writer.invoke(manager, key, none, false)
                    }
                    chain.proceed()
                })
            }
            ModernHookRegistry.installFast("${prefix}get", getter, XposedInterface.Hooker { chain ->
                if (!allowsFilter(chain.thisObject)) return@Hooker "default"
                val list = available()
                val manager = instance.invoke(null)
                val key = keyField.get(null)
                val index = (reader.invoke(manager, key, 0) as? Number)?.toInt() ?: -1
                val selected = CameraXpanFilterRules.selected(index, list, MediaUnlockSettings.enabledXposed("camera_unlock_xpan_legacy"))
                if (index !in list.indices && list.isNotEmpty()) {
                    val corrected = list.indexOf(selected)
                    if (corrected >= 0) writer.invoke(manager, key, corrected, false)
                    if (repaired.compareAndSet(false, true)) HookLog.i(tag, "Repaired an invalid native XPAN filter selection")
                }
                selected
            })
            ModernHookRegistry.installFast("${prefix}set", setter, XposedInterface.Hooker { chain ->
                val list = available()
                if (list.isEmpty()) return@Hooker null
                val requested = chain.getArg(0) as? String
                val selected = if (allowsFilter(chain.thisObject)) CameraXpanFilterRules.resolve(requested, list) else "default"
                if (selected !in list && noneField != null) {
                    writer.invoke(instance.invoke(null), keyField.get(null), noneField.getInt(null), false)
                    return@Hooker null
                }
                if (selected == requested) chain.proceed() else {
                    mappedLegacy()
                    val args = chain.args.toTypedArray().apply { this[0] = selected }
                    chain.proceed(args)
                }
            })
            metadata.getValue("callers").filterIsInstance<Method>().forEach { ModernHookRuntime.requireModule().deoptimize(it) }
            HotReloadLifecycle.register("${prefix}targets", capture = { metadata })
        }.onFailure {
            ModernHookRegistry.unhookPrefix(prefix)
            HookLog.w(tag, "Unsupported XPAN selection data; native behavior retained", it)
        }
    }
}
