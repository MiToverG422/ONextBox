package com.mi.onextbox.lsp

import android.view.View
import com.mi.onextbox.lsp.compat.HotReloadLifecycle
import com.mi.onextbox.lsp.compat.ModernHookRegistry
import com.mi.onextbox.lsp.compat.ModernMethodHook
import com.mi.onextbox.lsp.compat.ModernHookRuntime
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object KeyboardFlagEmojiHooker {
    private const val TAG = "ONextBox-KeyboardEmoji"

    fun hook(loader: ClassLoader, apkPath: String) {
        val stateKey = "$TAG:dexkit-v2:targets@${System.identityHashCode(loader)}"
        val saved = DexTargetCache.restoredState(stateKey) as? Array<*>
        val hookKey = "$TAG@${System.identityHashCode(loader)}:flags"
        runCatching {
            val targets = saved ?: resolve(loader, apkPath)
            val collector = targets[0] as Method
            val ownerField = (targets[1] as Field).apply { isAccessible = true }
            val category = (targets[2] as Method).apply { isAccessible = true }
            val flowValue = (targets[3] as Method).apply { isAccessible = true }
            val models = KeyboardFlagEmojiModels(
                targets[4] as Constructor<*>, targets[5] as Field, targets[6] as Field,
                targets[7] as Constructor<*>, targets[8] as Field,
                targets[9] as Constructor<*>, targets[10] as Field,
            )
            (targets[11] as Array<*>).filterIsInstance<Method>().forEach { target ->
                runCatching { ModernHookRuntime.requireModule().deoptimize(target) }
            }
            ModernHookRegistry.installCompat(hookKey, collector, object : ModernMethodHook() {
                private var applied = false
                private var warned = false
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!LspConfig.isKeyboardFlagEmojiEnabledXposed()) return
                    runCatching {
                        val owner = ownerField.get(param.thisObject) ?: return
                        if (!category.declaringClass.isInstance(owner)) return
                        val selected = flowValue.invoke(category.invoke(owner))
                        if (!KeyboardFlagEmojiRules.shouldAugment(true, selected)) return
                        models.supplement(param.args[0])?.let {
                            param.args[0] = it
                            if (!applied) {
                                applied = true
                                HookLog.i(TAG, "Native flag emoji panel augmented")
                            }
                        }
                    }.onFailure {
                        if (!warned) {
                            warned = true
                            HookLog.w(TAG, "Flag emoji payload changed, stock data retained", it)
                        }
                    }
                }
            })
            HotReloadLifecycle.register(stateKey, capture = { targets })
            HookLog.i(TAG, "DexKit flag emoji panel hook installed: ${collector.declaringClass.name}.${collector.name}")
        }.onFailure {
            ModernHookRegistry.unhook(hookKey)
            HookLog.w(TAG, "Flag emoji panel hook unavailable, stock behavior retained", it)
        }
    }

    private fun resolve(loader: ClassLoader, apkPath: String): Array<Any> =
        ApkDexSession.withSession(apkPath) { open -> resolve(open(), loader) }

    private fun resolve(dex: DexKitBridge, loader: ClassLoader): Array<Any> {
        val contents = dex.findMethod { matcher {
            usingStrings("emojiCategory"); returnType = "void"; paramTypes("java.lang.String")
        } }.mapNotNull { data ->
            runCatching { data.getMethodInstance(loader).declaringClass }.getOrNull()
        }.distinct().filter { type ->
            View::class.java.isAssignableFrom(type) && type.declaredFields.count {
                !Modifier.isStatic(it.modifiers) && List::class.java.isAssignableFrom(it.type)
            } >= 2
        }
        val collector = dex.findMethod { matcher {
            name = "emit"; returnType = "java.lang.Object"; paramCount = 2
        } }.mapNotNull { data ->
            runCatching {
                val method = data.getMethodInstance(loader)
                if (Modifier.isStatic(method.modifiers) || method.parameterTypes[0] != Any::class.java ||
                    !method.parameterTypes[1].isInterface) return@runCatching null
                val owner = method.declaringClass.declaredFields.singleOrNull {
                    !Modifier.isStatic(it.modifiers) && it.type in contents
                } ?: return@runCatching null
                Triple(data, method, owner)
            }.getOrNull()
        }.single()
        val content = collector.third.type
        val category = content.declaredMethods.single { method ->
            !Modifier.isStatic(method.modifiers) && method.parameterCount == 0 && method.returnType.isInterface &&
                runCatching { method.returnType.getMethod("getValue").returnType == Any::class.java }.getOrDefault(false)
        }
        val flowValue = category.returnType.getMethod("getValue")
        val emoji = dex.findMethod { matcher {
            name = "toString"; returnType = "java.lang.String"; paramTypes(); usingStrings("Emoji(value=")
        } }.mapNotNull { data ->
            runCatching {
                val type = data.getMethodInstance(loader).declaringClass
                val fields = type.declaredFields.filter { !Modifier.isStatic(it.modifiers) }
                if (fields.size != 3 || fields.count { it.type == String::class.java } != 2 ||
                    fields.count { it.type == Long::class.javaPrimitiveType } != 1) return@runCatching null
                val constructor = type.getDeclaredConstructor(String::class.java, String::class.java,
                    Long::class.javaPrimitiveType).apply { isAccessible = true }
                val probe = constructor.newInstance(KeyboardFlagEmojiRules.flags[0].value, "ONextBox", 0L)
                if (!probe.toString().startsWith("Emoji(value=")) return@runCatching null
                val value = fields.single {
                    it.type == String::class.java && it.apply { isAccessible = true }.get(probe) ==
                        KeyboardFlagEmojiRules.flags[0].value
                }
                constructor to value
            }.getOrNull()
        }.single()
        val group = dex.findMethod { matcher {
            name = "toString"; returnType = "java.lang.String"; paramTypes(); usingStrings("EmojiList(emojis=")
        } }.mapNotNull { data ->
            runCatching {
                val type = data.getMethodInstance(loader).declaringClass
                val entries = type.declaredFields.filter { !Modifier.isStatic(it.modifiers) }
                    .singleOrNull { it.type == List::class.java } ?: return@runCatching null
                val constructor = type.getDeclaredConstructor(List::class.java).apply { isAccessible = true }
                val probe = constructor.newInstance(arrayListOf(emoji.first.newInstance("🏁", "ONextBox", 0L)))
                if (!probe.toString().startsWith("EmojiList(emojis=")) return@runCatching null
                constructor to entries
            }.getOrNull()
        }.single()
        val pair = Class.forName("kotlin.Pair", false, loader)
            .getDeclaredConstructor(Any::class.java, Any::class.java).apply { isAccessible = true }
        val pairProbe = pair.newInstance("ONextBox-first", "ONextBox-second")
        val pairFields = pair.declaringClass.declaredFields.filter { !Modifier.isStatic(it.modifiers) }
            .onEach { it.isAccessible = true }
        val first = pairFields.single { it.get(pairProbe) == "ONextBox-first" }
        val second = pairFields.single { it.get(pairProbe) == "ONextBox-second" }
        val callers = collector.first.callers.mapNotNull {
            runCatching { it.getMethodInstance(loader) }.getOrNull()
        }
        val deoptimize = (callers + collector.second.declaringClass.declaredMethods).distinct().toTypedArray()
        return arrayOf(collector.second, collector.third, category, flowValue, pair, first, second,
            emoji.first, emoji.second, group.first, group.second, deoptimize)
    }
}

internal class KeyboardFlagEmojiModels(
    private val pair: Constructor<*>,
    private val first: Field,
    private val second: Field,
    private val emoji: Constructor<*>,
    private val value: Field,
    private val group: Constructor<*>,
    private val entries: Field,
) {
    init {
        listOf(pair, emoji, group).forEach { it.isAccessible = true }
        listOf(first, second, value, entries).forEach { it.isAccessible = true }
    }

    fun supplement(input: Any?): Any? {
        if (input == null || !pair.declaringClass.isInstance(input)) return null
        val originals = listOf(first.get(input), second.get(input)).map { raw ->
            val list = raw as? List<*> ?: return null
            if (list.any { !group.declaringClass.isInstance(it) }) return null
            list.filterNotNull()
        }
        val updated = originals.map { original ->
            KeyboardFlagEmojiRules.supplement(original, value = { item ->
                val values = entries.get(item) as? List<*> ?: error("Emoji group changed")
                val entry = values.firstOrNull() ?: error("Empty emoji group")
                check(emoji.declaringClass.isInstance(entry))
                value.get(entry) as String
            }, create = { flag ->
                group.newInstance(arrayListOf(emoji.newInstance(flag.value, flag.name, 0L)))
            })
        }
        return pair.newInstance(updated[0], updated[1])
    }
}
