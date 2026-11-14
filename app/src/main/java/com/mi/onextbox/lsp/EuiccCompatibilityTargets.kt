package com.mi.onextbox.lsp

import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Resolve LPA roles from their call contracts instead of version-specific SDK names. */
internal object EuiccCompatibilityTargets {
    class Confirmation(val values: Array<*>) {
        val downloads = (values[0] as Array<*>).filterIsInstance<Method>()
        val callbacks = (values[1] as Array<*>).filterIsInstance<Method>()
        val agent = values[2] as Method
    }

    class Diagnostics(val values: Array<*>) {
        val factory = values[0] as Method
        val provider = values[1] as Method
        val limit = values[2] as Method
        val manager = values[3] as Method
        val registry = values[4] as Method
        val profiles = values[5] as Method
        val info = values[6] as Method
        val addresses = values[7] as Method
        val infoModel = values[8] as Class<*>
        val addressModel = values[9] as Class<*>
        val infoCallback get() = info.parameterTypes.single()
        val addressCallback get() = addresses.parameterTypes.single()
    }

    fun confirmation(loader: ClassLoader, apkPath: String): Confirmation = ApkDexSession.withSession(apkPath) { open ->
        val dex = open()
        val download = dex.findMethod { matcher {
            usingStrings("EsimDownloadViewModel", "downloadProfile")
            returnType = "void"; paramTypes("java.lang.String")
        } }.single { !Modifier.isStatic(it.modifiers) }
        val operation = dex.findMethod { matcher {
            usingStrings("addProfile with data: ")
            returnType = "void"; paramTypes("android.content.Context", "java.lang.String")
        } }.filter { !Modifier.isStatic(it.modifiers) }
        val starts = (listOf(download) + operation).distinctBy { it.descriptor }
        val callbacks = dex.findMethod { matcher {
            usingStrings("onConfirmationCodeRequired")
            returnType = "void"; paramCount = 2
        } }.filter { callback ->
            callback.paramTypeNames.first() == "boolean" &&
                starts.any { callback.className.startsWith(it.className + "$") }
        }.map { it.getMethodInstance(loader).apply { isAccessible = true } }.toTypedArray()
        require(callbacks.isNotEmpty()) { "LPA confirmation response callback unavailable" }
        val agent = Class.forName("com.oplus.euicc.sdk.profileassistant.internal.b", false, loader)
            .declaredMethods.single { method ->
                !Modifier.isStatic(method.modifiers) && method.returnType == java.lang.Long.TYPE &&
                    method.parameterCount == 3 && method.parameterTypes[0] == String::class.java &&
                    method.parameterTypes[1] == String::class.java && method.parameterTypes[2].isInterface
            }.apply { isAccessible = true }
        require(callbacks.all { it.parameterTypes[1].isInterface })
        Confirmation(arrayOf(starts.map { it.getMethodInstance(loader).apply { isAccessible = true } }.toTypedArray(),
            callbacks, agent))
    }

    fun diagnostics(loader: ClassLoader, apkPath: String): Diagnostics = ApkDexSession.withSession(apkPath) { open ->
        val dex = open()
        val factory = dex.findMethod { matcher {
            usingStrings("getProfileAssistant() "); paramTypes()
        } }.single { Modifier.isStatic(it.modifiers) }.getMethodInstance(loader).apply { isAccessible = true }
        val infoModel = dex.findMethod { matcher {
            usingStrings("EuiccInfo{mEuiccPpVersion='"); returnType = "java.lang.String"; paramTypes()
        } }.single().getMethodInstance(loader).declaringClass
        val addressModel = dex.findMethod { matcher {
            usingStrings("SmXxAddress{mSmdpAddress='"); returnType = "java.lang.String"; paramTypes()
        } }.single().getMethodInstance(loader).declaringClass
        fun callbackRequests(type: Class<*>, model: Class<*>) = type.methods.filter { method ->
            method.returnType == java.lang.Void.TYPE && method.parameterCount == 1 &&
                method.parameterTypes[0].isInterface && method.parameterTypes[0].methods.any {
                    it.returnType == java.lang.Void.TYPE && it.parameterTypes.contentEquals(arrayOf(model))
                }
        }
        val manager = factory.returnType.methods.single { method ->
            method.parameterCount == 0 && !Modifier.isStatic(method.modifiers) &&
                callbackRequests(method.returnType, infoModel).size == 1 &&
                callbackRequests(method.returnType, addressModel).size == 1
        }
        val registry = factory.returnType.methods.single { method ->
            method.parameterCount == 0 && !Modifier.isStatic(method.modifiers) && method.returnType.methods.any {
                it.returnType == java.lang.Boolean.TYPE && it.parameterCount == 1 &&
                    List::class.java.isAssignableFrom(it.parameterTypes[0])
            }
        }
        val profiles = registry.returnType.methods.single { method ->
            method.returnType == java.lang.Boolean.TYPE && method.parameterCount == 1 &&
                List::class.java.isAssignableFrom(method.parameterTypes[0])
        }
        val countCheck = dex.findMethod { matcher {
            usingStrings("profileLimitReached Profile count now: "); returnType = "boolean"; paramTypes()
        } }.single()
        val limit = countCheck.invokes.distinctBy { it.descriptor }.single {
            it.returnTypeName == "int" && it.paramCount == 0 && !Modifier.isStatic(it.modifiers)
        }.getMethodInstance(loader).apply { isAccessible = true }
        val provider = limit.declaringClass.declaredMethods.single {
            Modifier.isStatic(it.modifiers) && it.returnType == limit.declaringClass &&
                it.parameterTypes.contentEquals(arrayOf(android.content.Context::class.java))
        }.apply { isAccessible = true }
        Diagnostics(arrayOf(factory, provider, limit, manager, registry, profiles,
            callbackRequests(manager.returnType, infoModel).single(),
            callbackRequests(manager.returnType, addressModel).single(), infoModel, addressModel))
    }
}
