package com.mi.onextbox.lsp

import android.content.pm.PackageManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dalvik.system.PathClassLoader
import java.io.File
import java.lang.reflect.Modifier
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.lsposed.hiddenapibypass.HiddenApiBypass

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
        val failures = mutableListOf<String>()
        for (apk in apks) {
            val paths = listOf(apk) + info.sharedLibraryFiles.orEmpty()
            val loader = PathClassLoader(paths.joinToString(File.pathSeparator), ClassLoader.getSystemClassLoader())
            val fingerprint = LauncherDexResolver.fingerprint(File(apk))
            fingerprints += fingerprint
            LauncherDexResolver.bind(loader, apk, cache)
            val priorFailures = failures.size
            fun check(name: String, test: () -> Unit) {
                InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
                    putString("stream", "\nChecking ${File(apk).name}: $name\n")
                })
                runCatching(test).onFailure { failures += "$apk ($name): ${it.stackTraceToString()}" }
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
                val target = LauncherDockSearch17.resolve(loader)
                if (target == null) {
                    val legacy = LauncherSearchTargets.forVersion(SystemAdaptationDetector.current())
                    assertTrue("Launcher requires a compatible constructor/injector dock-search API",
                        legacy.isAvailable(loader))
                    return@check
                }
                assertSame(target.base, target.proxy.type)
                assertNotSame(target.domestic, target.export)
                for (type in listOf(target.domestic, target.export)) {
                    assertEquals(java.lang.Boolean.TYPE, target.support(type).returnType)
                    assertEquals(0, target.mutex(type).type.getDeclaredConstructor().parameterCount)
                    assertEquals(Int::class.javaPrimitiveType, target.cached(type == target.export).type)
                }
                assertNotEquals(target.domesticProbe, target.support(target.domestic))
                assertTrue(android.database.ContentObserver::class.java.isAssignableFrom(target.exportObserver().type))
                assertEquals(0, target.domesticInterpolator().type.getDeclaredConstructor().parameterCount)
                assertEquals(java.lang.Boolean.TYPE, target.region.type)
                assertTrue(target.dispatch.parameterTypes[0].isAssignableFrom(target.coroutineScope.returnType))
                assertTrue(target.nativeListener.declaredFields.any {
                    !Modifier.isStatic(it.modifiers) && (it.type.name == "com.android.launcher.settings.c" || it.type == Any::class.java)
                })
            }
            check("dock material gate") { assertEquals(java.lang.Boolean.TYPE, LauncherDynamicTargets.largeDisplay(loader).returnType) }
            check("overview focus") {
                val gates = LauncherDynamicTargets.autoFocusGates(loader)
                assertTrue(gates.isNotEmpty())
                for (gate in gates) {
                    assertTrue(Modifier.isStatic(gate.modifiers))
                    assertEquals(java.lang.Boolean.TYPE, gate.returnType)
                }
                assertEquals(java.lang.Integer.TYPE, LauncherDynamicTargets.focusPage(loader).returnType)
            }
            check("badge drawable") {
                assertTrue(android.graphics.drawable.Drawable::class.java.isAssignableFrom(
                    LauncherDynamicTargets.workFlag(loader).declaringClass))
                val focus = loader.loadClass("com.android.launcher.Launcher")
                    .getDeclaredMethod("onWindowFocusChanged", Boolean::class.javaPrimitiveType)
                assertEquals(Void.TYPE, focus.returnType)
                for (type in listOf("com.android.launcher3.BubbleTextView", "com.android.launcher3.folder.FolderIcon")) {
                    assertTrue(View::class.java.isAssignableFrom(loader.loadClass(type)))
                }
            }
            check("category assets") {
                assertSame(LauncherDynamicTargets.assets(loader), LauncherDynamicTargets.updateAssets(loader).declaringClass)
                assertTrue(java.util.concurrent.Executor::class.java.isAssignableFrom(LauncherDynamicTargets.executor(loader).type))
            }
            check("category state restore") {
                val manager = loader.loadClass("com.android.launcher3.statemanager.StateManager")
                val reapply = LauncherCategoryStateReapply(manager)
                assertSame(manager, reapply.method.declaringClass)
                val panel = loader.loadClass("com.android.launcher3.allapps.OplusLauncherAllAppsContainerView")
                assertEquals(Void.TYPE, panel.getDeclaredMethod("resetToMainPageWhenSupportCategory").returnType)
            }
            check("predictive playback") {
                val playback = LauncherHomePredictionHooker.playback(loader)
                assertNotSame(playback.animator, playback.type)
                assertTrue(playback.callback.declaredFields.any { it.type == playback.type || it.type == Any::class.java })
                assertSame(playback.animator, playback.begin.declaringClass)
                assertArrayEquals(arrayOf(playback.motion, playback.callback), playback.begin.parameterTypes)
                if (playback.inlined) {
                    assertSame(playback.animator, playback.update.declaringClass)
                    assertArrayEquals(arrayOf(java.lang.Float.TYPE), playback.update.parameterTypes)
                } else {
                    assertTrue(playback.type.declaredFields.any { it.type == playback.animator })
                    assertSame(playback.callback, playback.update.declaringClass)
                    assertArrayEquals(arrayOf(android.window.BackEvent::class.java), playback.update.parameterTypes)
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
            check("grid menu and migration") {
                val menu = LauncherGridTargets.resolve(loader, apk)
                assertEquals(LauncherGridTargets.SCHEMA_SIZE, menu.values.size)
                val migration = LauncherGridApplyTargets.resolve(loader, apk)
                assertSame(migration.taskConstructor.declaringClass, migration.completed.declaringClass)
                assertEquals(3, migration.apply.parameterTypes.drop(1).size)
            }
            check("recents memory") {
                assertEquals(java.lang.Boolean.TYPE, LauncherMemoryTargets.resolve(loader, apk).detail.type)
            }
            check("app open and close animation") {
                // This test runs in the module app's process. The system launcher can
                // read the hidden SurfaceControl dimensions and compare surface handles.
                assertTrue(HiddenApiBypass.addHiddenApiExemptions("Landroid/view/SurfaceControl;"))
                val targets = LauncherIosMorphTargets(LauncherIosMotionTargets(loader))
                assertEquals(Int::class.javaPrimitiveType, targets.surfaceWidthMethod.returnType)
                assertEquals(Int::class.javaPrimitiveType, targets.surfaceHeightMethod.returnType)
                assertEquals(5, targets.routes.size)
                assertTrue(targets.sourceMethods.any { it.name == "startAppLaunchWindowAnim" })
                assertTrue(targets.sourceMethods.any { it.name == "startAppCloseWindowAnim" })
            }
            // A fresh loader also exercises loading persisted descriptors against this exact APK.
            val cachedLoader = PathClassLoader(paths.joinToString(File.pathSeparator), ClassLoader.getSystemClassLoader())
            LauncherDexResolver.bind(cachedLoader, apk, cache)
            check("cached mode") { assertEquals(LauncherModeApi(loader).instance.declaringClass.name,
                LauncherModeApi(cachedLoader).instance.declaringClass.name) }
            android.util.Log.i("ONextBox-LauncherCompatTest", "APK=$apk fingerprint=$fingerprint failures=${failures.size - priorFailures}")
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
        assertEquals("Distinct APKs must not share cached target metadata", apks.size, fingerprints.size)
    }
}
