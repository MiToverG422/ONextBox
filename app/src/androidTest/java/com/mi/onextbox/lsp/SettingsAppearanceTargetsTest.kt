package com.mi.onextbox.lsp

import android.content.pm.PackageManager
import android.os.Parcelable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dalvik.system.PathClassLoader
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsAppearanceTargetsTest {
    @Test fun installedSettingsSupportsNativeFixedCollapseAndTheDedicatedAccountRow() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getApplicationInfo("com.android.settings",
            PackageManager.ApplicationInfoFlags.of(PackageManager.GET_SHARED_LIBRARY_FILES.toLong()))
        val paths = listOf(info.sourceDir) + info.splitSourceDirs.orEmpty() + info.sharedLibraryFiles.orEmpty()
        val loader = PathClassLoader(paths.joinToString(File.pathSeparator), ClassLoader.getSystemClassLoader())
        val appBar = loader.loadClass("com.google.android.material.appbar.SettingsCollapsableAppBarLayout")
        assertEquals(1, appBar.getField("MODE_FIXED_COLLAPSED").getInt(null))
        assertEquals(2, appBar.getField("MODE_FIXED_EXPANDED").getInt(null))
        assertEquals(Int::class.javaPrimitiveType, appBar.getDeclaredMethod("getMode").returnType)
        assertEquals(Void.TYPE, appBar.getDeclaredMethod("setMode", Int::class.javaPrimitiveType).returnType)
        assertEquals(Parcelable::class.java, appBar.getDeclaredMethod("onSaveInstanceState").returnType)
        val preference = loader.loadClass("com.oplus.settings.feature.homepage.user.UserCenterPreference")
        val holder = loader.loadClass("androidx.preference.PreferenceViewHolder")
        assertEquals(Void.TYPE, preference.getDeclaredMethod("onBindViewHolder", holder).returnType)
        val home = loader.loadClass("com.oplus.settings.feature.homepage.OplusTopLevelSettings")
        assertEquals(Void.TYPE, home.getDeclaredMethod("onResume").returnType)
    }
}
