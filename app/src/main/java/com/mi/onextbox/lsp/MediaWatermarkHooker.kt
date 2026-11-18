package com.mi.onextbox.lsp

import android.content.Context
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Field
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean
import org.luckypray.dexkit.DexKitBridge

internal object MediaWatermarkHooker {
    fun targets(dex: DexKitBridge, loader: ClassLoader, camera: Boolean): Map<String, List<Member>> {
        val market = dex.findClass { matcher {
            usingStrings("MarketNameInfo", "ro.vendor.oplus.market.watermark", "ro.vendor.oplus.market.enname")
        } }.single().getInstance(loader)
        val names = market.declaredMethods.filter { Modifier.isStatic(it.modifiers) && it.returnType == String::class.java &&
            (it.parameterCount == 0 || it.parameterTypes.contentEquals(arrayOf(String::class.java))) }
        check(names.size >= 3)
        val base = mapOf("market" to names)
        if (!camera) {
            val content = loader.loadClass("com.oplus.tbluniformeditor.plugins.watermark.data.WatermarkContent")
            return base + mapOf("content" to listOf("getDeviceName", "getMake", "getLocation", "getTime").map { name ->
                content.getDeclaredMethod(name).also { check(it.returnType == String::class.java) }
            })
        }
        val converter = loader.loadClass("com.oplus.ocs.camera.ipuapi.process.watermark.entity.WatermarkProcessParameter")
            .getDeclaredMethod("covertToWatermarkYuvInfo", Context::class.java, Boolean::class.javaPrimitiveType)
        val userType = dex.findMethod { matcher { usingStrings("WatermarkUserConfigure{isCustomHasselbladOpen: ") } }
            .single().getMethodInstance(loader).declaringClass
        val userField = converter.returnType.declaredFields.single { it.type == userType }.apply { isAccessible = true }
        val userText = textProbe(userType)
        val outputText = textProbe(converter.returnType)
        val time = userText.named("mWatermarkTime")
        val author = userText.named("mWatermarkAuthor")
        val location = userText.fields.filter { userText.tokens.getValue(it) !in userText.description }.single()
        return base + mapOf("converter" to listOf(converter), "user" to listOf(userField),
            "inputModel" to listOf("mMarketName", "mEnMarketName").map(converter.declaringClass::getDeclaredField),
            "inputUser" to listOf(converter.declaringClass.getDeclaredField("mWatermarkUserConfigure")),
            "model" to listOf(outputText.named("marketName"), outputText.named("enMarketName")),
            "time" to listOf(time), "author" to listOf(author), "location" to listOf(location),
            "showTime" to listOf(booleanProbe(userType, "isWatermarkTimeOpened")),
            "showLocation" to listOf(booleanProbe(userType, "isWatermarkLocationOpened")))
    }

    private data class TextProbe(val fields: List<Field>, val tokens: Map<Field, String>, val description: String) {
        fun named(label: String): Field = fields.single { field ->
            Regex("(?:^|[, {])${Regex.escape(label)}\\s*[:=]\\s*${Regex.escape(tokens.getValue(field))}(?:[, }]|$)").containsMatchIn(description)
        }
    }
    private fun textProbe(type: Class<*>): TextProbe {
        val probe = type.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        val fields = type.declaredFields.filter { !Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .onEach { it.isAccessible = true }
        val tokens = fields.mapIndexed { index, field -> field to "ONextBoxProbe${index}End" }.toMap()
        tokens.forEach { (field, token) -> field.set(probe, token) }
        return TextProbe(fields, tokens, probe.toString())
    }
    private fun booleanProbe(type: Class<*>, label: String): Field {
        val probe = type.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        val fields = type.declaredFields.filter { !Modifier.isStatic(it.modifiers) && it.type == Boolean::class.javaPrimitiveType }
            .onEach { it.isAccessible = true; it.setBoolean(probe, false) }
        return fields.single { field ->
            field.setBoolean(probe, true)
            val matches = Regex("${Regex.escape(label)}\\s*[:=]\\s*true(?:[, }]|$)").containsMatchIn(probe.toString())
            field.setBoolean(probe, false)
            matches
        }
    }

    fun hook(loader: ClassLoader, path: String, camera: Boolean) {
        val tag = "ONextBox-WatermarkContent"
        val prefix = "$tag@${System.identityHashCode(loader)}:"
        runCatching {
            val targets = HostDexMetadata.members(loader, path, "watermark-content", "personalization-v2:$camera") {
                targets(it, loader, camera)
            }
            val reached = AtomicBoolean()
            val methods = targets.getValue("market").filterIsInstance<Method>()
            methods.forEachIndexed { index, method ->
                ModernHookRegistry.installFast("${prefix}market:$index", method, XposedInterface.Hooker { chain ->
                    val model = MediaWatermarkSettings.textXposed(MediaWatermarkSettings.MODEL)
                    if (model.isEmpty()) chain.proceed() else model
                })
            }
            if (camera) {
                val converter = targets.getValue("converter").single() as Method
                fun field(name: String) = (targets.getValue(name).single() as Field).apply { isAccessible = true }
                val user = field("user")
                val inputModel = targets.getValue("inputModel").map { (it as Field).apply { isAccessible = true } }
                val inputUser = field("inputUser")
                val inputTime = inputUser.type.getDeclaredField("mWatermarkTime").apply { isAccessible = true }
                val inputAuthor = inputUser.type.getDeclaredField("mWatermarkAuthor").apply { isAccessible = true }
                val inputShowTime = inputUser.type.getDeclaredField("isWatermarkTimeOpened").apply { isAccessible = true }
                val inputShowLocation = inputUser.type.getDeclaredField("isWatermarkLocationOpened").apply { isAccessible = true }
                val model = targets.getValue("model").map { (it as Field).apply { isAccessible = true } }
                val time = field("time"); val location = field("location"); val author = field("author")
                val showTime = field("showTime"); val showLocation = field("showLocation")
                ModernHookRegistry.installFast("${prefix}converter", converter, XposedInterface.Hooker { chain ->
                    if (!MediaWatermarkSettings.enabledXposed()) return@Hooker chain.proceed()
                    val name = MediaWatermarkSettings.textXposed(MediaWatermarkSettings.MODEL)
                    val source = checkNotNull(chain.thisObject)
                    val old = ArrayList<Triple<Any, Field, Any?>>()
                    fun set(target: Any, field: Field, value: Any?) {
                        old.add(Triple(target, field, field.get(target)))
                        field.set(target, value)
                    }
                    if (name.isNotEmpty()) inputModel.forEach { set(source, it, name) }
                    inputUser.get(source)?.let { info ->
                        val timeText = MediaWatermarkSettings.textXposed(MediaWatermarkSettings.TIME)
                        val authorText = MediaWatermarkSettings.textXposed(MediaWatermarkSettings.AUTHOR)
                        if (authorText.isNotEmpty()) set(info, inputAuthor, authorText)
                        if (MediaWatermarkSettings.hiddenXposed(MediaWatermarkSettings.HIDE_TIME)) set(info, inputShowTime, false)
                        else if (timeText.isNotEmpty()) { set(info, inputTime, timeText); set(info, inputShowTime, true) }
                        if (MediaWatermarkSettings.hiddenXposed(MediaWatermarkSettings.HIDE_LOCATION)) set(info, inputShowLocation, false)
                    }
                    val result = try { chain.proceed() } finally {
                        old.asReversed().forEach { (target, member, value) -> member.set(target, value) }
                    } ?: return@Hooker null
                    if (name.isNotEmpty()) model.forEach { it.set(result, name) }
                    val info = user.get(result) ?: return@Hooker result
                    fun content(key: String, hideKey: String, valueField: Field, flag: Field) {
                        val value = MediaWatermarkSettings.textXposed(key)
                        if (MediaWatermarkSettings.hiddenXposed(hideKey)) { flag.setBoolean(info, false); valueField.set(info, "") }
                        else if (value.isNotEmpty()) { flag.setBoolean(info, true); valueField.set(info, value) }
                    }
                    content(MediaWatermarkSettings.TIME, MediaWatermarkSettings.HIDE_TIME, time, showTime)
                    content(MediaWatermarkSettings.LOCATION, MediaWatermarkSettings.HIDE_LOCATION, location, showLocation)
                    val signature = MediaWatermarkSettings.textXposed(MediaWatermarkSettings.AUTHOR)
                    if (signature.isNotEmpty()) author.set(info, signature)
                    if (reached.compareAndSet(false, true)) HookLog.i(tag, "Native camera watermark personalization reached")
                    result
                })
            } else {
                targets.getValue("content").filterIsInstance<Method>().forEach { method ->
                    ModernHookRegistry.installFast("${prefix}content:${method.name}", method, XposedInterface.Hooker { chain ->
                        val key = when (method.name) {
                            "getDeviceName" -> MediaWatermarkSettings.MODEL
                            "getMake" -> MediaWatermarkSettings.MAKE
                            "getLocation" -> MediaWatermarkSettings.LOCATION
                            else -> MediaWatermarkSettings.TIME
                        }
                        val hidden = (key == MediaWatermarkSettings.LOCATION && MediaWatermarkSettings.hiddenXposed(MediaWatermarkSettings.HIDE_LOCATION)) ||
                            (key == MediaWatermarkSettings.TIME && MediaWatermarkSettings.hiddenXposed(MediaWatermarkSettings.HIDE_TIME))
                        val value = MediaWatermarkSettings.textXposed(key)
                        if (hidden || value.isNotEmpty()) {
                            if (reached.compareAndSet(false, true)) HookLog.i(tag, "Native gallery watermark personalization reached")
                            if (hidden) "" else value
                        } else chain.proceed()
                    })
                }
            }
            val hooked = targets.values.flatten().filterIsInstance<Method>()
            HostDexMetadata.methods(loader, path, "watermark-content-callers", "personalization-v2:$camera") { dex ->
                mapOf("callers" to hooked.map { it.declaringClass.name }.distinct().flatMap { owner ->
                    dex.getClassData(owner)?.methods.orEmpty().filter { data ->
                        data.isMethod && runCatching { data.getMethodInstance(loader) in hooked }.getOrDefault(false)
                    }.flatMap { it.callers }
                }.filter { it.isMethod }.mapNotNull { runCatching { it.getMethodInstance(loader) }.getOrNull() }.distinct().toTypedArray())
            }.getValue("callers").forEach { runCatching { ModernHookRuntime.requireModule().deoptimize(it) } }
            HotReloadLifecycle.register("${prefix}targets", capture = { targets })
        }.onFailure {
            ModernHookRegistry.unhookPrefix(prefix)
            HookLog.w(tag, "Unsupported watermark content data; native behavior retained", it)
        }
    }
}
