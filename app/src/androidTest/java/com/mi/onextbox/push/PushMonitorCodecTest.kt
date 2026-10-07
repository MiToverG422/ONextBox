package com.mi.onextbox.push

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PushMonitorCodecTest {
    @Test fun wireSnapshotDiscardsCredentialsAndContent() {
        val rows = PushMonitorCodec.readRows("""[{"package":"com.example.app","registered":true,"token":"secret","message":"private"}]""")
        val output = PushMonitorCodec.writeRows(rows).toString()
        assertFalse(output.contains("token"))
        assertFalse(output.contains("secret"))
        assertFalse(output.contains("message"))
        assertFalse(output.contains("private"))
        assertTrue(PushMonitorCodec.readRows(output).getValue("com.example.app"))
    }

    @Test fun roundTripKeepsCompletenessAndRefreshId() {
        val raw = JSONObject().put("time", 42).put("complete", false).put("requestId", "request-1")
            .put("rows", PushMonitorCodec.writeRows(mapOf("com.example.app" to false))).toString()
        val snapshot = PushMonitorCodec.readSnapshot(raw, """{"com.example.app":41}""")
        assertEquals(42L, snapshot.time)
        assertFalse(snapshot.complete)
        assertEquals("request-1", snapshot.requestId)
        assertEquals(PushRegistration.Unknown, PushMonitorRules.registration("com.example.app", snapshot))
        assertEquals(41L, snapshot.times["com.example.app"])
    }
}
