package com.mi.onextbox.lsp

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dalvik.system.PathClassLoader
import java.io.File
import java.lang.reflect.Modifier
import java.util.concurrent.CountDownLatch
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EuiccCompatibilityTargetsTest {
    @Test fun resolvesBothLpaGenerationsWithoutStartingAnEsimTransaction() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val installed = context.packageManager.getApplicationInfo("com.oplus.euicc",
            PackageManager.ApplicationInfoFlags.of(PackageManager.GET_SHARED_LIBRARY_FILES.toLong()))
        val apks = InstrumentationRegistry.getArguments().getString("euiccApks")?.split('|')
            ?: listOf(installed.sourceDir)
        for ((index, source) in apks.withIndex()) {
            val apk = File(context.codeCacheDir, "euicc-compatibility-$index.apk")
            apk.delete()
            try {
                apk.outputStream().use { output ->
                    check(apk.setReadOnly())
                    File(source).inputStream().use { it.copyTo(output) }
                }
                val paths = listOf(apk.path) + installed.sharedLibraryFiles.orEmpty()
                val loader = PathClassLoader(paths.joinToString(File.pathSeparator), ClassLoader.getSystemClassLoader())
                fun report(message: String) = instrumentation.sendStatus(0, Bundle().apply {
                    putString("stream", "\n${File(source).name}: $message\n")
                })
                report("region and profile limit")
                val checker = loader.loadClass("com.oplus.euicc.DownloadChecker")
                for (name in listOf("c", "d")) {
                    assertEquals(java.lang.Integer.TYPE, checker.getDeclaredMethod(name, String::class.java).returnType)
                }
                val limit = EuiccProfileLimitTargets.download(loader, apk.path)
                assertSame(checker, limit.check.declaringClass)
                assertTrue(limit.callers.isNotEmpty())
                report("confirmation UI, import flow and SDK response")
                val confirmation = EuiccCompatibilityTargets.confirmation(loader, apk.path)
                assertTrue(confirmation.downloads.isNotEmpty())
                confirmation.downloads.forEach { assertEquals(1, it.parameterTypes.count { type -> type == String::class.java }) }
                assertEquals(java.lang.Long.TYPE, confirmation.agent.returnType)
                val service = loader.loadClass("com.oplus.euicc.sdk.profileassistant.internal.AgentService")
                assertEquals(1, service.declaredFields.count { !Modifier.isStatic(it.modifiers) && it.type == String::class.java })
                assertEquals(1, service.declaredFields.count { it.type == CountDownLatch::class.java })
                confirmation.callbacks.forEach { callback ->
                    val responseType = callback.parameterTypes[1]
                    assertTrue(java.io.Serializable::class.java.isAssignableFrom(responseType))
                    assertTrue(responseType.isInterface)
                }
                report("profile summaries, chip information and SM-DP+/SM-DS diagnostics")
                val diagnostics = EuiccCompatibilityTargets.diagnostics(loader, apk.path)
                assertTrue(Modifier.isStatic(diagnostics.factory.modifiers))
                assertEquals(java.lang.Integer.TYPE, diagnostics.limit.returnType)
                assertSame(diagnostics.limit.declaringClass, diagnostics.provider.returnType)
                assertSame(diagnostics.manager.returnType, diagnostics.info.declaringClass)
                assertSame(diagnostics.manager.returnType, diagnostics.addresses.declaringClass)
                assertEquals(java.lang.Boolean.TYPE, diagnostics.profiles.returnType)
                val info = diagnostics.infoModel.getDeclaredConstructor(String::class.java, String::class.java,
                    String::class.java, java.lang.Long.TYPE, java.lang.Long.TYPE)
                    .newInstance("pp", "svn", "firmware", 123L, 456L)
                for ((method, expected) in mapOf("a" to "pp", "b" to "firmware", "c" to 123L, "d" to 456L, "e" to "svn")) {
                    assertEquals(expected, diagnostics.infoModel.getMethod(method).invoke(info))
                }
                val address = diagnostics.addressModel.getDeclaredConstructor(String::class.java, String::class.java)
                    .newInstance("smdp.invalid", "smds.invalid").toString()
                assertTrue(address.contains("mSmdpAddress='smdp.invalid'"))
                assertTrue(address.contains("mSmdsAddress='smds.invalid'"))
                report("all LPA contracts resolved")
            } finally {
                apk.delete()
            }
        }
    }

    @Test fun resolvesBothPhoneAddEntriesWithoutChangingTheirProfileCounts() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val installed = context.packageManager.getApplicationInfo("com.android.phone",
            PackageManager.ApplicationInfoFlags.of(PackageManager.GET_SHARED_LIBRARY_FILES.toLong()))
        val apks = InstrumentationRegistry.getArguments().getString("phoneApks")?.split('|')
            ?: listOf(installed.sourceDir)
        for ((index, source) in apks.withIndex()) {
            val apk = File(context.codeCacheDir, "euicc-phone-compatibility-$index.apk")
            apk.delete()
            try {
                apk.outputStream().use { output ->
                    check(apk.setReadOnly())
                    File(source).inputStream().use { it.copyTo(output) }
                }
                val paths = listOf(apk.path) + installed.sharedLibraryFiles.orEmpty()
                val loader = PathClassLoader(paths.joinToString(File.pathSeparator), ClassLoader.getSystemClassLoader())
                val entry = EuiccProfileLimitTargets.addEntry(loader, apk.path)
                assertEquals("onPreferenceTreeClick", entry.click.name)
                assertEquals(java.lang.Boolean.TYPE, entry.click.returnType)
                assertSame(entry.controller.type, entry.profileCount.declaringClass)
                assertEquals(java.lang.Integer.TYPE, entry.profileCount.type)
                assertFalse(Modifier.isFinal(entry.profileCount.modifiers))
                assertEquals("getKey", entry.preferenceKey.name)
                assertEquals("getContext", entry.context.name)
                instrumentation.sendStatus(0, Bundle().apply {
                    putString("stream", "\n${File(source).name}: add-eSIM entry resolved\n")
                })
            } finally {
                apk.delete()
            }
        }
    }
}
