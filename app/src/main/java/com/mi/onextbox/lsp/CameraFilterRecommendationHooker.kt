package com.mi.onextbox.lsp

import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean

internal object CameraFilterRecommendationHooker {
    fun targets(loader: ClassLoader): List<Method> {
        val type = loader.loadClass(CameraFilterRecommendationRules.OWNER)
        return listOf(type.getDeclaredMethod("isGrandTourFilterRecommended", Int::class.javaPrimitiveType, String::class.java),
            type.getDeclaredMethod("isGrandTourFilterSelected", Int::class.javaPrimitiveType)).onEach {
            check(Modifier.isStatic(it.modifiers) && it.returnType == Boolean::class.javaPrimitiveType)
        }
    }

    fun hook(loader: ClassLoader, path: String) {
        if (CameraFilterRecommendationRules.keys.none(MediaUnlockSettings::enabledXposed)) return
        val tag = "ONextBox-CameraFilters"
        val prefix = "$tag@${System.identityHashCode(loader)}:recommend:"
        runCatching {
            val methods = targets(loader)
            val type = methods.first().declaringClass
            fun field(name: String) = type.getDeclaredField(name).apply { isAccessible = true }
            val active = field("sbGrandTourFiltersRecommended")
            val city = field("sGrandTourSelectedCity")
            val registered = field("sGrandTourFilterToIndexMap")
            val cities = field("sGrandTourCityFilterListMap")
            val groups = loader.loadClass("com.oplus.camera.filter.FilterGroupManager")
            val street = groups.getDeclaredField("sStreetFilterGroup")
            val photo = groups.getDeclaredField("sFilterGroup")
            val back = loader.loadClass("com.oplus.camera.filter.FilterGroup").getDeclaredField("mBackTypeList")
            val config = loader.loadClass(MediaUnlockTargets.CAMERA_CONFIG)
                .getDeclaredMethod("getConfigBooleanValue", String::class.java)
            val protected = AtomicBoolean()
            ModernHookRegistry.installFast("${prefix}id", methods[0], XposedInterface.Hooker { chain ->
                // Do not index the street list using a photo/portrait UI position.
                CameraFilterRecommendationRules.recommended(chain.getArg(1) as? String, active.getBoolean(null),
                    city.get(null) as? String, registered.get(null) as? Map<*, *>, cities.get(null) as? Map<*, *>)
            })
            ModernHookRegistry.installFast("${prefix}index", methods[1], XposedInterface.Hooker { chain ->
                val useStreet = config.invoke(null, "com.oplus.street.grand.tour.filter.type.support") == true
                val list = back.get((if (useStreet) street else photo).get(null)) as? List<*>
                val index = chain.getArg(0) as Int
                if (list != null && CameraFilterRecommendationRules.validIndex(index, list.size)) chain.proceed()
                else {
                    if (protected.compareAndSet(false, true)) HookLog.i(tag, "Ignored a recommendation index outside the native filter group")
                    false
                }
            })
            val callers = HostDexMetadata.methods(loader, path, "camera-filter-recommendation", "native-id-v1") { dex ->
                val data = checkNotNull(dex.getClassData(CameraFilterRecommendationRules.OWNER))
                mapOf("callers" to data.methods.filter { it.name in methods.map(Method::getName) }
                    .flatMap { it.callers }.filter { it.isMethod }.mapNotNull {
                        runCatching { it.getMethodInstance(loader) }.getOrNull()
                    }.distinct().toTypedArray())
            }.getValue("callers")
            callers.forEach { ModernHookRuntime.requireModule().deoptimize(it) }
            HotReloadLifecycle.register("${prefix}targets", capture = { methods.toTypedArray() })
            HookLog.i(tag, "Native Grand Tour recommendation uses filter IDs with guarded selection indices")
        }.onFailure {
            ModernHookRegistry.unhookPrefix(prefix)
            HookLog.w(tag, "Unsupported Grand Tour recommendation implementation", it)
        }
    }
}
