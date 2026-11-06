package com.mi.onextbox.lsp

import android.content.pm.PackageManager
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dalvik.system.PathClassLoader
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FaceAuthCapsuleNativeTargetsTest {
    private fun loader(): ClassLoader {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getApplicationInfo("com.android.systemui",
            PackageManager.ApplicationInfoFlags.of(PackageManager.GET_SHARED_LIBRARY_FILES.toLong()))
        return PathClassLoader((listOf(info.sourceDir) + info.splitSourceDirs.orEmpty() + info.sharedLibraryFiles.orEmpty())
            .joinToString(File.pathSeparator), ClassLoader.getSystemClassLoader())
    }

    @Test fun nativeDispatcherAndAuthenticationTargetsHaveTheExpectedSignatures() {
        val loader = loader()
        val location = loader.loadClass("com.android.systemui.statusbar.phone.StatusBarLocation")
        val dispatcher = loader.loadClass("com.oplus.systemui.statusbar.seeding.HiddenIconDispatcherImpl")
        assertNotNull(dispatcher.getDeclaredMethod("setHiddenIconAreas", location, List::class.java))
        assertNotNull(dispatcher.getDeclaredMethod("getHiddenAreas", location))
        assertNotNull(location.getDeclaredField("HOME").get(null))
        assertNotNull(location.getDeclaredField("KEYGUARD").get(null))
        val monitor = loader.loadClass("com.android.keyguard.KeyguardUpdateMonitor")
        assertEquals(Boolean::class.javaPrimitiveType, monitor.getDeclaredMethod("isFaceAuthRunning").returnType)
        assertNotNull(monitor.getDeclaredMethod("onFaceAuthenticated", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType))
        assertNotNull(monitor.getDeclaredMethod("onFingerprintAuthenticated", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType))
        assertNotNull(monitor.getDeclaredField("mUserFingerprintAuthenticated"))
        assertNotNull(monitor.getDeclaredMethod("getUserUnlockedWithBiometric", Int::class.javaPrimitiveType))
    }

    @Test fun lockScreenVisibilityAndAcceptedCredentialTargetsExistWithoutFaceEnrollment() {
        val loader = loader()
        val manager = loader.loadClass("com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager")
        val monitor = loader.loadClass("com.android.keyguard.KeyguardUpdateMonitor")
        assertEquals(monitor, manager.getDeclaredField("mKeyguardUpdateManager").type)
        assertNotNull(manager.getDeclaredMethod("updateStates"))
        assertNotNull(manager.getDeclaredMethod("onStartedWakingUp"))
        val security = loader.loadClass("com.android.keyguard.KeyguardSecurityContainerController")
        val mode = loader.loadClass("com.android.keyguard.KeyguardSecurityModel\$SecurityMode")
        assertEquals(monitor, security.getDeclaredField("mUpdateMonitor").type)
        val finish = security.getDeclaredMethod("showNextSecurityScreenOrFinish", Boolean::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, mode)
        assertEquals(Boolean::class.javaPrimitiveType, finish.returnType)
        assertTrue(mode.enumConstants!!.map { (it as Enum<*>).name }.containsAll(listOf("PIN", "Password", "Pattern")))
    }

    @Test fun actualNativeAvoidanceHidesOnlyIconsInsideTheOccupiedAreas() {
        val loader = loader()
        val dispatcher = loader.loadClass("com.oplus.systemui.statusbar.seeding.HiddenIconDispatcher")
        val companion = dispatcher.getDeclaredField("Companion").get(null)
        val pair = loader.loadClass("kotlin.Pair")
        val constructor = pair.getConstructor(Any::class.java, Any::class.java)
        val method = companion.javaClass.getDeclaredMethod("isInAreas", ArrayList::class.java, pair)
        fun covered(left: Int, right: Int) = method.invoke(companion,
            arrayListOf(Rect(460, 0, 980, 160)), constructor.newInstance(left, right)) as Boolean
        assertTrue(covered(950, 1030))
        assertTrue(covered(430, 490))
        assertFalse(covered(1030, 1110))
        assertFalse(covered(200, 400))
    }
}
