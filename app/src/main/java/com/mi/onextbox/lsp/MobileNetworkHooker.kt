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

    fun hook(classLoader: ClassLoader) {
        if (!hookedLoaders.add(System.identityHashCode(classLoader))) return

        var hookCount = 0
        val preferenceClass = XposedHelpers.findClassIfExists(PREFERENCE_CLASS, classLoader)
        if (preferenceClass != null) {
            runCatching {
                XposedBridge.hookAllMethods(
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
                hookCount++
            }.onFailure { log("hook Preference.setVisible failed", it) }
        }

        val preferenceGroupClass =
            XposedHelpers.findClassIfExists(PREFERENCE_GROUP_CLASS, classLoader)
        if (preferenceGroupClass != null) {
            // ColorOS 17 shades AndroidX PreferenceGroup.addPreference() to e(). Hiding after
            // insertion also covers entries that the page adds asynchronously.
            runCatching {
                XposedBridge.hookAllMethods(
                    preferenceGroupClass,
                    "e",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            param.args.firstOrNull()?.let(::hideIfNeeded)
                        }
                    },
                )
                hookCount++
            }.onFailure { log("hook PreferenceGroup.e failed", it) }
        }

        val fragmentClass = XposedHelpers.findClassIfExists(FRAGMENT_CLASS, classLoader)
        if (fragmentClass != null) {
            listOf("onCreate", "onResume").forEach { methodName ->
                runCatching {
                    XposedBridge.hookAllMethods(
                        fragmentClass,
                        methodName,
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                param.thisObject?.let(::applyHiddenEntries)
                            }
                        },
                    )
                    hookCount++
                }.onFailure { log("hook $FRAGMENT_CLASS.$methodName failed", it) }
            }
        }

        val simCardPreferenceClass =
            XposedHelpers.findClassIfExists(SIM_CARD_PREFERENCE_CLASS, classLoader)
        if (simCardPreferenceClass != null) {
            runCatching {
                XposedBridge.hookAllMethods(
                    simCardPreferenceClass,
                    "onBindViewHolder",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (!LspConfig.isMobileNetworkHidePhoneNumberEnabledXposed()) return
                            param.args.firstOrNull()?.let(::maskPhoneNumbers)
                        }
                    },
                )
                hookCount++
            }.onFailure { log("hook SIM card number binding failed", it) }
        }

        // SystemUI owns the dialog shell, while ColorOS renders its SIM rows in an embedded
        // com.android.phone surface. Mask the C17 inline adapter output in that process.
        val inlineSimAdapterClass =
            XposedHelpers.findClassIfExists(INLINE_SIM_ADAPTER_CLASS, classLoader)
        if (inlineSimAdapterClass != null) {
            runCatching {
                XposedBridge.hookAllMethods(
                    inlineSimAdapterClass,
                    "onBindViewHolder",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (!LspConfig.isMobileNetworkHidePhoneNumberEnabledXposed()) return
                            param.args.firstOrNull()?.let(::maskInlinePhoneNumber)
                        }
                    },
                )
                hookCount++
            }.onFailure { log("hook inline SIM number binding failed", it) }
        }

        val couiPreferenceClass =
            XposedHelpers.findClassIfExists(COUI_PREFERENCE_CLASS, classLoader)
        if (couiPreferenceClass != null) {
            runCatching {
                XposedBridge.hookAllMethods(
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
                hookCount++
            }.onFailure { log("hook SIM detail number assignment failed", it) }
        }

        SIM_INFO_FRAGMENT_CLASSES.forEach { className ->
            val simInfoFragment = XposedHelpers.findClassIfExists(className, classLoader)
                ?: return@forEach
            CARRIER_BOOLEAN_VISIBILITY_CALLBACKS.forEach { methodName ->
                runCatching {
                    XposedBridge.hookAllMethods(
                        simInfoFragment,
                        methodName,
                        forceCarrierVisibilityCallback(visibleArgumentIndex = 1),
                    )
                    hookCount++
                }.onFailure { log("hook $className.$methodName failed", it) }
            }
            CARRIER_TEXT_VISIBILITY_CALLBACKS.forEach { methodName ->
                runCatching {
                    XposedBridge.hookAllMethods(
                        simInfoFragment,
                        methodName,
                        forceCarrierVisibilityCallback(
                            visibleArgumentIndex = 2,
                            restoreVoiceOverNrAfter = methodName == "j",
                        ),
                    )
                    hookCount++
                }.onFailure { log("hook $className.$methodName failed", it) }
            }
        }

        log("Mobile network entry hooks: $hookCount")
    }

    private fun forceCarrierVisibilityCallback(
        visibleArgumentIndex: Int,
        restoreVoiceOverNrAfter: Boolean = false,
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
                param.thisObject?.let(::ensureVoiceOverNrOption)
            }
        }

    private fun ensureVoiceOverNrOption(fragment: Any) {
        runCatching {
            val controller = XposedHelpers.getObjectField(fragment, "A") ?: return@runCatching
            val featureRouter = XposedHelpers.getObjectField(fragment, "L") ?: return@runCatching
            val slotId = XposedHelpers.callMethod(controller, "r") as? Int ?: return@runCatching
            val useVonr = XposedHelpers.callMethod(featureRouter, "l", slotId) as? Boolean ?: false
            val category = listOf("f6444f", "f6478f")
                .firstNotNullOfOrNull { fieldName ->
                    runCatching { XposedHelpers.getObjectField(fragment, fieldName) }.getOrNull()
                } ?: return@runCatching
            val vonr = XposedHelpers.getObjectField(fragment, "J")
            val vo5g = XposedHelpers.getObjectField(fragment, "K")
            val selected = if (useVonr) vonr else vo5g
            val unselected = if (useVonr) vo5g else vonr
            if (unselected != null) XposedHelpers.callMethod(category, "i", unselected)
            if (selected != null) XposedHelpers.callMethod(category, "e", selected)
        }.onFailure { log("restore VoNR/Vo5G option failed", it) }
    }

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
