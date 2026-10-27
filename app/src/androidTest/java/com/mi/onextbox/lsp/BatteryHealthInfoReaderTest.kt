package com.mi.onextbox.lsp

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BatteryHealthInfoReaderTest {
    @Test fun nativeProfileAndBroadcastAreReadWithoutChangingBatteryState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        assertNotNull(battery)
        val info = BatteryHealthInfoReader.read(context, battery, 0)
        val profileClass = Class.forName("com.android.internal.os.PowerProfile")
        val profile = profileClass.getConstructor(Context::class.java).newInstance(context)
        val typical = profileClass.getMethod("getBatteryCapacity").invoke(profile) as Number
        val rated = profileClass.getMethod("getAveragePower", String::class.java)
            .invoke(profile, "rated_capacity") as Number
        assertEquals(BatteryHealthInfoRules.capacity(typical.toDouble()), info.typicalCapacity)
        fun node(path: String) = runCatching { File(path).readText() }.getOrNull()
        assertEquals(BatteryHealthInfoRules.ratedCapacity(rated.toDouble(),
            node("/sys/class/oplus_chg/battery/design_capacity"),
            node("/sys/class/power_supply/battery/charge_full_design")), info.ratedCapacity)
        assertEquals(BatteryHealthInfoRules.fullChargeCapacity(
            node("/sys/class/oplus_chg/battery/battery_fcc"),
            node("/sys/class/power_supply/battery/charge_full"),
            node("/sys/class/power_supply/battery/batt_fcc")), info.fullChargeCapacity)
        assertEquals(BatteryHealthInfoRules.health(battery!!.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)), info.health)
        assertEquals(0, info.cycleCount)
        assertNotNull(info.typicalCapacity)
        android.util.Log.i("BatteryHealthInfoTest", "Native battery information: $info")
    }
}
