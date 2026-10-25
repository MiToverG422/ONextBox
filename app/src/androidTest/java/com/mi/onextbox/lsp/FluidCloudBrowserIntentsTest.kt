package com.mi.onextbox.lsp

import android.app.SearchManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FluidCloudBrowserIntentsTest {
    @Test
    fun webDataSearchBecomesAnExplicitBrowserViewWithoutChangingTheCardIntent() {
        val url = "https://example.com/a?q=a%2Bb&x=1#part"
        val original = Intent(Intent.ACTION_WEB_SEARCH).apply {
            component = ComponentName(FluidCloudBrowserRules.STOCK_BROWSER, "StockActivity")
            setPackage(FluidCloudBrowserRules.STOCK_BROWSER)
            putExtra(SearchManager.QUERY, url)
            putExtra("source", "fluid-cloud")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val extracted = FluidCloudBrowserRules.launchUrl(
            original.action, original.dataString, original.getStringExtra(SearchManager.QUERY), 29,
        )!!
        val result = FluidCloudBrowserIntents.redirect(original, extracted, "test.default.browser")

        assertEquals(Intent.ACTION_VIEW, result.action)
        assertEquals(url, result.dataString)
        assertEquals("test.default.browser", result.`package`)
        assertNull(result.component)
        assertTrue(result.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertFalse(result.hasExtra(SearchManager.QUERY))
        assertEquals("fluid-cloud", result.getStringExtra("source"))
        assertEquals(original.flags, result.flags)
        assertEquals(Intent.ACTION_WEB_SEARCH, original.action)
        assertEquals(url, original.getStringExtra(SearchManager.QUERY))
        assertNull(original.data)
        assertEquals(FluidCloudBrowserRules.STOCK_BROWSER, original.component?.packageName)
        assertEquals(FluidCloudBrowserRules.STOCK_BROWSER, original.`package`)
    }

    @Test
    fun deepLinkWrapperKeepsEncodedUrlParametersAfterConversion() {
        val original = Intent(Intent.ACTION_VIEW,
            Uri.parse("heytapbrowser://webpage?url=https%3A%2F%2Fexample.com%2F%3Fx%3D%252B"))
        val extracted = FluidCloudBrowserRules.launchUrl(original.action, original.dataString, null, null)!!
        val result = FluidCloudBrowserIntents.redirect(original, extracted, "test.default.browser")
        assertEquals("https://example.com/?x=%2B", result.dataString)
        assertEquals("+", result.data?.getQueryParameter("x"))
        assertEquals("heytapbrowser", original.data?.scheme)
    }

    @Test
    fun changingTheDefaultDoesNotLeaveAStaleTargetInTheOriginalCard() {
        val original = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/"))
            .setPackage(FluidCloudBrowserRules.STOCK_BROWSER)
        val first = FluidCloudBrowserIntents.redirect(original, original.dataString!!, "first.browser")
        val second = FluidCloudBrowserIntents.redirect(original, original.dataString!!, "second.browser")
        assertEquals("first.browser", first.`package`)
        assertEquals("second.browser", second.`package`)
        assertEquals(FluidCloudBrowserRules.STOCK_BROWSER, original.`package`)
    }
}
