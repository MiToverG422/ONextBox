package com.mi.onextbox.lsp

import android.content.pm.PackageManager
import android.os.CountDownTimer
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import dalvik.system.PathClassLoader
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class SystemCustomizationTargetsTest {
    private fun packageLoader(name: String): Pair<ClassLoader, String> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getApplicationInfo(name,
            PackageManager.ApplicationInfoFlags.of(PackageManager.GET_SHARED_LIBRARY_FILES.toLong()))
        val paths = listOf(info.sourceDir) + info.splitSourceDirs.orEmpty() + info.sharedLibraryFiles.orEmpty()
        return PathClassLoader(paths.joinToString(File.pathSeparator), ClassLoader.getSystemClassLoader()) to info.sourceDir
    }

    @Test fun currentLauncherRetainsBothNativeSizingAndFolderGeometryContracts() {
        val targets = LauncherCustomizationHooker.Targets(packageLoader("com.android.launcher").first)
        assertTrue(View::class.java.isAssignableFrom(targets.bubble))
        assertTrue(View::class.java.isAssignableFrom(targets.paged))
        assertTrue(targets.iconInitializers.isNotEmpty())
        assertTrue(targets.iconBindings.isNotEmpty())
        assertTrue(targets.creators.isNotEmpty())
        assertEquals(Int::class.javaPrimitiveType, targets.iconSize.type)
        assertEquals(2, targets.cellSizes.size)
        targets.iconInitializers.forEach { assertEquals(Int::class.javaPrimitiveType, it.returnType) }
        assertEquals(3, targets.limits.size)
        val pair = targets.cellPair.newInstance(120, 140)
        assertEquals(120, targets.cellWidth.get(pair))
        assertEquals(140, targets.cellHeight.get(pair))
        assertNotNull(targets.animation)
        assertEquals(0, targets.animation!!.factory.parameterCount)
        assertEquals(4, targets.animation.preview.parameterCount)
    }

    @Test fun galleryCountdownTargetsTheNativeConfirmationTimerOnly() {
        val (loader, path) = packageLoader(GalleryAiRetouchSettings.PACKAGE_NAME)
        val targets = ApkDexSession.withSession(path) { GalleryDeleteCountdownHooker.targets(it(), loader) }
        assertEquals(2, targets.size)
        assertTrue(CountDownTimer::class.java.isAssignableFrom(targets[0].declaringClass))
        assertEquals("onFinish", targets[0].name)
        assertEquals(Void.TYPE, targets[0].returnType)
        assertTrue(targets[1].declaringClass.name.contains("BottomMenuHelper"))
    }

    @Test fun notificationButtonExposesSeparateNativeWidthAndHeight() {
        val targets = NotificationClearButtonHooker.resolve(packageLoader("com.android.systemui").first)
        val width = targets[3] as java.lang.reflect.Field
        assertEquals(Int::class.javaPrimitiveType, width.type)
        assertEquals(Int::class.javaPrimitiveType, width.declaringClass.getDeclaredField("iconHeight").type)
    }

    @Test fun cloneAppExposesItsLiveAndCachedEligibilityLists() {
        val loader = packageLoader(SystemCustomizationSettings.MULTI_APP).first
        for ((name, method) in listOf("com.oplus.multiapp.platform.MultiAppAPI" to "getAllowedMultiApp",
            "com.oplus.multiapp.data.MultiAppDataManager" to "getAllowedAppList")) {
            assertTrue(List::class.java.isAssignableFrom(loader.loadClass(name).getDeclaredMethod(method).returnType))
        }
        val helper = loader.loadClass("com.oplus.multiapp.utils.CustomHelper")
        assertEquals(Boolean::class.javaPrimitiveType, helper.getDeclaredMethod("isAppLimited").returnType)
        assertEquals(Int::class.javaPrimitiveType, helper.getDeclaredMethod("getMaxCreateNum").returnType)
    }

    @Test fun audioAndCloneContractsResolveWithoutStartingOrCallingSystemServices() {
        val loader = PathClassLoader(listOf("/system/framework/services.jar", "/system/framework/oplus-services.jar",
            "/system/framework/oplus-framework.jar").joinToString(File.pathSeparator), ClassLoader.getSystemClassLoader())
        assertEquals("createStreamStates", VolumeStepsHooker.target(loader).name)
        val clone = MultiAppRestrictionHooker.systemTargets(loader)
        assertTrue(clone.size >= 4)
        assertEquals("getAllowedList", clone[0].name)
        assertEquals(Boolean::class.javaPrimitiveType, clone[1].returnType)
    }
}
