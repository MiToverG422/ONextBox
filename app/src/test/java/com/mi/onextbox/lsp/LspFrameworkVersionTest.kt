package com.mi.onextbox.lsp

import org.junit.Assert.assertEquals
import org.junit.Test

class LspFrameworkVersionTest {
    @Test
    fun includesTheFrameworkVersionBuildAndActualApi() {
        assertEquals("LSPosed 1.10.2-7231 (7231) / API 102",
            formatLspFrameworkVersion("LSPosed", "1.10.2-7231", 7231, 102))
    }

    @Test
    fun preservesTheActualFrameworkNameAndTrimsWhitespace() {
        assertEquals("Vector 2.0 (12000) / API 103",
            formatLspFrameworkVersion(" Vector ", " 2.0 ", 12000, 103))
    }

    @Test
    fun unavailableBuildDoesNotEraseTheVersionOrInventABuildNumber() {
        listOf(null, 0L, -1L).forEach { code ->
            assertEquals("LSPosed 1.10 / API 102",
                formatLspFrameworkVersion("LSPosed", "1.10", code, 102))
        }
    }

    @Test
    fun emptyFieldsDoNotLeaveExtraSpaces() {
        assertEquals("LSPosed (123) / API 102", formatLspFrameworkVersion(" ", " ", 123, 102))
    }
}
