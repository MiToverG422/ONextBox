package com.mi.onextbox.lsp

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dalvik.system.PathClassLoader
import java.io.File
import java.lang.reflect.Modifier
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenTranslationTargetsTest {
    @Test fun installedTranslationResolvesAllIndependentRestrictionsAndNativeCallers() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getApplicationInfo(ScreenTranslationSettings.PACKAGE_NAME,
            PackageManager.ApplicationInfoFlags.of(PackageManager.GET_SHARED_LIBRARY_FILES.toLong()))
        val paths = listOf(info.sourceDir) + info.splitSourceDirs.orEmpty() + info.sharedLibraryFiles.orEmpty()
        val loader = PathClassLoader(paths.joinToString(File.pathSeparator), ClassLoader.getSystemClassLoader())
        val groups = ApkDexSession.withSession(info.sourceDir) { open -> ScreenTranslationTargets.resolveAll(open(), loader) }
        assertEquals(setOf(ScreenTranslationTargets.SCHEMA, "floating", "blacklist", "pages", "landscape", "browser"), groups.keys)
        val targets = groups.getValue("floating")
        val detector = targets.first()
        assertEquals(Boolean::class.javaPrimitiveType, detector.returnType)
        assertEquals(0, detector.parameterCount)
        assertTrue(Modifier.isStatic(detector.modifiers))
        val callers = targets.drop(1).map { it.declaringClass.name }.toSet()
        assertTrue(callers.contains("com.coloros.translate.screen.translate.business.ScreenTranslationPresenter"))
        assertTrue(callers.contains("com.coloros.translate.screen.widget.ScreenTranslationToolCapsule"))
        val blacklist = groups.getValue("blacklist").first()
        assertEquals(Boolean::class.javaPrimitiveType, blacklist.returnType)
        assertEquals(0, blacklist.parameterCount)
        assertFalse(Modifier.isStatic(blacklist.modifiers))
        val pages = groups.getValue("pages").first()
        assertEquals("com.coloros.translate.screen.ScreenTranslationService", pages.declaringClass.name)
        assertEquals(Boolean::class.javaPrimitiveType, pages.returnType)
        assertEquals(0, pages.parameterCount)
        assertTrue(Modifier.isStatic(pages.modifiers))
        assertEquals("m", pages.name) // n() tracks an ongoing screenshot, which must keep its native guard.
        val landscape = groups.getValue("landscape")
        assertEquals(listOf("android.content.Context"), landscape[0].parameterTypes.map { it.name })
        assertEquals(Boolean::class.javaPrimitiveType, landscape[0].returnType)
        assertEquals("com.coloros.translate.screen.ScreenTranslationService", landscape[1].declaringClass.name)
        assertEquals(listOf("android.content.Intent"), landscape[1].parameterTypes.map { it.name })
        assertEquals(Void.TYPE, landscape[2].returnType)
        val browser = groups.getValue("browser")
        assertEquals(Boolean::class.javaPrimitiveType, browser[0].returnType)
        assertEquals(listOf("java.lang.String"), browser[0].parameterTypes.map { it.name })
        assertEquals(String::class.java, browser[1].returnType)
        assertEquals(listOf("java.lang.String", "long"), browser[1].parameterTypes.map { it.name })
        assertEquals(browser[0].declaringClass, browser[1].declaringClass)
        assertEquals(1, browser[1].declaringClass.declaredFields.count {
            Modifier.isStatic(it.modifiers) && it.type == java.util.concurrent.locks.ReentrantReadWriteLock::class.java
        })
    }
}
