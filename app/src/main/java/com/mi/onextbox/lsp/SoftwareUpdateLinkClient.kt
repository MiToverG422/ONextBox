package com.mi.onextbox.lsp

import android.content.Context
import android.os.CancellationSignal
import android.os.OperationCanceledException
import java.io.IOException
import java.lang.reflect.Method
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull

internal class SoftwareUpdateLinkClient(context: Context, private val cancellation: CancellationSignal, attestation: Method?) {
    private val authentication = SoftwareUpdateDownloadAuth(context, attestation)
    private val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25)
    private val serverTimes = hashMapOf<String, Pair<Long, Long>>()
    private var attempts = 0

    fun resolve(packages: List<SoftwareUpdatePackageInfo>): List<SoftwareUpdatePackageInfo> = packages.map { item ->
        cancellation.throwIfCanceled()
        val direct = item.links.filterNot(SoftwareUpdateDownloadLinks::isCheckUrl)
        if (direct.isNotEmpty()) return@map item.copy(downloadLinks = direct, linkFailures = emptyList())
        val failures = arrayListOf<SoftwareUpdateLinkFailure>()
        var found: String? = null
        for (link in item.links) {
            if (attempts++ >= 8) { failures += SoftwareUpdateLinkFailure(SoftwareUpdateLinkProblem.Timeout); break }
            val result = try {
                timeout()
                SoftwareUpdateDownloadLinks.resolve(link, item.bytes, ::probe)
            } catch (error: OperationCanceledException) {
                throw error
            } catch (_: SoftwareUpdateAuthenticationUnavailable) {
                SoftwareUpdateResolvedLink(failure = SoftwareUpdateLinkFailure(SoftwareUpdateLinkProblem.Authentication))
            } catch (_: SocketTimeoutException) {
                SoftwareUpdateResolvedLink(failure = SoftwareUpdateLinkFailure(SoftwareUpdateLinkProblem.Timeout))
            } catch (_: IOException) {
                cancellation.throwIfCanceled()
                SoftwareUpdateResolvedLink(failure = SoftwareUpdateLinkFailure(SoftwareUpdateLinkProblem.Network))
            } catch (_: Exception) {
                cancellation.throwIfCanceled()
                SoftwareUpdateResolvedLink(failure = SoftwareUpdateLinkFailure(SoftwareUpdateLinkProblem.Authentication))
            }
            if (result.url != null) { found = result.url; break }
            result.failure?.let(failures::add)
        }
        item.copy(downloadLinks = listOfNotNull(found), linkFailures = if (found != null) emptyList() else failures.distinct())
    }

    private fun timeout(): Int {
        cancellation.throwIfCanceled()
        val remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
        if (remaining <= 0) throw SocketTimeoutException()
        return remaining.coerceAtMost(5_000).toInt().coerceAtLeast(1)
    }

    private fun timestamp(url: String): String {
        val source = URI(url)
        val clockUrl = URI(source.scheme, null, source.host, source.port, "/ts", null, null).toString()
        serverTimes[clockUrl]?.let { return (it.first + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - it.second)).toString() }
        return connect(clockUrl, false, range = false) { connection ->
            if (connection.responseCode != 200) throw SoftwareUpdateAuthenticationUnavailable()
            val value = connection.inputStream.use { readLimited(it, 128) }.toString(Charsets.UTF_8).trim()
            if (!value.matches(Regex("[0-9]{13}"))) throw SoftwareUpdateAuthenticationUnavailable()
            serverTimes[clockUrl] = value.toLong() to System.nanoTime()
            value
        }
    }

    private fun probe(url: String, authenticate: Boolean): SoftwareUpdateLinkResponse = connect(url, authenticate) { connection ->
        val status = connection.responseCode
        val type = connection.contentType?.substringBefore(';')
        if (status in setOf(301, 302, 303, 307, 308)) {
            SoftwareUpdateLinkResponse(status, location = connection.getHeaderField("Location"))
        } else if (type?.contains("json", true) == true) {
            val body = (if (status >= 400) connection.errorStream else connection.inputStream)
                ?.use { readLimited(it, 4_096).toString(Charsets.UTF_8) }
            val code = runCatching { body?.let { Json.parseToJsonElement(it).jsonObject["responseCode"]?.jsonPrimitive?.intOrNull } }.getOrNull()
            SoftwareUpdateLinkResponse(status, mediaType = type, serverCode = code)
        } else if (status in setOf(200, 206)) {
            val range = connection.getHeaderField("Content-Range")
            val bytes = if (status == 206) range?.let { Regex("bytes 0-[0-9]+/([0-9]+)").matchEntire(it)?.groupValues?.get(1)?.toLongOrNull() }
                else connection.contentLengthLong.takeIf { it >= 0 }
            val prefix = connection.inputStream.use { input ->
                val result = java.io.ByteArrayOutputStream(4)
                repeat(4) { timeout(); val value = input.read(); if (value != -1) result.write(value) }
                result.toByteArray()
            }
            SoftwareUpdateLinkResponse(status, mediaType = type, totalBytes = bytes, prefix = prefix)
        } else SoftwareUpdateLinkResponse(status, mediaType = type)
    }

    private fun readLimited(input: java.io.InputStream, limit: Int): ByteArray {
        val result = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(256)
        while (true) {
            timeout()
            val count = input.read(buffer, 0, minOf(buffer.size, limit + 1 - result.size()))
            if (count < 0) return result.toByteArray()
            result.write(buffer, 0, count)
            if (result.size() > limit) throw IOException("Response exceeds metadata limit")
        }
    }

    private fun <T> connect(url: String, authenticate: Boolean, range: Boolean = true, read: (HttpURLConnection) -> T): T {
        require(SoftwareUpdateDownloadLinks.trustedUrl(url))
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = timeout()
            connection.readTimeout = timeout()
            connection.useCaches = false
            connection.setRequestProperty("Accept-Encoding", "identity")
            if (range) connection.setRequestProperty("Range", "bytes=0-3")
            if (authenticate) authentication.apply(connection, url) { timestamp(url) }
            cancellation.setOnCancelListener { connection.disconnect() }
            connection.connectTimeout = timeout()
            connection.readTimeout = timeout()
            return read(connection)
        } finally {
            cancellation.setOnCancelListener(null)
            connection.disconnect()
        }
    }
}
