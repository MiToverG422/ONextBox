package com.mi.onextbox.ui.home

import com.mi.onextbox.R
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.topjohnwu.superuser.Shell
import java.io.File

private object BatteryHardwareNodes {
    val paths = linkedMapOf(
        "battery" to "/sys/class/power_supply/battery/uevent",
        "usb" to "/sys/class/power_supply/usb/uevent",
        "wireless" to "/sys/class/power_supply/wireless/uevent",
        "serial" to "/sys/class/oplus_chg/battery/battery_sn",
        "icSerial" to "/sys/class/oplus_chg/battery/batt_ic_sn",
        "manufactured" to "/sys/class/oplus_chg/battery/battery_manu_date",
        "firstUse" to "/sys/class/oplus_chg/battery/battery_first_usage_date",
        "oemType" to "/sys/class/oplus_chg/battery/battery_type",
        "chemistryId" to "/sys/class/oplus_chg/battery/battery_chem_id",
        "fcc" to "/sys/class/oplus_chg/battery/battery_fcc",
        "design" to "/sys/class/oplus_chg/battery/design_capacity",
        "remaining" to "/sys/class/oplus_chg/battery/battery_rm",
        "qmax" to "/sys/class/oplus_chg/battery/battery_qmax",
        "soh" to "/sys/class/oplus_chg/battery/battery_soh",
        "uiSoh" to "/sys/class/oplus_chg/battery/battery_ui_soh",
        "cycles" to "/sys/class/oplus_chg/battery/battery_cc",
        "uiCycles" to "/sys/class/oplus_chg/battery/battery_ui_cc",
        "authenticated" to "/sys/class/oplus_chg/battery/authenticate",
        "used" to "/sys/class/oplus_chg/battery/battery_used_flag",
        "undervoltage" to "/sys/class/oplus_chg/battery/vbat_uv",
        "gauge" to "/proc/devinfo/gauge",
        "gaugeType" to "/sys/class/oplus_chg/battery/gauge_type",
        "gaugeVoltage" to "/sys/class/oplus_chg/battery/gauge_vbat",
        "chargingTechnology" to "/sys/class/oplus_chg/battery/charge_technology",
        "fastCharging" to "/sys/class/oplus_chg/battery/fast_charge",
        "vooc" to "/sys/class/oplus_chg/battery/voocchg_ing",
        "pps" to "/sys/class/oplus_chg/battery/ppschg_ing",
        "adapterPower" to "/sys/class/oplus_chg/common/adapter_power",
        "devicePower" to "/sys/class/oplus_chg/common/device_power",
        "protocol" to "/sys/class/oplus_chg/common/protocol_type",
        "manufacturer" to "/sys/class/power_supply/battery/manufacturer",
        "standardSerial" to "/sys/class/power_supply/battery/serial_number",
        "standardManufactured" to "/sys/class/power_supply/battery/manufacture_date",
        "standardFirstUse" to "/sys/class/power_supply/battery/first_usage_date",
        "bmsSerial" to "/sys/class/power_supply/bms/serial_number",
        "bmsManufacturer" to "/sys/class/power_supply/bms/manufacturer",
    )

    fun read(root: Boolean): Map<String, String> {
        val result = paths.mapValues { (_, path) -> runCatching {
            File(path).inputStream().use { it.readNBytes(4096).toString(Charsets.UTF_8).trim() }
        }.getOrDefault("") }.toMutableMap()
        if (root) runCatching {
            val script = buildString {
                paths.forEach { (key, path) ->
                    append("if [ -r '$path' ]; then printf '\\nONB_BAT=$key\\n'; head -c 4096 '$path' 2>/dev/null; printf '\\n'; fi;\n")
                }
            }
            BatteryHardwareRules.parseFields(Shell.cmd(script).exec().out).forEach { (key, value) -> result[key] = value }
        }
        return result
    }
}

internal fun readBatteryHardwareInfo(context: Context, root: Boolean): List<HardwareSection> {
    val raw = BatteryHardwareNodes.read(root)
    fun node(key: String) = BatteryHardwareRules.text(raw[key])
    fun supply(key: String): Map<String, String> = node(key)?.lineSequence()?.mapNotNull { line ->
        line.takeIf { it.startsWith("POWER_SUPPLY_") && '=' in it }?.let {
            it.substringBefore('=').removePrefix("POWER_SUPPLY_") to it.substringAfter('=')
        }
    }?.toMap().orEmpty()
    val battery = supply("battery")
    val usb = supply("usb")
    val wireless = supply("wireless")
    val manager = context.getSystemService(BatteryManager::class.java)
    val intent = runCatching { context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
    val unknown = if (root) context.getString(R.string.device_missing_data) else context.getString(R.string.device_missing_permission)
    fun text(value: String?) = BatteryHardwareRules.text(value) ?: unknown
    fun first(vararg values: String?) = values.firstNotNullOfOrNull(BatteryHardwareRules::text)
    fun number(value: String?) = BatteryHardwareRules.number(value)
    fun property(id: Int): Long? = runCatching { manager?.getLongProperty(id) }.getOrNull()
        ?.takeUnless { it == Long.MIN_VALUE || it == Long.MAX_VALUE || it == Int.MIN_VALUE.toLong() }
    fun extra(key: String): Int? = intent?.takeIf { it.hasExtra(key) }?.getIntExtra(key, Int.MIN_VALUE)?.takeUnless { it == Int.MIN_VALUE }
    fun value(number: Double?, unit: String): String = number?.let {
        "${BatteryHardwareRules.format(it)}${if (unit == "%") "" else " "}$unit"
    } ?: unknown
    fun micro(value: String?): Double? = number(value)?.div(1000)
    fun milliVolts(value: String?): Double? = number(value)?.div(1000)
    fun microVolts(value: String?): Double? = number(value)?.div(1_000_000)
    fun boolean(value: String?): String = when (value?.trim()?.lowercase()) { "1", "true" -> context.getString(R.string.common_yes); "0", "false" -> context.getString(R.string.common_no); else -> unknown }
    fun date(vararg values: String?): String {
        values.firstNotNullOfOrNull(BatteryHardwareRules::date)?.let { return it }
        val unparsed = values.firstNotNullOfOrNull(BatteryHardwareRules::text)
            ?.takeUnless { it in setOf("0", "-1", Long.MIN_VALUE.toString()) }
        return unparsed?.let { context.getString(R.string.device_raw_date, it) } ?: unknown
    }
    fun code(value: String?) = node(value.orEmpty())?.let { context.getString(R.string.device_raw_code, it) } ?: unknown
    fun sourceDate(id: Int) = property(id)?.takeIf { it > 0 }?.toString()

    val level = extra(BatteryManager.EXTRA_LEVEL) ?: battery["CAPACITY"]?.toIntOrNull()
    val scale = extra(BatteryManager.EXTRA_SCALE) ?: 100
    val status = extra(BatteryManager.EXTRA_STATUS) ?: property(BatteryManager.BATTERY_PROPERTY_STATUS)?.toInt()
    val health = extra(BatteryManager.EXTRA_HEALTH)
    val temperature = extra(BatteryManager.EXTRA_TEMPERATURE)?.toDouble()?.div(10)
        ?: number(battery["TEMP"])?.div(10)
    val voltage = extra(BatteryManager.EXTRA_VOLTAGE)?.takeIf { it > 0 }?.toDouble()?.div(1000)
        ?: microVolts(battery["VOLTAGE_NOW"])
    val current = property(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)?.toDouble()?.div(1000)
        ?: micro(battery["CURRENT_NOW"])
    val averageCurrent = property(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)?.toDouble()?.div(1000)
    val plugged = extra(BatteryManager.EXTRA_PLUGGED)
    val pluggedNames = plugged?.let { flags -> listOf(1 to context.getString(R.string.device_source_ac), 2 to "USB", 4 to context.getString(R.string.device_source_wireless), 8 to context.getString(R.string.device_source_dock))
        .filter { flags and it.first != 0 }.joinToString(" / ") { it.second }.ifEmpty { context.getString(R.string.device_source_none) } }
        ?: listOf("USB" to usb["ONLINE"], context.getString(R.string.device_source_wireless) to wireless["ONLINE"]).filter { it.second == "1" }
            .joinToString(" / ") { it.first }.ifEmpty { unknown }

    val identity = listOf(
        context.getString(R.string.device_battery_serial) to text(first(runCatching { manager?.getStringProperty(11) }.getOrNull(),
            node("serial"), battery["SERIAL_NUMBER"], node("standardSerial"), node("bmsSerial"))),
        context.getString(R.string.device_battery_ic_serial) to text(node("icSerial")),
        context.getString(R.string.device_battery_manufacture_date) to date(node("manufactured"), node("standardManufactured"), sourceDate(7)),
        context.getString(R.string.device_battery_first_use) to date(node("firstUse"), node("standardFirstUse"), sourceDate(8)),
        context.getString(R.string.device_cell_manufacturer) to text(first(battery["MANUFACTURER"], node("manufacturer"), node("bmsManufacturer"))),
        context.getString(R.string.device_battery_system_model) to text(battery["MODEL_NAME"]),
        context.getString(R.string.device_battery_type) to text(node("oemType")),
        context.getString(R.string.device_battery_chemistry) to text(intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: battery["TECHNOLOGY"]),
        context.getString(R.string.device_battery_chem_id) to text(BatteryHardwareRules.chemistryId(node("chemistryId"))),
        context.getString(R.string.device_gauge_model) to text(HardwareInfoRules.field(node("gauge"), "Device version")),
        context.getString(R.string.device_gauge_vendor) to text(HardwareInfoRules.field(node("gauge"), "Device manufacture")),
        context.getString(R.string.device_gauge_type) to code("gaugeType"),
        context.getString(R.string.device_battery_auth) to when (node("authenticated")) { "1" -> context.getString(R.string.device_auth_pass); "0" -> context.getString(R.string.device_auth_fail); else -> unknown },
        context.getString(R.string.device_battery_part_status) to when (property(12)?.toInt()) { 1 -> context.getString(R.string.device_part_original); 2 -> context.getString(R.string.device_part_replaced); else -> unknown },
    )

    val profile = runCatching { Class.forName("com.android.internal.os.PowerProfile")
        .getConstructor(Context::class.java).newInstance(context) }.getOrNull()
    fun profileCapacity(key: String? = null): Double? = runCatching {
        val owner = profile ?: return@runCatching null
        val result = if (key == null) owner.javaClass.getMethod("getBatteryCapacity").invoke(owner)
        else owner.javaClass.getMethod("getAveragePower", String::class.java).invoke(owner, key)
        (result as? Number)?.toDouble()?.takeIf { it > 0 && it <= 100_000 }
    }.getOrNull()
    val design = BatteryHardwareRules.capacityMah(node("design"))
        ?: BatteryHardwareRules.capacityMah(battery["CHARGE_FULL_DESIGN"], micro = true)
    val full = BatteryHardwareRules.capacityMah(node("fcc"))
        ?: BatteryHardwareRules.capacityMah(battery["CHARGE_FULL"], micro = true)
    val counter = (property(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)?.takeIf { it >= 0 }?.toDouble()?.div(1000)
        ?: micro(battery["CHARGE_COUNTER"]))?.takeIf { it in 0.0..100_000.0 }
    val cycles = BatteryHardwareRules.cycles(battery["CYCLE_COUNT"])
        ?: BatteryHardwareRules.cycles(node("cycles"))
        ?: extra(BatteryManager.EXTRA_CYCLE_COUNT)?.takeIf { it >= 0 }?.toLong()
    val soh = BatteryHardwareRules.percent(node("soh"))
        ?: property(10)?.toString()?.let(BatteryHardwareRules::percent)
    val capacity = listOf(
        context.getString(R.string.battery_health_status_label) to when (health) { 2 -> context.getString(R.string.battery_health_good); 3 -> context.getString(R.string.battery_health_overheat); 4 -> context.getString(R.string.battery_health_dead); 5 -> context.getString(R.string.battery_health_over_voltage); 6 -> context.getString(R.string.battery_health_failure); 7 -> context.getString(R.string.battery_health_cold); else -> text(battery["HEALTH"]) },
        context.getString(R.string.device_battery_soh) to value(soh, "%"),
        context.getString(R.string.device_battery_ui_soh) to value(BatteryHardwareRules.percent(node("uiSoh")), "%"),
        context.getString(R.string.device_battery_cycles) to (cycles?.let { context.resources.getQuantityString(R.plurals.device_cycles_count, it.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), it) } ?: unknown),
        context.getString(R.string.device_battery_ui_cycles) to (BatteryHardwareRules.cycles(node("uiCycles"))?.let { context.resources.getQuantityString(R.plurals.device_cycles_count, it.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), it) } ?: unknown),
        context.getString(R.string.device_battery_fcc) to value(full, "mAh"),
        context.getString(R.string.device_battery_design) to value(design, "mAh"),
        context.getString(R.string.device_battery_rated) to value(profileCapacity("rated_capacity"), "mAh"),
        context.getString(R.string.device_battery_typical) to value(profileCapacity(), "mAh"),
        context.getString(R.string.device_battery_ratio) to value(BatteryHardwareRules.ratio(full, design), "%"),
        context.getString(R.string.device_battery_system_fcc) to value(BatteryHardwareRules.capacityMah(battery["CHARGE_FULL"], micro = true), "mAh"),
        context.getString(R.string.device_battery_system_design) to value(BatteryHardwareRules.capacityMah(battery["CHARGE_FULL_DESIGN"], micro = true), "mAh"),
        context.getString(R.string.device_battery_qmax) to value(BatteryHardwareRules.capacityMah(node("qmax")), "mAh"),
        context.getString(R.string.device_battery_rm) to value(number(node("remaining"))?.takeIf { it >= 0 }, "mAh"),
        context.getString(R.string.device_battery_charge_counter) to value(counter, "mAh"),
        context.getString(R.string.device_battery_used_flag) to text(node("used")),
    )

    val energy = property(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)?.takeIf { it > 0 }?.toDouble()?.div(1_000_000_000)
    val state = listOf(
        context.getString(R.string.device_battery_present) to boolean(intent?.takeIf { it.hasExtra(BatteryManager.EXTRA_PRESENT) }
            ?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false)?.toString() ?: battery["PRESENT"]),
        context.getString(R.string.device_battery_level) to (level?.takeIf { it >= 0 && scale > 0 }?.let { value(it * 100.0 / scale, "%") } ?: unknown),
        context.getString(R.string.device_battery_status) to when (status) { 2 -> context.getString(R.string.device_status_charging); 3 -> context.getString(R.string.device_status_discharging); 4 -> context.getString(R.string.device_status_not_charging); 5 -> context.getString(R.string.device_status_full); else -> text(battery["STATUS"]) },
        context.getString(R.string.device_battery_power_source) to pluggedNames,
        context.getString(R.string.device_battery_temperature) to value(temperature, "°C"),
        context.getString(R.string.device_battery_voltage) to value(voltage, "V"),
        context.getString(R.string.device_battery_ocv) to value(microVolts(battery["VOLTAGE_OCV"]), "V"),
        context.getString(R.string.device_battery_current) to value(current, "mA"),
        context.getString(R.string.device_battery_average_current) to value(averageCurrent, "mA"),
        context.getString(R.string.device_battery_power) to value(if (voltage != null && current != null) voltage * current / 1000 else null, "W"),
        context.getString(R.string.device_battery_energy) to value(energy, "Wh"),
        context.getString(R.string.device_battery_min_voltage) to value(microVolts(battery["VOLTAGE_MIN_DESIGN"]), "V"),
        context.getString(R.string.device_battery_undervoltage) to value(milliVolts(node("undervoltage")), "V"),
        context.getString(R.string.device_battery_gauge_voltage) to value(milliVolts(node("gaugeVoltage")), "V"),
    )

    val inputVoltage = microVolts(usb["VOLTAGE_NOW"])
    val inputCurrent = micro(usb["CURRENT_NOW"])
    val maxCurrent = extra("max_charging_current")?.takeIf { it > 0 }?.toDouble()?.div(1_000_000)
        ?: number(usb["CURRENT_MAX"])?.takeIf { it > 0 }?.div(1_000_000)
    val maxVoltage = extra("max_charging_voltage")?.takeIf { it > 0 }?.toDouble()?.div(1_000_000)
        ?: microVolts(usb["VOLTAGE_MAX"])?.takeIf { it > 0 }
    val charging = listOf(
        context.getString(R.string.device_usb_online) to boolean(usb["ONLINE"]),
        context.getString(R.string.device_usb_type) to text(BatteryHardwareRules.selectedUsbType(usb["USB_TYPE"]) ?: usb["REAL_TYPE"]),
        context.getString(R.string.device_usb_voltage) to value(inputVoltage, "V"),
        context.getString(R.string.device_usb_current) to value(inputCurrent, "mA"),
        context.getString(R.string.device_usb_power) to value(if (inputVoltage != null && inputCurrent != null) inputVoltage * inputCurrent / 1000 else null, "W"),
        context.getString(R.string.device_input_current_max) to value(maxCurrent, "A"),
        context.getString(R.string.device_input_voltage_max) to value(maxVoltage, "V"),
        context.getString(R.string.device_input_power_max) to value(if (maxCurrent != null && maxVoltage != null) maxCurrent * maxVoltage else null, "W"),
        context.getString(R.string.device_usb_current_limit) to value(number(usb["INPUT_CURRENT_LIMIT"])?.div(1_000_000), "A"),
        context.getString(R.string.device_fast_charging) to boolean(node("fastCharging")),
        context.getString(R.string.device_vooc_active) to boolean(node("vooc")),
        context.getString(R.string.device_pps_active) to boolean(node("pps")),
        context.getString(R.string.device_adapter_power) to value(number(node("adapterPower"))?.takeIf { it > 0 }?.div(1000), "W"),
        context.getString(R.string.device_charging_capability) to value(number(node("devicePower"))?.takeIf { it > 0 }?.div(1000), "W"),
        context.getString(R.string.device_charging_technology) to code("chargingTechnology"),
        context.getString(R.string.device_charging_protocol) to code("protocol"),
        context.getString(R.string.device_wireless_online) to boolean(wireless["ONLINE"]),
        context.getString(R.string.device_time_to_full) to runCatching { manager?.computeChargeTimeRemaining()?.takeIf { it > 0 } }
            .getOrNull()?.let { context.getString(R.string.device_minutes_estimate, (it + 59_999) / 60_000) }.orEmpty().ifEmpty { if (status == BatteryManager.BATTERY_STATUS_FULL) context.getString(R.string.device_status_full) else unknown },
    )
    return listOf(
        HardwareSection(context.getString(R.string.device_battery_identity_section), identity),
        HardwareSection(context.getString(R.string.device_battery_health_section), capacity),
        HardwareSection(context.getString(R.string.device_battery_state_section), state),
        HardwareSection(context.getString(R.string.device_battery_charging_section), charging),
    )
}
