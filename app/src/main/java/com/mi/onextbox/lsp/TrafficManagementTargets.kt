package com.mi.onextbox.lsp

import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.MethodData

/** Strict, independently verifiable lookups for obfuscated OEM methods. */
internal class TrafficManagementTargets(private val dex: DexKitBridge) {
    private fun anchored(text: String, result: String, vararg parameters: String): MethodData =
        dex.findMethod {
            matcher { usingStrings(text); returnType = result; paramTypes(parameters.toList()) }
        }.single()

    fun google(): MethodData = anchored("IgnoreGmsUserSet", "boolean")

    fun ota(): List<MethodData> {
        val packageCheck = anchored("isKeepWifiAppByPackageInfo: context or packageInfo is null",
            "boolean", CONTEXT, "android.content.pm.PackageInfo")
        val nameCheck = dex.findMethod {
            matcher {
                declaredClass = packageCheck.className; returnType = "boolean"; paramTypes("java.lang.String")
                addInvoke { declaredClass = "java.util.List"; name = "contains" }
            }
        }.single()
        val uidCheck = anchored("isKeepWifiAppByUid: context is null or invalid uid=", "boolean", CONTEXT, "int")
        return listOf(nameCheck, packageCheck, uidCheck)
    }

    fun preinstalled(): List<MethodData> = listOf(
        dex.findMethod {
            matcher {
                declaredClass = MODEL; returnType = "boolean"; paramTypes(CONTEXT, "java.lang.String")
                usingStrings("android.permission.INTERNET")
            }
        }.single(),
        dex.findMethod {
            matcher { declaredClass = MODEL; returnType = "int"; paramTypes(); usingStrings("com.oplus.games") }
        }.single(),
    )

    fun cloud(): List<MethodData> = listOf(
        anchored("/api/client/app-disabled/v1/query-all", "void", CONTEXT),
        anchored("setAppNetworkStateList", "void", CONTEXT, "java.lang.String"),
    )

    fun hidden(): List<MethodData> = listOf(
        anchored("oplus.simsettings.restrict_hide", "java.util.List"),
        anchored("com.oplus.trafficmonitor.disable_close_restrict_background", "java.util.List"),
    )

    fun limit(): MethodData = anchored("com.oplus.trafficmonitor.customize_traffic_limit", "boolean")

    fun roaming(): List<MethodData> {
        val flag = anchored("com.oplus.trafficmonitor.customize_roaming_reduction", "boolean")
        val region = flag.callers.single { it.returnTypeName == "boolean" && it.paramTypeNames.isEmpty() }
        val ui = region.callers.single { it.className == CUSTOM && it.paramTypeNames.isEmpty() }
        val write = anchored("setBackgroundDataType uid: ", "void", CONTEXT, "int", "int")
        val read = dex.findMethod {
            matcher {
                declaredClass = write.className; returnType = "int"
                paramTypes(CONTEXT, "int"); usingStrings("background_data")
            }
        }.single()
        val readWrapper = dex.findMethod {
            matcher { declaredClass = CUSTOM; returnType = "int"; paramTypes(CONTEXT, "int") }
        }.single()
        val writeWrapper = dex.findMethod {
            matcher { declaredClass = CUSTOM; returnType = "void"; paramTypes(CONTEXT, "int", "int") }
        }.single()
        return listOf(region, ui, write, read, readWrapper, writeWrapper)
    }

    companion object {
        const val CONTEXT = "android.content.Context"
        const val CUSTOM = "com.oplus.trafficmonitor.CustomTrafficSetting"
        const val MODEL = "com.oplus.trafficmonitor.model.NetworkControlDataModel"
    }
}
