package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class SystemAdaptationVersionTest {
    @Test fun detectsTheActualColorOs16And17RomVersions() {
        assertEquals(SystemAdaptationVersion.ColorOs16, detect("ro.build.version.oplusrom" to "V16.1.0"))
        assertEquals(SystemAdaptationVersion.ColorOs17, detect("ro.build.version.oplusrom" to "V17.0.0"))
    }

    @Test fun publicDisplayVersionTakesPriorityOverTheUnderlyingRomVersion() {
        assertEquals(SystemAdaptationVersion.ColorOs17, detect(
            "ro.build.version.oplusrom.display" to " ColorOS 17.0 ",
            "ro.build.version.oplusrom" to "V16.1.0",
        ))
    }

    @Test fun displayVersionKeepsPriorityWhenConfidentialMetadataIsActive() {
        for (flag in listOf("ro.version.confidential", "persist.version.confidential")) {
            assertEquals(flag, SystemAdaptationVersion.ColorOs16, detect(
                flag to "TRUE",
                "ro.build.version.oplusrom.confidential" to "V17.0.0",
                "ro.build.version.oplusrom.display" to "16.0",
                "ro.build.version.oplusrom" to "V17.0.0",
            ))
        }
    }

    @Test fun normalRomPropertiesTakePriorityOverConfidentialFallback() {
        assertEquals(SystemAdaptationVersion.ColorOs16, detect(
            "ro.version.confidential" to "true",
            "ro.build.version.oplusrom.confidential" to "V17.0.0",
            "ro.build.version.oplusrom" to "V16.1.0",
        ))
    }

    @Test fun activeConfidentialVersionCanFillInMissingPublicMetadata() {
        for (flag in listOf("ro.version.confidential", "persist.version.confidential")) {
            assertEquals(flag, SystemAdaptationVersion.ColorOs17, detect(
                flag to "true",
                "ro.build.version.oplusrom.confidential" to "V17.0.0",
                "ro.build.version.oplusrom.display" to "V1.0.0",
            ))
        }
    }

    @Test fun inactiveConfidentialVersionDoesNotOverrideThePublicRomVersion() {
        assertEquals(SystemAdaptationVersion.ColorOs16, detect(
            "ro.version.confidential" to "false",
            "ro.build.version.oplusrom.confidential" to "V17.0.0",
            "ro.build.version.oplusrom" to "V16.1.0",
        ))
    }

    @Test fun missingOrMalformedRomPropertiesAllowLowerPriorityDetection() {
        assertNull(SystemAdaptationDetection.detect({ null }))
        assertEquals(SystemAdaptationVersion.ColorOs16, detect(
            "ro.build.version.oplusrom.display" to " ",
            "ro.build.version.oplusrom" to "unavailable",
            "ro.build.display.id" to "PMA110_16.0.10.501(CN01)",
        ))
    }

    @Test fun failedPropertyReadsDoNotPreventRemainingPropertiesFromBeingDetected() {
        assertEquals(SystemAdaptationVersion.ColorOs16, SystemAdaptationDetection.detect({ key ->
            if (key == "ro.build.version.oplusrom") "V16.1.0" else throw SecurityException(key)
        }))
        assertNull(SystemAdaptationDetection.detect({ throw SecurityException(it) }))
    }

    @Test fun unknownMajorVersionsContinueThroughFallbackMetadata() {
        for (displayVersion in listOf("V1.0.0", "ColorOS 18.0")) {
            assertEquals(SystemAdaptationVersion.ColorOs17, detect(
                "ro.build.version.oplusrom.display" to displayVersion,
                "ro.build.version.oplusrom" to "V17.0.0",
            ))
        }
        assertNull(detect(
            "ro.build.version.oplusrom.display" to "ColorOS 18.0",
            "ro.build.version.oplusrom" to "V1.0.0",
            "ro.build.display.id" to "PMA110_18.0.0.101(CN01)",
        ))
    }

    @Test fun firmwareDisplayCanDetectTheVersionWhenRomPropertiesAreUnavailable() {
        assertEquals(SystemAdaptationVersion.ColorOs16, detect(
            "ro.build.display.id" to "PMA110_16.0.10.501(CN01)",
        ))
        assertEquals(SystemAdaptationVersion.ColorOs17, SystemAdaptationDetection.detect(
            property = { null },
            buildDisplay = "PMA110_17.0.0.101(CN01)",
        ))
        assertEquals(SystemAdaptationVersion.ColorOs16, SystemAdaptationDetection.detect(
            property = { throw IllegalStateException(it) },
            buildDisplay = "PMA110_16.0.10.501(CN01)",
        ))
    }

    @Test fun firmwareDisplayCannotOverrideAnExplicitRomVersion() {
        assertEquals(SystemAdaptationVersion.ColorOs17, SystemAdaptationDetection.detect(
            property = mapOf("ro.build.version.oplusrom" to "V17.0.0")::get,
            buildDisplay = "PMA110_16.0.10.501(CN01)",
        ))
    }

    @Test fun androidReleaseAndInternalOtaNumberAreNotColorOsVersions() {
        assertNull(detect(
            "ro.build.version.release" to "17",
            "ro.build.version.sdk" to "37",
            "ro.build.display.id" to "PMA110_11.C.10_0100_202610250000",
        ))
        assertNull(SystemAdaptationDetection.detect({ null }, "PMA110_11.C.10_0100_202610250000"))
    }

    @Test fun detectedVersionsDoNotProbeOtherAdapters() {
        for (version in SystemAdaptationVersion.entries) {
            var probes = 0
            assertEquals(version, SystemAdaptationDetection.resolve(version) {
                probes++
                false
            })
            assertEquals(0, probes)
        }
    }

    @Test fun unknownVersionUsesColorOs17BeforeTryingColorOs16() {
        val tried = mutableListOf<SystemAdaptationVersion>()
        assertEquals(SystemAdaptationVersion.ColorOs17, SystemAdaptationDetection.resolve(null) {
            tried += it
            true
        })
        assertEquals(listOf(SystemAdaptationVersion.ColorOs17), tried)

        tried.clear()
        assertEquals(SystemAdaptationVersion.ColorOs16, SystemAdaptationDetection.resolve(null) {
            tried += it
            it == SystemAdaptationVersion.ColorOs16
        })
        assertEquals(listOf(SystemAdaptationVersion.ColorOs17, SystemAdaptationVersion.ColorOs16), tried)
    }

    @Test fun unsupportedAdaptersLeaveTheDefaultAtColorOs17() {
        val tried = mutableListOf<SystemAdaptationVersion>()
        assertEquals(SystemAdaptationVersion.ColorOs17, SystemAdaptationDetection.resolve(null) {
            tried += it
            false
        })
        assertEquals(listOf(SystemAdaptationVersion.ColorOs17, SystemAdaptationVersion.ColorOs16), tried)
    }

    @Test fun aFailedProbeStillAllowsTheNextAdapterAndThenTheDefault() {
        val tried = mutableListOf<SystemAdaptationVersion>()
        assertEquals(SystemAdaptationVersion.ColorOs16, SystemAdaptationDetection.resolve(null) {
            tried += it
            if (it == SystemAdaptationVersion.ColorOs17) throw LinkageError("Missing C17 API")
            true
        })
        assertEquals(listOf(SystemAdaptationVersion.ColorOs17, SystemAdaptationVersion.ColorOs16), tried)
        assertEquals(SystemAdaptationVersion.ColorOs17, SystemAdaptationDetection.resolve(null) {
            throw LinkageError("Missing API")
        })
    }

    private fun detect(vararg properties: Pair<String, String>): SystemAdaptationVersion? =
        SystemAdaptationDetection.detect(properties.toMap()::get)
}
