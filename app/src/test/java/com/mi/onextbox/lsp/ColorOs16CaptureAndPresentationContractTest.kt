package com.mi.onextbox.lsp

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ColorOs16CaptureAndPresentationContractTest {
    @Test fun screenshotVariantsKeepSeparateAuditedTargetsAndScopedCapturePolicy() {
        val capture = source("AodScreenshotHooker.kt")
        assertTrue(capture.contains("fun hookColorOs16(loader: ClassLoader)"))
        assertTrue(capture.contains("com.oplus.screenshot.common.core.e"))
        assertTrue(capture.contains("com.oplus.screenshot.screenshot.task.d"))
        assertTrue(capture.contains("com.oplus.screenshot.common.utils.p"))
        assertTrue(capture.contains("com.oplus.screenshot.screenshot.task.TaskCapture"))
        assertTrue(capture.contains("scope.withValue(active) { chain.proceed() }"))
        assertTrue(capture.contains("KeyguardCaptureRules.allowAodScreenshot("))
        val floating = source("HideScreenshotFloatingHooker.kt")
        assertTrue(floating.contains("\"com.oplus.screenshot.common.util.o0\", \"e\", \"q5.c\", \"m\""))
        assertTrue(floating.contains("\"com.oplus.screenshot.common.util.WindowExcludeUtils\", \"f\", \"k6.e\", \"n\""))
        assertTrue(floating.contains("merged.none { it === layer }"))
    }

    @Test fun c16VolumeInheritedDrawIsRestrictedToTheActualOemSubtype() {
        val text = source("SideVolumePercentageHooker.kt")
        assertTrue(text.contains("hook(loader, inheritedDraw = true)"))
        assertTrue(text.contains("if (!type.isInstance(param.thisObject)) return"))
        assertTrue(text.contains("drawing.track.set(Reflect.getObjectField(view, \"mBackgroundRect\") as Rect)"))
        assertTrue(text.contains("else Reflect.callMethod(view, \"copyBackgroundRect\", drawing.track)"))
    }

    @Test fun c16DoesNotAdvertiseAnUninitializedNativeBackPlugin() {
        assertFalse(source("ColorOs16SystemUiFeatureHooks.kt").contains("AospBackPanelHooker"))
    }

    @Test fun c16MobilePresentationUsesNamesAndDoesNotChangeRadioOrSubscriptionState() {
        val text = source("ColorOs16SignalPresentationHooks.kt")
        listOf("mobile_roaming", "mobile_roaming_large", "mobile_roaming_space", "data_inout", "data_inout_alone")
            .forEach { assertTrue(it, text.contains("\"$it\"")) }
        assertTrue(text.contains("it.parameterTypes[0] == ViewGroup::class.java"))
        assertTrue(text.contains("view.resources.getResourcePackageName(view.id) == \"com.android.systemui\""))
        assertTrue(text.contains("detachObserver(); restore()"))
        listOf("TelephonyManager", "SubscriptionManager", "setDataEnabled", "setRoaming").forEach {
            assertFalse(it, text.contains(it))
        }
    }

    private fun source(name: String): String = listOf(File("src/main/java/com/mi/onextbox/lsp/$name"),
        File("app/src/main/java/com/mi/onextbox/lsp/$name")).first { it.isFile }.readText()
}
