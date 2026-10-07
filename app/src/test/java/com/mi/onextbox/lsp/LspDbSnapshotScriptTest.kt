package com.mi.onextbox.lsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LspDbSnapshotScriptTest {
    private val prefix = "/system/bin/toybox timeout 5 /system/bin/sh -c "
    private val source = "/data/adb/lspd/config/modules_config.db"
    private val target = "/data/user/0/com.mi.onextbox/cache/lsp_state_123.db"
    private val dollar = '$'

    @Test
    fun theProbeRunsInAnIsolatedBoundedSubprocessWithoutNestedSu() {
        val command = LspDbSnapshotScript.command(source, target, 10123)
        assertTrue(command.startsWith(prefix))
        assertFalse(Regex("(^|\\s)su(\\s|$)").containsMatchIn(command))
        val script = script(command)
        assertTrue(script.startsWith("set -eu\n"))
        assertTrue(script.contains("for attempt in 1 2; do"))
        assertFalse(script.contains("while "))
    }

    @Test
    fun databaseAndWalMustMatchBeforeCopiedAndAfterFingerprints() {
        val script = script(LspDbSnapshotScript.command(source, target, 10123))
        assertTrue(script.contains("for suffix in '' '-wal'; do"))
        assertTrue(script.contains("printf '%s:absent\\n'"))
        val before = script.indexOf("before=${dollar}(fingerprint" )
        val copy = script.indexOf("/system/bin/toybox cp \"${dollar}{src}\" \"${dollar}{dst}\"")
        val copied = script.indexOf("copied=${dollar}(fingerprint")
        val after = script.indexOf("after=${dollar}(fingerprint")
        val match = script.indexOf("if [ \"${dollar}{before}\" = \"${dollar}{copied}\" ] && [ \"${dollar}{before}\" = \"${dollar}{after}\" ]")
        val ownership = script.indexOf("/system/bin/toybox chown")
        assertTrue(before >= 0 && before < copy && copy < copied && copied < after && after < match)
        assertTrue(match < ownership)
    }

    @Test
    fun changingSharedMemoryIsNotPartOfCommittedStateAndIsNotCopied() {
        val script = script(LspDbSnapshotScript.command(source, target, 10123))
        assertFalse(script.contains("-shm"))
        assertTrue(script.contains("[ ! -s \"${dollar}{base}-journal\" ] || return 1"))
    }

    @Test
    fun copyAndPermissionFailuresCannotFallThroughToSuccess() {
        val script = script(LspDbSnapshotScript.command(source, target, 10123))
        script.lineSequence().filter { line ->
            line.contains("/system/bin/toybox cp ") || line.contains("/system/bin/toybox chown ") ||
                line.contains("/system/bin/toybox chmod ") || line.contains("/system/bin/toybox rm ")
        }.forEach { assertTrue(it.trimEnd().endsWith("|| exit 1")) }
        assertTrue(script.contains("chown 10123:10123"))
        assertTrue(script.contains("chmod 600"))
        assertTrue(script.trimEnd().endsWith("exit 1"))
    }

    @Test
    fun aMissingWalIsRemovedFromThePrivateCopyBeforeVerification() {
        val script = script(LspDbSnapshotScript.command(source, target, 10123))
        assertTrue(script.contains("if [ -f \"${dollar}{src}-wal\" ]; then"))
        assertTrue(script.contains("/system/bin/toybox rm -f \"${dollar}{dst}-wal\" || exit 1"))
        assertFalse(script.contains("rm -f \"${dollar}{src}"))
        assertFalse(script.contains("chmod 600 \"${dollar}{src}"))
    }

    @Test
    fun sourceAbsenceIsDistinctFromPermissionAndConsistencyFailures() {
        val script = script(LspDbSnapshotScript.command(source, target, 10123))
        assertTrue(script.contains("if [ ! -f \"${dollar}{src}\" ]; then printf 'missing\\n'; exit 1; fi"))
        assertEquals(1, Regex("printf 'missing").findAll(script).count())
    }

    @Test
    fun pathsWithQuotesAndShellExpressionsStayQuotedData() {
        val unusual = "/cache/it's ${dollar}(id); file.db"
        val script = script(LspDbSnapshotScript.command(source, unusual, 10123))
        val quoted = "'" + unusual.replace("'", "'\"'\"'") + "'"
        assertTrue(script.contains("dst=$quoted\n"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun aNegativeUidIsRejected() {
        LspDbSnapshotScript.command(source, target, -1)
    }

    private fun script(command: String): String {
        assertTrue(command.startsWith(prefix))
        return command.removePrefix(prefix).removeSurrounding("'").replace("'\"'\"'", "'")
    }
}
