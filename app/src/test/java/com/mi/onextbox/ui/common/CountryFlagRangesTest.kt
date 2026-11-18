package com.mi.onextbox.ui.common

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CountryFlagRangesTest {
    @Test fun keepsTextAndOtherEmojiOutsideTheFlagSpan() {
        val text = "🌐 CN China 🇨🇳 / 🙂"
        val ranges = countryFlagRanges(text)
        assertEquals(1, ranges.size)
        assertEquals("🇨🇳", text.substring(ranges.single().start, ranges.single().end))
        assertEquals(text.indexOf("🇨🇳"), ranges.single().start)
    }

    @Test fun supportsAdjacentFlagsAndRegionalOrganizations() {
        val text = "🇨🇳🇺🇸 🇪🇺 🇺🇳 🇭🇰 🇲🇴 🇹🇼"
        assertEquals(listOf("🇨🇳", "🇺🇸", "🇪🇺", "🇺🇳", "🇭🇰", "🇲🇴", "🇹🇼"),
            countryFlagRanges(text).map { text.substring(it.start, it.end) })
    }

    @Test fun leavesUnsupportedPairsAndStandaloneIndicatorsToTheSystemFont() {
        assertTrue(countryFlagRanges("🇿🇿 🇦 text 🇨").isEmpty())
        assertEquals(listOf(CountryFlagRange(4, 8)), countryFlagRanges("🇿🇿🇨🇳"))
    }

    @Test fun regionalIndicatorsArePairedFromTheStartOfTheirRun() {
        assertEquals(listOf(CountryFlagRange(0, 4)), countryFlagRanges("🇨🇳🇺"))
        assertTrue(countryFlagRanges("🇿🇨🇳").isEmpty())
    }

    @Test fun doesNotChangeNonCountryFlagsOrBrokenSurrogates() {
        assertTrue(countryFlagRanges("普通文字 123 🌐 🏳️‍🌈 🏁 🏴 \uD83C \uDDFF").isEmpty())
    }

    @Test fun everyBundledCountryHasOneCompleteFlagSpan() {
        val root = listOf(File("."), File("..")).first { File(it, "third_party/discord-twemoji/sources.json").isFile }
        val manifest = File(root, "third_party/discord-twemoji/sources.json").readText()
        val countries = Regex("\"country\": \"([A-Z]{2})\"").findAll(manifest).map { it.groupValues[1] }.toList()
        assertEquals(259, countries.size)
        countries.forEach { code ->
            val flag = code.map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }.joinToString("")
            assertEquals(code, listOf(CountryFlagRange(0, 4)), countryFlagRanges(flag))
        }
    }
}
