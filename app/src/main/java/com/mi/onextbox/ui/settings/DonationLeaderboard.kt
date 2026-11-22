package com.mi.onextbox.ui.settings

import android.util.AtomicFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private const val DonationTableUrl = "https://raw.githubusercontent.com/MiToverG422/ONextBox/main/data/donations.json"
private const val DonationTableMaxBytes = 256 * 1024
private val donationCacheLock = Any()

internal class DonationLeaderboardState {
    var records by mutableStateOf<List<DonationRecord>?>(null)
        private set
    var refreshing by mutableStateOf(true)
        private set
    var refreshFailed by mutableStateOf(false)
        private set
    var refreshRequest by mutableIntStateOf(0)
        private set

    fun refresh() {
        if (refreshing) return
        refreshing = true
        refreshFailed = false
        refreshRequest++
    }

    suspend fun load(cache: AtomicFile) {
        if (records == null) records = readDonationCache(cache)
        val latest = downloadDonationRecords(cache)
        if (latest != null) records = latest
        refreshFailed = latest == null
        refreshing = false
    }
}

@Composable
internal fun rememberDonationLeaderboard(): DonationLeaderboardState {
    val context = LocalContext.current.applicationContext
    val cache = remember(context) { AtomicFile(context.filesDir.resolve("donation-leaderboard.json")) }
    val state = remember(cache) { DonationLeaderboardState() }
    LaunchedEffect(state, state.refreshRequest) { state.load(cache) }
    return state
}

private suspend fun readDonationCache(cache: AtomicFile): List<DonationRecord>? = withContext(Dispatchers.IO) {
    synchronized(donationCacheLock) {
        runCatching { cache.openRead().use { parseDonationTable(it.readDonationTable()) } }.getOrNull()
    }
}

private suspend fun downloadDonationRecords(cache: AtomicFile): List<DonationRecord>? = withContext(Dispatchers.IO) {
    val contents = runCatching { fetchDonationTable() }.getOrNull() ?: return@withContext null
    val records = runCatching { parseDonationTable(contents) }.getOrNull() ?: return@withContext null
    synchronized(donationCacheLock) {
        ensureActive()
        cache.writeDonationTable(contents)
    }
    records
}

private fun fetchDonationTable(): ByteArray {
    val connection = URI(DonationTableUrl).toURL().openConnection() as HttpURLConnection
    try {
        connection.instanceFollowRedirects = false
        connection.useCaches = false
        connection.connectTimeout = 8_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("Cache-Control", "no-cache")
        check(connection.responseCode == HttpURLConnection.HTTP_OK)
        return connection.inputStream.use { it.readDonationTable() }
    } finally {
        connection.disconnect()
    }
}

private fun InputStream.readDonationTable(): ByteArray = readNBytes(DonationTableMaxBytes + 1).also {
    require(it.size <= DonationTableMaxBytes)
}

private fun parseDonationTable(contents: ByteArray): List<DonationRecord> =
    parseDonationRecords(contents.decodeToString(throwOnInvalidSequence = true))

private fun AtomicFile.writeDonationTable(contents: ByteArray) {
    val output = try {
        startWrite()
    } catch (_: IOException) {
        return
    }
    try {
        output.write(contents)
        finishWrite(output)
    } catch (_: IOException) {
        failWrite(output)
    }
}
