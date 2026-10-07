package com.mi.onextbox.lsp

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Looper
import android.os.Process
import com.mi.onextbox.ui.common.AppLogStore
import com.mi.onextbox.ui.common.LspHomeDisplay
import com.mi.onextbox.ui.common.LspHomeDisplayCache
import com.mi.onextbox.ui.common.RootStartupCheck
import com.mi.onextbox.ui.common.ShellLogger
import com.topjohnwu.superuser.Shell
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

object LsposedScopeRequester {
    private const val CACHE_PREFS = "lsposed_status_cache"
    private const val ANDROID_UID_PER_USER_RANGE = 100_000
    private const val LSPOSED_SECRET_CODE = "5776733"
    private const val MAX_REPORTED_MANAGER_FAILURES = 6

    private val LSPOSED_CONFIG_DB_PATHS = listOf(
        "/data/adb/lspd/config/modules_config.db",
        "/data/adb/lspd/modules_config.db",
    )
    private val LSPOSED_MANAGER_PACKAGES = listOf(
        "org.lsposed.manager",
        "org.lsposed.manager.debug",
        "io.github.libxposed.manager",
    )
    private val LSPOSED_ACTION_SCRIPT_PATHS = listOf(
        "/data/adb/modules/zygisk_lsposed/action.sh",
        "/data/adb/modules/lsposed/action.sh",
        "/data/adb/modules/riru_lsposed/action.sh",
        "/data/adb/modules_update/zygisk_lsposed/action.sh",
        "/data/adb/modules_update/lsposed/action.sh",
        "/data/adb/modules_update/riru_lsposed/action.sh",
    )

    data class StatusSnapshot(
        val moduleState: LspModuleState = LspModuleState.UNKNOWN,
        val status: LspStatus = LspStatus.CHECKING,
        val serviceConnected: Boolean = false,
        val frameworkVersionText: String? = null,
        val verifiedScopes: Set<String>? = null,
        val missingScopes: Set<String> = emptySet(),
        val isRefreshing: Boolean = false,
        val source: String = "none",
        val reason: String = "checking",
    ) {
        val moduleEnabled: Boolean get() = moduleState == LspModuleState.ENABLED
        val isReady: Boolean get() = moduleEnabled && status == LspStatus.READY
        val moduleReady: Boolean get() = isReady
        val canContinue: Boolean get() = moduleEnabled && !isRefreshing &&
            status != LspStatus.CHECKING && status != LspStatus.API_UNSUPPORTED
        val hasSystemScope: Boolean get() = "system" in verifiedScopes.orEmpty()
        val hasAndroidScope: Boolean get() = "android" in verifiedScopes.orEmpty()
        val hasSystemUiScope: Boolean get() = "com.android.systemui" in verifiedScopes.orEmpty()
        val hasSettingsScope: Boolean get() = "com.android.settings" in verifiedScopes.orEmpty()
        val hasLauncherScope: Boolean get() = "com.android.launcher" in verifiedScopes.orEmpty()
        val hasAodScope: Boolean get() = "com.oplus.aod" in verifiedScopes.orEmpty()
        val hasRequiredScopes: Boolean get() = hasSystemScope && hasSystemUiScope
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
    private val initializationLock = Any()
    private val probeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableStates = MutableStateFlow(StatusSnapshot())
    val states: StateFlow<StatusSnapshot> = mutableStates.asStateFlow()
    private val displayLock = Any()
    private val mutableHomeDisplay = MutableStateFlow<LspHomeDisplay?>(null)
    internal val homeDisplayStates: StateFlow<LspHomeDisplay?> = mutableHomeDisplay.asStateFlow()
    private var lastReportedSnapshot: StatusSnapshot? = null
    private val refreshCoordinator = LspRefreshCoordinator(
        scope = probeScope,
        load = { readSnapshot() },
        onRefreshing = { mutableStates.value = mutableStates.value.copy(isRefreshing = true) },
        onResult = { result ->
            mutableStates.value = result
            publishHomeDisplay()
            if (lastReportedSnapshot != result) {
                lastReportedSnapshot = result
                AppLogStore.i(
                    "LSPosed",
                    "module=${result.moduleState} status=${result.status} " +
                        "connected=${result.serviceConnected} ready=${result.isReady} " +
                        "source=${result.source} reason=${result.reason}",
                )
            }
        },
    )

    fun initialize(context: Context? = null) {
        val application = context?.applicationContext ?: return
        val first = synchronized(initializationLock) {
            if (appContext != null) false else {
                mutableHomeDisplay.value = LspHomeDisplayCache.read(application)
                appContext = application
                true
            }
        }
        if (first) {
            probeScope.launch {
                runCatching {
                    application.createDeviceProtectedStorageContext()
                        .getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
                }
                if (states.value.status == LspStatus.CHECKING) refreshCoordinator.refresh()
            }
        }
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

/** Manager launcher with a Root secret-code fallback, dispatch success does not confirm that the UI opened. */
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



    fun hasRequiredScopes(context: Context? = null): Boolean = snapshot(context).hasRequiredScopes

    fun cachedSnapshot(context: Context? = null): StatusSnapshot {
        initialize(context)
        return states.value
    }

    fun snapshot(context: Context? = null): StatusSnapshot {
        initialize(context)
        val current = states.value
        if (current.status != LspStatus.CHECKING && !current.isRefreshing) return current
        if (Looper.myLooper() == Looper.getMainLooper()) {
            refreshCoordinator.refresh()
            return states.value
        }
        return runBlocking { refreshCoordinator.awaitSnapshot() }
    }

    fun refreshSnapshot(context: Context? = null): StatusSnapshot {
        initialize(context)
        if (Looper.myLooper() == Looper.getMainLooper()) {
            refreshCoordinator.refresh(invalidate = true)
            return states.value
        }
        return runBlocking { refreshCoordinator.awaitSnapshot(invalidate = true) }
    }

    fun onFrameworkServiceChanged() {
        refreshCoordinator.refresh(invalidate = true)
    }

    fun onRootAccessChanged() {
        refreshCoordinator.refresh(invalidate = true)
    }

    internal fun onRootStartupCompleted() {
        publishHomeDisplay()
    }

    private fun publishHomeDisplay() = synchronized(displayLock) {
        val context = appContext ?: return@synchronized
        val display = lspHomeDisplayForCache(states.value, RootStartupCheck.states.value)
            ?: return@synchronized
        if (mutableHomeDisplay.value == display) return@synchronized
        mutableHomeDisplay.value = display
        LspHomeDisplayCache.write(context, display)
    }

    private fun readSnapshot(): StatusSnapshot =
        try {
            val configured = readConfiguration()
            val framework = runCatching { LspFrameworkService.readSnapshot() }.getOrNull()
            val requiredScopes = if (configured.moduleState == LspModuleState.ENABLED) {
                readRequiredScopes()
            } else emptySet()
            val evaluated = LspDetectionPolicy.evaluate(
                configured.moduleState, framework, Process.myUid() / ANDROID_UID_PER_USER_RANGE,
                configuredScopes = configured.scopes,
                requiredScopes = requiredScopes ?: setOf("system", "com.android.systemui"),
            )
            val requirementsUnavailable = requiredScopes == null && evaluated.status !in
                setOf(LspStatus.API_UNSUPPORTED, LspStatus.MISSING_SCOPE)
            StatusSnapshot(
                moduleState = evaluated.moduleState,
                status = if (requirementsUnavailable) LspStatus.UNKNOWN else evaluated.status,
                serviceConnected = evaluated.serviceConnected,
                frameworkVersionText = evaluated.frameworkVersionText,
                verifiedScopes = configured.scopes,
                missingScopes = evaluated.missingScopes,
                source = evaluated.source,
                reason = when {
                    requirementsUnavailable -> "requirements_unavailable"
                    evaluated.reason == "config_unavailable" -> configured.unavailableReason
                    else -> evaluated.reason
                },
            )
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            AppLogStore.w("LSPosed", "Status probe failed: " + error.javaClass.simpleName)
            StatusSnapshot(status = LspStatus.UNKNOWN, reason = "probe_failed")
        }

    private fun readConfiguration(): LspDbConfiguration {
        val context = appContext ?: return LspDbConfiguration()
        val packageName = context.packageName.takeIf(String::isNotBlank) ?: return LspDbConfiguration()
        // Status checks only use an existing Root shell.
        val shell = runCatching {
            Shell.getCachedShell()?.takeIf { it.isRoot && it.isAlive }
        }.getOrNull() ?: return LspDbConfiguration(unavailableReason = "root_shell_unavailable")
        for (sourcePath in LSPOSED_CONFIG_DB_PATHS) {
            val copy = File.createTempFile("lsp_state_", ".db", context.cacheDir)
            try {
                File(copy.absolutePath + "-wal").createNewFile()
                val output = mutableListOf<String>()
                val errors = mutableListOf<String>()
                val result = shell.newJob()
                    .add(LspDbSnapshotScript.command(sourcePath, copy.absolutePath, Process.myUid()))
                    .to(output, errors).exec()
                if (!result.isSuccess) {
                    if ("missing" in output) continue
                    return LspDbConfiguration()
                }
                return readConfigurationFromCopy(copy, packageName)
            } catch (error: Exception) {
                AppLogStore.w("LSPosed", "Configuration probe failed: " + error.javaClass.simpleName)
                return LspDbConfiguration()
            } finally {
                deleteDbCopy(copy)
            }
        }
        return LspDbConfiguration()
    }

    @Suppress("DEPRECATION")
    private fun readRequiredScopes(): Set<String>? {
        val context = appContext ?: return null
        return runCatching {
            val stream = context.classLoader.getResourceAsStream("META-INF/xposed/scope.list")
                ?: error("Missing scope metadata")
            val recommended = stream.bufferedReader(Charsets.UTF_8).use { reader ->
                LspScopeRequirements.parse(reader.readText())
            }
            LspScopeRequirements.installed(recommended) { packageName ->
                try {
                    context.packageManager.getApplicationInfo(packageName, 0)
                    true
                } catch (_: PackageManager.NameNotFoundException) {
                    false
                }
            }
        }.onFailure { error ->
            AppLogStore.w("LSPosed", "Scope metadata probe failed: " + error.javaClass.simpleName)
        }.getOrNull()
    }

    private fun readConfigurationFromCopy(copy: File, packageName: String): LspDbConfiguration {
        val userId = Process.myUid() / ANDROID_UID_PER_USER_RANGE
        return SQLiteDatabase.openDatabase(copy.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val tables = mutableMapOf<String, Set<String>>()
            db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name IN ('modules_state', 'modules', 'scope')",
                emptyArray(),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val table = cursor.getString(0)
                    db.rawQuery("PRAGMA table_info(" + table + ")", emptyArray()).use { columns ->
                        val name = columns.getColumnIndexOrThrow("name")
                        tables[table] = buildSet {
                            while (columns.moveToNext()) add(columns.getString(name))
                        }
                    }
                }
            }
            val schema = LspDbRules.schema(tables, userId) ?: return@use LspDbConfiguration()
            val arguments = if (schema.perUser) arrayOf(packageName, userId.toString()) else arrayOf(packageName)
            val moduleState = db.rawQuery(LspDbRules.query(schema), arguments).use { cursor ->
                val rows = buildList<String?> {
                    while (cursor.moveToNext()) add(if (cursor.isNull(0)) null else cursor.getString(0))
                }
                LspDbRules.moduleState(rows)
            }
            val scopeQuery = LspDbRules.scopeQuery(tables)
            val scopes = if (scopeQuery == null) null else runCatching {
                db.rawQuery(scopeQuery, arrayOf(packageName, userId.toString())).use { cursor ->
                    val rows = buildList<String?> {
                        while (cursor.moveToNext()) add(if (cursor.isNull(0)) null else cursor.getString(0))
                    }
                    LspDbRules.scopes(rows)
                }
            }.onFailure { error ->
                AppLogStore.w("LSPosed", "Scope configuration probe failed: " + error.javaClass.simpleName)
            }.getOrNull()
            LspDbConfiguration(moduleState, scopes)
        }
    }

    private fun deleteDbCopy(copy: File) {
        for (suffix in listOf("", "-wal", "-shm", "-journal")) {
            val file = File(copy.absolutePath + suffix)
            if (!runCatching { !file.exists() || file.delete() }.getOrDefault(false)) {
                AppLogStore.w("LSPosed", "Could not remove private configuration snapshot")
            }
        }
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

}
