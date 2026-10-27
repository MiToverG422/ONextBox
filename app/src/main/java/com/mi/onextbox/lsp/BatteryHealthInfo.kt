package com.mi.onextbox.lsp

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import com.mi.onextbox.lsp.compat.ModernReflect
import java.io.File
import kotlin.math.roundToInt

internal data class BatteryHealthInfo(
    val cycleCount: Int? = null,
    val health: Int? = null,
    val fullChargeCapacity: Int? = null,
    val ratedCapacity: Int? = null,
    val typicalCapacity: Int? = null,
)

internal object BatteryHealthInfoRules {
    fun capacity(value: Double?): Int? = value
        ?.takeIf { it.isFinite() && it >= 1.0 && it <= 100_000.0 }
        ?.roundToInt()

    fun microampHours(raw: String?): Int? = raw?.trim()?.toLongOrNull()
        ?.takeIf { it > 0 }
        ?.let { capacity(it / 1_000.0) }

    fun milliampHours(raw: String?): Int? = raw?.trim()?.toLongOrNull()
        ?.let { capacity(it.toDouble()) }

    fun health(value: Int?): Int? = value?.takeIf { it in 1..7 }

    fun healthRatio(fullChargeCapacity: Int?, ratedCapacity: Int?): Double? {
        if (fullChargeCapacity == null || ratedCapacity == null || fullChargeCapacity <= 0 || ratedCapacity <= 0) return null
        return fullChargeCapacity.toDouble() / ratedCapacity
    }

    fun ratedCapacity(profile: Double?, vendorDesign: String?, standardDesign: String?): Int? =
        capacity(profile) ?: milliampHours(vendorDesign) ?: microampHours(standardDesign)

    fun fullChargeCapacity(vendorFcc: String?, standardFull: String?, legacyFcc: String?): Int? =
        milliampHours(vendorFcc) ?: microampHours(standardFull) ?: milliampHours(legacyFcc)
}

internal object BatteryHealthInfoReader {
    fun read(context: Context, battery: Intent?, cycles: Int?): BatteryHealthInfo {
        val profile = runCatching {
            ModernReflect.findClass("com.android.internal.os.PowerProfile", context.classLoader)
                .getConstructor(Context::class.java).newInstance(context)
        }.getOrNull()
        fun profileCapacity(name: String? = null): Double? = runCatching {
            val owner = profile ?: return@runCatching null
            val value = if (name == null) {
                owner.javaClass.getMethod("getBatteryCapacity").invoke(owner)
            } else {
                owner.javaClass.getMethod("getAveragePower", String::class.java).invoke(owner, name)
            }
            (value as? Number)?.toDouble()
        }.getOrNull()
        fun node(path: String) = runCatching { File(path).readText() }.getOrNull()
        return BatteryHealthInfo(
            cycleCount = cycles,
            health = BatteryHealthInfoRules.health(battery?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)),
            fullChargeCapacity = BatteryHealthInfoRules.fullChargeCapacity(
                node("/sys/class/oplus_chg/battery/battery_fcc"),
                node("/sys/class/power_supply/battery/charge_full"),
                node("/sys/class/power_supply/battery/batt_fcc"),
            ),
            ratedCapacity = BatteryHealthInfoRules.ratedCapacity(
                profileCapacity("rated_capacity"),
                node("/sys/class/oplus_chg/battery/design_capacity"),
                node("/sys/class/power_supply/battery/charge_full_design"),
            ),
            typicalCapacity = BatteryHealthInfoRules.capacity(profileCapacity()),
        )
    }
}
