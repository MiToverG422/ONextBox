package com.mi.onextbox.lsp

import android.content.pm.PackageManager
import android.content.Context
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dalvik.system.PathClassLoader
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationCardTargetsTest {
    class Controller { var state = 1 }
    class Manager { @JvmField val statusBarStateController = Controller() }
    class Layer {
        var rendered = 0f to 0f
        fun getExt(): Any = this
        fun setRadius(top: Float, bottom: Float, smooth: Boolean) {
            require(smooth)
            rendered = top to bottom
        }
    }
    class CustomRow(context: Context) : View(context) {
        companion object { @JvmField val BASE_VALUE = "BaseValue" }
        @JvmField val viewBlurManager = Manager()
        @JvmField val defaultCornerRadius = 36f
        val layer = Layer()
        var maxRadius = defaultCornerRadius
        var fraction = 1f
        fun getMBackgroundNormal() = layer
        fun getTopCornerRadius() = maxRadius * fraction
        fun getBottomCornerRadius() = maxRadius * fraction
        fun isExpandAnimationRunning() = false
        fun requestRoundness(top: Float, bottom: Float, source: String, animate: Boolean) {
            require(top == bottom && source == BASE_VALUE && !animate)
            fraction = top
        }
        fun updateBaseRoundness() { fraction = defaultCornerRadius / maxRadius }
    }

    @Test fun customCardCanChangeSceneAndRestoreTheNativeBackgroundInPlace() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val row = CustomRow(instrumentation.targetContext)
            val corners = CustomNotificationCorners(row.javaClass)
            assertEquals(SystemUiAppearance.Lock, corners.scope(row))
            row.maxRadius = 18f
            corners.apply(row, 18f, true)
            assertEquals(18f to 18f, row.layer.rendered)
            row.viewBlurManager.statusBarStateController.state = 0
            assertEquals(SystemUiAppearance.Shade, corners.scope(row))
            row.maxRadius = 42f
            corners.apply(row, 42f, true)
            assertEquals(42f to 42f, row.layer.rendered)
            row.maxRadius = row.defaultCornerRadius
            corners.restore(row)
            assertEquals(36f to 36f, row.layer.rendered)
        }
    }

    @Test fun installedColorOsCustomNotificationAndFullscreenPopupExposeTheirNativeMaterials() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.packageManager.getApplicationInfo("com.android.systemui", PackageManager.GET_SHARED_LIBRARY_FILES)
        val files = listOf(app.sourceDir) + app.splitSourceDirs.orEmpty() + app.sharedLibraryFiles.orEmpty()
        val loader = PathClassLoader(files.joinToString(File.pathSeparator), ClassLoader.getSystemClassLoader())
        CustomNotificationCorners(loader.loadClass("com.oplus.systemui.statusbar.notification.customcard.OplusCustomRow"))
        val banner = loader.loadClass("com.oplus.systemui.notification.interruption.fullscreenbanner.view.FullScreenBannerContainer")
        banner.getDeclaredField("mSpotLightRadius")
        banner.getDeclaredField("mFullScreenBannerBg")
        banner.getDeclaredField("mShadowEdgeController").type.getMethod("refresh", Float::class.javaPrimitiveType)
        val proxy = banner.getDeclaredField("mViewBlurProxy").type
        proxy.getMethod("applyBlurConfig")
        val blur = proxy.getMethod("getBlurConfig").returnType
        for (corner in listOf("CornerRadius", "LeftTopCornerRadius", "RightTopCornerRadius", "LeftBottomCornerRadius", "RightBottomCornerRadius")) {
            blur.getMethod("get$corner")
            blur.getMethod("set$corner", Float::class.javaPrimitiveType)
        }
        blur.getMethod("getGradientStrokeCornerParam").returnType.getMethod("setRadius", Float::class.javaPrimitiveType)
    }
}
