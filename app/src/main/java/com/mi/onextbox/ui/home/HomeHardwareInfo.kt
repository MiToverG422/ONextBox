package com.mi.onextbox.ui.home

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Display
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.mi.onextbox.ui.settings.SettingsGroup
import com.mi.onextbox.ui.settings.SettingsCardRow
import com.mi.onextbox.ui.settings.SettingsDivider
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private data class HardwareSection(val title: String, val rows: List<Pair<String, String>>)
private const val UnknownHardware = "未知（设备未提供或无读取权限）"

@Composable internal fun HomeHardwareInfo(rootGranted: Boolean, refreshKey: Int) {
    val context = LocalContext.current
    var sections by remember { mutableStateOf<List<HardwareSection>>(emptyList()) }
    LaunchedEffect(rootGranted, refreshKey) {
        sections = withContext(Dispatchers.IO) { readHardware(context, rootGranted) }
    }
    val shown = sections.ifEmpty { listOf("运行内存（RAM）", "闪存", "屏幕信息").map {
        HardwareSection(it, listOf("硬件信息" to "正在读取…"))
    } }
    shown.forEach { section ->
        SettingsGroup {
            Column {
                SettingsCardRow(section.title, "")
                section.rows.forEach { (label, value) ->
                    SettingsDivider()
                    SettingsCardRow(label, value)
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
    val nodes = HardwareNodes.read(root)
    fun node(key: String) = nodes[key]?.takeIf { it.isNotBlank() }
    fun info(key: String, field: String) = HardwareInfoRules.field(node(key), field)
    val ram = mutableListOf<Pair<String, String>>()
    ram += "类型（驱动上报）" to (info("ramType", "Device version") ?: info("ram", "Device version") ?: UnknownHardware)
    val ramManufacturer = info("ram", "Device manufacture")
    ram += "厂家" to (ramManufacturer?.substringBefore('|') ?: UnknownHardware)
    ram += "颗粒信息（驱动上报）" to (ramManufacturer ?: UnknownHardware)
    runCatching {
        val memory = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
        ram += "系统可见总量" to bytes(memory.totalMem)
        ram += "当前可用 / 已使用" to "${bytes(memory.availMem)} / ${bytes((memory.totalMem - memory.availMem).coerceAtLeast(0))}"
    }
    val ufs = node("ufs") != null || readHomeSystemProperty("ro.boot.bootdevice")?.contains("ufs", true) == true
    val storage = mutableListOf<Pair<String, String>>()
    storage += "类型" to if (ufs) "UFS" else if (node("emmcName") != null) "eMMC" else UnknownHardware
    storage += "厂家" to (info("ufs", "Device manufacture") ?: node("vendor") ?: node("emmcVendor")?.let { "厂商 ID：$it" } ?: UnknownHardware)
    storage += "型号" to (info("ufs", "Device version") ?: node("model") ?: node("emmcName") ?: UnknownHardware)
    if (ufs) storage += "UFS 规范版本" to (HardwareInfoRules.specification(node("spec")) ?: UnknownHardware)
    storage += "固件版本" to (node("revision") ?: UnknownHardware)
    val sectors = (node("sectors") ?: node("emmcSectors"))?.toLongOrNull()
    storage += "主存储逻辑单元容量" to (sectors?.takeIf { it in 1..(Long.MAX_VALUE / 512) }?.let { "${bytes(it * 512, false)}（${bytes(it * 512)}）" } ?: UnknownHardware)
    runCatching {
        val fs = StatFs(Environment.getDataDirectory().path)
        storage += "数据分区总量 / 可用" to "${bytes(fs.totalBytes)} / ${bytes(fs.availableBytes)}"
    }
    val screen = mutableListOf<Pair<String, String>>()
    val panelVendor = info("panel", "Device manufacture")
    val moduleVendor = HardwareInfoRules.touchPanelVendor(node("panelModule"))
    screen += "屏幕厂家" to (HardwareInfoRules.panelVendor(panelVendor)
        ?: moduleVendor?.let { "$it（触控模组上报）" } ?: UnknownHardware)
    screen += "显示面板厂家原始标识" to (panelVendor ?: UnknownHardware)
    screen += "面板型号 / 版本" to (info("panel", "Device version") ?: UnknownHardware)
    screen += "面板名称（驱动上报）" to (node("panelName") ?: UnknownHardware)
    screen += "屏幕系列 / 发光材料" to (HardwareInfoRules.panelSeries(node("panelName"))?.let { "$it（驱动名称）" }
        ?: "未公开，无法从内部料号确认")
    runCatching {
        val display = checkNotNull(context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY))
        val mode = display.mode
        screen += "当前显示模式" to "${mode.physicalWidth} × ${mode.physicalHeight} · ${HardwareInfoRules.rates(listOf(mode.refreshRate))} Hz"
        screen += "支持的分辨率与刷新率" to display.supportedModes.groupBy { it.physicalWidth to it.physicalHeight }.entries
            .sortedByDescending { it.key.first.toLong() * it.key.second }.joinToString("\n") { (size, modes) ->
                "${size.first} × ${size.second}：${HardwareInfoRules.rates(modes.map { it.refreshRate })} Hz"
            }
        @Suppress("DEPRECATION")
        val renderRates = display.supportedRefreshRates
        screen += "系统支持的渲染刷新率" to "${HardwareInfoRules.rates(renderRates.toList())} Hz"
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        screen += "显示密度" to "${metrics.densityDpi} dpi · ${String.format(Locale.ROOT, "%.2f", metrics.density)}×"
        screen += "物理 DPI（系统上报）" to String.format(Locale.ROOT, "横向 %.1f / 纵向 %.1f", metrics.xdpi, metrics.ydpi)
        screen += "广色域" to if (display.isWideColorGamut) "支持" else "系统未报告支持"
        val reflectedModes = runCatching { Display::class.java.getMethod("getSupportedColorModes").invoke(display) as IntArray }
            .getOrNull()?.let { "supportedColorModes [${it.joinToString()}]" }
        screen += "支持的色彩模式" to (HardwareInfoRules.colorModes(reflectedModes ?: node("display")) ?: UnknownHardware)
        screen += "色域覆盖率 / 面板位深" to "系统未公开，无法准确读取"
        @Suppress("DEPRECATION")
        val hdr = display.hdrCapabilities
        screen += "HDR 格式（当前模式）" to (hdr?.supportedHdrTypes?.joinToString(" / ") {
            when (it) { 1 -> "Dolby Vision"; 2 -> "HDR10"; 3 -> "HLG"; 4 -> "HDR10+"; else -> "类型 $it" }
        }?.takeIf { it.isNotBlank() } ?: "系统未报告支持")
        if (hdr != null) {
            fun luminance(value: Float) = if (value.isFinite() && value >= 0) String.format(Locale.ROOT, "%.2f nit", value) else "未知"
            screen += "HDR 亮度（系统上报）" to "峰值 ${luminance(hdr.desiredMaxLuminance)}\n最大平均 ${luminance(hdr.desiredMaxAverageLuminance)}\n最低 ${luminance(hdr.desiredMinLuminance)}"
        }
    }.onFailure { screen += "显示能力" to UnknownHardware }
    return listOf(HardwareSection("运行内存（RAM）", ram), HardwareSection("闪存", storage), HardwareSection("屏幕信息", screen))
}
