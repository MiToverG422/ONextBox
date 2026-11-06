package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class StdspInstallRulesTest {
    @Test fun onlyAllowsEnabledValidStdspVerificationRequests() {
        val action = "android.intent.action.PACKAGE_NEEDS_VERIFICATION"
        assertTrue(StdspInstallRules.mayAllow(true, "com.oplus.stdsp", action, 12, true))
        assertFalse(StdspInstallRules.mayAllow(false, "com.oplus.stdsp", action, 12, true))
        assertFalse(StdspInstallRules.mayAllow(true, "com.google.android.packageinstaller", action, 12, true))
        assertFalse(StdspInstallRules.mayAllow(true, "com.oplus.stdsp", "android.intent.action.PACKAGE_ADDED", 12, true))
        assertFalse(StdspInstallRules.mayAllow(true, "com.oplus.stdsp", action, -1, true))
        assertFalse(StdspInstallRules.mayAllow(true, "com.oplus.stdsp", action, 12, false))
    }
}
