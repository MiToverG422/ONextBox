package com.mi.onextbox.lsp

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dalvik.system.PathClassLoader
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstallerCleanupTargetsTest {
    private fun targets(packageName: String): Map<String, Array<java.lang.reflect.Method>> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getApplicationInfo(packageName,
            PackageManager.ApplicationInfoFlags.of(PackageManager.GET_SHARED_LIBRARY_FILES.toLong()))
        val loader = PathClassLoader((listOf(info.sourceDir) + info.splitSourceDirs.orEmpty() + info.sharedLibraryFiles.orEmpty())
            .joinToString(File.pathSeparator), ClassLoader.getSystemClassLoader())
        return ApkDexSession.withSession(info.sourceDir) { open -> InstallerCleanupTargets.resolve(open(), loader, packageName) }
    }

    @Test fun appInstallerResolvesTheNativeNoScanRouteAndBothAdPipelines() {
        val groups = targets(InstallerCleanupSettings.APP_DETAIL)
        assertEquals(setOf(InstallerCleanupTargets.SCHEMA, "market-card", "network", "fast-prepare", "scan-card", "collection-notice", "route", "plain", "recommend", "cancel", "risk-ad"), groups.keys)
        assertEquals("android.view.View", groups.getValue("market-card")[0].declaringClass.name)
        assertEquals("setVisibility", groups.getValue("market-card")[0].name)
        assertEquals("android.app.Activity", groups.getValue("market-card")[1].declaringClass.name)
        assertEquals("onResume", groups.getValue("market-card")[1].name)
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.packageManager
            .getResourcesForApplication(InstallerCleanupSettings.APP_DETAIL)
        assertTrue(resources.getIdentifier("install_done_suggest_B", "id", InstallerCleanupSettings.APP_DETAIL) != 0)
        assertTrue(resources.getIdentifier("layout_no_recommend", "id", InstallerCleanupSettings.APP_DETAIL) != 0)
        assertTrue(groups.getValue("network").map { it.name }.containsAll(setOf("connect", "read", "write", "android_getaddrinfo")))
        val preparation = groups.getValue("fast-prepare")
        assertEquals(Any::class.java, preparation[0].returnType)
        assertEquals(4, preparation[0].parameterCount)
        val fallback = preparation[1].declaringClass.declaredConstructors.single { it.parameterCount == 3 }
        assertNotNull(fallback.newInstance(null, null, ""))
        val card = groups.getValue("scan-card")[0]
        assertEquals("com.oplus.appdetail.modelv2.guide.view.ScanContentFrameLayout", card.declaringClass.name)
        assertEquals(Void.TYPE, card.returnType)
        assertEquals(0, card.parameterCount)
        val notice = groups.getValue("collection-notice")
        assertEquals("com.oplus.appdetail.model.entrance.ChannelWhiteListHelper", notice[0].declaringClass.name)
        assertEquals(listOf(String::class.java), notice[0].parameterTypes.toList())
        assertEquals(Boolean::class.javaPrimitiveType, notice[0].returnType)
        assertEquals(5, notice[1].parameterCount)
        assertEquals(Boolean::class.javaPrimitiveType, groups.getValue("route")[0].returnType)
        assertEquals("androidx.fragment.app.Fragment", groups.getValue("route")[1].returnType.name)
        assertEquals(Int::class.javaPrimitiveType, groups.getValue("plain")[0].returnType)
        assertEquals(String::class.java, groups.getValue("plain")[1].returnType)
        assertEquals(Any::class.java, groups.getValue("recommend")[0].returnType)
        assertEquals(Void.TYPE, groups.getValue("recommend")[1].returnType)
        assertEquals(Void.TYPE, groups.getValue("recommend")[2].returnType)
        assertTrue(groups.getValue("cancel").take(2).all { it.parameterCount == 0 && it.returnType == Boolean::class.javaPrimitiveType })
    }

    @Test fun packageInstallerResolvesOnlyTheRegisteredNativeInstaller() {
        val groups = targets(InstallerCleanupSettings.PACKAGE_INSTALLER)
        assertEquals(setOf(InstallerCleanupTargets.SCHEMA, "native"), groups.keys)
        val native = groups.getValue("native")[0]
        assertEquals("android.content.pm.OplusPackageManager", native.declaringClass.name)
        assertEquals("isClosedSuperFirewall", native.name)
        assertEquals(Boolean::class.javaPrimitiveType, native.returnType)
        assertEquals(listOf("android.content.Context"), groups.getValue("native")[3].parameterTypes.map { it.name })
        assertEquals(Boolean::class.javaPrimitiveType, groups.getValue("native")[3].returnType)
        assertEquals(listOf("boolean", "android.content.Intent"), groups.getValue("native")[4].parameterTypes.map { it.name })
        assertEquals("com.android.packageinstaller.DeleteStagedFileOnResult", groups.getValue("native")[5].declaringClass.name)
        val manager = InstrumentationRegistry.getInstrumentation().targetContext.packageManager
        listOf("com.android.packageinstaller.PackageInstallerActivity", "com.android.packageinstaller.v2.ui.InstallLaunch").forEach { name ->
            val activity = manager.getActivityInfo(ComponentName(InstallerCleanupSettings.PACKAGE_INSTALLER, name), PackageManager.ComponentInfoFlags.of(0))
            assertTrue(activity.enabled)
        }
    }
}
