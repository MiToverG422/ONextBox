package com.mi.onextbox.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TouchSamplingDiscoveryTest {
    @Test fun recognizesExactAidlServicesAndDeduplicatesInstances() {
        val service = "vendor.oplus.hardware.touch.IOplusTouch/default"
        assertEquals(listOf(service, "vendor.oplus.hardware.touch.IOplusTouch/panel1"),
            TouchSamplingDiscovery.oplusServices(
                "370\t$service: [vendor.oplus.hardware.touch.IOplusTouch]\n" +
                    "371 $service: [vendor.oplus.hardware.touch.IOplusTouch]\n" +
                    "372 vendor.oplus.hardware.touch.IOplusTouch/panel1: " +
                    "[vendor.oplus.hardware.touch.IOplusTouch]",
            ),
        )
    }

    @Test fun rejectsUnrelatedDescriptorsAndUnsafeServiceNames() {
        val lines = listOf(
            "1 vendor.oplus.hardware.touch.IOplusTouch/default: [wrong.interface]",
            "2 other.package.IOplusTouch/default: [vendor.oplus.hardware.touch.IOplusTouch]",
            "3 vendor.oplus.hardware.touch.IOplusTouch/default;id: [vendor.oplus.hardware.touch.IOplusTouch]",
            "4 vendor.oplus.hardware.touch.IOplusTouch/../default: [vendor.oplus.hardware.touch.IOplusTouch]",
            "5 vendor.oplus.hardware.touch.IOplusTouch/..: [vendor.oplus.hardware.touch.IOplusTouch]",
            "6 vendor.oplus.hardware.touch.IOplusTouch/default: [vendor.oplus.hardware.touch.IOplusTouch] injected",
            "7 vendor.oplus.hardware.touch@1.0::IOplusTouch/default: [vendor.oplus.hardware.touch.IOplusTouch]",
        )
        assertEquals(emptyList<String>(), TouchSamplingDiscovery.oplusServices(lines.joinToString("\n")))
    }

    @Test fun acceptsKnownConfigVariantsInConstrainedRoots() {
        val paths = listOf(
            "/data/vendor/touchconfig/vnd_custom_config_main.xml",
            "/vendor/etc/touchconfig/panel0/sys_touch_gt9916.xml",
            "/odm/etc/touchconfig/model/main/touch_config_default.xml",
            "/odm/etc/touchconfig/touchpanel_config.xml",
            "/odm/etc/touchconfig/new_chipset.xml",
        )
        assertEquals(paths, TouchSamplingDiscovery.configPaths((paths + paths.first()).joinToString("\n")))
    }

    @Test fun rejectsTraversalMetadataNonXmlFilesAndShellSyntax() {
        val paths = listOf(
            "/data/vendor/touchconfig/../vnd_custom_config_main.xml",
            "/data/vendor/touchconfig/./vnd_custom_config_main.xml",
            "/data/vendor/touchconfig//vnd_custom_config_main.xml",
            "/data/vendor/touchconfig/vnd_custom_config_main.xml;id",
            "/data/vendor/touchconfig/sys_touch_$(id).xml",
            "/data/vendor/touchconfig/unknown.json",
            "/data/vendor/touchconfig/sys_touch_cfg.xml.bak",
            "/data/vendor/touchconfig-other/sys_touch_cfg.xml",
            "/sdcard/sys_touch_cfg.xml",
            "lrwxrwxrwx /data/vendor/touchconfig/sys_touch_cfg.xml -> /sdcard/data.xml",
            "/data/vendor/touchconfig/a/b/c/sys_touch_cfg.xml",
        )
        assertEquals(emptyList<String>(), TouchSamplingDiscovery.configPaths(paths.joinToString("\n")))
    }

    @Test fun boundsConfigCount() {
        val paths = (0..40).map { "/vendor/etc/touchconfig/sys_touch_$it.xml" }
        assertEquals(paths.take(24), TouchSamplingDiscovery.configPaths(paths.joinToString("\n")))
    }

    @Test fun ignoresARecordTruncatedByTheListingLimit() {
        val prefix = " ".repeat(131_072 - "/vendor/etc/touchconfig/report.xml".length)
        assertEquals(emptyList<String>(), TouchSamplingDiscovery.configPaths(
            prefix + "/vendor/etc/touchconfig/report.xml;ignored suffix",
        ))
    }

    @Test fun acceptsOnlyKnownReadOnlyKernelCandidates() {
        val paths = listOf(
            "/proc/touchpanel/tp_index",
            "/proc/touchpanel0/report_rate",
            "/proc/touchpanel1/touch_report_rate",
            "/sys/class/touchpanel/sampling_rate",
        )
        assertEquals(paths, TouchSamplingDiscovery.kernelNodes(
            (paths + paths.first() + listOf(
                "/proc/touchpanel/game_switch_enable",
                "/proc/touchpanel2/report_rate",
                "/proc/touchpanel/debug_info/report_rate",
                "/proc/touchpanel/../report_rate",
                "/sys/class/touchpanel/report_rate;id",
                "/sys/class/touchpanel0/report_rate",
            )).joinToString("\n"),
        ))
    }

    @Test fun onlyAcceptsAnExplicitKnownPanelIndex() {
        assertEquals(0, TouchSamplingDiscovery.panelIndex(" 0\n"))
        assertEquals(1, TouchSamplingDiscovery.panelIndex("1"))
        for (value in listOf("2", "-1", "01", "0 1", "panel0", "0\n1", "240", "")) {
            assertNull(TouchSamplingDiscovery.panelIndex(value))
        }
    }

    @Test fun quotesShellMetacharactersAsOneArgument() {
        assertEquals("'/proc/touchpanel/report_rate'",
            TouchSamplingDiscovery.safeShellQuote("/proc/touchpanel/report_rate"))
        assertEquals("'x'\\'';$(id)'", TouchSamplingDiscovery.safeShellQuote("x';$(id)"))
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsControlCharactersInShellArguments() {
        TouchSamplingDiscovery.safeShellQuote("bad\nargument")
    }
}
