package com.mi.onextbox.lsp

import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dalvik.system.PathClassLoader
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyboardPictorialTargetsTest {
    private fun loader(pkg: String): ClassLoader {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(PackageManager.GET_SHARED_LIBRARY_FILES.toLong()))
        return PathClassLoader((listOf(info.sourceDir) + info.splitSourceDirs.orEmpty() + info.sharedLibraryFiles.orEmpty()).joinToString(File.pathSeparator), ClassLoader.getSystemClassLoader())
    }
    @Test fun actualKeyboardHasTwoKeyDrawingEntriesAndItsBackingThemeEngine() {
        val loader = loader("com.oplus.keyboard")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val apk = context.packageManager.getApplicationInfo("com.oplus.keyboard", 0).sourceDir
        val background = KeyboardRenderTargets.background(loader, apk)
        val renderer = background.declaringClass
        val key = background.parameterTypes[0]
        val draws = KeyboardAppearanceRules.drawEntries(renderer, Canvas::class.java, key)
        assertTrue(draws.isNotEmpty())
        assertTrue(Drawable::class.java.isAssignableFrom(background.returnType))
        assertNotNull(renderer.getDeclaredMethod("setDrawRefresh", Boolean::class.javaPrimitiveType))
        val effects = KeyboardRenderTargets.effects(loader, apk, renderer)
        assertEquals(1, effects.count(KeyboardRenderTargets::isEdge))
        assertEquals(1, effects.count { !KeyboardRenderTargets.isEdge(it) })
        assertEquals(String::class.java, key.getDeclaredField("t").type)
        val theme = loader.loadClass("com.oplus.keyboard.base.theme.a")
        assertTrue(theme.declaredMethods.count { it.returnType == Integer::class.java && String::class.java in it.parameterTypes } >= 3)
        val blur = loader.loadClass("com.oplus.keyboard.input.view.blur.b")
        assertEquals(View::class.java, blur.getDeclaredField("a").type)
        val manager = blur.getDeclaredField("b").type
        assertNotNull(manager.getMethod("setBlurRadius", Int::class.javaPrimitiveType))
        assertNotNull(manager.getMethod("setCornerRadius", *Array(4) { Float::class.javaPrimitiveType }))
        assertEquals(StateListDrawable::class.java, loader.loadClass("com.oplus.keyboard.input.view.head.ToolItemView").getDeclaredField("c").type)
    }

    @Test fun actualKeyboardEmojiResolverAcceptsMergedCollectors() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val apk = context.packageManager.getApplicationInfo("com.oplus.keyboard", 0).sourceDir
        val loader = loader("com.oplus.keyboard")
        val resolve = KeyboardFlagEmojiHooker::class.java.getDeclaredMethod("resolve", ClassLoader::class.java, String::class.java)
            .apply { isAccessible = true }
        val targets = resolve.invoke(KeyboardFlagEmojiHooker, loader, apk) as Array<*>
        val collector = targets[0] as java.lang.reflect.Method
        val owner = targets[1] as java.lang.reflect.Field
        val category = targets[2] as java.lang.reflect.Method
        assertEquals("emit", collector.name)
        assertTrue(owner.type.isAssignableFrom(category.declaringClass))
        val models = KeyboardFlagEmojiModels(
            targets[4] as java.lang.reflect.Constructor<*>, targets[5] as java.lang.reflect.Field,
            targets[6] as java.lang.reflect.Field, targets[7] as java.lang.reflect.Constructor<*>,
            targets[8] as java.lang.reflect.Field, targets[9] as java.lang.reflect.Constructor<*>, targets[10] as java.lang.reflect.Field,
        )
        val pair = (targets[4] as java.lang.reflect.Constructor<*>).newInstance(emptyList<Any>(), emptyList<Any>())
        assertNotNull(models.supplement(pair))
    }
    @Test fun actualSystemUiProvidesRotationEventsCurrentBitmapAndExistingClockAccess() {
        val loader = loader("com.android.systemui")
        val helper = loader.loadClass("com.oplus.systemui.keyguard.wallpaper.pictorial.PictorialWallpaperHelper")
        assertEquals(Bitmap::class.java, helper.getDeclaredMethod("copyWallpaperBitmapSafely").returnType)
        assertNotNull(helper.getDeclaredMethod("onWallpaperChange", Int::class.javaPrimitiveType))
        assertNotNull(helper.getDeclaredMethod("getCurrentSourceKey"))
        assertNotNull(loader.loadClass("com.android.keyguard.OplusKeyguardDependencyEx").getDeclaredMethod("getKeyguardStyleClockController"))
        assertNotNull(loader.loadClass("com.android.systemui.DependencyEx").getDeclaredField("sDependency"))
        val clock = loader.loadClass("com.oplus.systemui.keyguard.clockstyle.KeyguardStyleClockControllerImpl")
        assertNotNull(clock.getDeclaredField("wallpaperDeliveryController"))
        assertNotNull(clock.getDeclaredMethod("updateDynamicMixColorState"))
    }
}
