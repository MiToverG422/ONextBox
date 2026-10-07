package com.mi.onextbox.ui.common

import android.content.Context
import com.mi.onextbox.lsp.LsposedScopeRequester
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private const val ROOT_STATUS_CACHE_PREFS = "root_status_cache"
private const val ROOT_STATUS_CACHE_STATE = "state"
private const val ROOT_STATUS_CACHE_UID = "uid"
private const val ROOT_STATUS_CACHE_MANAGER_VERSION = "manager_version"
private const val ROOT_STATUS_CACHE_DETAIL = "detail"

enum class RootAccessState {
    Checking,
    Granted,
    NotGranted,
    Error
}

data class RootAccessInfo(
    val state: RootAccessState,
    val uid: String? = null,
    val managerVersion: String? = null,
    val detail: String? = null
)

/** This process has not finished its initial Root check yet. */
internal object RootStartupCheck {
    private val pending = MutableStateFlow(true)
    val states: StateFlow<Boolean> = pending.asStateFlow()

    fun complete() {
        pending.value = false
    }
}

suspend fun queryRootAccess(context: Context? = null): RootAccessInfo = withContext(Dispatchers.IO) {
    AppLogStore.i("RootAccess", "Start checking root access")
    val info = runCatching {
        refreshCachedShellIfNeeded()
        val result = runFreshSu("id -u")
        if (!result.isSuccess) {
            closeCachedShell("root check failed")
            AppLogStore.w(
                "RootAccess",
                "Root check failed: ${result.err.firstOrNull().orEmpty().ifBlank { "unknown" }}"
            )
            RootAccessInfo(
                state = RootAccessState.NotGranted,
                detail = result.err.firstOrNull()
            )
        } else {
            val uid = result.out.firstOrNull()?.trim().orEmpty()
            if (uid == "0") {
                AppLogStore.i("RootAccess", "Root granted (uid=0)")
                RootAccessInfo(
                    state = RootAccessState.Granted,
                    uid = uid,
                    managerVersion = detectRootManagerVersion()
                )
            } else {
                closeCachedShell("root uid is not 0")
                AppLogStore.w("RootAccess", "Root denied (uid=$uid)")
                RootAccessInfo(
                    state = RootAccessState.NotGranted,
                    uid = uid
                )
            }
        }
    }.getOrElse { throwable ->
        AppLogStore.e("RootAccess", "Root check exception: ${throwable.message.orEmpty()}")
        RootAccessInfo(
            state = RootAccessState.Error,
            detail = throwable.message
        )
    }

    try {
        context?.applicationContext?.let { cacheRootAccessInfo(it, info) }
        LsposedScopeRequester.onRootAccessChanged()
        if (context != null) LsposedScopeRequester.snapshot(context)
    } finally {
        RootStartupCheck.complete()
        LsposedScopeRequester.onRootStartupCompleted()
    }
    info
}

fun readCachedRootAccessInfo(context: Context): RootAccessInfo? {
    return runCatching {
        val prefs = context
            .applicationContext
            .getSharedPreferences(ROOT_STATUS_CACHE_PREFS, Context.MODE_PRIVATE)
        val stateName = prefs.getString(ROOT_STATUS_CACHE_STATE, null) ?: return@runCatching null
        val state = RootAccessState.entries.firstOrNull { it.name == stateName } ?: return@runCatching null
        RootAccessInfo(
            state = state,
            uid = prefs.getString(ROOT_STATUS_CACHE_UID, null),
            managerVersion = prefs.getString(ROOT_STATUS_CACHE_MANAGER_VERSION, null),
            detail = prefs.getString(ROOT_STATUS_CACHE_DETAIL, null)
        )
    }.getOrNull()
}

private fun cacheRootAccessInfo(context: Context, info: RootAccessInfo) {
    runCatching {
        context
            .getSharedPreferences(ROOT_STATUS_CACHE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(ROOT_STATUS_CACHE_STATE, info.state.name)
            .putString(ROOT_STATUS_CACHE_UID, info.uid)
            .putString(ROOT_STATUS_CACHE_MANAGER_VERSION, info.managerVersion)
            .putString(ROOT_STATUS_CACHE_DETAIL, info.detail)
            .apply()
    }.onFailure { throwable ->
        AppLogStore.w("RootAccess", "Cache root status failed: ${throwable.message.orEmpty()}")
    }
}

enum class AssistantScreenOption {
    Shelf,
    Disabled,
    Default
}

data class SystemSettingsSnapshot(
    val permissionMonitorVisible: Boolean,
    val launcherLayoutUnlocked: Boolean,
    val assistantScreenOption: AssistantScreenOption
)

data class AssistantScreenApplyResult(
    val success: Boolean,
    val detail: String? = null
)

data class SecureSettingApplyResult(
    val success: Boolean,
    val detail: String? = null
)

data class ScopeRestartResult(
    val success: Boolean,
    val detail: String? = null
)

private data class FreshSuResult(
    val isSuccess: Boolean,
    val out: List<String> = emptyList(),
    val err: List<String> = emptyList()
)

private fun runFreshSu(command: String, timeoutSeconds: Long = 15): FreshSuResult {
    AppLogStore.d("Shell", "[RootAccess] su -c ${command.take(240)}")
    return runCatching {
        val process = ProcessBuilder("su", "-c", command).start()
        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            AppLogStore.w("Shell", "[RootAccess] su timed out")
            return FreshSuResult(
                isSuccess = false,
                err = listOf("su timed out")
            )
        }
        val stdout = process.inputStream.bufferedReader().readText().lines().filter { it.isNotBlank() }
        val stderr = process.errorStream.bufferedReader().readText().lines().filter { it.isNotBlank() }
        val success = process.exitValue() == 0
        AppLogStore.i(
            "Shell",
            "[RootAccess] success=$success, out=${stdout.size}, err=${stderr.size}"
        )
        stdout.take(3).forEach { line ->
            AppLogStore.d("ShellOut", "[RootAccess] ${line.take(240)}")
        }
        stderr.take(5).forEach { line ->
            AppLogStore.w("ShellErr", "[RootAccess] ${line.take(240)}")
        }
        FreshSuResult(
            isSuccess = success,
            out = stdout,
            err = stderr
        )
    }.getOrElse { throwable ->
        AppLogStore.e("Shell", "[RootAccess] exception: ${throwable.message.orEmpty()}")
        FreshSuResult(
            isSuccess = false,
            err = listOfNotNull(throwable.message)
        )
    }
}

private fun refreshCachedShellIfNeeded() {
    runCatching {
        val cachedShell = Shell.getCachedShell() ?: return
        if (!cachedShell.isRoot) {
            AppLogStore.i("RootAccess", "Close cached non-root shell before root check")
            cachedShell.close()
        }
    }.onFailure { throwable ->
        AppLogStore.w("RootAccess", "Refresh cached shell failed: ${throwable.message.orEmpty()}")
    }
}

private fun closeCachedShell(reason: String) {
    runCatching {
        val cachedShell = Shell.getCachedShell() ?: return
        AppLogStore.i("RootAccess", "Close cached shell: $reason")
        cachedShell.close()
    }.onFailure { throwable ->
        AppLogStore.w("RootAccess", "Close cached shell failed: ${throwable.message.orEmpty()}")
    }
}

private fun detectRootManagerVersion(): String? {
    fun firstLineOf(command: String): String? {
        val result = ShellLogger.exec("RootAccess", command)
        return (result.out.firstOrNull { it.isNotBlank() }
            ?: result.err.firstOrNull { it.isNotBlank() })
            ?.trim()
            ?.takeIf { result.isSuccess && it.isNotBlank() }
    }

    val magiskVersionName = firstLineOf("magisk -v")
    val magiskVersionCode = firstLineOf("magisk -V")
    if (!magiskVersionName.isNullOrBlank()) {
        return if (magiskVersionCode.isNullOrBlank()) {
            magiskVersionName
        } else {
            "$magiskVersionName ($magiskVersionCode)"
        }
    }

    val suVersionName = firstLineOf("su -v")
    val suVersionCode = firstLineOf("su -V")
    if (!suVersionName.isNullOrBlank()) {
        return if (suVersionCode.isNullOrBlank()) {
            suVersionName
        } else {
            "$suVersionName ($suVersionCode)"
        }
    }

    return null
}

suspend fun applyAssistantScreenOption(option: AssistantScreenOption): AssistantScreenApplyResult =
    withContext(Dispatchers.IO) {
        AppLogStore.i("DesktopAssistant", "Apply option: $option")
        val (assistantType, leftEnable) = when (option) {
            AssistantScreenOption.Shelf -> 1 to 1
            AssistantScreenOption.Disabled -> 0 to 0
            AssistantScreenOption.Default -> 2 to 1
        }

        runCatching {
            val writeResult = ShellLogger.exec(
                "DesktopAssistant",
                "settings put secure assistant_screen_type $assistantType",
                "settings put secure assistant_screen_type_left_enable $leftEnable"
            )

            if (!writeResult.isSuccess) {
                val reason = writeResult.err.firstOrNull { it.isNotBlank() }
                    ?: writeResult.out.firstOrNull { it.isNotBlank() }
                    ?: "settings command failed"
                AppLogStore.e("DesktopAssistant", "Apply failed: $reason")
                return@runCatching AssistantScreenApplyResult(
                    success = false,
                    detail = reason
                )
            }

            AppLogStore.i("DesktopAssistant", "Apply succeeded")

            AssistantScreenApplyResult(success = true)
        }.getOrElse { throwable ->
            AppLogStore.e("DesktopAssistant", "Apply exception: ${throwable.message.orEmpty()}")
            AssistantScreenApplyResult(
                success = false,
                detail = throwable.message
            )
        }
    }

suspend fun restartScopePackages(
    packages: List<String>,
    rebootSystem: Boolean = false,
): ScopeRestartResult =
    withContext(Dispatchers.IO) {
        if (rebootSystem) {
            return@withContext runCatching {
                val result = ShellLogger.exec(
                    "ScopeRestart",
                    "svc power reboot || setprop sys.powerctl reboot",
                )
                if (result.isSuccess) {
                    AppLogStore.i("ScopeRestart", "System reboot requested")
                    ScopeRestartResult(success = true)
                } else {
                    val reason = result.err.firstOrNull { it.isNotBlank() }
                        ?: result.out.firstOrNull { it.isNotBlank() }
                        ?: "system reboot command failed"
                    AppLogStore.e("ScopeRestart", "System reboot failed: $reason")
                    ScopeRestartResult(success = false, detail = reason)
                }
            }.getOrElse { throwable ->
                AppLogStore.e("ScopeRestart", "System reboot exception: ${throwable.message.orEmpty()}")
                ScopeRestartResult(success = false, detail = throwable.message)
            }
        }

        val targets = packages
            .distinct()
            .filter { it.isNotBlank() && it != "android" && it != "system" }
        if (targets.isEmpty()) {
            return@withContext ScopeRestartResult(success = false, detail = "No restartable scope")
        }

        runCatching {
            val failures = targets.mapNotNull { packageName ->
                val result = when (packageName) {
                    "com.android.systemui" -> restartSystemUiScope()
                    // TeleService is a persistent system package. ActivityManager accepts
                    // force-stop for it but deliberately keeps the phone process alive, so
                    // LSPosed never gets a chance to inject the updated module state. Killing
                    // the exact persistent process lets system_server immediately recreate it.
                    "com.android.phone" -> restartPhoneScope()
                    else -> ShellLogger.exec("ScopeRestart", "am force-stop $packageName")
                }
                if (result.isSuccess) {
                    null
                } else {
                    packageName to (
                        result.err.firstOrNull { it.isNotBlank() }
                            ?: result.out.firstOrNull { it.isNotBlank() }
                            ?: "force-stop command failed"
                        )
                }
            }
            if (failures.isEmpty()) {
                AppLogStore.i("ScopeRestart", "Restart requested: ${targets.joinToString()}")
                ScopeRestartResult(success = true)
            } else {
                val reason = failures.joinToString { (packageName, reason) -> "$packageName: $reason" }
                AppLogStore.e("ScopeRestart", "Restart failed: $reason")
                ScopeRestartResult(success = false, detail = reason)
            }
        }.getOrElse { throwable ->
            AppLogStore.e("ScopeRestart", "Restart exception: ${throwable.message.orEmpty()}")
            ScopeRestartResult(success = false, detail = throwable.message)
        }
    }

private fun restartSystemUiScope(): Shell.Result {
    val killResult = ShellLogger.exec("ScopeRestart", "pkill -f com.android.systemui")
    return if (killResult.isSuccess) {
        killResult
    } else {
        ShellLogger.exec("ScopeRestart", "am force-stop com.android.systemui")
    }
}

private fun restartPhoneScope(): Shell.Result =
    ShellLogger.exec(
        "ScopeRestart",
        "phone_pid=\$(pidof com.android.phone); " +
            "[ -n \"\$phone_pid\" ] && kill -9 \$phone_pid",
    )

suspend fun queryAssistantScreenOption(): AssistantScreenOption =
    withContext(Dispatchers.IO) {
        runCatching {
            val typeResult = ShellLogger.exec("DesktopAssistant", "settings get secure assistant_screen_type")
            val leftResult = ShellLogger.exec(
                "DesktopAssistant",
                "settings get secure assistant_screen_type_left_enable"
            )

            val rawType = typeResult.out.firstOrNull()?.trim().orEmpty()
            val rawLeft = leftResult.out.firstOrNull()?.trim().orEmpty()

            decodeAssistantScreenOption(rawType, rawLeft)
        }.getOrElse {
            AssistantScreenOption.Default
        }
    }

suspend fun querySystemSettingsSnapshot(): SystemSettingsSnapshot =
    withContext(Dispatchers.IO) {
        runCatching {
            val result = ShellLogger.exec(
                "SettingsSnapshot",
                "printf 'permission_monitor='; settings get secure system_opt_enable",
                "printf 'launcher_layout='; settings get global useOldLayout",
                "printf 'assistant_type='; settings get secure assistant_screen_type",
                "printf 'assistant_left='; settings get secure assistant_screen_type_left_enable"
            )
            val values = result.out.mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                line.substring(0, separator).trim() to line.substring(separator + 1).trim()
            }.toMap()
            SystemSettingsSnapshot(
                permissionMonitorVisible = values["permission_monitor"] == "1",
                launcherLayoutUnlocked = values["launcher_layout"] == "1",
                assistantScreenOption = decodeAssistantScreenOption(
                    rawType = values["assistant_type"].orEmpty(),
                    rawLeft = values["assistant_left"].orEmpty()
                )
            )
        }.getOrElse { throwable ->
            AppLogStore.w(
                "SettingsSnapshot",
                "Batch read failed: ${throwable.message.orEmpty()}"
            )
            SystemSettingsSnapshot(
                permissionMonitorVisible = false,
                launcherLayoutUnlocked = false,
                assistantScreenOption = AssistantScreenOption.Default
            )
        }
    }

private fun decodeAssistantScreenOption(
    rawType: String,
    rawLeft: String
): AssistantScreenOption {
    val type = rawType.toIntOrNull()
    val left = rawLeft.toIntOrNull()

    // Mapping follows applyAssistantScreenOption():
    // Shelf -> (1,1), Disabled -> (0,0), Default -> (2,1)
    return when {
        left == 0 || type == 0 -> AssistantScreenOption.Disabled
        type == 1 && left == 1 -> AssistantScreenOption.Shelf
        type == 2 && left == 1 -> AssistantScreenOption.Default
        type == 1 -> AssistantScreenOption.Shelf
        else -> AssistantScreenOption.Default
    }
}

suspend fun queryPermissionMonitorVisibility(): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            val result = ShellLogger.exec("PermissionMonitor", "settings get secure system_opt_enable")
            result.out.firstOrNull()?.trim() == "1"
        }.getOrElse { throwable ->
            AppLogStore.w("PermissionMonitor", "Read failed: ${throwable.message.orEmpty()}")
            false
        }
    }

suspend fun applyPermissionMonitorVisibility(enabled: Boolean): SecureSettingApplyResult =
    withContext(Dispatchers.IO) {
        val command = if (enabled) {
            "settings put secure system_opt_enable 1"
        } else {
            "settings delete secure system_opt_enable"
        }
        AppLogStore.i("PermissionMonitor", "Apply visibility: $enabled")

        runCatching {
            val result = ShellLogger.exec("PermissionMonitor", command)
            if (result.isSuccess) {
                SecureSettingApplyResult(success = true)
            } else {
                val reason = result.err.firstOrNull { it.isNotBlank() }
                    ?: result.out.firstOrNull { it.isNotBlank() }
                    ?: "settings command failed"
                AppLogStore.e("PermissionMonitor", "Apply failed: $reason")
                SecureSettingApplyResult(success = false, detail = reason)
            }
        }.getOrElse { throwable ->
            AppLogStore.e("PermissionMonitor", "Apply exception: ${throwable.message.orEmpty()}")
            SecureSettingApplyResult(success = false, detail = throwable.message)
        }
    }

suspend fun queryLauncherLayoutUnlocked(): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            val result = ShellLogger.exec("LauncherLayout", "settings get global useOldLayout")
            result.out.firstOrNull()?.trim() == "1"
        }.getOrElse { throwable ->
            AppLogStore.w("LauncherLayout", "Read failed: ${throwable.message.orEmpty()}")
            false
        }
    }

suspend fun applyLauncherLayoutUnlocked(enabled: Boolean): SecureSettingApplyResult =
    withContext(Dispatchers.IO) {
        val command = if (enabled) {
            "settings put global useOldLayout 1"
        } else {
            "settings put global useOldLayout 3"
        }
        AppLogStore.i("LauncherLayout", "Apply unlocked layout: $enabled")

        runCatching {
            val result = ShellLogger.exec("LauncherLayout", command)
            if (result.isSuccess) {
                SecureSettingApplyResult(success = true)
            } else {
                val reason = result.err.firstOrNull { it.isNotBlank() }
                    ?: result.out.firstOrNull { it.isNotBlank() }
                    ?: "settings command failed"
                AppLogStore.e("LauncherLayout", "Apply failed: $reason")
                SecureSettingApplyResult(success = false, detail = reason)
            }
        }.getOrElse { throwable ->
            AppLogStore.e("LauncherLayout", "Apply exception: ${throwable.message.orEmpty()}")
            SecureSettingApplyResult(success = false, detail = throwable.message)
        }
    }
