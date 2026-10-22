package com.mi.onextbox.lsp

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class QsDeviceCardCornerContractTest {
    private fun source(name: String) = listOf(File("src/main/java/com/mi/onextbox/lsp/$name.kt"),
        File("app/src/main/java/com/mi/onextbox/lsp/$name.kt")).first { it.isFile }.readText()

    @Test fun deviceSdkCardsAreSeparateFromScenarioAndRegularTiles() {
        val hook = source("QsDeviceCardCorners")
        assertTrue(source("QsTileCornerHooker").contains("QsDeviceCardCorners.hook(loader)"))
        assertTrue(hook.contains("com.oplus.deviceplugin.sdk.ui.view.separatecardview."))
        for (card in listOf("SquareDeviceCardView", "SquareEntranceCardView", "RectangleDeviceCardView", "RectangleEntranceCardView", "NoDeviceEntranceCardView")) {
            assertTrue(card, hook.contains(card))
        }
        assertTrue(hook.contains("\"RectangleDeviceCardView\" to \"rectangleCoLayout\""))
        assertTrue(hook.contains("view.javaClass.name !in classNames"))
        assertFalse(hook.contains("View::class.java"))
        assertFalse(hook.contains("getStatic"))
    }
    @Test fun nativePluginPaintAndBothClippingLevelsAreUpdatedWithoutChangingCardData() {
        val hook = source("QsDeviceCardCorners")
        assertTrue(hook.contains("\"getBlurDrawable\""))
        assertTrue(hook.contains("\"setCornerRadius\", radius, weight"))
        assertTrue(hook.contains("\"getCornerWeight\", drawable"))
        assertTrue(hook.contains("clip(view, drawable, radius, weight)"))
        assertTrue(hook.contains("view.findViewById<View>(id)?.let { clip(it, drawable, radius, weight) }"))
        assertFalse(hook.contains("view.clipToOutline = true"))
        assertTrue(hook.contains("if (!view.clipToOutline) return"))
        assertTrue(hook.contains("if (current != radius || currentWeight != weight)"))
        assertTrue(hook.contains("usesDrawable(drawable)"))
        for (unsafe in listOf("setDeviceState", "setShapeType", "setBackground(", "setLayoutParams", "setPadding")) {
            assertFalse(unsafe, hook.contains(unsafe))
        }
    }
    @Test fun disableAndReloadRecomputeNativeThemeAndRestoreOnlyOwnedClips() {
        val hook = source("QsDeviceCardCorners")
        assertTrue(hook.contains("HotReloadLifecycle.register(\"qs:device-card-corners\", detach ="))
        assertTrue(hook.contains("Reflect.callMethod(view, \"g\")"))
        assertTrue(hook.contains("target.outlineProvider === state.custom"))
        assertTrue(hook.contains("target.clipToOutline = state.clipped"))
        assertTrue(hook.contains("nativeShape.set(true)"))
        assertTrue(hook.contains("finally { nativeShape.remove() }"))
        assertTrue(hook.contains("applied.remove(view)"))
        assertTrue(hook.contains("observers::watch"))
    }
    @Test fun nativeBackgroundRebuildRefreshesChildrenEvenIfOuterProviderIsUnchanged() {
        val hook = source("QsTileCornerHooker")
        assertTrue(hook.contains("\"onDrawableUpdate\""))
        assertTrue(hook.contains("refreshChildren = true"))
        assertTrue(hook.contains("if (refreshChildren)"))
        assertTrue(hook.contains("Reflect.callMethod(drawable, \"invalidatePath\")"))
        assertTrue(hook.contains("bg.invalidate()"))
    }
    @Test fun runtimeDiscoveryUsesOnlyTheQsRootAndActualNativeViewInstances() {
        val hook = source("QsDeviceCardCorners")
        assertTrue(hook.contains("com.oplus.systemui.plugins.qs.OplusQSRootView"))
        assertTrue(hook.contains("if (view.javaClass.name in classNames)"))
        assertTrue(hook.contains("observers.watch(view)"))
        assertTrue(source("QsTileCornerHooker").contains("QsDeviceCardCorners.discover(view)"))
        val discovery = source("QsCornerHierarchyObservers")
        assertTrue(discovery.contains("ViewTreeObserver.OnGlobalLayoutListener"))
        assertFalse(discovery.contains("OnPreDrawListener"))
        assertTrue(discovery.contains("removeOnGlobalLayoutListener(this)"))
        assertTrue(discovery.contains("WeakReference(root)"))
        assertTrue(discovery.contains("HotReloadLifecycle.registerReplay"))
    }
    @Test fun newZeroRadiusPluginDrawablesStillRefreshTheirUnderlyingBlurCacheOnce() {
        val hook = source("QsDeviceCardCorners")
        assertTrue(hook.contains("WeakHashMap<Drawable, Float>()"))
        assertTrue(hook.contains("painted[drawable] != radius"))
        assertTrue(hook.contains("Reflect.callMethod(drawable, \"invalidatePath\")"))
        assertTrue(hook.contains("painted[drawable] = radius"))
        assertTrue(hook.contains("painted.remove(drawable)"))
        assertTrue(hook.contains("painted.clear()"))
    }
}
