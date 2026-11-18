package com.mi.onextbox.lsp

import android.view.View
import android.widget.TextView
import com.mi.onextbox.lsp.compat.ModernHookBridge as XposedBridge
import com.mi.onextbox.lsp.compat.ModernMethodHook as XC_MethodHook
import com.mi.onextbox.lsp.compat.ModernReflect as XposedHelpers
import java.util.concurrent.ConcurrentHashMap

/** Hides selected ColorOS 17 entries from the Mobile network preference page. */
internal object MobileNetworkHooker {
    private const val TAG = "ONextBox-MobileNetwork"
    private const val FRAGMENT_CLASS =
        "com.android.simsettings.activity.OplusSimSettingsActivity\$a"
    private const val PREFERENCE_CLASS = "androidx.preference.Preference"
    private const val PREFERENCE_GROUP_CLASS = "androidx.preference.PreferenceGroup"
    private const val SIM_CARD_PREFERENCE_CLASS =
        "com.android.simsettings.activity.preference.SimSettingInfoPreferenceNoTraffic"
    private const val INLINE_SIM_ADAPTER_CLASS = "n6.d"
    private const val COUI_PREFERENCE_CLASS =
        "com.coui.appcompat.preference.COUIPreference"
    private const val MASKED_PHONE_NUMBER = "••••••••"
    private const val KEY_SIM_NUMBER = "sim_number"

    private val SIM_INFO_FRAGMENT_CLASSES = listOf(
        "com.android.simsettings.activity.OPSimInfoActivity\$a",
        "com.android.simsettings.activity.OplusSimInfoActivity\$a",
    )

    /** Visibility callbacks whose arguments are (type, visible). */
    private val CARRIER_BOOLEAN_VISIBILITY_CALLBACKS = listOf(
        "B", // Video calling (ViLTE)
        "v", // 5G
        "y", // 4.5G
        "z", // VoNR / Vo5G; the OEM keeps the platform-appropriate variant
        "a", // Allow 2G
    )

    /** Visibility callbacks whose arguments are (type, text, visible). */
    private val CARRIER_TEXT_VISIBILITY_CALLBACKS = listOf(
        "e", // Wi-Fi calling
        "j", // VoLTE
        "w", // Preferred network type
    )

    private const val KEY_AI_LINK_BOOST = "ai_link_boost"
    private const val KEY_ROAMING_SERVICE = "roaming_service"
    private const val KEY_ACTIVE_ROAMING_SERVICE = "tsts_roaming_card"
    private const val KEY_DATA_SIM_CARD_SERVICE = "data_sim_card_service"
    private const val KEY_SMART_CLOUD_ACCELERATION = "smart_cloud_acceleration"

    private val hookedLoaders = ConcurrentHashMap.newKeySet<Int>()

    fun hook(classLoader: ClassLoader, colorOs16: Boolean = false) {
        if (!hookedLoaders.add(System.identityHashCode(classLoader))) return

        val report = HookInstallReport()
        fun target(name: String): Class<*>? = XposedHelpers.findClassIfExists(name, classLoader).also {
            if (it == null) report.missing("class:$name")
        }
        fun install(owner: Class<*>, name: String, callback: XC_MethodHook) {
            val target = "${owner.name}#$name"
            var installed = 0
            // Count each success immediately; a failed overload must not hide earlier handles.
            owner.declaredMethods.filter { it.name == name }.forEach { method ->
                runCatching { XposedBridge.hookMethod(method, callback) }
                    .onSuccess { installed++ }
                    .onFailure { report.missing(method.toGenericString()); log("hook $target overload failed", it) }
            }
            report.record(target, installed)
        }
        val preferenceClass = target(PREFERENCE_CLASS)
        if (preferenceClass != null) {
            runCatching {
                install(
                    preferenceClass,
                    "setVisible",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val preference = param.thisObject ?: return
                            if (shouldHide(preference)) {
                                param.args[0] = false
                            }
                        }
                    },
                )
            }.onFailure { log("hook Preference.setVisible failed", it) }
        }

        val preferenceGroupClass =
            target(PREFERENCE_GROUP_CLASS)
        if (preferenceGroupClass != null) {
            // ColorOS 17 shades AndroidX PreferenceGroup.addPreference() to e(). Hiding after
            // insertion also covers entries that the page adds asynchronously.
            runCatching {
                install(
                    preferenceGroupClass,
                    "e",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            param.args.firstOrNull()?.let(::hideIfNeeded)
                        }
                    },
                )
            }.onFailure { log("hook PreferenceGroup.e failed", it) }
        }

        val fragmentClass = target(FRAGMENT_CLASS)
        if (fragmentClass != null) {
            listOf("onCreate", "onResume").forEach { methodName ->
                runCatching {
                    install(
                        fragmentClass,
                        methodName,
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                param.thisObject?.let(::applyHiddenEntries)
                            }
                        },
                    )
                }.onFailure { log("hook $FRAGMENT_CLASS.$methodName failed", it) }
            }
        }

        val simCardPreferenceClass =
            target(SIM_CARD_PREFERENCE_CLASS)
        if (simCardPreferenceClass != null) {
            runCatching {
                install(
                    simCardPreferenceClass,
                    "onBindViewHolder",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (!LspConfig.isMobileNetworkHidePhoneNumberEnabledXposed()) return
                            param.args.firstOrNull()?.let(::maskPhoneNumbers)
                        }
                    },
                )
            }.onFailure { log("hook SIM card number binding failed", it) }
        }

        // SystemUI owns the dialog shell, while ColorOS renders its SIM rows in an embedded
        // com.android.phone surface. Mask the C17 inline adapter output in that process.
        val inlineSimAdapterClass =
            target(INLINE_SIM_ADAPTER_CLASS)
        if (inlineSimAdapterClass != null) {
            runCatching {
                install(
                    inlineSimAdapterClass,
                    "onBindViewHolder",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (!LspConfig.isMobileNetworkHidePhoneNumberEnabledXposed()) return
                            param.args.firstOrNull()?.let(::maskInlinePhoneNumber)
                        }
                    },
                )
            }.onFailure { log("hook inline SIM number binding failed", it) }
        }

        val couiPreferenceClass =
            target(COUI_PREFERENCE_CLASS)
        if (couiPreferenceClass != null) {
            runCatching {
                install(
                    couiPreferenceClass,
                    "setAssignment",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (!LspConfig.isMobileNetworkHidePhoneNumberEnabledXposed()) return
                            val preference = param.thisObject ?: return
                            if (preferenceKey(preference) != KEY_SIM_NUMBER) return
                            if (isLikelyPhoneNumber(param.args.firstOrNull() as? CharSequence)) {
                                param.args[0] = MASKED_PHONE_NUMBER
                            }
                        }
                    },
                )
            }.onFailure { log("hook SIM detail number assignment failed", it) }
        }

        SIM_INFO_FRAGMENT_CLASSES.forEach { className ->
            val simInfoFragment = target(className)
                ?: return@forEach
            CARRIER_BOOLEAN_VISIBILITY_CALLBACKS.forEach { methodName ->
                runCatching {
                    install(
                        simInfoFragment,
                        methodName,
                        forceCarrierVisibilityCallback(visibleArgumentIndex = 1, colorOs16 = colorOs16),
                    )
                }.onFailure { log("hook $className.$methodName failed", it) }
            }
            CARRIER_TEXT_VISIBILITY_CALLBACKS.forEach { methodName ->
                runCatching {
                    install(
                        simInfoFragment,
                        methodName,
                        forceCarrierVisibilityCallback(
                            visibleArgumentIndex = 2,
                            restoreVoiceOverNrAfter = methodName == "j",
                            colorOs16 = colorOs16,
                        ),
                    )
                }.onFailure { log("hook $className.$methodName failed", it) }
            }
        }

        if (colorOs16) report.record("android.widget.TextView#setText", installColorOs16InlineNumberMask())
        log("Mobile network entry hooks: ${report.summary()}")
    }

    private fun forceCarrierVisibilityCallback(
        visibleArgumentIndex: Int,
        restoreVoiceOverNrAfter: Boolean = false,
        colorOs16: Boolean = false,
    ) =
        object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!LspConfig.isMobileNetworkForceCarrierOptionsEnabledXposed()) return
                if ((param.args.firstOrNull() as? Int) != 1) return
                if (param.args.size > visibleArgumentIndex) {
                    param.args[visibleArgumentIndex] = true
                }
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                if (!restoreVoiceOverNrAfter) return
                if (!LspConfig.isMobileNetworkForceCarrierOptionsEnabledXposed()) return
                if ((param.args.firstOrNull() as? Int) != 1) return
                // C17 refreshes VoNR/Vo5G first, then its VoLTE visibility callback removes
                // both entries again. Restore only the variant selected by the OEM feature
                // route; the matching preference already owns the original listener/state.
                param.thisObject?.let { ensureVoiceOverNrOption(it, colorOs16) }
            }
        }

    private fun ensureVoiceOverNrOption(fragment: Any, colorOs16: Boolean) {
        runCatching {
            val controller = XposedHelpers.getObjectField(fragment, "A") ?: return@runCatching
            val featureRouter = XposedHelpers.getObjectField(fragment, if (colorOs16) "M" else "L") ?: return@runCatching
            val slotId = XposedHelpers.callMethod(controller, if (colorOs16) "w" else "r") as? Int ?: return@runCatching
            val useVonr = XposedHelpers.callMethod(featureRouter, if (colorOs16) "o" else "l", slotId) as? Boolean ?: false
            val category = (if (colorOs16) listOf("f") else listOf("f6444f", "f6478f"))
                .firstNotNullOfOrNull { fieldName ->
                    runCatching { XposedHelpers.getObjectField(fragment, fieldName) }.getOrNull()
                } ?: return@runCatching
            val vonr = XposedHelpers.getObjectField(fragment, "J")
            val vo5g = XposedHelpers.getObjectField(fragment, "K")
            val selected = if (useVonr) vonr else vo5g
            val unselected = if (useVonr) vo5g else vonr
            if (unselected != null) XposedHelpers.callMethod(category, if (colorOs16) "j" else "i", unselected)
            if (selected != null) XposedHelpers.callMethod(category, "e", selected)
        }.onFailure { log("restore VoNR/Vo5G option failed", it) }
    }

    /** C16's inline SIM adapter is shaded differently; target its semantic TextView ID. */
    private fun installColorOs16InlineNumberMask(): Int =
        XposedBridge.hookAllMethods(TextView::class.java, "setText", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!LspConfig.isMobileNetworkHidePhoneNumberEnabledXposed()) return
                val view = param.thisObject as? TextView ?: return
                if (view.context.packageName != "com.android.phone" || view.id == View.NO_ID) return
                val entry = runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull()
                if (entry in setOf(KEY_SIM_NUMBER, "simsetting_summary1", "simsetting_summary2") &&
                    isLikelyPhoneNumber(param.args.firstOrNull() as? CharSequence)) {
                    param.args[0] = MASKED_PHONE_NUMBER
                    view.contentDescription = MASKED_PHONE_NUMBER
                }
            }
        }).size

    private fun maskPhoneNumbers(holder: Any) {
        val itemView = runCatching {
            XposedHelpers.getObjectField(holder, "itemView") as? View
        }.getOrNull() ?: return
        val resources = itemView.resources
        val packageName = itemView.context.packageName
        for (slot in 1..2) {
            val summaryId = resources.getIdentifier(
                "simsetting_summary$slot",
                "id",
                packageName,
            )
            if (summaryId == 0) continue
            val summary = itemView.findViewById<TextView>(summaryId) ?: continue
            if (!isLikelyPhoneNumber(summary.text)) continue
            summary.text = MASKED_PHONE_NUMBER

            // The OEM card also concatenates the number into its accessibility description.
            // Keep only the SIM title so the hidden value cannot still be read by TalkBack.
            val cardId = resources.getIdentifier("sim_info_slot_$slot", "id", packageName)
            val titleId = resources.getIdentifier("simsetting_title$slot", "id", packageName)
            val card = if (cardId == 0) null else itemView.findViewById<View>(cardId)
            val title = if (titleId == 0) null else itemView.findViewById<TextView>(titleId)
            card?.contentDescription = title?.text
        }
    }

    private fun maskInlinePhoneNumber(holder: Any) {
        val itemView = runCatching {
            XposedHelpers.getObjectField(holder, "itemView") as? View
        }.getOrNull() ?: return
        val numberId = itemView.resources.getIdentifier(
            KEY_SIM_NUMBER,
            "id",
            itemView.context.packageName,
        )
        if (numberId == 0) return
        val numberView = itemView.findViewById<TextView>(numberId) ?: return
        if (!isLikelyPhoneNumber(numberView.text)) return
        numberView.text = MASKED_PHONE_NUMBER
        numberView.contentDescription = MASKED_PHONE_NUMBER
    }

    private fun isLikelyPhoneNumber(value: CharSequence?): Boolean {
        val text = value?.toString()?.trim().orEmpty()
        if (text.count(Char::isDigit) < 5) return false
        return text.all { character ->
            character.isDigit() ||
                character.isWhitespace() ||
                character in "+-()[]./*#••" ||
                Character.getType(character) == Character.FORMAT.toInt()
        }
    }

    private fun applyHiddenEntries(fragment: Any) {
        hiddenKeys().forEach { key ->
            runCatching {
                XposedHelpers.callMethod(fragment, "findPreference", key)
            }.getOrNull()?.let(::hideIfNeeded)
        }
    }

    private fun hideIfNeeded(preference: Any) {
        if (!shouldHide(preference)) return
        runCatching {
            XposedHelpers.callMethod(preference, "setVisible", false)
        }.onFailure { log("hide Mobile network preference failed", it) }
    }

    private fun shouldHide(preference: Any): Boolean {
        val key = preferenceKey(preference) ?: return false
        return when (key) {
            KEY_AI_LINK_BOOST -> LspConfig.isMobileNetworkHideAiLinkBoostEnabledXposed()
            KEY_ROAMING_SERVICE,
            KEY_ACTIVE_ROAMING_SERVICE ->
                LspConfig.isMobileNetworkHideRoamingServiceEnabledXposed()
            KEY_DATA_SIM_CARD_SERVICE ->
                LspConfig.isMobileNetworkHideHighDataSimCardEnabledXposed()
            KEY_SMART_CLOUD_ACCELERATION ->
                LspConfig.isMobileNetworkHideSmartCloudAccelerationEnabledXposed()
            else -> false
        }
    }

    private fun preferenceKey(preference: Any): String? = runCatching {
        XposedHelpers.callMethod(preference, "getKey") as? String
    }.getOrNull()

    private fun hiddenKeys(): Set<String> = buildSet {
        if (LspConfig.isMobileNetworkHideAiLinkBoostEnabledXposed()) {
            add(KEY_AI_LINK_BOOST)
        }
        if (LspConfig.isMobileNetworkHideRoamingServiceEnabledXposed()) {
            add(KEY_ROAMING_SERVICE)
            add(KEY_ACTIVE_ROAMING_SERVICE)
        }
        if (LspConfig.isMobileNetworkHideHighDataSimCardEnabledXposed()) {
            add(KEY_DATA_SIM_CARD_SERVICE)
        }
        if (LspConfig.isMobileNetworkHideSmartCloudAccelerationEnabledXposed()) {
            add(KEY_SMART_CLOUD_ACCELERATION)
        }
    }

    private fun log(message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            HookLog.i(TAG, message)
        } else {
            HookLog.w(TAG, message, throwable)
        }
    }
}
