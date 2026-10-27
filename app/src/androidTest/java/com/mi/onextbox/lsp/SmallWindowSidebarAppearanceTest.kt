package com.mi.onextbox.lsp

import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmallWindowSidebarAppearanceTest {
    @Test fun readsInstalledSidebarColorAndUserOpacityWithoutChangingItsSettings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resources = context.packageManager.getResourcesForApplication(SmallWindowSidebarAppearance.PACKAGE_NAME)
        val id = resources.getIdentifier("coloros_ep_float_bar_fill", "color", SmallWindowSidebarAppearance.PACKAGE_NAME)
        assertNotEquals(0, id)
        val resolver = context.contentResolver
        val current = Settings.Secure.getInt(resolver, SmallWindowSidebarAppearance.ALPHA_KEY, -1)
        val legacy = Settings.Secure.getInt(resolver, SmallWindowSidebarAppearance.LEGACY_ALPHA_KEY, 40)
        val permanent = Settings.Secure.getInt(resolver, SmallWindowSidebarAppearance.PERMANENT_KEY, 1)
        SmallWindowSidebarAppearance(context) {}.use { appearance ->
            assertEquals(resources.getColor(id, null), appearance.value.color)
            assertNotEquals(android.graphics.Color.WHITE, appearance.value.color)
            assertEquals(SmallWindowRules.sidebarOpacity(current.takeUnless { it == -1 }, legacy, permanent == 1),
                appearance.value.opacity, 0.0001f)
            assertEquals(current, Settings.Secure.getInt(resolver, SmallWindowSidebarAppearance.ALPHA_KEY, -1))
            assertEquals(legacy, Settings.Secure.getInt(resolver, SmallWindowSidebarAppearance.LEGACY_ALPHA_KEY, 40))
            assertEquals(permanent, Settings.Secure.getInt(resolver, SmallWindowSidebarAppearance.PERMANENT_KEY, 1))
        }
    }
}
