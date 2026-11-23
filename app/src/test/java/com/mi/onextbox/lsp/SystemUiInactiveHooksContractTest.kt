package com.mi.onextbox.lsp

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SystemUiInactiveHooksContractTest {
    private fun source(name: String) = File("src/main/java/com/mi/onextbox/lsp/$name.kt").readText()

    @Test fun disabledGlassCompilesTheNativeSourceAndKeepsItsBindingsForActivation() {
        val hook = source("SoftLightGlassHooker")
        val constructor = hook.substringAfter("install(\"program\", ctor").substringBefore("val cache =")
        assertTrue(constructor.contains("marker != null && systemUi && !active()"))
        assertTrue(constructor.contains("useNative -> input.substring(marker.length)"))
        assertTrue(constructor.contains("let(::rememberNative)"))

        val cache = hook.substringAfter("install(\"cached:").substringBefore("val factory =")
        assertTrue(cache.contains("marker != null && systemUi && !active()"))
        assertTrue(cache.contains("args[0] = source.substring(marker.length)"))
        assertTrue(cache.contains("if (it is RuntimeShader) rememberNative(it)"))
        assertTrue(hook.contains("shader in programs || shader in nativePrograms"))
        assertTrue(hook.contains("result !in programs && result !in nativePrograms"))
    }

    @Test fun inactiveQsObserversKeepCandidatesWithoutRequestingFrames() {
        val observer = source("QsCornerViewObservers")
        assertTrue(observer.contains("if (!listening && configurationToken(view) != null)"))
        assertTrue(Regex("""fun markDirty\(\) \{\s+if \(!listening\) return""").containsMatchIn(observer))
        assertTrue(observer.contains("if (!enabled) dispose()"))
        assertTrue(observer.contains("key in settingsKeys"))
        assertTrue(observer.contains("resumeAfterRejection = ::activate"))
    }

    @Test fun appearanceDiscoveryAndAnimationPreparationAreTriggeredByPreferences() {
        val hook = source("SystemUiAppearanceHooker")
        assertTrue(hook.contains("if (newBar || newHeader || newCorners) discover("))
        assertTrue(hook.contains("if (newCorners) corners?.prepareAnimations()"))
        assertTrue(hook.contains("if (!configured && views.isEmpty()) return@installFast result"))
        val theme = source("ThemeNightResourcesHook")
        assertTrue(theme.contains("enabled() && prepared.compareAndSet(false, true)"))
    }
}
