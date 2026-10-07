package com.mi.onextbox.push

import org.json.JSONArray
import org.json.JSONObject

/** Only package names, registration flags and timestamps cross the bridge. */
internal object PushMonitorCodec {
    fun readRows(raw: String): Map<String, Boolean> {
        require(raw.length <= PushMonitorRules.MAX_WIRE_CHARS) { "Snapshot too large" }
        val array = JSONArray(raw)
        require(array.length() <= PushMonitorRules.MAX_APPS) { "Too many registrations" }
        return PushMonitorRules.sanitizeRows((0 until array.length()).map { index ->
            val row = array.getJSONObject(index)
            mapOf("package" to row.get("package"), "registered" to row.get("registered"))
        })
    }

    fun writeRows(rows: Map<String, Boolean>): JSONArray = JSONArray().apply {
        rows.forEach { (pkg, registered) ->
            put(JSONObject().put("package", pkg).put("registered", registered))
        }
    }

    fun readTimes(raw: String?): Map<String, Long> {
        if (raw == null) return emptyMap()
        require(raw.length <= PushMonitorRules.MAX_WIRE_CHARS)
        val json = JSONObject(raw)
        require(json.length() <= PushMonitorRules.MAX_APPS)
        return json.keys().asSequence().filter(PushMonitorRules::validPackage)
            .mapNotNull { pkg -> (json.opt(pkg) as? Number)?.toLong()?.let { pkg to it } }.toMap()
    }

    fun readSnapshot(raw: String?, times: String?): PushSnapshot {
        val lastPushes = readTimes(times)
        if (raw == null) return PushSnapshot(times = lastPushes)
        require(raw.length <= PushMonitorRules.MAX_WIRE_CHARS)
        val json = JSONObject(raw)
        return PushSnapshot(
            time = json.getLong("time"), complete = json.getBoolean("complete"),
            requestId = json.optString("requestId"), rows = readRows(json.getJSONArray("rows").toString()),
            times = lastPushes,
        )
    }
}
