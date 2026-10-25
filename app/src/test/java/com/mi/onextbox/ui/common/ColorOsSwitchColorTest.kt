package com.mi.onextbox.ui.common

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorOsSwitchColorTest {
    private val onPrimary = Color(0xFF003258)
    private val onSurface = Color(0xFFBFC8D1)

    @Test fun monetUsesSemanticColorsForBothStatesInEitherBrightness() {
        for (dark in listOf(false, true)) {
            assertEquals(onPrimary, color(true, true, true, dark))
            assertEquals(onSurface, color(true, false, true, dark))
        }
    }

    @Test fun disabledMonetRetainsStateHueWithReducedOpacity() {
        for (dark in listOf(false, true)) {
            assertEquals(onPrimary.copy(alpha = .38f), color(true, true, false, dark))
            assertEquals(onSurface.copy(alpha = .38f), color(true, false, false, dark))
        }
    }

    @Test fun nativeColorsRemainUnchangedForEveryState() {
        for (checked in listOf(false, true)) {
            for (dark in listOf(false, true)) {
                assertEquals(Color.White, color(false, checked, true, dark))
                assertEquals(if (dark) Color(0x29FFFFFF) else Color(0x8AFFFFFF),
                    color(false, checked, false, dark))
            }
        }
    }

    private fun color(monet: Boolean, checked: Boolean, enabled: Boolean, dark: Boolean) =
        colorOsSwitchThumbColor(monet, checked, enabled, dark, onPrimary, onSurface)
}
