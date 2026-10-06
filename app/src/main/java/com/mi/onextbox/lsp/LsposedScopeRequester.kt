package com.mi.onextbox.lsp

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Process
import android.os.SystemClock
import com.mi.onextbox.ui.common.AppLogStore
import com.mi.onextbox.ui.common.ShellLogger
import java.io.File
import java.util.Locale

object LsposedScopeRequester {
    private const val LSPOSED_API_VERSION = 102
    private const val CACHE_PREFS = "lsposed_status_cache"
    private const val CACHE_KEY_MODULE_ENABLED = "module_enabled"
    private const val CACHE_KEY_HAS_SYSTEM = "has_system_scope"
    private const val CACHE_KEY_HAS_ANDROID = "has_android_scope"
    private const val CACHE_KEY_HAS_SYSTEMUI = "has_systemui_scope"
    private const val CACHE_KEY_HAS_SETTINGS = "has_settings_scope"
    private const val CACHE_KEY_HAS_LAUNCHER = "has_launcher_scope"
    private const val CACHE_KEY_HAS_AOD = "has_aod_scope"
    private const val CACHE_KEY_FRAMEWORK_VERSION = "framework_version"
    private const val DB_MODULE_ENABLED_CACHE_MS = 2_000L
    private const val ANDROID_UID_PER_USER_RANGE = 100_000
    private const val LSPOSED_SECRET_CODE = "5776733"
    private const val MAX_REPORTED_MANAGER_FAILURES = 6

    private val LSPOSED_CONFIG_DB_PATHS = listOf(
        "/data/adb/lspd/config/modules_config.db",
        "/data/adb/lspd/modules_config.db"
    )
    private val LSPOSED_MODULE_PROP_PATHS = listOf(
        "/data/adb/modules/zygisk_lsposed/module.prop",
        "/data/adb/modules/lsposed/module.prop",
        "/data/adb/modules/riru_lsposed/module.prop",
        "/data/adb/modules_update/zygisk_lsposed/module.prop",
        "/data/adb/modules_update/lsposed/module.prop",
        "/data/adb/modules_update/riru_lsposed/module.prop",
    )
    private val LSPOSED_MANAGER_PACKAGES = listOf(
        "org.lsposed.manager",
        "org.lsposed.manager.debug",
        "io.github.libxposed.manager"
    )
    private val LSPOSED_ACTION_SCRIPT_PATHS = listOf(
        "/data/adb/modules/zygisk_lsposed/action.sh",
        "/data/adb/modules/lsposed/action.sh",
        "/data/adb/modules/riru_lsposed/action.sh",
        "/data/adb/modules_update/zygisk_lsposed/action.sh",
        "/data/adb/modules_update/lsposed/action.sh",
        "/data/adb/modules_update/riru_lsposed/action.sh",
    )

    private val packageColumnCandidates = listOf(
        "module_pkg_name",
        "modulePackageName",
        "package_name",
        "packageName",
        "pkg_name",
        "pkg",
        "name",
        "module"
    )

    private val enabledColumnCandidates = listOf(
        "enabled",
        "enable",
        "is_enabled",
        "isEnabled"
    )

    private val userIdColumnCandidates = listOf(
        "user_id",
        "userId",
        "userid",
    )

    data class StatusSnapshot(
        val serviceConnected: Boolean,
        val moduleEnabled: Boolean,
        val hasSystemScope: Boolean,
        val hasAndroidScope: Boolean,
        val hasSystemUiScope: Boolean,
        val hasSettingsScope: Boolean,
        val hasLauncherScope: Boolean,
        val hasAodScope: Boolean,
        val frameworkVersionText: String?
    ) {
        /**
         * Compatibility projection used by existing screens. LSPosed does not expose a stable,
         * public per-scope query API here, so these booleans must not be presented as an
         * independently verified scope list. [moduleEnabled] is the reliable onboarding signal.
         */
        val hasRequiredScopes: Boolean
            get() = hasSystemScope && hasSystemUiScope
    }

    enum class ManagerOpenMethod {
        PACKAGE_LAUNCHER,
        PACKAGE_COMPONENT,
        LSPOSED_DEEP_LINK,
        ROOT_SECRET_CODE,
    }

    enum class ManagerOpenStatus {
        OPENED,
        REQUEST_DISPATCHED,
        FAILED,
    }

    data class ManagerOpenResult(
        val status: ManagerOpenStatus,
        val method: ManagerOpenMethod? = null,
        val target: String? = null,
        val failureSummary: String? = null,
    ) {
        val isSuccess: Boolean
            get() = status != ManagerOpenStatus.FAILED
    }

    @Volatile
    private var appContext: Context? = null
    private val dbReadLock = Any()
    @Volatile
    private var cachedDbModuleEnabled: CachedDbModuleEnabled? = null

    fun initialize(context: Context? = null) {
        context?.applicationContext?.let { appContext = it }
    }

    fun requestRequiredScopes(): Boolean {
        val context = appContext
        if (context == null) {
            AppLogStore.w("LSPosed", "Cannot open manager before initialization")
            return false
        }
        AppLogStore.i(
            "LSPosed",
            "Static scope mode; opening LSPosed manager instead of a dynamic scope request"
        )
        return openManager(context).isSuccess
    }

    /**
     * Opens an installed LSPosed manager when it is addressable as a normal application. Modern
     * parasitic/randomized managers are intentionally invisible to package enumeration, so a
     * rooted secret-code broadcast is used as the final fallback. A dispatched secret code means
     * the framework accepted the launch request; Android does not provide an acknowledgement that
     * the manager UI actually became visible.
     */
    fun openManager(context: Context): ManagerOpenResult {
        initialize(context)
        val applicationContext = context.applicationContext
        val failures = mutableListOf<String>()

        LSPOSED_MANAGER_PACKAGES.forEach { packageName ->
            val launchIntent = runCatching {
                applicationContext.packageManager.getLaunchIntentForPackage(packageName)
            }.onFailure { throwable ->
                recordManagerOpenFailure(failures, "launcher lookup:$packageName", throwable)
            }.getOrNull()
            if (launchIntent != null) {
                tryStartManagerActivity(
                    context = applicationContext,
                    intent = launchIntent,
                    method = ManagerOpenMethod.PACKAGE_LAUNCHER,
                    target = packageName,
                    failures = failures,
                )?.let { return it }
            }

            val launcherIntent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(packageName)
            tryStartManagerActivity(
                context = applicationContext,
                intent = launcherIntent,
                method = ManagerOpenMethod.PACKAGE_LAUNCHER,
                target = packageName,
                failures = failures,
            )?.let { return it }

            managerActivityClassCandidates(packageName).forEach { className ->
                val componentIntent = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(ComponentName(packageName, className))
                tryStartManagerActivity(
                    context = applicationContext,
                    intent = componentIntent,
                    method = ManagerOpenMethod.PACKAGE_COMPONENT,
                    target = "$packageName/$className",
                    failures = failures,
                )?.let { return it }
            }

            val moduleDeepLink = Uri.Builder()
                .scheme("lsposed")
                .authority("module")
                .appendQueryParameter("modulePackageName", applicationContext.packageName)
                .appendQueryParameter(
                    "moduleUserId",
                    (Process.myUid() / ANDROID_UID_PER_USER_RANGE).toString(),
                )
                .build()
            val deepLinkIntent = Intent(Intent.ACTION_VIEW, moduleDeepLink)
                .setPackage(packageName)
            tryStartManagerActivity(
                context = applicationContext,
                intent = deepLinkIntent,
                method = ManagerOpenMethod.LSPOSED_DEEP_LINK,
                target = packageName,
                failures = failures,
            )?.let { return it }
        }

        dispatchLsposedSecretCode(failures)?.let { return it }

        val failureSummary = failures.takeLast(MAX_REPORTED_MANAGER_FAILURES).joinToString("; ")
            .ifBlank { "No supported LSPosed manager entry point was available" }
        AppLogStore.w("LSPosed", "Unable to open manager: $failureSummary")
        return ManagerOpenResult(
            status = ManagerOpenStatus.FAILED,
            failureSummary = failureSummary,
        )
    }

    /** Bypasses the short database cache when returning from the external manager. */
    fun refreshSnapshot(context: Context? = null): StatusSnapshot {
        synchronized(dbReadLock) {
            cachedDbModuleEnabled = null
        }
        return snapshot(context)
    }

    private fun managerActivityClassCandidates(packageName: String): List<String> = buildList {
        // Official LSPosed keeps this class name even when a fork changes applicationId.
        add("org.lsposed.manager.ui.activity.MainActivity")
        add("$packageName.ui.activity.MainActivity")
        add("$packageName.MainActivity")
    }.distinct()

    private fun tryStartManagerActivity(
        context: Context,
        intent: Intent,
        method: ManagerOpenMethod,
        target: String,
        failures: MutableList<String>,
    ): ManagerOpenResult? {
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        return runCatching {
            context.startActivity(intent)
            AppLogStore.i("LSPosed", "Opened manager via $method ($target)")
            ManagerOpenResult(
                status = ManagerOpenStatus.OPENED,
                method = method,
                target = target,
            )
        }.onFailure { throwable ->
            recordManagerOpenFailure(failures, "$method:$target", throwable)
        }.getOrNull()
    }

    private fun dispatchLsposedSecretCode(
        failures: MutableList<String>,
    ): ManagerOpenResult? {
        val scriptPaths = LSPOSED_ACTION_SCRIPT_PATHS.joinToString(" ") { shellQuote(it) }
        val command = buildString {
            append("for script in ")
            append(scriptPaths)
            append("; do if [ -f \"\$script\" ]; then sh \"\$script\"; exit \$?; fi; done; ")
            append("sdk=\$(getprop ro.build.version.sdk); ")
            append("if [ \"\$sdk\" -ge 29 ]; then ")
            append("am broadcast -a android.telephony.action.SECRET_CODE ")
            append("-d android_secret_code://")
            append(LSPOSED_SECRET_CODE)
            append(" android; else ")
            append("am broadcast -a android.provider.Telephony.SECRET_CODE ")
            append("-d android_secret_code://")
            append(LSPOSED_SECRET_CODE)
            append(" android; fi")
        }
        val result = runCatching {
            ShellLogger.exec(
                "Open LSPosed manager",
                "su -c ${shellQuote(command)}",
            )
        }.onFailure { throwable ->
            recordManagerOpenFailure(failures, "root secret-code broadcast", throwable)
        }.getOrNull() ?: return null

        if (!result.isSuccess) {
            val reason = result.err.lastOrNull()
                ?.trim()
                ?.take(160)
                ?.takeIf { it.isNotBlank() }
                ?: "shell exit code was not successful"
            failures += "root secret-code broadcast: $reason"
            AppLogStore.w("LSPosed", "Secret-code manager request failed: $reason")
            return null
        }

        AppLogStore.i(
            "LSPosed",
            "Dispatched rooted LSPosed secret-code manager request ($LSPOSED_SECRET_CODE)"
        )
        return ManagerOpenResult(
            status = ManagerOpenStatus.REQUEST_DISPATCHED,
            method = ManagerOpenMethod.ROOT_SECRET_CODE,
            target = "android_secret_code://$LSPOSED_SECRET_CODE",
        )
    }

    private fun recordManagerOpenFailure(
        failures: MutableList<String>,
        attempt: String,
        throwable: Throwable,
    ) {
        val reason = buildString {
            append(throwable.javaClass.simpleName.ifBlank { "Error" })
            throwable.message
                ?.replace('\n', ' ')
                ?.trim()
                ?.take(160)
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    append(": ")
                    append(it)
                }
        }
        failures += "$attempt: $reason"
        AppLogStore.d("LSPosed", "Manager open attempt failed ($attempt): $reason")
    }


    fun hasRequiredScopes(context: Context? = null): Boolean {
        return snapshot(context).hasRequiredScopes
    }

    fun cachedSnapshot(context: Context? = null): StatusSnapshot {
        context?.applicationContext?.let { appContext = it }
        return readCachedStatus()?.toSnapshot(serviceConnected = false) ?: emptySnapshot()
    }

    fun snapshot(context: Context? = null): StatusSnapshot {
        initialize(context)
        val cached = readCachedStatus()
        val dbModuleEnabled = readModuleEnabledFromLsposedDb()
        val runtimeSystemScopeActive = LspRuntimeStatus.isSystemScopeActive()
        val runtimeSystemUiScopeActive = LspRuntimeStatus.isSystemUiScopeActive()

        val moduleEnabled = when (dbModuleEnabled) {
            false -> false
            true -> true
            null -> runtimeSystemScopeActive || runtimeSystemUiScopeActive || cached?.moduleEnabled == true
        }

        val frameworkVersionText = readLsposedModuleVersionText()
            ?: readLsposedManagerVersionText()
            ?: cached?.frameworkVersionText?.takeIf { it.contains(" / API ") }
            ?: "LSPosed"
        val snapshot = StatusSnapshot(
            serviceConnected = false,
            moduleEnabled = moduleEnabled,
            hasSystemScope = moduleEnabled,
            hasAndroidScope = moduleEnabled,
            hasSystemUiScope = moduleEnabled,
            hasSettingsScope = moduleEnabled,
            hasLauncherScope = moduleEnabled,
            hasAodScope = moduleEnabled,
            frameworkVersionText = frameworkVersionText
        )
        cacheStatus(snapshot)
        return snapshot
    }

    private fun emptySnapshot(): StatusSnapshot {
        return StatusSnapshot(
            serviceConnected = false,
            moduleEnabled = false,
            hasSystemScope = false,
            hasAndroidScope = false,
            hasSystemUiScope = false,
            hasSettingsScope = false,
            hasLauncherScope = false,
            hasAodScope = false,
            frameworkVersionText = null
        )
    }

    private fun readModuleEnabledFromLsposedDb(): Boolean? {
        val now = SystemClock.elapsedRealtime()
        cachedDbModuleEnabled
            ?.takeIf { now - it.timestampMs < DB_MODULE_ENABLED_CACHE_MS }
            ?.let { return it.value }

        return synchronized(dbReadLock) {
            val lockedNow = SystemClock.elapsedRealtime()
            cachedDbModuleEnabled
                ?.takeIf { lockedNow - it.timestampMs < DB_MODULE_ENABLED_CACHE_MS }
                ?.let { return@synchronized it.value }

            val value = readModuleEnabledFromLsposedDbUncached()
            cachedDbModuleEnabled = CachedDbModuleEnabled(
                timestampMs = SystemClock.elapsedRealtime(),
                value = value
            )
            value
        }
    }

    private fun readModuleEnabledFromLsposedDbUncached(): Boolean? {
        val context = appContext ?: return null
        val packageName = context.packageName.takeIf { it.isNotBlank() } ?: return null
        val dbCopy = File(context.cacheDir, "lsposed_modules_config.db")
        LSPOSED_CONFIG_DB_PATHS.forEach { sourcePath ->
            val enabled = runCatching {
                copyLsposedDb(sourcePath, dbCopy)
                readModuleEnabledFromDbCopy(dbCopy, packageName)
            }.onFailure { throwable ->
                AppLogStore.w(
                    "LSPosed",
                    "Read module enabled from $sourcePath failed: ${throwable.message.orEmpty()}"
                )
            }.getOrNull()
            deleteLsposedDbCopy(dbCopy)
            if (enabled != null) return enabled
        }
        return null
    }

    private fun readLsposedManagerVersionText(): String? {
        val context = appContext ?: return null
        return LSPOSED_MANAGER_PACKAGES.firstNotNullOfOrNull { packageName ->
            runCatching {
                val info = context.packageManager.getPackageInfo(packageName, 0)
                val versionName = info.versionName
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: return@runCatching null
                val versionCode = info.longVersionCode.takeIf { it > 0L }

                if (versionCode != null) {
                    "LSPosed $versionName ($versionCode) / API $LSPOSED_API_VERSION"
                } else {
                    "LSPosed $versionName / API $LSPOSED_API_VERSION"
                }
            }.getOrNull()
        }
    }

    /**
     * Modern LSPosed builds can use a randomized or fork-specific manager package, so the
     * manager APK is no longer a reliable source of the framework version. Magisk/KernelSU
     * modules still expose their framework version through module.prop.
     */
    private fun readLsposedModuleVersionText(): String? {
        val paths = LSPOSED_MODULE_PROP_PATHS.joinToString(" ") { shellQuote(it) }
        val command =
            "for prop in $paths; do if [ -f \"\$prop\" ]; then cat \"\$prop\"; exit 0; fi; done; exit 1"
        val directResult = ShellLogger.exec("LSPosed module.prop direct", command)
        val lines = if (directResult.isSuccess && directResult.out.isNotEmpty()) {
            directResult.out
        } else {
            val rootResult = ShellLogger.exec(
                "LSPosed module.prop su",
                "su -c ${shellQuote(command)}",
            )
            if (!rootResult.isSuccess) return null
            rootResult.out
        }
        val properties = lines.mapNotNull { line ->
            val separator = line.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            val key = line.substring(0, separator).trim()
            val value = line.substring(separator + 1).trim()
            if (key.isBlank() || value.isBlank()) null else key to value
        }.toMap()
        val version = properties["version"]?.takeIf { it.isNotBlank() } ?: return null
        val versionCode = properties["versionCode"]?.takeIf { it.isNotBlank() }
        val frameworkName = properties["name"]
            ?.let { name ->
                when {
                    name.contains("LSPosed IT", ignoreCase = true) -> "LSPosed IT"
                    name.contains("LSPosed", ignoreCase = true) -> "LSPosed"
                    else -> null
                }
            }
            ?: "LSPosed"
        return buildString {
            append(frameworkName)
            append(' ')
            append(version)
            versionCode
                ?.takeUnless { code ->
                    Regex("\\(\\s*${Regex.escape(code)}\\s*\\)").containsMatchIn(version)
                }
                ?.let {
                append(" (")
                append(it)
                append(')')
            }
            append(" / API ")
            append(LSPOSED_API_VERSION)
        }
    }

    private fun copyLsposedDb(sourcePath: String, target: File) {
        val targetPath = target.absolutePath
        val uid = Process.myUid()
        val copyCommands = mutableListOf(
            "rm -f ${shellQuote(targetPath)} ${shellQuote("$targetPath-wal")} ${shellQuote("$targetPath-shm")} ${shellQuote("$targetPath-journal")}",
            "cp -f ${shellQuote(sourcePath)} ${shellQuote(targetPath)}",
            "[ ! -f ${shellQuote("$sourcePath-wal")} ] || cp -f ${shellQuote("$sourcePath-wal")} ${shellQuote("$targetPath-wal")}",
            "[ ! -f ${shellQuote("$sourcePath-shm")} ] || cp -f ${shellQuote("$sourcePath-shm")} ${shellQuote("$targetPath-shm")}",
            "[ ! -f ${shellQuote("$sourcePath-journal")} ] || cp -f ${shellQuote("$sourcePath-journal")} ${shellQuote("$targetPath-journal")}",
            "chown $uid:$uid ${shellQuote(targetPath)}",
            "chmod 600 ${shellQuote(targetPath)}"
        )
        copyCommands += "if [ -f ${shellQuote("$targetPath-wal")} ]; then chown $uid:$uid ${shellQuote("$targetPath-wal")}; chmod 600 ${shellQuote("$targetPath-wal")}; fi"
        copyCommands += "if [ -f ${shellQuote("$targetPath-shm")} ]; then chown $uid:$uid ${shellQuote("$targetPath-shm")}; chmod 600 ${shellQuote("$targetPath-shm")}; fi"
        copyCommands += "if [ -f ${shellQuote("$targetPath-journal")} ]; then chown $uid:$uid ${shellQuote("$targetPath-journal")}; chmod 600 ${shellQuote("$targetPath-journal")}; fi"
        val command = copyCommands.joinToString("; ")

        val directResult = ShellLogger.exec("LSPosed copy db direct", command)
        if (directResult.isSuccess && target.exists()) return

        val suResult = ShellLogger.exec("LSPosed copy db su", "su -c ${shellQuote(command)}")
        if (!suResult.isSuccess || !target.exists()) {
            error("Unable to copy LSPosed config database from $sourcePath")
        }
    }

    private fun deleteLsposedDbCopy(dbCopy: File) {
        runCatching { dbCopy.delete() }
        runCatching { File("${dbCopy.absolutePath}-wal").delete() }
        runCatching { File("${dbCopy.absolutePath}-shm").delete() }
        runCatching { File("${dbCopy.absolutePath}-journal").delete() }
    }

    private fun readModuleEnabledFromDbCopy(dbFile: File, packageName: String): Boolean? {
        if (!dbFile.exists() || dbFile.length() <= 0L) return null
        return runCatching {
            SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            ).use { db ->
                readModuleEnabledFromKnownSchema(db, packageName)
                    ?: readModuleEnabledFromDiscoveredSchema(db, packageName)
            }
        }.onFailure { throwable ->
            AppLogStore.w("LSPosed", "Read LSPosed database failed: ${throwable.message.orEmpty()}")
        }.getOrNull()
    }

    private fun readModuleEnabledFromKnownSchema(db: SQLiteDatabase, packageName: String): Boolean? {
        val userId = Process.myUid() / ANDROID_UID_PER_USER_RANGE
        val modernValue = runCatching {
            db.rawQuery(
                "SELECT enabled FROM modules_state WHERE module_pkg_name = ? AND user_id = ? LIMIT 1",
                arrayOf(packageName, userId.toString())
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    parseEnabledValue(cursor.getString(0))
                } else {
                    null
                }
            }
        }.getOrNull()
        if (modernValue != null) return modernValue

        return runCatching {
            db.rawQuery(
                "SELECT enabled FROM modules WHERE module_pkg_name = ? LIMIT 1",
                arrayOf(packageName)
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    parseEnabledValue(cursor.getString(0))
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun readModuleEnabledFromDiscoveredSchema(db: SQLiteDatabase, packageName: String): Boolean? {
        val tables = readUserTables(db)
        val userId = Process.myUid() / ANDROID_UID_PER_USER_RANGE
        for (table in tables) {
            val columns = readColumns(db, table)
            val packageColumn = findCandidateColumn(columns, packageColumnCandidates) ?: continue
            val enabledColumn = findCandidateColumn(columns, enabledColumnCandidates) ?: continue
            val userIdColumn = findCandidateColumn(columns, userIdColumnCandidates)
            val enabled = runCatching {
                val whereClause = buildString {
                    append("${sqlIdent(packageColumn)} = ?")
                    if (userIdColumn != null) {
                        append(" AND ${sqlIdent(userIdColumn)} = ?")
                    }
                }
                val selectionArgs = if (userIdColumn != null) {
                    arrayOf(packageName, userId.toString())
                } else {
                    arrayOf(packageName)
                }
                db.rawQuery(
                    "SELECT ${sqlIdent(enabledColumn)} FROM ${sqlIdent(table)} WHERE $whereClause LIMIT 1",
                    selectionArgs
                ).use { cursor ->
                    if (cursor.moveToFirst()) parseEnabledValue(cursor.getString(0)) else null
                }
            }.getOrNull()
            if (enabled != null) return enabled
        }
        return null
    }

    private fun readUserTables(db: SQLiteDatabase): List<String> {
        return db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'",
            emptyArray()
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    cursor.getString(0)?.takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
    }

    private fun readColumns(db: SQLiteDatabase, table: String): List<String> {
        return db.rawQuery("PRAGMA table_info(${sqlIdent(table)})", emptyArray()).use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            buildList {
                while (cursor.moveToNext()) {
                    cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
    }

    private fun findCandidateColumn(columns: List<String>, candidates: List<String>): String? {
        val byLowerName = columns.associateBy { it.lowercase(Locale.ROOT) }
        return candidates.firstNotNullOfOrNull { candidate ->
            byLowerName[candidate.lowercase(Locale.ROOT)]
        }
    }

    private fun parseEnabledValue(raw: String?): Boolean? {
        val value = raw?.trim()?.lowercase(Locale.ROOT) ?: return null
        return when (value) {
            "1", "true", "t", "yes", "y", "on", "enabled" -> true
            "0", "false", "f", "no", "n", "off", "disabled" -> false
            else -> value.toIntOrNull()?.let { it != 0 }
        }
    }

    private fun sqlIdent(value: String): String {
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }

    private data class CachedDbModuleEnabled(
        val timestampMs: Long,
        val value: Boolean?
    )

    private data class CachedStatus(
        val moduleEnabled: Boolean,
        val hasSystemScope: Boolean,
        val hasAndroidScope: Boolean,
        val hasSystemUiScope: Boolean,
        val hasSettingsScope: Boolean,
        val hasLauncherScope: Boolean,
        val hasAodScope: Boolean,
        val frameworkVersionText: String?
    ) {
        fun toSnapshot(serviceConnected: Boolean): StatusSnapshot {
            val normalizedHasScopes = moduleEnabled
            return StatusSnapshot(
                serviceConnected = serviceConnected,
                moduleEnabled = moduleEnabled,
                hasSystemScope = normalizedHasScopes,
                hasAndroidScope = normalizedHasScopes,
                hasSystemUiScope = normalizedHasScopes,
                hasSettingsScope = normalizedHasScopes,
                hasLauncherScope = normalizedHasScopes,
                hasAodScope = normalizedHasScopes,
                frameworkVersionText = frameworkVersionText?.takeIf { it.contains(" / API ") }
            )
        }
    }

    private fun cacheStatus(snapshot: StatusSnapshot) {
        val context = appContext ?: return
        runCatching {
            val prefs = context
                .createDeviceProtectedStorageContext()
                .getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(CACHE_KEY_MODULE_ENABLED, snapshot.moduleEnabled)
                .putBoolean(CACHE_KEY_HAS_SYSTEM, snapshot.hasSystemScope)
                .putBoolean(CACHE_KEY_HAS_ANDROID, snapshot.hasAndroidScope)
                .putBoolean(CACHE_KEY_HAS_SYSTEMUI, snapshot.hasSystemUiScope)
                .putBoolean(CACHE_KEY_HAS_SETTINGS, snapshot.hasSettingsScope)
                .putBoolean(CACHE_KEY_HAS_LAUNCHER, snapshot.hasLauncherScope)
                .putBoolean(CACHE_KEY_HAS_AOD, snapshot.hasAodScope)
                .putString(CACHE_KEY_FRAMEWORK_VERSION, snapshot.frameworkVersionText)
                .apply()
        }
    }

    private fun readCachedStatus(): CachedStatus? {
        val context = appContext ?: return null
        return runCatching {
            val prefs = context
                .createDeviceProtectedStorageContext()
                .getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
            val hasAny = prefs.contains(CACHE_KEY_MODULE_ENABLED) ||
                prefs.contains(CACHE_KEY_HAS_SYSTEM) ||
                prefs.contains(CACHE_KEY_HAS_ANDROID) ||
                prefs.contains(CACHE_KEY_HAS_SYSTEMUI) ||
                prefs.contains(CACHE_KEY_HAS_SETTINGS) ||
                prefs.contains(CACHE_KEY_HAS_LAUNCHER) ||
                prefs.contains(CACHE_KEY_HAS_AOD) ||
                prefs.contains(CACHE_KEY_FRAMEWORK_VERSION)
            if (!hasAny) {
                null
            } else {
                CachedStatus(
                    moduleEnabled = prefs.getBoolean(CACHE_KEY_MODULE_ENABLED, false),
                    hasSystemScope = prefs.getBoolean(CACHE_KEY_HAS_SYSTEM, false),
                    hasAndroidScope = prefs.getBoolean(CACHE_KEY_HAS_ANDROID, false),
                    hasSystemUiScope = prefs.getBoolean(CACHE_KEY_HAS_SYSTEMUI, false),
                    hasSettingsScope = prefs.getBoolean(CACHE_KEY_HAS_SETTINGS, false),
                    hasLauncherScope = prefs.getBoolean(CACHE_KEY_HAS_LAUNCHER, false),
                    hasAodScope = prefs.getBoolean(CACHE_KEY_HAS_AOD, false),
                    frameworkVersionText = prefs.getString(CACHE_KEY_FRAMEWORK_VERSION, null)
                )
            }
        }.getOrNull()
    }
}
