package com.mi.onextbox.lsp

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dalvik.system.PathClassLoader
import java.io.File
import java.lang.reflect.Modifier
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherGridApplyTargetsTest {
    @Test fun installedLauncherMigrationTargetsResolveWithoutStartingAMigration() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = runCatching {
            context.packageManager.getApplicationInfo("com.android.launcher",
                PackageManager.ApplicationInfoFlags.of(PackageManager.GET_SHARED_LIBRARY_FILES.toLong()))
        }.getOrNull()
        assumeNotNull(info)
        val installed = requireNotNull(info)
        val paths = listOf(installed.sourceDir) + installed.splitSourceDirs.orEmpty() +
            installed.sharedLibraryFiles.orEmpty()
        val loader = PathClassLoader(paths.joinToString(File.pathSeparator), ClassLoader.getSystemClassLoader())
        val resolved = LauncherGridApplyTargets.resolve(loader)
        assertEquals(LauncherGridApplyTargets.SCHEMA_SIZE, resolved.values.size)
        assertEquals("applyLayoutSettings", resolved.apply.name)
        assertSame(resolved.taskConstructor.declaringClass, resolved.completed.declaringClass)
        assertArrayEquals(arrayOf(Any::class.java), resolved.completed.parameterTypes)
        assertEquals(Void.TYPE, resolved.completed.returnType)
        assertFalse(Modifier.isStatic(resolved.completed.modifiers))
        assertFalse(Modifier.isAbstract(resolved.completed.modifiers))
    }
}
