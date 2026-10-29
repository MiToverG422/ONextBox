package com.mi.onextbox.ui.home

import org.junit.Assert.*
import org.junit.Test

class HardwareInfoRulesTest {
    @Test fun materialSeriesRequiresExplicitVendorAndSeriesInPanelName() {
        assertEquals("BOE X3", HardwareInfoRules.panelSeries("boe_x3_cmd_panel"))
        assertEquals("BOE X2", HardwareInfoRules.panelSeries("BOE X2"))
        assertNull(HardwareInfoRules.panelSeries("AC347 P 3 A0019 dsc cmd mode panel"))
        assertNull(HardwareInfoRules.panelSeries("some_x3_controller"))
    }
    @Test fun panelManufacturerUsesExplicitNamesWithoutGuessingOpaqueIds() {
        assertEquals("BOE 京东方", HardwareInfoRules.panelVendor("BOE"))
        assertEquals("Tianma 天马", HardwareInfoRules.panelVendor("tianma"))
        assertNull(HardwareInfoRules.panelVendor("P_3"))
        assertEquals("BOE 京东方", HardwareInfoRules.touchPanelVendor("touch_panel0=0:synaptics-s3910:boe"))
        assertNull(HardwareInfoRules.touchPanelVendor("oplus.fingerprint.version=BOE"))
        assertNull(HardwareInfoRules.touchPanelVendor("touch_panel0=0:synaptics-s3910"))
    }
    @Test fun parsesDriverFieldsWithoutGuessingManufacturerCodes() {
        val raw = "Device version:\tDDR5\nDevice manufacture:\tSamsung|D1b|16G\n"
        assertEquals("DDR5", HardwareInfoRules.field(raw, "Device version"))
        assertEquals("Samsung|D1b|16G", HardwareInfoRules.field(raw, "Device manufacture"))
        assertEquals("P_3", HardwareInfoRules.field("Device manufacture: P_3", "Device manufacture"))
        assertNull(HardwareInfoRules.field("", "Device version"))
    }
    @Test fun specificationDoesNotTreatFirmwareAsUfsVersion() {
        assertEquals("4.0", HardwareInfoRules.specification("0x0400"))
        assertEquals("3.1", HardwareInfoRules.specification("784"))
        assertNull(HardwareInfoRules.specification("2000"))
        assertNull(HardwareInfoRules.specification(null))
    }
    @Test fun refreshRatesRoundDeduplicateAndRejectInvalidValues() {
        assertEquals("28.8 / 60 / 120", HardwareInfoRules.rates(listOf(120.00001f, 60f, 59.99999f, 28.8f, Float.NaN, -1f)))
    }
    @Test fun colorModesRetainUnknownIdsAndDoNotInventCoverage() {
        assertEquals("原生模式 / sRGB / Display P3 / 模式 99", HardwareInfoRules.colorModes("supportedColorModes [0, 7, 9, 99]"))
        assertNull(HardwareInfoRules.colorModes("isWideColorGamut=true"))
    }
}
