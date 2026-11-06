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
class StatusBarPersistenceTargetsTest {
    private fun loader(): ClassLoader {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getApplicationInfo("com.android.systemui",
            PackageManager.ApplicationInfoFlags.of(PackageManager.GET_SHARED_LIBRARY_FILES.toLong()))
        return PathClassLoader((listOf(info.sourceDir) + info.splitSourceDirs.orEmpty() + info.sharedLibraryFiles.orEmpty())
            .joinToString(File.pathSeparator), ClassLoader.getSystemClassLoader())
    }

    @Test fun currentSystemUiExposesAllFiveVisibilityCombinersAndNativeModelContracts() {
        val loader = loader()
        val base = "com.android.systemui.statusbar.pipeline.shared.ui.viewmodel.HomeStatusBarViewModelImpl"
        for ((suffix, count) in listOf("isClockVisible" to 3, "isSystemInfoVisible" to 3,
                "shouldShowOperatorNameView" to 4, "isNotificationIconContainerVisible" to 4)) {
            assertNotNull(StatusBarVisibilityAdapter.combiner(loader, "$base\$$suffix\$1", count))
        }
        assertNotNull(StatusBarVisibilityAdapter.combiner(loader,
            "com.oplus.systemui.statusbar.pipeline.shared.ui.viewmodel.OplusHomeStatusBarViewModelImpl\$isSeedlingContainerVisible\$1", 6))
        assertNotNull(StatusBarVisibilityAdapter.load(loader))
    }

    @Test fun nativeOperatorNameRemainsABooleanAndHonorsCarrierAndHomeVisibility() {
        val loader = loader()
        val adapter = StatusBarVisibilityAdapter.load(loader)
        val method = StatusBarVisibilityAdapter.combiner(loader,
            "com.android.systemui.statusbar.pipeline.shared.ui.viewmodel.HomeStatusBarViewModelImpl\$shouldShowOperatorNameView\$1", 4)
        val flagsType = loader.loadClass("com.android.systemui.statusbar.pipeline.shared.domain.model.StatusBarDisableFlagsVisibilityModel")
        val constructor = flagsType.getConstructor(*Array(4) { Boolean::class.javaPrimitiveType })
        val original = constructor.newInstance(false, false, false, true)
        // OEM R8 can inline lambda constructors. Allocate only this isolated test fixture;
        // the real hook always receives the existing native lambda instance.
        val unsafeType = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeType.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        val instance = unsafeType.getMethod("allocateInstance", Class::class.java).invoke(unsafe, method.declaringClass)
        val allowed = adapter.allow(original, "isSystemInfoAllowed")
        assertEquals(false, method.invoke(instance, true, original, true, null))
        assertEquals(true, method.invoke(instance, true, allowed, true, null))
        assertEquals(false, method.invoke(instance, false, allowed, true, null))
        assertEquals(false, method.invoke(instance, true, allowed, false, null))
        assertFalse(flagsType.getDeclaredField("isSystemInfoAllowed").getBoolean(original))
        assertTrue(flagsType.getDeclaredField("animate").getBoolean(allowed))
    }

    @Test fun nativeVisibilityCopiesPreserveTheSourceAndAnimationDecision() {
        val adapter = StatusBarVisibilityAdapter.load(loader())
        val original = adapter.visibilityType.getConstructor(Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            .newInstance(4, true)
        val copy = adapter.withVisibility(original, 0)
        assertEquals(4, adapter.visibility(original))
        assertEquals(0, adapter.visibility(copy))
        assertTrue(adapter.visibilityType.getDeclaredField("shouldAnimateChange").getBoolean(copy))
    }
}
