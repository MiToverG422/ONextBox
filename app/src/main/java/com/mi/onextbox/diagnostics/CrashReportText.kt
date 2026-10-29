package com.mi.onextbox.diagnostics

import java.io.PrintWriter
import java.io.StringWriter
import java.security.MessageDigest

internal object CrashReportText {
    fun stack(error: Throwable): String =
        StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()

    fun fingerprint(stack: String): String = MessageDigest.getInstance("SHA-256")
        .digest(stack.toByteArray(Charsets.UTF_8)).take(8)
        .joinToString("") { "%02x".format(it) }
}
