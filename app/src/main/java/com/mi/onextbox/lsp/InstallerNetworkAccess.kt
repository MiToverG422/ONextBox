package com.mi.onextbox.lsp

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.FileDescriptor
import java.net.InetSocketAddress
import java.net.UnknownHostException
import java.lang.reflect.Method
import org.lsposed.hiddenapibypass.HiddenApiBypass

internal object InstallerNetworkAccess {
    fun targets(loader: ClassLoader): Array<Method> {
        val type = loader.loadClass("libcore.io.Linux")
        val operations = setOf("connect", "read", "readv", "write", "writev", "sendto", "recvfrom",
            "sendmsg", "recvmsg", "sendfile", "android_getaddrinfo")
        fun select(methods: List<Method>) = methods.filter { method ->
            method.name in operations && (method.name == "android_getaddrinfo" ||
                method.parameterTypes.firstOrNull() == FileDescriptor::class.java)
        }
        val required = setOf("connect", "read", "write", "android_getaddrinfo")
        var methods = select(type.declaredMethods.toList())
        if (!methods.map { it.name }.containsAll(required)) {
            methods = select(HiddenApiBypass.getDeclaredMethods(type).filterIsInstance<Method>())
        }
        require(methods.map { it.name }.containsAll(required)) { "Missing installer network operations: ${methods.map { it.name }.distinct()}" }
        return methods.toTypedArray()
    }

    fun check(operation: String, descriptor: FileDescriptor?, blocked: Boolean) {
        if (!blocked) return
        if (operation == "android_getaddrinfo") throw UnknownHostException("Installer networking is disabled")
        if (descriptor != null && isInternetSocket(descriptor)) throw ErrnoException(operation, OsConstants.EACCES)
    }

    fun isInternetSocket(descriptor: FileDescriptor): Boolean =
        runCatching { OsConstants.S_ISSOCK(Os.fstat(descriptor).st_mode) &&
            Os.getsockname(descriptor) is InetSocketAddress }.getOrDefault(false)
}
