package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class InstallerRoutingPolicyTest {
    private fun accepts(
        action: String? = InstallerRoutingPolicy.VIEW, type: String? = null, uri: String? = null,
        component: String? = null, uninstall: Boolean = false, session: Boolean = false,
        system: String = "", intercept: Boolean = false, name: String? = null,
    ) = InstallerRoutingPolicy.shouldRedirect(action, type, uri, component, uninstall, session, system, intercept, name)

    @Test fun packageValidation() {
        assertTrue(InstallerRoutingPolicy.validPackage(""))
        assertTrue(InstallerRoutingPolicy.validPackage("com.rosan.installer.x.revived"))
        listOf("some/path", "invalid package", "singleword", "a".repeat(8193)).forEach {
            assertFalse(InstallerRoutingPolicy.validPackage(it))
        }
    }

    @Test fun archiveFormatsMatchPhoneVersion() {
        listOf("app.apk", "APP.APKS", "app.xapk", "app.apkm", "app.apk.1", "app.apk.123").forEach {
            assertTrue(it, accepts(uri = "content://provider/$it"))
        }
        listOf("app.zip", "app.apk.exe", "app.apk.foo", "apk", "").forEach {
            assertFalse(it, InstallerRoutingPolicy.isArchiveName(it))
        }
    }

    @Test fun opaqueContentNamesAndMimeTypes() {
        assertTrue(accepts(type = InstallerRoutingPolicy.APK_TYPE, uri = "content://provider/123"))
        assertTrue(accepts(uri = "content://provider/123", name = "app.apk"))
        assertTrue(accepts(uri = "content://provider/APP.APK?token=1#part"))
        assertFalse(accepts(uri = "content://provider/123", name = "file.pdf"))
        assertFalse(accepts(uri = "https://example.com/app.apk", name = "app.apk"))
        assertFalse(accepts(type = InstallerRoutingPolicy.APK_TYPE, uri = "https://example.com/app.apk"))
    }

    @Test fun unrelatedActionsStayUntouched() {
        assertFalse(accepts(action = null, type = InstallerRoutingPolicy.APK_TYPE))
        assertFalse(accepts(action = "android.intent.action.SEND", type = InstallerRoutingPolicy.APK_TYPE))
        assertFalse(accepts(action = "android.content.pm.action.CONFIRM_PERMISSIONS", session = true))
        assertFalse(accepts(type = "application/pdf", uri = "content://documents/1.pdf"))
        assertTrue(accepts(action = InstallerRoutingPolicy.INSTALL))
    }

    @Test fun explicitTargetsRequireDetectedSystemInstallerAndOptIn() {
        assertFalse(accepts(type = InstallerRoutingPolicy.APK_TYPE, component = "official.installer"))
        assertFalse(accepts(type = InstallerRoutingPolicy.APK_TYPE, component = "official.installer", intercept = true))
        assertFalse(accepts(type = InstallerRoutingPolicy.APK_TYPE, component = "official.installer", system = "official.installer"))
        assertTrue(accepts(type = InstallerRoutingPolicy.APK_TYPE, component = "official.installer", system = "official.installer", intercept = true))
        assertFalse(accepts(type = InstallerRoutingPolicy.APK_TYPE, component = "third.party", system = "official.installer", intercept = true))
        assertFalse(accepts(uri = "content://provider/file.pdf", component = "official.installer", system = "official.installer", intercept = true))
    }

    @Test fun uninstallRequiresItsSwitchAndPackageUri() {
        for (action in listOf(InstallerRoutingPolicy.DELETE, InstallerRoutingPolicy.UNINSTALL)) {
            assertFalse(accepts(action = action, uri = "package:example.app"))
            assertTrue(accepts(action = action, uri = "package:example.app", uninstall = true))
            assertFalse(accepts(action = action, uri = "content://provider/1", uninstall = true))
        }
    }

    @Test fun sessionRequiresItsSwitchAndHonorsExplicitTarget() {
        assertFalse(accepts(action = InstallerRoutingPolicy.CONFIRM_INSTALL))
        assertTrue(accepts(action = InstallerRoutingPolicy.CONFIRM_INSTALL, session = true))
        assertFalse(accepts(action = InstallerRoutingPolicy.CONFIRM_INSTALL, session = true, component = "third.party"))
        assertTrue(accepts(action = InstallerRoutingPolicy.CONFIRM_INSTALL, session = true,
            component = "official.installer", system = "official.installer", intercept = true))
    }
}
