package com.mi.onextbox.lsp

import android.content.res.Configuration
import android.content.res.Resources
import android.content.res.TypedArray
import android.content.pm.ActivityInfo
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Method
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

internal object NativeInstallerThemeHooker {
    private const val TAG = "NativeInstallerTheme"
    private val mappings = WeakHashMap<Resources, Map<Int, Int>>()
    private val matchedPaths = ConcurrentHashMap.newKeySet<String>()
    private val styledData by lazy {
        TypedArray::class.java.getDeclaredField("mData").apply { isAccessible = true }
    }
    private val nativeConfigurations by lazy {
        HiddenApiBypass.getDeclaredMethods(ActivityInfo::class.java).filterIsInstance<Method>().single {
            it.name == "activityInfoConfigJavaToNative" &&
                it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
        }.apply { isAccessible = true }
    }
    private val slots by lazy {
        fun field(name: String) = TypedArray::class.java.getDeclaredField(name).apply { isAccessible = true }.getInt(null)
        NativeInstallerThemeRules.Slots(
            field("STYLE_NUM_ENTRIES"), field("STYLE_TYPE"), field("STYLE_DATA"), field("STYLE_ASSET_COOKIE"),
            field("STYLE_RESOURCE_ID"), field("STYLE_CHANGING_CONFIGURATIONS"), field("STYLE_DENSITY"),
        ).also { require(it.valid()) }
    }

    fun hook() {
        Resources::class.java.declaredMethods.filter {
            it.name in setOf("getColor", "getColorStateList", "getDrawable", "getDrawableForDensity", "getValue", "getValueForDensity") &&
                it.parameterTypes.firstOrNull() == Int::class.javaPrimitiveType
        }.forEach { method ->
            runCatching {
                ModernHookRegistry.installFast("$TAG:resources:${method.toGenericString()}", method) { chain ->
                    val target = runCatching {
                        if (method.name.startsWith("getValue") && chain.args.last() == false) 0
                        else replacement(chain.thisObject as Resources, chain.args.first() as Int)
                    }.getOrDefault(0)
                    if (target == 0) chain.proceed() else {
                        val args = chain.args.toTypedArray()
                        args[0] = target
                        matched(method.name)
                        chain.proceed(args)
                    }
                }
            }.onFailure { HookLog.w(TAG, "Resource color hook unavailable", it) }
        }
        TypedArray::class.java.declaredMethods.filter {
            it.name in setOf("getColor", "getColorStateList", "getDrawable", "getDrawableForDensity") &&
                it.parameterTypes.firstOrNull() == Int::class.javaPrimitiveType
        }.forEach { method ->
            runCatching {
                ModernHookRegistry.installFast("$TAG:typed:${method.toGenericString()}", method) { chain ->
                    val original = chain.proceed()
                    runCatching {
                        val array = chain.thisObject as TypedArray
                        val id = array.getResourceId(chain.args.first() as Int, 0)
                        val target = replacement(array.resources, id)
                        if (target == 0) original else {
                            matched("typed:${method.name}")
                            when (method.name) {
                                "getColorStateList" -> array.resources.getColorStateList(target, null)
                                "getDrawable", "getDrawableForDensity" -> ColorDrawable(array.resources.getColor(target, null))
                                else -> array.resources.getColor(target, null)
                            }
                        }
                    }.getOrDefault(original)
                }
            }.onFailure { HookLog.w(TAG, "Styled color hook unavailable", it) }
        }
        hookStyledArrays()
        runCatching {
            val method = Resources.Theme::class.java.getDeclaredMethod("resolveAttribute", Int::class.javaPrimitiveType, TypedValue::class.java, Boolean::class.javaPrimitiveType)
            ModernHookRegistry.installFast("$TAG:theme-value", method) { chain ->
                val result = chain.proceed()
                if (result == true && chain.args[2] == true) runCatching {
                    val resources = (chain.thisObject as Resources.Theme).resources
                    val value = chain.args[1] as TypedValue
                    val target = replacement(resources, value.resourceId)
                    if (target != 0 && value.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT) {
                        val configurations = value.changingConfigurations
                        resources.getValue(target, value, true)
                        value.changingConfigurations = value.changingConfigurations or configurations
                        matched("theme-value")
                    }
                }
                result
            }
        }.onFailure { HookLog.w(TAG, "Theme color hook unavailable", it) }
        HookLog.i(TAG, "Native installer day/night compatibility installed, including XML backgrounds")
    }

    private fun hookStyledArrays() {
        runCatching { styledData; slots; nativeConfigurations }.onFailure {
            HookLog.w(TAG, "Styled color table unavailable; using color/drawable accessors", it)
            return
        }
        listOf(Resources::class.java, Resources.Theme::class.java,
            runCatching { Class.forName("android.content.res.ResourcesImpl\$ThemeImpl") }.getOrNull(),
        ).filterNotNull().forEach { type ->
            type.declaredMethods.filter {
                it.returnType == TypedArray::class.java && it.name in setOf("obtainAttributes", "obtainStyledAttributes", "resolveAttributes")
            }.forEach { method ->
                runCatching {
                    ModernHookRegistry.installFast("$TAG:styles:${method.toGenericString()}", method) { chain ->
                        val result = chain.proceed()
                        if (result is TypedArray) runCatching { patchStyledColors(result) }
                            .onFailure { if (matchedPaths.add("styled-failure")) HookLog.w(TAG, "Styled color correction failed", it) }
                        result
                    }
                }.onFailure { HookLog.w(TAG, "Styled color acquisition hook unavailable", it) }
            }
        }
    }

    private fun patchStyledColors(array: TypedArray) {
        val resources = array.resources
        if (!enabled(resources)) return
        val entries = mapping(resources)
        if (entries.isEmpty()) return
        val data = styledData.get(array) as IntArray
        val count = NativeInstallerThemeRules.rewriteColors(data, array.length(), slots) { source ->
            entries[source]?.let { target ->
                val value = TypedValue()
                resources.getValue(target, value, true)
                NativeInstallerThemeRules.ColorValue(value.type, value.data, value.assetCookie, target,
                    nativeConfigurations.invoke(null, value.changingConfigurations) as Int, value.density)
            }
        }
        if (count != 0) matched("styled-array")
    }

    private fun matched(path: String) {
        if (matchedPaths.add(path)) HookLog.i(TAG, "Corrected native installer night colors through $path")
    }

    private fun enabled(resources: Resources) = InstallerCleanupSettings.active(InstallerCleanupSettings.NATIVE_UI) &&
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    private fun replacement(resources: Resources, id: Int): Int {
        if (id == 0 || !enabled(resources)) return 0
        return mapping(resources)[id] ?: 0
    }

    private fun mapping(resources: Resources): Map<Int, Int> = synchronized(mappings) {
        mappings.getOrPut(resources) {
            buildMap {
                NativeInstallerThemeRules.colors.forEach { (alias, role) ->
                    // The resolved reference in a TypedArray may point directly to android.
                    listOf(NativeInstallerThemeRules.PACKAGE to alias, "android" to "system_${role}_light")
                        .forEach { (owner, name) ->
                            val replacement = NativeInstallerThemeRules.replacement(owner, name, night = true, enabled = true)
                            val target = replacement?.let { resources.getIdentifier(it, "color", "android") } ?: 0
                            val source = resources.getIdentifier(name, "color", owner)
                            if (source != 0 && target != 0) put(source, target)
                        }
                }
            }
        }
    }
}
