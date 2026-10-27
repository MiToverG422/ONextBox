package com.mi.onextbox.lsp

import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Method
import android.app.Activity
import android.view.View

internal object InstallerCleanupTargets {
    const val SCHEMA = "installer-targets:10"
    fun resolve(dex: DexKitBridge, loader: ClassLoader, packageName: String): Map<String, Array<Method>> {
        val result = linkedMapOf(SCHEMA to emptyArray<Method>())
        fun group(name: String, find: () -> List<MethodData>) {
            runCatching {
                val methods = find()
                require(methods.isNotEmpty())
                val direct = methods.flatMap { it.callers }
                val callers = (direct + direct.flatMap { it.callers }).filter { it.isMethod }
                    .distinctBy { it.descriptor }.mapNotNull { runCatching { it.getMethodInstance(loader) }.getOrNull() }
                result[name] = (methods.map { it.getMethodInstance(loader) } + callers).distinct().toTypedArray()
            }.onFailure { HookLog.w("ONextBox-InstallerCleanup", "$name targets unavailable; native behavior retained", it) }
        }
        if (packageName == InstallerCleanupSettings.APP_DETAIL) {
            result["market-card"] = arrayOf(
                View::class.java.getDeclaredMethod("setVisibility", Int::class.javaPrimitiveType),
                Activity::class.java.getDeclaredMethod("onResume"),
            )
            runCatching {
                result["network"] = InstallerNetworkAccess.targets(loader)
            }.onFailure { HookLog.w("ONextBox-InstallerCleanup", "Network targets unavailable; native behavior retained", it) }
            group("fast-prepare") {
                val request = dex.findMethod { matcher {
                    declaredClass = "com.oplus.appdetail.modelv2.parsing.reository.PreloadGuideRepository"
                    usingStrings("preload requests timeout (5000ms)"); returnType = "java.lang.Object"
                } }.single()
                val result = request.invokes.distinctBy { it.descriptor }.single { it.isConstructor &&
                    it.paramCount == 3 && it.paramTypeNames[1] == "com.heytap.cdo.security.domain.expgray.ConfigResult" &&
                    it.paramTypeNames[2] == "java.lang.String" }
                listOf(request, dex.getClassData(result.className)!!.methods.single {
                    it.isMethod && it.methodName == "toString" && it.paramCount == 0
                })
            }
            group("scan-card") {
                listOf(dex.findMethod { matcher {
                    declaredClass = "com.oplus.appdetail.modelv2.guide.view.ScanContentFrameLayout"
                    returnType = "void"; paramTypes()
                } }.single { it.invokes.any { call ->
                    call.isConstructor && call.className.startsWith("com.oplus.appdetail.modelv2.guide.view.") &&
                        loader.loadClass(call.className).let { view ->
                            view.superclass?.name == "com.oplus.appdetail.base.view.RoundLinearLayout" && view.declaredMethods.isEmpty()
                        }
                } })
            }
            group("collection-notice") {
                val factory = dex.findMethod { matcher {
                    usingStrings("parsingResult", "statMap", "parseExtParam")
                } }.single { it.paramCount == 5 && it.paramTypeNames.last() == "boolean" &&
                    it.className.startsWith("com.oplus.appdetail.modelv2.parsing.reository.") }
                val allowed = factory.invokes.distinctBy { it.descriptor }.single {
                    it.className == "com.oplus.appdetail.model.entrance.ChannelWhiteListHelper" &&
                        it.returnTypeName == "boolean" && it.paramTypeNames == listOf("java.lang.String")
                }
                listOf(allowed, factory)
            }
            group("route") {
                val factory = dex.findMethod {
                    matcher {
                        returnType = "androidx.fragment.app.Fragment"
                        paramTypes("com.oplus.appdetail.model.guide.repository.ExtJumpParam")
                    }
                }.single { it.usingFields.any { field -> field.field.getFieldInstance(loader).name == "NoScanningGuide" } }
                val market = factory.invokes.distinctBy { it.descriptor }.single {
                    it.returnTypeName == "boolean" && it.paramTypeNames == listOf("android.content.Context") &&
                        it.invokes.any { call -> call.className == "android.content.pm.PackageManager" && call.methodName == "getPackageInfo" }
                }
                listOf(market, factory)
            }
            group("plain") {
                listOf("getGuidePageType" to "int", "getShieldWarning" to "java.lang.String").map { (name, type) ->
                    dex.findMethod { matcher {
                        declaredClass = "com.heytap.cdo.security.domain.safeguide.GuideConfig"
                        this.name = name; returnType = type; paramTypes()
                    } }.single()
                }
            }
            group("recommend") {
                val dispatch = dex.findMethod { matcher {
                    usingStrings("preCache noExist, request server data"); returnType = "void"
                } }.single()
                require(dispatch.className.startsWith("com.oplus.appdetail.model.finish."))
                val methods = dex.getClassData(dispatch.className)!!.methods
                val request = methods.single { it.isMethod && it.returnTypeName == "java.lang.Object" &&
                    it.paramCount == 2 && it.paramTypeNames[0] == dispatch.paramTypeNames[0] }
                val preload = dex.findMethod { matcher {
                    declaredClass = dispatch.className
                    usingStrings("preLoadInstallFinishRecommend failed, installId empty"); returnType = "void"
                    paramTypes("com.oplus.appdetail.model.guide.repository.ExtJumpParam")
                } }.single()
                val complete = methods.single { it.isMethod && it.returnTypeName == "void" && it.paramCount == 2 &&
                    it.paramTypeNames[0] == "com.heytap.cdo.card.domain.dto.ViewLayerWrapDto" }
                listOf(request, preload, complete)
            }
            group("cancel") {
                listOf("show_cancel_recommend_switch", "cancel_recommend_switch").map { text ->
                    dex.findMethod { matcher { usingStrings(text); returnType = "boolean"; paramTypes() } }
                        .single { text in it.usingStrings }
                }
            }
            group("risk-ad") {
                listOf(dex.findMethod { matcher {
                    declaredClass = "com.heytap.cdo.security.domain.open.RiskFineResultDto"
                    name = "getResourceDto"; returnType = "com.heytap.cdo.security.domain.watch.ResourceDto"; paramTypes()
                } }.single())
            }
        } else if (packageName == InstallerCleanupSettings.PACKAGE_INSTALLER) {
            group("native") {
                val entries = dex.findMethod { matcher {
                    declaredClass = "com.android.packageinstaller.InstallStart"; name = "onCreate"; paramTypes("android.os.Bundle")
                } }.single()
                val init = entries.invokes.single { call -> call.returnTypeName == "void" &&
                    call.paramTypeNames == listOf("android.content.Context") && call.invokes.any { it.methodName == "isClosedSuperFirewall" } }
                val pia = dex.findMethod { matcher {
                    usingStrings("Use PIA V2"); returnType = "boolean"; paramTypes("android.content.Context")
                } }.single()
                require(pia.className == "com.android.packageinstaller.PackageUtil")
                val legacy = dex.findMethod { matcher {
                    declaredClass = entries.className; returnType = "void"; paramTypes("boolean", "android.content.Intent")
                } }.single()
                val staged = dex.findMethod { matcher {
                    declaredClass = "com.android.packageinstaller.DeleteStagedFileOnResult"
                    name = "onCreate"; paramTypes("android.os.Bundle")
                } }.single()
                listOf(init.invokes.single { it.className == "android.content.pm.OplusPackageManager" &&
                    it.methodName == "isClosedSuperFirewall" && it.returnTypeName == "boolean" && it.paramCount == 0 },
                    init, entries, pia, legacy, staged)
            }
        }
        return result
    }
}
