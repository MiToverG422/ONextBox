package com.mi.onextbox.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class AppVisualEffectsTest {
    @Test fun explicitChoicesOverrideBothSystemValues() {
        for (system in listOf(true, false)) {
            assertEquals(system, VisualEffectMode.FollowSystem.resolve(system))
            assertEquals(false, VisualEffectMode.Off.resolve(system))
            assertEquals(true, VisualEffectMode.On.resolve(system))
        }
    }

    @Test fun legacyDisabledBlurDoesNotChangeTheDefaultsForOutlineOrAnimation() {
        for (effect in AppVisualEffect.entries) {
            val expected = if (effect == AppVisualEffect.BackgroundBlur) VisualEffectMode.Off else VisualEffectMode.FollowSystem
            assertEquals(expected, readVisualEffectMode(effect, null, legacyBlur = false))
            assertEquals(VisualEffectMode.FollowSystem, readVisualEffectMode(effect, null, legacyBlur = true))
        }
    }

    @Test fun explicitFollowSystemSupersedesTheLegacyPreference() {
        val blur = AppVisualEffect.BackgroundBlur
        assertEquals(VisualEffectMode.FollowSystem, readVisualEffectMode(blur, "FollowSystem", legacyBlur = false))
        assertEquals(VisualEffectMode.FollowSystem, readVisualEffectMode(blur, "invalid", legacyBlur = false))
    }
}
