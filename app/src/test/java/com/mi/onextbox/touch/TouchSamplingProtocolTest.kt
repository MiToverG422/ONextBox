package com.mi.onextbox.touch

import org.junit.Assert.*
import org.junit.Test

class TouchSamplingProtocolTest {
    @Test fun readsSingleLineSupportReply() {
        assertEquals(1, TouchSamplingProtocol.parcelInt("Result: Parcel(00000000 00000001 '........')"))
    }

    @Test fun rejectsBinderExceptionEvenWhenPayloadLooksSupported() {
        assertNull(TouchSamplingProtocol.parcelInt("Result: Parcel(fffffffc 00000001 '........')"))
        assertNull(TouchSamplingProtocol.currentMode("Result: Parcel(fffffffc 00000005 002c0035 00310020 00000030)"))
    }

    @Test fun ignoresHexLookingAsciiPreviewAndAddresses() {
        assertEquals("5, 10", TouchSamplingProtocol.parcelString(
            "Result: Parcel(\n0x00000000: 00000000 00000005 002c0035 00310020 '12345678'\n" +
                "0x00000010: 00000030 '00000001')",
        ))
    }

    @Test fun readsActualMultilinePhoneReply() {
        val reply = "Result: Parcel(\n0x00000000: 00000000 00000005 002c0035 00310020 '........5.,. .1.'\n" +
            "0x00000010: 00000030 '0...            ')"
        assertEquals(5 to 10, TouchSamplingProtocol.currentMode(reply))
    }

    @Test fun handlesNullEmptyAndTruncatedStrings() {
        assertNull(TouchSamplingProtocol.parcelString("Result: Parcel(00000000 ffffffff)"))
        assertEquals("", TouchSamplingProtocol.parcelString("Result: Parcel(00000000 00000000)"))
        assertNull(TouchSamplingProtocol.parcelString("Result: Parcel(00000000 00000005 002c0035)"))
        assertNull(TouchSamplingProtocol.currentMode("Can't find service: touch"))
    }

    @Test fun rejectsInvalidModeRatherThanEnablingWrites() {
        assertNull(TouchSamplingProtocol.currentMode("Result: Parcel(00000000 00000005 002c0035 0031002d 00000030)"))
    }

    @Test fun capabilityMismatchCanRescueReadsButNeverEnablesWrites() {
        for (support in listOf(null, 0, -1, 2)) {
            assertTrue(TouchSamplingProtocol.canDisplay(support, 5 to 10))
            assertFalse(TouchSamplingProtocol.canWrite(support, 5 to 10))
            assertFalse(TouchSamplingProtocol.canDisplay(support, null))
        }
    }

    @Test fun supportedButUnreadableInterfaceCannotBeModified() {
        assertTrue(TouchSamplingProtocol.canDisplay(1, null))
        assertFalse(TouchSamplingProtocol.canWrite(1, null))
        assertTrue(TouchSamplingProtocol.canWrite(1, 5 to 10))
    }

    @Test fun rejectsExtraWordsInvalidColumnsAndIncompleteParcelEnvelopes() {
        for (reply in listOf(
            "Result: Parcel(00000000 00000001 00000000)",
            "Result: Parcel(00000000 garbage 00000001)",
            "Result: Parcel(00000000 00000001",
            "Result: Parcel(00000000 00000001) trailing",
            "Result: Parcel(00000000 00000001 '........' garbage)",
        )) assertNull(reply, TouchSamplingProtocol.parcelInt(reply))
    }

    @Test fun requiresExactStringPayloadAndZeroTerminatorAndPadding() {
        assertEquals("5,10", TouchSamplingProtocol.parcelString("Result: Parcel(00000000 00000004 002c0035 00300031 00000000)"))
        for (reply in listOf(
            "Result: Parcel(00000000 00000005 002c0035 00310020 00000030 00000000)",
            "Result: Parcel(00000000 00000005 002c0035 00310020 00010030)",
            "Result: Parcel(00000000 00000004 002c0035 00300031 00010000)",
            "Result: Parcel(00000000 00000004 002c0035 00300031)",
        )) assertNull(reply, TouchSamplingProtocol.parcelString(reply))
    }

    @Test fun acceptsOnlyCanonicalInRangeIndexedModes() {
        assertEquals(255 to Int.MAX_VALUE, TouchSamplingProtocol.currentMode(stringParcel("255,2147483647")))
        for (mode in listOf("256,0", "05,10", "5,010", "5,2147483648", "5,\t10", "5,\u00a010", " ".repeat(32) + "5,10")) {
            assertNull(mode, TouchSamplingProtocol.currentMode(stringParcel(mode)))
        }
    }

    @Test fun supportsEmptyStringsWithoutAcceptingAppendedPayload() {
        assertEquals("", TouchSamplingProtocol.parcelString("Result: Parcel(00000000 00000000)"))
        assertEquals("", TouchSamplingProtocol.parcelString("Result: Parcel(00000000 00000000 00000000)"))
        assertNull(TouchSamplingProtocol.parcelString("Result: Parcel(00000000 00000000 00000001)"))
        assertNull(TouchSamplingProtocol.parcelString("Result: Parcel(00000000 00000000 00000000 00000000)"))
    }

    private fun stringParcel(text: String): String {
        val encoded = (text + '\u0000').toByteArray(Charsets.UTF_16LE)
        val padded = encoded.copyOf(((encoded.size + 3) / 4) * 4)
        val payload = padded.toList().chunked(4).joinToString(" ") { bytes ->
            val word = bytes.withIndex().fold(0L) { value, (index, byte) ->
                value or ((byte.toLong() and 0xff) shl (index * 8))
            }
            word.toString(16).padStart(8, '0')
        }
        return "Result: Parcel(00000000 ${text.length.toString(16).padStart(8, '0')} $payload)"
    }
}
