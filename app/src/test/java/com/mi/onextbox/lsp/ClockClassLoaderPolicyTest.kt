package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class ClockClassLoaderPolicyTest {
    @Test fun publicClockAndLayoutTypesKeepHostIdentity() {
        listOf("com.android.systemui.plugins.keyguard.ui.clocks.ClockController", "com.android.systemui.log.core.MessageBuffer",
            "kotlin.jvm.functions.Function1", "androidx.constraintlayout.widget.ConstraintSet", "androidx.compose.runtime.Composer")
            .forEach { assertTrue(it, ClockClassLoaderPolicy.hostFirst(it)) }
    }

    @Test fun independentlyOptimizedImplementationsDoNotUseHostR8Helpers() {
        listOf("com.android.systemui.customization.clocks.ClockContextImpl", "com.android.systemui.clocks.BaseClockProvider",
            "kotlin.collections.CollectionsKt", "kotlinx.coroutines.Dispatchers",
            "androidx.compose.animation.Scale\$\$ExternalSyntheticOutline0")
            .forEach { assertFalse(it, ClockClassLoaderPolicy.hostFirst(it)) }
    }
}
