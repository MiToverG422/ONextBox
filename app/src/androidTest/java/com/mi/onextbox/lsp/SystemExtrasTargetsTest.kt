package com.mi.onextbox.lsp

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dalvik.system.PathClassLoader
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.luckypray.dexkit.DexKitBridge

@RunWith(AndroidJUnit4::class)
class SystemExtrasTargetsTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun loader(pkg: String): ClassLoader {
        val info = context.packageManager.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(PackageManager.GET_SHARED_LIBRARY_FILES.toLong()))
        return PathClassLoader((listOf(info.sourceDir) + info.splitSourceDirs.orEmpty() + info.sharedLibraryFiles.orEmpty())
            .joinToString(File.pathSeparator), ClassLoader.getSystemClassLoader())
    }
    @Test fun nativeWeatherControlsArePresent() {
        val loader = loader(SystemExtrasSettings.WEATHER)
        assertNotNull(loader.loadClass("com.oplus.weather.uiconfig.UIConfigManager").getDeclaredMethod("getLocalDay15ExpandConfig"))
        val creator = loader.loadClass("com.oplus.weather.main.view.itemview.CctvWeatherForecastItemCreator")
        assertEquals(1, creator.declaredMethods.count { it.name == "create" && !it.isBridge })
        val local = loader.loadClass("com.oplus.weather.utils.LocalUtils")
        assertEquals(2, local.declaredMethods.count { it.name == "jumpToBrowser" && it.returnType == Void.TYPE })
        assertNotNull(loader.loadClass("com.oplus.weather.ad.OperationsCardAdManager").getDeclaredMethod("canRequestAds"))
    }
    @Test fun engineerHasAllIndividualKeyDisplayModels() {
        val loader = loader(SystemExtrasSettings.ENGINEER)
        val model = loader.loadClass("com.oplus.engineermode.security.KeyInstance")
        val view = loader.loadClass("com.oplus.engineermode.security.view.WriteKeyItemView")
        assertNotNull(view.getDeclaredMethod("initKeyStatus", model, java.util.concurrent.Executor::class.java))
        assertEquals(android.os.Handler::class.java, view.getDeclaredField("initKeyStatusHandler").type)
        EngineerKeySettings.entries.forEach { key ->
            val clazz = loader.loadClass("com.oplus.engineermode.security.KeyInstanceImpl\$${key.nativeClass}")
            assertTrue(model.isAssignableFrom(clazz))
        }
        assertNotNull(loader.loadClass("com.oplus.engineermode.entrance.EngineerFragmentCompat").getDeclaredMethod("removeUnnecessaryPreference", String::class.java))
    }
    @Test fun nativePickerEntriesAcceptOriginalSelectionParameters() {
        val loader = loader(SystemExtrasSettings.PICKER)
        assertNotNull(loader.loadClass("com.android.photopicker.MainActivity").getDeclaredMethod("onCreate", Bundle::class.java))
        listOf("android.provider.action.PICK_IMAGES", "android.intent.action.GET_CONTENT", "android.provider.action.USER_SELECT_IMAGES_FOR_APP").forEach { action ->
            val name = "com.android.providers.media.photopicker.${NativePhotoPickerRules.target(action)}"
            val info = context.packageManager.getActivityInfo(android.content.ComponentName("com.android.providers.media.module", name), 0)
            assertTrue(info.enabled && info.exported)
        }
        val original = Intent("android.provider.action.PICK_IMAGES").apply {
            type = "image/*"; putExtra("android.provider.extra.PICK_IMAGES_MAX", 5)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/png", "image/jpeg"))
            clipData = ClipData.newRawUri("test", Uri.parse("content://test/images/1"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val forwarded = Intent(original).addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
        assertEquals(5, forwarded.getIntExtra("android.provider.extra.PICK_IMAGES_MAX", 0))
        assertArrayEquals(original.getStringArrayExtra(Intent.EXTRA_MIME_TYPES), forwarded.getStringArrayExtra(Intent.EXTRA_MIME_TYPES))
        assertEquals(original.clipData?.getItemAt(0)?.uri, forwarded.clipData?.getItemAt(0)?.uri)
        assertTrue(forwarded.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }
    @Test fun batteryAssistantIsResolvedByItsNativePreferenceGate() {
        System.loadLibrary("dexkit")
        DexKitBridge.create(context.packageManager.getApplicationInfo("com.oplus.battery", 0).sourceDir).use { dex ->
            val gate = dex.findMethod { matcher {
                usingStrings("oplus_battery_agent_debug_force_entry"); returnType = "boolean"; paramTypes(Context::class.java)
            } }.single()
            val caller = gate.callers.single { it.className == gate.className && it.returnTypeName == "boolean" && it.paramTypeNames == listOf("android.content.Context") }
            assertEquals("i", caller.name)
            assertTrue(caller.callers.any { it.className.startsWith("i9.") })
        }
    }
}
