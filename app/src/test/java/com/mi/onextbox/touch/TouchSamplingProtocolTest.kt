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
}
