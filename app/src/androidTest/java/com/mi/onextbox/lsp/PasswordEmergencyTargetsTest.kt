package com.mi.onextbox.lsp

import android.content.pm.PackageManager
import android.widget.Button
import androidx.test.platform.app.InstrumentationRegistry
import dalvik.system.PathClassLoader
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class PasswordEmergencyTargetsTest {
    @Test fun installedSystemUiExposesButtonVisibilityAndCallStateContracts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getApplicationInfo("com.android.systemui",
            PackageManager.ApplicationInfoFlags.of(PackageManager.GET_SHARED_LIBRARY_FILES.toLong()))
        val paths = listOf(info.sourceDir) + info.sharedLibraryFiles.orEmpty()
        val loader = PathClassLoader(paths.joinToString(File.pathSeparator), ClassLoader.getSystemClassLoader())
        val targets = PasswordEmergencyHooker.Targets(loader)
        assertTrue(Button::class.java.isAssignableFrom(targets.button))
        for (method in listOf(targets.attach, targets.detach, targets.visibility, targets.update)) {
            assertEquals(java.lang.Void.TYPE, method.returnType)
        }
        assertArrayEquals(arrayOf(Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType), targets.update.parameterTypes)
        for (host in listOf("OplusKeyguardPinView", "OplusKeyguardFixedPinView", "OplusKeyguardPasswordView")) {
            assertTrue(android.view.View::class.java.isAssignableFrom(
                loader.loadClass("com.oplus.keyguard.security.view.$host")))
        }
    }
}
