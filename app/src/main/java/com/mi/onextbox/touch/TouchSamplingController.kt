package com.mi.onextbox.touch

import android.content.Context
import android.os.Build
import android.util.Base64
import com.mi.onextbox.ui.common.AppLogStore
import com.mi.onextbox.ui.common.ShellLogger
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Discovers touch modes, writes only the known OPlus indexed HAL contract. */
object TouchSamplingController {
    private data class Inspection(
        val state: TouchSamplingState,
        val endpoint: OplusTouchEndpoint? = null,
        val profile: TouchConfigProfile? = null,
        val sourceHashes: Map<String, String> = emptyMap(),
        val legacyOverride: Boolean = false,
    )

    private const val OVERRIDE_DIR = "/data/adb/onextbox"
    private const val OVERRIDE_TARGET = "$OVERRIDE_DIR/touch_rate_target"
    private const val OVERRIDE_ENDPOINT = "$OVERRIDE_DIR/touch_rate_endpoint"
    private const val OVERRIDE_MODES = "$OVERRIDE_DIR/touch_rate_modes"
    private const val OVERRIDE_CONFIGS = "$OVERRIDE_DIR/touch_rate_configs"
    private const val OVERRIDE_STATUS = "$OVERRIDE_DIR/touch_rate_status"
    private const val OVERRIDE_PID = "$OVERRIDE_DIR/touch_rate.pid"
    private const val OVERRIDE_SCRIPT = "/data/adb/service.d/onextbox_touch_rate.sh"
    private val lock = Any()

    suspend fun read(): TouchSamplingState = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val before = inspect()
            if (before.legacyOverride && disableOverride()) {
                val after = inspect().state
                after.copy(diagnostic = after.diagnostic + "\nLegacy override stopped, confirm the mode before enabling again")
            } else before.state
        }
    }

    /** Boot writes require a preset bound to the same endpoint and mode table. */
    fun applyOnBootIfEnabled(context: Context): Boolean = synchronized(lock) {
        if (!TouchSamplingPreferences.readAutoStartEnabled(context)) return true
        runCatching {
            val saved = TouchSamplingPreferences.readSelectedPreset(context)
            val binding = TouchSamplingPreferences.readSelectedBackendId(context)
            val before = inspect()
            if (before.legacyOverride && !disableOverride()) return@runCatching false
            if (saved == null || !writable(before, binding) || saved !in before.state.presets ||
                saved.chipValue == before.state.defaultChipValue
            ) {
                AppLogStore.w("TouchSampling", "Auto apply skipped: preset or backend requires confirmation")
                return@runCatching false
            }
            applyIndex(saved.index, saved, before, context).accepted
        }.onFailure {
            AppLogStore.w("TouchSampling", "Auto apply failed: ${it.javaClass.simpleName}")
        }.getOrDefault(false)
    }

    suspend fun applyPreset(
        index: Int,
        expectedBackendId: String? = null,
        context: Context? = null,
    ): TouchRateApplyResult = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val before = inspect()
            val preset = before.state.presets.firstOrNull { it.index == index }
            if (!writable(before, expectedBackendId) || preset == null ||
                preset.chipValue == before.state.defaultChipValue
            ) return@synchronized TouchRateApplyResult(false, null, before.state)
            applyIndex(index, preset, before, context)
        }
    }

    suspend fun restoreDefault(expectedBackendId: String? = null): TouchRateApplyResult =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                val before = inspect()
                if (!writable(before, expectedBackendId)) {
                    return@synchronized TouchRateApplyResult(false, null, before.state)
                }
                if (!disableOverride()) return@synchronized TouchRateApplyResult(false, null, inspect().state)
                applyIndex(0, null, before.copy(state = before.state.copy(overrideEnabled = false)), null)
            }
        }

    suspend fun resetConfiguration(): Boolean = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (!disableOverride()) return@synchronized false
            val before = inspect()
            if (!before.state.canWrite) return@synchronized true
            applyIndex(0, null, before, null).accepted
        }
    }

    suspend fun setOverrideEnabled(
        context: Context,
        enabled: Boolean,
        expectedBackendId: String? = null,
    ): TouchRateToggleResult = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (!enabled) {
                val success = disableOverride()
                val after = inspect().state
                return@synchronized TouchRateToggleResult(success && !after.overrideEnabled, false, after)
            }
            val before = inspect()
            if (!writable(before, expectedBackendId)) {
                return@synchronized TouchRateToggleResult(false, false, before.state)
            }
            val index = before.state.currentIndex
            val preset = before.state.presets.firstOrNull { it.index == index }
            if (preset == null || preset.chipValue == before.state.defaultChipValue) {
                return@synchronized TouchRateToggleResult(false, true, before.state)
            }
            val accepted = installOverride(context, before, preset.index)
            TouchRateToggleResult(accepted, false, inspect().state)
        }
    }

    private fun writable(inspection: Inspection, expected: String?): Boolean =
        inspection.state.canWrite && TouchSamplingPolicy.bindingMatches(inspection.state.backendId, expected)

    private fun applyIndex(
        index: Int,
        preset: TouchRatePreset?,
        before: Inspection,
        context: Context?,
    ): TouchRateApplyResult {
        val endpoint = before.endpoint ?: return TouchRateApplyResult(false, preset, before.state)
        val profile = before.profile ?: return TouchRateApplyResult(false, preset, before.state)
        val expectedChip = preset?.chipValue ?: profile.defaultChipValue
        if (!before.state.canWrite || expectedChip == null) return TouchRateApplyResult(false, preset, before.state)
        if (before.sourceHashes.any { (path, hash) -> configHash(path) != hash }) {
            return TouchRateApplyResult(false, preset, inspect().state)
        }
        val wasOverride = before.state.overrideEnabled
        if (wasOverride && !disableOverride()) return TouchRateApplyResult(false, preset, inspect().state)
        val reply = ShellLogger.exec("TouchSampling", endpoint.writeCommand(index))
        if (reply.isSuccess) Thread.sleep(150)
        val after = inspect()
        val sameBackend = TouchSamplingPolicy.bindingMatches(after.state.backendId, before.state.backendId)
        val accepted = reply.isSuccess && sameBackend && after.state.canWrite &&
            after.state.currentIndex == index && after.state.currentChipValue == expectedChip
        if (!accepted && sameBackend && after.state.canWrite) {
            before.state.currentIndex?.takeIf { it != index }?.let { previous ->
                ShellLogger.exec("TouchSampling", endpoint.writeCommand(previous))
            }
        }
        if (wasOverride && context != null) {
            val resumed = inspect()
            if (TouchSamplingPolicy.bindingMatches(resumed.state.backendId, before.state.backendId)) {
                val target = if (accepted) index else before.state.currentIndex
                if (target != null && target > 0) installOverride(context, resumed, target)
            }
        }
        val final = inspect().state
        val confirmed = accepted && final.canWrite &&
            TouchSamplingPolicy.bindingMatches(final.backendId, before.state.backendId) &&
            final.currentIndex == index && final.currentChipValue == expectedChip
        return TouchRateApplyResult(confirmed, preset, final)
    }

    private fun installOverride(context: Context, inspection: Inspection, index: Int): Boolean {
        val endpoint = inspection.endpoint ?: return false
        val profile = inspection.profile ?: return false
        val path = profile.sourceLabel ?: return false
        if (!inspection.state.canWrite || inspection.state.currentIndex != index || !disableOverride()) return false
        val hash = inspection.sourceHashes[path] ?: return false
        if (inspection.sourceHashes.any { (source, expected) -> configHash(source) != expected }) return false
        val binding = "${endpoint.service}\n${endpoint.panelIndex}\n$path\n$hash\n"
        val modes = buildString {
            append("0,").append(profile.defaultChipValue).append('\n')
            profile.presets.sortedBy { it.index }.forEach { append(it.index).append(',').append(it.chipValue).append('\n') }
        }
        val configs = inspection.sourceHashes.entries.joinToString("\n", postfix = "\n") { (source, value) -> "$value $source" }
        val script = context.assets.open("touch_rate_override.sh").use { it.readBytes() }
        val install = ShellLogger.exec(
            "TouchSampling",
            "umask 077; mkdir -p $OVERRIDE_DIR /data/adb/service.d && " +
                "chmod 700 $OVERRIDE_DIR && " +
                writeFile(OVERRIDE_SCRIPT, script) + " && chmod 700 $OVERRIDE_SCRIPT && " +
                writeFile(OVERRIDE_ENDPOINT, binding.toByteArray(Charsets.UTF_8)) + " && " +
                writeFile(OVERRIDE_MODES, modes.toByteArray(Charsets.UTF_8)) + " && " +
                writeFile(OVERRIDE_CONFIGS, configs.toByteArray(Charsets.UTF_8)) + " && " +
                "printf '%s\\n' '$index' > $OVERRIDE_TARGET && /system/bin/sh $OVERRIDE_SCRIPT",
        )
        // Wait for verified bindings, not merely a newly created PID file.
        if (install.isSuccess) repeat(30) {
            Thread.sleep(100)
            val status = output("/system/bin/toybox cat $OVERRIDE_STATUS 2>/dev/null")?.trim()
            if (status == "active" || status == "sleeping") {
                val alive = output("pid=''; test -f $OVERRIDE_PID && pid=\$(/system/bin/toybox cat $OVERRIDE_PID); " +
                    "case \"\$pid\" in ''|*[!0-9]*) exit 1;; esac; kill -0 \"\$pid\" 2>/dev/null") != null
                if (alive) return true
            }
        }
        disableOverride()
        return false
    }

    private fun writeFile(path: String, bytes: ByteArray): String =
        "printf '%s' '${Base64.encodeToString(bytes, Base64.NO_WRAP)}' | /system/bin/toybox base64 -d > $path"

    private fun disableOverride(): Boolean = ShellLogger.exec(
        "TouchSampling",
        "( rm -f $OVERRIDE_TARGET; " +
            "pid=\$(/system/bin/toybox cat $OVERRIDE_PID 2>/dev/null); " +
            "case \"\$pid\" in ''|*[!0-9]*) ;; *) " +
            "if /system/bin/toybox tr '\\000' ' ' < /proc/\$pid/cmdline 2>/dev/null | " +
            "/system/bin/toybox grep -Fq '$OVERRIDE_SCRIPT'; then " +
            "kill \"\$pid\" 2>/dev/null; attempt=0; " +
            "while kill -0 \"\$pid\" 2>/dev/null; do " +
            "[ \"\$attempt\" -lt 60 ] || exit 1; /system/bin/toybox sleep 0.1; attempt=\$((attempt + 1)); done; fi;; esac; " +
            "rm -f $OVERRIDE_ENDPOINT $OVERRIDE_MODES $OVERRIDE_CONFIGS $OVERRIDE_STATUS $OVERRIDE_PID " +
            "$OVERRIDE_DIR/touch_rate_expected $OVERRIDE_SCRIPT )",
    ).isSuccess

    private fun inspect(): Inspection {
        runCatching { Shell.getCachedShell()?.takeIf { !it.isRoot }?.close() }
        if (output("/system/bin/id -u")?.trim() != "0") {
            return Inspection(TouchSamplingState(error = "root", diagnostic = "API: ${Build.VERSION.SDK_INT}\nRoot: not granted"))
        }
        val override = output("test -f $OVERRIDE_TARGET && test -f $OVERRIDE_SCRIPT") != null
        val legacyOverride = override && output("test -f $OVERRIDE_ENDPOINT && test -f $OVERRIDE_MODES && test -f $OVERRIDE_CONFIGS") == null
        val services = TouchSamplingDiscovery.oplusServices(output("/system/bin/service list").orEmpty())
        val paths = TouchSamplingDiscovery.configPaths(output(CONFIG_LISTING).orEmpty())
        val profiles = mutableListOf<TouchConfigProfile>()
        val hashes = mutableMapOf<String, String>()
        var rejectedTables = 0
        for (path in paths) {
            val initialHash = configHash(path)
            val xml = output("/system/bin/toybox head -c 1048577 ${quote(path)} 2>/dev/null") ?: continue
            val parsed = TouchSamplingConfig.parse(xml)
            if (parsed.isEmpty() && Regex("id\\s*=\\s*[\"']report_rate[\"']").containsMatchIn(xml)) rejectedTables++
            if (parsed.isNotEmpty() && (initialHash == null || configHash(path) != initialHash)) {
                rejectedTables++
                continue
            }
            if (parsed.isNotEmpty() && initialHash != null) hashes[path] = initialHash
            profiles += parsed.map { it.copy(sourceLabel = path) }
        }
        val nodes = TouchSamplingDiscovery.kernelNodes(output(KERNEL_LISTING).orEmpty())
        val observedPanels = nodes.filter { it.endsWith("/tp_index") }
            .mapNotNull { TouchSamplingDiscovery.panelIndex(output("/system/bin/toybox head -c 64 ${quote(it)}").orEmpty()) }
        val knownProfiles = profiles.filter { it.node == 182 }
        val panel = TouchSamplingPolicy.panelIndex(observedPanels, knownProfiles, services)
        val endpoint = if (services.size == 1 && panel != null) OplusTouchEndpoint(services.single(), panel) else null
        val probe = endpoint ?: services.singleOrNull()?.takeIf { it == "vendor.oplus.hardware.touch.IOplusTouch/default" }
            ?.let { OplusTouchEndpoint(it, 0) }
        val support = probe?.let { TouchSamplingProtocol.parcelInt(output(it.supportCommand()).orEmpty()) }
        val mode = probe?.let { TouchSamplingProtocol.currentMode(output(it.readCommand()).orEmpty()) }
        val profile = panel?.let { TouchSamplingConfig.select(knownProfiles, it, null) }
        val binding = if (endpoint != null && profile != null) runCatching { endpoint.bindingId(profile) }.getOrNull() else null
        val matched = profile != null && TouchSamplingPolicy.matches(profile, mode)
        val error = when {
            services.size > 1 || (panel == null && knownProfiles.isNotEmpty()) -> "config_ambiguous"
            probe == null -> "backend_unknown"
            rejectedTables > 0 || (profile == null && knownProfiles.isNotEmpty()) -> "config_ambiguous"
            support != 1 && mode == null -> if (support == 0 || support == -1) "hal_unsupported" else "hal"
            profile == null || profile.presets.isEmpty() -> "config"
            endpoint == null -> "config_ambiguous"
            mode == null || !matched -> "read"
            support != 1 -> "read_only"
            binding == null -> "config"
            else -> null
        }
        val diagnostic = buildString {
            append("API: ").append(Build.VERSION.SDK_INT).append("\nRoot: granted")
            append("\nOPlus AIDL services: ").append(services.size)
            services.forEach { append("\n  ").append(it) }
            append("\nConfig files: ").append(paths.size).append("; indexed tables: ").append(profiles.size)
            paths.forEach { append("\n  ").append(it) }
            append("\nRejected tables: ").append(rejectedTables)
            append("\nPanel: ").append(panel ?: "unresolved")
            append("\nNode: 182; support: ").append(support ?: "unavailable")
            if (endpoint == null && probe != null) append(" (primary-panel read-only probe)")
            append("\nCurrent indexed mode: ").append(mode?.let { "${it.first},${it.second}" } ?: "unavailable")
            append("\nTable matches current mode: ").append(matched)
            append("\nAccess: ").append(error ?: "read/write")
            append("\nKernel nodes (discovery only): ").append(nodes.size)
            nodes.forEach { append("\n  ").append(it) }
            if (override) {
                val status = output("/system/bin/toybox head -c 64 $OVERRIDE_STATUS 2>/dev/null")?.trim()
                append("\nOverride: ").append(status?.takeIf { it.matches(Regex("[a-z_-]{1,40}")) } ?: "legacy or pending")
            }
        }
        return Inspection(
            TouchSamplingState(
                available = services.isNotEmpty() || nodes.isNotEmpty() || profiles.isNotEmpty(),
                canWrite = error == null,
                presets = profile?.presets.orEmpty().sortedWith(compareBy<TouchRatePreset> { it.hz ?: Int.MAX_VALUE }.thenBy { it.index }),
                defaultChipValue = profile?.defaultChipValue,
                currentIndex = mode?.first,
                currentChipValue = mode?.second,
                overrideEnabled = override,
                error = error,
                diagnostic = diagnostic,
                backendId = binding,
                backendLabel = endpoint?.let { "OPlus AIDL · panel ${it.panelIndex} · node ${it.node}" }
                    ?: probe?.let { "OPlus AIDL · primary-panel read-only probe" } ?: services.singleOrNull(),
                configSource = profile?.sourceLabel,
            ),
            endpoint,
            profile,
            hashes,
            legacyOverride,
        )
    }

    // Config contents and vendor errors are not copied into application logs.
    private fun output(command: String): String? {
        val result = Shell.cmd("( $command )").exec()
        return if (result.isSuccess) result.out.joinToString("\n") else null
    }

    private fun quote(value: String) = TouchSamplingDiscovery.safeShellQuote(value)

    private fun configHash(path: String): String? = output("/system/bin/toybox sha256sum ${quote(path)}")
        ?.substringBefore(' ')?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }

    private val CONFIG_LISTING = """
        for root in /data/vendor/touchconfig /vendor/etc/touchconfig /odm/etc/touchconfig; do
            [ -d "${'$'}root" ] && [ ! -L "${'$'}root" ] || continue
            for file in "${'$'}root"/*.xml "${'$'}root"/*/*.xml "${'$'}root"/*/*/*.xml; do
                [ -f "${'$'}file" ] && [ ! -L "${'$'}file" ] || continue
                resolved=${'$'}(/system/bin/toybox realpath "${'$'}file" 2>/dev/null) || continue
                [ "${'$'}resolved" = "${'$'}file" ] && printf '%s\n' "${'$'}file"
            done
        done
        true
    """.trimIndent()

    private val KERNEL_LISTING = """
        for root in /proc/touchpanel /proc/touchpanel0 /proc/touchpanel1 /sys/class/touchpanel; do
            for name in tp_index report_rate touch_report_rate sampling_rate; do
                [ -r "${'$'}root/${'$'}name" ] && printf '%s\n' "${'$'}root/${'$'}name"
            done
        done
        true
    """.trimIndent()
}
