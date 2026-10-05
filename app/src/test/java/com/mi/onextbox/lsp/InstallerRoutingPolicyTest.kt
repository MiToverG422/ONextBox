package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class InstallerRoutingPolicyTest {
    @Test fun customPackagesAreValidatedBeforeSaving() {
        assertTrue(InstallerRoutingPolicy.validPackage(""))
        assertTrue(InstallerRoutingPolicy.validPackage("bin.mt.plus.canary"))
        assertFalse(InstallerRoutingPolicy.validPackage("some/path"))
        assertFalse(InstallerRoutingPolicy.validPackage("invalid package"))
        assertFalse(InstallerRoutingPolicy.validPackage("singleword"))
    }
    private fun accepts(action: String?, type: String? = null, uri: String? = null,
                        component: String? = null,
                        uninstall: Boolean = false, session: Boolean = false) =
        InstallerRoutingPolicy.shouldRedirect(action, type, uri, component, uninstall, session)

    @Test fun onlyInstallationRequestsAreRouted() {
        assertTrue(accepts(InstallerRoutingPolicy.VIEW, InstallerRoutingPolicy.APK_TYPE, "content://provider/123"))
        assertTrue(accepts(InstallerRoutingPolicy.VIEW, uri = "content://provider/TEST.APK?token=1"))
        assertTrue(accepts(InstallerRoutingPolicy.INSTALL))
        assertFalse(accepts(InstallerRoutingPolicy.VIEW, "application/pdf", "content://provider/1.pdf"))
        assertFalse(accepts("android.intent.action.SEND", InstallerRoutingPolicy.APK_TYPE))
        assertFalse(accepts(InstallerRoutingPolicy.VIEW, uri = "https://example.com/app.apk"))
    }

    @Test fun optionalInterceptionAndExplicitComponentsAreHonored() {
        assertFalse(accepts(InstallerRoutingPolicy.DELETE, uri = "package:example.app"))
        assertTrue(accepts(InstallerRoutingPolicy.UNINSTALL, uri = "package:example.app", uninstall = true))
        assertFalse(accepts(InstallerRoutingPolicy.DELETE, uri = "content://documents/1", uninstall = true))
        assertFalse(accepts(InstallerRoutingPolicy.CONFIRM_INSTALL))
        assertTrue(accepts(InstallerRoutingPolicy.CONFIRM_INSTALL, session = true))
        assertFalse(accepts(InstallerRoutingPolicy.VIEW, InstallerRoutingPolicy.APK_TYPE, component = "official.installer"))
        assertFalse(accepts(InstallerRoutingPolicy.CONFIRM_INSTALL,
            component = "official.installer", session = true))
    }

    @Test fun uninstallTargetFollowsOnlyWhenRequested() {
        assertEquals("install.app", InstallerRoutingPolicy.target(InstallerRoutingPolicy.DELETE, "install.app", "uninstall.app", true))
        assertEquals("uninstall.app", InstallerRoutingPolicy.target(InstallerRoutingPolicy.DELETE, "install.app", "uninstall.app", false))
        assertEquals("install.app", InstallerRoutingPolicy.target(InstallerRoutingPolicy.CONFIRM_PERMISSIONS, "install.app", "uninstall.app", false))
    }
}
