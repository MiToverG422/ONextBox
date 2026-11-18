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
        val callers = (getter.callers + setter.callers).filter { it.isMethod }
            .mapNotNull { runCatching { it.getMethodInstance(loader) }.getOrNull() }
            .plus(legacy.getMethodInstance(loader)).distinct()
        return mapOf("getter" to listOf(getter.getMethodInstance(loader)), "setter" to listOf(setter.getMethodInstance(loader)),
            "key" to listOf(key), "read" to listOf(reader), "write" to listOf(writer), "instance" to listOf(instance),
            "legacy" to listOf(legacy.getMethodInstance(loader)), "receiver" to listOf(receiver),
            "legacyWrite" to listOf(legacyWriter), "preview" to listOf(preview), "callers" to callers)
    }

    fun hook(loader: ClassLoader, path: String) {
        val tag = "ONextBox-XpanFilter"
        val prefix = "$tag@${System.identityHashCode(loader)}:"
        runCatching {
            val metadata = HostDexMetadata.members(loader, path, "camera-xpan-filter", "legacy-selection-v2") { targets(it, loader) }
            fun method(name: String) = (metadata.getValue(name).single() as Method).apply { isAccessible = true }
            val getter = method("getter"); val setter = method("setter")
            val reader = method("read"); val writer = method("write"); val instance = method("instance")
            val keyField = (metadata.getValue("key").single() as Field).apply { isAccessible = true }
            val receiver = (metadata.getValue("receiver").single() as Field).apply { isAccessible = true }
            val group = loader.loadClass("com.oplus.camera.filter.FilterGroupManager").getDeclaredField("sHasselbladXpanFilterGroup")
            val types = group.type.getDeclaredField("mBackTypeList")
            fun available(): List<String> = (types.get(group.get(null)) as? List<*>)?.filterIsInstance<String>().orEmpty()
            val repaired = AtomicBoolean()
            val mapped = AtomicBoolean()
            val legacyScope = ThreadLocal<Boolean>()
            fun mappedLegacy() {
                if (mapped.compareAndSet(false, true)) HookLog.i(tag, "Mapped an unavailable legacy XPAN filter to a native supported filter")
            }
            ModernHookRegistry.installFast("${prefix}legacy", method("legacy"), XposedInterface.Hooker { chain ->
                if (!getter.declaringClass.isInstance(receiver.get(chain.thisObject))) return@Hooker chain.proceed()
                val previous = legacyScope.get()
                legacyScope.set(true)
                try { chain.proceed() } finally {
                    if (previous == null) legacyScope.remove() else legacyScope.set(previous)
                }
            })
            ModernHookRegistry.installFast("${prefix}legacy-preview", method("preview"), XposedInterface.Hooker { chain ->
                if (legacyScope.get() != true || chain.getArg(0) != CameraXpanFilterRules.LEGACY_MONO) return@Hooker chain.proceed()
                val list = available()
                if (list.isEmpty()) return@Hooker chain.proceed()
                val selected = CameraXpanFilterRules.resolve(CameraXpanFilterRules.LEGACY_MONO, list)
                if (selected == CameraXpanFilterRules.LEGACY_MONO) chain.proceed() else {
                    mappedLegacy()
                    chain.proceed(chain.args.toTypedArray().apply { this[0] = selected })
                }
            })
            ModernHookRegistry.installFast("${prefix}legacy-write", method("legacyWrite"), XposedInterface.Hooker { chain ->
                if (legacyScope.get() != true || chain.getArg(0) != keyField.get(null)) return@Hooker chain.proceed()
                val index = (chain.getArg(1) as? Number)?.toInt() ?: return@Hooker chain.proceed()
                val list = available()
                if (index in list.indices || list.isEmpty()) return@Hooker chain.proceed()
                val selected = CameraXpanFilterRules.selected(index, list, true)
                val corrected = list.indexOf(selected)
                if (corrected < 0) chain.proceed() else {
                    mappedLegacy()
                    chain.proceed(chain.args.toTypedArray().apply { this[1] = corrected })
                }
            })
            ModernHookRegistry.installFast("${prefix}get", getter, XposedInterface.Hooker {
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
                if (list.isEmpty()) return@Hooker chain.proceed()
                val requested = chain.getArg(0) as? String
                val selected = CameraXpanFilterRules.resolve(requested, list)
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
