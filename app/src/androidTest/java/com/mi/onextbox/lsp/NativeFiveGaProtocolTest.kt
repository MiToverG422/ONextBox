package com.mi.onextbox.lsp

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeFiveGaProtocolTest {
    private fun snapshot() = NativeFiveGaSnapshot("phone-original", 23,
        listOf(NativeFiveGaSlot(0, 1, "test", 8, true, 20, 5, true, "qualified", 3, 300_000)), "qualified")

    @Test fun compatibleScopesCanReconnectAcrossDifferentApkBuilds() {
        for (build in listOf(1L, NativeFiveGaProtocol.build - 1, NativeFiveGaProtocol.build + 1)) {
            val bundle = NativeFiveGaProtocol.encode(snapshot())!!.apply { putLong("build", build) }
            assertTrue(NativeFiveGaProtocol.compatible(bundle))
            assertEquals(snapshot(), NativeFiveGaProtocol.decode(bundle))
        }
    }

    @Test fun buildMetadataIsOptionalButTheWireVersionIsMandatory() {
        val bundle = NativeFiveGaProtocol.encode(snapshot())!!.apply { remove("build") }
        assertEquals(snapshot(), NativeFiveGaProtocol.decode(bundle))
        bundle.remove("protocol")
        assertFalse(NativeFiveGaProtocol.compatible(bundle))
        assertNull(NativeFiveGaProtocol.decode(bundle))
        assertFalse(NativeFiveGaProtocol.compatible(null))
    }

    @Test fun incompatibleSchemasAreStillRejectedEvenWhenTheApkBuildMatches() {
        for (version in listOf(-1, 0, NativeFiveGaProtocol.VERSION + 1)) {
            val bundle = NativeFiveGaProtocol.encode(snapshot())!!.apply { putInt("protocol", version) }
            assertFalse(NativeFiveGaProtocol.compatible(bundle))
            assertNull(NativeFiveGaProtocol.decode(bundle))
        }
    }

    @Test fun aNewBrokerLetsARetainedPhoneScopePublishWithItsOriginalHandshake() {
        val legacyBuild = 1234567L
        val request = NativeFiveGaProtocol.envelope().apply { putLong("build", legacyBuild) }
        val response = NativeFiveGaProtocol.response(request)
        assertEquals(NativeFiveGaProtocol.VERSION, response.getInt("protocol"))
        assertEquals(legacyBuild, response.getLong("build"))
        assertEquals(legacyBuild, request.getLong("build"))
        assertTrue(NativeFiveGaProtocol.compatible(response))
    }

    @Test fun aSchemaOnlyClientGetsTheCurrentBuildMetadataWithoutChangingTheRequest() {
        val request = Bundle().apply { putInt("protocol", NativeFiveGaProtocol.VERSION) }
        val response = NativeFiveGaProtocol.response(request)
        assertEquals(NativeFiveGaProtocol.build, response.getLong("build"))
        assertFalse(request.containsKey("build"))
    }

    @Test fun crossBuildCompatibilityDoesNotMakeAnOrdinaryNrStateQualified() {
        for (network in listOf(0, 13, 19)) {
            val bundle = NativeFiveGaProtocol.encode(snapshot())!!.apply { putLong("build", 1L) }
            bundle.getParcelableArrayList("slots", Bundle::class.java)!!.single().putInt("network", network)
            assertNull(NativeFiveGaProtocol.decode(bundle))
        }
        val bundle = NativeFiveGaProtocol.encode(snapshot())!!.apply { putLong("build", 1L) }
        bundle.getParcelableArrayList("slots", Bundle::class.java)!!.single().putBoolean("ready", false)
        assertNull(NativeFiveGaProtocol.decode(bundle))
    }

    @Test fun reconnectionDoesNotAcceptAStaleSourceOnAnExistingEndpoint() {
        val old = NativeFiveGaReceipt()
        assertEquals(NativeFiveGaReceipt.Result.ACCEPT, old.receive("old-phone", 100))
        assertEquals(NativeFiveGaReceipt.Result.INVALID, old.receive("new-phone", 0))
        assertEquals(NativeFiveGaReceipt.Result.IGNORE, old.receive("old-phone", 101))
        assertEquals(NativeFiveGaReceipt.Result.ACCEPT, NativeFiveGaReceipt().receive("new-phone", 0))
    }

    @Test fun independentRestartsDoNotRelaxPublisherAndReaderPermissions() {
        assertTrue(NativeFiveGaAccess.publisher(1001))
        assertFalse(NativeFiveGaAccess.publisher(1000))
        assertFalse(NativeFiveGaProtocol.canRead(1001, 12000, 10309))
        assertFalse(NativeFiveGaProtocol.canRead(12001, 12000, 10309))
        assertTrue(NativeFiveGaProtocol.canRead(10309, 12000, 10309))
    }
}
