package com.mi.onextbox.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class TouchOverrideProtocolTest {
    @Test fun shellScriptHasValidSyntax() {
        val process = ProcessBuilder(shell(), "-n", asset().absolutePath).redirectErrorStream(true).start()
        assertTrue(process.waitFor(5, TimeUnit.SECONDS))
        assertEquals(process.inputStream.bufferedReader().readText(), 0, process.exitValue())
    }

    @Test fun shellScriptHasPortableShSyntax() {
        val shell = File("/bin/sh")
        assumeTrue("A POSIX shell is required", shell.isFile)
        val process = ProcessBuilder(shell.absolutePath, "-n", asset().absolutePath).redirectErrorStream(true).start()
        assertTrue(process.waitFor(5, TimeUnit.SECONDS))
        assertEquals(process.inputStream.bufferedReader().readText(), 0, process.exitValue())
    }

    @Test fun readsKnownCurrentMode() {
        assertEquals("5,10", decode(parcel("5, 10")).second)
        assertEquals("0,2147483647", decode(parcel("0, 2147483647")).second)
    }

    @Test fun ignoresAddressesAndAsciiColumns() {
        val reply = "Result: Parcel(\n" +
            "0x00000000: 00000000 00000005 002c0035 00310020 '12345678'\n" +
            "0x00000010: 00000030 '00000001')"
        assertEquals(0 to "5,10", decode(reply))
    }

    @Test fun rejectsExceptionsTruncationAndBadUtf16() {
        for (reply in listOf(
            parcel("5, 10").replaceFirst("00000000", "fffffffc"),
            "Result: Parcel(00000000 00000005 002c0035 00310020)",
            "Result: Parcel(00000000 00000005 002c0035 00310020 00410030)",
            "Result: Parcel(00000000 00000005 002c0035 00310020 00000030 00000000)",
            "Result: Parcel(00000000 ffffffff)",
            "Can't find service: touch",
        )) assertFalse(reply, decode(reply).first == 0)
    }

    @Test fun rejectsMalformedOrOutOfRangeModes() {
        for (text in listOf("1 2, 3", "1,2,3", "-1, 2", "256, 4", "1, 2147483648", "01, 2", "1, 02")) {
            assertFalse(text, decode(parcel(text)).first == 0)
        }
    }

    @Test fun onlyAcceptsExactSupportedParcel() {
        assertEquals(0, runDecoder("supported", "Result: Parcel(00000000 00000001 '........')").first)
        for (reply in listOf(
            "Result: Parcel(00000000 00000002)",
            "Result: Parcel(fffffffc 00000001)",
            "Result: Parcel(00000000 00000001 00000000)",
            "Result: Parcel(00000000 invalid 00000001)",
        )) assertFalse(reply, runDecoder("supported", reply).first == 0)
    }

    private fun decode(reply: String): Pair<Int, String> = runDecoder("decode_mode", reply)

    private fun runDecoder(function: String, reply: String): Pair<Int, String> {
        val decoder = asset().readText().replace("\r\n", "\n").substringAfter("# Parcel decoder\n")
            .substringBefore("# Daemon binding\n").replace("/system/bin/toybox sed", "sed")
        val process = ProcessBuilder(shell(), "--noprofile", "--norc", "-s")
            .redirectErrorStream(true).start()
        val literal = "'" + reply.replace("'", "'\\''") + "'"
        val program = "set -f\n$decoder\nreply=$literal\n$function \"\$reply\"\n"
        process.outputStream.use { it.write(program.toByteArray(Charsets.UTF_8)) }
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            error("Parcel decoder timed out")
        }
        return process.exitValue() to process.inputStream.bufferedReader().readText().trim()
    }

    private fun shell(): String {
        val local = File("C:/Program Files/Git/bin/bash.exe")
        if (local.isFile) return local.absolutePath
        val system = listOf(File("/bin/bash"), File("/usr/bin/bash")).firstOrNull(File::isFile)
        assumeTrue("Bash is required for shell protocol tests", system != null)
        return requireNotNull(system).absolutePath
    }

    private fun asset(): File = listOf(
        File("src/main/assets/touch_rate_override.sh"), File("app/src/main/assets/touch_rate_override.sh"),
    ).first(File::isFile)

    private fun parcel(text: String): String {
        val raw = (text + '\u0000').toByteArray(Charsets.UTF_16LE)
        val padded = raw.copyOf((raw.size + 3) / 4 * 4)
        val words = padded.asList().chunked(4).map { bytes ->
            bytes.withIndex().fold(0L) { word, (index, byte) ->
                word or ((byte.toLong() and 0xff) shl (index * 8))
            }.toString(16).padStart(8, '0')
        }
        return "Result: Parcel(00000000 ${text.length.toString(16).padStart(8, '0')} ${words.joinToString(" ")})"
    }
}
