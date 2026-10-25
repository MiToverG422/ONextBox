package com.mi.onextbox.lsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FluidCloudBrowserRulesTest {
    @Test
    fun nullEmptyOrUnavailableDefaultLookupFallsBack() {
        assertEquals("com.quark.browser", FluidCloudBrowserRules.firstAvailableBrowser(
            { null }, { " " }, { throw SecurityException() }, { "android" }, { "com.quark.browser" },
        ))
        assertEquals(null, FluidCloudBrowserRules.firstAvailableBrowser({ null }, { "" }))
    }

    @Test
    fun browserRoleWinsAndIsQueriedAgainForEachClick() {
        var role = "com.quark.browser"
        val lookup = { role }
        assertEquals(role, FluidCloudBrowserRules.firstAvailableBrowser(lookup, { error("Not needed") }))
        role = "mark.via"
        assertEquals(role, FluidCloudBrowserRules.firstAvailableBrowser(lookup))
    }

    @Test
    fun recognizedWebLinksCanUseTheSystemDefaultBrowser() {
        assertTrue(accepts())
        assertTrue(accepts(scheme = "http"))
        assertTrue(accepts(scheme = "HTTPS"))
        assertTrue(accepts(host = "127.0.0.1"))
        assertTrue(accepts(explicitPackage = STOCK_BROWSER, componentPackage = STOCK_BROWSER))
    }

    @Test
    fun recognizedWebDataCanCarryTheUrlInItsSearchQuery() {
        val url = "https://example.com/a?q=a%2Bb&x=1#part"
        assertEquals(url, FluidCloudBrowserRules.launchUrl(
            "android.intent.action.WEB_SEARCH", null, url, 29,
        ))
        assertTrue(accepts(action = "android.intent.action.WEB_SEARCH", textType = 29))
        assertEquals(url, FluidCloudBrowserRules.launchUrl(
            "android.intent.action.VIEW", url, null, null,
        ))
    }

    @Test
    fun webDataQueryCanUseTheStockBrowserWebpageWrapper() {
        assertEquals("https://example.com/?x=%2B", FluidCloudBrowserRules.launchUrl(
            "android.intent.action.WEB_SEARCH", null,
            "heytapbrowser://webpage?url=https%3A%2F%2Fexample.com%2F%3Fx%3D%252B", 29,
        ))
    }

    @Test
    fun textSearchesAndOtherQueryTypesKeepTheirOriginalRoute() {
        listOf(null, "", "weather tomorrow", "example.com", "weixin://scan", "javascript:alert(1)")
            .forEach { query ->
                assertEquals(null, FluidCloudBrowserRules.launchUrl(
                    "android.intent.action.WEB_SEARCH", null, query, 29,
                ))
            }
        listOf(null, 2, 4).forEach { type ->
            assertEquals(null, FluidCloudBrowserRules.launchUrl(
                "android.intent.action.WEB_SEARCH", null, "https://example.com/", type,
            ))
            assertFalse(accepts(action = "android.intent.action.WEB_SEARCH", textType = type))
        }
    }

    @Test
    fun conflictingOrUnrelatedIntentPayloadsAreNotTreatedAsWebData() {
        assertEquals(null, FluidCloudBrowserRules.launchUrl(
            "android.intent.action.WEB_SEARCH", "https://other.example/", "https://example.com/", 29,
        ))
        assertEquals(null, FluidCloudBrowserRules.launchUrl(
            "android.intent.action.SEND", "https://example.com/", "https://example.com/", 29,
        ))
        assertEquals(null, FluidCloudBrowserRules.launchUrl(
            "android.intent.action.VIEW", null, "https://example.com/", 29,
        ))
    }

    @Test
    fun disablingTheFeatureLeavesAnExistingStockCardUnchanged() {
        assertFalse(accepts(enabled = false))
    }

    @Test
    fun onlyRecognizedWebLinksAndActivityLaunchesAreEligible() {
        // C17 Metis DeepLinkData leaves the type unset; package/action/URL define a browser link.
        assertTrue(accepts(textType = null))
        assertTrue(accepts(textType = 29))
        assertTrue(accepts(textType = 4))
        assertFalse(accepts(intentType = 2))
        assertFalse(accepts(intentType = 0))
        assertFalse(accepts(action = "android.intent.action.WEB_SEARCH"))
        assertFalse(accepts(action = "android.intent.action.SEND"))
        assertFalse(accepts(action = null))
    }

    @Test
    fun metisBrowserWrapperExtractsOnlyTheActualWebUrl() {
        assertEquals("https://example.com/a?q=a%2Bb&x=1#part", FluidCloudBrowserRules.webUrl(
            "heytapbrowser://webpage?url=https%3A%2F%2Fexample.com%2Fa%3Fq%3Da%252Bb%26x%3D1%23part&source=cloud",
        ))
        assertEquals("http://example.com/", FluidCloudBrowserRules.webUrl(
            "HEYTAPBROWSER://WEBPAGE?url=http%3A%2F%2Fexample.com%2F",
        ))
        assertEquals("https://example.com/?x=%2B", FluidCloudBrowserRules.webUrl("https://example.com/?x=%2B"))
    }

    @Test
    fun invalidAndNonWebBrowserRoutesRemainUntouched() {
        listOf(null, "", "heytapbrowser://webpage", "heytapbrowser://search?url=https://example.com",
            "heytapbrowser://webpage?url=javascript%3Aalert(1)",
            "heytapbrowser://webpage?url=weixin%3A%2F%2Fscan",
            "heytapbrowser://webpage?url=https%3A%2F%2F",
            "heytapbrowser://webpage?url=%ZZ", "heytapbrowser://webpage?url=example.com",
            "heytapbrowser://webpage?url=https://a.test&url=https://b.test",
            "heytapbrowser://webpage/other?url=https://example.com",
            "heytapbrowser://user@webpage?url=https://example.com",
            "weixin://webpage?url=https://example.com")
            .forEach { assertEquals(it, null, FluidCloudBrowserRules.webUrl(it)) }
    }

    @Test
    fun quickAppsAndExplicitlyChosenThirdPartyAppsAreNotRedirected() {
        assertFalse(accepts(packageName = "com.nearme.instant.platform"))
        assertFalse(accepts(packageName = "com.android.chrome"))
        assertFalse(accepts(packageName = null))
        assertFalse(accepts(explicitPackage = "com.tencent.mm"))
        assertFalse(accepts(componentPackage = "com.tencent.mm"))
        assertFalse(accepts(explicitPackage = STOCK_BROWSER, componentPackage = "com.tencent.mm"))
        assertFalse(accepts(explicitPackage = "com.tencent.mm", componentPackage = STOCK_BROWSER))
    }

    @Test
    fun customProtocolsAndMissingWebHostsAreNotRedirected() {
        listOf(null, "", "intent", "alipays", "weixin", "file", "content", "javascript")
            .forEach { assertFalse("Unexpected scheme: $it", accepts(scheme = it)) }
        listOf(null, "", " ", "\t\n")
            .forEach { assertFalse("Unexpected host: $it", accepts(host = it)) }
    }

    @Test
    fun selectorsAreNotRewrittenEvenIfTheOuterIntentIsAWebLink() {
        assertFalse(accepts(hasSelector = true))
    }

    @Test
    fun temporaryPackageIsVisibleOnlyDuringLaunch() {
        var currentPackage: String? = STOCK_BROWSER
        val packageWrites = mutableListOf<String?>()
        val result = FluidCloudBrowserRules.withTarget(
            originalPackage = currentPackage,
            targetPackage = "com.android.chrome",
            setPackage = { currentPackage = it; packageWrites.add(it) },
        ) {
            assertEquals("com.android.chrome", currentPackage)
            "launched"
        }
        assertEquals("launched", result)
        assertEquals(STOCK_BROWSER, currentPackage)
        assertEquals(listOf("com.android.chrome", STOCK_BROWSER), packageWrites)
    }

    @Test
    fun failedLaunchRestoresMetadataAndDoesNotRetry() {
        var currentPackage: String? = STOCK_BROWSER
        var launchCount = 0
        val failure = IllegalStateException("Launch failed after being attempted")
        try {
            FluidCloudBrowserRules.withTarget(
                originalPackage = currentPackage,
                targetPackage = "com.android.chrome",
                setPackage = { currentPackage = it },
            ) {
                launchCount++
                throw failure
            }
            fail("The original launch failure must propagate")
        } catch (actual: IllegalStateException) {
            assertSame(failure, actual)
        }
        assertEquals(1, launchCount)
        assertEquals(STOCK_BROWSER, currentPackage)
    }

    @Test
    fun failedMetadataPreparationDoesNotLaunchAndStillAttemptsRestoration() {
        val packageWrites = mutableListOf<String?>()
        var launchCount = 0
        val failure = IllegalAccessException("Cannot change target package")
        try {
            FluidCloudBrowserRules.withTarget(
                originalPackage = STOCK_BROWSER,
                targetPackage = "com.android.chrome",
                setPackage = {
                    packageWrites.add(it)
                    if (it == "com.android.chrome") throw failure
                },
            ) { launchCount++ }
            fail("The preparation failure must propagate")
        } catch (actual: IllegalAccessException) {
            assertSame(failure, actual)
        }
        assertEquals(0, launchCount)
        assertEquals(listOf("com.android.chrome", STOCK_BROWSER), packageWrites)
    }

    @Test
    fun consecutiveClicksCanFollowDifferentDefaultsWithoutStaleMetadata() {
        var currentPackage: String? = STOCK_BROWSER
        val launchedPackages = mutableListOf<String?>()
        listOf("com.android.chrome", "mark.via").forEach { defaultBrowser ->
            assertTrue(accepts(packageName = currentPackage))
            FluidCloudBrowserRules.withTarget(
                originalPackage = currentPackage,
                targetPackage = defaultBrowser,
                setPackage = { currentPackage = it },
            ) { launchedPackages.add(currentPackage) }
            assertEquals(STOCK_BROWSER, currentPackage)
        }
        assertEquals(listOf("com.android.chrome", "mark.via"), launchedPackages)
    }

    private fun accepts(
        enabled: Boolean = true,
        textType: Int? = 2,
        intentType: Int = 1,
        packageName: String? = STOCK_BROWSER,
        action: String? = "android.intent.action.VIEW",
        scheme: String? = "https",
        host: String? = "example.com",
        explicitPackage: String? = null,
        componentPackage: String? = null,
        hasSelector: Boolean = false,
    ): Boolean = FluidCloudBrowserRules.accepts(
        enabled, textType, intentType, packageName, action, scheme, host,
        explicitPackage, componentPackage, hasSelector,
    )

    private companion object {
        const val STOCK_BROWSER = "com.heytap.browser"
    }
}
