package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.R
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method

internal object GalleryNativeFilterGroups {
    const val OWNER = "com.oplus.ocs.camera.ipusdk.processunit.filter.list.FilterGroupManager"
    private val cameraKeys = setOf("camera_unlock_filter_master", "camera_unlock_filter_realme_classic",
        "camera_unlock_filter_tone", "camera_unlock_filter_street", "camera_unlock_filter_night", "camera_unlock_filter_gr",
        "camera_unlock_filter_jzk", "camera_unlock_filter_festival", "camera_unlock_filter_lively")
    val keys = cameraKeys.map { it.replaceFirst("camera_", "gallery_") }.toSet()
    val titles = mapOf(
        "camera_unlock_filter_master" to R.string.gallery_unlock_filter_master,
        "camera_unlock_filter_realme_classic" to R.string.gallery_unlock_filter_realme_classic,
        "camera_unlock_filter_tone" to R.string.gallery_unlock_filter_tone,
        "camera_unlock_filter_street" to R.string.gallery_unlock_filter_street,
        "camera_unlock_filter_night" to R.string.gallery_unlock_filter_night,
        "camera_unlock_filter_gr" to R.string.gallery_unlock_filter_gr,
        "camera_unlock_filter_jzk" to R.string.gallery_unlock_filter_jzk,
        "camera_unlock_filter_festival" to R.string.gallery_unlock_filter_festival,
        "camera_unlock_filter_lively" to R.string.gallery_unlock_filter_lively,
    )
    fun target(loader: ClassLoader): Method = loader.loadClass(OWNER).getDeclaredMethod(
        "init", Context::class.java, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)

    fun hook(loader: ClassLoader) {
        val tag = "ONextBox-GalleryFilters"
        val prefix = "$tag@${System.identityHashCode(loader)}:"
        if (keys.none(MediaUnlockSettings::enabledXposed) && HotReloadLifecycle.restoredState(prefix + "state") == null) return
        runCatching {
            val method = target(loader)
            val type = loader.loadClass("com.oplus.ocs.camera.ipusdk.processunit.filter.list.FilterGroup")
            val back = type.getDeclaredField("mBackTypeList")
            val names = type.getDeclaredField("mBackNameIdList")
            val append = type.getDeclaredMethod("addBack", String::class.java, String::class.java)
            val groups = listOf("sFilterGroup", "sProFilterGroup").map(method.declaringClass::getDeclaredField)
            ModernHookRegistry.installFast(prefix + "groups", method, XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                val context = chain.getArg(0) as? Context ?: return@Hooker result
                val candidates = CameraNativeFilterEntries.candidates { key ->
                    key in cameraKeys && MediaUnlockSettings.enabledXposed(key.replaceFirst("camera_", "gallery_"))
                }.filterKeys(CameraNativeFilterEntries::hasMaterial)
                if (candidates.isEmpty()) return@Hooker result
                val camera by lazy { runCatching { context.createPackageContext(MediaUnlockCatalog.App.Camera.packageName, 0) }.getOrNull() }
                var added = 0
                groups.forEach { group ->
                    val instance = checkNotNull(group.get(null))
                    val ids = back.get(instance) as List<*>
                    check(ids.size == (names.get(instance) as List<*>).size)
                    candidates.forEach { (id, name) ->
                        if (id !in ids) {
                            fun title(source: Context?): String? = source?.let {
                                val resource = it.resources.getIdentifier(name, "string", it.packageName)
                                if (resource == 0) null else it.getString(resource)
                            }
                            val label = title(context) ?: title(camera)
                            if (!label.isNullOrBlank()) { append.invoke(instance, id, label); added++ }
                        }
                    }
                    check(ids.size == (names.get(instance) as List<*>).size)
                }
                if (added > 0) {
                    HookLog.i(tag, "Native editor filter groups completed; added=$added")
                }
                result
            })
            HotReloadLifecycle.register(prefix + "state", capture = { method })
        }.onFailure {
            ModernHookRegistry.unhookPrefix(prefix)
            HookLog.w(tag, "Native editor filter completion unavailable", it)
        }
    }
}
