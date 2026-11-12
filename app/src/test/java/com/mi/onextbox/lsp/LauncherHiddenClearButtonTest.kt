package com.mi.onextbox.lsp

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class LauncherHiddenClearButtonTest {
    private fun source() = File("src/main/java/com/mi/onextbox/lsp/LauncherFeaturesHooker.kt").readText()

    @Test fun hiddenButtonCannotBeReenabledByNativeTransitions() {
        val source = source()
        assertTrue(source.contains("button.isLongClickable = false"))
        assertTrue(source.contains("getDeclaredMethod(\"setViewClickable\""))
        assertTrue(source.contains("chain.proceed(arrayOf(0f))"))
    }

    @Test fun programmaticAndForwardedClicksAreGuardedWithoutRemovingOriginalListener() {
        val source = source()
        assertTrue(source.contains("hook(panelClick)"))
        assertTrue(source.contains("hook(clickBridge)"))
        assertTrue(source.contains("after(listenerSetter)"))
        assertTrue(source.contains("deoptimize(listener.javaClass.getDeclaredMethod(\"onClick\", View::class.java))"))
        assertFalse(source.contains("arrayOf(View.OnClickListener {"))
        assertTrue(source.contains("val clickBridge = instanceClick ?:"))
        assertFalse(source.contains("View::class.java.getDeclaredMethod(\"performClick\""))
    }
}
