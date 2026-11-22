package com.mi.onextbox.lsp

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationCornerSceneTest {
    @Test fun lockscreenMorphAndHeightClampKeepTheirNativeRadiusUntilTheRowSettles() {
        val radius = SystemUiAppearanceHooker.Radius(native = 30f, resting = 30f, selected = 50f)
        assertEquals(50f, radius.resolved(), 0f)
        radius.native = 60f
        assertEquals(60f, radius.resolved(), 0f)
        radius.native = 20f
        assertEquals(20f, radius.resolved(), 0f)
        radius.selected = 5f
        assertEquals(20f, radius.resolved(), 0f)
        radius.native = 30f
        assertEquals(5f, radius.resolved(), 0f)
        assertEquals(30f, radius.native, 0f)
    }
}
