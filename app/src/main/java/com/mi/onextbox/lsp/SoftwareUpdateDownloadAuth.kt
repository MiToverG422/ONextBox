package com.mi.onextbox.lsp

import android.content.Context
import java.net.HttpURLConnection
import java.net.URI
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.security.MessageDigest
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import org.json.JSONObject

internal class SoftwareUpdateAuthenticationUnavailable : Exception()

internal class SoftwareUpdateDownloadAuth(private val context: Context, private val attestation: Method?) {
    private val propertyGetter by lazy {
        Class.forName("android.os.SystemProperties").getMethod("get", String::class.java, String::class.java)
    }
    private fun property(key: String): String = propertyGetter.invoke(null, key, "") as String

    fun apply(connection: HttpURLConnection, url: String, timestamp: () -> String) {
        check(SoftwareUpdateDownloadLinks.trustedCheckUrl(url))
        val sessionMode = context.getSharedPreferences("state_info", Context.MODE_PRIVATE).getInt("gka_req_download", 0)
        val mode = SoftwareUpdateDownloadLinks.authenticationMode(url, sessionMode)
            ?: throw SoftwareUpdateAuthenticationUnavailable()
        val version = context.packageManager.getPackageInfo("com.oplus.ota", 0).longVersionCode
        connection.setRequestProperty("userId", "oplus-ota|$version")
        connection.setRequestProperty("marketName", Base64.getEncoder().encodeToString(property("ro.vendor.oplus.market.name").toByteArray()))
        val provider = context.classLoader.loadClass("com.android.id.impl.IdProviderImpl")
        val guid = provider.getMethod("getOpenid", Context::class.java, String::class.java)
            .invoke(provider.getDeclaredConstructor().newInstance(), context, "GUID") as? String
        if (guid.isNullOrEmpty()) throw SoftwareUpdateAuthenticationUnavailable()
        val id = MessageDigest.getInstance("MD5").digest(guid.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
        when (mode) {
            0 -> {
                val languageId = context.resources.getIdentifier("language_values_exam", "string", "oplus")
                val language = if (languageId != 0) context.getString(languageId) else context.resources.configuration.locales[0].toLanguageTag()
                val rom = property("ro.build.version.oplusrom.confidential").ifEmpty { "V1.0.0" }
                val suffix = when { rom.endsWith("alpha", true) -> "alpha"; rom.endsWith("beta", true) -> "beta"; else -> "" }
                val brand = when (property("ro.product.brand")) {
                    "realme" -> "realme"
                    "OnePlus" -> if (property("ro.oplus.image.my_stock.type").contains("domestic")) "oneplus" else "oneplus-exp"
                    else -> "oppo"
                }
                mapOf(
                    "language" to language, "androidVersion" to "Android ${property("ro.build.version.release")}",
                    "colorOSVersion" to "ColorOS${rom.substringAfter('V').substringBefore('_')}$suffix",
                    "otaVersion" to property("ro.build.version.ota").split('_').take(4).joinToString("_"),
                    "model" to property("ro.product.name"), "mode" to if (property("sys.ota.checkmode") == "1") "1" else "0",
                    "nvCarrier" to property("ro.build.oplus_nv_id"), "brand" to brand,
                    "osType" to property("ro.oplus.image.my_stock.type"),
                    "operator" to property("persist.sys.channel.info").ifEmpty { property("ro.oplus.pipeline.carrier").ifEmpty { "default" } },
                    "prjNum" to property("ro.separate.soft"), "id" to id, "ts" to timestamp(),
                ).forEach { (key, value) -> connection.setRequestProperty(key, value) }
            }
            1, 2 -> {
                val method = attestation ?: throw SoftwareUpdateAuthenticationUnavailable()
                // Reuse an already authenticated client session; never enroll or replace device keys here.
                val owner = method.declaringClass
                val cache = owner.declaredFields.singleOrNull { Modifier.isStatic(it.modifiers) && it.type == owner }
                    ?.apply { isAccessible = true }?.get(null) ?: throw SoftwareUpdateAuthenticationUnavailable()
                val certificate = runCatching {
                    cache.javaClass.getDeclaredField(if (mode == 1) "a" else "b").apply { isAccessible = true }.get(cache) as? String
                }.getOrNull() ?: throw SoftwareUpdateAuthenticationUnavailable()
                val valid = runCatching {
                    val certificates = CertificateFactory.getInstance("X.509").generateCertificates(Base64.getDecoder().decode(certificate).inputStream())
                    check(certificates.isNotEmpty())
                    certificates.forEach { (it as X509Certificate).checkValidity() }
                }.isSuccess
                if (!valid) throw SoftwareUpdateAuthenticationUnavailable()
                val ts = timestamp()
                val uri = URI(url)
                val canonical = (uri.path + (uri.query?.let { "?$it" } ?: "")).replaceFirst("/", "").replace("=", ":")
                val data = JSONObject().put("id", id).put("ts", ts).put("uri", canonical).toString()
                val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                val key = keys.getKey(if (mode == 1) "intelligent-brain" else "ota_pki_attest", null) as? PrivateKey
                    ?: throw SoftwareUpdateAuthenticationUnavailable()
                val signature = Signature.getInstance("SHA256withECDSA").apply {
                    initSign(key)
                    update(data.toByteArray())
                }.sign()
                mapOf("id" to id, "ts" to ts, "ac" to certificate, "as" to Base64.getEncoder().encodeToString(signature))
                    .forEach { (name, value) -> connection.setRequestProperty(name, value) }
            }
            else -> throw SoftwareUpdateAuthenticationUnavailable()
        }
    }
}
