package com.mi.onextbox.ui.common

import com.mi.onextbox.lsp.LspStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class LspHomeDisplayCodecTest {
    @Test
    fun readyKeepsLastFrameworkVersion() {
        assertEquals(
            LspHomeDisplay(LspStatus.READY, "LSPosed 1.10.2 / API 102"),
            decode("READY", "  LSPosed 1.10.2 / API 102  "),
        )
    }

    @Test
    fun missingScopeKeepsLastDetails() {
        val missing = setOf("system", "com.android.systemui")
        assertEquals(
            LspHomeDisplay(LspStatus.MISSING_SCOPE, "Vector / API 102", missing),
            decode("MISSING_SCOPE", "Vector / API 102", missing),
        )
    }

    @Test
    fun everyNonCheckingStatusSurvivesNameRoundTrip() {
        LspStatus.entries.filter { it != LspStatus.CHECKING }.forEach { status ->
            val display = decode(status.name, "LSPosed / API 102", setOf("com.android.settings"))
            assertNotNull(display)
            assertEquals(status, display?.status)
            assertEquals("LSPosed / API 102", display?.frameworkVersionText)
            assertEquals(setOf("com.android.settings"), display?.missingScopes)
        }
    }

    @Test
    fun checkingIsNeverCached() {
        assertNull(decode("CHECKING", "Old version", setOf("system")))
    }

    @Test
    fun unsupportedSchemaIsRejected() {
        listOf(-1, 0, 2, Int.MAX_VALUE).forEach { schema ->
            assertNull(LspHomeDisplayCodec.decode(schema, "READY", null, emptySet()))
        }
    }

    @Test
    fun malformedStatusIsRejected() {
        listOf(null, "", "ready", "READY ", "NOT_A_STATUS", "x".repeat(33)).forEach { status ->
            assertNull(decode(status))
        }
    }

    @Test
    fun absentOrBlankVersionIsOptional() {
        listOf(null, "", "   ").forEach { version ->
            assertEquals(LspHomeDisplay(LspStatus.UNKNOWN), decode("UNKNOWN", version))
        }
    }

    @Test
    fun excessiveOrControlCharacterVersionIsRejected() {
        listOf("x".repeat(LspHomeDisplayCodec.MAX_VERSION_LENGTH + 1), "Version\nnext", "Version\u0000")
            .forEach { version -> assertNull(decode("READY", version)) }
    }

    @Test
    fun malformedScopeNamesAreRejected() {
        listOf("", "com.example app", "com.example\napp", ".com.example", "com.example/child", "应用")
            .forEach { scope -> assertNull(decode("MISSING_SCOPE", missingScopes = setOf(scope))) }
    }

    @Test
    fun scopeSizeLimitsAreEnforced() {
        assertNull(decode("MISSING_SCOPE", missingScopes = setOf("x".repeat(LspHomeDisplayCodec.MAX_SCOPE_LENGTH + 1))))
        val tooMany = (0..LspHomeDisplayCodec.MAX_SCOPES).map { "com.example.app$it" }.toSet()
        assertNull(decode("MISSING_SCOPE", missingScopes = tooMany))
    }

    @Test
    fun valuesAtTheLimitsRemainValid() {
        val scopes = (0 until LspHomeDisplayCodec.MAX_SCOPES).map { "com.example.app$it" }.toSet()
        assertNotNull(decode("READY", "x".repeat(LspHomeDisplayCodec.MAX_VERSION_LENGTH), scopes))
        assertNotNull(decode("MISSING_SCOPE", missingScopes = setOf("x".repeat(LspHomeDisplayCodec.MAX_SCOPE_LENGTH))))
    }

    @Test
    fun decodedScopesAreAnImmutableIndependentCopy() {
        val original = linkedSetOf("system", "com.android.systemui")
        val display = requireNotNull(decode("MISSING_SCOPE", missingScopes = original))
        original.clear()
        assertEquals(setOf("system", "com.android.systemui"), display.missingScopes)
        assertThrows(UnsupportedOperationException::class.java) {
            (display.missingScopes as MutableSet<String>).add("com.example.other")
        }
    }

    private fun decode(
        status: String?,
        version: String? = null,
        missingScopes: Set<String> = emptySet(),
    ): LspHomeDisplay? = LspHomeDisplayCodec.decode(
        LspHomeDisplayCodec.SCHEMA_VERSION, status, version, missingScopes,
    )
}
