package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import org.luckypray.dexkit.DexKitBridge

internal object CameraFilterSdkGroups {
    private const val OWNER = "com.oplus.camera.filter.FilterGroupManager"
    private const val FEATURE = "com.oplus.camera.feature.capture_params"

    fun targets(loader: ClassLoader): List<Method> = listOf(loader.loadClass(OWNER)
        .getDeclaredMethod("initFromIpu", Context::class.java, HashMap::class.java))

    fun appCallers(dex: DexKitBridge, loader: ClassLoader): List<Method> = dex.findMethod { matcher {
        addInvoke { declaredClass = OWNER; name = "initFromIpu" }
    } }.filter { it.isMethod }.mapNotNull { runCatching { it.getMethodInstance(loader) }.getOrNull() }.distinct()

    fun countTargets(dex: DexKitBridge, loader: ClassLoader): Map<String, Array<Method>> {
        val candidates = dex.findMethod { matcher {
            usingStrings(FEATURE); paramTypes(); returnType = "int"
        } }.filter { data -> data.usingStrings.toSet() == setOf(FEATURE) && data.invokes.none {
            it.isMethod && it.className == data.className && it.paramCount == 0 && it.returnTypeName == "int"
        } && data.invokes.any {
            it.isMethod && it.className == data.className && it.paramCount == 0 && it.returnTypeName == List::class.java.name
        } }
        val count = candidates.single()
        val types = count.invokes.single { it.isMethod && it.className == count.className && it.paramCount == 0 &&
            it.returnTypeName == List::class.java.name }
        return mapOf("count" to arrayOf(count.getMethodInstance(loader)), "types" to arrayOf(types.getMethodInstance(loader)),
            "callers" to count.callers.filter { it.isMethod }.mapNotNull {
                runCatching { it.getMethodInstance(loader) }.getOrNull()
            }.distinct().toTypedArray())
    }

    fun install(loader: ClassLoader, prefix: String, path: String) {
        val target = targets(loader).single()
        val owner = target.declaringClass
        val group = loader.loadClass("com.oplus.camera.filter.FilterGroup")
        val back = group.getDeclaredField("mBackTypeList")
        val names = group.getDeclaredField("mBackNameIdList")
        val add = group.getDeclaredMethod("addBack", String::class.java, Int::class.javaObjectType)
        val capture = loader.loadClass("com.oplus.camera.filter.FilterHelper").getDeclaredField("sFilterPathConvertMap")
        val fields = listOf("sFilterGroup", "sProFilterGroup").map(owner::getDeclaredField)
        val modified = ConcurrentHashMap.newKeySet<String>()
        val reported = AtomicBoolean()
        val metadata = HostDexMetadata.methods(loader, path, "camera-filter-count", "native-group-count-v1") {
            countTargets(it, loader)
        }
        val types = metadata.getValue("types").single().apply { isAccessible = true }
        ModernHookRegistry.installFast("${prefix}groups:count", metadata.getValue("count").single(), XposedInterface.Hooker { chain ->
            if (modified.isEmpty()) return@Hooker chain.proceed()
            val list = types.invoke(chain.thisObject) as? List<*> ?: return@Hooker chain.proceed()
            if (list.any { it is String && it in modified }) list.size else chain.proceed()
        })
        ModernHookRegistry.installFast("${prefix}groups:app", target, XposedInterface.Hooker { chain ->
            val result = chain.proceed()
            val context = chain.getArg(0) as? Context ?: return@Hooker result
            val candidates = CameraNativeFilterEntries.candidates(MediaUnlockSettings::enabledXposed)
            if (candidates.isEmpty()) return@Hooker result
            val captureIds = (capture.get(null) as? Map<*, *>)?.keys?.filterIsInstance<String>()?.toSet().orEmpty()
            val resources = context.resources
            val labels = HashMap<String, Int>()
            val eligible = CameraNativeFilterEntries.eligible(candidates, captureIds, CameraNativeFilterEntries::hasMaterial) {
                labels.getOrPut(it) { resources.getIdentifier(it, "string", context.packageName) } != 0
            }
            val counts = fields.mapNotNull { field ->
                val instance = field.get(null) ?: return@mapNotNull null
                val list = back.get(instance) as? List<*> ?: return@mapNotNull null
                if (!CameraXpanFilterRules.hasValidEntries(list, names.get(instance) as? List<*>)) return@mapNotNull null
                eligible.forEach { (id, name) ->
                    if (id !in list) {
                        add.invoke(instance, id, labels.getValue(name))
                        modified.add(id)
                    }
                }
                field.name to list.size
            }.toMap()
            if (reported.compareAndSet(false, true)) HookLog.i("ONextBox-CameraFilters",
                "Native app filter groups completed: eligible=${eligible.size}/${candidates.size}; added=${modified.size}; $counts")
            result
        })
        metadata.getValue("callers").forEach { ModernHookRuntime.requireModule().deoptimize(it) }
        // The lists and their entries belong to the camera SDK. Only the count correction needs ownership.
        HotReloadLifecycle.register("${prefix}groups:state", capture = { modified.toTypedArray() },
            restore = { saved -> (saved as? Array<*>)?.filterIsInstanceTo(modified, String::class.java) })
    }
}
