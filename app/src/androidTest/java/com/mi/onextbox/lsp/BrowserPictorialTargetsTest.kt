package com.mi.onextbox.lsp

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dalvik.system.PathClassLoader
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.luckypray.dexkit.DexKitBridge

@RunWith(AndroidJUnit4::class)
class BrowserPictorialTargetsTest {
    private fun loader(pkg: String): ClassLoader {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(PackageManager.GET_SHARED_LIBRARY_FILES.toLong()))
        return PathClassLoader((listOf(info.sourceDir) + info.splitSourceDirs.orEmpty() + info.sharedLibraryFiles.orEmpty()).joinToString(File.pathSeparator), ClassLoader.getSystemClassLoader())
    }

    @Test fun browserTabsAndSearchHaveTheExpectedNativeInterfaces() {
        val loader = loader(BrowserSettings.PACKAGE_NAME)
        val manager = loader.loadClass("com.heytap.browser.trait.tab.BottomTabManager")
        val list = manager.declaredMethods.single { it.parameterCount == 0 && List::class.java.isAssignableFrom(it.returnType) }
        assertEquals("l", list.name)
        assertEquals(Int::class.javaPrimitiveType, loader.loadClass("com.heytap.browser.trait.tab.d").getDeclaredMethod("k").returnType)
        val defaults = loader.loadClass("com.heytap.browser.platform.settings.DefaultTabSetting")
        assertEquals("d", defaults.declaredMethods.single { it.parameterCount == 0 && it.returnType.name.endsWith("DefaultTabSetting\$b") }.name)
        val engines = loader.loadClass("com.heytap.browser.search.impl.engine.DefaultSearchEngines")
        assertEquals(List::class.java, engines.getDeclaredMethod("n", Boolean::class.javaPrimitiveType).returnType)
        assertNotNull(engines.getDeclaredMethod("J"))
        assertNotNull(engines.getDeclaredMethod("o0", List::class.java, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType))
        val channel = loader.loadClass("com.heytap.browser.ai.page.multi.ui.channel.MultiSearchTabLayout")
        assertEquals("H0", channel.declaredMethods.single { it.parameterTypes.contentEquals(arrayOf(List::class.java, Boolean::class.javaPrimitiveType, Context::class.java)) }.name)
        val page = loader.loadClass("com.heytap.browser.ai.page.multi.MultiSearchInnerPage")
        assertEquals("S", page.declaredMethods.single {
            it.returnType == String::class.java && it.parameterTypes.map { p -> p.name } == listOf("com.heytap.browser.api.search.engine.c", "java.lang.String", "java.lang.String", "java.lang.String")
        }.name)
    }

    @Test fun browserCardsAdsAndAccountControlsArePresent() {
        val loader = loader(BrowserSettings.PACKAGE_NAME)
        val profile = loader.loadClass("com.heytap.browser.usercenter.my.MyProfileView")
        assertNotNull(profile.getDeclaredMethod("onAttachedToWindow"))
        assertEquals("android.widget.LinearLayout", profile.getDeclaredField("q").type.name)
        val info = loader.loadClass("com.platform.sdk.center.widget.HeyTapAccountInfoView")
        assertNotNull(info.getDeclaredMethod("setShowLogoView", Boolean::class.javaPrimitiveType))
        assertNotNull(info.getDeclaredMethod("setShowSignButton", Boolean::class.javaPrimitiveType))
        listOf("mHayTapPlateAndIcon", "mBtnSignIn", "mImageBtnSignIn").forEach {
            assertTrue(View::class.java.isAssignableFrom(info.getDeclaredField(it).type))
        }
        assertTrue(View::class.java.isAssignableFrom(loader.loadClass("com.heytap.browser.usercenter.my.documentation.MyDocCard")))
        assertNotNull(loader.loadClass("com.heytap.browser.download.ui.downloadlist.fragment.DownloadManagerFragment").getDeclaredMethod("onViewCreated", View::class.java, Bundle::class.java))
        val sdk = loader.loadClass("com.bytedance.sdk.openadsdk.TTAdSdk")
        assertEquals(1, sdk.declaredMethods.count { it.name == "init" && it.parameterTypes.map { p -> p.name } == listOf("android.content.Context", "com.bytedance.sdk.openadsdk.TTAdConfig") })
    }

    @Test fun notificationReminderIsLocatedWithoutAnObfuscatedCallbackName() {
        System.loadLibrary("dexkit")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getApplicationInfo(BrowserSettings.PACKAGE_NAME, 0)
        DexKitBridge.create(info.sourceDir).use { dex ->
            val target = dex.findMethod { matcher {
                returnType = "boolean"; paramCount = 4
                usingStrings("checkOpenPushPermissionDialog allSceneUserDialogCount=%s")
            } }.single()
            assertEquals("com.heytap.browser.platform.utils.b1", target.className)
            assertEquals("p", target.name)
        }
    }

    @Test fun wallpaperSaveCompositorCanReturnAnIndependentUnmarkedBitmap() {
        val loader = loader("com.heytap.pictorial")
        val save = loader.loadClass("com.heytap.pictorial.ui.media.SavePictureManager").declaredMethods.single { it.name == "saveBitmap" && it.parameterCount == 6 && it.parameterTypes[3] == Bitmap::class.java }
        assertEquals(Void.TYPE, save.returnType)
        val composer = loader.loadClass("com.heytap.pictorial.utils.BitmapUtils").declaredMethods.single { it.name == "k" && it.parameterCount == 8 && it.returnType == Bitmap::class.java }
        assertEquals(Bitmap::class.java, composer.parameterTypes[1])
        val original = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff1685a2.toInt()) }
        val copy = original.copy(original.config!!, false)
        assertTrue(original.sameAs(copy))
        copy.recycle()
        assertFalse(original.isRecycled)
        original.recycle()
    }
}
