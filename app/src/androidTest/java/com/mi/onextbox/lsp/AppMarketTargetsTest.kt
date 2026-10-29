package com.mi.onextbox.lsp

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dalvik.system.PathClassLoader
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppMarketTargetsTest {
    private fun loader(): ClassLoader {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getApplicationInfo("com.heytap.market",
            PackageManager.ApplicationInfoFlags.of(PackageManager.GET_SHARED_LIBRARY_FILES.toLong()))
        return PathClassLoader((listOf(info.sourceDir) + info.splitSourceDirs.orEmpty() + info.sharedLibraryFiles.orEmpty())
            .joinToString(File.pathSeparator), ClassLoader.getSystemClassLoader())
    }

    @Test fun recommendationBoundaryIsSeparateFromNativeUpdateAndDownloadModels() {
        val loader = loader()
        val prefix = "com.heytap.market.appmanage.core.upgrade.upgrademgr."
        listOf("BaseDownloadFragment", "BaseRecyclerViewDownloadFragment").forEach { name ->
            val base = loader.loadClass(prefix + name)
            val target = base.declaredMethods.single { method ->
                method.returnType == Void.TYPE && method.parameterTypes.map { it.name } == listOf(
                    "java.lang.Object", "com.heytap.cdo.card.domain.dto.ViewLayerWrapDto", "java.lang.String")
            }
            assertFalse(target.isBridge)
            assertTrue(base.declaredClasses.any { it.genericSuperclass?.typeName?.contains("ViewLayerWrapDto") == true })
        }
        listOf("UpdateListAdapter", "ExpUpdateListAdapter").forEach { name ->
            val method = loader.loadClass(prefix + "recyclerview." + name)
                .getDeclaredMethod("addDataAndNotifyChanged", List::class.java)
            assertTrue(method.genericParameterTypes.single().typeName.contains("CardDto"))
        }
    }

    @Test fun updateAndDownloadFragmentsResolveToDifferentSwitches() {
        val loader = loader()
        fun page(name: String) = AppMarketCleanupRules.page(
            generateSequence(loader.loadClass(name) as Class<*>?) { it.superclass }.map { it.name })
        assertEquals(AppMarketCleanupRules.Page.UPDATE, page(
            "com.heytap.market.appmanage.core.upgrade.upgrademgr.recyclerview.AppUpdateFragment"))
        assertEquals(AppMarketCleanupRules.Page.DOWNLOAD, page(
            "com.heytap.market.appmanage.core.download.downloadmgr.DownloadManageFragment"))
        assertNull(page("com.heytap.market.appmanage.core.upgrade.upgrademgr.ignore.UpgradeIgnoreAdapter"))
    }
}
