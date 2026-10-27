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
class BatteryWhitelistRestoreTargetsTest {
    @Test fun installedBatteryResolvesOnlyThePerAppRestoreWithoutExecutingIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getApplicationInfo("com.oplus.battery",
            PackageManager.ApplicationInfoFlags.of(PackageManager.GET_SHARED_LIBRARY_FILES.toLong()))
        val paths = listOf(info.sourceDir) + info.splitSourceDirs.orEmpty() + info.sharedLibraryFiles.orEmpty()
        val loader = PathClassLoader(paths.joinToString(File.pathSeparator), ClassLoader.getSystemClassLoader())
        val targets = ApkDexSession.withSession(info.sourceDir) { open -> BatteryWhitelistRestoreTargets.resolve(open(), loader) }
        val restore = targets.first()
        assertEquals(Void.TYPE, restore.returnType)
        assertTrue(Modifier.isStatic(restore.modifiers))
        assertEquals(listOf("android.os.IDeviceIdleController", "java.lang.String", "android.content.Context"),
            restore.parameterTypes.map { it.name })
        assertTrue(targets.drop(1).isNotEmpty())
        assertEquals(targets.size, targets.distinct().size)
    }
}
