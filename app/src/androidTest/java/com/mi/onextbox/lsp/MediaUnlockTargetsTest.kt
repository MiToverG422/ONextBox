package com.mi.onextbox.lsp

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dalvik.system.PathClassLoader
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Resolve installed app code without launching either app, reading media or changing preferences. */
@RunWith(AndroidJUnit4::class)
class MediaUnlockTargetsTest {
    private fun installed(app: MediaUnlockCatalog.App): Pair<android.content.pm.ApplicationInfo, ClassLoader> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getApplicationInfo(app.packageName,
            PackageManager.ApplicationInfoFlags.of(PackageManager.GET_SHARED_LIBRARY_FILES.toLong()))
        val paths = listOf(info.sourceDir) + info.splitSourceDirs.orEmpty() + info.sharedLibraryFiles.orEmpty()
        val loader = PathClassLoader(paths.joinToString(File.pathSeparator), ClassLoader.getSystemClassLoader())
        return info to loader
    }

    private fun targets(app: MediaUnlockCatalog.App): Map<String, Array<java.lang.reflect.Method>> {
        val (info, loader) = installed(app)
        return ApkDexSession.withSession(info.sourceDir) { open ->
            if (app == MediaUnlockCatalog.App.Camera) MediaUnlockTargets.camera(open(), loader)
            else MediaUnlockTargets.gallery(open(), loader)
        }
    }

    @Test fun installedCameraIncludesCachedAndDefaultValueReaders() {
        val result = targets(MediaUnlockCatalog.App.Camera)
        val readers = result.getValue("readers")
        assertTrue(readers.all(MediaUnlockTargets::cameraReader))
        assertTrue(readers.any { it.name == "getConfigBooleanValue" && it.parameterCount == 1 })
        assertTrue(readers.any { it.name == "d" && it.parameterCount == 3 })
        assertTrue(result.getValue("callers").isNotEmpty())
    }

    @Test fun installedGalleryIncludesTypedDispatcherAndStringFacade() {
        val result = targets(MediaUnlockCatalog.App.Gallery)
        val dispatcher = result.getValue("dispatcher").single()
        assertTrue(GalleryAiRetouchRules.configMethod(dispatcher))
        assertTrue(GalleryAiRetouchRules.keyGetter(result.getValue("key").single(), dispatcher.parameterTypes[0]))
        assertTrue(result.getValue("stringReaders").isNotEmpty())
        assertTrue(result.getValue("stringReaders").all(MediaUnlockTargets::galleryKeyReader))
        assertTrue(result.getValue("stringReaders").any {
            it.parameterTypes.toList() == listOf(String::class.java, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
        })
        assertTrue(result.getValue("stringReaders").any { it.parameterTypes.first() == Int::class.javaPrimitiveType })
        assertFalse(result.getValue("stringReaders").first().declaringClass.declaredMethods
            .filter { it.parameterTypes.contentEquals(arrayOf(String::class.java)) }.any(MediaUnlockTargets::galleryKeyReader))
        assertTrue(result.getValue("callers").isNotEmpty())
        assertEquals(1, result.getValue("functionReaders").size)
    }

    @Test fun installedCameraHasTheNativeIpuCatalogEntryWithoutOpeningCamera() {
        val (_, loader) = installed(MediaUnlockCatalog.App.Camera)
        val method = CameraFilterCatalogHooker.target(loader)
        assertEquals(HashMap::class.java, method.returnType)
        assertEquals(listOf(android.content.Context::class.java), method.parameterTypes.toList())
        assertEquals(2, CameraFilterRecommendationHooker.targets(loader).size)
        assertTrue(CameraFilterSdkHooker.targets(loader).size >= 2)
        assertEquals(1, CameraFilterSdkGroups.targets(loader).size)
        val (info, _) = installed(MediaUnlockCatalog.App.Camera)
        ApkDexSession.withSession(info.sourceDir) { open ->
            val counts = CameraFilterSdkGroups.countTargets(open(), loader)
            assertEquals(1, counts.getValue("count").size)
            assertEquals(1, counts.getValue("types").size)
            val xpan = CameraXpanFilterHooker.targets(open(), loader)
            assertEquals(1, xpan.getValue("getter").size)
            assertEquals(1, xpan.getValue("setter").size)
            assertEquals(1, xpan.getValue("legacy").size)
            assertEquals(1, xpan.getValue("legacyWrite").size)
            assertEquals(1, xpan.getValue("preview").size)
            assertTrue(xpan.getValue("callers").isNotEmpty())
        }
    }

    @Test fun missingPaletteResourcesCannotBeEnabled() {
        if (CameraPaletteSupport.available()) return
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val before = LspPreferenceStore.prefs(context).all[CameraPaletteSupport.OPTION]
        assertThrows(IllegalArgumentException::class.java) { MediaUnlockSettings.set(context, CameraPaletteSupport.OPTION, true) }
        assertEquals(before, LspPreferenceStore.prefs(context).all[CameraPaletteSupport.OPTION])
    }

    @Test fun installedCameraAndGalleryExposeWatermarkPersonalizationTargets() {
        MediaUnlockCatalog.App.entries.forEach { app ->
            val (info, loader) = installed(app)
            ApkDexSession.withSession(info.sourceDir) { open ->
                val result = MediaWatermarkHooker.targets(open(), loader, app == MediaUnlockCatalog.App.Camera)
                assertTrue(result.getValue("market").size >= 3)
                if (app == MediaUnlockCatalog.App.Camera) {
                    assertEquals(2, result.getValue("inputModel").size)
                    assertEquals(1, result.getValue("inputUser").size)
                    assertEquals(2, result.getValue("model").size)
                    assertNotEquals(result.getValue("time").single(), result.getValue("location").single())
                    assertNotEquals(result.getValue("showTime").single(), result.getValue("showLocation").single())
                } else assertEquals(4, result.getValue("content").size)
            }
        }
    }

    @Test fun installedCameraExposesPreviewAndVideoWatermarkGuards() {
        val (info, loader) = installed(MediaUnlockCatalog.App.Camera)
        assertEquals(Map::class.java, CameraAiCompositionHooker.target(loader).returnType)
        assertEquals(1, CameraPaletteHooker.cameraTarget(loader).parameterCount)
        assertEquals(2, CameraPaletteHooker.galleryTargets(loader).size)
        assertEquals(3, GalleryNativeFilterGroups.target(loader).parameterCount)
        ApkDexSession.withSession(info.sourceDir) { open ->
            val result = CameraMediaGuardHooker.targets(open(), loader)
            assertEquals(android.graphics.Bitmap::class.java, result.getValue("crop").single().returnType)
            assertEquals(android.os.Bundle::class.java, result.getValue("bundle").single().returnType)
        }
    }

    @Test fun installedPaletteCaptureMetadataAcceptsTheCompletedNativeAlgorithmList() {
        val (_, loader) = installed(MediaUnlockCatalog.App.Camera)
        val method = CameraPaletteHooker.cameraTarget(loader)
        val key = loader.loadClass("com.oplus.ocs.camera.common.util.ParameterKeys").getField("KEY_CAPTURE_ALGO_LIST")
        assertNotEquals(key.declaringClass.name, "com.oplus.ocs.camera.common.util.ParameterKeys")
        val nativeKey = key.get(null)
        val meta = method.returnType.getDeclaredConstructor().newInstance()
        val get = method.returnType.methods.single { it.name == "get" && it.parameterTypes.contentEquals(arrayOf(key.type)) }
        val set = method.returnType.methods.single { it.name == "setParameter" && it.parameterCount == 2 && it.parameterTypes[0] == key.type }
        val complete = CameraPaletteAlgorithms.include(arrayOf("aps_algo_hdr", "aps_algo_face_info"))
        set.invoke(meta, nativeKey, complete)
        assertArrayEquals(complete, get.invoke(meta, nativeKey) as Array<*>)
    }

    @Test fun savedLegacyWatermarkContentWorksAndExplicitDisableStillWins() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "media_watermark_legacy_compatibility_validation"
        check(!File(context.dataDir, "shared_prefs/$name.xml").exists())
        val prefs = context.getSharedPreferences(name, android.content.Context.MODE_PRIVATE)
        try {
            assertTrue(prefs.edit().putString(MediaWatermarkSettings.MODEL, "ONextBox Validation").commit())
            assertTrue(MediaWatermarkSettings.enabled(prefs))
            assertTrue(prefs.edit().putBoolean(MediaWatermarkSettings.ENABLED, false).commit())
            assertFalse(MediaWatermarkSettings.enabled(prefs))
            assertTrue(prefs.edit().clear().putBoolean(MediaWatermarkSettings.HIDE_TIME, true).commit())
            assertTrue(MediaWatermarkSettings.enabled(prefs))
            assertTrue(prefs.edit().clear().commit())
            assertFalse(MediaWatermarkSettings.enabled(prefs))
        } finally { assertTrue(context.deleteSharedPreferences(name)) }
    }

    @Test fun installedGalleryHasActualRealmeTemplatesAndHostPairCatalogs() {
        val (info, loader) = installed(MediaUnlockCatalog.App.Gallery)
        ApkDexSession.withSession(info.sourceDir) { open ->
            val devices = GalleryWatermarkDeviceHooker.targets(open(), loader)
            assertEquals(1, devices.getValue("host").size)
            assertEquals(1, devices.getValue("plugin").size)
            assertTrue(devices.getValue("callers").isNotEmpty())
        }
        assertEquals(6, GalleryWatermarkCatalogHooker.targets(loader).size)
        val available = GalleryWatermarkCatalogHooker.available(info.sourceDir)
        assertTrue(available.containsAll(listOf("personalize_film_realme_1", "personalize_film_realme_2",
            "realme_brand_imprint_inspirationPhoto_1", "hassel_style_xpan_1", "lumo_style_master_mode_1")))
        assertFalse(available.contains("personalize_brand_3"))
        val type = loader.loadClass(GalleryWatermarkCatalogRules.OWNER)
        val owner = type.declaredConstructors.single { it.parameterCount == 0 }.apply { isAccessible = true }.newInstance()
        val pairs = type.getDeclaredMethod("getBuiltInLegacyPersonalizeFilmRealmeStyles").invoke(owner) as List<*>
        assertEquals(2, pairs.size)
        val pairType = loader.loadClass("kotlin.Pair")
        val first = pairType.getMethod("getFirst")
        assertTrue(pairs.all { pairType.isInstance(it) && first.invoke(it) in available })
    }

    @Test fun nativeVisibleFilmChoicesHaveRealPortraitLandscapeAndCameraThumbnails() {
        val (_, loader) = installed(MediaUnlockCatalog.App.Gallery)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
            .createPackageContext(MediaUnlockCatalog.App.Gallery.packageName, 0)
        val type = loader.loadClass(GalleryWatermarkCatalogRules.OWNER)
        val companion = type.declaredConstructors.single { it.parameterCount == 0 }.apply { isAccessible = true }.newInstance()
        val pairId = loader.loadClass("kotlin.Pair").getMethod("getFirst")
        val native = type.getDeclaredMethod("getBuiltInFilmStyles", Boolean::class.javaPrimitiveType)
            .invoke(companion, false) as List<*>
        val ids = ArrayList(native.map { pairId.invoke(it) as String }).apply {
            addAll(listOf("personalize_film_realme_1", "personalize_film_realme_2"))
        }
        assertEquals(4, ids.distinct().size)

        // Test-only shell instance: execute the app's actual DTO builder without
        // starting an editor, accessing a photo or constructing an editing session.
        val unsafeType = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeType.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        val visible = GalleryWatermarkCatalogHooker.visibleTarget(loader).apply { isAccessible = true }
        val vmType = visible.declaringClass
        val vm = unsafeType.getMethod("allocateInstance", Class::class.java).invoke(unsafe, vmType)
        val lazy = loader.loadClass("kotlin.InitializedLazyImpl").getConstructor(Any::class.java)
        vmType.getDeclaredField("b0").apply { isAccessible = true }.set(vm, linkedMapOf<String, Boolean>())
        for ((camera, landscape, arrayName) in listOf(
            Triple(false, false, "picture3d_editor_array_watermark_camera_film_sketch_array"),
            Triple(false, true, "picture3d_editor_array_watermark_camera_film_sketch_land_array"),
            Triple(true, false, "picture3d_editor_array_watermark_camera_section_film_sketch_array"),
        )) {
            vmType.getDeclaredField("o").apply { isAccessible = true }.set(vm, lazy.newInstance(camera))
            val arrayId = context.resources.getIdentifier(arrayName, "array", context.packageName)
            assertNotEquals(0, arrayId)
            val array = context.resources.obtainTypedArray(arrayId)
            val stock = try { IntArray(array.length()) { array.getResourceId(it, 0) } } finally { array.recycle() }
            assertEquals(2, stock.size)
            val complete = GalleryWatermarkCatalogRules.completeChoices(ids, stock) { id ->
                GalleryWatermarkCatalogHooker.preview(context.resources, context.packageName, id, camera, landscape)
            }
            assertEquals(ids, complete.ids)
            val previews = complete.previews
            assertEquals(4, previews.size)
            val entries = visible.invoke(vm, ids, previews, 41) as ArrayList<*>
            assertEquals(ids, entries.map { it!!.javaClass.getDeclaredField("a").get(it) })
            entries.forEachIndexed { index, entry ->
                assertEquals(previews[index], entry!!.javaClass.getMethod("getIconResId").invoke(entry))
                assertTrue(entry.javaClass.getDeclaredField("h").getBoolean(entry))
                assertEquals(41, entry.javaClass.getDeclaredField("k").getInt(entry))
            }
        }
    }
}
