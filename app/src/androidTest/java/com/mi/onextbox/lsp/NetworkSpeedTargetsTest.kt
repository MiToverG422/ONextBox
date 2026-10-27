package com.mi.onextbox.lsp

import android.content.pm.PackageManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dalvik.system.PathClassLoader
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NetworkSpeedTargetsTest {
    @Test fun installedSystemUiProvidesNativeRefreshAndDisplayInterfaces() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getApplicationInfo("com.android.systemui",
            PackageManager.ApplicationInfoFlags.of(PackageManager.GET_SHARED_LIBRARY_FILES.toLong()))
        val paths = listOf(info.sourceDir) + info.splitSourceDirs.orEmpty() + info.sharedLibraryFiles.orEmpty()
        val loader = PathClassLoader(paths.joinToString(File.pathSeparator), ClassLoader.getSystemClassLoader())
        val controller = Class.forName(NetworkSpeedHooker.CONTROLLER, false, loader)
        assertEquals(Void.TYPE, controller.getDeclaredMethod("postUpdateNetworkSpeedDelay", Long::class.javaPrimitiveType).returnType)
        val view = Class.forName(NetworkSpeedHooker.VIEW, false, loader)
        val state = Class.forName(NetworkSpeedHooker.STATE, false, loader)
        assertTrue(FrameLayout::class.java.isAssignableFrom(view))
        assertEquals(TextView::class.java, view.getDeclaredField("mSpeedNumber").type)
        assertEquals(TextView::class.java, view.getDeclaredField("mSpeedUnit").type)
        assertEquals(Void.TYPE, view.getDeclaredMethod("applyNetworkState", state).returnType)
        assertEquals(Long::class.javaPrimitiveType, state.getDeclaredMethod("getSpeedText").returnType)
    }
}
