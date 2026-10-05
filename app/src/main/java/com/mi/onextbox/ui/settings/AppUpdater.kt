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

    private const val REPO = "MiToverG422/ONextBox"
    private const val API_BASE = "https://api.github.com/repos/$REPO"
    private const val USER_AGENT = "ONextBox/${BuildConfig.VERSION_NAME}"
    private const val RUNTIME_PREFS = "onextbox_updater_runtime"
    private const val KEY_PENDING_APK_PATH = "pending_apk_path"
    private const val KEY_PENDING_VERSION = "pending_version"
    private const val KEY_DOWNLOADED_APK_PATH = "downloaded_apk_path"
    private const val KEY_DOWNLOADED_VERSION = "downloaded_version"
    private val downloadMutex = Mutex()

    suspend fun checkAndInstall(
        context: Context,
        channel: UpdateChannel,
        buildType: UpdateBuildType = UpdateBuildType.Release,
        installMode: UpdateInstallMode = UpdateInstallMode.Interactive,
    ): UpdateResult {
        val update = runCatching { checkForUpdate(channel, buildType) }
            .getOrElse { error ->
                if (error is CancellationException) throw error
                return UpdateResult.Failed(error.message ?: error::class.java.simpleName)
            }
            ?: return UpdateResult.NoUpdate
        return downloadAndInstall(
            context = context,
            update = update,
            installMode = installMode,
        )
    }

    suspend fun checkForUpdate(
        channel: UpdateChannel,
        buildType: UpdateBuildType = UpdateBuildType.Release,
    ): AvailableUpdate? =
        withContext(Dispatchers.IO) {
            val update = when (channel) {
                UpdateChannel.GitHubReleases -> findLatestRelease(buildType)
                UpdateChannel.GitHubCi -> findLatestCiArtifact(buildType)
            } ?: return@withContext null
            update.takeIf(::isNewerUpdate)
        }

    suspend fun findRelease(
        versionName: String,
        buildType: UpdateBuildType = UpdateBuildType.Release,
    ): AvailableUpdate? =
        withContext(Dispatchers.IO) {
            val normalizedVersion = versionName.removeVersionPrefix()
            sequenceOf(
                "V$normalizedVersion",
                "v$normalizedVersion",
                normalizedVersion,
            )
                .distinct()
                .firstNotNullOfOrNull { tagName ->
                    runCatching {
                        findLatestReleaseAsset("$API_BASE/releases/tags/$tagName", buildType)
                    }.getOrNull()
                }
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
        if (!UpdateChannelPreference.getAutomaticSilentUpdate(appContext)) return null
        if (!UpdateChannelPreference.shouldRunAutomaticSilentUpdate(appContext)) return null
        UpdateChannelPreference.markAutomaticSilentUpdateChecked(appContext)
        AppLogStore.i("Updater", "Automatic silent update check started")
        return checkAndInstall(
            context = appContext,
            channel = UpdateChannelPreference.get(appContext),
            buildType = UpdateChannelPreference.getBuildType(appContext),
            installMode = UpdateInstallMode.Silent,
        ).also { result ->
            AppLogStore.i("Updater", "Automatic silent update result: $result")
        }
    }

    private fun findLatestRelease(buildType: UpdateBuildType): AvailableUpdate? {
        return findLatestReleaseAsset("$API_BASE/releases/latest", buildType)
    }

    private fun findLatestCiRelease(buildType: UpdateBuildType): AvailableUpdate? {
        return runCatching {
            findLatestReleaseAsset("$API_BASE/releases/tags/ci-latest", buildType)
        }.getOrNull()
    }

    private fun findLatestReleaseAsset(
        url: String,
        buildType: UpdateBuildType,
    ): AvailableUpdate? {
        val json = JSONObject(getText(url))
        val assets = json.optJSONArray("assets") ?: JSONArray()
        val asset = firstApkAsset(assets, buildType) ?: return null
        val assetName = asset.optString("name", "")
        val versionName = extractReleaseVersionName(
            tagName = json.optString("tag_name"),
            releaseName = json.optString("name"),
            assetName = assetName,
        ) ?: return null
        return AvailableUpdate(
            versionName = versionName,
            downloadUrl = asset.getString("browser_download_url"),
            fileName = assetName.ifBlank {
                "onextbox-$versionName-${buildType.name.lowercase(Locale.ROOT)}.apk"
            },
            fileSizeBytes = asset.optLong("size").takeIf { it > 0L },
            releaseNotes = normalizeReleaseNotes(json.optString("body")),
            isArtifactZip = false,
        )
    }

    private fun findLatestCiArtifact(buildType: UpdateBuildType): AvailableUpdate? {
        findLatestCiRelease(buildType)?.let { return it }
        val runsJson = getLatestCiRunsJson()
        val runs = runsJson.optJSONArray("workflow_runs") ?: return null
        for (runIndex in 0 until runs.length()) {
            val run = runs.optJSONObject(runIndex) ?: continue
            val artifactsUrl = run.optString("artifacts_url").takeIf { it.isNotBlank() } ?: continue
            val artifactsJson = JSONObject(getText(artifactsUrl))
            val artifacts = artifactsJson.optJSONArray("artifacts") ?: continue
            val artifact = firstUsableArtifact(artifacts) ?: continue
            val artifactName = artifact.optString("name")
            val versionName = artifactName.removePrefix("onextbox-").ifBlank {
                run.optString("head_sha").takeIf { it.length >= 8 }
                    ?.let { "17.0-CI-${it.take(8)}" }
                    .orEmpty()
            }
            if (versionName.isBlank()) continue
            if (isNewerVersion(versionName, BuildConfig.VERSION_NAME)) {
                error("CI APK is not published to ci-latest release yet")
            }
            return null
        }
        return null
    }

    private fun getLatestCiRunsJson(): JSONObject {
        val requests = listOf(
            "$API_BASE/actions/runs?branch=main&status=success&per_page=10",
            "$API_BASE/actions/runs?branch=master&status=success&per_page=10",
            "$API_BASE/actions/runs?status=success&per_page=10",
        )
        var lastError: Throwable? = null
        for (request in requests) {
            val result = runCatching { JSONObject(getText(request)) }
            val json = result.getOrNull()
            if (json?.optJSONArray("workflow_runs")?.length()?.let { it > 0 } == true) {
                return json
            }
            lastError = result.exceptionOrNull()
        }
        lastError?.let { throw it }
        return JSONObject("""{"workflow_runs":[]}""")
    }

    private fun firstApkAsset(
        assets: JSONArray,
        buildType: UpdateBuildType,
    ): JSONObject? {
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name").lowercase(Locale.ROOT)
            val matchesBuildType = when (buildType) {
                UpdateBuildType.Release -> name.endsWith(".apk") && !name.contains("debug")
                UpdateBuildType.Debug -> name.endsWith(".apk") && name.contains("debug")
            }
            if (matchesBuildType) {
                return asset
            }
        }
        return null
    }

    private fun firstUsableArtifact(artifacts: JSONArray): JSONObject? {
        for (index in 0 until artifacts.length()) {
            val artifact = artifacts.optJSONObject(index) ?: continue
            val name = artifact.optString("name").lowercase(Locale.ROOT)
            if (
                name.startsWith("onextbox-") &&
                artifact.optBoolean("expired").not() &&
                artifact.optString("archive_download_url").isNotBlank()
            ) {
                return artifact
            }
        }
        return null
    }

    private fun extractReleaseVersionName(
        tagName: String,
        releaseName: String,
        assetName: String,
    ): String? {
        val candidates = listOf(
            tagName.removeVersionPrefix(),
            releaseName
                .removePrefix("ONextBox CI Latest ")
                .removePrefix("ONextBox ")
                .removeVersionPrefix(),
            assetName
                .removePrefix("onextbox-")
                .removeSuffix(".apk")
                .removeSuffix("-release")
                .removeSuffix("-debug")
                .removeVersionPrefix(),
        )
        return candidates
            .map { it.trim() }
            .firstOrNull { candidate -> candidate.isComparableVersionName() }
            ?: candidates
                .asSequence()
                .mapNotNull { candidate -> VERSION_NAME_REGEX.find(candidate)?.value }
                .firstOrNull()
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
                error("GitHub request failed: HTTP $code ${message.take(120)}")
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
        if (isNewerVersion(update.versionName, BuildConfig.VERSION_NAME)) return true
        return update.versionCode?.let { it > BuildConfig.VERSION_CODE.toLong() } == true
    }

    private fun isNewerVersion(remote: String, current: String): Boolean {
        if (remote.isBlank()) return false
        if (remote == current) return false
        val remoteVersion = ParsedVersion.parse(remote) ?: return false
        val currentVersion = ParsedVersion.parse(current) ?: return true
        val remoteParts = remoteVersion.parts
        val currentParts = currentVersion.parts
        for (index in 0 until maxOf(remoteParts.size, currentParts.size)) {
            val remotePart = remoteParts.getOrElse(index) { 0 }
            val currentPart = currentParts.getOrElse(index) { 0 }
            if (remotePart != currentPart) return remotePart > currentPart
        }
        for (index in 0 until maxOf(remoteVersion.qualifierParts.size, currentVersion.qualifierParts.size)) {
            val remotePart = remoteVersion.qualifierParts.getOrElse(index) { 0 }
            val currentPart = currentVersion.qualifierParts.getOrElse(index) { 0 }
            if (remotePart != currentPart) return remotePart > currentPart
        }
        if (remoteVersion.qualifierRank != currentVersion.qualifierRank) {
            return remoteVersion.qualifierRank > currentVersion.qualifierRank
        }
        return remoteVersion.qualifierText != currentVersion.qualifierText
    }

    private fun String.isComparableVersionName(): Boolean {
        return matches(VERSION_NAME_REGEX)
    }

    private fun String.removeVersionPrefix(): String {
        return trim().replaceFirst(Regex("^v", RegexOption.IGNORE_CASE), "")
    }

    private fun normalizeReleaseNotes(markdown: String): String? {
        return markdown
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

private val VERSION_NAME_REGEX =
    Regex("""\d+(?:\.\d+)+(?:-(?:BETA(?:\d+(?:\.\d+)*)?|CI-[A-Za-z0-9]+))?""", RegexOption.IGNORE_CASE)

private data class ParsedVersion(
    val parts: List<Int>,
    val qualifierRank: Int,
    val qualifierParts: List<Int>,
    val qualifierText: String,
) {
    companion object {
        fun parse(version: String): ParsedVersion? {
            val normalized = version.trim().replaceFirst(Regex("^v", RegexOption.IGNORE_CASE), "")
            val match = VERSION_NAME_REGEX.find(normalized) ?: return null
            val comparable = match.value
            val base = comparable.substringBefore("-")
            val qualifier = comparable.substringAfter("-", missingDelimiterValue = "")
            val parts = base.split('.').mapNotNull { it.toIntOrNull() }
            if (parts.isEmpty()) return null
            val upperQualifier = qualifier.uppercase(Locale.ROOT)
            val qualifierRank = when {
                upperQualifier.startsWith("CI-") -> 1
                upperQualifier.startsWith("BETA") -> 2
                upperQualifier.isBlank() -> 3
                else -> 0
            }
            val qualifierParts = when {
                upperQualifier.startsWith("BETA") ->
                    upperQualifier
                        .removePrefix("BETA")
                        .takeIf { it.isNotBlank() }
                        ?.split('.')
                        ?.mapNotNull { it.toIntOrNull() }
                        .orEmpty()
                else -> emptyList()
            }
            return ParsedVersion(
                parts = parts,
                qualifierRank = qualifierRank,
                qualifierParts = qualifierParts,
                qualifierText = upperQualifier,
            )
        }
    }
}

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
