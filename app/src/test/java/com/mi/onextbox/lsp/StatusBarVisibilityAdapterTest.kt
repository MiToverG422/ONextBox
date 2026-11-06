package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class StatusBarVisibilityAdapterTest {
    class Flags(
        @JvmField val isClockAllowed: Boolean,
        @JvmField val areNotificationIconsAllowed: Boolean,
        @JvmField val isSystemInfoAllowed: Boolean,
        @JvmField val animate: Boolean,
    )
    class Visibility(@JvmField val visibility: Int, @JvmField val shouldAnimateChange: Boolean)
    private val adapter = StatusBarVisibilityAdapter(Visibility::class.java, Flags::class.java)

    @Test fun allowingOneAreaDoesNotModifyTheSharedFlagsOrOtherAreas() {
        val original = Flags(false, false, false, true)
        val clock = adapter.allow(original, "isClockAllowed") as Flags
        assertTrue(clock.isClockAllowed)
        assertFalse(clock.areNotificationIconsAllowed)
        assertFalse(clock.isSystemInfoAllowed)
        assertTrue(clock.animate)
        assertFalse(original.isClockAllowed)
        assertFalse(original.areNotificationIconsAllowed)
        assertFalse(original.isSystemInfoAllowed)
        val icons = adapter.allow(original, "areNotificationIconsAllowed") as Flags
        assertTrue(icons.areNotificationIconsAllowed)
        assertFalse(icons.isClockAllowed)
        assertFalse(icons.isSystemInfoAllowed)
        val info = adapter.allow(original, "isSystemInfoAllowed") as Flags
        assertTrue(info.isSystemInfoAllowed)
        assertFalse(info.isClockAllowed)
        assertFalse(info.areNotificationIconsAllowed)
    }

    @Test fun unchangedModelsKeepTheirIdentityAndAnimationState() {
        val original = Flags(true, false, false, false)
        assertSame(original, adapter.allow(original, "isClockAllowed"))
        val visibility = Visibility(0, true)
        assertSame(visibility, adapter.withVisibility(visibility, 0))
        val hidden = adapter.withVisibility(visibility, 4) as Visibility
        assertEquals(4, hidden.visibility)
        assertTrue(hidden.shouldAnimateChange)
        assertEquals(0, visibility.visibility)
    }

    @Test fun aNonAnimatedNativeModelDoesNotAcquireAnimations() {
        val original = Visibility(8, false)
        val visible = adapter.withVisibility(original, 0) as Visibility
        assertFalse(visible.shouldAnimateChange)
        assertEquals(8, original.visibility)
    }

    @Test(expected = IllegalArgumentException::class)
    fun theAnimationFlagCannotBeOverriddenAsAVisibleArea() {
        adapter.allow(Flags(false, false, false, false), "animate")
    }
}
