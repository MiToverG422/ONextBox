package com.mi.onextbox.lsp

import java.net.URI
import java.net.URLDecoder
import java.util.Locale

// Parcel card destinations and tracking number detection.
internal object ExpressCardRules {
    const val PLATFORM = "com.nearme.instant.platform"
    const val LAUNCHER = "com.android.launcher"
    const val ASSISTANT = "com.coloros.assistantscreen"
    const val EXPRESS = "com.nearme.quickapp.express"
    const val MARKER = "__nomini"
    private const val BASE = "hap://app/$EXPRESS/pages/"
    val packages = setOf(PLATFORM, LAUNCHER, ASSISTANT)
    val requestFields = listOf("path", "extData", "messageExt", "extInfo")
    private val callerKeys = setOf("HAP_PACKAGE", "EXTRA_CALLING_QUICK_PKG", "EXTRA_CALLING_PKG")
    private val hiddenPackages = setOf(
        "com.tencent.mm", "com.eg.android.AlipayGphone", "com.taobao.taobao",
        "com.cainiao.wireless", "com.jingdong.app.mall", "com.sf.activity", "com.fcbox.hiveconsumer",
    )
    private val waybill = Regex("(?:[0-9]{10,24}|[A-Za-z]{2}[0-9]{9,20})")
    private val waybillHints = listOf("waybill", "express", "mail", "tracking", "billno", "bill_no", "kuaidi")
    private val waybillInText = Regex(
        "(?:expressNo|mailNo|waybillNo|trackingNo|billNo)=([A-Za-z0-9]{10,24})(?=[&#\\s]|$)",
        RegexOption.IGNORE_CASE,
    )

    fun isHiddenPackage(name: String?): Boolean = name in hiddenPackages

    fun trackingNumber(value: String?): String? = value?.trim()?.takeIf(waybill::matches)

    fun isExpressUri(text: String?): Boolean = uri(text)?.let(::isExpressUri) == true

    fun pendingTrackingNumber(text: String?): String? {
        val uri = uri(text) ?: return null
        if (!isExpressUri(uri) || uri.path != "/$EXPRESS/pages/expressSearch") return null
        return trackingNumber(query(uri)[MARKER])
    }

    fun destination(number: String?): String {
        val tracking = trackingNumber(number) ?: return "${BASE}expressList"
        return "${BASE}expressSearch?expressNo=$tracking&waybillno=$tracking&$MARKER=$tracking"
    }

    fun redirect(packageName: String, action: String?, data: String?, extras: Map<String, String>): String? {
        if (packageName !in packages) return null
        val uri = uri(data)
        if (uri != null && isExpressUri(uri)) return null
        if (packageName != PLATFORM) {
            val fromExpress = callerKeys.any { extras[it] == EXPRESS } || extras["EXTRA_FROM"]?.contains(EXPRESS) == true
            if (!fromExpress) return null
            val parameters = uri?.let(::query).orEmpty()
            return destination(findTracking(parameters) ?: findTracking(extras))
        }
        if (action != "android.intent.action.VIEW" || uri == null || uri.scheme != "hap" || uri.host != "app") return null
        if (uri.path.isNullOrEmpty() || uri.path == "/") return null
        val parameters = query(uri)
        val tracking = trackingNumber(parameters["waybillno"]) ?: trackingNumber(parameters["expressNo"]) ?: return null
        return destination(tracking)
    }

    fun wechatDestination(fields: Map<String, String>, fromCardSdk: Boolean): String? {
        val values = fields.values
        val cardRequest = fromCardSdk || values.any { it.contains("sceneid=card", ignoreCase = true) }
        if (!cardRequest) return null
        val tracking = values.firstNotNullOfOrNull { text ->
            waybillInText.findAll(text).firstNotNullOfOrNull { trackingNumber(it.groupValues[1]) }
        }
        // Keep WeChat login, payments, and sharing unchanged for other cards.
        if (tracking == null && values.none { it.contains(EXPRESS) }) return null
        return destination(tracking)
    }

    private fun findTracking(values: Map<String, String>): String? = values.entries.firstNotNullOfOrNull { (key, value) ->
        val name = key.lowercase(Locale.ROOT)
        if (waybillHints.any(name::contains)) trackingNumber(value) else null
    }

    private fun uri(text: String?): URI? = text?.let { runCatching { URI(it) }.getOrNull() }

    private fun isExpressUri(uri: URI): Boolean =
        uri.scheme == "hap" && uri.host == "app" && uri.path?.split('/')?.getOrNull(1) == EXPRESS

    private fun query(uri: URI): Map<String, String> = uri.rawQuery.orEmpty().split('&').mapNotNull { part ->
        val separator = part.indexOf('=')
        if (separator < 0) return@mapNotNull null
        runCatching {
            URLDecoder.decode(part.take(separator), "UTF-8") to URLDecoder.decode(part.substring(separator + 1), "UTF-8")
        }.getOrNull()
    }.toMap()
}
