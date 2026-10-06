package com.mi.onextbox.touch

import android.content.Context
import android.util.Base64
import com.mi.onextbox.ui.common.AppLogStore
import com.mi.onextbox.ui.common.ShellLogger
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TouchRatePreset(
    val index: Int,
    val hz: Int,
    val chipValue: Int,
    val isIstMode: Boolean,
)

data class TouchSamplingState(
    val available: Boolean = false,
    val canWrite: Boolean = false,
    val presets: List<TouchRatePreset> = emptyList(),
    val defaultChipValue: Int? = null,
    val currentIndex: Int? = null,
    val currentChipValue: Int? = null,
    val overrideEnabled: Boolean = false,
    val error: String? = null,
    val diagnostic: String? = null,
)

data class TouchRateApplyResult(
    val accepted: Boolean,
    val selected: TouchRatePreset?,
    val state: TouchSamplingState,
)

data class TouchRateToggleResult(
    val accepted: Boolean,
    val needsPreset: Boolean,
    val state: TouchSamplingState,
)

/** OPlus report_rate is an indexed HAL mode. It does not accept an arbitrary Hz value. */
object TouchSamplingController {
    private data class ParsedConfig(
        val presets: List<TouchRatePreset> = emptyList(),
        val defaultChipValue: Int? = null,
    )

    private const val SERVICE = "vendor.oplus.hardware.touch.IOplusTouch/default"
    private const val REPORT_RATE_NODE = 182
    private const val CONFIG = "/data/vendor/touchconfig/vnd_custom_config_main.xml"
    private const val OVERRIDE_DIR = "/data/adb/onextbox"
    private const val OVERRIDE_TARGET = "$OVERRIDE_DIR/touch_rate_target"
    private const val OVERRIDE_EXPECTED = "$OVERRIDE_DIR/touch_rate_expected"
    private const val OVERRIDE_SCRIPT = "/data/adb/service.d/onextbox_touch_rate.sh"
    private val rateModule = Regex(
        "<modules\\b[^>]*\\bid=\"report_rate\"[^>]*>(.*?)</modules>",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val supportsParam = Regex(
        "<param\\b[^>]*\\bname=\"supports\"[^>]*>(.*?)</param>",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val rateComment = Regex("rate\\s*:\\s*([^\\r\\n<]+)", RegexOption.IGNORE_CASE)
    private val valueTag = Regex("<value>\\s*([0-9,\\s]+)\\s*</value>")

    suspend fun read(): TouchSamplingState = withContext(Dispatchers.IO) { readNow() }

    /** Called only on the BootSync worker after credential storage is available. */
    fun applyOnBootIfEnabled(context: Context): Boolean {
        if (!TouchSamplingPreferences.readAutoStartEnabled(context)) return true
        val saved = TouchSamplingPreferences.readSelectedPreset(context) ?: run {
            AppLogStore.w("TouchSampling", "Auto apply skipped: no saved preset")
            return false
        }
        return runCatching {
            val before = readNow()
            // Recheck the complete OEM preset after an OTA or a configuration import.
            if (!before.canWrite || saved !in before.presets ||
                saved.chipValue == before.defaultChipValue
            ) {
                AppLogStore.w("TouchSampling", "Auto apply skipped: saved preset no longer available")
                return@runCatching false
            }
            val accepted = applyIndex(saved.index, saved, before).accepted
            if (accepted) {
                AppLogStore.i("TouchSampling", "Auto applied index=${saved.index}, chip=${saved.chipValue}")
            } else {
                AppLogStore.w("TouchSampling", "Auto apply rejected for index=${saved.index}")
            }
            accepted
        }.onFailure {
            AppLogStore.w("TouchSampling", "Auto apply failed: ${it.javaClass.simpleName}")
        }.getOrDefault(false)
    }

    suspend fun applyPreset(index: Int): TouchRateApplyResult = withContext(Dispatchers.IO) {
        val before = readNow()
        val preset = before.presets.firstOrNull { it.index == index }
        if (!before.canWrite || preset == null) {
            return@withContext TouchRateApplyResult(false, null, before)
        }
        applyIndex(index, preset, before)
    }

    suspend fun restoreDefault(): TouchRateApplyResult = withContext(Dispatchers.IO) {
        val before = readNow()
        if (!before.canWrite) return@withContext TouchRateApplyResult(false, null, before)
        if (before.overrideEnabled && !disableOverride()) {
            return@withContext TouchRateApplyResult(false, null, readNow())
        }
        applyIndex(0, null, before.copy(overrideEnabled = false))
    }

    suspend fun setOverrideEnabled(context: Context, enabled: Boolean): TouchRateToggleResult =
        withContext(Dispatchers.IO) {
            val before = readNow()
            if (!enabled) {
                val success = disableOverride()
                val after = readNow()
                return@withContext TouchRateToggleResult(success && !after.overrideEnabled, false, after)
            }
            if (!before.canWrite) return@withContext TouchRateToggleResult(false, false, before)
            val index = before.currentIndex
            if (index == null || before.presets.none { it.index == index }) {
                return@withContext TouchRateToggleResult(false, true, before)
            }
            val script = context.assets.open("touch_rate_override.sh").use { it.readBytes() }
            val encoded = Base64.encodeToString(script, Base64.NO_WRAP)
            val install = ShellLogger.exec(
                "TouchSampling",
                "mkdir -p $OVERRIDE_DIR /data/adb/service.d && " +
                    "printf '%s' '$encoded' | base64 -d > $OVERRIDE_SCRIPT && " +
                    "chmod 700 $OVERRIDE_SCRIPT && " +
                    "printf '%s\\n' '$index' > $OVERRIDE_TARGET && " +
                    "service call $SERVICE 3 i32 0 i32 $REPORT_RATE_NODE > $OVERRIDE_EXPECTED && " +
                    "sh $OVERRIDE_SCRIPT",
            )
            val after = readNow()
            val accepted = install.isSuccess && after.overrideEnabled
            if (!accepted) disableOverride()
            TouchRateToggleResult(accepted, false, if (accepted) after else readNow())
        }

    private fun applyIndex(
        index: Int,
        preset: TouchRatePreset?,
        before: TouchSamplingState,
    ): TouchRateApplyResult {
        val previousTarget = if (before.overrideEnabled) {
            ShellLogger.exec("TouchSampling", "cat $OVERRIDE_TARGET")
                .out.firstOrNull()?.trim()?.toIntOrNull()
        } else {
            null
        }
        if (before.overrideEnabled) {
            if (previousTarget == null || !ShellLogger.exec(
                    "TouchSampling",
                    "printf '%s\\n' '$index' > $OVERRIDE_TARGET",
                ).isSuccess
            ) {
                return TouchRateApplyResult(false, preset, readNow())
            }
        }
        val reply = ShellLogger.exec(
            "TouchSampling",
            "service call $SERVICE 4 i32 0 i32 $REPORT_RATE_NODE s16 $index",
        )
        if (!reply.isSuccess) {
            if (previousTarget != null) restoreOverrideTarget(previousTarget)
            return TouchRateApplyResult(false, preset, readNow())
        }
        // Some OEM modes return 2 even though the request was applied. Verify the
        // HAL's reported index and chip value instead of treating 1 as the only success code.
        Thread.sleep(150)
        val after = readNow()
        val accepted = after.currentIndex == index &&
            (preset == null || after.currentChipValue == preset.chipValue)
        if (!accepted && before.currentIndex != null && before.currentIndex != index) {
            // A rejected or remapped write should leave the previous mode in place.
            ShellLogger.exec(
                "TouchSampling",
                "service call $SERVICE 4 i32 0 i32 $REPORT_RATE_NODE s16 ${before.currentIndex}",
            )
        }
        if (!accepted && previousTarget != null) restoreOverrideTarget(previousTarget)
        if (accepted && before.overrideEnabled) {
            val synced = ShellLogger.exec(
                "TouchSampling",
                "service call $SERVICE 3 i32 0 i32 $REPORT_RATE_NODE > $OVERRIDE_EXPECTED",
            ).isSuccess
            if (!synced) disableOverride()
            return TouchRateApplyResult(true, preset, readNow())
        }
        return TouchRateApplyResult(accepted, preset, if (accepted) after else readNow())
    }

    private fun restoreOverrideTarget(index: Int) {
        ShellLogger.exec("TouchSampling", "printf '%s\\n' '$index' > $OVERRIDE_TARGET")
    }

    private fun disableOverride(): Boolean = ShellLogger.exec(
        "TouchSampling",
        "rm -f $OVERRIDE_TARGET $OVERRIDE_EXPECTED $OVERRIDE_SCRIPT",
    ).isSuccess

    private fun readNow(): TouchSamplingState {
        // A shell cached before Root was granted otherwise keeps reporting non-root
        // until the whole app is restarted. Retry only in response to this read.
        runCatching { Shell.getCachedShell()?.takeIf { !it.isRoot }?.close() }
        val root = ShellLogger.exec("TouchSampling", "id -u")
        if (!root.isSuccess || root.out.firstOrNull()?.trim() != "0") {
            return TouchSamplingState(error = "root")
        }
        val support = ShellLogger.exec(
            "TouchSampling",
            "service call $SERVICE 2 i32 0 i32 $REPORT_RATE_NODE",
        )
        val supportValue = if (support.isSuccess) TouchSamplingProtocol.parcelInt(support.out.joinToString("\n")) else null
        // Query the read-only node even if the capability query fails. Do not treat
        // the capability response alone as proof that there is no readable data.
        val current = ShellLogger.exec(
            "TouchSampling",
            "service call $SERVICE 3 i32 0 i32 $REPORT_RATE_NODE",
        )
        val currentMode = if (current.isSuccess) TouchSamplingProtocol.currentMode(current.out.joinToString("\n")) else null
        val diagnostic = "Root: granted; node: $REPORT_RATE_NODE; support: ${supportValue ?: "invalid reply"}; readable: ${currentMode != null}"
        AppLogStore.i("TouchSampling", diagnostic)
        if (!TouchSamplingProtocol.canDisplay(supportValue, currentMode)) {
            return TouchSamplingState(
                error = if (supportValue == 0 || supportValue == -1) "hal_unsupported" else "hal",
                diagnostic = diagnostic,
            )
        }
        val configResult = ShellLogger.exec("TouchSampling", "cat $CONFIG")
        val config = if (configResult.isSuccess) {
            parseConfig(configResult.out.joinToString("\n"))
        } else {
            ParsedConfig()
        }
        return TouchSamplingState(
            available = true,
            canWrite = TouchSamplingProtocol.canWrite(supportValue, currentMode),
            presets = config.presets,
            defaultChipValue = config.defaultChipValue,
            currentIndex = currentMode?.first,
            currentChipValue = currentMode?.second,
            overrideEnabled = ShellLogger.exec(
                "TouchSampling",
                "test -f $OVERRIDE_TARGET && test -f $OVERRIDE_SCRIPT",
            ).isSuccess,
            error = when {
                config.presets.isEmpty() -> "config"
                currentMode == null -> "read"
                supportValue != 1 -> "read_only"
                else -> null
            },
            diagnostic = diagnostic,
        )
    }

    private fun parseConfig(xml: String): ParsedConfig {
        val module = rateModule.find(xml)?.groupValues?.get(1) ?: return ParsedConfig()
        val supports = supportsParam.find(module)?.groupValues?.get(1) ?: return ParsedConfig()
        val codes = valueTag.find(supports)?.groupValues?.get(1)
            ?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: return ParsedConfig()
        val names = rateComment.find(supports)?.groupValues?.get(1)
            ?.split(',')?.map { it.trim() }
            ?: return ParsedConfig(defaultChipValue = codes.firstOrNull())
        val presets = names.mapIndexedNotNull { index, name ->
            if (index == 0 || index >= codes.size) return@mapIndexedNotNull null
            val hz = Regex("\\d+").find(name)?.value?.toIntOrNull() ?: return@mapIndexedNotNull null
            if (hz !in 30..4000) return@mapIndexedNotNull null
            TouchRatePreset(index, hz, codes[index], name.contains("Ist", ignoreCase = true))
        }.distinctBy { it.index }.sortedWith(compareBy<TouchRatePreset> { it.hz }.thenBy { it.isIstMode })
        return ParsedConfig(presets, codes.firstOrNull())
    }

}
