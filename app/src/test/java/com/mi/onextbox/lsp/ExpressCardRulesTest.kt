package com.mi.onextbox.lsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressCardRulesTest {
    private val number = "123456789012"
    private val ownSearch = "hap://app/com.nearme.quickapp.express/pages/expressSearch"
    private val card = mapOf("HAP_PACKAGE" to ExpressCardRules.EXPRESS)

    @Test fun onlyTheThreeModulePackagesAreSupported() {
        assertEquals(setOf("com.nearme.instant.platform", "com.android.launcher", "com.coloros.assistantscreen"), ExpressCardRules.packages)
        assertNull(ExpressCardRules.redirect("com.tencent.mm", "android.intent.action.VIEW", "weixin://app", card))
    }

    @Test fun nativeExpressLinksAreNeverRedirectedAgain() {
        for (pkg in ExpressCardRules.packages) {
            assertNull(ExpressCardRules.redirect(pkg, "android.intent.action.VIEW", "$ownSearch?expressNo=$number", card))
            assertNull(ExpressCardRules.redirect(pkg, "android.intent.action.VIEW", ExpressCardRules.destination(null), card))
        }
    }

    @Test fun hostWithoutAnExpressCallerDoesNotChangeAnyJump() {
        for (pkg in listOf(ExpressCardRules.LAUNCHER, ExpressCardRules.ASSISTANT)) {
            assertNull(ExpressCardRules.redirect(pkg, "android.intent.action.VIEW", "weixin://app?waybillno=$number", emptyMap()))
            assertNull(ExpressCardRules.redirect(pkg, "android.intent.action.VIEW", "alipays://app", mapOf("HAP_PACKAGE" to "com.other.card")))
        }
    }

    @Test fun allExpressCallerKeysCanRedirect() {
        for (key in listOf("HAP_PACKAGE", "EXTRA_CALLING_QUICK_PKG", "EXTRA_CALLING_PKG")) {
            for (pkg in listOf(ExpressCardRules.LAUNCHER, ExpressCardRules.ASSISTANT)) {
                assertEquals(ExpressCardRules.destination(number), ExpressCardRules.redirect(
                    pkg, "android.intent.action.VIEW", "weixin://app?waybillno=$number", mapOf(key to ExpressCardRules.EXPRESS),
                ))
            }
        }
    }

    @Test fun expressCardCanUseAnExtraFromMarker() {
        assertEquals(ExpressCardRules.destination(number), ExpressCardRules.redirect(
            ExpressCardRules.ASSISTANT, "android.intent.action.VIEW", "alipays://app",
            mapOf("EXTRA_FROM" to "card:${ExpressCardRules.EXPRESS}", "trackingNo" to number),
        ))
    }

    @Test fun identifiedCardWithoutATrackingNumberFallsBackToTheList() {
        assertEquals("hap://app/com.nearme.quickapp.express/pages/expressList", ExpressCardRules.redirect(
            ExpressCardRules.LAUNCHER, "android.intent.action.VIEW", "weixin://app", card,
        ))
    }

    @Test fun trackingNumberMayComeFromExtras() {
        assertEquals(ExpressCardRules.destination(number), ExpressCardRules.redirect(
            ExpressCardRules.LAUNCHER, "android.intent.action.VIEW", "weixin://app", card + ("express_no" to number),
        ))
    }

    @Test fun frameworkRewritesForeignQuickAppLinksWithAValidNumber() {
        for (key in listOf("waybillno", "expressNo")) {
            assertEquals(ExpressCardRules.destination(number), ExpressCardRules.redirect(
                ExpressCardRules.PLATFORM, "android.intent.action.VIEW", "hap://app/other.express/pages/search?$key=$number", emptyMap(),
            ))
        }
    }

    @Test fun frameworkLeavesOtherSchemesAndActionsUnchanged() {
        for (url in listOf("https://app/other?waybillno=$number", "weixin://app?waybillno=$number", "hap://other/other?waybillno=$number")) {
            assertNull(ExpressCardRules.redirect(ExpressCardRules.PLATFORM, "android.intent.action.VIEW", url, emptyMap()))
        }
        assertNull(ExpressCardRules.redirect(ExpressCardRules.PLATFORM, "android.intent.action.SEND", "hap://app/other?waybillno=$number", emptyMap()))
        assertNull(ExpressCardRules.redirect(ExpressCardRules.PLATFORM, "android.intent.action.VIEW", "hap://app/?waybillno=$number", emptyMap()))
    }

    @Test fun missingMalformedOrInvalidNumbersAreNotRewrittenByTheFramework() {
        for (url in listOf(null, "not a uri", "hap://app/other", "hap://app/other?waybillno=123", "hap://app/other?waybillno=%zz")) {
            assertNull(ExpressCardRules.redirect(ExpressCardRules.PLATFORM, "android.intent.action.VIEW", url, emptyMap()))
        }
    }

    @Test fun trackingNumbersAreValidatedBeforeBuildingALink() {
        for (value in listOf("1234567890", "123456789012345678901234", "SF123456789", "AB12345678901234567890")) {
            assertEquals(value, ExpressCardRules.trackingNumber(value))
        }
        for (value in listOf(null, "123456789", "1234567890123456789012345", "A1234567890", "123456789012&evil=true", "<script>123456789</script>")) {
            assertNull(ExpressCardRules.trackingNumber(value))
        }
        assertEquals(number, ExpressCardRules.trackingNumber(" $number "))
    }

    @Test fun markerIsAcceptedOnlyForTheNativeExpressSearchPage() {
        assertEquals(number, ExpressCardRules.pendingTrackingNumber(ExpressCardRules.destination(number)))
        for (url in listOf("weixin://app?$MARKER=$number", "hap://app/other/pages/expressSearch?$MARKER=$number", "hap://app/${ExpressCardRules.EXPRESS}/pages/expressList?$MARKER=$number", "$ownSearch?$MARKER=123")) {
            assertNull(ExpressCardRules.pendingTrackingNumber(url))
        }
    }

    @Test fun onlyNativeAppUrisAreExpressUris() {
        assertTrue(ExpressCardRules.isExpressUri(ownSearch))
        assertFalse(ExpressCardRules.isExpressUri("https://app/${ExpressCardRules.EXPRESS}/pages/expressSearch"))
        assertFalse(ExpressCardRules.isExpressUri("hap://app/${ExpressCardRules.EXPRESS}.fake/pages/expressSearch"))
    }

    @Test fun wxExpressCardQueryUsesTheNativeSearchPage() {
        assertEquals(ExpressCardRules.destination(number), ExpressCardRules.wechatDestination(mapOf("path" to "pages/query?expressNo=$number&sceneid=card"), false))
        assertEquals(ExpressCardRules.destination(number), ExpressCardRules.wechatDestination(mapOf("extData" to "waybillNo=$number"), true))
    }

    @Test fun ordinaryWechatLoginPaymentAndSharingAreNotChanged() {
        for (fields in listOf(
            mapOf("path" to "pages/query?expressNo=$number"),
            mapOf("path" to "pages/payment?sceneid=card"),
            mapOf("extInfo" to "login"),
            emptyMap(),
        )) {
            assertNull(ExpressCardRules.wechatDestination(fields, false))
        }
        assertNull(ExpressCardRules.wechatDestination(mapOf("path" to "pages/payment?sceneid=card"), true))
    }

    @Test fun wxExpressCardWithNoNumberFallsBackToTheList() {
        assertEquals(ExpressCardRules.destination(null), ExpressCardRules.wechatDestination(mapOf("path" to "${ExpressCardRules.EXPRESS}?sceneid=card"), false))
    }

    @Test fun onlyDeliveryRelatedNativePackagesAreHidden() {
        for (name in listOf("com.tencent.mm", "com.eg.android.AlipayGphone", "com.taobao.taobao", "com.cainiao.wireless", "com.jingdong.app.mall", "com.sf.activity", "com.fcbox.hiveconsumer")) {
            assertTrue(ExpressCardRules.isHiddenPackage(name))
        }
        for (name in listOf(null, "com.android.settings", ExpressCardRules.PLATFORM, "com.tencent.mm.fake")) {
            assertFalse(ExpressCardRules.isHiddenPackage(name))
        }
    }

    private companion object { const val MARKER = ExpressCardRules.MARKER }
}
