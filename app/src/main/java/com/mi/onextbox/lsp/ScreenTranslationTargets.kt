package com.mi.onextbox.lsp

import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object ScreenTranslationTargets {
    const val SCHEMA = "translation-restrictions:v1"

    fun resolveAll(dex: DexKitBridge, loader: ClassLoader): Map<String, Array<Method>> {
        val groups = linkedMapOf(SCHEMA to emptyArray<Method>())
        fun group(name: String, resolve: () -> Array<Method>) {
            runCatching(resolve).onSuccess { groups[name] = it }
                .onFailure { HookLog.w("ONextBox-Translation", "$name targets unavailable; native behavior retained", it) }
        }
        group("floating") { resolve(dex, loader) }
        group("blacklist") {
            val check = dex.findMethod {
                matcher {
                    usingStrings("Top Component Name is empty!", "isContainsInAppBlackList exception ")
                    returnType = "boolean"; paramTypes()
                }
            }.single()
            require(check.className.startsWith("com.coloros.translate."))
            require(!Modifier.isStatic(check.getMethodInstance(loader).modifiers))
            methods(listOf(check), loader)
        }
        group("pages") {
            val guard = dex.findMethod {
                matcher { usingStrings("screen not support full"); returnType = "boolean"; paramTypes() }
            }.single()
            val check = guard.invokes.distinctBy { it.descriptor }.single {
                it.className == "com.coloros.translate.screen.ScreenTranslationService" &&
                    it.returnTypeName == "boolean" && it.paramCount == 0
            }
            require(Modifier.isStatic(check.getMethodInstance(loader).modifiers))
            methods(listOf(check), loader)
        }
        group("landscape") {
            val service = dex.findMethod {
                matcher {
                    usingStrings("onCreate, landscape status, stop self")
                    returnType = "void"; paramTypes("android.content.Intent")
                }
            }.single()
            val full = dex.findMethod {
                matcher { usingStrings("startFullScreenTranslation, landscape , return"); returnType = "void" }
            }.single()
            require(service.className == "com.coloros.translate.screen.ScreenTranslationService")
            require(full.className.startsWith("com.coloros.translate.screen."))
            val fullInvokes = full.invokes.map { it.descriptor }.toSet()
            val support = service.invokes.distinctBy { it.descriptor }.single {
                it.descriptor in fullInvokes && it.returnTypeName == "boolean" &&
                    it.paramTypeNames == listOf("android.content.Context") &&
                    it.invokes.any { call -> call.className == "com.oplus.os.OplusBuild" && call.methodName == "getOplusOSVERSION" }
            }
            require(Modifier.isStatic(support.getMethodInstance(loader).modifiers))
            methods(listOf(support, service, full), loader)
        }
        group("browser") {
            val mapping = dex.findMethod {
                matcher {
                    usingStrings(" can not find version list")
                    returnType = "java.lang.String"; paramTypes("java.lang.String", "long")
                }
            }.single()
            require(mapping.className.startsWith("com.coloros.translate.screen."))
            val allow = dex.findMethod {
                matcher { declaredClass = mapping.className; returnType = "boolean"; paramTypes("java.lang.String") }
            }.single()
            require(listOf(allow, mapping).all { Modifier.isStatic(it.getMethodInstance(loader).modifiers) })
            methods(listOf(allow, mapping), loader)
        }
        return groups
    }

    fun resolve(dex: DexKitBridge, loader: ClassLoader): Array<Method> {
        val data = dex.findMethod {
            matcher {
                usingStrings("this is zoom window")
                returnType = "boolean"
                paramTypes(emptyList<String>())
            }
        }.single()
        require(data.className.startsWith("com.coloros.translate.screen."))
        require(data.invokes.any { it.className == "android.app.OplusActivityManager" && it.methodName == "getAllTopAppInfos" })
        val detector = data.getMethodInstance(loader)
        require(ScreenTranslationRules.isFloatingDetector(detector.returnType.name,
            detector.parameterTypes.map { it.name }, Modifier.isStatic(detector.modifiers)))
        return methods(listOf(data), loader)
    }

    private fun methods(targets: List<MethodData>, loader: ClassLoader): Array<Method> {
        val direct = targets.flatMap { it.callers }
        val callers = (direct + direct.flatMap { it.callers }).filter { it.isMethod }.distinctBy { it.descriptor }
            .mapNotNull { runCatching { it.getMethodInstance(loader) }.getOrNull() }
        return (targets.map { it.getMethodInstance(loader) } + callers).distinct().toTypedArray()
    }
}
