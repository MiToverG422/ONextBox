package com.mi.onextbox.touch

import org.junit.Assert.*
import org.junit.Test

class TouchSamplingConfigTest {
    @Test fun readsActualX9uValuesInsteadOfStaleChipComments() {
        val xml = requireNotNull(javaClass.getResourceAsStream("/touch/x9u-report-rate.xml"))
            .bufferedReader().use { it.readText() }
        val profile = parseSingle(xml)
        assertEquals(16, profile.presets.size)
        assertEquals(2, profile.defaultChipValue)
        assertEquals(2, profile.presets.first { it.index == 1 }.chipValue)
        assertEquals(8, profile.presets.first { it.index == 12 }.chipValue)
        assertEquals(listOf(10, 11, 16), profile.presets.filter { it.isIstMode }.map { it.index })
        assertEquals(TouchRatePreset(5, 288, 10, false), profile.presets.first { it.index == 5 })
        assertNotNull(TouchSamplingConfig.select(listOf(profile), 0, 5 to 10))
    }

    @Test fun parsesLegacyIndexedTableAndRateComment() {
        val profile = parseSingle("""
            <root><device id="0"><panel id="0">
                <modules id="report_rate" enable="true"><param name="supports">
                    <value>0,1,2,3,10</value><!-- rate: default,120,240Hz,360,720Ist -->
                </param></modules>
            </panel></device></root>
        """)
        assertEquals(0, profile.panelIndex)
        assertEquals(0, profile.defaultChipValue)
        assertEquals(listOf(1, 2, 3, 4), profile.presets.map { it.index })
        assertEquals(listOf(120, 240, 360, 720), profile.presets.map { it.hz })
        assertEquals(listOf(1, 2, 3, 10), profile.presets.map { it.chipValue })
        assertTrue(profile.presets.last().isIstMode)
    }

    @Test fun acceptsQuotesWhitespaceAndEncodingDeclaration() {
        val profile = parseSingle("""
            <?xml version='1.0' encoding='UTF-8'?>
            <root><panel id = '1'><modules id = 'report_rate'>
                <param name = 'supports'><value> 0 , 4 , 7 </value></param>
                <param name = 'frequency_hz'><value>0,240,360</value></param>
            </modules></panel></root>
        """.trimIndent())
        assertEquals(1, profile.panelIndex)
        assertEquals(listOf(240, 360), profile.presets.map { it.hz })
    }

    @Test fun preservesModesWithoutFrequencyLabels() {
        val profile = parseSingle(table("0,7,12"))
        assertEquals(listOf(null, null), profile.presets.map { it.hz })
        assertEquals(listOf(1, 2), profile.presets.map { it.index })
        assertEquals(listOf(7, 12), profile.presets.map { it.chipValue })
    }

    @Test fun doesNotGuessFrequencyFromChipCode() {
        val profile = parseSingle(table("0,120,240"))
        assertTrue(profile.presets.all { it.hz == null })
    }

    @Test fun parsesExplicitIndexedLabelsWithoutReorderingModes() {
        val profile = parseSingle("""
            <root><module id="report_rate"><param name="supports">
                <value index="2" label="240 Hz Ist">9</value>
                <value index="0" hz="0">3</value>
                <value index="1" hz="360">8</value>
            </param></module></root>
        """)
        assertEquals(3, profile.defaultChipValue)
        assertEquals(listOf(360, 240), profile.presets.map { it.hz })
        assertTrue(profile.presets.last().isIstMode)
    }

    @Test fun preservesExplicitAlternateNodeOnlyAsMetadata() {
        val profile = parseSingle(table("0,1", attributes = "node='183'"))
        assertEquals(183, profile.node)
    }

    @Test fun parsesEveryPanelInsteadOfSelectingTheFirst() {
        val profiles = TouchSamplingConfig.parse("""
            <root><panel id="0">${module("0,4")}</panel>
                <panel id="1">${module("0,7")}</panel></root>
        """)
        assertEquals(setOf(0, 1), profiles.map { it.panelIndex }.toSet())
        val selected = TouchSamplingConfig.select(profiles, 1, 1 to 7)
        assertEquals(7, selected?.presets?.single()?.chipValue)
        assertNull(TouchSamplingConfig.select(profiles, 1, 1 to 4))
        assertNull(TouchSamplingConfig.select(profiles, 2, 1 to 4))
    }

    @Test fun choosesExactPanelBeforeUnscopedConfiguration() {
        val profiles = TouchSamplingConfig.parse("""
            <root>${module("0,9")}<panel id="1">${module("0,4")}</panel></root>
        """)
        assertEquals(4, TouchSamplingConfig.select(profiles, 1, 1 to 4)?.presets?.single()?.chipValue)
        assertNull(TouchSamplingConfig.select(profiles, 1, 1 to 9))
        assertEquals(9, TouchSamplingConfig.select(profiles, 2, 1 to 9)?.presets?.single()?.chipValue)
    }

    @Test fun validatesDefaultIndexAndCodeAsWellAsPresetIndexes() {
        val profiles = TouchSamplingConfig.parse(table("3,9,7"))
        assertNotNull(TouchSamplingConfig.select(profiles, 0, 0 to 3))
        assertNull(TouchSamplingConfig.select(profiles, 0, 0 to 0))
        assertNull(TouchSamplingConfig.select(profiles, 0, 3 to 7))
        assertNull(TouchSamplingConfig.select(profiles, 0, -1 to 9))
        assertNull(TouchSamplingConfig.select(profiles, -1, 1 to 9))
    }

    @Test fun keepsSingleUnverifiedProfileAvailableForReadOnlyDisplay() {
        val profiles = TouchSamplingConfig.parse(table("0,7,12"))
        assertNotNull(TouchSamplingConfig.select(profiles, 0, null))
    }

    @Test fun rejectsConflictingUnscopedProfilesRatherThanUsingCurrentCodeToGuess() {
        val profiles = TouchSamplingConfig.parse("<root>${module("0,4")}${module("0,7")}</root>")
        assertNull(TouchSamplingConfig.select(profiles, 0, 1 to 4))
    }

    @Test fun mergesUnknownAndKnownFrequencyLabelsOnlyForIdenticalMappings() {
        val profiles = TouchSamplingConfig.parse(table("0,7")) +
            TouchSamplingConfig.parse(table("0,7", "default,240Hz"))
        assertEquals(240, TouchSamplingConfig.select(profiles, 0, 1 to 7)?.presets?.single()?.hz)
    }

    @Test fun rejectsFrequencyConflictsEvenWhenFirstProfileHasNoLabels() {
        val profiles = listOf(null, "default,240", "default,360").flatMap {
            TouchSamplingConfig.parse(table("0,7", it))
        }
        assertNull(TouchSamplingConfig.select(profiles, 0, 1 to 7))
    }

    @Test fun rejectsConflictingSchemasWithinAModule() {
        assertTrue(TouchSamplingConfig.parse("""
            <root><modules id="report_rate">
                <param name="supports"><value>0,1</value></param>
                <param name="supports"><value>0,2</value></param>
            </modules></root>
        """).isEmpty())
        assertTrue(TouchSamplingConfig.parse("""
            <root><modules id="report_rate">
                <param name="supports"><value>0,1</value><!--rate: default,120--></param>
                <param name="frequency"><value>0,240</value></param>
            </modules></root>
        """).isEmpty())
    }

    @Test fun rejectsMalformedCodeListsWithoutShiftingIndexes() {
        for (codes in listOf("0,bad,2", "0,-1,2", "0,,2", "0,2,", "0,2147483648", "0,+2", "0,0x2")) {
            assertTrue("Accepted $codes", TouchSamplingConfig.parse(table(codes)).isEmpty())
        }
    }

    @Test fun doesNotUseAPartialDocumentWhenAnotherActiveTableIsMalformed() {
        val xml = "<root><panel id='0'>${module("0,4")}</panel>" +
            "<panel id='1'>${module("0,bad,7")}</panel></root>"
        assertTrue(TouchSamplingConfig.parse(xml).isEmpty())
    }

    @Test fun rejectsSparseDuplicateAndOutOfRangeExplicitIndexes() {
        for (indexes in listOf("0,2", "0,0", "0,-1", "0,256")) {
            val values = indexes.split(',').joinToString("") { "<value index='$it'>1</value>" }
            assertTrue(TouchSamplingConfig.parse("<root><modules id='report_rate'><param name='supports'>$values</param></modules></root>").isEmpty())
        }
    }

    @Test fun rejectsInvalidPanelIndexInsteadOfTreatingItAsUnscoped() {
        for (attributes in listOf("id='x'", "id='-1'", "id='256'", "id='0' index='1'", "name='unknown'")) {
            assertTrue(TouchSamplingConfig.parse("<root><panel $attributes>${module("0,4")}</panel></root>").isEmpty())
        }
    }

    @Test fun rejectsMismatchedOrInvalidExplicitFrequencies() {
        for (frequency in listOf("0,120,240", "0,bad", "0,1", "0,4001")) {
            val xml = "<root><modules id='report_rate'><param name='supports'><value>0,1</value></param>" +
                "<param name='frequency_hz'><value>$frequency</value></param></modules></root>"
            assertTrue(TouchSamplingConfig.parse(xml).isEmpty())
        }
    }

    @Test fun ignoresFeatureFlagsRatherThanMakingRatePresets() {
        val xml = """
            <root><device id='0'><panel id='0'><modules id='TouchConfig'>
                <param name='game_mode_divided_report_rate_support'><value>1</value></param>
                <param name='touchpanel,smooth-level'><value>16,20,18,16,14,12</value></param>
            </modules></panel><panel id='1'><modules id='TouchConfig'>
                <param name='game_mode_divided_report_rate_support'><value>1</value></param>
            </modules></panel></device></root>
        """
        assertTrue(TouchSamplingConfig.parse(xml).isEmpty())
    }

    @Test fun ignoresDisabledReportRateModules() {
        assertTrue(TouchSamplingConfig.parse(table("0,1", attributes = "enable='false'")).isEmpty())
    }

    @Test fun rejectsXxeAndMalformedXmlWithoutOpeningEntities() {
        val payloads = listOf(
            "<!DOCTYPE root [<!ENTITY code SYSTEM 'file:///nonexistent-touch-secret'>]><root>${module("0,&code;")}</root>",
            "<!DOCTYPE root SYSTEM 'https://invalid.example/config.dtd'><root>${module("0,1")}</root>",
            "<root>${module("0,1")}",
        )
        payloads.forEach { assertTrue(TouchSamplingConfig.parse(it).isEmpty()) }
    }

    @Test fun boundsInputSizeAndTreeDepth() {
        assertTrue(TouchSamplingConfig.parse(" ".repeat(1024 * 1024 + 1)).isEmpty())
        assertTrue(TouchSamplingConfig.parse("<r>" + "<x>".repeat(70) + module("0,1") + "</x>".repeat(70) + "</r>").isEmpty())
    }

    private fun parseSingle(xml: String): TouchConfigProfile = TouchSamplingConfig.parse(xml.trimIndent()).single()

    private fun table(codes: String, labels: String? = null, attributes: String = ""): String =
        "<root>${module(codes, labels, attributes)}</root>"

    private fun module(codes: String, labels: String? = null, attributes: String = ""): String =
        "<modules id='report_rate' $attributes><param name='supports'><value>$codes</value>" +
            (labels?.let { "<!--rate: $it-->" } ?: "") + "</param></modules>"
}
