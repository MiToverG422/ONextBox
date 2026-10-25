package com.mi.onextbox.lsp

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class SoftwareUpdateDownloadLinksTest {
    private val check = "https://component-ota-cn.allawntech.com/downloadCheck?signature=unchanged%2Btoken"
    private val cdn = "https://gauss-compota-c-cn.allawnfs.com/package.zip?token=keep%2Bencoded"
    private val zip = SoftwareUpdateLinkResponse(206, mediaType = "application/zip", totalBytes = 1234,
        prefix = byteArrayOf(0x50, 0x4b, 0x03, 0x04))

    @Test fun internationalDownloadEndpointsResolveWithoutRewritingSignedParameters() {
        for (host in listOf("component-ota-in.allawnos.com", "component-ota-eu.allawnos.com",
            "component-ota-cn.allawntech.com")) {
            val link = "https://$host/download?p=keep%2Bencoded&s=signed&gkaReq=2&mode=1"
            val target = "https://cdn.allawnos.com/package.zip?token=keep%2Bencoded"
            assertTrue(SoftwareUpdateDownloadLinks.isCheckUrl(link))
            assertTrue(SoftwareUpdateDownloadLinks.trustedCheckUrl(link))
            val calls = arrayListOf<Pair<String, Boolean>>()
            val result = SoftwareUpdateDownloadLinks.resolve(link, 1234) { url, auth ->
                calls += url to auth
                if (url == link) SoftwareUpdateLinkResponse(302, location = target) else zip
            }
            assertEquals(target, result.url)
            assertEquals(listOf(link to true, target to false), calls)
        }
    }

    @Test fun explicitAuthenticationModeTakesPrecedenceWithoutRewritingTheLink() {
        for (path in listOf("download", "downloadCheck")) {
            val base = "https://component-ota-in.allawnos.com/$path"
            for (mode in 0..2) {
                assertEquals(mode, SoftwareUpdateDownloadLinks.authenticationMode("$base?gkaReq=$mode&s=keep%2Btoken", 0))
                assertEquals(mode, SoftwareUpdateDownloadLinks.authenticationMode("$base?s=keep%2Btoken", mode))
            }
            assertEquals(2, SoftwareUpdateDownloadLinks.authenticationMode("$base?gka%52eq=%32", 0))
            assertNull(SoftwareUpdateDownloadLinks.authenticationMode(base, 3))
            for (query in listOf("gkaReq=3", "gkaReq=-1", "gkaReq=", "gkaReq", "gkaReq=wrong",
                "gkaReq=2&gkaReq=0", "gkaReq=2&gkaReq=2", "gkaReq=%ZZ")) {
                assertNull(query, SoftwareUpdateDownloadLinks.authenticationMode("$base?$query", 0))
            }
        }
        assertNull(SoftwareUpdateDownloadLinks.authenticationMode("https://example.test/download?gkaReq=2", 2))
        assertNull(SoftwareUpdateDownloadLinks.authenticationMode(cdn, 2))
    }

    @Test fun downloadCanRedirectToDownloadCheckButCdnNeverReceivesDeviceHeaders() {
        val download = "https://component-ota-in.allawnos.com/download?gkaReq=2&s=signed"
        val checkLink = "https://component-ota-in.allawnos.com/downloadCheck?s=other"
        val calls = arrayListOf<Pair<String, Boolean>>()
        val result = SoftwareUpdateDownloadLinks.resolve(download, 1234) { url, auth ->
            calls += url to auth
            when (url) {
                download -> SoftwareUpdateLinkResponse(302, location = checkLink)
                checkLink -> SoftwareUpdateLinkResponse(307, location = cdn)
                else -> zip
            }
        }
        assertEquals(cdn, result.url)
        assertEquals(listOf(download to true, checkLink to true, cdn to false), calls)
    }

    @Test fun internationalJsonErrorIsNotExportedAsADownloadLink() {
        val result = SoftwareUpdateDownloadLinks.resolve("https://component-ota-in.allawnos.com/download?gkaReq=2", 1234) { _, _ ->
            SoftwareUpdateLinkResponse(200, mediaType = "application/json", serverCode = 2301)
        }
        assertNull(result.url)
        assertEquals(SoftwareUpdateLinkFailure(SoftwareUpdateLinkProblem.Server, 2301), result.failure)
    }

    @Test fun internationalDownloadAuthenticationIsRestrictedToOfficialComponentHosts() {
        for (link in listOf("https://component-ota-in.allawnos.com.evil.test/download",
            "https://other.allawnos.com/download", "http://component-ota-in.allawnos.com/download",
            "https://user@component-ota-in.allawnos.com/download", "https://component-ota-in.allawnos.com:8443/download",
            "https://component-ota-in.allawnos.com/download#fragment")) {
            assertFalse(link, SoftwareUpdateDownloadLinks.trustedCheckUrl(link))
            assertNull(SoftwareUpdateDownloadLinks.resolve(link, 1234) { _, _ -> fail("Unexpected request"); zip }.url)
        }
        assertFalse(SoftwareUpdateDownloadLinks.isCheckUrl("https://cdn.allawnos.com/package.zip?next=/download"))
        assertFalse(SoftwareUpdateDownloadLinks.isCheckUrl("https://component-ota-in.allawnos.com/downloadExtra"))
        assertTrue(SoftwareUpdateDownloadLinks.isCheckUrl("https://component-ota-in.allawnos.com/DOWNLOAD/"))
    }

    @Test fun authenticatedRedirectResolvesToAnUnauthenticatedSizeMatchedZip() {
        val calls = arrayListOf<Pair<String, Boolean>>()
        val result = SoftwareUpdateDownloadLinks.resolve(check, 1234) { url, auth ->
            calls += url to auth
            if (url == check) SoftwareUpdateLinkResponse(302, location = cdn) else zip
        }
        assertEquals(cdn, result.url)
        assertNull(result.failure)
        assertEquals(listOf(check to true, cdn to false), calls)
    }

    @Test fun fullAndIncrementalPackagesUseTheSameResolutionPath() {
        for (size in listOf(298_782_362L, 8_123_456_789L)) {
            val result = SoftwareUpdateDownloadLinks.resolve(check, size) { url, _ ->
                if (url == check) SoftwareUpdateLinkResponse(307, location = cdn) else zip.copy(totalBytes = size)
            }
            assertEquals(cdn, result.url)
        }
    }

    @Test fun checkEndpointsAreRecognizedWithoutChangingTheirQuery() {
        assertTrue(SoftwareUpdateDownloadLinks.isCheckUrl(check))
        assertTrue(SoftwareUpdateDownloadLinks.isCheckUrl("https://component-ota-cn.allawntech.com/DOWNLOADCHECK/"))
        assertFalse(SoftwareUpdateDownloadLinks.isCheckUrl(cdn))
        assertFalse(SoftwareUpdateDownloadLinks.isCheckUrl("https://example.test/update.zip?next=downloadCheck"))
    }

    @Test fun nonOfficialCheckEndpointsAreNeverProbed() {
        for (value in listOf("https://example.test/downloadCheck", "http://component-ota-cn.allawntech.com/downloadCheck",
            "https://allawntech.com.evil.test/downloadCheck", "https://user@component-ota-cn.allawntech.com/downloadCheck",
            "https://component-ota-cn.allawntech.com:8443/downloadCheck", "https://component-ota-cn.allawntech.com/downloadCheck#fragment",
            "https://gauss-compota-c-cn.allawnfs.com/downloadCheck", "https://other.allawntech.com/downloadCheck",
            "https:///downloadCheck", "file:///downloadCheck")) {
            assertNull(value, SoftwareUpdateDownloadLinks.resolve(value, 1234) { _, _ -> fail("Unexpected network request"); zip }.url)
        }
    }

    @Test fun credentialBearingAndNonOfficialRedirectsAreRejectedBeforeAnyRequest() {
        for (location in listOf("https://example.test/update.zip", "http://gauss-compota-c-cn.allawnfs.com/update.zip",
            "https://allawnfs.com.evil.test/update.zip", "https://user:password@gauss-compota-c-cn.allawnfs.com/update.zip",
            "https://127.0.0.1/update.zip", "file:///data/update.zip", "//localhost/update.zip")) {
            var requests = 0
            val result = SoftwareUpdateDownloadLinks.resolve(check, 1234) { _, _ ->
                requests++
                SoftwareUpdateLinkResponse(302, location = location)
            }
            assertNull(location, result.url)
            assertEquals(1, requests)
        }
    }

    @Test fun multipleCdnRedirectsNeverReceiveDeviceHeaders() {
        val next = "https://other.allawnfs.com/final.zip"
        val calls = arrayListOf<Boolean>()
        val result = SoftwareUpdateDownloadLinks.resolve(check, 1234) { url, auth ->
            calls += auth
            when (url) {
                check -> SoftwareUpdateLinkResponse(302, location = cdn)
                cdn -> SoftwareUpdateLinkResponse(308, location = next)
                else -> zip
            }
        }
        assertEquals(next, result.url)
        assertEquals(listOf(true, false, false), calls)
    }

    @Test fun relativeRedirectsAreResolvedAgainstTheCurrentUrl() {
        var requests = 0
        val result = SoftwareUpdateDownloadLinks.resolve(check, 1234) { _, auth ->
            requests++
            if (requests == 1) SoftwareUpdateLinkResponse(303, location = "/package.zip?token=kept%2Bencoded")
            else { assertFalse(auth); zip }
        }
        assertEquals("https://component-ota-cn.allawntech.com/package.zip?token=kept%2Bencoded", result.url)
    }

    @Test fun successfulHttpStatusWithAJsonErrorIsNotAFile() {
        val result = SoftwareUpdateDownloadLinks.resolve(check, 1234) { _, _ ->
            SoftwareUpdateLinkResponse(200, mediaType = "application/json", serverCode = 2306)
        }
        assertNull(result.url)
        assertEquals(2306, result.failure?.code)
        assertEquals(SoftwareUpdateLinkProblem.Server, result.failure?.problem)
    }

    @Test fun validAuthenticatedFileMustAlsoBeReadableWithoutAuthentication() {
        val calls = arrayListOf<Boolean>()
        val result = SoftwareUpdateDownloadLinks.resolve(check, 1234) { _, auth ->
            calls += auth
            if (auth) zip else SoftwareUpdateLinkResponse(403)
        }
        assertNull(result.url)
        assertEquals(SoftwareUpdateLinkProblem.Authentication, result.failure?.problem)
        assertEquals(listOf(true, false), calls)
    }

    @Test fun aCheckUrlThatReallyWorksWithoutHeadersCanBeExported() {
        assertEquals(check, SoftwareUpdateDownloadLinks.resolve(check, 1234) { _, _ -> zip }.url)
    }

    @Test fun wrongSizesHtmlJsonAndNonPackagesAreNotReportedAsDownloads() {
        for (response in listOf(zip.copy(totalBytes = 1233), zip.copy(totalBytes = null), zip.copy(prefix = byteArrayOf()),
            zip.copy(prefix = "<htm".toByteArray()), zip.copy(mediaType = "text/html"), zip.copy(mediaType = "application/json"),
            zip.copy(status = 404))) {
            assertFalse(SoftwareUpdateDownloadLinks.packageResponse(response, 1234))
        }
        assertFalse(SoftwareUpdateDownloadLinks.packageResponse(zip, 0))
        assertTrue(SoftwareUpdateDownloadLinks.packageResponse(zip.copy(status = 200), 1234))
        assertTrue(SoftwareUpdateDownloadLinks.packageResponse(zip.copy(prefix = "CrAU".toByteArray()), 1234))
    }

    @Test fun redirectsHaveALoopGuardAndAHardHopLimit() {
        var requests = 0
        assertNull(SoftwareUpdateDownloadLinks.resolve(check, 1234) { _, _ ->
            requests++
            SoftwareUpdateLinkResponse(302, location = check)
        }.url)
        assertEquals(1, requests)
        requests = 0
        assertNull(SoftwareUpdateDownloadLinks.resolve(check, 1234) { _, _ ->
            requests++
            SoftwareUpdateLinkResponse(302, location = "https://cdn.allawnfs.com/$requests.zip")
        }.url)
        assertEquals(6, requests)
    }

    @Test fun aRedirectWithoutALocationCannotBecomeADownload() {
        assertNull(SoftwareUpdateDownloadLinks.resolve(check, 1234) { _, _ -> SoftwareUpdateLinkResponse(302) }.url)
    }

    @Test fun resolutionDoesNotTriggerOtaJobsOrWriteActivationOrDeviceKeys() {
        val source = source("SoftwareUpdateLinkClient.kt") + source("SoftwareUpdateDownloadAuth.kt")
        for (forbidden in listOf("startService", "sendBroadcast", "applyPayload", "generateKeyPair", "generateX509",
            "KeyPairGenerator", ".setKeyEntry(", ".deleteEntry(", ".insert(", ".update(", ".delete(", "setPrimaryClip")) {
            assertFalse(forbidden, source.contains(forbidden))
        }
        assertTrue(source.contains("bytes=0-3"))
        assertTrue(source.contains("instanceFollowRedirects = false"))
        assertTrue(source.contains("setOnCancelListener"))
        assertTrue(source.contains("TimeUnit.SECONDS.toNanos(25)"))
        assertTrue(source.contains("readLimited(it, 4_096)"))
        assertFalse(source.contains("HookLog"))
        val menu = source("SoftwareUpdateMenuController.kt")
        assertTrue(menu.contains("packages.flatMap { it.downloadLinks }"))
        assertTrue(menu.contains("dialogCancellations.remove(dialog)?.cancel()"))
    }

    private fun source(name: String) = listOf(File("src/main/java/com/mi/onextbox/lsp/$name"),
        File("app/src/main/java/com/mi/onextbox/lsp/$name")).first { it.isFile }.readText()
}
