package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class NativeInstallerThemeRulesTest {
    @Test fun fixesAllSixNativeColorAliasesAndTheirResolvedSystemReferences() {
        NativeInstallerThemeRules.colors.forEach { (alias, role) ->
            val expected = "system_${role}_dark"
            assertEquals(expected, NativeInstallerThemeRules.replacement(NativeInstallerThemeRules.PACKAGE, alias, true, true))
            assertEquals(expected, NativeInstallerThemeRules.replacement("android", "system_${role}_light", true, true))
        }
    }
    @Test fun preservesLightModeAndDisabledFeature() {
        assertNull(NativeInstallerThemeRules.replacement(NativeInstallerThemeRules.PACKAGE, "primaryColor", false, true))
        assertNull(NativeInstallerThemeRules.replacement(NativeInstallerThemeRules.PACKAGE, "primaryColor", true, false))
    }
    @Test fun leavesOtherPackagesAndUnrelatedColorsAlone() {
        assertNull(NativeInstallerThemeRules.replacement("com.oplus.appdetail", "primaryColor", true, true))
        assertNull(NativeInstallerThemeRules.replacement("android", "system_error_light", true, true))
        assertNull(NativeInstallerThemeRules.replacement(NativeInstallerThemeRules.PACKAGE, "couiColorPrimary", true, true))
    }
    @Test fun darkColorsAreNeverRedirectedRecursively() {
        assertNull(NativeInstallerThemeRules.replacement("android", "system_primary_dark", true, true))
    }

    private val slots = NativeInstallerThemeRules.Slots(7, 0, 1, 2, 3, 4, 5)
    private val dark = NativeInstallerThemeRules.ColorValue(28, 0xff1d1f26.toInt(), 2, 202, 0x1000, 0)
    private fun resolve(id: Int) = dark.takeIf { id == 101 }

    @Test fun xmlColorBackgroundAndTextReceiveTheDarkValueBeforeDrawableOrTextInflation() {
        val data = intArrayOf(28, 0xffe7e7f1.toInt(), 1, 101, 0x400, 320, 999)
        assertEquals(1, NativeInstallerThemeRules.rewriteColors(data, 1, slots, ::resolve))
        assertArrayEquals(intArrayOf(28, dark.data, 2, 202, 0x1400, 0, 999), data)
    }

    @Test fun sameRgbFromUnrelatedResourceAndStatefulXmlRemainUntouched() {
        val data = intArrayOf(
            28, 0xffe7e7f1.toInt(), 1, 303, 0, 0, 0,
            3, 123, 1, 101, 0, 0, 0,
            16, 0xffe7e7f1.toInt(), 1, 101, 0, 0, 0,
        )
        val before = data.copyOf()
        assertEquals(0, NativeInstallerThemeRules.rewriteColors(data, 3, slots, ::resolve))
        assertArrayEquals(before, data)
    }

    @Test fun pooledArrayCapacityBeyondItsLogicalLengthIsNotChanged() {
        val data = intArrayOf(28, 1, 1, 101, 0, 0, 999, 28, 1, 1, 101, 0, 0, 999)
        val tail = data.copyOfRange(7, 14)
        assertEquals(1, NativeInstallerThemeRules.rewriteColors(data, 1, slots, ::resolve))
        assertArrayEquals(tail, data.copyOfRange(7, 14))
        assertEquals(0, NativeInstallerThemeRules.rewriteColors(data, 1, slots, ::resolve))
    }

    @Test fun rejectsUnexpectedFrameworkTableLayoutWithoutWritingAnyPartOfIt() {
        val data = intArrayOf(28, 1, 1, 101, 0, 0, 999)
        val before = data.copyOf()
        assertEquals(0, NativeInstallerThemeRules.rewriteColors(data, 2, slots, ::resolve))
        assertEquals(0, NativeInstallerThemeRules.rewriteColors(data, 1, slots.copy(id = 9), ::resolve))
        assertEquals(0, NativeInstallerThemeRules.rewriteColors(data, 1, slots.copy(cookie = 1), ::resolve))
        assertArrayEquals(before, data)
    }

    @Test fun missingOrNonColorTargetDoesNotDestroyTheOriginalValue() {
        val data = intArrayOf(28, 1, 1, 101, 0, 0, 999)
        val before = data.copyOf()
        assertEquals(0, NativeInstallerThemeRules.rewriteColors(data, 1, slots) { null })
        assertEquals(0, NativeInstallerThemeRules.rewriteColors(data, 1, slots) { dark.copy(type = 3) })
        assertArrayEquals(before, data)
    }
}
