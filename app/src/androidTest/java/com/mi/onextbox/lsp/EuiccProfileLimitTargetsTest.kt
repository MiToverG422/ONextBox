package com.mi.onextbox.lsp

import android.content.Intent
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
class EuiccProfileLimitTargetsTest {
    @Test fun resolvesOnlyTheSettingsCountAndDownloadGateFromInstalledApks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.startActivity(Intent(context, com.mi.onextbox.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        fun host(packageName: String): Pair<String, ClassLoader> {
            val info = context.packageManager.getApplicationInfo(packageName,
                PackageManager.ApplicationInfoFlags.of(PackageManager.GET_SHARED_LIBRARY_FILES.toLong()))
            val paths = listOf(info.sourceDir) + info.sharedLibraryFiles.orEmpty()
            return info.sourceDir to PathClassLoader(paths.joinToString(File.pathSeparator), ClassLoader.getSystemClassLoader())
        }
        val (phoneApk, phoneLoader) = host("com.android.phone")
        val add = EuiccProfileLimitTargets.addEntry(phoneLoader, phoneApk)
        assertEquals("onPreferenceTreeClick", add.click.name)
        assertEquals(java.lang.Boolean.TYPE, add.click.returnType)
        assertEquals("androidx.preference.Preference", add.click.parameterTypes.single().name)
        assertSame(add.controller.type, add.profileCount.declaringClass)
        assertEquals(java.lang.Integer.TYPE, add.profileCount.type)
        assertFalse(Modifier.isStatic(add.profileCount.modifiers))
        assertFalse(Modifier.isFinal(add.profileCount.modifiers))
        assertEquals("getKey", add.preferenceKey.name)
        assertEquals("getContext", add.context.name)
        val (lpaApk, lpaLoader) = host("com.oplus.euicc")
        val download = EuiccProfileLimitTargets.download(lpaLoader, lpaApk)
        assertEquals("com.oplus.euicc.DownloadChecker", download.check.declaringClass.name)
        assertEquals(java.lang.Boolean.TYPE, download.check.returnType)
        assertEquals(0, download.check.parameterCount)
        assertTrue(download.callers.isNotEmpty())
        assertTrue(download.callers.any {
            it.returnType == java.lang.Integer.TYPE && it.parameterTypes.contentEquals(arrayOf(String::class.java))
        })
        android.util.Log.i("ONextBox-EuiccTest", "Add-entry and download targets verified without changing SIM state")
    }
}
