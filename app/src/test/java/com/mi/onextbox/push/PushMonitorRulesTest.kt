package com.mi.onextbox.push

import org.junit.Assert.*
import org.junit.Test

class PushMonitorRulesTest {
    @Test fun registrationStartsUnknown() {
        assertEquals(PushRegistration.Unknown, PushMonitorRules.registration("com.example.app", PushSnapshot()))
    }

    @Test fun incompleteSnapshotNeverClaimsUnregistered() {
        val snapshot = PushSnapshot(time = 1, rows = mapOf("com.example.app" to false))
        assertEquals(PushRegistration.Unknown, PushMonitorRules.registration("com.example.app", snapshot))
        assertEquals(PushRegistration.Unknown, PushMonitorRules.registration("com.example.other", snapshot))
    }

    @Test fun knownRegistrationSurvivesPartialRead() {
        assertEquals(PushRegistration.Registered, PushMonitorRules.registration("com.example.app",
            PushSnapshot(time = 1, rows = mapOf("com.example.app" to true))))
    }

    @Test fun unregisteredRequiresCompleteSnapshot() {
        assertEquals(PushRegistration.Unregistered, PushMonitorRules.registration("com.example.app",
            PushSnapshot(time = 1, complete = true)))
        assertEquals(PushRegistration.Unknown, PushMonitorRules.registration("com.example.app",
            PushSnapshot(complete = true)))
    }

    @Test fun mcsIsAServiceNotARegisteredApp() {
        assertEquals(PushRegistration.SystemService, PushMonitorRules.registration(MCS_PACKAGE, PushSnapshot()))
    }

    @Test fun snapshotsWhitelistFieldsAndMergeDuplicates() {
        val input = listOf(
            mapOf("package" to "com.example.app", "registered" to true, "token" to "secret", "message" to "private"),
            mapOf("package" to "com.example.app", "registered" to false),
        )
        assertEquals(mapOf("com.example.app" to true), PushMonitorRules.sanitizeRows(input))
        assertFalse(PushMonitorRules.sanitizeRows(input).toString().contains("secret"))
        assertFalse(PushMonitorRules.sanitizeRows(input).toString().contains("private"))
    }

    @Test fun invalidPackagesAndCoercedBooleansAreRejected() {
        listOf("", "../app", "com.app\n", "com.app token", "a." + "b".repeat(254)).forEach { pkg ->
            assertFalse(pkg, PushMonitorRules.validPackage(pkg))
            assertThrows(IllegalArgumentException::class.java) {
                PushMonitorRules.sanitizeRows(listOf(mapOf("package" to pkg, "registered" to true)))
            }
        }
        assertThrows(IllegalStateException::class.java) {
            PushMonitorRules.sanitizeRows(listOf(mapOf("package" to "com.example.app", "registered" to "true")))
        }
    }

    @Test fun oversizedSnapshotsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            PushMonitorRules.sanitizeRows(List(PushMonitorRules.MAX_APPS + 1) {
                mapOf("package" to "com.example.app", "registered" to false)
            })
        }
    }

    @Test fun readAccessIsAppPrivate() {
        assertTrue(PushMonitorRules.canRead(12345, 12345))
        assertFalse(PushMonitorRules.canRead(1000, 12345))
        assertFalse(PushMonitorRules.canRead(23456, 12345))
    }

    @Test fun writeAccessNeedsMcsUidInSameUser() {
        assertTrue(PushMonitorRules.canWrite(1000, 12345, 1000))
        assertFalse(PushMonitorRules.canWrite(23456, 12345, 1000))
        assertFalse(PushMonitorRules.canWrite(12345, 12345, 1000))
        assertFalse(PushMonitorRules.canWrite(1000, 12345, null))
        assertFalse(PushMonitorRules.canWrite(101000, 12345, 101000))
        assertTrue(PushMonitorRules.canWrite(101000, 112345, 101000))
    }

    @Test fun oldInvalidAndFuturePushTimesArePruned() {
        val now = PushMonitorRules.RETENTION_MS + 100
        assertEquals(mapOf("com.example.recent" to now - 1, "com.example.new" to now),
            PushMonitorRules.recordPushes(mapOf(
                "com.example.old" to 1, "com.example.recent" to now - 1,
                "com.example.future" to now + 1, "bad package" to now,
            ), listOf("com.example.new"), now))
    }

    @Test fun pushStorageIsBoundedAndKeepsNewest() {
        val existing = (1..PushMonitorRules.MAX_APPS).associate { "com.example.app$it" to it.toLong() }
        val times = PushMonitorRules.recordPushes(existing, listOf("com.example.new"), 5000)
        assertEquals(PushMonitorRules.MAX_APPS, times.size)
        assertEquals(5000L, times["com.example.new"])
        assertFalse(times.containsKey("com.example.app1"))
    }

    @Test fun searchAndFiltersKeepUnknownOutOfUnregistered() {
        val apps = listOf(
            PushApp("com.example.cool", "Cool app", PushRegistration.Registered),
            PushApp("com.example.none", "Not registered", PushRegistration.Unregistered),
            PushApp("com.example.unknown", "Unknown", PushRegistration.Unknown),
            PushApp(MCS_PACKAGE, "System messages", PushRegistration.SystemService),
        )
        assertEquals(MCS_PACKAGE, PushMonitorRules.shown(apps, "", PushFilter.All).first().packageName)
        assertEquals(listOf(apps[0]), PushMonitorRules.shown(apps, " cool ", PushFilter.Registered))
        assertEquals(listOf(apps[1]), PushMonitorRules.shown(apps, "COM.EXAMPLE", PushFilter.Unregistered))
        assertTrue(PushMonitorRules.shown(apps, "unknown", PushFilter.Unregistered).isEmpty())
    }

    @Test fun registeredAppsSortNewestPushFirstAndMissingHistoryLast() {
        val apps = listOf(
            PushApp("com.example.none", "A no history", PushRegistration.Registered),
            PushApp("com.example.old", "B older", PushRegistration.Registered, 100),
            PushApp("com.example.recent", "Z recent", PushRegistration.Registered, 200),
        )
        assertEquals(listOf(apps[2], apps[1], apps[0]),
            PushMonitorRules.shown(apps, "", PushFilter.All))
    }

    @Test fun registrationGroupsStayOrderedEvenWithOldUnregisteredPushHistory() {
        val apps = listOf(
            PushApp("com.example.unregistered", "A unregistered", PushRegistration.Unregistered, 300),
            PushApp("com.example.unknown", "B unknown", PushRegistration.Unknown, 200),
            PushApp("com.example.registered", "Z registered", PushRegistration.Registered),
            PushApp(MCS_PACKAGE, "System messages", PushRegistration.SystemService),
        )
        assertEquals(listOf(apps[3], apps[2], apps[1], apps[0]),
            PushMonitorRules.shown(apps, "", PushFilter.All))
    }

    @Test fun equalPushTimesUseCaseInsensitiveNamesThenPackages() {
        val apps = listOf(
            PushApp("com.example.z", "same", PushRegistration.Registered, 100),
            PushApp("com.example.a", "Same", PushRegistration.Registered, 100),
            PushApp("com.example.b", "Before", PushRegistration.Registered, 100),
        )
        val expected = listOf(apps[2], apps[1], apps[0])
        assertEquals(expected, PushMonitorRules.shown(apps, "", PushFilter.All))
        assertEquals(expected, PushMonitorRules.shown(apps.reversed(), "", PushFilter.All))
    }

    @Test fun filtersAndSearchKeepRecentPushOrdering() {
        val apps = listOf(
            PushApp("com.example.registeredOld", "Target older", PushRegistration.Registered, 100),
            PushApp("com.example.unregisteredOld", "Target older", PushRegistration.Unregistered, 50),
            PushApp("com.example.registeredNew", "Target recent", PushRegistration.Registered, 200),
            PushApp("com.example.unregisteredNew", "Target recent", PushRegistration.Unregistered, 150),
            PushApp("com.example.other", "Other", PushRegistration.Registered, 300),
        )
        assertEquals(listOf(apps[2], apps[0]),
            PushMonitorRules.shown(apps, "target", PushFilter.Registered))
        assertEquals(listOf(apps[3], apps[1]),
            PushMonitorRules.shown(apps, "target", PushFilter.Unregistered))
        assertEquals(listOf(apps[2], apps[0], apps[3], apps[1]),
            PushMonitorRules.shown(apps, "target", PushFilter.All))
    }

    @Test fun nonPositivePushTimesBehaveLikeMissingHistory() {
        val apps = listOf(
            PushApp("com.example.zero", "Z zero", PushRegistration.Registered),
            PushApp("com.example.negative", "A negative", PushRegistration.Registered, Long.MIN_VALUE),
            PushApp("com.example.positive", "B positive", PushRegistration.Registered, 1),
        )
        assertEquals(listOf(apps[2], apps[1], apps[0]),
            PushMonitorRules.shown(apps, "", PushFilter.All))
    }

    @Test fun longPackageNamesCannotOverflowBinderPayloads() {
        val entries = (1..PushMonitorRules.MAX_APPS).associate {
            "com." + "a".repeat(240) + it to true
        }
        val rows = PushMonitorRules.boundedRows(entries)
        assertTrue(rows.size < entries.size)
        assertTrue(rows.keys.sumOf { it.length + 36 } + 2 <= PushMonitorRules.MAX_WIRE_CHARS - 1024)
        val times = PushMonitorRules.recordPushes(emptyMap(), entries.keys.toList(), 100)
        assertTrue(times.size < entries.size)
        assertTrue(times.entries.sumOf { it.key.length + it.value.toString().length + 4 } + 2 <= PushMonitorRules.MAX_WIRE_CHARS)
    }
}
