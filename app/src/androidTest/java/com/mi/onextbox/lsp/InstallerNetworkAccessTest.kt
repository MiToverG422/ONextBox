package com.mi.onextbox.lsp

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import java.net.UnknownHostException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstallerNetworkAccessTest {
    @Test fun resolvesTheCurrentPlatformsNetworkOperations() {
        val methods = InstallerNetworkAccess.targets(ClassLoader.getSystemClassLoader())
        assertTrue(methods.map { it.name }.containsAll(setOf("connect", "read", "write", "android_getaddrinfo")))
    }

    @Test fun blocksIpv4Ipv6AndUdpWithoutOpeningAConnection() {
        listOf(OsConstants.AF_INET to OsConstants.SOCK_STREAM, OsConstants.AF_INET6 to OsConstants.SOCK_STREAM,
            OsConstants.AF_INET to OsConstants.SOCK_DGRAM).forEach { (family, type) ->
            val socket = Os.socket(family, type, 0)
            try {
                assertTrue(InstallerNetworkAccess.isInternetSocket(socket))
                assertThrows(ErrnoException::class.java) { InstallerNetworkAccess.check("write", socket, true) }
                InstallerNetworkAccess.check("write", socket, false)
            } finally { Os.close(socket) }
        }
    }

    @Test fun preservesLocalIpcAndApkFileIo() {
        val socket = Os.socket(OsConstants.AF_UNIX, OsConstants.SOCK_STREAM, 0)
        try {
            assertFalse(InstallerNetworkAccess.isInternetSocket(socket))
            InstallerNetworkAccess.check("write", socket, true)
        } finally { Os.close(socket) }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("installer-network-", ".tmp", context.cacheDir)
        try {
            FileOutputStream(file).use {
                assertFalse(InstallerNetworkAccess.isInternetSocket(it.fd))
                InstallerNetworkAccess.check("write", it.fd, true)
                it.write(1)
            }
            assertEquals(1L, file.length())
        } finally { file.delete() }
    }

    @Test fun disablesDnsOnlyWhenTheSwitchIsEnabled() {
        assertThrows(UnknownHostException::class.java) { InstallerNetworkAccess.check("android_getaddrinfo", null, true) }
        InstallerNetworkAccess.check("android_getaddrinfo", null, false)
    }
}
