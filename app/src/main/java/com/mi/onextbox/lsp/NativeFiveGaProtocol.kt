package com.mi.onextbox.lsp

import android.os.Bundle
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.mi.onextbox.BuildConfig

internal data class NativeFiveGaSlot(
    val phoneId: Int,
    val subId: Int,
    val profile: String,
    val epoch: Long,
    val qualified: Boolean,
    val networkType: Int,
    val overrideType: Int,
    val ready: Boolean,
    val reason: String,
    val cellCount: Int = 0,
    val bandwidthKhz: Int = 0,
)

internal data class NativeFiveGaSnapshot(
    val source: String,
    val sequence: Long,
    val slots: List<NativeFiveGaSlot>,
    val status: String,
)

internal object NativeFiveGaProtocol {
    const val AUTHORITY = "com.mi.onextbox.native5ga"
    const val PACKAGE = "com.mi.onextbox"
    const val DESCRIPTOR = "$AUTHORITY.v1"
    const val VERSION = 1
    const val READ = 1
    const val REGISTER = 2
    const val UNREGISTER = 3
    const val CHANGED = 4
    const val INVALIDATED = 5
    const val PHONE_UID = 1001
    const val SYSTEM_UID = 1000
    val build: Long get() = BuildConfig.APP_BUILD_TIMESTAMP

    fun envelope() = Bundle().apply {
        putInt("protocol", VERSION)
        putLong("build", build)
    }

    // Restarted scopes can coexist with code retained from the previous APK.
    // The schema version defines compatibility; build time is diagnostic metadata.
    fun compatible(bundle: Bundle?) = bundle != null && bundle.getInt("protocol", -1) == VERSION

    fun response(request: Bundle?) = envelope().apply {
        // Previous phone scopes also validate this field when publishing to a new broker.
        if (request?.containsKey("build") == true) putLong("build", request.getLong("build"))
    }

    fun systemUiUid(context: Context): Int = runCatching {
        val manager = context.packageManager
        val info = manager.getApplicationInfo("com.android.systemui", 0)
        check(info.flags and ApplicationInfo.FLAG_SYSTEM != 0)
        check(manager.checkSignatures("android", "com.android.systemui") == PackageManager.SIGNATURE_MATCH)
        info.uid
    }.getOrDefault(-1)

    fun canRead(uid: Int, moduleUid: Int, systemUiUid: Int) = NativeFiveGaAccess.reader(uid, moduleUid, systemUiUid)

    fun encode(snapshot: NativeFiveGaSnapshot?): Bundle? = snapshot?.let {
        envelope().apply {
            putString("source", it.source)
            putLong("sequence", it.sequence)
            putString("status", it.status)
            putParcelableArrayList("slots", ArrayList(it.slots.map { slot ->
                Bundle().apply {
                    putInt("phone", slot.phoneId)
                    putInt("sub", slot.subId)
                    putString("profile", slot.profile)
                    putLong("epoch", slot.epoch)
                    putBoolean("qualified", slot.qualified)
                    putInt("network", slot.networkType)
                    putInt("override", slot.overrideType)
                    putBoolean("ready", slot.ready)
                    putString("reason", slot.reason)
                    putInt("cells", slot.cellCount)
                    putInt("bandwidth", slot.bandwidthKhz)
                }
            }))
        }
    }

    fun decode(bundle: Bundle?): NativeFiveGaSnapshot? = runCatching {
        if (!compatible(bundle)) return null
        val value = requireNotNull(bundle)
        val source = requireNotNull(value.getString("source")).also { require(it.length in 1..96) }
        val sequence = value.getLong("sequence", -1).also { require(it >= 0) }
        val status = requireNotNull(value.getString("status")).also { require(it.length in 1..64) }
        val rows = requireNotNull(value.getParcelableArrayList("slots", Bundle::class.java))
        require(rows.size <= 4)
        val slots = rows.map { row ->
            NativeFiveGaSlot(
                phoneId = row.getInt("phone", -1), subId = row.getInt("sub", -1),
                profile = row.getString("profile", ""), epoch = row.getLong("epoch", -1),
                qualified = row.getBoolean("qualified"), networkType = row.getInt("network"),
                overrideType = row.getInt("override"), ready = row.getBoolean("ready"),
                reason = row.getString("reason", ""), cellCount = row.getInt("cells"),
                bandwidthKhz = row.getInt("bandwidth"),
            ).also {
                require(it.phoneId in 0..3 && it.subId >= -1 && it.epoch >= 0)
                require(it.profile.length <= 64 && it.reason.length <= 64)
                require(it.cellCount in 0..32 && it.bandwidthKhz in 0..10_000_000)
                require(!it.qualified || (it.ready && it.subId >= 0 && it.networkType == 20 && it.overrideType == 5))
            }
        }
        require(slots.distinctBy { it.phoneId }.size == slots.size)
        NativeFiveGaSnapshot(source, sequence, slots, status)
    }.getOrNull()
}
