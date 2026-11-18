package com.mi.onextbox.ui.home

import com.mi.onextbox.ui.settings.SettingsDataRow
import com.mi.onextbox.R
import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Display
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsCardRow
import com.mi.onextbox.ui.settings.SettingsDivider
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class HardwareSection(val title: String, val rows: List<Pair<String, String>>)

@Composable internal fun HomeHardwareInfo(rootGranted: Boolean, refreshKey: Int) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    val owner = LocalLifecycleOwner.current
    var sections by remember { mutableStateOf<List<HardwareSection>>(emptyList()) }
    var batteries by remember { mutableStateOf<List<HardwareSection>>(emptyList()) }
    var revision by remember { mutableIntStateOf(0) }
    var updatedAt by remember { mutableLongStateOf(0L) }
    var reading by remember { mutableStateOf(false) }
    LaunchedEffect(context, rootGranted, refreshKey, revision, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                reading = true
                sections = withContext(Dispatchers.IO) { readHardware(context, rootGranted) }
                while (isActive) {
                    batteries = withContext(Dispatchers.IO) { readBatteryHardwareInfo(context, rootGranted) }
                    updatedAt = System.currentTimeMillis()
                    reading = false
                    delay(30_000)
                }
            } finally { reading = false }
        }
    }
    SettingsGroup {
        SettingsDataRow(
            title = resources.getString(R.string.device_refresh),
            summary = if (updatedAt == 0L) resources.getString(R.string.diagnostics_loading) else resources.getString(R.string.device_updated, SimpleDateFormat("HH:mm:ss", locale).format(Date(updatedAt))),
            onClick = { if (!reading) revision++ },
            refreshing = reading,
        )
    }
    val batteryShown = batteries.ifEmpty { listOf(HardwareSection(resources.getString(R.string.battery_information_group), listOf(resources.getString(R.string.device_battery_data) to resources.getString(R.string.diagnostics_loading)))) }
    val shown = batteryShown + sections.ifEmpty { listOf(resources.getString(R.string.device_ram_section), resources.getString(R.string.device_storage_section), resources.getString(R.string.device_display_section)).map {
        HardwareSection(it, listOf(resources.getString(R.string.device_hardware_data) to resources.getString(R.string.diagnostics_loading)))
    } }
    shown.forEach { section ->
        key(section.title) {
            SettingsGroup {
                SelectionContainer {
                    Column {
                        SettingsCardRow(section.title, "")
                        section.rows.forEach { (label, value) ->
                            SettingsDivider()
                            SettingsDataRow(label, value)
                        }
                    }
                }
            }
        }
    }
}

private object HardwareNodes {
    val paths = linkedMapOf(
        "ram" to "/proc/devinfo/ddr", "ramType" to "/proc/devinfo/ddr_type",
        "ufs" to "/proc/devinfo/ufs", "panel" to "/proc/devinfo/lcd",
        "vendor" to "/sys/class/block/sda/device/vendor", "model" to "/sys/class/block/sda/device/model",
        "revision" to "/sys/class/block/sda/device/rev", "sectors" to "/sys/class/block/sda/size",
        "emmcName" to "/sys/class/block/mmcblk0/device/name", "emmcVendor" to "/sys/class/block/mmcblk0/device/manfid",
        "emmcSectors" to "/sys/class/block/mmcblk0/size",
    )
    private var cache: Map<String, String> = emptyMap()
    private var cachedAt = 0L
    private var cachedRoot = false

    @Synchronized fun read(root: Boolean): Map<String, String> {
        if (cachedAt != 0L && root == cachedRoot && SystemClock.elapsedRealtime() - cachedAt < 300_000) return cache
        val result = paths.mapValues { (_, path) -> runCatching {
            File(path).inputStream().use { it.readNBytes(4096).toString(Charsets.UTF_8).trim() }
        }.getOrDefault("") }.toMutableMap()
        if (root) runCatching {
            val script = buildString {
                paths.forEach { (key, path) ->
                    append("printf '\\nONB_FIELD=$key\\n'; head -c 4096 '$path' 2>/dev/null; printf '\\n';\n")
                }
                append("printf '\\nONB_FIELD=spec\\n'; for f in /sys/bus/platform/devices/*.ufshc/device_descriptor/specification_version /sys/class/scsi_host/host*/device_descriptor/specification_version; do if [ -r \"\$f\" ]; then head -c 64 \"\$f\"; break; fi; done;\n")
                append("printf '\\nONB_FIELD=display\\n'; dumpsys display | grep 'DisplayDeviceInfo{' | grep -m 1 'FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY';\n")
                append("printf '\\nONB_FIELD=panelModule\\n'; tr ' ' '\\n' < /proc/device-tree/chosen/bootargs 2>/dev/null | grep '^touch_panel0=' | head -c 256;\n")
                append("printf '\\nONB_FIELD=panelName\\n'; panel=\$(tr ' ' '\\n' < /proc/device-tree/chosen/bootargs 2>/dev/null | sed -n 's/^msm_drm.dsi_display0=\\([^:]*\\):.*/\\1/p' | head -n 1); ")
                append("case \"\$panel\" in ''|*[!a-zA-Z0-9_,.-]*) ;; *) for d in /proc/device-tree/soc/*/\"\$panel\"; do if [ -r \"\$d/qcom,mdss-dsi-panel-name\" ]; then head -c 512 \"\$d/qcom,mdss-dsi-panel-name\" | tr -d '\\000'; break; fi; done ;; esac;\n")
            }
            var key: String? = null
            val fields = mutableMapOf<String, StringBuilder>()
            Shell.cmd(script).exec().out.forEach { line ->
                if (line.startsWith("ONB_FIELD=")) {
                    key = line.removePrefix("ONB_FIELD=")
                    fields[key!!] = StringBuilder()
                } else key?.let { fields[it]?.appendLine(line) }
            }
            fields.forEach { (name, content) -> content.toString().trim().takeIf { it.isNotEmpty() }?.let { result[name] = it } }
        }
        cache = result.toMap(); cachedAt = SystemClock.elapsedRealtime(); cachedRoot = root
        return cache
    }
}

private fun bytes(value: Long, binary: Boolean = true): String {
    val unit = if (binary) 1024.0 else 1000.0
    return String.format(Locale.ROOT, "%.2f %s", value / unit / unit / unit, if (binary) "GiB" else "GB")
}

private fun readHardware(context: Context, root: Boolean): List<HardwareSection> {
    val unknownHardware = context.getString(R.string.device_unknown_hardware)
    val nodes = HardwareNodes.read(root)
    fun node(key: String) = nodes[key]?.takeIf { it.isNotBlank() }
    fun info(key: String, field: String) = HardwareInfoRules.field(node(key), field)
    val ram = mutableListOf<Pair<String, String>>()
    ram += context.getString(R.string.device_driver_type) to (info("ramType", "Device version") ?: info("ram", "Device version") ?: unknownHardware)
    val ramManufacturer = info("ram", "Device manufacture")
    ram += context.getString(R.string.device_manufacturer) to (ramManufacturer?.substringBefore('|') ?: unknownHardware)
    ram += context.getString(R.string.device_ram_chip) to (ramManufacturer ?: unknownHardware)
    runCatching {
        val memory = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
        ram += context.getString(R.string.device_ram_total) to bytes(memory.totalMem)
        ram += context.getString(R.string.device_ram_usage) to "${bytes(memory.availMem)} / ${bytes((memory.totalMem - memory.availMem).coerceAtLeast(0))}"
    }
    val ufs = node("ufs") != null || readHomeSystemProperty("ro.boot.bootdevice")?.contains("ufs", true) == true
    val storage = mutableListOf<Pair<String, String>>()
    storage += context.getString(R.string.device_type) to if (ufs) "UFS" else if (node("emmcName") != null) "eMMC" else unknownHardware
    storage += context.getString(R.string.device_manufacturer) to (info("ufs", "Device manufacture") ?: node("vendor") ?: node("emmcVendor")?.let { context.getString(R.string.device_vendor_id, it) } ?: unknownHardware)
    storage += context.getString(R.string.device_model) to (info("ufs", "Device version") ?: node("model") ?: node("emmcName") ?: unknownHardware)
    if (ufs) storage += context.getString(R.string.device_ufs_version) to (HardwareInfoRules.specification(node("spec")) ?: unknownHardware)
    storage += context.getString(R.string.device_firmware) to (node("revision") ?: unknownHardware)
    val sectors = (node("sectors") ?: node("emmcSectors"))?.toLongOrNull()
    storage += context.getString(R.string.device_storage_capacity) to (sectors?.takeIf { it in 1..(Long.MAX_VALUE / 512) }?.let { "${bytes(it * 512, false)}（${bytes(it * 512)}）" } ?: unknownHardware)
    runCatching {
        val fs = StatFs(Environment.getDataDirectory().path)
        storage += context.getString(R.string.device_data_partition) to "${bytes(fs.totalBytes)} / ${bytes(fs.availableBytes)}"
    }
    val screen = mutableListOf<Pair<String, String>>()
    val panelVendor = info("panel", "Device manufacture")
    val moduleVendor = HardwareInfoRules.touchPanelVendor(node("panelModule"))
    screen += context.getString(R.string.device_panel_vendor) to (HardwareInfoRules.panelVendor(panelVendor)?.let { localizedVendor(context, it) }
        ?: moduleVendor?.let { context.getString(R.string.device_touch_vendor_value, localizedVendor(context, it)) } ?: unknownHardware)
    screen += context.getString(R.string.device_panel_vendor_raw) to (panelVendor ?: unknownHardware)
    screen += context.getString(R.string.device_panel_model) to (info("panel", "Device version") ?: unknownHardware)
    screen += context.getString(R.string.device_panel_name) to (node("panelName") ?: unknownHardware)
    screen += context.getString(R.string.device_panel_material) to (HardwareInfoRules.panelSeries(node("panelName"))?.let { context.getString(R.string.device_driver_name_value, it) }
        ?: context.getString(R.string.device_panel_material_unknown))
    runCatching {
        val display = checkNotNull(context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY))
        val mode = display.mode
        screen += context.getString(R.string.device_display_mode) to "${mode.physicalWidth} × ${mode.physicalHeight} · ${HardwareInfoRules.rates(listOf(mode.refreshRate))} Hz"
        screen += context.getString(R.string.device_display_supported_modes) to display.supportedModes.groupBy { it.physicalWidth to it.physicalHeight }.entries
            .sortedByDescending { it.key.first.toLong() * it.key.second }.joinToString("\n") { (size, modes) ->
                "${size.first} × ${size.second}: ${HardwareInfoRules.rates(modes.map { it.refreshRate })} Hz"
            }
        @Suppress("DEPRECATION")
        val renderRates = display.supportedRefreshRates
        screen += context.getString(R.string.device_display_render_rates) to "${HardwareInfoRules.rates(renderRates.toList())} Hz"
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        screen += context.getString(R.string.device_display_density) to "${metrics.densityDpi} dpi · ${String.format(Locale.ROOT, "%.2f", metrics.density)}×"
        screen += context.getString(R.string.device_physical_dpi) to context.getString(R.string.device_dpi_axes, metrics.xdpi, metrics.ydpi)
        screen += context.getString(R.string.device_wide_color) to if (display.isWideColorGamut) context.getString(R.string.feature_esim_diag_yes) else context.getString(R.string.device_support_not_reported)
        val reflectedModes = runCatching { Display::class.java.getMethod("getSupportedColorModes").invoke(display) as IntArray }
            .getOrNull()?.let { "supportedColorModes [${it.joinToString()}]" }
        screen += context.getString(R.string.device_color_modes) to (HardwareInfoRules.colorModes(reflectedModes ?: node("display"), context.getString(R.string.device_native_color_mode)) { context.getString(R.string.device_color_mode_value, it) } ?: unknownHardware)
        screen += context.getString(R.string.device_panel_depth) to context.getString(R.string.device_precision_unavailable)
        @Suppress("DEPRECATION")
        val hdr = display.hdrCapabilities
        screen += context.getString(R.string.device_hdr_formats) to (hdr?.supportedHdrTypes?.joinToString(" / ") {
            when (it) { 1 -> "Dolby Vision"; 2 -> "HDR10"; 3 -> "HLG"; 4 -> "HDR10+"; else -> context.getString(R.string.device_type_value, it) }
        }?.takeIf { it.isNotBlank() } ?: context.getString(R.string.device_support_not_reported))
        if (hdr != null) {
            fun luminance(value: Float) = if (value.isFinite() && value >= 0) String.format(Locale.ROOT, "%.2f nit", value) else context.getString(R.string.home_info_unknown)
            screen += context.getString(R.string.device_hdr_luminance) to context.getString(R.string.device_hdr_luminance_values, luminance(hdr.desiredMaxLuminance), luminance(hdr.desiredMaxAverageLuminance), luminance(hdr.desiredMinLuminance))
        }
    }.onFailure { screen += context.getString(R.string.device_display_capability) to unknownHardware }
    return listOf(HardwareSection(context.getString(R.string.device_ram_section), ram), HardwareSection(context.getString(R.string.device_storage_section), storage), HardwareSection(context.getString(R.string.device_display_section), screen))
}

private fun localizedVendor(context: Context, name: String): String = when (name) {
    "BOE 京东方" -> context.getString(R.string.device_vendor_boe)
    "Tianma 天马" -> context.getString(R.string.device_vendor_tianma)
    "Samsung 三星" -> context.getString(R.string.device_vendor_samsung)
    "CSOT 华星光电" -> context.getString(R.string.device_vendor_csot)
    "Visionox 维信诺" -> context.getString(R.string.device_vendor_visionox)
    "Sharp 夏普" -> context.getString(R.string.device_vendor_sharp)
    "AUO 友达" -> context.getString(R.string.device_vendor_auo)
    else -> name
}
