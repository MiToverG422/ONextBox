package com.mi.onextbox.lsp

import android.content.pm.PackageManager
import android.content.Intent
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dalvik.system.PathClassLoader
import java.io.File
import java.lang.reflect.Modifier
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherCompatibilityTargetsTest {
    @Test fun bothLauncherApksResolveBeforeAnyNativeStateIsChanged() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.startActivity(Intent(context, com.mi.onextbox.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val args = InstrumentationRegistry.getArguments()
        val info = context.packageManager.getApplicationInfo("com.android.launcher",
            PackageManager.ApplicationInfoFlags.of(PackageManager.GET_SHARED_LIBRARY_FILES.toLong()))
        val apks = args.getString("launcherApks")?.split('|') ?: listOf(info.sourceDir)
        val cache = File(context.cacheDir, "launcher-compatibility-test")
        val fingerprints = mutableSetOf<String>()
        for (apk in apks) {
            val paths = listOf(apk) + info.sharedLibraryFiles.orEmpty()
            val loader = PathClassLoader(paths.joinToString(File.pathSeparator), ClassLoader.getSystemClassLoader())
            val fingerprint = LauncherDexResolver.fingerprint(File(apk))
            fingerprints += fingerprint
            LauncherDexResolver.bind(loader, apk, cache)
            val failures = mutableListOf<String>()
            fun check(name: String, test: () -> Unit) {
                InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
                    putString("stream", "\nChecking ${File(apk).name}: $name\n")
                })
                runCatching(test).onFailure { failures += "$name: ${it.stackTraceToString()}" }
            }
            check("mode manager") {
                val api = LauncherModeApi(loader)
                assertSame(api.instance.declaringClass, api.instance.returnType)
                assertTrue(Modifier.isStatic(api.instance.modifiers))
                assertEquals(3, setOf(api.simple, api.standard, api.drawer).size)
                for (method in listOf(api.simple, api.standard, api.drawer)) {
                    assertSame(api.instance.returnType, method.declaringClass)
                    assertEquals(java.lang.Boolean.TYPE, method.returnType)
                }
            }
            check("dock search") {
                val target = LauncherDockSearchTargets(loader)
                assertSame(target.base, target.proxy.type)
                assertNotSame(target.domestic, target.export)
                for (type in listOf(target.domestic, target.export)) {
                    assertEquals(java.lang.Boolean.TYPE, target.support(type).returnType)
                    assertEquals(0, target.mutex(type).type.getDeclaredConstructor().parameterCount)
                }
                assertEquals(0, target.domesticInterpolator().type.getDeclaredConstructor().parameterCount)
                assertEquals(java.lang.Boolean.TYPE, target.region.type)
                assertTrue(target.dispatch.parameterTypes[0].isAssignableFrom(target.coroutineScope.returnType))
                assertTrue(target.nativeListener.declaredFields.any {
                    !Modifier.isStatic(it.modifiers) && (it.type.name == "com.android.launcher.settings.c" || it.type == Any::class.java)
                })
            }
            check("dock material gate") { assertEquals(java.lang.Boolean.TYPE, LauncherDynamicTargets.largeDisplay(loader).returnType) }
            check("overview focus") {
                assertEquals(java.lang.Boolean.TYPE, LauncherDynamicTargets.autoFocus(loader).returnType)
                assertEquals(java.lang.Integer.TYPE, LauncherDynamicTargets.focusPage(loader).returnType)
            }
            check("badge drawable") {
                assertTrue(android.graphics.drawable.Drawable::class.java.isAssignableFrom(
                    LauncherDynamicTargets.workFlag(loader).declaringClass))
            }
            check("category assets") {
                assertSame(LauncherDynamicTargets.assets(loader), LauncherDynamicTargets.updateAssets(loader).declaringClass)
                assertTrue(java.util.concurrent.Executor::class.java.isAssignableFrom(LauncherDynamicTargets.executor(loader).type))
            }
            check("predictive playback") {
                val playback = LauncherHomePredictionHooker.playback(loader)
                assertNotSame(playback.animator, playback.type)
                assertTrue(playback.callback.declaredFields.any { it.type == playback.type || it.type == Any::class.java })
                if (playback.inlined) {
                    assertSame(playback.callback, requireNotNull(playback.begin).parameterTypes[1])
                    assertSame(playback.animator, requireNotNull(playback.resolvedUpdate).declaringClass)
                }
            }
            check("material program") {
                val builder = listOf("com.oplus.posteffect.agsl.BlurDrawableShaderBaseStringKt", "com.oplus.posteffect.agsl.a")
                    .firstNotNullOf { name -> runCatching { Class.forName(name, false, loader) }.getOrNull() }
                    .declaredMethods.single {
                        Modifier.isStatic(it.modifiers) && it.returnType == Void.TYPE && it.parameterCount == 6 &&
                            it.parameterTypes[0] == StringBuilder::class.java && it.parameterTypes[5] == List::class.java
                    }.apply { isAccessible = true }
                val corner = requireNotNull(builder.parameterTypes[3].enumConstants).first()
                val source = StringBuilder()
                builder.invoke(null, source, 0, 0, corner, false, emptyList<Any>())
                assertNotNull("Native material must retain a supported sample and shape mask",
                    SoftLightGlassShader.extend(source.toString(), launcher = true, systemUi = false))
            }
            check("grid and memory") {
                assertEquals(LauncherGridTargets.SCHEMA_SIZE, LauncherGridTargets.resolve(loader, apk).values.size)
                assertEquals(java.lang.Boolean.TYPE, LauncherMemoryTargets.resolve(loader, apk).detail.type)
            }
            // A fresh loader also exercises loading persisted descriptors against this exact APK.
            val cachedLoader = PathClassLoader(paths.joinToString(File.pathSeparator), ClassLoader.getSystemClassLoader())
            LauncherDexResolver.bind(cachedLoader, apk, cache)
            check("cached mode") { assertEquals(LauncherModeApi(loader).instance.declaringClass.name,
                LauncherModeApi(cachedLoader).instance.declaringClass.name) }
            android.util.Log.i("ONextBox-LauncherCompatTest", "APK=$apk fingerprint=$fingerprint failures=${failures.size}")
            assertTrue("$apk\n${failures.joinToString("\n")}", failures.isEmpty())
        }
        assertEquals("Distinct APKs must not share cached target metadata", apks.size, fingerprints.size)
    }
}
