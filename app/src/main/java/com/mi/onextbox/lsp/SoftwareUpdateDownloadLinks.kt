package com.mi.onextbox.lsp

import java.net.URI
import java.net.URLDecoder

internal enum class SoftwareUpdateLinkProblem { Authentication, Network, Timeout, Server, InvalidResponse }
internal data class SoftwareUpdateLinkFailure(val problem: SoftwareUpdateLinkProblem, val code: Int? = null)
internal data class SoftwareUpdateResolvedLink(val url: String? = null, val failure: SoftwareUpdateLinkFailure? = null)
internal data class SoftwareUpdateLinkResponse(
    val status: Int,
    val location: String? = null,
    val mediaType: String? = null,
    val totalBytes: Long? = null,
    val prefix: ByteArray = byteArrayOf(),
    val serverCode: Int? = null,
)

internal object SoftwareUpdateDownloadLinks {
    fun isCheckUrl(value: String): Boolean = runCatching {
        URI(value).path.trimEnd('/').let { path ->
            path.equals("/downloadCheck", ignoreCase = true) || path.equals("/download", ignoreCase = true)
        }
    }.getOrDefault(false)

    // Device credentials stay on official check endpoints, never on CDN hops.
    fun trustedUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        val host = uri.host?.lowercase().orEmpty()
        value.length <= 65_536 && uri.scheme.equals("https", ignoreCase = true) && uri.userInfo == null &&
            uri.fragment == null && uri.port in setOf(-1, 443) &&
            listOf("allawntech.com", "allawnfs.com", "allawnos.com").any { host == it || host.endsWith(".$it") }
    }.getOrDefault(false)

    fun trustedCheckUrl(value: String): Boolean = trustedUrl(value) && isCheckUrl(value) &&
        URI(value).host.lowercase().matches(Regex("component-ota(?:-[a-z0-9]+)*\\.(?:allawntech|allawnos)\\.com"))

    // A signed URL may specify a different mode from the last OTA query on this device.
    // Decode only for inspection: the original URL must remain unchanged for signing/requesting.
    fun authenticationMode(value: String, sessionMode: Int): Int? = runCatching {
        if (!trustedCheckUrl(value)) return null
        val modes = URI(value).rawQuery.orEmpty().split('&').mapNotNull { part ->
            val pair = part.split('=', limit = 2)
            if (URLDecoder.decode(pair[0], "UTF-8") == "gkaReq") {
                URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
            } else null
        }
        when {
            modes.isEmpty() -> sessionMode.takeIf { it in 0..2 }
            modes.size == 1 && modes.single() in listOf("0", "1", "2") -> modes.single().toInt()
            else -> null
        }
    }.getOrNull()

    fun packageResponse(response: SoftwareUpdateLinkResponse, expectedBytes: Long): Boolean {
        val type = response.mediaType.orEmpty().lowercase()
        val signature = response.prefix.take(4).toByteArray()
        val binary = signature.contentEquals(byteArrayOf(0x50, 0x4b, 0x03, 0x04)) ||
            signature.contentEquals("CrAU".toByteArray(Charsets.US_ASCII))
        return response.status in setOf(200, 206) && expectedBytes > 0 && response.totalBytes == expectedBytes &&
            !type.contains("json") && !type.startsWith("text/") && binary
    }

    fun resolve(value: String, expectedBytes: Long, probe: (String, Boolean) -> SoftwareUpdateLinkResponse): SoftwareUpdateResolvedLink {
        fun failed(problem: SoftwareUpdateLinkProblem, code: Int? = null) =
            SoftwareUpdateResolvedLink(failure = SoftwareUpdateLinkFailure(problem, code))
        if (!trustedCheckUrl(value)) return failed(SoftwareUpdateLinkProblem.InvalidResponse)
        var current = value
        val visited = hashSetOf<String>()
        repeat(6) {
            if (!trustedUrl(current) || !visited.add(current)) return failed(SoftwareUpdateLinkProblem.InvalidResponse)
            val authenticate = trustedCheckUrl(current)
            if (isCheckUrl(current) && !authenticate) return failed(SoftwareUpdateLinkProblem.InvalidResponse)
            val response = probe(current, authenticate)
            if (response.status in setOf(301, 302, 303, 307, 308)) {
                val location = response.location ?: return failed(SoftwareUpdateLinkProblem.InvalidResponse)
                current = runCatching { URI(current).resolve(location).toString() }.getOrNull()
                    ?: return failed(SoftwareUpdateLinkProblem.InvalidResponse)
            } else if (packageResponse(response, expectedBytes)) {
                // A URL requiring request headers cannot be exported as a browser download link.
                if (authenticate && !packageResponse(probe(current, false), expectedBytes)) {
                    return failed(SoftwareUpdateLinkProblem.Authentication)
                }
                return SoftwareUpdateResolvedLink(url = current)
            } else {
                return failed(if (response.serverCode != null || response.status !in setOf(200, 206)) {
                    SoftwareUpdateLinkProblem.Server
                } else SoftwareUpdateLinkProblem.InvalidResponse, response.serverCode ?: response.status.takeIf { it !in setOf(200, 206) })
            }
        }
        return failed(SoftwareUpdateLinkProblem.InvalidResponse)
    }
}
