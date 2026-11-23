package com.mi.onextbox.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.mi.onextbox.BuildConfig
import com.mi.onextbox.ui.common.AppLogStore
import com.mi.onextbox.ui.common.ShellLogger
import com.mi.onextbox.ui.onboarding.ActivationGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.zip.ZipInputStream

object AppUpdater {
    const val INSTALL_PERMISSION_REQUIRED = "Install permission required"

    private const val USER_AGENT = "ONextBox/${BuildConfig.VERSION_NAME}"
    private const val RUNTIME_PREFS = "onextbox_updater_runtime"
    private const val KEY_PENDING_APK_PATH = "pending_apk_path"
    private const val KEY_PENDING_VERSION = "pending_version"
    private const val KEY_DOWNLOADED_APK_PATH = "downloaded_apk_path"
    private const val KEY_DOWNLOADED_VERSION = "downloaded_version"
    private val downloadMutex = Mutex()

    suspend fun checkAndInstall(
        context: Context,
        channel: UpdateChannel = AppUpdatePreferences.getUpdateChannel(context),
        installMode: UpdateInstallMode = UpdateInstallMode.Interactive,
    ): UpdateResult {
        val update = runCatching { checkForUpdate(channel) }
            .getOrElse { error ->
                if (error is CancellationException) throw error
                return UpdateResult.Failed(error.message ?: error::class.java.simpleName)
            }
            ?: return UpdateResult.NoUpdate
        if (update.channel != AppUpdatePreferences.getUpdateChannel(context)) return UpdateResult.NoUpdate
        return downloadAndInstall(
            context = context,
            update = update,
            installMode = installMode,
        )
    }

    suspend fun checkForUpdate(
        channel: UpdateChannel,
    ): AvailableUpdate? =
        withContext(Dispatchers.IO) {
            val buildType = AppUpdatePreferences.currentBuildType
            when (channel) {
                UpdateChannel.Release -> findLatestStableRelease(buildType)
                UpdateChannel.Ci -> findLatestCiRelease(buildType)?.takeIf(::isNewerUpdate)
            }
        }

    internal suspend fun findReleaseNotes(versionName: String): String? =
        withContext(Dispatchers.IO) {
            ReleaseNotesLookup.find(versionName,
                byTag = { repository, tag ->
                    try {
                        releaseNotesEntry(JSONObject(getText("https://api.github.com/repos/$repository/releases/tags/$tag")))
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (error: GitHubRequestException) {
                        if (error.status != 404) throw error
                        null
                    }
                },
                page = { repository, page ->
                    val releases = JSONArray(getText("https://api.github.com/repos/$repository/releases?per_page=100&page=$page"))
                    (0 until releases.length()).map { releaseNotesEntry(releases.getJSONObject(it)) }
                },
            )?.body
        }

    private fun releaseNotesEntry(json: JSONObject): ReleaseNotesEntry {
        val assets = json.optJSONArray("assets") ?: JSONArray()
        return ReleaseNotesEntry(
            tag = json.optString("tag_name"),
            name = json.optString("name"),
            assetNames = (0 until assets.length()).map { assets.optJSONObject(it)?.optString("name").orEmpty() },
            body = normalizeReleaseNotes(json.optString("body")),
            draft = json.optBoolean("draft"),
        )
    }

    suspend fun downloadAndInstall(
        context: Context,
        update: AvailableUpdate,
        installMode: UpdateInstallMode = UpdateInstallMode.Interactive,
        onProgress: suspend (DownloadProgress) -> Unit = {},
    ): UpdateResult {
        val apkFile = try {
            downloadUpdate(context, update, onProgress)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            return UpdateResult.Failed(error.message ?: error::class.java.simpleName)
        }
        return installDownloadedUpdate(context, update, apkFile, installMode)
    }

    suspend fun downloadUpdate(
        context: Context,
        update: AvailableUpdate,
        onProgress: suspend (DownloadProgress) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        downloadMutex.withLock {
            val appContext = context.applicationContext
            val updateDir = File(appContext.cacheDir, "updates")
            val expectedApk = downloadedApkFile(updateDir, update)
            pruneUpdateDirectory(updateDir, setOf(expectedApk))
            clearPendingInstallation(appContext)
            clearDownloadedUpdate(appContext)
            val apkFile = if (update.isArtifactZip) {
                downloadArtifactApk(updateDir, update, onProgress)
            } else {
                downloadApk(updateDir, update, onProgress)
            }
            rememberDownloadedUpdate(appContext, update, apkFile)
            pruneUpdateDirectory(updateDir, setOf(apkFile))
            apkFile
        }
    }

    suspend fun installDownloadedUpdate(
        context: Context,
        update: AvailableUpdate,
        apkFile: File,
        installMode: UpdateInstallMode = UpdateInstallMode.Interactive,
    ): UpdateResult {
        if (update.channel != AppUpdatePreferences.getUpdateChannel(context)) return UpdateResult.NoUpdate
        markPendingInstallation(context, update, apkFile)
        return runCatching {
            when (installMode) {
                UpdateInstallMode.Interactive -> {
                    withContext(Dispatchers.Main) {
                        installApk(context, apkFile)
                    }
                    UpdateResult.InstallStarted(update.versionName)
                }
                UpdateInstallMode.Silent -> withContext(Dispatchers.IO) {
                    if (silentInstallApk(apkFile)) {
                        deleteDownloadedUpdate(context, apkFile)
                        clearDownloadedUpdate(context)
                        clearPendingInstallation(context)
                        UpdateNotificationScheduler.cancelAvailableUpdateNotification(context)
                        UpdateResult.InstallFinished(update.versionName)
                    } else {
                        clearPendingInstallation(context)
                        UpdateResult.Failed("Silent install failed")
                    }
                }
            }
        }.getOrElse { error ->
            clearPendingInstallation(context)
            if (error is CancellationException) throw error
            UpdateResult.Failed(error.message ?: error::class.java.simpleName)
        }
    }

    fun cleanupInstalledUpdate(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE)
        val updateDir = File(appContext.cacheDir, "updates")
        var retainedApk: File? = null

        val pendingVersion = prefs.getString(KEY_PENDING_VERSION, null)
        val pendingApkPath = prefs.getString(KEY_PENDING_APK_PATH, null)
        if (
            pendingVersion != null &&
            isNewerVersion(pendingVersion, BuildConfig.VERSION_NAME) &&
            pendingApkPath?.let(::File)?.takeIf { it.isManagedUpdateFile(updateDir) && it.isFile } != null
        ) {
            retainedApk = File(pendingApkPath)
        } else if (pendingVersion != null || pendingApkPath != null) {
            pendingApkPath?.let { deleteDownloadedUpdate(appContext, File(it)) }
            clearPendingInstallation(appContext)
            UpdateNotificationScheduler.cancelAvailableUpdateNotification(appContext)
            AppLogStore.i("Updater", "Installed update package cleaned: ${pendingVersion.orEmpty()}")
        }

        val downloadedVersion = prefs.getString(KEY_DOWNLOADED_VERSION, null)
        val downloadedApkPath = prefs.getString(KEY_DOWNLOADED_APK_PATH, null)
        val downloadedApk = downloadedApkPath?.let(::File)
        if (
            downloadedVersion != null &&
            isNewerVersion(downloadedVersion, BuildConfig.VERSION_NAME) &&
            downloadedApk?.takeIf { it.isManagedUpdateFile(updateDir) && it.isFile } != null
        ) {
            retainedApk = downloadedApk
        } else {
            downloadedApk?.let { deleteDownloadedUpdate(appContext, it) }
            if (downloadedVersion != null || downloadedApkPath != null) {
                clearDownloadedUpdate(appContext)
            }
        }

        pruneUpdateDirectory(updateDir, setOfNotNull(retainedApk))
    }

    fun findDownloadedUpdate(context: Context, update: AvailableUpdate): File? {
        val output = downloadedApkFile(File(context.cacheDir, "updates"), update)
        val expectedSize = update.fileSizeBytes.takeUnless { update.isArtifactZip }
        return output.takeIf { it.isCompleteDownload(expectedSize) }
    }

    suspend fun runAutomaticSilentUpdate(context: Context): UpdateResult? {
        val appContext = context.applicationContext
        if (!ActivationGate.mayRunBackgroundNetwork(appContext)) return null
        if (!AppUpdatePreferences.getAutomaticSilentUpdate(appContext)) return null
        if (!AppUpdatePreferences.shouldRunAutomaticSilentUpdate(appContext)) return null
        AppUpdatePreferences.markAutomaticSilentUpdateChecked(appContext)
        AppLogStore.i("Updater", "Automatic silent update check started")
        return checkAndInstall(
            context = appContext,
            installMode = UpdateInstallMode.Silent,
        ).also { result ->
            AppLogStore.i("Updater", "Automatic silent update result: $result")
        }
    }

    private val compareByUpdateVersion = Comparator<AvailableUpdate> { first, second ->
        AppUpdateRules.compareVersions(first.versionName, second.versionName)
    }

    private fun findLatestStableRelease(buildType: UpdateBuildType): AvailableUpdate? {
        val apiBase = "https://api.github.com/repos/${AppUpdateRules.MAIN_REPOSITORY}"
        val candidates = mutableListOf<AvailableUpdate>()
        for (page in 1..3) {
            val releases = JSONArray(getText("$apiBase/releases?per_page=100&page=$page"))
            for (index in 0 until releases.length()) {
                val release = releases.optJSONObject(index) ?: continue
                val update = releaseUpdate(release, buildType, UpdateChannel.Release) ?: continue
                if (isNewerUpdate(update)) candidates.add(update)
            }
            if (releases.length() < 100) break
        }
        return candidates.maxWithOrNull(compareByUpdateVersion)
    }

    private fun findLatestCiRelease(buildType: UpdateBuildType): AvailableUpdate? {
        val url = "https://api.github.com/repos/${AppUpdateRules.MAIN_REPOSITORY}/releases/tags/${AppUpdateRules.CI_RELEASE_TAG}"
        val release = try {
            JSONObject(getText(url))
        } catch (error: GitHubRequestException) {
            if (error.status == 404) return null
            throw error
        }
        return releaseUpdate(release, buildType, UpdateChannel.Ci)
    }

    private fun releaseUpdate(
        json: JSONObject,
        buildType: UpdateBuildType,
        channel: UpdateChannel,
    ): AvailableUpdate? {
        if (json.optBoolean("draft")) return null
        val assets = json.optJSONArray("assets") ?: JSONArray()
        val tag = json.optString("tag_name")
        val title = json.optString("name")
        val body = json.optString("body")
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val assetName = asset.optString("name")
            if (!AppUpdateRules.matchesApk(assetName, buildType)) continue
            if (!AppUpdateRules.acceptsRelease(
                    tag = tag,
                    draft = false,
                    prerelease = json.optBoolean("prerelease"),
                    channel = channel,
                    releaseName = title,
                    assetName = assetName,
                )
            ) continue
            val versionName = AppUpdateRules.releaseVersionName(tag, title, assetName) ?: continue
            return AvailableUpdate(
                versionName = versionName,
                versionCode = AppUpdateRules.releaseVersionCode(body),
                downloadUrl = asset.getString("browser_download_url"),
                fileName = assetName,
                fileSizeBytes = asset.optLong("size").takeIf { it > 0L },
                releaseNotes = normalizeReleaseNotes(body),
                channel = channel,
            )
        }
        return null
    }

    private suspend fun downloadApk(
        updateDir: File,
        update: AvailableUpdate,
        onProgress: suspend (DownloadProgress) -> Unit,
    ): File {
        val output = downloadedApkFile(updateDir, update)
        if (output.isCompleteDownload(update.fileSizeBytes)) {
            onProgress(
                DownloadProgress(
                    bytesDownloaded = output.length(),
                    totalBytes = output.length(),
                ),
            )
            return output
        }
        output.delete()
        downloadToFile(update.downloadUrl, output, onProgress)
        return output
    }

    private suspend fun downloadArtifactApk(
        updateDir: File,
        update: AvailableUpdate,
        onProgress: suspend (DownloadProgress) -> Unit,
    ): File {
        val output = downloadedApkFile(updateDir, update)
        if (output.isCompleteDownload(expectedSize = null)) {
            onProgress(
                DownloadProgress(
                    bytesDownloaded = output.length(),
                    totalBytes = output.length(),
                ),
            )
            return output
        }
        output.delete()
        val zipFile = updateFile(updateDir, update.fileName.ensureZipSuffix())
        return try {
            downloadToFile(update.downloadUrl, zipFile, onProgress)
            ZipInputStream(zipFile.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && entry.name.lowercase(Locale.ROOT).endsWith(".apk")) {
                        FileOutputStream(output).use { stream ->
                            zip.copyTo(stream)
                            stream.fd.sync()
                        }
                        return output
                    }
                }
            }
            error("No APK found in CI artifact")
        } finally {
            zipFile.delete()
            File(zipFile.parentFile, "${zipFile.name}.part").delete()
        }
    }

    private suspend fun downloadToFile(
        downloadUrl: String,
        output: File,
        onProgress: suspend (DownloadProgress) -> Unit,
    ) {
        output.parentFile?.mkdirs()
        val partialOutput = File(output.parentFile, "${output.name}.part")
        partialOutput.delete()
        val connection = openConnection(downloadUrl)
        try {
            val totalBytes = connection.contentLengthLong.takeIf { it > 0L }
            onProgress(DownloadProgress(bytesDownloaded = 0L, totalBytes = totalBytes))
            connection.inputStream.use { input ->
                FileOutputStream(partialOutput).use { outputStream ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var bytesDownloaded = 0L
                    var lastProgressAtNanos = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        outputStream.write(buffer, 0, count)
                        bytesDownloaded += count
                        val now = System.nanoTime()
                        if (
                            now - lastProgressAtNanos >= PROGRESS_UPDATE_INTERVAL_NANOS ||
                            totalBytes?.let { bytesDownloaded >= it } == true
                        ) {
                            lastProgressAtNanos = now
                            onProgress(
                                DownloadProgress(
                                    bytesDownloaded = bytesDownloaded,
                                    totalBytes = totalBytes,
                                ),
                            )
                        }
                    }
                    outputStream.fd.sync()
                    onProgress(
                        DownloadProgress(
                            bytesDownloaded = bytesDownloaded,
                            totalBytes = totalBytes ?: bytesDownloaded,
                        ),
                    )
                }
            }
            output.delete()
            require(partialOutput.renameTo(output)) { "Failed to finalize downloaded APK" }
        } catch (error: Throwable) {
            partialOutput.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun getText(url: String): String {
        val connection = openConnection(url)
        try {
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String): HttpURLConnection {
        var requestUrl = url
        repeat(6) {
            val connection = URL(requestUrl).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream")
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank()) error("GitHub request redirected without Location")
                requestUrl = URL(URL(requestUrl), location).toString()
                return@repeat
            }
            if (code !in 200..299) {
                val message = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                connection.disconnect()
                throw GitHubRequestException(code, "GitHub request failed: HTTP $code ${message.take(120)}")
            }
            return connection
        }
        error("GitHub request redirected too many times")
    }

    private fun installApk(context: Context, apkFile: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                "package:${context.packageName}".toUri(),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settingsIntent)
            error(INSTALL_PERMISSION_REQUIRED)
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        val installIntent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(apkUri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(installIntent)
        } catch (_: ActivityNotFoundException) {
            error("No installer available")
        }
    }

    private fun silentInstallApk(apkFile: File): Boolean {
        val result = ShellLogger.exec(
            tag = "Updater",
            "pm install -r ${apkFile.absolutePath.shellQuote()}",
        )
        return result.isSuccess
    }

    private fun updateFile(updateDir: File, fileName: String): File {
        return File(updateDir, fileName)
    }

    private fun downloadedApkFile(updateDir: File, update: AvailableUpdate): File {
        val fileName = if (update.isArtifactZip) {
            update.fileName.removeZipSuffix().ensureApkSuffix()
        } else {
            update.fileName.ensureApkSuffix()
        }
        return updateFile(updateDir, fileName)
    }

    private fun markPendingInstallation(
        context: Context,
        update: AvailableUpdate,
        apkFile: File,
    ) {
        context.applicationContext
            .getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_APK_PATH, apkFile.absolutePath)
            .putString(KEY_PENDING_VERSION, update.versionName)
            .apply()
    }

    private fun clearPendingInstallation(context: Context) {
        context.applicationContext
            .getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PENDING_APK_PATH)
            .remove(KEY_PENDING_VERSION)
            .apply()
    }

    private fun rememberDownloadedUpdate(
        context: Context,
        update: AvailableUpdate,
        apkFile: File,
    ) {
        context.applicationContext
            .getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DOWNLOADED_APK_PATH, apkFile.absolutePath)
            .putString(KEY_DOWNLOADED_VERSION, update.versionName)
            .apply()
    }

    private fun clearDownloadedUpdate(context: Context) {
        context.applicationContext
            .getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_DOWNLOADED_APK_PATH)
            .remove(KEY_DOWNLOADED_VERSION)
            .apply()
    }

    private fun deleteDownloadedUpdate(context: Context, apkFile: File) {
        runCatching {
            val updateDir = File(context.cacheDir, "updates").canonicalFile
            val resolvedApk = apkFile.canonicalFile
            if (resolvedApk.parentFile != updateDir) return@runCatching
            resolvedApk.delete()
            File(updateDir, "${resolvedApk.name}.part").delete()
            if (updateDir.listFiles()?.isEmpty() == true) {
                updateDir.delete()
            }
        }
    }

    private fun pruneUpdateDirectory(updateDir: File, keepFiles: Set<File>) {
        runCatching {
            val resolvedDir = updateDir.canonicalFile
            val retainedPaths = keepFiles.mapNotNullTo(mutableSetOf()) { file ->
                file.canonicalFile.takeIf { it.parentFile == resolvedDir }?.path
            }
            resolvedDir.listFiles()?.forEach { child ->
                if (child.canonicalPath !in retainedPaths) {
                    child.deleteRecursively()
                }
            }
            if (resolvedDir.listFiles()?.isEmpty() == true) {
                resolvedDir.delete()
            }
        }
    }

    private fun File.isManagedUpdateFile(updateDir: File): Boolean {
        return runCatching { canonicalFile.parentFile == updateDir.canonicalFile }.getOrDefault(false)
    }

    private fun File.isCompleteDownload(expectedSize: Long?): Boolean {
        return isFile && length() > 0L && (expectedSize == null || length() == expectedSize)
    }

    private fun isNewerUpdate(update: AvailableUpdate): Boolean {
        return AppUpdateRules.isNewerInstallableVersion(
            update.versionName, BuildConfig.VERSION_NAME, update.versionCode, BuildConfig.VERSION_CODE.toLong(),
        )
    }

    private fun isNewerVersion(remote: String, current: String): Boolean {
        return AppUpdateRules.isNewerVersion(remote, current)
    }

    private fun normalizeReleaseNotes(markdown: String): String? {
        return AppUpdateRules.releaseNotesText(markdown)
            .lineSequence()
            .map { line ->
                line
                    .replaceFirst(Regex("^\\s{0,3}#{1,6}\\s+"), "")
                    .replaceFirst(Regex("^\\s*[-*]\\s+"), "• ")
                    .trimEnd()
            }
            .joinToString("\n")
            .trim()
            .takeIf { it.isNotBlank() }
    }

    private fun String.ensureApkSuffix() =
        if (lowercase(Locale.ROOT).endsWith(".apk")) this else "$this.apk"

    private fun String.ensureZipSuffix() =
        if (lowercase(Locale.ROOT).endsWith(".zip")) this else "$this.zip"

    private fun String.removeZipSuffix() =
        if (lowercase(Locale.ROOT).endsWith(".zip")) dropLast(4) else this

    private fun String.shellQuote(): String {
        return "'${replace("'", "'\\''")}'"
    }
}

private class GitHubRequestException(val status: Int, message: String) : java.io.IOException(message)

enum class UpdateInstallMode {
    Interactive,
    Silent,
}

sealed interface UpdateResult {
    data object NoUpdate : UpdateResult
    data class InstallStarted(val versionName: String) : UpdateResult
    data class InstallFinished(val versionName: String) : UpdateResult
    data class Failed(val reason: String) : UpdateResult
}

data class AvailableUpdate(
    val versionName: String,
    val versionCode: Long? = null,
    val downloadUrl: String,
    val fileName: String,
    val fileSizeBytes: Long? = null,
    val releaseNotes: String? = null,
    val isArtifactZip: Boolean = false,
    val channel: UpdateChannel = UpdateChannel.Release,
)

data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long?,
) {
    val fraction: Float?
        get() = totalBytes
            ?.takeIf { it > 0L }
            ?.let { (bytesDownloaded.toDouble() / it.toDouble()).toFloat().coerceIn(0f, 1f) }
}

private const val PROGRESS_UPDATE_INTERVAL_NANOS = 100_000_000L
